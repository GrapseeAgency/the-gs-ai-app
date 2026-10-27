/**
 * Runtime provider catalogue cache.
 *
 * The catalog module is deliberately transport-only and has no environment or
 * secret access. This server-side wrapper supplies explicitly named credentials,
 * isolates provider failures, retains the last known-good metadata snapshot,
 * and schedules metadata-only refreshes outside the request path.
 */

import fs from 'node:fs'
import path from 'node:path'
import {
  fetchProviderCatalog,
  PROVIDER_IDS,
  type NormalizedModelMetadata,
  type ProviderCatalogError,
  type ProviderCatalogFetchOptions,
  type ProviderCatalogSnapshot,
  type ProviderId,
} from './provider-catalog'

const CACHE_VERSION = 1
const CACHE_PATH = path.join(process.cwd(), 'data', '.provider-catalog.json')
const DEFAULT_REFRESH_MS = 6 * 60 * 60 * 1_000
const DEFAULT_TIMEOUT_MS = 8_000

const ENV_NAMES: Readonly<Record<ProviderId, readonly string[]>> = Object.freeze({
  openrouter: ['OPENROUTER_API_KEYS', 'OPENROUTER_API_KEY'],
  groq: ['GROQ_API_KEYS', 'GROQ_API_KEY'],
  'google-gemini': ['GOOGLE_API_KEYS', 'GOOGLE_API_KEY', 'GEMINI_API_KEYS', 'GEMINI_API_KEY'],
  cerebras: ['CEREBRAS_API_KEYS', 'CEREBRAS_API_KEY'],
  blablador: ['HELMHOLTZ_API_KEYS', 'HELMHOLTZ_API_KEY', 'BLABLADOR_API_KEYS', 'BLABLADOR_API_KEY'],
  'hugging-face': ['HF_API_KEYS', 'HF_API_KEY', 'HUGGINGFACE_API_KEYS', 'HUGGINGFACE_API_KEY'],
  siliconflow: ['SILICONFLOW_API_KEYS', 'SILICONFLOW_API_KEY'],
  dmxapi: ['DMX_API_KEYS', 'DMX_API_KEY', 'DMXAPI_API_KEYS', 'DMXAPI_API_KEY'],
  bfl: ['BFL_API_KEYS', 'BFL_API_KEY', 'BLACK_FOREST_LABS_API_KEYS', 'BLACK_FOREST_LABS_API_KEY'],
})

export interface ProviderCatalogFailure {
  provider: ProviderId
  code: string
  message: string
  status: number | null
}

export interface ProviderCatalogCache {
  version: typeof CACHE_VERSION
  syncedAt: string
  providers: Partial<Record<ProviderId, NormalizedModelMetadata[]>>
}

export interface ConfiguredCatalogSyncResult {
  ok: boolean
  startedAt: string
  finishedAt: string
  durationMs: number
  providers: ProviderId[]
  successes: ProviderCatalogSnapshot[]
  failures: ProviderCatalogFailure[]
  models: NormalizedModelMetadata[]
  modelCount: number
  freeModelCount: number
  candidateModelCount: number
}

type RuntimeState = {
  cache: ProviderCatalogCache | null
  syncPromise: Promise<ConfiguredCatalogSyncResult> | null
  timer: ReturnType<typeof setInterval> | null
  started: boolean
}

const globalState = globalThis as typeof globalThis & { __gsProviderCatalogState?: RuntimeState }
const state: RuntimeState = globalState.__gsProviderCatalogState ?? {
  cache: null,
  syncPromise: null,
  timer: null,
  started: false,
}
globalState.__gsProviderCatalogState = state

function firstConfiguredKey(names: readonly string[], env: NodeJS.ProcessEnv = process.env): string | undefined {
  for (const name of names) {
    const raw = env[name]
    if (typeof raw !== 'string') continue
    const key = raw.split(/[\n,]/).map((part) => part.trim()).find(Boolean)
    if (key) return key
  }
  return undefined
}

/** Resolve a key for a provider without exposing or logging its value. */
export function configuredProviderKey(provider: ProviderId, env: NodeJS.ProcessEnv = process.env): string | undefined {
  return firstConfiguredKey(ENV_NAMES[provider], env)
}

function isProviderId(value: unknown): value is ProviderId {
  return typeof value === 'string' && (PROVIDER_IDS as readonly string[]).includes(value)
}

