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

function slugify(input: string): string {
  return input
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 60)
}

// GET /api/v1/assistants?category=writing&favourite=true — blueprint catalogue
export async function GET(req: NextRequest) {
  const params = new URL(req.url).searchParams
  const category = params.get('category')
  const favourite = params.get('favourite')

  const assistants = await db.assistant.findMany({
    where: {
      ...(category ? { category } : {}),
      ...(favourite === 'true' ? { favourite: true } : {}),
      ...(favourite === 'false' ? { favourite: false } : {}),
    },
    orderBy: [{ favourite: 'desc' }, { uses: 'desc' }],
  })

  return NextResponse.json({ items: assistants.map(assistantToJson) })
}

// POST /api/v1/assistants — { name, description?, instructions?, category?, starters? }
export async function POST(req: NextRequest) {
  // Guardrail: 10 creations / minute / client.
  const limit = rateLimit(clientKey(req, 'assistants:create'), 10, 60_000)
  if (!limit.allowed) {
    return NextResponse.json(
      { code: 'rate_limited', message: 'Too many assistant creations — slow down.' },
      { status: 429, headers: { 'Retry-After': String(limit.retryAfterSec) } }
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

  const name = typeof body.name === 'string' ? body.name.trim() : ''
  if (!name) {
    return NextResponse.json(
      { code: 'bad_request', message: 'name must be a non-empty string' },
      { status: 400 }
    )
  }
  if (name.length > MAX_NAME) {
    return NextResponse.json(
      { code: 'bad_request', message: `name must not exceed ${MAX_NAME} characters` },
      { status: 400 }
    )
  }

  const description = typeof body.description === 'string' ? body.description.slice(0, MAX_TEXT) : ''
  const instructions = typeof body.instructions === 'string' ? body.instructions.slice(0, MAX_TEXT) : ''
  const category = ALLOWED_CATEGORIES.includes(body.category as never)
    ? (body.category as string)
    : 'general'
  const starters = Array.isArray(body.starters)
    ? body.starters.filter((s): s is string => typeof s === 'string').slice(0, 6)
    : []

  const base = slugify(name) || 'assistant'
  let slug = base
  for (let i = 2; ; i++) {
    const clash = await db.assistant.findUnique({ where: { slug } })
    if (!clash) break
    slug = `${base}-${i}`
  }

  const assistant = await db.assistant.create({
    data: {
      slug,
      name,
      description,
      instructions,
      category,
      starters: JSON.stringify(starters),
      published: body.published === true,
    },
  })

  return NextResponse.json(assistantToJson(assistant), { status: 201 })
}
