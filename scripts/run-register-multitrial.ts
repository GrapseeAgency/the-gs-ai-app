/**
 * MEASUREMENT HARNESS — MULTI-TRIAL REGISTER RE-RUN (register variance task).
 *
 * This file is measurement tooling ONLY. It changes NOTHING in the system
 * under test: every judged run shells out to the UNCHANGED
 * scripts/run-register-judge.ts (pinned judge glm-4.6, temperature 0, rubric
 * from tests/eval-suite-v1.json), which rides the real production path via
 * scripts/lib/eval-turn-client.ts. No prompt, route, rubric or judge edits —
 * this is a measurement run.
 *
 * Protocol (per config): 10 REGISTER cases × 5 trials = 50 judged runs.
 *  - A trial = one full invocation of run-register-judge.ts for ONE case
 *    (fresh conversation each trial, production wire path, judge temp 0).
 *  - A trial is JUDGED when its unit JSONL row carries verdict PASS or FAIL.
 *  - ERROR trials (provider throttle, judge unavailable) and route-mismatch
 *    trials are re-queued with an attempt cap; the reported score uses
 *    EXACTLY 5 judged trials per case (passed/judged, never best-of).
 *  - ROUTE GUARD: config 'zai' requires modelRoute starting 'zai/';
 *    config 'gsfree' requires 'openrouter/'. A keypool flip mid-run would
 *    otherwise silently corrupt a column — mismatched trials are rejected
 *    and re-run, and a sustained flip aborts the config.
 *
 * Idempotent / resumable: a unit with a valid judged row is skipped; ERROR
 * rows are moved to errors/ and the unit re-run. Designed to be invoked
 * repeatedly under a time budget (sandbox kills long-lived background
 * processes) — run it until it prints "ALL UNITS JUDGED".
 *
 * Usage:
 *   bun scripts/run-register-multitrial.ts --config zai --budget-ms 420000
 *       [--conc 4] [--trials 5] [--cases J51,...] [--force-free] [--origin URL]
 */

import fs from 'node:fs'
import path from 'node:path'

const ROOT = path.resolve(import.meta.dir, '..')
const TMP = '/tmp/register-multitrial'

// --- args --------------------------------------------------------------------

const argv = process.argv.slice(2)
function argOf(flag: string, fallback: string): string {
  const i = argv.indexOf(flag)
  return i >= 0 && argv[i + 1] !== undefined ? argv[i + 1] : fallback
}
const has = (flag: string): boolean => argv.includes(flag)
const CONFIG = argOf('--config', 'zai') // 'zai' | 'gsfree'
const BUDGET_MS = Number(argOf('--budget-ms', '420000'))
const CONC = Math.max(1, Number(argOf('--conc', '4')))
const TRIALS = Math.max(1, Number(argOf('--trials', '5')))
const ORIGIN = argOf('--origin', 'http://localhost:3000')
const FORCE_FREE = has('--force-free')
const MAX_ATTEMPTS = Math.max(1, Number(argOf('--max-attempts', '4')))
const PACING_MS = Math.max(0, Number(argOf('--pacing-ms', '15000')))
const MAKEUP_SLOTS = 4 // t6..t9 spare slots per case for ERROR makeups

const CASES = (argv.includes('--cases') ? argOf('--cases', '') : 'J51,J52,J53,J54,J55,J56,J57,J58,J59,J60')
  .split(',')
  .map((s) => s.trim())
  .filter(Boolean)

const cfgDir = path.join(TMP, CONFIG)
const errDir = path.join(cfgDir, 'errors')
const logDir = path.join(cfgDir, 'logs')
fs.mkdirSync(errDir, { recursive: true })
fs.mkdirSync(logDir, { recursive: true })

// --- unit state ----------------------------------------------------------------

interface UnitRow {
  at: string
  id: string
  verdict: 'PASS' | 'FAIL' | 'ERROR'
  modelRoute: string | null
  note?: string
}

function unitFile(caseId: string, trial: number): string {
  return path.join(cfgDir, `${caseId}-t${trial}.jsonl`)
}

function readRow(file: string): UnitRow | null {
  try {
    const text = fs.readFileSync(file, 'utf8').trim()
    if (!text) return null
    const lines = text.split('\n')
    return JSON.parse(lines[lines.length - 1]) as UnitRow
  } catch {
    return null
  }
}

