"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.dynamic = void 0;
exports.GET = GET;
exports.POST = POST;
const server_1 = require("next/server");
const db_1 = require("@/lib/db");
const serializers_1 = require("@/lib/serializers");
const rate_limit_1 = require("@/lib/rate-limit");
exports.dynamic = 'force-dynamic';
const DEFAULT_LIMIT = 20;
const MAX_LIMIT = 100;
function parseLimit(raw) {
    if (raw === null)
        return DEFAULT_LIMIT;
    const parsed = Number.parseInt(raw, 10);
    if (Number.isNaN(parsed))
        return DEFAULT_LIMIT;
    return Math.min(Math.max(parsed, 1), MAX_LIMIT);
}
// GET /api/v1/conversations?limit=20 — newest first (updatedAt desc)
async function GET(req) {
    const limit = parseLimit(new URL(req.url).searchParams.get('limit'));
    const conversations = await db_1.db.conversation.findMany({
        orderBy: { updatedAt: 'desc' },
        take: limit,
        include: { assistant: { select: { id: true, name: true } } },
    });
    return server_1.NextResponse.json({
        items: conversations.map((c) => ({
            // include-select narrows `assistant` below the full serializer input —
            // the narrowed row still satisfies every field conversationToJson reads.
            ...(0, serializers_1.conversationToJson)(c),
            ...(c.assistant ? { assistantName: c.assistant.name } : {}),
        })),
        nextCursor: null,
    });
}
// POST /api/v1/conversations — { title?, assistantId? } → 201 Conversation
// ARCHITECTURE LOCK: client-sent modelId (legacy) is IGNORED — the backend
// GS Router derives all routing server-side; no model preference is stored.
async function POST(req) {
    // Guardrail: 30 creations / minute / client (in-memory; Redis at scale).
    const limit = (0, rate_limit_1.rateLimit)((0, rate_limit_1.clientKey)(req, 'convs:create'), 30, 60000);
    if (!limit.allowed) {
        return server_1.NextResponse.json({ code: 'rate_limited', message: 'Too many conversations — slow down a little.' }, { status: 429, headers: { 'Retry-After': String(limit.retryAfterSec) } });
    }
    let body;
    try {
        body = (await req.json());
    }
    catch {
        body = {};
    }
    const title = typeof body?.title === 'string' && body.title.trim().length > 0 ? body.title.trim() : undefined;
    const assistantId = typeof body?.assistantId === 'string' && body.assistantId.trim().length > 0
        ? body.assistantId.trim()
        : undefined;
    if (assistantId) {
        const assistant = await db_1.db.assistant.findUnique({ where: { id: assistantId } });
        if (!assistant) {
            return server_1.NextResponse.json({ code: 'bad_request', message: 'assistantId does not reference a known assistant' }, { status: 400 });
        }
    }
    const conversation = await db_1.db.conversation.create({
        data: {
            ...(title ? { title } : {}),
            ...(assistantId ? { assistantId } : {}),
        },
    });
    return server_1.NextResponse.json((0, serializers_1.conversationToJson)(conversation), { status: 201 });
}
