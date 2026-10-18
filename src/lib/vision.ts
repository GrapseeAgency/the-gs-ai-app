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
 * Normalization policy (§10 performance, REVISED by the operator's 2026-10-14
 * TASK 3 audit): provider payload bounded by re-encoding to JPEG at a bounded
 * longest edge. 1568 px was chosen for token economy, but the operator's real
 * phone photos proved it destroys SMALL TEXT: wrapper/label text that is
 * readable in the original 4000 px capture became unreadable mush at 1568 —
 * the model then guessed. 2048 px keeps small-text fidelity (the whole point
 * of "read the actual text") at ~1.7x the token cost of 1568 — a cost the
 * flash-model turn budget absorbs easily.
 */
export const VISION_MAX_EDGE = 2048
export const VISION_JPEG_QUALITY = 90

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
  /**
   * `full` = the whole frame, bounded to VISION_MAX_EDGE.
   * `detail` = a native-resolution crop of the densest region of the SAME
   * frame, sent after it and labelled in the message.
   */
  role: 'full' | 'detail'
}

export type DetailRegion = { left: number; top: number; width: number; height: number }

export type PrepareResult =
  | { ok: true; image: PreparedVisionImage; detail: PreparedVisionImage | null }
  | { ok: false; failure: VisionImageFailure }

/**
 * HARD-PICTURE POLICY (operator 2026-10-18): "general to hard" images.
 *
 * The full frame is bounded to 2048 px for token economy. A 12 MP phone photo
 * of a product box is therefore delivered at roughly a quarter of its linear
 * resolution, and a label that was perfectly legible in the capture becomes a
 * few dozen pixels of Bengali/Arabic/CJK — the model then guesses, confidently
 * and wrongly ("নাপ্রক্স" for "নাইটকপ").
 *
 * So when the stored image is much larger than the provider copy, we also send
 * ONE crop of the densest region at native resolution. Text is the highest
 * frequency content in a photo, so the energy map lands on the label, the
 * document, the sign — exactly the part that was destroyed by downscaling.
 *
 * The full frame still travels FIRST, so context is never lost, and the crop is
 * labelled in the message so the model reads it as a detail of the same image
 * rather than as a second picture. Cost: ~1 extra image per hard picture, only
 * when there is real detail to recover.
 */
export const VISION_DETAIL_MIN_SOURCE_EDGE = 2600
export const VISION_DETAIL_MAX_EDGE = 2048
export const VISION_DETAIL_CROP_NOTE =
  'Detail crop of the same image at native resolution (same photo, zoomed).'

/** Cells in the density map. 48x48 is enough to find a label, cheap to compute. */
const DETAIL_GRID = 48

/**
 * Where the detail is, in the SOURCE image's own pixel space (no rotation
 * applied — the caller crops in that same space, then orients the crop).
 *
 * Returns null when the image is not being meaningfully downscaled, or when
 * the dense region covers too much / too little of the frame to be a crop
 * worth sending.
 */
