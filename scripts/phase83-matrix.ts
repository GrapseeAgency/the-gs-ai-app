/**
 * PHASE 8.3 — BLACK-BOX ACCEPTANCE MATRIX (spec §16/§22/§23).
 *
 * Runs the exact forensic cases through the REAL public path: POST to the
 * messages API with stream:true (identical to the web client), SSE parsed
 * the same way page.tsx parses it. Verdicts come from the WIRE RESPONSES —
 * never from function existence.
 */
import { detectExecutionContradiction } from '../src/lib/synthesis-guard'

const BASE = 'http://localhost:3000'
const results: string[] = []

function log(line: string) {
  console.log(line)
  results.push(line)
}

async function newConversation(title: string): Promise<string> {
  const res = await fetch(`${BASE}/api/v1/conversations`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ title }),
  })
  if (!res.ok) throw new Error(`conversation create failed: ${res.status}`)
  const data = (await res.json()) as { id: string }
  return data.id
}

/** Same client path as page.tsx: stream:true + SSE `data:` line parsing. */
async function send(
  convId: string,
  content: string
): Promise<{ text: string; sources: { ordinal: number; domain: string; url: string; status: string; used: boolean }[]; error?: string }> {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), 150_000)
  try {
    const res = await fetch(`${BASE}/api/v1/conversations/${convId}/messages`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ content, stream: true, timezone: 'Asia/Dhaka' }),
      signal: controller.signal,
    })
    if (!res.ok || !res.body) throw new Error(`HTTP ${res.status}`)
    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let text = ''
    let sources: { ordinal: number; domain: string; url: string; status: string; used: boolean }[] = []
    let error: string | undefined
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() ?? ''
      for (const line of lines) {
        const trimmed = line.trim()
        if (!trimmed.startsWith('data:')) continue
        try {
          const event = JSON.parse(trimmed.slice(5).trim()) as { event: string; data: string }
          if (event.event === 'delta') text += event.data ?? ''
          else if (event.event === 'error') error = event.data
          else if (event.event === 'done') {
            const msg = JSON.parse(event.data) as {
              content?: string
              sources?: { ordinal: number; domain: string; url: string; status: string; used: boolean }[]
            }
            if (msg.content) text = msg.content
            if (msg.sources) sources = msg.sources
          }
        } catch {
          // keep-alive comment or partial line — ignore
        }
      }
    }
    return { text, sources, error }
  } finally {
    clearTimeout(timeout)
  }
}

function check(name: string, ok: boolean, detail: string) {
  log(`${ok ? 'PASS' : 'FAIL'} | ${name} | ${detail}`)
}

function denialCheck(label: string, text: string) {
  const v = detectExecutionContradiction(text, {
    capability: 'WEB',
    searchExecuted: true,
    evidenceProvided: true,
    timeProvided: false,
    documentProvided: false,
    reuseProvided: false,
  })
  check(`${label} CASE-I-regression`, v === null, v ? `DENIAL LEAKED: "${v.match}"` : 'no contradiction with executed search')
}

async function main() {
  // ---- A: "hi matey" → normal chat, zero search ----------------------------
  const convA = await newConversation('83-A')
  const a = await send(convA, 'hi matey')
  check('A hi-matey', a.sources.length === 0 && a.text.length > 0 && !a.error, `sources=${a.sources.length} err=${a.error ?? 'none'} text="${a.text.slice(0, 80)}"`)

  // ---- B: "what time is it?" → REAL time capability ------------------------
  const convB = await newConversation('83-B')
  const b = await send(convB, 'what time is it?')
  const nowMin = new Date().getHours() * 60 + new Date().getMinutes()
  const times = [...b.text.matchAll(/(\d{1,2})[:.](\d{2})/g)].map((m) => Number(m[1]) * 60 + Number(m[2]))
  const timeOk =
    times.some((t) => Math.abs(t - nowMin) <= 15 || Math.abs(t - nowMin) >= 1425) ||
    /Asia\/Dhaka|UTC\+0?6/i.test(b.text)
  check('B what-time', b.sources.length === 0 && timeOk && !a.error, `sources=${b.sources.length} timeMatch=${timeOk} text="${b.text.slice(0, 120)}"`)

  // ---- E: "hello" → normal chat, zero search -------------------------------
  const convE = await newConversation('83-E')
  const e = await send(convE, 'hello')
  check('E hello', e.sources.length === 0 && e.text.length > 0, `sources=${e.sources.length} text="${e.text.slice(0, 80)}"`)

  // ---- F/G/H/I: the four-turn pollution + reuse + re-search sequence -------
  const convF = await newConversation('83-FGHI')
  const f = await send(convF, 'search the internet for who was the first Muslim')
  check('F first-muslim-search', f.sources.length > 0 && f.text.length > 0, `sources=${f.sources.length} domains=[${[...new Set(f.sources.map((s) => s.domain))].slice(0, 4).join(',')}]`)
  denialCheck('F', f.text)

  const g = await send(convF, "what's the most beautiful diagram chart?")
  const polluted = /\b(muslim|islam|islamic|adam|quran|kaaba)\b/i.test(g.text)
  check('G subjective-isolation', g.sources.length === 0 && !polluted, `sources=${g.sources.length} pollution=${polluted} text="${g.text.slice(0, 100)}"`)

  const h = await send(convF, 'stop searching and tell me what you already found')
  const hOnTopic = /\b(muslim|islam|islamic|first)\b/i.test(h.text)
  check('H stop-reuse', hOnTopic && !h.error, `sources=${h.sources.length} onTopic=${hOnTopic} text="${h.text.slice(0, 120)}"`)

  const i = await send(convF, 'search again using primary sources')
  check('I search-again', i.sources.length > 0, `sources=${i.sources.length} domains=[${[...new Set(i.sources.map((s) => s.domain))].slice(0, 4).join(',')}]`)
  denialCheck('I', i.text)

  // ---- C: explicit internet directive → forced WEB --------------------------
  const convC = await newConversation('83-C')
  const c = await send(convC, "go to the internet and find today's AI news")
  check('C internet-directive', c.sources.length > 0, `sources=${c.sources.length} text="${c.text.slice(0, 100)}"`)
  denialCheck('C', c.text)

  // ---- D: freshness retrieval ------------------------------------------------
  const convD = await newConversation('83-D')
  const d = await send(convD, 'who is the current president of Chile?')
  check('D freshness', d.sources.length > 0, `sources=${d.sources.length} text="${d.text.slice(0, 100)}"`)
  denialCheck('D', d.text)

  // ---- J: source-specific retrieval ------------------------------------------
  const convJ = await newConversation('83-J')
  const j = await send(convJ, 'what does Wikipedia say about the London Underground map?')
  const wikiSource = j.sources.some((s) => /wikipedia\.org/i.test(s.domain))
  const cited = /\[\d+\]/.test(j.text)
  check('J wikipedia-source', j.sources.length > 0 && wikiSource && cited, `sources=${j.sources.length} wiki=${wikiSource} cited=${cited}`)
  denialCheck('J', j.text)

  log('MATRIX-COMPLETE')
  await import('fs').then((fs) => fs.writeFileSync('/home/z/my-project/tool-results/phase83-matrix-results.txt', results.join('\n')))
}

main().catch((err) => {
  log(`MATRIX-ABORT ${err instanceof Error ? err.message : String(err)}`)
  process.exit(1)
})
