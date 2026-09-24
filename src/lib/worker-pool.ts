/**
 * PHASE 8 — WORKER POOL (horizontal-scaling topology).
 *
 * API service: receives requests, ENQUEUES tasks (the run row with status
 * 'queued' IS the queue entry — the shared store is the queue), returns
 * streamId/runId immediately, serves SSE subscriptions. NEVER runs a turn.
 *
 * Worker service: DEQUEUES tasks, runs the turn (call LLM, search, tools),
 * publishes chunks to the stream broker, stores final state. NEVER accepts
 * HTTP. Independent workers scale horizontally without touching the API tier
 * because all coordination is lease-claiming on the shared store.
 *
 * At-least-once delivery via leases: a claimed run holds a lease with a TTL;
 * a worker that dies mid-turn leaves a STUCK job that the recovery scheduler
 * below re-enqueues. Idempotency keys + the run's assistantMessageId reuse
 * guarantee no duplicate work on the redo.
 *
 * SANDBOX NOTE (single open port): in this deployment the worker loop runs
 * inside the API process as an in-process consumer — the TOPOLOGY is
 * preserved (routes only enqueue; produceRun is invoked exclusively by this
 * loop) and the loop module is lift-and-shift into a standalone process with
 * zero code change (it opens no socket and shares only the store).
 */

import { db } from '@/lib/db'
import { appendEvent } from '@/lib/turn-events'
import { publishEnd } from '@/lib/stream-broker'
import { produceRun } from '@/lib/turn-producer'

const WORKER_CONCURRENCY = Number(process.env.GS_WORKER_CONCURRENCY ?? 3)
const TICK_MS = 1_000
const RECOVERY_INTERVAL_MS = 15_000
const MAX_ATTEMPTS = 3

const loopState = globalThis as unknown as { __gsWorkerLoop?: { active: number; timer: NodeJS.Timeout; recoveryTimer: NodeJS.Timeout } }

/** Drain queued runs — one tick of the worker pool. */
async function tick(): Promise<void> {
  const loop = loopState.__gsWorkerLoop
  if (!loop) return
  if (loop.active >= WORKER_CONCURRENCY) return
  try {
    const candidates = await db.turnRun.findMany({
      where: { status: 'queued' },
      orderBy: { createdAt: 'asc' },
      take: Math.max(1, WORKER_CONCURRENCY - loop.active),
    })
    for (const candidate of candidates) {
      if (loop.active >= WORKER_CONCURRENCY) break
      loop.active++
      console.log(`GS-WORKER claim run=${candidate.id} attempts=${candidate.attempts} active=${loop.active}`)
      void produceRun(candidate.id)
        .catch((e) => {
          console.error(`GS-WORKER run=${candidate.id} crashed: ${e instanceof Error ? e.message : String(e)}`)
        })
        .finally(() => {
          loop.active--
        })
    }
  } catch {
    // queue probe failure — next tick retries
  }
}

/**
 * STUCK-JOB RECOVERY: jobs stuck in RUNNING past their lease are re-enqueued
 * (at-least-once) or, past MAX_ATTEMPTS, marked killed with an honest end
 * marker on their stream. Runs whose answer already persisted are reconciled
 * by the producer (one answer, not two).
 */
async function recoverStuckJobs(): Promise<void> {
  try {
    const stale = await db.turnRun.findMany({
      where: { status: 'running', leaseExpiresAt: { lt: new Date() } },
      take: 20,
    })
    for (const run of stale) {
      if (run.attempts >= MAX_ATTEMPTS) {
        await db.turnRun.update({
          where: { id: run.id },
          data: { status: 'killed', finalStatus: 'error', error: 'stuck-job recovery: max attempts exceeded', leaseOwner: null, leaseExpiresAt: null },
        })
        await appendEvent(run.id, 'RunFailed', { reason: 'stuck-job recovery: max attempts exceeded', attempts: run.attempts })
        await publishEnd(run.streamId, 'killed')
        console.error(`GS-WORKER-RECOVERY run=${run.id} killed after ${run.attempts} attempts`)
      } else {
        // atomic-ish re-queue guarded by the same stale predicate
        const res = await db.turnRun.updateMany({
          where: { id: run.id, status: 'running', leaseExpiresAt: { lt: new Date() } },
          data: { status: 'queued', leaseOwner: null, leaseExpiresAt: null },
        })
        if (res.count > 0) {
          await appendEvent(run.id, 'RunReconciled', { reason: 'stuck-job recovery: re-enqueued', nextAttempt: run.attempts + 1 })
          console.warn(`GS-WORKER-RECOVERY run=${run.id} re-enqueued (attempt ${run.attempts + 1} next)`)
        }
      }
    }
  } catch {
    // recovery is best-effort — the next interval retries
  }
}

/**
 * Start the worker loop exactly once per process (idempotent across dev
 * hot-reloads via the global flag). Returns the drain handle for tests.
 */
export function ensureWorkerLoop(): void {
  if (loopState.__gsWorkerLoop) return
  loopState.__gsWorkerLoop = { active: 0, timer: null as unknown as NodeJS.Timeout, recoveryTimer: null as unknown as NodeJS.Timeout }
  loopState.__gsWorkerLoop.timer = setInterval(() => void tick(), TICK_MS)
  loopState.__gsWorkerLoop.recoveryTimer = setInterval(() => void recoverStuckJobs(), RECOVERY_INTERVAL_MS)
  console.log(`GS-WORKER-LOOP started concurrency=${WORKER_CONCURRENCY} recovery=${RECOVERY_INTERVAL_MS / 1000}s`)
}

/**
 * API-side enqueue: register the job and wake the loop immediately (the
 * interval is the fallback cadence; the kick gives ~zero pickup latency).
 */
export async function enqueueRun(runId: string): Promise<void> {
  ensureWorkerLoop()
  // the run row (status 'queued') is the queue entry — just kick the tick
  void tick()
  void runId
}
