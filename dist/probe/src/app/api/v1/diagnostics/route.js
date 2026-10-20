"use strict";
/**
 * DEVICE DIAGNOSTICS — the endpoint the phone-side acceptance harness pings.
 *
 * Purpose (forensic closure TASK 4): prove the PHYSICAL DEVICE is talking to
 * the SHIPPED backend before trusting any on-device test result. Returns:
 *
 *   - clientVersion:   the X-GS-App-Version the request itself carried
 *   - currentVersion:  the backend's current expected app version
 *   - versionMatch:    whether the two agree ('web' callers are exempt, as
 *                      everywhere else in the API)
 *   - backendRevision: env override > live git revision — the proof of WHICH
 *                      backend this is
 *   - trace:           the last N GS-CAP rows (the structured trace store,
 *                      src/lib/trace.ts) for ONE conversation, newest first,
 *                      each with the user prompt preview so the operator can
 *                      match rows to the 6 acceptance prompts by eye
 *
 * ARCHITECTURE LOCK: modelRoute stays INTERNAL ONLY and is deliberately NOT
 * returned — no plan field, model id or provider name reaches a client.
 *
 * Scoping: without ?conversationId or ?requestId only the version block is
 * returned (no cross-conversation content). With ?conversationId the trace
 * rows are scoped to that conversation — the same visibility
 * GET /conversations/:id/messages already grants, no new exposure class.
 * ?requestId scopes to one turn (CI gate export: silent-failure verdicts per
 * eval run row). modelRoute stays INTERNAL ONLY in every mode.
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.dynamic = void 0;
exports.GET = GET;
const server_1 = require("next/server");
const db_1 = require("@/lib/db");
const revision_1 = require("@/lib/revision");
exports.dynamic = 'force-dynamic';
const CURRENT_APP_VERSION = '0.73.0';
const DEFAULT_LIMIT = 10;
const MAX_LIMIT = 50;
function parseLimit(raw) {
    if (raw === null)
        return DEFAULT_LIMIT;
    const parsed = Number.parseInt(raw, 10);
    if (Number.isNaN(parsed))
        return DEFAULT_LIMIT;
    return Math.min(Math.max(parsed, 1), MAX_LIMIT);
}
// GET /api/v1/diagnostics?conversationId=<id>&limit=10
// GET /api/v1/diagnostics?requestId=<id>            (one turn — CI gate export)
async function GET(req) {
    const url = new URL(req.url);
    const clientVersion = req.headers.get('x-gs-app-version') ?? 'web';
    const backendRevision = await (0, revision_1.getBackendRevision)();
    const conversationId = url.searchParams.get('conversationId');
    const requestId = url.searchParams.get('requestId');
    const limit = parseLimit(url.searchParams.get('limit'));
    const base = {
        clientVersion,
        currentVersion: CURRENT_APP_VERSION,
        versionMatch: clientVersion === 'web' || clientVersion === CURRENT_APP_VERSION,
        backendRevision,
        conversationId,
    };
    if (!conversationId && !requestId) {
        return server_1.NextResponse.json({ ...base, trace: [] });
    }
    const rows = await db_1.db.turnTrace.findMany({
        where: requestId ? { requestId } : { conversationId: conversationId },
        orderBy: { timestamp: 'desc' },
        take: limit,
    });
    // Prompt previews: join each trace row's user message so the operator can
    // match rows to the acceptance prompts without another call.
    const messageIds = Array.from(new Set(rows.map((r) => r.messageId)));
    const messages = await db_1.db.message.findMany({
        where: { id: { in: messageIds } },
        select: { id: true, role: true, content: true },
    });
    const promptById = new Map(messages.filter((m) => m.role === 'user').map((m) => [m.id, m.content.slice(0, 120)]));
    const trace = rows.map((r) => ({
        requestId: r.requestId,
        messageId: r.messageId,
        timestamp: r.timestamp.toISOString(),
        clientVersion: r.clientVersion,
        backendRevision: r.backendRevision,
        capability: r.capability,
        trigger: r.trigger,
        searchExecuted: r.searchExecuted,
        researchExecuted: r.researchExecuted,
        evidenceCount: r.evidenceCount,
        sourceCount: r.sourceCount,
        sourcesRead: r.sourcesRead,
        sourcesFailed: r.sourcesFailed,
        domains: JSON.parse(r.domains),
        finalStatus: r.finalStatus,
        cited: JSON.parse(r.cited),
        latencyMs: r.latencyMs,
        errorType: r.errorType,
        // silent-failure gate verdicts — QA result of the turn, never a plan field
        silentFailures: r.silentFailures ? JSON.parse(r.silentFailures) : [],
        // FLASH MODE (Phase 5) — the mode dimension is device-diagnosable.
        // modelRoute stays INTERNAL ONLY (Architecture Lock, unchanged).
        requestedMode: r.requestedMode,
        effectiveMode: r.effectiveMode,
        thinkingLatencyMs: r.thinkingLatencyMs,
        modeNote: r.modeNote,
        promptPreview: promptById.get(r.messageId) ?? null,
    }));
    return server_1.NextResponse.json({ ...base, trace });
}
