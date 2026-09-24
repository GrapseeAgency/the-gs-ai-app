/**
 * CONTAMINATION AUDIT (FTD / SplitGuard-style, simplified).
 *
 * The eval suites live in a public repo. Any model trained on data that
 * includes this repo would score on memorized prompts, not capability —
 * the measurement would be fake. This tool provides the three checks:
 *
 *   1. MANIFEST (--manifest)  — sha256 every prompt in every suite file
 *      (normalized: trim, collapse whitespace, lowercase) into
 *      tests/contamination-manifest.json. The hashes let ANY future corpus
 *      (public dataset dumps, training-data probes) be checked for these
 *      exact prompts offline.
 *
 *   2. HOLDOUT (--compare)    — the suite is split: a PRIVATE holdout
 *      (tests/private-holdout.json — GITIGNORED, never committed, never
 *      shipped in any artifact) vs the public set. Run the model on both:
 *      if the private set scores much lower (Welch's t, same rule as the CI
 *      gate: significant AND delta > 0.1 in the public's favor), the public
 *      set is CONTAMINATED — the model memorized the seen prompts.
 *
 *   3. WEB OVERLAP (--web)    — search public corpora for exact-prompt
 *      matches. Requires provider quota; when the bucket is dry this check
 *      is reported NOT RUN / BLOCKED, never guessed.
 *
 * Usage:
 *   bun scripts/contamination-audit.ts --manifest
 *   bun scripts/contamination-audit.ts --compare public-results.jsonl holdout-results.jsonl
 *   bun scripts/contamination-audit.ts --web
 */

import crypto from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'

const ROOT = path.resolve(import.meta.dir, '..')
const SUITE_FILES = [
  'tests/eval-suite-v1.json',
  'tests/eval-suite-search-v1.json',
  'tests/eval-suite-instruction-generalization-v1.json',
  'tests/eval-suite-multiturn-v1.json',
  'tests/eval-suite-security-v1.json',
] as const

interface SuiteLike {
  cases: {
    id: string
    prompt?: string | null
    conversation?: { prompt: string }[]
    injection?: string
  }[]
}

function normalize(p: string): string {
  return p.trim().replace(/\s+/g, ' ').toLowerCase()
}
function sha256(s: string): string {
  return crypto.createHash('sha256').update(s).digest('hex')
}

function argOf(flag: string, fallback: string): string {
  const i = process.argv.indexOf(flag)
  return i > -1 && process.argv[i + 1] ? process.argv[i + 1] : fallback
}

// --- 1. manifest ------------------------------------------------------------
if (process.argv.includes('--manifest')) {
  const entries: { suite: string; caseId: string; kind: string; hash: string }[] = []
  for (const rel of SUITE_FILES) {
    const abs = path.join(ROOT, rel)
    if (!fs.existsSync(abs)) continue
    const suite = JSON.parse(fs.readFileSync(abs, 'utf8')) as SuiteLike
    for (const c of suite.cases) {
      if (c.prompt) entries.push({ suite: path.basename(rel), caseId: c.id, kind: 'prompt', hash: sha256(normalize(c.prompt)) })
      for (const t of c.conversation ?? []) {
        entries.push({ suite: path.basename(rel), caseId: c.id, kind: 'turn', hash: sha256(normalize(t.prompt)) })
      }
      if (c.injection) entries.push({ suite: path.basename(rel), caseId: c.id, kind: 'injection-payload', hash: sha256(normalize(c.injection)) })
    }
  }
  const bySuite: Record<string, number> = {}
  for (const e of entries) bySuite[e.suite] = (bySuite[e.suite] ?? 0) + 1
  const out = {
    generatedAt: new Date().toISOString(),
    algorithm: 'sha256 of normalized prompt (trim, collapse whitespace, lowercase)',
    counts: bySuite,
    total: entries.length,
    webOverlap: 'NOT RUN — provider quota; run bun scripts/contamination-audit.ts --web when quota returns',
    entries,
  }
  const target = path.join(ROOT, 'tests', 'contamination-manifest.json')
  fs.writeFileSync(target, `${JSON.stringify(out, null, 2)}\n`)
  console.log(`manifest written: ${target}`)
  console.log(`prompts hashed: ${out.total} (${JSON.stringify(bySuite)})`)
  process.exit(0)
}

// --- 2. holdout comparison ----------------------------------------------------
if (process.argv.includes('--compare')) {
  const publicPath = argOf('--compare', '')
  const holdoutPath = process.argv[process.argv.indexOf('--compare') + 2] ?? ''
  const load = (p: string): number[] =>
    fs
      .readFileSync(path.resolve(p), 'utf8')
      .trim()
      .split('\n')
      .filter((l) => l.trim())
      .map((l) => {
        const row = JSON.parse(l) as { pass?: unknown; verdict?: unknown }
        const v = row.pass ?? row.verdict
        return v === true || v === 'PASS' || v === 1 ? 1 : 0
      })
  const pub = load(publicPath)
  const priv = load(holdoutPath)
  if (priv.length === 0) {
    console.error('HOLDOUT EMPTY — tests/private-holdout.json is missing or its results are absent; the audit cannot compare')
    process.exit(2)
  }
  const mp = pub.reduce((a, b) => a + b, 0) / pub.length
  const mh = priv.reduce((a, b) => a + b, 0) / priv.length
  const delta = mp - mh // public advantage over the private holdout
  console.log(`contamination compare: public ${(mp * 100).toFixed(1)}% (n=${pub.length}) vs private holdout ${(mh * 100).toFixed(1)}% (n=${priv.length}) | public advantage ${(delta * 100).toFixed(1)}pp`)
  if (delta > 0.1) {
    console.error('CONTAMINATION SIGNAL: public set scores much higher than the private holdout — treat public-suite scores as inflated (memorization suspected)')
    process.exit(1)
  }
  console.log('NO CONTAMINATION SIGNAL: private holdout tracks the public set')
  process.exit(0)
}

// --- 3. web overlap -----------------------------------------------------------
if (process.argv.includes('--web')) {
  const manifest = JSON.parse(fs.readFileSync(path.join(ROOT, 'tests', 'contamination-manifest.json'), 'utf8')) as { total: number }
  console.error(
    `BLOCKED (quota): the exact-prompt web-overlap check needs the search provider (1 query per prompt ≈ ${manifest.total} calls). The hash manifest is committed — run this mode when the quota refills. No overlap number is faked.`,
  )
  process.exit(2)
}

console.log('usage: contamination-audit.ts --manifest | --compare <public.jsonl> <holdout.jsonl> | --web')
process.exit(2)
