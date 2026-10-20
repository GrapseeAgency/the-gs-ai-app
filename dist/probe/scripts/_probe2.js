"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* Live probe: every /genui v3 section payload must parse AND have a renderer. */
require("./_probe2hook");
const fs_1 = require("fs");
const stream_1 = require("../src/components/genui/stream");
const registry_1 = require("../src/components/genui/registry");
const page = (0, fs_1.readFileSync)('/home/user/the-gs-ai-app/src/app/genui/page.tsx', 'utf8');
const ids = ['sims', 'stage3d', 'canvas', 'editors', 'terminal'];
let total = 0;
let missing = 0;
for (const id of ids) {
    const start = page.indexOf(`id: '${id}'`);
    const end = page.indexOf(`id: '`, start + 6) === -1 ? page.indexOf('\n]\n', start) : page.indexOf(`id: '`, start + 6);
    const chunk = page.slice(start, end > start ? end : undefined);
    const mdStart = chunk.indexOf('md: `');
    if (mdStart === -1) {
        console.log(`${id}: MD NOT FOUND`);
        continue;
    }
    const raw = chunk.slice(mdStart + 5, chunk.indexOf('`,\n  }', mdStart));
    // the page stores md as a JS template literal — unescape it the way the
    // runtime would before feeding the parser
    const md = raw.replace(/\\`/g, '`').replace(/\\\$/g, '$').replace(/\\\\/g, '\\');
    const segments = (0, stream_1.parseStream)(md);
    const widgets = segments.filter((s) => s.kind === 'widget');
    const kinds = widgets.map((w) => `${w.widget}${w.complete ? '' : '?'}`);
    for (const w of widgets) {
        if (!(0, registry_1.rendererFor)(w.widget))
            missing++;
        const sub = String(w.payload.surface ?? w.payload.editor ?? w.payload.sim ?? w.payload.view ?? w.payload.type ?? '');
        if (sub)
            console.log(`  ${id}: ${w.widget}/${sub}${(0, registry_1.rendererFor)(w.widget) ? '' : '  <- NO RENDERER'}`);
        else
            console.log(`  ${id}: ${w.widget}${(0, registry_1.rendererFor)(w.widget) ? '' : '  <- NO RENDERER'}`);
    }
    total += widgets.length;
    console.log(`${id}: ${widgets.length} widgets [${kinds.join(', ')}]`);
}
console.log(`TOTAL ${total} v3 widgets, ${missing} without renderer`);
if (missing > 0)
    process.exit(1);
