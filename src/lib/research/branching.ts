/**
 * PHASE 5 — BRANCHING SEARCH (replaces linear ReAct for deep turns).
 *
 * Instead of   query → results → read → query → results → read → answer
 * do           query → split into N sub-questions
 *              → run each sub-question as a PARALLEL BRANCH
 *              → each branch reads its sources and updates the LEDGER
 *              → gaps are identified
 *              → new branches spawned for gaps (EAQL queries)
 *              → when no open gaps remain (or budget/stop), synthesize.
 *
 * Each branch is locally parallel. Branch failures do NOT kill sibling
 * branches. STOP CONDITIONS: all gaps resolved → synthesize; budget
 * exhausted → synthesize with what exists; user says stop → synthesize
 * immediately (isStopped hook between phases). The ledger is never silently
 * invalidated — contradictions are surfaced, never dropped.
 *
 * Output is a ResearchOutcome-compatible object so evidence assembly and
 * synthesis are untouched (no route changes, no prompt changes).
 */

import { runResearch, RESEARCH_BUDGET, type ResearchOutcome } from '../search/research'
import type { ResearchSource, SearchEventEmitter, SearchIntent, TimeRange } from '../search/types'
import { NOOP_EMIT } from '../search/types'
import { launchSubagent, waitForSubagent } from '@/lib/subagents'
import {
  claimsFromExtract,
  decomposeQuestion,
  detectContradictions,
  detectGaps,
  emptyLedger,
  keyTerms,
  queryForGap,
  saveLedger,
  stripCommandPrefix,
  type Claim,
  type ResearchLedger,
} from './ledger'

export type BranchingInput = {
  question: string
  queries: string[] // planner-provided seed queries (branch 0 seeds)
  intent: SearchIntent
  timeRange: TimeRange
  region?: string
  sourceHint?: string
  officialOnly?: boolean
  depth: 'quick' | 'deep'
  emit: SearchEventEmitter
  deadlineAt: number
  /** ledger key: runId for detached runs, requestId for inline turns */
  ledgerKey: string
  /** user stop check between phases — a stop synthesizes immediately */
  isStopped?: () => boolean
}

const MAX_FIRST_WAVE_BRANCHES = 3
const MAX_GAP_BRANCHES = 2
const BRANCH_CONCURRENCY = 2

async function runBranch(
  branchId: string,
  query: string,
  input: BranchingInput,
  deadlineAt: number,
  ledger: ResearchLedger,
  emit: SearchEventEmitter
): Promise<ResearchOutcome> {
  const branch = ledger.branches.find((b) => b.id === branchId)
  if (branch) branch.status = 'running'
  emit('ledger', { type: 'branch_started', branchId, query })
  try {
    const outcome = await runResearch({
      queries: [query],
      intent: input.intent,
      timeRange: input.timeRange,
      region: input.region,
      sourceHint: input.sourceHint,
      officialOnly: input.officialOnly,
      depth: 'quick', // per-branch budget; the SUPERVISOR owns the deep budget
      emit,
      deadlineAt,
    })
    if (branch) {
      branch.status = outcome.ok ? 'done' : 'failed'
      branch.resultSourceIds = outcome.sources.map((s) => s.url)
    }
    return outcome
  } catch (e) {
    // Branch failures do not kill sibling branches.
    if (branch) branch.status = 'failed'
    emit('ledger', { type: 'branch_failed', branchId, query, error: String(e instanceof Error ? e.message : e).slice(0, 120) })
    return {
      ok: false,
      sources: [],
      queriesRun: [query],
      enginesUsed: [],
      rounds: 1,
      timings: {},
      syndicatedGroups: 0,
      failure: { kind: 'all_failed', message: `branch failed: ${String(e instanceof Error ? e.message : e).slice(0, 100)}` },
    }
  }
}

