"use strict";
/**
 * BENCHMARK INFRA — PHASE 1: OpenAI-compatible shim (chat/completions).
 *
 * Purpose: let standard eval harnesses (inspect-ai, lm-eval, HELM, tau2-bench)
 * drive GS AI with a single OpenAI-shaped endpoint. This is a MEASUREMENT
 * surface only:
 *   - It BYPASSES the production router (capability / search / evidence
 *     pipeline) entirely — a bench request is a raw synthesis call, exactly
 *     what a benchmark must measure.
 *   - It does NOT touch the production system prompt: no SYSTEM_PROMPT is
 *     injected. The harness's own instructions are passed through verbatim.
 *
 * Model resolution (`model` field):
 *   gs-ai              → production synthesis flagship  (z-ai, glm-4.6,
 *                        thinking disabled, temperature 0.2 — the exact
 *                        completeChatWithMeta synthesis defaults)
 *   gs-ai-flash        → production flash tier          (z-ai, glm-4.5-flash,
 *                        thinking disabled)
 *   gs-ai-thinking     → production thinking tier       (z-ai, glm-4.6,
 *                        thinking enabled, reasoning_effort=high)
 *   <vendor/model>     → OpenRouter passthrough (e.g. openai/gpt-5.5),
 *                        routed through the existing orCompleteChat key-pool
 *                        chain so competitor probes use the same accounting
 *                        as production free-tier traffic.
 *
 * NOTE ON PUBLISHED BASELINES: external comms refer to the GS AI flagship as
 * "GLM-5.2"; the serving catalogue in this repo maps the synthesis tiers to
 * glm-4.6 (src/lib/models.ts PROVIDER_MODELS). The shim never renames the
 * provider: the `model` field of every response carries the SERVING model id
 * returned by the upstream provider meta, so scorecards always show what
 * actually answered.
 *
 * Optional auth: if env GS_BENCH_API_KEY is set, requests must present
 * `Authorization: Bearer <key>`. Unset (current state) = open, same trust
 * level as the rest of the public /api/v1 surface.
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.maxDuration = exports.runtime = void 0;
exports.resolveShimModel = resolveShimModel;
exports.POST = POST;
const server_1 = require("next/server");
const ai_1 = require("@/lib/ai");
const openrouter_1 = require("@/lib/openrouter");
const models_1 = require("@/lib/models");
const scaffold_1 = require("@/lib/bench/scaffold");
exports.runtime = 'nodejs';
exports.maxDuration = 300;
function authOk(req) {
    const required = process.env.GS_BENCH_API_KEY;
    if (!required)
        return true;
    const header = req.headers.get('authorization') ?? '';
    return header === `Bearer ${required}`;
}
function flattenContent(content) {
    if (typeof content === 'string')
        return content;
    if (Array.isArray(content)) {
        return content
            .map((part) => part && typeof part === 'object' && typeof part.text === 'string'
            ? part.text
            : '')
            .join('');
    }
    return '';
}
function toInternalMessages(messages) {
    if (!Array.isArray(messages) || messages.length === 0) {
        return { ok: false, error: 'messages must be a non-empty array' };
    }
    const out = messages.map((m) => ({
        role: typeof m?.role === 'string' ? m.role : 'user',
        content: flattenContent(m?.content),
    }));
    if (out.some((m) => m.role !== 'system' && m.role !== 'user' && m.role !== 'assistant')) {
        return { ok: false, error: `unsupported role in messages (allowed: system, user, assistant)` };
    }
    return { ok: true, messages: out };
}
/**
 * Shim-only model resolution. Deliberately does NOT import resolveModelRoute
 * (that is the production router entry) — the shim pins explicit targets so a
 * future routing change can never silently invalidate a benchmark run.
 */
