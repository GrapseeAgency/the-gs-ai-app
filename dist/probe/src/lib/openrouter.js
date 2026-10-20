"use strict";
/**
 * PHASE 8.1 — OpenRouter free-tier provider (user-supplied keys, 2026-09-20).
 *
 * WHY: the primary provider (z-ai-web-dev-sdk) enforces an ACCOUNT-LEVEL
 * quota (proven 2026-09-20: 10 different model ids all returned the same
 * 429 for 5+ hours). The user supplied a pool of OpenRouter keys whose free
 * models are quota-independent, so text tiers mapped to OpenRouter stay
 * usable while the primary window is closed.
 *
 * DESIGN:
 *  - OpenAI-compatible REST (plain fetch — no new dependency).
 *  - Key POOL with rotation: on 401 (expired), 402 (credits), 429 (rate
 *    limit), or 5xx the next key is tried; a module-level cursor starts each
 *    request at the last known-good key to avoid thundering on key 0.
 *  - The same SSE wire format as the primary provider → reuses
 *    consumeSseStream from ai.ts (streaming semantics identical).
 *  - Timeouts mirror the primary provider's discipline: bounded connect and
 *    completion timeouts, retry-before-first-delta only.
 *  - PHASE 8.1b: an HTTP-200 completion with EMPTY content is treated as an
 *    upstream capacity hiccup (observed live on nemotron-3-ultra), not a
 *    success — the request rotates to the next key instead of saving a blank
 *    assistant bubble. Streaming rotates only while nothing has been
 *    forwarded (zero duplication risk for the client).
 *  - Keys live in THREE redundant layers (env → .secrets file → SecretVault
 *    db table — the db survived every platform wipe so far). Never logged in
 *    full (last-4 only), never committed, never sent to GitHub.
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.orCompleteChat = orCompleteChat;
exports.orStreamChat = orStreamChat;
exports.orPoolStatus = orPoolStatus;
exports.orHealthProbe = orHealthProbe;
const ai_1 = require("@/lib/ai");
const keypool_1 = require("@/lib/keypool");
const models_1 = require("@/lib/models");
const OPENROUTER_BASE = 'https://openrouter.ai/api/v1';
// PHASE 8.1b — several free models carry strong self-branding from their own
// training (observed live: nex-n2.5-pro answered "I'm Nex, from Nex-AGI" to a
// "which AI are you" question despite the GS persona system prompt). This
// suffix is appended to the system prompt on every OpenRouter call so every
// model in the chain answers as GS.
const IDENTITY_PIN_SUFFIX = '\n\nIdentity: you are GS, the assistant built for Grapsee Agency. When asked who you are, answer as GS. Never claim to be built by, named after, or representing any other company, product, or model brand.';
const CONNECT_TIMEOUT_MS = 15000;
const COMPLETION_TIMEOUT_MS = 90000;
/**
 * Durable key storage — FOUR redundant layers with a self-healing loader.
 * Implementation lives in src/lib/keypool.ts (forensic audit [2]):
 *   (a) process.env → (b) .secrets/openrouter.keys → (c) db/vault.db SQLite →
 *   (d) out-of-project /home/z/.gs-vault/openrouter.keys.
 * The loader logs which layer supplied the pool and back-fills wiped layers.
 */
// Cursor into the key pool — starts each request at the last known-good key.
let keyCursor = 0;
function maskKey(k) {
    return `***${k.slice(-4)}`;
}
function sleep(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms));
}
/**
 * Map the internal message shape to the OpenAI wire format and append the
 * GS identity pin to the leading system message (or prepend one when absent).
 */
function buildPinnedMessages(messages) {
    const mapped = messages.map((m) => ({
        role: (['assistant', 'system'].includes(m.role.toLowerCase())
            ? m.role.toLowerCase()
            : 'user'),
        content: m.content,
    }));
    if (mapped.length > 0 && mapped[0].role === 'system') {
        mapped[0] = { ...mapped[0], content: mapped[0].content + IDENTITY_PIN_SUFFIX };
    }
    else {
        mapped.unshift({ role: 'system', content: IDENTITY_PIN_SUFFIX.trim() });
    }
    return mapped;
}
/** One POST attempt with a specific key. Returns the raw Response. */
async function postChat(key, model, messages, stream, reasoning) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), stream ? CONNECT_TIMEOUT_MS : COMPLETION_TIMEOUT_MS);
    try {
        return await fetch(`${OPENROUTER_BASE}/chat/completions`, {
            method: 'POST',
            signal: controller.signal,
            headers: {
                Authorization: `Bearer ${key}`,
                'Content-Type': 'application/json',
                // Optional attribution headers per OpenRouter docs.
                'HTTP-Referer': 'https://grapsee.agency',
                'X-Title': 'GS AI',
            },
            body: JSON.stringify({
                model,
                messages: buildPinnedMessages(messages),
                stream,
                // FORENSIC AUDIT [25] — nondeterminism: we own this wire body, so pin
                // a low temperature for turn-to-turn consistency (same question, same
                // conversation → same answer; the free-model chain is quota-rotated,
                // temperature is the deterministic lever available here).
                temperature: 0.2,
                // FLASH MODE (Phase 2) — explicit reasoning toggle per turn mode;
                // omitted entirely when null (legacy body preserved).
                ...(reasoning !== undefined && reasoning !== null ? { reasoning } : {}),
            }),
        });
    }
    finally {
        clearTimeout(timer);
    }
}
/**
 * True when the failure is KEY- or CAPACITY-shaped (rotate to the next key)
 * rather than a permanent request problem (400 invalid body, 404 unknown
 * model → those should surface, not rotate).
 */
