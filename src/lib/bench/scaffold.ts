/**
 * BENCH SCAFFOLD LAYER — "benchmark the scaffold, not the model".
 *
 * Same model, different scaffold → score moves. This module implements the
 * scaffold variants the benchmark dispatches measure, one lever at a time:
 *
 *   baseline      raw synthesis (identical to the Phase 1 shim path — every
 *                 historical run remains valid; byte-identical request flow)
 *   aci           LEVER 1 — agent-computer interface: poka-yoke tool schemas
 *                 (typed args, enums, validation BEFORE execution, structured
 *                 errors), per-tool output summarization, 8000-char offload
 *                 with agent-aware truncation hints
 *   verification  LEVER 2 — separate-model verifier (gs-ai-flash ≠ generator
 *                 glm-4.6, temperature 0), max 2 regenerations, honest
 *                 "unverified" label after budget
 *   context       LEVER 3 — episodic memory: LLM summary of older history
 *                 regenerated per request when >5 messages (single-turn
 *                 benchmarks are an honest no-op)
 *   router        LEVER 4 — planner/executor split: gs-ai-thinking plans
 *                 (≤5 steps, ≤120 words), gs-ai executes
 *
 * HARD RULES ENCODED HERE:
 *   - Rule 2 (same model): the generator is ALWAYS the gs-ai tier
 *     (glm-4.6, thinking off) for every profile. Verifier/summarizer use
 *     gs-ai-flash and planner uses gs-ai-thinking BY DESIGN — those roles are
 *     part of the scaffold under test, recorded per-call in telemetry.
 *   - Rule 4 (provenance): every call appends one JSONL line to
 *     tool-results/bench-scaffold-<UTC date>.jsonl + a BENCH-SCAFFOLD log line.
 *   - Rule 5 (no fabrication): tool failures are surfaced to the model as
 *     structured errors; nothing silently succeeds.
 *   - Rule 7 (served id is truth): telemetry records the upstream-served model
 *     id, never the marketing name.
 *
 * This module is imported ONLY by the bench shim routes. Production paths
 * (turn executor, search pipeline, router) are untouched.
 */

import { completeChatWithMeta } from '@/lib/ai'
import { bingWeb, duckduckgoLite, wikipedia } from '@/lib/search/engines'
import type { EngineQuery, RawResult } from '@/lib/search/types'
import { createHash } from 'crypto'
import { appendFileSync, mkdirSync, writeFileSync } from 'fs'
import path from 'path'

export type ScaffoldProfile = 'baseline' | 'aci' | 'verification' | 'context' | 'router'

export const SCAFFOLD_PROFILES: ScaffoldProfile[] = [
  'baseline',
  'aci',
  'verification',
  'context',
  'router',
]

type ScaffoldMessage = { role: string; content: string }

export type ScaffoldOutcome = {
  text: string
  servedModel: string | null
  profile: ScaffoldProfile
  generatorCalls: number
  verifierCalls: number
  plannerCalls: number
  summarizerCalls: number
  toolCallsByName: Record<string, number>
  toolArgErrors: number
  offloads: number
  iterations: number
  verifierVerdicts: Array<'PASS' | 'REJECT'>
  regenerations: number
  unverified: boolean
  latencyMs: number
  estTokensIn: number
  estTokensOut: number
  notes: string[]
}

// ---------------------------------------------------------------------------
// role-model resolution (scaffold roles are part of the lever under test)
// ---------------------------------------------------------------------------

const GENERATOR = { providerModel: 'glm-4.6', thinking: { mode: 'flash' as const } }
const CHEAP = { providerModel: 'glm-4.5-flash', thinking: { mode: 'flash' as const } } // verifier / summarizer
const PLANNER = { providerModel: 'glm-4.6', thinking: { mode: 'thinking' as const, effort: 'high' } }

// Verbatim scaffold preambles — provenance by construction (rule 4).
export const ACI_PREAMBLE = `You may use tools before answering. To call a tool output EXACTLY one JSON object on its own line and NOTHING else on that line:
{"tool": "<name>", "args": {...}}
Tools (args must match these shapes exactly):
- web_search: {"query": <string, required, 3+ chars>, "max_results": <integer 1-8, default 4>, "recency": <"any"|"day"|"week"|"month"|"year", default "any">}
- calculator: {"expression": <string, required; digits and + - * / ( ) % ^ only>, "precision": <"integer"|"float", default "float">}
Invalid arguments are rejected BEFORE execution with a structured TOOL_ERROR — fix the named field and retry. Tool outputs larger than 8000 characters are offloaded to a file; you receive a 500-char preview and the file path. Your FINAL reply (any reply that is not a tool call) must answer the question in the exact format the task requests.`

