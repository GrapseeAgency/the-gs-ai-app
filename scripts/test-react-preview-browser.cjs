/* Real Chromium, production MessageContent -> CodeBlock -> ReactPreview.
 * No React/compiler/storage mocks. Native WebView acceptance is separate. */
const assert = require('node:assert/strict')
const fs = require('node:fs'), os = require('node:os'), path = require('node:path')
const http = require('node:http'), zlib = require('node:zlib')
const { execFileSync } = require('node:child_process')
const chromium = require('@sparticuz/chromium'), puppeteer = require('puppeteer-core')
const esbuild = require('esbuild')
let checks = 0
function check(name, fn) { fn(); checks++; console.log('PASS React browser:', name) }
const taskSource = require('../shared-contracts/react-preview-acceptance.json').source
async function main() {
 const libs=fs.mkdtempSync(path.join(os.tmpdir(),'gs-react-browser-'))
 let browser, server, page
 try {
  const bin=path.resolve(path.dirname(require.resolve('@sparticuz/chromium')),'../../bin')
  const archive=path.join(libs,'al2023.tar');fs.writeFileSync(archive,zlib.brotliDecompressSync(fs.readFileSync(path.join(bin,'al2023.tar.br'))));execFileSync('tar',['-xf',archive,'-C',libs])
  const compiled=await esbuild.build({entryPoints:['scripts/browser-code-entry.tsx'],bundle:true,write:false,platform:'browser',format:'iife',jsx:'automatic',tsconfig:'tsconfig.json',define:{'process.env.NODE_ENV':'"production"'},logLevel:'silent'})
  let networkHits=0
  server=http.createServer((req,res)=>{if(req.url==='/react-probe')networkHits++;res.writeHead(200,{'Content-Type':'text/html','Access-Control-Allow-Origin':'*'});res.end('<!doctype html><div id="app"></div>')})
  await new Promise(r=>server.listen(0,'127.0.0.1',r));const endpoint='http://127.0.0.1:'+server.address().port
  browser=await puppeteer.launch({args:chromium.args.filter(arg=>arg!=='--disable-web-security' && arg!=='--allow-running-insecure-content'),executablePath:await chromium.executablePath(),headless:true,env:{...process.env,LD_LIBRARY_PATH:path.join(libs,'lib')+':'+(process.env.LD_LIBRARY_PATH||'')}})
  page=await browser.newPage();await page.goto(endpoint);await page.addScriptTag({content:compiled.outputFiles[0].text})
  async function run(source, scope='message-a', language='tsx') {
   await page.evaluate((source,scope,language)=>window.gsRenderMessage('```'+language+'\n'+source+'\n```',scope),source,scope,language)
   await page.waitForSelector('[data-code-block] button')
   const run=await page.waitForFunction(()=>[...document.querySelectorAll('[data-code-block] button')].find(b=>b.textContent.trim().startsWith('Run')))
   await run.asElement().click();await page.waitForSelector('iframe')
   const frame=await (await page.$('iframe')).contentFrame()
   await frame.waitForFunction(()=>/running|error/.test(document.getElementById('status')?.textContent||''),{timeout:20000})
   return frame
  }
  async function running(frame) { const state=await frame.$eval('#status',n=>n.textContent);const error=await frame.$eval('#error',n=>n.textContent);assert.equal(state,'React preview running',error) }
  let frame=await run(taskSource)
  await running(frame)
  check('production TSX fence compiles, imports hooks and actually mounts without a React default import',()=>{})
  await frame.waitForFunction(()=>getComputedStyle(document.querySelector('section')).paddingTop===getComputedStyle(document.documentElement).fontSize,{timeout:15000})
  assert.equal(await frame.$eval('h1',n=>getComputedStyle(n).fontWeight),'700')
  check('bundled Tailwind really computes p-4 one-rem padding and font-bold',()=>{})
  await frame.type('[aria-label="New task"]','Review real behavior');await frame.click('button[type="submit"]')
  await frame.waitForFunction(()=>document.querySelector('#count').textContent==='0/1 complete')
  check('Add updates the actual task list and derived completion count',()=>{})
  await frame.click('input[type="checkbox"]');await frame.waitForFunction(()=>document.querySelector('#count').textContent==='1/1 complete')
  check('Toggle changes state and recomputes completion rather than displaying a canned result',()=>{})
  await frame.click('[aria-label="Task title"]',{clickCount:3});await frame.type('[aria-label="Task title"]','Edited task')
  await frame.waitForFunction(()=>JSON.parse(localStorage.getItem('tasks'))[0].title==='Edited task')
  await page.waitForFunction(()=>Object.entries(localStorage).some(([k,v])=>k.startsWith('gs.react-preview.v1:message-a:')&&v.includes('Edited task')))
  check('Edit is persisted through the opaque-origin broker to the actual host storage',()=>{})
  frame=await run(taskSource);await running(frame)
  assert.equal(await frame.$eval('[aria-label="Task title"]',n=>n.value),'Edited task');assert.equal(await frame.$eval('input[type="checkbox"]',n=>n.checked),true)
  check('Rerun creates a fresh React mount and restores genuine edits and completion state',()=>{})
  frame=await run(taskSource,'message-b');await running(frame)
  assert.equal(await frame.$eval('#count',n=>n.textContent),'0/0 complete')
  check('A different assistant response cannot read the first response task storage',()=>{})
  frame=await run(taskSource,'message-a');await running(frame);await frame.click('li button')
  await frame.waitForFunction(()=>document.querySelector('#count').textContent==='0/0 complete')
  check('Delete removes the actual task and updates the derived count',()=>{})
  frame=await run(taskSource,'message-a');await running(frame);assert.equal(await frame.$$eval('li',n=>n.length),0)
  check('Deletion survives rerun, not just the first DOM render',()=>{})
  const semantics = '$& $$ $` $\' </script> __GS_REACT_STORAGE_BOOTSTRAP__'
  frame=await run('export default function App(){return <p id="semantics">{'+JSON.stringify(semantics)+'}</p>}','literal-source','jsx');await running(frame)
  assert.equal(await frame.$eval('#semantics',n=>n.textContent),semantics)
  check('Real JSX execution preserves dollars, script terminators and template-token source semantics',()=>{})
  frame=await run("export default function App(){return <p id='emoji'>"+String.fromCodePoint(128512)+" Clean</p>}",'emoji','react');await running(frame)
  await frame.waitForFunction(()=>!document.querySelector('#emoji').textContent.includes(String.fromCodePoint(128512)))
  check('React-generated visible text obeys the actual zero-emoji DOM policy',()=>{})
  for (const [name,source,expected] of [
   ['syntax errors','export default function App( { return <div>broken</div> }',/Unexpected (?:token|keyword)/],
   ['missing default export','export function App(){return <p>source</p>}',/default React component/],
   ['unbundled imports',"import secret from 'unbundled-library';export default function App(){return <p>{secret}</p>}",/Unsupported dependency/],
   ['render exceptions',"export default function App(){throw Error('Actual render failure')}",/Actual render failure/]
  ]) { frame=await run(source,'error-'+name);assert.equal(await frame.$eval('#status',n=>n.textContent),'React preview error');assert.match(await frame.$eval('#error',n=>n.textContent),expected);check(name+' are actually reported, never simulated success',()=>{}) }
  frame=await run(`export default function App(){return <button id="network" onClick={async()=>{try{await fetch(${JSON.stringify(endpoint+'/react-probe')});document.getElementById('network').textContent='Reached'}catch{document.getElementById('network').textContent='Denied'}}}>Try network</button>}`,'network');await running(frame);await frame.click('#network');await frame.waitForFunction(()=>document.querySelector('#network').textContent==='Denied');assert.equal(networkHits,0)
  check('React execution cannot reach the real HTTP endpoint under production CSP',()=>{})
  const isolation=await frame.evaluate(()=>{let parentDenied=false;try{parent.document.body}catch{parentDenied=true};return{parentDenied,origin:location.origin,sandbox:typeof window.Android,webkit:typeof window.webkit}})
  assert.equal(isolation.parentDenied,true);assert.equal(isolation.sandbox,'undefined');assert.equal(isolation.webkit,'undefined')
  check('The actual iframe has no parent DOM or native application bridge access',()=>{})
  await page.screenshot({path:'/home/user/gs-react-preview-browser.png',fullPage:true})
  console.log(`\n${checks} actual React browser acceptance checks passed`)
 } catch(error) {
  if(page) {console.error(await page.evaluate(()=>({host:document.body.innerText.slice(-1200),frames:document.querySelectorAll('iframe').length})).catch(()=>''));for(const frame of page.frames().slice(1))console.error(await frame.evaluate(()=>document.body.innerText.slice(0,2000)).catch(()=>''))}
  throw error
 } finally {if(server){server.closeAllConnections();server.close()}if(browser)await browser.close();fs.rmSync(libs,{recursive:true,force:true})}
}
main().catch(error=>{console.error(error);process.exitCode=1})
