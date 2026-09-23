/**
 * EVAL INFRASTRUCTURE — LAYER 4 RUNNER (Phase 3).
 *
 * Runs tests/eval-suite-v1.json through the REAL production path:
 *   1. POST /api/v1/conversations                     (fresh conversation per case)
 *   2. POST /api/v1/conversations/:id/messages        (SSE streaming, setup + main turns)
 *   3. Captures X-GS-Request-Id from the response headers
 *   4. Reads the structured trace row from the `turns` table (Layer 1)
 *   5. Evaluates the case's assertions against trace + streamed text
 *
 * Recovery: the public origin's outer proxy caps SSE views at ~30s while
 * search turns legitimately run longer — the turn itself is DETACHED and
 * always persists. When the view ends without `done`, the runner POLLS the
 * persisted conversation until the turn's own assistant message lands (the
 * exact recovery the Android client performs, audit [4]) — never grading the
 * previous turn's answer by accident.
 *
 * Long runs are CHUNKABLE (sandbox/background-process safe and CI-throttle
 * friendly):
 *   --only CATEGORY          run one category
 *   --ids R01,R02,C03        run exact case ids (composable with --only)
 *   --merge-baseline PATH    upsert this run's results into the baseline by
 *                            case id and recompute the scorecard — the way
 *                            the single baseline number is assembled from
 *                            chunked runs, and the way ERROR cases are
 *                            re-run later without touching PASS/FAIL history
 *
 * Usage:
 *   bun scripts/run-eval-suite.ts [--origin URL] [--delay MS] [--only CATEGORY]
 *       [--ids ID,ID] [--baseline PATH] [--merge-baseline PATH] [--jsonl PATH]
 *       [--strict] [--github-annotations]
 *
 * Exit codes: 0 always unless --strict, in which case any FAIL in a blocking
 * category (ROUTING / INSTRUCTION / CONTEXT_ISOLATION) exits 1. Infra ERRORs
 * (429 throttle, 502 gateway) are reported but NEVER count as assertion
 * failures — and never as passes (case-i lesson).
 */

import { Database } from 'bun:sqlite'
import fs from 'node:fs'
import path from 'node:path'

const ROOT = path.resolve(import.meta.dir, '..')
const DB_PATH = path.join(ROOT, 'db', 'custom.db')

// --- args -------------------------------------------------------------------

const argv = process.argv.slice(2)
function argOf(flag: string, fallback: string): string {
  const i = argv.indexOf(flag)
  return i >= 0 && argv[i + 1] ? argv[i + 1] : fallback
}
const ORIGIN = argOf('--origin', 'http://localhost:3000')
const DELAY_MS = Number(argOf('--delay', '12000'))
const ONLY = argv.includes('--only') ? argOf('--only', '') : null
const IDS = argv.includes('--ids') ? argOf('--ids', '').split(',').map((s) => s.trim()).filter(Boolean) : null
const BASELINE = argv.includes('--baseline') ? argOf('--baseline', '') : null
const MERGE_BASELINE = argv.includes('--merge-baseline') ? argOf('--merge-baseline', '') : null
const JSONL = argv.includes('--jsonl') ? argOf('--jsonl', '') : null
const STRICT = argv.includes('--strict')
const ANNOTATE = argv.includes('--github-annotations')
const APP_VERSION = '0.68.1'

const BLOCKING_CATS = new Set(['ROUTING', 'INSTRUCTION', 'CONTEXT_ISOLATION'])

// --- suite ------------------------------------------------------------------

interface Assertion {
  type: string
  op?: string
  value?: unknown
  values?: unknown[]
  flags?: string
}
interface EvalCase {
  id: string
  category: string
  prompt?: string | null
  setup?: string[]
  fault?: string
  blocking?: boolean
  grading?: string
  skipReason?: string
  judgeCriteria?: string
  auditRef?: string
  assertions: Assertion[]
}
interface Suite {
  suite: string
  categories: string[]
  cases: EvalCase[]
}
const suite = JSON.parse(fs.readFileSync(path.join(ROOT, 'tests', 'eval-suite-v1.json'), 'utf8')) as Suite

// --- production-path turn -----------------------------------------------------

interface TurnResult {
  requestId: string | null
  answer: string
  done: boolean
  errorEvent: string | null
  events: string[]
  httpError: string | null
}

