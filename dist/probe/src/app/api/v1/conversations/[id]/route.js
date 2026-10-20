"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.dynamic = void 0;
exports.GET = GET;
exports.PATCH = PATCH;
exports.DELETE = DELETE;
const server_1 = require("next/server");
const db_1 = require("@/lib/db");
const serializers_1 = require("@/lib/serializers");
exports.dynamic = 'force-dynamic';
// GET /api/v1/conversations/:id
async function GET(_req, { params }) {
    const { id } = await params;
    const conversation = await db_1.db.conversation.findUnique({ where: { id } });
    if (!conversation) {
        return server_1.NextResponse.json({ code: 'not_found', message: 'Conversation not found' }, { status: 404 });
    }
    return server_1.NextResponse.json((0, serializers_1.conversationToJson)(conversation));
}
// PATCH /api/v1/conversations/:id — { title?, pinned?, archived? }
// ARCHITECTURE LOCK: client-sent modelId (legacy) is IGNORED — no model
// preference is stored or honored anywhere.
async function PATCH(req, { params }) {
    const { id } = await params;
    const existing = await db_1.db.conversation.findUnique({ where: { id } });
    if (!existing) {
        return server_1.NextResponse.json({ code: 'not_found', message: 'Conversation not found' }, { status: 404 });
    }
    let body;
    try {
        body = (await req.json());
    }
    catch {
        return server_1.NextResponse.json({ code: 'bad_request', message: 'Request body must be valid JSON' }, { status: 400 });
    }
    const data = {};
    if (typeof body.title === 'string' && body.title.trim())
        data.title = body.title.trim().slice(0, 120);
    if (typeof body.pinned === 'boolean')
        data.pinned = body.pinned;
    if (typeof body.archived === 'boolean')
        data.archived = body.archived;
    if (Object.keys(data).length === 0) {
        return server_1.NextResponse.json({ code: 'bad_request', message: 'No valid fields to update' }, { status: 400 });
    }
    const updated = await db_1.db.conversation.update({ where: { id }, data });
    return server_1.NextResponse.json((0, serializers_1.conversationToJson)(updated));
}
// DELETE /api/v1/conversations/:id — messages cascade via Prisma onDelete: Cascade
async function DELETE(_req, { params }) {
    const { id } = await params;
    const conversation = await db_1.db.conversation.findUnique({ where: { id } });
    if (!conversation) {
        return server_1.NextResponse.json({ code: 'not_found', message: 'Conversation not found' }, { status: 404 });
    }
    await db_1.db.conversation.delete({ where: { id } });
    return new server_1.NextResponse(null, { status: 204 });
}
