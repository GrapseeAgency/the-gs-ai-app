'use client'

import { useEffect } from 'react'

const selector = '.gs-reflective-overlay, .gs-reflective-control, [data-slot="navigation-menu"][data-viewport="false"] [data-slot="navigation-menu-content"]'

/** A static, rim-only raster of the installed ArgentUI native shader.
 * The original element owns the frosted body, focus, layout and scrolling.
 * No live canvas or animation loop remains behind a visible panel. */
export function ReflectiveOverlayRuntime() {
  useEffect(() => {
    if (typeof ResizeObserver === 'undefined' || typeof MutationObserver === 'undefined') return
    const elements = new Map<HTMLElement, string>()
    const pending = new Set<HTMLElement>()
    const cache = new Map<string, string>()
    const contrast = window.matchMedia('(forced-colors: active), (prefers-contrast: more)')
    let frame = 0, alive = true, working = false
    let engine: Promise<typeof import('argentui')> | undefined

    function reset(el: HTMLElement) {
      el.style.removeProperty('--gs-argent-edge-image')
      el.removeAttribute('data-gs-argent-ready')
    }
    function schedule(el: HTMLElement) {
      pending.add(el)
      if (!frame && !working) frame = requestAnimationFrame(flush)
    }
    function visit(node: Node, add: boolean) {
      if (!(node instanceof Element)) return
      const apply = (el: Element) => {
        if (!(el instanceof HTMLElement)) return
        if (add && !elements.has(el)) {
          elements.set(el, '')
          resize.observe(el)
          schedule(el)
        } else if (!add && !el.isConnected) {
          reset(el); elements.delete(el); pending.delete(el); resize.unobserve(el)
        }
      }
      if (node.matches(selector)) apply(node)
      node.querySelectorAll(selector).forEach(apply)
    }
    function corners(style: CSSStyleDeclaration,w:number,h:number) {
      return [style.borderTopLeftRadius,style.borderTopRightRadius,
        style.borderBottomRightRadius,style.borderBottomLeftRadius].map(value=>{
        const [x,y=x]=value.trim().split(/\s+/)
        const px=(s:string,d:number)=>Math.max(0,Math.min(d/2,
          (Number.parseFloat(s)||0)*(s.endsWith('%')?d/100:1)))
        return {x:px(x,w),y:px(y,h)}
      })
    }
    function install(el:HTMLElement, value:string) {
      el.style.setProperty('--gs-argent-edge-image',value)
      el.setAttribute('data-gs-argent-ready','')
    }
    async function paint(el:HTMLElement) {
      if (!alive||!el.isConnected||!elements.has(el)) return
      if (contrast.matches) {reset(el);elements.set(el,'');return}
      const w=el.offsetWidth,h=el.offsetHeight
      if(w<16||h<16||w>1600||h>1600){reset(el);elements.set(el,'');return}
      const dark=Boolean(el.closest('.dark, [data-theme="dark"]'))
      const radius=corners(getComputedStyle(el),w,h)
      const key=JSON.stringify([w,h,dark,radius])
      if(elements.get(el)===key)return
      elements.set(el,key)
      reset(el)
      const cached=cache.get(key)
      if(cached){install(el,cached);return}
      let canvas:HTMLCanvasElement|undefined
      let mount:{destroy():void}|null=null
      let gl:WebGL2RenderingContext|null=null
      try {
        const {mountMetal}=await (engine??=import('argentui'))
        if(!alive||!el.isConnected||elements.get(el)!==key||contrast.matches)return
        canvas=document.createElement('canvas')
        canvas.setAttribute('aria-hidden','true')
        Object.assign(canvas.style,{position:'fixed',left:'-10000px',top:'0',
          width:w+'px',height:h+'px',visibility:'hidden',pointerEvents:'none'})
        document.body.appendChild(canvas)
        gl=canvas.getContext('webgl2',{antialias:true,preserveDrawingBuffer:true})
        if(!gl)return
        mount=mountMetal(canvas,{
          light:dark?'#dbdbdb':'#999999',dark:dark?'#949494':'#595959',
          repetition:1.2,angle:40,softness:.48,dispersion:0,distortion:.05,scale:1.6,speed:0,
        })
        if(!mount)return
        const output=document.createElement('canvas')
        const mask=document.createElement('canvas')
        output.width=mask.width=canvas.width;output.height=mask.height=canvas.height
        const ctx=output.getContext('2d'),alpha=mask.getContext('2d')
        if(!ctx||!alpha)return
        ctx.drawImage(canvas,0,0)
        alpha.scale(mask.width/w,mask.height/h)
        const tint=alpha.createLinearGradient(0,0,w,h)
        tint.addColorStop(0,dark?'rgba(0,0,0,.48)':'rgba(0,0,0,.40)')
        tint.addColorStop(.45,dark?'rgba(0,0,0,.14)':'rgba(0,0,0,.10)')
        tint.addColorStop(1,dark?'rgba(0,0,0,.42)':'rgba(0,0,0,.34)')
        alpha.strokeStyle=tint;alpha.lineWidth=1.5
        alpha.beginPath()
        alpha.roundRect(.75,.75,w-1.5,h-1.5,
          radius.map(r=>({x:Math.max(0,r.x-.75),y:Math.max(0,r.y-.75)})))
        alpha.stroke()
        ctx.globalCompositeOperation='destination-in'
        ctx.drawImage(mask,0,0)
        // Reject an empty raster rather than removing the fallback border.
        const sample=ctx.getImageData(0,0,output.width,output.height).data
        let visible=false
        for(let n=3;n<sample.length;n+=4)if(sample[n]>0){visible=true;break}
        if(!visible||!alive||!el.isConnected||elements.get(el)!==key||contrast.matches)return
        const value='url("'+output.toDataURL('image/png')+'")'
        cache.set(key,value)
        if(cache.size>16)cache.delete(cache.keys().next().value!)
        install(el,value)
      } catch { /* Retain the CSS glass and neutral border without WebGL. */ }
      finally {
        if(mount)mount.destroy()
        else gl?.getExtension('WEBGL_lose_context')?.loseContext()
        canvas?.remove()
      }
    }
    async function flush() {
      frame=0;working=true
      const el=pending.values().next().value
      if(el){pending.delete(el);await paint(el)}
      working=false
      if(alive&&pending.size)frame=requestAnimationFrame(flush)
    }
    const resize=new ResizeObserver(entries=>entries.forEach(e=>schedule(e.target as HTMLElement)))
    const observer=new MutationObserver(records=>{
      for(const r of records){
        r.addedNodes.forEach(n=>visit(n,true))
        r.removedNodes.forEach(n=>visit(n,false))
      }
    })
    const refresh=()=>elements.forEach((_,el)=>{elements.set(el,'');schedule(el)})
    const theme=new MutationObserver(refresh)
    observer.observe(document.body,{childList:true,subtree:true})
    theme.observe(document.documentElement,{attributes:true,attributeFilter:['class','data-theme']})
    contrast.addEventListener('change',refresh)
    visit(document.body,true)
    return ()=>{
      alive=false
      if(frame)cancelAnimationFrame(frame)
      observer.disconnect();resize.disconnect();theme.disconnect()
      contrast.removeEventListener('change',refresh)
      elements.forEach((_,el)=>reset(el))
      elements.clear();pending.clear();cache.clear()
    }
  },[])
  return null
}
