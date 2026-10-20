"use strict";
/**
 * BENCHMARK INFRA — PHASE 1: OpenAI-compatible shim (GET /models).
 * Standard discovery endpoint so harnesses can list what the shim serves.
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.runtime = void 0;
exports.GET = GET;
const server_1 = require("next/server");
exports.runtime = 'nodejs';
const MODELS = [
    { id: 'gs-ai', note: 'production synthesis flagship (z-ai glm-4.6, thinking disabled)' },
    { id: 'gs-ai-flash', note: 'production flash tier (z-ai glm-4.5-flash)' },
    { id: 'gs-ai-thinking', note: 'production thinking tier (z-ai glm-4.6, reasoning_effort=high)' },
    { id: 'openai/gpt-5.5', note: 'OpenRouter passthrough' },
    { id: 'anthropic/claude-opus-4.8', note: 'OpenRouter passthrough' },
    { id: 'google/gemini-3.1-pro', note: 'OpenRouter passthrough' },
];
async function GET() {
    const created = Math.floor(Date.now() / 1000);
    return server_1.NextResponse.json({
        object: 'list',
        data: MODELS.map((m) => ({
            id: m.id,
            object: 'model',
            created,
            owned_by: 'gs-bench-shim',
            note: m.note,
        })),
    }, { headers: { 'x-gs-bench': 'openai-shim' } });
}
