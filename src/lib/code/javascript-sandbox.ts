import { escapeCodeDisplay } from './display-source'

/** Isolated, bounded execution: opaque-origin iframe + CSP + disposable Worker.
 * The app never evaluates model code in its own window. Native timers are not
 * replaced; async work must be awaited before the worker completes. */
export function javascriptSandboxDocument(source: string): string {
  const worker = `self.onmessage = async ({data: source}) => {
    let sent = 0;
    const send = (kind, value) => { if (kind === 'log' && sent++ >= 100) return; self.postMessage({kind, value: String(value).slice(0, 16000)}); };
    const fmt = v => { try { return typeof v === 'string' ? v : JSON.stringify(v); } catch { return String(v); } };
    const console = Object.fromEntries(['log','info','warn','error'].map(k => [k, (...args) => send('log', args.map(fmt).join(' '))]));
    try {
      const AsyncFunction = Object.getPrototypeOf(async function(){}).constructor;
      const value = await new AsyncFunction('console', '"use strict";\\n' + source)(console);
      if (value !== undefined) send('log', fmt(value));
      send('done', 'Completed. Unawaited background work is stopped.');
    } catch (error) { send('error', error.name + ': ' + error.message); }
  };`
  const literal = (s: string) => JSON.stringify(s).replace(/</g, '\\u003c')
  return `<!doctype html><html><head><meta charset="utf-8">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline' 'unsafe-eval' blob:; worker-src blob:; connect-src 'none'; style-src 'unsafe-inline'">
<style>body{font:13px system-ui;margin:12px}pre{white-space:pre-wrap;overflow-wrap:anywhere}button{cursor:pointer}</style></head>
<body><button id="run">Run again</button> <span>JavaScript worker · 2 second limit · no network</span><pre id="out" role="log"></pre>
<script>
${escapeCodeDisplay.toString()}
let worker, timer;
function run(){
  if(worker)worker.terminate();clearTimeout(timer);
  const out=document.getElementById('out');out.textContent='';let count=0;
  function line(text, final=false){if(final||count++<100)out.textContent+=escapeCodeDisplay(String(text))+'\\n';}
  try{
    const url=URL.createObjectURL(new Blob([${literal(worker)}],{type:'text/javascript'}));
    worker=new Worker(url);URL.revokeObjectURL(url);
    worker.onmessage=({data})=>{line(data.value,data.kind!=='log');if(data.kind==='done'||data.kind==='error'){clearTimeout(timer);worker.terminate();}};
    worker.onerror=(event)=>{line('Worker error: '+event.message);clearTimeout(timer);worker.terminate();};
    timer=setTimeout(()=>{worker.terminate();line('Stopped: execution exceeded 2 seconds.',true);},2000);
    worker.postMessage(${literal(source)});
  }catch(error){line('Cannot start worker: '+error.message);}
}
document.getElementById('run').onclick=run;run();
</script></body></html>`
}
