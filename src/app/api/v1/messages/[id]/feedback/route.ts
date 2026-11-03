import { NextRequest, NextResponse } from 'next/server'
import { db } from '@/lib/db'

export const dynamic = 'force-dynamic'

type RouteContext = { params: Promise<{ id: string }> }

/**
 * PATCH /api/v1/messages/:id/feedback — the reader's verdict on one assistant
 * turn: `{ value: "up" | "down" | "" }` ("" withdraws it).
 *
 * Why it exists: a thumbs-down that only paints a colour on the device is
 * decoration. The verdict is written onto the message row, and the next turn
 * in that conversation quotes the most recent verdict into the model's
 * context (see `feedbackNoteFor` in the turn executor). That is the honest
 * version of "the model learns from your feedback": no training happens, the
 * model simply gets told what you said about its last answer, immediately.
 *
 * Only a real assistant turn accepts a verdict — marking a user turn or a
 * missing id is a 404/400 rather than a silent no-op, because the client
 * already stored the value optimistically and a false 200 would hide the
 * mismatch forever.
 */
export async function PATCH(req: NextRequest, { params }: RouteContext) {
  const { id } = await params

  let body: { value?: unknown }
  try {
    body = (await req.json()) as typeof body
  } catch {
    return NextResponse.json(
      { code: 'bad_request', message: 'Request body must be valid JSON' },
      { status: 400 }
    )
  }

  const raw = typeof body.value === 'string' ? body.value.trim().toLowerCase() : null
  if (raw === null) {
    return NextResponse.json(
      { code: 'bad_request', message: 'value must be "up", "down" or ""' },
      { status: 400 }
    )
  }
  if (raw !== '' && raw !== 'up' && raw !== 'down') {
    return NextResponse.json(
      { code: 'bad_request', message: 'value must be "up", "down" or ""' },
      { status: 400 }
    )
  }

  const message = await db.message.findUnique({ where: { id } })
  if (!message) {
    return NextResponse.json({ code: 'not_found', message: 'Message not found' }, { status: 404 })
  }
  if (message.role !== 'assistant') {
    return NextResponse.json(
      { code: 'bad_request', message: 'Only an assistant turn carries a verdict' },
      { status: 400 }
    )
  }

  const updated = await db.message.update({
    where: { id },
    data: {
      feedback: raw === '' ? null : raw,
      feedbackAt: raw === '' ? null : new Date(),
    },
  })

  return NextResponse.json({ id: updated.id, feedback: updated.feedback })
}

// GET /api/v1/messages/:id/feedback — what the row currently says. Used by the
// diagnostics page and by the client when it needs to reconcile a verdict that
// was taken while offline.
export async function GET(_req: NextRequest, { params }: RouteContext) {
  const { id } = await params
  const message = await db.message.findUnique({
    where: { id },
    select: { id: true, role: true, feedback: true, feedbackAt: true },
  })
  if (!message) {
    return NextResponse.json({ code: 'not_found', message: 'Message not found' }, { status: 404 })
  }
  return NextResponse.json(message)
}
