"use strict";
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
Object.defineProperty(exports, "__esModule", { value: true });
exports.dynamic = void 0;
exports.GET = GET;
const server_1 = require("next/server");
const run_store_1 = require("@/lib/run-store");
const turn_events_1 = require("@/lib/turn-events");
const db_1 = require("@/lib/db");
exports.dynamic = 'force-dynamic';
async function GET(_req, { params }) {
    const { runId } = await params;
    const run = await (0, run_store_1.getRun)(runId);
    if (!run) {
        return server_1.NextResponse.json({ code: 'not_found', message: 'Run not found' }, { status: 404 });
    }
    const folded = await (0, turn_events_1.foldRunState)(runId);
    const pendingApproval = await db_1.db.approvalRequest.findFirst({
        where: { runId, status: 'pending' },
        orderBy: { createdAt: 'desc' },
    });
    return server_1.NextResponse.json({
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
    });
}
