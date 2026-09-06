import { NextRequest, NextResponse } from 'next/server'
import { db } from '@/lib/db'
import { assistantToJson } from '@/lib/serializers'
import { clientKey, rateLimit } from '@/lib/rate-limit'

export const dynamic = 'force-dynamic'

const MAX_NAME = 80
const MAX_TEXT = 8000
const ALLOWED_CATEGORIES = [
  'general',
  'research',
  'writing',
  'coding',
  'productivity',
  'learning',
] as const

// GET /api/v1/assistants/:id — full assistant (with conversation count)
export async function GET(_req: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params
  const assistant = await db.assistant.findUnique({
    where: { id },
    include: { _count: { select: { conversations: true } } },
  })
  if (!assistant) {
    return NextResponse.json(
      { code: 'not_found', message: 'Assistant not found' },
      { status: 404 }
    )
  }
  return NextResponse.json({
    ...assistantToJson(assistant),
    conversationCount: assistant._count.conversations,
  })
}

// PATCH /api/v1/assistants/:id — partial update (favourite, publish, edits)
export async function PATCH(req: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params

  // Guardrail: 30 mutations / minute / client.
  const limit = rateLimit(clientKey(req, 'assistants:patch'), 30, 60_000)
  if (!limit.allowed) {
    return NextResponse.json(
      { code: 'rate_limited', message: 'Too many updates — slow down.' },
      { status: 429, headers: { 'Retry-After': String(limit.retryAfterSec) } }
    )
  }

  const existing = await db.assistant.findUnique({ where: { id } })
  if (!existing) {
    return NextResponse.json(
      { code: 'not_found', message: 'Assistant not found' },
      { status: 404 }
    )
  }

  let body: Record<string, unknown>
  try {
    body = (await req.json()) as Record<string, unknown>
  } catch {
    return NextResponse.json(
      { code: 'bad_request', message: 'Request body must be valid JSON' },
      { status: 400 }
    )
  }

  const data: Record<string, unknown> = {}
  if (typeof body.name === 'string' && body.name.trim()) data.name = body.name.trim().slice(0, MAX_NAME)
  if (typeof body.description === 'string') data.description = body.description.slice(0, MAX_TEXT)
  if (typeof body.instructions === 'string') data.instructions = body.instructions.slice(0, MAX_TEXT)
  if (typeof body.category === 'string' && ALLOWED_CATEGORIES.includes(body.category as never)) {
    data.category = body.category
  }
  if (typeof body.favourite === 'boolean') data.favourite = body.favourite
  if (typeof body.published === 'boolean') data.published = body.published
  if (Array.isArray(body.starters)) {
    data.starters = JSON.stringify(
      body.starters.filter((s): s is string => typeof s === 'string').slice(0, 6)
    )
  }

  if (Object.keys(data).length === 0) {
    return NextResponse.json(
      { code: 'bad_request', message: 'No valid fields to update' },
      { status: 400 }
    )
  }

  const updated = await db.assistant.update({ where: { id }, data })
  return NextResponse.json(assistantToJson(updated))
}

// DELETE /api/v1/assistants/:id — remove (conversations keep running, unlink first)
export async function DELETE(_req: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params
  const existing = await db.assistant.findUnique({ where: { id } })
  if (!existing) {
    return NextResponse.json(
      { code: 'not_found', message: 'Assistant not found' },
      { status: 404 }
    )
  }
  await db.conversation.updateMany({ where: { assistantId: id }, data: { assistantId: null } })
  await db.assistant.delete({ where: { id } })
  return NextResponse.json({ deleted: true, id })
}
