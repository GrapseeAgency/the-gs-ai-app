/**
 * Four fresh, non-reference questions answered by the production flash model.
 * Used only for the final native verification: the captures are embedded in
 * the Android/iOS render harnesses, so both devices render real model text,
 * not a plan fixture.
 *
 *   OPENROUTER_API_KEY=... bun scripts/live-random-questions.ts
 *
 * No key is written or logged. Flash only: reasoning is disabled, matching the
 * mode the apps use for these turns.
 */
import fs from 'node:fs'
import path from 'node:path'
import { SYSTEM_PROMPT } from '../src/lib/ai'
import { WIDGET_PROMPT } from '../src/lib/widgets/prompt'
import { ensureWidgets } from '../src/lib/widgets/compile'

type Question = { id: string; question: string }

const QUESTIONS: Question[] = [
  {
    id: 'coding-python-list',
    question:
      'Why does my Python list comprehension return None when I use .append() inside it? I want a short, concrete fix.',
  },
  {
    id: 'life-morning',
    question:
      'I keep hitting snooze and feel behind all morning. What is one change that actually sticks?',
  },
  {
    id: 'coding-react-effect',
    question:
      'My React useEffect fetches the same data twice in development. What is the correct way to stop that?',
  },
  {
    id: 'life-interrupting-friend',
    question:
      'How do I tell a close friend they interrupt me without turning it into a fight?',
  },
]

const key = process.env.OPENROUTER_API_KEY
if (!key) {
  console.error('OPENROUTER_API_KEY is not set')
  process.exit(2)
}

const model = process.argv[2] || 'nvidia/nemotron-3-ultra-550b-a55b:free'
const endpoint = 'https://openrouter.ai/api/v1/chat/completions'
const system = `${SYSTEM_PROMPT}\n\n${WIDGET_PROMPT}`
const outDir = path.join(process.cwd(), 'docs/reference/nexly')

async function ask(item: Question): Promise<void> {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), 180_000)
  try {
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
          { role: 'user', content: item.question },
        ],
        stream: false,
        temperature: 0.3,
        // Flash: no reasoning tier, no hidden thinking budget.
        reasoning: { enabled: false },
        max_tokens: 1600,
      }),
    })
    const text = await response.text()
    if (!response.ok) {
      throw new Error(`OpenRouter ${response.status}: ${text.slice(0, 300)}`)
    }
    const completion = JSON.parse(text) as {
      choices?: { message?: { content?: unknown } }[]
      model?: string
    }
    const raw = completion?.choices?.[0]?.message?.content
    if (typeof raw !== 'string' || !raw.trim()) {
      throw new Error('OpenRouter returned empty content')
    }
    const compiled = ensureWidgets(raw, { userPrompt: item.question })
    fs.writeFileSync(path.join(outDir, `random-${item.id}-raw.md`), raw.trim() + '\n')
    fs.writeFileSync(path.join(outDir, `random-${item.id}-final.md`), compiled.text.trim() + '\n')
    console.log(`\n=== ${item.id} ===`)
    console.log(`model: ${completion.model ?? model}`)
    console.log(`question: ${item.question}`)
    console.log(`raw chars: ${raw.length}, compiler=${compiled.path}, widgets=${compiled.widgets}`)
    console.log(raw.trim().slice(0, 500))
  } finally {
    clearTimeout(timer)
  }
}

fs.mkdirSync(outDir, { recursive: true })
let failures = 0
for (const item of QUESTIONS) {
  try {
    await ask(item)
  } catch (e) {
    failures += 1
    console.error(`\n=== ${item.id} FAILED ===`)
    console.error(e instanceof Error ? e.message : String(e))
  }
}
if (failures > 0) process.exit(1)