function sseTurn(conv: string, message: string): Promise<TurnResult> {
  return new Promise((resolve) => {
    const result: TurnResult = { requestId: null, answer: '', done: false, errorEvent: null, events: [], httpError: null }
    fetch(`${ORIGIN}/api/v1/conversations/${conv}/messages`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'x-gs-app-version': APP_VERSION },
      body: JSON.stringify({ content: message, stream: true, timezone: 'Asia/Dhaka' }),
      signal: AbortSignal.timeout(240_000),
    })
      .then((res) => {
        result.requestId = res.headers.get('x-gs-request-id')
        if (!res.ok || !res.body) {
          result.httpError = `HTTP ${res.status}`
          res.text().then(() => resolve(result)).catch(() => resolve(result))
          return
        }
        const reader = res.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''
        const pump = (): void => {
          reader
            .read()
            .then(({ done, value }) => {
              if (done) {
                resolve(result)
                return
              }
              buffer += decoder.decode(value, { stream: true })
              const lines = buffer.split('\n')
              buffer = lines.pop() ?? ''
              for (const line of lines) {
                const trimmed = line.trim()
                if (!trimmed.startsWith('data:')) continue
                try {
                  const ev = JSON.parse(trimmed.slice(5).trim()) as { event: string; data: unknown }
                  result.events.push(ev.event)
                  if (ev.event === 'delta' && typeof ev.data === 'string') result.answer += ev.data
                  else if (ev.event === 'done' && typeof ev.data === 'string') {
                    result.done = true
                    try {
                      const msg = JSON.parse(ev.data) as { content?: string }
                      if (msg.content) result.answer = msg.content
                    } catch { /* keep deltas */ }
                  } else if (ev.event === 'error') {
                    result.errorEvent = String(ev.data)
                  }
                } catch { /* non-JSON keep-alive comment */ }
              }
              pump()
            })
            .catch((e: unknown) => {
              // View cut (proxy cap / abort) — the detached turn continues
              // server-side; the caller polls for the persisted answer.
              result.httpError = e instanceof Error ? e.message : String(e)
              resolve(result)
            })
        }
        pump()
      })
      .catch((e: unknown) => {
        result.httpError = e instanceof Error ? e.message : String(e)
        resolve(result)
      })
  })
}

async function newConversation(title: string): Promise<string> {
  const res = await fetch(`${ORIGIN}/api/v1/conversations`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'x-gs-app-version': APP_VERSION },
    body: JSON.stringify({ title }),
    signal: AbortSignal.timeout(30_000),
  })
  if (!res.ok) throw new Error(`conversation create failed: HTTP ${res.status}`)
  const body = (await res.json()) as { id: string }
  return body.id
}

interface MessagesState {
  assistantCount: number
  lastAssistant: string | null
}

async function fetchMessages(conv: string): Promise<MessagesState | null> {
  try {
    const res = await fetch(`${ORIGIN}/api/v1/conversations/${conv}/messages`, {
      headers: { 'x-gs-app-version': APP_VERSION },
      signal: AbortSignal.timeout(30_000),
    })
    if (!res.ok) return null
    const messages = (await res.json()) as { role: string; content: string }[]
    const assistants = messages.filter((m) => m.role === 'assistant')
    return {
      assistantCount: assistants.length,
      lastAssistant: assistants.length > 0 ? assistants[assistants.length - 1].content : null,
    }
  } catch {
    return null
  }
}

/**
 * Persisted-answer recovery, settlement-safe: poll until the conversation
 * holds at least `expectedAssistants` assistant messages (setup turns + this
 * turn) so a cut view NEVER gets graded against the PREVIOUS turn's answer.
 */
async function refetchSettledAnswer(conv: string, expectedAssistants: number, timeoutMs = 120_000): Promise<{ answer: string | null; settled: boolean }> {
  const deadline = Date.now() + timeoutMs
  let last: MessagesState | null = null
  while (Date.now() < deadline) {
    last = await fetchMessages(conv)
    if (last && last.assistantCount >= expectedAssistants && last.lastAssistant) {
      return { answer: last.lastAssistant, settled: true }
    }
    await new Promise((r) => setTimeout(r, 3_000))
  }
  return { answer: last?.lastAssistant ?? null, settled: false }
}

// --- trace read (Layer 1) ------------------------------------------------------

