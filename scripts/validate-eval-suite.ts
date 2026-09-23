/**
 * EVAL INFRASTRUCTURE — SUITE VALIDATOR (Phase 4, hermetic).
 *
 * Structural gate for tests/eval-suite-v1.json. Runs in CI with NO network,
 * NO providers, NO database — it proves the suite itself is well-formed so a
 * malformed suite can never ship and silently void the gate:
 *
 *   1. valid JSON with `suite`, `categories[]`, `cases[]`
 *   2. every category referenced by a case is declared (and vice versa)
 *   3. case ids unique, prefixed per category (R/I/C/S/F, judge cases J#)
 *   4. every case has >= 1 assertion, every assertion well-typed:
 *      - trace.* assertions require `value` (number|boolean|string)
 *      - response.matchesRegex requires a regex that COMPILES (with flags)
 *      - response.contains/notContains require non-empty string `value`
 *      - response.containsAny requires non-empty `values[]`
 *      - anyOf requires non-empty `values[]` of nested well-typed assertions
 *      - response.wordCount/lineCount require non-negative integer
 *   5. every case carries `auditRef` — an assertion must map to a real audit
 *      bug class (rules: every assertion must be traceable to a real bug)
 *   6. llm_judge cases declare `judgeCriteria` and are excluded from the
 *      deterministic score; fault cases declare `skipReason`
 *   7. blocking is only allowed on ROUTING / INSTRUCTION / CONTEXT_ISOLATION
 *      (high-confidence deterministic classes — CI block set)
 *   8. baseline consistency (when tests/baseline-v1.json exists): every case
 *      id appears exactly once in the baseline; no unknown ids; baseline
 *      declares the same suite name
 *
 * Usage: bun scripts/validate-eval-suite.ts   (exit 1 on any violation)
 */

import fs from 'node:fs'
import path from 'node:path'

const ROOT = path.resolve(import.meta.dir, '..')
const SUITE_PATH = path.join(ROOT, 'tests', 'eval-suite-v1.json')
const BASELINE_PATH = path.join(ROOT, 'tests', 'baseline-v1.json')

const KNOWN_ASSERTION_TYPES = new Set([
  'trace.searchExecuted',
  'trace.capability',
  'trace.trigger',
  'trace.sourceCount',
  'trace.finalStatus',
  'trace.domainsMin',
  'trace.sourcesReadMin',
  'trace.readRate',
  'citationsWithinSources',
  'response.matchesRegex',
  'response.wordCount',
  'response.lineCount',
  'response.contains',
  'response.notContains',
  'response.containsAny',
  'anyOf',
])
const BLOCKABLE_CATEGORIES = new Set(['ROUTING', 'INSTRUCTION', 'CONTEXT_ISOLATION'])

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

const errors: string[] = []
function fail(msg: string): void {
  errors.push(msg)
}

// --- load --------------------------------------------------------------------

let suite: Suite
try {
  suite = JSON.parse(fs.readFileSync(SUITE_PATH, 'utf8')) as Suite
} catch (e) {
  console.error(`FATAL: suite JSON unparseable: ${e instanceof Error ? e.message : String(e)}`)
  process.exit(1)
}

// --- suite shape --------------------------------------------------------------

if (typeof suite.suite !== 'string' || !suite.suite) fail('suite name missing')
if (!Array.isArray(suite.categories) || suite.categories.length === 0) fail('categories[] missing')
if (!Array.isArray(suite.cases) || suite.cases.length === 0) fail('cases[] missing')

const declared = new Set(suite.categories ?? [])
const seenIds = new Set<string>()
const usedCategories = new Set<string>()
const idPrefix: Record<string, string> = {
  ROUTING: 'R',
  INSTRUCTION: 'I',
  CONTEXT_ISOLATION: 'C',
  SOURCE_QUALITY: 'S',
  FAILURE_HONESTY: 'F',
  REGISTER: 'J',
}

// --- assertion well-typedness (recursive for anyOf) ----------------------------

