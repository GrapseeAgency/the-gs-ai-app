/* Real Chromium: verify the production Argent overlay runtime, not a
 * hardcoded screenshot or a mocked canvas. */
const assert=require('node:assert/strict')
const fs=require('node:fs')
const os=require('node:os')
const path=require('node:path')
const zlib=require('node:zlib')
const {execFileSync}=require('node:child_process')
const chromium=require('@sparticuz/chromium')
const puppeteer=require('puppeteer-core')
const esbuild=require('esbuild')

async function main(){
  const root=path.resolve(__dirname,'..')
  const libs=fs.mkdtempSync(path.join(os.tmpdir(),'gs-overlay-browser-'))
  const bin=path.resolve(path.dirname(require.resolve('@sparticuz/chromium')),'../../bin')
  const archive=path.join(libs,'al2023.tar')
  fs.writeFileSync(archive,zlib.brotliDecompressSync(fs.readFileSync(path.join(bin,'al2023.tar.br'))))
  execFileSync('tar',['-xf',archive,'-C',libs])
  const compiled=await esbuild.build({
    stdin:{contents:"import React from 'react'; import {createRoot} from 'react-dom/client'; import {ReflectiveOverlayRuntime} from './src/components/ui/ReflectiveOverlayRuntime'; createRoot(document.getElementById('runtime')).render(React.createElement(ReflectiveOverlayRuntime));",resolveDir:root,sourcefile:'overlay-fixture.tsx',loader:'tsx'},
    bundle:true,write:false,platform:'browser',format:'iife',jsx:'automatic',
    define:{'process.env.NODE_ENV':'"production"'},logLevel:'silent'
  })
  const browser=await puppeteer.launch({executablePath:await chromium.executablePath(),
    args:[...chromium.args.filter(a=>a!=='--disable-web-security'&&a!=='--disable-gpu'&&!a.startsWith('--use-angle=')&&!a.startsWith('--use-gl=')),
      '--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader'],
    headless:true,env:{...process.env,LD_LIBRARY_PATH:path.join(libs,'lib')+':'+(process.env.LD_LIBRARY_PATH||'')}})
  try{
    const page=await browser.newPage()
    const source=fs.readFileSync(path.join(root,'src/app/globals.css'),'utf8')
    const css=source.slice(source.indexOf('/* App-owned transient glass.'))
    assert.ok(css.includes('.gs-reflective-overlay'))
    await page.setContent('<style>:root{--popover:#f8efe8}body{margin:20px;background:#d9cece}'+css+'</style><div id="runtime"></div><button id="open">Open</button><div id="mount"></div>')
    await page.evaluate(()=>{
      document.querySelector('#open').onclick=()=>{
        const el=document.createElement('div')
        el.className='gs-reflective-overlay';el.id='panel';el.style.cssText='width:248px;height:320px;border-radius:22px;overflow:auto;'
        el.innerHTML='<button id="close">Close</button><div style="height:700px">Menu content</div>'
        el.querySelector('#close').onclick=()=>el.remove()
        document.querySelector('#mount').appendChild(el)
      }
    })
    await page.addScriptTag({content:compiled.outputFiles[0].text})
    await page.click('#open')
    const capable=await page.evaluate(()=>{const c=document.createElement('canvas');const gl=c.getContext('webgl2');const ok=!!gl;gl?.getExtension('WEBGL_lose_context')?.loseContext();return ok})
    if(!capable)throw Error('Real WebGL2 not available in this browser; shader test unverified')
    await page.waitForSelector('#panel[data-gs-argent-ready]',{timeout:15000})
    const result=await page.evaluate(async()=>{
      const el=document.querySelector('#panel')
      const texture=el.style.getPropertyValue('--gs-argent-edge-image')
      const img=new Image();img.src=texture.slice(5,-2);await img.decode()
      const c=document.createElement('canvas');c.width=img.width;c.height=img.height
      const x=c.getContext('2d');x.drawImage(img,0,0)
      const center=x.getImageData(Math.floor(c.width/2),Math.floor(c.height/2),1,1).data[3]
      const top=x.getImageData(0,0,c.width,Math.min(8,c.height)).data
      let edge=0;for(let n=3;n<top.length;n+=4)if(top[n]>0)edge++
      el.scrollTop=120
      return {center,edge,scrolled:el.scrollTop>0,stable:el.style.getPropertyValue('--gs-argent-edge-image')===texture,texture,blur:getComputedStyle(el).backdropFilter}
    })
    assert.equal(result.center,0,'center must remain fully transparent')
    assert.ok(result.edge>10,'actual rim must contain pixels')
    assert.ok(result.scrolled&&result.stable,'rim must not scroll with menu content')
    assert.match(result.blur,/blur/)
    await page.evaluate(()=>{document.querySelector('#panel').style.width='300px'})
    await page.waitForFunction(old=>{const p=document.querySelector('#panel');return p.hasAttribute('data-gs-argent-ready')&&p.style.getPropertyValue('--gs-argent-edge-image')!==old},{timeout:15000},result.texture)
    await page.screenshot({path:'/tmp/gs-reflective-overlay.png'})
    await page.evaluate(()=>{document.querySelector('#close').click()})
    await page.waitForFunction(()=>!document.querySelector('#panel')&&!document.querySelector('canvas[aria-hidden="true"]'))
    console.log('PASS actual Argent raster: transparent center, perimeter pixels, glass, scroll, resize, dismissal and no live shader canvas')
  }finally{await browser.close();fs.rmSync(libs,{recursive:true,force:true})}
}
main().catch(error=>{console.error(error);process.exitCode=1})
