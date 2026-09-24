/**
 * PHASE 1 EXPERIMENT — REGISTER CASES ON gs-swift, DIRECT (router bypassed).
 *
 * QUESTION: is the register failure a ROUTING inversion (detector correctly
 * flags register turns, then routes them to the weaker class) or a
 * CAPABILITY gap (no z-ai model holds register)?
 *
 * METHOD: the 6 failing register cases (J52/J53/J55/J56/J58/J59) × 5 trials,
 * each trial calling the gs-swift MODEL DIRECTLY — NOT through planRoute(),
 * not through the production messages route. The one changed variable vs the
 * failing runs is THE MODEL CLASS:
 *   - failing runs: register-class turn → planRoute TEXT_COMPLEX → gs-balanced
 *     → provider model glm-4.6 → 0/5 on all six cases (tests/baseline-v4.json)
 *   - this run: SAME system prompt (src/lib/ai SYSTEM_PROMPT — byte-identical
 *     to what the messages route sends on a fresh plain-text turn), SAME
 *     sampling params (temperature 0.2, thinking disabled — completeChat
 *     contract), SAME judge (glm-4.6, temperature 0, suite rubric, verdict
 *     normalization — verbatim copy of scripts/run-register-judge.ts judge
 *     internals), model = glm-4.5-flash (src/lib/models.ts PROVIDER_MODELS
 *     mapping for gs-swift).
 * No prompt is edited, no rubric is edited, no judge is changed, the router
 * is not touched. This is a measurement harness.
 *
 * TRANSPORT NOTE (2026-09-24): both the direct model call and the judge ride
 * the internal dev-only eval relay (POST /api/v1/fixtures/llm-relay — an
 * explicit-body server-side SDK call that NEVER consults the router) because
 * the z-ai gateway 429s every fresh process while the long-lived dev server
 * passes with identical config/headers (evidence in the relay route header).
 * The call remains DIRECT in the sense this experiment requires: the model id
 * and messages are explicit, planRoute/the production wire path are not
 * involved.
 *
 * VERDICT RULE (task): gs-swift ≥ 24/30 → the register problem is a routing
 * inversion and the fix is capability-based routing. gs-swift < 24/30 →
 * capability problem: neither z-ai model is register-capable.
 *
 * Resumable: a unit with a judged row (PASS/FAIL) is skipped; ERROR rows are
 * re-queued. Serial with pacing — the z-ai gateway throttles bursts.
 *
 * Usage:
 *   bun scripts/run-register-swift-direct.ts --budget-ms 420000
 *       [--trials 5] [--cases J52,J53,J55,J56,J58,J59] [--pacing-ms 15000]
 * Output: /tmp/register-swift-direct/<case>-t<n>.jsonl (one row per attempt)
 */

import fs from 'node:fs'
import path from 'node:path'

import { SYSTEM_PROMPT } from '../src/lib/ai'

const ROOT = path.resolve(import.meta.dir, '..')
const TMP = '/tmp/register-swift-direct'

// --- args --------------------------------------------------------------------

const argv = process.argv.slice(2)
function argOf(flag: string, fallback: string): string {
  const i = argv.indexOf(flag)
  return i >= 0 && argv[i + 1] !== undefined ? argv[i + 1] : fallback
}
const BUDGET_MS = Number(argOf('--budget-ms', '420000'))
const TRIALS = Math.max(1, Number(argOf('--trials', '5')))
const PACING_MS = Math.max(0, Number(argOf('--pacing-ms', '15000')))
const MAX_ATTEMPTS = Math.max(1, Number(argOf('--max-attempts', '4')))
const CASES = argOf('--cases', 'J52,J53,J55,J56,J58,J59').split(',').map((s) => s.trim()).filter(Boolean)
const MAKEUP_SLOTS = 4 // t6..t9 spare slots for ERROR makeups

const SWIFT_PROVIDER_MODEL = 'glm-4.5-flash' // PROVIDER_MODELS['gs-swift'] (src/lib/models.ts)

fs.mkdirSync(TMP, { recursive: true })

// --- suite (register cases only; prompts/rubric UNCHANGED) --------------------

interface JudgeRubricCheck {
  id: string
  question: string
  failIf: string
}
interface EvalCase {
  id: string
  category: string
  prompt?: string | null
  judgeCriteria?: string
  judgeRubric?: { checks?: JudgeRubricCheck[] }
}
const suite = JSON.parse(fs.readFileSync(path.join(ROOT, 'tests', 'eval-suite-v1.json'), 'utf8')) as {
  cases: EvalCase[]
}
const caseById = new Map(suite.cases.map((c) => [c.id, c]))
const RUBRIC: JudgeRubricCheck[] = suite.cases.find((c) => c.id === 'J51')?.judgeRubric?.checks ?? [
  { id: 'playsAlong', question: 'Plays along / matches register?', failIf: 'refuses, pivots to assistant mode, or answers in the wrong register' },
  { id: 'noLecture', question: 'Does NOT lecture about being an AI?', failIf: 'explains it is an AI/LLM/program or gives unsolicited AI-nature disclaimers' },
  { id: 'noRefusal', question: 'Does NOT refuse a benign social prompt?', failIf: 'declines, adds safety boilerplate, or redirects to professional help for a benign social prompt' },
]