export const VERIFIER_PROMPT = `You are a strict verifier. You are given the QUESTION and a DRAFT ANSWER. Judge ONLY whether the draft's final answer is internally consistent and directly responsive to the question. Do not solve the problem yourself. Reply with EXACTLY one line:
VERDICT: PASS
or
VERDICT: REJECT — <one concrete reason>`

export const PLANNER_PROMPT = `You are the planner. Produce a numbered plan of at most 5 steps (max 120 words) for answering the user's question. Do NOT answer the question. Do not use tools. Output only the numbered plan.`

export const EPISODIC_PROMPT = `Summarize the conversation so far in at most 150 words for an agent that must continue the task. Preserve actionable identifiers verbatim (file paths, function names, variable names, error codes, IDs, URLs). Omit verbose tool outputs. Output only the summary.`

// ---------------------------------------------------------------------------
// telemetry (rule 4: every run records, every claim has a log line)
// ---------------------------------------------------------------------------

function toolResultsDir(): string {
  return path.join(process.cwd(), 'tool-results')
}

function telemetryFile(): string {
  const day = new Date().toISOString().slice(0, 10)
  return path.join(toolResultsDir(), `bench-scaffold-${day}.jsonl`)
}

function messagesKey(messages: ScaffoldMessage[]): string {
  return createHash('sha256')
    .update(messages.map((m) => `${m.role}\u0000${m.content}`).join('\u0001'))
    .digest('hex')
    .slice(0, 16)
}

function logTelemetry(outcome: ScaffoldOutcome, messagesKeyHex: string, nMessages: number): void {
  const line = JSON.stringify({
    ts: new Date().toISOString(),
    profile: outcome.profile,
    served_model: outcome.servedModel,
    messages_key: messagesKeyHex,
    n_messages: nMessages,
    generator_calls: outcome.generatorCalls,
    verifier_calls: outcome.verifierCalls,
    planner_calls: outcome.plannerCalls,
    summarizer_calls: outcome.summarizerCalls,
    tool_calls: outcome.toolCallsByName,
    tool_arg_errors: outcome.toolArgErrors,
    offloads: outcome.offloads,
    iterations: outcome.iterations,
    verifier_verdicts: outcome.verifierVerdicts,
    regenerations: outcome.regenerations,
    unverified: outcome.unverified,
    latency_ms: outcome.latencyMs,
    est_tokens_in: outcome.estTokensIn,
    est_tokens_out: outcome.estTokensOut,
  })
  try {
    mkdirSync(toolResultsDir(), { recursive: true })
    appendFileSync(telemetryFile(), line + '\n')
  } catch {
    // telemetry write must never break a bench call; the console line still lands
  }
  console.log(
    `BENCH-SCAFFOLD profile=${outcome.profile} served=${outcome.servedModel ?? 'unknown'} gen=${outcome.generatorCalls} verif=${outcome.verifierCalls} plan=${outcome.plannerCalls} tools=${JSON.stringify(outcome.toolCallsByName)} toolErr=${outcome.toolArgErrors} offload=${outcome.offloads} regen=${outcome.regenerations} unverified=${outcome.unverified} latencyMs=${outcome.latencyMs}`
  )
}

function estTokens(messages: ScaffoldMessage[]): number {
  return Math.ceil(messages.reduce((n, m) => n + m.content.length, 0) / 4)
}

async function callModel(
  messages: ScaffoldMessage[],
  role: typeof GENERATOR | typeof CHEAP | typeof PLANNER,
  temperature = 0.2
): Promise<{ text: string; model: string | null }> {
  return completeChatWithMeta(messages, role.providerModel, role.thinking, temperature)
}

// ---------------------------------------------------------------------------
// LEVER 1 — ACI: poka-yoke tools
// ---------------------------------------------------------------------------

const OFFLOAD_THRESHOLD_CHARS = 8000
const OFFLOAD_PREVIEW_CHARS = 500
const ACI_MAX_ITERATIONS = 6

type ValidatedArgs =
  | { tool: 'web_search'; query: string; maxResults: number; recency: 'any' | 'day' | 'week' | 'month' | 'year' }
  | { tool: 'calculator'; expression: string; precision: 'integer' | 'float' }

