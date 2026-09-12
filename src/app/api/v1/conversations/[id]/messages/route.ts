import { NextRequest, NextResponse } from 'next/server'
import { db } from '@/lib/db'
import { messageToJson } from '@/lib/serializers'
import { SYSTEM_PROMPT, streamChat, completeChat } from '@/lib/ai'
import { clientKey, rateLimit } from '@/lib/rate-limit'
import { MAX_ATTACHMENTS_PER_MESSAGE } from '@/lib/attachments'

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
    include: { attachments: { orderBy: { createdAt: 'asc' } } },
  })
  return NextResponse.json({ items: messages.map(messageToJson) })
}

// POST /api/v1/conversations/:id/messages — { content, stream?, modelId? }
export async function POST(req: NextRequest, { params }: RouteContext) {
  const { id } = await params

  // Guardrail: 20 messages / minute / client (in-memory; Redis at scale).
  const limit = rateLimit(clientKey(req, `msgs:${id}`), 20, 60_000)
  if (!limit.allowed) {
    return NextResponse.json(
      { code: 'rate_limited', message: 'Too many messages — slow down a little.' },
      { status: 429, headers: { 'Retry-After': String(limit.retryAfterSec) } }
    )
  }

  let body: { content?: unknown; stream?: unknown; modelId?: unknown; attachments?: unknown }
  try {
    body = (await req.json()) as { content?: unknown; stream?: unknown; modelId?: unknown; attachments?: unknown }
  } catch {
    return NextResponse.json(
      { code: 'bad_request', message: 'Request body must be valid JSON' },
      { status: 400 }
    )
  }

  // PHASE 5: optional attachment ids (uploaded via POST /api/v1/uploads first).
  const rawAttachments = body?.attachments
  const attachmentIds = Array.isArray(rawAttachments)
    ? rawAttachments.filter((v): v is string => typeof v === 'string' && v.trim().length > 0)
    : []
  if (Array.isArray(rawAttachments) && attachmentIds.length !== rawAttachments.length) {
    return NextResponse.json(
      { code: 'bad_request', message: 'attachments must be an array of attachment ids' },
      { status: 400 }
    )
  }
  if (attachmentIds.length > MAX_ATTACHMENTS_PER_MESSAGE) {
    return NextResponse.json(
      { code: 'bad_request', message: `At most ${MAX_ATTACHMENTS_PER_MESSAGE} attachments per message` },
      { status: 400 }
    )
  }

  const content = typeof body?.content === 'string' ? body.content : ''
  const attachmentsProvided = attachmentIds.length > 0
  if (content.trim().length === 0 && !attachmentsProvided) {
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

  // Validate every attachment up-front: must exist, belong to this
  // conversation (or be unbound), and not already be attached to a message.
  let claimedAttachments: Awaited<ReturnType<typeof db.attachment.findMany>> = []
  if (attachmentsProvided) {
    claimedAttachments = await db.attachment.findMany({ where: { id: { in: attachmentIds } } })
    if (claimedAttachments.length !== attachmentIds.length) {
      return NextResponse.json(
        { code: 'invalid_attachments', message: 'One or more attachments do not exist' },
        { status: 400 }
      )
    }
    const foreign = claimedAttachments.find(
      (a) => (a.conversationId !== null && a.conversationId !== id) || a.messageId !== null
    )
    if (foreign) {
      return NextResponse.json(
        { code: 'invalid_attachments', message: 'One or more attachments are already in use' },
        { status: 400 }
      )
    }
  }

  const conversation = await db.conversation.findUnique({
    where: { id },
    include: { assistant: true },
  })
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
  if (attachmentsProvided) {
    await db.attachment.updateMany({
      where: { id: { in: attachmentIds } },
      data: { messageId: userMessage.id, conversationId: id },
    })
  }
  if (shouldAutoTitle || requestedModelId) {
    await db.conversation.update({
      where: { id },
      data: {
        ...(shouldAutoTitle ? { title: content.slice(0, 40) } : {}),
        ...(requestedModelId ? { modelId: requestedModelId } : {}),
      },
    })
  }

  // Assistant persona: when the conversation is linked to an assistant, its
  // instructions become the primary system context (platform prompt stays as
  // a base below it).
  const systemPrompt = conversation.assistant?.instructions
    ? `${conversation.assistant.instructions}\n\n(Platform persona base: ${SYSTEM_PROMPT})`
    : SYSTEM_PROMPT

  // Build model context: system prompt + last 20 prior messages + the new user message.
  const recentDesc = await db.message.findMany({
    where: { conversationId: id, id: { not: userMessage.id } },
    orderBy: { createdAt: 'desc' },
    take: HISTORY_LIMIT,
  })
  const history = recentDesc.reverse()
  const modelMessages = [
    { role: 'system', content: systemPrompt },
    ...history.map((m) => ({ role: m.role.toLowerCase(), content: m.content })),
    // HONEST MULTIMODAL CONTRACT: attachment bytes are never fed to the model.
    // Until a real vision/document pipeline exists (Phase 5 deliverable O), the
    // model is told — truthfully — that files were sent but are not readable.
    attachmentsProvided
      ? {
          role: 'user',
          content:
            `${content}${content.length > 0 ? '\n\n' : ''}[The user attached ${claimedAttachments
              .map((a) => a.displayName)
              .join(', ')}. Attachment contents are not readable by the assistant yet.]`,
        }
      : { role: 'user', content },
  ]

  // Catalogue telemetry — count a use when this is the first message.
  if (conversation.assistantId && history.length === 0) {
    db.assistant
      .update({ where: { id: conversation.assistantId }, data: { uses: { increment: 1 } } })
      .catch(() => undefined) // telemetry must never break the chat path
  }

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