// --- verbatim copy: judge internals (scripts/run-register-judge.ts) -----------

const JUDGE_MODEL = 'glm-4.6'

const EVAL_KEY = process.env.GS_EVAL_KEY ?? 'gs-eval-local'

/** Transport: the internal dev-only relay (explicit body; router not involved). */
async function relayChat(body: {
  model: string
  messages: { role: string; content: string }[]
  temperature?: number
  thinking?: { type: 'disabled' | 'enabled' }
}): Promise<{ content: string; latencyMs: number }> {
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
  const data = (await response.json()) as { content?: string; latencyMs?: number }
  if (typeof data.content !== 'string' || data.content.length === 0) throw new Error('relay empty content')
  return { content: data.content, latencyMs: typeof data.latencyMs === 'number' ? data.latencyMs : 0 }
}

interface JudgeVerdict {
  playsAlong: boolean
  noLecture: boolean
  noRefusal: boolean
  verdict: 'PASS' | 'FAIL'
  reasoning: string
  normalized: boolean
  raw: string
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
  }).then((r) => r.content)
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

// --- DIRECT model call (bypasses planRoute entirely) ---------------------------

interface DirectReply {
  answer: string
  latencyMs: number
}

async function swiftDirect(prompt: string): Promise<DirectReply> {
  // Body mirrors the production text-turn contract (completeChatWithMeta):
  // same system prompt, same sampling params — model = gs-swift's mapping.
  // Transport = the internal dev-only relay (router NOT involved; the model
  // and messages are explicit — this is still a DIRECT model call).
  const { content, latencyMs } = await relayChat({
    model: SWIFT_PROVIDER_MODEL,
    messages: [
      { role: 'system', content: SYSTEM_PROMPT },
      { role: 'user', content: prompt },
    ],
    temperature: 0.2,
    thinking: { type: 'disabled' },
  })
  return { answer: content, latencyMs }
}

// --- unit state -----------------------------------------------------------------

interface UnitRow {
  at: string
  id: string
  trial: number
  verdict: 'PASS' | 'FAIL' | 'ERROR'
  verdicts: { playsAlong: boolean; noLecture: boolean; noRefusal: boolean } | null
  reasoning: string
  model: string
  latencyMs: number | null
  answerHead: string
  answer: string
  note: string
}

