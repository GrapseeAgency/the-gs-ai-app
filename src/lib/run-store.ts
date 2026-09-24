/**
 * RUN STORE — the durable registry of detached turn runs (ADR-117).
 *
 * runId  — billing/correlation id. Stable across HITL interrupt + resumption.
 * streamId — transport id, fresh per start POST.
 *
 * Resume semantics (durable-agents model): runtime.resume(runId) is safe to
 * call more than once.
 *   - finished run      → returns the state, no side effects.
 *   - dangling run      → reconciles the dangling operation first (the
 *                         event-sourcing fold in turn-events.ts decides what
 *                         was interrupted), then the run may be re-produced
 *                         under a FRESH streamId.
 *   - more than once    → the second resume of a reconciled run is a no-op
 *                         state read.
 */

import { createHash } from 'crypto'
import { db } from '@/lib/db'

export type RunStatus =
  | 'queued'
  | 'running'
  | 'awaiting_approval'
  | 'completed'
  | 'failed'
  | 'killed'
  | 'cancelled'

export const TERMINAL_RUN_STATUSES: RunStatus[] = ['completed', 'failed', 'killed', 'cancelled']
export const isTerminalStatus = (s: string): boolean => TERMINAL_RUN_STATUSES.includes(s as RunStatus)

export type RunRecord = {
  id: string
  streamId: string
  conversationId: string
  userMessageId: string | null
  assistantMessageId: string | null
  status: RunStatus
  finalStatus: string | null
  error: string | null
  attempts: number
  idempotencyKeys: Record<string, string>
}

const toRecord = (r: {
  id: string
  streamId: string
  conversationId: string
  userMessageId: string | null
  assistantMessageId: string | null
  status: string
  finalStatus: string | null
  error: string | null
  attempts: number
  idempotencyKeys: string
}): RunRecord => ({
  id: r.id,
  streamId: r.streamId,
  conversationId: r.conversationId,
  userMessageId: r.userMessageId,
  assistantMessageId: r.assistantMessageId,
  status: r.status as RunStatus,
  finalStatus: r.finalStatus,
  error: r.error,
  attempts: r.attempts,
  idempotencyKeys: safeParse(r.idempotencyKeys),
})

const safeParse = (raw: string): Record<string, string> => {
  try {
    const v = JSON.parse(raw)
    return v && typeof v === 'object' ? (v as Record<string, string>) : {}
  } catch {
    return {}
  }
}

export async function createRun(input: {
  runId: string
  streamId: string
  conversationId: string
  userMessageId?: string | null
}): Promise<RunRecord> {
  const row = await db.turnRun.create({
    data: {
      id: input.runId,
      streamId: input.streamId,
      conversationId: input.conversationId,
      userMessageId: input.userMessageId ?? null,
      status: 'queued',
    },
  })
  return toRecord(row)
}

export async function getRun(runId: string): Promise<RunRecord | null> {
  const row = await db.turnRun.findUnique({ where: { id: runId } })
  return row ? toRecord(row) : null
}

export async function getRunByStream(streamId: string): Promise<RunRecord | null> {
  const row = await db.turnRun.findUnique({ where: { streamId } })
  return row ? toRecord(row) : null
}

// ---------------------------------------------------------------------------
// Lease claiming (worker topology: N workers, at-least-once via leases)
// ---------------------------------------------------------------------------

export const LEASE_TTL_MS = 120_000
const instanceId = `w-${process.pid}-${Math.random().toString(36).slice(2, 8)}`

/**
 * Claim a run for execution. A run is claimable when queued, or when its
 * previous holder's lease expired (stuck-job recovery, at-least-once).
 */