interface TraceRow {
  requestId: string
  capability: string
  trigger: string
  searchExecuted: number
  sourceCount: number
  sourcesRead: number
  sourcesFailed: number
  domains: string
  modelRoute: string
  finalStatus: string
  cited: string
  latencyMs: number
  errorType: string | null
}

async function readTrace(requestId: string | null): Promise<TraceRow | null> {
  if (!requestId) return null
  const db = new Database(DB_PATH, { readonly: true })
  try {
    // The dev server's Prisma connection holds short write locks; a bare
    // readonly open fails fast with SQLITE_BUSY under contention. Wait for
    // writers instead of crashing the whole run (observed live at R08).
    try {
      db.run('PRAGMA busy_timeout = 10000')
    } catch { /* older bun: pragma may be unsupported — retry loop below still applies */ }
    for (let i = 0; i < 20; i++) {
      for (let attempt = 0; attempt < 6; attempt++) {
        try {
          const row = db
            .query('SELECT requestId, capability, trigger, searchExecuted, sourceCount, sourcesRead, sourcesFailed, domains, modelRoute, finalStatus, cited, latencyMs, errorType FROM turns WHERE requestId = ?')
            .get(requestId) as TraceRow | undefined
          if (row) return row
          break
        } catch (e) {
          const code = (e as { code?: string }).code
          if (code !== 'SQLITE_BUSY') throw e
          await new Promise((r) => setTimeout(r, 1_000))
        }
      }
      await new Promise((r) => setTimeout(r, 500)) // trace write is fire-and-forget
    }
    return null
  } finally {
    db.close()
  }
}

// --- deterministic graders (shared core: src/lib/eval-grader.ts) -----------------

import { evalAssertion, type Assertion as CoreAssertion } from '../src/lib/eval-grader'
type GradeAssertion = CoreAssertion

// --- main ------------------------------------------------------------------------

type Status = 'PASS' | 'FAIL' | 'SKIP' | 'ERROR'
interface CaseResult {
  id: string
  category: string
  status: Status
  failures: string[]
  requestId: string | null
  trace: TraceRow | null
  answerHead: string
  note: string
}

async function runCase(c: EvalCase): Promise<CaseResult> {
  const base: CaseResult = { id: c.id, category: c.category, status: 'SKIP', failures: [], requestId: null, trace: null, answerHead: '', note: '' }
  if (c.grading === 'llm_judge') {
    base.note = 'LLM-judge category — excluded from deterministic score'
    return base
  }
  if (c.fault) {
    base.note = c.skipReason ?? 'fault-injection case'
    return base
  }
  const prompt = c.prompt ?? ''
  if (!prompt) {
    base.note = 'case has no prompt'
    return base
  }
  let conv: string
  try {
    conv = await newConversation(`eval-${c.id}`)
  } catch (e) {
    base.status = 'ERROR'
    base.note = `infra: ${e instanceof Error ? e.message : String(e)}`
    return base
  }
  const setupTurns = c.setup ?? []
  // Setup turns (context-isolation T1) in the SAME conversation.
  for (const setupPrompt of setupTurns) {
    await sseTurn(conv, setupPrompt)
    await new Promise((r) => setTimeout(r, 4_000))
  }
  const expectedAssistants = setupTurns.length + 1
  const turn = await sseTurn(conv, prompt)
  let answer = turn.answer
  let note = ''
  if (!turn.done && !turn.errorEvent) {
    // View cut before done (30s proxy cap on public origin) — poll for the
    // persisted answer of THIS turn, never grade the previous turn's text.
    const { answer: recovered, settled } = await refetchSettledAnswer(conv, expectedAssistants)
    if (recovered !== null && settled) {
      answer = recovered
      note = 'recovered-persisted (view cut before done)'
    } else if (recovered !== null) {
      answer = recovered
      note = 'UNSETTLED recovery — answer may belong to an earlier turn'
    } else {
      note = 'view ended before done; no persisted answer'
    }
  }
  if (turn.errorEvent) note = `error event: ${turn.errorEvent.slice(0, 120)}`
  if (turn.httpError) note = `${note} http=${turn.httpError}`.trim()

  const trace = await readTrace(turn.requestId)
  const failures = trace ? (c.assertions as GradeAssertion[]).map((a) => evalAssertion(a, trace, answer)).filter((v) => !v.ok) : []

  base.requestId = turn.requestId
  base.trace = trace
  base.answerHead = answer.slice(0, 140).replace(/\n/g, ' ')
  base.note = note

  // Infra errors (no trace, no answer, no done) are ERROR — never FAIL, never PASS.
  const infra = !trace && !turn.done && !answer && (turn.httpError !== null || turn.errorEvent !== null)
  if (infra) {
    base.status = 'ERROR'
    return base
  }
  if (!trace) {
    base.status = 'ERROR'
    base.note = `${base.note} — trace row missing for requestId=${turn.requestId}`.trim()
    return base
  }
  // A turn that TERMINATED IN ERROR produced no gradeable answer — provider
  // throttle / upstream failure is infrastructure, not an assertion failure
  // and never a pass (case-i lesson). Re-run these; the baseline merge
  // upserts by case id.
  if (turn.errorEvent !== null || trace.finalStatus === 'error') {
    base.status = 'ERROR'
    base.note = `${base.note} provider-error errorType=${trace.errorType ?? 'unknown'}`.trim()
    return base
  }
  base.status = failures.length === 0 ? 'PASS' : 'FAIL'
  base.failures = failures.map((f) => f.detail)
  return base
}

