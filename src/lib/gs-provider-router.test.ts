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
      CEREBRAS_API_KEY_1: 'permanentKey-cerebras',
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
    expect(serialized).not.toContain('permanentKey-cerebras')
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
      GROQ_API_KEY_1: 'k-empty',
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

  test('parks a throttled key and rotates to the next key in the pool', async () => {
    const used: string[] = []
    const fetchImpl = async (input: string | URL | Request, init?: RequestInit): Promise<Response> => {
      const url = String(input)
      if (!url.includes('api.groq.com')) return response({ error: { message: 'unauthorized' } }, 401)
      const body = JSON.parse(String(init?.body ?? '{}')) as { max_tokens?: number }
      const auth = String((init?.headers as Record<string, string>)?.Authorization ?? '')
      if (body.max_tokens === 1) return response({ model: 'qwen', choices: [{ message: { content: 'ok' } }] })
      used.push(auth)
      // The first key is throttled; the second must serve instead.
      if (auth.includes('keyA')) return response({ error: { message: 'Rate limit reached' } }, 429)
      return response({ model: 'qwen/qwen3.8-27b', choices: [{ message: { content: 'served by keyB' } }] })
    }
    const env = { GROQ_API_KEY_1: 'keyA', GROQ_API_KEY_2: 'keyB' } as unknown as NodeJS.ProcessEnv
    const result = await runGsProviderChat([{ role: 'user', content: 'hi' }], {
      env, fetchImpl, forceHealth: true, maxTokens: 256, exhaustionBudgetMs: 0,
    })
    // Round-robin means the pool is genuinely shared, not pinned to key one.
    expect(result.text).toBe('served by keyB')
    expect(used.some((h) => h.includes('keyB'))).toBe(true)
  })

  test('does not re-dial a parked key within the same request', async () => {
    let dials = 0
    const fetchImpl = async (input: string | URL | Request, init?: RequestInit): Promise<Response> => {
      const url = String(input)
      if (!url.includes('api.groq.com')) return response({ error: { message: 'unauthorized' } }, 401)
      const body = JSON.parse(String(init?.body ?? '{}')) as { max_tokens?: number }
      if (body.max_tokens === 1) return response({ model: 'qwen', choices: [{ message: { content: 'ok' } }] })
      dials += 1
      return response({ error: { message: 'Rate limit reached' } }, 429)
    }
    const env = { GROQ_API_KEY_1: 'solo-key' } as unknown as NodeJS.ProcessEnv
    await runGsProviderChat([{ role: 'user', content: 'hi' }], {
      env, fetchImpl, forceHealth: true, maxTokens: 256, exhaustionBudgetMs: 0,
    }).catch(() => undefined)
    // One dial: the key is then parked for its cooldown rather than retried.
    expect(dials).toBe(1)
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
        GROQ_API_KEY_1: 'k-empty',
        CEREBRAS_API_KEY_1: 'k-empty-cerebras',
      } as unknown as NodeJS.ProcessEnv,
      fetchImpl,
      forceHealth: true,
      maxTokens: 256,
    })
    // Retried (a pool hands out a different model next time), then failed over.
    expect(groqRequests).toBe(3)
    expect(result.provider).toBe('cerebras')
  })
})
