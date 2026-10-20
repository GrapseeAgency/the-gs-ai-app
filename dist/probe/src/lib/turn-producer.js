"use strict";
/**
 * TURN PRODUCER — the detached owner of a background run (ADR-117).
 *
 * The producer OWNS THE RUN: execution, event log, run-state bookkeeping and
 * the terminal stream marker. It never talks to a client; the SSE endpoint is
 * a mere subscriber of the broker stream it publishes to.
 *
 * Client-disconnect behavior is structural here: there is no reference to any
 * Request/Response object — a subscriber dying cannot cancel this code.
 */
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.parkedEndStatus = exports.backgroundRunsEnabled = void 0;
exports.newStreamId = newStreamId;
exports.newRunId = newRunId;
exports.rebuildPrepForRun = rebuildPrepForRun;
exports.produceRun = produceRun;
exports.parkForApproval = parkForApproval;
const stream_broker_1 = require("@/lib/stream-broker");
const run_store_1 = require("@/lib/run-store");
const turn_events_1 = require("@/lib/turn-events");
const serializers_1 = require("@/lib/serializers");
const db_1 = require("@/lib/db");
const turn_executor_1 = require("@/lib/turn-executor");
const backgroundRunsEnabled = () => process.env.BACKGROUND_RUNS_ENABLED === '1';
exports.backgroundRunsEnabled = backgroundRunsEnabled;
function newStreamId() {
    return `st-${crypto.randomUUID().replace(/-/g, '').slice(0, 20)}`;
}
function newRunId() {
    return `run-${crypto.randomUUID().replace(/-/g, '')}`;
}
/**
 * Rebuild the turn context from DURABLE STATE alone (the worker topology:
 * the API enqueued the job, THIS process executes it — the in-request prep
 * object never crosses the queue). The user message persisted at start time
 * is reused (existingUserMessageId) — never duplicated.
 */
async function rebuildPrepForRun(runId) {
    const run = await (0, run_store_1.getRun)(runId);
    if (!run)
        return { error: `run ${runId} not found` };
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
    const { prepareTurn } = await Promise.resolve().then(() => __importStar(require('@/lib/turn-executor')));
    const prepared = await prepareTurn({
        conversationId: run.conversationId,
        content: facts.content ?? '',
        timezone: facts.timezone ?? null,
        clientVersion: facts.clientVersion ?? 'web',
        requestId: runId,
        attachmentIds: facts.attachmentIds ?? [],
        runId,
        existingUserMessageId: run.userMessageId ?? undefined,
    }, { content: facts.content ?? '', attachments: facts.attachmentIds ?? [] });
    if (prepared.kind === 'ready')
        return prepared.prep;
    return { error: `prepareTurn returned ${prepared.kind}` };
}
/**
 * Execute a claimed run to completion. ALWAYS terminates the stream: the
 * terminal marker is written on every in-process exit path (the try/finally
 * below is the asyncio.shield equivalent — abnormal producer death still
 * lands the end marker). Out-of-process death is covered by subscriber-side
 * orphan exit + the broker TTL.
 *
 * prep may be omitted (worker-pool topology): the context is then rebuilt
 * from durable state alone.
 */
