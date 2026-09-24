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
 *   gs-ai              → production synthesis flagship  (z-ai, glm-4.6,
 *                        thinking disabled, temperature 0.2 — the exact
 *                        completeChatWithMeta synthesis defaults)
 *   gs-ai-flash        → production flash tier          (z-ai, glm-4.5-flash,
 *                        thinking disabled)
 *   gs-ai-thinking     → production thinking tier       (z-ai, glm-4.6,
 *                        thinking enabled, reasoning_effort=high)
 *   <vendor/model>     → OpenRouter passthrough (e.g. openai/gpt-5.5),
 *                        routed through the existing orCompleteChat key-pool
 *                        chain so competitor probes use the same accounting
 *                        as production free-tier traffic.
 *
 * NOTE ON PUBLISHED BASELINES: external comms refer to the GS AI flagship as
 * "GLM-5.2"; the serving catalogue in this repo maps the synthesis tiers to
 * glm-4.6 (src/lib/models.ts PROVIDER_MODELS). The shim never renames the
 * provider: the `model` field of every response carries the SERVING model id
 * returned by the upstream provider meta, so scorecards always show what
 * actually answered.
 *
 * Optional auth: if env GS_BENCH_API_KEY is set, requests must present
 * `Authorization: Bearer <key>`. Unset (current state) = open, same trust
 * level as the rest of the public /api/v1 surface.
 */

import { NextRequest, NextResponse } from 'next/server'
import { completeChatWithMeta } from '@/lib/ai'
import { orCompleteChat } from '@/lib/openrouter'

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
  | {
      backend: 'zai'
      providerModel: string | null
      thinking: { mode: 'flash' | 'thinking'; effort?: string } | null
    }
  | { backend: 'openrouter'; models: string[] }

/**
 * Shim-only model resolution. Deliberately does NOT import resolveModelRoute
 * (that is the production router entry) — the shim pins explicit targets so a
 * future routing change can never silently invalidate a benchmark run.
 */
export function resolveShimModel(model: string | undefined): ResolvedModel | null {
  const m = (model ?? 'gs-ai').trim()
  switch (m) {
    case 'gs-ai':
      return { backend: 'zai', providerModel: 'glm-4.6', thinking: { mode: 'flash' } }
    case 'gs-ai-flash':
      return { backend: 'zai', providerModel: 'glm-4.5-flash', thinking: { mode: 'flash' } }
    case 'gs-ai-thinking':
      return {
        backend: 'zai',
        providerModel: 'glm-4.6',
        thinking: { mode: 'thinking', effort: 'high' },
      }
    default:
      if (m.includes('/')) return { backend: 'openrouter', models: [m] }
      return null
  }
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
      `Unknown model '${body.model}'. Serving: gs-ai, gs-ai-flash, gs-ai-thinking, or any vendor/model id for OpenRouter passthrough.`
    )
  }

  const startedAt = Date.now()
  try {
    let text = ''
    let servedModel: string | null = null

    if (resolved.backend === 'zai') {
      const meta = await completeChatWithMeta(parsed.messages, resolved.providerModel, resolved.thinking)
      text = meta.text
      servedModel = meta.model
    } else {
      const meta = await orCompleteChat(parsed.messages, resolved.models, null)
      text = meta.text
      servedModel = meta.model
    }

    const id = `chatcmpl-gs-${Date.now().toString(36)}`
    const created = Math.floor(Date.now() / 1000)
    const latencyMs = Date.now() - startedAt
    console.log(
      `BENCH-SHIM kind=chat_completions requested=${body.model ?? 'gs-ai'} served=${servedModel ?? 'unknown'} latencyMs=${latencyMs} chars=${text.length}`
    )

    const promptTokens = Math.ceil(parsed.messages.reduce((n, m) => n + m.content.length, 0) / 4)
    const completionTokens = Math.ceil(text.length / 4)
    return NextResponse.json(
      {
        id,
        object: 'chat.completion',
        created,
        model: servedModel ?? body.model ?? 'gs-ai',
        system_fingerprint: 'gs-bench-shim-v1',
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
      },
      { headers: { 'x-gs-bench': 'openai-shim' } }
    )
  } catch (e) {
    const message = e instanceof Error ? e.message : String(e)
    console.error(
      `BENCH-SHIM-ERROR kind=chat_completions requested=${body.model ?? 'gs-ai'}: ${message.slice(0, 300)}`
    )
    return openAiError(502, 'upstream_error', message.slice(0, 500))
  }
}
