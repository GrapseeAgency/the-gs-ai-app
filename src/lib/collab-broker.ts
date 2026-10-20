/**
 * COLLAB BROKER — SQLite-backed shared-state rooms for the `collab` kind.
 *
 * BACKEND: node:sqlite (DatabaseSync) — the standard-library SQLite that
 * ships with Node 22+, so there is no native build step and no new
 * dependency. One file on disk (`COLLAB_DB`, default `.data/collab.db`).
 *
 * WHAT CHANGED VS THE IN-MEMORY PROTOTYPE:
 *   - rooms + counters + todo lists SURVIVE a server restart;
 *   - two server processes (multi-replica deploys) share one room store —
 *     the revision increment and state merge happen inside one SQL
 *     transaction per mutation;
 *   - presence stays TTL-based (10s heartbeat / 30s expiry) but is stored,
 *     so a restart no longer empties the roster until hearts stop.
 *
 * WHAT DID NOT CHANGE (still Tier-2 honesty, still labelled in the widget):
 *   - counters/votes apply COMMUTATIVE INCREMENTS server-side (deltas, not
 *     absolute values) so concurrent taps cannot lose state;
 *   - todo lists are LIST-REPLACE with last-write-wins — concurrent edits to
 *     ONE item can still drop work, and the widget still says so;
 *   - SSE fan-out subscriptions stay in-memory per process: a mutation on
 *     instance A rev-broadcasts to A's subscribers directly; B's subscribers
 *     learn the change on their next snapshot/rev poll of this store.
 *
 * Caps keep a room from becoming a bomb: 256 rooms, 32 clients/room, 16 KB
 * state, 12 items (LIMITS.maxCollabItems), 30-min idle eviction (DELETE).
 */

