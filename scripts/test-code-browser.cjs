/* Requires the compiled probe plus pinned Chromium/puppeteer-core/esbuild.
 * Real browser, actual React controls and clipboard. No fabricated worker or
 * fetch responses are used to claim denial: the allowed counterfactual MUST
 * reach a real local HTTP endpoint, while production CSP must prevent it. */
const assert = require('node:assert/strict')
const http = require('node:http')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const zlib = require('node:zlib')
const { execFileSync } = require('node:child_process')
const chromium = require('@sparticuz/chromium')
const puppeteer = require('puppeteer-core')
const esbuild = require('esbuild')
require('../dist/probe/scripts/probe-alias-hook')
const { javascriptSandboxDocument } = require('../dist/probe/src/lib/code/javascript-sandbox')
const { terminalSandboxDocument } = require('../dist/probe/src/lib/code/terminal-sandbox')
const { encodeSourceEditor } = require('../dist/probe/src/lib/code/display-source')
let checks = 0
const check = (name, fn) => { fn(); checks++; console.log('PASS browser:', name) }

async function main() {
  // The binary package bundles these libraries for minimal Linux hosts too.
  const bin = path.resolve(path.dirname(require.resolve('@sparticuz/chromium')), '../../bin')
  const libs = fs.mkdtempSync(path.join(os.tmpdir(), 'gs-browser-libs-'))
  const archive = path.join(libs, 'al2023.tar')
  fs.writeFileSync(archive, zlib.brotliDecompressSync(fs.readFileSync(path.join(bin, 'al2023.tar.br'))))
  execFileSync('tar', ['-xf', archive, '-C', libs])
  const compiled = await esbuild.build({ entryPoints: [path.join(__dirname, 'browser-code-entry.tsx')], bundle: true,
    write: false, platform: 'browser', format: 'iife', jsx: 'automatic', tsconfig: path.join(__dirname, '../tsconfig.json'),
    define: { 'process.env.NODE_ENV': '"production"' }, logLevel: 'silent' })
  // The fixture serves the actual production route and SQLite-backed broker,
  // not canned snapshots or invented shared counts.
  const routeBundle = await esbuild.build({entryPoints:[path.join(__dirname,'../src/app/api/v1/collab/route.ts')],bundle:true,write:false,platform:'node',format:'cjs',external:['next/server'],tsconfig:path.join(__dirname,'../tsconfig.json'),logLevel:'silent'})
  process.env.COLLAB_DB = path.join(libs,'collab-test.sqlite')
  const Module = require('node:module')
  const routeModule = new Module(__filename, module); routeModule.paths = module.paths
  routeModule._compile(routeBundle.outputFiles[0].text, __filename + '.collab.cjs')
  const collabRoute = routeModule.exports
  const {NextRequest} = require('next/server')
  let collabReadsAvailable = true, collabWritesAvailable = true
  const browser = await puppeteer.launch({ executablePath: await chromium.executablePath(), args: chromium.args.filter(arg => arg !== '--disable-web-security'),
    headless: true, env: { ...process.env, LD_LIBRARY_PATH: path.join(libs, 'lib') + ':' + (process.env.LD_LIBRARY_PATH || '') } })
  let closeProbe = () => {}
  try {
    const probeHits = { '/code-probe': 0, '/terminal-probe': 0 }
    const server = http.createServer(async (request, response) => {
      if (request.url.startsWith('/api/v1/collab')) {
        if ((request.method === 'POST' && !collabWritesAvailable) || (request.method === 'GET' && !collabReadsAvailable)) {
          response.writeHead(503, {'Content-Type':'application/json'}); response.end('{"error":"test transport unavailable"}'); return
        }
        const abort = new AbortController()
        response.on('close', () => abort.abort())
        try {
          const chunks=[]; for await (const chunk of request) chunks.push(chunk)
          const req = new NextRequest('http://127.0.0.1:'+server.address().port+request.url, {method:request.method,signal:abort.signal,...(request.method==='POST'?{body:Buffer.concat(chunks).toString(),headers:{'Content-Type':'application/json'}}:{})})
          const result = await collabRoute[request.method](req)
          response.writeHead(result.status, Object.fromEntries(result.headers))
          if (result.body) require('node:stream').Readable.fromWeb(result.body).pipe(response)
          else response.end()
        } catch (error) { if (!response.headersSent) response.writeHead(500); response.end(String(error)) }
        return
      }
      if (request.method === 'GET' && Object.hasOwn(probeHits, request.url)) probeHits[request.url]++
      response.writeHead(200, { 'Content-Type': Object.hasOwn(probeHits, request.url) ? 'application/json' : 'text/html', 'Access-Control-Allow-Origin': '*', 'Cache-Control': 'no-store' })
      response.end(Object.hasOwn(probeHits, request.url) ? '{"allowed":true}' : '<!doctype html><div id="app"></div>')
    })
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
    const endpoint = `http://127.0.0.1:${server.address().port}`
    closeProbe = () => { server.closeAllConnections(); server.close() }

    const context = browser.defaultBrowserContext()
    await context.overridePermissions(endpoint, ['clipboard-read', 'clipboard-write', 'clipboard-sanitized-write'])
    async function pageWithApp() {
      const page = await browser.newPage()
      // Real same-address-space HTTP avoids Local Network Access/CORS/CDP
      // artifacts masquerading as CSP. No browser network mocks or bypasses.
      await page.goto(endpoint + '/')
      return page
    }
    const page = await pageWithApp()
    await page.addScriptTag({ content: compiled.outputFiles[0].text })
    async function mount(source, language = 'javascript') {
      await page.evaluate((source, language) => window.gsRenderCode(source, language), source, language)
      await page.waitForSelector('button')
    }
    async function click(label) {
      await page.waitForFunction(label => [...document.querySelectorAll('button')].some(b => b.textContent.trim() === label), {}, label)
      for (const button of await page.$$('button')) {
        if ((await button.evaluate(b => b.textContent)).trim() === label) { await button.click(); return }
        await button.dispose()
      }
      throw new Error('Actual browser button missing: ' + label)
    }
    async function frameOutput(expected) {
      await page.waitForSelector('iframe')
      const element = await page.$('iframe')
      const frame = await element.contentFrame()
      await frame.waitForFunction(expected => document.getElementById('out')?.textContent.includes(expected), { timeout: 10000 }, expected)
      return { frame, output: await frame.$eval('#out', el => el.textContent) }
    }
    async function typeSource(draft) {
      const editor = await page.$('textarea')
      assert.ok(editor)
      await editor.click()
      await page.keyboard.down('Control'); await page.keyboard.press('A'); await page.keyboard.up('Control')
      await page.keyboard.type(draft)
    }
    const source = 'const data={"😀":7,"🌅":9}; console.log(data["😀"]+data["🌅"],"😀".length,"$&","$$");'
    await mount(source)
    const visibleEmoji = await page.evaluate(() => document.body.innerText.includes('😀'))
    check('visible source uses escapes', () => assert.equal(visibleEmoji, false))
    await click('Copy')
    await page.waitForFunction(() => [...document.querySelectorAll('button')].some(b => b.textContent.trim() === 'Copied'))
    const clipboard = await page.evaluate(() => navigator.clipboard.readText())
    check('real browser clipboard receives original source', () => assert.equal(clipboard, source))
    await click('Run Code')
    const first = await frameOutput('Completed.')
    check('actual worker computes Unicode keys and literal dollar strings', () => assert.ok(first.output.includes('16 2 $& $$')))
    await click('Run Code')
    const second = await frameOutput('Completed.')
    check('a repeated Run replaces the previous execution context', () => { assert.ok(first.frame.isDetached()); assert.notEqual(first.frame, second.frame) })
    await click('Edit'); await typeSource('"unfinished')
    await click('Run Code')
    const stale = await page.$('iframe')
    check('actual invalid draft Run clears stale successful execution', () => assert.equal(stale, null))
    await click('Reset'); await click('Done'); await click('Run Code')
    const reset = await frameOutput('Completed.')
    check('actual Reset restores original executable source', () => assert.ok(reset.output.includes('16 2 $& $$')))
    await mount('console.log("😀", undefined); for(let i=0;i<10000;i++)console.log(i);')
    await click('Run Code')
    const logs = await frameOutput('Completed.')
    check('bounded output escapes emoji and represents undefined honestly', () => { assert.ok(logs.output.startsWith('\\ud83d\\ude00 undefined\n')); assert.equal(logs.output.trimEnd().split('\n').length, 101) })
    await mount('while(true){}')
    const began = Date.now(); await click('Run Code')
    const stopped = await frameOutput('Stopped: execution exceeded 2 seconds.')
    check('infinite actual Worker is terminated without blocking the app', () => assert.ok(Date.now() - began >= 1900 && Date.now() - began < 10000))
    assert.equal(await stopped.frame.$eval('#run', el => el.textContent), 'Run again')
    const networkSource = 'try{await fetch(' + JSON.stringify(endpoint + '/code-probe') + ');console.log("NETWORK REACHED")}catch{console.log("FETCH REFUSED")}await new Promise(r=>setTimeout(r,100));'
    await mount(networkSource); await click('Run Code')
    const denied = await frameOutput('Completed.')
    check('real browser Worker reports an actual CSP violation', () => { assert.ok(denied.output.includes('BLOCKED connect-src')); assert.ok(denied.output.includes('FETCH REFUSED')); assert.ok(!denied.output.includes('NETWORK REACHED')) })
    check('production CSP prevents any actual HTTP request', () => assert.equal(probeHits['/code-probe'], 0))
    // Causal negative control: only the connection policy changes. If requests
    // were merely offline/blocked by the test infrastructure this MUST fail.
    const allowedPage = await pageWithApp()
    const permissive = javascriptSandboxDocument(networkSource).replace("connect-src 'none'", `connect-src ${endpoint}`)
    await allowedPage.addScriptTag({ content: compiled.outputFiles[0].text })
    await allowedPage.evaluate(source => window.gsRenderHTML(source), permissive)
    await allowedPage.waitForSelector('iframe')
    const allowedFrame = await (await allowedPage.$('iframe')).contentFrame()
    await allowedFrame.waitForFunction(() => document.getElementById('out')?.textContent.includes('NETWORK REACHED'), { timeout: 10000 })
    check('removing just CSP denial actually reaches the real HTTP endpoint', () => assert.equal(probeHits['/code-probe'], 1))
    await page.screenshot({ path: path.join(os.tmpdir(), 'gs-code-browser-runtime.png'), fullPage: true })
    await page.evaluate(() => window.gsRenderChecklist({title:'Actual tasks', items:[{label:'Original task',detail:'Only supplied detail',done:false},{label:'Keep task',done:true}]}))
    await page.waitForSelector('input[aria-label="New task"]')
    await page.click('button[aria-label="Edit Original task"]')
    await page.waitForSelector('input[aria-label="Edit task"]')
    await page.$eval('input[aria-label="Edit task"]', input => input.select())
    await page.type('input[aria-label="Edit task"]', 'Changed source task')
    await click('Save')
    await page.click('button[aria-label="Delete Changed source task"]')
    const survivingProgress = await page.$eval('[role="progressbar"]', node => node.getAttribute('aria-valuenow'))
    check('real checklist source edit/delete retains completion on its surviving task', () => assert.equal(survivingProgress, '100'))
    await page.type('input[aria-label="New task"]', 'Added actual task')
    await click('Add Task')
    await page.click('[role="checkbox"][aria-label="Added actual task"]')
    const progress = await page.$eval('[role="progressbar"]', node => node.getAttribute('aria-valuenow'))
    check('real keyboard-entered task can be added and completed without losing spaces', () => assert.equal(progress, '100'))
    await page.evaluate(() => window.gsRenderChecklist({title:'New source',items:[{label:'New supplied task',done:false}]}))
    await page.waitForSelector('button[aria-label="Edit New supplied task"]')
    const resetProgress = await page.$eval('[role="progressbar"]', node => node.getAttribute('aria-valuenow'))
    check('real browser payload replacement clears prior checklist completion', () => assert.equal(resetProgress, '0'))
    await page.evaluate(() => window.gsRenderApp('wizard',{type:'wizard',steps:[{title:'Actual step',fields:[{type:'slider',label:'Exact zero',value:0,min:0,max:10},{type:'text',label:'Real answer'}]}]}))
    await page.waitForSelector('input[placeholder="Real answer"]')
    await page.type('input[placeholder="Real answer"]', 'Actual keyboard answer')
    await click('Finish')
    await page.waitForSelector('[role="status"]')
    const wizard = await page.$eval('#app', node => node.innerText)
    check('actual wizard Finish shows the real zero and typed answer without an external-submission claim', () => { assert.match(wizard, /Completed locally/); assert.match(wizard, /Actual keyboard answer/); assert.match(wizard, /No data has been submitted/) })
    await page.evaluate(() => window.gsRenderApp('onboarding',{type:'onboarding',steps:[{title:'First actual step'},{title:'Second actual step'}]}))
    await click('Next'); await click('Get started')
    await page.waitForSelector('[role="status"]')
    const onboarding = await page.$eval('[role="status"]', node => node.innerText)
    check('actual onboarding final control completes locally', () => assert.match(onboarding, /Introduction completed locally/))
    await page.evaluate(() => window.gsRenderApp('booking',{type:'booking',slots:['09:00','10:00']}))
    await click('10:00'); await click('Save slot locally')
    await page.waitForSelector('[role="status"]')
    const booking = await page.$eval('[role="status"]', node => node.innerText)
    check('actual booking control saves only the selected local slot and never claims a reservation', () => assert.match(booking, /Saved locally: 10:00.*No reservation has been made/))
    const peer = await pageWithApp()
    await peer.addScriptTag({content:compiled.outputFiles[0].text})
    const voteSource = {surface:'vote',room:'real-browser-vote',items:['Ship now','Wait']}
    for (const viewer of [page,peer]) {
      await viewer.evaluate(payload=>window.gsRenderCollab(payload),voteSource)
      await viewer.waitForFunction(()=>document.body.textContent.includes('live · room'))
    }
    const initialVotes = await page.$$eval('button[aria-label^="Vote "]',nodes=>nodes.map(node=>node.textContent))
    check('two real viewers receive actual production-route snapshots without invented initial votes',()=>{assert.equal(initialVotes.length,2); for(const text of initialVotes) assert.match(text,/0$/)})
    collabWritesAvailable=false
    await page.click('button[aria-label="Vote Ship now"]')
    await page.waitForSelector('[role="alert"]')
    await page.waitForFunction(()=>!document.querySelector('button[aria-label="Vote Ship now"]').disabled)
    const refusedVote = await page.$eval('button[aria-label="Vote Ship now"]',node=>node.textContent)
    check('a real rejected HTTP write neither invents a vote nor locks its viewer out of retry',()=>{assert.match(refusedVote,/0$/);assert.doesNotMatch(refusedVote,/yours/)})
    collabWritesAvailable=true
    await page.click('button[aria-label="Vote Ship now"]')
    await peer.waitForFunction(()=>document.querySelector('button[aria-label="Vote Ship now"]')?.textContent.endsWith('1'))
    await page.waitForFunction(()=>document.querySelector('button[aria-label="Vote Ship now"]')?.disabled)
    const peerCount=await peer.$eval('button[aria-label="Vote Ship now"]',node=>node.textContent)
    check('retry applies a real SQLite-backed vote and broadcasts its actual count to another viewer',()=>assert.match(peerCount,/1$/))
    const todoSource={surface:'todo',room:'real-browser-todo',items:['Provided task']}
    for(const viewer of [page,peer]) {
      await viewer.evaluate(payload=>window.gsRenderCollab(payload),todoSource)
      await viewer.waitForFunction(()=>document.body.textContent.includes('live · room'))
    }
    await page.type('input[aria-label="new shared item"]','Actual shared keyboard task')
    collabWritesAvailable=false; await click('Add')
    await page.waitForSelector('[role="alert"]')
    const unsavedDraft=await page.$eval('input[aria-label="new shared item"]',node=>node.value)
    check('an actual failed shared-task write preserves every typed input character',()=>assert.equal(unsavedDraft,'Actual shared keyboard task'))
    collabWritesAvailable=true; await click('Add')
    await peer.waitForFunction(()=>document.body.textContent.includes('Actual shared keyboard task'))
    await page.waitForFunction(()=>document.querySelector('input[aria-label="new shared item"]')?.value==='')
    const sharedTask=await peer.$eval('#app',node=>node.innerText)
    check('confirmed shared-task retry clears only the saved draft and reaches the real second viewer',()=>{assert.match(sharedTask,/Provided task/);assert.match(sharedTask,/Actual shared keyboard task/)})
    const counterSource={surface:'counter',room:'real-source-start',start:12.5}
    for(const viewer of [page,peer]) {
      await viewer.evaluate(payload=>window.gsRenderCollab(payload),counterSource)
      await viewer.waitForFunction(()=>document.body.textContent.includes('live · room'))
    }
    await click('+1')
    await peer.waitForFunction(()=>document.body.textContent.includes('13.5'))
    const actualCounter=await peer.$eval('#app',node=>node.innerText)
    check('a real shared mutation atomically uses the actual fractional source start instead of an invented zero baseline',()=>assert.match(actualCounter,/13\.5/))
    await peer.evaluate(payload=>window.gsRenderCollab(payload),{...counterSource,start:9000})
    await peer.waitForFunction(()=>document.body.textContent.includes('live · room'))
    const rejoinedCounter=await peer.$eval('#app',node=>node.innerText)
    check('a later viewer cannot overwrite confirmed shared values with another configured source start',()=>{assert.match(rejoinedCounter,/13\.5/);assert.doesNotMatch(rejoinedCounter,/9000/)})
    collabReadsAvailable=false
    await page.evaluate(()=>window.gsRenderCollab({surface:'counter',room:'unavailable-browser-room',start:0}))
    await page.waitForFunction(()=>document.body.textContent.includes('offline · room'))
    const disconnected=await page.$$eval('button',nodes=>nodes.every(node=>node.disabled))
    check('a real unavailable initial event stream is labeled offline and cannot expose dead active shared controls',()=>assert.equal(disconnected,true))
    collabReadsAvailable=true
    await peer.close()
    const displaySource = '<div id="shadow-host"></div><canvas id="display-canvas" width="100" height="40"></canvas><svg data-custom-glass="true"><path d="M0 0L8 8"/></svg><script>window.marker=String.fromCodePoint(0x1F600);window.closedRoot=document.getElementById("shadow-host").attachShadow({mode:"closed"});closedRoot.innerHTML="<button>Shadow &#x1F600;</button><output>0</output>";window.shadowClicks=0;closedRoot.querySelector("button").onclick=()=>{shadowClicks++;closedRoot.querySelector("output").textContent=shadowClicks+marker;};const ctx=document.getElementById("display-canvas").getContext("2d");ctx.font="28px sans-serif";ctx.fillText(marker,0,30);ctx.strokeText(marker,40,30);</script>'
    await page.evaluate(source => window.gsRenderHTML(source), displaySource)
    await page.waitForSelector('iframe')
    const displayFrame = await (await page.$('iframe')).contentFrame()
    await displayFrame.waitForFunction(() => window.closedRoot?.querySelector('button').textContent === 'Shadow ')
    await displayFrame.evaluate(() => window.closedRoot.querySelector('button').click())
    await displayFrame.waitForFunction(() => window.closedRoot.querySelector('output').textContent === '1')
    const shadowState = await displayFrame.evaluate(() => ({counter:window.shadowClicks,marker:window.marker.codePointAt(0),button:window.closedRoot.querySelector('button').textContent}))
    check('actual closed-shadow-root controls run real logic and suppress static and dynamic emoji without changing machine strings', () => assert.deepEqual(shadowState, {counter:1,marker:128512,button:'Shadow '}))
    const canvasPolicy = await displayFrame.evaluate(() => {
      const ctx=document.getElementById('display-canvas').getContext('2d')
      const clear=ctx.getImageData(0,0,100,40).data.every(value=>value===0)
      ctx.fillStyle='#123456';ctx.fillRect(0,0,4,4)
      return {clear,vector:ctx.getImageData(0,0,1,1).data[3],glass:!!document.querySelector('svg[data-custom-glass] path')}
    })
    check('actual canvas fill/stroke suppress emoji text while preserving real canvas vectors and custom glass SVG', () => assert.deepEqual(canvasPolicy, {clear:true,vector:255,glass:true}))
    const displayContract = require('../shared-contracts/emoji-dom-policy.json')
    const originalDocument = await page.$eval('iframe', node => node.srcdoc)
    const withoutDisplayPolicy = originalDocument.replace('<script>'+displayContract.script+'</script>', '')
    assert.notEqual(withoutDisplayPolicy, originalDocument)
    await page.evaluate(source => { const frame=document.createElement('iframe');frame.id='display-counterfactual';frame.setAttribute('sandbox','allow-scripts');frame.srcdoc=source;document.body.append(frame) }, withoutDisplayPolicy)
    const displayCounterfactual = await (await page.$('#display-counterfactual')).contentFrame()
    await displayCounterfactual.waitForFunction(() => typeof window.marker === 'string')
    const unfilteredDisplay = await displayCounterfactual.evaluate(() => ({ink:document.getElementById('display-canvas').getContext('2d').getImageData(0,0,100,40).data.some(value=>value!==0),shadow:window.closedRoot.querySelector('button').textContent.includes(window.marker)}))
    check('removing only the production display policy really restores shadow emoji and canvas glyph pixels', () => assert.deepEqual(unfilteredDisplay, {ink:true,shadow:true}))
    await page.$eval('#display-counterfactual', node => node.remove())
    await page.evaluate(() => window.gsRenderApp('timer',{type:'timer',items:[{title:'Not time',price:25}]}))
    await page.waitForSelector('[role="timer"]')
    const unconfigured = await page.$eval('[role="timer"]', node => node.textContent)
    const startDisabled = await page.$$eval('button', nodes => nodes.find(node => node.textContent === 'Start').disabled)
    check('actual countdown never derives a 25-minute preset from a monetary price', () => { assert.equal(unconfigured, '00:00'); assert.equal(startDisabled, true) })
    await page.type('input[aria-label="Duration (seconds)"]', '2')
    await click('Start'); await click('Pause')
    const pausedTime = await page.$eval('[role="timer"]', node => node.textContent)
    await new Promise(resolve => setTimeout(resolve, 2100))
    const stillPaused = await page.$eval('[role="timer"]', node => node.textContent)
    check('actual countdown Pause freezes remaining time while real time passes', () => assert.equal(stillPaused, pausedTime))
    await click('Start')
    await page.waitForFunction(() => document.querySelector('[role="timer"]')?.textContent === '00:00', {timeout:4000})
    const finishedTimer = await page.$eval('#app', node => node.innerText)
    check('actual source-configured countdown reaches zero and never claims a background alarm', () => { assert.match(finishedTimer, /Done — take a break/); assert.match(finishedTimer, /No background alarm or notification is scheduled/) })
    await click('Reset')
    const resetTimer = await page.$eval('[role="timer"]', node => node.textContent)
    const editableTimer = await page.$eval('input[aria-label="Duration (seconds)"]', node => !node.disabled)
    check('actual Reset restores the precise user duration and re-enables configuration', () => { assert.equal(resetTimer, '00:02'); assert.equal(editableTimer, true) })
    await page.evaluate(() => {
      window.gsRenderApp('timer',{type:'timer',durationSeconds:5})
      window.__actualTimerNow = performance.now.bind(performance)
      window.__timerClockDelta = 0
      performance.now = () => window.__actualTimerNow() + window.__timerClockDelta
    })
    await page.waitForFunction(() => document.querySelector('[role="timer"]')?.textContent === '00:05')
    await click('Start')
    await page.evaluate(() => { window.__timerClockDelta = 2100 })
    await page.waitForFunction(() => document.querySelector('[role="timer"]')?.textContent === '00:03', {timeout:1000})
    const elapsedTimer = await page.$eval('[role="timer"]', node => node.textContent)
    check('actual timer uses elapsed deadline rather than counting delivered interval callbacks', () => assert.equal(elapsedTimer, '00:03'))
    await page.evaluate(() => { performance.now = window.__actualTimerNow; window.gsRenderApp('timer',{type:'timer'}) })
    await page.waitForFunction(() => document.querySelector('[role="timer"]')?.textContent === '00:00' && document.querySelector('input[aria-label="Duration (seconds)"]')?.value === '')
    const replacedTimer = await page.$eval('#app', node => node.innerText)
    check('same mounted timer source replacement cancels its old running clock and completion state', () => assert.doesNotMatch(replacedTimer, /Done —/))
    await page.evaluate(() => window.gsRenderApp('timer',{type:'timer',durationSeconds:-5}))
    await page.waitForSelector('[role="alert"]')
    const invalidTimer = await page.$eval('[role="alert"]', node => node.textContent)
    check('actual timer visibly refuses invalid source duration rather than clamping to a fake preset', () => assert.match(invalidTimer, /not clamped or taken from prices/))
    const payload = JSON.parse('{"welcome":"Welcome $& $$ </script> 😀 __GS_WORKER_CONTRACT_LITERAL__ __GS_TERMINAL_PAYLOAD_LITERAL__","commands":[{"name":"deploy","description":"Help, not execution","output":"deployed"}],"__proto__":{"polluted":true}}')
    await page.evaluate(json => window.gsRenderTerminal(JSON.parse(json)), JSON.stringify(payload))
    await page.waitForSelector('iframe[title="interactive view"]')
    const terminal = await (await page.$('iframe[title="interactive view"]')).contentFrame()
    await terminal.waitForSelector('#command')
    const terminalLog = () => terminal.$eval('#log', node => node.textContent)
    async function command(text) {
      await terminal.$eval('#command', node => { node.value = '' })
      await terminal.type('#command', text)
      await terminal.click('button[type="submit"]')
      return terminalLog()
    }
    async function execution(parent, expected) {
      await parent.waitForSelector('#execution iframe')
      const frame = await (await parent.$('#execution iframe')).contentFrame()
      await frame.waitForFunction(expected => document.getElementById('out')?.textContent.includes(expected), { timeout: 10000 }, expected)
      return { frame, output: await frame.$eval('#out', node => node.textContent) }
    }
    const proto = await terminal.evaluate(() => ({ own: Object.prototype.hasOwnProperty.call(payload, '__proto__'), polluted: ({}).polluted === true }))
    check('terminal parses metadata as data without object-literal prototype mutation', () => { assert.equal(proto.own, true); assert.equal(proto.polluted, false) })
    let text = await terminalLog()
    check('terminal metadata preserves dollars, script text and token-looking content without raw emoji', () => {
      assert.ok(text.includes('Welcome $& $$ </script> \\ud83d\\ude00 __GS_WORKER_CONTRACT_LITERAL__ __GS_TERMINAL_PAYLOAD_LITERAL__'))
      assert.ok(!text.includes('😀'))
    })
    await command('mkdir "my folder"'); await command('cd "my folder"'); await command('touch note.txt')
    text = await command('ls')
    check('terminal mkdir/cd/touch/ls mutate the actual virtual session', () => assert.ok(text.endsWith('note.txt')))
    await command('rm note.txt'); text = await command('ls')
    check('terminal rm actually removes a virtual file', () => assert.ok(text.endsWith('(empty)')))
    text = await command('cd missing')
    check('terminal refuses nonexistent directories rather than pretending to change them', () => assert.ok(text.endsWith('No such directory: /home/guest/my folder/missing')))
    text = await command('deploy')
    check('terminal model commands never fabricate execution or exit success', () => assert.ok(text.endsWith('deploy: not executed — no OS shell or command runtime is connected.')))
    await command('js ({answer:[1,2,3].reduce((a,b)=>a+b,0)})')
    let result = await execution(terminal, 'Completed.')
    check('terminal runs actual JavaScript objects and arrays, not an arithmetic approximation', () => assert.ok(result.output.includes('{"answer":6}')))
    await command('js console.log("$&", "$$", "</script>", String.fromCodePoint(128512)); return 42')
    result = await execution(terminal, 'Completed.')
    check('terminal actual execution preserves source dollars, script terminators and Unicode semantics', () => { assert.ok(result.output.includes('$& $$ </script> \\ud83d\\ude00')); assert.ok(result.output.includes('42')); assert.ok(!result.output.includes('😀')) })
    await command('js while(true) {}'); result = await execution(terminal, 'exceeded 2 seconds')
    check('terminal stops an actual infinite Worker', () => assert.ok(result.output.includes('Stopped:')))
    text = await command('python print(7)')
    check('terminal explicitly refuses unsupported runtimes', () => assert.ok(text.endsWith('python: not executed — no OS shell or command runtime is connected.')))
    check('terminal unsupported execution clears the stale successful/running frame', () => assert.equal(terminal.childFrames().length, 0))
    await command('js 5+7'); result = await execution(terminal, 'Completed.')
    check('terminal starts a fresh real Worker after a runaway', () => assert.ok(result.output.startsWith('12\n')))
    await terminal.click('#reset'); text = await command('ls')
    check('terminal reset discards all modified virtual files and cwd', () => assert.ok(text.endsWith('README.md')))
    text = await command('help')
    check('terminal labels help metadata and discloses unsupported capabilities', () => { assert.ok(text.includes('No Python, SQL, Bash, Node APIs or installed packages.')); assert.ok(text.includes('Help metadata only: deploy')) })
    for (const allowed of [false, true]) {
      const controlPage = await pageWithApp()
      await controlPage.addScriptTag({ content: compiled.outputFiles[0].text })
      const production = terminalSandboxDocument(payload)
      const document = allowed ? production.replaceAll("connect-src 'none'", `connect-src ${endpoint}`) : production
      await controlPage.evaluate(source => window.gsRenderHTML(source), document)
      await controlPage.waitForSelector('iframe[title="interactive view"]')
      const host = await (await controlPage.$('iframe[title="interactive view"]')).contentFrame()
      await host.waitForSelector('#command')
      await host.type('#command', 'js try{await fetch(' + JSON.stringify(endpoint + '/terminal-probe') + ');console.log("NETWORK REACHED")}catch{console.log("FETCH REFUSED")}await new Promise(r=>setTimeout(r,100));')
      await host.click('button[type="submit"]')
      const output = await execution(host, 'Completed.')
      check(allowed ? 'terminal policy-only counterfactual really reaches the endpoint' : 'terminal production CSP prevents any actual HTTP request', () => {
        assert.equal(probeHits['/terminal-probe'], allowed ? 1 : 0)
        assert.equal(output.output.includes('NETWORK REACHED'), allowed)
        if (!allowed) assert.ok(output.output.includes('FETCH REFUSED'))
      })
      await controlPage.close()
    }
    await page.screenshot({ path: path.join(os.tmpdir(), 'gs-terminal-browser-runtime.png'), fullPage: true })
    await page.evaluate(() => window.gsRenderMessage('| Name | Trend |\n| --- | --- |\n| Up | +0.2% |\n| Down | ↘1.5% |\n| Negative | −0.3% |'))
    await page.waitForSelector('tbody tr')
    await page.click('th:nth-child(2)')
    await page.waitForFunction(() => document.querySelector('tbody tr').textContent.includes('Down'))
    let firstRow = await page.$eval('tbody tr', row=>row.textContent)
    check('ordinary Markdown actual browser sort preserves downward arrow loss semantics', () => assert.ok(firstRow.includes('Down')))
    await page.click('th:nth-child(2)')
    await page.waitForFunction(() => document.querySelector('tbody tr').textContent.includes('Up'))
    firstRow = await page.$eval('tbody tr', row=>row.textContent)
    check('ordinary Markdown reverse sort exposes the actual gain before losses', () => assert.ok(firstRow.includes('Up')))
    await page.evaluate(() => window.gsRenderMessage('| # | Language | Popularity |\n| --- | --- | --- |\n' + Array.from({length:31},(_,index)=>`| ${index+1} | Language ${index+1} | ${index+1}% |`).join('\n')))
    await page.waitForSelector('[aria-label="Data view"] button')
    await (await page.$$('[aria-label="Data view"] button'))[1].click()
    await page.waitForFunction(() => !document.querySelector('table') && document.body.textContent.includes('31 points'))
    const actualChart = await page.$eval('#app', root=>root.textContent)
    check('ordinary browser Chart tab renders all 31 source observations with the real measure title', () => { assert.ok(actualChart.includes('Popularity')); assert.ok(actualChart.includes('31 points')) })
    await (await page.$$('[aria-label="Data view"] button'))[0].click()
    await page.waitForSelector('tbody tr')
    const actualRows = await page.$$eval('tbody tr', rows=>rows.length)
    check('ordinary browser Table tab restores the actual paginated source rows', () => assert.equal(actualRows, 5))
    await page.evaluate(() => window.gsRenderMessage('```chart\n' + JSON.stringify({
      type: 'hbar', title: 'Actual signed observations', labels: Array.from({length:13}, (_,index) => 'Source '+index),
      series: [
        {name:'Primary', data:Array.from({length:13}, (_,index)=>[index,index%2===0 ? -(index+1) : index+1])},
        {name:'Secondary', data:Array.from({length:13}, (_,index)=>[index,(index+1)*10])}
      ]
    }) + '\n```'))
    await page.waitForFunction(() => {
      const marks = Array.from(document.querySelectorAll('.recharts-bar-rectangle'))
      return marks.length === 26 && marks.every(node => { const box=node.getBBox(); return box.width>0 && box.height>0 })
    })
    const marks = await page.$$eval('.recharts-bar-rectangle', nodes => nodes.map(node => {
      const box = node.getBBox(); return {width:box.width,height:box.height}
    }))
    check('real horizontal chart paints every observation in both series beyond ten rows', () => {
      assert.equal(marks.length,26); assert.ok(marks.every(mark=>mark.width>0 && mark.height>0))
    })
    await page.waitForFunction(() => document.querySelectorAll('.recharts-label-list text').length === 26)
    const values = await page.$$eval('.recharts-label-list text', nodes => nodes.map(node=>node.textContent))
    check('real horizontal-chart labels retain every signed source value without clamping or discarding a series', () => {
      assert.deepEqual(values, [
        ...Array.from({length:13}, (_,index)=>String(index%2===0 ? -(index+1) : index+1)),
        ...Array.from({length:13}, (_,index)=>String((index+1)*10))
      ])
    })
    assert.equal(checks, 57, 'All required real-browser code, terminal and production widget-control cases must execute')
    console.log(`\n${checks} real-browser code, terminal and widget-control checks passed`)
  } finally { closeProbe(); await browser.close(); fs.rmSync(libs, { recursive: true, force: true }) }
}
main().catch(error => { console.error(error); process.exitCode = 1 })
