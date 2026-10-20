"use strict";
/**
 * RUN STORE — the durable registry of detached turn runs (ADR-117).
 *
 * runId  — billing/correlation id. Stable across HITL interrupt + resumption.
 * streamId — transport id, fresh per start POST.
 *
 * Resume semantics (durable-agents model): runtime.resume(runId) is safe to
 * call more than once.
 *   - finished run      → returns the state, no side effects.
 *   - dangling run      → reconciles the dangling operation first (the
 *                         event-sourcing fold in turn-events.ts decides what
 *                         was interrupted), then the run may be re-produced
 *                         under a FRESH streamId.
 *   - more than once    → the second resume of a reconciled run is a no-op
 *                         state read.
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.LEASE_TTL_MS = exports.isTerminalStatus = exports.TERMINAL_RUN_STATUSES = void 0;
exports.createRun = createRun;
exports.getRun = getRun;
exports.getRunByStream = getRunByStream;
exports.claimRun = claimRun;
exports.heartbeatRun = heartbeatRun;
exports.finishRun = finishRun;
exports.rebindStream = rebindStream;
exports.idempotencyKey = idempotencyKey;
exports.firstTime = firstTime;
exports.rememberIdempotent = rememberIdempotent;
exports.resetPendingIdempotency = resetPendingIdempotency;
const crypto_1 = require("crypto");
const db_1 = require("@/lib/db");
exports.TERMINAL_RUN_STATUSES = ['completed', 'failed', 'killed', 'cancelled'];
const isTerminalStatus = (s) => exports.TERMINAL_RUN_STATUSES.includes(s);
exports.isTerminalStatus = isTerminalStatus;
const toRecord = (r) => ({
    id: r.id,
    streamId: r.streamId,
    conversationId: r.conversationId,
    userMessageId: r.userMessageId,
    assistantMessageId: r.assistantMessageId,
    status: r.status,
    finalStatus: r.finalStatus,
    error: r.error,
    attempts: r.attempts,
    idempotencyKeys: safeParse(r.idempotencyKeys),
});
const safeParse = (raw) => {
    try {
        const v = JSON.parse(raw);
        return v && typeof v === 'object' ? v : {};
    }
    catch {
        return {};
    }
};
async function createRun(input) {
    const row = await db_1.db.turnRun.create({
        data: {
            id: input.runId,
            streamId: input.streamId,
            conversationId: input.conversationId,
            userMessageId: input.userMessageId ?? null,
            status: 'queued',
        },
    });
    return toRecord(row);
}
async function getRun(runId) {
    const row = await db_1.db.turnRun.findUnique({ where: { id: runId } });
    return row ? toRecord(row) : null;
}
async function getRunByStream(streamId) {
    const row = await db_1.db.turnRun.findUnique({ where: { streamId } });
    return row ? toRecord(row) : null;
}
// ---------------------------------------------------------------------------
// Lease claiming (worker topology: N workers, at-least-once via leases)
// ---------------------------------------------------------------------------
exports.LEASE_TTL_MS = 120000;
const instanceId = `w-${process.pid}-${Math.random().toString(36).slice(2, 8)}`;
/**
 * Claim a run for execution. A run is claimable when queued, or when its
 * previous holder's lease expired (stuck-job recovery, at-least-once).
 */
async function claimRun(runId, ttlMs = exports.LEASE_TTL_MS) {
    const now = new Date();
    const run = await db_1.db.turnRun.findUnique({ where: { id: runId } });
    if (!run)
        return null;
    const claimable = run.status === 'queued' ||
        (run.status === 'running' && (!run.leaseExpiresAt || run.leaseExpiresAt < now));
    if (!claimable)
        return toRecord(run);
    const row = await db_1.db.turnRun.update({
        where: { id: runId },
        data: {
            status: 'running',
            leaseOwner: instanceId,
            leaseExpiresAt: new Date(Date.now() + ttlMs),
            attempts: { increment: 1 },
        },
    });
    return toRecord(row);
}
/** Refresh the lease while a run is alive (long research turns outlive the TTL). */
async function heartbeatRun(runId, ttlMs = exports.LEASE_TTL_MS) {
    await db_1.db.turnRun
        .updateMany({
        where: { id: runId, leaseOwner: instanceId },
        data: { leaseExpiresAt: new Date(Date.now() + ttlMs) },
    })
        .catch(() => undefined);
}
async function finishRun(runId, status, finalStatus, error, assistantMessageId) {
    await db_1.db.turnRun.update({
        where: { id: runId },
        data: {
            status,
            finalStatus,
            error: error ? error.slice(0, 1000) : null,
            ...(assistantMessageId ? { assistantMessageId } : {}),
            leaseOwner: null,
            leaseExpiresAt: null,
        },
    });
}
/** Re-bind the run to a FRESH stream id (resumption window). */
async function rebindStream(runId, streamId) {
    await db_1.db.turnRun.update({ where: { id: runId }, data: { streamId } });
}
// ---------------------------------------------------------------------------
// Idempotency keys (side-effect dedupe across restarts)
// ---------------------------------------------------------------------------
/** sha256(runId + seq + tool + args) — stable across restarts. */
function idempotencyKey(runId, seq, tool, args) {
    return (0, crypto_1.createHash)('sha256').update(`${runId}:${seq}:${tool}:${stableStringify(args)}`).digest('hex');
}
const stableStringify = (v) => {
    if (v === null || v === undefined)
        return 'null';
    if (typeof v !== 'object')
        return JSON.stringify(v);
    if (Array.isArray(v))
        return `[${v.map(stableStringify).join(',')}]`;
    const obj = v;
    return `{${Object.keys(obj)
        .sort()
        .map((k) => `${JSON.stringify(k)}:${stableStringify(obj[k])}`)
        .join(',')}}`;
};
/** True (and marks the op as claimed) when this side-effecting op was never done. */
async function firstTime(runId, key) {
    const run = await db_1.db.turnRun.findUnique({ where: { id: runId } });
    if (!run)
        return false;
    const keys = safeParse(run.idempotencyKeys);
    if (keys[key] !== undefined)
        return false; // already claimed or completed
    keys[key] = 'pending';
    await db_1.db.turnRun.update({ where: { id: runId }, data: { idempotencyKeys: JSON.stringify(keys) } });
    return true;
}
async function rememberIdempotent(runId, key, digest) {
    const run = await db_1.db.turnRun.findUnique({ where: { id: runId } });
    if (!run)
        return;
    const keys = safeParse(run.idempotencyKeys);
    keys[key] = digest;
    await db_1.db.turnRun.update({ where: { id: runId }, data: { idempotencyKeys: JSON.stringify(keys) } });
}
/**
 * Reconcile pass: keys left 'pending' by a killed process are interruptible
 * work whose outcome is unknown — a resume is allowed to redo them once.
 */
async function resetPendingIdempotency(runId) {
    const run = await db_1.db.turnRun.findUnique({ where: { id: runId } });
    if (!run)
        return [];
    const keys = safeParse(run.idempotencyKeys);
    const reset = Object.entries(keys)
        .filter(([, v]) => v === 'pending')
        .map(([k]) => k);
    if (reset.length > 0) {
        for (const k of reset)
            delete keys[k];
        await db_1.db.turnRun.update({ where: { id: runId }, data: { idempotencyKeys: JSON.stringify(keys) } });
    }
    return reset;
}
