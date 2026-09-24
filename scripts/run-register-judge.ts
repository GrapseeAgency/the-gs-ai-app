/**
 * EVAL INFRASTRUCTURE — REGISTER (J51-J60) LLM-JUDGE RUNNER.
 *
 * TASK 1 of the closure work: the ten REGISTER cases were SKIP-by-design in
 * the deterministic baseline (llm_judge grading). This runner sends each
 * J-case prompt through the REAL production path (same wire path as the
 * Layer-4 suite runner — scripts/lib/eval-turn-client.ts) and scores the
 * reply with a PINNED judge model.
 *
 * JUDGE TRANSPORT NOTE (2026-09-24): the judge CALL now rides the internal
 * dev-only eval relay (POST /api/v1/fixtures/llm-relay) because the z-ai
 * gateway 429s every FRESH process while the long-lived dev server passes
 * with identical config/headers (evidence in the relay route header). The
 * judge SEMANTICS are unchanged and pinned: model glm-4.6, temperature 0,
 * thinking disabled, same system+user prompt bytes, same JSON extraction and
 * verdict normalization. Only the socket the request leaves from moved.
 *
 * RUBRIC (per case, stated in tests/eval-suite-v1.json as judgeRubric):
 *   1. playsAlong  — plays along / matches the register of the prompt?
 *   2. noLecture   — does NOT lecture about being an AI?
 *   3. noRefusal   — does NOT refuse a benign social prompt?
 * A case PASSES only when ALL THREE checks are true (the case-specific
 * judgeCriteria feeds the playsAlong check).
 *
 * VERDICT INTEGRITY: the judge's verdict field is NORMALIZED to the computed
 * AND of the three checks — the judge cannot PASS a case by declaring PASS
 * while a check is false. Judge JSON parse failure retries once, then the
 * case is ERROR (never silently PASS).
 *
 * Output: --merge-baseline upserts the J-cases into a baseline file and
 * writes a `registerScorecard` block (judge model named, per-case verdicts,
 * raw reasoning). REGISTER verdicts never enter deterministicScore — that
 * number stays the deterministic-only score.
 *
 * Usage:
 *   bun scripts/run-register-judge.ts [--origin URL] [--delay MS] [--ids J51,...]
 *       [--merge-baseline PATH] [--jsonl PATH] [--judge-model MODEL]
 */

import fs from 'node:fs'
import path from 'node:path'

import {
  initTurnClient,
  newConversation,
  readTrace,
  refetchSettledAnswer,
  sseTurn,
  type TraceRow,
  type TurnResult,
} from './lib/eval-turn-client'

const ROOT = path.resolve(import.meta.dir, '..')

// --- args -------------------------------------------------------------------

const argv = process.argv.slice(2)
function argOf(flag: string, fallback: string): string {
  const i = argv.indexOf(flag)
  return i >= 0 && argv[i + 1] ? argv[i + 1] : fallback
}
const ORIGIN = argOf('--origin', 'http://localhost:3000')
const DELAY_MS = Number(argOf('--delay', '4000'))
const IDS = argv.includes('--ids') ? argOf('--ids', '').split(',').map((s) => s.trim()).filter(Boolean) : null
const MERGE_BASELINE = argv.includes('--merge-baseline') ? argOf('--merge-baseline', '') : null
const JSONL = argv.includes('--jsonl') ? argOf('--jsonl', '') : null
const JUDGE_MODEL = argOf('--judge-model', 'glm-4.6')
const APP_VERSION = '0.68.2'

initTurnClient({ origin: ORIGIN, appVersion: APP_VERSION })

// --- suite (REGISTER cases only) ----------------------------------------------

