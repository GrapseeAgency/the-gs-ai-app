/**
 * Runtime proof for the widget pipeline. Run with:
 *   npx tsc scripts/test-widget-compiler.ts --outDir /tmp/wt --module commonjs \
 *     --target es2020 --moduleResolution node --esModuleInterop --skipLibCheck
 *   node /tmp/wt/scripts/test-widget-compiler.js
 */
import { compileWidgets } from '../src/lib/widgets/compile'
import { validateWidget, extractWidgetFences, repairJson, validationErrors } from '../src/lib/widgets/validate'
import { toolCallToFence } from '../src/lib/widgets/tools'

declare const process: { exit(code: number): never }

let passed = 0
const failures: string[] = []

function check(name: string, condition: boolean, detail = '') {
  if (condition) {
    passed++
    console.log(`ok   ${name}`)
  } else {
    failures.push(`${name}${detail ? ` — ${detail}` : ''}`)
    console.log(`FAIL ${name}${detail ? ` — ${detail}` : ''}`)
  }
}

// 1. A valid fence is kept and normalised.
{
  const reply =
    'Here you go.\n\n```chart\n' +
    JSON.stringify({
      type: 'bar',
      title: 'Revenue',
      labels: ['Q1', 'Q2'],
      series: [{ name: 'A', data: [[0, 10], [1, 20]] }],
    }) +
    '\n```'
  const r = compileWidgets({ reply, userPrompt: 'show me a chart' })
  check('valid fence is kept', r.path === 'model' && r.widgets === 1, r.path)
}

// 2. Trailing comma is repaired, not dropped.
{
  const reply = '```chart\n{"type":"bar","labels":["a","b"],"series":[{"name":"v","data":[[0,1],[1,2],]},]}\n```'
  const r = compileWidgets({ reply, userPrompt: 'chart' })
  check('trailing comma repaired', r.path === 'repaired' && r.widgets === 1, `${r.path} ${r.errors.join(',')}`)
}

// 3. Smart quotes + one missing brace are repaired.
{
  const reply = '```table\n{\u201ctitle\u201d:\u201cT\u201d,\u201ccolumns\u201d:[\u201ca\u201d],\u201crows\u201d:[[\u201c1\u201d]]\n```'
  const r = compileWidgets({ reply, userPrompt: 'table' })
  check('smart quotes + missing brace repaired', r.path === 'repaired' && r.widgets === 1, r.path)
}

// 4. No widget, but the answer states the numbers → deterministic fallback.
{
  const reply = 'Revenue by quarter:\n- Q1: 120\n- Q2: 150\n- Q3: 180'
  const r = compileWidgets({ reply, userPrompt: 'show me a bar chart' })
  check('fallback builds a chart', r.path === 'fallback' && r.widgets === 1, r.path)
  const fences = extractWidgetFences(r.text)
  check('fallback fence parses', fences.length === 1 && validateWidget(fences[0].kind, fences[0].json).ok)
}

// 5. No widget and no data → nothing invented.
{
  const r = compileWidgets({ reply: 'I cannot display charts.', userPrompt: 'show me a chart' })
  check('no data → no widget', r.path === 'none' && r.widgets === 0, r.path)
}

// 6. Pie with one point per slice is accepted (the shape that used to be rejected).
{
  const payload = {
    type: 'pie',
    labels: ['a', 'b'],
    series: [{ name: 'a', data: [[0, 40]] }, { name: 'b', data: [[0, 60]] }],
  }
  const v = validateWidget('chart', JSON.stringify(payload))
  check('pie one-point slices accepted', v.ok, validationErrors(v).join(','))
}

// 7. Unknown chart type is repaired to bar, not dropped.
{
  const payload = { type: 'sankeyish', labels: ['a', 'b'], series: [{ name: 'v', data: [[0, 1], [1, 2]] }] }
  const v = validateWidget('chart', JSON.stringify(payload))
  check('unknown chart type → bar', v.ok && v.widget.type === 'bar' && v.repaired, v.ok ? String(v.widget.type) : validationErrors(v).join(','))
}

// 8. Caps are enforced.
{
  const cols = Array.from({ length: 20 }, (_, i) => `c${i}`)
  const rows = [cols.map(() => 'x')]
  const v = validateWidget('table', JSON.stringify({ columns: cols, rows }))
  check('table columns capped', v.ok && (v.widget.columns as unknown[]).length === 8)
}

// 9. A chart with a single point is rejected (not enough to plot).
{
  const payload = { type: 'line', labels: ['a'], series: [{ name: 'v', data: [[0, 1]] }] }
  const v = validateWidget('chart', JSON.stringify(payload))
  check('single-point line rejected', !v.ok)
}

// 10. Tool calls convert to the same fence format the clients already parse.
{
  const fence = toolCallToFence('render_chart', {
    type: 'bar',
    title: 'T',
    labels: ['a', 'b'],
    series: [{ name: 'v', data: [[0, 1], [1, 2]] }],
  })
  check('tool call → fence', fence !== null && fence.startsWith('```chart'))
}

// 11. Bad JSON that cannot be repaired is dropped, not shipped.
{
  const reply = '```chart\n{not json at all\n```'
  const r = compileWidgets({ reply, userPrompt: 'chart' })
  check('unrepairable fence dropped', r.widgets === 0 && !r.text.includes('```chart'), r.path)
}

// 12. repairJson returns null on garbage.
check('repairJson rejects garbage', repairJson('hello world') === null)

console.log(`\n${passed} passed, ${failures.length} failed`)
if (failures.length) {
  for (const f of failures) console.log(`  - ${f}`)
  process.exit(1)
}