function resolveShimModel(model) {
    // BENCH SCAFFOLD — `gs-ai@<profile>` selects a scaffold variant; the bare id
    // (and `@baseline`) keeps the byte-identical raw-synthesis path so every
    // historical run remains comparable (rule 2: same model, only scaffold moves).
    const parsed = (0, scaffold_1.parseScaffoldModel)(model);
    if (!parsed)
        return null;
    const m = parsed.base;
    const profile = parsed.profile;
    const resolved = resolveShimBaseModel(m);
    return resolved ? { ...resolved, profile } : null;
}
function resolveShimBaseModel(m) {
    switch (m) {
        case 'gs-ai':
            return { backend: 'zai', providerModel: 'glm-4.6', thinking: { mode: 'flash' } };
        case 'gs-ai-flash':
            return { backend: 'zai', providerModel: 'glm-4.5-flash', thinking: { mode: 'flash' } };
        case 'gs-ai-thinking':
            return {
                backend: 'zai',
                providerModel: 'glm-4.6',
                thinking: { mode: 'thinking', effort: 'high' },
            };
        default:
            if (m.includes('/'))
                return { backend: 'openrouter', models: [m] };
            return null;
    }
}
function profileOf(model) {
    return (0, scaffold_1.parseScaffoldModel)(model)?.profile ?? 'baseline';
}
function openAiError(status, code, message) {
    return server_1.NextResponse.json({ error: { message, type: code, code } }, { status, headers: { 'x-gs-bench': 'openai-shim' } });
}
async function POST(req) {
    if (!authOk(req)) {
        return openAiError(401, 'invalid_api_key', 'Missing or invalid bearer key (GS_BENCH_API_KEY).');
    }
    let body;
    try {
        body = (await req.json());
    }
    catch {
        return openAiError(400, 'invalid_request_error', 'Body must be valid JSON.');
    }
    const parsed = toInternalMessages(body.messages);
    if (!parsed.ok)
        return openAiError(400, 'invalid_request_error', parsed.error);
    const resolved = resolveShimModel(body.model);
    if (!resolved) {
        return openAiError(404, 'model_not_found', `Unknown model '${body.model}'. Serving: gs-ai, gs-ai-flash, gs-ai-thinking, any vendor/model id for OpenRouter passthrough, and gs-ai@<baseline|aci|verification|context|router> scaffold variants.`);
    }
    const profile = profileOf(body.model);
    if (profile !== 'baseline' && resolved.backend !== 'zai') {
        // The scaffold levers pin the generator to the gs-ai tier by design (rule 2).
        // A vendor passthrough with a profile would silently measure a different
        // generator — refuse instead (rule 5: no silent substitutions).
        return openAiError(400, 'invalid_request_error', `Scaffold profile '${profile}' is only valid on gs-ai tier models (generator is pinned); got '${body.model}'.`);
    }
    const startedAt = Date.now();
    try {
        let text = '';
        let servedModel = null;
        let scaffoldTelemetry = null;
        if (profile !== 'baseline') {
            // BENCH SCAFFOLD — lever path. The scaffold layer owns generator/verifier/
            // planner role calls and returns per-call telemetry (rule 4).
            const outcome = await (0, scaffold_1.runScaffoldCompletion)(parsed.messages, profile);
            text = outcome.text;
            servedModel = outcome.servedModel;
            scaffoldTelemetry = {
                profile: outcome.profile,
                generator_calls: outcome.generatorCalls,
                verifier_calls: outcome.verifierCalls,
                planner_calls: outcome.plannerCalls,
                summarizer_calls: outcome.summarizerCalls,
                tool_calls: outcome.toolCallsByName,
                tool_arg_errors: outcome.toolArgErrors,
                offloads: outcome.offloads,
                iterations: outcome.iterations,
                verifier_verdicts: outcome.verifierVerdicts,
                regenerations: outcome.regenerations,
                unverified: outcome.unverified,
                scaffold_latency_ms: outcome.latencyMs,
                notes: outcome.notes.slice(0, 12),
            };
        }
        else if (resolved.backend === 'zai') {
            // RUN 37769190819 postmortem: sustained tau2 load trips the z-ai account
            // throttle; the shim surfaced the breaker error as a hard 502, which
            // tau2 recorded as 113 infra errors and an empty result dir. The
            // production turn path already falls back to the gs-free OpenRouter
            // chain; the shim now does the same and says so in its log line.
            try {
                const meta = await (0, ai_1.completeChatWithMeta)(parsed.messages, resolved.providerModel, resolved.thinking);
                text = meta.text;
                servedModel = meta.model;
            }
            catch (e) {
                const msg = e instanceof Error ? e.message : String(e);
                if (!(0, ai_1.zaiThrottleActive)() && !/throttled|circuit open|429/i.test(msg))
                    throw e;
                console.log(`BENCH-SHIM zai throttled (circuit open) — falling back to gs-free OpenRouter chain requested=${body.model ?? 'gs-ai'}`);
                const meta = await (0, openrouter_1.orCompleteChat)(parsed.messages, models_1.OPENROUTER_MODELS['gs-free'], null);
                text = meta.text;
                servedModel = meta.model;
            }
        }
        else {
            const meta = await (0, openrouter_1.orCompleteChat)(parsed.messages, resolved.models, null);
            text = meta.text;
            servedModel = meta.model;
        }
        const id = `chatcmpl-gs-${Date.now().toString(36)}`;
        const created = Math.floor(Date.now() / 1000);
        const latencyMs = Date.now() - startedAt;
        console.log(`BENCH-SHIM kind=chat_completions requested=${body.model ?? 'gs-ai'} profile=${profile} served=${servedModel ?? 'unknown'} latencyMs=${latencyMs} chars=${text.length}`);
        const promptTokens = Math.ceil(parsed.messages.reduce((n, m) => n + m.content.length, 0) / 4);
        const completionTokens = Math.ceil(text.length / 4);
        const payload = {
            id,
            object: 'chat.completion',
            created,
            model: servedModel ?? body.model ?? 'gs-ai',
            system_fingerprint: profile === 'baseline' ? 'gs-bench-shim-v1' : `gs-bench-shim-v1;profile=${profile}`,
            choices: [
                {
                    index: 0,
                    message: { role: 'assistant', content: text },
                    finish_reason: 'stop',
                    logprobs: null,
                },
            ],
            usage: {
                // Char-based ESTIMATE; the shim's upstreams do not expose token
                // counts on every path. Not used for scoring.
                prompt_tokens: promptTokens,
                completion_tokens: completionTokens,
                total_tokens: promptTokens + completionTokens,
            },
        };
        if (scaffoldTelemetry)
            payload.gs_scaffold = scaffoldTelemetry;
        return server_1.NextResponse.json(payload, {
            headers: {
                'x-gs-bench': 'openai-shim',
                ...(profile !== 'baseline' ? { 'x-gs-scaffold': profile } : {}),
            },
        });
    }
    catch (e) {
        const message = e instanceof Error ? e.message : String(e);
        console.error(`BENCH-SHIM-ERROR kind=chat_completions requested=${body.model ?? 'gs-ai'} profile=${profile}: ${message.slice(0, 300)}`);
        return openAiError(502, 'upstream_error', message.slice(0, 500));
    }
}
