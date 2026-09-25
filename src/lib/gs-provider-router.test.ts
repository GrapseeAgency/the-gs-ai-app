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

    const first = await ensureGsProviderHealth({ env, fetchImpl, forceHealth: true })
    expect(first.find((record) => record.provider === 'google')?.healthy).toBe(false)
    expect(first.find((record) => record.provider === 'groq')?.healthy).toBe(true)
    const afterFirstHealth = calls.length
    await ensureGsProviderHealth({ env, fetchImpl })
    expect(calls.length).toBe(afterFirstHealth)

    const result = await runGsProviderChat([{ role: 'user', content: 'hi' }], {
      env,
      fetchImpl,
      role: 'generator',
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
})
