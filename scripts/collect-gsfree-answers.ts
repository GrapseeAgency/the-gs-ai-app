/**
 * PHASE 4 DATA COLLECTOR — GS Free register turns (answers only, judged later).
 *
 * The z-ai gateway (which the pinned judge rides) is quota-starved to a
 * trickle (~1 call/40 min — evidence in worklog REGISTER-ROUTING-FIX-V1),
 * while the OpenRouter free chains are fully operational. The judge transport
 * recovering later must not force re-running 50 turns, so this harness
 * records the production-path ANSWERS now (turn = one full production SSE run
 * through the messages route with GS_FORCE_FREE_CHAIN=1 — the identical wire
 * the multitrial harness uses) and a batch judge scores the recorded
 * (prompt, answer) pairs when the gateway recovers (same judge semantics on
 * recorded pairs — the established judge-determinism-probe precedent).
 *
 * Route guard: every recorded turn must carry an openrouter/ modelRoute;
 * zai/* rows are rejected and re-run (a silent fallback would fake the column).
 *
 * Idempotent/resumable; serial with pacing (app rate limiter proven safe at
 * conc=1 + 15s).
 *
 * Usage:
 *   bun scripts/collect-gsfree-answers.ts --budget-ms 400000 [--trials 5]
 *       [--cases J51,...] [--pacing-ms 15000]
 * Output: /tmp/gsfree-answers/<case>-t<n>.jsonl
 */

import fs from 'node:fs'
import path from 'node:path'

import { initTurnClient, newConversation, readTrace, refetchSettledAnswer, sseTurn } from './lib/eval-turn-client'

const ROOT = path.resolve(import.meta.dir, '..')
const TMP = '/tmp/gsfree-answers'

const argv = process.argv.slice(2)
function argOf(flag: string, fallback: string): string {
  const i = argv.indexOf(flag)
  return i >= 0 && argv[i + 1] !== undefined ? argv[i + 1] : fallback
}
const BUDGET_MS = Number(argOf('--budget-ms', '400000'))
const TRIALS = Math.max(1, Number(argOf('--trials', '5')))
const PACING_MS = Math.max(0, Number(argOf('--pacing-ms', '15000')))
const CASES = argOf('--cases', 'J51,J52,J53,J54,J55,J56,J57,J58,J59,J60').split(',').map((s) => s.trim()).filter(Boolean)
const MAKEUP_SLOTS = 4

const APP_VERSION = '0.68.2'
initTurnClient({ origin: 'http://localhost:3000', appVersion: APP_VERSION })

const suite = JSON.parse(fs.readFileSync(path.join(ROOT, 'tests', 'eval-suite-v1.json'), 'utf8')) as {
  cases: { id: string; category: string; prompt?: string | null }[]
}
const caseById = new Map(suite.cases.map((c) => [c.id, c]))

fs.mkdirSync(TMP, { recursive: true })
// GS_FORCE_FREE_CHAIN=1 must be ON in the SERVER for the free chains to serve
// register turns (the capability router locks register to zai otherwise). The
// server env is set at boot; the child env here is irrelevant — but we refuse
// to run when the server was not booted with the flag: the first turn's
// modelRoute tells the truth and the route guard rejects zai/* rows anyway.

interface AnswerRow {
  at: string
  id: string
  trial: number
  status: 'RECORDED' | 'ERROR'
  modelRoute: string | null
  requestId: string | null
  answerHead: string
  answer: string
  note: string
}

function unitFile(caseId: string, trial: number): string {
  return path.join(TMP, `${caseId}-t${trial}.jsonl`)
}

function readRow(file: string): AnswerRow | null {
  try {
    const text = fs.readFileSync(file, 'utf8').trim()
    if (!text) return null
    return JSON.parse(text.split('\n').pop() as string) as AnswerRow
  } catch {
    return null
  }
}

function unitDone(file: string): boolean {
  const row = readRow(file)
  return row !== null && row.status === 'RECORDED'
}

