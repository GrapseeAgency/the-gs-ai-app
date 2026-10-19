/**
 * Tool definitions for a provider that supports native tool calling.
 *
 * GS's current provider does not (see src/lib/websearch.ts), so the live path
 * is `compile.ts`. These definitions exist so the migration is a wiring change,
 * not a rewrite: OpenAI `tools`, Anthropic `tools` with `strict: true`, and
 * Gemini `functionDeclarations` all consume the same JSON Schema shape below.
 *
 * Design note: production assistants expose ONE TOOL PER ACTION rather than a
 * single mega-tool. A strict schema with a `kind` enum plus a free-form payload
 * cannot be expressed (strict mode forbids open objects), and small schemas are
 * what make constrained decoding reliable. The three core actions are strict;
 * the long tail uses `render_widget` with a closed kind enum.
 */

import { APP_TYPES, CALC_TYPES, CHART_TYPES, MEDIA_TYPES } from './catalog'

export interface JsonSchema {
  type: 'object'
  properties: Record<string, unknown>
  required: string[]
  additionalProperties: false
}

export interface ToolDefinition {
  name: string
  description: string
  input_schema: JsonSchema
}

const CHART_TOOL: ToolDefinition = {
  name: 'render_chart',
  description:
    'Render a chart in the chat. Use it when the answer contains numbers that are easier to ' +
    'compare visually. The data must come from the answer; never invent measurements.',
  input_schema: {
    type: 'object',
    properties: {
      type: { type: 'string', enum: [...CHART_TYPES] },
      title: { type: 'string' },
      unit: { type: 'string' },
      labels: { type: 'array', items: { type: 'string' } },
      series: {
        type: 'array',
        items: {
          type: 'object',
          properties: {
            name: { type: 'string' },
            data: {
              type: 'array',
              items: { type: 'array', items: { type: 'number' } },
            },
          },
          required: ['name', 'data'],
          additionalProperties: false,
        },
      },
    },
    required: ['type', 'title', 'labels', 'series'],
    additionalProperties: false,
  },
}

const TABLE_TOOL: ToolDefinition = {
  name: 'render_table',
  description:
    'Render a sortable table. Use it for multi-column comparisons that would be unreadable as prose.',
  input_schema: {
    type: 'object',
    properties: {
      title: { type: 'string' },
      columns: { type: 'array', items: { type: 'string' } },
      rows: { type: 'array', items: { type: 'array', items: { type: 'string' } } },
      sortable: { type: 'boolean' },
    },
    required: ['title', 'columns', 'rows'],
    additionalProperties: false,
  },
}

const STAT_TOOL: ToolDefinition = {
  name: 'render_stat',
  description: 'Render key metrics as stat cards. Use it for a handful of headline numbers.',
  input_schema: {
    type: 'object',
    properties: {
      title: { type: 'string' },
      cards: {
        type: 'array',
        items: {
          type: 'object',
          properties: {
            label: { type: 'string' },
            value: { type: 'string' },
            unit: { type: 'string' },
            delta: { type: 'string' },
            direction: { type: 'string', enum: ['up', 'down', 'flat'] },
          },
          required: ['label', 'value'],
          additionalProperties: false,
        },
      },
    },
    required: ['title', 'cards'],
    additionalProperties: false,
  },
}

const WIDGET_TOOL: ToolDefinition = {
  name: 'render_widget',
  description:
    'Render any other interactive widget. Prefer render_chart / render_table / render_stat when ' +
    'they fit. The payload is validated against the catalogue; an invalid payload is rejected.',
  input_schema: {
    type: 'object',
    properties: {
      kind: {
        type: 'string',
        enum: [
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
        ],
      },
      payload: { type: 'object' },
      subtype: {
        type: 'string',
        enum: ['', ...MEDIA_TYPES, ...CALC_TYPES, ...APP_TYPES],
      },
    },
    required: ['kind', 'payload'],
    additionalProperties: false,
  },
}

export const WIDGET_TOOLS: ToolDefinition[] = [
  CHART_TOOL,
  TABLE_TOOL,
  STAT_TOOL,
  WIDGET_TOOL,
]

/** OpenAI chat.completions / responses shape. */
export function toOpenAITools(tools: ToolDefinition[] = WIDGET_TOOLS) {
  return tools.map((t) => ({
    type: 'function' as const,
    function: {
      name: t.name,
      description: t.description,
      parameters: t.input_schema,
      strict: true,
    },
  }))
}

/** Anthropic messages shape. */
export function toAnthropicTools(tools: ToolDefinition[] = WIDGET_TOOLS) {
  return tools.map((t) => ({
    name: t.name,
    description: t.description,
    input_schema: t.input_schema,
    strict: true,
  }))
}

/**
 * Converts a tool call into the fenced block the clients already understand.
 * Keeping one wire format means a provider migration changes nothing on the
 * Android/iOS side.
 */
export function toolCallToFence(name: string, input: Record<string, unknown>): string | null {
  const kind =
    name === 'render_chart'
      ? 'chart'
      : name === 'render_table'
        ? 'table'
        : name === 'render_stat'
          ? 'stat'
          : name === 'render_widget'
            ? typeof input.kind === 'string'
              ? input.kind
              : null
            : null
  if (!kind) return null
  const payload =
    name === 'render_widget' && typeof input.payload === 'object' && input.payload !== null
      ? (input.payload as Record<string, unknown>)
      : input
  return '```' + kind + '\n' + JSON.stringify(payload) + '\n```'
}
