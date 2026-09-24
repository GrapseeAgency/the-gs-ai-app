/**
 * REGISTRY COMPUTE (measurement tooling) — derives per-model measured
 * capability scores from the existing eval artifacts. No network, no product
 * code. Output feeds the literals in src/lib/model-capabilities.ts; a score
 * without an artifact is forbidden, so every cell prints its artifact + n.
 *
 * Attribution rule (from the models.ts wiring):
 *   zai/gs-balanced   → zai glm-4.6            (TEXT_COMPLEX/CODING/DEEP tier)
 *   zai/gs-swift      → zai glm-4.5-flash      (TEXT_SIMPLE tier, empty pool)
 *   openrouter/gs-balanced label → openrouter gs-free-big chain (ultra-550b)
 *                                      [register-class/WEB/forceFree tiers]
 *   openrouter/gs-swift    label → openrouter gs-free chain (super-120b)
 *                                      [TEXT_SIMPLE tier, keys present]
 * Category → capability mapping:
 *   instruction ← INSTRUCTION + ROUTING
 *   reasoning   ← CONTEXT_ISOLATION + FAILURE_HONESTY
 *   search      ← SOURCE_QUALITY
 *   register    ← REGISTER (llm-judge; multi-trial from register-multitrial)
 * Latency: p50 of production trace latencyMs per attributed model.
 */

import fs from 'node:fs'
import path from 'node:path'

const ROOT = path.resolve(import.meta.dir, '..')

interface ArtCase {
  id: string
  category: string
  status: string
  trace?: { modelRoute?: string | null; latencyMs?: number | null } | null
  modelRoute?: string | null
  latencyMs?: number | null
}

function attribute(c: ArtCase): string | null {
  const label = c.trace?.modelRoute ?? c.modelRoute ?? null
  if (!label) return null
  const [backend, internal] = label.split('/')
  if (backend === 'zai') {
    if (internal === 'gs-balanced') return 'zai/gs-balanced (glm-4.6)'
    if (internal === 'gs-swift') return 'zai/gs-swift (glm-4.5-flash)'
    return `zai/${internal}`
  }
  if (backend === 'openrouter') {
    // openrouter labels carry the TIER id; the executed chain follows the wiring
    if (internal === 'gs-swift') return 'openrouter/gs-free (nemotron-3-super-120b)'
    if (internal === 'gs-balanced') return 'openrouter/gs-free-big (nemotron-3-ultra-550b)'
    return `openrouter/${internal}`
  }
  return null
}

const CAP_OF: Record<string, string> = {
  INSTRUCTION: 'instruction',
  ROUTING: 'instruction',
  CONTEXT_ISOLATION: 'reasoning',
  FAILURE_HONESTY: 'reasoning',
  SOURCE_QUALITY: 'search',
  REGISTER: 'register',
}

const score = new Map<string, Map<string, { pass: number; total: number; lat: number[]; artifact: string }>>()
function bump(model: string, cap: string, pass: boolean, artifact: string, latencyMs?: number | null): void {
  if (!score.has(model)) score.set(model, new Map())
  const m = score.get(model)!
  if (!m.has(cap)) m.set(cap, { pass: 0, total: 0, lat: [], artifact })
  const cell = m.get(cap)!
  cell.total++
  if (pass) cell.pass++
  if (typeof latencyMs === 'number' && latencyMs > 0) cell.lat.push(latencyMs)
}

// deterministic artifacts (per-case status + trace)
for (const f of ['baseline-v3.json', 'baseline-v3-gsfree.json']) {
  const b = JSON.parse(fs.readFileSync(path.join(ROOT, 'tests', f), 'utf8')) as { cases: ArtCase[] }
  for (const c of b.cases) {
    if (c.status === 'SKIP') continue
    const cap = CAP_OF[c.category]
    const model = attribute(c)
    if (!cap || !model) continue
    bump(model, cap, c.status === 'PASS', `tests/${f}`, c.trace?.latencyMs)
  }
}

// multi-trial register (z-ai column) — per-trial verdicts
const mtPath = path.join(ROOT, 'tests', 'register-multitrial-v1.json')
if (fs.existsSync(mtPath)) {
  const mt = JSON.parse(fs.readFileSync(mtPath, 'utf8')) as {
    configs?: Record<string, { cases?: { id: string; trials?: { verdict?: string; modelRoute?: string | null; latencyMs?: number | null }[] }[] }>
  }
  for (const [, cfg] of Object.entries(mt.configs ?? {})) {
    for (const c of cfg.cases ?? []) {
      for (const t of c.trials ?? []) {
        if (t.verdict !== 'PASS' && t.verdict !== 'FAIL') continue
        const label = t.modelRoute ?? ''
        const model = label.startsWith('zai/gs-balanced')
          ? 'zai/gs-balanced (glm-4.6)'
          : label.startsWith('zai/gs-swift')
            ? 'zai/gs-swift (glm-4.5-flash)'
            : label.startsWith('openrouter/')
              ? label.includes('gs-free-big')
                ? 'openrouter/gs-free-big (nemotron-3-ultra-550b)'
                : 'openrouter/gs-free (nemotron-3-super-120b)'
              : null
        if (!model) continue
        bump(model, 'register', t.verdict === 'PASS', 'tests/register-multitrial-v1.json', t.latencyMs)
      }
    }
  }
}

