/**
 * In-memory sliding-window rate limiter (per-process).
 * Deliberately simple: single-node sandbox now, Redis-backed at scale
 * (see docs/ROADMAP.md Phase 2). Shape mirrors a future distributed limiter.
 */

type Bucket = { count: number; resetAt: number }

const buckets = new Map<string, Bucket>()

// Periodic sweep so the map never grows unbounded.
const SWEEP_INTERVAL_MS = 60_000
let lastSweep = Date.now()

function sweep(now: number) {
  if (now - lastSweep < SWEEP_INTERVAL_MS) return
  lastSweep = now
  for (const [key, bucket] of buckets) {
    if (bucket.resetAt <= now) buckets.delete(key)
  }
}

export type RateLimitResult = { allowed: boolean; remaining: number; retryAfterSec: number }

export function rateLimit(
  key: string,
  max: number,
  windowMs: number
): RateLimitResult {
  const now = Date.now()
  sweep(now)

  const bucket = buckets.get(key)
  if (!bucket || bucket.resetAt <= now) {
    buckets.set(key, { count: 1, resetAt: now + windowMs })
    return { allowed: true, remaining: max - 1, retryAfterSec: 0 }
  }

  if (bucket.count >= max) {
    return {
      allowed: false,
      remaining: 0,
      retryAfterSec: Math.max(1, Math.ceil((bucket.resetAt - now) / 1000)),
    }
  }

  bucket.count += 1
  return { allowed: true, remaining: max - bucket.count, retryAfterSec: 0 }
}

/** Best-effort client identity for rate-limit keys. */
export function clientKey(req: Request, scope: string): string {
  const fwd = req.headers.get('x-forwarded-for') ?? ''
  const ip = fwd.split(',')[0]?.trim() || req.headers.get('x-real-ip') || 'local'
  return `${scope}:${ip}`
}
