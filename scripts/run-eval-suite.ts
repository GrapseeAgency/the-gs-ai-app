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
 * always persists. When the view ends without `done`, the runner re-fetches
 * GET /api/v1/conversations/:id/messages and grades the persisted answer
 * (the exact recovery the Android client performs, audit [4]).
 *
 * Usage:
 *   bun scripts/run-eval-suite.ts [--origin URL] [--delay MS] [--only CATEGORY]
 *                                 [--baseline PATH] [--jsonl PATH] [--strict]
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
const BASELINE = argv.includes('--baseline') ? argOf('--baseline', '') : null
const JSONL = argv.includes('--jsonl') ? argOf('--jsonl', '') : null
const STRICT = argv.includes('--strict')
const APP_VERSION = '0.68.1'

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

// --- production-path turn ----------------------------------------------------

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
              // server-side; the caller re-fetches the persisted answer.
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

/** Persisted-answer recovery: last assistant message of the conversation. */
async function refetchAnswer(conv: string): Promise<string | null> {
  try {
    const res = await fetch(`${ORIGIN}/api/v1/conversations/${conv}/messages`, {
      headers: { 'x-gs-app-version': APP_VERSION },
      signal: AbortSignal.timeout(30_000),
    })
    if (!res.ok) return null
    const messages = (await res.json()) as { role: string; content: string }[]
    const assistants = messages.filter((m) => m.role === 'assistant')
    return assistants.length > 0 ? assistants[assistants.length - 1].content : null
  } catch {
    return null
  }
}

// --- trace read (Layer 1) -----------------------------------------------------

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
    for (let i = 0; i < 12; i++) {
      const row = db
        .query('SELECT requestId, capability, trigger, searchExecuted, sourceCount, sourcesRead, sourcesFailed, domains, modelRoute, finalStatus, cited, latencyMs, errorType FROM turns WHERE requestId = ?')
        .get(requestId) as TraceRow | undefined
      if (row) return row
      await new Promise((r) => setTimeout(r, 500)) // trace write is fire-and-forget
    }
    return null
  } finally {
    db.close()
  }
}

// --- deterministic graders -----------------------------------------------------

function wordCount(text: string): number {
  return text.trim().split(/\s+/).filter((w) => w.length > 0).length
}
function lineCount(text: string): number {
  return text.split('\n').filter((l) => l.trim().length > 0).length
}

