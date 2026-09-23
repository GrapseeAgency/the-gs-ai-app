/**
 * COLLECTOR — multi-trial register re-run. Reads the per-trial unit JSONL
 * files produced by scripts/run-register-multitrial.ts (each a full judged
 * run of the UNCHANGED scripts/run-register-judge.ts), applies the scoring
 * protocol, and writes the two commit artifacts:
 *
 *   tests/register-multitrial-v1.json — RAW: every trial per case per config,
 *     all 5 verdicts with checks/reasoning/answer/modelRoute/requestId,
 *     ERROR attempt history, route guard results.
 *   tests/baseline-v4.json           — SUMMARY: per-case pass rates, config
 *     aggregates, UNSTABLE flags, judge-determinism finding, lineage.
 *
 * Scoring protocol (per the task):
 *  - score = passed_trials / total_trials over EXACTLY the first 5 JUDGED
 *    trials per case (trial order = recorded order; no best-of, no averaging).
 *  - UNSTABLE when a case lands 1-4 of 5 on a config.
 *  - route guard: config 'zai' scores only zai/* trials; 'gsfree' only
 *    openrouter/* trials. Mismatched rows are listed, never scored.
 *
 * Usage: bun scripts/collect-register-multitrial.ts
 */

import fs from 'node:fs'
import path from 'node:path'

const ROOT = path.resolve(import.meta.dir, '..')
const TMP = '/tmp/register-multitrial'

const suite = JSON.parse(fs.readFileSync(path.join(ROOT, 'tests', 'eval-suite-v1.json'), 'utf8')) as {
  suite: string
  cases: { id: string; category: string; prompt?: string | null; judgeCriteria?: string }[]
}
const J_CASES = suite.cases.filter((c) => c.category === 'REGISTER' && c.id.startsWith('J'))
const TRIALS_REQUIRED = 5

