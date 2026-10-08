/**
 * Strict assertions for GS-CONSTRAINT-GUARD (quality failures A-D from
 * tests/app-comparison/results.md, run 2026-10-08). Run: bun scripts/test-constraint-guard.ts
 * Every assertion that follows encodes one bar from the directive:
 *   A: a 5-word prompt enforces exactly 5 words, every time.
 *   B: a forbidden character never appears in the returned text.
 *   C: the t17 CSV fixture yields West = 6,150, every time.
 *   D: a stale version claim is caught against evidence; honest fallback text
 *      starts with the could-not-verify sentence.
 */
import assert from 'node:assert/strict'
import {
  detectOutputConstraints,
  constraintViolations,
  buildConstraintRetryMessages,
  repairConstraints,
  honestConstraintFailure,
  parseCsvTable,
  verifyCsvAggregates,
  applyAggregateCorrection,
  isFreshnessRequest,
  extractVersions,
  freshnessViolations,
  honestFreshnessFallback,
  countWords,
} from '../src/lib/constraint-guard'

// ---------- FAILURE A: exact word count ----------
const t04 =
  'Answer with exactly five words, no more and no less: why is the sky blue?'
const cA = detectOutputConstraints(t04)
assert.notEqual(cA, null, 'A: t04 constraint not detected')
assert.equal(cA?.exactWords, 5, 'A: exactWords must be 5')

const failedT04 = 'Sunlight scatters; short blue wavelengths dominate.' // 6 words
assert.equal(countWords(failedT04), 6)
const violsA = constraintViolations(failedT04, cA!)
assert.equal(violsA.length, 1, 'A: 6-word draft must violate')
const repairedA = repairConstraints(failedT04, cA!)
assert.notEqual(repairedA, null)
assert.equal(countWords(repairedA!), 5, 'A: repaired output must be exactly 5 words')
assert.equal(repairedA, 'Sunlight scatters; short blue wavelengths', 'A: truncation keeps first 5 words')

// under-length draft pads to exactly N (bar: exactly N, every time)
const shortA = repairConstraints('Rayleigh scattering.', cA!)
assert.notEqual(shortA, null)
assert.equal(countWords(shortA!), 5, 'A: under-length padded to exactly 5')

// retry message keeps the conversation and restates the constraint
const msgsA = buildConstraintRetryMessages(
  [
    { role: 'system', content: 'base' },
    { role: 'user', content: t04 },
  ],
  cA!
)
assert.equal(msgsA.length, 2)
assert.match(msgsA[0]!.content as string, /exactly 5 words/)

// non-constraint prompts must NOT trip the detector
assert.equal(detectOutputConstraints('What is the capital of France?'), null)
assert.equal(detectOutputConstraints('Write a story about five words a day.'), null)

// ---------- FAILURE B: forbidden character ----------
const t16 =
  "Write a single sentence of at least twelve words that does not contain the letter 'e' anywhere in it."
const cB = detectOutputConstraints(t16)
assert.notEqual(cB, null, 'B: t16 constraint not detected')
assert.deepEqual(cB?.forbiddenChars, ['e'], 'B: forbidden char must be e')
assert.equal(cB?.minWords, 12, 'B: min words 12')

const failedT16 =
  'A quick brown fox jumps over lazy dogs, but this sentence avoids e entirely.'
const violsB = constraintViolations(failedT16, cB!)
assert.ok(violsB.some((v) => v.includes("'e'")), 'B: draft with e must violate')

const repairedB = repairConstraints(failedT16, cB!)
// The failed draft has only 10 e-free words, so no deterministic repair can
// reach minWords 12: repair is null and the honest fallback ships instead
// (directive spec: "state failure honestly and return the closest valid
// output").
assert.equal(repairedB, null, 'B: unrepairable draft must fall through to honest fallback')
const honestB2 = honestConstraintFailure(failedT16, cB!)
assert.ok(honestB2.startsWith('NO VALID OUTPUT'), 'B: honest fallback announces failure')
assert.ok(!honestB2.toLowerCase().includes('e'), 'B: honest fallback itself contains no e')
assert.ok(honestB2.includes('A quick brown fox jumps lazy dogs'), 'B: closest valid output preserved')

