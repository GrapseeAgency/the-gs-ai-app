/**
 * Second batch of fully random questions — this time THROUGH the live server
 * (not straight to OpenRouter), so the first-turn decisions the user asked
 * about are exercised for real: the planner classifies each question in the
 * first chat and decides answer / clarify / search on its own, flash mode
 * (reasoning disabled) like the apps.
 *
 *   GS_TEST_BASE=http://127.0.0.1:3000 bun scripts/live-random-questions-v2.ts
 *
 * Writes docs/reference/nexly/random2-<id>.md plus random2-summary.json.
 * No key is read or logged here; the server owns the key pool.
 */
import fs from 'node:fs'
import path from 'node:path'

const base = (process.env.GS_TEST_BASE || 'http://127.0.0.1:3000').replace(/\/$/, '')
const session = `probe-random2-${Date.now()}`
const outDir = path.join(process.cwd(), 'docs/reference/nexly')

type Question = { id: string; question: string; expectSearch: boolean }

const QUESTIONS: Question[] = [
  {
    id: 'coding-sql-join-duplicates',
    question:
      'Why does my SQL query return duplicate rows when I join orders with order_items, and what is the cleanest fix?',
    expectSearch: false,
  },
  {
    id: 'coding-js-async-beginner',
    question:
      'Explain async/await vs .then() promises in JavaScript to a complete beginner with one tiny example.',
    expectSearch: false,
  },
  {
    id: 'life-monstera-yellow',
    question:
      'The bottom leaves of my monstera are turning yellow. What is most likely wrong and what should I do first?',
    expectSearch: false,
  },
  {
    id: 'life-dst-end-current',
    question: 'When does daylight saving time end in the United States this year?',
    expectSearch: true,
  },
]

type Msg = {
  id: string
  role: string
  content: string
  clarifyOptions?: string
  sources?: unknown[]
}

async function ask(conversationId: string, content: string): Promise<Msg> {
  let lastErr: unknown = null
  for (let attempt = 1; attempt <= 3; attempt++) {
    try {
      const res = await fetch(`${base}/api/v1/conversations/${conversationId}/messages`, {
        method: 'POST',
        headers: { 'content-type': 'application/json', 'x-session-id': session },
        body: JSON.stringify({ content, stream: false, mode: 'flash' }),
      })
      const text = await res.text()
      if (res.status === 502 || res.status === 503 || res.status === 429) {
        lastErr = new Error(`HTTP ${res.status}: ${text.slice(0, 200)}`)
        if (attempt < 3) {
          console.log(`attempt ${attempt} got HTTP ${res.status} — retrying in ${attempt * 10}s`)
          await new Promise((r) => setTimeout(r, attempt * 10_000))
          continue
        }
        throw lastErr
      }
      if (!res.ok) throw new Error(`HTTP ${res.status}: ${text.slice(0, 400)}`)
      return JSON.parse(text) as Msg
    } catch (err) {
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

async function main() {
  fs.mkdirSync(outDir, { recursive: true })
  const summary: Record<string, unknown>[] = []
  for (const q of QUESTIONS) {
    const conv = await fetch(`${base}/api/v1/conversations`, {
      method: 'POST',
      headers: { 'content-type': 'application/json', 'x-session-id': session },
      body: JSON.stringify({ title: `Random v2 — ${q.id}` }),
    })
    if (!conv.ok) throw new Error(`conversation create HTTP ${conv.status}`)
    const { id } = (await conv.json()) as { id: string }
    const answer = await ask(id, q.question)
    const sources = Array.isArray(answer.sources) ? answer.sources.length : 0
    const clarify = answer.clarifyOptions ? JSON.parse(answer.clarifyOptions) : null
    const row = {
      id: q.id,
      conversationId: id,
      question: q.question,
      clarified: Boolean(clarify),
      sources,
      expectSearch: q.expectSearch,
      searchMatched: q.expectSearch ? sources > 0 : true,
      answerChars: answer.content.length,
    }
    summary.push(row)
    fs.writeFileSync(
      path.join(outDir, `random2-${q.id}.md`),
      `# Random v2 — ${q.id}\n\n**Question (user, flash mode):** ${q.question}\n\n` +
        (clarify ? `**Model asked for clarification instead of guessing:**\n\n${answer.content}\n` : '') +
        `\n**Model answer:**\n\n${answer.content}\n\n` +
        `---\n\n- mode: flash (reasoning disabled at the model layer; planner/capability routing unaffected)\n` +
        `- planner decision: ${clarify ? 'clarify (ambiguous)' : sources > 0 ? `search first turn (${sources} sources)` : 'answer directly'}\n` +
        `- conversation: ${id}\n`
    )
    console.log(
      `${q.id}: clarify=${row.clarify} sources=${sources} (expected search: ${q.expectSearch}) chars=${row.answerChars}`
    )
  }
  fs.writeFileSync(
    path.join(outDir, 'random2-summary.json'),
    JSON.stringify({ at: new Date().toISOString(), base, questions: summary }, null, 2)
  )
  const allMatch = summary.every((s) => s.searchMatched && !s.clarified)
  console.log('\nRANDOM-V2 RESULT:', allMatch ? 'PASS' : 'CHECK')
  if (!allMatch) process.exitCode = 1
}

main().catch((err) => {
  console.error(err instanceof Error ? err.message : err)
  process.exit(1)
})
