"use strict";
/**
 * In-memory sliding-window rate limiter (per-process).
 * Deliberately simple: single-node sandbox now, Redis-backed at scale
 * (see docs/ROADMAP.md Phase 2). Shape mirrors a future distributed limiter.
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.rateLimit = rateLimit;
exports.clientKey = clientKey;
const buckets = new Map();
// Periodic sweep so the map never grows unbounded.
const SWEEP_INTERVAL_MS = 60000;
let lastSweep = Date.now();
function sweep(now) {
    if (now - lastSweep < SWEEP_INTERVAL_MS)
        return;
    lastSweep = now;
    for (const [key, bucket] of buckets) {
        if (bucket.resetAt <= now)
            buckets.delete(key);
    }
}
function rateLimit(key, max, windowMs) {
    const now = Date.now();
    sweep(now);
    const bucket = buckets.get(key);
    if (!bucket || bucket.resetAt <= now) {
        buckets.set(key, { count: 1, resetAt: now + windowMs });
        return { allowed: true, remaining: max - 1, retryAfterSec: 0 };
    }
    if (bucket.count >= max) {
        return {
            allowed: false,
            remaining: 0,
            retryAfterSec: Math.max(1, Math.ceil((bucket.resetAt - now) / 1000)),
        };
    }
    bucket.count += 1;
    return { allowed: true, remaining: max - bucket.count, retryAfterSec: 0 };
}
/** Best-effort client identity for rate-limit keys. */
function clientKey(req, scope) {
    const fwd = req.headers.get('x-forwarded-for') ?? '';
    const ip = fwd.split(',')[0]?.trim() || req.headers.get('x-real-ip') || 'local';
    return `${scope}:${ip}`;
}
