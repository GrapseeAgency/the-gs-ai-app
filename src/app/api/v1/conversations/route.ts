import { NextRequest, NextResponse } from 'next/server'
import { db } from '@/lib/db'
import { conversationToJson } from '@/lib/serializers'
import { clientKey, rateLimit } from '@/lib/rate-limit'

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
    include: { assistant: { select: { id: true, name: true } } },
  })
  return NextResponse.json({
    items: conversations.map((c) => ({
      ...conversationToJson(c),
      ...(c.assistant ? { assistantName: c.assistant.name } : {}),
    })),
    nextCursor: null,
  })
}

// POST /api/v1/conversations — { title?, modelId?, assistantId? } → 201 Conversation
export async function POST(req: NextRequest) {
  // Guardrail: 30 creations / minute / client (in-memory; Redis at scale).
  const limit = rateLimit(clientKey(req, 'convs:create'), 30, 60_000)
  if (!limit.allowed) {
    return NextResponse.json(
      { code: 'rate_limited', message: 'Too many conversations — slow down a little.' },
      { status: 429, headers: { 'Retry-After': String(limit.retryAfterSec) } }
    )
  }

  let body: { title?: unknown; modelId?: unknown; assistantId?: unknown }
  try {
    body = (await req.json()) as { title?: unknown; modelId?: unknown; assistantId?: unknown }
  } catch {
    body = {}
  }

  const title =
    typeof body?.title === 'string' && body.title.trim().length > 0 ? body.title.trim() : undefined
  const modelId =
    typeof body?.modelId === 'string' && body.modelId.trim().length > 0
      ? body.modelId.trim()
      : undefined
  const assistantId =
    typeof body?.assistantId === 'string' && body.assistantId.trim().length > 0
      ? body.assistantId.trim()
      : undefined

  if (assistantId) {
    const assistant = await db.assistant.findUnique({ where: { id: assistantId } })
    if (!assistant) {
      return NextResponse.json(
        { code: 'bad_request', message: 'assistantId does not reference a known assistant' },
        { status: 400 }
      )
    }
  }

  const conversation = await db.conversation.create({
    data: {
      ...(title ? { title } : {}),
      ...(modelId ? { modelId } : {}),
      ...(assistantId ? { assistantId } : {}),
    },
  })

  return NextResponse.json(conversationToJson(conversation), { status: 201 })
}
