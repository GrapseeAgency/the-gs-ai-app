/**
 * COLLECTOR — REGISTER ROUTING FIX (phases 1+3+4 artifacts).
 *
 * Reads the per-trial unit JSONLs produced by:
 *   /tmp/register-multitrial/zai        — phase 3 (production path, NEW
 *                                         capability-registry router, keypool
 *                                         parked → all-zai column)
 *   /tmp/register-multitrial/gsfree     — phase 4 (forced GS Free chains)
 *   /tmp/register-swift-direct          — phase 1 (gs-swift DIRECT experiment)
 *
 * Writes the commit artifacts:
 *   tests/register-multitrial-v2.json — RAW: every trial per case per config
 *   tests/baseline-v5.json            — SUMMARY: before/after, phase-1
 *                                        verdict, per-case pass rates, unstable
 *
 * Scoring protocol (unchanged): score = passed_trials / total_trials over
 * EXACTLY 5 judged trials per case (no best-of, no averaging); UNSTABLE when
 * 1-4 of 5; route guard per config; ERROR trials re-run, never scored.
 *
 * Usage: bun scripts/collect-register-multitrial-v2.ts
 */

import fs from 'node:fs'
import path from 'node:path'

const ROOT = path.resolve(import.meta.dir, '..')
const TMP = '/tmp/register-multitrial'
const TMP_P1 = '/tmp/register-swift-direct'

const suite = JSON.parse(fs.readFileSync(path.join(ROOT, 'tests', 'eval-suite-v1.json'), 'utf8')) as {
  suite: string
  cases: { id: string; category: string; prompt?: string | null; judgeCriteria?: string }[]
}
const J_CASES = suite.cases.filter((c) => c.category === 'REGISTER' && c.id.startsWith('J'))
const P1_CASES = ['J52', 'J53', 'J55', 'J56', 'J58', 'J59']
const TRIALS_REQUIRED = 5

interface UnitRow {
  at: string
  id: string
  verdict: 'PASS' | 'FAIL' | 'ERROR'
  verdicts: { playsAlong: boolean; noLecture: boolean; noRefusal: boolean } | null
  reasoning: string
  model?: string
  modelRoute?: string | null
  requestId?: string | null
  latencyMs?: number | null
  answerHead?: string
  answer: string
  note: string
  trial?: number
}

interface ScoredTrial {
  trialFile: string
  verdict: 'PASS' | 'FAIL'
  checks: { playsAlong: boolean; noLecture: boolean; noRefusal: boolean } | null
  reasoning: string
  modelRoute: string | null
  requestId: string | null
  answer: string
  at: string
}

function readJsonl(file: string): UnitRow | null {
  try {
    const text = fs.readFileSync(file, 'utf8').trim()
    if (!text) return null
    return JSON.parse(text.split('\n').pop() as string) as UnitRow
  } catch {
    return null
  }
}

function routeOk(config: string, route: string | null): boolean {
  if (!route) return false
  return config === 'zai' ? route.startsWith('zai/') : route.startsWith('openrouter/')
}

interface CaseResult {
  id: string
  prompt: string
  judged: number
  passed: number
  passRate: string
  unstable: boolean
  trials: ScoredTrial[]
  errorAttempts: { file: string; note: string; verdict: string }[]
  rejectedRouteMismatch: { file: string; modelRoute: string | null }[]
  excludedExtra: { file: string; verdict: string; at: string }[]
}