import { DatabaseSync } from 'node:sqlite'
import { mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'

export type CollabState = Record<string, unknown>
export type CollabPresence = { id: string; name: string; color: string; at: number }

type Sub = (rev: number, state: CollabState, presence: CollabPresence[]) => void

const MAX_ROOMS = 256
const MAX_CLIENTS_PER_ROOM = 32
const MAX_STATE_BYTES = 16 * 1024
const PRESENCE_TTL_MS = 30_000
const ROOM_IDLE_TTL_MS = 30 * 60_000

let db: DatabaseSync | null = null
const subs = new Map<string, Set<Sub>>()

function store(): DatabaseSync {
  if (db) return db
  const file = process.env.COLLAB_DB || join(process.cwd(), '.data', 'collab.db')
  mkdirSync(dirname(file), { recursive: true })
  db = new DatabaseSync(file)
  db.exec('PRAGMA journal_mode = WAL; PRAGMA busy_timeout = 3000;')
  db.exec(`
    CREATE TABLE IF NOT EXISTS collab_rooms (
      slug       TEXT PRIMARY KEY,
      rev        INTEGER NOT NULL DEFAULT 0,
      state_json TEXT NOT NULL DEFAULT '{}',
      touched_at INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS collab_presence (
      room_slug TEXT NOT NULL,
      id        TEXT NOT NULL,
      name      TEXT NOT NULL,
      color     TEXT NOT NULL,
      at        INTEGER NOT NULL,
      PRIMARY KEY (room_slug, id)
    );
  `)
  return db
}

function sweep(now = Date.now()): void {
  const s = store()
  s.prepare('DELETE FROM collab_presence WHERE at <= ?').run(now - PRESENCE_TTL_MS)
  s.prepare('DELETE FROM collab_rooms WHERE touched_at <= ? AND slug NOT IN (SELECT DISTINCT room_slug FROM collab_presence)').run(now - ROOM_IDLE_TTL_MS)
}

function roomRow(slug: string): { rev: number; state: CollabState } {
  const s = store()
  const row = s.prepare('SELECT rev, state_json FROM collab_rooms WHERE slug = ?').get(slug) as { rev: number; state_json: string } | undefined
  if (row) {
    try {
      return { rev: row.rev, state: JSON.parse(row.state_json) as CollabState }
    } catch {
      return { rev: row.rev, state: {} }
    }
  }
  // create-on-touch; evict the oldest idle room when the cap is hit
  if ((s.prepare('SELECT COUNT(*) AS n FROM collab_rooms').get() as { n: number }).n >= MAX_ROOMS) {
    s.prepare('DELETE FROM collab_rooms WHERE slug = (SELECT slug FROM collab_rooms WHERE slug NOT IN (SELECT DISTINCT room_slug FROM collab_presence) ORDER BY touched_at ASC LIMIT 1)').run()
  }
  s.prepare('INSERT OR IGNORE INTO collab_rooms (slug, rev, state_json, touched_at) VALUES (?, 0, ?, ?)').run(slug, '{}', Date.now())
  return { rev: 0, state: {} }
}

function livePresence(slug: string): CollabPresence[] {
  const now = Date.now()
  const rows = store().prepare('SELECT id, name, color, at FROM collab_presence WHERE room_slug = ? AND at > ?').all(slug, now - PRESENCE_TTL_MS) as Array<{ id: string; name: string; color: string; at: number }>
  return rows.map((r) => ({ id: r.id, name: r.name, color: r.color, at: r.at }))
}

function bytesOf(v: unknown): number {
  try {
    return JSON.stringify(v).length
  } catch {
    return MAX_STATE_BYTES + 1
  }
}

function notify(slug: string, rev: number, state: CollabState, presence: CollabPresence[]): void {
  for (const sub of subs.get(slug) ?? []) {
    try {
      sub(rev, state, presence)
    } catch {
      /* a dead subscriber is removed by its own response loop */
    }
  }
}

/** Apply one payload to a room; returns the new revision or null if dropped. */
export function applyCollab(
  slug: string,
  patch: { delta?: Record<string, number>; set?: CollabState; heartbeat?: CollabPresence },
): { rev: number; state: CollabState; presence: CollabPresence[] } | null {
  const s = store()
  sweep()
  const now = Date.now()
  let changed = false

  // hand-rolled BEGIN IMMEDIATE — database.transaction() is not in every
  // node:sqlite build this repo's types target
  s.exec('BEGIN IMMEDIATE')
  let result: { rev: number; state: CollabState } | null = null
  try {
    const r = roomRow(slug)
    s.prepare('UPDATE collab_rooms SET touched_at = ? WHERE slug = ?').run(now, slug)
    let next: CollabState = { ...r.state }

    if (patch.heartbeat) {
      const h = patch.heartbeat
      if (typeof h.id === 'string' && h.id) {
        const count = (s.prepare('SELECT COUNT(*) AS n FROM collab_presence WHERE room_slug = ?').get(slug) as { n: number }).n
        const known = !!s.prepare('SELECT id FROM collab_presence WHERE room_slug = ? AND id = ?').get(slug, h.id)
        if (known || count < MAX_CLIENTS_PER_ROOM) {
          s.prepare(
            'INSERT INTO collab_presence (room_slug, id, name, color, at) VALUES (?, ?, ?, ?, ?) ON CONFLICT (room_slug, id) DO UPDATE SET name = excluded.name, color = excluded.color, at = excluded.at',
          ).run(slug, h.id, String(h.name ?? 'guest').slice(0, 24), /^#[0-9a-f]{6}$/i.test(String(h.color)) ? String(h.color) : '#4C6FFF', now)
          changed = true
        }
      }
    }
    if (patch.delta) {
      for (const [k, v] of Object.entries(patch.delta)) {
        if (typeof v !== 'number' || !Number.isFinite(v)) continue
        const cur = typeof next[k] === 'number' ? (next[k] as number) : 0
        next[k] = cur + v
      }
      changed = true
    }
    if (patch.set && bytesOf(patch.set) <= MAX_STATE_BYTES) {
      next = { ...next, ...patch.set }
      changed = true
    }
    if (!changed) {
      s.exec('COMMIT')
      return null
    }

    const rev = r.rev + 1
    s.prepare('UPDATE collab_rooms SET rev = ?, state_json = ? WHERE slug = ?').run(rev, JSON.stringify(next), slug)
    result = { rev, state: next }
    s.exec('COMMIT')
  } catch {
    try {
      s.exec('ROLLBACK')
    } catch {
      /* connection-level failure; the WAL recovers */
    }
    throw new Error('collab: mutation failed')
  }

  if (!result) return null
  const presence = livePresence(slug)
  notify(slug, result.rev, result.state, presence)
  return { ...result, presence }
}

export function joinCollab(
  slug: string,
  who: CollabPresence,
): { rev: number; state: CollabState; presence: CollabPresence[]; subscribe: (fn: Sub) => () => void } {
  const s = store()
  sweep()
  s.prepare('UPDATE collab_rooms SET touched_at = ? WHERE slug = ?').run(Date.now(), slug)
  const known = !!s.prepare('SELECT id FROM collab_presence WHERE room_slug = ? AND id = ?').get(slug, who.id)
  const count = (s.prepare('SELECT COUNT(*) AS n FROM collab_presence WHERE room_slug = ?').get(slug) as { n: number }).n
  if (known || count < MAX_CLIENTS_PER_ROOM) {
    s.prepare(
      'INSERT INTO collab_presence (room_slug, id, name, color, at) VALUES (?, ?, ?, ?, ?) ON CONFLICT (room_slug, id) DO UPDATE SET name = excluded.name, color = excluded.color, at = excluded.at',
    ).run(slug, who.id, String(who.name ?? 'guest').slice(0, 24), /^#[0-9a-f]{6}$/i.test(who.color) ? who.color : '#4C6FFF', Date.now())
  }
  const { rev, state } = roomRow(slug)
  const subscribe = (fn: Sub) => {
    let set = subs.get(slug)
    if (!set) {
      set = new Set()
      subs.set(slug, set)
    }
    set.add(fn)
    return () => {
      set!.delete(fn)
      if (set!.size === 0) subs.delete(slug)
    }
  }
  return { rev, state, presence: livePresence(slug), subscribe }
}

export function collabRoomSnapshot(slug: string): { rev: number; state: CollabState; presence: CollabPresence[] } | null {
  const s = store()
  const row = s.prepare('SELECT rev, state_json FROM collab_rooms WHERE slug = ?').get(slug) as { rev: number; state_json: string } | undefined
  if (!row) return null
  let state: CollabState = {}
  try {
    state = JSON.parse(row.state_json) as CollabState
  } catch {
    /* corrupt row reads as empty */
  }
  return { rev: row.rev, state, presence: livePresence(slug) }
}
