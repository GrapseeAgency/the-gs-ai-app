/* Compile first: npx tsc -p tsconfig.probe.json --resolveJsonModule --incremental false
 * Then: node scripts/test-interaction-honesty.cjs */
const assert = require('node:assert/strict')
const vm = require('node:vm')
const { JSDOM } = require('jsdom')
const dom = new JSDOM('<!doctype html><div id="root"></div>', { url: 'https://app.example/' })
global.window = dom.window
global.document = dom.window.document
global.HTMLElement = dom.window.HTMLElement
global.IS_REACT_ACT_ENVIRONMENT = true
require('../dist/probe/scripts/probe-alias-hook')
const React = require('react')
const { act } = React
const { createRoot } = require('react-dom/client')
const { renderToString } = require('react-dom/server')
const from = (p) => require('../dist/probe/src/' + p)
const { arithmeticPreview } = from('lib/code/arithmetic-preview')
const { evaluateExpr, formatOutput } = from('lib/calc/evaluate')
const { extractConceptDeck, conceptQuestions } = from('lib/nexly/concept-deck')
const { sanitizeWidgetPresentation } = from('lib/nexly/widget-presentation')
const { WidgetRenderer } = from('components/genui/WidgetRenderer')
const { WIDGET_PROMPT } = from('lib/widgets/prompt')
const { sanitizeReplyEmojis } = from('lib/nexly/icons')
const { javascriptSandboxDocument } = from('lib/code/javascript-sandbox')
const { MessageContent } = from('components/genui/MessageContent')
const { SpontaneousConceptDeck } = from('components/genui/SpontaneousConceptDeck')
const { CalcWidget } = from('components/genui/widgets/CalcWidget')
const { CodeBlock } = from('components/genui/blocks/CodeBlock')
const { chartRange } = from('lib/nexly/chart-range')
const { ChecklistWidget } = from('components/genui/widgets/ChecklistWidget')
const { TableWidget } = from('components/genui/widgets/TableWidget')
const { TitleRow } = from('components/genui/primitives/TitleRow')
const { HtmlBlock } = from('components/genui/blocks/HtmlBlock')
const { ChartWidget } = from('components/genui/widgets/ChartWidget')
const { PollWidget } = from('components/genui/widgets/PollWidget')
const { pieData, PieChartView } = from('components/genui/charts/PieChart')
const h = React.createElement
const text = '- **Mitosis**: A cell divides into two genetically identical cells.\n- **Meiosis**: A cell divides to produce four genetically distinct cells.\n- **Osmosis**: Water moves through a selectively permeable membrane.'
const concepts = extractConceptDeck(text)
let checks = 0
function check(name, fn) { fn(); checks++; console.log('PASS', name) }