function collectConfigDir(config: string, dir: string, caseIds: string[]): { cases: CaseResult[]; judgedTotal: number; passedTotal: number } {
  const errDir = path.join(dir, 'errors')
  const cases: CaseResult[] = []
  let judgedTotal = 0
  let passedTotal = 0
  for (const id of caseIds) {
    const meta = J_CASES.find((c) => c.id === id)
    const trialFiles: string[] = []
    for (let t = 1; t <= 12; t++) {
      const f = path.join(dir, `${id}-t${t}.jsonl`)
      if (fs.existsSync(f)) trialFiles.push(f)
    }
    const scored: ScoredTrial[] = []
    const rejected: { file: string; modelRoute: string | null }[] = []
    const extras: { file: string; verdict: string; at: string }[] = []
    for (const f of trialFiles) {
      const row = readJsonl(f)
      if (!row) continue
      if (row.verdict === 'PASS' || row.verdict === 'FAIL') {
        const route = config === 'p1' ? `direct/${row.model ?? 'glm-4.5-flash'}` : row.modelRoute ?? null
        if (config !== 'p1' && !routeOk(config, row.modelRoute ?? null)) {
          rejected.push({ file: path.basename(f), modelRoute: row.modelRoute ?? null })
          continue
        }
        scored.push({
          trialFile: path.basename(f),
          verdict: row.verdict,
          checks: row.verdicts,
          reasoning: row.reasoning,
          modelRoute: route,
          requestId: row.requestId ?? null,
          answer: row.answer,
          at: row.at,
        })
      }
    }
    scored.sort((a, b) => a.at.localeCompare(b.at))
    const inScore = scored.slice(0, TRIALS_REQUIRED)
    for (const extra of scored.slice(TRIALS_REQUIRED)) {
      extras.push({ file: extra.trialFile, verdict: extra.verdict, at: extra.at })
    }
    const passed = inScore.filter((t) => t.verdict === 'PASS').length
    judgedTotal += inScore.length
    passedTotal += passed
    const errorAttempts: { file: string; note: string; verdict: string }[] = []
    if (fs.existsSync(errDir)) {
      for (const f of fs.readdirSync(errDir).filter((n) => n.startsWith(`${id}-`)).sort()) {
        const row = readJsonl(path.join(errDir, f))
        if (row) errorAttempts.push({ file: f, note: row.note ?? '', verdict: row.verdict })
      }
    }
    cases.push({
      id,
      prompt: meta?.prompt ?? '',
      judged: inScore.length,
      passed,
      passRate: `${passed}/${inScore.length}`,
      unstable: inScore.length === TRIALS_REQUIRED && passed >= 1 && passed <= 4,
      trials: inScore,
      errorAttempts,
      rejectedRouteMismatch: rejected,
      excludedExtra: extras,
    })
  }
  return { cases, judgedTotal, passedTotal }
}

function hasData(dir: string, caseIds: string[]): boolean {
  if (!fs.existsSync(dir)) return false
  return fs.readdirSync(dir).some((n) => n.endsWith('.jsonl') && caseIds.some((id) => n.startsWith(id)))
}

function byModelRoute(cases: CaseResult[]): { modelRoute: string; judged: number; passed: number }[] {
  return Object.entries(
    cases
      .flatMap((c) => c.trials)
      .reduce<Record<string, { judged: number; passed: number }>>((acc, t) => {
        const k = t.modelRoute ?? 'unknown'
        acc[k] = acc[k] ?? { judged: 0, passed: 0 }
        acc[k].judged++
        if (t.verdict === 'PASS') acc[k].passed++
        return acc
      }, {}),
  ).map(([modelRoute, v]) => ({ modelRoute, ...v }))
}

const generatedAt = new Date().toISOString()
const ALL_J = J_CASES.map((c) => c.id)

// phase 1 — direct experiment
const p1Dir = TMP_P1
const p1HasData = hasData(p1Dir, P1_CASES)
const p1 = p1HasData ? collectConfigDir('p1', p1Dir, P1_CASES) : null
// phase 3 — production path, new router, parked keypool (all-zai)
const zaiHasData = hasData(path.join(TMP, 'zai'), ALL_J)
const zai = zaiHasData ? collectConfigDir('zai', path.join(TMP, 'zai'), ALL_J) : null
// phase 4 — forced GS Free
const gsfreeHasData = hasData(path.join(TMP, 'gsfree'), ALL_J)
const gsfree = gsfreeHasData ? collectConfigDir('gsfree', path.join(TMP, 'gsfree'), ALL_J) : null

if (!p1 && !zai && !gsfree) {
  console.log('NO DATA in /tmp/register-swift-direct nor /tmp/register-multitrial/{zai,gsfree} — nothing to collect')
  process.exit(1)
}

