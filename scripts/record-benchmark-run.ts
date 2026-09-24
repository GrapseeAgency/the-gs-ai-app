/**
 * GS BENCH — Phase 7: ingest gs-bench result files into benchmark_runs.
 *
 * Usage: bun run scripts/record-benchmark-run.ts <result.json> [more.json ...]
 * Reads the unified result schema (cli/main.py make_result) verbatim.
 * Never invents values: missing fields stay null; BLOCKED rows keep their
 * raw_reason. Safe to re-run (upsert on runId).
 */
import { readFileSync } from 'node:fs'
import { db } from '../src/lib/db'

type ResultFile = {
  run_id?: string
  model?: string
  benchmark?: string
  dataset_revision?: string
  harness?: string
  harness_version?: string
  docker_image_digest?: string
  infrastructure?: Record<string, unknown>
  started_at?: string
  completed_at?: string
  n_samples?: number
  metric?: string
  score?: number | null
  score_stderr?: number | null
  score_ci_lo?: number | null
  score_ci_hi?: number | null
  git_commit?: string
  raw_log_url?: string
  status?: string
  raw_reason?: string
  failed_count?: number
  errored_count?: number
  skipped_count?: number
  exploit_rate?: number | null
  contamination_flag?: string | null
  contamination_score?: number | null
  cost?: { spent_usd?: number } | null
}

function iso(value?: string): Date | null {
  if (!value) return null
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? null : d
}

async function main() {
  const files = process.argv.slice(2)
  if (files.length === 0) {
    console.error('usage: bun run scripts/record-benchmark-run.ts <result.json> [...]')
    process.exit(2)
  }
  for (const path of files) {
    const r = JSON.parse(readFileSync(path, 'utf8')) as ResultFile
    if (!r.run_id || !r.model || !r.benchmark) {
      console.error(`SKIP ${path}: missing run_id/model/benchmark`)
      continue
    }
    const row = await db.benchmarkRun.upsert({
      where: { runId: r.run_id },
      create: {
        runId: r.run_id,
        model: r.model!,
        benchmark: r.benchmark!,
        datasetRevision: r.dataset_revision ?? null,
        harness: r.harness ?? 'unknown',
        harnessVersion: r.harness_version ?? null,
        dockerImageDigest: r.docker_image_digest ?? null,
        infrastructure: JSON.stringify(r.infrastructure ?? {}),
        startedAt: iso(r.started_at),
        completedAt: iso(r.completed_at),
        nSamples: r.n_samples ?? null,
        metric: r.metric ?? null,
        score: r.score ?? null,
        scoreStderr: r.score_stderr ?? null,
        scoreCiLo: r.score_ci_lo ?? null,
        scoreCiHi: r.score_ci_hi ?? null,
        gitCommit: r.git_commit ?? null,
        rawLogUrl: r.raw_log_url ?? null,
        costUsd: r.cost?.spent_usd ?? null,
        failedCount: r.failed_count ?? 0,
        erroredCount: r.errored_count ?? 0,
        skippedCount: r.skipped_count ?? 0,
        exploitRate: r.exploit_rate ?? null,
        contaminationFlag: r.contamination_flag ?? null,
        contaminationScore: r.contamination_score ?? null,
        status: r.status ?? 'done',
        rawReason: r.raw_reason ?? null,
      },
      update: {
        status: r.status ?? 'done',
        score: r.score ?? null,
        scoreStderr: r.score_stderr ?? null,
        scoreCiLo: r.score_ci_lo ?? null,
        scoreCiHi: r.score_ci_hi ?? null,
        completedAt: iso(r.completed_at),
        rawReason: r.raw_reason ?? null,
        failedCount: r.failed_count ?? 0,
        erroredCount: r.errored_count ?? 0,
        skippedCount: r.skipped_count ?? 0,
      },
    })
    console.log(`recorded ${path} -> benchmark_runs runId=${row.runId} status=${row.status}`)
  }
  await db.$disconnect()
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
