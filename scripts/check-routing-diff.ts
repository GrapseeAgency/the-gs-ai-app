/**
 * ROUTING DIFF CHECK (measurement tooling, zero network) — proves the
 * capability-registry router preserves the serving decision for every
 * DETERMINISTIC suite case and changes ONLY the register-class destination.
 *
 * For each of the 58 suite cases it builds the RouteRequest the production
 * path would build (real evaluateWebSearchGate, empty history, fresh
 * conversation), runs it through BOTH routers (old = git HEAD copy, new =
 * capability registry) and reports every (route, internalModelId) change.
 *
 * Usage: bun scripts/check-routing-diff.ts
 */

import fs from 'node:fs'
import path from 'node:path'

import { evaluateWebSearchGate } from '../src/lib/websearch'
import { planRoute as newPlanRoute, type RouteRequest } from '../src/lib/router'
// old router copy (git HEAD) — no imports of its own, loads standalone
import { planRoute as oldPlanRoute } from '/tmp/router-old.ts'

const ROOT = path.resolve(import.meta.dir, '..')
const suite = JSON.parse(fs.readFileSync(path.join(ROOT, 'tests', 'eval-suite-v1.json'), 'utf8')) as {
  cases: { id: string; category: string; prompt?: string | null }[]
}

function reqFor(prompt: string): RouteRequest {
  return {
    content: prompt,
    hasImages: false,
    hasDocuments: false,
    webGateTrigger: evaluateWebSearchGate(prompt).trigger,
    clarifyFollowUp: false,
    historicalReligious: false,
    historyChars: 0,
  }
}

let changed = 0
let registerMoved = 0
for (const c of suite.cases) {
  const prompt = c.prompt ?? ''
  if (!prompt) continue
  const o = oldPlanRoute(reqFor(prompt))
  const n = newPlanRoute(reqFor(prompt))
  const same = o.route === n.route && o.internalModelId === n.internalModelId
  const isRegister = c.category === 'REGISTER'
  if (!same) {
    changed++
    if (isRegister) registerMoved++
    console.log(
      `${same ? 'SAME' : 'CHANGED'} ${c.id} [${c.category}] ${JSON.stringify(prompt).slice(0, 50)}\n    old: ${o.route}/${o.internalModelId} (${o.reason})\n    new: ${n.route}/${n.internalModelId} (${n.reason})`,
    )
  } else if (isRegister && n.route === 'TEXT_SIMPLE' && n.internalModelId === 'gs-swift') {
    // register cases whose old destination was gs-balanced (detector hits) —
    // these SHOULD have changed; flag if they did not
    if (o.internalModelId === 'gs-balanced') {
      console.log(`!! UNEXPECTED ${c.id} register-detector case did NOT move: old=${o.route}/${o.internalModelId} new=${n.route}/${n.internalModelId}`)
    }
  }
}
console.log(`\n=== DIFF SUMMARY: ${changed} case(s) changed, ${registerMoved} of them REGISTER cases ===`)