export async function runBranchingResearch(input: BranchingInput): Promise<ResearchOutcome & { ledger: ResearchLedger }> {
  const emit = input.emit ?? NOOP_EMIT
  const startedAt = Date.now()
  const hardDeadline = Math.min(input.deadlineAt, startedAt + RESEARCH_BUDGET[input.depth].wallClockMs)
  const budget = RESEARCH_BUDGET[input.depth]
  const question = stripCommandPrefix(input.question)

  const ledger = emptyLedger(input.ledgerKey, question)

  // ---- WAVE 1 — deterministic decomposition ------------------------------
  const seedQueries = Array.from(
    new Set([...decomposeQuestion(question), ...input.queries.slice(0, 2)])
  ).slice(0, MAX_FIRST_WAVE_BRANCHES)

  emit('ledger', { type: 'ledger_started', question: question.slice(0, 100), branches: seedQueries.length })

  const allSources: ResearchSource[] = []
  const allQueries: string[] = []
  const enginesUsed = new Set<string>()
  let syndicatedGroups = 0

  const runWave = async (queries: string[], parentBranchId?: string): Promise<void> => {
    const branchIds: string[] = []
    for (const q of queries) {
      const id = `br-${ledger.branches.length + 1}`
      ledger.branches.push({ id, parentBranchId, query: q, status: 'pending', resultSourceIds: [] })
      branchIds.push(id)
    }
    // locally parallel, bounded
    const queue = branchIds.map((id, i) => ({ id, q: queries[i] }))
    const workers = Array.from({ length: Math.min(BRANCH_CONCURRENCY, queue.length) }, async () => {
      for (;;) {
        const next = queue.shift()
        if (!next) return
        if (Date.now() >= hardDeadline) {
          const b = ledger.branches.find((x) => x.id === next.id)
          if (b && b.status === 'pending') b.status = 'failed'
          continue
        }
        const outcome = await runBranch(next.id, next.q, input, hardDeadline, ledger, emit)
        outcome.enginesUsed.forEach((e) => enginesUsed.add(e))
        allQueries.push(...outcome.queriesRun)
        syndicatedGroups = Math.max(syndicatedGroups, outcome.syndicatedGroups)
        // re-ordinal branch sources onto the global ledger
        const base = allSources.length
        const branch = ledger.branches.find((x) => x.id === next.id)
        for (let i = 0; i < outcome.sources.length; i++) {
          const s = outcome.sources[i]
          s.ordinal = base + i + 1
          allSources.push(s)
          if (branch) branch.resultSourceIds.push(s.url)
        }
        // LEDGER UPDATE — claims from what was actually READ
        const queryTerms = keyTerms(question)
        for (const s of outcome.sources) {
          if (s.status === 'retrieved' && s.pageExtract) {
            const claims = claimsFromExtract(s.pageExtract.slice(0, 12_000), s.url, queryTerms)
            ledger.claims.push(...claims)
          }
        }
        emit('ledger', {
          type: 'branch_completed',
          branchId: next.id,
          query: next.q.slice(0, 80),
          sources: outcome.sources.length,
          retrieved: outcome.sources.filter((s) => s.status === 'retrieved').length,
          ok: outcome.ok,
        })
      }
    })
    await Promise.all(workers)
  }

  await runWave(seedQueries)
  await saveLedger(ledger)

  // ---- GAP ROUND — EAQL branches for what the ledger still lacks ---------
  const qTerms = keyTerms(question)
  let gapRound = 0
  const maxGapRounds = input.depth === 'deep' ? 2 : 1
  while (gapRound < maxGapRounds && Date.now() < hardDeadline) {
    if (input.isStopped?.()) {
      emit('ledger', { type: 'stopped_by_user', note: 'stop honored — synthesizing with what exists' })
      break
    }
    const gaps = detectGaps(
      question,
      ledger.claims,
      allSources.filter((s) => s.status === 'retrieved' || s.status === 'snippet_only').map((s) => `${s.title} ${s.snippet}`)
    )
    const attempted = new Set(ledger.gaps.filter((g) => g.status === 'open').map((g) => g.id))
    const fresh = gaps.filter((g) => !attempted.has(g.id)).slice(0, MAX_GAP_BRANCHES)
    if (fresh.length === 0) {
      ledger.gaps.forEach((g) => {
        if (g.status === 'open' && !detectGaps(question, ledger.claims, allSources.map((s) => s.title)).some((x) => x.id === g.id)) {
          g.status = 'resolved'
        }
      })
      break
    }
    for (const g of fresh) ledger.gaps.push(g)
    const gapQueries: string[] = []
    for (const g of fresh) {
      const q = queryForGap(g, question)
      g.attemptedQueries.push(q)
      if (q.length >= 3) gapQueries.push(q)
    }
    if (gapQueries.length === 0) break
    emit('ledger', { type: 'gaps_open', gaps: fresh.map((g) => g.description), queries: gapQueries })
    gapRound++

    // PHASE 6 — ASYNC SUBAGENT DISPATCH: gap branches are LAUNCHED and the
    // supervisor continues working (claim consolidation + contradiction
    // detection below) while they run, then collects results bounded by the
    // deadline. Long research never blocks the supervisor turn.
    const launched: { taskId: string; branchId: string }[] = []
    for (const q of gapQueries) {
      const branchId = `br-${ledger.branches.length + 1}`
      ledger.branches.push({ id: branchId, query: q, status: 'pending', resultSourceIds: [] })
      const taskId = await launchSubagent({
        runId: input.ledgerKey,
        name: 'research_branch',
        input: { query: q, intent: input.intent, timeRange: input.timeRange, deadlineAt: hardDeadline },
      })
      launched.push({ taskId, branchId })
    }
    emit('ledger', { type: 'subagents_launched', count: launched.length })

    // supervisor work DURING the branches: pre-consolidation pass
    const preClaims = ledger.claims.slice(0, 24)
    ledger.contradictions = detectContradictions(preClaims)
    await saveLedger(ledger)

    for (const { taskId, branchId } of launched) {
      const t = await waitForSubagent(taskId, hardDeadline)
      const branch = ledger.branches.find((b) => b.id === branchId)
      const result = (t.result ?? {}) as {
        ok?: boolean
        sources?: {
          title: string
          url: string
          domain: string
          snippet: string
          status: string
          query: string
          extract?: string
        }[]
        queriesRun?: string[]
      }
      allQueries.push(...(result.queriesRun ?? []))
      if (branch) {
        branch.status = t.status === 'done' && result.ok ? 'done' : 'failed'
        branch.resultSourceIds = (result.sources ?? []).map((s) => s.url)
      }
      const base = allSources.length
      for (let i = 0; i < (result.sources ?? []).length; i++) {
        const s = result.sources![i]
        allSources.push({
          ordinal: base + i + 1,
          id: s.url,
          title: s.title,
          url: s.url,
          domain: s.domain,
          snippet: s.snippet,
          publishedDate: null,
          retrievedAt: new Date().toISOString(),
          query: s.query,
          engine: 'subagent',
          searchRank: null,
          status: s.status === 'retrieved' ? 'retrieved' : (s.status as ResearchSource['status']),
          ...(s.extract ? { pageExtract: s.extract } : {}),
        })
      }
      const queryTerms = keyTerms(question)
      for (const s of result.sources ?? []) {
        if (s.status === 'retrieved' && s.extract) {
          ledger.claims.push(...claimsFromExtract(s.extract, s.url, queryTerms))
        }
      }
      emit('ledger', {
        type: 'branch_completed',
        branchId,
        query: (result.queriesRun?.[0] ?? '').slice(0, 80),
        sources: (result.sources ?? []).length,
        retrieved: (result.sources ?? []).filter((s) => s.status === 'retrieved').length,
        ok: result.ok === true,
        via: 'subagent',
      })
    }
    await saveLedger(ledger)
  }

  // ---- CONTRADICTIONS — never silently invalidated ------------------------
  ledger.contradictions = detectContradictions(ledger.claims)
  if (ledger.contradictions.length > 0) {
    emit('ledger', {
      type: 'contradictions',
      count: ledger.contradictions.length,
      note: 'conflicting evidence preserved explicitly — the answer must present both sides with citations',
    })
  }
  ledger.claims = ledger.claims.slice(0, 40)
  await saveLedger(ledger)
  emit('ledger', {
    type: 'ledger_final',
    claims: ledger.claims.length,
    contradictions: ledger.contradictions.length,
    openGaps: ledger.gaps.filter((g) => g.status === 'open').length,
    branches: ledger.branches.length,
  })

  // ---- assemble the ResearchOutcome-compatible result ---------------------
  const retrievedCount = allSources.filter((s) => s.status === 'retrieved').length
  for (const s of allSources) {
    if (s.status === 'discovered') s.status = 'snippet_only'
  }
  if (allSources.length === 0) {
    return {
      ok: false,
      sources: [],
      queriesRun: Array.from(new Set(allQueries)),
      enginesUsed: [...enginesUsed],
      rounds: 1 + gapRound,
      timings: { totalMs: Date.now() - startedAt },
      syndicatedGroups: 0,
      failure: {
        kind: 'no_results',
        message: enginesUsed.size > 0 ? 'No usable results were found.' : 'All search engines failed or were throttled.',
      },
      ledger,
    }
  }

  return {
    ok: true,
    sources: allSources.slice(0, budget.maxResultsDiscovered),
    queriesRun: Array.from(new Set(allQueries)),
    enginesUsed: [...enginesUsed],
    rounds: 1 + gapRound,
    timings: { totalMs: Date.now() - startedAt },
    syndicatedGroups,
    failure:
      retrievedCount === 0
        ? { kind: 'all_failed', message: 'Sources were found but none could be opened for reading.' }
        : undefined,
    ledger,
  }
}
