"use strict";
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
Object.defineProperty(exports, "__esModule", { value: true });
exports.dynamic = void 0;
exports.POST = POST;
const server_1 = require("next/server");
const db_1 = require("@/lib/db");
const rate_limit_1 = require("@/lib/rate-limit");
const attachments_1 = require("@/lib/attachments");
const turn_executor_1 = require("@/lib/turn-executor");
const model_capabilities_1 = require("@/lib/model-capabilities");
const turn_producer_1 = require("@/lib/turn-producer");
const worker_pool_1 = require("@/lib/worker-pool");
const run_store_1 = require("@/lib/run-store");
const turn_events_1 = require("@/lib/turn-events");
exports.dynamic = 'force-dynamic';
const MAX_CONTENT_LENGTH = 32000;
async function POST(req) {
    if (!(0, turn_producer_1.backgroundRunsEnabled)()) {
        return server_1.NextResponse.json({ code: 'background_disabled', message: 'Background runs are disabled on this deployment.' }, { status: 404 });
    }
    let body;
    try {
        body = (await req.json());
    }
    catch {
        return server_1.NextResponse.json({ code: 'bad_request', message: 'Request body must be valid JSON' }, { status: 400 });
    }
    const rawAttachments = body?.attachments;
    const attachmentIds = Array.isArray(rawAttachments)
        ? rawAttachments.filter((v) => typeof v === 'string' && v.trim().length > 0)
        : [];
    if (Array.isArray(rawAttachments) && attachmentIds.length !== rawAttachments.length) {
        return server_1.NextResponse.json({ code: 'bad_request', message: 'attachments must be an array of attachment ids' }, { status: 400 });
    }
    if (attachmentIds.length > attachments_1.MAX_ATTACHMENTS_PER_MESSAGE) {
        return server_1.NextResponse.json({ code: 'bad_request', message: `At most ${attachments_1.MAX_ATTACHMENTS_PER_MESSAGE} attachments per message` }, { status: 400 });
    }
    // ---- RESUME path (durable execution): runId reused, streamId fresh ------
    // NOTE: a resume does NOT require body content — the request facts are
    // reconstructed from the TurnStarted event (durable state alone).
    if (typeof body?.resumeRunId === 'string' && body.resumeRunId) {
        const run = await (0, run_store_1.getRun)(body.resumeRunId);
        if (!run) {
            return server_1.NextResponse.json({ code: 'not_found', message: 'Run not found' }, { status: 404 });
        }
        // Safe to call more than once: a finished run just returns its state.
        const folded = await (0, turn_events_1.resume)(run.id);
        if ((0, run_store_1.isTerminalStatus)(run.status) && folded.completed) {
            return server_1.NextResponse.json({
                runId: run.id,
                streamId: run.streamId,
                status: run.status,
                finalStatus: run.finalStatus,
                assistantMessageId: run.assistantMessageId,
                resumed: false,
                reconciled: false,
            });
        }
        // Dangling run: reconcile happened inside resume(); re-produce on a
        // FRESH transport window (new streamId), SAME runId.
        const turnRequest = await turnRequestFromRun(run, req);
        const prepared = await (0, turn_executor_1.prepareTurn)(turnRequest, {
            content: turnRequest.content,
            attachments: turnRequest.attachmentIds,
        });
        if (prepared.kind !== 'ready')
            return prepared.response;
        const streamId = (0, turn_producer_1.newStreamId)();
        await (0, run_store_1.rebindStream)(run.id, streamId);
        await (0, turn_events_1.appendEvent)(run.id, 'TurnStarted', {
            resumed: true,
            streamId,
            reconciled: folded.reconciled,
            dangling: folded.dangling?.eventType ?? null,
        });
        await (0, worker_pool_1.enqueueRun)(run.id);
        return server_1.NextResponse.json({
            runId: run.id,
            streamId,
            status: 'queued',
            resumed: true,
            reconciled: folded.reconciled,
        });
    }
    const content = typeof body?.content === 'string' ? body.content : '';
    if (content.trim().length === 0 && attachmentIds.length === 0) {
        return server_1.NextResponse.json({ code: 'bad_request', message: 'content must be a non-empty string' }, { status: 400 });
    }
    if (content.length > MAX_CONTENT_LENGTH) {
        return server_1.NextResponse.json({ code: 'bad_request', message: `content must not exceed ${MAX_CONTENT_LENGTH} characters` }, { status: 400 });
    }
    const clientVersion = req.headers.get('x-gs-app-version') ?? 'web';
    const timezone = typeof body?.timezone === 'string' ? body.timezone : null;
    // FLASH MODE (Phase 2) — absent/invalid parses to 'flash' (the new default).
    const mode = (0, model_capabilities_1.parseThinkingMode)(body?.mode);
    // ---- FRESH start ---------------------------------------------------------
    const conversationId = typeof body?.conversationId === 'string' ? body.conversationId : '';
    if (!conversationId) {
        return server_1.NextResponse.json({ code: 'bad_request', message: 'conversationId is required' }, { status: 400 });
    }
    // Guardrail: same 20/min/client budget as the legacy route.
    const limit = (0, rate_limit_1.rateLimit)((0, rate_limit_1.clientKey)(req, `msgs:${conversationId}`), 20, 60000);
    if (!limit.allowed) {
        return server_1.NextResponse.json({ code: 'rate_limited', message: 'Too many messages — slow down a little.' }, { status: 429, headers: { 'Retry-After': String(limit.retryAfterSec) } });
    }
    const conversation = await db_1.db.conversation.findUnique({ where: { id: conversationId } });
    if (!conversation) {
        return server_1.NextResponse.json({ code: 'not_found', message: 'Conversation not found' }, { status: 404 });
    }
    const runId = (0, turn_producer_1.newRunId)();
    const streamId = (0, turn_producer_1.newStreamId)();
    // prepareTurn validates attachments, persists the USER MESSAGE and builds
    // the turn context — all fast DB work, no model calls. The producer is
    // spawned detached; this request returns immediately after.
    const prepared = await (0, turn_executor_1.prepareTurn)({
        conversationId,
        content,
        timezone,
        clientVersion,
        requestId: runId, // correlation: the runId IS the request id (billing)
        attachmentIds,
        mode,
        runId,
    }, { content, attachments: rawAttachments });
    if (prepared.kind !== 'ready')
        return prepared.response;
    await (0, run_store_1.createRun)({
        runId,
        streamId,
        conversationId,
        userMessageId: prepared.prep.userMessage.id,
    });
    // Event sourcing: TurnStarted carries the request facts a resume needs to
    // reconstruct the turn from durable state alone.
    await (0, turn_events_1.appendEvent)(runId, 'TurnStarted', {
        conversationId,
        content,
        attachmentIds,
        timezone,
        clientVersion,
        mode,
        userMessageId: prepared.prep.userMessage.id,
    });
    // PHASE 8 — the API ENQUEUES; the worker pool runs the turn. The request
    // returns immediately; no turn is ever executed on the request path.
    await (0, worker_pool_1.enqueueRun)(runId);
    return server_1.NextResponse.json({ streamId, runId }, { status: 200 });
}
// ---------------------------------------------------------------------------
// helpers
// ---------------------------------------------------------------------------
/** Event-sourced request facts: TurnStarted carries the original request. */
async function turnRequestFromRun(run, req) {
    const events = await db_1.db.turnEvent.findMany({
        where: { runId: run.id, eventType: 'TurnStarted' },
        orderBy: { seq: 'asc' },
        take: 1,
    });
    let facts = {};
    try {
        facts = JSON.parse(events[0]?.payload ?? '{}');
    }
    catch {
        facts = {};
    }
    return {
        conversationId: run.conversationId,
        content: facts.content ?? '',
        timezone: facts.timezone ?? null,
        clientVersion: facts.clientVersion ?? req.headers.get('x-gs-app-version') ?? 'web',
        requestId: run.id, // correlation: the runId IS the request id on resume
        attachmentIds: facts.attachmentIds ?? [],
        mode: (0, model_capabilities_1.parseThinkingMode)(facts.mode), // durable mode survives resume
        runId: run.id,
        existingUserMessageId: run.userMessageId ?? undefined,
    };
}
