"use strict";
/**
 * BENCHMARK INFRA — Phase 1b: OpenAI Responses API adapter.
 *
 * WHY THIS EXISTS (raw evidence, run 36040334662): inspect-ai 0.3.268's
 * `openai` provider defaults to the OpenAI Responses API and POSTs
 * {base}/responses — the shim 404'd it (sandbox dev.log:
 * "POST /api/v1/openai/responses 404"). This endpoint adapts the Responses
 * shape onto the same raw synthesis path as /chat/completions (router still
 * bypassed, no system prompt injected).
 *
 * Adapters that want classic chat completions keep using /chat/completions
 * (inspect's `openai-api` provider, litellm, lm-eval, tau2).
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.maxDuration = exports.runtime = void 0;
exports.POST = POST;
const server_1 = require("next/server");
const ai_1 = require("@/lib/ai");
const openrouter_1 = require("@/lib/openrouter");
const shared_1 = require("../shared");
exports.runtime = 'nodejs';
exports.maxDuration = 300;
async function POST(req) {
    if (!(0, shared_1.authOkShim)(req)) {
        return server_1.NextResponse.json({ error: { message: 'Missing or invalid bearer key.', code: 'invalid_api_key' } }, { status: 401 });
    }
    let body;
    try {
        body = (await req.json());
    }
    catch {
        return server_1.NextResponse.json({ error: { message: 'Body must be valid JSON.', code: 'invalid_request_error' } }, { status: 400 });
    }
    if (body.stream) {
        return server_1.NextResponse.json({
            error: {
                message: 'stream=true is not supported by the shim responses adapter; use non-streaming clients.',
                code: 'not_implemented',
            },
        }, { status: 501 });
    }
    const messages = [];
    if (typeof body.instructions === 'string' && body.instructions.length > 0) {
        messages.push({ role: 'system', content: body.instructions });
    }
    if (typeof body.input === 'string') {
        messages.push({ role: 'user', content: body.input });
    }
    else if (Array.isArray(body.input)) {
        for (const item of body.input) {
            const role = typeof item?.role === 'string' ? item.role : 'user';
            messages.push({ role, content: (0, shared_1.flattenContentShim)(item?.content) });
        }
    }
    if (messages.length === 0) {
        return server_1.NextResponse.json({ error: { message: 'input must be a string or non-empty array.', code: 'invalid_request_error' } }, { status: 400 });
    }
    const resolved = resolveShimModelLocal(body.model);
    if (!resolved) {
        return server_1.NextResponse.json({
            error: {
                message: `Unknown model '${body.model}'. Serving: gs-ai, gs-ai-flash, gs-ai-thinking, or vendor/model passthrough.`,
                code: 'model_not_found',
            },
        }, { status: 404 });
    }
    const startedAt = Date.now();
    try {
        let text = '';
        let servedModel = null;
        if (resolved.backend === 'zai') {
            const meta = await (0, ai_1.completeChatWithMeta)(messages, resolved.providerModel, resolved.thinking);
            text = meta.text;
            servedModel = meta.model;
        }
        else {
            const meta = await (0, openrouter_1.orCompleteChat)(messages, resolved.models, null);
            text = meta.text;
            servedModel = meta.model;
        }
        const created = Math.floor(Date.now() / 1000);
        console.log(`BENCH-SHIM kind=responses requested=${body.model ?? 'gs-ai'} served=${servedModel ?? 'unknown'} latencyMs=${Date.now() - startedAt} chars=${text.length}`);
        return server_1.NextResponse.json({
            id: `resp-gs-${Date.now().toString(36)}`,
            object: 'response',
            created_at: created,
            status: 'completed',
            model: servedModel ?? body.model ?? 'gs-ai',
            output: [
                {
                    type: 'message',
                    id: `msg-gs-${Date.now().toString(36)}`,
                    role: 'assistant',
                    status: 'completed',
                    content: [{ type: 'output_text', text, annotations: [] }],
                },
            ],
            output_text: text,
            usage: {
                // Char-based ESTIMATE (documented); upstreams do not expose token counts.
                input_tokens: Math.ceil(messages.reduce((n, m) => n + m.content.length, 0) / 4),
                output_tokens: Math.ceil(text.length / 4),
                total_tokens: Math.ceil(messages.reduce((n, m) => n + m.content.length, 0) / 4) +
                    Math.ceil(text.length / 4),
            },
        });
    }
    catch (e) {
        const message = e instanceof Error ? e.message : String(e);
        console.error(`BENCH-SHIM-ERROR kind=responses requested=${body.model ?? 'gs-ai'}: ${message.slice(0, 300)}`);
        return server_1.NextResponse.json({ error: { message: message.slice(0, 500), code: 'upstream_error' } }, { status: 502 });
    }
}
// Local copy of the shim model resolution (chat/completions route owns the
// canonical one); kept identical so future drift shows in code review.
function resolveShimModelLocal(model) {
    const m = (model ?? 'gs-ai').trim();
    switch (m) {
        case 'gs-ai':
            return { backend: 'zai', providerModel: 'glm-4.6', thinking: { mode: 'flash' } };
        case 'gs-ai-flash':
            return { backend: 'zai', providerModel: 'glm-4.5-flash', thinking: { mode: 'flash' } };
        case 'gs-ai-thinking':
            return { backend: 'zai', providerModel: 'glm-4.6', thinking: { mode: 'thinking', effort: 'high' } };
        default:
            if (m.includes('/'))
                return { backend: 'openrouter', models: [m] };
            return null;
    }
}
