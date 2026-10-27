import { describe, expect, test } from 'bun:test'
import {
  hasEligibleDirectFreeCatalogRoute,
  selectCatalogFastRoutes,
} from './catalog-router'
import type { NormalizedModelMetadata } from './provider-catalog'
import type { ProviderCatalogCache } from './provider-catalog-store'

function model(
  provider: NormalizedModelMetadata['providerId'],
  modelId: string,
  overrides: Partial<NormalizedModelMetadata> = {},
): NormalizedModelMetadata {
  return {
    providerId: provider,
    provider,
    id: modelId,
    modelId,
    displayName: modelId,
    owner: null,
    contextWindowTokens: 32_000,
    maxOutputTokens: null,
    inputModalities: ['text'],
    outputModalities: ['text'],
    capabilities: [],
    pricing: { input: 0, output: 0, request: null, currency: 'USD', unit: 'token' },
    createdAt: null,
    freePolicy: 'free',
    freePolicyReason: 'test',
    isFree: true,
    isFreePolicyKnown: true,
    trust: 'untrusted',
    lifecycle: 'candidate',
    status: 'candidate',
    isCandidate: true,
    isTrusted: false,
    untrusted: true,
    validation: { status: 'pending', health: 'passed', capabilities: 'pending' },
    source: 'provider-metadata',
    sourceUrl: 'https://example.test/models',
    ...overrides,
  }
}

function cache(providers: Partial<Record<string, NormalizedModelMetadata[]>>): ProviderCatalogCache {
  return {
    version: 1,
    syncedAt: new Date().toISOString(),
    providers: providers as ProviderCatalogCache['providers'],
  }
}

describe('catalog fast router', () => {
  test('selects free text candidates for simple turns only', () => {
    const result = selectCatalogFastRoutes({
      route: 'TEXT_SIMPLE',
      hasImages: false,
      hasDocuments: false,
      estimatedInputChars: 30,
      cache: cache({
        groq: [model('groq', 'groq-free')],
        openrouter: [model('openrouter', 'vendor/free:free')],
        cerebras: [model('cerebras', 'paid', { freePolicy: 'paid', isFree: false })],
        'hugging-face': [model('hugging-face', 'image', {
          inputModalities: ['image'],
          outputModalities: ['image'],
        })],
      }),
      env: { GROQ_API_KEY: 'groq-key' } as unknown as NodeJS.ProcessEnv,
    })

    expect(result.map((route) => route.provider)).toEqual(['groq', 'openrouter'])
    expect(result.every((route) => route.candidate)).toBe(true)
    expect(selectCatalogFastRoutes({
      route: 'VISION',
      hasImages: true,
      hasDocuments: false,
      cache: cache({ groq: [model('groq', 'groq-free')] }),
      env: { GROQ_API_KEY: 'groq-key' } as unknown as NodeJS.ProcessEnv,
    })).toEqual([])
  })

  test('requires direct provider keys and respects the auto fast-path switch', () => {
    const cached = cache({ groq: [model('groq', 'groq-free')] })
    const env = {} as NodeJS.ProcessEnv
    expect(selectCatalogFastRoutes({ route: 'TEXT_SIMPLE', hasImages: false, hasDocuments: false, cache: cached, env })).toEqual([])
    expect(hasEligibleDirectFreeCatalogRoute({ cache: cached, env })).toBe(false)
    expect(hasEligibleDirectFreeCatalogRoute({ cache: cached, env: { GROQ_API_KEY: 'k' } as unknown as NodeJS.ProcessEnv })).toBe(true)
    expect(selectCatalogFastRoutes({
      route: 'TEXT_SIMPLE',
      hasImages: false,
      hasDocuments: false,
      cache: cached,
      env: { GROQ_API_KEY: 'k', GS_CATALOG_AUTO_FAST_PATH: '0' } as unknown as NodeJS.ProcessEnv,
    })).toEqual([])
  })

  test('does not auto-route stale free metadata', () => {
    const stale = cache({ openrouter: [model('openrouter', 'old/free:free')] })
    stale.syncedAt = new Date(Date.now() - 48 * 60 * 60 * 1_000).toISOString()
    expect(selectCatalogFastRoutes({
      route: 'TEXT_SIMPLE',
      hasImages: false,
      hasDocuments: false,
      cache: stale,
      env: {} as NodeJS.ProcessEnv,
    })).toEqual([])
  })
})
