/**
 * BENCHMARK INFRA — PHASE 1: OpenAI-compatible shim (chat/completions).
 *
 * Purpose: let standard eval harnesses (inspect-ai, lm-eval, HELM, tau2-bench)
 * drive GS AI with a single OpenAI-shaped endpoint. This is a MEASUREMENT
 * surface only:
 *   - It BYPASSES the production router (capability / search / evidence
 *     pipeline) entirely — a bench request is a raw synthesis call, exactly
 *     what a benchmark must measure.
 *   - It does NOT touch the production system prompt: no SYSTEM_PROMPT is
 *     injected. The harness's own instructions are passed through verbatim.
 *
 * Model resolution (`model` field):
 *   gs-ai              → GS AI provider router, generator role
 *   gs-ai-flash        → GS AI provider router, cheap role
 *   gs-ai-thinking     → GS AI provider router, planner role
 *   <vendor/model>     → OpenRouter passthrough (e.g. openai/gpt-5.5),
 *                        routed through the existing orCompleteChat key-pool
 *                        chain so competitor probes use the same accounting
 *                        as production free-tier traffic.
 *
 * The `model` field of every response carries the provider and concrete model
 * that actually answered, so scorecards always show the serving distribution.
 *
 * Optional auth: if env GS_BENCH_API_KEY is set, requests must present
 * `Authorization: Bearer <key>`. Unset (current state) = open, same trust
 * level as the rest of the public /api/v1 surface.
 */

import { NextRequest, NextResponse } from 'next/server'
import { orCompleteChat } from '@/lib/openrouter'
import {
  GsAllProvidersUnavailableError,
  runGsProviderChat,
} from '@/lib/gs-provider-router'
import { parseScaffoldModel, runScaffoldCompletion } from '@/lib/bench/scaffold'

export const runtime = 'nodejs'
export const maxDuration = 300

type OpenAiMessage = { role: string; content: unknown }

type ChatCompletionsBody = {
  model?: string
  messages?: OpenAiMessage[]
  temperature?: number
  max_tokens?: number
  stream?: boolean
}

function authOk(req: NextRequest): boolean {
  const required = process.env.GS_BENCH_API_KEY
  if (!required) return true
  const header = req.headers.get('authorization') ?? ''
  return header === `Bearer ${required}`
}

function flattenContent(content: unknown): string {
  if (typeof content === 'string') return content
  if (Array.isArray(content)) {
    return content
      .map((part) =>
        part && typeof part === 'object' && typeof (part as { text?: unknown }).text === 'string'
          ? (part as { text: string }).text
          : ''
      )
      .join('')
  }
  return ''
}

function toInternalMessages(messages: OpenAiMessage[] | undefined):
  | { ok: true; messages: { role: string; content: string }[] }
  | { ok: false; error: string } {
  if (!Array.isArray(messages) || messages.length === 0) {
    return { ok: false, error: 'messages must be a non-empty array' }
  }
  const out = messages.map((m) => ({
    role: typeof m?.role === 'string' ? m.role : 'user',
    content: flattenContent(m?.content),
  }))
  if (out.some((m) => m.role !== 'system' && m.role !== 'user' && m.role !== 'assistant')) {
    return { ok: false, error: `unsupported role in messages (allowed: system, user, assistant)` }
  }
  return { ok: true, messages: out }
}

type ResolvedModel =
  | { backend: 'gs-ai'; role: 'generator' | 'cheap' | 'planner' }
  | { backend: 'openrouter'; models: string[] }

/**
 * Shim-only model resolution. Deliberately does NOT import resolveModelRoute
 * (that is the production router entry) — the shim pins explicit targets so a
 * future routing change can never silently invalidate a benchmark run.
 */
type ResolvedShimModel = ResolvedModel & {
  profile: 'baseline' | 'aci' | 'verification' | 'context' | 'router'
}

export function resolveShimModel(model: string | undefined): ResolvedShimModel | null {
  // BENCH SCAFFOLD — `gs-ai@<profile>` selects a scaffold variant; the bare id
  // (and `@baseline`) keeps the byte-identical raw-synthesis path so every
  // historical run remains comparable (rule 2: same model, only scaffold moves).
  const parsed = parseScaffoldModel(model)
  if (!parsed) return null
  const m = parsed.base
  const profile = parsed.profile
  const resolved = resolveShimBaseModel(m)
  return resolved ? { ...resolved, profile } : null
}

function resolveShimBaseModel(m: string): ResolvedModel | null {
  switch (m) {
    case 'gs-ai':
      return { backend: 'gs-ai', role: 'generator' }
    case 'gs-ai-flash':
      return { backend: 'gs-ai', role: 'cheap' }
    case 'gs-ai-thinking':
      return { backend: 'gs-ai', role: 'planner' }
    default:
      if (m.includes('/')) return { backend: 'openrouter', models: [m] }
      return null
  }
}

function profileOf(model: string | undefined): 'baseline' | 'aci' | 'verification' | 'context' | 'router' {
  return parseScaffoldModel(model)?.profile ?? 'baseline'
}

