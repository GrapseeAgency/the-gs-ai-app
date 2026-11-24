/**
 * Capacity policy for the GS AI free-first execution path.
 *
 * A free provider becoming unavailable is a queue condition, not a reason to
 * silently spend credits. The durable run queue owns the job; this module
 * supplies the small, deterministic policy helpers used by the worker and
 * provider adapters.
 */

export const FREE_ONLY_DEFAULT = true

export function freeOnlyEnabled(env: NodeJS.ProcessEnv = process.env): boolean {
  return env.GS_FREE_ONLY !== '0'
}

/** A typed, retryable signal for exhausted free capacity. */
export class FreeCapacityUnavailableError extends Error {
  readonly code = 'free_capacity_unavailable' as const
  readonly retryAfterMs: number

  constructor(message = 'No free provider capacity is currently available.', retryAfterMs = 5_000) {
    super(message)
    this.name = 'FreeCapacityUnavailableError'
    this.retryAfterMs = retryAfterMs
  }
}

export function isFreeCapacityUnavailableError(error: unknown): error is FreeCapacityUnavailableError {
  return error instanceof FreeCapacityUnavailableError ||
    (error instanceof Error && (error as Error & { code?: string }).code === 'free_capacity_unavailable')
}

/** Exponential queue backoff, bounded so a recovered provider is noticed promptly. */
export function freeRetryDelayMs(attempt: number, now = Date.now()): number {
  const normalized = Math.max(0, Math.floor(attempt))
  const delay = Math.min(5 * 60_000, 2_000 * 2 ** Math.min(normalized, 8))
  const jitter = (normalized * 977) % 1_000
  return delay + (now % 1_000 === 0 ? jitter : 0)
}

/** Persist the provider's actual Retry-After alongside local backoff. Never
 * shorten a daily quota window to the local five-minute retry cap. */
export function nextFreeAttemptAt(attempt: number, retryAfterMs: number, now = Date.now()): Date {
  const providerDelay = Number.isFinite(retryAfterMs) && retryAfterMs > 0
    ? Math.min(retryAfterMs, 8.64e15 - now) : 0
  return new Date(now + Math.max(freeRetryDelayMs(attempt, now), providerDelay))
}

/** Legacy queued rows still use the old backoff until their next retry. */
export function queuedRunIsDue(updatedAt: Date, attempts: number, now = Date.now(), retryNotBefore?: Date | null): boolean {
  if (retryNotBefore) return retryNotBefore.getTime() <= now
  if (attempts <= 0) return true
  return updatedAt.getTime() + freeRetryDelayMs(attempts, updatedAt.getTime()) <= now
}
