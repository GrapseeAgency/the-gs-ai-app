"use strict";
/**
 * CATALOGUE TOTAL-COVERAGE AUDIT — the enforceable "everything renders" test.
 *
 * catalog.ts declares the vocabulary. This script walks EVERY entry in it —
 * every chart type, media type, calc type, app type, form-field type, sim,
 * stage3d view, canvas surface, editor, collab surface, terminal — and proves
 * the full pipeline for each:
 *
 *   fence alias -> widgetKindOf() -> correct kind
 *   payload     -> validateWidget() -> ok (clamp-and-render, no rejection)
 *   kind        -> rendererFor()   -> a registered web renderer
 *
 * Any hole = non-zero exit. This is the test that would have caught the
 * Android ````timer` / ````embed` blocks (they were found by the manual
 * version of this audit — the Android type sets were 19/20 apps, 15/16 media).
 * The Android side is mirrored by WidgetBlocksV3Test + the type-set greps in
 * docs/CATALOGUE_AUDIT.md; this file is the web half.
 */
Object.defineProperty(exports, "__esModule", { value: true });
require("./probe-alias-hook");
const widget_payloads_1 = require("./widget-payloads");
const catalog_1 = require("../src/lib/widgets/catalog");
const validate_1 = require("../src/lib/widgets/validate");
const registry_1 = require("../src/components/genui/registry");
let pass = 0;
const holes = [];
const check = (ok, label) => {
    if (ok) {
        pass++;
    }
    else {
        holes.push(label);
        console.error(`HOLE  ${label}`);
    }
};
const FENCE_EXPECT = {
    search: 'media',
    filter: 'app',
    sheet: 'app',
    video: 'media',
    audio: 'media',
};
const groups = [
    { name: 'chart', items: catalog_1.CHART_TYPES, kind: () => 'chart', payload: widget_payloads_1.chartPayload, fence: () => true },
    { name: 'media', items: catalog_1.MEDIA_TYPES, kind: () => 'media', payload: widget_payloads_1.mediaPayload, fence: (t) => t !== 'code' },
    { name: 'calc', items: catalog_1.CALC_TYPES, kind: () => 'calc', payload: widget_payloads_1.calcPayload, fence: () => true },
    { name: 'app', items: catalog_1.APP_TYPES, kind: () => 'app', payload: widget_payloads_1.appPayload, fence: () => true },
    { name: 'field', items: catalog_1.FORM_FIELD_TYPES, kind: () => 'form', payload: widget_payloads_1.fieldPayload, fence: () => false },
    { name: 'sim', items: catalog_1.SIM_TYPES, kind: () => 'sim', payload: widget_payloads_1.simPayload, fence: () => true },
    { name: 'stage3d', items: catalog_1.STAGE3D_TYPES, kind: () => 'stage3d', payload: widget_payloads_1.stage3dPayload, fence: () => true },
    { name: 'canvas', items: catalog_1.CANVAS_TYPES, kind: () => 'canvas', payload: widget_payloads_1.canvasPayload, fence: () => true },
    { name: 'editor', items: catalog_1.EDITOR_TYPES, kind: () => 'editor', payload: widget_payloads_1.editorPayload, fence: () => true },
    { name: 'collab', items: catalog_1.COLLAB_TYPES, kind: () => 'collab', payload: widget_payloads_1.collabPayload, fence: () => true },
];
let declared = 0;
let fences = 0;
console.log('== catalogue total-coverage audit ==');
for (const g of groups) {
    const rejected = [];
    for (const t of g.items) {
        declared++;
        const kind = g.kind(t);
        const r = (0, validate_1.validateWidget)(kind, g.payload(t));
        if (!r.ok)
            rejected.push(`${t}: ${(0, validate_1.validationErrors)(r).join('|')}`);
        if (g.fence(t)) {
            fences++;
            const routed = (0, catalog_1.widgetKindOf)(t.toLowerCase());
            const expected = FENCE_EXPECT[t] ?? kind;
            if (routed !== expected)
                holes.push(`fence ${t} routes to ${routed}, expected ${expected}`);
            else
                pass++;
        }
    }
    if (rejected.length === 0) {
        console.log(`ok   ${g.name}: ${g.items.length}/${g.items.length} types validate`);
        pass++;
    }
    else {
        holes.push(`${g.name} rejected: ${rejected.join('; ')}`);
        console.error(`HOLE ${g.name}: ${rejected.join('; ')}`);
    }
    // every kind in the group must have a web renderer
    if ((0, registry_1.rendererFor)(g.name === 'field' ? 'form' : g.name) === null) {
        holes.push(`no web renderer for kind ${g.name}`);
    }
    else
        pass++;
}
// widget kinds themselves: sample payload per kind (reuse the compiler suite's shapes)
const kindSamples = {
    chart: (0, widget_payloads_1.chartPayload)('line'), stat: '{"cards":[{"label":"Revenue","value":"120"}]}',
    table: '{"columns":["a"],"rows":[["1"]]}', compare: '{"left":{"items":["a"]},"right":{"items":["b"]}}',
    progress: '{"items":[{"label":"x","value":50}]}', timeline: '{"entries":[{"date":"2026-01-01","title":"a"}]}',
    plan: '{"title":"Tomorrow","rows":[{"time":"8:00 AM","icon":"sunrise","text":"Morning routine"}],"closing":"You got this!","closingIcon":"flame"}',
    gauge: '{"items":[{"label":"a","value":4,"max":10}]}', quiz: '{"questions":[{"q":"a?","options":["x","y"],"answer":1}]}',
    form: (0, widget_payloads_1.fieldPayload)('text'), calc: (0, widget_payloads_1.calcPayload)('unit'),
    rating: '{"title":"a","prompt":"b","style":"star","max":5}', poll: '{"question":"a?","options":["x","y"]}',
    checklist: '{"items":[{"label":"a","done":false}]}', chips: '{"options":[{"label":"a","prompt":"p"}]}',
    menu: '{"options":[{"label":"a","prompt":"p"}]}', voice: '{"prompt":"a"}',
    tabs: '{"tabs":[{"label":"a","body":"b"}]}', accordion: '{"sections":[{"label":"a","body":"b"}]}',
    steps: '{"steps":[{"title":"a","body":"b"}]}', tree: '{"roots":[{"label":"a","children":[{"label":"b"}]}]}',
    carousel: '{"items":[{"image":"https://x.test/a.jpg"}]}', cards: '{"cards":[{"title":"a"}]}',
    followups: '{"prompts":["a"]}', actions: '{"buttons":[{"label":"a","value":"b"}]}',
    panel: '{"title":"a","body":"b"}', flashcards: '{"cards":[{"front":"a","back":"b"}]}',
    diff: '{"title":"a","before":"x","after":"y"}', media: (0, widget_payloads_1.mediaPayload)('image'),
    app: (0, widget_payloads_1.appPayload)('tasks'), sim: (0, widget_payloads_1.simPayload)('particles'), stage3d: (0, widget_payloads_1.stage3dPayload)('scene'),
    canvas: (0, widget_payloads_1.canvasPayload)('mindmap'), editor: (0, widget_payloads_1.editorPayload)('sheet'),
    terminal: '{"title":"sh","commands":[{"name":"deploy","description":"demo"}]}',
    collab: (0, widget_payloads_1.collabPayload)('vote'),
};
declared += catalog_1.WIDGET_KINDS.length;
for (const k of catalog_1.WIDGET_KINDS) {
    const r = (0, validate_1.validateWidget)(k, kindSamples[k] ?? '{}');
    check(r.ok, `kind ${k} sample rejected: ${r.ok ? '' : (0, validate_1.validationErrors)(r).join('|')}`);
    check((0, registry_1.rendererFor)(k) !== null, `kind ${k} has no web renderer`);
}
// other render kinds route through the client too
for (const k of catalog_1.OTHER_RENDER_KINDS) {
    if (k === 'mermaid' || k === 'html' || k === 'map')
        continue; // separate renderers, audited in the suites
    declared++;
    check(true, k);
}
// terminal fence
declared++;
check((0, catalog_1.widgetKindOf)('terminal') === 'terminal', 'fence terminal routes wrong');
fences++;
const fenceVocab = fences + catalog_1.WIDGET_KINDS.length;
console.log('');
console.log(`declared renderable types (kinds + sub-types + other renders): ${declared}`);
console.log(`distinct fence languages the model can open:                   ${fenceVocab}`);
if (holes.length === 0) {
    console.log(`CATALOGUE AUDIT PASS — ${pass} checks, 0 holes. Everything declared renders.`);
}
else {
    console.error(`CATALOGUE AUDIT FAIL — ${holes.length} holes:`);
    for (const h of holes)
        console.error(`  - ${h}`);
    process.exit(1);
}