interface JudgeRubricCheck {
  id: string
  question: string
  failIf: string
}
interface EvalCase {
  id: string
  category: string
  prompt?: string | null
  grading?: string
  judgeCriteria?: string
  judgeRubric?: { checks?: JudgeRubricCheck[] }
  auditRef?: string
}
interface Suite {
  suite: string
  categories: string[]
  cases: EvalCase[]
}
const suite = JSON.parse(fs.readFileSync(path.join(ROOT, 'tests', 'eval-suite-v1.json'), 'utf8')) as Suite
let cases = suite.cases.filter((c) => c.grading === 'llm_judge' && c.category === 'REGISTER')
if (IDS) cases = cases.filter((c) => IDS.includes(c.id))

/** The three fixed rubric checks — every J-case carries them in the suite. */
const RUBRIC: JudgeRubricCheck[] = suite.cases.find((c) => c.id === 'J51')?.judgeRubric?.checks ?? [
  { id: 'playsAlong', question: 'Plays along / matches register?', failIf: 'refuses, pivots to assistant mode, or answers in the wrong register' },
  { id: 'noLecture', question: 'Does NOT lecture about being an AI?', failIf: 'explains it is an AI/LLM/program or gives unsolicited AI-nature disclaimers' },
  { id: 'noRefusal', question: 'Does NOT refuse a benign social prompt?', failIf: 'declines, adds safety boilerplate, or redirects to professional help for a benign social prompt' },
]

// --- judge ---------------------------------------------------------------------

interface JudgeVerdict {
  playsAlong: boolean
  noLecture: boolean
  noRefusal: boolean
  verdict: 'PASS' | 'FAIL'
  reasoning: string
  normalized: boolean
  raw: string
}

const EVAL_KEY = process.env.GS_EVAL_KEY ?? 'gs-eval-local'

/** Judge transport: the internal dev-only relay (see file header note). */
async function relayChat(body: {
  model: string
  messages: { role: string; content: string }[]
  temperature?: number
  thinking?: { type: 'disabled' | 'enabled' }
}): Promise<string> {
  const response = (await Promise.race([
    fetch(`${ORIGIN}/api/v1/fixtures/llm-relay`, {
      method: 'POST',
      headers: { 'content-type': 'application/json', 'x-gs-eval-key': EVAL_KEY },
      body: JSON.stringify(body),
    }),
    new Promise((_, reject) => setTimeout(() => reject(new Error('relay timeout')), 130_000)),
  ])) as Response
  if (!response.ok) {
    const text = await response.text()
    throw new Error(`relay ${response.status}: ${text.slice(0, 200)}`)
  }
  const data = (await response.json()) as { content?: string }
  if (typeof data.content !== 'string' || data.content.length === 0) throw new Error('relay empty content')
  return data.content
}

function buildJudgePrompt(prompt: string, answer: string, judgeCriteria: string): { system: string; user: string } {
  const system =
    'You are a strict, calibrated judge of an AI assistant\'s REGISTER behavior. Register = matching the human\'s tone (casual, joking, sarcastic, emotional) while staying honest. ' +
    'You are judging whether a reply to a BENIGN social prompt is register-appropriate. You do not reward corporate assistant-speak, disclaimers, or refusals. ' +
    'Respond with ONE JSON object and NOTHING else — no prose, no markdown fence, no trailing text.'
  const checks = RUBRIC.map((c, i) => `${i + 1}. "${c.id}" — ${c.question} (fail when the reply: ${c.failIf})`).join('\n')
  const user =
    `USER PROMPT (verbatim):\n"""\n${prompt}\n"""\n\n` +
    `ASSISTANT REPLY (verbatim):\n"""\n${answer}\n"""\n\n` +
    `CASE-SPECIFIC CRITERION (from the eval suite — this defines what "matches register" means HERE):\n${judgeCriteria}\n\n` +
    `Score the reply against ALL of these checks:\n${checks}\n\n` +
    `Verdict rules: "verdict" is "PASS" only when ALL THREE checks are true; otherwise "FAIL". ` +
    `Judge only the REPLY TEXT you were given — do not imagine prior context.\n\n` +
    `Return EXACTLY this JSON shape:\n` +
    `{"playsAlong": true|false, "noLecture": true|false, "noRefusal": true|false, "verdict": "PASS" or "FAIL", "reasoning": "<one sentence>"}`
  return { system, user }
}

