/**
 * EVAL INFRASTRUCTURE — LAYER 1: STRUCTURED TRACE STORE.
 *
 * Every turn emits ONE structured row into the SQLite `turns` table (Prisma
 * model TurnTrace, mapped via @@map). This formalizes the partial console
 * logs (GS-CAP, GS-ROUTER, GS-FREE-ROUTE) into queryable records that the
 * eval suite (Layer 4), the CI gate and the production monitor (Layer 5)
 * grade against. Graders read these rows — never console output.
 *
 * CONTRACT:
 *  - Writing a trace must NEVER break a turn: fire-and-forget, all failures
 *    degrade to a TRACE-WRITE-FAIL log line.
 *  - modelRoute is INTERNAL ONLY — it must never reach a client payload.
 *  - One row per turn, exactly once (written at the GS-CAP terminal points).
 */

import { db } from '@/lib/db'

export interface TurnTraceRecord {
  requestId: string
  conversationId: string
  messageId: string
  clientVersion: string
  backendRevision: string
  /** CHAT | WEB | DEEP_RESEARCH | TIME | VISION | DOCUMENT */
  capability: string
  /** explicit | recency | source_specific | none | stop_reuse | research_again | ... */
  trigger: string
  searchExecuted: boolean
  researchExecuted: boolean
  evidenceCount: number
  sourceCount: number
  /** Fresh sources whose retrieval status is 'retrieved' (page actually read). */
  sourcesRead: number
  /** Fresh sources whose retrieval status is 'failed'. */
  sourcesFailed: number
  /** Distinct domains returned this turn (fresh + reused evidence). */
  domains: string[]
  /** Internal execution route, e.g. 'openrouter/gs-swift' — never client-visible. */
  modelRoute: string
  /** done | error | clarify */
  finalStatus: string
  /** Which source ordinals the final answer cited. */
  cited: number[]
  latencyMs: number
  /** provider_429 | ssrf_block | parse_fail | provider_error | null */
  errorType: string | null
}

/**
 * Deterministic error classification from the raw provider error string.
 * Unknown failure shapes classify as 'provider_error' rather than being
 * silently dropped — the monitor's honest-failure graders depend on this.
 */
export function classifyErrorType(raw: string | null | undefined): string | null {
  if (!raw) return null
  if (/\b429\b/.test(raw)) return 'provider_429'
  if (/ssrf/i.test(raw)) return 'ssrf_block'
  if (/parse|json/i.test(raw) && /fail|error|invalid/i.test(raw)) return 'parse_fail'
  return 'provider_error'
}

/** Persist one trace row. Never throws — observability must not break turns. */
export async function writeTurnTrace(record: TurnTraceRecord): Promise<void> {
  try {
    await db.turnTrace.create({
      data: {
        requestId: record.requestId,
        conversationId: record.conversationId,
        messageId: record.messageId,
        clientVersion: record.clientVersion,
        backendRevision: record.backendRevision,
        capability: record.capability,
        trigger: record.trigger,
        searchExecuted: record.searchExecuted,
        researchExecuted: record.researchExecuted,
        evidenceCount: record.evidenceCount,
        sourceCount: record.sourceCount,
        sourcesRead: record.sourcesRead,
        sourcesFailed: record.sourcesFailed,
        domains: JSON.stringify(record.domains),
        modelRoute: record.modelRoute,
        finalStatus: record.finalStatus,
        cited: JSON.stringify(record.cited),
        latencyMs: Math.max(0, Math.round(record.latencyMs)),
        errorType: record.errorType,
      },
    })
  } catch (e) {
    const message = e instanceof Error ? e.message : String(e)
    console.error(`TRACE-WRITE-FAIL requestId=${record.requestId}: ${message.slice(0, 180)}`)
  }
}
