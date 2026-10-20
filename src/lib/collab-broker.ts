/**
 * COLLAB BROKER — in-memory shared-state rooms for the `collab` widget kind.
 *
 * HONEST SCOPE (Tier 2, labelled in every widget): this is a PROTOTYPE.
 *   - rooms live in this process's memory: restart wipes them, a second
 *     server instance does not share them (the same caveat the stream
 *     broker states for its SQLite backend — swap point, same interface);
 *   - counters and votes apply COMMUTATIVE INCREMENTS server-side (deltas,
 *     not absolute values, so concurrent taps cannot lose state);
 *   - todo lists are LIST-REPLACE with last-write-wins — the semantics
 *     Notion ships for small blocks; concurrent edits to ONE item can drop
 *     work, and the widget says so;
 *   - presence/pointers are ephemeral: a 10s heartbeat, a 30s expiry, no
 *     persistence (Yjs awareness semantics without the CRDT).
 *
 * Caps keep a room from being a memory bomb: 256 rooms, 32 clients/room,
 * 16 KB state, 12 items (LIMITS.maxCollabItems), 30-min idle eviction.
 */

export type CollabState = Record<string, unknown>
export type CollabPresence = { id: string; name: string; color: string; at: number }
type CollabRoom = {
  state: CollabState
  rev: number
  subs: Set<(rev: number, state: CollabState, presence: CollabPresence[]) => void>
  presence: Map<string, CollabPresence>
  touchedAt: number
}

const rooms = new Map<string, CollabRoom>()
const MAX_ROOMS = 256
const MAX_CLIENTS_PER_ROOM = 32
const MAX_STATE_BYTES = 16 * 1024
const PRESENCE_TTL_MS = 30_000
const ROOM_IDLE_TTL_MS = 30 * 60_000

function room(slug: string): CollabRoom {
  sweep()
  let r = rooms.get(slug)
  if (!r) {
    if (rooms.size >= MAX_ROOMS) {
      // evict the oldest idle room — a demo broker, not a database
      const oldest = [...rooms.entries()].sort((a, b) => a[1].touchedAt - b[1].touchedAt)[0]
      if (oldest) rooms.delete(oldest[0])
    }
    r = { state: {}, rev: 0, subs: new Set(), presence: new Map(), touchedAt: Date.now() }
    rooms.set(slug, r)
  }
  return r
}

function sweep(): void {
  const now = Date.now()
  for (const [slug, r] of rooms) {
    if (r.subs.size === 0 && now - r.touchedAt > ROOM_IDLE_TTL_MS) rooms.delete(slug)
    for (const [id, p] of r.presence) if (now - p.at > PRESENCE_TTL_MS) r.presence.delete(id)
  }
}

function livePresence(r: CollabRoom): CollabPresence[] {
  const now = Date.now()
  return [...r.presence.values()].filter((p) => now - p.at <= PRESENCE_TTL_MS)
}

function bytesOf(v: unknown): number {
  try {
    return JSON.stringify(v).length
  } catch {
    return MAX_STATE_BYTES + 1
  }
}

/** Apply one payload to a room; returns the new revision or null if dropped. */
export function applyCollab(
  slug: string,
  patch: { delta?: Record<string, number>; set?: CollabState; heartbeat?: CollabPresence },
): { rev: number; state: CollabState; presence: CollabPresence[] } | null {
  const r = room(slug)
  r.touchedAt = Date.now()
  let changed = false

  if (patch.heartbeat) {
    const h = patch.heartbeat
    if (typeof h.id === 'string' && h.id && r.presence.size < MAX_CLIENTS_PER_ROOM) {
      r.presence.set(h.id, { id: h.id, name: String(h.name ?? 'guest').slice(0, 24), color: /^#[0-9a-f]{6}$/i.test(String(h.color)) ? String(h.color) : '#4C6FFF', at: Date.now() })
      changed = true
    }
  }
  if (patch.delta) {
    const d = patch.delta
    for (const [k, v] of Object.entries(d)) {
      if (typeof v !== 'number' || !Number.isFinite(v)) continue
      const cur = typeof r.state[k] === 'number' ? (r.state[k] as number) : 0
      r.state[k] = cur + v
    }
    changed = true
  }
  if (patch.set) {
    if (bytesOf(patch.set) <= MAX_STATE_BYTES) {
      r.state = { ...r.state, ...patch.set }
      changed = true
    }
  }
  if (!changed) return null
  r.rev += 1
  const presence = livePresence(r)
  for (const sub of r.subs) {
    try {
      sub(r.rev, r.state, presence)
    } catch {
      /* a dead subscriber is removed by its own response loop */
    }
  }
  return { rev: r.rev, state: r.state, presence }
}

export function joinCollab(
  slug: string,
  who: CollabPresence,
): { rev: number; state: CollabState; presence: CollabPresence[]; subscribe: (fn: (rev: number, state: CollabState, presence: CollabPresence[]) => void) => () => void } {
  const r = room(slug)
  r.touchedAt = Date.now()
  if (r.presence.size < MAX_CLIENTS_PER_ROOM) r.presence.set(who.id, { ...who, at: Date.now() })
  const subscribe = (fn: (rev: number, state: CollabState, presence: CollabPresence[]) => void) => {
    r.subs.add(fn)
    return () => r.subs.delete(fn)
  }
  return { rev: r.rev, state: r.state, presence: livePresence(r), subscribe }
}

export function collabRoomSnapshot(slug: string): { rev: number; state: CollabState; presence: CollabPresence[] } | null {
  const r = rooms.get(slug)
  if (!r) return null
  return { rev: r.rev, state: r.state, presence: livePresence(r) }
}
