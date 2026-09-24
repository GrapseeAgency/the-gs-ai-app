/**
 * CI GATE — SILENT-FAILURE BLOCKER.
 *
 * For every turn of an eval run (identified by its X-GS-Request-Id), fetch the
 * turn's trace row from the deployed origin's diagnostics endpoint and BLOCK
 * (exit 1) if ANY turn carries a silent-failure-gate violation. A turn that
 * answered while degrading evidence/citations/register must never gate green.
 *
 * Input: the eval run JSONL (rows must carry requestId) or a plain
 * newline list of request ids.
 *
 * Usage: bun scripts/silent-failure-ci.ts --origin "$ORIGIN_URL" --rows eval-run.jsonl
 */

import fs from 'node:fs'

function argOf(flag: string, fallback: string): string {
  const i = process.argv.indexOf(flag)
  return i > -1 && process.argv[i + 1] ? process.argv[i + 1] : fallback
}

const origin = argOf('--origin', '')
const rowsPath = argOf('--rows', '')
if (!origin || !rowsPath) {
  console.error('usage: silent-failure-ci.ts --origin <url> --rows <run.jsonl|request-ids.txt>')
  process.exit(2)
}

const raw = fs.readFileSync(rowsPath, 'utf8').trim().split('\n').filter((l) => l.trim())
const requestIds: string[] = raw
  .map((line) => {
    try {
      const row = JSON.parse(line) as { requestId?: string }
      return row.requestId ?? ''
    } catch {
      return line.trim()
    }
  })
  .filter((id) => id.length > 0)

if (requestIds.length === 0) {
  console.log('SILENT-FAILURE GATE: NEUTRAL — no request ids in the run rows')
  process.exit(0)
}

let violations = 0
let fetchErrors = 0
for (const requestId of requestIds) {
  let verdicts: string[] | null = null
  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      const res = await fetch(`${origin}/api/v1/diagnostics?requestId=${encodeURIComponent(requestId)}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const body = (await res.json()) as { trace?: { silentFailures?: string[] }[] }
      verdicts = body.trace?.[0]?.silentFailures ?? []
      fetchErrors = 0
      break
    } catch (e) {
      if (attempt === 2) {
        fetchErrors++
        console.error(`SILENT-FAILURE GATE: export failed for ${requestId}: ${e instanceof Error ? e.message : e}`)
      } else {
        await new Promise((r) => setTimeout(r, 2000))
      }
    }
  }
  if (verdicts === null) continue
  if (verdicts.length > 0) {
    violations++
    console.error(`SILENT-FAILURE GATE: requestId=${requestId} violations=[${verdicts.join(' | ')}]`)
  }
}

if (fetchErrors > 0) {
  console.error(`SILENT-FAILURE GATE: BLOCK — ${fetchErrors} turn(s) could not be exported from a reachable origin; an unexportable run cannot gate green`)
  process.exit(1)
}
if (violations > 0) {
  console.error(`SILENT-FAILURE GATE: BLOCK — ${violations} turn(s) with silent-failure violations (see above)`)
  process.exit(1)
}
console.log(`SILENT-FAILURE GATE: PASS — ${requestIds.length} turn(s) clean`)
