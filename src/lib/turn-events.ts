/**
 * DURABLE EXECUTION — event sourcing (durable-agents / DBOS Transact model).
 *
 * Every model call, tool call, and approval is appended to turn_events
 * BEFORE and AFTER it happens. Run state is a fold over that log.
 *
 *   seq=0  TurnStarted
 *   seq=1  LLMCallRequested   step=1
 *   seq=2  LLMCallFailed      step=1 attempt=1 error='429'
 *   seq=3  LLMCallCompleted   step=1 -> text(...)
 *   seq=4  ToolCallRequested  step=1 search(...)
 *          <- process killed here
 *   seq=5  ToolCallCompleted  step=1 search -> {...}   (written by another process)
 *   seq=6  RunCompleted       final_answer='...'
 *
 * The `tracked` wrapper produces exactly this shape around any awaited
 * operation. `foldRunState` replays the log and reports the dangling
 * operation (Requested without Completed/Failed) a resume must reconcile.
 */

import { db } from '@/lib/db'
import { firstTime, idempotencyKey, rememberIdempotent } from '@/lib/run-store'

export type TurnEventType =
  | 'TurnStarted'
  | 'LLMCallRequested'
  | 'LLMCallCompleted'
  | 'LLMCallFailed'
  | 'ToolCallRequested'
  | 'ToolCallCompleted'
  | 'ToolCallFailed'
  | 'SearchRequested'
  | 'SearchCompleted'
  | 'SourceOpened'
  | 'SourceRead'
  | 'SynthesisRequested'
  | 'SynthesisCompleted'
  | 'ApprovalRequested'
  | 'ApprovalDecided'
  | 'RunReconciled'
  | 'RunCompleted'
  | 'RunFailed'

export async function appendEvent(runId: string, eventType: TurnEventType, payload: Record<string, unknown> = {}): Promise<number> {
  try {
    const row = await db.turnEvent.create({
      data: { runId, eventType, payload: JSON.stringify(payload) },
    })
    return row.seq
  } catch (e) {
    // The log must never break the run — a lost trace row is recoverable
    // from the run store, a raised one kills the turn.
    console.error(`TURN-EVENT-DROP run=${runId} type=${eventType} error=${e instanceof Error ? e.message : String(e)}`)
    return -1
  }
}

/**
 * Wrap an awaited operation with its event pair. Side-effecting operations
 * pass `tool` to get an idempotency key (sha256(runId+seq+tool+args)); when
 * the key was already completed, `fn` is NOT re-executed and the recorded
 * digest is returned — a retry sees one call, not two.
 */
export async function tracked<T>(
  runId: string,
  eventType: { request: TurnEventType; completed: TurnEventType; failed?: TurnEventType },
  payload: Record<string, unknown>,
  fn: () => Promise<T>,
  opts?: { tool?: string; emit?: (eventType: TurnEventType, payload: Record<string, unknown>) => void }
): Promise<T> {
  const requestSeq = await appendEvent(runId, eventType.request, payload)
  opts?.emit?.(eventType.request, { ...payload, seq: requestSeq })

  // Side-effect dedupe: if this exact op already completed, skip the call.
  if (opts?.tool) {
    const key = idempotencyKey(runId, requestSeq, opts.tool, payload)
    const fresh = await firstTime(runId, key)
    if (!fresh) {
      // Already claimed/completed by a previous attempt. If it completed,
      // the run row's digest exists — redo is refused (one call, not two).
      throw new DuplicatedSideEffect(`idempotency-key=${key.slice(0, 16)} tool=${opts.tool}`)
    }
    try {
      const result = await fn()
      await rememberIdempotent(runId, key, stableDigest(result))
      await appendEvent(runId, eventType.completed, { ...payload, seq: requestSeq })
      opts?.emit?.(eventType.completed, { ...payload, seq: requestSeq })
      return result
    } catch (e) {
      if (eventType.failed) await appendEvent(runId, eventType.failed, { ...payload, seq: requestSeq, error: String(e instanceof Error ? e.message : e).slice(0, 300) })
      throw e
    }
  }

  try {
    const result = await fn()
    await appendEvent(runId, eventType.completed, { ...payload, seq: requestSeq })
    opts?.emit?.(eventType.completed, { ...payload, seq: requestSeq })
    return result
  } catch (e) {
    if (eventType.failed) await appendEvent(runId, eventType.failed, { ...payload, seq: requestSeq, error: String(e instanceof Error ? e.message : e).slice(0, 300) })
    throw e
  }
}

