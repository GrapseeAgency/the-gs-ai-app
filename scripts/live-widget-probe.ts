/**
 * LIVE-SERVER PROBE (GenUI).
 *
 * The v0.81.12 client ships today; the backend does not. Until the operator
 * deploys, devices talk to an OLD build of the server. This probe asks the
 * live server for the widgets the prompt cannot misread (direct fence
 * requests), captures the raw assistant text, then runs every payload
 * through the CURRENT parser rules — the same rules the Android client uses
 * — and reports what would render.
 *
 * Usage: npx tsx scripts/live-widget-probe.ts   (or tsc+node like test:widgets)
 */
declare const process: { env: Record<string, string | undefined>; argv: string[] }

const BASE = 'https://preview-chat-c945696f-6447-4dfa-b510-971d8b9eb5bf.space-z.ai'

const PROMPTS: Array<{ name: string; text: string }> = [
  { name: 'bar-chart', text: 'Reply with ONLY a ```chart fence, no prose: a bar chart of quarterly revenue Q1 120, Q2 90, Q3 140, Q4 170 (millions USD).' },
  { name: 'line-chart', text: 'Reply with ONLY a ```chart fence: a line chart of monthly signups Jan..Jun: 12, 19, 31, 44, 58, 71.' },
  { name: 'table', text: 'Reply with ONLY a ```table fence: columns Name/Role/Score, rows Ada/eng/91, Lin/ops/78, Kai/design/85.' },
  { name: 'subtype-kanban', text: 'Reply with ONLY a ```kanban fence: columns Todo/Doing/Done, one card in each.' },
  { name: 'subtype-loan', text: 'Reply with ONLY a ```loan fence: a 30-year mortgage calculator, principal 320000, rate 6.4, down 64000.' },
  { name: 'media-pdf', text: 'Reply with ONLY a ```pdf fence: a document preview titled "Lease agreement" with 3 pages.' },
  { name: 'gallery', text: 'Reply with ONLY a ```gallery fence: three placeholder images from https://picsum.photos/seed/a/600/400 style urls.' },
  { name: 'gauge', text: 'Reply with ONLY a ```gauge fence: CPU load 72 percent.' },
  { name: 'quiz', text: 'Reply with ONLY a ```quiz fence: one question, two options, answer index 1.' },
  { name: 'form', text: 'Reply with ONLY a ```form fence: fields email and a select of plan basic/pro, submit label "Continue".' },
]

async function turn(text: string, timezone: string | null): Promise<string> {
  const create = await fetch(`${BASE}/api/v1/conversations`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ title: 'widget probe' }),
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
        /* not every frame is JSON; ignore */
      }
    }
  }
  return out
}

// The fence vocabulary the v0.81.12 clients route to widgets.
const WIDGET_FENCES = /^```(chart|table|timeline|stat|kpi|pie|donut|gauge|quiz|form|carousel|gallery|pdf|search|media|kanban|calendar|booking|app|loan|mortgage|tax|calc|gantt|funnel|mindmap|flowchart|mermaid|svg|html|React|react|tsx|jsx|component)\b/m

async function main() {
  const tz = process.env.PROBE_TZ ?? 'Asia/Dhaka'
  const only = process.argv[2]
  let ok = 0
  let bad = 0
  for (const p of PROMPTS) {
    if (only && !p.name.includes(only)) continue
    let text = ''
    try {
      text = await turn(`${p.text}\n\nIf you must add prose, keep it under 10 words.`, tz)
    } catch (e) {
      console.log(`FAIL ${p.name}: ${(e as Error).message}`)
      bad++
      continue
    }
    const fence = text.match(/```([A-Za-z0-9_-]+)/)?.[1] ?? '(none)'
    const routed = WIDGET_FENCES.test(text)
    const kind = routed ? 'WIDGET' : 'raw'
    console.log(`${routed ? 'ok  ' : 'BAD '} ${p.name.padEnd(16)} fence=${fence.padEnd(10)} ${kind} len=${text.length}`)
    if (routed) ok++
    else {
      bad++
      console.log('----- raw -----')
      console.log(text.slice(0, 400))
      console.log('---------------')
    }
  }
  console.log(`\n${ok} routed, ${bad} not`)
}

void main()
