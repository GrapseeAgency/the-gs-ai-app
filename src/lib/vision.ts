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

/**
 * Slots held back for the history images when a previous image turn exists.
 *
 * Detail crops are the last thing that should spend the budget: a crop makes
 * the CURRENT photo more legible, but dropping the history images breaks
 * "what about the other one?" — a failure the grounding harness already
 * records ("follow-up retention"). Two slots is one image from each of the
 * two turns VISION_HISTORY_IMAGE_TURNS admits.
 */
export const VISION_HISTORY_IMAGE_RESERVE = 2

/**
 * How many of the current turn's images may spend a second slot on a detail
 * crop.
 *
 * Pure on purpose: this is the one place the request-level image budget is
 * decided, so it can be asserted without a database, a provider or a file.
 * Invariants it must never break:
 *   * `currentImageCount + allowance + reserve <= VISION_MAX_IMAGES_PER_REQUEST`;
 *   * a full frame is never dropped in favour of a crop.
 */
export function historyImageReserve(
  currentImageCount: number,
  hasHistoryImages: boolean
): number {
  if (!hasHistoryImages) return 0
  const frames = Math.min(Math.max(0, Math.floor(currentImageCount)), VISION_MAX_IMAGES_PER_REQUEST)
  // The reserve cannot exceed what is left after the frames — otherwise the
  // three terms sum past the cap (5 frames + 2 reserved = 7 of 6).
  return Math.min(VISION_HISTORY_IMAGE_RESERVE, VISION_MAX_IMAGES_PER_REQUEST - frames)
}

export function detailCropAllowance(
  currentImageCount: number,
  hasHistoryImages: boolean
): number {
  const frames = Math.min(Math.max(0, Math.floor(currentImageCount)), VISION_MAX_IMAGES_PER_REQUEST)
  const reserve = historyImageReserve(currentImageCount, hasHistoryImages)
  // Never more crops than images: one image cannot use two.
  return Math.min(frames, Math.max(0, VISION_MAX_IMAGES_PER_REQUEST - frames - reserve))
}

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

/**
 * Analysis grid for the detail planner. 384 cells over the long edge keeps a
 * 2-3 mm label (≈80 px in a 12 MP photo) at ~8 cells tall — enough for its
 * strokes to register as gradient, while staying cheap (147k samples).
 */
const DETAIL_GRID = 384

/** Candidate window sides, in cells (as a fraction of the grid). */
const DETAIL_WINDOW_SIDES = [48, 64, 96, 128, 192]

