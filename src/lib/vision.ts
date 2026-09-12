/**
 * PHASE 6 — IMAGE UNDERSTANDING: server-side preparation of stored image
 * attachments into real provider vision input.
 *
 * AUDIT EVIDENCE (proven empirically against the installed SDK 0.0.18, not
 * assumed — see docs/VISION.md):
 *   - `zai.chat.completions.createVision()` → POST {baseUrl}/chat/completions/vision
 *   - serving model: `glm-5v-turbo` (read from live response `model` field)
 *   - image input: `{ type: 'image_url', image_url: { url } }` with a base64
 *     data URL (SDK CLI uses exactly this for local files)
 *   - JPEG / PNG / WebP accepted natively; GIF rejected by the provider
 *     (HTTP 400 code 1210) → transcoded to PNG here via sharp; HEIC/HEIF
 *     decoded by sharp and normalized to JPEG
 *   - corrupted bytes → provider 400/1210 ("image input format/parsing
 *     error") → pre-validated here with the real sharp decoder so users get
 *     an honest, readable failure instead
 *   - multiple images per message verified (order-preserving)
 *
 * Security (§9): the DECODER is the authority. A file's declared mimeType is
 * never trusted here — only what sharp actually decodes. Storage paths never
 * leave this module. Failures never fabricate content.
 */

import sharp from 'sharp'
import { readAttachmentFile } from './attachments'
import type { Attachment } from '@prisma/client'

/**
 * Normalization policy (§10 performance): provider payload bounded by
 * re-encoding to JPEG at a bounded longest edge. 1568 px is the largest edge
 * at which vision models retain full patch fidelity without inflating image
 * tokens; the probe measured ~424 prompt tokens for a 640x400 image.
 */
export const VISION_MAX_EDGE = 1568
export const VISION_JPEG_QUALITY = 85

/**
 * Follow-up policy (§13: "follow-up question without re-uploading"): user
 * turns with image attachments are re-included from history so the model
 * keeps seeing them. Bounded: only the most recent
 * `VISION_HISTORY_IMAGE_TURNS` image-bearing turns, and at most
 * `VISION_MAX_IMAGES_PER_REQUEST` images in the whole request (current
 * message wins the budget first, newest history turns next).
 */
export const VISION_HISTORY_IMAGE_TURNS = 2
export const VISION_MAX_IMAGES_PER_REQUEST = 6

/** Formats sharp may hand to the provider after normalization. */
const DECODABLE_SOURCE_FORMATS = new Set(['jpeg', 'png', 'webp', 'gif', 'heif'])

export type VisionImageFailureReason = 'storage_missing' | 'corrupt_image' | 'unsupported_format'

export type VisionImageFailure = {
  attachmentId: string
  displayName: string
  reason: VisionImageFailureReason
}

export type PreparedVisionImage = {
  attachmentId: string
  /** data:{mime};base64 payload — the exact representation the SDK CLI uses. */
  dataUrl: string
  width: number
  height: number
}

export type PrepareResult =
  | { ok: true; image: PreparedVisionImage }
  | { ok: false; failure: VisionImageFailure }

/** One honest, user-facing sentence per failure class — no storage paths, no jargon. */
export function visionFailureMessage(f: VisionImageFailure): string {
  switch (f.reason) {
    case 'storage_missing':
      return `${f.displayName} is no longer available and was left out.`
    case 'corrupt_image':
      return `${f.displayName} appears to be corrupted and could not be opened.`
    case 'unsupported_format':
      return `${f.displayName} is in a format the assistant can't read — try JPG, PNG or WebP.`
  }
}

/**
 * Read, decode (real decoder = the security boundary), normalize and encode
 * one stored image attachment into provider input.
 *
 * GIF/HEIC/HEIF are transcoded to JPEG (animated GIF → first frame) because
 * the provider rejects GIF natively (400/1210) and HEIC support varies.
 */
export async function prepareVisionImage(att: Attachment): Promise<PrepareResult> {
  const base = { attachmentId: att.id, displayName: att.displayName }

  let bytes: Buffer
  try {
    bytes = await readAttachmentFile(att.storagePath)
  } catch {
    return { ok: false, failure: { ...base, reason: 'storage_missing' } }
  }

  // Real decode — this is both §9 validation (declared MIME is irrelevant
  // here) and the corrupt-image filter the provider would otherwise 400 on.
  let meta: sharp.Metadata
  try {
    meta = await sharp(bytes).metadata()
  } catch {
    return { ok: false, failure: { ...base, reason: 'corrupt_image' } }
  }
  if (!meta.format || !DECODABLE_SOURCE_FORMATS.has(meta.format)) {
    return { ok: false, failure: { ...base, reason: 'unsupported_format' } }
  }

  try {
    const normalized = await sharp(bytes)
      .rotate() // honour EXIF orientation before resizing
      .resize({ width: VISION_MAX_EDGE, height: VISION_MAX_EDGE, fit: 'inside', withoutEnlargement: true })
      .jpeg({ quality: VISION_JPEG_QUALITY })
      .toBuffer()
    const outMeta = await sharp(normalized).metadata()
    return {
      ok: true,
      image: {
        attachmentId: att.id,
        dataUrl: `data:image/jpeg;base64,${normalized.toString('base64')}`,
        width: outMeta.width ?? 0,
        height: outMeta.height ?? 0,
      },
    }
  } catch {
    // Decoded metadata but re-encode failed — treat as corrupt, honestly.
    return { ok: false, failure: { ...base, reason: 'corrupt_image' } }
  }
}

/** Best-effort detection of provider-side image rejections for honest errors (§8). */
export function isProviderImageRejection(message: string): boolean {
  return message.includes('1210') || message.includes('图片')
}