// production latency for z-ai register trials lives in the archived multitrial
// unit JSONLs (each row carries the full trace row) — /tmp/register-multitrial/<config>/<case>-t<n>.jsonl
for (const dir of ['/tmp/register-multitrial/zai', '/tmp/register-multitrial/gsfree', '/tmp/register-multitrial-v1-archived/zai', '/tmp/register-multitrial-v1-archived/gsfree']) {
  if (!fs.existsSync(dir)) continue
  for (const f of fs.readdirSync(dir)) {
    if (!f.endsWith('.jsonl')) continue
    try {
      const lines = fs.readFileSync(path.join(dir, f), 'utf8').trim().split('\n')
      const row = JSON.parse(lines[lines.length - 1]) as {
        verdict?: string
        modelRoute?: string | null
        trace?: { latencyMs?: number | null } | null
      }
      if (row.verdict !== 'PASS' && row.verdict !== 'FAIL') continue
      const label = row.modelRoute ?? ''
      const model = label.startsWith('zai/gs-balanced')
        ? 'zai/gs-balanced (glm-4.6)'
        : label.startsWith('zai/gs-swift')
          ? 'zai/gs-swift (glm-4.5-flash)'
          : label.startsWith('openrouter/')
            ? label.includes('gs-free-big')
              ? 'openrouter/gs-free-big (nemotron-3-ultra-550b)'
              : 'openrouter/gs-free (nemotron-3-super-120b)'
            : null
      if (!model) continue
      const lat = row.trace?.latencyMs
      if (typeof lat === 'number' && lat > 0) {
        const m = score.get(model)
        const cell = m?.get('register')
        if (cell) cell.lat.push(lat)
      }
    } catch {
      /* skip unreadable unit */
    }
  }
}

// PHASE 1 artifact (gs-swift direct) — folded in when present
const p1Path = path.join(ROOT, 'tests', 'register-swift-direct-v1.json')
if (fs.existsSync(p1Path)) {
  const p1 = JSON.parse(fs.readFileSync(p1Path, 'utf8')) as {
    cases?: { id: string; trials?: { verdict?: string; latencyMs?: number | null }[] }[]
  }
  for (const c of p1.cases ?? []) {
    for (const t of c.trials ?? []) {
      if (t.verdict !== 'PASS' && t.verdict !== 'FAIL') continue
      // DIRECT call — latency is NOT a production trace; count verdict only
      bump('zai/gs-swift (glm-4.5-flash)', 'register', t.verdict === 'PASS', 'tests/register-swift-direct-v1.json', null)
    }
  }
}

// latency p50 / p95 (production traces only; nearest-rank percentiles)
function p50(xs: number[]): number | null {
  if (xs.length === 0) return null
  const s = [...xs].sort((a, b) => a - b)
  return s[Math.floor((s.length - 1) / 2)]
}
function p95(xs: number[]): number | null {
  if (xs.length === 0) return null
  const s = [...xs].sort((a, b) => a - b)
  return s[Math.min(s.length - 1, Math.floor(0.95 * (s.length - 1) + 0.5))]
}

/** Wilson 95% score interval — binomial proportion CI, valid at small n. */
function wilson(pass: number, total: number): [number, number] | null {
  if (total <= 0) return null
  const z = 1.959963984540054 // 95%
  const p = pass / total
  const denom = 1 + (z * z) / total
  const center = (p + (z * z) / (2 * total)) / denom
  const half = (z * Math.sqrt((p * (1 - p)) / total + (z * z) / (4 * total * total))) / denom
  return [round3(Math.max(0, center - half)), round3(Math.min(1, center + half))]
}
function round3(x: number): number {
  return Math.round(x * 1000) / 1000
}
function fmtWilson(pass: number, total: number): string {
  const ci = wilson(pass, total)
  return ci ? `[${ci[0].toFixed(3)}, ${ci[1].toFixed(3)}]` : '-'
}

