"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.dynamic = void 0;
exports.GET = GET;
exports.PATCH = PATCH;
exports.DELETE = DELETE;
const server_1 = require("next/server");
const db_1 = require("@/lib/db");
const serializers_1 = require("@/lib/serializers");
const rate_limit_1 = require("@/lib/rate-limit");
exports.dynamic = 'force-dynamic';
const MAX_NAME = 80;
const MAX_TEXT = 8000;
const ALLOWED_CATEGORIES = [
    'general',
    'research',
    'writing',
    'coding',
    'productivity',
    'learning',
];
// GET /api/v1/assistants/:id — full assistant (with conversation count)
async function GET(_req, { params }) {
    const { id } = await params;
    const assistant = await db_1.db.assistant.findUnique({
        where: { id },
        include: { _count: { select: { conversations: true } } },
    });
    if (!assistant) {
        return server_1.NextResponse.json({ code: 'not_found', message: 'Assistant not found' }, { status: 404 });
    }
    return server_1.NextResponse.json({
        ...(0, serializers_1.assistantToJson)(assistant),
        conversationCount: assistant._count.conversations,
    });
}
// PATCH /api/v1/assistants/:id — partial update (favourite, publish, edits)
async function PATCH(req, { params }) {
    const { id } = await params;
    // Guardrail: 30 mutations / minute / client.
    const limit = (0, rate_limit_1.rateLimit)((0, rate_limit_1.clientKey)(req, 'assistants:patch'), 30, 60000);
    if (!limit.allowed) {
        return server_1.NextResponse.json({ code: 'rate_limited', message: 'Too many updates — slow down.' }, { status: 429, headers: { 'Retry-After': String(limit.retryAfterSec) } });
    }
    const existing = await db_1.db.assistant.findUnique({ where: { id } });
    if (!existing) {
        return server_1.NextResponse.json({ code: 'not_found', message: 'Assistant not found' }, { status: 404 });
    }
    let body;
    try {
        body = (await req.json());
    }
    catch {
        return server_1.NextResponse.json({ code: 'bad_request', message: 'Request body must be valid JSON' }, { status: 400 });
    }
    const data = {};
    if (typeof body.name === 'string' && body.name.trim())
        data.name = body.name.trim().slice(0, MAX_NAME);
    if (typeof body.description === 'string')
        data.description = body.description.slice(0, MAX_TEXT);
    if (typeof body.instructions === 'string')
        data.instructions = body.instructions.slice(0, MAX_TEXT);
    if (typeof body.category === 'string' && ALLOWED_CATEGORIES.includes(body.category)) {
        data.category = body.category;
    }
    if (typeof body.favourite === 'boolean')
        data.favourite = body.favourite;
    if (typeof body.published === 'boolean')
        data.published = body.published;
    if (Array.isArray(body.starters)) {
        data.starters = JSON.stringify(body.starters.filter((s) => typeof s === 'string').slice(0, 6));
    }
    if (Object.keys(data).length === 0) {
        return server_1.NextResponse.json({ code: 'bad_request', message: 'No valid fields to update' }, { status: 400 });
    }
    const updated = await db_1.db.assistant.update({ where: { id }, data });
    return server_1.NextResponse.json((0, serializers_1.assistantToJson)(updated));
}
// DELETE /api/v1/assistants/:id — remove (conversations keep running, unlink first)
async function DELETE(_req, { params }) {
    const { id } = await params;
    const existing = await db_1.db.assistant.findUnique({ where: { id } });
    if (!existing) {
        return server_1.NextResponse.json({ code: 'not_found', message: 'Assistant not found' }, { status: 404 });
    }
    await db_1.db.conversation.updateMany({ where: { assistantId: id }, data: { assistantId: null } });
    await db_1.db.assistant.delete({ where: { id } });
    return server_1.NextResponse.json({ deleted: true, id });
}
