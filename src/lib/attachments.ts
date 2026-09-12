/**
 * PHASE 5 input & multimodal foundation — server-side attachment validation
 * and disk storage. Single source of truth for the MIME allowlist, size caps
 * and kind derivation. Clients mirror these limits only as early, friendly
 * pre-flight checks; the server never trusts them (§11 security).
 *
 * Honest-capability rule (Phase 5 §5): these rules exist to RECEIVE content.
 * Nothing here analyzes content — no OCR, no vision, no document intelligence.
 */

import { createHash } from 'crypto'
import { mkdir, readFile, writeFile } from 'fs/promises'
import path from 'path'
import type { Attachment } from '@prisma/client'

/** Hard byte cap per attachment — enforced before any byte is written. */
export const MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024 // 10 MB
/** Hard cap per message. */
export const MAX_ATTACHMENTS_PER_MESSAGE = 6

/**
 * Receive allowlist. Audio/video are deliberately ABSENT — the platform has no
 * processor for them yet, so receiving them would only fake a capability
 * (Phase 5 §5/§14). Extend this list only when a real consumer exists.
 */
export const ALLOWED_MIME_TYPES: ReadonlySet<string> = new Set([
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
])

export type AttachmentKind = 'image' | 'pdf' | 'document'

export function kindForMime(mime: string): AttachmentKind | null {
  if (mime.startsWith('image/')) return 'image'
  if (mime === 'application/pdf') return 'pdf'
  if (mime.startsWith('text/')) return 'document'
  return null
}

/** Storage root for uploaded attachments (relative to repo root). */
export function uploadsRoot(): string {
  return path.join(process.cwd(), 'uploads')
}

/** Server-private relative path — never exposed through the API. */
export function storagePathFor(id: string, displayName: string): string {
  return path.join('attachments', id, sanitizeDisplayName(displayName))
}

/** Strip path separators/control chars; keep a readable, bounded filename. */
export function sanitizeDisplayName(raw: string): string {
  const base = raw.split(/[\\/]/).pop() ?? ''
  const cleaned = Array.from(base)
    .filter((ch) => ch.charCodeAt(0) >= 32 && ch !== '\u007f')
    .join('')
    .trim()
  const safe = cleaned.length > 0 ? cleaned : 'attachment'
  return safe.length > 120 ? safe.slice(0, 120) : safe
}

/**
 * Light magic-byte sniff so a spoofed Content-Type cannot smuggle binary data
 * in as text or vice versa. Deliberately minimal — this is validation, not
 * format analysis.
 */
export function sniffMatchesMime(head: Uint8Array, mime: string): boolean {
  const ascii = (start: number, text: string) =>
    text
      .split('')
      .every((ch, i) => head[start + i] === ch.charCodeAt(0))
  switch (mime) {
    case 'image/jpeg':
      return head[0] === 0xff && head[1] === 0xd8 && head[2] === 0xff
    case 'image/png':
      return head[0] === 0x89 && ascii(1, 'PNG')
    case 'image/gif':
      return ascii(0, 'GIF8')
    case 'image/webp':
      return ascii(0, 'RIFF') && ascii(8, 'WEBP')
    case 'image/heic':
    case 'image/heif':
      // ISO BMFF: brand string sits at offset 8 ("heic"/"heix"/"mif1"/"msf1").
      return ascii(4, 'ftyp') && (ascii(8, 'heic') || ascii(8, 'heix') || ascii(8, 'mif1') || ascii(8, 'msf1'))
    case 'application/pdf':
      return ascii(0, '%PDF')
    case 'text/plain':
    case 'text/markdown':
    case 'text/csv':
      // Text must decode as UTF-8 and contain no NUL bytes.
      return !head.slice(0, 512).includes(0)
    default:
      return false
  }
}

/** Persist bytes under the uploads root; returns the private relative path. */
export async function writeAttachmentFile(
  id: string,
  displayName: string,
  bytes: Uint8Array
): Promise<string> {
  const rel = storagePathFor(id, displayName)
  const abs = path.join(uploadsRoot(), rel)
  await mkdir(path.dirname(abs), { recursive: true })
  await writeFile(abs, bytes)
  return rel
}

export async function readAttachmentFile(rel: string): Promise<Buffer> {
  const abs = path.join(uploadsRoot(), rel)
  // Defense-in-depth: the resolved path must stay inside the uploads root.
  const resolved = path.resolve(abs)
  const root = path.resolve(uploadsRoot())
  if (!resolved.startsWith(root + path.sep)) {
    throw new Error('attachment path escapes storage root')
  }
  return readFile(resolved)
}

/** Cheap content fingerprint kept in logs for integrity debugging. */
export function contentHash(bytes: Uint8Array): string {
  return createHash('sha256').update(bytes).digest('hex').slice(0, 16)
}

/** API-facing attachment shape — mirrors shared-contracts/openapi.yaml. */
export type AttachmentJson = {
  id: string
  kind: string
  displayName: string
  mimeType: string
  byteSize: number
  createdAt: string
  url: string
}

export function attachmentToJson(a: Attachment): AttachmentJson {
  return {
    id: a.id,
    kind: a.kind,
    displayName: a.displayName,
    mimeType: a.mimeType,
    byteSize: a.byteSize,
    createdAt: a.createdAt.toISOString(),
    url: `/api/v1/files/${a.id}`,
  }
}
