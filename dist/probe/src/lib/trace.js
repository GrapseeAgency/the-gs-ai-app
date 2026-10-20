"use strict";
/**
 * EVAL INFRASTRUCTURE — LAYER 1: STRUCTURED TRACE STORE.
 *
 * Every turn emits ONE structured row into the SQLite `turns` table (Prisma
 * model TurnTrace, mapped via @@map). This formalizes the partial console
 * logs (GS-CAP, GS-ROUTER, GS-FREE-ROUTE) into queryable records that the
 * eval suite (Layer 4), the CI gate and the production monitor (Layer 5)
 * grade against. Graders read these rows — never console output.
 *
 * CONTRACT:
 *  - Writing a trace must NEVER break a turn: fire-and-forget, all failures
 *    degrade to a TRACE-WRITE-FAIL log line.
 *  - modelRoute is INTERNAL ONLY — it must never reach a client payload.
 *  - One row per turn, exactly once (written at the GS-CAP terminal points).
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.classifyErrorType = classifyErrorType;
exports.writeTurnTrace = writeTurnTrace;
const db_1 = require("@/lib/db");
/**
 * Deterministic error classification from the raw provider error string.
 * Unknown failure shapes classify as 'provider_error' rather than being
 * silently dropped — the monitor's honest-failure graders depend on this.
 */
function classifyErrorType(raw) {
    if (!raw)
        return null;
    if (/\b429\b/.test(raw))
        return 'provider_429';
    if (/ssrf/i.test(raw))
        return 'ssrf_block';
    if (/parse|json/i.test(raw) && /fail|error|invalid/i.test(raw))
        return 'parse_fail';
    return 'provider_error';
}
/** Persist one trace row. Never throws — observability must not break turns. */
async function writeTurnTrace(record) {
    try {
        await db_1.db.turnTrace.create({
            data: {
                requestId: record.requestId,
                conversationId: record.conversationId,
                messageId: record.messageId,
                clientVersion: record.clientVersion,
                backendRevision: record.backendRevision,
                capability: record.capability,
                trigger: record.trigger,
                searchExecuted: record.searchExecuted,
                researchExecuted: record.researchExecuted,
                evidenceCount: record.evidenceCount,
                sourceCount: record.sourceCount,
                sourcesRead: record.sourcesRead,
                sourcesFailed: record.sourcesFailed,
                domains: JSON.stringify(record.domains),
                modelRoute: record.modelRoute,
                finalStatus: record.finalStatus,
                cited: JSON.stringify(record.cited),
                latencyMs: Math.max(0, Math.round(record.latencyMs)),
                errorType: record.errorType,
                silentFailures: JSON.stringify(record.silentFailures),
                requestedMode: record.requestedMode,
                effectiveMode: record.effectiveMode,
                thinkingLatencyMs: record.thinkingLatencyMs === null || record.thinkingLatencyMs === undefined
                    ? null
                    : Math.max(0, Math.round(record.thinkingLatencyMs)),
                modeNote: record.modeNote,
            },
        });
    }
    catch (e) {
        const message = e instanceof Error ? e.message : String(e);
        console.error(`TRACE-WRITE-FAIL requestId=${record.requestId}: ${message.slice(0, 180)}`);
    }
}
