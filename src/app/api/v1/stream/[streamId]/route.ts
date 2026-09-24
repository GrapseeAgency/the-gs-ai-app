/**
 * GET /api/v1/stream/{streamId} — SSE SUBSCRIBER (transport only).
 *
 * PHASE 1 — the subscriber is a mere relay: broker chunks → SSE lines.
 * Nothing else. The producer (detached) owns the run; killing this
 * connection never touches it.
 *
 * PHASE 2 — keepalive every 15s (cleared on every exit path) and
 * Last-Event-ID reconnection: every chunk carries `id: <broker entry id>`
 * (the <ms>-<seq> entry id directly); on reconnect the client's
 * Last-Event-ID header is the XRANGE cursor.
 *
 * PHASE 4 — three convergent end paths:
 *   1. real END        — the terminal broker entry relayed/streamed.
 *   2. terminal short-circuit — a late join on a terminal run whose retained
 *      stream is gone returns a single end immediately (no subscription).
 *   3. synthetic end   — during sustained silence the subscriber reconciles
 *      against the run store; if the run went terminal without its end
 *      event arriving, one is emitted here.
 */

import { NextRequest } from 'next/server'
import { parseEntry, readAfter } from '@/lib/stream-broker'
import { getRunByStream, isTerminalStatus } from '@/lib/run-store'

export const dynamic = 'force-dynamic'

type RouteContext = { params: Promise<{ streamId: string }> }

const KEEPALIVE_MS = 15_000
// Subscriber poll cadence — SQLite has no blocking XREAD; the subscriber
// loop reads with short commands (this IS the dual-pool isolation in the
// SQLite backend: no connection is ever held open by a subscription, so
// subscribers can never starve publishes).
const POLL_ACTIVE_MS = 250
const POLL_IDLE_MS = 1_000
// Sustained silence before a run-store reconciliation (synthetic end path).
const RECONCILE_AFTER_MS = 45_000
// A run with no update for this long is orphaned (hard-kill recovery owns
// the authoritative flip; the subscriber exits honestly instead of hanging).
const ORPHAN_SILENCE_MS = 180_000

