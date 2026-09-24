/**
 * STREAM BROKER — the transport that decouples turn execution from the HTTP
 * connection (ADR-117 detached execution).
 *
 * BACKEND NOTE (commit-level contract): Redis is NOT available in this
 * sandbox. The broker is implemented on SQLite (Prisma) with the SAME
 * protocol semantics as Redis Streams; the interface below is the swap
 * point — a redis implementation replaces the function bodies only:
 *
 *   Redis Streams                 this module (SQLite)
 *   ─────────────────────────     ────────────────────────────────────
 *   XADD chat:run:{id} * d ...    INSERT INTO stream_chunks (payload)
 *   entry id <ms>-<seq>           entryId "<ms>-<seq>" (ms + autoincrement)
 *   XREAD / XRANGE cursor         readAfter(streamKey, cursor)
 *   MAXLEN ~ N                    trimStream (bounded append)
 *   EXPIRE key ttl NX             expiresAt on every entry (+ refreshTtl)
 *   XADD {"end":"1","status":s}   publishEnd terminal marker
 *
 * Transport-level only — invisible to the SSE contract. Chunks ride as
 * {"d": <chunk JSON>}; the terminal entry is {"end":"1","status":s} with
 * s ∈ completed | error | killed | cancelled.
 */

import { db } from '@/lib/db'

export type BrokerChunk = { event: string; data: string }
export type BrokerTerminalStatus = 'completed' | 'error' | 'killed' | 'cancelled'
export type BrokerEntry = { entryId: string; payload: string }

export const STREAM_KEY_PREFIX = 'chat:run:'
export const streamKeyFor = (streamId: string): string => `${STREAM_KEY_PREFIX}${streamId}`

/** MAXLEN ~ N — bounded append (approximate trim, sampled). */
export const STREAM_MAXLEN = 800
/** EXPIRE — safety TTL so a missed cleanup cannot leak streams (86400s default). */
export const STREAM_TTL_SECONDS = 86_400

const publishRetry = 2
const publishRetryBackoffMs = 60

/** Is this error transient (SQLite busy/locked) — safe to retry short? */
function isTransient(e: unknown): boolean {
  const raw = e instanceof Error ? e.message : String(e)
  return /SQLITE_BUSY|SQLITE_LOCKED|database is locked|too many connections|Timed out fetching/i.test(raw)
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms))

async function nextSeq(): Promise<number> {
  // seq comes from the table's autoincrement; we need it BEFORE building the
  // unique entryId, so allocate by inserting a placeholder row and patching.
  // Cheaper and atomic-enough for a single-writer SQLite: use a monotonic
  // counter table-free approach — the (streamKey, entryId) unique index is
  // the real guard; on conflict we retry with a fresh ms.
  return Date.now()
}

/**
 * Publish one data chunk. BEST-EFFORT per the bridge contract: transient
 * errors are retried with short backoff, then the frame is dropped and
 * logged rather than raised — Redis/SQLite availability must never couple
 * to run success (the persisted answer is the recovery path).
 */
export async function publishChunk(streamId: string, chunk: BrokerChunk): Promise<string | null> {
  const key = streamKeyFor(streamId)
  const payload = JSON.stringify({ d: JSON.stringify(chunk) })
  for (let attempt = 0; attempt <= publishRetry; attempt++) {
    try {
      const ms = await nextSeq()
      const created = await db.streamChunk.create({
        data: {
          streamKey: key,
          entryId: `${ms}-0`, // patched below with the real seq
          payload,
          expiresAt: new Date(Date.now() + STREAM_TTL_SECONDS * 1000),
        },
      })
      const entryId = `${new Date(created.createdAt).getTime()}-${created.seq}`
      await db.streamChunk.update({ where: { seq: created.seq }, data: { entryId } })
      void trimAndRefresh(key)
      return entryId
    } catch (e) {
      if (attempt < publishRetry && isTransient(e)) {
        await sleep(publishRetryBackoffMs)
        continue
      }
      console.error(`BROKER-DROP stream=${streamId} kind=chunk error=${e instanceof Error ? e.message : String(e)}`)
      return null
    }
  }
  return null
}

/**
 * Terminal marker — ALWAYS written on every in-process exit path (the
 * producer writes it in its finally/shield). Same best-effort policy but
 * with a deeper retry budget: a lost end marker is the difference between
 * a clean close and a synthetic end.
 */
export async function publishEnd(streamId: string, status: BrokerTerminalStatus): Promise<void> {
  const key = streamKeyFor(streamId)
  const payload = JSON.stringify({ end: '1', status })
  for (let attempt = 0; attempt <= publishRetry + 3; attempt++) {
    try {
      const ms = Date.now()
      const created = await db.streamChunk.create({
        data: {
          streamKey: key,
          entryId: `${ms}-end`,
          payload,
          expiresAt: new Date(Date.now() + STREAM_TTL_SECONDS * 1000),
        },
      })
      await db.streamChunk.update({ where: { seq: created.seq }, data: { entryId: `${ms}-${created.seq}` } })
      return
    } catch (e) {
      if (attempt <= publishRetry + 2 && isTransient(e)) {
        await sleep(publishRetryBackoffMs)
        continue
      }
      console.error(`BROKER-DROP stream=${streamId} kind=end status=${status} error=${e instanceof Error ? e.message : String(e)}`)
      return
    }
  }
}

