/**
 * EVALUATION INFRASTRUCTURE — LAYER 5 PRODUCTION MONITOR (Phase 5).
 *
 * Closed the loop the audit demanded: the SAME deterministic grading that
 * gates PRs (Layer 4) now samples LIVE traffic.
 *
 *   hourly pass:
 *     1. sample 5% of the turns recorded in `turns` in the trailing hour
 *        (evenly spaced — stable, no clustering)
 *     2. join each sampled turn to its user message and classify it into a
 *        monitor-gradable bucket (deterministic prompt classifiers mirroring
 *        the eval-suite categories)
 *     3. grade the trace with the Layer-4 assertion semantics
 *     4. write one eval_scores row per (windowStart, category) — passRate is
 *        the honest production number
 *     5. week-over-week: compare against the same category's rate 7 days
 *        earlier; a drop > 5 percentage points sets alerted + alertNote
 *
 * DETERMINISM: classification and grading are pure functions of (prompt,
 * trace row). No network. Provider-throttled turns (finalStatus=error with
 * an errorType) are UNGRADEABLE for the search-expecting buckets — infra
 * must never count as a product failure (case-i lesson) — but contamination
 * guards (ROUTING/ISOLATION) still grade, because a routing decision that
 * fired on casual text is a product fact even when the provider then died.
 */

import { Database } from 'bun:sqlite'

export const MONITOR_CATEGORIES = ['ROUTING', 'CONTEXT_ISOLATION', 'SOURCE_QUALITY', 'FAILURE_HONESTY'] as const
export type MonitorCategory = (typeof MONITOR_CATEGORIES)[number]

export type Bucket = 'NO_SEARCH_EXPECTED' | 'EXPLICIT_SEARCH' | 'ISOLATION_GUARD' | 'SOURCE_QUALITY' | 'HONEST_NO_RESULTS' | 'UNCLASSIFIED'

export interface MonitorTrace {
  requestId: string
  conversationId: string
  messageId: string
  capability: string
  searchExecuted: number
  sourceCount: number
  sourcesRead: number
  sourcesFailed: number
  domains: string
  finalStatus: string
  errorType: string | null
}

export interface TurnVerdict {
  requestId: string
  bucket: Bucket
  category: MonitorCategory | null
  graded: boolean
  passed: boolean | null
  failures: string[]
}

// --- deterministic prompt classifiers (mirror the suite's case families) ---------