function evalAssertion(a: Assertion, trace: TraceRow | null, answer: string): { ok: boolean; detail: string } {
  const hasTrace = trace !== null
  switch (a.type) {
    case 'trace.searchExecuted': {
      if (!hasTrace) return { ok: false, detail: 'no trace row' }
      const actual = trace!.searchExecuted === 1
      return actual === a.value
        ? { ok: true, detail: `searchExecuted=${actual}` }
        : { ok: false, detail: `searchExecuted=${actual} expected=${a.value}` }
    }
    case 'trace.capability': {
      if (!hasTrace) return { ok: false, detail: 'no trace row' }
      return trace!.capability === a.value
        ? { ok: true, detail: `capability=${trace!.capability}` }
        : { ok: false, detail: `capability=${trace!.capability} expected=${a.value}` }
    }
    case 'trace.sourceCount': {
      if (!hasTrace) return { ok: false, detail: 'no trace row' }
      return trace!.sourceCount === a.value
        ? { ok: true, detail: `sourceCount=${trace!.sourceCount}` }
        : { ok: false, detail: `sourceCount=${trace!.sourceCount} expected=${a.value}` }
    }
    case 'trace.finalStatus': {
      if (!hasTrace) return { ok: false, detail: 'no trace row' }
      return trace!.finalStatus === a.value
        ? { ok: true, detail: `finalStatus=${trace!.finalStatus}` }
        : { ok: false, detail: `finalStatus=${trace!.finalStatus} expected=${a.value}` }
    }
    case 'trace.domainsMin': {
      if (!hasTrace) return { ok: false, detail: 'no trace row' }
      const domains = JSON.parse(trace!.domains) as string[]
      return domains.length >= Number(a.value)
        ? { ok: true, detail: `domains=${domains.length}` }
        : { ok: false, detail: `domains=${domains.length} expected>=${a.value} (${domains.join(',')})` }
    }
    case 'trace.sourcesReadMin': {
      if (!hasTrace) return { ok: false, detail: 'no trace row' }
      return trace!.sourcesRead >= Number(a.value)
        ? { ok: true, detail: `sourcesRead=${trace!.sourcesRead}` }
        : { ok: false, detail: `sourcesRead=${trace!.sourcesRead} expected>=${a.value} (sourceCount=${trace!.sourceCount} failed=${trace!.sourcesFailed})` }
    }
    case 'trace.readRate': {
      if (!hasTrace) return { ok: false, detail: 'no trace row' }
      if (trace!.sourceCount === 0) return { ok: false, detail: 'sourceCount=0 — read rate undefined' }
      const rate = trace!.sourcesRead / trace!.sourceCount
      return rate >= Number(a.value)
        ? { ok: true, detail: `readRate=${rate.toFixed(2)}` }
        : { ok: false, detail: `readRate=${rate.toFixed(2)} expected>=${a.value}` }
    }
    case 'citationsWithinSources': {
      if (!hasTrace) return { ok: false, detail: 'no trace row' }
      const cited = JSON.parse(trace!.cited) as number[]
      const bad = cited.filter((n) => n > trace!.sourceCount)
      return bad.length === 0
        ? { ok: true, detail: `cited=${cited.join(',')} within sourceCount=${trace!.sourceCount}` }
        : { ok: false, detail: `cited ${bad.join(',')} exceed sourceCount=${trace!.sourceCount}` }
    }
    case 'response.matchesRegex': {
      const re = new RegExp(a.value as string, a.flags ?? 'i')
      return re.test(answer)
        ? { ok: true, detail: `answer=${JSON.stringify(answer.slice(0, 40))} matched` }
        : { ok: false, detail: `answer=${JSON.stringify(answer.slice(0, 60))} did not match /${a.value}/${a.flags ?? 'i'}` }
    }
    case 'response.wordCount': {
      const actual = wordCount(answer)
      return actual === a.value
        ? { ok: true, detail: `wordCount=${actual}` }
        : { ok: false, detail: `wordCount=${actual} expected=${a.value} answer=${JSON.stringify(answer.slice(0, 60))}` }
    }
    case 'response.lineCount': {
      const actual = lineCount(answer)
      return actual === a.value
        ? { ok: true, detail: `lineCount=${actual}` }
        : { ok: false, detail: `lineCount=${actual} expected=${a.value}` }
    }
    case 'response.contains': {
      return answer.toLowerCase().includes(String(a.value).toLowerCase())
        ? { ok: true, detail: `contains "${a.value}"` }
        : { ok: false, detail: `missing "${a.value}" in ${JSON.stringify(answer.slice(0, 80))}` }
    }
    case 'response.notContains': {
      return !answer.toLowerCase().includes(String(a.value).toLowerCase())
        ? { ok: true, detail: `no "${a.value}"` }
        : { ok: false, detail: `forbidden "${a.value}" present in ${JSON.stringify(answer.slice(0, 80))}` }
    }
    case 'response.containsAny': {
      const values = (a.values ?? []) as string[]
      const hit = values.find((v) => answer.toLowerCase().includes(v.toLowerCase()))
      return hit
        ? { ok: true, detail: `contains "${hit}"` }
        : { ok: false, detail: `none of [${values.join('|')}] in ${JSON.stringify(answer.slice(0, 80))}` }
    }
    case 'anyOf': {
      const nested = (a.values ?? []) as Assertion[]
      const results = nested.map((n) => evalAssertion(n, trace, answer))
      const ok = results.some((r) => r.ok)
      return ok
        ? { ok: true, detail: results.find((r) => r.ok)!.detail }
        : { ok: false, detail: `anyOf failed: ${results.map((r) => r.detail).join(' AND ')}` }
    }
    default:
      return { ok: false, detail: `unknown assertion type ${a.type}` }
  }
}

