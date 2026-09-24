/**
 * PHASE 3 — run state + HUMAN APPROVAL endpoints (HITL parking).
 *
 * GET  /api/v1/runs/{runId}        → authoritative run state + folded event
 *                                    log (resume diagnostics).
 * POST /api/v1/runs/{runId}/approve → decide a pending approval; an approved
 *                                    tool call resumes the run under a fresh
 *                                    stream window (route file lives in
 *                                    approve/route.ts).
 *
 * A run parked for approval holds NO thread and NO process: the decision
 * may arrive days later. The run's event log is the durable state.
 */

import { NextRequest, NextResponse } from 'next/server'
import { getRun } from '@/lib/run-store'
import { foldRunState } from '@/lib/turn-events'
import { db } from '@/lib/db'

export const dynamic = 'force-dynamic'

type RouteContext = { params: Promise<{ runId: string }> }

export async function GET(_req: NextRequest, { params }: RouteContext) {
  const { runId } = await params
  const run = await getRun(runId)
  if (!run) {
    return NextResponse.json({ code: 'not_found', message: 'Run not found' }, { status: 404 })
  }
  const folded = await foldRunState(runId)
  const pendingApproval = await db.approvalRequest.findFirst({
    where: { runId, status: 'pending' },
    orderBy: { createdAt: 'desc' },
  })
  return NextResponse.json({
    runId: run.id,
    streamId: run.streamId,
    conversationId: run.conversationId,
    status: run.status,
    finalStatus: run.finalStatus,
    assistantMessageId: run.assistantMessageId,
    attempts: run.attempts,
    eventCount: folded.eventCount,
    lastEventType: folded.lastEventType,
    dangling: folded.dangling ? { seq: folded.dangling.seq, event: folded.dangling.eventType } : null,
    pendingApproval: pendingApproval
      ? { id: pendingApproval.id, tool: pendingApproval.tool, createdAt: pendingApproval.createdAt }
      : null,
  })
}
