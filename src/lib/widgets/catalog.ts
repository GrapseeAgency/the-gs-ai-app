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

export const WIDGET_KINDS = [
  // -- data surfaces ---------------------------------------------------------
  'chart',
  'stat',
  'table',
  'compare',
  'progress',
  'timeline',
  'plan',
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
  'collab', // LIVE shared state across viewers (prototype: per-server memory, LWW)
] as const

export type WidgetKind = (typeof WIDGET_KINDS)[number]

/** Non-widget fenced blocks the client renders natively too. */
export const OTHER_RENDER_KINDS = ['map', 'mermaid', 'html', 'svg'] as const

export const CHART_TYPES = [
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
] as const

/** Chart types whose payload is a GRAPH (nodes/links), not a series list. */
export const GRAPH_CHART_TYPES = ['sankey', 'network'] as const

export const MEDIA_TYPES = [
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
] as const

export const CALC_TYPES = [
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
] as const

export const APP_TYPES = [
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
] as const

export const FORM_FIELD_TYPES = [
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
] as const

/** sim sub-types — each is a real integrated simulation. */
export const SIM_TYPES = [
  'cloth',
  'softbody',
  'fluid',
  'smoke',
  'particles',
  'rigid',
  'terrain',
  'game',
] as const

/** stage3d sub-types — real WebGL via three.js (lazy chunk). */
export const STAGE3D_TYPES = [
  'scene',
  'scatter3d',
  'surface3d',
  'molecule',
  'cad',
  'anatomy',
  'vr', // magic-window stereo; true headset sessions are not grantable in chat
  'ar', // camera passthrough when the client grants it, else magic-window
] as const

/** canvas sub-types — freeform pointer surfaces. */
export const CANVAS_TYPES = [
  'whiteboard',
  'mindmap',
  'diagram',
  'polygons',
] as const

/** editor sub-types — in-chat application editors. */
export const COLLAB_TYPES = [
  'counter', // shared tally, server-side increments
  'vote', // shared options with live counts
  'todo', // shared checklist (list replace, LWW)
  'pointer', // live cursors + presence only (ephemeral)
] as const
export const EDITOR_TYPES = [
  'nodes', // node-based visual programming
  'sheet', // spreadsheet with a formula engine
  'pivot', // pivot-table builder
  'site', // drag-and-drop site builder
  'css', // visual CSS layout editor
  'video', // clip timeline editor
  'audio', // waveform + spectrogram editor
] as const

/** Hard limits, mirrored by the clients. */
export const LIMITS = {
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
  maxCollabItems: 12,
} as const

export function isWidgetKind(value: string): value is WidgetKind {
  return (WIDGET_KINDS as readonly string[]).includes(value)
}

/**
 * Chart SUB-TYPES models use as the fence language itself: ```funnel, ```gantt,
 * ```heatmap … with the type implied by the fence. They are chart widgets, not
 * unknown languages — treating them as unknown made the compiler report "no
 * widget" and append a deterministic fallback on top of a valid payload.
 */
export const CHART_ALIASES = CHART_TYPES.map((t) => t.toLowerCase())

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
const APP_ALIASES = APP_TYPES as readonly string[]
const CALC_ALIASES = CALC_TYPES as readonly string[]
const SIM_ALIASES = SIM_TYPES as readonly string[]
const STAGE3D_ALIASES = STAGE3D_TYPES as readonly string[]
const CANVAS_ALIASES = CANVAS_TYPES as readonly string[]
const EDITOR_ALIASES = EDITOR_TYPES as readonly string[]
const COLLAB_ALIASES = COLLAB_TYPES as readonly string[]
/** ```code is source code, never a media widget. */
const MEDIA_ALIASES = (MEDIA_TYPES as readonly string[]).filter((t) => t !== 'code')

export function widgetKindOf(language: string): WidgetKind | null {
  const lang = language.toLowerCase()
  if (isWidgetKind(lang)) return lang
  if (CHART_ALIASES.includes(lang)) return 'chart'
  // Media first: "search" is both a media result list and an app filter panel,
  // and a ```search fence almost always holds results.
  if (MEDIA_ALIASES.includes(lang)) return 'media'
  if (APP_ALIASES.includes(lang)) return 'app'
  if (CALC_ALIASES.includes(lang)) return 'calc'
  if (SIM_ALIASES.includes(lang)) return 'sim'
  if (STAGE3D_ALIASES.includes(lang)) return 'stage3d'
  if (CANVAS_ALIASES.includes(lang)) return 'canvas'
  if (EDITOR_ALIASES.includes(lang)) return 'editor'
  if (lang === 'terminal') return 'terminal'
  if (COLLAB_ALIASES.includes(lang)) return 'collab'
  return null
}

/** `stackedarea` -> `stackedArea`; every other alias is already canonical. */
export function canonicalChartType(alias: string): string {
  const hit = CHART_TYPES.find((t) => t.toLowerCase() === alias.toLowerCase())
  return hit ?? alias
}

/** Inject the type implied by the fence when the payload does not name one. */
export function injectChartType(json: string, alias: string): string {
  if (/\"type\"\s*:/.test(json)) return json
  const brace = json.indexOf('{')
  if (brace < 0) return json
  return `${json.slice(0, brace + 1)}\"type\":\"${canonicalChartType(alias)}\",${json.slice(brace + 1)}`
}

/** Same, for a sub-type fence: ```kanban -> kind "app", type "kanban". */
export function injectWidgetType(json: string, alias: string, kind: WidgetKind): string {
  if (/\"type\"\s*:/.test(json)) return json
  const brace = json.indexOf('{')
  if (brace < 0) return json
  const type = kind === 'chart' ? canonicalChartType(alias) : alias.toLowerCase()
  return `${json.slice(0, brace + 1)}\"type\":\"${type}\",${json.slice(brace + 1)}`
}

export function isChartType(value: unknown): value is (typeof CHART_TYPES)[number] {
  return typeof value === 'string' && (CHART_TYPES as readonly string[]).includes(value)
}
