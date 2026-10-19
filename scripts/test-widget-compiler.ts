/**
 * Runtime proof for the widget pipeline. Run with:
 *   npx tsc scripts/test-widget-compiler.ts --outDir /tmp/wt --module commonjs \
 *     --target es2020 --moduleResolution node --esModuleInterop --skipLibCheck
 *   node /tmp/wt/scripts/test-widget-compiler.js
 */
import { compileWidgets } from '../src/lib/widgets/compile'
import { validateWidget, extractWidgetFences, repairJson, validationErrors } from '../src/lib/widgets/validate'
import { toolCallToFence } from '../src/lib/widgets/tools'
import { WIDGET_PROMPT } from '../src/lib/widgets/prompt'
import { WIDGET_KINDS, CHART_TYPES, MEDIA_TYPES, CALC_TYPES, APP_TYPES, FORM_FIELD_TYPES, OTHER_RENDER_KINDS } from '../src/lib/widgets/catalog'

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

// 13. Chart sub-type fences are chart widgets, not unknown languages.
{
  const reply =
    'Here is the funnel.\n\n```funnel\n' +
    JSON.stringify({
      title: 'Checkout',
      labels: ['Visit', 'Cart', 'Pay'],
      series: [{ name: 'Users', data: [[0, 1200], [1, 540], [2, 210]] }],
    }) +
    '\n```'
  const r = compileWidgets({ reply, userPrompt: 'show me a funnel chart' })
  check('funnel fence compiles as a chart', r.widgets === 1 && r.path === 'model', r.path)
  check('funnel fence normalises to ```chart', r.text.includes('```chart') && !r.text.includes('```funnel'))
  check('funnel type is injected', r.text.includes('"type":"funnel"'))
}

// 14. An explicit type in the payload beats the fence hint.
{
  const reply = '```line\n{"type":"bar","title":"T","series":[{"name":"a","data":[[0,1],[1,2]]}]}\n```'
  const r = compileWidgets({ reply, userPrompt: 'chart' })
  check('payload type wins over fence alias', r.widgets === 1 && r.text.includes('"type":"bar"'))
}

// 15. stackedarea canonicalises.
{
  const reply = '```stackedarea\n{"title":"T","series":[{"name":"a","data":[[0,1],[1,2]]}]}\n```'
  const r = compileWidgets({ reply, userPrompt: 'chart' })
  check('stackedarea → stackedArea', r.text.includes('"type":"stackedArea"'), r.path)
}


// 16. Real payload shapes from the live server: flat, string-label, object.
{
  const flat = '```chart\n{"type":"funnel","labels":["Visit","Cart"],"series":[{"name":"Users","data":[12000,5400]}]}\n```'
  const r = compileWidgets({ reply: flat, userPrompt: 'funnel chart' })
  check('flat numeric data survives', r.widgets === 1, r.errors.join('|'))
}

{
  const labelled =
    '```chart\n{"type":"bar","labels":["Q1","Q2"],"series":[{"name":"Revenue","data":[["Q1",120],["Q2",180]]}]}\n```'
  const r = compileWidgets({ reply: labelled, userPrompt: 'bar chart' })
  check('string-label data survives', r.widgets === 1, r.errors.join('|'))
}

{
  const objects =
    '```chart\n{"type":"line","series":[{"name":"v","data":[{"x":0,"y":12},{"x":1,"y":18}]}]}\n```'
  const r = compileWidgets({ reply: objects, userPrompt: 'line chart' })
  check('object point data survives', r.widgets === 1, r.errors.join('|'))
}

// 17. A bare payload with no fence at all becomes a real widget.
{
  const reply = '{"type":"bar","title":"Revenue","labels":["Q1","Q2"],"series":[{"name":"R","data":[120,180]}]}'
  const r = compileWidgets({ reply, userPrompt: 'bar chart' })
  check('bare payload is fenced', r.widgets === 1 && r.text.startsWith('```chart'), r.path)
  const outsideFences = r.text.replace(/```[\s\S]*?```/g, '')
  check('bare payload is not left as prose', !outsideFences.includes('"series"'), outsideFences.slice(0, 60))
}

// 18. A ```json block holding a widget is promoted, prose with braces is not.
{
  const fenced = '```json\n{"type":"bar","series":[{"name":"R","data":[120,180]}]}\n```'
  const r = compileWidgets({ reply: fenced, userPrompt: 'chart' })
  check('json fence holding a widget is promoted', r.widgets === 1 && r.text.includes('```chart'), r.path)

  const prose = 'The config is { "a": 1 } and that is all.'
  const r2 = compileWidgets({ reply: prose, userPrompt: 'chart' })
  check('prose with braces is untouched', r2.widgets === 0 && r2.text === prose)
}


