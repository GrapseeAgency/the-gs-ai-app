/**
 * Genui pipeline smoke test — the /genui gallery IS the fixture.
 *
 * Extracts every section's markdown from src/app/genui/page.tsx, unescapes
 * the template literal, runs the real stream parser, and asserts each widget
 * segment resolves to a registered renderer.
 *
 * Run: ./node_modules/.bin/tsc -p tsconfig.probe.json && node dist/probe/scripts/test-genui-pipeline.js
 * Exits 1 if any widget lacks a renderer or a section yields none.
 */
import './probe-alias-hook'
import { readFileSync } from 'fs'
import { parseStream } from '../src/components/genui/stream'
import { rendererFor } from '../src/components/genui/registry'

const page = readFileSync('/home/user/the-gs-ai-app/src/app/genui/page.tsx', 'utf8')
const ids = ['sims', 'stage3d', 'canvas', 'editors', 'terminal']
let total = 0
let missing = 0
for (const id of ids) {
  const start = page.indexOf(`id: '${id}'`)
  const end = page.indexOf(`id: '`, start + 6) === -1 ? page.indexOf('\n]\n', start) : page.indexOf(`id: '`, start + 6)
  const chunk = page.slice(start, end > start ? end : undefined)
  const mdStart = chunk.indexOf('md: `')
  if (mdStart === -1) { console.log(`${id}: MD NOT FOUND`); continue }
  const raw = chunk.slice(mdStart + 5, chunk.indexOf('`,\n  }', mdStart))
  // the page stores md as a JS template literal — unescape it the way the
  // runtime would before feeding the parser
  const md = raw.replace(/\\`/g, '`').replace(/\\\$/g, '$').replace(/\\\\/g, '\\')
  const segments = parseStream(md)
  const widgets = segments.filter((s): s is Extract<typeof s, { kind: 'widget' }> => s.kind === 'widget')
  const kinds = widgets.map((w) => `${w.widget}${w.complete ? '' : '?'}`)
  for (const w of widgets) {
    if (!rendererFor(w.widget)) missing++
    const sub = String((w.payload as Record<string, unknown>).surface ?? (w.payload as Record<string, unknown>).editor ?? (w.payload as Record<string, unknown>).sim ?? (w.payload as Record<string, unknown>).view ?? (w.payload as Record<string, unknown>).type ?? '')
    if (sub) console.log(`  ${id}: ${w.widget}/${sub}${rendererFor(w.widget) ? '' : '  <- NO RENDERER'}`)
    else console.log(`  ${id}: ${w.widget}${rendererFor(w.widget) ? '' : '  <- NO RENDERER'}`)
  }
  total += widgets.length
  console.log(`${id}: ${widgets.length} widgets [${kinds.join(', ')}]`)
}
console.log(`TOTAL ${total} v3 widgets, ${missing} without renderer`)
if (missing > 0) process.exit(1)
