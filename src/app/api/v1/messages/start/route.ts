/**
 * PHASE 1 — DETACHED TURN EXECUTION (ADR-117, "Background Chat Runs").
 *
 * POST /api/v1/messages/start
 *   body: { conversationId, content, attachments?, timezone?, resumeRunId? }
 *   returns { streamId, runId } — 200 OK, NO streaming yet.
 *
 *   ├─ spawn detached producer (runs independently of any connection)
 *   └─ producer executes the turn and writes chunks to the stream broker
 *      (key: chat:run:{streamId}); the client subscribes via
 *      GET /api/v1/stream/{streamId}.
 *
 * TWO IDENTIFIERS — DELIBERATELY DISTINCT:
 *   streamId — transport identifier. FRESH on every POST (including
 *              resumption POSTs). Broker key suffix.
 *   runId    — billing/correlation identifier. Reused across HITL interrupt
 *              + resumption; passed down through the turn executor.
 *
 * Rollback: BACKGROUND_RUNS_ENABLED (default false). Flag OFF → this
 * endpoint 404s and the legacy inline SSE path serves every turn unchanged.
 */

import { NextRequest, NextResponse } from 'next/server'
import { db } from '@/lib/db'
import { clientKey, rateLimit } from '@/lib/rate-limit'
import { MAX_ATTACHMENTS_PER_MESSAGE } from '@/lib/attachments'
import { prepareTurn } from '@/lib/turn-executor'
import { backgroundRunsEnabled, newRunId, newStreamId } from '@/lib/turn-producer'
import { enqueueRun } from '@/lib/worker-pool'
import { createRun, getRun, isTerminalStatus, rebindStream } from '@/lib/run-store'
import { appendEvent, resume } from '@/lib/turn-events'

export const dynamic = 'force-dynamic'

const MAX_CONTENT_LENGTH = 32000

type StartBody = {
  conversationId?: unknown
  content?: unknown
  attachments?: unknown
  timezone?: unknown
  resumeRunId?: unknown
}