// 19. The contract the model reads must mention everything the clients render.
// A kind the prompt never names is a kind the model will never emit, no matter
// how well the renderer works — this is the drift guard between catalogue and
// prompt, and it runs on every test pass.
{
  const missing = (label: string, values: readonly string[]) => {
    const absent = values.filter((v) => !WIDGET_PROMPT.includes(v))
    check(`${label}: all ${values.length} named in the prompt`, absent.length === 0, absent.join(','))
  }
  missing('kinds', WIDGET_KINDS)
  missing('chart types', CHART_TYPES)
  missing('media types', MEDIA_TYPES)
  missing('calc types', CALC_TYPES)
  missing('app types', APP_TYPES)
  missing('form fields', FORM_FIELD_TYPES)
  missing('other renderers', OTHER_RENDER_KINDS)

  check('prompt declares the svg fence', /^- svg:/m.test(WIDGET_PROMPT))
  check('prompt names the theme tokens', WIDGET_PROMPT.includes('--gs-surface') && WIDGET_PROMPT.includes('--gs-hue-1'))
  check('prompt carries the art-direction keys', WIDGET_PROMPT.includes('"accent"') && WIDGET_PROMPT.includes('"variant"'))
  check('prompt forbids emoji', /emoji/i.test(WIDGET_PROMPT))
}

// 20. A kind must not appear in the catalogue without a validator, otherwise a
// malformed payload of that kind reaches the client and renders nothing.
{
  const payloads: Record<string, string> = {
    chart: '{"type":"bar","series":[{"name":"v","data":[1,2]}]}',
    stat: '{"cards":[{"label":"Revenue","value":"120"}]}',
    table: '{"columns":["a"],"rows":[["1"]]}',
    compare: '{"left":{"items":["a"]},"right":{"items":["b"]}}',
    progress: '{"items":[{"label":"a","percent":40}]}',
    timeline: '{"entries":[{"date":"2026-01-01","title":"a"}]}',
    gauge: '{"items":[{"label":"a","value":4,"max":10}]}',
    quiz: '{"questions":[{"q":"a?","options":["x","y"],"answer":1}]}',
    form: '{"fields":[{"type":"text","label":"Name"}]}',
    carousel: '{"items":[{"image":"https://x.test/a.jpg"}]}',
    cards: '{"cards":[{"title":"a"}]}',
    followups: '{"prompts":["a"]}',
    media: '{"type":"image","items":[{"url":"https://x.test/a.jpg"}]}',
    actions: '{"buttons":[{"label":"a","value":"b"}]}',
    panel: '{"title":"a","body":"b"}',
    voice: '{"prompt":"a"}',
    calc: '{"fields":[{"key":"p","label":"P","value":1}],"outputs":[{"label":"o","expr":"p*2"}]}',
    app: '{"type":"tasks","items":[{"title":"a"}]}',
  }
  const noValidator: string[] = []
  const rejected: string[] = []
  for (const kind of WIDGET_KINDS) {
    const json = payloads[kind]
    if (!json) { noValidator.push(kind); continue }
    const r = validateWidget(kind, json)
    if (!r.ok) rejected.push(`${kind}:${validationErrors(r).join('|')}`)
  }
  check('every kind has a sample payload', noValidator.length === 0, noValidator.join(','))
  check('every sample payload validates', rejected.length === 0, rejected.join(';'))
}


// 21. Comment stripping must not eat the // inside a URL. The regex version did,
// which made every media/carousel/link payload fail as "invalid JSON".
{
  const url = '{"items":[{"url":"https://cdn.test/a.jpg","thumb":"http://x.test/b.png"}]}'
  const repaired = repairJson(url)
  check('repairJson keeps https://', repaired !== null && repaired.includes('https://cdn.test/a.jpg'), String(repaired))
  check('repairJson keeps http://', repaired !== null && repaired.includes('http://x.test/b.png'))
  check('media with urls validates', validateWidget('media', url).ok)

  const commented = '{\n  "columns": ["a"], // the only column\n  "rows": [["1"]] /* one row */\n}'
  const r = repairJson(commented)
  check('real comments are still stripped', r !== null && !r.includes('//') && !r.includes('/*'), String(r))

  const trailing = '{"cards":[{"label":"a","value":"1"},],"title":"x",}'
  check('trailing commas are still repaired', validateWidget('stat', trailing).ok)
}


// 22. Data placed at the top level instead of inside a series is still a chart.
{
  const values = '{"type":"bar","labels":["Q1","Q2","Q3"],"values":[120,180,150]}'
  const r = validateWidget('chart', values)
  check('top-level values become a series', r.ok && (r as any).widget.series[0].data.length === 3)

  const pairs = '{"type":"line","data":[[0,12],[1,18]]}'
  const r2 = validateWidget('chart', pairs)
  check('top-level pairs become a series', r2.ok && (r2 as any).widget.series[0].data.length === 2)

  const single = '{"type":"image","url":"https://cdn.test/a.jpg","caption":"A"}'
  const r3 = validateWidget('media', single)
  check('top-level url becomes an item', r3.ok && (r3 as any).widget.items[0].url === 'https://cdn.test/a.jpg')
}

// 23. A chart that is still unusable must not take the prose down with it.
{
  const broken = 'Here is the chart.\n\n```chart\n{"type":"bar","series":[]}\n```\n\nThat is all.'
  const r = compileWidgets({ reply: broken, userPrompt: 'bar chart of revenue' })
  check('an empty chart fence is removed, prose survives', r.text.includes('Here is the chart') && !r.text.includes('"series"'))
}

console.log(`\n${passed} passed, ${failures.length} failed`)
if (failures.length) {
  for (const f of failures) console.log(`  - ${f}`)
  process.exit(1)
}
