/**
 * BENCHMARK INFRA — Phase 1: shared shim helpers.
 */
import type { NextRequest } from 'next/server'

export function authOkShim(req: NextRequest): boolean {
  const required = process.env.GS_BENCH_API_KEY
  if (!required) return true
  return req.headers.get('authorization') === `Bearer ${required}`
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