function shouldRotate(status) {
    return status === 401 || status === 402 || status === 408 || status === 429 || status >= 500;
}
/**
 * FLASH-MODE BUG 3 — dead-key cooldown. A key that just answered 429/402 is
 * almost certainly daily-quota-exhausted (50 req/day per free key); the old
 * rotation re-probed it on EVERY turn, costing a full OpenRouter round trip
 * (~0.5–1.5s each) before the winning key was reached — measured as the
 * device's "flash is slow". Cooled keys are skipped for the window; if every
 * key is cooled we try them anyway (recovery beats starvation).
 */
const KEY_COOLDOWN_MS = 10 * 60 * 1000;
const keyCooldownUntil = new Map();
function coolKey(key) {
    keyCooldownUntil.set(key, Date.now() + KEY_COOLDOWN_MS);
}
function keyIsCooled(key) {
    return (keyCooldownUntil.get(key) ?? 0) > Date.now();
}
function someLiveKey(keys) {
    return keys.some((k) => !keyIsCooled(k));
}
async function extractError(response) {
    try {
        const j = (await response.json());
        return j?.error?.message ?? `HTTP ${response.status}`;
    }
    catch {
        return `HTTP ${response.status}`;
    }
}
/** Non-streaming OpenRouter completion: key-pool rotation × model chain. */
async function orCompleteChat(messages, models, reasoning) {
    const keys = (await (0, keypool_1.loadKeyPool)()).keys;
    if (keys.length === 0)
        throw new Error('GS Free is offline: the OpenRouter key pool is empty (env, key file and db vault are all empty after the platform wipes). Re-provision keys to restore free-tier models.');
    let lastError = 'OpenRouter unavailable';
    for (let mi = 0; mi < models.length; mi++) {
        const model = models[mi];
        const isLastModel = mi === models.length - 1;
        for (let i = 0; i < keys.length; i++) {
            const key = keys[(keyCursor + i) % keys.length];
            if (keyIsCooled(key) && someLiveKey(keys)) {
                console.log(`OR-COOLDOWN model=${model} key=***${key.slice(-4)} skip (quota cooldown)`);
                continue;
            }
            try {
                const response = await postChat(key, model, messages, false, reasoning);
                if (!response.ok) {
                    lastError = await extractError(response);
                    if (shouldRotate(response.status)) {
                        if (response.status === 401 || response.status === 402 || response.status === 429)
                            coolKey(key);
                        console.log(`OR-ROTATE model=${model} status=${response.status} key=***${key.slice(-4)}`);
                        continue;
                    }
                    // Permanent for THIS model (400/404): fall through to the next
                    // model in the chain; only surface after the last one.
                    lastError = `OpenRouter ${model}: ${lastError}`;
                    if (isLastModel)
                        throw new Error(lastError);
                    break;
                }
                const completion = (await response.json());
                keyCursor = (keyCursor + i) % keys.length;
                const text = completion?.choices?.[0]?.message?.content;
                if (typeof text !== 'string' || text.trim().length === 0) {
                    // PHASE 8.1b — 200-with-empty-content is a capacity hiccup: rotate.
                    // (Otherwise a blank bubble gets persisted as a real answer.)
                    lastError = 'empty completion from upstream';
                    continue;
                }
                return {
                    text,
                    model: typeof completion?.model === 'string' ? completion.model : null,
                };
            }
            catch (e) {
                lastError = e instanceof Error ? e.message : String(e);
                if (lastError.startsWith('OpenRouter ') && isLastModel)
                    throw e;
                if (lastError.startsWith('OpenRouter '))
                    break; // next model in chain
            }
        }
    }
    throw new Error(`OpenRouter ${models.join(' | ')}: ${lastError}`);
}
/**
 * Streaming OpenRouter completion: key-pool rotation × model chain. Same
 * semantics as streamChat: deltas go to `onDelta`; a mid-stream failure after
 * output was forwarded throws (restarting would duplicate text on the client).
 */
