/**
 * BENCHMARK INFRA — Phase 1b: OpenAI Responses API adapter.
 *
 * WHY THIS EXISTS (raw evidence, run 36040334662): inspect-ai 0.3.268's
 * `openai` provider defaults to the OpenAI Responses API and POSTs
 * {base}/responses — the shim 404'd it (sandbox dev.log:
 * "POST /api/v1/openai/responses 404"). This endpoint adapts the Responses
 * shape onto the same raw synthesis path as /chat/completions (router still
 * bypassed, no system prompt injected).
 *
 * Adapters that want classic chat completions keep using /chat/completions
 * (inspect's `openai-api` provider, litellm, lm-eval, tau2).
 */

import { NextRequest, NextResponse } from 'next/server'
import { completeChatWithMeta } from '@/lib/ai'
import { orCompleteChat } from '@/lib/openrouter'
import { authOkShim, flattenContentShim } from '../shared'

export const runtime = 'nodejs'
export const maxDuration = 300

type ResponsesBody = {
  model?: string
  input?: string | Array<{ role?: string; content?: unknown; type?: string }>
  instructions?: string
  max_output_tokens?: number
  temperature?: number
  stream?: boolean
}

export async function POST(req: NextRequest) {
  if (!authOkShim(req)) {
    return NextResponse.json(
      { error: { message: 'Missing or invalid bearer key.', code: 'invalid_api_key' } },
      { status: 401 }
    )
  }

  let body: ResponsesBody
  try {
    body = (await req.json()) as ResponsesBody
  } catch {
    return NextResponse.json(
      { error: { message: 'Body must be valid JSON.', code: 'invalid_request_error' } },
      { status: 400 }
    )
  }

  if (body.stream) {
    return NextResponse.json(
      {
        error: {
          message: 'stream=true is not supported by the shim responses adapter; use non-streaming clients.',
          code: 'not_implemented',
        },
      },
      { status: 501 }
    )
  }

  const messages: { role: string; content: string }[] = []
  if (typeof body.instructions === 'string' && body.instructions.length > 0) {
    messages.push({ role: 'system', content: body.instructions })
  }
  if (typeof body.input === 'string') {
    messages.push({ role: 'user', content: body.input })
  } else if (Array.isArray(body.input)) {
    for (const item of body.input) {
      const role = typeof item?.role === 'string' ? item.role : 'user'
      messages.push({ role, content: flattenContentShim(item?.content) })
    }
  }
  if (messages.length === 0) {
    return NextResponse.json(
      { error: { message: 'input must be a string or non-empty array.', code: 'invalid_request_error' } },
      { status: 400 }
    )
  }

  const resolved = resolveShimModelLocal(body.model)
  if (!resolved) {
    return NextResponse.json(
      {
        error: {
          message: `Unknown model '${body.model}'. Serving: gs-ai, gs-ai-flash, gs-ai-thinking, or vendor/model passthrough.`,
          code: 'model_not_found',
        },
      },
      { status: 404 }
    )
  }

  const startedAt = Date.now()
  try {
    let text = ''
    let servedModel: string | null = null
    if (resolved.backend === 'zai') {
      const meta = await completeChatWithMeta(messages, resolved.providerModel, resolved.thinking)
      text = meta.text
      servedModel = meta.model
    } else {
      const meta = await orCompleteChat(messages, resolved.models, null)
      text = meta.text
      servedModel = meta.model
    }
    const created = Math.floor(Date.now() / 1000)
    console.log(
      `BENCH-SHIM kind=responses requested=${body.model ?? 'gs-ai'} served=${servedModel ?? 'unknown'} latencyMs=${Date.now() - startedAt} chars=${text.length}`
    )
    return NextResponse.json({
      id: `resp-gs-${Date.now().toString(36)}`,
      object: 'response',
      created_at: created,
      status: 'completed',
      model: servedModel ?? body.model ?? 'gs-ai',
      output: [
        {
          type: 'message',
          id: `msg-gs-${Date.now().toString(36)}`,
          role: 'assistant',
          status: 'completed',
          content: [{ type: 'output_text', text, annotations: [] }],
        },
      ],
      output_text: text,
      usage: {
        // Char-based ESTIMATE (documented); upstreams do not expose token counts.
        input_tokens: Math.ceil(messages.reduce((n, m) => n + m.content.length, 0) / 4),
        output_tokens: Math.ceil(text.length / 4),
        total_tokens:
          Math.ceil(messages.reduce((n, m) => n + m.content.length, 0) / 4) +
          Math.ceil(text.length / 4),
      },
    })
  } catch (e) {
    const message = e instanceof Error ? e.message : String(e)
    console.error(`BENCH-SHIM-ERROR kind=responses requested=${body.model ?? 'gs-ai'}: ${message.slice(0, 300)}`)
    return NextResponse.json(
      { error: { message: message.slice(0, 500), code: 'upstream_error' } },
      { status: 502 }
    )
  }
}

// Local copy of the shim model resolution (chat/completions route owns the
// canonical one); kept identical so future drift shows in code review.
function resolveShimModelLocal(model: string | undefined):
  | { backend: 'zai'; providerModel: string | null; thinking: { mode: 'flash' | 'thinking'; effort?: string } | null }
  | { backend: 'openrouter'; models: string[] }
  | null {
  const m = (model ?? 'gs-ai').trim()
  switch (m) {
    case 'gs-ai':
      return { backend: 'zai', providerModel: 'glm-4.6', thinking: { mode: 'flash' } }
    case 'gs-ai-flash':
      return { backend: 'zai', providerModel: 'glm-4.5-flash', thinking: { mode: 'flash' } }
    case 'gs-ai-thinking':
      return { backend: 'zai', providerModel: 'glm-4.6', thinking: { mode: 'thinking', effort: 'high' } }
    default:
      if (m.includes('/')) return { backend: 'openrouter', models: [m] }
      return null
  }
}
