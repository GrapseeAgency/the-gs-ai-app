/**
 * POST /api/v1/runs/{runId}/approve — decide a pending human approval.
 *
 * A tool marked requires_approval parked the run (no thread, no process).
 * The decision may arrive days later; the event log is the durable state.
 *   approved → the run resumes under a FRESH stream window (same runId).
 *   denied   → the run completes as failed with an honest reason.
 */

import { NextRequest, NextResponse } from 'next/server'
import { db } from '@/lib/db'
import { getRun, rebindStream } from '@/lib/run-store'
import { appendEvent } from '@/lib/turn-events'
import { newStreamId, produceRun } from '@/lib/turn-producer'
import { prepareTurn } from '@/lib/turn-executor'

export const dynamic = 'force-dynamic'

type RouteContext = { params: Promise<{ runId: string }> }

export async function POST(req: NextRequest, { params }: RouteContext) {
  const { runId } = await params
  const run = await getRun(runId)
  if (!run) {
    return NextResponse.json({ code: 'not_found', message: 'Run not found' }, { status: 404 })
  }
  let body: { decision?: unknown }
  try {
    body = (await req.json()) as { decision?: unknown }
  } catch {
    return NextResponse.json({ code: 'bad_request', message: 'Request body must be valid JSON' }, { status: 400 })
  }
  const decision = body?.decision === 'approved' ? 'approved' : body?.decision === 'denied' ? 'denied' : null
  if (!decision) {
    return NextResponse.json(
      { code: 'bad_request', message: "decision must be 'approved' or 'denied'" },
      { status: 400 }
    )
  }
  const pending = await db.approvalRequest.findFirst({
    where: { runId, status: 'pending' },
    orderBy: { createdAt: 'desc' },
  })
  if (!pending) {
    return NextResponse.json(
      { code: 'no_pending_approval', message: 'No approval is pending for this run' },
      { status: 409 }
    )
  }
  await db.approvalRequest.update({
    where: { id: pending.id },
    data: { status: decision, decidedAt: new Date() },
  })
  await appendEvent(runId, 'ApprovalDecided', { tool: pending.tool, decision })

  if (decision === 'denied') {
    await db.turnRun.update({
      where: { id: runId },
      data: { status: 'failed', finalStatus: 'error', error: `approval denied: ${pending.tool}` },
    })
    await appendEvent(runId, 'RunFailed', { error: `approval denied: ${pending.tool}` })
    return NextResponse.json({ runId, decision, status: 'failed' })
  }

  // Approved: re-produce the run from durable state on a fresh stream window.
  const turnEvents = await db.turnEvent.findFirst({
    where: { runId, eventType: 'TurnStarted' },
    orderBy: { seq: 'asc' },
  })
  let facts: { content?: string; attachmentIds?: string[]; timezone?: string | null; clientVersion?: string } = {}
  try {
    facts = JSON.parse(turnEvents?.payload ?? '{}') as typeof facts
  } catch {
    facts = {}
  }
  const prepared = await prepareTurn(
    {
      conversationId: run.conversationId,
      content: facts.content ?? '',
      timezone: facts.timezone ?? null,
      clientVersion: facts.clientVersion ?? 'web',
      requestId: runId,
      attachmentIds: facts.attachmentIds ?? [],
      runId,
      existingUserMessageId: run.userMessageId ?? undefined,
    },
    { content: facts.content ?? '', attachments: facts.attachmentIds ?? [] }
  )
  if (prepared.kind !== 'ready') return prepared.response
  const streamId = newStreamId()
  await rebindStream(runId, streamId)
  await appendEvent(runId, 'TurnStarted', { resumed: true, afterApproval: pending.tool, streamId })
  void produceRun(runId, prepared.prep).catch(() => undefined)
  return NextResponse.json({ runId, decision, streamId, status: 'queued' })
}