/**
 * Where the detail is, in the SOURCE image's own pixel space (no rotation
 * applied — the caller crops in that same space, then orients the crop).
 *
 * Returns null when the image is not being meaningfully downscaled, or when
 * the dense region covers too much of the frame to be a crop worth sending.
 *
 * ALGORITHM — edge DENSITY over sliding windows, not total energy.
 * The first version accumulated gradient energy per 48x48 cell and took the
 * box covering 85% of the total. Measured on a synthetic 4032x3024 "cluttered
 * desk with a small Bengali label", that returned 81% OF THE FRAME: a few long
 * structural edges (table edge, box edges) carry more raw energy than every
 * glyph in the image combined, so the box stretched across everything and the
 * crop arrived at the model at 1.0x — a duplicate of the full frame, i.e. the
 * feature silently did nothing on exactly the case it exists for.
 *
 * Density inverts that: a glyph plate is a small window where a large share of
 * cells contain gradient; a table edge is a thin line diluted across its cell.
 * Concretely, per cell we take the MEAN gradient (not the sum), so a 1 px
 * high-contrast edge inside a cell scores ~contrast/width while text strokes
 * score several times higher, then we pick the densest window and GROW it
 * while the strip being added is itself dense (see the growth notes below).
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

  // Local gradient magnitude |dx| + |dy| at analysis resolution.
  const grad = new Float64Array(DETAIL_GRID * DETAIL_GRID)
  for (let y = 1; y < DETAIL_GRID; y++) {
    for (let x = 1; x < DETAIL_GRID; x++) {
      const i = y * DETAIL_GRID + x
      grad[i] = Math.abs(raw[i] - raw[i - 1]) + Math.abs(raw[i] - raw[i - DETAIL_GRID])
    }
  }

  // Integral image → any window mean in O(1). This is what makes a full sweep
  // over five window sizes affordable inside a request.
  const S = DETAIL_GRID + 1
  const integral = new Float64Array(S * S)
  for (let y = 0; y < DETAIL_GRID; y++) {
    let rowSum = 0
    for (let x = 0; x < DETAIL_GRID; x++) {
      rowSum += grad[y * DETAIL_GRID + x]
      integral[(y + 1) * S + (x + 1)] = integral[y * S + (x + 1)] + rowSum
    }
  }
  const winSum = (x0: number, y0: number, n: number): number =>
    integral[(y0 + n) * S + (x0 + n)] -
    integral[y0 * S + (x0 + n)] -
    integral[(y0 + n) * S + x0] +
    integral[y0 * S + x0]

  // Best window per size (density = mean gradient per cell).
  type Best = { side: number; x: number; y: number; mean: number }
  const perSize: Best[] = []
  for (const side of DETAIL_WINDOW_SIDES) {
    if (side > DETAIL_GRID) continue
    const stride = Math.max(2, Math.floor(side / 4))
    let best: Best = { side, x: 0, y: 0, mean: -1 }
    for (let y = 0; y + side <= DETAIL_GRID; y += stride) {
      for (let x = 0; x + side <= DETAIL_GRID; x += stride) {
        const mean = winSum(x, y, side) / (side * side)
        if (mean > best.mean) best = { side, x, y, mean }
      }
    }
    if (best.mean > 0) perSize.push(best)
  }
  if (perSize.length === 0) return null

  const peak = perSize.reduce((a, b) => Math.max(a, b.mean), 0)
  if (peak <= 0) return null

  // Grow from the densest window instead of picking a fixed size.
  //
  // Two rejected variants, both measured on the 4032x3024 synthetic label:
  //  * "largest window scoring within 85% of the peak" cut a 900 px label in
  //    half — the peak is a 504 px window sitting on ONE END of it, and no
  //    window wide enough to hold the whole label comes within 85%;
  //  * averaging the whole grown box against a fraction of the peak overshot to
  //    197x45 cells (1963..4032 px): each added strip of flat background only
  //    dilutes the average gradually, so the box keeps creeping.
  // So the test is MARGINAL: a strip is accepted when the strip ITSELF is
  // dense, not when the enlarged average still clears a bar. Growth then stops
  // exactly at the plate edge, where the strip turns into background.
  const seed = perSize.reduce((a, b) => (b.mean > a.mean ? b : a))
  let bx0 = seed.x
  let by0 = seed.y
  let bx1 = seed.x + seed.side
  let by1 = seed.y + seed.side
  const step = Math.max(2, Math.round(seed.side / 8))
  const stripFloor = peak * 0.45
  const maxCells = DETAIL_GRID * DETAIL_GRID * 0.5 // matches the area ceiling below

  const sumOf = (x0: number, y0: number, x1: number, y1: number): number =>
    integral[y1 * S + x1] - integral[y0 * S + x1] - integral[y1 * S + x0] + integral[y0 * S + x0]

  const grow = (floor: number, maxIter: number): void => {
    for (let iter = 0; iter < maxIter; iter++) {
      if ((bx1 - bx0) * (by1 - by0) >= maxCells) return
      const options: { x0: number; y0: number; x1: number; y1: number; strip: number }[] = []
      const push = (x0: number, y0: number, x1: number, y1: number, added: number) => {
        if (added <= 0) return
        const strip = (sumOf(x0, y0, x1, y1) - sumOf(bx0, by0, bx1, by1)) / added
        options.push({ x0, y0, x1, y1, strip })
      }
      if (bx1 + step <= DETAIL_GRID) push(bx0, by0, bx1 + step, by1, step * (by1 - by0))
      if (bx0 - step >= 0) push(bx0 - step, by0, bx1, by1, step * (by1 - by0))
      if (by1 + step <= DETAIL_GRID) push(bx0, by0, bx1, by1 + step, step * (bx1 - bx0))
      if (by0 - step >= 0) push(bx0, by0 - step, bx1, by1, step * (bx1 - bx0))
      if (options.length === 0) return
      const best = options.reduce((a, b) => (b.strip > a.strip ? b : a))
      if (best.strip < floor) return
      bx0 = best.x0
      by0 = best.y0
      bx1 = best.x1
      by1 = best.y1
    }
  }

  grow(stripFloor, 64)
  // GRACE PASS. The last glyph of a line is often cut by the plate edge, so the
  // final strip is part text, part background and scores below the strict
  // floor — measured: a 617 px line inside a 360 px plate stopped 64 px short,
  // cropping "500mg" to "500m". Two more steps at a much lower floor recover
  // the tail without letting the box walk into open background.
  grow(stripFloor * 0.45, 2)

  const chosen = { x: bx0, y: by0, sideX: bx1 - bx0, sideY: by1 - by0 }

  // Map the window back to source pixels and pad so the label is not cut at
  // its own edge.
  const cellX = w / DETAIL_GRID
  const cellY = h / DETAIL_GRID
  const padX = Math.max(4, Math.round(chosen.sideX * 0.1))
  const padY = Math.max(4, Math.round(chosen.sideY * 0.1))
  const gx0 = Math.max(0, chosen.x - padX)
  const gy0 = Math.max(0, chosen.y - padY)
  const gx1 = Math.min(DETAIL_GRID, chosen.x + chosen.sideX + padX)
  const gy1 = Math.min(DETAIL_GRID, chosen.y + chosen.sideY + padY)

  const left = Math.floor(gx0 * cellX)
  const top = Math.floor(gy0 * cellY)
  const right = Math.ceil(gx1 * cellX)
  const bottom = Math.ceil(gy1 * cellY)
  const width = Math.min(w - left, right - left)
  const height = Math.min(h - top, bottom - top)
  if (width <= 0 || height <= 0) return null

  // A label is SMALL relative to a product photo — that is the whole point.
  // The floor is only there to reject sensor noise, so it is on absolute size,
  // not on area share.
  if (width < 128 || height < 128) return null

  // The ceiling is a USEFULNESS test, not a tidiness one: the crop is resized to
  // the same 2048 px as the full frame, so its value is the ZOOM it buys.
  // Measured failures of weaker gates: a 39.8%-of-frame crop of a full-page
  // document arrived at 1.0x (a duplicate of the frame, one extra image slot
  // spent for nothing) and so did a 14%-of-frame horizontal band on a gradient
  // sky. Area share alone cannot see that — a wide thin crop is small in area
  // and still gains nothing — so the gate is on the LONG-EDGE ratio, which is
  // exactly the resize factor.
  const ratio = (width * height) / (w * h)
  if (ratio > 0.5) return null
  if (Math.max(width, height) > Math.max(w, h) / 1.25) return null

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