export async function claimRun(runId: string, ttlMs = LEASE_TTL_MS): Promise<RunRecord | null> {
  const now = new Date()
  const run = await db.turnRun.findUnique({ where: { id: runId } })
  if (!run) return null
  const claimable =
    run.status === 'queued' ||
    (run.status === 'running' && (!run.leaseExpiresAt || run.leaseExpiresAt < now))
  if (!claimable) return toRecord(run)
  const row = await db.turnRun.update({
    where: { id: runId },
    data: {
      status: 'running',
      leaseOwner: instanceId,
      leaseExpiresAt: new Date(Date.now() + ttlMs),
      attempts: { increment: 1 },
    },
  })
  return toRecord(row)
}

/** Refresh the lease while a run is alive (long research turns outlive the TTL). */
export async function heartbeatRun(runId: string, ttlMs = LEASE_TTL_MS): Promise<void> {
  await db.turnRun
    .updateMany({
      where: { id: runId, leaseOwner: instanceId },
      data: { leaseExpiresAt: new Date(Date.now() + ttlMs) },
    })
    .catch(() => undefined)
}

export async function finishRun(
  runId: string,
  status: RunStatus,
  finalStatus: string | null,
  error: string | null,
  assistantMessageId?: string | null
): Promise<void> {
  await db.turnRun.update({
    where: { id: runId },
    data: {
      status,
      finalStatus,
      error: error ? error.slice(0, 1000) : null,
      ...(assistantMessageId ? { assistantMessageId } : {}),
      leaseOwner: null,
      leaseExpiresAt: null,
    },
  })
}

/** Re-bind the run to a FRESH stream id (resumption window). */
export async function rebindStream(runId: string, streamId: string): Promise<void> {
  await db.turnRun.update({ where: { id: runId }, data: { streamId } })
}

// ---------------------------------------------------------------------------
// Idempotency keys (side-effect dedupe across restarts)
// ---------------------------------------------------------------------------

/** sha256(runId + seq + tool + args) — stable across restarts. */
export function idempotencyKey(runId: string, seq: number | string, tool: string, args: unknown): string {
  return createHash('sha256').update(`${runId}:${seq}:${tool}:${stableStringify(args)}`).digest('hex')
}

const stableStringify = (v: unknown): string => {
  if (v === null || v === undefined) return 'null'
  if (typeof v !== 'object') return JSON.stringify(v)
  if (Array.isArray(v)) return `[${v.map(stableStringify).join(',')}]`
  const obj = v as Record<string, unknown>
  return `{${Object.keys(obj)
    .sort()
    .map((k) => `${JSON.stringify(k)}:${stableStringify(obj[k])}`)
    .join(',')}}`
}

/** True (and marks the op as claimed) when this side-effecting op was never done. */
export async function firstTime(runId: string, key: string): Promise<boolean> {
  const run = await db.turnRun.findUnique({ where: { id: runId } })
  if (!run) return false
  const keys = safeParse(run.idempotencyKeys)
  if (keys[key] !== undefined) return false // already claimed or completed
  keys[key] = 'pending'
  await db.turnRun.update({ where: { id: runId }, data: { idempotencyKeys: JSON.stringify(keys) } })
  return true
}

export async function rememberIdempotent(runId: string, key: string, digest: string): Promise<void> {
  const run = await db.turnRun.findUnique({ where: { id: runId } })
  if (!run) return
  const keys = safeParse(run.idempotencyKeys)
  keys[key] = digest
  await db.turnRun.update({ where: { id: runId }, data: { idempotencyKeys: JSON.stringify(keys) } })
}

/**
 * Reconcile pass: keys left 'pending' by a killed process are interruptible
 * work whose outcome is unknown — a resume is allowed to redo them once.
 */
export async function resetPendingIdempotency(runId: string): Promise<string[]> {
  const run = await db.turnRun.findUnique({ where: { id: runId } })
  if (!run) return []
  const keys = safeParse(run.idempotencyKeys)
  const reset = Object.entries(keys)
    .filter(([, v]) => v === 'pending')
    .map(([k]) => k)
  if (reset.length > 0) {
    for (const k of reset) delete keys[k]
    await db.turnRun.update({ where: { id: runId }, data: { idempotencyKeys: JSON.stringify(keys) } })
  }
  return reset
}
