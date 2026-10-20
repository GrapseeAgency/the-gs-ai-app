"use strict";
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
Object.defineProperty(exports, "__esModule", { value: true });
exports.WIDGET_TOOLS = void 0;
exports.toOpenAITools = toOpenAITools;
exports.toAnthropicTools = toAnthropicTools;
exports.toolCallToFence = toolCallToFence;
const catalog_1 = require("./catalog");
const rulebook_1 = require("../design/rulebook");
/**
 * Style keys shared by every render tool. In strict mode every property must
 * be required, so these are not optional: a tool-calling provider must state
 * the face, exactly like the prompt asks a fenced-block provider to.
 */
const STYLE_PROPERTIES = {
    accent: {
        type: 'string',
        enum: [...rulebook_1.DATA_HUES],
        description: 'The hue for this block. Pick the one that fits the subject; never repeat the block above.',
    },
    variant: {
        type: 'string',
        enum: [...rulebook_1.SURFACES],
        description: 'The surface: hero KPI, tinted chart, editorial figure, inset form, outline list, plain table.',
    },
    palette: {
        type: 'string',
        enum: rulebook_1.PALETTES.map((p) => p.id),
        description: 'The reply-level palette. Every widget in one answer should share it.',
    },
};
const STYLE_REQUIRED = ['accent', 'variant', 'palette'];
const CHART_TOOL = {
    name: 'render_chart',
    description: 'Render a chart in the chat. Use it when the answer contains numbers that are easier to ' +
        'compare visually. The data must come from the answer; never invent measurements.',
    input_schema: {
        type: 'object',
        properties: {
            type: { type: 'string', enum: [...catalog_1.CHART_TYPES] },
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
            ...STYLE_PROPERTIES,
        },
        required: ['type', 'title', 'labels', 'series', ...STYLE_REQUIRED],
        additionalProperties: false,
    },
};
const TABLE_TOOL = {
    name: 'render_table',
    description: 'Render a sortable table. Use it for multi-column comparisons that would be unreadable as prose.',
    input_schema: {
        type: 'object',
        properties: {
            title: { type: 'string' },
            columns: { type: 'array', items: { type: 'string' } },
            rows: { type: 'array', items: { type: 'array', items: { type: 'string' } } },
            sortable: { type: 'boolean' },
            ...STYLE_PROPERTIES,
        },
        required: ['title', 'columns', 'rows', ...STYLE_REQUIRED],
        additionalProperties: false,
    },
};
const STAT_TOOL = {
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
            ...STYLE_PROPERTIES,
        },
        required: ['title', 'cards', ...STYLE_REQUIRED],
        additionalProperties: false,
    },
};
const WIDGET_TOOL = {
    name: 'render_widget',
    description: 'Render any other interactive widget. Prefer render_chart / render_table / render_stat when ' +
        'they fit. The payload is validated against the catalogue; an invalid payload is rejected.',
    input_schema: {
        type: 'object',
        properties: {
            kind: {
                // Derived from the catalogue so a new kind can never drift from the
                // tool contract (chart/table/stat have their own dedicated tools).
                type: 'string',
                enum: catalog_1.WIDGET_KINDS.filter((k) => !['chart', 'table', 'stat'].includes(k)),
            },
            payload: { type: 'object' },
            subtype: {
                type: 'string',
                enum: ['', ...catalog_1.MEDIA_TYPES, ...catalog_1.CALC_TYPES, ...catalog_1.APP_TYPES],
            },
            ...STYLE_PROPERTIES,
        },
        required: ['kind', 'payload', ...STYLE_REQUIRED],
        additionalProperties: false,
    },
};
exports.WIDGET_TOOLS = [
    CHART_TOOL,
    TABLE_TOOL,
    STAT_TOOL,
    WIDGET_TOOL,
];
/** OpenAI chat.completions / responses shape. */
function toOpenAITools(tools = exports.WIDGET_TOOLS) {
    return tools.map((t) => ({
        type: 'function',
        function: {
            name: t.name,
            description: t.description,
            parameters: t.input_schema,
            strict: true,
        },
    }));
}
/** Anthropic messages shape. */
function toAnthropicTools(tools = exports.WIDGET_TOOLS) {
    return tools.map((t) => ({
        name: t.name,
        description: t.description,
        input_schema: t.input_schema,
        strict: true,
    }));
}
/**
 * Converts a tool call into the fenced block the clients already understand.
 * Keeping one wire format means a provider migration changes nothing on the
 * Android/iOS side.
 */
function toolCallToFence(name, input) {
    const kind = name === 'render_chart'
        ? 'chart'
        : name === 'render_table'
            ? 'table'
            : name === 'render_stat'
                ? 'stat'
                : name === 'render_widget'
                    ? typeof input.kind === 'string'
                        ? input.kind
                        : null
                    : null;
    if (!kind)
        return null;
    const payload = name === 'render_widget' && typeof input.payload === 'object' && input.payload !== null
        ? {
            ...input.payload,
            // Style is top-level on every tool; the client reads it inside the
            // rendered payload, so fold it in on the way to the fence.
            ...(typeof input.accent === 'string' ? { accent: input.accent } : {}),
            ...(typeof input.variant === 'string' ? { variant: input.variant } : {}),
        }
        : input;
    return '```' + kind + '\n' + JSON.stringify(payload) + '\n```';
}
