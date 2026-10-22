/**
 * LIVE Nexly model verification.
 *
 * One real OpenRouter call with the EXACT production system prompt
 * (SYSTEM_PROMPT + WIDGET_PROMPT) and the reference user message. The raw
 * answer and the server-compiled final wire are written to disk; the native
 * render harnesses then render that real output instead of a handcrafted demo.
 *
 * Run once, at the end:
 *   OPENROUTER_API_KEY=... bun scripts/live-nexly-model.ts [model]
 *
 * The key is never written to disk or printed.
 */

import fs from 'node:fs'
import path from 'node:path'
import { SYSTEM_PROMPT } from '../src/lib/ai'
import { WIDGET_PROMPT } from '../src/lib/widgets/prompt'
import { ensureWidgets } from '../src/lib/widgets/compile'
import { parseNexlyReply } from '../src/lib/nexly/plan'

const USER =
  'Can you help me plan a productive day tomorrow? I have classes, ' +
  'assignments and also want to stay healthy and get some rest.'

const key = process.env.OPENROUTER_API_KEY
if (!key) {
  console.error('OPENROUTER_API_KEY is not set')
  process.exit(2)
}

const model = process.argv[2] || 'nvidia/nemotron-3-ultra-550b-a55b:free'
const endpoint = 'https://openrouter.ai/api/v1/chat/completions'
const controller = new AbortController()
const timer = setTimeout(() => controller.abort(), 180_000)

const system = `${SYSTEM_PROMPT}\n\n${WIDGET_PROMPT}`

const response = await fetch(endpoint, {
  method: 'POST',
  signal: controller.signal,
  headers: {
    Authorization: `Bearer ${key}`,
    'Content-Type': 'application/json',
    'HTTP-Referer': 'https://grapsee.agency',
    'X-Title': 'GS AI',
  },
  body: JSON.stringify({
    model,
    messages: [
      { role: 'system', content: system },
      { role: 'user', content: USER },
    ],
    stream: false,
    temperature: 0.2,
    reasoning: { enabled: true },
    max_tokens: 2400,
  }),
})
clearTimeout(timer)

const text = await response.text()
if (!response.ok) {
  console.error(`OpenRouter ${response.status}: ${text.slice(0, 500)}`)
  process.exit(1)
}

const completion = JSON.parse(text) as {
  choices?: { message?: { content?: unknown; reasoning?: unknown } }[]
  model?: string
}
const raw = completion?.choices?.[0]?.message?.content
if (typeof raw !== 'string' || !raw.trim()) {
  console.error('OpenRouter returned empty content')
  console.error(text.slice(0, 500))
  process.exit(1)
}

const compiled = ensureWidgets(raw, { userPrompt: USER })
const parsed = parseNexlyReply(compiled.text)
const outDir = path.join(process.cwd(), 'docs/reference/nexly')
fs.mkdirSync(outDir, { recursive: true })
fs.writeFileSync(path.join(outDir, 'live-model-raw.md'), raw.trim() + '\n')
fs.writeFileSync(path.join(outDir, 'live-model-final.md'), compiled.text.trim() + '\n')

const emoji = /\p{Extended_Pictographic}/u.test(compiled.text)
const rows = parsed.plan?.rows ?? []
console.log(`model: ${completion.model ?? model}`)
console.log(`raw chars: ${raw.length}`)
console.log(`compiler: path=${compiled.path} widgets=${compiled.widgets} errors=${compiled.errors.join(' | ') || 'none'}`)
console.log(`plan: ${parsed.plan ? 'YES' : 'NO'} rows=${rows.length}`)
console.log(`title: ${parsed.plan?.title ?? '-'}`)
console.log(`icons: ${rows.map((r) => r.icon).join(', ')}`)
console.log(`closing: ${parsed.plan?.closing ?? '-'}`)
console.log(`closingIcon: ${parsed.plan?.closingIcon ?? '-'}`)
console.log(`timestamp: ${parsed.plan?.timestamp ?? '-'}`)
console.log(`emoji in final: ${emoji ? 'YES' : 'NO'}`)
console.log(`wrote: docs/reference/nexly/live-model-raw.md`)
console.log(`wrote: docs/reference/nexly/live-model-final.md`)

if (!parsed.plan || rows.length < 2) {
  console.error('LIVE MODEL DID NOT EMIT A RENDERABLE PLAN')
  process.exit(1)
}
