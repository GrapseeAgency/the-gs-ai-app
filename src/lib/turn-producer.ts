/**
 * TURN PRODUCER — the detached owner of a background run (ADR-117).
 *
 * The producer OWNS THE RUN: execution, event log, run-state bookkeeping and
 * the terminal stream marker. It never talks to a client; the SSE endpoint is
 * a mere subscriber of the broker stream it publishes to.
 *
 * Client-disconnect behavior is structural here: there is no reference to any
 * Request/Response object — a subscriber dying cannot cancel this code.
 */

import { publishChunk, publishEnd, type BrokerTerminalStatus } from '@/lib/stream-broker'
import { claimRun, finishRun, heartbeatRun, LEASE_TTL_MS } from '@/lib/run-store'
import { appendEvent } from '@/lib/turn-events'
import { runTurn, type Prep } from '@/lib/turn-executor'

export const backgroundRunsEnabled = (): boolean => process.env.BACKGROUND_RUNS_ENABLED === '1'

export function newStreamId(): string {
  return `st-${crypto.randomUUID().replace(/-/g, '').slice(0, 20)}`
}

export function newRunId(): string {
  return `run-${crypto.randomUUID().replace(/-/g, '')}`
}

/**
 * Execute a claimed run to completion. ALWAYS terminates the stream: the
 * terminal marker is written on every in-process exit path (the try/finally
 * below is the asyncio.shield equivalent — abnormal producer death still
 * lands the end marker). Out-of-process death is covered by subscriber-side
 * orphan exit + the broker TTL.
 */
export async function produceRun(runId: string, prep: Prep): Promise<void> {
  const claimed = await claimRun(runId)
  if (!claimed || claimed.status !== 'running') {
    // already claimed by another worker, or terminal — do not double-run
    console.log(`RUN-PRODUCE-SKIP run=${runId} status=${claimed?.status ?? 'missing'}`)
    return
  }
  const streamId = claimed.streamId

  // Serialized publish chain — ordering guarantee for the fire-and-forget
  // broker writes (a retrying chunk must never overtake a later one).
  let chain: Promise<unknown> = Promise.resolve()
  const push = (event: string, data: string) => {
    chain = chain.then(() => publishChunk(streamId, { event, data }))
  }

  // Lease keeper — a long research turn must not lose its lease mid-flight.
  const keeper = setInterval(() => void heartbeatRun(runId), Math.floor(LEASE_TTL_MS / 3))

  try {
    const result = await runTurn(prep, push)
    await chain // flush every data chunk before the terminal marker
    const runStatus = result.finalStatus === 'error' ? 'failed' : 'completed'
    await finishRun(runId, runStatus, result.finalStatus, result.errorRaw ?? null, result.assistantMessageId ?? null)
    if (result.finalStatus === 'error') {
      await appendEvent(runId, 'RunFailed', { error: (result.errorRaw ?? '').slice(0, 300) })
    } else {
      await appendEvent(runId, 'RunCompleted', {
        final_status: result.finalStatus,
        assistant_message_id: result.assistantMessageId ?? null,
      })
    }
    await publishEnd(streamId, result.finalStatus === 'error' ? 'error' : 'completed')
    console.log(`RUN-DONE run=${runId} stream=${streamId} final=${result.finalStatus}`)
  } catch (e) {
    const raw = String(e instanceof Error ? e.message : e)
    try {
      await chain
      await finishRun(runId, 'failed', 'error', raw, null)
      await appendEvent(runId, 'RunFailed', { error: raw.slice(0, 300) })
      await publishEnd(streamId, 'error')
    } catch (inner) {
      console.error(`RUN-FINISH-FAIL run=${runId} error=${inner instanceof Error ? inner.message : String(inner)}`)
    }
    console.error(`RUN-CRASH run=${runId} stream=${streamId} error=${raw.slice(0, 300)}`)
  } finally {
    clearInterval(keeper)
  }
}

/** Park a run for human approval — no thread held, resumption re-produces. */
export async function parkForApproval(runId: string, tool: string, args: unknown): Promise<void> {
  const { db } = await import('@/lib/db')
  const { idempotencyKey } = await import('@/lib/run-store')
  const key = idempotencyKey(runId, 'approval', tool, args)
  await db.approvalRequest.create({
    data: { runId, tool, args: JSON.stringify(args), idempotencyKey: key, status: 'pending' },
  })
  await appendEvent(runId, 'ApprovalRequested', { tool })
  await finishRun(runId, 'awaiting_approval', null, null, null)
}

/** Terminal status mapping for parked-run subscribers. */
export const parkedEndStatus: BrokerTerminalStatus = 'cancelled'
