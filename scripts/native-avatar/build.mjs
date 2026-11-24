import { build } from 'esbuild'
import { chromium } from 'playwright'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { mkdir, readFile, writeFile, readdir } from 'node:fs/promises'
import { createHash } from 'node:crypto'

const here = dirname(fileURLToPath(import.meta.url))
const output = resolve(process.argv[2] ?? resolve(here,
  '../../android/app/build/generated/originalAvatarAssets/bot-avatar/native'))
const cache = resolve(here, '.cache')
await mkdir(cache, {recursive:true})
await mkdir(output, {recursive:true})
const bundle = resolve(cache, 'original-bot-avatars.js')
await build({
  entryPoints:[resolve(here,'entry.tsx')], outfile:bundle,
  platform:'browser', format:'iife', bundle:true, minify:true,
  jsx:'automatic', target:['chrome90'], legalComments:'inline',
})
const vendor = resolve(here,'node_modules/bot-avatars')
const packageJson = JSON.parse(await readFile(resolve(vendor,'package.json'),'utf8'))
if(packageJson.version!=='0.2.2') throw Error('Unexpected original package version')
for(const name of await readdir(vendor)){
  if(/^(LICENSE|NOTICE|COPYING)([.]|$)/i.test(name))
    await writeFile(resolve(output,'bot-avatars-'+name),await readFile(resolve(vendor,name)))
}
const sha=bytes=>createHash('sha256').update(bytes).digest('hex')
const metadata={
  source:'bot-avatars',version:packageJson.version,
  bundleSha256:sha(await readFile(bundle)),license:packageJson.license??null,
  frameSize:144,columns:12,frames:180,fps:30,animations:{}
}
const browser=await chromium.launch({
  headless:true,
  ...(process.env.GS_AVATAR_CHROMIUM_EXECUTABLE?{executablePath:process.env.GS_AVATAR_CHROMIUM_EXECUTABLE}:{}),
  args:['--disable-dev-shm-usage','--disable-background-timer-throttling',
    '--disable-renderer-backgrounding','--disable-backgrounding-occluded-windows']
})
const html='<!doctype html><meta charset="utf-8"><style>html,body,#root{margin:0;padding:0;width:100%;height:100%;background:transparent;overflow:hidden}#root{display:flex;align-items:center;justify-content:center}#root canvas{display:block}</style><div id="root"></div>'
const decode=value=>Buffer.from(value.split(',')[1],'base64')
try {
  for(const type of ['clover','flower','triangle','square']){
    for(const state of ['default','working']){
      const page=await browser.newPage({viewport:{width:128,height:128},deviceScaleFactor:2,reducedMotion:'no-preference'})
      try{
        await page.setContent(html)
        await page.addScriptTag({path:bundle})
        await page.evaluate(config=>window.gsAvatarUpdate(config),{type,state})
        await page.waitForFunction(expected=>{
          const c=document.querySelector('canvas')
          return c&&c.dataset.state===expected&&c.width===144&&c.height===144&&
            Number(getComputedStyle(c).opacity)>=.99
        },state,{timeout:30000})
        await page.waitForTimeout(250)
        const recording=await page.evaluate(async ({state})=>{
          const c=document.querySelector('canvas')
          const box=c.getBoundingClientRect()
          if(box.left<0||box.top<0||box.right>innerWidth||box.bottom>innerHeight)
            throw Error('Original canvas is clipped in capture viewport')
          const size=144,cols=12,total=240,count=180,interval=1000/30
          const capture=document.createElement('canvas')
          capture.width=size*cols
          capture.height=size*Math.ceil(total/cols)
          const ctx=capture.getContext('2d',{willReadFrequently:true})
          const hashes=[],samples=[]
          const start=performance.now()
          let n=0
          await new Promise((ok,fail)=>{
            function tick(now){
              try{
                if(performance.now()-start>16000)throw Error('Vendor capture throttled')
                if(now-start+1>=n*interval){
                  const x=(n%cols)*size,y=Math.floor(n/cols)*size
                  // Copy the original complete overscan canvas and its alpha.
                  // Never synthesize any shape, eye, shading or pose.
                  ctx.drawImage(c,x,y)
                  const data=ctx.getImageData(x,y,size,size).data
                  let hash=2166136261,visible=0
                  const sample=[]
                  for(let i=0;i<data.length;i+=16){
                    hash=Math.imul(hash^data[i],16777619)
                    hash=Math.imul(hash^data[i+1],16777619)
                    hash=Math.imul(hash^data[i+2],16777619)
                    hash=Math.imul(hash^data[i+3],16777619)
                    if(data[i+3]>32)visible++
                    if(i%256===0)sample.push(data[i],data[i+1],data[i+2],data[i+3])
                  }
                  if(visible<100)throw Error('Blank source frame '+n)
                  hashes.push(hash>>>0);samples.push(sample);n++
                }
                if(n===total)ok();else requestAnimationFrame(tick)
              }catch(e){fail(e)}
            }
            requestAnimationFrame(tick)
          })
          const captureFps=(total-1)*1000/(performance.now()-start)
          if(captureFps<26)throw Error('Original capture too slow: '+captureFps)
          // Pick a contiguous six-second original recording with the least
          // visible seam. No interpolated or invented animation frames.
          let best=0,bestDistance=Infinity
          for(let j=0;j<=total-count;j++){
            let distance=0
            const a=samples[j],b=samples[j+count-1]
            for(let k=0;k<a.length;k++)distance+=Math.abs(a[k]-b[k])
            if(distance<bestDistance){bestDistance=distance;best=j}
          }
          const distinct=new Set(hashes.slice(best,best+count)).size
          if(distinct<(state==='working'?135:50))throw Error('Too few distinct original frames: '+distinct)
          const atlas=document.createElement('canvas')
          atlas.width=size*cols;atlas.height=size*(count/cols)
          const a=atlas.getContext('2d')
          for(let i=0;i<count;i++){
            const j=best+i
            a.drawImage(capture,(j%cols)*size,Math.floor(j/cols)*size,size,size,
              (i%cols)*size,Math.floor(i/cols)*size,size,size)
          }
          const poster=document.createElement('canvas');poster.width=poster.height=size
          poster.getContext('2d').drawImage(atlas,0,0,size,size,0,0,size,size)
          return {atlas:atlas.toDataURL('image/png'),poster:poster.toDataURL('image/png'),
            distinct,captureFps,sourceFrameStart:best,loopBoundaryDistance:bestDistance}
        },{state})
        const key=type+'-'+state
        const atlas=decode(recording.atlas),poster=decode(recording.poster)
        await writeFile(resolve(output,key+'-atlas.png'),atlas)
        await writeFile(resolve(output,key+'-poster.png'),poster)
        metadata.animations[key]={
          distinctFrames:recording.distinct,sourceFps:recording.captureFps,
          sourceFrameStart:recording.sourceFrameStart,loopBoundaryDistance:recording.loopBoundaryDistance,
          atlasSha256:sha(atlas),posterSha256:sha(poster)
        }
        console.log('Original',key,recording.distinct+'/180 unique frames, capture FPS',recording.captureFps.toFixed(1))
      }finally{await page.close()}
    }
  }
  await writeFile(resolve(output,'manifest.json'),JSON.stringify(metadata,null,2)+'\n')
}finally{await browser.close()}
