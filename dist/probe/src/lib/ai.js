"use strict";
/**
 * Server-only AI wrapper around z-ai-web-dev-sdk.
 *
 * Do NOT import this module from client components — the SDK is
 * backend-only (see skills/LLM/SKILL.md).
 *
 * SDK shape (verified against node_modules/z-ai-web-dev-sdk/dist/index.d.ts):
 *   const zai = await ZAI.create()
 *   const res = await zai.chat.completions.create({ messages, stream?, thinking? })
 *
 * Streaming behaviour (verified in SDK source): when `stream: true` is set and
 * the upstream answers with an event-stream / text-plain content type, the SDK
 * returns the RAW Web ReadableStream (response.body) — the caller must parse
 * the SSE bytes itself. Otherwise a normal JSON completion object is returned.
 */
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.VISION_OCR_FALLBACK_PREFIX = exports.VISION_GROUNDING_PROMPT = exports.WIDGET_PROMPT = exports.SYSTEM_PROMPT = void 0;
exports.consumeSseStream = consumeSseStream;
exports.zaiThrottleActive = zaiThrottleActive;
exports.streamChat = streamChat;
exports.completeChat = completeChat;
exports.completeChatWithMeta = completeChatWithMeta;
exports.streamVisionChat = streamVisionChat;
exports.completeVisionChat = completeVisionChat;
const z_ai_web_dev_sdk_1 = __importDefault(require("z-ai-web-dev-sdk"));
/**
 * PHASE 8.3 (user directive: "STOP USING THE SYSTEM PROMPT AS THE
 * APPLICATION BRAIN") — stripped to the operator's five-clause contract
 * (2026-10-14 directive, TASK 4). Identity, the request, tool integrity,
 * citation integrity, voice. Nothing else: no behaviour rules, no
 * formatting instructions, no hedge language, no self-limitation copy.
 * Every "do not" that is not integrity-critical is gone — the renderer
 * owns formatting, the scaffold owns feeding the model correctly.
 *
 * Word count: 33 (was 98).
 */
exports.SYSTEM_PROMPT = `You are GS. Answer the user's actual request. Use the tools the application provides and do not invent tool results. Cite sources when you use retrieved evidence. Match the user's language and register. Never use emoji anywhere in your replies. Write plain prose: prefer commas, colons and parentheses over em dashes.`;
/**
 * GENUI widget contract (docs/GENUI.md). The provider has no tool-calling, so
 * interactive rendering is prompt-driven: the model embeds widget fences in the
 * markdown stream and the clients render them natively (chart/stat/table/
 * compare/progress/timeline). Hard rules keep this safe:
 *
 *  - a widget SUPPLEMENTS the prose around it, never replaces it;
 *  - the body is strict JSON, double-quoted, no comments, no trailing commas;
 *  - a widget only ever contains data the answer already established — it is a
 *    visualisation of said facts, never a place for new claims;
 *  - at most 3 widgets per answer, only when genuinely useful.
 *
 * Invalid payloads degrade to code blocks on the client, so a malformed widget
 * costs one ugly block, not the answer.
 */
var prompt_1 = require("./widgets/prompt");
Object.defineProperty(exports, "WIDGET_PROMPT", { enumerable: true, get: function () { return prompt_1.WIDGET_PROMPT; } });
/**
 * PHASE 7 vision grounding, REWRITTEN by the operator's 2026-10-14 directive
 * (TASK 3). The old rule 2 literally taught the model to hedge ("mark it as
 * such ('appears to be', 'resembles')") — with a flash model that instruction
 * is the whole failure: every identification arrived padded as "likely a
 * couch", "appear to be candies". The rewritten contract is the opposite:
 * read what is actually visible, quote image text verbatim when asked, refuse
 * plainly when the image cannot be read, and never pad. Grounding stays;
 * hedge-teaching is deleted.
 */
exports.VISION_GROUNDING_PROMPT = `When this conversation includes attached images, ground every answer in them. The images are primary evidence: state what they actually show, and when the user asks about text in an image, quote that text verbatim. Never invent details that are not visibly present. If the image does not contain the answer, say so plainly and name what is missing. If something cannot be identified from the image, describe what is visible and say it cannot be identified. Do not use "likely", "appears to be", "possibly" or similar qualifiers: either the image identifies it or it does not. Read text exactly as written; if text cannot be read reliably, say which text is unreadable and offer no candidate readings or brand guesses.`;
/**
 * TASK 3 vision input contract — the exact prefix the operator specified for
 * the OCR fallback path. Applied ONLY when the image bytes cannot be sent to
 * the vision model (storage missing / corrupt / unsupported format) and an
 * on-device OCR sidecar exists. It tells the model the text is complete
 * content, not a description, so it reads instead of guessing.
 */
