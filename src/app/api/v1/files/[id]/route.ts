import { NextRequest, NextResponse } from 'next/server'
import { db } from '@/lib/db'
import { readAttachmentFile } from '@/lib/attachments'

export const runtime = 'nodejs'
export const dynamic = 'force-dynamic'

type RouteContext = { params: Promise<{ id: string }> }

/**
 * GET /api/v1/files/:id — serves attachment bytes with their real MIME type.
 * The storage path never appears in any response; ids are the only handle.
 */
export async function GET(_req: NextRequest, { params }: RouteContext) {
  const { id } = await params
  const attachment = await db.attachment.findUnique({ where: { id } })
  if (!attachment) {
    return NextResponse.json({ code: 'not_found', message: 'Attachment not found' }, { status: 404 })
  }

  try {
    const bytes = await readAttachmentFile(attachment.storagePath)
    const inlineName = encodeURIComponent(attachment.displayName)
    return new Response(new Uint8Array(bytes), {
      headers: {
        'Content-Type': attachment.mimeType,
        'Content-Length': String(bytes.byteLength),
        'Content-Disposition': `inline; filename*=UTF-8''${inlineName}`,
        'Cache-Control': 'private, max-age=31536000, immutable',
      },
    })
  } catch {
    // Record exists but bytes are gone — say so honestly instead of 404-blur.
    return NextResponse.json(
      { code: 'file_missing', message: 'Attachment record exists but its contents are unavailable' },
      { status: 410 }
    )
  }
}