const CASUAL_RE =
  /^\s*(hi+|hello+|hey+|yo+|sup|what'?s up|how are you|how r u|thanks?|thank you|thx|ok(ay)?|lol+|lmao|gm|gn|good (morning|afternoon|evening|night)|bye+|cool|nice|great|wow|bruh)\b/i
const ARITHMETIC_RE = /^\s*(what is|what's|whats|calc(ulate)?|how much is)?\s*[\d\s+\-*/().^%]+\s*[?.!]*\s*$/i
const JOKE_RE = /^\s*(tell|say|give) (me )?a joke\b/i
const EXPLICIT_SEARCH_RE =
  /^\s*(search( the web| online| for)?|go to the internet|go online|google|look up|look this up|find (me )?|browse)\b/i
const WEB_MARKERS_RE = /\b(search the web|on the internet|online|latest|today'?s|current|right now|news)\b/i

/** Classify a user prompt into a monitor bucket. Pure function. */
export function classifyPrompt(prompt: string): Bucket {
  const p = prompt.trim()
  if (!p) return 'UNCLASSIFIED'
  if (EXPLICIT_SEARCH_RE.test(p) || WEB_MARKERS_RE.test(p)) return 'EXPLICIT_SEARCH'
  if (CASUAL_RE.test(p) || ARITHMETIC_RE.test(p) || JOKE_RE.test(p)) return 'NO_SEARCH_EXPECTED'
  return 'UNCLASSIFIED'
}

export function parseDomains(raw: string): string[] {
  try {
    const arr = JSON.parse(raw) as unknown
    return Array.isArray(arr) ? (arr as string[]) : []
  } catch {
    return []
  }
}

export function parseCited(raw: string): number[] {
  try {
    const arr = JSON.parse(raw) as unknown
    return Array.isArray(arr) ? (arr as number[]) : []
  } catch {
    return []
  }
}

/**
 * Grade one classified turn. Pure function of (bucket, trace, priorTurnHadSources).
 * Mirrors the Layer-4 assertion semantics for each bucket.
 */
export function gradeTurn(bucket: Bucket, trace: MonitorTrace, priorTurnHadSources: boolean): TurnVerdict {
  const base: TurnVerdict = { requestId: trace.requestId, bucket, category: null, graded: false, passed: null, failures: [] }
  const errored = trace.finalStatus === 'error' || trace.errorType !== null

  switch (bucket) {
    case 'NO_SEARCH_EXPECTED': {
      // ROUTING: casual/arithmetic/joke prompts must not search (audit B1 class).
      base.category = 'ROUTING'
      base.graded = true
      const failures: string[] = []
      if (trace.searchExecuted !== 0) failures.push(`searchExecuted=${trace.searchExecuted} expected 0`)
      if (trace.sourceCount !== 0) failures.push(`sourceCount=${trace.sourceCount} expected 0`)
      base.passed = failures.length === 0
      base.failures = failures
      return base
    }
    case 'ISOLATION_GUARD': {
      // CONTEXT_ISOLATION: casual follow-up in a conversation that already
      // used sources must stay source-free (audit [9] class).
      base.category = 'CONTEXT_ISOLATION'
      base.graded = true
      const failures: string[] = []
      if (trace.searchExecuted !== 0) failures.push(`searchExecuted=${trace.searchExecuted} expected 0`)
      if (trace.sourceCount !== 0) failures.push(`sourceCount=${trace.sourceCount} expected 0 (prior turn had sources)`)
      base.passed = failures.length === 0
      base.failures = failures
      return base
    }
    case 'EXPLICIT_SEARCH': {
      // ROUTING: explicit search prompts must actually search. A throttled
      // turn is infra, not a routing lie — ungradeable, never a pass.
      base.category = 'ROUTING'
      if (errored) {
        base.graded = false
        base.failures = [`turn errored (errorType=${trace.errorType ?? 'unknown'}) — ungradeable`]
        return base
      }
      base.graded = true
      base.passed = trace.searchExecuted === 1
      if (!base.passed) base.failures = [`searchExecuted=${trace.searchExecuted} expected 1 for explicit search`]
      return base
    }
    case 'SOURCE_QUALITY': {
      // SOURCE_QUALITY: grounded turns need >=2 distinct domains and a >=70%
      // read rate (audit [15]/[16] classes). Errored turns ungradeable.
      base.category = 'SOURCE_QUALITY'
      if (errored) {
        base.graded = false
        base.failures = [`turn errored (errorType=${trace.errorType ?? 'unknown'}) — ungradeable`]
        return base
      }
      base.graded = true
      const failures: string[] = []
      const domains = parseDomains(trace.domains)
      if (trace.sourceCount <= 0) failures.push(`sourceCount=${trace.sourceCount} expected >0 for grounded turn`)
      else {
        if (domains.length < 2) failures.push(`domains=${domains.length} expected >=2 (${domains.join(',')})`)
        if (trace.sourcesRead / trace.sourceCount < 0.7) failures.push(`readRate=${(trace.sourcesRead / trace.sourceCount).toFixed(2)} expected >=0.70`)
      }
      base.passed = failures.length === 0
      base.failures = failures
      return base
    }
    case 'HONEST_NO_RESULTS': {
      // FAILURE_HONESTY: a search that found nothing must still complete
      // honestly (finalStatus=done), not error out or fake results (case K).
      base.category = 'FAILURE_HONESTY'
      if (errored) {
        base.graded = false
        base.failures = [`turn errored (errorType=${trace.errorType ?? 'unknown'}) — ungradeable`]
        return base
      }
      base.graded = true
      base.passed = trace.finalStatus === 'done'
      if (!base.passed) base.failures = [`finalStatus=${trace.finalStatus} expected done for zero-result search`]
      return base
    }
    default:
      return base
  }
}

/** Bucket override: casual prompt in a source-bearing conversation → isolation guard. */
export function resolveBucket(prompt: string, priorTurnHadSources: boolean): Bucket {
  const b = classifyPrompt(prompt)
  if (b === 'NO_SEARCH_EXPECTED' && priorTurnHadSources) return 'ISOLATION_GUARD'
  return b
}

// --- sampling + persistence ---------------------------------------------------------

interface SqliteRow {
  requestId: string
  conversationId: string
  messageId: string
  capability: string
  searchExecuted: number
  sourceCount: number
  sourcesRead: number
  sourcesFailed: number
  domains: string
  finalStatus: string
  errorType: string | null
}

function openDb(path: string): Database {
  const db = new Database(path)
  try {
    db.run('PRAGMA busy_timeout = 10000')
  } catch { /* retry logic per-statement below still applies */ }
  return db
}

const sleep = (ms: number): Promise<void> => new Promise((r) => setTimeout(r, ms))

async function withRetry<T>(fn: () => T, attempts = 6): Promise<T> {
  let last: unknown
  for (let i = 0; i < attempts; i++) {
    try {
      return await fn()
    } catch (e) {
      last = e
      const code = (e as { code?: string }).code
      if (code !== 'SQLITE_BUSY') throw e
      await sleep(1_000)
    }
  }
  throw last
}

export interface MonitorPassOptions {
  dbPath: string
  /** Window to grade. Default: the trailing full hour. */
  windowStart?: Date
  windowEnd?: Date
  sampleRate?: number // default 0.05
  maxSample?: number // default 60 — keep passes bounded on spike hours
}

export interface CategoryScore {
  category: MonitorCategory
  sampledTurns: number
  gradedTurns: number
  passCount: number
  failCount: number
  passRate: number
  wowDelta: number | null
  alerted: boolean
  alertNote: string | null
  sampleDetail: { requestId: string; bucket: Bucket; graded: boolean; passed: boolean | null; failures: string[] }[]
}

export interface MonitorPassResult {
  windowStart: string
  windowEnd: string
  turnsInWindow: number
  sampled: number
  graded: number
  passed: number
  failed: number
  scores: CategoryScore[]
}

/** Evenly-spaced deterministic sample of indices [0..n-1], ~rate, capped. */
export function sampleIndices(n: number, rate: number, cap: number): number[] {
  if (n <= 0) return []
  const want = Math.min(cap, Math.max(1, Math.round(n * rate)))
  if (want >= n) return Array.from({ length: n }, (_, i) => i)
  const step = n / want
  const out: number[] = []
  for (let i = 0; i < want; i++) out.push(Math.floor(i * step))
  return out
}

export async function runMonitorPass(opts: MonitorPassOptions): Promise<MonitorPassResult> {
  const rate = opts.sampleRate ?? 0.05
  const cap = opts.maxSample ?? 60
  const end = opts.windowEnd ?? new Date()
  const start = opts.windowStart ?? new Date(end.getTime() - 3_600_000)
  const db = openDb(opts.dbPath)
  try {
    const rows = withRetry(() =>
      db
        .query(
          `SELECT requestId, conversationId, messageId, capability, searchExecuted, sourceCount, sourcesRead, sourcesFailed, domains, finalStatus, errorType
           FROM turns WHERE timestamp >= ? AND timestamp < ? ORDER BY timestamp ASC`
        )
        .all(start.getTime(), end.getTime()) as SqliteRow[]
    )
    const idx = sampleIndices(rows.length, rate, cap)
    const sampled = idx.map((i) => rows[i])

    // Prior-turn context per conversation: an assistant message with sources
    // earlier in the same conversation marks the isolation guard.
    const priorSources = new Map<string, boolean>()
    const msgStmt = db.query('SELECT COUNT(1) n FROM MessageSource ms JOIN Message m ON ms.messageId = m.id WHERE m.conversationId = ? AND m.createdAt < (SELECT createdAt FROM Message WHERE id = ?)')
    const userStmt = db.query('SELECT content FROM Message WHERE id = ? AND role = ?')

    const verdicts: TurnVerdict[] = []
    for (const row of sampled) {
      const um = await withRetry(() => userStmt.get(row.messageId, 'user') as { content: string } | undefined)
      const prompt = um?.content ?? ''
      const hadSources = priorSources.get(row.conversationId) ?? (await withRetry(() => (msgStmt.get(row.conversationId, row.messageId) as { n: number } | undefined)?.n > 0)) ?? false
      priorSources.set(row.conversationId, hadSources || row.sourceCount > 0)
      const bucket = resolveBucket(prompt, hadSources)
      verdicts.push(gradeTurn(bucket, row, hadSources))
    }

    // Aggregate per category.
    const scores: CategoryScore[] = []
    for (const category of MONITOR_CATEGORIES) {
      const vs = verdicts.filter((v) => v.category === category)
      const graded = vs.filter((v) => v.graded)
      const passCount = graded.filter((v) => v.passed === true).length
      const failCount = graded.filter((v) => v.passed === false).length
      const passRate = graded.length > 0 ? passCount / graded.length : 0

      // WoW: same category, window 7 days earlier (±1h tolerance).
      const wowRow = await withRetry(() =>
        db
          .query(
            `SELECT passRate FROM eval_scores WHERE category = ? AND windowStart BETWEEN ? AND ? ORDER BY ABS(windowStart - ?) ASC LIMIT 1`
          )
          .get(category, start.getTime() - 7 * 3_600_000 - 3_600_000, start.getTime() - 7 * 3_600_000 + 3_600_000, start.getTime() - 7 * 3_600_000) as { passRate: number } | undefined
      )
      const wowDelta = wowRow ? passRate - wowRow.passRate : null
      const alerted = wowDelta !== null && wowDelta <= -0.05
      const alertNote = alerted
        ? `${category} passRate regressed ${(wowDelta! * 100).toFixed(1)}pp WoW (${(wowRow!.passRate * 100).toFixed(1)}% → ${(passRate * 100).toFixed(1)}%) — investigate production turns`
        : null

      scores.push({
        category,
        sampledTurns: vs.length,
        gradedTurns: graded.length,
        passCount,
        failCount,
        passRate,
        wowDelta,
        alerted,
        alertNote,
        sampleDetail: vs.map((v) => ({ requestId: v.requestId, bucket: v.bucket, graded: v.graded, passed: v.passed, failures: v.failures })),
      })

      if (graded.length > 0) {
        const detail = vs.map((v) => ({ requestId: v.requestId, bucket: v.bucket, graded: v.graded, passed: v.passed, failures: v.failures }))
        await withRetry(() =>
          db
            .query(
              `INSERT INTO eval_scores (id, windowStart, windowEnd, category, sampledTurns, gradedTurns, passCount, failCount, passRate, wowDelta, alerted, alertNote, sampleDetail, createdAt)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
               ON CONFLICT(windowStart, category) DO UPDATE SET
                 sampledTurns=excluded.sampledTurns, gradedTurns=excluded.gradedTurns, passCount=excluded.passCount,
                 failCount=excluded.failCount, passRate=excluded.passRate, wowDelta=excluded.wowDelta,
                 alerted=excluded.alerted, alertNote=excluded.alertNote, sampleDetail=excluded.sampleDetail`
            )
            .run(
              crypto.randomUUID(),
              start.getTime(),
              end.getTime(),
              category,
              vs.length,
              graded.length,
              passCount,
              failCount,
              passRate,
              wowDelta,
              alerted ? 1 : 0,
              alertNote,
              JSON.stringify(detail),
              Date.now()
            )
        )
      }
    }

    return {
      windowStart: start.toISOString(),
      windowEnd: end.toISOString(),
      turnsInWindow: rows.length,
      sampled: sampled.length,
      graded: verdicts.filter((v) => v.graded).length,
      passed: verdicts.filter((v) => v.graded && v.passed === true).length,
      failed: verdicts.filter((v) => v.graded && v.passed === false).length,
      scores,
    }
  } finally {
    db.close()
  }
}
