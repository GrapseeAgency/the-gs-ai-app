import { describe, expect, test } from 'bun:test'
import {
  FreeCapacityUnavailableError,
  freeOnlyEnabled,
  freeRetryDelayMs,
  isFreeCapacityUnavailableError,
  queuedRunIsDue,
} from './capacity-policy'

describe('capacity policy', () => {
  test('free-only is the default and can be explicitly disabled', () => {
    expect(freeOnlyEnabled({} as unknown as NodeJS.ProcessEnv)).toBe(true)
    expect(freeOnlyEnabled({ GS_FREE_ONLY: '0' } as unknown as NodeJS.ProcessEnv)).toBe(false)
    expect(freeOnlyEnabled({ GS_FREE_ONLY: '1' } as unknown as NodeJS.ProcessEnv)).toBe(true)
  })

  test('capacity errors are typed and detectable', () => {
    const error = new FreeCapacityUnavailableError('busy', 1234)
    expect(error.code).toBe('free_capacity_unavailable')
    expect(error.retryAfterMs).toBe(1234)
    expect(isFreeCapacityUnavailableError(error)).toBe(true)
    expect(isFreeCapacityUnavailableError(new Error('nope'))).toBe(false)
  })

  test('retry backoff grows and remains bounded', () => {
    const first = freeRetryDelayMs(1, 1_000)
    const second = freeRetryDelayMs(2, 1_000)
    const later = freeRetryDelayMs(99, 1_000)
    expect(second).toBeGreaterThan(first)
    expect(later).toBeLessThanOrEqual(5 * 60_000 + 999)
  })

  test('first attempts are due and retries are deferred', () => {
    const now = 100_000
    expect(queuedRunIsDue(new Date(now), 0, now)).toBe(true)
    expect(queuedRunIsDue(new Date(now), 1, now)).toBe(false)
    expect(queuedRunIsDue(new Date(now - 10_000), 1, now)).toBe(true)
  })
})
