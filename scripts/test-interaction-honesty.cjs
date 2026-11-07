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
const { sanitizeReplyEmojis } = from('lib/nexly/icons')
const { javascriptSandboxDocument } = from('lib/code/javascript-sandbox')
const { MessageContent } = from('components/genui/MessageContent')
const { SpontaneousConceptDeck } = from('components/genui/SpontaneousConceptDeck')
const { CalcWidget } = from('components/genui/widgets/CalcWidget')
const { CodeBlock } = from('components/genui/blocks/CodeBlock')
const { chartRange } = from('lib/nexly/chart-range')
const { TableWidget } = from('components/genui/widgets/TableWidget')
const { TitleRow } = from('components/genui/primitives/TitleRow')
const h = React.createElement
const text = '- **Mitosis**: A cell divides into two genetically identical cells.\n- **Meiosis**: A cell divides to produce four genetically distinct cells.\n- **Osmosis**: Water moves through a selectively permeable membrane.'
const concepts = extractConceptDeck(text)
let checks = 0
function check(name, fn) { fn(); checks++; console.log('PASS', name) }

async function main() {
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
  await mount(h(CodeBlock, { language: 'sql', code: 'SELECT * FROM private_accounts' }))
  await click(buttons().find((b) => b.textContent.includes('Run Code')))
  check('SQL button never fabricates a result', () => { assert.match(document.body.textContent, /no database is connected/); assert.doesNotMatch(document.body.textContent, /Ava Chen|3 rows returned/) })
  await act(async () => root.unmount())
  console.log(`\n${checks} interaction-honesty checks passed`)
}
main().catch((error) => { console.error(error); process.exitCode = 1 })
