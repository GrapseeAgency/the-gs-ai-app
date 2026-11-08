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
  const browser = await puppeteer.launch({ executablePath: await chromium.executablePath(), args: chromium.args.filter(arg => arg !== '--disable-web-security'),
    headless: true, env: { ...process.env, LD_LIBRARY_PATH: path.join(libs, 'lib') + ':' + (process.env.LD_LIBRARY_PATH || '') } })
  let closeProbe = () => {}
  try {
    const probeHits = { '/code-probe': 0, '/terminal-probe': 0 }
    const server = http.createServer((request, response) => {
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
    assert.equal(checks, 27, 'All required real-browser code and terminal cases must execute')
    console.log(`\n${checks} real-browser code and terminal checks passed`)
  } finally { closeProbe(); await browser.close(); fs.rmSync(libs, { recursive: true, force: true }) }
}
main().catch(error => { console.error(error); process.exitCode = 1 })
