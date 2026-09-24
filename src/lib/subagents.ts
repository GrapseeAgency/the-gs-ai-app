/**
 * PHASE 6 — ASYNC SUBAGENT DISPATCH (LangChain Deep Agents v0.5 pattern).
 *
 * Long-running research branches must not block the supervisor turn: the
 * supervisor LAUNCHES a task and continues working (ledger consolidation,
 * contradiction detection), then collects results bounded by the deadline.
 *
 * TOOLS: launch_subagent → taskId | check_subagent → status|result |
 * cancel_subagent | list_subagents | get_subagent_result | steer_subagent
 * (course-correct mid-task).
 *
 * HETEROGENEOUS DEPLOYMENT: a task may run on a LOCAL executor (the default
 * registry below) or on a REMOTE agent server (url + graphId — different
 * hardware, different model, own tool set). The remote client is the
 * deployment seam: POST {graphId, input} → {taskId}, poll GET ?taskId=.
 */

import { db } from '@/lib/db'
import { runResearch } from '@/lib/search/research'
import { NOOP_EMIT, type SearchIntent, type TimeRange } from '@/lib/search/types'

export interface AsyncSubagent {
  name: string
  description: string
  url?: string // remote agent server; undefined = local executor
  graphId?: string
}

export type SubagentTaskRow = {
  id: string
  runId: string
  name: string
  status: 'pending' | 'running' | 'done' | 'failed' | 'cancelled'
  input: Record<string, unknown>
  result: Record<string, unknown> | null
  error: string | null
}

const aborts = new Map<string, AbortController>()
const steerNotes = new Map<string, string[]>()

/** The local executor registry — named task types runnable in-process. */
type LocalHandler = (input: Record<string, unknown>, taskId: string) => Promise<Record<string, unknown>>
const localHandlers: Record<string, LocalHandler> = {
  // research_branch: one bounded research execution (the Phase-5 branch unit)
  research_branch: async (input, taskId) => {
    const query = String(input.query ?? '')
    const deadlineAt = typeof input.deadlineAt === 'number' ? input.deadlineAt : Date.now() + 45_000
    const steerKey = `steer:${taskId}`
    const outcome = await runResearch({
      queries: [query],
      intent: (input.intent as SearchIntent) ?? 'research',
      timeRange: (input.timeRange as TimeRange) ?? 'none',
      region: input.region as string | undefined,
      sourceHint: input.sourceHint as string | undefined,
      officialOnly: input.officialOnly === true,
      depth: 'quick',
      emit: NOOP_EMIT,
      deadlineAt,
    })
    const note = steerNotes.get(steerKey)?.[0]
    return {
      query,
      ...(note ? { steered: note } : {}),
      ok: outcome.ok,
      sources: outcome.sources.map((s) => ({
        ordinal: s.ordinal,
        title: s.title,
        url: s.url,
        domain: s.domain,
        snippet: s.snippet,
        status: s.status,
        query: s.query,
        // trimmed extract — enough for claim extraction without bloating
        // the task result row (the supervisor ledger re-claims from it)
        ...(s.pageExtract ? { extract: s.pageExtract.slice(0, 4_000) } : {}),
      })),
      queriesRun: outcome.queriesRun,
      failure: outcome.failure ?? null,
    }
  },
}

export function registerLocalHandler(name: string, handler: LocalHandler): void {
  localHandlers[name] = handler
}