function checkAssertion(a: Assertion, caseId: string, depth: number): void {
  const at = `${caseId}: assertion(${a.type})`
  if (depth > 3) {
    fail(`${at}: anyOf nesting deeper than 3`)
    return
  }
  if (!KNOWN_ASSERTION_TYPES.has(a.type)) {
    fail(`${at}: unknown assertion type`)
    return
  }
  switch (a.type) {
    case 'trace.searchExecuted':
    case 'trace.capability':
    case 'trace.trigger':
    case 'trace.sourceCount':
    case 'trace.finalStatus':
    case 'trace.domainsMin':
    case 'trace.sourcesReadMin':
    case 'trace.readRate':
    case 'response.wordCount':
    case 'response.lineCount': {
      if (a.value === undefined || a.value === null) fail(`${at}: missing value`)
      else if ((a.type === 'trace.domainsMin' || a.type.startsWith('response.') || a.type === 'trace.readRate' || a.type === 'trace.sourcesReadMin') && (typeof a.value !== 'number' || a.value < 0 || !Number.isInteger(a.value) && a.type !== 'trace.readRate'))
        fail(`${at}: value must be a non-negative number`)
      break
    }
    case 'response.matchesRegex': {
      if (typeof a.value !== 'string' || !a.value) {
        fail(`${at}: value must be a regex source string`)
        break
      }
      try {
        new RegExp(a.value, a.flags ?? 'i')
      } catch (e) {
        fail(`${at}: regex does not compile — ${e instanceof Error ? e.message : String(e)}`)
      }
      break
    }
    case 'response.contains':
    case 'response.notContains': {
      if (typeof a.value !== 'string' || a.value.length === 0) fail(`${at}: value must be a non-empty string`)
      break
    }
    case 'response.containsAny': {
      if (!Array.isArray(a.values) || a.values.length === 0 || a.values.some((v) => typeof v !== 'string' || v.length === 0))
        fail(`${at}: values must be a non-empty string[]`)
      break
    }
    case 'anyOf': {
      if (!Array.isArray(a.values) || a.values.length === 0) {
        fail(`${at}: values must be a non-empty assertion[]`)
        break
      }
      for (const nested of a.values as Assertion[]) checkAssertion(nested, caseId, depth + 1)
      break
    }
  }
}

// --- case checks ----------------------------------------------------------------

for (const c of suite.cases) {
  const label = c.id || '<missing-id>'
  if (!c.id) fail(`case missing id`)
  if (seenIds.has(c.id)) fail(`duplicate case id ${c.id}`)
  seenIds.add(c.id)
  if (!c.category || !declared.has(c.category)) fail(`${label}: category "${c.category}" not declared in categories[]`)
  usedCategories.add(c.category)

  const wantPrefix = idPrefix[c.category]
  if (wantPrefix && !c.id.startsWith(wantPrefix)) fail(`${label}: id does not match category prefix ${wantPrefix}*`)

  const isJudge = c.grading === 'llm_judge'
  if (!isJudge && (!Array.isArray(c.assertions) || c.assertions.length === 0)) fail(`${label}: needs >= 1 assertion`)
  for (const a of (isJudge ? [] : c.assertions) ?? []) checkAssertion(a, c.id, 0)

  if (!c.auditRef) fail(`${label}: missing auditRef — every assertion must map to a real audit bug class`)

  if (isJudge) {
    if (!c.judgeCriteria) fail(`${label}: llm_judge case must declare judgeCriteria`)
  } else if (c.fault) {
    if (!c.skipReason) fail(`${label}: fault-injection case must declare skipReason`)
  } else if (!c.prompt) {
    fail(`${label}: deterministic case must have a prompt`)
  }

  if (c.blocking && !BLOCKABLE_CATEGORIES.has(c.category))
    fail(`${label}: blocking=true not allowed on category ${c.category} (block set is ROUTING/INSTRUCTION/CONTEXT_ISOLATION)`)
}

for (const cat of declared) if (!usedCategories.has(cat)) fail(`declared category ${cat} has no cases`)

// --- baseline consistency ---------------------------------------------------------

if (fs.existsSync(BASELINE_PATH)) {
  try {
    const baseline = JSON.parse(fs.readFileSync(BASELINE_PATH, 'utf8')) as {
      suite?: string
      cases?: { id: string; status: string }[]
    }
    if (baseline.suite !== suite.suite) fail(`baseline suite name "${baseline.suite}" != "${suite.suite}"`)
    const ids = (baseline.cases ?? []).map((c) => c.id)
    const baselineIds = new Set(ids)
    if (ids.length !== baselineIds.size) fail('baseline contains duplicate case ids')
    for (const id of baselineIds) if (!seenIds.has(id)) fail(`baseline has unknown case id ${id}`)
    for (const c of suite.cases) if (!baselineIds.has(c.id)) fail(`baseline missing case id ${c.id}`)
    for (const bc of baseline.cases ?? []) {
      if (!['PASS', 'FAIL', 'ERROR', 'SKIP'].includes(bc.status)) fail(`baseline case ${bc.id}: invalid status ${bc.status}`)
    }
  } catch (e) {
    fail(`baseline JSON unparseable: ${e instanceof Error ? e.message : String(e)}`)
  }
}

// --- verdict ------------------------------------------------------------------------

if (errors.length > 0) {
  console.error(`SUITE VALIDATION FAILED — ${errors.length} violation(s):`)
  for (const e of errors) console.error(`  - ${e}`)
  process.exit(1)
}
console.log(
  `suite ok: ${suite.suite} — ${suite.cases.length} cases across ${declared.size} categories` +
    (fs.existsSync(BASELINE_PATH) ? ', baseline consistent' : ', no baseline present (pre-Phase-3 state)')
)
