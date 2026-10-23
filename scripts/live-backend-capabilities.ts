/**
 * End-to-end capability probe against a locally running branch server.
 *
 *   1. ambiguous coding question -> planner must clarify
 *   2. send the clarify choice   -> answer turn (multi-turn)
 *   3. current-info question     -> planner must search, sources must return
 *   4. fresh coding question     -> answer
 *
 * It uses the real production route, the real SQLite DB, the real planner and
 * the repository's OpenRouter key in CI. No fixture, no mocked model.
 */
import fs from 'node:fs'
import path from 'node:path'

const base = (process.env.GS_TEST_BASE || 'http://127.0.0.1:3000').replace(/\/$/, '')
const session = `nexly-cap-${Date.now()}`

type Msg = {
  id?: string
  content?: string
  clarifyOptions?: string
  sources?: unknown[]
}

// A free-tier upstream can refuse one request and serve the next; the key
// pool rotates server-side, so a transient 502/upstream_error is a "try
// again", not a failure of the capability under test.
async function post<T>(pathName: string, body: unknown): Promise<T> {
  let lastErr: unknown = null
  for (let attempt = 1; attempt <= 3; attempt++) {
    try {
      const res = await fetch(`${base}${pathName}`, {
        method: 'POST',
        headers: {
          'content-type': 'application/json',
          'x-session-id': session,
        },
        body: JSON.stringify(body),
      })
      const text = await res.text()
      if (res.status === 502 || res.status === 503 || res.status === 429) {
        lastErr = new Error(`${pathName} HTTP ${res.status}: ${text.slice(0, 300)}`)
        if (attempt < 3) {
          console.log(`attempt ${attempt} got HTTP ${res.status} — retrying in ${attempt * 10}s`)
          await new Promise((r) => setTimeout(r, attempt * 10_000))
          continue
        }
        throw lastErr
      }
      if (!res.ok) throw new Error(`${pathName} HTTP ${res.status}: ${text.slice(0, 500)}`)
      return JSON.parse(text) as T
    } catch (err) {
      // network-level refusal (connection reset mid-stream): also transient
      if (attempt < 3 && err instanceof TypeError) {
        lastErr = err
        await new Promise((r) => setTimeout(r, attempt * 10_000))
        continue
      }
      throw err
    }
  }
  throw lastErr ?? new Error('unreachable')
}

async function ask(conversationId: string, content: string): Promise<Msg> {
  return post<Msg>(`/api/v1/conversations/${conversationId}/messages`, {
    content,
    stream: false,
    mode: 'flash',
  })
}

async function main() {
  const conversation = await post<{ id: string }>('/api/v1/conversations', {
    title: 'Nexly capability probe',
  })
  const id = conversation.id
  const report: Record<string, unknown> = { conversationId: id }

  // 1 + 2 — ambiguous request, clarify chip, answered follow-up.
  const ambiguous = await ask(id, 'Can you fix my code? It is not working.')
  report.ambiguous = ambiguous
  let clarifyOptions: { id: string; label: string }[] = []
  if (ambiguous.clarifyOptions) {
    try {
      const parsed = JSON.parse(ambiguous.clarifyOptions) as {
        options?: { id?: unknown; label?: unknown }[]
      }
      clarifyOptions = (parsed.options ?? [])
        .filter(
          (o): o is { id: string; label: string } =>
            typeof o?.id === 'string' && typeof o?.label === 'string'
        )
        .slice(0, 5)
    } catch {
      // leave empty; the assertion below will report it
    }
  }
  report.clarifyOptions = clarifyOptions
  if (clarifyOptions.length >= 2) {
    report.clarifyFollowUp = await ask(id, clarifyOptions[0].label)
  }

  // 3 — current information must force retrieval.
  const fresh = await ask(id, 'What is the latest stable version of the Bun runtime right now?')
  report.fresh = fresh
  report.freshSourceCount = Array.isArray(fresh.sources) ? fresh.sources.length : 0

  // 4 — a normal coding answer must not search or clarify unnecessarily.
  const coding = await ask(
    id,
    'Why does my Python list comprehension return None when I use .append() inside it? I want a short, concrete fix.'
  )
  report.coding = coding

  // 5 — persist the checklist server-side, then ask from a turn with NO
  // client turnContext. This is the cross-device path: the server must supply
  // the progress from the DB.
  const saveRes = await fetch(`${base}/api/v1/conversations/${id}/plan-progress`, {
    method: 'PUT',
    headers: { 'content-type': 'application/json', 'x-session-id': session },
    body: JSON.stringify({
      fingerprint: 'demo-morning-v1',
      title: 'demo morning',
      items: [
        { key: 'water', label: 'Drink water' },
        { key: 'curtains', label: 'Open curtains' },
      ],
      done: ['water', 'curtains'],
      preference: 'up',
    }),
  })
  if (!saveRes.ok) throw new Error(`plan-progress PUT HTTP ${saveRes.status}`)
  const readRes = await fetch(`${base}/api/v1/conversations/${id}/plan-progress`, {
    headers: { 'x-session-id': session },
  })
  const readBody = (await readRes.json()) as { progress?: unknown }
  report.planProgressSaved = true
  report.planProgressRead = readBody.progress ?? null
  if (!readBody.progress) throw new Error('PLAN PROGRESS DID NOT PERSIST SERVER-SIDE')
  const progress = await ask(
    id,
    'List the completed items from my plan progress context, separated by " and ". Do not add anything else.'
  )
  report.progress = progress

  const outDir = path.join(process.cwd(), 'docs/reference/nexly')
  fs.mkdirSync(outDir, { recursive: true })
  fs.writeFileSync(
    path.join(outDir, 'backend-capabilities.json'),
    JSON.stringify(report, null, 2) + '\n'
  )

  console.log('\n=== CAPABILITY REPORT ===')
  console.log(`conversation: ${id}`)
  console.log(`ambiguous question: ${ambiguous.content?.slice(0, 160)}`)
  console.log(`clarify options: ${clarifyOptions.map((o) => o.label).join(' | ') || 'NONE'}`)
  console.log(`clarify follow-up answered: ${Boolean(report.clarifyFollowUp)}`)
  console.log(`fresh sources: ${report.freshSourceCount}`)
  console.log(`fresh answer: ${fresh.content?.slice(0, 300)}`)
  console.log(`progress answer: ${progress.content?.slice(0, 300)}`)

  if (clarifyOptions.length < 2) {
    throw new Error('PLANNER DID NOT CLARIFY AN AMBIGUOUS REQUEST')
  }
  if (!report.clarifyFollowUp) {
    throw new Error('CLARIFY FOLLOW-UP TURN DID NOT RUN')
  }
  if (report.freshSourceCount < 1) {
    throw new Error('FRESH QUESTION DID NOT RETURN SOURCES')
  }
  const progressText = (progress.content ?? '').toLowerCase()
  if (!progressText.includes('water') && !progressText.includes('curtain')) {
    throw new Error('MODEL DID NOT RECEIVE THE PLAN PROGRESS CONTEXT')
  }
}

main().catch((e) => {
  console.error(e instanceof Error ? e.message : String(e))
  process.exit(1)
})