async function runUnit(caseId: string, trial: number): Promise<void> {
  const c = caseById.get(caseId)
  const file = unitFile(caseId, trial)
  const prompt = c?.prompt ?? ''
  let row: AnswerRow
  if (!prompt) {
    row = { at: new Date().toISOString(), id: caseId, trial, status: 'ERROR', modelRoute: null, requestId: null, answerHead: '', answer: '', note: 'case missing/empty prompt' }
  } else {
    try {
      const conv = await newConversation(`gsfree-answer-${caseId}-t${trial}`)
      const turn = await sseTurn(conv, prompt)
      let answer = turn.answer
      let note = ''
      if (!turn.done && !turn.errorEvent) {
        const { answer: recovered, settled } = await refetchSettledAnswer(conv, 1)
        if (recovered !== null && settled) {
          answer = recovered
          note = 'recovered-persisted'
        } else if (recovered !== null) {
          answer = recovered
          note = 'UNSETTLED recovery'
        } else {
          note = 'view ended before done; no persisted answer'
        }
      }
      if (turn.errorEvent) note = `error event: ${turn.errorEvent.slice(0, 120)}`
      if (turn.httpError) note = `${note} http=${turn.httpError}`.trim()
      const trace = await readTrace(turn.requestId)
      const modelRoute = trace?.modelRoute ?? null
      const okRoute = typeof modelRoute === 'string' && modelRoute.startsWith('openrouter/')
      if (!okRoute || turn.errorEvent !== null || !answer.trim()) {
        row = { at: new Date().toISOString(), id: caseId, trial, status: 'ERROR', modelRoute, requestId: turn.requestId, answerHead: answer.slice(0, 100), answer, note: `route-guard/${note} — not recorded`.trim() }
      } else {
        row = { at: new Date().toISOString(), id: caseId, trial, status: 'RECORDED', modelRoute, requestId: turn.requestId, answerHead: answer.slice(0, 140).replace(/\n/g, ' '), answer, note }
      }
    } catch (e) {
      row = { at: new Date().toISOString(), id: caseId, trial, status: 'ERROR', modelRoute: null, requestId: null, answerHead: '', answer: '', note: `infra: ${e instanceof Error ? e.message : String(e)}`.slice(0, 200) }
    }
  }
  fs.appendFileSync(file, `${JSON.stringify(row)}\n`)
}

async function main(): Promise<void> {
  const start = Date.now()
  console.log(`GSFREE-ANSWERS cases=${CASES.length} trials=${TRIALS} budget=${BUDGET_MS}ms pacing=${PACING_MS}ms`)
  let recorded = 0
  let errors = 0
  let lastAt = 0
  const units: { caseId: string; trial: number }[] = []
  for (let t = 1; t <= TRIALS + MAKEUP_SLOTS; t++) {
    for (const caseId of CASES) {
      if (t > TRIALS) {
        const done = Array.from({ length: TRIALS + MAKEUP_SLOTS }, (_, i) => i + 1).filter((tt) => unitDone(unitFile(caseId, tt))).length
        if (done >= TRIALS) continue
      }
      if (unitDone(unitFile(caseId, t))) continue
      units.push({ caseId, trial: t })
    }
  }
  for (const u of units) {
    if (Date.now() - start > BUDGET_MS) {
      console.log(`BUDGET reached — ${units.length - recorded - errors} units left for next invocation`)
      break
    }
    const since = Date.now() - lastAt
    if (lastAt > 0 && since < PACING_MS) await new Promise((r) => setTimeout(r, PACING_MS - since))
    lastAt = Date.now()
    await runUnit(u.caseId, u.trial)
    const row = readRow(unitFile(u.caseId, u.trial))
    if (row?.status === 'RECORDED') {
      recorded++
      console.log(`RECORDED ${u.caseId}-t${u.trial} route=${row.modelRoute} — ${row.answerHead.slice(0, 70)}`)
    } else {
      errors++
      console.log(`ERROR ${u.caseId}-t${u.trial} — ${(row?.note ?? 'no row').slice(0, 90)}`)
    }
  }
  let total = 0
  for (const caseId of CASES) {
    let n = 0
    for (let t = 1; t <= TRIALS + MAKEUP_SLOTS; t++) if (unitDone(unitFile(caseId, t))) n++
    total += n
    console.log(`${caseId} recorded=${n}/${TRIALS}`)
  }
  console.log(`TOTAL recorded=${total}/${CASES.length * TRIALS} (errors this run: ${errors})`)
  if (total === CASES.length * TRIALS) console.log('ALL ANSWERS RECORDED — run the batch judge when the z-ai gateway recovers')
  else console.log('INCOMPLETE — invoke again to continue')
}

await main()