export async function GET(req: NextRequest, { params }: RouteContext) {
  const { streamId } = await params
  const run = await getRunByStream(streamId)
  if (!run) {
    return new Response('not found', { status: 404 })
  }

  const lastEventId = req.headers.get('last-event-id') ?? ''
  let cursor = lastEventId

  // PHASE 4 — terminal short-circuit: a late join on a terminal run whose
  // retained stream no longer has anything after the cursor gets one end
  // immediately instead of a subscription that would hang on heartbeats.
  if (isTerminalStatus(run.status) && cursor) {
    const entries = await readAfter(streamId, cursor)
    const hasEnd = entries.some((e) => parseEntry(e.payload).end)
    if (!hasEnd) {
      return sseSingleEnd(mapStatus(run.status), run.id)
    }
  }
  if (isTerminalStatus(run.status) && !cursor) {
    const entries = await readAfter(streamId, '')
    if (entries.length === 0) {
      return sseSingleEnd(mapStatus(run.status), run.id)
    }
  }

  const enc = new TextEncoder()
  const stream = new ReadableStream<Uint8Array>({
    async start(controller) {
      let closed = false
      const write = (text: string): boolean => {
        if (closed) return false
        try {
          controller.enqueue(enc.encode(text))
          return true
        } catch {
          closed = true // subscriber gone — producer keeps running
          return false
        }
      }

      // PHASE 2 — keepalive: aggressive proxies kill idle SSE; 15s is
      // comfortably under their idle windows. Cleared on every exit path.
      let lastActivityAt = Date.now()
      const keepaliveTimer = setInterval(() => {
        write(': keepalive\n\n')
      }, KEEPALIVE_MS)

      const close = () => {
        clearInterval(keepaliveTimer)
        closed = true
        try {
          controller.close()
        } catch {
          // already closed by the runtime on client disconnect
        }
      }

      const reqAbort = req.signal
      reqAbort.addEventListener('abort', () => {
        // subscriber disconnect — cleanup only, the run is untouched
        close()
      })

      let idlePollMs = POLL_ACTIVE_MS
      try {
        // SSE preamble
        write(': connected\n\n')
        for (;;) {
          if (closed) return
          const entries = await readAfter(streamId, cursor)
          for (const entry of entries) {
            cursor = entry.entryId
            lastActivityAt = Date.now()
            const parsed = parseEntry(entry.payload)
            if (parsed.end) {
              // real END — path 1: ordering and late-join replay preserved.
              write(`: end ${parsed.end.status}\n\n`)
              return close()
            }
            if (parsed.chunk) {
              // Every chunk carries its id — the client's Last-Event-ID is
              // exactly this broker entry id.
              write(`id: ${entry.entryId}\ndata: ${JSON.stringify(parsed.chunk)}\n\n`)
            }
            idlePollMs = POLL_ACTIVE_MS
          }

          if (entries.length === 0) {
            // PHASE 4 — synthetic end (path 3): sustained silence triggers a
            // run-store reconciliation.
            const silence = Date.now() - lastActivityAt
            if (silence > RECONCILE_AFTER_MS) {
              const fresh = await getRunByStream(streamId)
              if (!fresh) return close() // run purged — nothing to follow
              if (isTerminalStatus(fresh.status)) {
                write(
                  `data: ${JSON.stringify({ event: 'end', data: JSON.stringify({ status: mapStatus(fresh.status), synthetic: true }) })}\n\n`
                )
                return close()
              }
              if (fresh.status === 'awaiting_approval') {
                write(`data: ${JSON.stringify({ event: 'status', data: '"awaiting_approval"' })}\n\n`)
                write(
                  `data: ${JSON.stringify({ event: 'end', data: JSON.stringify({ status: 'cancelled', awaitingApproval: true }) })}\n\n`
                )
                return close()
              }
              if (silence > ORPHAN_SILENCE_MS) {
                write(
                  `data: ${JSON.stringify({ event: 'end', data: JSON.stringify({ status: 'killed', synthetic: true, reason: 'orphan-silence' }) })}\n\n`
                )
                return close()
              }
            }
            idlePollMs = Math.min(idlePollMs + 100, POLL_IDLE_MS)
          }

          await new Promise((r) => setTimeout(r, idlePollMs))
          if (reqAbort.aborted) return close()
        }
      } catch {
        // any subscriber-side error: cleanup only — never the producer
      } finally {
        clearInterval(keepaliveTimer)
        if (!closed) {
          closed = true
          try {
            controller.close()
          } catch {
            /* runtime already closed */
          }
        }
      }
    },
    cancel() {
      // subscriber disconnected — the detached run is NOT cancelled
    },
  })

  return new Response(stream, {
    headers: {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache, no-transform',
      Connection: 'keep-alive',
      'X-Accel-Buffering': 'no',
      'X-GS-Run-Id': run.id,
      'X-GS-Run-Status': run.status,
    },
  })
}

/** One-end response for terminal short-circuit joins. */
function sseSingleEnd(status: string, runId: string): Response {
  const body = `: connected\n\n: terminal\n\ndata: ${JSON.stringify({
    event: 'end',
    data: JSON.stringify({ status, shortCircuit: true }),
  })}\n\n`
  return new Response(body, {
    headers: {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache, no-transform',
      'X-Accel-Buffering': 'no',
      'X-GS-Run-Id': runId,
    },
  })
}

/** Run-store status → broker terminal status vocabulary. */
function mapStatus(status: string): string {
  switch (status) {
    case 'completed':
      return 'completed'
    case 'failed':
      return 'error'
    case 'killed':
      return 'killed'
    case 'cancelled':
      return 'cancelled'
    default:
      return 'error'
  }
}