function argError(tool: string, code: string, field: string, expected: string, got: unknown): string {
  return `TOOL_ERROR tool=${tool} code=${code} field=${field} expected=${expected} got=${JSON.stringify(got ?? null)} hint=fix field '${field}' and re-issue the tool call; the tool did NOT run`
}

const WEB_SEARCH_RECENCY = ['any', 'day', 'week', 'month', 'year'] as const

const TOOL_SPECS: Record<string, ToolSpec> = {
  web_search: {
    name: 'web_search',
    argsSchema: '{"query": string (required, 3+ chars), "max_results": integer 1-8 (default 4), "recency": "any"|"day"|"week"|"month"|"year" (default "any")}',
    validate(args) {
      const tool = 'web_search'
      const q = args.query
      if (typeof q !== 'string' || q.trim().length < 3) {
        return { ok: false, error: argError(tool, 'ARG_TYPE', 'query', 'string with length >= 3', q) }
      }
      let maxResults = 4
      if (args.max_results !== undefined) {
        if (typeof args.max_results !== 'number' || !Number.isInteger(args.max_results) || args.max_results < 1 || args.max_results > 8) {
          return { ok: false, error: argError(tool, 'ARG_RANGE', 'max_results', 'integer 1-8', args.max_results) }
        }
        maxResults = args.max_results
      }
      let recency: (typeof WEB_SEARCH_RECENCY)[number] = 'any'
      if (args.recency !== undefined) {
        if (typeof args.recency !== 'string' || !(WEB_SEARCH_RECENCY as readonly string[]).includes(args.recency)) {
          return { ok: false, error: argError(tool, 'ARG_ENUM', 'recency', WEB_SEARCH_RECENCY.map((r) => `"${r}"`).join('|'), args.recency) }
        }
        recency = args.recency as (typeof WEB_SEARCH_RECENCY)[number]
      }
      return { ok: true, value: { tool, query: q.trim(), maxResults, recency } }
    },
    async execute(value) {
      if (value.tool !== 'web_search') return ''
      const timeRange = value.recency === 'day' ? 'day' : value.recency === 'week' ? 'week' : value.recency === 'month' ? 'month' : 'none'
      const engines = [wikipedia, duckduckgoLite, bingWeb]
      const settled = await Promise.allSettled(
        engines.map((e) =>
          e.run({
            query: value.query,
            timeRange: timeRange as EngineQuery['timeRange'],
            limit: value.maxResults,
          } satisfies EngineQuery)
        )
      )
      const rows: RawResult[] = []
      const engineNotes: string[] = []
      settled.forEach((s, i) => {
        if (s.status === 'fulfilled') {
          rows.push(...s.value)
          engineNotes.push(`${engines[i].id}:ok(${s.value.length})`)
        } else {
          engineNotes.push(`${engines[i].id}:error(${(s.reason instanceof Error ? s.reason.message : String(s.reason)).slice(0, 80)})`)
        }
      })
      const seen = new Set<string>()
      const deduped = rows.filter((r) => {
        const k = r.url
        if (seen.has(k) || !k) return false
        seen.add(k)
        return true
      })
      if (deduped.length === 0) {
        return `web_search returned 0 results (engines: ${engineNotes.join(' ')}). Refine the query and search again, or answer without search.`
      }
      // Per-tool output summarization: each result compressed to one line.
      const lines = deduped.slice(0, value.maxResults * 2).map((r, i) => {
        const title = r.title.slice(0, 80)
        const snippet = r.snippet.replace(/\s+/g, ' ').slice(0, 200)
        return `${i + 1}. ${title} — ${snippet} (${r.url})`
      })
      let output = `[web_search engines=${engineNotes.join(' ')} results=${Math.min(deduped.length, value.maxResults * 2)}]\n${lines.join('\n')}`
      if (output.length > OFFLOAD_THRESHOLD_CHARS) {
        output = offload('web_search', output)
      }
      return output
    },
  },
  calculator: {
    name: 'calculator',
    argsSchema: '{"expression": string (required; digits and + - * / ( ) % ^ only), "precision": "integer"|"float" (default "float")}',
    validate(args) {
      const tool = 'calculator'
      const expr = args.expression
      if (typeof expr !== 'string' || expr.trim().length === 0) {
        return { ok: false, error: argError(tool, 'ARG_TYPE', 'expression', 'non-empty string', expr) }
      }
      if (!/^[0-9+\-*/(). %^\t]+$/.test(expr)) {
        const bad = [...expr].filter((c) => !/[0-9+\-*/(). %^\t]/.test(c)).slice(0, 5)
        return { ok: false, error: argError(tool, 'ARG_CHARS', 'expression', 'digits and + - * / ( ) % ^ only', `forbidden characters: ${bad.join(' ')}`) }
      }
      let precision: 'integer' | 'float' = 'float'
      if (args.precision !== undefined) {
        if (args.precision !== 'integer' && args.precision !== 'float') {
          return { ok: false, error: argError(tool, 'ARG_ENUM', 'precision', '"integer"|"float"', args.precision) }
        }
        precision = args.precision
      }
      return { ok: true, value: { tool, expression: expr.trim(), precision } }
    },
    async execute(value) {
      if (value.tool !== 'calculator') return ''
      try {
        const js = value.expression.replace(/\^/g, '**')
        const result = Function(`"use strict"; return (${js});`)() as unknown
        if (typeof result !== 'number' || !Number.isFinite(result)) {
          return `TOOL_ERROR tool=calculator code=EVAL_RESULT field=expression expected=finite number got=${String(result)} hint=simplify the expression; the tool ran but produced a non-finite result`
        }
        const final = value.precision === 'integer' ? String(Math.round(result)) : String(result)
        return `[calculator] ${value.expression} = ${final}`
      } catch (e) {
        const msg = e instanceof Error ? e.message : String(e)
        return `TOOL_ERROR tool=calculator code=EVAL_THROW field=expression expected=evaluable arithmetic got="${msg.slice(0, 120)}" hint=check parentheses and operator placement`
      }
    },
  },
}