// a draft that CAN be repaired: 12+ words once e-words are dropped
const fixableT16 = 'My big cat naps on warm sunny days and also on cold nights.'
const repairedB2 = repairConstraints(fixableT16, cB!)
assert.notEqual(repairedB2, null, 'B: e-free draft must pass through')
assert.ok(!repairedB2!.toLowerCase().includes('e'), 'B: repaired text has no e')
assert.ok(countWords(repairedB2!) >= 12, 'B: repaired text keeps min words')

// ---------- FAILURE C: CSV aggregates ----------
const csv = [
  'month,region,product,units,total_sales',
  '2026-01,North,widget,120,1200',
  '2026-01,East,widget,95,950',
  '2026-01,South,widget,60,600',
  '2026-01,West,widget,125,1250',
  '2026-02,North,gadget,80,1600',
  '2026-02,East,gadget,70,1400',
  '2026-02,South,gadget,55,1100',
  '2026-02,West,gadget,115,2300',
  '2026-03,North,widget,90,900',
  '2026-03,East,widget,85,850',
  '2026-03,South,widget,95,950',
  '2026-03,West,widget,130,2600',
  '',
].join('\n')
const table = parseCsvTable(csv)
assert.notEqual(table, null, 'C: CSV table not parsed')
assert.equal(table!.totals.get('West'), 6150, 'C: West must total 6,150')
assert.equal(table!.totals.get('North'), 3700, 'C: North must total 3,700')
assert.equal(table!.totals.get('East'), 3200, 'C: East must total 3,200')
assert.equal(table!.totals.get('South'), 2650, 'C: South must total 2,650')

const failedT17 = 'West had the highest total sales at 7350.'
const aggv = verifyCsvAggregates(failedT17, table!)
assert.equal(aggv.length, 1, 'C: wrong total must be caught')
assert.equal(aggv[0]!.claimed, 7350)
assert.equal(aggv[0]!.actual, 6150)

const correctedT17 = applyAggregateCorrection(failedT17, aggv)
assert.notEqual(correctedT17, null)
assert.ok(correctedT17!.includes('6,150'), 'C: rewrite states 6,150')
assert.ok(!correctedT17!.includes('7350'), 'C: wrong total removed')
assert.equal(verifyCsvAggregates(correctedT17!, table!).length, 0, 'C: corrected draft verifies')

// a correct draft passes untouched
const goodT17 = 'West had the highest total sales at 6,150.'
assert.equal(verifyCsvAggregates(goodT17, table!).length, 0, 'C: correct draft not flagged')

// ---------- FAILURE D: freshness ----------
const t06 =
  'What is the latest stable major version of the Next.js framework as of today, and what is one headline feature of it?'
assert.equal(isFreshnessRequest(t06), true, 'D: t06 must be freshness-critical')
assert.equal(isFreshnessRequest('What is the capital of France?'), false)

const evidence = 'Next.js 16 release notes (Oct 2025): Next.js 16.1.3 ships Turbopack by default.'
const staleDraft = 'The latest stable major release of Next.js is version 15. It ships React 19 support.'
const ungrounded = freshnessViolations(staleDraft, evidence)
assert.deepEqual(ungrounded, ['15'], 'D: stale version 15 must be ungrounded against 16.x evidence')

const freshDraft = 'The latest stable major version is 16 (16.1.3 in the retrieved release notes).'
assert.equal(freshnessViolations(freshDraft, evidence).length, 0, 'D: grounded draft passes')

// version extraction covers dotted and major-only forms
assert.deepEqual(extractVersions('version 15'), ['15'])
assert.deepEqual(extractVersions('release 16.1.3'), ['16.1.3'])

const honestD = honestFreshnessFallback(staleDraft, evidence)
assert.ok(
  honestD.startsWith('I could not verify the current version from live sources.'),
  'D: honest fallback opens with the could-not-verify sentence'
)

console.log('ALL CONSTRAINT-GUARD ASSERTIONS PASSED')