function loadCache(): ProviderCatalogCache | null {
  try {
    const raw = fs.readFileSync(CACHE_PATH, 'utf8')
    const parsed: unknown = JSON.parse(raw)
    if (typeof parsed !== 'object' || parsed === null) return null
    const record = parsed as Record<string, unknown>
    if (record.version !== CACHE_VERSION || typeof record.syncedAt !== 'string' || typeof record.providers !== 'object' || record.providers === null) {
      return null
    }
    const providers: Partial<Record<ProviderId, NormalizedModelMetadata[]>> = {}
    for (const [provider, models] of Object.entries(record.providers as Record<string, unknown>)) {
      if (!isProviderId(provider) || !Array.isArray(models)) continue
      providers[provider] = models.filter((model): model is NormalizedModelMetadata =>
        typeof model === 'object' && model !== null &&
        (model as NormalizedModelMetadata).providerId === provider &&
        typeof (model as NormalizedModelMetadata).modelId === 'string'
      )
    }
    return { version: CACHE_VERSION, syncedAt: record.syncedAt, providers }
  } catch {
    return null
  }
}

function persistCache(cache: ProviderCatalogCache): void {
  const dir = path.dirname(CACHE_PATH)
  fs.mkdirSync(dir, { recursive: true })
  const temporary = `${CACHE_PATH}.${process.pid}.tmp`
  fs.writeFileSync(temporary, `${JSON.stringify(cache)}\n`, { mode: 0o600 })
  fs.renameSync(temporary, CACHE_PATH)
}

function failureFrom(error: unknown, provider: ProviderId): ProviderCatalogFailure {
  const record = error as Partial<ProviderCatalogError> & { code?: unknown; message?: unknown; status?: unknown }
  return {
    provider,
    code: typeof record?.code === 'string' ? record.code : 'network-error',
    message: `${provider}: metadata sync failed`,
    status: typeof record?.status === 'number' ? record.status : null,
  }
}

async function fetchOne(
  provider: ProviderId,
  options: ProviderCatalogFetchOptions,
): Promise<ProviderCatalogSnapshot> {
  const apiKey = configuredProviderKey(provider)
  return fetchProviderCatalog(provider, {
    ...options,
    apiKey: options.apiKey ?? apiKey,
  })
}

/** Refresh every configured provider in parallel, retaining last-known-good data on failure. */
export async function syncConfiguredProviderCatalog(
  options: ProviderCatalogFetchOptions & { providers?: readonly ProviderId[] } = {},
): Promise<ConfiguredCatalogSyncResult> {
  if (state.syncPromise) return state.syncPromise
  const providers = [...new Set(options.providers ?? PROVIDER_IDS)]
  const started = Date.now()
  const run = (async (): Promise<ConfiguredCatalogSyncResult> => {
    const settled = await Promise.all(
      providers.map(async (provider) => {
        try {
          return { provider, snapshot: await fetchOne(provider, options), error: null }
        } catch (error) {
          return { provider, snapshot: null, error: failureFrom(error, provider) }
        }
      }),
    )
    const successes = settled.flatMap((item) => (item.snapshot ? [item.snapshot] : []))
    const failures = settled.flatMap((item) => (item.error ? [item.error] : []))
    const models = successes.flatMap((snapshot) => snapshot.models)
    const providersData: Partial<Record<ProviderId, NormalizedModelMetadata[]>> = {}
    for (const snapshot of successes) providersData[snapshot.provider] = snapshot.models
    if (successes.length > 0) {
      const nextCache: ProviderCatalogCache = {
        version: CACHE_VERSION,
        syncedAt: new Date().toISOString(),
        providers: { ...(state.cache?.providers ?? {}), ...providersData },
      }
      try {
        persistCache(nextCache)
        state.cache = nextCache
      } catch {
        // A read-only runtime filesystem must not break model discovery.
      }
    }
    const finished = Date.now()
    const result: ConfiguredCatalogSyncResult = {
      ok: failures.length === 0,
      startedAt: new Date(started).toISOString(),
      finishedAt: new Date(finished).toISOString(),
      durationMs: finished - started,
      providers,
      successes,
      failures,
      models,
      modelCount: models.length,
      freeModelCount: models.filter((model) => model.freePolicy === 'free').length,
      candidateModelCount: models.filter((model) => model.isCandidate).length,
    }
    console.log(
      `PROVIDER-CATALOG-SYNC ok=${result.ok} providers=${providers.length} succeeded=${successes.length} failed=${failures.length} models=${result.modelCount} free=${result.freeModelCount} candidates=${result.candidateModelCount}`
    )
    return result
  })()
  state.syncPromise = run
  try {
    return await run
  } finally {
    state.syncPromise = null
  }
}

export function getProviderCatalogCache(): ProviderCatalogCache | null {
  if (state.cache === null) state.cache = loadCache()
  return state.cache
}

/** Start one non-blocking refresh loop per server process. */
export function ensureProviderCatalogSync(
  options: ProviderCatalogFetchOptions & { providers?: readonly ProviderId[]; refreshMs?: number } = {},
): void {
  if (state.started) return
  state.started = true
  void syncConfiguredProviderCatalog(options)
  const refreshMs = options.refreshMs ?? DEFAULT_REFRESH_MS
  state.timer = setInterval(() => {
    void syncConfiguredProviderCatalog(options)
  }, refreshMs)
  state.timer.unref?.()
}