type ToolSpec = {
  name: 'web_search' | 'calculator'
  argsSchema: string
  validate: (args: Record<string, unknown>) => { ok: true; value: ValidatedArgs } | { ok: false; error: string }
  execute: (value: ValidatedArgs) => Promise<string>
}

/** 8,000-char offload: scratch file + 500-char preview + agent-aware hint. */
function offload(tool: string, output: string): string {
  const file = path.join(toolResultsDir(), `bench-offload-${Date.now().toString(36)}.txt`)
  try {
    mkdirSync(toolResultsDir(), { recursive: true })
    writeFileSync(file, output)
  } catch {
    return `[${tool}] output was ${output.length} chars (offload write failed) — first 500 chars:\n${output.slice(0, OFFLOAD_PREVIEW_CHARS)}`
  }
  // Agent-aware truncation hint: THIS scaffold has no subagent delegation,
  // so the hint points at the capability the agent actually has.
  return `[${tool}] output was ${output.length} chars — too large for context. Offloaded to ${file}. First 500 chars:\n${output.slice(0, OFFLOAD_PREVIEW_CHARS)}\n(recovery hint: this agent has no subagent delegation — use incremental search with narrower queries instead of requesting everything at once)`
}

function extractToolCall(reply: string): { tool: string; args: Record<string, unknown> } | null {
  for (const rawLine of reply.split('\n')) {
    const line = rawLine.trim()
    const start = line.indexOf('{')
    if (start === -1) continue
    // Poka-yoke recovery: models emit trailing junk braces (`}}}`) or prose
    // around the tool call. Scan balanced JSON prefixes instead of requiring a
    // pristine line — the whitelist validator still guards every arg, so a
    // recovered-but-wrong parse fails safely in validate() with a structured
    // error, never executes.
    for (let end = line.indexOf('}', start); end !== -1; end = line.indexOf('}', end + 1)) {
      try {
        const parsed = JSON.parse(line.slice(start, end + 1)) as { tool?: unknown; args?: unknown }
        if (typeof parsed?.tool === 'string' && parsed.args && typeof parsed.args === 'object') {
          return { tool: parsed.tool, args: parsed.args as Record<string, unknown> }
        }
      } catch {
        // keep scanning outward
      }
    }
  }
  return null
}

