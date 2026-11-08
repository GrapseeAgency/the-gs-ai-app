'use client'
import { useMemo, useCallback } from 'react'
import { HtmlBlock } from './HtmlBlock'
import { reactPreviewDocument } from '@/lib/code/react-preview'

/** The iframe keeps an opaque origin. Its only storage capability is this
 * snippet's map; it cannot access cookies, host DOM or other response keys. */
export function ReactPreview({source, storageKey}: {source: string; storageKey: string}) {
  const namespace = 'gs.react-preview.v1:' + storageKey
  const document = useMemo(() => {
    let initial: Record<string,string> = {}, available = false
    try {
      if (typeof window !== 'undefined') {
        const parsed: unknown = JSON.parse(window.localStorage.getItem(namespace) ?? '{}')
        if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
          initial = Object.fromEntries(Object.entries(parsed).filter(([key,value])=>key.length <= 256 && typeof value === 'string' && value.length <= 131072))
        }
        available = true
      }
    } catch { /* Explicit temporary-storage notice inside the preview. */ }
    const bootstrap = `
      const memory=Object.assign(Object.create(null),${JSON.stringify(initial).replace(/</g,'\\u003c')});
      function storageNotice(message){let p=document.getElementById('storage-status');if(!p){p=document.createElement('p');p.id='storage-status';p.setAttribute('role','alert');document.body.prepend(p);}p.textContent=message;}
      window.addEventListener('message',event=>{if(event.source===parent&&event.data?.type==='gs-react-storage-error')storageNotice(event.data.message);});
      ${available ? '' : "document.addEventListener('DOMContentLoaded',()=>storageNotice('Persistent storage unavailable. Changes in this preview are temporary.'));"}
      function persist(){parent.postMessage({type:'gs-react-storage',value:memory},'*');}
      const storage={getItem:key=>Object.hasOwn(memory,String(key))?memory[String(key)]:null,
        setItem:(key,value)=>{key=String(key);value=String(value);if(key.length>256||value.length>131072||JSON.stringify({...memory,[key]:value}).length>524288)throw new DOMException('Preview storage quota exceeded','QuotaExceededError');memory[key]=value;persist();},
        removeItem:key=>{delete memory[String(key)];persist();},clear:()=>{for(const key of Object.keys(memory))delete memory[key];persist();},
        key:index=>Object.keys(memory)[index]??null,get length(){return Object.keys(memory).length;}};
      Object.defineProperty(window,'localStorage',{value:storage,configurable:false});
    `
    return reactPreviewDocument(source, bootstrap)
  }, [source, namespace])
  const persist = useCallback((data: unknown) => {
    if (!data || typeof data !== 'object' || !('type' in data) || data.type !== 'gs-react-storage' || !('value' in data)) return
    try {
      const value = data.value
      if (!value || typeof value !== 'object' || Array.isArray(value)) throw Error('Invalid storage map')
      if (Object.entries(value).some(([key,val])=>key.length>256 || typeof val!=='string' || val.length>131072)) throw Error('Preview storage quota exceeded')
      const encoded = JSON.stringify(value)
      if (encoded.length > 524288) throw Error('Preview storage quota exceeded')
      window.localStorage.setItem(namespace, encoded)
    } catch {
      return {type:'gs-react-storage-error',message:'Persistent storage unavailable. Changes in this preview are temporary.'}
    }
  }, [namespace])
  return <HtmlBlock source={document} fallbackHeight={420} onSandboxMessage={persist} />
}
