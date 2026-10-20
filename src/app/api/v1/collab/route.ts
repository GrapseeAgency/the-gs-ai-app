/**
 * /api/v1/collab — the collab widget's shared-state transport.
 *
 *   GET  ?room=<slug>            SSE: snapshot event, then `rev` events on
 *                                every change; heartbeat comments keep the
 *                                connection alive through proxies.
 *   POST {room, delta|set|heartbeat}  apply + broadcast; returns the new
 *                                revision and state.
 *
 * State semantics live in collab-broker.ts (prototype: per-server memory,
 * commutative deltas for counts, LWW list replace, ephemeral presence).
 */

import { NextRequest } from 'next/server'
import { applyCollab, joinCollab, type CollabPresence } from '@/lib/collab-broker'

export const dynamic = 'force-dynamic'

const KEEPALIVE_MS = 15_000
const slugOf = (v: string | null) => (v ?? '').toLowerCase().replace(/[^a-z0-9-]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 48) || 'lobby'

export async function GET(req: NextRequest) {
  const room = slugOf(req.nextUrl.searchParams.get('room'))
  const who: CollabPresence = {
    id: req.nextUrl.searchParams.get('client') || `c${Math.random().toString(36).slice(2, 10)}`,
    name: (req.nextUrl.searchParams.get('name') || 'guest').slice(0, 24),
    color: /^#[0-9a-f]{6}$/i.test(req.nextUrl.searchParams.get('color') ?? '') ? (req.nextUrl.searchParams.get('color') as string) : '#4C6FFF',
    at: Date.now(),
  }

  const enc = new TextEncoder()
  const session = joinCollab(room, who)
  let unsubscribe: (() => void) | null = null
  let keepalive: ReturnType<typeof setInterval> | null = null

  const stream = new ReadableStream<Uint8Array>({
    start(controller) {
      let closed = false
      const write = (event: string, data: unknown): boolean => {
        if (closed) return false
        try {
          controller.enqueue(enc.encode(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`))
          return true
        } catch {
          closed = true
          return false
        }
      }
      const close = () => {
        if (closed) return
        closed = true
        unsubscribe?.()
        if (keepalive) clearInterval(keepalive)
        try {
          controller.close()
        } catch {
          /* already closed by the runtime on client disconnect */
        }
      }

      write('snapshot', { rev: session.rev, state: session.state, presence: session.presence, room })
      unsubscribe = session.subscribe((rev, state, presence) => {
        if (!write('rev', { rev, state, presence })) close()
      })
      req.signal.addEventListener('abort', close)
      keepalive = setInterval(() => {
        if (!write('ping', { t: Date.now() })) close()
      }, KEEPALIVE_MS)
    },
    cancel() {
      unsubscribe?.()
      if (keepalive) clearInterval(keepalive)
    },
  })

  return new Response(stream, {
    headers: {
      'Content-Type': 'text/event-stream; charset=utf-8',
      'Cache-Control': 'no-cache, no-transform',
      Connection: 'keep-alive',
      'X-Accel-Buffering': 'no',
    },
  })
}

export async function POST(req: NextRequest) {
  let body: unknown
  try {
    body = await req.json()
  } catch {
    return Response.json({ error: 'invalid JSON' }, { status: 400 })
  }
  const o = (body ?? {}) as Record<string, unknown>
  const room = slugOf(typeof o.room === 'string' ? o.room : '')
  if (room === 'lobby' && !o.room) return Response.json({ error: 'room required' }, { status: 400 })
  const patch: { delta?: Record<string, number>; set?: Record<string, unknown>; heartbeat?: CollabPresence } = {}
  if (o.delta && typeof o.delta === 'object') {
    const d: Record<string, number> = {}
    for (const [k, v] of Object.entries(o.delta as Record<string, unknown>)) if (typeof v === 'number' && Number.isFinite(v)) d[k.slice(0, 40)] = Math.max(-1000, Math.min(1000, v))
    patch.delta = d
  }
  if (o.set && typeof o.set === 'object') patch.set = o.set as Record<string, unknown>
  if (o.heartbeat && typeof o.heartbeat === 'object') {
    const h = o.heartbeat as Record<string, unknown>
    patch.heartbeat = {
      id: String(h.id ?? '').slice(0, 40),
      name: String(h.name ?? 'guest').slice(0, 24),
      color: /^#[0-9a-f]{6}$/i.test(String(h.color)) ? String(h.color) : '#4C6FFF',
      at: Date.now(),
    }
  }
  if (!patch.delta && !patch.set && !patch.heartbeat) return Response.json({ error: 'nothing to apply' }, { status: 400 })
  const result = applyCollab(room, patch)
  if (!result) return Response.json({ error: 'room at capacity or nothing changed' }, { status: 409 })
  return Response.json({ room, ...result })
}
