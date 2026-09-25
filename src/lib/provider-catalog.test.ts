import { describe, expect, test } from 'bun:test'
import {
  classifyFreePolicy,
  fetchProviderCatalog,
  markModelValidated,
  normalizeProviderModels,
  PROVIDER_CATALOG_ADAPTERS,
  syncProviderCatalog,
  type FetchLike,
} from './provider-catalog'

function jsonResponse(value: unknown, status = 200): Response {
  return new Response(JSON.stringify(value), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

describe('provider catalogue normalisation', () => {
  test('normalises OpenRouter metadata and leaves discovered models untrusted', () => {
    const models = normalizeProviderModels('openrouter', {
      data: [
        {
          id: 'acme/free-model:free',
          name: 'Free model',
          context_length: 131072,
          architecture: {
            input_modalities: ['text', 'image'],
            output_modalities: ['text'],
          },
          supported_parameters: ['tools', 'reasoning'],
          pricing: { prompt: '0', completion: '0' },
        },
        {
          id: 'acme/paid-model',
          context_length: 32768,
          pricing: { prompt: '0.000002', completion: '0.000004' },
        },
      ],
    })

    expect(models).toHaveLength(2)
    expect(models[0]).toMatchObject({
      providerId: 'openrouter',
      modelId: 'acme/free-model:free',
      contextWindowTokens: 131072,
      freePolicy: 'free',
      isFree: true,
      trust: 'untrusted',
      lifecycle: 'candidate',
      status: 'candidate',
      isTrusted: false,
      untrusted: true,
    })
    expect(models[0].validation).toEqual({
      status: 'pending',
      health: 'pending',
      capabilities: 'pending',
    })
    expect(models[0].inputModalities).toEqual(['text', 'image'])
    expect(models[0].outputModalities).toEqual(['text'])
    expect(models[0].capabilities).toEqual(expect.arrayContaining(['vision', 'tool-use', 'reasoning']))
    expect(models[1].freePolicy).toBe('paid')
    expect(models[1].isFree).toBe(false)

    const failedValidation = markModelValidated(models[0], { health: 'passed', capabilities: 'failed' })
    expect(failedValidation.trust).toBe('untrusted')
    expect(failedValidation.isCandidate).toBe(true)
    const validated = markModelValidated(models[0], { health: 'passed', capabilities: 'passed' })
    expect(validated).toMatchObject({ trust: 'trusted', lifecycle: 'validated', isTrusted: true, untrusted: false })
  })

  test('normalises Gemini, Hugging Face, and BFL metadata shapes', () => {
    const gemini = normalizeProviderModels('google-gemini', {
      models: [
        {
          name: 'models/gemini-flash',
          displayName: 'Gemini Flash',
          inputTokenLimit: 1048576,
          outputTokenLimit: 8192,
          supportedGenerationMethods: ['generateContent', 'countTokens'],
        },
      ],
    })
    expect(gemini[0]).toMatchObject({
      providerId: 'google-gemini',
      modelId: 'gemini-flash',
      contextWindowTokens: 1048576,
      maxOutputTokens: 8192,
      freePolicy: 'unknown',
      trust: 'untrusted',
    })
    expect(gemini[0].capabilities).toContain('text-generation')

    const huggingFace = normalizeProviderModels('hugging-face', [
      { id: 'org/text-model', pipeline_tag: 'text-generation' },
    ])
    expect(huggingFace[0]).toMatchObject({
      providerId: 'hugging-face',
      modelId: 'org/text-model',
      freePolicy: 'unknown',
    })
    expect(huggingFace[0].capabilities).toContain('text-generation')

    const bfl = normalizeProviderModels('bfl', {
      openapi: '3.1.0',
      paths: {
        '/v1/flux-2-pro': { post: { summary: 'Generate an image' } },
        '/v1/not-a-model': { post: { summary: 'Utility' } },
      },
    })
    expect(bfl.map((model) => model.modelId)).toEqual(['flux-2-pro'])
    expect(bfl[0]).toMatchObject({ providerId: 'bfl', freePolicy: 'unknown', trust: 'untrusted' })
  })

  test('all required provider adapters have bounded metadata endpoints', () => {
    expect(Object.keys(PROVIDER_CATALOG_ADAPTERS).sort()).toEqual([
      'bfl',
      'blablador',
      'cerebras',
      'dmxapi',
      'google-gemini',
      'groq',
      'hugging-face',
      'openrouter',
      'siliconflow',
    ])
    for (const adapter of Object.values(PROVIDER_CATALOG_ADAPTERS)) {
      expect(adapter.metadataUrl.startsWith('https://')).toBe(true)
      expect(adapter.metadataUrl).not.toMatch(/chat\/completions|responses|generate/i)
      expect(adapter.metadataUrl).not.toMatch(/[?&](?:key|token|secret|password|auth)=/i)
    }
  })
})

describe('conservative free-policy classification', () => {
  test('uses explicit policy and complete zero-price evidence', () => {
    expect(classifyFreePolicy({ pricing: { prompt: 0, completion: 0 } })).toBe('free')
    expect(classifyFreePolicy({ id: 'vendor/model:free' })).toBe('free')
    expect(classifyFreePolicy({ freeTier: 'free trial' })).toBe('trial')
    expect(classifyFreePolicy({ trial: true })).toBe('trial')
    expect(classifyFreePolicy({ access: 'credit-based' })).toBe('credit')
    expect(classifyFreePolicy({ credits: true })).toBe('credit')
    expect(classifyFreePolicy({ pricing: { prompt: '0.10', completion: '0.20' } })).toBe('paid')
  })

  test('does not promote ambiguous or partial pricing to free', () => {
    expect(classifyFreePolicy({})).toBe('unknown')
    expect(classifyFreePolicy({ pricing: { prompt: 0 } })).toBe('unknown')
    expect(classifyFreePolicy({ id: 'vendor/model-free' })).toBe('unknown')
    expect(classifyFreePolicy({ freeTier: 'mystery' })).toBe('unknown')
  })
})

describe('provider isolation and bounded transport', () => {
  test('keeps a successful provider when another provider fails or returns an error', async () => {
    const secret = 'sk-test-secret-that-must-not-escape'
    const seen: string[] = []
    const fetchImpl: FetchLike = async (input, init) => {
      const url = String(input)
      seen.push(url)
      expect(init?.method).toBe('GET')
      if (url.includes('openrouter.ai')) {
        return jsonResponse({ data: [{ id: 'openrouter/free-model:free', pricing: { prompt: '0', completion: '0' } }] })
      }
      if (url.includes('groq.com')) throw new Error(`upstream leaked ${secret}`)
      if (url.includes('googleapis.com')) return jsonResponse({ error: secret }, 503)
      throw new Error('unexpected provider')
    }

    const summary = await syncProviderCatalog({
      providers: ['openrouter', 'groq', 'google-gemini'],
      concurrency: 2,
      timeoutMs: 100,
      fetchImpl,
    })

    expect(summary.ok).toBe(false)
    expect(summary.successfulProviders).toBe(1)
    expect(summary.failedProviders).toBe(2)
    expect(summary.successes[0].provider).toBe('openrouter')
    expect(summary.successes[0].models[0]).toMatchObject({ freePolicy: 'free', untrusted: true })
    expect(summary.failures.map((failure) => failure.provider).sort()).toEqual(['google-gemini', 'groq'])
    expect(JSON.stringify(summary)).not.toContain(secret)
    expect(seen.every((url) => !url.includes('/chat/completions'))).toBe(true)
  })

  test('aborts a metadata request that exceeds its timeout', async () => {
    const fetchImpl: FetchLike = (_input, init) =>
      new Promise<Response>((_resolve, reject) => {
        init?.signal?.addEventListener('abort', () => reject(new Error('aborted')), { once: true })
      })

    await expect(fetchProviderCatalog('openrouter', { fetchImpl, timeoutMs: 5 })).rejects.toMatchObject({
      code: 'timeout',
    })
  })

  test('sends explicit credentials only as request headers and never returns them', async () => {
    const secret = 'explicit-test-key'
    let requestHeaders: HeadersInit | undefined
    const fetchImpl: FetchLike = async (_input, init) => {
      requestHeaders = init?.headers
      return jsonResponse({ data: [{ id: 'groq/llama' }] })
    }

    const snapshot = await fetchProviderCatalog('groq', { fetchImpl, apiKey: secret })
    expect((requestHeaders as Record<string, string>).authorization).toBe(`Bearer ${secret}`)
    expect(JSON.stringify(snapshot)).not.toContain(secret)
  })
})
