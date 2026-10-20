"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.dynamic = exports.runtime = void 0;
exports.GET = GET;
const server_1 = require("next/server");
const db_1 = require("@/lib/db");
const attachments_1 = require("@/lib/attachments");
exports.runtime = 'nodejs';
exports.dynamic = 'force-dynamic';
/**
 * GET /api/v1/files/:id — serves attachment bytes with their real MIME type.
 * The storage path never appears in any response; ids are the only handle.
 */
async function GET(_req, { params }) {
    const { id } = await params;
    const attachment = await db_1.db.attachment.findUnique({ where: { id } });
    if (!attachment) {
        return server_1.NextResponse.json({ code: 'not_found', message: 'Attachment not found' }, { status: 404 });
    }
    try {
        const bytes = await (0, attachments_1.readAttachmentFile)(attachment.storagePath);
        const inlineName = encodeURIComponent(attachment.displayName);
        return new Response(new Uint8Array(bytes), {
            headers: {
                'Content-Type': attachment.mimeType,
                'Content-Length': String(bytes.byteLength),
                'Content-Disposition': `inline; filename*=UTF-8''${inlineName}`,
                'Cache-Control': 'private, max-age=31536000, immutable',
            },
        });
    }
    catch {
        // Record exists but bytes are gone — say so honestly instead of 404-blur.
        return server_1.NextResponse.json({ code: 'file_missing', message: 'Attachment record exists but its contents are unavailable' }, { status: 410 });
    }
}
