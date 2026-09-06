import { NextRequest, NextResponse } from 'next/server'
import { db } from '@/lib/db'
import { conversationToJson } from '@/lib/serializers'

export const dynamic = 'force-dynamic'

const DEFAULT_LIMIT = 20
const MAX_LIMIT = 100

function parseLimit(raw: string | null): number {
  if (raw === null) return DEFAULT_LIMIT
  const parsed = Number.parseInt(raw, 10)
  if (Number.isNaN(parsed)) return DEFAULT_LIMIT
  return Math.min(Math.max(parsed, 1), MAX_LIMIT)
}

// GET /api/v1/conversations?limit=20 — newest first (updatedAt desc)
export async function GET(req: NextRequest) {
  const limit = parseLimit(new URL(req.url).searchParams.get('limit'))
  const conversations = await db.conversation.findMany({
    orderBy: { updatedAt: 'desc' },
    take: limit,
  })
  return NextResponse.json({
    items: conversations.map(conversationToJson),
    nextCursor: null,
  })
}

// POST /api/v1/conversations — { title?, modelId? } → 201 Conversation
export async function POST(req: NextRequest) {
  let body: { title?: unknown; modelId?: unknown }
  try {
    body = (await req.json()) as { title?: unknown; modelId?: unknown }
  } catch {
    body = {}
  }

  const title =
    typeof body?.title === 'string' && body.title.trim().length > 0 ? body.title.trim() : undefined
  const modelId =
    typeof body?.modelId === 'string' && body.modelId.trim().length > 0
      ? body.modelId.trim()
      : undefined

  const conversation = await db.conversation.create({
    data: {
      ...(title ? { title } : {}),
      ...(modelId ? { modelId } : {}),
    },
  })

  return NextResponse.json(conversationToJson(conversation), { status: 201 })
}
