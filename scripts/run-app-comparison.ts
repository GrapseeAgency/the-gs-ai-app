/**
 * App-comparison harness runner (GS-AI leg only).
 *
 * Sends the 20 tasks in tests/app-comparison/tasks.json to the local GS-AI
 * app exactly as a client would (POST /api/v1/conversations, attachment
 * upload via /api/v1/uploads, POST /api/v1/conversations/:id/messages with
 * stream=false), and writes the raw transcript to
 * tests/app-comparison/raw-answers.json. Grading is human work and lives in
 * results.md, never here.
 *
 * Usage: bun scripts/run-app-comparison.ts [baseUrl]
 */
import { readFileSync, writeFileSync } from "node:fs";
import path from "node:path";

const REPO = path.resolve(import.meta.dir, "..");
const BASE = process.argv[2] ?? "http://localhost:3000";
const TASKS = JSON.parse(readFileSync(path.join(REPO, "tests/app-comparison/tasks.json"), "utf8"));
const OUT = path.join(REPO, "tests/app-comparison/raw-answers.json");

type Task = {
  id: string;
  category: string;
  prompt?: string;
  turns?: string[];
  fixture?: string;
};

type TurnRecord = {
  request: string;
  httpStatus: number;
  response: unknown;
};

type TaskRecord = {
  id: string;
  category: string;
  conversationId: string | null;
  attachmentIds: string[];
  turns: TurnRecord[];
  error?: string;
};

async function createConversation(title: string): Promise<string> {
  const res = await fetch(`${BASE}/api/v1/conversations`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ title }),
  });
  if (!res.ok) throw new Error(`conversation create ${res.status}: ${await res.text()}`);
  const json = (await res.json()) as { id: string };
  return json.id;
}

async function uploadFixture(convId: string, fixture: string): Promise<string> {
  const bytes = readFileSync(path.join(REPO, "tests/app-comparison/fixtures", fixture));
  const mime = fixture.endsWith(".csv") ? "text/csv" : "text/plain";
  const form = new FormData();
  form.append("file", new Blob([bytes], { type: mime }), fixture);
  form.append("conversationId", convId);
  const res = await fetch(`${BASE}/api/v1/uploads`, { method: "POST", body: form });
  if (!res.ok) throw new Error(`upload ${fixture} ${res.status}: ${await res.text()}`);
  const json = (await res.json()) as { attachment: { id: string } };
  return json.attachment.id;
}

async function sendMessage(
  convId: string,
  content: string,
  attachmentIds: string[]
): Promise<{ status: number; json: unknown }> {
  const res = await fetch(`${BASE}/api/v1/conversations/${convId}/messages`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ content, attachments: attachmentIds, stream: false }),
  });
  let json: unknown = null;
  try {
    json = await res.json();
  } catch {
    json = { unparsed: await res.text() };
  }
  return { status: res.status, json };
}

async function main() {
  const results: TaskRecord[] = [];
  for (const task of TASKS.tasks as Task[]) {
    const record: TaskRecord = {
      id: task.id,
      category: task.category,
      conversationId: null,
      attachmentIds: [],
      turns: [],
    };
    try {
      const convId = await createConversation(`app-compare ${task.id}`);
      record.conversationId = convId;
      const attachmentIds: string[] = [];
      if (task.fixture) attachmentIds.push(await uploadFixture(convId, task.fixture));
      record.attachmentIds = attachmentIds;
      const turns = task.turns ?? [task.prompt ?? ""];
      for (const turn of turns) {
        const { status, json } = await sendMessage(convId, turn, attachmentIds);
        record.turns.push({ request: turn, httpStatus: status, response: json });
      }
    } catch (e) {
      record.error = e instanceof Error ? e.message : String(e);
    }
    results.push(record);
    console.log(`${task.id} done${record.error ? " (ERROR)" : ""}`);
  }
  writeFileSync(OUT, JSON.stringify({ capturedAt: new Date().toISOString(), base: BASE, results }, null, 2));
  console.log(`wrote ${OUT}`);
}

await main();
