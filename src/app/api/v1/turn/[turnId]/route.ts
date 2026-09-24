/**
 * PHASE 2 — POLLING FALLBACK for background runs.
 *
 * GET /api/v1/turn/{turnId}?after={entryId}
 *
 * When EventSource fails N times in a row (restrictive proxy, serverless
 * runtime), the client polls this endpoint instead: every response carries
 * the chunks after the cursor plus the authoritative run state, so a
 * poller is a full substitute for the SSE subscription.
 *
 * turnId is the runId (billing/correlation id). The streamId of the current
 * transport window is returned for clients that want to re-subscribe SSE.
 */

import { NextRequest, NextResponse } from 'next/server'
import { parseEntry, readAfter } from '@/lib/stream-broker'
import { getRun, isTerminalStatus } from '@/lib/run-store'

export const dynamic = 'force-dynamic'

type RouteContext = { params: Promise<{ turnId: string }> }

export async function GET(req: NextRequest, { params }: RouteContext) {
  const { turnId } = await params
  const run = await getRun(turnId)
  if (!run) {
    return NextResponse.json({ code: 'not_found', message: 'Run not found' }, { status: 404 })
  }
  const after = req.nextUrl.searchParams.get('after') ?? ''
  const entries = await readAfter(run.streamId, after)

  const chunks: { id: string; event: string; data: string }[] = []
  let final: string | null = null
  for (const entry of entries) {
    const parsed = parseEntry(entry.payload)
    if (parsed.end) {
      final = parsed.end.status
      continue
    }
    if (parsed.chunk) {
      chunks.push({ id: entry.entryId, event: parsed.chunk.event, data: parsed.chunk.data })
    }
  }
  // Terminal run: report final even if the broker entry was trimmed.
  if (!final && isTerminalStatus(run.status)) {
    final = run.status === 'completed' ? 'completed' : run.status === 'failed' ? 'error' : run.status
  }

  return NextResponse.json({
    runId: run.id,
    streamId: run.streamId,
    status: run.status,
    finalStatus: run.finalStatus,
    final,
    chunks,
    cursor: chunks.length > 0 ? chunks[chunks.length - 1].id : after,
  })
}
