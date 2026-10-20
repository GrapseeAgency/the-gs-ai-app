"use strict";
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
Object.defineProperty(exports, "__esModule", { value: true });
exports.dynamic = void 0;
exports.GET = GET;
const server_1 = require("next/server");
const stream_broker_1 = require("@/lib/stream-broker");
const run_store_1 = require("@/lib/run-store");
exports.dynamic = 'force-dynamic';
async function GET(req, { params }) {
    const { turnId } = await params;
    const run = await (0, run_store_1.getRun)(turnId);
    if (!run) {
        return server_1.NextResponse.json({ code: 'not_found', message: 'Run not found' }, { status: 404 });
    }
    const after = req.nextUrl.searchParams.get('after') ?? '';
    const entries = await (0, stream_broker_1.readAfter)(run.streamId, after);
    const chunks = [];
    let final = null;
    for (const entry of entries) {
        const parsed = (0, stream_broker_1.parseEntry)(entry.payload);
        if (parsed.end) {
            final = parsed.end.status;
            continue;
        }
        if (parsed.chunk) {
            chunks.push({ id: entry.entryId, event: parsed.chunk.event, data: parsed.chunk.data });
        }
    }
    // Terminal run: report final even if the broker entry was trimmed.
    if (!final && (0, run_store_1.isTerminalStatus)(run.status)) {
        final = run.status === 'completed' ? 'completed' : run.status === 'failed' ? 'error' : run.status;
    }
    return server_1.NextResponse.json({
        runId: run.id,
        streamId: run.streamId,
        status: run.status,
        finalStatus: run.finalStatus,
        final,
        chunks,
        cursor: chunks.length > 0 ? chunks[chunks.length - 1].id : after,
    });
}
