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

/** Whether a queued run is old enough to claim after a capacity retry. */
export function queuedRunIsDue(updatedAt: Date, attempts: number, now = Date.now()): boolean {
  if (attempts <= 0) return true
  return updatedAt.getTime() + freeRetryDelayMs(attempts, now) <= now
}
