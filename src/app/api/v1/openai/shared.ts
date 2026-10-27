/**
 * BENCHMARK INFRA — Phase 1: shared shim helpers.
 */
import type { NextRequest } from 'next/server'

export function authOkShim(req: NextRequest): boolean {
  const required = process.env.GS_BENCH_API_KEY
  if (!required) return true
  return req.headers.get('authorization') === `Bearer ${required}`
}

/**
 * The served model id must name the provider that answered (rule 5: no silent
 * substitution). The free-chain router already returns `provider/model`; the
 * primary provider reports its own bare id, so it gets the same treatment here
 * rather than leaving half the scorecards unattributed.
 */
export function servedWithProvider(provider: string, model: string | null): string | null {
  if (!model) return null
  return model.includes('/') ? model : `${provider}/${model}`
}

/** Highest output budget any configured provider will honour in one call. */
export const SHIM_MAX_OUTPUT_TOKENS = 8192

/**
 * A harness may send a cap above every provider's ceiling, or a non-number.
 * The router drops providers that cannot honour the budget, so an inflated
 * value surfaces as 503 all_providers_unavailable — an outage-shaped answer to
 * a malformed request. Clamp instead; `undefined` still means "router default".
 */
export function shimMaxTokens(raw: unknown): number | undefined {
  if (raw === undefined || raw === null) return undefined
  const n = typeof raw === 'number' ? raw : Number(raw)
  if (!Number.isFinite(n)) return undefined
  return Math.min(SHIM_MAX_OUTPUT_TOKENS, Math.max(1, Math.floor(n)))
}

export function flattenContentShim(content: unknown): string {
  if (typeof content === 'string') return content
  if (Array.isArray(content)) {
    return content
      .map((part) =>
        part && typeof part === 'object' && typeof (part as { text?: unknown }).text === 'string'
          ? (part as { text: string }).text
          : ''
      )
      .join('')
  }
  return ''
}