async function produceRun(runId, prepInput) {
    const claimed = await (0, run_store_1.claimRun)(runId);
    if (!claimed || claimed.status !== 'running') {
        // already claimed by another worker, or terminal — do not double-run
        console.log(`RUN-PRODUCE-SKIP run=${runId} status=${claimed?.status ?? 'missing'}`);
        return;
    }
    const streamId = claimed.streamId;
    // Worker rebuild: the prep may come from a different process's enqueue.
    let prep = prepInput;
    if (!prep) {
        const rebuilt = await rebuildPrepForRun(runId);
        if ('error' in rebuilt) {
            await (0, run_store_1.finishRun)(runId, 'failed', 'error', rebuilt.error, null);
            await (0, turn_events_1.appendEvent)(runId, 'RunFailed', { error: rebuilt.error.slice(0, 300) });
            await (0, stream_broker_1.publishEnd)(streamId, 'error');
            console.error(`RUN-REBUILD-FAIL run=${runId}: ${rebuilt.error}`);
            return;
        }
        prep = rebuilt;
    }
    // Serialized publish chain — ordering guarantee for the fire-and-forget
    // broker writes (a retrying chunk must never overtake a later one).
    let chain = Promise.resolve();
    const push = (event, data) => {
        chain = chain.then(() => (0, stream_broker_1.publishChunk)(streamId, { event, data }));
    };
    // RESUME RECONCILIATION (durable execution): if a prior attempt already
    // persisted the answer but died before marking the run complete, the
    // dangling operation IS the completion — reconcile it by finishing the run
    // with the EXISTING message. Re-executing would stream a fresh synthesis
    // over an already-persisted answer; the durable guarantee is one answer
    // exists, not two.
    if (claimed.assistantMessageId) {
        const existing = await db_1.db.message.findUnique({
            where: { id: claimed.assistantMessageId },
            include: { sources: { orderBy: { ordinal: 'asc' } } },
        });
        if (existing) {
            push('delta', existing.content);
            push('done', JSON.stringify((0, serializers_1.messageToJson)(existing)));
            await chain;
            await (0, run_store_1.finishRun)(runId, 'completed', 'done', null, existing.id);
            await (0, turn_events_1.appendEvent)(runId, 'RunCompleted', {
                reconciled: true,
                assistant_message_id: existing.id,
                note: 'answer persisted by a prior attempt — run completed from durable state',
            });
            await (0, stream_broker_1.publishEnd)(streamId, 'completed');
            console.log(`RUN-RECONCILED run=${runId} stream=${streamId} reused=${existing.id}`);
            return;
        }
    }
    // Lease keeper — a long research turn must not lose its lease mid-flight.
    const keeper = setInterval(() => void (0, run_store_1.heartbeatRun)(runId), Math.floor(run_store_1.LEASE_TTL_MS / 3));
    // PHASE 4 — TTL keeper: the stream's TTL is refreshed independently of the
    // event rate (sparse long-idle runs), cancelled when the run goes terminal.
    const ttlKeeper = (0, stream_broker_1.startStreamTtlKeeper)(streamId);
    try {
        const result = await (0, turn_executor_1.runTurn)(prep, push);
        await chain; // flush every data chunk before the terminal marker
        const runStatus = result.finalStatus === 'error' ? 'failed' : 'completed';
        await (0, run_store_1.finishRun)(runId, runStatus, result.finalStatus, result.errorRaw ?? null, result.assistantMessageId ?? null);
        if (result.finalStatus === 'error') {
            await (0, turn_events_1.appendEvent)(runId, 'RunFailed', { error: (result.errorRaw ?? '').slice(0, 300) });
        }
        else {
            await (0, turn_events_1.appendEvent)(runId, 'RunCompleted', {
                final_status: result.finalStatus,
                assistant_message_id: result.assistantMessageId ?? null,
            });
        }
        await (0, stream_broker_1.publishEnd)(streamId, result.finalStatus === 'error' ? 'error' : 'completed');
        console.log(`RUN-DONE run=${runId} stream=${streamId} final=${result.finalStatus}`);
    }
    catch (e) {
        const raw = String(e instanceof Error ? e.message : e);
        try {
            await chain;
            await (0, run_store_1.finishRun)(runId, 'failed', 'error', raw, null);
            await (0, turn_events_1.appendEvent)(runId, 'RunFailed', { error: raw.slice(0, 300) });
            await (0, stream_broker_1.publishEnd)(streamId, 'error');
        }
        catch (inner) {
            console.error(`RUN-FINISH-FAIL run=${runId} error=${inner instanceof Error ? inner.message : String(inner)}`);
        }
        console.error(`RUN-CRASH run=${runId} stream=${streamId} error=${raw.slice(0, 300)}`);
    }
    finally {
        clearInterval(keeper);
        ttlKeeper();
    }
}
/** Park a run for human approval — no thread held, resumption re-produces. */
async function parkForApproval(runId, tool, args) {
    const { db } = await Promise.resolve().then(() => __importStar(require('@/lib/db')));
    const { idempotencyKey } = await Promise.resolve().then(() => __importStar(require('@/lib/run-store')));
    const key = idempotencyKey(runId, 'approval', tool, args);
    await db.approvalRequest.create({
        data: { runId, tool, args: JSON.stringify(args), idempotencyKey: key, status: 'pending' },
    });
    await (0, turn_events_1.appendEvent)(runId, 'ApprovalRequested', { tool });
    await (0, run_store_1.finishRun)(runId, 'awaiting_approval', null, null, null);
}
/** Terminal status mapping for parked-run subscribers. */
exports.parkedEndStatus = 'cancelled';
