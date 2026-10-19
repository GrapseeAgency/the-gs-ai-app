/**
 * The widget catalogue: the single source of truth for what a client can render.
 *
 * Why a catalogue and not a prompt: a prompt is a request, a schema is a
 * contract. Production systems (OpenAI structured outputs, Anthropic strict
 * tool use, Google A2UI) all validate the model's output against a declared
 * schema before anything reaches the UI. This file is that declaration for GS.
 *
 * Keep in sync with:
 *   - android/.../WidgetBlocks.kt  (WIDGET_LANGUAGES, CHART_TYPES, ...)
 *   - docs/GENUI.md
 *   - docs/WIDGET_COVERAGE.md
 */

export const WIDGET_KINDS = [
  'chart',
  'stat',
  'table',
  'compare',
  'progress',
  'timeline',
  'gauge',
  'quiz',
  'form',
  'carousel',
  'cards',
  'followups',
  'media',
  'actions',
  'panel',
  'voice',
  'calc',
  'app',
] as const

export type WidgetKind = (typeof WIDGET_KINDS)[number]

/** Non-widget fenced blocks the client renders natively too. */
export const OTHER_RENDER_KINDS = ['map', 'mermaid', 'html'] as const

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
] as const

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
} as const

export function isWidgetKind(value: string): value is WidgetKind {
  return (WIDGET_KINDS as readonly string[]).includes(value)
}

export function isChartType(value: unknown): value is (typeof CHART_TYPES)[number] {
  return typeof value === 'string' && (CHART_TYPES as readonly string[]).includes(value)
}