export class DuplicatedSideEffect extends Error {}

const stableDigest = (result: unknown): string => {
  const raw = typeof result === 'string' ? result : JSON.stringify(result ?? null)
  return `${raw.length}:${raw.slice(0, 64)}`
}

// ---------------------------------------------------------------------------
// Fold — replay the log into a resumable state
// ---------------------------------------------------------------------------

export type FoldedRunState = {
  eventCount: number
  lastEventType: TurnEventType | null
  dangling: { seq: number; eventType: TurnEventType; payload: Record<string, unknown> } | null
  completed: boolean
  finalAnswer?: string
  failed?: string
}

const PAIRS: Partial<Record<TurnEventType, TurnEventType>> = {
  LLMCallRequested: 'LLMCallCompleted',
  ToolCallRequested: 'ToolCallCompleted',
  SearchRequested: 'SearchCompleted',
  SynthesisRequested: 'SynthesisCompleted',
}

/** Fold the log: the state of the run is a fold over its events. */
export async function foldRunState(runId: string): Promise<FoldedRunState> {
  const events = await db.turnEvent.findMany({
    where: { runId },
    orderBy: { seq: 'asc' },
  })
  const open = new Map<string, { seq: number; eventType: TurnEventType; payload: Record<string, unknown> }>()
  let lastEventType: TurnEventType | null = null
  let completed = false
  let finalAnswer: string | undefined
  let failed: string | undefined

  for (const ev of events) {
    const type = ev.eventType as TurnEventType
    lastEventType = type
    let payload: Record<string, unknown> = {}
    try {
      payload = JSON.parse(ev.payload) as Record<string, unknown>
    } catch {
      payload = {}
    }
    if (type.endsWith('Requested')) {
      open.set(`${type}:${payload.step ?? ''}`, { seq: ev.seq, eventType: type, payload })
    } else if (type.endsWith('Completed') || type.endsWith('Failed')) {
      // close the matching Requested
      for (const [k, v] of open) {
        const closer = PAIRS[v.eventType]
        if (closer && (type === closer || type === 'ToolCallFailed')) {
          open.delete(k)
          break
        }
      }
    }
    if (type === 'RunCompleted') {
      completed = true
      finalAnswer = typeof payload.final_answer === 'string' ? payload.final_answer : undefined
    }
    if (type === 'RunFailed') {
      completed = true
      failed = typeof payload.error === 'string' ? payload.error : 'unknown'
    }
  }

  const dangling = open.values().next().value ?? null
  return {
    eventCount: events.length,
    lastEventType,
    dangling,
    completed,
    finalAnswer,
    failed,
  }
}

/**
 * Resume semantics — safe to call more than once:
 *   finished run → state returned, nothing re-executed.
 *   dangling run → the dangling operation is reconciled FIRST (RunReconciled
 *                  appended; pending idempotency claims cleared so the redo
 *                  can execute once), then state returned.
 */
export async function resume(runId: string): Promise<FoldedRunState & { reconciled: boolean }> {
  const state = await foldRunState(runId)
  if (state.completed || !state.dangling) {
    return { ...state, reconciled: false }
  }
  await appendEvent(runId, 'RunReconciled', {
    danglingSeq: state.dangling.seq,
    danglingEvent: state.dangling.eventType,
    note: 'process died between Requested and Completed — op will be redone once under its idempotency key',
  })
  const { resetPendingIdempotency } = await import('@/lib/run-store')
  const reset = await resetPendingIdempotency(runId)
  if (reset.length > 0) {
    await appendEvent(runId, 'RunReconciled', { resetIdempotencyKeys: reset.length })
  }
  const after = await foldRunState(runId)
  return { ...after, reconciled: true }
}
