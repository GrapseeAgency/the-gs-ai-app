"use strict";
/**
 * DB-model → API-JSON mappers.
 *
 * Field names follow shared-contracts/openapi.yaml EXACTLY:
 *   Conversation: { id, title, assistantId?, pinned, archived, createdAt, updatedAt }
 *   Message:      { id, conversationId, role, content, createdAt }
 *
 * ARCHITECTURE LOCK: modelId is INTERNAL (GS Router) and is no longer part
 * of any client-facing payload — clients never see or choose models.
 * Dates are serialized as ISO-8601 date-time strings.
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.assistantToJson = assistantToJson;
exports.conversationToJson = conversationToJson;
exports.messageToJson = messageToJson;
exports.sourceToJson = sourceToJson;
const attachments_1 = require("@/lib/attachments");
function parseStarters(raw) {
    try {
        const parsed = JSON.parse(raw);
        return Array.isArray(parsed) ? parsed.filter((s) => typeof s === 'string') : [];
    }
    catch {
        return [];
    }
}
function assistantToJson(a) {
    return {
        id: a.id,
        slug: a.slug,
        name: a.name,
        description: a.description,
        instructions: a.instructions,
        category: a.category,
        starters: parseStarters(a.starters),
        published: a.published,
        favourite: a.favourite,
        uses: a.uses,
        rating: a.rating,
        createdAt: a.createdAt.toISOString(),
    };
}
function conversationToJson(c) {
    return {
        id: c.id,
        title: c.title,
        ...(c.assistantId ? { assistantId: c.assistantId } : {}),
        pinned: c.pinned,
        archived: c.archived,
        createdAt: c.createdAt.toISOString(),
        updatedAt: c.updatedAt.toISOString(),
    };
}
function messageToJson(m) {
    return {
        id: m.id,
        conversationId: m.conversationId,
        role: m.role,
        content: m.content,
        createdAt: m.createdAt.toISOString(),
        ...(m.attachments && m.attachments.length > 0
            ? { attachments: m.attachments.map(attachments_1.attachmentToJson) }
            : {}),
        ...(m.sources && m.sources.length > 0
            ? { sources: m.sources.map(sourceToJson) }
            : {}),
        ...(m.clarifyOptions ? { clarifyOptions: m.clarifyOptions } : {}),
    };
}
function sourceToJson(s) {
    return {
        id: s.id,
        ordinal: s.ordinal,
        title: s.title,
        url: s.url,
        domain: s.domain,
        snippet: s.snippet,
        ...(s.publishedDate ? { publishedDate: s.publishedDate } : {}),
        query: s.query,
        retrievedAt: s.retrievedAt.toISOString(),
        ...(s.status ? { status: s.status } : {}),
        ...(s.rank !== null ? { rank: s.rank } : {}),
        used: s.used,
    };
}