function routeOk(route: string | null): boolean {
  if (!route) return false
  return CONFIG === 'zai' ? route.startsWith('zai/') : route.startsWith('openrouter/')
}

/** unit is DONE when a judged row (PASS/FAIL) exists on the expected route. */
function unitDone(file: string): boolean {
  const row = readRow(file)
  return row !== null && (row.verdict === 'PASS' || row.verdict === 'FAIL') && routeOk(row.modelRoute)
}

function quarantine(file: string, caseId: string, trial: number, attempt: number): void {
  try {
    fs.renameSync(file, path.join(errDir, `${caseId}-t${trial}-a${attempt}-${Date.now()}.jsonl`))
  } catch {
    /* nothing to quarantine */
  }
}

// --- work list -------------------------------------------------------------------

interface Unit {
  caseId: string
  trial: number
  attempts: number
}

function buildWorkList(): Unit[] {
  const units: Unit[] = []
  // trial-major order: spread each case's trials across the whole wall clock
  for (let t = 1; t <= TRIALS + MAKEUP_SLOTS; t++) {
    for (const caseId of CASES) {
      if (t > TRIALS) {
        // makeup slots only enqueue when the case still lacks 5 judged trials
        const judged = Array.from({ length: TRIALS + MAKEUP_SLOTS }, (_, i) => i + 1).filter(
          (tt) => unitDone(unitFile(caseId, tt)),
        ).length
        if (judged >= TRIALS) continue
      }
      const file = unitFile(caseId, t)
      if (unitDone(file)) continue
      const row = readRow(file)
      if (row && row.verdict === 'ERROR') continue // errored units re-enqueue below with fresh attempts
      units.push({ caseId, trial: t, attempts: 0 })
    }
  }
  return units
}

function erroredUnitsWithBudget(): Unit[] {
  // errored unit files (t within slots) are re-queued once more per invocation
  const units: Unit[] = []
  for (let t = 1; t <= TRIALS + MAKEUP_SLOTS; t++) {
    for (const caseId of CASES) {
      const file = unitFile(caseId, t)
      const row = readRow(file)
      if (row && row.verdict === 'ERROR') units.push({ caseId, trial: t, attempts: 0 })
    }
  }
  return units
}

// --- runner ---------------------------------------------------------------------

let lastDispatchAt = 0
let consecutiveInfra = 0

async function runUnit(u: Unit): Promise<void> {
  // global backpressure: the z-ai gateway throttles HARD (429 circuit) under
  // concurrency/burst; a tripped window poisons every in-flight call. Pace
  // dispatches and back off exponentially on consecutive infra failures.
  const sinceLast = Date.now() - lastDispatchAt
  if (lastDispatchAt > 0 && sinceLast < PACING_MS) {
    await new Promise((r) => setTimeout(r, PACING_MS - sinceLast))
  }
  if (consecutiveInfra >= 2) {
    const backoff = Math.min(120_000, 30_000 * consecutiveInfra)
    console.log(`BACKOFF ${backoff}ms after ${consecutiveInfra} consecutive infra failures`)
    await new Promise((r) => setTimeout(r, backoff))
  }
  lastDispatchAt = Date.now()
  const file = unitFile(u.caseId, u.trial)
  quarantine(file, u.caseId, u.trial, u.attempts + 1) // move any stale row aside
  const logFile = path.join(logDir, `${u.caseId}-t${u.trial}-a${u.attempts + 1}.log`)
  const childEnv: Record<string, string> = {}
  for (const [k, v] of Object.entries(process.env)) if (v !== undefined) childEnv[k] = v
  if (FORCE_FREE) childEnv.GS_FORCE_FREE_CHAIN = '1'
  else delete childEnv.GS_FORCE_FREE_CHAIN

  const proc = Bun.spawn(
    ['bun', path.join(ROOT, 'scripts', 'run-register-judge.ts'), '--origin', ORIGIN, '--delay', '0', '--ids', u.caseId, '--jsonl', file],
    { stdout: 'ignore', stderr: 'ignore', env: childEnv, cwd: ROOT },
  )
  const timeout = setTimeout(() => {
    try {
      proc.kill()
    } catch {
      /* already exited */
    }
  }, 300_000)
  await proc.exited
  clearTimeout(timeout)
  void logFile
}

