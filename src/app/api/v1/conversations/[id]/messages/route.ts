import { NextRequest, NextResponse } from 'next/server'
import { db } from '@/lib/db'
import { messageToJson } from '@/lib/serializers'
import { SYSTEM_PROMPT, streamChat, completeChat } from '@/lib/ai'

export const dynamic = 'force-dynamic'

type RouteContext = { params: Promise<{ id: string }> }

const HISTORY_LIMIT = 20
const MAX_CONTENT_LENGTH = 32000 // shared-contracts SendMessageInput.maxLength
const DEFAULT_TITLE = 'New chat'

// GET /api/v1/conversations/:id/messages — history, oldest first
export async function GET(_req: NextRequest, { params }: RouteContext) {
  const { id } = await params
  const conversation = await db.conversation.findUnique({ where: { id } })
  if (!conversation) {
    return NextResponse.json({ code: 'not_found', message: 'Conversation not found' }, { status: 404 })
  }
  const messages = await db.message.findMany({
    where: { conversationId: id },
    orderBy: { createdAt: 'asc' },
  })
  return NextResponse.json({ items: messages.map(messageToJson) })
}

// POST /api/v1/conversations/:id/messages — { content, stream?, modelId? }
export async function POST(req: NextRequest, { params }: RouteContext) {
  const { id } = await params

  let body: { content?: unknown; stream?: unknown; modelId?: unknown }
  try {
    body = (await req.json()) as { content?: unknown; stream?: unknown; modelId?: unknown }
  } catch {
    return NextResponse.json(
      { code: 'bad_request', message: 'Request body must be valid JSON' },
      { status: 400 }
    )
  }

  const content = typeof body?.content === 'string' ? body.content : ''
  if (content.trim().length === 0) {
    return NextResponse.json(
      { code: 'bad_request', message: 'content must be a non-empty string' },
      { status: 400 }
    )
  }
  if (content.length > MAX_CONTENT_LENGTH) {
    return NextResponse.json(
      { code: 'bad_request', message: `content must not exceed ${MAX_CONTENT_LENGTH} characters` },
      { status: 400 }
    )
  }

  const conversation = await db.conversation.findUnique({ where: { id } })
  if (!conversation) {
    return NextResponse.json({ code: 'not_found', message: 'Conversation not found' }, { status: 404 })
  }

  const stream = body?.stream === true
  const requestedModelId =
    typeof body?.modelId === 'string' && body.modelId.trim().length > 0
      ? body.modelId.trim()
      : null
  const shouldAutoTitle = conversation.title === DEFAULT_TITLE

  // Persist the user message + apply auto-title / model override.
  const userMessage = await db.message.create({
    data: { conversationId: id, role: 'user', content },
  })
  if (shouldAutoTitle || requestedModelId) {
    await db.conversation.update({
      where: { id },
      data: {
        ...(shouldAutoTitle ? { title: content.slice(0, 40) } : {}),
        ...(requestedModelId ? { modelId: requestedModelId } : {}),
      },
    })
  }

  // Build model context: system prompt + last 20 prior messages + the new user message.
  const recentDesc = await db.message.findMany({
    where: { conversationId: id, id: { not: userMessage.id } },
    orderBy: { createdAt: 'desc' },
    take: HISTORY_LIMIT,
  })
  const history = recentDesc.reverse()
  const modelMessages = [
    { role: 'system', content: SYSTEM_PROMPT },
    ...history.map((m) => ({ role: m.role.toLowerCase(), content: m.content })),
    { role: 'user', content },
  ]

  // Non-streaming: single JSON reply.
  if (!stream) {
    try {
      const full = await completeChat(modelMessages)
      const assistantMessage = await db.message.create({
        data: { conversationId: id, role: 'assistant', content: full },
      })
      return NextResponse.json(messageToJson(assistantMessage))
    } catch (e) {
      return NextResponse.json(
        { code: 'upstream_error', message: e instanceof Error ? e.message : String(e) },
        { status: 502 }
      )
    }
  }

  // Streaming: text/event-stream — `delta` events, then a final `done` (or `error`).
  return new Response(
    new ReadableStream({
      async start(controller) {
        const enc = new TextEncoder()
        const send = (event: string, data: string) =>
          controller.enqueue(enc.encode(`data: ${JSON.stringify({ event, data })}\n\n`))
        try {
          const full = await streamChat(modelMessages, (d) => send('delta', d))
          const saved = await db.message.create({
            data: { conversationId: id, role: 'assistant', content: full },
          })
          send('done', JSON.stringify(messageToJson(saved)))
          controller.close()
        } catch (e) {
          send('error', String(e instanceof Error ? e.message : e))
          controller.close()
        }
      },
    }),
    {
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache, no-transform',
        Connection: 'keep-alive',
        'X-Accel-Buffering': 'no',
      },
    }
  )
}