exports.VISION_OCR_FALLBACK_PREFIX = `The following is OCR text extracted from the user's image. It is complete text content, not a description.`;
function toSdkMessages(messages) {
    return messages.map((m) => ({
        role: (m.role.toLowerCase() === 'assistant'
            ? 'assistant'
            : m.role.toLowerCase() === 'system'
                ? 'system'
                : 'user'),
        content: m.content,
    }));
}
function isWebReadableStream(value) {
    return (typeof value === 'object' &&
        value !== null &&
        typeof value.getReader === 'function');
}
/** Extract the text delta from one upstream SSE payload (defensive, OpenAI-ish shapes). */
function extractDelta(parsed) {
    const p = parsed;
    if (!p)
        return '';
    const direct = p.choices?.[0]?.delta?.content ?? p.choices?.[0]?.message?.content ?? p.delta?.content ?? '';
    return typeof direct === 'string' ? direct : '';
}
/** The provider's thinking trace (`choices[0].delta.reasoning_content`, the
 * glm/zai stream shape for enabled thinking). Empty for every non-thinking
 * response, so callers can wire it unconditionally. */
function extractReasoningDelta(parsed) {
    const p = parsed;
    if (!p)
        return '';
    const r = p.choices?.[0]?.delta?.reasoning_content ??
        p.choices?.[0]?.delta?.reasoning ??
        p.choices?.[0]?.message?.reasoning_content ??
        '';
    return typeof r === 'string' ? r : '';
}
/**
 * Consume the SDK's raw SSE byte stream, forwarding each text delta to
 * `onDelta` and returning the accumulated full text.
 */
async function consumeSseStream(body, onDelta, onModel, onReasoning) {
    const reader = body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    let full = '';
    let terminated = false;
    const handleLine = async (rawLine) => {
        const line = rawLine.trim();
        if (!line || line.startsWith(':'))
            return false; // heartbeat / comment / empty
        let payload = line;
        if (payload.startsWith('data:'))
            payload = payload.slice(5).trim();
        if (!payload)
            return false;
        if (payload === '[DONE]')
            return true;
        let delta = '';
        try {
            const parsed = JSON.parse(payload);
            if (onModel && typeof parsed?.model === 'string' && parsed.model.length > 0)
                onModel(parsed.model);
            delta = extractDelta(parsed);
            // REASONING TRACE — thinking-enabled calls stream the model's reasoning
            // separately from the answer; carried out when a listener is wired
            // (turn-executor forwards it to clients as `reasoning` events).
            if (onReasoning) {
                const r = extractReasoningDelta(parsed);
                if (r)
                    await onReasoning(r);
            }
        }
        catch {
            // Non-JSON payload (plain-text chunk) — treat the whole payload as a delta.
            delta = payload;
        }
        if (delta) {
            full += delta;
            await onDelta(delta);
        }
        return false;
    };
    while (!terminated) {
        const { done, value } = await reader.read();
        if (done)
            break;
        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() ?? ''; // keep the trailing partial line in the buffer
        for (const line of lines) {
            if (await handleLine(line)) {
                terminated = true;
                break;
            }
        }
    }
    // Flush whatever is left in the buffer (upstream may omit the trailing newline).
    if (!terminated && buffer)
        await handleLine(buffer);
    return full;
}
/**
 * Provider resilience:
 * - TIMEOUT: every upstream attempt is bounded (slow streams that have already
 *   produced output are NOT aborted mid-flight — only the initial connection
 *   is raced against the deadline).
 * - RETRY: non-streaming calls retry once on failure. Streaming calls retry
 *   only when the failure happens BEFORE the first delta was forwarded —
 *   restarting mid-stream would duplicate output for the consumer.
 */
const CONNECT_TIMEOUT_MS = 15000;
const COMPLETION_TIMEOUT_MS = 60000;
const MAX_ATTEMPTS = 2;
/**
 * PHASE 8.1 — account-level throttle circuit breaker. The 2026-09-20 window
 * proved the primary provider can 429 for HOURS; without a breaker every turn
 * pays the full doomed-retry latency (~30s) before any OpenRouter fallback
 * fires. When a 429 is seen, all primary calls short-circuit for 10 minutes.
 * The breaker is per-process state — restarts simply re-probe.
 */
