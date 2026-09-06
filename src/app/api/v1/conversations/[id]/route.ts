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