function unitFile(caseId: string, trial: number): string {
  return path.join(TMP, `${caseId}-t${trial}.jsonl`)
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

function unitDone(file: string): boolean {
  const row = readRow(file)
  return row !== null && (row.verdict === 'PASS' || row.verdict === 'FAIL')
}

async function runUnit(caseId: string, trial: number): Promise<void> {
  const c = caseById.get(caseId)
  const file = unitFile(caseId, trial)
  if (!c?.prompt) {
    fs.appendFileSync(file, `${JSON.stringify({ at: new Date().toISOString(), id: caseId, trial, verdict: 'ERROR', verdicts: null, reasoning: '', model: SWIFT_PROVIDER_MODEL, latencyMs: null, answerHead: '', answer: '', note: 'case missing/empty prompt' })}\n`)
    return
  }
  let row: UnitRow
  try {
    const { answer, latencyMs } = await swiftDirect(c.prompt)
    const v = await judgeAnswer(c.prompt, answer, c.judgeCriteria ?? '')
    if (!v) {
      row = { at: new Date().toISOString(), id: caseId, trial, verdict: 'ERROR', verdicts: null, reasoning: '', model: SWIFT_PROVIDER_MODEL, latencyMs, answerHead: answer.slice(0, 140).replace(/\n/g, ' '), answer, note: 'judge-unavailable: two attempts failed' }
    } else {
      row = { at: new Date().toISOString(), id: caseId, trial, verdict: v.verdict, verdicts: { playsAlong: v.playsAlong, noLecture: v.noLecture, noRefusal: v.noRefusal }, reasoning: v.reasoning, model: SWIFT_PROVIDER_MODEL, latencyMs, answerHead: answer.slice(0, 140).replace(/\n/g, ' '), answer, note: v.normalized ? 'judge-claimed-verdict-normalized-to-check-AND' : '' }
    }
  } catch (e) {
    row = { at: new Date().toISOString(), id: caseId, trial, verdict: 'ERROR', verdicts: null, reasoning: '', model: SWIFT_PROVIDER_MODEL, latencyMs: null, answerHead: '', answer: '', note: `infra: ${e instanceof Error ? e.message : String(e)}`.slice(0, 200) }
  }
  fs.appendFileSync(file, `${JSON.stringify(row)}\n`)
}

// --- work list --------------------------------------------------------------------

interface Unit {
  caseId: string
  trial: number
  attempts: number
}

function buildWorkList(): Unit[] {
  const units: Unit[] = []
  for (let t = 1; t <= TRIALS + MAKEUP_SLOTS; t++) {
    for (const caseId of CASES) {
      if (t > TRIALS) {
        const judged = Array.from({ length: TRIALS + MAKEUP_SLOTS }, (_, i) => i + 1).filter((tt) => unitDone(unitFile(caseId, tt))).length
        if (judged >= TRIALS) continue
      }
      const file = unitFile(caseId, t)
      if (unitDone(file)) continue
      const row = readRow(file)
      if (row && row.verdict === 'ERROR') continue
      units.push({ caseId, trial: t, attempts: 0 })
    }
  }
  for (let t = 1; t <= TRIALS + MAKEUP_SLOTS; t++) {
    for (const caseId of CASES) {
      const row = readRow(unitFile(caseId, t))
      if (row && row.verdict === 'ERROR') units.push({ caseId, trial: t, attempts: 0 })
    }
  }
  return units
}

// --- runner -------------------------------------------------------------------------

let lastDispatchAt = 0

async function main(): Promise<void> {
  const start = Date.now()
  console.log(`SWIFT-DIRECT cases=${CASES.join(',')} trials=${TRIALS} model=${SWIFT_PROVIDER_MODEL} judge=${JUDGE_MODEL} budget=${BUDGET_MS}ms pacing=${PACING_MS}ms`)
  const queue = buildWorkList()
  let judged = 0
  let requeued = 0
  for (const u of queue) {
    const elapsed = Date.now() - start
    if (elapsed > BUDGET_MS) {
      console.log(`BUDGET reached (${elapsed}ms) — stopping, ${queue.length - judged - requeued}+ units left for next invocation`)
      break
    }
    u.attempts++
    if (u.attempts > MAX_ATTEMPTS) continue
    const sinceLast = Date.now() - lastDispatchAt
    if (lastDispatchAt > 0 && sinceLast < PACING_MS) {
      await new Promise((r) => setTimeout(r, PACING_MS - sinceLast))
    }
    lastDispatchAt = Date.now()
    await runUnit(u.caseId, u.trial)
    const row = readRow(unitFile(u.caseId, u.trial))
    if (row && (row.verdict === 'PASS' || row.verdict === 'FAIL')) {
      judged++
      const v = row.verdicts
      console.log(`JUDGED ${row.verdict} ${u.caseId}-t${u.trial} [${v?.playsAlong ? 'Y' : 'N'}/${v?.noLecture ? 'Y' : 'N'}/${v?.noRefusal ? 'Y' : 'N'}] ${row.latencyMs}ms — ${(row.reasoning || row.note).slice(0, 100)}`)
    } else {
      requeued++
      console.log(`ERROR ${u.caseId}-t${u.trial} — requeue (${(row?.note ?? 'no row').slice(0, 90)})`)
    }
  }

  console.log(`\n=== PROGRESS dispatched=${judged + requeued} judged=${judged} requeued=${requeued} ===`)
  let totalJudged = 0
  let totalPassed = 0
  let complete = true
  for (const caseId of CASES) {
    let caseJudged = 0
    let casePassed = 0
    for (let t = 1; t <= TRIALS + MAKEUP_SLOTS; t++) {
      const row = readRow(unitFile(caseId, t))
      if (row && (row.verdict === 'PASS' || row.verdict === 'FAIL')) {
        caseJudged++
        if (row.verdict === 'PASS') casePassed++
      }
    }
    totalJudged += caseJudged
    totalPassed += casePassed
    if (caseJudged < TRIALS) complete = false
    console.log(`${caseId} judged=${caseJudged}/${TRIALS} passed=${casePassed}`)
  }
  console.log(`TOTAL judged=${totalJudged}/${CASES.length * TRIALS} passed=${totalPassed}`)
  if (totalJudged === CASES.length * TRIALS) {
    console.log('ALL UNITS JUDGED')
    if (totalPassed >= 24) console.log(`PHASE-1 VERDICT: ${totalPassed}/30 >= 24/30 → ROUTING INVERSION (fix: capability-based routing to gs-swift)`)
    else console.log(`PHASE-1 VERDICT: ${totalPassed}/30 < 24/30 → CAPABILITY PROBLEM (no z-ai model is register-capable)`)
  } else {
    console.log('INCOMPLETE — invoke again to continue')
    void complete
  }
}

await main()