let zaiThrottledUntil = 0;
function zaiThrottleActive() {
    return Date.now() < zaiThrottledUntil;
}
function markZaiThrottled() {
    zaiThrottledUntil = Date.now() + 10 * 60000;
    console.log('[ZAI-BREAKER] 429 observed — primary provider calls short-circuited for 10 minutes');
}
function sleep(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms));
}
function errMessage(e) {
    return e instanceof Error ? e.message : String(e);
}
function zaiThinkingBody(thinking) {
    if (thinking?.mode === 'thinking') {
        return thinking.effort
            ? { thinking: { type: 'enabled' }, reasoning_effort: thinking.effort }
            : { thinking: { type: 'enabled' } };
    }
    return { thinking: { type: 'disabled' } };
}
/**
 * Streaming chat completion. Calls `onDelta` for every token/chunk received
 * and resolves with the complete assistant text.
 *
 * PHASE 8.1 — `providerModel` (from the gs-* catalogue mapping) is sent when
 * present. If the provider rejects it as invalid/unknown (HTTP 400/404), the
 * loop retries modelless (the exact pre-8.1 behavior) — a bad mapping can
 * degrade to the default model, never break chat. Account-level 429s are
 * NOT retried modelless (same quota pool either way).
 *
 * FLASH MODE (Phase 2) — `thinking` carries the turn's resolved mode; the
 * provider body's `thinking` field is built from it (null = legacy disabled).
 */
async function streamChat(messages, onDelta, providerModel, thinking, onReasoning) {
    let lastError = 'Upstream unavailable';
    let activeModel = typeof providerModel === 'string' && providerModel.length > 0 ? providerModel : null;
    for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
        let forwarded = false;
        try {
            if (zaiThrottleActive())
                throw new Error('z-ai throttled (circuit open)');
            const zai = await z_ai_web_dev_sdk_1.default.create();
            const body = {
                messages: toSdkMessages(messages),
                stream: true,
                ...zaiThinkingBody(thinking ?? null),
                // FORENSIC AUDIT [25] — consistency lever (ignored if the endpoint
                // does not accept it; never breaks the request).
                temperature: 0.2,
            };
            if (activeModel)
                body.model = activeModel;
            const response = await Promise.race([
                zai.chat.completions.create(body),
                new Promise((_, reject) => setTimeout(() => reject(new Error('Upstream connect timeout')), CONNECT_TIMEOUT_MS)),
            ]);
            if (isWebReadableStream(response)) {
                const guardedOnDelta = (t) => {
                    forwarded = true;
                    return onDelta(t);
                };
                return await consumeSseStream(response, guardedOnDelta, undefined, onReasoning);
            }
            // Upstream ignored `stream: true` — fall back to a single full-text delta.
            const completion = response;
            const text = completion?.choices?.[0]?.message?.content;
            const full = typeof text === 'string' ? text : '';
            if (full) {
                forwarded = true;
                await onDelta(full);
            }
            return full;
        }
        catch (e) {
            lastError = errMessage(e);
            if (/429|Too many requests/i.test(lastError))
                markZaiThrottled();
            // PHASE 8.1 — mapped model rejected by the provider (invalid/unknown):
            // drop the model key and retry modelless immediately.
            if (activeModel && /status\s+(400|404)\b/.test(lastError)) {
                activeModel = null;
                continue;
            }
            // Mid-stream failure after output was already forwarded: restarting
            // would duplicate text on the client — surface the error instead.
            if (forwarded)
                throw new Error(lastError);
            if (attempt < MAX_ATTEMPTS)
                await sleep(400 * attempt);
        }
    }
    throw new Error(lastError);
}
/** Non-streaming chat completion. Resolves with the assistant text. */
async function completeChat(messages, providerModel, thinking) {
    return (await completeChatWithMeta(messages, providerModel, thinking)).text;
}
/**
 * PHASE 6 — non-streaming completion with serving-model metadata. Resolves
 * with the assistant text and the serving model id.
 *
 * MODEL SELECTION: the `model` key is OMITTED by default, exactly like the
 * SDK's own vision CLI — the endpoint then serves its compatible default
 * (observed live as `glm-5v-turbo` on the vision path). PHASE 8.1 adds the
 * optional `providerModel` for the TEXT path (gs-* catalogue mapping): sent
 * when present; on an invalid/unknown-model rejection (HTTP 400/404) the loop
 * retries modelless, so a wrong mapping degrades to the default, never breaks
 * chat. Account-level 429s are NOT retried modelless.
 */
