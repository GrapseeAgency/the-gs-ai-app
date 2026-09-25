import { describe, expect, test } from 'bun:test'
import {
  GsAllProvidersUnavailableError,
  ensureGsProviderHealth,
  runGsProviderChat,
} from './gs-provider-router'

function response(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

describe('GS provider router', () => {
  test('health-checks providers once, caches for five minutes, and honors priority fallback', async () => {
    const calls: string[] = []
    const secretGoogle = 'google-secret-value'
    const secretGroq = 'groq-secret-value'
    const fetchImpl = async (input: string | URL | Request): Promise<Response> => {
      const url = String(input)
      calls.push(url)
      if (url.includes('generativelanguage.googleapis.com')) {
        return response({ error: { message: 'dead google' } }, 401)
      }
      if (url.includes('api.groq.com')) {
        return response({ model: 'groq-served', choices: [{ message: { content: 'ok' } }] })
      }
      return response({ error: { message: 'unexpected provider' } }, 500)
    }
    const env = {
      GOOGLE_API_KEY_1: secretGoogle,
      GROQ_API_KEY_1: secretGroq,
    } as unknown as NodeJS.ProcessEnv

    const first = await ensureGsProviderHealth({ env, fetchImpl, forceHealth: true, maxTokens: 256 })
    expect(first.find((record) => record.provider === 'google')?.healthy).toBe(false)
    expect(first.find((record) => record.provider === 'groq')?.healthy).toBe(true)
    const afterFirstHealth = calls.length
    await ensureGsProviderHealth({ env, fetchImpl })
    expect(calls.length).toBe(afterFirstHealth)

    const result = await runGsProviderChat([{ role: 'user', content: 'hi' }], {
      env,
      fetchImpl,
      role: 'generator',
      maxTokens: 256,
    })
    expect(result.provider).toBe('groq')
    expect(result.servedModel).toBe('groq/groq-served')
    expect(JSON.stringify(result)).not.toContain(secretGoogle)
    expect(JSON.stringify(result)).not.toContain(secretGroq)
  })

  test('returns redacted per-provider attempts when nothing is live', async () => {
    const secrets = {
      OPENROUTER_API_KEY_1: 'openrouter-secret',
      CEREBRAS_API_KEY_1: 'cerebras-secret',
    }
    const fetchImpl = async (): Promise<Response> => response({ error: { message: 'unauthorized' } }, 401)
    let caught: unknown
    try {
      await runGsProviderChat([{ role: 'user', content: 'hi' }], {
        env: secrets as unknown as NodeJS.ProcessEnv,
        fetchImpl,
        forceHealth: true,
      })
    } catch (error) {
      caught = error
    }
    expect(caught).toBeInstanceOf(GsAllProvidersUnavailableError)
    const serialized = JSON.stringify(caught)
    expect(serialized).toContain('openrouter')
    expect(serialized).toContain('cerebras')
    expect(serialized).not.toContain('openrouter-secret')
    expect(serialized).not.toContain('cerebras-secret')
  })

  test('keeps a provider live when the probe returns a truncated but well-formed completion', async () => {
    // Gemini answers a 1-token probe with HTTP 200, `finish_reason: length` and
    // no content. That is a live provider, not a dead one.
    const fetchImpl = async (input: string | URL | Request): Promise<Response> => {
      const url = String(input)
      if (url.includes('generativelanguage.googleapis.com')) {
        return response({ model: 'gemini-2.5-flash', choices: [{ finish_reason: 'length', message: { role: 'assistant' } }] })
      }
      return response({ error: { message: 'unauthorized' } }, 401)
    }
    const health = await ensureGsProviderHealth({
      env: { GOOGLE_API_KEY_1: 'google-secret' } as unknown as NodeJS.ProcessEnv,
      fetchImpl,
      forceHealth: true,
      maxTokens: 256,
    })
    expect(health.find((record) => record.provider === 'google')?.healthy).toBe(true)
  })

  test('never truncates: a provider below the requested budget is ineligible', async () => {
    const budgets: number[] = []
    const urls: string[] = []
    const fetchImpl = async (input: string | URL | Request, init?: RequestInit): Promise<Response> => {
      const url = String(input)
      const body = JSON.parse(String(init?.body ?? '{}')) as { max_tokens?: number }
      if (body.max_tokens === 1) return response({ model: 'probe', choices: [{ message: { content: 'ok' } }] })
      if (url.includes('api.groq.com') || url.includes('generativelanguage.googleapis.com')) {
        urls.push(url)
        if (typeof body.max_tokens === 'number') budgets.push(body.max_tokens)
        return response({ model: 'served', choices: [{ message: { content: 'ok' } }] })
      }
      return response({ error: { message: 'unauthorized' } }, 401)
    }
    const env = {
      GOOGLE_API_KEY_1: 'google-secret',
      GROQ_API_KEY_1: 'groq-secret',
    } as unknown as NodeJS.ProcessEnv

    // Default budget is 4096, which Groq (ceiling 512) cannot honour, so Groq
    // must be skipped rather than served a silently truncated answer.
    await runGsProviderChat([{ role: 'user', content: 'hi' }], { env, fetchImpl, forceHealth: true })
    expect(budgets.every((b) => b === 4096)).toBe(true)
    expect(urls.some((u) => u.includes('api.groq.com'))).toBe(false)

    // With a budget everyone can meet, the full budget is sent verbatim.
    budgets.length = 0
    urls.length = 0
    await runGsProviderChat([{ role: 'user', content: 'hi' }], { env, fetchImpl, maxTokens: 256, forceHealth: true })
    expect(budgets[budgets.length - 1]).toBe(256)
  })

  test('fails loudly when no provider can honour the requested budget', async () => {
    const fetchImpl = async (): Promise<Response> =>
      response({ model: 'probe', choices: [{ message: { content: 'ok' } }] })
    let caught: unknown
    try {
      await runGsProviderChat([{ role: 'user', content: 'hi' }], {
        env: { GROQ_API_KEY_1: 'groq-secret' } as unknown as NodeJS.ProcessEnv,
        fetchImpl,
        maxTokens: 999_999,
        forceHealth: true,
      })
    } catch (error) {
      caught = error
    }
    expect(caught).toBeInstanceOf(GsAllProvidersUnavailableError)
    expect(JSON.stringify(caught)).toContain('budget_too_small')
  })

  test('retries a throttled provider instead of writing a five-minute death record', async () => {
    let groqRequests = 0
    let health = 0
    const fetchImpl = async (input: string | URL | Request, init?: RequestInit): Promise<Response> => {
      const url = String(input)
      if (!url.includes('api.groq.com')) return response({ error: { message: 'unauthorized' } }, 401)
      const body = JSON.parse(String(init?.body ?? '{}')) as { max_tokens?: number }
      if (body.max_tokens === 1) {
        health += 1
        return response({ model: 'qwen', choices: [{ message: { content: 'ok' } }] })
      }
      groqRequests += 1
      // Throttled once, then recovers: a 429 must not end the request.
      if (groqRequests === 1) return response({ error: { message: 'Rate limit reached' } }, 429)
      return response({ model: 'qwen/qwen3.8-27b', choices: [{ message: { content: 'recovered' } }] })
    }
    const env = { GROQ_API_KEY_1: 'groq-secret' } as unknown as NodeJS.ProcessEnv
    const result = await runGsProviderChat([{ role: 'user', content: 'hi' }], { env, fetchImpl, forceHealth: true, maxTokens: 256 })
    expect(groqRequests).toBe(2)
    expect(result.text).toBe('recovered')
    expect(health).toBe(1)
  })

  test('does not retry a permanent failure, and keeps it dead until the next probe', async () => {
    let groqRequests = 0
    let probes = 0
    const fetchImpl = async (input: string | URL | Request, init?: RequestInit): Promise<Response> => {
      const url = String(input)
      if (!url.includes('api.groq.com')) return response({ error: { message: 'unauthorized' } }, 401)
      const body = JSON.parse(String(init?.body ?? '{}')) as { max_tokens?: number }
      // The probe succeeds; only the real request is rejected permanently.
      if (body.max_tokens === 1) {
        probes += 1
        return response({ model: 'qwen', choices: [{ message: { content: 'ok' } }] })
      }
      groqRequests += 1
      return response({ error: { message: 'Payment required' } }, 402)
    }
    const env = { GROQ_API_KEY_1: 'groq-secret' } as unknown as NodeJS.ProcessEnv
    await runGsProviderChat([{ role: 'user', content: 'hi' }], { env, fetchImpl, forceHealth: true, maxTokens: 256 }).catch(() => undefined)
    // A 402 cannot become valid by asking again, so it is attempted once.
    expect(groqRequests).toBe(1)
    // And the next call neither re-dials it nor re-probes the fresh record.
    await runGsProviderChat([{ role: 'user', content: 'hi' }], { env, fetchImpl, maxTokens: 256, exhaustionBudgetMs: 0 }).catch(() => undefined)
    expect(groqRequests).toBe(1)
    expect(probes).toBe(1)
  })

  test('gives a throttled provider a short cooldown, not a five-minute death', async () => {
    let groqRequests = 0
    const fetchImpl = async (input: string | URL | Request, init?: RequestInit): Promise<Response> => {
      const url = String(input)
      if (!url.includes('api.groq.com')) return response({ error: { message: 'unauthorized' } }, 401)
      const body = JSON.parse(String(init?.body ?? '{}')) as { max_tokens?: number }
      if (body.max_tokens === 1) return response({ model: 'qwen', choices: [{ message: { content: 'ok' } }] })
      groqRequests += 1
      return response({ error: { message: 'Rate limit reached' } }, 429)
    }
    const env = { GROQ_API_KEY_1: 'groq-secret' } as unknown as NodeJS.ProcessEnv
    // A 30ms cooldown keeps the test fast while still exercising the real path.
    const failed = await runGsProviderChat([{ role: 'user', content: 'hi' }], {
      env,
      fetchImpl,
      forceHealth: true,
      cooldownMs: 30,
      maxTokens: 256,
      exhaustionBudgetMs: 0,
    })
      .then(() => null)
      .catch((error: unknown) => error as GsAllProvidersUnavailableError)
    expect(failed).toBeInstanceOf(GsAllProvidersUnavailableError)
    // Retried within the single request, then gave up for this call.
    expect(groqRequests).toBe(3)

    // Inside the cooldown the provider is skipped, not re-dialled.
    await runGsProviderChat([{ role: 'user', content: 'hi' }], { env, fetchImpl, maxTokens: 256, exhaustionBudgetMs: 0 }).catch(() => undefined)
    expect(groqRequests).toBe(3)

    // After the short cooldown it is retried — a throttle must not read as a
    // five-minute death.
    await new Promise((resolve) => setTimeout(resolve, 50))
    await runGsProviderChat([{ role: 'user', content: 'hi' }], { env, fetchImpl, maxTokens: 256, cooldownMs: 30, exhaustionBudgetMs: 0 }).catch(() => undefined)
    expect(groqRequests).toBeGreaterThan(3)
  })

  test('fails over when a live provider returns an empty completion for a real request', async () => {
    let groqRequests = 0
    const fetchImpl = async (input: string | URL | Request, init?: RequestInit): Promise<Response> => {
      const url = String(input)
      const body = JSON.parse(String(init?.body ?? '{}')) as { max_tokens?: number }
      const isProbe = body.max_tokens === 1
      if (url.includes('api.groq.com')) {
        if (!isProbe) groqRequests += 1
        return response({ model: 'qwen', choices: [{ finish_reason: 'length', message: { role: 'assistant' } }] })
      }
      if (url.includes('api.cerebras.ai')) {
        return response({ model: 'qwen-3.8-27b', choices: [{ message: { content: 'ok' } }] })
      }
      return response({ error: { message: 'unauthorized' } }, 401)
    }
    const result = await runGsProviderChat([{ role: 'user', content: 'hi' }], {
      env: {
        GROQ_API_KEY_1: 'groq-secret',
        CEREBRAS_API_KEY_1: 'cerebras-secret',
      } as unknown as NodeJS.ProcessEnv,
      fetchImpl,
      forceHealth: true,
      maxTokens: 256,
    })
    expect(groqRequests).toBe(1)
    expect(result.provider).toBe('cerebras')
  })
})
