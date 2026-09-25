/**
 * BENCHMARK INFRA — PHASE 1: OpenAI-compatible shim (legacy /v1/completions).
 *
 * Required by lm-eval for MCQ tasks (MMLU, HellaSwag, ARC) which score via
 * per-token logprobs of the answer-letter continuation. The shim's upstreams
 * do not surface `logprobs` on these paths, so a faithful logprob-based MCQ
 * implementation is NOT possible here.
 *
 * HARD RULE 7 (never fabricate): instead of silently degrading to a
 * generation-based approximation and reporting it as the standard metric,
 * this endpoint returns 501 Not Implemented with the exact reason. Harnesses
 * that support a generation-based MCQ mode (inspect-ai multiple_choice) can
 * still evaluate MCQ suites through /chat/completions with an explicit
 * `multiple_choice` template — that path is honest and is what the
 * knowledge suite configs use.
 */

import { NextRequest, NextResponse } from 'next/server'
import { orCompleteChat } from '@/lib/openrouter'
import {
  GsAllProvidersUnavailableError,
  runGsProviderChat,
} from '@/lib/gs-provider-router'

export const runtime = 'nodejs'
export const maxDuration = 300

type CompletionsBody = {
  model?: string
  prompt?: string | string[]
  max_tokens?: number
  temperature?: number
  logprobs?: number | boolean
  echo?: boolean
  stream?: boolean
}

function openAiError(status: number, code: string, message: string) {
  return NextResponse.json(
    { error: { message, type: code, code } },
    { status, headers: { 'x-gs-bench': 'openai-shim' } }
  )
}

function normalizePrompt(p: string | string[] | undefined): string[] {
  if (typeof p === 'string') return [p]
  if (Array.isArray(p)) return p.map((x) => (typeof x === 'string' ? x : ''))
  return []
}

export async function POST(req: NextRequest) {
  let body: CompletionsBody
  try {
    body = (await req.json()) as CompletionsBody
  } catch {
    return openAiError(400, 'invalid_request_error', 'Body must be valid JSON.')
  }

  // logprobs requested → we cannot serve it honestly.
  const wantsLogprobs = body.logprobs !== undefined && body.logprobs !== null && body.logprobs !== 0
  if (wantsLogprobs || body.echo) {
    return openAiError(
      501,
      'not_implemented',
      'logprobs/echo cannot be produced by the GS AI shim upstreams. Use a harness generation mode (inspect-ai multiple_choice) over /api/v1/openai/chat/completions instead of logprob MCQ scoring.'
    )
  }

  const prompts = normalizePrompt(body.prompt)
  if (prompts.length === 0) {
    return openAiError(400, 'invalid_request_error', 'prompt must be a string or non-empty array.')
  }
  if (body.stream) {
    return openAiError(501, 'not_implemented', 'stream=true is not supported on /completions; use /chat/completions with stream handling in the harness.')
  }

  const model = (body.model ?? 'gs-ai').trim()
  const normalized = model.replace(/^openai-api\/openai\//, '').replace(/^openai\//, '')
  const isGsModel = ['gs-ai', 'gs-ai-flash', 'gs-ai-thinking'].includes(normalized)
  const isVendor = !isGsModel && model.includes('/')
  if (!isGsModel && !isVendor) {
    return openAiError(404, 'model_not_found', `Unknown model '${model}'.`)
  }

  const startedAt = Date.now()
  try {
    let servedModel: string | null = null
    const choices = [] as {
      index: number
      text: string
      finish_reason: string
      logprobs: null
    }[]

    for (let i = 0; i < prompts.length; i++) {
      const messages = [{ role: 'user', content: prompts[i] }]
      let text = ''
      if (isVendor) {
        const result = await orCompleteChat(messages, [model], null)
        text = result.text
        servedModel = `openrouter/${result.model ?? model}`
      } else {
        const result = await runGsProviderChat(messages, {
          role: normalized === 'gs-ai-flash' ? 'cheap' : normalized === 'gs-ai-thinking' ? 'planner' : 'generator',
          temperature: body.temperature ?? 0.2,
          maxTokens: body.max_tokens,
        })
        text = result.text
        servedModel = result.servedModel
      }
      choices.push({ index: i, text, finish_reason: 'stop', logprobs: null })
    }

    console.log(
      `BENCH-SHIM kind=completions requested=${model} n=${prompts.length} latencyMs=${Date.now() - startedAt}`
    )
    return NextResponse.json(
      {
        id: `cmpl-gs-${Date.now().toString(36)}`,
        object: 'text_completion',
        created: Math.floor(Date.now() / 1000),
        model: servedModel ?? model,
        choices,
        usage: {
          prompt_tokens: Math.ceil(prompts.join('').length / 4),
          completion_tokens: Math.ceil(choices.reduce((n, c) => n + c.text.length, 0) / 4),
        },
      },
      { headers: { 'x-gs-bench': 'openai-shim' } }
    )
  } catch (e) {
    const message = e instanceof Error ? e.message : String(e)
    console.error(`BENCH-SHIM-ERROR kind=completions requested=${model}: ${message.slice(0, 300)}`)
    if (e instanceof GsAllProvidersUnavailableError) {
      return NextResponse.json(
        { error: { message, type: e.code, code: e.code, providers: e.attempts } },
        { status: 503, headers: { 'x-gs-bench': 'openai-shim' } }
      )
    }
    return openAiError(502, 'upstream_error', message.slice(0, 500))
  }
}
