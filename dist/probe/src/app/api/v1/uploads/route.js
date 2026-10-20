"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.dynamic = exports.runtime = void 0;
exports.POST = POST;
const server_1 = require("next/server");
const db_1 = require("@/lib/db");
const rate_limit_1 = require("@/lib/rate-limit");
const attachments_1 = require("@/lib/attachments");
exports.runtime = 'nodejs';
exports.dynamic = 'force-dynamic';
const SNIFF_HEAD_BYTES = 16;
/**
 * POST /api/v1/uploads — multipart/form-data
 *   file            (required) the attachment bytes
 *   conversationId  (optional) pre-bind to a conversation
 *
 * Real transport, real storage, real records. No simulated progress anywhere:
 * the HTTP round-trip IS the upload lifecycle the clients surface.
 */
async function POST(req) {
    const limit = (0, rate_limit_1.rateLimit)((0, rate_limit_1.clientKey)(req, 'uploads'), 30, 60000);
    if (!limit.allowed) {
        return server_1.NextResponse.json({ code: 'rate_limited', message: 'Too many uploads — slow down a little.' }, { status: 429, headers: { 'Retry-After': String(limit.retryAfterSec) } });
    }
    let form;
    try {
        form = await req.formData();
    }
    catch {
        return server_1.NextResponse.json({ code: 'bad_request', message: 'Request must be multipart/form-data' }, { status: 400 });
    }
    const file = form.get('file');
    if (!(file instanceof File) || file.size === 0) {
        return server_1.NextResponse.json({ code: 'bad_request', message: 'A non-empty "file" field is required' }, { status: 400 });
    }
    if (file.size > attachments_1.MAX_ATTACHMENT_BYTES) {
        return server_1.NextResponse.json({ code: 'too_large', message: `Attachment exceeds the ${Math.round(attachments_1.MAX_ATTACHMENT_BYTES / (1024 * 1024))} MB limit` }, { status: 413 });
    }
    // PHASE 7 §10: declared MIME may carry parameters ("text/plain;charset=utf-8"
    // from some clients/blob implementations). Validate the bare type — the
    // bytes are verified independently below.
    const mimeType = (file.type || 'application/octet-stream').split(';')[0].trim().toLowerCase();
    const kind = (0, attachments_1.kindForMime)(mimeType);
    if (!attachments_1.ALLOWED_MIME_TYPES.has(mimeType) || !kind) {
        return server_1.NextResponse.json({ code: 'unsupported_media_type', message: `Files of type ${mimeType} are not accepted yet` }, { status: 415 });
    }
    // Optional conversation pre-bind — must exist if provided.
    const rawConversationId = form.get('conversationId');
    let conversationId = null;
    if (typeof rawConversationId === 'string' && rawConversationId.trim().length > 0) {
        const conversation = await db_1.db.conversation.findUnique({ where: { id: rawConversationId.trim() } });
        if (!conversation) {
            return server_1.NextResponse.json({ code: 'not_found', message: 'Conversation not found' }, { status: 404 });
        }
        conversationId = conversation.id;
    }
    // Read once — the size gate above ran on the declared size; these bytes are
    // the truth. Re-check so a lying Content-Length cannot bypass the cap.
    const bytes = new Uint8Array(await file.arrayBuffer());
    if (bytes.byteLength === 0 || bytes.byteLength > attachments_1.MAX_ATTACHMENT_BYTES) {
        return server_1.NextResponse.json({ code: 'too_large', message: `Attachment exceeds the ${Math.round(attachments_1.MAX_ATTACHMENT_BYTES / (1024 * 1024))} MB limit` }, { status: 413 });
    }
    if (!(0, attachments_1.sniffMatchesMime)(bytes.slice(0, SNIFF_HEAD_BYTES), mimeType)) {
        return server_1.NextResponse.json({ code: 'unsupported_media_type', message: 'File contents do not match the declared type' }, { status: 415 });
    }
    const displayNameField = form.get('displayName');
    const displayName = typeof displayNameField === 'string' && displayNameField.trim().length > 0
        ? displayNameField.trim()
        : file.name || 'attachment';
    // TASK 3 vision input contract: the client runs OCR at attach time and may
    // send the extracted text alongside the bytes. Stored as a sidecar only:
    // images are still handed to the vision model as real bytes, and the OCR
    // text is used exclusively when the bytes cannot be (see turn-executor).
    // Images only, bounded, whitespace-normalized; empty means no sidecar.
    const rawOcr = form.get('ocrText');
    const ocrText = kind === 'image' && typeof rawOcr === 'string' && rawOcr.trim().length > 0
        ? rawOcr.replace(/\s+/g, ' ').trim().slice(0, attachments_1.MAX_OCR_TEXT_CHARS)
        : null;
    try {
        const created = await db_1.db.$transaction(async (tx) => {
            const attachment = await tx.attachment.create({
                data: {
                    kind,
                    displayName: displayName.slice(0, 200),
                    mimeType,
                    byteSize: bytes.byteLength,
                    storagePath: 'pending', // finalized below once the row id exists
                    conversationId,
                    ocrText,
                },
            });
            const storagePath = await (0, attachments_1.writeAttachmentFile)(attachment.id, displayName, bytes);
            return tx.attachment.update({ where: { id: attachment.id }, data: { storagePath } });
        });
        // Structured log line — fingerprint only, never file contents.
        console.log(`[uploads] stored attachment=${created.id} kind=${created.kind} bytes=${created.byteSize} hash=${(0, attachments_1.contentHash)(bytes)} ocr=${created.ocrText ? created.ocrText.length : 0}`);
        return server_1.NextResponse.json({ attachment: (0, attachments_1.attachmentToJson)(created) }, { status: 201 });
    }
    catch (e) {
        console.error('[uploads] storage failure', e);
        return server_1.NextResponse.json({ code: 'storage_error', message: 'Could not store the attachment — try again' }, { status: 500 });
    }
}
