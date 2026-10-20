"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.dynamic = void 0;
exports.GET = GET;
const server_1 = require("next/server");
const turn_producer_1 = require("@/lib/turn-producer");
const ai_1 = require("@/lib/ai");
const keypool_1 = require("@/lib/keypool");
const openrouter_1 = require("@/lib/openrouter");
exports.dynamic = 'force-dynamic';
const HEALTH_CACHE_OK_MS = 15000;
const HEALTH_CACHE_FAIL_MS = 5000;
const PRIMARY_PROBE_MESSAGES = [{ role: 'user', content: 'Reply with the single word: ok' }];
let cache = null;
async function probePrimary() {
    const t0 = Date.now();
    try {
        const text = await (0, ai_1.completeChat)(PRIMARY_PROBE_MESSAGES, null);
        return {
            ok: typeof text === 'string' && text.trim().length > 0,
            latencyMs: Date.now() - t0,
            error: null,
        };
    }
    catch (e) {
        return {
            ok: false,
            latencyMs: Date.now() - t0,
            error: (e instanceof Error ? e.message : String(e)).slice(0, 200),
        };
    }
}
async function GET() {
    const now = Date.now();
    if (cache && now - cache.at < (cache.ok ? HEALTH_CACHE_OK_MS : HEALTH_CACHE_FAIL_MS)) {
        return server_1.NextResponse.json({ ...cache.body, cached: true }, { status: cache.ok ? 200 : 503 });
    }
    const pool = await (0, keypool_1.loadKeyPool)();
    const or = await (0, openrouter_1.orHealthProbe)();
    let primary = null;
    // Pool loaded and OpenRouter answered: the fallback layer is serving.
    let upstreamAlive = or.ok;
    if (or.probed && !or.ok) {
        // Fallback layer down (invalid keys / provider failure) — the turn path
        // falls back to the primary provider, so probe it before declaring 503.
        primary = await probePrimary();
        upstreamAlive = primary.ok;
    }
    if (!or.probed) {
        // Pool empty: free tier offline. The primary is the only serving path.
        primary = await probePrimary();
        upstreamAlive = primary.ok;
    }
    const body = {
        status: upstreamAlive ? 'ok' : 'unavailable',
        backgroundRuns: (0, turn_producer_1.backgroundRunsEnabled)(),
        keypool: { layer: pool.layer, size: pool.keys.length },
        upstream: {
            openrouter: {
                probed: or.probed,
                ok: or.ok,
                status: or.status,
                latencyMs: or.latencyMs,
                error: or.error,
            },
            ...(primary !== null ? { primary } : {}),
        },
        checkedAt: new Date(now).toISOString(),
    };
    cache = { at: now, ok: upstreamAlive, body };
    return server_1.NextResponse.json(body, { status: upstreamAlive ? 200 : 503 });
}