export async function POST(req: NextRequest) {
  if (!backgroundRunsEnabled()) {
    return NextResponse.json(
      { code: 'background_disabled', message: 'Background runs are disabled on this deployment.' },
      { status: 404 }
    )
  }

  let body: StartBody
  try {
    body = (await req.json()) as StartBody
  } catch {
    return NextResponse.json(
      { code: 'bad_request', message: 'Request body must be valid JSON' },
      { status: 400 }
    )
  }

  const rawAttachments = body?.attachments
  const attachmentIds = Array.isArray(rawAttachments)
    ? rawAttachments.filter((v): v is string => typeof v === 'string' && v.trim().length > 0)
    : []
  if (Array.isArray(rawAttachments) && attachmentIds.length !== rawAttachments.length) {
    return NextResponse.json(
      { code: 'bad_request', message: 'attachments must be an array of attachment ids' },
      { status: 400 }
    )
  }
  if (attachmentIds.length > MAX_ATTACHMENTS_PER_MESSAGE) {
    return NextResponse.json(
      { code: 'bad_request', message: `At most ${MAX_ATTACHMENTS_PER_MESSAGE} attachments per message` },
      { status: 400 }
    )
  }

  // ---- RESUME path (durable execution): runId reused, streamId fresh ------
  // NOTE: a resume does NOT require body content — the request facts are
  // reconstructed from the TurnStarted event (durable state alone).
  if (typeof body?.resumeRunId === 'string' && body.resumeRunId) {
    const run = await getRun(body.resumeRunId)
    if (!run) {
      return NextResponse.json({ code: 'not_found', message: 'Run not found' }, { status: 404 })
    }
    // Safe to call more than once: a finished run just returns its state.
    const folded = await resume(run.id)
    if (isTerminalStatus(run.status) && folded.completed) {
      return NextResponse.json({
        runId: run.id,
        streamId: run.streamId,
        status: run.status,
        finalStatus: run.finalStatus,
        assistantMessageId: run.assistantMessageId,
        resumed: false,
        reconciled: false,
      })
    }
    // Dangling run: reconcile happened inside resume(); re-produce on a
    // FRESH transport window (new streamId), SAME runId.
    const turnRequest = await turnRequestFromRun(run, req)
    const prepared = await prepareTurn(turnRequest, {
      content: turnRequest.content,
      attachments: turnRequest.attachmentIds,
    })
    if (prepared.kind !== 'ready') return prepared.response
    const streamId = newStreamId()
    await rebindStream(run.id, streamId)
    await appendEvent(run.id, 'TurnStarted', {
      resumed: true,
      streamId,
      reconciled: folded.reconciled,
      dangling: folded.dangling?.eventType ?? null,
    })
    await enqueueRun(run.id)
    return NextResponse.json({
      runId: run.id,
      streamId,
      status: 'queued',
      resumed: true,
      reconciled: folded.reconciled,
    })
  }

  const content = typeof body?.content === 'string' ? body.content : ''
  if (content.trim().length === 0 && attachmentIds.length === 0) {
    return NextResponse.json(
      { code: 'bad_request', message: 'content must be a non-empty string' },
      { status: 400 }
    )
  }
  if (content.length > MAX_CONTENT_LENGTH) {
    return NextResponse.json(
      { code: 'bad_request', message: `content must not exceed ${MAX_CONTENT_LENGTH} characters` },
      { status: 400 }
    )
  }
  const clientVersion = req.headers.get('x-gs-app-version') ?? 'web'
  const timezone = typeof body?.timezone === 'string' ? body.timezone : null

  // ---- FRESH start ---------------------------------------------------------
  const conversationId = typeof body?.conversationId === 'string' ? body.conversationId : ''
  if (!conversationId) {
    return NextResponse.json(
      { code: 'bad_request', message: 'conversationId is required' },
      { status: 400 }
    )
  }

  // Guardrail: same 20/min/client budget as the legacy route.
  const limit = rateLimit(clientKey(req, `msgs:${conversationId}`), 20, 60_000)
  if (!limit.allowed) {
    return NextResponse.json(
      { code: 'rate_limited', message: 'Too many messages — slow down a little.' },
      { status: 429, headers: { 'Retry-After': String(limit.retryAfterSec) } }
    )
  }

  const conversation = await db.conversation.findUnique({ where: { id: conversationId } })
  if (!conversation) {
    return NextResponse.json({ code: 'not_found', message: 'Conversation not found' }, { status: 404 })
  }

  const runId = newRunId()
  const streamId = newStreamId()

  // prepareTurn validates attachments, persists the USER MESSAGE and builds
  // the turn context — all fast DB work, no model calls. The producer is
  // spawned detached; this request returns immediately after.
  const prepared = await prepareTurn(
    {
      conversationId,
      content,
      timezone,
      clientVersion,
      requestId: runId, // correlation: the runId IS the request id (billing)
      attachmentIds,
      runId,
    },
    { content, attachments: rawAttachments }
  )
  if (prepared.kind !== 'ready') return prepared.response

  await createRun({
    runId,
    streamId,
    conversationId,
    userMessageId: prepared.prep.userMessage.id,
  })
  // Event sourcing: TurnStarted carries the request facts a resume needs to
  // reconstruct the turn from durable state alone.
  await appendEvent(runId, 'TurnStarted', {
    conversationId,
    content,
    attachmentIds,
    timezone,
    clientVersion,
    userMessageId: prepared.prep.userMessage.id,
  })

  // PHASE 8 — the API ENQUEUES; the worker pool runs the turn. The request
  // returns immediately; no turn is ever executed on the request path.
  await enqueueRun(runId)

  return NextResponse.json({ streamId, runId }, { status: 200 })
}

// ---------------------------------------------------------------------------
// helpers
// ---------------------------------------------------------------------------

/** Event-sourced request facts: TurnStarted carries the original request. */
async function turnRequestFromRun(
  run: { id: string; conversationId: string; userMessageId: string | null },
  req: NextRequest
) {
  const events = await db.turnEvent.findMany({
    where: { runId: run.id, eventType: 'TurnStarted' },
    orderBy: { seq: 'asc' },
    take: 1,
  })
  let facts: { content?: string; attachmentIds?: string[]; timezone?: string | null; clientVersion?: string } = {}
  try {
    facts = JSON.parse(events[0]?.payload ?? '{}') as typeof facts
  } catch {
    facts = {}
  }
  return {
    conversationId: run.conversationId,
    content: facts.content ?? '',
    timezone: facts.timezone ?? null,
    clientVersion: facts.clientVersion ?? req.headers.get('x-gs-app-version') ?? 'web',
    requestId: run.id, // correlation: the runId IS the request id on resume
    attachmentIds: facts.attachmentIds ?? [],
    runId: run.id,
    existingUserMessageId: run.userMessageId ?? undefined,
  }
}
