/**
 * CI GATE — WELCH'S T-TEST for judged categories (register, reasoning quality).
 *
 * 2026 CI/CD playbook: deterministic categories block on any failure; judged
 * categories are compared STATISTICALLY between baseline and the new run —
 * single-run point estimates flake, trials + a test do not.
 *
 * Decision rule (the playbook's):
 *   block   iff p < 0.05 AND delta < -0.10   (significant regression)
 *   pass    if  p >= 0.05                     (no significant change)
 *   pass    if  delta > 0.10                  (significant improvement)
 *   neutral if either sample has n < 2 trials (no fake significance)
 *
 * Input: two files, each a JSON array of 0/1 per-trial outcomes (or a JSONL
 * of {"pass": true|false} rows), for ONE judged metric.
 * Hermetic: pure math, no network.
 *
 * Usage: bun scripts/welch-t-gate.ts --baseline tests/judged-baseline.jsonl --run eval-run.jsonl --label register
 */

import fs from 'node:fs'

function argOf(flag: string, fallback: string): string {
  const i = process.argv.indexOf(flag)
  return i > -1 && process.argv[i + 1] ? process.argv[i + 1] : fallback
}

function loadOutcomes(path: string): number[] {
  const raw = fs.readFileSync(path, 'utf8').trim()
  if (!raw) return []
  if (raw.startsWith('[')) {
    const arr = JSON.parse(raw) as unknown[]
    return arr.map((v) => (typeof v === 'number' ? v : typeof v === 'boolean' ? (v ? 1 : 0) : Number((v as { pass?: unknown }).pass ?? 0)))
  }
  return raw
    .split('\n')
    .filter((l) => l.trim().length > 0)
    .map((l) => {
      const row = JSON.parse(l) as { pass?: unknown; verdict?: unknown }
      const v = row.pass ?? row.verdict
      return v === true || v === 'PASS' || v === 1 ? 1 : 0
    })
}

function mean(xs: number[]): number {
  return xs.reduce((a, b) => a + b, 0) / xs.length
}
function variance(xs: number[]): number {
  if (xs.length < 2) return 0
  const m = mean(xs)
  return xs.reduce((a, b) => a + (b - m) * (b - m), 0) / (xs.length - 1)
}

/** Regularized incomplete beta I_x(a,b) via continued fraction (NR style). */
function betacf(a: number, b: number, x: number): number {
  const MAXIT = 200
  const EPS = 3e-12
  const FPMIN = 1e-300
  const qab = a + b
  const qap = a + 1
  const qam = a - 1
  let c = 1
  let d = 1 - (qab * x) / qap
  if (Math.abs(d) < FPMIN) d = FPMIN
  d = 1 / d
  let h = d
  for (let m = 1; m <= MAXIT; m++) {
    const m2 = 2 * m
    let aa = (m * (b - m) * x) / ((qam + m2) * (a + m2))
    d = 1 + aa * d
    if (Math.abs(d) < FPMIN) d = FPMIN
    c = 1 + aa / c
    if (Math.abs(c) < FPMIN) c = FPMIN
    d = 1 / d
    h *= d * c
    aa = -(a + m) * (qab + m) * x / ((a + m2) * (qap + m2))
    d = 1 + aa * d
    if (Math.abs(d) < FPMIN) d = FPMIN
    c = 1 + aa / c
    if (Math.abs(c) < FPMIN) c = FPMIN
    d = 1 / d
    const del = d * c
    h *= del
    if (Math.abs(del - 1) < EPS) break
  }
  return h
}

function gammaln(x: number): number {
  const cof = [
    76.18009172947146, -86.50532032941677, 24.01409824083091, -1.231739572450155,
    0.1208650973866179e-2, -0.5395239384953e-5,
  ]
  let y = x
  let tmp = x + 5.5
  tmp -= (x + 0.5) * Math.log(tmp)
  let ser = 1.000000000190015
  for (let j = 0; j < 6; j++) ser += cof[j] / ++y
  return -tmp + Math.log((2.5066282746310005 * ser) / x)
}

function ibeta(x: number, a: number, b: number): number {
  if (x <= 0) return 0
  if (x >= 1) return 1
  const bt = Math.exp(gammaln(a + b) - gammaln(a) - gammaln(b) + a * Math.log(x) + b * Math.log(1 - x))
  if (x < (a + 1) / (a + b + 2)) return (bt * betacf(a, b, x)) / a
  return 1 - (bt * betacf(b, a, 1 - x)) / b
}

const baselinePath = argOf('--baseline', '')
const runPath = argOf('--run', '')
const label = argOf('--label', 'judged-metric')

const base = loadOutcomes(baselinePath)
const run = loadOutcomes(runPath)

if (base.length < 2 || run.length < 2) {
  console.log(
    `WELCH GATE [${label}]: NEUTRAL — trials below the significance floor (baseline n=${base.length}, run n=${run.length}); no fake significance from single runs`,
  )
  process.exit(0)
}

const m1 = mean(base)
const m2 = mean(run)
const v1 = variance(base)
const v2 = variance(run)
const n1 = base.length
const n2 = run.length
const se = Math.sqrt(v1 / n1 + v2 / n2)
const t = se === 0 ? 0 : (m2 - m1) / se
const df = se === 0 ? 1 : (v1 / n1 + v2 / n2) ** 2 / ((v1 / n1) ** 2 / (n1 - 1) + (v2 / n2) ** 2 / (n2 - 1))
// two-sided p from the t distribution: p = I_{df/(df+t²)}(df/2, 1/2)
const p = t === 0 ? 1 : ibeta(df / (df + t * t), df / 2, 0.5)
const delta = m2 - m1

console.log(
  `WELCH GATE [${label}]: baseline ${(m1 * 100).toFixed(1)}% (n=${n1}) → run ${(m2 * 100).toFixed(1)}% (n=${n2}) | delta=${(delta * 100).toFixed(1)}pp | t=${t.toFixed(3)} df=${df.toFixed(1)} p=${p.toFixed(4)}`,
)

if (p < 0.05 && delta < -0.1) {
  console.error(`WELCH GATE [${label}]: BLOCK — significant regression (p<0.05 and delta<-0.1)`)
  process.exit(1)
}
if (delta > 0.1) {
  console.log(`WELCH GATE [${label}]: PASS — significant improvement (delta>0.1)`)
} else if (p >= 0.05) {
  console.log(`WELCH GATE [${label}]: PASS — no significant change (p>=0.05)`)
} else {
  console.log(`WELCH GATE [${label}]: PASS — significant p but delta within [-0.1, 0.1] band`)
}