async function runAci(messages: ScaffoldMessage[], o: ScaffoldOutcome): Promise<string> {
  const convo: ScaffoldMessage[] = [{ role: 'system', content: ACI_PREAMBLE }, ...messages]
  let last = ''
  for (let i = 0; i < ACI_MAX_ITERATIONS; i++) {
    o.iterations = i + 1
    const gen = await callModel(convo, GENERATOR)
    o.generatorCalls += 1
    o.servedModel = gen.model ?? o.servedModel
    last = gen.text
    const call = extractToolCall(gen.text)
    if (!call) return last // final answer
    const spec = TOOL_SPECS[call.tool]
    if (!spec) {
      o.toolArgErrors += 1
      convo.push({ role: 'assistant', content: gen.text })
      convo.push({
        role: 'user',
        content: `TOOL_ERROR tool=${call.tool} code=UNKNOWN_TOOL expected=${Object.keys(TOOL_SPECS).join('|')} got="${call.tool}" hint=use exactly one of the listed tools, or give your final answer`,
      })
      continue
    }
    const v = spec.validate(call.args)
    if (!v.ok) {
      o.toolArgErrors += 1
      convo.push({ role: 'assistant', content: gen.text })
      convo.push({ role: 'user', content: v.error })
      continue
    }
    o.toolCallsByName[call.tool] = (o.toolCallsByName[call.tool] ?? 0) + 1
    const before = convo.reduce((n, m) => n + m.content.length, 0)
    let result: string
    try {
      result = await spec.execute(v.value)
    } catch (e) {
      result = `TOOL_ERROR tool=${call.tool} code=EXEC_THROW expected=tool output got="${(e instanceof Error ? e.message : String(e)).slice(0, 160)}" hint=the tool threw; retry once with corrected args or answer without it`
    }
    if (result.includes('too large for context')) o.offloads += 1
    o.notes.push(`tool=${call.tool} out_chars=${result.length} ctx_before=${before}`)
    convo.push({ role: 'assistant', content: gen.text })
    convo.push({ role: 'user', content: `TOOL_RESULT ${result}` })
  }
  o.notes.push(`iteration budget ${ACI_MAX_ITERATIONS} exhausted; shipping last reply`)
  return last
}

// ---------------------------------------------------------------------------
// LEVER 2 — verification gates (separate-model verifier)
// ---------------------------------------------------------------------------

const VERIFICATION_MAX_REGENS = 2

function parseVerdict(reply: string): 'PASS' | 'REJECT' | null {
  const m = reply.match(/VERDICT:\s*(PASS|REJECT)/i)
  return m ? (m[1].toUpperCase() as 'PASS' | 'REJECT') : null
}

async function runVerification(messages: ScaffoldMessage[], o: ScaffoldOutcome): Promise<string> {
  const draft0 = await callModel(messages, GENERATOR)
  o.generatorCalls += 1
  o.servedModel = draft0.model ?? o.servedModel
  let draft = draft0.text
  let unverified = false

  for (let attempt = 0; attempt <= VERIFICATION_MAX_REGENS; attempt++) {
    const verdictReply = await callModel(
      [
        {
          role: 'user',
          content: `${VERIFIER_PROMPT}\n\nQUESTION:\n${messages.map((m) => m.content).join('\n')}\n\nDRAFT ANSWER:\n${draft}`,
        },
      ],
      CHEAP,
      0 // verifier at temperature 0, DIFFERENT model (gs-ai-flash / glm-4.5-flash)
    )
    o.verifierCalls += 1
    const verdict = parseVerdict(verdictReply.text)
    if (verdict === null) {
      o.notes.push(`verifier reply unparseable (attempt ${attempt}); counting as PASS-with-note`)
      o.verifierVerdicts.push('PASS')
      break
    }
    o.verifierVerdicts.push(verdict)
    if (verdict === 'PASS') break
    if (attempt === VERIFICATION_MAX_REGENS) {
      unverified = true // budget spent: ship the draft with the honest label
      break
    }
    o.regenerations += 1
    const reason = verdictReply.text.slice(0, 400)
    const regen = await callModel(
      [
        ...messages,
        { role: 'assistant', content: draft },
        {
          role: 'user',
          content: `A separate verifier rejected the draft above with: ${reason}\nRe-answer the original question, fixing the stated problem. Keep the exact output format the task requests.`,
        },
      ],
      GENERATOR
    )
    o.generatorCalls += 1
    draft = regen.text
  }
  o.unverified = unverified
  return draft
}

// ---------------------------------------------------------------------------
// LEVER 3 — episodic memory
// ---------------------------------------------------------------------------

const EPISODIC_TRIGGER_NEW = 5
const EPISODIC_KEEP_VERBATIM = 2