console.log('=== MEASURED REGISTRY (per model × capability) ===')
for (const [model, caps] of [...score.entries()].sort()) {
  const allLat = [...caps.values()].flatMap((c) => c.lat)
  console.log(`\n${model}  (overall production p50 latency: ${p50(allLat) ?? '-'}ms, p95: ${p95(allLat) ?? '-'}ms, n=${allLat.length})`)
  for (const [cap, cell] of [...caps.entries()].sort()) {
    const l = p50(cell.lat)
    console.log(
      `  ${cap.padEnd(12)} ${cell.pass}/${cell.total} = ${(cell.pass / cell.total).toFixed(3)}  ci=${fmtWilson(cell.pass, cell.total)}  p50lat=${l ?? '-'}ms (n=${cell.lat.length})  artifact=${cell.artifact}`,
    )
  }
}

// ---------------------------------------------------------------------------
// SUB-CAPABILITY MATRIX (query-level granularity, HyDRA-style shortfall input)
//
// A sub-capability cell is scored ONLY from cases that a documented, class-
// level mapping ties to it. No case → no score → UNMEASURED (never guessed).
// Mapping is by case id + the suite's own assertion types:
//  register    ← multitrial/baseline register cases grouped by the ROUTER's
//                class detectors (the same detector that routes them):
//                casual (plain social openers), banter (humor/lol), sarcasm
//                (mock enthusiasm), teasing (ai-tease/mock insult), emotional
//                (emotional + social-nuance).
//  instruction ← assertion-backed: oneWord (single-token reply contracts),
//                wordCount (response.wordCount), format (response.lineCount),
//                conditional (if-then prompt I16). negation/unseenConstraint:
//                no case exists in any artifact → UNMEASURED (phase 4 suite
//                adds unseenConstraint).
//  search      ← assertion-backed: retrieval (sourcesReadMin), citation
//                (citationsWithinSources), diversity (domainsMin). freshness:
//                no date assertion exists → UNMEASURED.
//  reasoning   ← the deterministic CONTEXT_ISOLATION/FAILURE_HONESTY cases do
//                not isolate arithmetic/logic/multiStep/counterfactual → all
//                UNMEASURED at query granularity (aggregate score only).
// ---------------------------------------------------------------------------
const REGISTER_SUBCAP: Record<string, string[]> = {
  casual: ['J57', 'J60'],
  banter: ['J51', 'J52', 'J59'],
  sarcasm: ['J55'],
  teasing: ['J53', 'J58'],
  emotional: ['J54', 'J56'],
}
const INSTRUCTION_SUBCAP: Record<string, string[]> = {
  oneWord: ['I11', 'I12', 'I14', 'I17', 'I18'],
  wordCount: ['I13'],
  format: ['I19'],
  conditional: ['I16'],
  negation: [],
  unseenConstraint: [],
}
const SEARCH_SUBCAP: Record<string, string[]> = {
  retrieval: ['S40'],
  citation: ['S31', 'S32', 'S33', 'S34', 'S35', 'S36', 'S37', 'S38', 'S39', 'S40'],
  diversity: ['S31', 'S32', 'S33', 'S34', 'S35', 'S36', 'S37'],
  freshness: [],
}
const REASONING_SUBCAP: Record<string, string[]> = {
  arithmetic: [],
  logic: [],
  multiStep: [],
  counterfactual: [],
}
const SUBCAPS: Record<string, Record<string, string[]>> = {
  register: REGISTER_SUBCAP,
  instruction: INSTRUCTION_SUBCAP,
  search: SEARCH_SUBCAP,
  reasoning: REASONING_SUBCAP,
}

interface SubCell {
  pass: number
  total: number
  artifact: string
}
const subScore = new Map<string, Map<string, Map<string, SubCell>>>()
function bumpSub(model: string, cap: string, sub: string, pass: boolean, artifact: string): void {
  if (!SUBCAPS[cap]?.[sub]) return
  if (SUBCAPS[cap][sub].length === 0) return // no defining cases → stays UNMEASURED
  if (!subScore.has(model)) subScore.set(model, new Map())
  if (!subScore.get(model)!.has(cap)) subScore.get(model)!.set(cap, new Map())
  const cell = subScore.get(model)!.get(cap)!.get(sub)
  if (!cell) {
    subScore.get(model)!.get(cap)!.set(sub, { pass: pass ? 1 : 0, total: 1, artifact })
  } else {
    cell.total++
    if (pass) cell.pass++
  }
}

function attrOf(c: ArtCase): string | null {
  return attribute(c)
}

