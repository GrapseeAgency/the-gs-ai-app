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
  await frame.type('[aria-label="New task"]','Survives reload $&');await frame.click('button[type="submit"]')
  await page.waitForFunction(()=>Object.entries(localStorage).some(([k,v])=>k.startsWith('gs.react-preview.v1:message-a:')&&v.includes('Survives reload $&')))
  await page.reload();await page.addScriptTag({content:compiled.outputFiles[0].text})
  frame=await run(taskSource,'message-a');await running(frame)
  assert.equal(await frame.$eval('[aria-label="Task title"]',n=>n.value),'Survives reload $&')
  check('Task state survives an actual browser page reload with its real response identity',()=>{})
  const protoSource="export default function App(){return <p id='prototype'>{localStorage.getItem('__proto__') || 'Not stored'}</p>}"
  frame=await run(protoSource,'prototype');await running(frame)
  await frame.evaluate(()=>localStorage.setItem('__proto__','Stored $& </script>'))
  await page.waitForFunction(()=>Object.entries(localStorage).some(([k,v])=>k.startsWith('gs.react-preview.v1:prototype:')&&v.includes('Stored $&')))
  frame=await run(protoSource,'prototype');await running(frame)
  assert.equal(await frame.$eval('#prototype',n=>n.textContent),'Stored $& </script>')
  check('Prototype-named storage keys and dollar/script values survive real broker persistence and remount',()=>{})
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
  await page.evaluate(()=>window.gsRenderMessage('## Next actions\n- [ ] Original task\n  Supplied source detail\n- [x] Keep completed','ordinary-tasks'))
  await page.waitForSelector('button[role="checkbox"]')
  assert.ok((await page.$eval('#app',n=>n.textContent)).includes('1/2 completed'))
  await page.focus('button[aria-label="Complete task: Original task"]');await page.keyboard.press('Space')
  await page.waitForFunction(()=>document.querySelector('#app').textContent.includes('2/2 completed'))
  check('Ordinary Markdown checkboxes are real keyboard-operable controls with derived progress',()=>{})
  await page.click('[aria-label="Edit task: Original task"]');await page.waitForSelector('input[aria-label="Task text"]')
  await page.click('input[aria-label="Task text"]',{clickCount:3});await page.type('input[aria-label="Task text"]','Edited source');await page.keyboard.press('Enter')
  await page.waitForSelector('[aria-label="Edit task: Edited source"]')
  assert.ok((await page.$eval('#app',n=>n.textContent)).includes('Supplied source detail'))
  assert.ok((await page.$eval('#app',n=>n.textContent)).includes('2/2 completed'))
  check('Editing an ordinary task by keyboard preserves its real completion and supplied detail',()=>{})
  await page.click('[aria-label="Delete task: Keep completed"]')
  await page.waitForFunction(()=>document.querySelector('#app').textContent.includes('1/1 completed'))
  check('Deleting a source task actually removes it and recomputes ordinary response progress',()=>{})
  async function addOrdinaryTask(text) {
   const add=await page.waitForFunction(()=>[...document.querySelectorAll('button')].find(b=>b.textContent.trim()==='Add Task'))
   await add.asElement().click();await page.waitForSelector('input[aria-label="Task text"]');await page.type('input[aria-label="Task text"]',text);await page.keyboard.press('Enter');await page.waitForSelector('[aria-label="Edit task: '+text+'"]')
  }
  await addOrdinaryTask('First added task');await page.click('[aria-label="Complete task: First added task"]')
  await addOrdinaryTask('Second added task');await page.click('[aria-label="Delete task: First added task"]')
  assert.equal(await page.$eval('[aria-label="Complete task: Second added task"]',n=>n.getAttribute('aria-checked')),'false')
  assert.equal(await page.$eval('[aria-label="Complete task: Edited source"]',n=>n.getAttribute('aria-checked')),'true')
  check('Deleting an added task cannot transfer its completion to its next neighbor',()=>{})
  await page.click('[aria-label="Edit task: Second added task"]');await page.type('input[aria-label="Task text"]',' unsaved');await page.keyboard.press('Escape')
  await page.waitForFunction(()=>!document.querySelector('input[aria-label="Task text"]'))
  assert.ok(await page.$('[aria-label="Edit task: Second added task"]'))
  check('Escape cancels an ordinary task draft without mutating source or completion',()=>{})
  await page.evaluate(()=>window.gsRenderMessage('## Next actions\n- [ ] Replacement source','ordinary-tasks'))
  await page.waitForSelector('[aria-label="Complete task: Replacement source"]')
  assert.ok((await page.$eval('#app',n=>n.textContent)).includes('0/1 completed'));assert.equal(await page.$('[aria-label="Edit task: Edited source"]'),null)
  check('Replacing ordinary source resets old task edits and checked state rather than contaminating another answer',()=>{})
  await page.screenshot({path:'/home/user/gs-ordinary-task-browser.png',fullPage:true})
  console.log(`\n${checks} actual React and ordinary-response task acceptance checks passed`)
 } catch(error) {
  if(page) {console.error(await page.evaluate(()=>({host:document.body.innerText.slice(-1200),frames:document.querySelectorAll('iframe').length})).catch(()=>''));for(const frame of page.frames().slice(1))console.error(await frame.evaluate(()=>document.body.innerText.slice(0,2000)).catch(()=>''))}
  throw error
 } finally {if(server){server.closeAllConnections();server.close()}if(browser)await browser.close();fs.rmSync(libs,{recursive:true,force:true})}
}
main().catch(error=>{console.error(error);process.exitCode=1})
