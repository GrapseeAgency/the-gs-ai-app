/** Respect provider rate limits across all request paths. An ambiguous 429
 * is treated as shared capacity, not an invitation to rotate credentials.
 * The provider's Retry-After is authoritative; no retry before the deadline. */
export function rateLimitDeadline(retryAfter: string | null, reason: string, now = Date.now()): number {
  let deadline = now + 60_000
  if (/free-models-per-day|per-day|daily limit/i.test(reason)) {
    const reset = new Date(now)
    reset.setUTCHours(24, 5, 0, 0)
    deadline = Math.max(deadline, reset.getTime())
  }
  if (retryAfter) {
    const raw = retryAfter.trim()
    const parsed = /^\d+(?:\.\d+)?$/.test(raw) ? now + Number(raw)*1000 : Date.parse(raw)
    if (Number.isFinite(parsed)) deadline = Math.max(deadline, parsed)
  }
  return deadline
}
export class ProviderRateLimit {
  private until = 0
  observe(retryAfter: string | null, reason: string, now = Date.now()): void {
    this.until = Math.max(this.until, rateLimitDeadline(retryAfter,reason,now))
  }
  remaining(now = Date.now()): number { return Math.max(0,this.until-now) }
}