// --- main -----------------------------------------------------------------------

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
  // Setup turns (context-isolation T1) in the SAME conversation.
  for (const setupPrompt of c.setup ?? []) {
    await sseTurn(conv, setupPrompt)
    await new Promise((r) => setTimeout(r, 4_000))
  }
  const turn = await sseTurn(conv, prompt)
  let answer = turn.answer
  let note = ''
  if (!turn.done && !turn.errorEvent) {
    // View cut before done (30s proxy cap on public origin) — recover the
    // persisted answer like the Android client does (audit [4]).
    const recovered = await refetchAnswer(conv)
    if (recovered !== null) {
      answer = recovered
      note = 'recovered-persisted (view cut before done)'
    } else {
      note = 'view ended before done; no persisted answer'
    }
  }
  if (turn.errorEvent) note = `error event: ${turn.errorEvent.slice(0, 120)}`
  if (turn.httpError) note = `${note} http=${turn.httpError}`.trim()

  const trace = await readTrace(turn.requestId)
  const results = c.assertions.map((a) => evalAssertion(a, trace, answer))
  const failed = results.filter((r) => !r.ok)

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
  base.status = failed.length === 0 ? 'PASS' : 'FAIL'
  base.failures = failed.map((f) => f.detail)
  return base
}

async function main(): Promise<void> {
  const cases = ONLY ? suite.cases.filter((c) => c.category === ONLY) : suite.cases
  console.log(`EVAL RUN suite=${suite.suite} cases=${cases.length} origin=${ORIGIN} delay=${DELAY_MS}ms strict=${STRICT}`)
  const jsonl = JSONL ? fs.createWriteStream(JSONL, { flags: 'a' }) : null

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
    jsonl?.write(`${JSON.stringify({ at: new Date().toISOString(), ...r })}\n`)
    if (DELAY_MS > 0) await new Promise((resolve) => setTimeout(resolve, DELAY_MS))
  }
  jsonl?.end()

  // Scorecard
  const blockingCats = new Set(['ROUTING', 'INSTRUCTION', 'CONTEXT_ISOLATION'])
  console.log('\n=== SCORECARD ===')
  console.log('CATEGORY          PASS  FAIL  ERROR  SKIP')
  let blockingFail = false
  let liveTotal = 0
  let livePass = 0
  for (const cat of suite.categories) {
    const rs = results.filter((r) => r.category === cat)
    if (rs.length === 0) continue
    const pass = rs.filter((r) => r.status === 'PASS').length
    const fail = rs.filter((r) => r.status === 'FAIL').length
    const error = rs.filter((r) => r.status === 'ERROR').length
    const skip = rs.filter((r) => r.status === 'SKIP').length
    const live = rs.filter((r) => r.status !== 'SKIP').length
    liveTotal += live
    livePass += pass
    if (blockingCats.has(cat) && fail > 0) blockingFail = true
    console.log(`${cat.padEnd(17)} ${String(pass).padStart(4)}  ${String(fail).padStart(4)}  ${String(error).padStart(5)}  ${String(skip).padStart(4)}`)
  }
  console.log(`\nDETERMINISTIC SCORE: ${livePass}/${liveTotal} live cases passed (${results.length - liveTotal} skipped: judge/fault-injection)`)

  if (BASELINE) {
    const payload = {
      suite: suite.suite,
      generatedAt: new Date().toISOString(),
      origin: ORIGIN,
      scorecard: suite.categories
        .map((cat) => {
          const rs = results.filter((r) => r.category === cat)
          return {
            category: cat,
            pass: rs.filter((r) => r.status === 'PASS').length,
            fail: rs.filter((r) => r.status === 'FAIL').length,
            error: rs.filter((r) => r.status === 'ERROR').length,
            skip: rs.filter((r) => r.status === 'SKIP').length,
          }
        })
        .filter((s) => s.pass + s.fail + s.error + s.skip > 0),
      deterministicScore: { pass: livePass, total: liveTotal, skipped: results.length - liveTotal },
      cases: results.map((r) => ({
        id: r.id,
        category: r.category,
        status: r.status,
        failures: r.failures,
        requestId: r.requestId,
        answerHead: r.answerHead,
        note: r.note,
        trace: r.trace
          ? {
              capability: r.trace.capability,
              trigger: r.trace.trigger,
              searchExecuted: r.trace.searchExecuted === 1,
              sourceCount: r.trace.sourceCount,
              sourcesRead: r.trace.sourcesRead,
              sourcesFailed: r.trace.sourcesFailed,
              domains: JSON.parse(r.trace.domains) as string[],
              modelRoute: r.trace.modelRoute,
              finalStatus: r.trace.finalStatus,
              cited: JSON.parse(r.trace.cited) as number[],
              latencyMs: r.trace.latencyMs,
              errorType: r.trace.errorType,
            }
          : null,
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