// --- baseline merge -----------------------------------------------------------------

interface BaselinePayload {
  suite: string
  generatedAt: string
  firstBaselineAt?: string
  origin: string
  scorecard: { category: string; pass: number; fail: number; error: number; skip: number }[]
  deterministicScore: { pass: number; total: number; skipped: number }
  cases: {
    id: string
    category: string
    status: Status
    failures: string[]
    requestId: string | null
    answerHead: string
    note: string
    trace: Record<string, unknown> | null
  }[]
}

function traceToPayload(r: TraceRow): Record<string, unknown> {
  return {
    capability: r.capability,
    trigger: r.trigger,
    searchExecuted: r.searchExecuted === 1,
    sourceCount: r.sourceCount,
    sourcesRead: r.sourcesRead,
    sourcesFailed: r.sourcesFailed,
    domains: JSON.parse(r.domains) as string[],
    modelRoute: r.modelRoute,
    finalStatus: r.finalStatus,
    cited: JSON.parse(r.cited) as number[],
    latencyMs: r.latencyMs,
    errorType: r.errorType,
  }
}

function buildScorecard(cases: BaselinePayload['cases']): BaselinePayload['scorecard'] {
  return suite.categories
    .map((cat) => {
      const rs = cases.filter((c) => c.category === cat)
      return {
        category: cat,
        pass: rs.filter((r) => r.status === 'PASS').length,
        fail: rs.filter((r) => r.status === 'FAIL').length,
        error: rs.filter((r) => r.status === 'ERROR').length,
        skip: rs.filter((r) => r.status === 'SKIP').length,
      }
    })
    .filter((s) => s.pass + s.fail + s.error + s.skip > 0)
}

function writeMergedBaseline(target: string, results: CaseResult[]): void {
  let merged: BaselinePayload
  if (fs.existsSync(target)) {
    try {
      merged = JSON.parse(fs.readFileSync(target, 'utf8')) as BaselinePayload
      if (merged.suite !== suite.suite) throw new Error(`suite name mismatch: ${merged.suite}`)
    } catch (e) {
      throw new Error(`merge target unreadable/incompatible: ${e instanceof Error ? e.message : String(e)}`)
    }
  } else {
    merged = { suite: suite.suite, generatedAt: '', firstBaselineAt: new Date().toISOString(), origin: ORIGIN, scorecard: [], deterministicScore: { pass: 0, total: 0, skipped: 0 }, cases: [] }
  }
  const byId = new Map(merged.cases.map((c) => [c.id, c]))
  for (const r of results) {
    byId.set(r.id, {
      id: r.id,
      category: r.category,
      status: r.status,
      failures: r.failures,
      requestId: r.requestId,
      answerHead: r.answerHead,
      note: r.note,
      trace: r.trace ? traceToPayload(r.trace) : null,
    })
  }
  const cases = [...byId.values()]
  const live = cases.filter((c) => c.status !== 'SKIP')
  merged.cases = cases
  merged.scorecard = buildScorecard(cases)
  merged.deterministicScore = {
    pass: cases.filter((c) => c.status === 'PASS').length,
    total: live.length,
    skipped: cases.length - live.length,
  }
  merged.generatedAt = new Date().toISOString()
  fs.writeFileSync(target, `${JSON.stringify(merged, null, 2)}\n`)
  console.log(`baseline merged: ${target} — ${cases.length}/${suite.cases.length} cases graded so far`)
}

