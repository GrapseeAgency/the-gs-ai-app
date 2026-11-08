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
const { escapeCodeDisplay, encodeSourceEditor, decodeSourceEditor } = from('lib/code/display-source')
const { ensureWidgets } = from('lib/widgets/compile')
const { parseStream } = from('components/genui/stream')
const { MessageContent, parseProseBlocks } = from('components/genui/MessageContent')
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
  check('learning suggestions exclude real source and action sections and resume at actual concepts', () => {
    for (const heading of ['Sources','Key Sources','References','Step-by-step plan','Next actions','Checklist']) assert.equal(extractConceptDeck('## '+heading+'\n'+text).length, 0, heading)
    assert.equal(extractConceptDeck('## Sources\n- IEA: a supplied publisher record.\n## Core concepts\n'+text).length, 3)
    assert.equal(extractConceptDeck('**Sources**\n'+text).length, 0)
  })
  check('learning retains complete source definitions and never quizzes ambiguous duplicate definitions', () => {
    const definition = 'A supplied definition with an important source qualification. '.repeat(6).trim()
    const source = ['Alpha','Beta','Gamma'].map(term=>`- **${term}**: ${definition} ${term}.`).join('\n')
    assert.equal(extractConceptDeck(source)[0].definition, definition+' Alpha.')
    assert.equal(extractConceptDeck(['Alpha','Beta','Gamma'].map(term=>`- **${term}**: ${definition}`).join('\n')).length, 0)
  })
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
  const { validateWidget } = from('lib/widgets/validate')
  const { compileWidgets } = from('lib/widgets/compile')
  check('model pie payload retains every observation beyond the former series-expansion cap', () => {
    const result = validateWidget('chart', JSON.stringify({ type: 'pie', labels: Array.from({length: 24}, (_, i) => 'P' + i), series: [{ name: 'Actual', data: Array.from({length: 24}, (_, i) => [i, i + 1]) }] }))
    assert.equal(result.ok, true)
    assert.equal(result.widget.series.reduce((count, series) => count + series.data.length, 0), 24)
  })
  check('model chart normalization never slides another dimension into a missing coordinate', () => {
    for (const data of [[[0,null],[1,2]], [[0,1,null],[1,2,3]], [[0,1],{x:1,y:null}]]) {
      assert.equal(validateWidget('chart', JSON.stringify({ type: 'line', series: [{ name: 'Actual', data }] })).ok, false)
    }
  })
  check('model chart normalization preserves every real singleton series and blank category position', () => {
    const result = validateWidget('chart', JSON.stringify({ type: 'line', labels: ['', 'B'], series: Array.from({length: 10}, (_, i) => ({ name: 'S' + i, data: [[0, i]] })) }))
    assert.equal(result.ok, true); assert.equal(result.widget.series.length, 10)
    assert.deepEqual(result.widget.labels, ['', 'B'])
  })
  check('missing named table cells never borrow an unrelated field by object position', () => {
    const result = validateWidget('table', JSON.stringify({ columns: ['Name','Amount'], rows: [{ Name: 'Actual', Unrelated: 42 }] }))
    assert.equal(result.ok, true); assert.deepEqual(result.widget.rows, [['Actual','']])
  })
  check('inferred table columns retain fields introduced after the first row', () => {
    const result = validateWidget('table', JSON.stringify({ rows: [{ Name: 'Actual' }, { Amount: 42 }] }))
    assert.equal(result.ok, true); assert.deepEqual(result.widget.columns, ['Name','Amount'])
    assert.deepEqual(result.widget.rows, [['Actual',''],['','42']])
  })
  check('over-capacity model data is visibly refused with lossless source, never silently truncated', () => {
    const data = { columns: ['Name','Amount'], rows: Array.from({length: 101}, (_, i) => ['Actual ' + (i+1), i+1]) }
    const source = JSON.stringify(data)
    assert.equal(validateWidget('table', source).ok, false)
    const compiled = compileWidgets({ reply: '```table\n' + source + '\n```', userPrompt: 'Make a chart of this dataset' })
    assert.equal(compiled.widgets, 0)
    assert.match(compiled.text, /Original source is preserved/)
    assert.ok(compiled.text.includes(source))
    const code = parseStream(compiled.text).find(segment => segment.kind === 'code')
    assert.equal(code.code, source)
  })
  check('automatic chart generation preserves actual observations and refuses fabricated range midpoints or signed pie', () => {
    const range = compileWidgets({reply:'Alpha: 100–200\nBeta: 300–500', userPrompt:'Make a bar chart'})
    assert.equal(range.widgets, 0)
    assert.ok(range.text.includes('100–200'))
    const many = compileWidgets({reply:Array.from({length:12}, (_,i)=>`Category ${i+1}: ${i+1}`).join('\n'), userPrompt:'Make a bar chart'})
    assert.equal(many.widgets, 1)
    const widget = parseStream(many.text).find(segment => segment.kind === 'widget')
    assert.equal(widget.payload.series[0].data.length, 12)
    const signed = compileWidgets({reply:'Alpha: -5\nBeta: 10', userPrompt:'Make a pie chart'})
    assert.equal(signed.widgets, 0)
    assert.ok(signed.text.includes('Alpha: -5'))
  })
  check('automatic charts retain every pie category and percentage unit without mixing incomparable counts', () => {
    const pie = compileWidgets({reply:Array.from({length:31}, (_,i)=>`Category ${i+1}: ${i+1}`).join('\n'), userPrompt:'Make a pie chart'})
    assert.equal(pie.widgets, 1)
    const widget = parseStream(pie.text).find(segment => segment.kind === 'widget')
    assert.equal(widget.payload.series.length, 1)
    assert.equal(widget.payload.series[0].data.length, 31)
    const percent = compileWidgets({reply:'Alpha: 40%\nBeta: 60%', userPrompt:'Make a bar chart'})
    assert.equal(parseStream(percent.text).find(segment => segment.kind === 'widget').payload.unit, '%')
    const mixed = compileWidgets({reply:'Count: 100\nRate: 10%', userPrompt:'Make a bar chart'})
    assert.equal(mixed.widgets, 0)
    assert.ok(mixed.text.includes('Rate: 10%'))
  })
  check('all six supplied section fixtures pass production generation and parsing without discarded widget source', () => {
    const fixtures = require('../shared-contracts/response-section-references.json').fixtures
    assert.equal(fixtures.length, 6)
    for (const fixture of fixtures) {
      const compiled = compileWidgets({reply:fixture.wire,userPrompt:fixture.question})
      assert.deepEqual(compiled.errors, [], fixture.id)
      assert.deepEqual(parseStream(compiled.text).filter(segment=>segment.kind==='widget').map(segment=>segment.widget), fixture.widgetKinds, fixture.id)
      assert.doesNotThrow(()=>renderToString(h(MessageContent,{text:compiled.text,dark:false})), fixture.id)
    }
    const grid = parseProseBlocks(fixtures.find(f=>f.id==='05-data-grid').wire).find(block=>block.kind==='table')
    assert.equal(grid.rows.length, 11)
    const code = parseStream(fixtures.find(f=>f.id==='03-react-code').wire).find(segment=>segment.kind==='code')
    assert.equal(code.language, 'jsx'); assert.ok(code.code.includes('localStorage'))
  })
  check('shared votes refuse invented malformed and over-capacity options without healing them into unrelated tasks',()=>{
    for(const items of [[],['Only option'],['Actual',''],['Actual',{title:'Not a string'}],Array.from({length:13},(_,i)=>'Option '+i)]) {
      const source=JSON.stringify({surface:'vote',room:'source-options',items})
      assert.equal(validateWidget('collab',source).ok,false)
      const response=compileWidgets({reply:'```collab\n'+source+'\n```',userPrompt:'Let viewers vote'})
      assert.equal(response.widgets,0)
      assert.ok(response.text.includes(source))
    }
    assert.equal(validateWidget('collab',JSON.stringify({surface:'vote',room:'source-options',items:['First','Second']})).ok,true)
    assert.equal(validateWidget('collab',JSON.stringify({surface:'unknown',room:'source-options'})).ok,false)
  })
  check('timer generation preserves real explicit seconds without price defaults or unit coercion', () => {
    for (const durationSeconds of [0, -1, 1.5, 604801, null, true, '$25', '25%', '25']) {
      assert.equal(validateWidget('app', JSON.stringify({type:'timer',durationSeconds})).ok, false)
    }
    const configured = validateWidget('app', JSON.stringify({type:'timer',durationSeconds:5}))
    assert.equal(configured.ok, true); assert.equal(configured.widget.durationSeconds, 5)
    const setup = validateWidget('app', JSON.stringify({type:'timer',items:[{title:'Not a duration',price:99}]}))
    assert.equal(setup.ok, true); assert.equal(setup.widget.durationSeconds, undefined)
    const result = compileWidgets({reply:'```timer\n{"durationSeconds":5}\n```', userPrompt:'Set a five second countdown'})
    assert.equal(result.widgets, 1)
    assert.equal(parseStream(result.text).find(segment => segment.kind === 'widget').payload.durationSeconds, 5)
  })
  check('invalid or overflowing checklist tasks are refused without loss or unrelated widget healing', () => {
    for (const items of [[{label:'Actual'}, {alien:42}], Array.from({length:31}, (_,i)=>({label:`Task ${i}`}))]) {
      const source = JSON.stringify({items})
      assert.equal(validateWidget('checklist', source).ok, false)
      const compiled = compileWidgets({reply:'```checklist\n' + source + '\n```', userPrompt:'my actual checklist'})
      assert.equal(compiled.widgets, 0)
      assert.ok(compiled.text.includes(source))
    }
  })
  check('whole non-widget JSON renders exact source without interpreting machine strings as Markdown', () => {
    for (const source of ['{}','[]','[1,2,3]','[{"literal":"**bold** :rocket: 😀"}]',' \r\n{"literal":"**bold** :rocket: 😀","nested":{"x":true}}\r\n ']) {
      const segments = parseStream(source)
      assert.equal(segments.length, 1); assert.equal(segments[0].kind, 'code')
      assert.equal(segments[0].language, 'json'); assert.equal(segments[0].code, source)
    }
  })
  check('client streaming parser cannot bypass dataset validation with a merely parseable JSON object', () => {
    const invalidPayloads = [
      ['chart', { type: 'line', series: [{ name: 'Actual', data: [[0,null],[1,2]] }] }],
      ['table', { columns: ['Actual'], rows: Array.from({length: 101}, (_, i) => [i]) }],
      ['app', { type: 'wizard', rows: [[42]], columns: ['Actual'] }],
    ]
    for (const [kind, payload] of invalidPayloads) {
      const source = JSON.stringify(payload)
      const segments = parseStream('```' + kind + '\n' + source + '\n```')
      assert.equal(segments.filter(segment => segment.kind === 'widget').length, 0)
      assert.equal(segments.find(segment => segment.kind === 'code').code, source)
      assert.match(segments.find(segment => segment.kind === 'text').text, /could not be rendered/)
    }
  })
  check('answer widget overflow preserves its original source and announces the limit', () => {
    const reply = Array.from({length: 13}, (_, i) => '```table\n' + JSON.stringify({ columns: ['Value'], rows: [[i + 1]] }) + '\n```').join('\n\n')
    const result = compileWidgets({ reply, userPrompt: 'Show these tables' })
    assert.equal(result.widgets, 12); assert.match(result.text, /exceeds this response’s widget limit/)
    assert.ok(result.text.includes('[[13]]'))
  })
  // Execute the EXACT generated worker source. This is runtime validation,
  // not matching a prewritten expected log from the source text.
  const doc = javascriptSandboxDocument('irrelevant')
  const workerSource = JSON.parse(doc.match(/new Blob\(\[("(?:[^"\\]|\\.)*")\]/)[1])
  let messages = []
  const self = { postMessage: (m) => messages.push(m), addEventListener: () => {} }
  vm.runInNewContext(workerSource, { self, setTimeout, clearTimeout })
  check('actual worker announces readiness before accepting execution', () => assert.equal(messages[0].kind, 'ready'))
  messages = []
  await self.onmessage({ data: 'console.log(7*9); await new Promise(r=>setTimeout(r, 15)); console.log("later");' })
  check('JavaScript runs arithmetic and real awaited timers', () => assert.deepEqual(messages.map((m) => m.value), ['63', 'later', 'Completed. Unawaited background work is stopped.']))
  messages = []
  await self.onmessage({ data: 'throw new Error("actual failure")' })
  check('JavaScript errors are actual exceptions', () => assert.match(messages[0].value, /actual failure/))
  check('worker source escapes script terminators', () => assert.ok(!javascriptSandboxDocument('</script><img src=x>').includes('</script><img')))
  check('worker network is forbidden and execution is bounded', () => { assert.match(doc, /connect-src 'none'/); assert.match(doc, /worker\.terminate\(\)/); assert.match(doc, /2000/) })
  for (const source of ['console.log("$&")', 'console.log("$$")', 'console.log("$`")', 'console.log("$\'")', 'console.log("</script><script>hostEscape=1</script>")']) {
    const generated = javascriptSandboxDocument(source)
    check('sandbox source insertion is literal: ' + source, () => {
      const extracted = JSON.parse(generated.match(/worker.postMessage\(("(?:[^"\\]|\\.)*")\)/)[1])
      assert.equal(extracted, source)
      assert.ok(!generated.includes('</script><script>hostEscape'))
    })
  }
  const actualWrapper = new JSDOM(renderToString(h(HtmlBlock, { source: javascriptSandboxDocument('console.log(7)') })))
  const actualDocument = new JSDOM(actualWrapper.window.document.querySelector('iframe').getAttribute('srcDoc'))
  check('actual HTML wrapper keeps worker CSP inside a real document head', () => {
    assert.equal(actualDocument.window.document.querySelector('meta[http-equiv="Content-Security-Policy"]').parentElement.tagName, 'HEAD')
  })
  actualWrapper.window.close(); actualDocument.window.close()


  const unicodeSource = 'const data = {"😀": 7, "🌅": 9};\nconsole.log(data["😀"] + data["🌅"], "😀".length);'.replace('\\n', '\n')
  // The reply normalizer, compiler, parser and runtime all receive the actual
  // source. A text-only no-emoji assertion would miss corrupted computation.
  for (const marker of ['```', '~~~', '````']) {
    const reply = 'Ready 😀\n' + marker + 'javascript\n' + unicodeSource + '\n' + marker + '\nDone 😀'
    const normalized = sanitizeReplyEmojis(reply)
    check('machine fence survives normalization: ' + marker, () => {
      assert.ok(normalized.includes(unicodeSource)); assert.ok(!normalized.split(marker)[0].includes('😀'))
      assert.equal(sanitizeReplyEmojis(normalized), normalized)
    })
    const compiled = ensureWidgets(reply, { userPrompt: 'Explain this JavaScript code' })
    const parsed = parseStream(compiled.text).find(segment => segment.kind === 'code')
    check('actual compiler and parser preserve source: ' + marker, () => assert.equal(parsed?.code, unicodeSource))
    messages = []; await self.onmessage({ data: parsed.code })
    check('parsed Unicode-key computation is genuine: ' + marker, () => assert.equal(messages[0].value, '16 2'))
    for (let boundary = reply.indexOf(marker) + marker.length + 'javascript\n'.length; boundary < reply.indexOf('\n' + marker, reply.indexOf(marker) + 1); boundary++) {
      const prefix = reply.slice(0, boundary)
      assert.ok(sanitizeReplyEmojis(prefix).endsWith(prefix.slice(prefix.indexOf(marker))), 'Accumulated stream boundary ' + boundary)
    }
    check('every accumulated source boundary preserves machine bytes: ' + marker, () => assert.ok(true))
  }
  check('whole JSON keys and escaped/literal strings survive preparse normalization', () => {
    const payload = JSON.stringify({ '😀': '🌅', code: unicodeSource, nested: { '🔑': 7 } })
    assert.equal(sanitizeReplyEmojis(payload), payload)
    assert.deepEqual(JSON.parse(sanitizeReplyEmojis(payload)), JSON.parse(payload))
  })
  check('inline source and Unicode link destinations are not rewritten', () => {
    const raw = 'Use `"😀".length` and [Open 😀](https://example.test/😀?key=🌅).'
    const normalized = sanitizeReplyEmojis(raw)
    assert.ok(normalized.includes('`"😀".length`'))
    assert.ok(normalized.includes('(https://example.test/😀?key=🌅)'))
    const html = new JSDOM(renderToString(h(MessageContent, { text: raw })))
    assert.equal(html.window.document.querySelector('code').textContent, '"\\ud83d\\ude00".length')
    assert.equal(html.window.document.querySelector('a').getAttribute('href'), 'https://example.test/😀?key=🌅')
    assert.ok(!html.window.document.body.textContent.includes('😀'))
    html.window.close()
  })
  check('intentional source whitespace survives the fence parser', () => {
    const raw = '  console.log(7);\n\n'
    assert.equal(parseStream('```js\n' + raw + '\n```')[0].code, raw)
    const html = '  <pre>  exact  </pre>  '
    assert.equal(parseStream('```html\n' + html + '\n```')[0].source, html)
  })
  check('incomplete executable HTML has no iframe to run partial source', () => {
    const output = new JSDOM(renderToString(h(MessageContent, { text: '```html\n<button>Partial</button><script>throw 7;', streaming: true })))
    assert.equal(output.window.document.querySelectorAll('iframe').length, 0)
    assert.match(output.window.document.body.textContent, /Building html/)
    output.window.close()
  })
  for (const source of [unicodeSource, 'console.log("\\ud83d\\ude00")', 'const cost = "বাংলা 日本語";', '"\\n\\t\\u2600"', '\u200d\ufe0f\u20e3', 'flag 🏳️‍🌈']) {
    check('Unicode-safe editor is lossless: ' + JSON.stringify(source).slice(0, 45), () => {
      const encoded = encodeSourceEditor(source)
      assert.equal(decodeSourceEditor(encoded), source)
      assert.equal(escapeCodeDisplay(encoded), encoded)
    })
  }
  for (const draft of ['{"code":"x"}', '[]', 'null', '"unfinished', '"ok" false']) {
    check('invalid source draft rejected: ' + draft, () => assert.throws(() => decodeSourceEditor(draft)))
  }
  messages = []
  await self.onmessage({ data: 'for(let i=0;i<10000;i++) console.log(i);' })
  check('worker throttles before postMessage and still completes after 100 logs', () => {
    assert.equal(messages.length, 101)
    assert.equal(messages.at(-1).kind, 'done')
  })

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
    const mixed = pieData({ type: 'pie', labels: ['P4', 'P5'], series: [
      { name: 'Long', data: [[0, 7], [1, 8]] }, { name: 'Short', data: [[0, 104]] }
    ] })
    assert.deepEqual(mixed.map(point => point.name), ['Long: P4', 'Long: P5', 'Short: P4'])
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
  check('a chart sum never claims the individual points growth as aggregate-period growth', () => {
    const label = Array.from(document.querySelectorAll('div')).find(node => node.textContent === 'Sum')
    assert.ok(label)
    const card = label.parentElement.textContent
    assert.match(card, /30/)
    assert.match(card, /2 points/)
    assert.doesNotMatch(card, /%|↑|↓/)
    assert.match(document.body.textContent, /First–last change/)
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
  const { parseTableNumber } = from('lib/nexly/table-number')
  check('whole-cell numbers retain signs, grouping and real unit multipliers', () => {
    for (const [raw, expected] of [['↓ 1.2%', -1.2], ['↘ -1.2%', -1.2], ['↑ +0.5%', 0.5], ['−0.3%', -0.3], ['-$1,200.50', -1200.5], ['$-1.2k', -1200], ['৳2M', 2e6], ['₹10', 10], ['2x', 2], ['0', 0]]) assert.equal(parseTableNumber(raw), expected, raw)
  })
  check('malformed or contradictory numbers never become invented numeric cells', () => {
    for (const raw of ['1,00', '1+2', '1 2', '1e3', 'NaN', 'Infinity', '↓+1%', '↑-1%', '--1', '+-1', 'growth 12%', '1.2.3', true, null]) assert.equal(parseTableNumber(raw), null, String(raw))
  })
  const { chartFromTableCells, plainTableCell } = from('lib/nexly/table-number')
  const completeTable = Array.from({length:31}, (_, index)=>[String(index+1), 'Language '+(index+1), (index+1)+'%'])
  check('derived ranked-table chart includes the actual measure, names, units and all 31 rows', () => {
    const chart = chartFromTableCells(['#','Language','Popularity'], completeTable)
    assert.equal(chart.title, 'Popularity'); assert.equal(chart.unit, '%'); assert.equal(chart.labels.at(-1), 'Language 31')
    assert.equal(chart.series[0].data.length, 31); assert.deepEqual(chart.series[0].data.at(-1), [30,31])
  })
  check('derived charts refuse missing values, mixed units and invented arithmetic from Markdown erasure', () => {
    for (const bad of ['not recorded', '$3', '1*2%', '+1_2%']) {
      const rows = completeTable.map((row,index)=>index===2 ? [row[0],row[1],bad] : row)
      assert.equal(chartFromTableCells(['#','Language','Popularity'], rows), null, bad)
    }
    assert.equal(plainTableCell('**12.5%**'), '12.5%'); assert.equal(plainTableCell('1*2%'), '1*2%')
  })
  await mount(h(MessageContent, {text:'| # | Language | Popularity |\n| --- | --- | --- |\n' + completeTable.map(row=>'| '+row.join(' | ')+' |').join('\n')}))
  await click(buttons().find(button=>button.textContent==='Chart'))
  check('ordinary Web Markdown Chart tab really renders the complete source-derived chart', () => {
    assert.equal(document.querySelector('table'), null)
    assert.match(document.body.textContent, /Popularity/)
    assert.match(document.body.textContent, /31 points/)
  })
  await click(buttons().find(button=>button.textContent==='Table'))
  check('ordinary Web chart switches back to the original paginated table', () => assert.equal(document.querySelectorAll('tbody tr').length, 5))
  const trendRows = [['Down', '↘1.5%'], ['Up', '+0.2%'], ['Negative', '−0.3%']]
  for (const ordinary of [false, true]) {
    await mount(ordinary ? h(MessageContent, {text: '| Name | Trend |\n| --- | --- |\n' + trendRows.map(row=>'| '+row.join(' | ')+' |').join('\n')}) : h(TableWidget, {payload:{columns:['Name','Trend'],rows:trendRows}}))
    await click(document.querySelectorAll('th')[1])
    check(`${ordinary ? 'Markdown' : 'widget'} trend sorting puts the actual largest loss first`, () => assert.match(document.querySelector('tbody tr').textContent, /Down/))
    await click(document.querySelectorAll('th')[1])
    check(`${ordinary ? 'Markdown' : 'widget'} descending trend sorting puts the actual gain first`, () => assert.match(document.querySelector('tbody tr').textContent, /Up/))
  }
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
  await mount(h(ChecklistWidget, { payload: { title: 'Fresh source', items: [{label:'New supplied task', done:false}] } }))
  check('replacement checklist source resets task edits and completion', () => {
    assert.ok(document.querySelector('button[aria-label="Edit New supplied task"]'))
    assert.equal(document.querySelector('[role="progressbar"]').getAttribute('aria-valuenow'), '0')
  })
  const { AppTasks } = require('../dist/probe/src/components/genui/apps/AppTasks')
  await mount(h(AppTasks, {payload:{type:'tasks',title:'Actual app tasks',items:[
    {title:'First source',subtitle:'Supplied app detail',meta:'Supplied app meta',done:true},
    {title:'Second source',done:false}]}}))
  check('app tasks preserve supplied completion and metadata without invented dates or priorities', () => {
    assert.equal(document.querySelector('[role="progressbar"]').getAttribute('aria-valuenow'), '50')
    assert.match(document.body.textContent, /Supplied app detail.*Supplied app meta/)
    assert.doesNotMatch(document.body.textContent, /High priority|Due today|2026/)
  })
  await click(document.querySelector('button[aria-label="Delete Second source"]'))
  check('app task deletion keeps completion attached to the surviving source task', () => {
    assert.equal(document.querySelector('[role="progressbar"]').getAttribute('aria-valuenow'), '100')
  })
  await click(document.querySelector('button[aria-label="Edit First source"]'))
  await typeIn(document.querySelector('input[aria-label="Edit task"]'), 'Changed app task')
  await click(buttons().find(button => button.textContent === 'Save'))
  check('app source tasks have real editable labels', () => assert.ok(document.querySelector('button[aria-label="Delete Changed app task"]')))
  const { AppWizard } = require('../dist/probe/src/components/genui/apps/AppWizard')
  const { AppOnboarding } = require('../dist/probe/src/components/genui/apps/AppOnboarding')
  const { AppBooking } = require('../dist/probe/src/components/genui/apps/AppBooking')
  await mount(h(AppWizard, {payload:{type:'wizard',steps:[{title:'Actual step',fields:[
    {type:'slider',label:'Exact zero',value:0,min:0,max:10}, {type:'text',label:'Real answer'}]}]}}))
  check('wizard bounded fields preserve a real zero instead of inventing fifty', () => {
    assert.equal(document.querySelector('input[type="range"]').value, '0')
  })
  await typeIn(document.querySelector('input[placeholder="Real answer"]'), 'Actual typed answer')
  await click(buttons().find(button => button.textContent === 'Finish'))
  check('wizard Finish completes locally with the actual answers and no fabricated submission', () => {
    assert.match(document.querySelector('[role="status"]').textContent, /Completed locally.*No data has been submitted/)
    assert.ok([...document.querySelectorAll('dd')].some(item => item.textContent === '0'))
    assert.match(document.body.textContent, /Actual typed answer/)
  })
  await click(buttons().find(button => button.textContent === 'Review answers'))
  check('wizard review retains the actual typed values', () => assert.equal(document.querySelector('input[placeholder="Real answer"]').value, 'Actual typed answer'))
  await mount(h(AppOnboarding, {payload:{type:'onboarding',steps:[{title:'First actual step'},{title:'Second actual step'}]}}))
  await click(buttons().find(button => button.textContent === 'Next'))
  await click(buttons().find(button => button.textContent === 'Get started'))
  check('onboarding final CTA actually completes its local flow', () => assert.match(document.querySelector('[role="status"]').textContent, /Introduction completed locally/))
  await click(buttons().find(button => button.textContent === 'Start again'))
  check('onboarding restart returns to its real first step', () => assert.match(document.body.textContent, /First actual step/))
  await mount(h(AppBooking, {payload:{type:'booking',slots:Array.from({length:13}, (_,i)=>`Slot ${i+1}`)}}))
  check('booking preview retains every supplied slot rather than hiding the thirteenth', () => assert.ok(buttons().some(button => button.textContent === 'Slot 13')))
  await click(buttons().find(button => button.textContent === 'Slot 13'))
  await click(buttons().find(button => button.textContent === 'Save slot locally'))
  check('booking confirmation saves only the actual local selection and never claims a reservation', () => {
    assert.match(document.querySelector('[role="status"]').textContent, /Saved locally: Slot 13.*No reservation has been made/)
    assert.doesNotMatch(document.body.textContent, /Booked for/)
  })
  await click(buttons().find(button => button.textContent === 'Change selection'))
  await click(buttons().find(button => button.textContent === 'Slot 1'))
  await click(buttons().find(button => button.textContent === 'Save slot locally'))
  check('booking change-selection control really changes the saved local slot', () => assert.match(document.querySelector('[role="status"]').textContent, /Saved locally: Slot 1\./))
  let copiedSource = null
  Object.defineProperty(global, 'navigator', { configurable: true, value: { clipboard: { writeText: async source => { copiedSource = source } } } })
  const typeSource = async value => {
    const input = document.querySelector('textarea')
    assert.ok(input, 'real code editor exists')
    await act(async () => {
      Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value').set.call(input, value)
      input.dispatchEvent(new window.Event('input', { bubbles: true }))
    })
  }
  await mount(h(CodeBlock, { language: 'javascript', code: unicodeSource }))
  check('visible source is escaped without leaking raw Unicode emoji', () => {
    assert.ok(!document.body.textContent.includes('😀'))
    assert.ok(document.body.textContent.includes('\\ud83d\\ude00'))
  })
  await click(buttons().find(b => b.textContent === 'Copy'))
  check('Copy receives exact original source rather than display escapes', () => assert.equal(copiedSource, unicodeSource))
  await click(buttons().find(b => b.textContent === 'Edit'))
  check('Unicode source starts a reversible JSON editor', () => {
    assert.equal(document.querySelector('textarea').getAttribute('aria-label'), 'JSON-encoded source')
    assert.equal(decodeSourceEditor(document.querySelector('textarea').value), unicodeSource)
  })
  await typeSource('"unterminated')
  await click(buttons().find(b => b.textContent.includes('Run Code')))
  check('invalid live editor draft cannot execute stale valid source', () => {
    assert.match(document.body.textContent, /Not executed: correct/)
    assert.equal(document.querySelectorAll('iframe').length, 0)
  })
  await click(buttons().find(b => b.textContent === 'Done'))
  check('Done cannot conceal invalid draft errors', () => assert.ok(document.querySelector('textarea')))
  const updated = unicodeSource + '\nconsole.log("actual edit");'
  await typeSource(encodeSourceEditor(updated))
  await click(buttons().find(b => b.textContent === 'Done'))
  await click(buttons().find(b => b.textContent === 'Copied' || b.textContent === 'Copy'))
  check('valid Unicode editor changes preserve the actual source', () => assert.equal(copiedSource, updated))
  await click(buttons().find(b => b.textContent === 'Reset'))
  await click(buttons().find(b => b.textContent === 'Copied' || b.textContent === 'Copy'))
  check('Reset restores exact original source and clears prior error state', () => {
    assert.equal(copiedSource, unicodeSource)
    assert.equal(document.querySelector('[role="alert"]'), null)
  })
  await mount(h(CodeBlock, { language: 'json', code: '{"fresh":7}' }))
  check('new code/language resets editor, output and execution state', () => {
    assert.equal(document.querySelector('textarea'), null)
    assert.equal(document.querySelector('iframe'), null)
    assert.ok(!document.body.textContent.includes('actual edit'))
  })
  await click(buttons().find(b => b.textContent === 'Edit'))
  await typeSource('')
  check('cleared source remains editable', () => assert.equal(document.querySelector('textarea').value, ''))
  await typeSource('"😀"')
  check('pasting emoji in ordinary editor switches losslessly to encoded mode', () => {
    assert.equal(decodeSourceEditor(document.querySelector('textarea').value), '"😀"')
    assert.equal(document.querySelector('textarea').getAttribute('aria-label'), 'JSON-encoded source')
    assert.ok(!document.querySelector('textarea').value.includes('😀'))
  })
  await mount(h(CodeBlock, { language: 'sql', code: 'SELECT * FROM private_accounts' }))
  check('SQL discloses its unavailable connection without advertising a fake Run action', () => {
    assert.match(document.body.textContent, /no database is connected/)
    assert.doesNotMatch(document.body.textContent, /Ava Chen|3 rows returned/)
    assert.ok(!buttons().some(button => /Run/.test(button.textContent)))
  })
  for (const language of ['python', 'typescript']) {
    await mount(h(CodeBlock, { language, code: language === 'python' ? 'print(6*7)' : 'const value: number = 42' }))
    check(language + ' immediately discloses unsupported execution without a nonworking Run control', () => {
      assert.match(document.body.textContent, /Not executed|Source only/)
      assert.ok(!buttons().some(button => /Run/.test(button.textContent)))
      assert.doesNotMatch(document.body.textContent, /Arithmetic preview/)
      assert.equal(document.querySelectorAll('iframe').length, 0)
    })
  }
  for (const language of ['jsx', 'tsx', 'react']) {
    await mount(h(CodeBlock, { language, code: 'export default function App(){return <p>Actual component</p>}' }))
    check(language + ' exposes actual React execution but does not falsely announce a mount before Run', () => {
      assert.ok(buttons().some(button => button.textContent.trim() === 'Run in Browser'))
      assert.doesNotMatch(document.body.textContent, /Source only|React preview running/)
      assert.equal(document.querySelectorAll('iframe').length, 0)
    })
  }
  for (const source of ["{'x':1}", '{x:1}', '{"x":1,}', '[1,]', '{} false', '01', 'NaN', '"raw\nnewline"', '"bad\\x"', '\u00a0{}']) {
    await mount(h(CodeBlock, { language: 'json', code: source }))
    await click(buttons().find(b => b.textContent.includes('Validate JSON')))
    check('JSON validation rejects malformed actual source: ' + JSON.stringify(source), () => {
      assert.ok(document.querySelector('[role="alert"]'))
      assert.equal(document.querySelectorAll('iframe').length, 0)
    })
  }
  // Contextual controls must operate locally, not send their labels to the model.
  let prompts = [], downloads = [], shared = null
  const urls = new Map()
  const createURL = URL.createObjectURL, revokeURL = URL.revokeObjectURL
  const anchorClick = window.HTMLAnchorElement.prototype.click
  URL.createObjectURL = blob => { const url = 'blob:response-' + urls.size; urls.set(url, blob); return url }
  URL.revokeObjectURL = () => {}
  window.HTMLAnchorElement.prototype.click = function () {
    if (this.download) downloads.push({ name: this.download, blob: urls.get(this.href) })
    else anchorClick.call(this)
  }
  const response = '```javascript\nconsole.log("original")\n```'
  const renderResponse = text => mount(h(MessageContent, { text, onPrompt: prompt => prompts.push(prompt) }))
  const contextual = label => buttons().find(button => button.textContent === label)
  await renderResponse(response)
  await click(contextual('Edit Code'))
  check('contextual Edit Code opens the actual editor without requesting a model response', () => {
    assert.ok(document.querySelector('textarea[aria-label="Source code"]'))
    assert.deepEqual(prompts, [])
  })
  await typeSource('console.log("edited source")')
  await click(contextual('Download Project'))
  const archiveBytes = new Uint8Array(await downloads[0].blob.arrayBuffer())
  const unzip = require('node:child_process').spawnSync('python3', ['-c', 'import sys,io,zipfile,json; z=zipfile.ZipFile(io.BytesIO(sys.stdin.buffer.read())); print(json.dumps({n:z.read(n).decode("utf-8") for n in z.namelist()}))'], { input: archiveBytes, encoding: 'utf8' })
  check('Download Project emits a valid ZIP with the actual edited source, not fabricated project files', () => {
    assert.equal(unzip.status, 0, unzip.stderr)
    const files = JSON.parse(unzip.stdout)
    assert.equal(files['snippet-1.js'], 'console.log("edited source")')
    assert.match(files['README.txt'], /not a complete runnable project/)
    assert.equal(downloads[0].name, 'gs-ai-source-snippets.zip')
    assert.deepEqual(prompts, [])
  })
  const { responseSourceArchive, responseSourceFile } = from('lib/code/response-export')
  const lossless = responseSourceArchive([responseSourceFile('বাংলা \r\n' + String.fromCodePoint(0x1f600) + '\u0000', 'python', 0)])
  const readLossless = require('node:child_process').spawnSync('python3', ['-c', 'import sys,io,zipfile; sys.stdout.buffer.write(zipfile.ZipFile(io.BytesIO(sys.stdin.buffer.read())).read("snippet-1.py"))'], { input: lossless })
  check('source ZIP preserves Unicode, line endings and null bytes with independently validated CRCs', () => {
    assert.equal(readLossless.status, 0)
    assert.equal(readLossless.stdout.toString(), 'বাংলা \r\n' + String.fromCodePoint(0x1f600) + '\u0000')
    assert.throws(() => responseSourceArchive([{ name: '../unsafe.js', source: '' }]), /Unsafe/)
  })
  const tableResponse = 'Actual table\n\n| Name | Amount |\n| --- | --- |\n| Item | 42 |'
  await renderResponse(tableResponse)
  await click(contextual('Share'))
  check('Share without Web Share copies the response with an explicit fallback notice', () => {
    assert.equal(copiedSource, tableResponse)
    assert.match(document.querySelector('[role="status"]').textContent, /Sharing is unavailable.*Response copied/)
    assert.deepEqual(prompts, [])
  })
  navigator.share = async payload => { shared = payload }
  await click(contextual('Share'))
  check('Share calls the platform share API with the exact response', () => assert.equal(shared.text, tableResponse))
  navigator.share = async () => { throw Object.assign(new Error('Cancelled'), { name: 'AbortError' }) }
  await click(contextual('Share'))
  check('cancelled sharing does not announce success or create an error', () => assert.equal(document.querySelector('[role="alert"]'), null))
  delete navigator.share
  navigator.clipboard.writeText = async () => { throw new Error('Clipboard permission denied') }
  await click(contextual('Share'))
  check('denied sharing fallback reports the actual error', () => assert.match(document.querySelector('[role="alert"]').textContent, /permission denied/))
  navigator.clipboard.writeText = async value => { copiedSource = value }
  await click(contextual('View Full Report'))
  check('View Full Report shows the existing source and data instead of silently generating another answer', () => {
    assert.match(document.querySelector('[aria-label="Response report"]').textContent, /Item.*42/s)
    assert.deepEqual(prompts, [])
  })
  await click(contextual('Close details'))
  const chartResponse = '```chart\n' + JSON.stringify({ type: 'line', series: [{ name: 'Measured', data: [[0, 3], [1, 7]] }] }) + '\n```'
  await renderResponse(chartResponse)
  await click(contextual('Download Report'))
  check('Download Report emits the response and real dataset as Markdown', () => assert.equal(downloads.at(-1).name, 'gs-ai-response.md'))
  check('report downloads never send a model prompt', () => assert.deepEqual(prompts, []))
  assert.equal(await downloads.at(-1).blob.text(), chartResponse)
  await mount(h(MessageContent, { text: 'Provided links, not verified research.', citations: [
    {ordinal:1,title:'Forged domain',domain:'nature.com',url:'https://untrusted.example/nature.com?publisher=arxiv.org'},
    {ordinal:2,title:'Deceptive suffix',domain:'nature.com',url:'https://nature.com.untrusted.example/paper'},
    {ordinal:3,title:'Actual academic host',domain:'untrusted.example',url:'https://www.nature.com/paper'},
    {ordinal:4,title:'Unsafe destination',domain:'nature.com',url:'javascript:alert(1)'},
    {ordinal:5,title:'Misleading credentials',domain:'nature.com',url:'https://nature.com@untrusted.example/paper'},
  ] }))
  check('source cards never invent verification or trust from model domain labels or URL substrings', () => {
    assert.doesNotMatch(document.body.textContent, /Trusted Source/)
    const cards = [...document.querySelectorAll('a')].filter(node => /Forged domain|Deceptive suffix|Actual academic host/.test(node.textContent))
    assert.equal(cards.length, 3)
    assert.ok(cards[0].textContent.includes('untrusted.example')); assert.ok(cards[0].textContent.includes('Source link'))
    assert.ok(cards[1].textContent.includes('Source link')); assert.ok(!cards[1].textContent.includes('Academic domain'))
    assert.ok(cards[2].textContent.includes('nature.com')); assert.ok(cards[2].textContent.includes('Academic domain'))
  })
  check('unsafe or credential-obscured citations cannot become clickable source destinations', () => {
    const cards = [...document.querySelectorAll('a')].filter(node => /Unsafe destination|Misleading credentials/.test(node.textContent))
    assert.equal(cards.length, 2)
    for (const node of cards) { assert.equal(node.getAttribute('href'), null); assert.equal(node.getAttribute('aria-disabled'), 'true'); assert.ok(node.textContent.includes('Invalid link')) }
  })
  await mount(h(MessageContent, { text: 'Evidence-backed answer.', citations: [{ ordinal: 1, title: 'Original evidence', url: 'https://example.org/evidence' }], onPrompt: prompt => prompts.push(prompt) }))
  await click(contextual('Copy Citations'))
  check('Copy Citations copies only the supplied source records', () => {
    assert.equal(copiedSource, '[1] Original evidence — https://example.org/evidence')
    assert.deepEqual(prompts, [])
  })
  await click(contextual('View All Sources'))
  check('View All Sources displays the real citation destinations', () => assert.ok(document.querySelector('[aria-label="All sources"] a[href="https://example.org/evidence"]')))
  await renderResponse('A different ordinary response.')
  check('response replacement closes previous source disclosure and clears its notice', () => {
    assert.equal(document.querySelector('[aria-label="All sources"]'), null)
    assert.equal(document.querySelector('[role="status"]'), null)
  })
  let finishOldClipboard
  navigator.clipboard.writeText = () => new Promise(resolve => { finishOldClipboard = resolve })
  await renderResponse(tableResponse)
  await click(contextual('Share'))
  await renderResponse('New answer while clipboard permission is pending.')
  await act(async () => { finishOldClipboard(); await Promise.resolve() })
  check('late clipboard completion cannot announce sharing success on a different response', () => {
    assert.equal(document.querySelector('[role="status"]'), null)
    assert.equal(document.querySelector('[role="alert"]'), null)
  })
  URL.createObjectURL = createURL; URL.revokeObjectURL = revokeURL
  window.HTMLAnchorElement.prototype.click = anchorClick
  await act(async () => root.unmount())
  console.log(`\n${checks} interaction-honesty checks passed`)
}
main().catch((error) => { console.error(error); process.exitCode = 1 })