/** XRANGE from cursor — entries with entryId > cursor, ascending. */
export async function readAfter(streamId: string, cursor: string, limit = 200): Promise<BrokerEntry[]> {
  const key = streamKeyFor(streamId)
  const rows = await db.streamChunk.findMany({
    where: { streamKey: key, entryId: { gt: cursor } },
    orderBy: { seq: 'asc' },
    take: limit,
  })
  return rows.map((r) => ({ entryId: r.entryId, payload: r.payload }))
}

/** Parse a stored payload into a chunk or the terminal marker. */
export function parseEntry(payload: string): { chunk?: BrokerChunk; end?: { status: BrokerTerminalStatus } } {
  try {
    const obj = JSON.parse(payload) as { d?: string; end?: string; status?: string }
    if (obj.end === '1' && typeof obj.status === 'string') {
      return { end: { status: obj.status as BrokerTerminalStatus } }
    }
    if (typeof obj.d === 'string') return { chunk: JSON.parse(obj.d) as BrokerChunk }
  } catch {
    // corrupt frame — skip (transport-level, never breaks the SSE contract)
  }
  return {}
}

/** The last terminal marker on the stream, if any (terminal short-circuit probe). */
export async function terminalOf(streamId: string): Promise<{ status: BrokerTerminalStatus; entryId: string } | null> {
  const key = streamKeyFor(streamId)
  const rows = await db.streamChunk.findMany({
    where: { streamKey: key },
    orderBy: { seq: 'desc' },
    take: 25,
  })
  for (const r of rows) {
    const parsed = parseEntry(r.payload)
    if (parsed.end) return { status: parsed.end.status, entryId: r.entryId }
  }
  return null
}

// ---------------------------------------------------------------------------
// Maintenance (Phase 4 hardening lives here: bounded append + TTL refresh)
// ---------------------------------------------------------------------------

const maintenanceState = new Map<string, { lastRefreshAt: number }>()
const TTL_REFRESH_SAMPLE_MS = 60_000

/**
 * MAXLEN ~ + sampled TTL refresh — runs off the publish hot path, at most
 * once per stream per 60s (refresh_ttl sampled, never per-publish).
 */
async function trimAndRefresh(key: string): Promise<void> {
  const now = Date.now()
  const st = maintenanceState.get(key)
  if (st && now - st.lastRefreshAt < TTL_REFRESH_SAMPLE_MS) return
  maintenanceState.set(key, { lastRefreshAt: now })
  try {
    // bounded append: keep only the most recent STREAM_MAXLEN entries
    const head = await db.streamChunk.findMany({
      where: { streamKey: key },
      orderBy: { seq: 'desc' },
      skip: STREAM_MAXLEN,
      take: 200,
      select: { seq: true },
    })
    if (head.length > 0) {
      await db.streamChunk.deleteMany({ where: { streamKey: key, seq: { lte: head[head.length - 1].seq } } })
    }
    // EXPIRE NX semantics: push each entry's TTL forward (the key lives as
    // long as its newest entry; safety TTL from first entry is already set).
    await db.streamChunk.updateMany({
      where: { streamKey: key, expiresAt: { lt: new Date(now + STREAM_TTL_SECONDS * 1000) } },
      data: { expiresAt: new Date(now + STREAM_TTL_SECONDS * 1000) },
    })
  } catch {
    // maintenance is best-effort — never breaks publish
  }
}

/** Janitor: physically drop expired entries (lazy expiry — SQLite has no daemon). */
export async function sweepExpired(): Promise<number> {
  try {
    const res = await db.streamChunk.deleteMany({ where: { expiresAt: { lt: new Date() } } })
    return res.count
  } catch {
    return 0
  }
}

/**
 * TTL keeper for SPARSE long-idle runs (Phase 4): refreshes the key's TTL
 * independently of the event rate — a run that thinks for minutes must not
 * have its stream age out underneath it. Started by the producer when the
 * run begins; cancelled when the run goes terminal. Returns the cancel fn.
 */
export function startStreamTtlKeeper(streamId: string, intervalMs = 60_000): () => void {
  const key = streamKeyFor(streamId)
  const timer = setInterval(() => {
    void db.streamChunk
      .updateMany({
        where: { streamKey: key },
        data: { expiresAt: new Date(Date.now() + STREAM_TTL_SECONDS * 1000) },
      })
      .catch(() => undefined)
  }, intervalMs)
  return () => clearInterval(timer)
}
