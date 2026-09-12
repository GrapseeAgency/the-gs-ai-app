import { NextRequest, NextResponse } from 'next/server'
import { db } from '@/lib/db'
import { clientKey, rateLimit } from '@/lib/rate-limit'
import {
  ALLOWED_MIME_TYPES,
  MAX_ATTACHMENT_BYTES,
  contentHash,
  kindForMime,
  sniffMatchesMime,
  writeAttachmentFile,
  attachmentToJson,
} from '@/lib/attachments'

export const runtime = 'nodejs'
export const dynamic = 'force-dynamic'

const SNIFF_HEAD_BYTES = 16

/**
 * POST /api/v1/uploads — multipart/form-data
 *   file            (required) the attachment bytes
 *   conversationId  (optional) pre-bind to a conversation
 *
 * Real transport, real storage, real records. No simulated progress anywhere:
 * the HTTP round-trip IS the upload lifecycle the clients surface.
 */
export async function POST(req: NextRequest) {
  const limit = rateLimit(clientKey(req, 'uploads'), 30, 60_000)
  if (!limit.allowed) {
    return NextResponse.json(
      { code: 'rate_limited', message: 'Too many uploads — slow down a little.' },
      { status: 429, headers: { 'Retry-After': String(limit.retryAfterSec) } }
    )
  }

  let form: FormData
  try {
    form = await req.formData()
  } catch {
    return NextResponse.json(
      { code: 'bad_request', message: 'Request must be multipart/form-data' },
      { status: 400 }
    )
  }

  const file = form.get('file')
  if (!(file instanceof File) || file.size === 0) {
    return NextResponse.json(
      { code: 'bad_request', message: 'A non-empty "file" field is required' },
      { status: 400 }
    )
  }
  if (file.size > MAX_ATTACHMENT_BYTES) {
    return NextResponse.json(
      { code: 'too_large', message: `Attachment exceeds the ${Math.round(MAX_ATTACHMENT_BYTES / (1024 * 1024))} MB limit` },
      { status: 413 }
    )
  }

  const mimeType = (file.type || 'application/octet-stream').toLowerCase()
  const kind = kindForMime(mimeType)
  if (!ALLOWED_MIME_TYPES.has(mimeType) || !kind) {
    return NextResponse.json(
      { code: 'unsupported_media_type', message: `Files of type ${mimeType} are not accepted yet` },
      { status: 415 }
    )
  }

  // Optional conversation pre-bind — must exist if provided.
  const rawConversationId = form.get('conversationId')
  let conversationId: string | null = null
  if (typeof rawConversationId === 'string' && rawConversationId.trim().length > 0) {
    const conversation = await db.conversation.findUnique({ where: { id: rawConversationId.trim() } })
    if (!conversation) {
      return NextResponse.json({ code: 'not_found', message: 'Conversation not found' }, { status: 404 })
    }
    conversationId = conversation.id
  }

  // Read once — the size gate above ran on the declared size; these bytes are
  // the truth. Re-check so a lying Content-Length cannot bypass the cap.
  const bytes = new Uint8Array(await file.arrayBuffer())
  if (bytes.byteLength === 0 || bytes.byteLength > MAX_ATTACHMENT_BYTES) {
    return NextResponse.json(
      { code: 'too_large', message: `Attachment exceeds the ${Math.round(MAX_ATTACHMENT_BYTES / (1024 * 1024))} MB limit` },
      { status: 413 }
    )
  }
  if (!sniffMatchesMime(bytes.slice(0, SNIFF_HEAD_BYTES), mimeType)) {
    return NextResponse.json(
      { code: 'unsupported_media_type', message: 'File contents do not match the declared type' },
      { status: 415 }
    )
  }

  const displayNameField = form.get('displayName')
  const displayName =
    typeof displayNameField === 'string' && displayNameField.trim().length > 0
      ? displayNameField.trim()
      : file.name || 'attachment'

  try {
    const created = await db.$transaction(async (tx) => {
      const attachment = await tx.attachment.create({
        data: {
          kind,
          displayName: displayName.slice(0, 200),
          mimeType,
          byteSize: bytes.byteLength,
          storagePath: 'pending', // finalized below once the row id exists
          conversationId,
        },
      })
      const storagePath = await writeAttachmentFile(attachment.id, displayName, bytes)
      return tx.attachment.update({ where: { id: attachment.id }, data: { storagePath } })
    })

    // Structured log line — fingerprint only, never file contents.
    console.log(
      `[uploads] stored attachment=${created.id} kind=${created.kind} bytes=${created.byteSize} hash=${contentHash(bytes)}`
    )
    return NextResponse.json({ attachment: attachmentToJson(created) }, { status: 201 })
  } catch (e) {
    console.error('[uploads] storage failure', e)
    return NextResponse.json(
      { code: 'storage_error', message: 'Could not store the attachment — try again' },
      { status: 500 }
    )
  }
}
