"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.dynamic = void 0;
exports.GET = GET;
exports.POST = POST;
const server_1 = require("next/server");
const db_1 = require("@/lib/db");
const serializers_1 = require("@/lib/serializers");
const rate_limit_1 = require("@/lib/rate-limit");
const attachments_1 = require("@/lib/attachments");
const turn_executor_1 = require("@/lib/turn-executor");
const model_capabilities_1 = require("@/lib/model-capabilities");
exports.dynamic = 'force-dynamic';
const MAX_CONTENT_LENGTH = 32000; // shared-contracts SendMessageInput.maxLength
// GET /api/v1/conversations/:id/messages — history, oldest first
async function GET(_req, { params }) {
    const { id } = await params;
    const conversation = await db_1.db.conversation.findUnique({ where: { id } });
    if (!conversation) {
        return server_1.NextResponse.json({ code: 'not_found', message: 'Conversation not found' }, { status: 404 });
    }
    const messages = await db_1.db.message.findMany({
        where: { conversationId: id },
        orderBy: { createdAt: 'asc' },
        include: {
            attachments: { orderBy: { createdAt: 'asc' } },
            sources: { orderBy: { ordinal: 'asc' } },
        },
    });
    return server_1.NextResponse.json({ items: messages.map(serializers_1.messageToJson) });
}
// POST /api/v1/conversations/:id/messages — { content, stream?, attachments? }
// ARCHITECTURE LOCK: a client-sent modelId (legacy wire compat) is accepted
// and IGNORED — the GS Router derives all routing server-side.
//
// PHASE 1 (detached turn execution, ADR-117): the turn PIPELINE now lives in
// src/lib/turn-executor.ts — this route is the legacy transport only. Flag ON
// (BACKGROUND_RUNS_ENABLED=1), new clients should prefer
//   POST /api/v1/messages/start  +  GET /api/v1/stream/{streamId}
// where a client disconnect can never kill the turn. This inline path is
// preserved byte-for-byte as the instant-rollback default.
async function POST(req, { params }) {
    const { id } = await params;
    // FLASH-MODE BUG 3 — full-path latency instrumentation. T0=request arrival;
    // prepareMs=pre-model pipeline (router/capability/gates); T2=SSE headers;
    // T4=first event queued by the turn (mode event); firstWriteMs=first byte
    // on the wire (T4→client); T6=terminal event. Emitted as GS-PERF lines.
    const t0 = Date.now();
    // Guardrail: 20 messages / minute / client (in-memory; Redis at scale).
    const limit = (0, rate_limit_1.rateLimit)((0, rate_limit_1.clientKey)(req, `msgs:${id}`), 20, 60000);
    if (!limit.allowed) {
        return server_1.NextResponse.json({ code: 'rate_limited', message: 'Too many messages — slow down a little.' }, { status: 429, headers: { 'Retry-After': String(limit.retryAfterSec) } });
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
    const content = typeof body?.content === 'string' ? body.content : '';
    const attachmentsProvided = attachmentIds.length > 0;
    if (content.trim().length === 0 && !attachmentsProvided) {
        return server_1.NextResponse.json({ code: 'bad_request', message: 'content must be a non-empty string' }, { status: 400 });
    }
    if (content.length > MAX_CONTENT_LENGTH) {
        return server_1.NextResponse.json({ code: 'bad_request', message: `content must not exceed ${MAX_CONTENT_LENGTH} characters` }, { status: 400 });
    }
    const stream = body?.stream === true;
    const clientVersion = req.headers.get('x-gs-app-version') ?? 'web';
    const clientTimezone = typeof body?.timezone === 'string' ? body.timezone : req.headers.get('x-client-timezone');
    const turnRequest = {
        conversationId: id,
        content,
        timezone: clientTimezone,
        clientVersion,
        requestId: crypto.randomUUID(),
        attachmentIds,
        // FLASH MODE (Phase 2) — absent/invalid parses to 'flash' (the new
        // default; old clients keep working unchanged).
        mode: (0, model_capabilities_1.parseThinkingMode)(body?.mode),
    };
    const prepared = await (0, turn_executor_1.prepareTurn)(turnRequest, { content, attachments: rawAttachments });
    if (prepared.kind !== 'ready')
        return prepared.response;
    const prep = prepared.prep;
    // BUG 3: pre-model pipeline duration (router + capability + search gate +
    // evidence assembly) — everything BEFORE the first LLM call.
    const prepareMs = Date.now() - t0;
    // Non-streaming: single JSON reply.
    if (!stream) {
        const result = await (0, turn_executor_1.runTurn)(prep, null);
        if (result.errorResponse) {
            return server_1.NextResponse.json({ code: result.errorResponse.code, message: result.errorResponse.message }, { status: result.errorResponse.status });
        }
        return server_1.NextResponse.json(result.savedMessage ?? { content: '' });
    }
    // Streaming: text/event-stream — the legacy inline transport. The turn runs
    // detached from THIS connection (in-process event queue): if the view dies
    // (proxy cap / abort / navigation) the turn finishes and persists, and the
    // client adopts the answer via a conversation re-fetch. BACKGROUND_RUNS
    // (messages/start + /stream) generalizes this across reconnects.
    const eventQueue = [];
    let wakeup = null;
    const fireWakeup = () => {
        const w = wakeup;
        if (w)
            w();
    };
    let turnSettled = false;
    // BUG 3: T4 = first event the turn queues (the mode event); firstWriteMs =
    // first SSE data line actually written to the connection.
    let firstEventAt = null;
    let firstWriteAt = null;
    const push = (event, data) => {
        if (firstEventAt === null)
            firstEventAt = Date.now();
        eventQueue.push({ event, data });
        fireWakeup();
    };
    const nextEvent = async () => {
        if (eventQueue.length > 0)
            return eventQueue.shift();
        return new Promise((resolve) => {
            wakeup = () => {
                wakeup = null;
                resolve(eventQueue.shift() ?? null);
            };
        });
    };
    // The detached turn is fired (not awaited) — its lifetime is independent
    // of the Response/ReadableStream below.
    void (async () => {
        try {
            await (0, turn_executor_1.runTurn)(prep, push);
        }
        catch (e) {
            // runTurn handles its own errors; this guard only covers the executor
            // itself. The turn must always settle so the view can close.
            console.error(`TURN-CRASH conv=${id}: ${e instanceof Error ? e.message : String(e)}`);
            push('error', 'GS AI is temporarily unavailable. Please try again shortly.');
        }
        finally {
            turnSettled = true;
            fireWakeup();
        }
    })();
    return new Response(new ReadableStream({
        async start(controller) {
            const enc = new TextEncoder();
            let clientGone = false;
            // BUG 3: T2 = SSE headers handed to the transport.
            const t2 = Date.now();
            const write = (chunk) => {
                if (clientGone)
                    return;
                if (firstWriteAt === null && chunk.startsWith('data:'))
                    firstWriteAt = Date.now();
                try {
                    controller.enqueue(enc.encode(chunk));
                }
                catch {
                    clientGone = true; // view is dead — the detached turn continues
                }
            };
            // Keep-alive during silent phases (planner, retrieval, first token).
            const ping = setInterval(() => write(': ping\n\n'), 4000);
            try {
                for (;;) {
                    const ev = await nextEvent();
                    if (ev)
                        write(`data: ${JSON.stringify({ event: ev.event, data: ev.data })}\n\n`);
                    if (ev && (ev.event === 'done' || ev.event === 'error'))
                        break;
                    if (!ev && turnSettled)
                        break;
                    if (clientGone)
                        break;
                }
            }
            catch {
                clientGone = true;
            }
            finally {
                clearInterval(ping);
                // BUG 3: T6 = the terminal event reached the view. One GS-PERF line
                // per streamed turn — the numbers for the T0→T6 chain.
                console.log(`GS-PERF conv=${id} msg=${prep.userMessage.id} t0=0 prepareMs=${prepareMs} ` +
                    `headersMs=${t2 - t0} firstEventMs=${firstEventAt !== null ? firstEventAt - t0 : 'none'} ` +
                    `firstWriteMs=${firstWriteAt !== null ? firstWriteAt - t0 : 'none'} doneMs=${Date.now() - t0}`);
            }
            try {
                controller.close();
            }
            catch {
                // already closed by the runtime on client disconnect
            }
        },
        // Client disconnect (proxy cap / abort / navigation): the detached
        // turn is NOT cancelled — it finishes and persists server-side.
        cancel() { },
    }), {
        headers: {
            'Content-Type': 'text/event-stream',
            'Cache-Control': 'no-cache, no-transform',
            Connection: 'keep-alive',
            'X-Accel-Buffering': 'no',
            // FORENSIC AUDIT [3] — version handshake on every response.
            'X-GS-App-Version': prep.clientVersion,
            'X-GS-Backend-Revision': prep.backendRevision,
            'X-GS-Request-Id': prep.requestId,
        },
    });
}