const phase1Verdict = p1
  ? p1.passedTotal >= 24
    ? `ROUTING INVERSION CONFIRMED: gs-swift direct pass ${p1.passedTotal}/30 >= 24/30 (80%) — the fix is capability-based routing to gs-swift`
    : `CAPABILITY PROBLEM: gs-swift direct pass ${p1.passedTotal}/30 < 24/30 — neither z-ai model is register-capable`
  : 'PENDING (no phase-1 data collected)'

const raw = {
  suite: suite.suite,
  kind: 'register-routing-fix-raw',
  generatedAt,
  protocol: {
    trialsPerCaseRequired: TRIALS_REQUIRED,
    judge: {
      model: 'glm-4.6',
      temperature: 0,
      source: 'scripts/run-register-judge.ts (judge semantics unchanged: same model, temperature 0, prompt bytes, rubric, verdict normalization; transport moved onto the internal dev-only eval relay — z-ai gateway 429s fresh processes, evidence in the relay route header)',
    },
    rubricSource: 'tests/eval-suite-v1.json judgeRubric + judgeCriteria per case (unchanged)',
    promptsUnchanged: true,
    wirePath: {
      phase1: 'direct model call (glm-4.5-flash, SYSTEM_PROMPT, temp 0.2) via the internal eval relay — planRoute NOT involved',
      phase3: 'scripts/lib/eval-turn-client.ts → production messages route with the CAPABILITY-REGISTRY router (fresh conversation per trial)',
      phase4: 'same production path with GS_FORCE_FREE_CHAIN=1 → OpenRouter free chains',
    },
    scoring: 'passed_trials / total_trials over exactly 5 judged trials per case; ERROR trials re-run, never scored; no best-of',
    routeGuard: "phase-3 column scores only modelRoute zai/*; phase-4 column only openrouter/*; phase-1 rows are direct/glm-4.5-flash by construction",
  },
  phase1: p1
    ? {
        label: 'CONTROLLED EXPERIMENT — 6 failing register cases on gs-swift DIRECTLY (router bypassed)',
        model: 'glm-4.5-flash (PROVIDER_MODELS["gs-swift"])',
        cases: p1.cases,
        aggregate: { judged: p1.judgedTotal, passed: p1.passedTotal, passRate: `${p1.passedTotal}/${p1.judgedTotal} judged` },
        verdict: phase1Verdict,
      }
    : { status: 'PENDING' },
  phase3: zai
    ? {
        label: 'FULL REGISTER SUITE × 5 TRIALS THROUGH THE PRODUCTION PATH (SSE, capability-registry router included, keypool parked → all-zai)',
        cases: zai.cases,
        aggregate: { judged: zai.judgedTotal, passed: zai.passedTotal, passRate: `${zai.passedTotal}/${zai.judgedTotal} judged` },
        byModelRoute: byModelRoute(zai.cases),
      }
    : { status: 'PENDING' },
  phase4: gsfree
    ? {
        label: 'GS FREE COLUMNS — forced free chains through the production path',
        cases: gsfree.cases,
        aggregate: { judged: gsfree.judgedTotal, passed: gsfree.passedTotal, passRate: `${gsfree.passedTotal}/${gsfree.judgedTotal} judged` },
        byModelRoute: byModelRoute(gsfree.cases),
      }
    : { status: 'PENDING' },
}

const before = {
  value: '20/50',
  detail: 'gs-balanced 5/35 (register-detector cases, glm-4.6), gs-swift 15/15 (non-detector casual cases, glm-4.5-flash) — tests/baseline-v4.json, pre-registry router',
}

