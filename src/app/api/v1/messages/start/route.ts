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
 *   streamId — transport identifier. Stable for reconnects to a run.
 *              A new turn or approved action creates a fresh window.
 *   runId    — billing/correlation identifier. Reused across HITL interrupt
 *              + resumption; passed down through the turn executor.
 *
 * Rollback: BACKGROUND_RUNS_ENABLED=0 opts back into the legacy inline path.
 * The durable queue is enabled by default so free-capacity waits survive
 * request disconnects and process restarts.
 */

import { NextRequest, NextResponse } from 'next/server'
import { db } from '@/lib/db'
import { clientKey, rateLimit } from '@/lib/rate-limit'
import { MAX_ATTACHMENTS_PER_MESSAGE } from '@/lib/attachments'
import { prepareTurn } from '@/lib/turn-executor'
import { parseThinkingMode } from '@/lib/model-capabilities'
import { backgroundRunsEnabled, newRunId, newStreamId } from '@/lib/turn-producer'
import { enqueueRun } from '@/lib/worker-pool'
import { createRun, getRun, isTerminalStatus } from '@/lib/run-store'

export const dynamic = 'force-dynamic'

const MAX_CONTENT_LENGTH = 32000

type StartBody = {
  conversationId?: unknown
  content?: unknown
  attachments?: unknown
  timezone?: unknown
  mode?: unknown
  turnContext?: unknown
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
    // A reconnect is a subscription to the original run, not another
    // execution request. Rebinding an active stream could orphan its producer
    // and re-running its preparation could mutate the original user turn.
    if (run.status === 'queued' || run.status === 'running') await enqueueRun(run.id)
    return NextResponse.json({
      runId: run.id,
      streamId: run.streamId,
      status: run.status,
      finalStatus: run.finalStatus,
      assistantMessageId: run.assistantMessageId,
      resumed: !isTerminalStatus(run.status),
      reconciled: false,
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
  // FLASH MODE (Phase 2) — absent/invalid parses to 'flash' (the new default).
  const mode = parseThinkingMode(body?.mode)
  const turnContext = typeof body?.turnContext === 'string' ? body.turnContext : null

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
      mode,
      turnContext,
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
    startFacts: {
      conversationId, content, attachmentIds, timezone, clientVersion, mode, turnContext,
      userMessageId: prepared.prep.userMessage.id,
    },
  })

  // PHASE 8 — the API ENQUEUES; the worker pool runs the turn. The request
  // returns immediately; no turn is ever executed on the request path.
  await enqueueRun(runId)

  return NextResponse.json({ streamId, runId }, { status: 200 })
}