async function completeChatWithMeta(messages, providerModel, thinking, 
// BENCH SCAFFOLD — optional sampling temperature (default 0.2 unchanged).
// Additive-only: every existing caller keeps the exact previous behavior.
temperature) {
    let lastError = 'Upstream unavailable';
    let activeModel = typeof providerModel === 'string' && providerModel.length > 0 ? providerModel : null;
    for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
        try {
            if (zaiThrottleActive())
                throw new Error('z-ai throttled (circuit open)');
            const zai = await z_ai_web_dev_sdk_1.default.create();
            const body = {
                messages: toSdkMessages(messages),
                stream: false,
                ...zaiThinkingBody(thinking ?? null),
                // FORENSIC AUDIT [25] — consistency lever (see streamChat).
                temperature: typeof temperature === 'number' && Number.isFinite(temperature) ? temperature : 0.2,
            };
            if (activeModel)
                body.model = activeModel;
            const completion = (await Promise.race([
                zai.chat.completions.create(body),
                new Promise((_, reject) => setTimeout(() => reject(new Error('Upstream completion timeout')), COMPLETION_TIMEOUT_MS)),
            ]));
            const text = completion?.choices?.[0]?.message?.content;
            return {
                text: typeof text === 'string' ? text : '',
                model: typeof completion?.model === 'string' ? completion.model : null,
            };
        }
        catch (e) {
            lastError = errMessage(e);
            if (/429|Too many requests/i.test(lastError))
                markZaiThrottled();
            // PHASE 8.1 — mapped model rejected by the provider: retry modelless.
            if (activeModel && /status\s+(400|404)\b/.test(lastError)) {
                activeModel = null;
                continue;
            }
            if (attempt < MAX_ATTEMPTS)
                await sleep(400 * attempt);
        }
    }
    throw new Error(lastError);
}
/**
 * PHASE 6 — streaming vision completion. Same SSE wire, same timeout /
 * retry-before-first-delta semantics as [streamChat]; the request goes to the
 * provider's real vision endpoint with typed content parts (text + image
 * data URLs). The vision endpoint was probed to stream the same
 * OpenAI-ish `choices[0].delta.content` chunks.
 *
 * The `model` key is omitted from the body (cast required — the SDK's type
 * marks it required, but the SDK's own vision CLI omits it and the endpoint
 * serves its default; see docs/VISION.md for the audit evidence).
 */
async function streamVisionChat(messages, onDelta) {
    let lastError = 'Upstream unavailable';
    for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
        let forwarded = false;
        try {
            const zai = await z_ai_web_dev_sdk_1.default.create();
            const body = {
                messages,
                stream: true,
                thinking: { type: 'disabled' },
            };
            const response = await Promise.race([
                zai.chat.completions.createVision(
                // `model` deliberately omitted — CLI-parity; see doc comment.
                body),
                new Promise((_, reject) => setTimeout(() => reject(new Error('Upstream connect timeout')), CONNECT_TIMEOUT_MS)),
            ]);
            if (isWebReadableStream(response)) {
                let servedModel = null;
                const guardedOnDelta = (t) => {
                    forwarded = true;
                    return onDelta(t);
                };
                const text = await consumeSseStream(response, guardedOnDelta, (m) => (servedModel = m));
                return { text, model: servedModel };
            }
            // Upstream ignored `stream: true` — fall back to a single full-text delta.
            const completion = response;
            const text = completion?.choices?.[0]?.message?.content;
            const full = typeof text === 'string' ? text : '';
            if (full) {
                forwarded = true;
                await onDelta(full);
            }
            return {
                text: full,
                model: typeof completion?.model === 'string' ? completion.model : null,
            };
        }
        catch (e) {
            lastError = errMessage(e);
            // Mid-stream failure after output was already forwarded: restarting
            // would duplicate text on the client — surface the error instead.
            if (forwarded)
                throw new Error(lastError);
            if (attempt < MAX_ATTEMPTS)
                await sleep(400 * attempt);
        }
    }
    throw new Error(lastError);
}
/** Non-streaming vision completion. */
async function completeVisionChat(messages) {
    let lastError = 'Upstream unavailable';
    for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
        try {
            const zai = await z_ai_web_dev_sdk_1.default.create();
            const body = {
                messages,
                stream: false,
                thinking: { type: 'disabled' },
            };
            const completion = (await Promise.race([
                zai.chat.completions.createVision(
                // `model` deliberately omitted — CLI-parity; see doc comment.
                body),
                new Promise((_, reject) => setTimeout(() => reject(new Error('Upstream completion timeout')), COMPLETION_TIMEOUT_MS)),
            ]));
            const text = completion?.choices?.[0]?.message?.content;
            return {
                text: typeof text === 'string' ? text : '',
                model: typeof completion?.model === 'string' ? completion.model : null,
            };
        }
        catch (e) {
            lastError = errMessage(e);
            if (attempt < MAX_ATTEMPTS)
                await sleep(400 * attempt);
        }
    }
    throw new Error(lastError);
}
