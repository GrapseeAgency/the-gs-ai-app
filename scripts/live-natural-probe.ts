/**
 * NATURAL-LANGUAGE LIVE PROBE (GenUI).
 *
 * live-widget-probe.ts asks for widgets BY FENCE NAME — it proves the client
 * routes every family. This one asks the way a user actually asks, which tests
 * the other half: what the model does when nobody names a fence. Against the
 * currently deployed (old) server, that is exactly what users get today, so
 * every dropped answer here is a real recovery gap.
 *
 * Usage: npx tsc scripts/live-natural-probe.ts --outDir /tmp/wpn --module commonjs \
 *   --target es2020 --moduleResolution node --esModuleInterop --skipLibCheck --lib es2020,dom
 *   node /tmp/wpn/scripts/live-natural-probe.js
 */
declare const process: { env: Record<string, string | undefined>; argv: string[] }
import { compileWidgets } from '../src/lib/widgets/compile'

const BASE = 'https://preview-chat-c945696f-6447-4dfa-b510-971d8b9eb5bf.space-z.ai'

const PROMPTS: Array<{ name: string; text: string }> = [
  { name: 'mrr-chart', text: 'Chart the last 6 months of our MRR: Apr 41200, May 43800, Jun 46100, Jul 48900, Aug 52300, Sep 55600.' },
  { name: 'gdp-table', text: 'Make a table of the top 5 EU economies by GDP with 2024 numbers.' },
  { name: 'quiz', text: 'Quiz me on the French Revolution, three questions.' },
  { name: 'compare-plans', text: 'Compare our Basic and Pro pricing plans as cards I can flip through.' },
  { name: 'mortgage', text: "What's the monthly payment on a $320,000 loan at 6.4% for 30 years with $64,000 down? Show me a calculator." },
  { name: 'map', text: 'Show me where the Eiffel Tower is on a map.' },
  { name: 'timeline', text: 'Give me a timeline of the Apollo program milestones.' },
  { name: 'gauge', text: 'Gauge of how full our disk is — 82 percent.' },
  { name: 'gantt', text: 'Gantt chart for a 6-week product launch plan.' },
  { name: 'search', text: 'Search the web for the tallest building in the world and show me the results.' },
  { name: 'progress', text: 'Show our sprint burndown as progress bars: design 100, api 75, docs 40.' },
  { name: 'followups', text: 'Suggest three follow-up questions about our pricing page.' },
]

async function turn(text: string, timezone: string | null): Promise<string> {
  const create = await fetch(`${BASE}/api/v1/conversations`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ title: 'natural probe' }),
  })
  if (!create.ok) throw new Error(`create ${create.status}`)
  const { id } = (await create.json()) as { id: string }
  const res = await fetch(`${BASE}/api/v1/conversations/${id}/messages`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ content: text, stream: true, timezone }),
  })
  if (!res.ok || !res.body) throw new Error(`send ${res.status}`)
  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buf = ''
  let out = ''
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    buf += decoder.decode(value, { stream: true })
    let idx: number
    while ((idx = buf.indexOf('\n')) >= 0) {
      const line = buf.slice(0, idx).trim()
      buf = buf.slice(idx + 1)
      if (!line.startsWith('data:')) continue
      const payload = line.slice(5).trim()
      if (!payload) continue
      try {
        const frame = JSON.parse(payload) as { event?: string; data?: unknown }
        if (frame.event === 'delta' && typeof frame.data === 'string') out += frame.data
        if (frame.event === 'error') return `ERROR_EVENT: ${payload.slice(0, 300)}`
      } catch {
        /* ignore non-JSON frames */
      }
    }
  }
  return out
}

async function main() {
  const tz = process.env.PROBE_TZ ?? 'Asia/Dhaka'
  let ok = 0
  let bad = 0
  for (const p of PROMPTS) {
    let text = ''
    try {
      text = await turn(p.text, tz)
    } catch (e) {
      console.log(`FAIL ${p.name}: ${(e as Error).message}`)
      bad++
      continue
    }
    const fences = [...text.matchAll(/```([A-Za-z0-9_-]+)/g)].map((m) => m[1])
    const compiled = compileWidgets({ reply: text, userPrompt: p.text })
    const kept = compiled.widgets > 0
    const rendered = kept || /```(mermaid|mindmap|map|html|svg)\b/.test(text)
    console.log(
      `${rendered ? 'ok  ' : 'BAD '} ${p.name.padEnd(14)} fences=[${fences.join(',') || 'none'}] ` +
      `compiled=${compiled.path}/${compiled.widgets}` +
      (compiled.errors.length ? ` ERR: ${compiled.errors.join(' | ').slice(0, 140)}` : '')
    )
    if (rendered) ok++
    else {
      bad++
      console.log('----- raw (first 600) -----')
      console.log(text.slice(0, 600))
      console.log('-----------------------------')
    }
  }
  console.log(`\n${ok} rendered, ${bad} not`)
}

void main()
