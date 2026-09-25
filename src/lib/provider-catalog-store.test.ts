import { afterEach, describe, expect, test } from 'bun:test'
import fs from 'node:fs'
import path from 'node:path'
import { configuredProviderKey, getProviderCatalogCache, syncConfiguredProviderCatalog } from './provider-catalog-store'

const cachePath = path.join(process.cwd(), 'data', '.provider-catalog.json')
afterEach(() => {
  try { fs.rmSync(cachePath, { force: true }) } catch { /* best effort */ }
})

describe('provider catalog runtime store', () => {
  test('selects one configured key without returning or logging it', () => {
    const key = configuredProviderKey('groq', {
      GROQ_API_KEYS: 'first-key,second-key',
    } as unknown as NodeJS.ProcessEnv)
    expect(key).toBe('first-key')
    expect(configuredProviderKey('groq', {} as unknown as NodeJS.ProcessEnv)).toBeUndefined()
  })

  test('keeps successful providers isolated and caches only metadata', async () => {
    const secret = 'provider-secret-must-not-escape'
    const summary = await syncConfiguredProviderCatalog({
      providers: ['openrouter', 'groq'],
      fetchImpl: async (input, init) => {
        const url = String(input)
        expect(init?.method).toBe('GET')
        if (url.includes('openrouter.ai')) {
          return new Response(JSON.stringify({ data: [{ id: 'vendor/free:free', pricing: { prompt: 0, completion: 0 } }] }), { status: 200 })
        }
        throw new Error(secret)
      },
    } as never)

    expect(summary.ok).toBe(false)
    expect(summary.successes.map((item) => item.provider)).toEqual(['openrouter'])
    expect(summary.failures.map((item) => item.provider)).toEqual(['groq'])
    expect(JSON.stringify(summary)).not.toContain(secret)
    expect(getProviderCatalogCache()?.providers.openrouter?.[0]?.modelId).toBe('vendor/free:free')
  })
})
