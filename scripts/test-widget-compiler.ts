/**
 * Runtime proof for the widget pipeline. Run with:
 *   npx tsc scripts/test-widget-compiler.ts --outDir /tmp/wt --module commonjs \
 *     --target es2020 --moduleResolution node --esModuleInterop --skipLibCheck
 *   node /tmp/wt/scripts/test-widget-compiler.js
 */
import { compileWidgets } from '../src/lib/widgets/compile'
import { validateWidget, extractWidgetFences, repairJson, validationErrors, inferWidgetKind } from '../src/lib/widgets/validate'
import { toolCallToFence } from '../src/lib/widgets/tools'
import { WIDGET_PROMPT } from '../src/lib/widgets/prompt'
import { directWidgetArt } from '../src/lib/widgets/art-direct'
import { SURFACES } from '../src/lib/design/rulebook'
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

// 10b. A tool call's style keys land inside the rendered payload, including
// render_widget, where they sit beside `payload` on the wire.
{
  const chart = toolCallToFence('render_chart', {
    type: 'bar',
    title: 'T',
    labels: ['a', 'b'],
    series: [{ name: 'v', data: [[0, 1], [1, 2]] }],
    accent: '#E03131',
    variant: 'tinted',
  })
  const fromChart = JSON.parse(extractWidgetFences(chart!)[0].json) as Record<string, unknown>
  check('tool style survives render_chart', fromChart.accent === '#E03131' && fromChart.variant === 'tinted')

  const widget = toolCallToFence('render_widget', {
    kind: 'stat',
    payload: { title: 'T', cards: [{ label: 'a', value: '1' }] },
    accent: '#0CA678',
    variant: 'hero',
  })
  const fromWidget = JSON.parse(extractWidgetFences(widget!)[0].json) as Record<string, unknown>
  check('tool style folds into render_widget', fromWidget.accent === '#0CA678' && fromWidget.variant === 'hero')
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
  check('prompt names every surface', SURFACES.every((surface) => WIDGET_PROMPT.includes(surface)), SURFACES.filter((s) => !WIDGET_PROMPT.includes(s)).join(','))
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
    plan: '{"title":"Tomorrow","rows":[{"time":"8:00 AM","icon":"sunrise","text":"Morning routine"}],"closing":"You got this!","closingIcon":"flame"}',
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
    tabs: '{"tabs":[{"label":"a","body":"b"}]}',
    accordion: '{"sections":[{"label":"a","body":"b"}]}',
    steps: '{"steps":[{"title":"a","body":"b","state":"current"}]}',
    menu: '{"options":[{"label":"a","prompt":"p"}]}',
    chips: '{"options":[{"label":"a","prompt":"p"}],"multi":true}',
    rating: '{"title":"a","prompt":"b","style":"star","max":5}',
    poll: '{"question":"a?","options":["x","y"]}',
    checklist: '{"items":[{"label":"a","done":false}]}',
    tree: '{"roots":[{"label":"a","children":[{"label":"b"}]}]}',
    flashcards: '{"cards":[{"front":"a","back":"b"}]}',
    diff: '{"title":"a","before":"x","after":"y","language":"ts"}',
    sim: '{"sim":"particles","gravity":9.8,"count":40,"seed":3}',
    stage3d: '{"view":"molecule","nodes":[{"label":"O","x":0,"y":0,"z":0},{"label":"H","x":1,"y":0,"z":0}],"bonds":[[0,1]]}',
    canvas: '{"surface":"mindmap","nodes":[{"label":"A","x":0.3,"y":0.3},{"label":"B","x":0.7,"y":0.6}],"edges":[[0,1]]}',
    editor: '{"editor":"sheet","cells":{"A1":"2","A2":"=A1*3"}}',
    terminal: '{"title":"sh","commands":[{"name":"deploy","description":"demo"}]}',
    collab: '{"surface":"vote","room":"Launch Day","items":["Ship it","Hold"]}',
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


// 24. Sub-type fences (```kanban, ```loan, ```gallery) are their parent kind
// with the type injected — the same class of bug as ```funnel.
{
  const kanban = '```kanban\n{"columns":[{"title":"Todo","items":[]}]}\n```'
  const f = extractWidgetFences(kanban)[0]
  check('kanban fence becomes an app widget', f?.kind === 'app', JSON.stringify(f))
  check('kanban type is injected', f?.json.includes('"type":"kanban"') === true, f?.json)

  const loan = '```loan\n{"fields":[{"key":"p","label":"P","value":1}],"outputs":[{"label":"o","expr":"p"}]}\n```'
  const g = extractWidgetFences(loan)[0]
  check('loan fence becomes a calc widget', g?.kind === 'calc' && g.json.includes('"type":"loan"'))

  const code = '```code\n{ "a": 1 }\n```'
  check('```code is never a widget', extractWidgetFences(code).length === 0)

  const explicit = '```kanban\n{"type":"calendar","items":[]}\n```'
  const h = extractWidgetFences(explicit)[0]
  check('an explicit type wins', h?.json.includes('"type":"calendar"') === true && !h.json.includes('kanban'))
}

// 25. THE LIVE PDF PAYLOAD (probe 2026-10-20). The server sends
// {"title":…,"pages":[{"page":1,"text":…}]} with no type and no items. The
// validator used to answer "media needs items, code or text" and the widget
// shipped as nothing. Verified against the real bytes from the live server.
{
  const live = '```pdf\n' +
    '{"title":"Lease agreement","pages":[{"page":1,"text":"LEASE AGREEMENT between the parties."},' +
    '{"page":2,"text":"RENT is due on the first day of each month."},' +
    '{"page":3,"text":"EXHIBIT A property condition report."}]}\n```'
  const f = extractWidgetFences(live)[0]
  check('live pdf fence is a media widget', f?.kind === 'media', JSON.stringify(f))
  const r = validateWidget('media', f!.json)
  check('live pdf pages validate', r.ok && (r as any).widget.pages.length === 3, r.ok ? '' : JSON.stringify((r as any).errors))
  check('live pdf type is pdf', r.ok && (r as any).widget.type === 'pdf')
  const c = compileWidgets({ reply: live, userPrompt: 'a pdf preview' })
  check('live pdf survives the compiler', c.widgets === 1 && c.errors.length === 0, c.errors.join(','))

  // The same payload through the fence the clients see after normalisation,
  // and the bare (unfenced) promotion path.
  const bare = '{"title":"Memo","pages":[{"page":1,"text":"Page one body."}]}'
  check('a bare pages payload is a document', validateWidget(inferWidgetKind(bare) ?? '', bare).ok)
  check('bare pages become a pdf widget', (inferWidgetKind(bare) ?? '') === 'media')

  // The SECOND live shape, seen the same day: "pages":3 — a count, no bodies.
  const counted = '```pdf\n{"title":"Lease agreement","pages":3}\n```'
  const rc = validateWidget('media', extractWidgetFences(counted)[0]!.json)
  check('a page COUNT validates as a document', rc.ok && (rc as any).widget.pageCount === 3, rc.ok ? '' : 'rejected')
  check('a counted document stays pdf', rc.ok && (rc as any).widget.type === 'pdf')

  // A ```search fence carries `results`, not `items` — the probe watched the
  // live server send it and the validator call the payload empty.
  const results = '```search\n{"results":[{"title":"Eiffel Tower","url":"https://en.wikipedia.org/wiki/Eiffel","domain":"en.wikipedia.org","snippet":"330 m tall."}]}\n```'
  const rr = validateWidget('media', extractWidgetFences(results)[0]!.json)
  check('search results are items', rr.ok && (rr as any).widget.items.length === 1 && (rr as any).widget.items[0].domain === 'en.wikipedia.org', rr.ok ? '' : 'rejected')
  check('search results keep the search type', rr.ok && (rr as any).widget.type === 'search')

  // A ```gantt fence whose payload is a schedule, not a series: it must retry
  // as a timeline instead of vanishing (probe 2026-10-20).
  const gantt = '```gantt\n{"title":"Project Schedule","entries":[{"date":"2026-10-20","title":"Task 1","detail":"Research"},{"date":"2026-10-27","title":"Task 2","detail":"Development"}]}\n```'
  const cg = compileWidgets({ reply: gantt, userPrompt: 'a project schedule' })
  check('a schedule in a gantt fence becomes a timeline', cg.widgets === 1 && cg.errors.length === 0, cg.errors.join(','))
  check('the retry rewrites the fence to timeline', cg.text.startsWith('```timeline'), cg.text.slice(0, 30))

  // …and a ```gantt fence holding MERMAID's gantt syntax (the natural-language
  // probe watched the live model do exactly this) is a diagram, not a widget:
  // it must survive untouched — stripping it destroyed a renderable chart.
  const mermaidGantt = 'Here is the plan.\n\n```gantt\ntitle Product Launch Plan\ndateFormat  YYYY-MM-DD\nsection Planning\nMarket Research :a1, 2024-06-03, 7d\n```\n\nDone.'
  const cm = compileWidgets({ reply: mermaidGantt, userPrompt: 'gantt chart for a launch plan' })
  check('mermaid gantt syntax survives the compiler', cm.text.includes('```gantt\n') && cm.text.includes('Market Research'), cm.text.slice(0, 120))
  check('mermaid gantt is not an error', cm.errors.length === 0, cm.errors.join(','))

  // …and a gantt payload that encodes ONE RANGE PER SERIES (Task 1 = [0,2])
  // is canonical gantt: one point per series must be enough.
  const rangeGantt = '```gantt\n{"title":"Project Schedule","type":"gantt","labels":["Week 1","Week 2","Week 3","Week 4","Week 5","Week 6"],"series":[{"name":"Task 1","data":[[0,2]]},{"name":"Task 2","data":[[2,4]]},{"name":"Task 3","data":[[4,6]]}]}\n```'
  const cr = compileWidgets({ reply: rangeGantt, userPrompt: 'gantt chart' })
  check('one range per series is a gantt', cr.widgets === 1 && cr.errors.length === 0, cr.errors.join(','))
}

// 26. THE TYPO CASE (probe 2026-10-20). The live model sent a calendar payload
// whose last string never closed: "subtitle":"All day]}. Truncation and typos
// are one failure to the parser — something was opened and never closed.
{
  const typo = '```calendar\n{"title":"Next Week","month":"2025-06-16","items":[{"title":"Team Meeting","date":"2025-06-17","subtitle":"10:00 AM - 11:30 AM"},{"title":"Project Deadline","date":"2025-06-20","subtitle":"All day]}\n```'
  const c = compileWidgets({ reply: typo, userPrompt: 'next week calendar' })
  check('an unterminated string is closed', c.widgets === 1 && c.errors.length === 0, c.errors.join(','))
  check('the repaired calendar keeps both events', (c.text.match(/Team Meeting|Project Deadline/g) ?? []).length === 2, c.text.slice(0, 200))
  check('repairJson handles truncation', repairJson('{"title":"Memo","pages":[{"page":1') !== null)
  check('repairJson still rejects garbage', repairJson('hello world') === null)
}

// 27. Art direction is GUARANTEED, not requested. Three widgets the model left
// unstyled still get three different faces, and no two adjacent blocks share an
// accent or a surface. This is the "same card background" regression guard.
{
  const chart = '```chart\n' + JSON.stringify({
    type: 'bar',
    title: 'Revenue by quarter',
    labels: ['Q1', 'Q2'],
    series: [{ name: 'Revenue', data: [[0, 120], [1, 180]] }],
  }) + '\n```'
  const table = '```table\n' + JSON.stringify({
    title: 'Costs',
    columns: ['Item', 'Amount'],
    rows: [['Hosting', '120']],
  }) + '\n```'
  const timeline = '```timeline\n' + JSON.stringify({
    title: 'Launch plan',
    entries: [{ date: '2026-11-02', title: 'Ship' }],
  }) + '\n```'
  const reply = `Revenue first.\n\n${chart}\n\nThen costs.\n\n${table}\n\nAnd the plan.\n\n${timeline}`
  const r = compileWidgets({ reply, userPrompt: 'revenue and costs' })
  const payloads = extractWidgetFences(r.text).map((f) => JSON.parse(f.json) as Record<string, unknown>)
  check('three unstyled widgets compile', r.widgets === 3 && payloads.length === 3, `${r.widgets} ${r.errors.join(',')}`)
  check('every widget gets an accent', payloads.every((p) => /^#[0-9a-f]{6}$/.test(String(p.accent))), payloads.map((p) => String(p.accent)).join(','))
  check('every widget gets a surface', payloads.every((p) => (SURFACES as readonly string[]).includes(String(p.variant))), payloads.map((p) => String(p.variant)).join(','))
  check('adjacent accents differ', payloads[0].accent !== payloads[1].accent && payloads[1].accent !== payloads[2].accent, payloads.map((p) => String(p.accent)).join(','))
  check('adjacent surfaces differ', payloads[0].variant !== payloads[1].variant && payloads[1].variant !== payloads[2].variant, payloads.map((p) => String(p.variant)).join(','))
  check('all three accents are distinct', new Set(payloads.map((p) => p.accent)).size === 3, payloads.map((p) => String(p.accent)).join(','))
  check('all three surfaces are distinct', new Set(payloads.map((p) => p.variant)).size === 3, payloads.map((p) => String(p.variant)).join(','))
}

// 28. A deliberate model choice wins — the compiler only breaks a tie it can
// see, it does not overrule the model's taste.
{
  const reply = '```stat\n' + JSON.stringify({
    title: 'Churn',
    accent: '#E03131',
    variant: 'hero',
    cards: [{ label: 'Churn', value: '4.2%' }],
  }) + '\n```'
  const r = compileWidgets({ reply, userPrompt: 'churn' })
  const payload = JSON.parse(extractWidgetFences(r.text)[0].json) as Record<string, unknown>
  check('model accent survives', payload.accent === '#e03131', String(payload.accent))
  check('model surface survives', payload.variant === 'hero', String(payload.variant))
}

// 29. Determinism: the same reply always compiles to the same colours, so a
// replay, a cache or a test never sees a different design.
{
  const reply = '```chart\n{"type":"bar","title":"Revenue","series":[{"name":"R","data":[[0,1],[1,2]]}]}\n```'
  const a = compileWidgets({ reply, userPrompt: 'chart' })
  const b = compileWidgets({ reply, userPrompt: 'chart' })
  check('art direction is deterministic', a.text === b.text)
}

// 30. Subject fit: the derived hue reads the content, not a fixed slot.
{
  const money = directWidgetArt({ kind: 'chart', payload: { type: 'bar', title: 'Revenue and profit' } })
  const risk = directWidgetArt({ kind: 'chart', payload: { type: 'bar', title: 'Customer churn and incidents' } })
  check('money gets a money hue', ['#e8590c', '#f08c00', '#66a80f'].includes(money.accent), money.accent)
  check('risk gets a risk hue', ['#e03131', '#d6336c', '#e8590c'].includes(risk.accent), risk.accent)
}

// 31. A new turn does not repeat the previous turn's derived palette.
{
  const prior = '```chart\n{"accent":"#E8590C","type":"bar"}\n```\n```chart\n{"accent":"#F08C00","type":"bar"}\n```'
  const direction = directWidgetArt({ kind: 'chart', payload: { type: 'bar', title: 'Revenue' }, priorText: prior })
  check('derived accent avoids the previous turn', !['#e8590c', '#f08c00'].includes(direction.accent), direction.accent)
}

// 32. When the previous turn burnt every hue in the subject family, the
// fallback widens to the rest of the palette instead of replaying one of them.
// Three money widgets in one answer can exhaust tangerine/amber/olive alone.
{
  const prior = [
    '```chart\n{"accent":"#E8590C","type":"bar"}\n```',
    '```chart\n{"accent":"#F08C00","type":"bar"}\n```',
    '```chart\n{"accent":"#66A80F","type":"bar"}\n```',
  ].join('\n')
  const direction = directWidgetArt({ kind: 'chart', payload: { type: 'bar', title: 'Revenue' }, priorText: prior })
  check(
    'an exhausted subject family widens the palette',
    !['#e8590c', '#f08c00', '#66a80f'].includes(direction.accent),
    direction.accent
  )
}

// 33. The validator carries the new surfaces and rejects invented ones, so a
// typo never reaches a renderer that would silently ignore it.
{
  const withEditorial = validateWidget('chart', '{"type":"bar","title":"T","labels":["a","b"],"series":[{"name":"v","data":[[0,1],[1,2]]}],"variant":"editorial"}')
  check('new surface survives validation', withEditorial.ok && withEditorial.widget.variant === 'editorial')
  const withGlass = validateWidget('chart', '{"type":"bar","title":"T","labels":["a","b"],"series":[{"name":"v","data":[[0,1],[1,2]]}],"variant":"glass"}')
  check('unknown surface is dropped', withGlass.ok && withGlass.widget.variant === undefined)
}

// 34. v3 — the six new chart families validate through the graph-payload path.
{
  const v3charts: Array<[string, string]> = [
    ['force', '{"type":"force","nodes":["a","b","c"],"links":[{"source":"a","target":"b","value":2},{"source":"b","target":"c","value":1}]}'],
    ['depth', '{"type":"depth","bids":[[100,5],[99,8]],"asks":[[101,4],[102,6]]}'],
    ['violin', '{"type":"violin","series":[{"name":"a","data":[[0,1],[1,2],[2,1.2],[3,0.8]]},{"name":"b","data":[[0,2],[1,1],[2,2.4],[3,1.9]]}]}'],
    ['contour', '{"type":"contour","points":[[0,0,1],[1,0,2],[2,0,1],[0,1,2],[1,1,4],[2,1,2],[0,2,1],[1,2,2],[2,2,1]],"levels":6}'],
    ['parallel', '{"type":"parallel","axes":["speed","cost","range"],"series":[{"name":"A","values":[8,3,9]},{"name":"B","values":[5,6,4]}]}'],
    ['dendrogram', '{"type":"dendrogram","root":{"label":"all","distance":0,"children":[{"label":"x","distance":2},{"label":"y","distance":1,"children":[{"label":"y1","distance":0.5},{"label":"y2","distance":0.5}]},{"label":"z","distance":1.5}]}}'],
  ]
  for (const [type, json] of v3charts) {
    const r = validateWidget('chart', json)
    const okType = r.ok && r.widget.type === type
    check(`v3 chart ${type} validates`, okType, r.ok ? `type=${String(r.widget.type)}` : validationErrors(r).join('|'))
  }
  const badDepth = validateWidget('chart', '{"type":"depth","bids":"nope"}')
  check('v3 chart junk is rejected', !badDepth.ok)
}

// 35. v3 — sub-type fences canonicalise to their parent kind on the wire.
{
  const molecule = extractWidgetFences('```molecule\n{"nodes":[{"label":"O","x":0,"y":0,"z":0}]}\n```')
  check('molecule fence -> stage3d kind', molecule[0]?.kind === 'stage3d', JSON.stringify(molecule[0]?.kind))
  const cloth = extractWidgetFences('```cloth\n{"title":"silk"}\n```')
  check('cloth fence -> sim kind', cloth[0]?.kind === 'sim', JSON.stringify(cloth[0]?.kind))
  const pivot = extractWidgetFences('```pivot\n{"rows":[["a","b"],["x","1"]]}\n```')
  check('pivot fence -> editor kind', pivot[0]?.kind === 'editor', JSON.stringify(pivot[0]?.kind))
  const depthFence = extractWidgetFences('```depth\n{"bids":[[100,5]],"asks":[[101,4]]}\n```')
  check('depth fence -> chart kind with type', depthFence[0]?.kind === 'chart', JSON.stringify(depthFence[0]?.kind))
  const depthValid = depthFence[0] ? validateWidget(depthFence[0].kind, depthFence[0].json) : null
  check('depth fence payload validates with injected type', !!depthValid && depthValid.ok && depthValid.widget.type === 'depth')
  // unknown sub-type inside a v3 kind falls back to the default surface
  const badSim = validateWidget('sim', '{"sim":"blackhole"}')
  check('unknown sim degrades to particles', badSim.ok && badSim.widget.sim === 'particles' && badSim.repaired === true)
}

// 36. v3 — knobs clamp and defaults fill in for the sim family.
{
  const r = validateWidget('sim', '{"sim":"game","gravity":999,"count":100000,"speed":50}')
  const w = r.ok ? r.widget : null
  check('sim knobs clamp', !!w && w.gravity !== 999 && w.count !== 100000, JSON.stringify(w))
  const stage = validateWidget('stage3d', '{"view":"surface3d","z":"sin(x)*cos(y)","nodes":[{"x":1,"y":2,"z":3}]}')
  const sw = stage.ok ? stage.widget : null
  check('surface3d keeps whitelisted z expr', !!sw && sw.z === 'sin(x)*cos(y)')
  const evil = validateWidget('stage3d', '{"view":"surface3d","z":"fetch(window)","nodes":[]}')
  check('stage3d z rejects non-math tokens in the validator', !!evil && evil.ok && evil.widget.z === undefined && evil.repaired === true, JSON.stringify(evil.ok ? evil.widget.z : null))
  const editor = validateWidget('editor', '{"editor":"video","duration":9999,"tracks":[{"name":"A","clips":[{"start":0,"dur":8,"label":"x"}]}]}')
  check('editor timeline clamps duration', editor.ok && (editor.widget as Record<string, unknown>).duration !== 9999)
}

// 37. v3.1 — collab: shared-state kind validates, slugs, clamps, repairs.
{
  const vote = validateWidget('collab', '{"surface":"vote","room":"Launch Day!!","items":["a","b"],"junk":1}')
  const w = vote.ok ? vote.widget : null
  check('collab vote validates with slugified room', !!w && w.room === 'launch-day' && w.surface === 'vote')
  const junk = validateWidget('collab', '{"surface":"whiteboard","items":"x"}')
  check('collab junk surface repairs to counter', junk.ok && junk.widget.surface === 'counter' && junk.repaired === true)
  const clamped = validateWidget('collab', '{"surface":"todo","room":"r","items":' + JSON.stringify(Array.from({ length: 20 }, (_, i) => `i${i}`)) + '}')
  check('collab items clamp to 12', clamped.ok && (clamped.widget.items as unknown[]).length === 12)
  const counterFence = extractWidgetFences('```counter\n{"room":"taps"}\n```')
  check('counter fence -> collab kind', counterFence[0]?.kind === 'collab', JSON.stringify(counterFence[0]?.kind))
  const pointerFence = extractWidgetFences('```pointer\n{}\n```')
  check('pointer fence -> collab kind with default room', pointerFence[0]?.kind === 'collab')
}

console.log(`\n${passed} passed, ${failures.length} failed`)
if (failures.length) {
  for (const f of failures) console.log(`  - ${f}`)
  process.exit(1)
}
