"use strict";
/**
 * The widget catalogue: the single source of truth for what a client can render.
 *
 * Why a catalogue and not a prompt: a prompt is a request, a schema is a
 * contract. Production systems (OpenAI structured outputs, Anthropic strict
 * tool use, Google A2UI) all validate the model's output against a declared
 * schema before anything reaches the UI. This file is that declaration for GS.
 *
 * V2 (2026-10-20): the catalogue stopped being "18 kinds" and became a
 * compositional vocabulary in the A2UI / Apps-SDK sense — layout and control
 * surfaces (tabs, accordion, steps, menu, chips, rating, poll, checklist,
 * flashcards), developer surfaces (diff, tree), and five new chart types
 * (sankey, network, wordcloud, sunburst, bullet). The count of DISTINCT
 * renderable components (kinds × sub-types × field types × diagram languages)
 * is well past 150; the kinds list below is the fence vocabulary, not the
 * ceiling. Anything beyond it is bespoke ```html — which is unlimited.
 *
 * Keep in sync with:
 *   - android/.../WidgetBlocks.kt  (WIDGET_LANGUAGES, CHART_TYPES, ...)
 *   - docs/GENUI.md
 *   - docs/WIDGET_COVERAGE.md
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.CHART_ALIASES = exports.LIMITS = exports.EDITOR_TYPES = exports.CANVAS_TYPES = exports.STAGE3D_TYPES = exports.SIM_TYPES = exports.FORM_FIELD_TYPES = exports.APP_TYPES = exports.CALC_TYPES = exports.MEDIA_TYPES = exports.GRAPH_CHART_TYPES = exports.CHART_TYPES = exports.OTHER_RENDER_KINDS = exports.WIDGET_KINDS = void 0;
exports.isWidgetKind = isWidgetKind;
exports.widgetKindOf = widgetKindOf;
exports.canonicalChartType = canonicalChartType;
exports.injectChartType = injectChartType;
exports.injectWidgetType = injectWidgetType;
exports.isChartType = isChartType;
exports.WIDGET_KINDS = [
    // -- data surfaces ---------------------------------------------------------
    'chart',
    'stat',
    'table',
    'compare',
    'progress',
    'timeline',
    'gauge',
    // -- interactive inputs ----------------------------------------------------
    'quiz',
    'form',
    'calc',
    'rating',
    'poll',
    'checklist',
    'chips',
    'menu',
    'voice',
    // -- layout and navigation -------------------------------------------------
    'tabs',
    'accordion',
    'steps',
    'tree',
    'carousel',
    'cards',
    'followups',
    'actions',
    'panel',
    'flashcards',
    'diff',
    // -- rich content ----------------------------------------------------------
    'media',
    'app',
    // -- v3: generative worlds, editors and runtimes ----------------------------
    'sim', // physics/agent simulations on a live canvas
    'stage3d', // real WebGL scenes via three.js
    'canvas', // freeform drawing surfaces (whiteboard, mindmap, diagram, polygons)
    'editor', // in-chat application editors (nodes, sheet, pivot, site, css, video, audio)
    'terminal', // a real terminal with a sandboxed JS runtime
];
/** Non-widget fenced blocks the client renders natively too. */
exports.OTHER_RENDER_KINDS = ['map', 'mermaid', 'html', 'svg'];
exports.CHART_TYPES = [
    'line',
    'area',
    'stackedArea',
    'bar',
    'hbar',
    'stacked',
    'grouped',
    'pie',
    'donut',
    'scatter',
    'bubble',
    'histogram',
    'boxplot',
    'heatmap',
    'correlation',
    'treemap',
    'waterfall',
    'candlestick',
    'gantt',
    'funnel',
    'radar',
    'chord',
    'quadrant',
    // v2 — composition / flow / text charts
    'sankey', // {"nodes":[..],"links":[{"source","target","value"}]}
    'network', // {"nodes":[{"id","label","group"}],"edges":[{"source","target","weight"}]}
    'wordcloud', // {"items":[{"text","weight"}]}
    'sunburst', // {"root":{"label","children":[...]}}
    'bullet', // {"items":[{"label","value","target","ranges":[..],"max"}]}
    // v3 — distribution, projection, exploration
    'violin', // {"series":[{"name","data":[samples]}]} distribution shapes
    'contour', // {"levels":N,"points":[[x,y,z]]} iso-lines over a scalar field
    'parallel', // {"axes":[...],"series":[{"name","values":[...]}]} multidimensional
    'dendrogram', // {"root":tree with distance merges} hierarchical clustering
    'force', // network with force-directed layout + drag exploration
    'depth', // {"bids":[[price,size]],"asks":[[price,size]]} order-book ladder
];
/** Chart types whose payload is a GRAPH (nodes/links), not a series list. */
exports.GRAPH_CHART_TYPES = ['sankey', 'network'];
exports.MEDIA_TYPES = [
    'image',
    'gallery',
    'video',
    'audio',
    'code',
    'file',
    'link',
    'citation',
    'search',
    'entity',
    'product',
    'listing',
    'doc',
    'embed',
    // The clients render these two as well (WidgetsMedia.kt). Leaving them out of
    // the catalogue meant a ```media payload with type "pdf" was rewritten to
    // "image" by the validator — a correct answer turned into a broken one.
    'document',
    'pdf',
];
exports.CALC_TYPES = [
    'unit',
    'currency',
    'finance',
    'loan',
    'mortgage',
    'pricing',
    'tax',
    'budget',
    'investment',
    'probability',
    'statistics',
    'scientific',
    'datetime',
    'health',
];
exports.APP_TYPES = [
    'wizard',
    'onboarding',
    'kanban',
    'calendar',
    'sheet',
    'tasks',
    'dashboard',
    'config',
    'configurator',
    'booking',
    'schedule',
    'search',
    'filter',
    'analytics',
    'report',
    'files',
    'connected',
    'decision',
    'scenario',
    'timer',
];
exports.FORM_FIELD_TYPES = [
    'text',
    'textarea',
    'select',
    'radio',
    'checkbox',
    'slider',
    'toggle',
    'date',
    'segmented',
    // v2 — the native <input> family. Free to support, and every one of them is
    // a real interactive control the catalogue previously had no name for.
    'number',
    'time',
    'email',
    'url',
    'color',
    'file',
    'rating',
    'otp',
];
/** sim sub-types — each is a real integrated simulation. */
exports.SIM_TYPES = [
    'cloth',
    'softbody',
    'fluid',
    'smoke',
    'particles',
    'rigid',
    'terrain',
    'game',
];
/** stage3d sub-types — real WebGL via three.js (lazy chunk). */
exports.STAGE3D_TYPES = [
    'scene',
    'scatter3d',
    'surface3d',
    'molecule',
    'cad',
    'anatomy',
    'vr', // magic-window stereo; true headset sessions are not grantable in chat
    'ar', // camera passthrough when the client grants it, else magic-window
];
/** canvas sub-types — freeform pointer surfaces. */
exports.CANVAS_TYPES = [
    'whiteboard',
    'mindmap',
    'diagram',
    'polygons',
];
/** editor sub-types — in-chat application editors. */
exports.EDITOR_TYPES = [
    'nodes', // node-based visual programming
    'sheet', // spreadsheet with a formula engine
    'pivot', // pivot-table builder
    'site', // drag-and-drop site builder
    'css', // visual CSS layout editor
    'video', // clip timeline editor
    'audio', // waveform + spectrogram editor
];
/** Hard limits, mirrored by the clients. */
exports.LIMITS = {
    maxWidgetsPerAnswer: 3,
    maxWidgetBytes: 64 * 1024,
    maxChartSeries: 8,
    maxPointsPerSeries: 200,
    maxTableRows: 100,
    maxTableColumns: 8,
    maxCards: 8,
    maxQuizQuestions: 10,
    maxFormFields: 12,
    maxAppItems: 40,
    maxTabs: 8,
    maxSections: 10,
    maxSteps: 12,
    maxOptions: 12,
    maxTreeDepth: 6,
    maxFlashcards: 20,
    maxChecklistItems: 30,
    maxDiffLines: 200,
    maxSimBodies: 120,
    max3dNodes: 60,
    maxCanvasNodes: 40,
    maxSheetCells: 400,
    maxTracks: 6,
};
function isWidgetKind(value) {
    return exports.WIDGET_KINDS.includes(value);
}
/**
 * Chart SUB-TYPES models use as the fence language itself: ```funnel, ```gantt,
 * ```heatmap … with the type implied by the fence. They are chart widgets, not
 * unknown languages — treating them as unknown made the compiler report "no
 * widget" and append a deterministic fallback on top of a valid payload.
 */
