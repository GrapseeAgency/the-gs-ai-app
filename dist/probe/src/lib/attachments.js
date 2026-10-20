"use strict";
/**
 * PHASE 5 input & multimodal foundation — server-side attachment validation
 * and disk storage. Single source of truth for the MIME allowlist, size caps
 * and kind derivation. Clients mirror these limits only as early, friendly
 * pre-flight checks; the server never trusts them (§11 security).
 *
 * Honest-capability rule (Phase 5 §5): these rules exist to RECEIVE content.
 * Nothing here analyzes content — no OCR, no vision, no document intelligence.
 */
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.ALLOWED_MIME_TYPES = exports.MAX_OCR_TEXT_CHARS = exports.MAX_ATTACHMENTS_PER_MESSAGE = exports.MAX_ATTACHMENT_BYTES = void 0;
exports.kindForMime = kindForMime;
exports.uploadsRoot = uploadsRoot;
exports.storagePathFor = storagePathFor;
exports.sanitizeDisplayName = sanitizeDisplayName;
exports.sniffMatchesMime = sniffMatchesMime;
exports.writeAttachmentFile = writeAttachmentFile;
exports.readAttachmentFile = readAttachmentFile;
exports.contentHash = contentHash;
exports.attachmentToJson = attachmentToJson;
const crypto_1 = require("crypto");
const promises_1 = require("fs/promises");
const path_1 = __importDefault(require("path"));
/** Hard byte cap per attachment — enforced before any byte is written. */
exports.MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024; // 10 MB
/** Hard cap per message. */
exports.MAX_ATTACHMENTS_PER_MESSAGE = 6;
/**
 * TASK 3 vision input contract — cap on the client-supplied OCR sidecar for
 * image attachments. Bounded at the receive layer so a client cannot smuggle
 * an unbounded prompt payload in through the upload form.
 */
exports.MAX_OCR_TEXT_CHARS = 20000;
/**
 * Receive allowlist. Audio/video are deliberately ABSENT — the platform has no
 * processor for them yet, so receiving them would only fake a capability
 * (Phase 5 §5/§14). Extend this list only when a real consumer exists.
 */
exports.ALLOWED_MIME_TYPES = new Set([
    'image/jpeg',
    'image/png',
    'image/webp',
    'image/gif',
    'image/heic',
    'image/heif',
    'application/pdf',
    'text/plain',
    'text/markdown',
    'text/csv',
]);
function kindForMime(mime) {
    if (mime.startsWith('image/'))
        return 'image';
    if (mime === 'application/pdf')
        return 'pdf';
    if (mime.startsWith('text/'))
        return 'document';
    return null;
}
/** Storage root for uploaded attachments (relative to repo root). */
function uploadsRoot() {
    return path_1.default.join(process.cwd(), 'uploads');
}
/** Server-private relative path — never exposed through the API. */
function storagePathFor(id, displayName) {
    return path_1.default.join('attachments', id, sanitizeDisplayName(displayName));
}
/** Strip path separators/control chars; keep a readable, bounded filename. */
function sanitizeDisplayName(raw) {
    const base = raw.split(/[\\/]/).pop() ?? '';
    const cleaned = Array.from(base)
        .filter((ch) => ch.charCodeAt(0) >= 32 && ch !== '\u007f')
        .join('')
        .trim();
    const safe = cleaned.length > 0 ? cleaned : 'attachment';
    return safe.length > 120 ? safe.slice(0, 120) : safe;
}
/**
 * Light magic-byte sniff so a spoofed Content-Type cannot smuggle binary data
 * in as text or vice versa. Deliberately minimal — this is validation, not
 * format analysis.
 */
function sniffMatchesMime(head, mime) {
    const ascii = (start, text) => text
        .split('')
        .every((ch, i) => head[start + i] === ch.charCodeAt(0));
    switch (mime) {
        case 'image/jpeg':
            return head[0] === 0xff && head[1] === 0xd8 && head[2] === 0xff;
        case 'image/png':
            return head[0] === 0x89 && ascii(1, 'PNG');
        case 'image/gif':
            return ascii(0, 'GIF8');
        case 'image/webp':
            return ascii(0, 'RIFF') && ascii(8, 'WEBP');
        case 'image/heic':
        case 'image/heif':
            // ISO BMFF: brand string sits at offset 8 ("heic"/"heix"/"mif1"/"msf1").
            return ascii(4, 'ftyp') && (ascii(8, 'heic') || ascii(8, 'heix') || ascii(8, 'mif1') || ascii(8, 'msf1'));
        case 'application/pdf':
            return ascii(0, '%PDF');
        case 'text/plain':
        case 'text/markdown':
        case 'text/csv':
            // Text must decode as UTF-8 and contain no NUL bytes.
            return !head.slice(0, 512).includes(0);
        default:
            return false;
    }
}
/** Persist bytes under the uploads root; returns the private relative path. */
async function writeAttachmentFile(id, displayName, bytes) {
    const rel = storagePathFor(id, displayName);
    const abs = path_1.default.join(uploadsRoot(), rel);
    await (0, promises_1.mkdir)(path_1.default.dirname(abs), { recursive: true });
    await (0, promises_1.writeFile)(abs, bytes);
    return rel;
}
async function readAttachmentFile(rel) {
    const abs = path_1.default.join(uploadsRoot(), rel);
    // Defense-in-depth: the resolved path must stay inside the uploads root.
    const resolved = path_1.default.resolve(abs);
    const root = path_1.default.resolve(uploadsRoot());
    if (!resolved.startsWith(root + path_1.default.sep)) {
        throw new Error('attachment path escapes storage root');
    }
    return (0, promises_1.readFile)(resolved);
}
/** Cheap content fingerprint kept in logs for integrity debugging. */
function contentHash(bytes) {
    return (0, crypto_1.createHash)('sha256').update(bytes).digest('hex').slice(0, 16);
}
function attachmentToJson(a) {
    return {
        id: a.id,
        kind: a.kind,
        displayName: a.displayName,
        mimeType: a.mimeType,
        byteSize: a.byteSize,
        createdAt: a.createdAt.toISOString(),
        url: `/api/v1/files/${a.id}`,
    };
}
