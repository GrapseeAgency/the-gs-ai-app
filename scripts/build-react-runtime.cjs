/* Generated UI runs only in an isolated browser/WebView. This build bundles
 * trusted dependencies; it never evaluates a model's source in Node. */
const fs = require('node:fs');
const path = require('node:path');
const esbuild = require('esbuild');
const versions = {react:'19.3.1','react-dom':'19.3.1','@babel/standalone':'7.26.10','@tailwindcss/browser':'4.1.18'};
for (const [name, version] of Object.entries(versions)) {
  if (require(name+'/package.json').version !== version) throw Error('Install pinned runtime dependency '+name+'@'+version);
}
const safeScript = script => script.replace(/<\/script/gi, '<\\/script');
const bundle = esbuild.buildSync({entryPoints:['scripts/react-runtime/entry.js'],bundle:true,write:false,minify:true,format:'iife',platform:'browser',define:{'process.env.NODE_ENV':'"production"'},legalComments:'inline'}).outputFiles[0].text;
const tailwind = fs.readFileSync(require.resolve('@tailwindcss/browser'),'utf8');
const launcher = `
const status = document.getElementById('status'), errorPanel = document.getElementById('error');
function fail(error) { status.textContent='React preview error'; errorPanel.hidden=false; errorPanel.textContent=String(error?.message || error); }
window.addEventListener('error', event=>fail(event.error || event.message));
window.addEventListener('unhandledrejection', event=>fail(event.reason));
try {
  const {React, createRoot, flushSync, transform} = window.GSReact;
  const source = __GS_REACT_SOURCE_LITERAL__;
  if (source.length > 128000) throw Error('Not executed: React source exceeds the 128,000 character limit.');
  const compiled = transform(source, {filename:'component.tsx',presets:[['react',{runtime:'classic'}],['typescript',{allExtensions:true,isTSX:true}]],plugins:['transform-modules-commonjs']}).code;
  const module = {exports:{}};
  const require = name => {
    if (name==='react') return React;
    if (name==='react-dom/client') return {createRoot};
    throw Error('Unsupported dependency: '+name+'. Only bundled React and Tailwind are available; external imports are not fetched.');
  };
  new Function('require','module','exports','React',compiled)(require,module,module.exports,React);
  const App = module.exports.default;
  if (typeof App!=='function' && (typeof App!=='object' || App===null)) throw Error('Export a default React component to preview this source.');
  class Boundary extends React.Component {
    constructor(props){super(props);this.state={failed:false};}
    static getDerivedStateFromError(){return {failed:true};}
    componentDidCatch(error){fail(error);}
    render(){return this.state.failed ? null : this.props.children;}
  }
  function Mounted(){React.useLayoutEffect(()=>{status.textContent='React preview running';},[]);return React.createElement(App);}
  const root = createRoot(document.getElementById('app'),{onUncaughtError:fail});
  flushSync(()=>root.render(React.createElement(Boundary,null,React.createElement(Mounted))));
} catch(error) { fail(error); }
`;
const template = `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline' 'unsafe-eval'; style-src 'unsafe-inline'; img-src data: blob:; connect-src 'none'; form-action 'none'; base-uri 'none'; object-src 'none'; frame-src 'none'; worker-src 'none'"><style>body{margin:0;padding:12px;font:14px system-ui;background:transparent}#status{font:11px system-ui;color:#5b5378;margin:0 0 10px}#error{white-space:pre-wrap;overflow-wrap:anywhere;background:#fff0f0;color:#9d1628;padding:12px;border-radius:10px}</style><script>${safeScript(bundle)}</script><script>${safeScript(tailwind)}</script><script>__GS_REACT_STORAGE_BOOTSTRAP__</script></head><body><p id="status" role="status">Compiling React source…</p><pre id="error" role="alert" hidden></pre><main id="app"></main><script>${safeScript(launcher)}</script></body></html>`;
fs.writeFileSync('shared-contracts/react-preview-runtime.json', JSON.stringify({version:1,dependencies:versions,template}));
let licenses = 'Offline React preview runtime: third-party licenses\n\n';
for (const name of Object.keys(versions)) {
  const dir = path.dirname(require.resolve(name+'/package.json'));
  const license = ['LICENSE','LICENSE.txt','LICENSE.md'].map(file=>path.join(dir,file)).find(file=>fs.existsSync(file));
  if (!license) throw Error('Missing license: '+name);
  licenses += '\n===== '+name+' '+versions[name]+' =====\n'+fs.readFileSync(license,'utf8');
}
fs.writeFileSync('shared-contracts/react-preview-LICENSES.txt',licenses);
console.log('Built offline React/JSX/TSX/Tailwind runtime:',Buffer.byteLength(template),'bytes');