async function main(): Promise<void> {
  const start = Date.now()
  console.log(
    `MULTITRIAL config=${CONFIG} cases=${CASES.length} trials=${TRIALS} conc=${CONC} budget=${BUDGET_MS}ms forceFree=${FORCE_FREE} origin=${ORIGIN}`,
  )

  const inFlight = new Set<Promise<void>>()
  let dispatched = 0
  let judged = 0
  let requeued = 0
  let routeMismatch = 0
  let consecutiveRouteMismatch = 0
  let aborted: string | null = null

  const queue: Unit[] = [...buildWorkList(), ...erroredUnitsWithBudget()]

  const dispatch = (u: Unit): void => {
    dispatched++
    const p = runUnit(u)
      .then(() => {
        const file = unitFile(u.caseId, u.trial)
        const row = readRow(file)
        if (row && (row.verdict === 'PASS' || row.verdict === 'FAIL')) {
          if (routeOk(row.modelRoute)) {
            judged++
            consecutiveInfra = 0
            consecutiveRouteMismatch = 0
            console.log(`JUDGED ${row.verdict} ${u.caseId}-t${u.trial} route=${row.modelRoute} (${Date.now() - start}ms)`)
          } else {
            routeMismatch++
            consecutiveRouteMismatch++
            quarantine(file, u.caseId, u.trial, 90 + u.attempts)
            console.log(`ROUTE-MISMATCH ${u.caseId}-t${u.trial} route=${row.modelRoute} — quarantined`)
            if (consecutiveRouteMismatch >= 4) aborted = `keypool flipped mid-run: 4 consecutive openrouter/ routes on config=${CONFIG}`
          }
        } else if (row && row.verdict === 'ERROR') {
          requeued++
          consecutiveInfra++
          console.log(`ERROR ${u.caseId}-t${u.trial} — will requeue (note: ${(row.note ?? '').slice(0, 80)})`)
        } else {
          requeued++
          consecutiveInfra++
          console.log(`NO-ROW ${u.caseId}-t${u.trial} — will requeue`)
        }
      })
      .finally(() => inFlight.delete(p))
    inFlight.add(p)
  }

  while (queue.length > 0 || inFlight.size > 0) {
    if (aborted) break
    const elapsed = Date.now() - start
    if (elapsed > BUDGET_MS && inFlight.size === 0) break
    if (elapsed > BUDGET_MS - 90_000) {
      // stop dispatching near the budget edge; let in-flight finish
      if (inFlight.size === 0) break
      await new Promise((r) => setTimeout(r, 3000))
      continue
    }
    if (queue.length > 0 && inFlight.size < CONC) {
      const u = queue.shift()
      if (u) {
        u.attempts++
        if (u.attempts <= MAX_ATTEMPTS) dispatch(u)
        // over attempt cap: leave the unit undone — a later invocation retries
      }
      continue
    }
    await new Promise((r) => setTimeout(r, 1500))
  }

  // final per-case tally
  console.log(`\n=== PROGRESS config=${CONFIG} dispatched=${dispatched} judged=${judged} requeued=${requeued} routeMismatch=${routeMismatch}${aborted ? ` ABORTED: ${aborted}` : ''} ===`)
  let totalJudged = 0
  let totalPassed = 0
  for (const caseId of CASES) {
    let caseJudged = 0
    let casePassed = 0
    for (let t = 1; t <= TRIALS + MAKEUP_SLOTS; t++) {
      const row = readRow(unitFile(caseId, t))
      if (row && (row.verdict === 'PASS' || row.verdict === 'FAIL') && routeOk(row.modelRoute)) {
        caseJudged++
        if (row.verdict === 'PASS') casePassed++
      }
    }
    totalJudged += caseJudged
    totalPassed += casePassed
    console.log(`${caseId} judged=${caseJudged}/${TRIALS} passed=${casePassed}`)
  }
  console.log(`TOTAL judged=${totalJudged}/${CASES.length * TRIALS} passed=${totalPassed}`)
  if (totalJudged === CASES.length * TRIALS && !aborted) console.log('ALL UNITS JUDGED')
}

await main()
