import { NextRequest, NextResponse } from 'next/server'
import { db } from '@/lib/db'

export const dynamic = 'force-dynamic'

type RouteContext = { params: Promise<{ id: string }> }
type Item = { key: string; label: string }

function cleanItems(raw: unknown): Item[] {
  if (!Array.isArray(raw)) return []
  return raw
    .filter(
      (x): x is Item =>
        typeof x === 'object' &&
        x !== null &&
        typeof (x as Item).key === 'string' &&
        typeof (x as Item).label === 'string' &&
        (x as Item).label.trim().length > 0
    )
    .map((x) => ({ key: x.key.slice(0, 160), label: x.label.trim().slice(0, 200) }))
    .slice(0, 14)
}

function cleanDone(raw: unknown, items: Item[]): string[] {
  if (!Array.isArray(raw)) return []
  const known = new Set(items.map((i) => i.key))
  return Array.from(new Set(raw.filter((x): x is string => typeof x === 'string' && known.has(x)))).slice(0, 14)
}

function toJson(row: {
  fingerprint: string
  title: string
  itemsJson: string
  doneJson: string
  preference: string | null
  updatedAt: Date
}) {
  const parse = (s: string) => {
    try { return JSON.parse(s) as unknown } catch { return [] }
  }
  return {
    fingerprint: row.fingerprint,
    title: row.title,
    items: cleanItems(parse(row.itemsJson)),
    done: Array.isArray(parse(row.doneJson))
      ? (parse(row.doneJson) as unknown[]).filter((x): x is string => typeof x === 'string')
      : [],
    preference: row.preference,
    updatedAt: row.updatedAt.toISOString(),
  }
}

export async function GET(_req: NextRequest, { params }: RouteContext) {
  const { id } = await params
  if (!(await db.conversation.findUnique({ where: { id } }))) {
    return NextResponse.json({ code: 'not_found', message: 'Conversation not found' }, { status: 404 })
  }
  const row = await db.planProgress.findUnique({ where: { conversationId: id } })
  return NextResponse.json({ progress: row ? toJson(row) : null })
}

export async function PUT(req: NextRequest, { params }: RouteContext) {
  const { id } = await params
  if (!(await db.conversation.findUnique({ where: { id } }))) {
    return NextResponse.json({ code: 'not_found', message: 'Conversation not found' }, { status: 404 })
  }
  let body: Record<string, unknown>
  try { body = (await req.json()) as Record<string, unknown> } catch {
    return NextResponse.json({ code: 'bad_request', message: 'Invalid JSON' }, { status: 400 })
  }
  const fingerprint = typeof body.fingerprint === 'string' ? body.fingerprint.slice(0, 160) : ''
  const title = typeof body.title === 'string' ? body.title.trim().slice(0, 200) : ''
  const items = cleanItems(body.items)
  const done = cleanDone(body.done, items)
  const preference = body.preference === 'up' || body.preference === 'down' ? body.preference : null
  if (!fingerprint || items.length === 0) {
    return NextResponse.json({ code: 'bad_request', message: 'fingerprint and at least one item are required' }, { status: 400 })
  }
  const row = await db.planProgress.upsert({
    where: { conversationId: id },
    create: { conversationId: id, fingerprint, title, itemsJson: JSON.stringify(items), doneJson: JSON.stringify(done), preference },
    update: { fingerprint, title, itemsJson: JSON.stringify(items), doneJson: JSON.stringify(done), preference },
  })
  return NextResponse.json({ progress: toJson(row) })
}