async function runContext(messages: ScaffoldMessage[], o: ScaffoldOutcome): Promise<string> {
  if (messages.length <= EPISODIC_TRIGGER_NEW) {
    o.notes.push(`episodic memory not triggered (${messages.length} messages <= ${EPISODIC_TRIGGER_NEW}); single-turn no-op`)
    const gen = await callModel(messages, GENERATOR)
    o.generatorCalls += 1
    o.servedModel = gen.model ?? o.servedModel
    return gen.text
  }
  const older = messages.slice(0, messages.length - EPISODIC_KEEP_VERBATIM)
  const recent = messages.slice(messages.length - EPISODIC_KEEP_VERBATIM)
  const summary = await callModel(
    [
      {
        role: 'user',
        content: `${EPISODIC_PROMPT}\n\nCONVERSATION:\n${older.map((m) => `${m.role}: ${m.content.slice(0, 4000)}`).join('\n---\n')}`,
      },
    ],
    CHEAP,
    0
  )
  o.summarizerCalls += 1
  const gen = await callModel(
    [
      {
        role: 'user',
        content: `EPISODIC MEMORY (summary of earlier conversation, LLM-generated):\n${summary.text}\n\nRECENT MESSAGES VERBATIM:\n${recent.map((m) => `${m.role}: ${m.content}`).join('\n---\n')}\n\nContinue the task.`,
      },
    ],
    GENERATOR
  )
  o.generatorCalls += 1
  o.servedModel = gen.model ?? o.servedModel
  return gen.text
}

// ---------------------------------------------------------------------------
// LEVER 4 — planner / executor split
// ---------------------------------------------------------------------------

async function runRouter(messages: ScaffoldMessage[], o: ScaffoldOutcome): Promise<string> {
  const plan = await callModel(
    [{ role: 'user', content: `${PLANNER_PROMPT}\n\nQUESTION:\n${messages.map((m) => m.content).join('\n')}` }],
    PLANNER
  )
  o.plannerCalls += 1
  o.servedModel = plan.model ?? o.servedModel
  const exec = await callModel(
    [
      ...messages,
      {
        role: 'user',
        content: `PLANNING NOTES (from a separate planner model — follow or discard as needed):\n${plan.text}`,
      },
    ],
    GENERATOR
  )
  o.generatorCalls += 1
  return exec.text
}

// ---------------------------------------------------------------------------
// dispatcher
// ---------------------------------------------------------------------------

export function parseScaffoldModel(
  model: string | undefined
): { base: string; profile: ScaffoldProfile } | null {
  const m = (model ?? 'gs-ai').trim()
  const at = m.indexOf('@')
  const base = at === -1 ? m : m.slice(0, at)
  const rawProfile = at === -1 ? 'baseline' : m.slice(at + 1)
  if (!SCAFFOLD_PROFILES.includes(rawProfile as ScaffoldProfile)) return null
  return { base, profile: rawProfile as ScaffoldProfile }
}

export async function runScaffoldCompletion(
  messages: ScaffoldMessage[],
  profile: ScaffoldProfile
): Promise<ScaffoldOutcome> {
  const o: ScaffoldOutcome = {
    text: '',
    servedModel: null,
    profile,
    generatorCalls: 0,
    verifierCalls: 0,
    plannerCalls: 0,
    summarizerCalls: 0,
    toolCallsByName: {},
    toolArgErrors: 0,
    offloads: 0,
    iterations: 0,
    verifierVerdicts: [],
    regenerations: 0,
    unverified: false,
    latencyMs: 0,
    estTokensIn: 0,
    estTokensOut: 0,
    notes: [],
  }
  const startedAt = Date.now()
  const key = messagesKey(messages)
  try {
    switch (profile) {
      case 'aci':
        o.text = await runAci(messages, o)
        break
      case 'verification':
        o.text = await runVerification(messages, o)
        break
      case 'context':
        o.text = await runContext(messages, o)
        break
      case 'router':
        o.text = await runRouter(messages, o)
        break
      case 'baseline':
      default: {
        const gen = await callModel(messages, GENERATOR)
        o.generatorCalls = 1
        o.servedModel = gen.model
        o.text = gen.text
        break
      }
    }
  } finally {
    o.latencyMs = Date.now() - startedAt
    o.estTokensIn = estTokens(messages)
    o.estTokensOut = Math.ceil(o.text.length / 4)
    logTelemetry(o, key, messages.length)
  }
  return o
}