// deterministic artifacts → sub-caps
for (const f of ['baseline-v3.json', 'baseline-v3-gsfree.json']) {
  const b = JSON.parse(fs.readFileSync(path.join(ROOT, 'tests', f), 'utf8')) as { cases: ArtCase[] }
  for (const c of b.cases) {
    if (c.status === 'SKIP') continue
    const cap = CAP_OF[c.category]
    const model = attrOf(c)
    if (!cap || !model || cap === 'register') continue // register sub-caps come from trial-level data below
    for (const [sub, ids] of Object.entries(SUBCAPS[cap] ?? {})) {
      if (ids.includes(c.id)) bumpSub(model, cap, sub, c.status === 'PASS', `tests/${f}`)
    }
  }
}

// register multi-trial (z-ai column) → sub-caps, trial-level
{
  const mt = JSON.parse(fs.readFileSync(mtPath, 'utf8')) as {
    configs?: Record<string, { cases?: { id: string; trials?: { verdict?: string; modelRoute?: string | null }[] }[] }>
  }
  for (const [, cfg] of Object.entries(mt.configs ?? {})) {
    for (const c of cfg.cases ?? []) {
      for (const t of c.trials ?? []) {
        if (t.verdict !== 'PASS' && t.verdict !== 'FAIL') continue
        const label = t.modelRoute ?? ''
        const model = label.startsWith('zai/gs-balanced')
          ? 'zai/gs-balanced (glm-4.6)'
          : label.startsWith('zai/gs-swift')
            ? 'zai/gs-swift (glm-4.5-flash)'
            : label.startsWith('openrouter/')
              ? label.includes('gs-free-big')
                ? 'openrouter/gs-free-big (nemotron-3-ultra-550b)'
                : 'openrouter/gs-free (nemotron-3-super-120b)'
              : null
        if (!model) continue
        for (const [sub, ids] of Object.entries(REGISTER_SUBCAP)) {
          if (ids.includes(c.id)) bumpSub(model, 'register', sub, t.verdict === 'PASS', 'tests/register-multitrial-v1.json')
        }
      }
    }
  }
}

// PHASE 1 direct artifact → gs-swift register sub-caps (verdict only)
if (fs.existsSync(p1Path)) {
  const p1 = JSON.parse(fs.readFileSync(p1Path, 'utf8')) as {
    cases?: { id: string; trials?: { verdict?: string }[] }[]
  }
  for (const c of p1.cases ?? []) {
    for (const t of c.trials ?? []) {
      if (t.verdict !== 'PASS' && t.verdict !== 'FAIL') continue
      for (const [sub, ids] of Object.entries(REGISTER_SUBCAP)) {
        if (ids.includes(c.id)) bumpSub('zai/gs-swift (glm-4.5-flash)', 'register', sub, t.verdict === 'PASS', 'tests/register-swift-direct-v1.json')
      }
    }
  }
}

console.log('\n=== SUB-CAPABILITY MATRIX (query-level; empty cell = UNMEASURED) ===')
for (const [model, caps] of [...subScore.entries()].sort()) {
  console.log(`\n${model}`)
  for (const [cap, subs] of [...caps.entries()].sort()) {
    for (const [sub, cell] of [...subs.entries()].sort()) {
      console.log(`  ${cap}.${sub.padEnd(16)} ${cell.pass}/${cell.total} = ${(cell.pass / cell.total).toFixed(3)}  ci=${fmtWilson(cell.pass, cell.total)}  artifact=${cell.artifact}`)
    }
  }
}

// --- machine-readable output for the registry literal refresh ----------------
if (process.argv.includes('--json')) {
  const out: Record<string, unknown> = {}
  for (const [model, caps] of [...score.entries()].sort()) {
    const allLat = [...caps.values()].flatMap((c) => c.lat)
    const measured: Record<string, unknown> = {}
    for (const [cap, cell] of [...caps.entries()].sort()) {
      measured[cap] = {
        rate: round3(cell.pass / cell.total),
        pass: cell.pass,
        total: cell.total,
        ci: wilson(cell.pass, cell.total),
        source: cell.artifact,
      }
    }
    out[model] = {
      latency: { p50: p50(allLat), p95: p95(allLat), n: allLat.length },
      measured,
      capabilities: (() => {
        const m = subScore.get(model)
        if (!m) return {}
        const o: Record<string, Record<string, unknown>> = {}
        for (const [cap, subs] of [...m.entries()].sort()) {
          o[cap] = {}
          for (const [sub, cell] of [...subs.entries()].sort()) {
            o[cap][sub] = {
              rate: round3(cell.pass / cell.total),
              pass: cell.pass,
              total: cell.total,
              ci: wilson(cell.pass, cell.total),
              source: cell.artifact,
            }
          }
        }
        return o
      })(),
    }
  }
  console.log('\n=== REGISTRY JSON ===')
  console.log(JSON.stringify(out, null, 2))
}
