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
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

interface SilentFailureLogger {
  log(message: string): void
  error(message: string): void
}

interface SilentFailureCIOptions {
  origin: string
  requestIds: readonly string[]
  fetchImpl?: (url: string) => Promise<Response>
  sleep?: (milliseconds: number) => Promise<void>
  logger?: SilentFailureLogger
}

export interface SilentFailureCIResult {
  requestCount: number
  violations: string[]
  exportErrors: string[]
  exitCode: 0 | 1
}

const MAX_ATTEMPTS = 3
const RETRY_DELAY_MS = 2000

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

/**
 * Inspect every request's persisted diagnostics row. Dependencies are injectable
 * so the fail-closed retry/validation contract can be exercised hermetically.
 */
export async function runSilentFailureCI(
  options: SilentFailureCIOptions,
): Promise<SilentFailureCIResult> {
  const { origin, requestIds } = options
  const fetchImpl = options.fetchImpl ?? fetch
  const sleep = options.sleep ?? ((milliseconds) =>
    new Promise<void>((resolveSleep) => setTimeout(resolveSleep, milliseconds)))
  const logger = options.logger ?? console

  if (requestIds.length === 0) {
    logger.log('SILENT-FAILURE GATE: NEUTRAL — no request ids in the run rows')
    return { requestCount: 0, violations: [], exportErrors: [], exitCode: 0 }
  }

  const exportErrors: string[] = []
  const violations: string[] = []
  for (const requestId of requestIds) {
    let verdicts: string[] | null = null
    for (let attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
      try {
        const res = await fetchImpl(
          `${origin}/api/v1/diagnostics?requestId=${encodeURIComponent(requestId)}`,
        )
        if (!res.ok) throw new Error(`HTTP ${res.status}`)

        const body: unknown = await res.json()
        if (!isRecord(body) || !Array.isArray(body.trace)) {
          throw new Error('diagnostics response is missing its trace array')
        }
        const traceRow = body.trace.find(
          (candidate: unknown): candidate is Record<string, unknown> =>
            isRecord(candidate) && candidate.requestId === requestId,
        )
        if (!traceRow) throw new Error('no persisted trace row was returned for this request id')

        const silentFailures = traceRow.silentFailures
        if (
          !Array.isArray(silentFailures) ||
          silentFailures.some((entry: unknown) => typeof entry !== 'string')
        ) {
          throw new Error('trace row is missing a valid silentFailures array')
        }
        verdicts = silentFailures as string[]
        break
      } catch (e) {
        const message = e instanceof Error ? e.message : String(e)
        if (attempt === MAX_ATTEMPTS - 1) {
          exportErrors.push(`requestId=${requestId} error=${message}`)
          logger.error(`SILENT-FAILURE GATE: export failed for ${requestId}: ${message}`)
        } else {
          await sleep(RETRY_DELAY_MS)
        }
      }
    }

    if (verdicts === null) continue
    if (verdicts.length > 0) {
      const detail = `requestId=${requestId} violations=[${verdicts.join(' | ')}]`
      violations.push(detail)
      logger.error(`SILENT-FAILURE GATE: ${detail}`)
    }
  }

  if (exportErrors.length > 0) {
    const annotation = exportErrors.join('\n')
      .replace(/%/g, '%25')
      .replace(/\r/g, '%0D')
      .replace(/\n/g, '%0A')
    logger.error(`::error title=Diagnostics export failures (${exportErrors.length})::${annotation}`)
    logger.error(`SILENT-FAILURE GATE: BLOCK — ${exportErrors.length} turn(s) could not be exported from a reachable origin; an unexportable run cannot gate green`)
    return { requestCount: requestIds.length, violations, exportErrors, exitCode: 1 }
  }

  if (violations.length > 0) {
    const annotation = violations.join('\n')
      .replace(/%/g, '%25')
      .replace(/\r/g, '%0D')
      .replace(/\n/g, '%0A')
    logger.error(`::error title=Silent-failure violations (${violations.length})::${annotation}`)
    logger.error(`SILENT-FAILURE GATE: BLOCK — ${violations.length} turn(s) with silent-failure violations (see above)`)
    return { requestCount: requestIds.length, violations, exportErrors, exitCode: 1 }
  }

  logger.log(`SILENT-FAILURE GATE: PASS — ${requestIds.length} turn(s) clean`)
  return { requestCount: requestIds.length, violations, exportErrors, exitCode: 0 }
}

function argOf(flag: string, fallback: string): string {
  const i = process.argv.indexOf(flag)
  return i > -1 && process.argv[i + 1] ? process.argv[i + 1] : fallback
}

async function main(): Promise<void> {
  const origin = argOf('--origin', '')
  const rowsPath = argOf('--rows', '')
  if (!origin || !rowsPath) {
    console.error('usage: silent-failure-ci.ts --origin <url> --rows <run.jsonl|request-ids.txt>')
    process.exitCode = 2
    return
  }

  const raw = fs.readFileSync(rowsPath, 'utf8').trim().split('\n').filter((line) => line.trim())
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

  const result = await runSilentFailureCI({ origin, requestIds })
  process.exitCode = result.exitCode
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  await main()
}