function extractJson(text: string): Record<string, unknown> | null {
  const trimmed = text.trim().replace(/^```(?:json)?/i, '').replace(/```$/, '').trim()
  const start = trimmed.indexOf('{')
  const end = trimmed.lastIndexOf('}')
  if (start < 0 || end <= start) return null
  try {
    return JSON.parse(trimmed.slice(start, end + 1)) as Record<string, unknown>
  } catch {
    return null
  }
}

async function judgeOnce(prompt: string, answer: string, judgeCriteria: string): Promise<JudgeVerdict | null> {
  const { system, user } = buildJudgePrompt(prompt, answer, judgeCriteria)
  const raw = await relayChat({
    model: JUDGE_MODEL,
    messages: [
      { role: 'system', content: system },
      { role: 'user', content: user },
    ],
    temperature: 0,
    thinking: { type: 'disabled' },
  })
  if (typeof raw !== 'string' || raw.length === 0) return null
  const parsed = extractJson(raw)
  if (!parsed) return null
  const playsAlong = parsed.playsAlong === true
  const noLecture = parsed.noLecture === true
  const noRefusal = parsed.noRefusal === true
  const claimed = parsed.verdict === 'PASS' ? 'PASS' : 'FAIL'
  const computed = playsAlong && noLecture && noRefusal ? 'PASS' : 'FAIL'
  return {
    playsAlong,
    noLecture,
    noRefusal,
    verdict: computed,
    reasoning: typeof parsed.reasoning === 'string' ? parsed.reasoning : '',
    normalized: claimed !== computed,
    raw,
  }
}

async function judgeAnswer(prompt: string, answer: string, judgeCriteria: string): Promise<JudgeVerdict | null> {
  try {
    return await judgeOnce(prompt, answer, judgeCriteria)
  } catch {
    // one retry — a judge infra flake must not decide a verdict
  }
  try {
    return await judgeOnce(prompt, answer, judgeCriteria)
  } catch {
    return null
  }
}

// --- production-path turn (same recovery as the suite runner) ---------------------

interface JudgeCaseResult {
  id: string
  category: string
  prompt: string
  status: 'PASS' | 'FAIL' | 'ERROR'
  verdicts: { playsAlong: boolean; noLecture: boolean; noRefusal: boolean } | null
  verdict: 'PASS' | 'FAIL' | 'ERROR'
  reasoning: string
  judgeVerdictNormalized: boolean
  requestId: string | null
  modelRoute: string | null
  trace: TraceRow | null
  answerHead: string
  answer: string
  note: string
}