const summary = {
  suite: suite.suite,
  kind: 'register-routing-fix-summary',
  generatedAt,
  baselineLineage:
    'baseline-v4 = BEFORE (tier-label router: register-detector cases → zai/gs-balanced 5/35, casual cases → zai/gs-swift 15/15; aggregate 20/50). This file = AFTER (capability-registry router): register turns select the measured-best model (zai/gs-swift 1.000) and gs-balanced is BARRED (register 0.143 → neverServe).',
  phase1: raw.phase1,
  beforeAfter: {
    before,
    after: zai
      ? {
          value: `${zai.passedTotal}/${zai.judgedTotal} judged${zai.judgedTotal < 50 ? ' (SHORT OF 50 — infra shortfall, see raw)' : ''}`,
          byModelRoute: byModelRoute(zai.cases),
          fixed: zai.judgedTotal === 50 && zai.passedTotal >= 40,
          rule: '>= 40/50 → the routing inversion is fixed; < 40/50 → inspect which cases routed where and why',
        }
      : 'PENDING',
  },
  perCase: ALL_J.map((id) => {
    const meta = J_CASES.find((c) => c.id === id)
    const zc = zai?.cases.find((c) => c.id === id)
    const pc = p1?.cases.find((c) => c.id === id)
    const gc = gsfree?.cases.find((c) => c.id === id)
    return {
      case: id,
      prompt: meta?.prompt ?? '',
      before: { passRate: ['J51', 'J54', 'J57', 'J60'].includes(id) ? '5/5' : '0/5', route: ['J51', 'J52', 'J53', 'J55', 'J56', 'J58', 'J59'].includes(id) ? 'zai/gs-balanced' : 'zai/gs-swift' },
      afterZai: zc ? { passRate: zc.passRate, judged: zc.judged, unstable: zc.unstable, verdicts: zc.trials.map((t) => t.verdict), modelRoute: zc.trials[0]?.modelRoute ?? null } : null,
      phase1Direct: pc ? { passRate: pc.passRate, judged: pc.judged, verdicts: pc.trials.map((t) => t.verdict) } : null,
      gsfree: gc ? { passRate: gc.passRate, judged: gc.judged, unstable: gc.unstable, verdicts: gc.trials.map((t) => t.verdict), modelRoute: gc.trials[0]?.modelRoute ?? null } : null,
    }
  }),
  unstable: [
    ...(zai ?? { cases: [] as CaseResult[] }).cases.filter((c) => c.unstable).map((c) => ({ case: c.id, config: 'after-zai', detail: `${c.passed}/5 — variance is real: ${c.trials.map((t) => t.verdict).join(',')}` })),
    ...(gsfree ?? { cases: [] as CaseResult[] }).cases.filter((c) => c.unstable).map((c) => ({ case: c.id, config: 'gsfree', detail: `${c.passed}/5 — variance is real: ${c.trials.map((t) => t.verdict).join(',')}` })),
  ],
}

fs.writeFileSync(path.join(ROOT, 'tests', 'register-multitrial-v2.json'), `${JSON.stringify(raw, null, 2)}\n`)
fs.writeFileSync(path.join(ROOT, 'tests', 'baseline-v5.json'), `${JSON.stringify(summary, null, 2)}\n`)

console.log('=== REGISTER ROUTING FIX SUMMARY (baseline-v5) ===')
if (p1) {
  console.log(`\nPHASE 1 (gs-swift DIRECT, 6 failing cases): ${p1.passedTotal}/${p1.judgedTotal}`)
  for (const c of p1.cases) console.log(`  ${c.id} ${c.passRate}${c.judged < TRIALS_REQUIRED ? ` (judged ${c.judged}/5)` : ''}`)
  console.log(`  verdict: ${phase1Verdict}`)
}
if (zai) {
  console.log(`\nPHASE 3 (production path, new router): ${zai.passedTotal}/${zai.judgedTotal}`)
  for (const c of zai.cases) console.log(`  ${c.id} ${c.passRate}${c.unstable ? ' UNSTABLE' : ''}${c.judged < TRIALS_REQUIRED ? ` (judged ${c.judged}/5)` : ''}`)
  for (const r of byModelRoute(zai.cases)) console.log(`  route ${r.modelRoute}: ${r.passed}/${r.judged}`)
}
if (gsfree) {
  console.log(`\nPHASE 4 (GS Free): ${gsfree.passedTotal}/${gsfree.judgedTotal}`)
  for (const c of gsfree.cases) console.log(`  ${c.id} ${c.passRate}${c.unstable ? ' UNSTABLE' : ''}`)
}
console.log('\nwrote tests/register-multitrial-v2.json + tests/baseline-v5.json')