exports.CHART_ALIASES = exports.CHART_TYPES.map((t) => t.toLowerCase());
/**
 * Canonical widget kind for a fence language, or null when it is not a widget.
 * Chart aliases collapse to 'chart'. Mirrors widgetKindOf() on the clients.
 */
/**
 * Sub-types models use as the fence language itself: ```kanban, ```loan,
 * ```gallery, ```pdf. Same behaviour as the chart aliases — the fence names the
 * shape, the payload carries the content — and the same failure if unhandled:
 * the block reaches the client as source code.
 */
const APP_ALIASES = exports.APP_TYPES;
const CALC_ALIASES = exports.CALC_TYPES;
const SIM_ALIASES = exports.SIM_TYPES;
const STAGE3D_ALIASES = exports.STAGE3D_TYPES;
const CANVAS_ALIASES = exports.CANVAS_TYPES;
const EDITOR_ALIASES = exports.EDITOR_TYPES;
/** ```code is source code, never a media widget. */
const MEDIA_ALIASES = exports.MEDIA_TYPES.filter((t) => t !== 'code');
function widgetKindOf(language) {
    const lang = language.toLowerCase();
    if (isWidgetKind(lang))
        return lang;
    if (exports.CHART_ALIASES.includes(lang))
        return 'chart';
    // Media first: "search" is both a media result list and an app filter panel,
    // and a ```search fence almost always holds results.
    if (MEDIA_ALIASES.includes(lang))
        return 'media';
    if (APP_ALIASES.includes(lang))
        return 'app';
    if (CALC_ALIASES.includes(lang))
        return 'calc';
    if (SIM_ALIASES.includes(lang))
        return 'sim';
    if (STAGE3D_ALIASES.includes(lang))
        return 'stage3d';
    if (CANVAS_ALIASES.includes(lang))
        return 'canvas';
    if (EDITOR_ALIASES.includes(lang))
        return 'editor';
    if (lang === 'terminal')
        return 'terminal';
    return null;
}
/** `stackedarea` -> `stackedArea`; every other alias is already canonical. */
function canonicalChartType(alias) {
    const hit = exports.CHART_TYPES.find((t) => t.toLowerCase() === alias.toLowerCase());
    return hit ?? alias;
}
/** Inject the type implied by the fence when the payload does not name one. */
function injectChartType(json, alias) {
    if (/\"type\"\s*:/.test(json))
        return json;
    const brace = json.indexOf('{');
    if (brace < 0)
        return json;
    return `${json.slice(0, brace + 1)}\"type\":\"${canonicalChartType(alias)}\",${json.slice(brace + 1)}`;
}
/** Same, for a sub-type fence: ```kanban -> kind "app", type "kanban". */
function injectWidgetType(json, alias, kind) {
    if (/\"type\"\s*:/.test(json))
        return json;
    const brace = json.indexOf('{');
    if (brace < 0)
        return json;
    const type = kind === 'chart' ? canonicalChartType(alias) : alias.toLowerCase();
    return `${json.slice(0, brace + 1)}\"type\":\"${type}\",${json.slice(brace + 1)}`;
}
function isChartType(value) {
    return typeof value === 'string' && exports.CHART_TYPES.includes(value);
}