async function orStreamChat(messages, models, onDelta, reasoning, onReasoning) {
    const keys = (await (0, keypool_1.loadKeyPool)()).keys;
    if (keys.length === 0)
        throw new Error('GS Free is offline: the OpenRouter key pool is empty (env, key file and db vault are all empty after the platform wipes). Re-provision keys to restore free-tier models.');
    let lastError = 'OpenRouter unavailable';
    for (let mi = 0; mi < models.length; mi++) {
        const model = models[mi];
        const isLastModel = mi === models.length - 1;
        for (let i = 0; i < keys.length; i++) {
            const key = keys[(keyCursor + i) % keys.length];
            if (keyIsCooled(key) && someLiveKey(keys)) {
                console.log(`OR-COOLDOWN model=${model} key=***${key.slice(-4)} skip (quota cooldown)`);
                continue;
            }
            let forwarded = false;
            try {
                const response = await postChat(key, model, messages, true, reasoning);
                if (!response.ok) {
                    lastError = await extractError(response);
                    if (shouldRotate(response.status)) {
                        if (response.status === 401 || response.status === 402 || response.status === 429)
                            coolKey(key);
                        console.log(`OR-ROTATE model=${model} status=${response.status} key=***${key.slice(-4)}`);
                        continue;
                    }
                    lastError = `OpenRouter ${model}: ${lastError}`;
                    if (isLastModel)
                        throw new Error(lastError);
                    break; // next model in chain
                }
                if (!response.body) {
                    lastError = 'OpenRouter returned no body';
                    continue;
                }
                keyCursor = (keyCursor + i) % keys.length;
                const guardedOnDelta = (t) => {
                    forwarded = true;
                    return onDelta(t);
                };
                const full = await (0, ai_1.consumeSseStream)(response.body, guardedOnDelta, undefined, onReasoning);
                if (full.trim().length === 0 && !forwarded) {
                    // PHASE 8.1b — nothing reached the client yet: safe to rotate.
                    lastError = 'empty completion from upstream';
                    continue;
                }
                return full;
            }
            catch (e) {
                lastError = e instanceof Error ? e.message : String(e);
                if (forwarded)
                    throw new Error(lastError);
                if (lastError.startsWith('OpenRouter ') && isLastModel)
                    throw e;
                if (lastError.startsWith('OpenRouter '))
                    break; // next model in chain
                // rotate to next key after a brief pause
                if (i < keys.length - 1)
                    await sleep(300);
            }
        }
    }
    throw new Error(`OpenRouter ${models.join(' | ')}: ${lastError}`);
}
/** Diagnostic: pool size + masked fingerprints (never full keys). */
async function orPoolStatus() {
    const keys = (await (0, keypool_1.loadKeyPool)()).keys;
    return { size: keys.length, fingerprints: keys.map(maskKey) };
}
const HEALTH_PROBE_TIMEOUT_MS = 10000;
const HEALTH_PROBE_MAX_KEYS = 3;
async function orHealthProbe() {
    const t0 = Date.now();
    const keys = (await (0, keypool_1.loadKeyPool)()).keys;
    if (keys.length === 0) {
        return { probed: false, ok: false, status: null, error: 'keypool empty', latencyMs: 0 };
    }
    const model = models_1.OPENROUTER_MODELS['gs-free'][0];
    let lastError = 'OpenRouter unavailable';
    let lastStatus = null;
    for (let i = 0; i < Math.min(HEALTH_PROBE_MAX_KEYS, keys.length); i++) {
        const key = keys[(keyCursor + i) % keys.length];
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), HEALTH_PROBE_TIMEOUT_MS);
        try {
            const response = await fetch(`${OPENROUTER_BASE}/chat/completions`, {
                method: 'POST',
                signal: controller.signal,
                headers: {
                    Authorization: `Bearer ${key}`,
                    'Content-Type': 'application/json',
                    'HTTP-Referer': 'https://grapsee.agency',
                    'X-Title': 'GS AI',
                },
                body: JSON.stringify({
                    model,
                    max_tokens: 1,
                    messages: [{ role: 'user', content: 'ping' }],
                }),
            });
            lastStatus = response.status;
            if (response.ok) {
                return { probed: true, ok: true, status: 200, error: null, latencyMs: Date.now() - t0 };
            }
            lastError = await extractError(response);
            // 400/404 are permanent for this request shape — rotating keys cannot
            // fix them; report the failure as the layer's real state.
            if (!shouldRotate(response.status))
                break;
        }
        catch (e) {
            lastError = e instanceof Error ? e.message : String(e);
        }
        finally {
            clearTimeout(timer);
        }
    }
    return {
        probed: true,
        ok: false,
        status: lastStatus,
        error: lastError.slice(0, 200),
        latencyMs: Date.now() - t0,
    };
}
