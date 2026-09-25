import { describe, expect, test } from 'bun:test'
import { isFreeCapacityUnavailableError } from './capacity-policy'
import {
  GENERIC_TEXT_PROVIDER_IDS,
  PROVIDER_EXECUTION_ENDPOINTS,
  ProviderExecutionError,
  executeProviderCompletion,
  executeProviderStream,
  isProviderCapacityError,
  type ProviderExecutionFetch,
} from './provider-execution'

const messages = [{ role: 'user' as const, content: 'hello' }]

function jsonResponse(value: unknown, status = 200): Response {
  return new Response(JSON.stringify(value), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

function sseResponse(parts: readonly string[]): Response {
  const encoder = new TextEncoder()
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      for (const part of parts) controller.enqueue(encoder.encode(part))
      controller.close()
    },
  })
  return new Response(body, {
    status: 200,
    headers: { 'content-type': 'text/event-stream' },
  })
}

describe('generic provider execution', () => {
  test('uses each provider allow-listed endpoint and explicit auth header', async () => {
    const secret = 'explicit-provider-key'
    const seen: { url: string; headers: Headers; body: Record<string, unknown> }[] = []

    for (const provider of GENERIC_TEXT_PROVIDER_IDS) {
      const fetchImpl: ProviderExecutionFetch = async (input, init) => {
        const headers = new Headers(init?.headers)
        const body = JSON.parse(String(init?.body)) as Record<string, unknown>
        seen.push({ url: String(input), headers, body })
        return jsonResponse({
          model: `${provider}-served`,
          choices: [{ message: { content: `ok:${provider}` } }],
        })
      }

      const result = await executeProviderCompletion({
        provider,
        model: `${provider}-model`,
        messages,
        apiKey: secret,
        headers: { 'X-Test-Request': 'present' },
        fetchImpl,
      })

      expect(result).toMatchObject({ provider, text: `ok:${provider}` })
      const call = seen[seen.length - 1]
      expect(call.url).toBe(PROVIDER_EXECUTION_ENDPOINTS[provider])
      expect(call.headers.get('authorization')).toBe(`Bearer ${secret}`)
      expect(call.headers.get('content-type')).toBe('application/json')
      expect(call.headers.get('accept')).toBe('application/json')
      expect(call.headers.get('x-test-request')).toBe('present')
      expect(call.body).toMatchObject({ model: `${provider}-model`, stream: false })
      expect(String(initBody(call.body))).not.toContain(secret)
    }
  })

  test('parses streamed deltas with the shared SSE semantics', async () => {
    const deltas: string[] = []
    const fetchImpl: ProviderExecutionFetch = async () =>
      sseResponse([
        'data: {"model":"served-stream-model","choices":[{"delta":{"content":"Hel"}}]}\n\n',
        ': heartbeat\n\n',
        'data: {"choices":[{"delta":{"content":"lo"}}]}\n\n',
        'data: [DONE]\n\n',
      ])

    const result = await executeProviderStream(
      {
        provider: 'groq',
        model: 'requested-model',
        messages,
        apiKey: 'stream-key',
        fetchImpl,
      },
      (delta) => {
        deltas.push(delta)
      },
    )

    expect(deltas).toEqual(['Hel', 'lo'])
    expect(result).toEqual({
      provider: 'groq',
      text: 'Hello',
      model: 'served-stream-model',
    })
  })

  test('bounds a hanging native request and classifies it as retryable timeout', async () => {
    const fetchImpl: ProviderExecutionFetch = (_input, init) =>
      new Promise<Response>((_resolve, reject) => {
        init?.signal?.addEventListener('abort', () => reject(new Error('raw abort detail')), {
          once: true,
        })
      })

    await expect(
      executeProviderCompletion({
        provider: 'cerebras',
        model: 'test-model',
        messages,
        apiKey: 'timeout-key',
        timeoutMs: 5,
        fetchImpl,
      }),
    ).rejects.toMatchObject({
      code: 'timeout',
      retryable: true,
    })
  })

  test('maps provider capacity responses to the existing typed queue error', async () => {
    const secret = 'capacity-body-must-not-escape'
    const fetchImpl: ProviderExecutionFetch = async () =>
      new Response(JSON.stringify({ error: { message: secret } }), {
        status: 429,
        headers: { 'content-type': 'application/json', 'retry-after': '2' },
      })

    let caught: unknown
    try {
      await executeProviderCompletion({
        provider: 'openrouter',
        model: 'test-model',
        messages,
        apiKey: 'capacity-key',
        fetchImpl,
      })
    } catch (error) {
      caught = error
    }

    expect(isProviderCapacityError(caught)).toBe(true)
    expect(isFreeCapacityUnavailableError(caught)).toBe(true)
    expect(caught).toMatchObject({
      code: 'free_capacity_unavailable',
      retryable: true,
      status: 429,
      retryAfterMs: 2_000,
    })
    expect(String(caught)).not.toContain(secret)
    expect(JSON.stringify(caught)).not.toContain(secret)
  })

  test('does not put credentials or raw upstream errors in results/errors', async () => {
    const secret = 'secret-that-must-never-leak'
    const successfulFetch: ProviderExecutionFetch = async () =>
      jsonResponse({ model: 'safe-model', choices: [{ message: { content: 'safe answer' } }] })
    const success = await executeProviderCompletion({
      provider: 'dmxapi',
      model: 'test-model',
      messages,
      apiKey: secret,
      fetchImpl: successfulFetch,
    })
    expect(JSON.stringify(success)).not.toContain(secret)

    const failingFetch: ProviderExecutionFetch = async () => {
      throw new Error(`network failed with ${secret}`)
    }
    let caught: unknown
    try {
      await executeProviderCompletion({
        provider: 'dmxapi',
        model: 'test-model',
        messages,
        apiKey: secret,
        fetchImpl: failingFetch,
      })
    } catch (error) {
      caught = error
    }
    expect(caught).toBeInstanceOf(ProviderExecutionError)
    expect(String(caught)).not.toContain(secret)
    expect(JSON.stringify(caught)).not.toContain(secret)
  })

  test('rejects Google and BFL without attempting a guessed completion call', async () => {
    let calls = 0
    const fetchImpl: ProviderExecutionFetch = async () => {
      calls += 1
      return jsonResponse({ choices: [{ message: { content: 'must not be called' } }] })
    }

    for (const provider of ['google-gemini', 'bfl'] as const) {
      await expect(
        executeProviderCompletion({
          provider,
          model: 'test-model',
          messages,
          apiKey: 'unused-key',
          fetchImpl,
        }),
      ).rejects.toMatchObject({ code: 'unsupported-provider', retryable: false })
    }
    expect(calls).toBe(0)
  })
})

function initBody(body: Record<string, unknown>): string {
  return JSON.stringify(body)
}
