/**
 * Pipe a saved model reply (file) through the CURRENT compiler — the exact
 * rules the shipped client uses — and report what would render.
 *   node /tmp/wp4/scripts/pipe-live-payloads.js file.md [file.md ...]
 */
import { compileWidgets } from '../src/lib/widgets/compile'
declare function require(name: string): any
declare const process: { argv: string[] }
const { readFileSync } = require('fs')

for (const f of process.argv.slice(2)) {
  const reply = readFileSync(f, 'utf8')
  const r = compileWidgets({ reply, userPrompt: 'a widget' })
  const kept = r.text.match(/```[a-z]+\n/g) ?? []
  console.log(`${f}: path=${r.path} widgets=${r.widgets} errors=${r.errors.length ? r.errors.join(' | ') : '(none)'} fences=[${kept.map((s: string) => s.trim()).join(', ')}]`)
}