/** launch_subagent(name, input) → taskId — returns IMMEDIATELY. */
export async function launchSubagent(params: {
  runId: string
  name: string
  input: Record<string, unknown>
  url?: string
  graphId?: string
}): Promise<string> {
  const task = await db.subagentTask.create({
    data: {
      runId: params.runId,
      name: params.name,
      url: params.url ?? null,
      graphId: params.graphId ?? null,
      input: JSON.stringify(params.input),
      status: 'pending',
    },
  })
  const taskId = task.id
  const ac = new AbortController()
  aborts.set(taskId, ac)

  // fire-and-continue: the supervisor's next line runs without waiting
  void (async () => {
    await db.subagentTask.update({ where: { id: taskId }, data: { status: 'running' } })
    try {
      let result: Record<string, unknown>
      if (params.url && params.graphId) {
        // REMOTE agent server (heterogeneous deployment seam)
        const res = await fetch(params.url, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ graphId: params.graphId, input: params.input }),
          signal: ac.signal,
        })
        result = (await res.json()) as Record<string, unknown>
      } else {
        const handler = localHandlers[params.name]
        if (!handler) throw new Error(`no local executor for subagent '${params.name}'`)
        result = await handler(params.input, taskId)
      }
      if (aborts.get(taskId)?.signal.aborted) return // cancelled mid-flight
      await db.subagentTask.update({
        where: { id: taskId },
        data: { status: 'done', result: JSON.stringify(result) },
      })
    } catch (e) {
      const raw = String(e instanceof Error ? e.message : e)
      await db.subagentTask
        .update({
          where: { id: taskId },
          data: { status: ac.signal.aborted ? 'cancelled' : 'failed', error: raw.slice(0, 500) },
        })
        .catch(() => undefined)
    } finally {
      aborts.delete(taskId)
    }
  })()

  return taskId
}

/** check_subagent(taskId) → status | result */
export async function checkSubagent(taskId: string): Promise<SubagentTaskRow> {
  const t = await db.subagentTask.findUnique({ where: { id: taskId } })
  if (!t) throw new Error(`subagent task ${taskId} not found`)
  return {
    id: t.id,
    runId: t.runId,
    name: t.name,
    status: t.status as SubagentTaskRow['status'],
    input: safeJson(t.input),
    result: t.result ? safeJson(t.result) : null,
    error: t.error,
  }
}

/** get_subagent_result(taskId) → result (throws unless done) */
export async function getSubagentResult(taskId: string): Promise<Record<string, unknown>> {
  const t = await checkSubagent(taskId)
  if (t.status !== 'done' || !t.result) {
    throw new Error(`subagent ${taskId} not done (status=${t.status})`)
  }
  return t.result
}

/** cancel_subagent(taskId) — course-correct primitive + abort. */
export async function cancelSubagent(taskId: string): Promise<void> {
  aborts.get(taskId)?.abort()
  await db.subagentTask
    .updateMany({ where: { id: taskId, status: { in: ['pending', 'running'] } }, data: { status: 'cancelled' } })
}

/** list_subagents(runId) */
export async function listSubagents(runId: string): Promise<SubagentTaskRow[]> {
  const rows = await db.subagentTask.findMany({ where: { runId }, orderBy: { createdAt: 'asc' } })
  return rows.map((t) => ({
    id: t.id,
    runId: t.runId,
    name: t.name,
    status: t.status as SubagentTaskRow['status'],
    input: safeJson(t.input),
    result: t.result ? safeJson(t.result) : null,
    error: t.error,
  }))
}

/** steer_subagent(taskId, instruction) — mid-task course-correction. */
export function steerSubagent(taskId: string, instruction: string): void {
  const notes = steerNotes.get(`steer:${taskId}`) ?? []
  notes.push(instruction)
  steerNotes.set(`steer:${taskId}`, notes)
}

/** Await a task's completion, bounded — the supervisor collects, never blocks unbounded. */
export async function waitForSubagent(taskId: string, deadlineAt: number, pollMs = 250): Promise<SubagentTaskRow> {
  for (;;) {
    const t = await checkSubagent(taskId)
    if (t.status === 'done' || t.status === 'failed' || t.status === 'cancelled') return t
    if (Date.now() >= deadlineAt) return t // supervisor budget wins
    await new Promise((r) => setTimeout(r, pollMs))
  }
}

const safeJson = (raw: string): Record<string, unknown> => {
  try {
    return JSON.parse(raw) as Record<string, unknown>
  } catch {
    return {}
  }
}