function openAiError(status: number, code: string, message: string) {
  return NextResponse.json(
    { error: { message, type: code, code } },
    { status, headers: { 'x-gs-bench': 'openai-shim' } }
  )
}

export async function POST(req: NextRequest) {
  if (!authOk(req)) {
    return openAiError(401, 'invalid_api_key', 'Missing or invalid bearer key (GS_BENCH_API_KEY).')
  }

  let body: ChatCompletionsBody
  try {
    body = (await req.json()) as ChatCompletionsBody
  } catch {
    return openAiError(400, 'invalid_request_error', 'Body must be valid JSON.')
  }

  const parsed = toInternalMessages(body.messages)
  if (!parsed.ok) return openAiError(400, 'invalid_request_error', parsed.error)

  const resolved = resolveShimModel(body.model)
  if (!resolved) {
    return openAiError(
      404,
      'model_not_found',
      `Unknown model '${body.model}'. Serving: gs-ai, gs-ai-flash, gs-ai-thinking, any vendor/model id for OpenRouter passthrough, and gs-ai@<baseline|aci|verification|context|router> scaffold variants.`
    )
  }
  const profile = profileOf(body.model)
  if (profile !== 'baseline' && resolved.backend !== 'gs-ai') {
    // The scaffold levers pin the generator to the gs-ai tier by design (rule 2).
    // A vendor passthrough with a profile would silently measure a different
    // generator — refuse instead (rule 5: no silent substitutions).
    return openAiError(
      400,
      'invalid_request_error',
      `Scaffold profile '${profile}' is only valid on gs-ai tier models (generator is pinned); got '${body.model}'.`
    )
  }

  const startedAt = Date.now()
  try {
    let text = ''
    let servedModel: string | null = null
    let scaffoldTelemetry: Record<string, unknown> | null = null

    if (profile !== 'baseline') {
      // BENCH SCAFFOLD — lever path. The scaffold layer owns generator/verifier/
      // planner role calls and returns per-call telemetry (rule 4).
      const outcome = await runScaffoldCompletion(parsed.messages, profile)
      text = outcome.text
      servedModel = outcome.servedModel
      scaffoldTelemetry = {
        profile: outcome.profile,
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
        scaffold_latency_ms: outcome.latencyMs,
        notes: outcome.notes.slice(0, 12),
      }
    } else if (resolved.backend === 'gs-ai') {
      const result = await runGsProviderChat(parsed.messages, {
        role: resolved.role,
        temperature: body.temperature ?? 0.2,
        maxTokens: body.max_tokens,
      })
      text = result.text
      servedModel = result.servedModel
    } else {
      const meta = await orCompleteChat(parsed.messages, resolved.models, null)
      text = meta.text
      servedModel = meta.model
    }

    const id = `chatcmpl-gs-${Date.now().toString(36)}`
    const created = Math.floor(Date.now() / 1000)
    const latencyMs = Date.now() - startedAt
    console.log(
      `BENCH-SHIM kind=chat_completions requested=${body.model ?? 'gs-ai'} profile=${profile} served=${servedModel ?? 'unknown'} latencyMs=${latencyMs} chars=${text.length}`
    )

    const promptTokens = Math.ceil(parsed.messages.reduce((n, m) => n + m.content.length, 0) / 4)
    const completionTokens = Math.ceil(text.length / 4)
    const payload: Record<string, unknown> = {
      id,
      object: 'chat.completion',
      created,
      model: servedModel ?? body.model ?? 'gs-ai',
      system_fingerprint: profile === 'baseline' ? 'gs-bench-shim-v1' : `gs-bench-shim-v1;profile=${profile}`,
      choices: [
        {
          index: 0,
          message: { role: 'assistant', content: text },
          finish_reason: 'stop',
          logprobs: null,
        },
      ],
      usage: {
        // Char-based ESTIMATE; the shim's upstreams do not expose token
        // counts on every path. Not used for scoring.
        prompt_tokens: promptTokens,
        completion_tokens: completionTokens,
        total_tokens: promptTokens + completionTokens,
      },
    }
    if (scaffoldTelemetry) payload.gs_scaffold = scaffoldTelemetry
    return NextResponse.json(payload, {
      headers: {
        'x-gs-bench': 'openai-shim',
        ...(profile !== 'baseline' ? { 'x-gs-scaffold': profile } : {}),
      },
    })
  } catch (e) {
    const message = e instanceof Error ? e.message : String(e)
    console.error(
      `BENCH-SHIM-ERROR kind=chat_completions requested=${body.model ?? 'gs-ai'} profile=${profile}: ${message.slice(0, 300)}`
    )
    if (e instanceof GsAllProvidersUnavailableError) {
      return NextResponse.json(
        {
          error: {
            message,
            type: 'all_providers_unavailable',
            code: e.code,
            providers: e.attempts,
          },
        },
        { status: 503, headers: { 'x-gs-bench': 'openai-shim' } }
      )
    }
    return openAiError(502, 'upstream_error', message.slice(0, 500))
  }
}
