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
import type { Prisma } from '@prisma/client'
import { nextFreeAttemptAt, queuedRunIsDue } from './capacity-policy'

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
  retryNotBefore: Date | null
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
  retryNotBefore: Date | null
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
  retryNotBefore: r.retryNotBefore,
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
  startFacts?: Record<string, unknown>
  requiresProvider?: boolean
}): Promise<RunRecord> {
  const create = db.turnRun.create({
    data: {
      id: input.runId,
      streamId: input.streamId,
      conversationId: input.conversationId,
      userMessageId: input.userMessageId ?? null,
      status: 'queued',
      requiresProvider: input.requiresProvider ?? true,
    },
  })
  // A worker must never observe a queued row before its original request
  // exists. The transaction is the admission boundary for durable work.
  const row = input.startFacts
    ? (await db.$transaction([create, db.turnEvent.create({data: {
        runId: input.runId, eventType: 'TurnStarted', payload: JSON.stringify(input.startFacts),
      }})]))[0]
    : await create
  return toRecord(row)
}

export async function getRun(runId: string): Promise<RunRecord | null> {
  const row = await db.turnRun.findUnique({ where: { id: runId } })
  return row ? toRecord(row) : null
}

/** Select actual due work before the bounded batch. Local-only runs and
 * persisted-answer recovery cannot be starved by a queue of remote work. */
export async function selectDueRuns(providerAvailable:boolean, limit:number, now = new Date()) {
  const candidates = await db.turnRun.findMany({
    where: {status:'queued', AND:[
      {OR:[{retryNotBefore:null},{retryNotBefore:{lte:now}}]},
      ...(providerAvailable ? [] : [{OR:[{requiresProvider:false},{assistantMessageId:{not:null}}]}]),
    ]},
    orderBy:{createdAt:'asc'},
    take:Math.max(1,limit),
  })
  return candidates.filter(row=>queuedRunIsDue(row.updatedAt,row.attempts,now.getTime(),row.retryNotBefore))
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
  // Compare-and-set on the database row, not a read followed by an
  // unconditional update. Exactly one contender acquires this attempt.
  const claimed = await db.turnRun.updateMany({
    where: { id: runId, OR: [
      {status: 'queued', OR: [{retryNotBefore:null}, {retryNotBefore:{lte:now}}]},
      {status: 'running', OR: [{leaseExpiresAt: null}, {leaseExpiresAt: {lt: now}}]},
    ] },
    data: {
      status: 'running', leaseOwner: instanceId, retryNotBefore: null,
      leaseExpiresAt: new Date(now.getTime() + ttlMs), attempts: { increment: 1 },
    },
  })
  if (claimed.count !== 1) return null
  const row = await db.turnRun.findUnique({ where: { id: runId } })
  return row?.leaseOwner === instanceId && row.status === 'running' ? toRecord(row) : null
}

const ownedAttempt = (runId: string, attempt: number) => ({
  id: runId, status: 'running', leaseOwner: instanceId, attempts: attempt,
  leaseExpiresAt: { gt: new Date() },
})

/** Only the current, unexpired attempt can renew its own lease. */
export async function heartbeatRun(runId: string, ttlMs = LEASE_TTL_MS, attempt?: number): Promise<void> {
  await db.turnRun.updateMany({
    where: attempt === undefined ? { id: runId, leaseOwner: instanceId, status: 'running' } : ownedAttempt(runId, attempt),
    data: { leaseExpiresAt: new Date(Date.now() + ttlMs) },
  }).catch(() => undefined)
}

export async function finishRun(
  runId: string,
  status: RunStatus,
  finalStatus: string | null,
  error: string | null,
  assistantMessageId?: string | null,
  attempt?: number,
): Promise<boolean> {
  const updated = await db.turnRun.updateMany({
    where: attempt === undefined ? {id: runId} : ownedAttempt(runId, attempt),
    data: {
      status, finalStatus, retryNotBefore: null, error: error ? error.slice(0, 1000) : null,
      ...(assistantMessageId ? { assistantMessageId } : {}),
      leaseOwner: null, leaseExpiresAt: null,
    },
  })
  return updated.count === 1
}

/** A stale producer cannot put the newer attempt back in the queue. */
export async function requeueRun(runId: string, reason: string, attempt?: number, retryAfterMs = 0): Promise<boolean> {
  const updated = await db.turnRun.updateMany({
    where: attempt === undefined ? {id: runId} : ownedAttempt(runId, attempt),
    data: {
      status: 'queued', finalStatus: null, error: reason.slice(0, 1000),
      retryNotBefore: nextFreeAttemptAt(attempt ?? 0, retryAfterMs),
      requiresProvider: true,
      leaseOwner: null, leaseExpiresAt: null,
    },
  })
  return updated.count === 1
}

export type AssistantData = {conversationId: string; role: string; content: string; clarifyOptions?: string}
export type AssistantSourceData = Omit<Prisma.MessageSourceCreateManyInput, 'messageId'>

export async function persistAssistant(data: AssistantData, sources: AssistantSourceData[] = []) {
  return db.message.create({data: {...data, ...(sources.length ? {sources: {createMany: {data: sources}}} : {})}})
}

/** Commit the answer, citations and run link in ONE transaction. A crashed
 * producer leaves either all of them or none; retries reuse the same answer.
 * The initial conditional write fences the transaction against stale owners
 * and obtains the SQLite write lock before reading the run's answer link. */
export async function persistRunAssistant(runId: string, attempt: number, data: AssistantData, sources: AssistantSourceData[] = []) {
  return db.$transaction(async tx => {
    const locked = await tx.turnRun.updateMany({
      where: {...ownedAttempt(runId, attempt), conversationId: data.conversationId},
      data: {leaseExpiresAt: new Date(Date.now() + LEASE_TTL_MS)},
    })
    if (locked.count !== 1) throw new Error('Turn execution lease lost')
    const run = await tx.turnRun.findUniqueOrThrow({where: {id: runId}})
    if (run.assistantMessageId) {
      const existing = await tx.message.findUnique({where:{id:run.assistantMessageId}})
      if (!existing || existing.conversationId !== data.conversationId || existing.role !== 'assistant')
        throw new Error('Durable assistant link invalid')
      return existing
    }
    const saved = await tx.message.create({data:{...data,...(sources.length ? {sources:{createMany:{data:sources}}} : {})}})
    await tx.turnRun.update({where:{id:runId},data:{assistantMessageId:saved.id}})
    return saved
  })
}

/** Re-bind a parked run's transport only after explicit approval. */
export async function rebindStream(runId: string, streamId: string): Promise<void> {
  await db.turnRun.update({where:{id:runId},data:{streamId}})
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
