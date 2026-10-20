"use strict";
/**
 * POST /api/v1/runs/{runId}/approve — decide a pending human approval.
 *
 * A tool marked requires_approval parked the run (no thread, no process).
 * The decision may arrive days later; the event log is the durable state.
 *   approved → the run resumes under a FRESH stream window (same runId).
 *   denied   → the run completes as failed with an honest reason.
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.dynamic = void 0;
exports.POST = POST;
const server_1 = require("next/server");
const db_1 = require("@/lib/db");
const run_store_1 = require("@/lib/run-store");
const turn_events_1 = require("@/lib/turn-events");
const turn_producer_1 = require("@/lib/turn-producer");
const worker_pool_1 = require("@/lib/worker-pool");
const turn_executor_1 = require("@/lib/turn-executor");
exports.dynamic = 'force-dynamic';
async function POST(req, { params }) {
    const { runId } = await params;
    const run = await (0, run_store_1.getRun)(runId);
    if (!run) {
        return server_1.NextResponse.json({ code: 'not_found', message: 'Run not found' }, { status: 404 });
    }
    let body;
    try {
        body = (await req.json());
    }
    catch {
        return server_1.NextResponse.json({ code: 'bad_request', message: 'Request body must be valid JSON' }, { status: 400 });
    }
    const decision = body?.decision === 'approved' ? 'approved' : body?.decision === 'denied' ? 'denied' : null;
    if (!decision) {
        return server_1.NextResponse.json({ code: 'bad_request', message: "decision must be 'approved' or 'denied'" }, { status: 400 });
    }
    const pending = await db_1.db.approvalRequest.findFirst({
        where: { runId, status: 'pending' },
        orderBy: { createdAt: 'desc' },
    });
    if (!pending) {
        return server_1.NextResponse.json({ code: 'no_pending_approval', message: 'No approval is pending for this run' }, { status: 409 });
    }
    await db_1.db.approvalRequest.update({
        where: { id: pending.id },
        data: { status: decision, decidedAt: new Date() },
    });
    await (0, turn_events_1.appendEvent)(runId, 'ApprovalDecided', { tool: pending.tool, decision });
    if (decision === 'denied') {
        await db_1.db.turnRun.update({
            where: { id: runId },
            data: { status: 'failed', finalStatus: 'error', error: `approval denied: ${pending.tool}` },
        });
        await (0, turn_events_1.appendEvent)(runId, 'RunFailed', { error: `approval denied: ${pending.tool}` });
        return server_1.NextResponse.json({ runId, decision, status: 'failed' });
    }
    // Approved: re-produce the run from durable state on a fresh stream window.
    const turnEvents = await db_1.db.turnEvent.findFirst({
        where: { runId, eventType: 'TurnStarted' },
        orderBy: { seq: 'asc' },
    });
    let facts = {};
    try {
        facts = JSON.parse(turnEvents?.payload ?? '{}');
    }
    catch {
        facts = {};
    }
    const prepared = await (0, turn_executor_1.prepareTurn)({
        conversationId: run.conversationId,
        content: facts.content ?? '',
        timezone: facts.timezone ?? null,
        clientVersion: facts.clientVersion ?? 'web',
        requestId: runId,
        attachmentIds: facts.attachmentIds ?? [],
        runId,
        existingUserMessageId: run.userMessageId ?? undefined,
    }, { content: facts.content ?? '', attachments: facts.attachmentIds ?? [] });
    if (prepared.kind !== 'ready')
        return prepared.response;
    const streamId = (0, turn_producer_1.newStreamId)();
    await (0, run_store_1.rebindStream)(runId, streamId);
    await (0, turn_events_1.appendEvent)(runId, 'TurnStarted', { resumed: true, afterApproval: pending.tool, streamId });
    await (0, worker_pool_1.enqueueRun)(runId);
    return server_1.NextResponse.json({ runId, decision, streamId, status: 'queued' });
}