// --- entry ---------------------------------------------------------------------------

async function main(): Promise<void> {
  let cases = suite.cases
  if (ONLY) cases = cases.filter((c) => c.category === ONLY)
  if (IDS) cases = cases.filter((c) => IDS.includes(c.id))
  console.log(`EVAL RUN suite=${suite.suite} cases=${cases.length}/${suite.cases.length} origin=${ORIGIN} delay=${DELAY_MS}ms strict=${STRICT}${ONLY ? ` only=${ONLY}` : ''}${IDS ? ` ids=${IDS.join(',')}` : ''}`)

  const results: CaseResult[] = []
  for (const c of cases) {
    const r = await runCase(c)
    results.push(r)
    const line =
      r.status === 'PASS'
        ? `PASS ${c.id.padEnd(4)} [${c.category}] ${JSON.stringify(c.prompt ?? c.fault).slice(0, 50)}`
        : r.status === 'FAIL'
          ? `FAIL ${c.id.padEnd(4)} [${c.category}] ${JSON.stringify(c.prompt ?? c.fault).slice(0, 50)} — ${r.failures.join(' | ')}`
          : `${r.status} ${c.id.padEnd(4)} [${c.category}] ${JSON.stringify(c.prompt ?? c.fault).slice(0, 50)} — ${r.note}`
    console.log(line)
    if (ANNOTATE && r.status === 'FAIL') {
      const kind = BLOCKING_CATS.has(c.category) ? 'error' : 'warning'
      console.log(`::${kind} title=eval ${c.id} [${c.category}]::${r.failures.join(' | ')}`)
    }
    if (JSONL) fs.appendFileSync(JSONL, `${JSON.stringify({ at: new Date().toISOString(), ...r })}\n`) // sync: crash-safe raw log
    if (DELAY_MS > 0) await new Promise((resolve) => setTimeout(resolve, DELAY_MS))
  }

  // Scorecard for THIS run
  const blockingFail = results.some((r) => r.status === 'FAIL' && BLOCKING_CATS.has(r.category))
  const live = results.filter((r) => r.status !== 'SKIP')
  console.log('\n=== RUN SCORECARD ===')
  console.log('CATEGORY          PASS  FAIL  ERROR  SKIP')
  for (const cat of suite.categories) {
    const rs = results.filter((r) => r.category === cat)
    if (rs.length === 0) continue
    console.log(
      `${cat.padEnd(17)} ${String(rs.filter((r) => r.status === 'PASS').length).padStart(4)}  ${String(rs.filter((r) => r.status === 'FAIL').length).padStart(4)}  ${String(rs.filter((r) => r.status === 'ERROR').length).padStart(5)}  ${String(rs.filter((r) => r.status === 'SKIP').length).padStart(4)}`
    )
  }
  console.log(`\nRUN SCORE: ${live.filter((r) => r.status === 'PASS').length}/${live.length} live cases passed (${results.length - live.length} skipped: judge/fault-injection)`)

  if (MERGE_BASELINE) writeMergedBaseline(MERGE_BASELINE, results)
  else if (BASELINE) {
    // Single-shot mode: write the full payload from this run alone.
    const payload: BaselinePayload = {
      suite: suite.suite,
      generatedAt: new Date().toISOString(),
      origin: ORIGIN,
      scorecard: buildScorecard(
        results.map((r) => ({
          id: r.id,
          category: r.category,
          status: r.status,
          failures: r.failures,
          requestId: r.requestId,
          answerHead: r.answerHead,
          note: r.note,
          trace: r.trace ? traceToPayload(r.trace) : null,
        }))
      ),
      deterministicScore: { pass: live.filter((r) => r.status === 'PASS').length, total: live.length, skipped: results.length - live.length },
      cases: results.map((r) => ({
        id: r.id,
        category: r.category,
        status: r.status,
        failures: r.failures,
        requestId: r.requestId,
        answerHead: r.answerHead,
        note: r.note,
        trace: r.trace ? traceToPayload(r.trace) : null,
      })),
    }
    fs.writeFileSync(BASELINE, `${JSON.stringify(payload, null, 2)}\n`)
    console.log(`baseline written: ${BASELINE}`)
  }
  if (STRICT && blockingFail) {
    console.error('STRICT: blocking-category failures present — exit 1')
    process.exit(1)
  }
}

await main()
