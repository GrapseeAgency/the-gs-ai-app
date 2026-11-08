/* Requires the compiled probe plus pinned Chromium/puppeteer-core/esbuild.
 * Real browser, actual React controls and clipboard. No fabricated worker or
 * fetch responses are used to claim denial: the allowed counterfactual MUST
 * reach the intercepted request, while production CSP must prevent it. */
const assert = require('node:assert/strict')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const zlib = require('node:zlib')
const { execFileSync } = require('node:child_process')
const chromium = require('@sparticuz/chromium')
const puppeteer = require('puppeteer-core')
const esbuild = require('esbuild')
require('../dist/probe/scripts/probe-alias-hook')
const React = require('react')
const { renderToString } = require('react-dom/server')
const { HtmlBlock } = require('../dist/probe/src/components/genui/blocks/HtmlBlock')
const { javascriptSandboxDocument } = require('../dist/probe/src/lib/code/javascript-sandbox')
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
  const browser = await puppeteer.launch({ executablePath: await chromium.executablePath(), args: chromium.args,
    headless: true, env: { ...process.env, LD_LIBRARY_PATH: path.join(libs, 'lib') + ':' + (process.env.LD_LIBRARY_PATH || '') } })
  try {
    const requests = []
    const context = browser.defaultBrowserContext()
    await context.overridePermissions('https://gs.test', ['clipboard-read', 'clipboard-write'])
    async function pageWithApp() {
      const page = await browser.newPage()
      await page.setViewport({ width: 430, height: 920 })
      await page.setRequestInterception(true)
      page.on('request', request => {
        requests.push(request.url())
        request.respond({ status: 200, contentType: request.url().includes('forbidden.example') ? 'application/json' : 'text/html',
          headers: { 'Access-Control-Allow-Origin': '*' }, body: request.url().includes('forbidden.example') ? '{"allowed":true}' : '<!doctype html><div id="app"></div>' }).catch(() => {})
      })
      await page.goto('https://gs.test/')
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
    const networkSource = 'try{await fetch("https://forbidden.example/secret");console.log("NETWORK REACHED")}catch{console.log("FETCH REFUSED")}await new Promise(r=>setTimeout(r,100));'
    await mount(networkSource); await click('Run Code')
    const denied = await frameOutput('Completed.')
    check('real browser Worker reports an actual CSP violation', () => { assert.ok(denied.output.includes('BLOCKED connect-src')); assert.ok(denied.output.includes('FETCH REFUSED')); assert.ok(!denied.output.includes('NETWORK REACHED')) })
    check('production CSP prevents the request before interception', () => assert.equal(requests.filter(url => url.includes('forbidden.example')).length, 0))
    // Causal negative control: only the connection policy changes. If requests
    // were merely offline/blocked by the test infrastructure this MUST fail.
    const allowedPage = await pageWithApp()
    const permissive = javascriptSandboxDocument(networkSource).replace("connect-src 'none'", 'connect-src https://forbidden.example')
    await allowedPage.setContent(renderToString(React.createElement(HtmlBlock, { source: permissive })))
    const allowedFrame = await (await allowedPage.$('iframe')).contentFrame()
    await allowedFrame.waitForFunction(() => document.getElementById('out')?.textContent.includes('NETWORK REACHED'), { timeout: 10000 })
    check('removing just CSP denial actually reaches the controlled network endpoint', () => assert.equal(requests.filter(url => url.includes('forbidden.example')).length, 1))
    await page.screenshot({ path: path.join(os.tmpdir(), 'gs-code-browser-runtime.png'), fullPage: true })
    console.log(`\n${checks} real-browser code checks passed`)
  } finally { await browser.close(); fs.rmSync(libs, { recursive: true, force: true }) }
}
main().catch(error => { console.error(error); process.exitCode = 1 })