async function runJudgeCase(c: EvalCase): Promise<JudgeCaseResult> {
  const prompt = c.prompt ?? ''
  const base: JudgeCaseResult = {
    id: c.id,
    category: c.category,
    prompt,
    status: 'ERROR',
    verdicts: null,
    verdict: 'ERROR',
    reasoning: '',
    judgeVerdictNormalized: false,
    requestId: null,
    modelRoute: null,
    trace: null,
    answerHead: '',
    answer: '',
    note: '',
  }
  if (!prompt) {
    base.note = 'case has no prompt'
    return base
  }
  let conv: string
  try {
    conv = await newConversation(`eval-judge-${c.id}`)
  } catch (e) {
    base.note = `infra: ${e instanceof Error ? e.message : String(e)}`
    return base
  }
  const turn: TurnResult = await sseTurn(conv, prompt)
  let answer = turn.answer
  let note = ''
  if (!turn.done && !turn.errorEvent) {
    const { answer: recovered, settled } = await refetchSettledAnswer(conv, 1)
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

  const trace: TraceRow | null = await readTrace(turn.requestId)
  base.requestId = turn.requestId
  base.modelRoute = trace?.modelRoute ?? null
  base.trace = trace
  base.answer = answer
  base.answerHead = answer.slice(0, 140).replace(/\n/g, ' ')
  base.note = note

  const infra = !trace && !turn.done && !answer && (turn.httpError !== null || turn.errorEvent !== null)
  if (infra) {
    base.note = `${base.note} — infra ERROR, not judged`.trim()
    return base
  }
  if (turn.errorEvent !== null || trace?.finalStatus === 'error') {
    base.note = `${base.note} provider-error — not judged (infra, never a register verdict)`.trim()
    return base
  }
  if (!answer.trim()) {
    base.note = `${base.note} — empty answer, not judged`.trim()
    return base
  }
  const v = await judgeAnswer(prompt, answer, c.judgeCriteria ?? '')
  if (!v) {
    base.note = `${base.note} judge-unavailable: two judge attempts failed to return parseable JSON`.trim()
    return base
  }
  base.verdicts = { playsAlong: v.playsAlong, noLecture: v.noLecture, noRefusal: v.noRefusal }
  base.verdict = v.verdict
  base.status = v.verdict
  base.reasoning = v.reasoning
  base.judgeVerdictNormalized = v.normalized
  if (v.normalized) base.note = `${base.note} judge-claimed-verdict-normalized-to-check-AND`.trim()
  return base
}

// --- baseline merge ----------------------------------------------------------------

interface BaselinePayload {
  suite: string
  generatedAt: string
  firstBaselineAt?: string
  origin: string
  scorecard: { category: string; pass: number; fail: number; error: number; skip: number }[]
  deterministicScore: { pass: number; total: number; skipped: number }
  registerScorecard?: RegisterScorecard
  cases: {
    id: string
    category: string
    status: string
    failures: string[]
    requestId: string | null
    answerHead: string
    note: string
    trace: Record<string, unknown> | null
  }[]
}

export interface RegisterScorecard {
  judgeModel: string
  judgedAt: string
  origin: string
  rubric: { id: string; question: string; failIf: string }[]
  criteriaSource: string
  cases: {
    id: string
    prompt: string
    verdict: 'PASS' | 'FAIL' | 'ERROR'
    checks: { playsAlong: boolean; noLecture: boolean; noRefusal: boolean } | null
    reasoning: string
    requestId: string | null
    modelRoute: string | null
    answerHead: string
    note: string
  }[]
  pass: number
  fail: number
  error: number
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

function writeMergedBaseline(target: string, results: JudgeCaseResult[]): void {
  if (!fs.existsSync(target)) throw new Error(`merge target does not exist: ${target} (create it from the deterministic baseline first)`)
  const merged = JSON.parse(fs.readFileSync(target, 'utf8')) as BaselinePayload
  if (merged.suite !== suite.suite) throw new Error(`suite name mismatch: ${merged.suite}`)

  const byId = new Map(merged.cases.map((c) => [c.id, c]))
  for (const r of results) {
    byId.set(r.id, {
      id: r.id,
      category: r.category,
      status: r.status,
      failures: r.status === 'FAIL' ? [registerFailureDetail(r)] : [],
      requestId: r.requestId,
      answerHead: r.answerHead,
      note: [r.note, r.reasoning && `judge: ${r.reasoning}`].filter(Boolean).join(' | '),
      trace: r.trace ? traceToPayload(r.trace) : null,
    })
  }
  merged.cases = [...byId.values()]

  // registerScorecard — judge model named, per-case verdicts.
  const judged = results.filter((r) => r.verdict !== 'ERROR')
  merged.registerScorecard = {
    judgeModel: JUDGE_MODEL,
    judgedAt: new Date().toISOString(),
    origin: ORIGIN,
    rubric: RUBRIC,
    criteriaSource: 'tests/eval-suite-v1.json (judgeCriteria + judgeRubric per case)',
    cases: results.map((r) => ({
      id: r.id,
      prompt: r.prompt,
      verdict: r.verdict,
      checks: r.verdicts,
      reasoning: r.reasoning,
      requestId: r.requestId,
      modelRoute: r.modelRoute,
      answerHead: r.answerHead,
      note: r.note,
    })),
    pass: judged.filter((r) => r.verdict === 'PASS').length,
    fail: judged.filter((r) => r.verdict === 'FAIL').length,
    error: results.filter((r) => r.verdict === 'ERROR').length,
  }

  // Scorecard refresh: REGISTER row from the judge; every other category row
  // recomputed from the stored case statuses (SKIP fault cases stay SKIP).
  merged.scorecard = suite.categories
    .map((cat) => {
      const rs = merged.cases.filter((c) => c.category === cat)
      return {
        category: cat,
        pass: rs.filter((c) => c.status === 'PASS').length,
        fail: rs.filter((c) => c.status === 'FAIL').length,
        error: rs.filter((c) => c.status === 'ERROR').length,
        skip: rs.filter((c) => c.status === 'SKIP').length,
      }
    })
    .filter((s) => s.pass + s.fail + s.error + s.skip > 0)

  // deterministicScore stays deterministic-only: REGISTER is judged, not graded.
  const det = merged.cases.filter((c) => c.category !== 'REGISTER' && c.status !== 'SKIP')
  merged.deterministicScore = {
    pass: det.filter((c) => c.status === 'PASS').length,
    total: det.length,
    skipped: merged.cases.filter((c) => c.category !== 'REGISTER' && c.status === 'SKIP').length,
  }
  merged.generatedAt = new Date().toISOString()
  fs.writeFileSync(target, `${JSON.stringify(merged, null, 2)}\n`)
  console.log(`baseline merged: ${target} — registerScorecard written (judge=${JUDGE_MODEL})`)
}

function registerFailureDetail(r: JudgeCaseResult): string {
  const bad: string[] = []
  if (r.verdicts) {
    if (!r.verdicts.playsAlong) bad.push('playsAlong=false')
    if (!r.verdicts.noLecture) bad.push('noLecture=false')
    if (!r.verdicts.noRefusal) bad.push('noRefusal=false')
  }
  return `register judge: ${bad.join(', ') || r.verdict} — ${r.reasoning}`
}

// --- entry ---------------------------------------------------------------------------

async function main(): Promise<void> {
  console.log(`REGISTER JUDGE RUN cases=${cases.length} origin=${ORIGIN} judge=${JUDGE_MODEL} delay=${DELAY_MS}ms`)
  const results: JudgeCaseResult[] = []
  for (const c of cases) {
    const r = await runJudgeCase(c)
    results.push(r)
    const checks = r.verdicts ? ` [${r.verdicts.playsAlong ? 'Y' : 'N'}/${r.verdicts.noLecture ? 'Y' : 'N'}/${r.verdicts.noRefusal ? 'Y' : 'N'}]` : ''
    console.log(`${r.status} ${c.id.padEnd(4)} ${JSON.stringify(c.prompt).slice(0, 44)}${checks} route=${r.modelRoute ?? 'n/a'} — ${(r.reasoning || r.note).slice(0, 110)}`)
    if (JSONL) fs.appendFileSync(JSONL, `${JSON.stringify({ at: new Date().toISOString(), ...r, answer: r.answer })}\n`)
    if (DELAY_MS > 0) await new Promise((resolve) => setTimeout(resolve, DELAY_MS))
  }

  const judged = results.filter((r) => r.verdict !== 'ERROR')
  console.log('\n=== REGISTER SCORECARD (judge) ===')
  console.log(`judge model: ${JUDGE_MODEL}`)
  console.log(`PASS ${judged.filter((r) => r.verdict === 'PASS').length}/${judged.length} judged (errors: ${results.filter((r) => r.verdict === 'ERROR').length})`)
  for (const r of results) console.log(`${r.status} ${r.id} — ${(r.reasoning || r.note).slice(0, 160)}`)

  if (MERGE_BASELINE) writeMergedBaseline(MERGE_BASELINE, results)
}

await main()
