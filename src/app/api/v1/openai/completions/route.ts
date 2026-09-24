/**
 * BENCHMARK INFRA — PHASE 1: OpenAI-compatible shim (legacy /v1/completions).
 *
 * Required by lm-eval for MCQ tasks (MMLU, HellaSwag, ARC) which score via
 * per-token logprobs of the answer-letter continuation. The shim's upstreams
 * (z-ai SDK endpoint and the OpenRouter free-pool chain as wired in this repo)
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
import { completeChatWithMeta } from '@/lib/ai'
import { orCompleteChat } from '@/lib/openrouter'

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
      'logprobs/echo cannot be produced by the GS AI shim upstreams (z-ai SDK and the OpenRouter free-pool chain do not expose per-token logprobs on these paths). Use a harness generation mode (inspect-ai multiple_choice) over /api/v1/openai/chat/completions instead of logprob MCQ scoring.'
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
  const isVendor = model.includes('/')
  if (!isVendor && !['gs-ai', 'gs-ai-flash', 'gs-ai-thinking'].includes(model)) {
    return openAiError(404, 'model_not_found', `Unknown model '${model}'.`)
  }

  const startedAt = Date.now()
  try {
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
        text = (await orCompleteChat(messages, [model], null)).text
      } else {
        const providerModel = model === 'gs-ai-flash' ? 'glm-4.5-flash' : 'glm-4.6'
        const thinking = model === 'gs-ai-thinking' ? { mode: 'thinking' as const, effort: 'high' } : null
        text = await completeChatWithMeta(messages, providerModel, thinking).then((m) => m.text)
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
        model,
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
    return openAiError(502, 'upstream_error', message.slice(0, 500))
  }
}
