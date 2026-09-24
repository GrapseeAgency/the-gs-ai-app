/**
 * INSTRUCTION GENERALIZATION SUITE — hermetic grader self-check (no network,
 * no model). Every G-case in tests/eval-suite-instruction-generalization-v1.json
 * ships graderSelfTests: fixtures the deterministic grader MUST pass and MUST
 * fail. This proves the graders themselves before the suite ever runs against
 * a model — a broken grader would fake a capability measurement.
 *
 * Usage: bun scripts/test-instruction-graders.ts   (exit 1 on any failure)
 */

import fs from 'node:fs'
import path from 'node:path'
import { evalAssertion, type Assertion } from '../src/lib/eval-grader'

const ROOT = path.resolve(import.meta.dir, '..')
const suite = JSON.parse(
  fs.readFileSync(path.join(ROOT, 'tests', 'eval-suite-instruction-generalization-v1.json'), 'utf8'),
) as {
  cases: {
    id: string
    prompt: string
    assertions: Assertion[]
    graderSelfTests: { pass: string[]; fail: string[] }
  }[]
}

let failures = 0

for (const c of suite.cases) {
  // Compound constraints grade ALL assertions: a pass fixture must satisfy
  // every assertion; a fail fixture must violate at least one.
  for (const good of c.graderSelfTests.pass) {
    const bad = c.assertions.map((a) => evalAssertion(a, null, good)).filter((v) => !v.ok)
    if (bad.length > 0) {
      failures++
      console.error(`FAIL ${c.id} pass-fixture ${JSON.stringify(good.slice(0, 40))}: ${bad.map((v) => v.detail).join(' AND ')}`)
    }
  }
  for (const bad of c.graderSelfTests.fail) {
    const results = c.assertions.map((a) => evalAssertion(a, null, bad))
    if (results.every((v) => v.ok)) {
      failures++
      console.error(`FAIL ${c.id} fail-fixture ${JSON.stringify(bad.slice(0, 40))}: grader passed when it must fail`)
    }
  }
  if (c.assertions.length < 1) {
    failures++
    console.error(`FAIL ${c.id}: expected at least 1 deterministic grader`)
  }
}

const unseen = JSON.parse(
  fs.readFileSync(path.join(ROOT, 'tests', 'eval-suite-v1.json'), 'utf8'),
) as { cases: { id: string; prompt: string }[] }
// unseen guarantee: no G-case prompt may be a substring-equal of any v1 prompt
for (const g of suite.cases) {
  for (const v of unseen.cases) {
    if (v.prompt && g.prompt.trim().toLowerCase() === v.prompt.trim().toLowerCase()) {
      failures++
      console.error(`FAIL unseen guarantee: ${g.id} duplicates ${v.id}`)
    }
  }
}

console.log(`checked ${suite.cases.length} graders × (pass+fail fixtures) + unseen-guarantee`)
console.log(failures === 0 ? 'ALL INSTRUCTION GRADER SELF-TESTS PASS' : `${failures} SELF-TEST(S) FAILED`)
process.exit(failures === 0 ? 0 : 1)