export async function planDetailRegion(bytes: Buffer): Promise<DetailRegion | null> {
  let meta: sharp.Metadata
  try {
    meta = await sharp(bytes).metadata()
  } catch {
    return null
  }
  const w = meta.width ?? 0
  const h = meta.height ?? 0
  if (w <= 0 || h <= 0) return null
  if (Math.max(w, h) < VISION_DETAIL_MIN_SOURCE_EDGE) return null

  let raw: Buffer
  try {
    raw = await sharp(bytes).greyscale().resize(DETAIL_GRID, DETAIL_GRID, { fit: 'fill' }).raw().toBuffer()
  } catch {
    return null
  }
  if (raw.length < DETAIL_GRID * DETAIL_GRID) return null

  // Local gradient magnitude: |dx| + |dy|. Text edges light this up; sky and
  // flat packaging do not.
  const energy = new Float64Array(DETAIL_GRID * DETAIL_GRID)
  let total = 0
  for (let y = 1; y < DETAIL_GRID; y++) {
    for (let x = 1; x < DETAIL_GRID; x++) {
      const i = y * DETAIL_GRID + x
      const e = Math.abs(raw[i] - raw[i - 1]) + Math.abs(raw[i] - raw[i - DETAIL_GRID])
      energy[i] = e
      total += e
    }
  }
  if (total <= 0) return null

  // Bounding box of the cells that carry the bulk of the detail energy.
  //
  // NOT a fixed top-N: in a photo that is mostly flat packaging, most cells
  // have ZERO energy, and a top-20% cut would drag the box across the whole
  // frame (the zeros sort arbitrarily among themselves). Walking the sorted
  // cells until 85% of the total energy is covered keeps the box on the text.
  const order = Array.from({ length: DETAIL_GRID * DETAIL_GRID }, (_, i) => i).sort(
    (a, b) => energy[b] - energy[a]
  )
  const target = total * 0.85
  let acc = 0
  let x0 = DETAIL_GRID
  let y0 = DETAIL_GRID
  let x1 = -1
  let y1 = -1
  for (let k = 0; k < order.length; k++) {
    const i = order[k]
    const e = energy[i]
    if (e <= 0) break
    const x = i % DETAIL_GRID
    const y = (i / DETAIL_GRID) | 0
    if (x < x0) x0 = x
    if (x > x1) x1 = x
    if (y < y0) y0 = y
    if (y > y1) y1 = y
    acc += e
    if (acc >= target) break
  }
  if (x1 < x0 || y1 < y0) return null

  // Pad the box so the label is not cut at its own edge.
  const padX = Math.max(2, Math.round((x1 - x0 + 1) * 0.15))
  const padY = Math.max(2, Math.round((y1 - y0 + 1) * 0.15))
  const cx0 = Math.max(0, x0 - padX)
  const cy0 = Math.max(0, y0 - padY)
  const cx1 = Math.min(DETAIL_GRID - 1, x1 + padX)
  const cy1 = Math.min(DETAIL_GRID - 1, y1 + padY)

  const left = Math.floor((cx0 / DETAIL_GRID) * w)
  const top = Math.floor((cy0 / DETAIL_GRID) * h)
  const right = Math.ceil(((cx1 + 1) / DETAIL_GRID) * w)
  const bottom = Math.ceil(((cy1 + 1) / DETAIL_GRID) * h)
  const width = Math.min(w - left, right - left)
  const height = Math.min(h - top, bottom - top)
  if (width <= 0 || height <= 0) return null

  // A label is SMALL relative to a product photo — that is the whole point.
  // The floor is only there to reject sensor noise, so it is on absolute size,
  // not on area share. The ceiling rejects "the crop is the whole frame".
  if (width < 128 || height < 128) return null
  const ratio = (width * height) / (w * h)
  if (ratio > 0.9) return null

  return { left, top, width, height }
}

/** Crop in the source's own pixel space, THEN orient — sharp's pipeline
 *  applies rotate before extract, which would invalidate these coordinates. */
async function buildDetailImage(bytes: Buffer, region: DetailRegion): Promise<Buffer | null> {
  try {
    const cropped = await sharp(bytes).extract(region).toBuffer()
    return await sharp(cropped)
      .rotate()
      .resize({ width: VISION_DETAIL_MAX_EDGE, height: VISION_DETAIL_MAX_EDGE, fit: 'inside', withoutEnlargement: true })
      .jpeg({ quality: VISION_JPEG_QUALITY })
      .toBuffer()
  } catch {
    return null
  }
}

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
export async function prepareVisionImage(
  att: Attachment,
  opts: { withDetail?: boolean } = {}
): Promise<PrepareResult> {
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

    // Detail crop — best effort, never fatal. If anything about it fails the
    // turn proceeds exactly as before, with the full frame only.
    let detail: PreparedVisionImage | null = null
    if (opts.withDetail) {
      const region = await planDetailRegion(bytes)
      if (region) {
        const detailBytes = await buildDetailImage(bytes, region)
        if (detailBytes) {
          const detailMeta = await sharp(detailBytes).metadata()
          detail = {
            attachmentId: att.id,
            dataUrl: `data:image/jpeg;base64,${detailBytes.toString('base64')}`,
            width: detailMeta.width ?? 0,
            height: detailMeta.height ?? 0,
            role: 'detail',
          }
        }
      }
    }

    return {
      ok: true,
      image: {
        attachmentId: att.id,
        dataUrl: `data:image/jpeg;base64,${normalized.toString('base64')}`,
        width: outMeta.width ?? 0,
        height: outMeta.height ?? 0,
        role: 'full',
      },
      detail,
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
