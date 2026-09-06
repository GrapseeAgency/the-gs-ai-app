import { NextRequest, NextResponse } from 'next/server'
import { db } from '@/lib/db'
import { conversationToJson } from '@/lib/serializers'

export const dynamic = 'force-dynamic'

type RouteContext = { params: Promise<{ id: string }> }

// GET /api/v1/conversations/:id
export async function GET(_req: NextRequest, { params }: RouteContext) {
  const { id } = await params
  const conversation = await db.conversation.findUnique({ where: { id } })
  if (!conversation) {
    return NextResponse.json({ code: 'not_found', message: 'Conversation not found' }, { status: 404 })
  }
  return NextResponse.json(conversationToJson(conversation))
}

// PATCH /api/v1/conversations/:id — { title?, pinned?, archived?, modelId? }
export async function PATCH(req: NextRequest, { params }: RouteContext) {
  const { id } = await params
  const existing = await db.conversation.findUnique({ where: { id } })
  if (!existing) {
    return NextResponse.json({ code: 'not_found', message: 'Conversation not found' }, { status: 404 })
  }

  let body: { title?: unknown; pinned?: unknown; archived?: unknown; modelId?: unknown }
  try {
    body = (await req.json()) as typeof body
  } catch {
    return NextResponse.json(
      { code: 'bad_request', message: 'Request body must be valid JSON' },
      { status: 400 }
    )
  }

  const data: Record<string, unknown> = {}
  if (typeof body.title === 'string' && body.title.trim()) data.title = body.title.trim().slice(0, 120)
  if (typeof body.pinned === 'boolean') data.pinned = body.pinned
  if (typeof body.archived === 'boolean') data.archived = body.archived
  if (typeof body.modelId === 'string' && body.modelId.trim()) data.modelId = body.modelId.trim()

  if (Object.keys(data).length === 0) {
    return NextResponse.json(
      { code: 'bad_request', message: 'No valid fields to update' },
      { status: 400 }
    )
  }

  const updated = await db.conversation.update({ where: { id }, data })
  return NextResponse.json(conversationToJson(updated))
}

// DELETE /api/v1/conversations/:id — messages cascade via Prisma onDelete: Cascade
export async function DELETE(_req: NextRequest, { params }: RouteContext) {
  const { id } = await params
  const conversation = await db.conversation.findUnique({ where: { id } })
  if (!conversation) {
    return NextResponse.json({ code: 'not_found', message: 'Conversation not found' }, { status: 404 })
  }
  await db.conversation.delete({ where: { id } })
  return new NextResponse(null, { status: 204 })
}