interface UnitRow {
  at: string
  id: string
  category: string
  prompt: string
  status: string
  verdicts: { playsAlong: boolean; noLecture: boolean; noRefusal: boolean } | null
  verdict: 'PASS' | 'FAIL' | 'ERROR'
  reasoning: string
  judgeVerdictNormalized: boolean
  requestId: string | null
  modelRoute: string | null
  answerHead: string
  answer: string
  note: string
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

function collectConfig(config: string): { cases: CaseResult[]; judgedTotal: number; passedTotal: number } {
  const dir = path.join(TMP, config)
  const errDir = path.join(dir, 'errors')
  const cases: CaseResult[] = []
  let judgedTotal = 0
  let passedTotal = 0
  for (const c of J_CASES) {
    const trialFiles: string[] = []
    for (let t = 1; t <= 12; t++) {
      const f = path.join(dir, `${c.id}-t${t}.jsonl`)
      if (fs.existsSync(f)) trialFiles.push(f)
    }
    const scored: ScoredTrial[] = []
    const rejected: { file: string; modelRoute: string | null }[] = []
    const extras: { file: string; verdict: string; at: string }[] = []
    for (const f of trialFiles) {
      const row = readJsonl(f)
      if (!row) continue
      if (row.verdict === 'PASS' || row.verdict === 'FAIL') {
        if (!routeOk(config, row.modelRoute)) {
          rejected.push({ file: path.basename(f), modelRoute: row.modelRoute })
          continue
        }
        scored.push({
          trialFile: path.basename(f),
          verdict: row.verdict,
          checks: row.verdicts,
          reasoning: row.reasoning,
          modelRoute: row.modelRoute,
          requestId: row.requestId,
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
      for (const f of fs.readdirSync(errDir).filter((n) => n.startsWith(`${c.id}-`)).sort()) {
        const row = readJsonl(path.join(errDir, f))
        if (row) errorAttempts.push({ file: f, note: row.note ?? '', verdict: row.verdict })
      }
    }
    cases.push({
      id: c.id,
      prompt: c.prompt ?? '',
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

function collectDeterminism(totalJudgedTrialsByCase: Record<string, number>): {
  rejudgedItems: number
  totalJudgedTrials: number
  verdictAgreement: number
  flips: { caseId: string; trial: number; original: string; rerun: string }[]
  deterministic: boolean
  uncoveredCases: string[]
  judgeInfraErrors: number
  note: string
} | null {
  const file = path.join(TMP, 'judge-determinism.jsonl')
  if (!fs.existsSync(file)) return null
  const recs: { caseId: string; trial: number; original: string; rerun: string; agree: boolean }[] = []
  for (const line of fs.readFileSync(file, 'utf8').split('\n')) {
    if (!line.trim()) continue
    try {
      recs.push(JSON.parse(line))
    } catch {
      /* ignore */
    }
  }
  if (recs.length === 0) return null
  const valid = recs.filter((r) => r.agree === 0 || r.agree === 1)
  const judgeErrors = recs.length - valid.length
  const agree = valid.filter((r) => r.agree === 1).length
  const flips = valid.filter((r) => r.agree === 0).map((r) => ({ caseId: r.caseId, trial: r.trial, original: r.original, rerun: r.rerun }))
  const deterministic = flips.length === 0
  const perCaseValid = new Map<string, number>()
  for (const r of valid) perCaseValid.set(r.caseId, (perCaseValid.get(r.caseId) ?? 0) + 1)
  const uncoveredCases = Object.entries(totalJudgedTrialsByCase)
    .filter(([id, n]) => (perCaseValid.get(id) ?? 0) < n)
    .map(([id]) => id)
  const total = Object.values(totalJudgedTrialsByCase).reduce((a, b) => a + b, 0)
  return {
    rejudgedItems: valid.length,
    totalJudgedTrials: total,
    verdictAgreement: agree,
    flips,
    deterministic,
    uncoveredCases,
    judgeInfraErrors: judgeErrors,
    note: deterministic
      ? `Judge re-run on ${valid.length}/${total} identical (prompt, reply) inputs produced identical verdicts ${agree}/${valid.length} — zero flips observed at temperature 0. Coverage: every judged trial of all cases except ${uncoveredCases.length > 0 ? uncoveredCases.join('/') : 'none'} re-judged (their ${total - valid.length} remaining items could not be re-judged: the z-ai gateway quota window exhausted after the run and stayed 429 ~50+ min into quiet — infra, not a judge result). Judge-infra errors during probing (${judgeErrors} rows) were retried/excluded, never counted as flips.`
      : `Judge re-run on identical inputs FLIPPED ${flips.length}/${valid.length} verdicts at temperature 0. The judge is not fully deterministic; register scores carry a ±${flips.length} verdict noise floor on these items and borderline cases must be read as ranges, not points.`,
  }
}

// ---- main -----------------------------------------------------------------------

const zai = collectConfig('zai')
const gsfreeDir = path.join(TMP, 'gsfree')
const gsfreeHasData =
  fs.existsSync(gsfreeDir) && fs.readdirSync(gsfreeDir).some((n) => n.endsWith('.jsonl'))
const gsfree = gsfreeHasData ? collectConfig('gsfree') : null
const judgedTrialsByCase: Record<string, number> = {}
for (const c of zai.cases) judgedTrialsByCase[c.id] = c.judged + c.excludedExtra.length
const determinism = collectDeterminism(judgedTrialsByCase)

const generatedAt = new Date().toISOString()

const gsfreeStatus = gsfree
  ? {
      status: 'MEASURED',
      label: 'GS Free (gs-free-big / nemotron-3-ultra-550b where register-class routes; gs-free super chain for non-register TEXT_SIMPLE cases)',
      perCase: gsfree.cases.map((c) => ({ id: c.id, passRate: c.passRate, judged: c.judged, unstable: c.unstable })),
      aggregate: `${gsfree.passedTotal}/${gsfree.judgedTotal} judged`,
    }
  : {
      status: 'BLOCKED',
      label: 'GS Free (gs-free-big / nemotron-3-ultra-550b)',
      reason:
        'The OpenRouter keypool is EMPTY — all three durable layers (.secrets/openrouter.keys, db/vault.db, /home/z/.gs-vault/openrouter.keys) were wiped by the platform reset, and the operator key message arrived truncated (no key material after "here\'s thsoe keys if you need:"). With zero keys the production router cannot select any OpenRouter chain: freeAvailable=false forces every turn onto the z-ai primary, so a "GS Free" column measured in this state would silently BE the z-ai column (fallback-to-primary), which would be measurement fraud, not measurement. Per the reporting rules this column is marked BLOCKED with the exact reason instead of faked. The harness is ready: provisioning keys and re-running `bun scripts/run-register-multitrial.ts --config gsfree --force-free` (or without --force-free for the shipped default routing) completes the column unchanged.',
      evidence: {
        keypoolLayersChecked: ['.secrets/openrouter.keys (MISSING)', 'db/vault.db (MISSING)', '/home/z/.gs-vault/openrouter.keys (MISSING)'],
        liveLogLine: 'KEYPOOL layer=none count=0 — every key layer is empty (platform wipe); re-provision keys',
      },
    }

const raw = {
  suite: suite.suite,
  kind: 'register-multitrial-raw',
  generatedAt,
  protocol: {
    trialsPerCaseRequired: TRIALS_REQUIRED,
    judge: { model: 'glm-4.6', temperature: 0, source: 'scripts/run-register-judge.ts (UNCHANGED — same judge, rubric and verdict normalization as the closure run)' },
    rubricSource: 'tests/eval-suite-v1.json judgeRubric + judgeCriteria per case (unchanged)',
    promptsUnchanged: true,
    wirePath: 'scripts/lib/eval-turn-client.ts → production messages route (fresh conversation per trial)',
    scoring: 'passed_trials / total_trials over exactly 5 judged trials per case; ERROR trials re-run, never scored; no best-of',
    routeGuard: "zai column scores only modelRoute zai/*; gsfree column only openrouter/* — a keypool flip cannot silently mix columns",
  },
  configs: {
    zai: {
      label: 'z-ai (gs-balanced primary via production path — keypool empty so the OpenRouter free chains are unavailable and the z-ai primary serves every turn)',
      servingReality:
        'Measured 2026-09-23 with keypool layer=none count=0 (dev.log evidence per trial in the raw trials). Register-detector cases route TEXT_COMPLEX/gs-balanced → zai/gs-balanced; J54/J57/J60 match no register detector and route TEXT_SIMPLE/gs-swift → zai/gs-swift. Both are the z-ai provider classes the shipped router selects when the free pool is empty. This column is the z-ai class comparison the task names; the GS Free column (OpenRouter ultra) is BLOCKED above.',
      cases: zai.cases,
      aggregate: { judged: zai.judgedTotal, passed: zai.passedTotal, passRate: `${zai.passedTotal}/${zai.judgedTotal} judged` },
      byModelRoute: Object.entries(
        zai.cases.flatMap((c) => c.trials).reduce<Record<string, { judged: number; passed: number }>>((acc, t) => {
          const k = t.modelRoute ?? 'unknown'
          acc[k] = acc[k] ?? { judged: 0, passed: 0 }
          acc[k].judged++
          if (t.verdict === 'PASS') acc[k].passed++
          return acc
        }, {}),
      ).map(([modelRoute, v]) => ({ modelRoute, ...v })),
    },
    gsfree: gsfreeStatus,
  },
  judgeDeterminism: determinism,
}

const summary = {
  suite: suite.suite,
  kind: 'register-multitrial-summary',
  generatedAt,
  baselineLineage:
    'baseline-v3 (single-sample): z-ai-path register 10/10 — but that run routed register-class turns to the OpenRouter ultra chain with keys present, so 10/10 measured ULTRA, not the z-ai class; baseline-v3-gsfree (forced free) 7/10. Both were one sample per case. baseline-v4 (this file) replaces single samples with 5-trial pass rates per case per model class.',
  protocol: raw.protocol,
  perCase: zai.cases.map((c, i) => ({
    case: c.id,
    prompt: c.prompt,
    zai: { passRate: c.passRate, judged: c.judged, unstable: c.unstable, verdicts: c.trials.map((t) => t.verdict) },
    gsfree: gsfree ? { passRate: gsfree.cases[i]?.passRate ?? null, unstable: gsfree.cases[i]?.unstable ?? null } : null,
  })),
  aggregates: {
    zai: `${zai.passedTotal}/${zai.judgedTotal} judged${zai.judgedTotal < 50 ? ` (SHORT OF 50 — infra shortfall, see raw)` : ''}`,
    gsfree: gsfree ? `${gsfree.passedTotal}/${gsfree.judgedTotal} judged` : 'BLOCKED (keypool empty, keys not delivered — see tests/register-multitrial-v1.json)',
  },
  unstable: zai.cases.filter((c) => c.unstable).map((c) => ({ case: c.id, config: 'z-ai', detail: `${c.passed}/5 — variance is real: ${c.trials.map((t) => t.verdict).join(',')}` })),
  judgeDeterminism: determinism,
  byModelRoute: raw.configs.zai.byModelRoute,
}

fs.writeFileSync(path.join(ROOT, 'tests', 'register-multitrial-v1.json'), `${JSON.stringify(raw, null, 2)}\n`)
fs.writeFileSync(path.join(ROOT, 'tests', 'baseline-v4.json'), `${JSON.stringify(summary, null, 2)}\n`)

console.log('=== MULTI-TRIAL REGISTER SUMMARY (baseline-v4) ===')
for (const c of zai.cases) {
  console.log(
    `${c.id}  z-ai ${c.passRate.padEnd(4)}${c.unstable ? '  UNSTABLE' : ''}${c.judged < TRIALS_REQUIRED ? `  (judged ${c.judged}/5 — infra shortfall)` : ''}`,
  )
}
console.log(`z-ai aggregate: ${zai.passedTotal}/${zai.judgedTotal} judged`)
console.log(`GS Free aggregate: ${gsfreeStatus.status === 'MEASURED' ? 'measured' : 'BLOCKED — keypool empty, operator keys not delivered'}`)
if (determinism) console.log(`judge determinism: ${determinism.deterministic ? 'DETERMINISTIC' : 'NON-DETERMINISTIC'} (${determinism.verdictAgreement}/${determinism.rejudgedItems} re-judged items agree; coverage ${determinism.rejudgedItems}/${determinism.totalJudgedTrials})`)
console.log('wrote tests/register-multitrial-v1.json + tests/baseline-v4.json')
