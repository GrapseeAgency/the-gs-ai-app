/**
 * Catalog-backed lightweight routing.
 *
 * This is deliberately a narrow fast path, not a replacement for the measured
 * capability router. A newly discovered model may serve simple text turns when
 * the provider explicitly marks it free, the model advertises text I/O, and a
 * provider key is available. Complex, vision, document, and reasoning turns
 * continue to use the measured registry until a model has application-level
 * validation evidence.
 */

import {
  configuredProviderKey,
  getProviderCatalogCache,
  type ProviderCatalogCache,
} from './provider-catalog-store'
import {
  GENERIC_TEXT_PROVIDER_IDS,
  type GenericTextProviderId,
} from './provider-execution'
import type { NormalizedModelMetadata } from './provider-catalog'

const AUTO_FAST_PATH_ENV = 'GS_CATALOG_AUTO_FAST_PATH'
const DEFAULT_MAX_CANDIDATES = 6
const DEFAULT_MAX_CATALOG_AGE_MS = 24 * 60 * 60 * 1_000
const APPROX_CHARS_PER_TOKEN = 3

export type CatalogFastRoute = {
  provider: GenericTextProviderId
  modelId: string
  contextWindowTokens: number | null
  source: 'provider-catalog'
  candidate: true
}

export type CatalogRouteRequest = {
  route: string
  hasImages: boolean
  hasDocuments: boolean
  estimatedInputChars?: number
  maxCandidates?: number
  maxCatalogAgeMs?: number
  /** Defaults to enabled unless explicitly set to 0. */
  autoFastPath?: boolean
  cache?: ProviderCatalogCache | null
  env?: NodeJS.ProcessEnv
}

const genericProviders = new Set<GenericTextProviderId>(GENERIC_TEXT_PROVIDER_IDS)

function autoFastPathEnabled(env: NodeJS.ProcessEnv = process.env): boolean {
  return env[AUTO_FAST_PATH_ENV] !== '0'
}

function isGenericProvider(value: string): value is GenericTextProviderId {
  return genericProviders.has(value as GenericTextProviderId)
}

function isTextModel(model: NormalizedModelMetadata): boolean {
  return model.inputModalities.includes('text') || model.outputModalities.includes('text')
}

function estimatedTokens(chars: number): number {
  return Math.max(1, Math.ceil(Math.max(0, chars) / APPROX_CHARS_PER_TOKEN))
}

function cacheIsFresh(
  cache: ProviderCatalogCache,
  request: Pick<CatalogRouteRequest, 'maxCatalogAgeMs'>,
  now = Date.now(),
): boolean {
  const maxAge = Math.max(0, request.maxCatalogAgeMs ?? DEFAULT_MAX_CATALOG_AGE_MS)
  const syncedAt = Date.parse(cache.syncedAt)
  return Number.isFinite(syncedAt) && syncedAt <= now && now - syncedAt <= maxAge
}

type EligibleModel = { provider: GenericTextProviderId; model: NormalizedModelMetadata }

function eligibleModels(request: CatalogRouteRequest): EligibleModel[] {
  const env = request.env ?? process.env
  const cache = request.cache === undefined ? getProviderCatalogCache() : request.cache
  if (!cache || !cacheIsFresh(cache, request)) return []

  const models = Object.entries(cache.providers).flatMap(([provider, providerModels]) =>
    (providerModels ?? []).map((model) => ({ provider, model })),
  )
  const minimumContext = estimatedTokens(request.estimatedInputChars ?? 0)
  const keyedProviders = new Set<GenericTextProviderId>()
  for (const provider of genericProviders) {
    // OpenRouter uses the existing durable key pool, which may be populated
    // from the vault even when no environment variable is present. Direct
    // providers must have an explicitly configured key before they are eligible.
    if (provider === 'openrouter' || configuredProviderKey(provider, env)) keyedProviders.add(provider)
  }

  const eligible: EligibleModel[] = []
  for (const entry of models) {
    const { provider, model } = entry
    if (!isGenericProvider(provider) || !keyedProviders.has(provider)) continue
    if (model.lifecycle === 'rejected' || model.status === 'rejected') continue
    if (model.freePolicy !== 'free' || !model.isFree) continue
    if (!isTextModel(model)) continue
    // The generic execution path is text-chat only. Do not auto-route audio,
    // image, or video output models into a normal text response.
    if (model.outputModalities.some((modality) => modality !== 'text')) continue
    if (model.contextWindowTokens !== null && model.contextWindowTokens < minimumContext) continue
    eligible.push({ provider, model })
  }
  return eligible.sort((a, b) => {
    // Keep the existing pinned OpenRouter chain first. New direct-provider
    // candidates are attempted next, then additional OpenRouter candidates.
    const aRank = a.provider === 'openrouter' ? 1 : 0
    const bRank = b.provider === 'openrouter' ? 1 : 0
    return aRank - bRank || a.provider.localeCompare(b.provider) || a.model.modelId.localeCompare(b.model.modelId)
  })
}

function toFastRoute(entry: { provider: GenericTextProviderId; model: NormalizedModelMetadata }): CatalogFastRoute {
  return {
    provider: entry.provider,
    modelId: entry.model.modelId,
    contextWindowTokens: entry.model.contextWindowTokens,
    source: 'provider-catalog',
    candidate: true,
  }
}

/** Select free catalog candidates for a simple, text-only turn. */
export function selectCatalogFastRoutes(request: CatalogRouteRequest): CatalogFastRoute[] {
  const env = request.env ?? process.env
  if (request.autoFastPath === false || !autoFastPathEnabled(env)) return []
  if (request.route !== 'TEXT_SIMPLE' || request.hasImages || request.hasDocuments) return []

  const maxCandidates = Math.min(
    DEFAULT_MAX_CANDIDATES,
    Math.max(0, Math.floor(request.maxCandidates ?? DEFAULT_MAX_CANDIDATES)),
  )
  if (maxCandidates === 0) return []

  const seen = new Set<string>()
  const result: CatalogFastRoute[] = []
  for (const entry of eligibleModels(request)) {
    const key = `${entry.provider}:${entry.model.modelId}`
    if (seen.has(key)) continue
    seen.add(key)
    result.push(toFastRoute(entry))
    if (result.length >= maxCandidates) break
  }
  return result
}

/** Whether at least one direct provider can currently supply a free text call. */
export function hasEligibleDirectFreeCatalogRoute(
  request: Partial<CatalogRouteRequest> = {},
): boolean {
  const env = request.env ?? process.env
  if (request.autoFastPath === false || !autoFastPathEnabled(env)) return false
  const cache = request.cache === undefined ? getProviderCatalogCache() : request.cache
  if (!cache || !cacheIsFresh(cache, request)) return false
  return Object.entries(cache.providers).some(([provider, models]) => {
    if (provider === 'openrouter' || !isGenericProvider(provider) || !configuredProviderKey(provider, env)) return false
    return (models ?? []).some(
      (model) =>
        model.freePolicy === 'free' &&
        model.isFree &&
        model.lifecycle !== 'rejected' &&
        model.status !== 'rejected' &&
        isTextModel(model) &&
        !model.outputModalities.some((modality) => modality !== 'text'),
    )
  })
}

export const CATALOG_ROUTER_ENV = {
  AUTO_FAST_PATH: AUTO_FAST_PATH_ENV,
} as const