async function main() {
  // Execute the exact iframe srcDoc, including the shared policy, not a mock
  // sanitizer. JSDOM exercises DOM events/MutationObserver, not pixel layout.
  const source = `<button id="run" title="Increase &#x1F600;">Increase &#x1F600;</button><output id="result">0</output><svg data-custom-glass="true"><path d="M0 0L8 8"/></svg><script>window.marker=String.fromCodePoint(0x1F600);window.count=0;document.getElementById('run').onclick=function(){window.count++;document.getElementById('result').textContent=window.count+window.marker;};</script>`
  const frameHTML = renderToString(h(HtmlBlock, { source }))
  const wrapper = new JSDOM(frameHTML)
  const previewDocument = wrapper.window.document.querySelector('iframe').getAttribute('srcDoc')
  const preview = new JSDOM(previewDocument, { runScripts: 'dangerously' })
  await new Promise(resolve => preview.window.setTimeout(resolve, 0))
  check('HTML entity emoji do not bypass the actual preview DOM', () => {
    assert.equal(preview.window.document.getElementById('run').textContent, 'Increase ')
    assert.equal(preview.window.document.getElementById('run').getAttribute('title'), 'Increase ')
  })
  check('DOM display policy preserves machine state and custom vector glyphs', () => {
    assert.equal(preview.window.marker.codePointAt(0), 0x1F600)
    assert.ok(preview.window.document.querySelector('svg[data-custom-glass] path'))
  })
  preview.window.document.getElementById('run').click()
  await new Promise(resolve => preview.window.setTimeout(resolve, 0))
  check('actual HTML button runs its own logic without rendering dynamic emoji', () => {
    assert.equal(preview.window.count, 1)
    assert.equal(preview.window.document.getElementById('result').textContent, '1')
  })
  preview.window.document.getElementById('result').firstChild.nodeValue = '2' + preview.window.marker
  preview.window.document.getElementById('run').setAttribute('aria-label', 'Increase ' + preview.window.marker)
  await new Promise(resolve => preview.window.setTimeout(resolve, 0))
  check('late character and accessibility label mutations use the same policy', () => {
    assert.equal(preview.window.document.getElementById('result').textContent, '2')
    assert.equal(preview.window.document.getElementById('run').getAttribute('aria-label'), 'Increase ')
  })
  preview.window.close(); wrapper.window.close()

  check('real arithmetic uses the actual inputs', () => assert.match(arithmeticPreview('price = 240\ntax = 0.15\nprint(price * (1 + tax))').output, /276/))
  for (const source of ['import pandas as pd\nprint(42)', 'for i in range(3):\n print(i)', 'print(missing)', 'print(1 / 0)', 'print(2 3)', 'x = 1\nunknown(x)', 'print(sum([1, nope, 3]))', 'print(3 // 2)', 'print("a" + "b")']) {
    check('unsupported preview fails closed: ' + source.split('\n')[0], () => {
      const result = arithmeticPreview(source); assert.ok(result.error); assert.equal(result.output, '')
    })
  }
  for (const [expr, expected] of [['-2**2', -4], ['2**3**2', 512], ['2 ** -2', .25], ['3 * 2 ** 3', 24], ['(2+3)*4', 20]]) {
    check('precedence: ' + expr, () => assert.equal(evaluateExpr(expr, {}), expected))
  }
  check('non-finite output is not fabricated zero', () => assert.equal(formatOutput(Infinity), '—'))
  check('no quiz facts invented', () => {
    assert.equal(concepts.length, 3)
    conceptQuestions(concepts).forEach((q, i) => assert.equal(q.options[q.answer], concepts[i].term))
  })
  for (const invalid of ['- [ ] Book a trip\n- [ ] Buy tickets', 'Hello there', '```python\n'+text+'\n```', '- X: short\n- X: short\n- X: short']) {
    check('no irrelevant spontaneous deck', () => assert.equal(extractConceptDeck(invalid).length, 0))
  }
  const emoji = '😀 🫠 👩🏽‍💻 🇧🇩 1️⃣ ⚠️ ❤️ 🚀 🪿 🫩'
  check('emoji are removed at normalization boundary', () => assert.doesNotMatch(sanitizeReplyEmojis(emoji), /\p{Extended_Pictographic}|[\u{1F1E6}-\u{1F1FF}]|\uFE0F|\u200D|\u20E3/u))
  check('ordinary rendered output has glass glyphs, not emoji', () => {
    const html = renderToString(h(MessageContent, { text: emoji, dark: false }))
    assert.doesNotMatch(html, /\p{Extended_Pictographic}/u); assert.match(html, /nx-glyph/)
  })
  check('widget titles render icon tokens, not literal tokens', () => {
    const html = renderToString(h(TitleRow, { title: ':rocket: Launch' }))
    assert.match(html, /nx-glyph/); assert.doesNotMatch(html, /:rocket:/)
  })
  check('automatic tools wait for finalized text', () => {
    assert.doesNotMatch(renderToString(h(MessageContent, { text, streaming: true })), /Practice concepts from this answer/)
    assert.match(renderToString(h(MessageContent, { text })), /Practice concepts from this answer/)
  })
  for (const [value, expected] of [[.99, '99%'], [1, '100%'], [1.01, '101%'], [-1.2, '-120%']]) {
    check('percentage ratios do not change units at ' + value, () => assert.equal(formatOutput(value, 'percent'), expected))
  }
  check('explicit percentage points are not rescaled', () => assert.equal(formatOutput(.99, 'number', '%'), '0.99 %'))
  check('prompt has one calculator schema and never requires padded datasets', () => {
    assert.doesNotMatch(WIDGET_PROMPT, /MUST include mathematically real `inputs`|at least 5–8 real/)
    assert.match(WIDGET_PROMPT, /canonical `fields` array/)
    assert.match(WIDGET_PROMPT, /Never\s+pad a dataset/)
    assert.match(WIDGET_PROMPT, /Never invent limits to decorate a slider/)
  })
  check('prompt calculator defines every variable it uses', () => {
    const sample = JSON.parse(WIDGET_PROMPT.match(/^- calc: (\{.*?\}) \(Schema example/m)[1])
    const vars = Object.fromEntries(sample.fields.map(f => [f.key, f.value]))
    assert.ok(Math.abs(evaluateExpr(sample.outputs[0].expr, vars) - 4072.236566943605) < 1e-8)
  })
  check('decoded Unicode escapes cannot leak through widget labels', () => {
    const source = JSON.parse(String.raw`{"title":"\ud83d\ude80 Calculator","icon":"\ud83d\ude80","fields":[{"key":"p","label":"Amount \ud83d\ude00 \u00a9\ufe0f","value":100}],"outputs":[{"label":"Result \ud83d\ude80","expr":"p*2"}],"rows":[["\ud83d\ude00"]],"code":"print(\"\ud83d\ude00\")","url":"https://example.org/\ud83d\ude00","source":{"title":"\ud83d\ude00 Reference"}}`)
    const safe = sanitizeWidgetPresentation(source)
    assert.equal(safe.code, source.code); assert.equal(safe.url, source.url)
    assert.equal(safe.fields[0].key, 'p'); assert.equal(safe.outputs[0].expr, 'p*2')
    assert.equal(safe.icon, 'rocket'); assert.equal(source.icon, '🚀')
    assert.doesNotMatch(safe.source.title, /\p{Extended_Pictographic}/u)
    const html = renderToString(h(WidgetRenderer, { kind: 'calc', payload: source, dark: false }))
    assert.doesNotMatch(html, /\p{Extended_Pictographic}/u)
    assert.match(html, /nx-glyph/)
  })
  // Execute the EXACT generated worker source. This is runtime validation,
  // not matching a prewritten expected log from the source text.
  const doc = javascriptSandboxDocument('irrelevant')
  const workerSource = JSON.parse(doc.match(/new Blob\(\[("(?:[^"\\]|\\.)*")\]/)[1])
  let messages = []
  const self = { postMessage: (m) => messages.push(m) }
  vm.runInNewContext(workerSource, { self, setTimeout, clearTimeout })
  await self.onmessage({ data: 'console.log(7*9); await new Promise(r=>setTimeout(r, 15)); console.log("later");' })
  check('JavaScript runs arithmetic and real awaited timers', () => assert.deepEqual(messages.map((m) => m.value), ['63', 'later', 'Completed. Unawaited background work is stopped.']))
  messages = []
  await self.onmessage({ data: 'throw new Error("actual failure")' })
  check('JavaScript errors are actual exceptions', () => assert.match(messages[0].value, /actual failure/))
  check('worker source escapes script terminators', () => assert.ok(!javascriptSandboxDocument('</script><img src=x>').includes('</script><img')))
  check('worker network is forbidden and execution is bounded', () => { assert.match(doc, /connect-src 'none'/); assert.match(doc, /worker\.terminate\(\)/); assert.match(doc, /2000/) })

  const calendarLabels = ['2025-11', '2026-01', '2026-04', '2026-06', '2026-11']
  const clock = new Date('2026-06-15T12:00:00Z')
  check('Last 3M uses calendar timestamps, excludes future points', () => assert.deepEqual(chartRange(calendarLabels, 5, 0, clock).indices, [2, 3]))
  check('YTD starts at January of the current year', () => assert.deepEqual(chartRange(calendarLabels, 5, 2, clock).indices, [1, 2, 3]))
  check('categories never claim to be months', () => assert.equal(chartRange(['A','B','C','D'], 4, 0, clock).options[0], 'Last 3 points'))
  check('invalid dates never acquire calendar semantics', () => assert.equal(chartRange(['2026-02-31'], 1, 0, clock).options[0], 'Last 3 points'))
  const root = createRoot(document.getElementById('root'))
  async function mount(element) { await act(async () => root.render(element)) }
  async function click(button) { assert.ok(button, 'button exists'); await act(async () => button.dispatchEvent(new window.MouseEvent('click', { bubbles: true }))) }
  const buttons = () => [...document.querySelectorAll('button')]
  check('pie switching retains all observations and their source labels', () => {
    const data = pieData({ type: 'pie', labels: ['Jan', 'Feb', 'Mar'], series: [{ name: 'Actual', data: [[0, 10], [1, 20], [2, 30]] }] })
    assert.deepEqual(data, [{ name: 'Jan', value: 10 }, { name: 'Feb', value: 20 }, { name: 'Mar', value: 30 }])
    assert.deepEqual(pieData({ type: 'pie', series: [{ name: 'A', data: [[0, 10]] }, { name: 'B', data: [[0, 20]] }] }).map(p => p.value), [10, 20])
  })
  check('pie does not silently discard negative or zero-total source data', () => {
    for (const values of [[-10, 20], [0, 0]]) {
      const html = renderToString(h(PieChartView, { payload: { type: 'pie', series: [{ name: 'Actual', data: values.map((value, i) => [i, value]) }] } }))
      assert.match(html, /positive total/)
    }
  })
  await mount(h(PollWidget, { payload: { title: 'Preference', options: [{ label: 'A' }, { label: 'B' }] } }))
  check('a new poll has zero real local votes, not a fabricated denominator vote', () => {
    assert.match(document.body.textContent, /0 local votes/)
    assert.match(document.body.textContent, /not a live audience vote/)
  })
  await click(buttons().find(button => button.textContent.trim() === 'A'))
  check('a poll click adds one actual local vote and computes its percentage', () => {
    assert.match(document.body.textContent, /1 local vote/)
    assert.match(document.body.textContent, /100%/)
  })
  await click(buttons().find(button => button.textContent.includes('B')))
  check('a repeated vote cannot inflate a local poll', () => {
    assert.match(document.body.textContent, /1 local vote/)
    assert.doesNotMatch(document.body.textContent, /2 local votes/)
  })
  await mount(h(PollWidget, { payload: { title: 'Different preference', options: [{ label: 'C' }, { label: 'D' }] } }))
  check('changed poll options reset the previous poll selection and tally', () => {
    assert.match(document.body.textContent, /0 local votes/)
    assert.doesNotMatch(document.body.textContent, /100%/)
  })
  // Chart metrics/controls are tested in a DOM. ResizeObserver is a layout
  // platform stub; these assertions do not claim a rasterized chart test.
  global.ResizeObserver = class { observe() {} disconnect() {} unobserve() {} }
  await mount(h(ChartWidget, { payload: { type: 'line', series: [{ name: 'Zero baseline', data: [[0,0],[1,10]] }] } }))
  check('undefined chart growth has no invented downward trend arrow', () => {
    assert.match(document.body.textContent, /N\/A \(zero baseline\)/)
    assert.doesNotMatch(document.body.textContent, /↓\s*N\/A/)
  })
  await click(buttons().find(button => button.textContent.includes('Pie Chart')))
  check('pie category comparisons do not masquerade as a growth rate', () => {
    assert.match(document.body.textContent, /N\/A \(slice values\)/)
    assert.doesNotMatch(document.body.textContent, /zero baseline/)
  })
  await mount(h(ChartWidget, { payload: { type: 'line', series: [
    { name: 'Short', data: [[0,10],[1,20]] },
    { name: 'Long', data: [[0,1],[1,2],[2,3],[3,4],[4,5],[5,6]] }
  ] } }))
  check('multi-series chart metrics explicitly identify their actual scope', () => {
    assert.match(document.body.textContent, /Summary: Short/)
    assert.match(document.body.textContent, /sum of observations is 30/)
    assert.doesNotMatch(document.body.textContent, /cumulative volume/)
  })
  await click(buttons().find(b => b.textContent.includes('All')))
  check('range uses longest series and does not restore short out-of-window history', () => {
    assert.match(document.body.textContent, /Last 3 points/)
    assert.doesNotMatch(document.body.textContent, /sum of observations|zero baseline/)
  })
  await mount(h(SpontaneousConceptDeck, { concepts, dark: false }))
  await click(buttons().find((b) => b.textContent.includes('tap to flip')))
  check('flashcard flip reveals actual definition', () => assert.ok(document.body.textContent.includes(concepts[0].definition)))
  await click(buttons().find((b) => b.textContent.includes('Try a quick quiz')))
  await click(buttons().find((b) => b.textContent === concepts[0].term))
  check('correct quiz answer updates score', () => assert.match(document.body.textContent, /1\/3/))
  await click(buttons().find((b) => b.textContent.includes('Switch to flashcards')))
  check('mode switch returns to cards', () => assert.match(document.body.textContent, /Flashcards · from this answer/))
  await mount(h(CalcWidget, { payload: { fields: [{ key: 'amount', label: 'Amount', value: 100, min: 0, max: 1000, step: 1 }], outputs: [{ label: 'Double', expr: 'amount*2' }], presets: [{ label: 'Use 250', values: { amount: 250 } }] } }))
  check('calculator initial value is computed', () => assert.match(document.body.textContent, /200/))
  await click(buttons().find((b) => b.textContent === 'Use 250'))
  check('calculator preset recomputes from state', () => assert.match(document.body.textContent, /500/))
  await click(buttons().find((b) => b.textContent === 'Reset'))
  check('calculator reset restores real inputs', () => assert.match(document.body.textContent, /200/))
  await typeIn(document.querySelector('input[type="number"]'), '12.3456')
  check('typed fractional input computes without forced integer stepping', () => assert.match(document.body.textContent, /24\.6912/))
  await typeIn(document.querySelector('input[type="number"]'), '')
  check('cleared input is invalid rather than invented zero', () => assert.match(document.body.textContent, /Enter valid inputs/))
  await click(buttons().find(b => b.textContent === 'Reset'))
  check('reset restores a cleared input', () => assert.equal(document.querySelector('input[type="number"]').value, '100'))
  await mount(h(CalcWidget, { payload: { fields: [{ key: 'amount', label: 'Amount', value: 1200 }], outputs: [{ label: 'Double', expr: 'amount*2' }] } }))
  check('new payload resets stale state', () => assert.match(document.body.textContent, /2,400/))
  check('no invented 0-100 slider for unbounded source input', () => assert.equal(document.querySelectorAll('input[type="range"]').length, 0))
  await mount(h(CalcWidget, { payload: { fields: [], outputs: [] } }))
  check('empty payload changes do not violate React hook order', () => assert.match(document.body.textContent, /misconfigured/))
  await mount(h(TableWidget, { payload: { columns: ['Name','Amount'], rows: Array.from({length: 31}, (_, i) => ['Item '+(i+1), String(31-i)]) } }))
  check('table genuinely paginates', () => assert.equal(document.querySelectorAll('tbody tr').length, 5))
  await click(document.querySelectorAll('th')[1])
  check('table numeric sorting uses actual values', () => assert.equal(document.querySelector('tbody tr td:last-child').textContent, '1'))
  await click(buttons().find((b) => b.textContent === '›'))
  check('next page reveals the next actual rows', () => assert.equal(document.querySelector('tbody tr td:last-child').textContent, '6'))
  await click(buttons().find((b) => b.textContent.includes('Show:')))
  check('page-size change changes the visible row count', () => assert.equal(document.querySelectorAll('tbody tr').length, 10))
  await click(buttons().find((b) => b.textContent.includes('Show:')))
  check('All displays more than 25 rows', () => assert.equal(document.querySelectorAll('tbody tr').length, 31))
  const filter = document.querySelector('input[placeholder="Filter rows…"]')
  await act(async () => {
    Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set.call(filter, 'Item 31')
    filter.dispatchEvent(new window.Event('input', { bubbles: true }))
  })
  check('filter actually changes the dataset', () => assert.equal(document.querySelectorAll('tbody tr').length, 1))
  async function typeIn(input, value) {
    await act(async () => {
      Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set.call(input, value)
      input.dispatchEvent(new window.Event('input', { bubbles: true }))
    })
  }
  await mount(h(ChecklistWidget, { payload: { title: 'My tasks', items: [] } }))
  check('empty checklist remains usable', () => assert.ok(document.querySelector('input[aria-label="New task"]')))
  await typeIn(document.querySelector('input[aria-label="New task"]'), 'Book actual tickets')
  await click(buttons().find((b) => b.textContent === 'Add Task'))
  check('add task uses the typed text', () => assert.match(document.body.textContent, /Book actual tickets/))
  await click(document.querySelector('[role="checkbox"]'))
  check('task completion updates real progress', () => assert.equal(document.querySelector('[role="progressbar"]').getAttribute('aria-valuenow'), '100'))
  await click(document.querySelector('button[aria-label="Edit Book actual tickets"]'))
  await typeIn(document.querySelector('input[aria-label="Edit task"]'), 'Confirm train tickets')
  await click(buttons().find((b) => b.textContent === 'Save'))
  check('task edit saves the actual new label', () => { assert.match(document.body.textContent, /Confirm train tickets/); assert.doesNotMatch(document.body.textContent, /Book actual tickets/) })
  await click(document.querySelector('button[aria-label="Delete Confirm train tickets"]'))
  check('delete removes the task and resets progress', () => { assert.equal(document.querySelectorAll('[role="checkbox"]').length, 0); assert.equal(document.querySelector('[role="progressbar"]').getAttribute('aria-valuenow'), '0') })
  await mount(h(CodeBlock, { language: 'sql', code: 'SELECT * FROM private_accounts' }))
  await click(buttons().find((b) => b.textContent.includes('Run Code')))
  check('SQL button never fabricates a result', () => { assert.match(document.body.textContent, /no database is connected/); assert.doesNotMatch(document.body.textContent, /Ava Chen|3 rows returned/) })
  await act(async () => root.unmount())
  console.log(`\n${checks} interaction-honesty checks passed`)
}
main().catch((error) => { console.error(error); process.exitCode = 1 })
