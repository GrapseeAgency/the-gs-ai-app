/**
 * EVALUATION INFRASTRUCTURE — LAYER 5 MONITOR SERVICE (Phase 5).
 *
 * Standalone bun service (:3031) that closes the eval loop:
 *
 *   every hour (aligned to the hour boundary):
 *     runMonitorPass() — sample 5% of the turns recorded in the trailing hour
 *     (evenly spaced, cap 60), classify each prompt into monitor buckets,
 *     grade the trace with the SAME Layer-4 deterministic semantics that gate
 *     PRs, write one eval_scores row per (windowStart, category), and flag
 *     week-over-week regressions > 5 percentage points (alerted + alertNote).
 *
 *   on boot: one immediate pass over the trailing full hour, so a restarted
 *   sandbox never loses more than one window.
 *
 *   endpoints:
 *     GET  /health   — liveness + last pass summary (never includes key data;
 *                      sampleDetail holds requestIds only)
 *     POST /run      — manual trigger for the trailing full hour (or an
 *                      explicit window via ?minutes=N backfill). Returns the
 *                      full MonitorPassResult.
 *
 * DB is the shared SQLite file (db/custom.db) with busy_timeout + retries —
 * the same contention discipline as the eval runner (R08 lesson).
 */

import { runMonitorPass, type MonitorPassResult } from '../../src/lib/eval-monitor'

const PORT = 3031
const DB_PATH = '/home/z/my-project/db/custom.db'
const SAMPLE_RATE = 0.05
const MAX_SAMPLE = 60
const HOUR_MS = 3_600_000

interface LastPass {
  at: string
  windowStart: string
  windowEnd: string
  turnsInWindow: number
  sampled: number
  graded: number
  passed: number
  failed: number
  alertedCategories: string[]
  ok: boolean
  error?: string
}

let lastPass: LastPass | null = null
let running = false
const startedAt = new Date()

function trailingHour(now = new Date()): { start: Date; end: Date } {
  // The trailing FULL hour (aligned): for 14:37 that is 13:00→14:00.
  const end = new Date(now)
  end.setMinutes(0, 0, 0)
  const start = new Date(end.getTime() - HOUR_MS)
  return { start, end }
}

async function executePass(start?: Date, end?: Date, rate = SAMPLE_RATE): Promise<MonitorPassResult> {
  const w = trailingHour()
  const result = await runMonitorPass({
    dbPath: DB_PATH,
    windowStart: start ?? w.start,
    windowEnd: end ?? w.end,
    sampleRate: rate,
    maxSample: MAX_SAMPLE,
  })
  lastPass = {
    at: new Date().toISOString(),
    windowStart: result.windowStart,
    windowEnd: result.windowEnd,
    turnsInWindow: result.turnsInWindow,
    sampled: result.sampled,
    graded: result.graded,
    passed: result.passed,
    failed: result.failed,
    alertedCategories: result.scores.filter((s) => s.alerted).map((s) => s.category),
    ok: true,
  }
  console.log(
    `EVAL-MONITOR pass window=${result.windowStart}..${result.windowEnd} turns=${result.turnsInWindow} sampled=${result.sampled} graded=${result.graded} pass=${result.passed} fail=${result.failed}` +
      (lastPass.alertedCategories.length ? ` ALERTS=${lastPass.alertedCategories.join(',')}` : '')
  )
  return result
}

/** Hourly tick: aligned to wall-clock hour, drift-corrected. */
function scheduleHourly(): void {
  const now = new Date()
  const next = new Date(now)
  next.setMinutes(0, 0, 0)
  next.setHours(next.getHours() + 1)
  const delay = next.getTime() - now.getTime() + 5_000 // +5s so the hour is fully closed
  setTimeout(async () => {
    if (!running) {
      running = true
      try {
        await executePass()
      } catch (e) {
        lastPass = { at: new Date().toISOString(), windowStart: '', windowEnd: '', turnsInWindow: 0, sampled: 0, graded: 0, passed: 0, failed: 0, alertedCategories: [], ok: false, error: e instanceof Error ? e.message : String(e) }
        console.error('EVAL-MONITOR pass failed:', e)
      } finally {
        running = false
      }
    }
    scheduleHourly()
  }, delay)
  console.log(`EVAL-MONITOR next pass at ${next.toISOString()} (+5s), in ${Math.round(delay / 1000)}s`)
}

const json = (body: unknown, status = 200): Response =>
  new Response(JSON.stringify(body, null, 2), { status, headers: { 'content-type': 'application/json' } })

const server = Bun.serve({
  port: PORT,
  async fetch(req): Promise<Response> {
    const url = new URL(req.url)
    if (url.pathname === '/health') {
      return json({
        status: 'ok',
        service: 'gs-eval-monitor',
        layer: 5,
        dbPath: DB_PATH,
        sampleRate: SAMPLE_RATE,
        maxSample: MAX_SAMPLE,
        startedAt: startedAt.toISOString(),
        running,
        lastPass,
      })
    }
    if (url.pathname === '/run' && req.method === 'POST') {
      if (running) return json({ error: 'a pass is already running' }, 409)
      // ?minutes=N backfills the trailing N minutes instead of the standard hour.
      const minutes = Number(url.searchParams.get('minutes') ?? '')
      running = true
      try {
        const end = new Date()
        const start = Number.isFinite(minutes) && minutes > 0 ? new Date(end.getTime() - minutes * 60_000) : undefined
        const result = await executePass(start, end, minutes > 0 ? 1 : SAMPLE_RATE) // manual runs sample 100% when backfilling a short window
        return json(result)
      } catch (e) {
        return json({ error: e instanceof Error ? e.message : String(e) }, 500)
      } finally {
        running = false
      }
    }
    return json({ error: 'not found', endpoints: ['GET /health', 'POST /run?minutes=N'] }, 404)
  },
})

// Boot: immediate pass over the trailing full hour, then hourly forever.
console.log(`EVAL-MONITOR listening on :${PORT} — layer 5 production monitor (5% hourly sample, WoW alert at -5pp)`)
void executePass()
  .catch((e) => {
    lastPass = { at: new Date().toISOString(), windowStart: '', windowEnd: '', turnsInWindow: 0, sampled: 0, graded: 0, passed: 0, failed: 0, alertedCategories: [], ok: false, error: e instanceof Error ? e.message : String(e) }
    console.error('EVAL-MONITOR boot pass failed (hourly schedule continues):', e)
  })
  .finally(() => scheduleHourly())

process.on('SIGINT', () => {
  server.stop(true)
  process.exit(0)
})
