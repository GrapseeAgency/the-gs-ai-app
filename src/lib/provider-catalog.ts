/**
 * Provider model catalogue discovery.
 *
 * Metadata-only by design: this module never reads environment variables,
 * persists credentials, logs request data, or calls a completion/generation
 * endpoint. Callers may provide an API key for a GET request explicitly; it
 * is used only to build a request header and is never returned or logged.
 *
 * Remote catalogue entries are untrusted candidates. A separate health and
 * capability validation step must pass before an entry can be promoted.
 */

export const PROVIDER_IDS = [
  'openrouter',
  'groq',
  'google-gemini',
  'cerebras',
  'blablador',
  'hugging-face',
  'siliconflow',
  'dmxapi',
  'bfl',
] as const

export type ProviderId = (typeof PROVIDER_IDS)[number]
export type ProviderInput = ProviderId | string

export const FREE_POLICIES = ['free', 'trial', 'credit', 'unknown', 'paid'] as const
export type FreePolicy = (typeof FREE_POLICIES)[number]
export type ModelTrust = 'untrusted' | 'trusted'
export type ModelLifecycle = 'candidate' | 'validated' | 'rejected'
export type ValidationState = 'pending' | 'passed' | 'failed'

export interface ModelValidation {
  status: 'pending' | 'passed' | 'failed'
  health: ValidationState
  capabilities: ValidationState
}

export interface NormalizedPricing {
  /** Provider-reported input price; the unit is provider-defined. */
  input: number | null
  /** Provider-reported output price; the unit is provider-defined. */
  output: number | null
  /** Provider-reported per-request price, when present. */
  request: number | null
  currency: string | null
  unit: 'token' | 'request' | 'unknown'
}

export interface NormalizedModelMetadata {
  providerId: ProviderId
  provider: ProviderId
  id: string
  modelId: string
  displayName: string | null
  owner: string | null
  contextWindowTokens: number | null
  maxOutputTokens: number | null
  inputModalities: string[]
  outputModalities: string[]
  capabilities: string[]
  pricing: NormalizedPricing
  createdAt: number | null
  freePolicy: FreePolicy
  freePolicyReason: string
  isFree: boolean
  isFreePolicyKnown: boolean
  trust: ModelTrust
  lifecycle: ModelLifecycle
  status: 'candidate' | 'validated' | 'rejected'
  isCandidate: boolean
  isTrusted: boolean
  untrusted: boolean
  validation: ModelValidation
  source: 'provider-metadata'
  sourceUrl: string
}

export type ProviderAuthScheme = 'none' | 'bearer' | 'x-goog-api-key' | 'x-key'
export type ProviderMetadataKind = 'models' | 'openapi'

export interface ProviderAdapter {
  readonly id: ProviderId
  readonly provider: ProviderId
  readonly name: string
  readonly metadataUrl: string
  readonly catalogUrl: string
  readonly metadataKind: ProviderMetadataKind
  readonly auth: ProviderAuthScheme
  readonly requiresAuth: boolean
  readonly normalize: (payload: unknown) => NormalizedModelMetadata[]
}

/** Provider-level metadata without a transport/normalizer implementation. */
export type NormalizedProviderMetadata = Omit<ProviderAdapter, 'normalize'>
export type ProviderModelMetadata = NormalizedModelMetadata
export type ProviderModel = NormalizedModelMetadata
export type FreeTierPolicy = FreePolicy

export type FetchLike = (
  input: string | URL | Request,
  init?: RequestInit,
) => Promise<Response>

export interface ProviderCatalogFetchOptions {
  /** Injected transport, primarily for deterministic tests. */
  fetchImpl?: FetchLike
  /** Alias for `fetchImpl`; the explicit `fetchImpl` wins when both exist. */
  fetch?: FetchLike
  /** Extra headers supplied by the caller. No environment lookup occurs. */
  headers?: HeadersInit
  /** Optional explicit credential. It is never returned or logged. */
  apiKey?: string
  timeoutMs?: number
  signal?: AbortSignal
  maxResponseBytes?: number
}

export type ProviderCatalogErrorCode =
  | 'invalid-options'
  | 'unknown-provider'
  | 'timeout'
  | 'aborted'
  | 'network-error'
  | 'http-error'
  | 'response-too-large'
  | 'invalid-json'
  | 'invalid-payload'

export class ProviderCatalogError extends Error {
  readonly code: ProviderCatalogErrorCode
  readonly provider: ProviderId | null
  readonly status: number | null

  constructor(
    code: ProviderCatalogErrorCode,
    provider: ProviderId | null = null,
    status: number | null = null,
  ) {
    // Never interpolate a response, URL, header, or thrown error.
    const suffix = status === null ? '' : ` (${status})`
    super(`${provider ?? 'provider'}: ${code}${suffix}`)
    this.name = 'ProviderCatalogError'
    this.code = code
    this.provider = provider
    this.status = status
  }
}

export interface ProviderCatalogFailure {
  code: ProviderCatalogErrorCode
  message: string
  status: number | null
}

export interface ProviderCatalogSnapshot {
  provider: ProviderId
  models: NormalizedModelMetadata[]
  modelCount: number
  fetchedAt: string
  durationMs: number
  sourceUrl: string
  truncated: boolean
}

export interface ProviderSyncSuccess {
  ok: true
  provider: ProviderId
  models: NormalizedModelMetadata[]
  modelCount: number
  fetchedAt: string
  durationMs: number
  sourceUrl: string
  truncated: boolean
}

export interface ProviderSyncFailure {
  ok: false
  provider: ProviderId
  models: []
  modelCount: 0
  fetchedAt: string
  durationMs: number
  error: ProviderCatalogFailure
}

export type ProviderSyncResult = ProviderSyncSuccess | ProviderSyncFailure

export interface ProviderCatalogSyncOptions extends ProviderCatalogFetchOptions {
  /** Defaults to every built-in provider. */
  providers?: readonly (ProviderId | string)[]
  /** Maximum number of provider requests in flight at once. */
  concurrency?: number
}

export interface ProviderCatalogSyncSummary {
  ok: boolean
  startedAt: string
  finishedAt: string
  durationMs: number
  requestedProviders: ProviderId[]
  providers: ProviderSyncResult[]
  results: ProviderSyncResult[]
  successes: ProviderSyncSuccess[]
  failures: ProviderSyncFailure[]
  successfulProviders: number
  failedProviders: number
  totalModels: number
  candidateModels: number
  freeModels: number
  freePolicyCounts: Record<FreePolicy, number>
  modelsByProvider: Partial<Record<ProviderId, number>>
  models: NormalizedModelMetadata[]
}

export interface ValidationEvidence {
  health: 'passed' | 'failed'
  capabilities: 'passed' | 'failed'
}

const DEFAULT_TIMEOUT_MS = 8_000
const MAX_TIMEOUT_MS = 30_000
const DEFAULT_MAX_RESPONSE_BYTES = 5 * 1024 * 1024
const MAX_RESPONSE_BYTES = 20 * 1024 * 1024
const DEFAULT_CONCURRENCY = 3
const MAX_CONCURRENCY = PROVIDER_IDS.length
const MAX_MODELS_PER_PROVIDER = 5_000
const MAX_MODEL_ID_LENGTH = 512
const MAX_DISPLAY_NAME_LENGTH = 256

/**
 * Fixed GET-only metadata endpoints. No completion or generation path is in
 * this allowlist. BFL has no general model-list endpoint, so its adapter reads
 * the public OpenAPI document and extracts model paths without calling them.
 */
export const PROVIDER_METADATA_ENDPOINTS: Readonly<Record<ProviderId, string>> = Object.freeze({
  openrouter: 'https://openrouter.ai/api/v1/models',
  groq: 'https://api.groq.com/openai/v1/models',
  'google-gemini': 'https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000',
  cerebras: 'https://api.cerebras.ai/public/v1/models',
  blablador: 'https://api.blablador.fz-juelich.de/v1/models',
  'hugging-face': 'https://huggingface.co/api/models?limit=100&full=false',
  siliconflow: 'https://api.siliconflow.com/v1/models',
  dmxapi: 'https://api.dmxapi.com/v1/models',
  bfl: 'https://api.bfl.ai/openapi.json',
})

const PROVIDER_ALIASES: Readonly<Record<string, ProviderId>> = Object.freeze({
  openrouter: 'openrouter',
  'open-router': 'openrouter',
  groq: 'groq',
  google: 'google-gemini',
  gemini: 'google-gemini',
  googleai: 'google-gemini',
  'google-gemini': 'google-gemini',
  cerebras: 'cerebras',
  blablador: 'blablador',
  huggingface: 'hugging-face',
  'hugging-face': 'hugging-face',
  hf: 'hugging-face',
  siliconflow: 'siliconflow',
  'silicon-flow': 'siliconflow',
  'silicon-flow-cn': 'siliconflow',
  dmxapi: 'dmxapi',
  'dmx-api': 'dmxapi',
  dmx: 'dmxapi',
  bfl: 'bfl',
  'black-forest-labs': 'bfl',
  blackforestlabs: 'bfl',
})

const AUTH_HEADER_BY_SCHEME: Readonly<Record<Exclude<ProviderAuthScheme, 'none'>, string>> =
  Object.freeze({
    bearer: 'authorization',
    'x-goog-api-key': 'x-goog-api-key',
    'x-key': 'x-key',
  })

// ---------------------------------------------------------------------------
// Safe value helpers
// ---------------------------------------------------------------------------

type JsonRecord = Record<string, unknown>

function isRecord(value: unknown): value is JsonRecord {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function firstValue(record: JsonRecord, keys: readonly string[]): unknown {
  for (const key of keys) {
    if (Object.prototype.hasOwnProperty.call(record, key) && record[key] !== undefined) return record[key]
  }
  return undefined
}

function firstRecord(record: JsonRecord, keys: readonly string[]): JsonRecord | null {
  for (const key of keys) {
    if (isRecord(record[key])) return record[key]
  }
  return null
}

function firstString(record: JsonRecord, keys: readonly string[]): string | null {
  const value = firstValue(record, keys)
  return typeof value === 'string' && value.trim().length > 0 ? value.trim() : null
}

function finiteNumber(value: unknown): number | null {
  if (typeof value === 'number') return Number.isFinite(value) ? value : null
  if (typeof value !== 'string') return null
  const trimmed = value.trim()
  if (trimmed.length === 0) return null
  const parsed = Number(trimmed)
  return Number.isFinite(parsed) ? parsed : null
}

function nonNegativeNumber(value: unknown): number | null {
  const parsed = finiteNumber(value)
  return parsed !== null && parsed >= 0 ? parsed : null
}

function positiveInteger(value: unknown): number | null {
  const parsed = finiteNumber(value)
  return parsed === null || parsed <= 0 ? null : Math.floor(parsed)
}

function cleanText(value: unknown, maxLength: number): string | null {
  if (typeof value !== 'string') return null
  const trimmed = value.trim()
  if (trimmed.length === 0 || trimmed.length > maxLength) return null
  if (/[\u0000-\u001f\u007f]/.test(trimmed)) return null
  return trimmed
}

function stringList(value: unknown): string[] {
  const values: string[] = []
  if (Array.isArray(value)) {
    for (const item of value) {
      if (typeof item === 'string' && item.trim().length > 0) values.push(item.trim())
    }
  } else if (typeof value === 'string' && value.trim().length > 0) {
    values.push(value.trim())
  }
  return [...new Set(values)]
}

function unique(values: readonly string[]): string[] {
  return [...new Set(values.filter((value) => value.length > 0))]
}

function canonicalTag(value: string): string {
  return value
    .trim()
    .toLowerCase()
    .replace(/([a-z0-9])([A-Z])/g, '$1-$2')
    .replace(/[\s_]+/g, '-')
    .replace(/[^a-z0-9.+/-]/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '')
}

function looksLikeModelId(value: string): boolean {
  return value.length > 0 && value.length <= MAX_MODEL_ID_LENGTH && !/[\s\u0000-\u001f\u007f]/.test(value)
}

function modelIdFromRecord(record: JsonRecord, provider: ProviderId): string | null {
  const candidate = firstValue(record, [
    'id',
    'modelId',
    'model_id',
    'model',
    'slug',
    'canonical_model_id',
    'base_model',
  ])
  let id = typeof candidate === 'string' ? candidate.trim() : ''
  if (id.length === 0 && provider === 'google-gemini') {
    const name = firstString(record, ['name'])
    if (name) id = name
  }
  if (id.length === 0 && provider === 'bfl') {
    const name = firstString(record, ['name', 'displayName', 'display_name'])
    if (name && looksLikeModelId(name)) id = name
  }
  if (provider === 'google-gemini' && id.startsWith('models/')) id = id.slice('models/'.length)
  return looksLikeModelId(id) ? id : null
}

function nestedValue(record: JsonRecord, path: readonly string[]): unknown {
  let current: unknown = record
  for (const key of path) {
    if (!isRecord(current)) return undefined
    current = current[key]
  }
  return current
}

// ---------------------------------------------------------------------------
// Conservative free-policy classification
// ---------------------------------------------------------------------------

function policyWord(value: unknown): FreePolicy | null {
  if (typeof value !== 'string') return null
  const normalized = value.trim().toLowerCase().replace(/[\s_]+/g, '-')
  if (normalized.length === 0) return null
  if (normalized.includes('not-free') || normalized.includes('non-free') || normalized.includes('requires-payment')) return 'paid'
  if (normalized.includes('trial')) return 'trial'
  if (normalized.includes('credit')) return 'credit'
  if (/(^|-)free($|-)/.test(normalized) || normalized === 'no-cost' || normalized === 'zero-cost') return 'free'
  if (normalized.includes('paid') || normalized.includes('metered') || normalized.includes('subscription')) return 'paid'
  return null
}

function booleanFlag(record: JsonRecord, keys: readonly string[]): boolean | null {
  for (const key of keys) {
    if (typeof record[key] === 'boolean') return record[key] as boolean
  }
  return null
}

function pricingState(record: JsonRecord): {
  hasPositive: boolean
  input: number | null
  output: number | null
  hasInput: boolean
  hasOutput: boolean
} {
  const pricing = firstRecord(record, ['pricing', 'price', 'cost_pricing', 'costs']) ?? record
  const inputRaw = firstValue(pricing, [
    'input', 'prompt', 'input_price', 'inputPrice', 'prompt_price', 'input_per_million', 'inputPerMillion',
  ])
  const outputRaw = firstValue(pricing, [
    'output', 'completion', 'output_price', 'outputPrice', 'completion_price', 'output_per_million', 'outputPerMillion',
  ])
  const requestRaw = firstValue(pricing, ['request', 'request_price', 'requestPrice', 'per_request'])
  const input = nonNegativeNumber(inputRaw)
  const output = nonNegativeNumber(outputRaw)
  const request = nonNegativeNumber(requestRaw)
  const all = [input, output, request].filter((value): value is number => value !== null)
  return {
    hasPositive: all.some((value) => value > 0),
    input,
    output,
    hasInput: inputRaw !== undefined && input !== null,
    hasOutput: outputRaw !== undefined && output !== null,
  }
}

/** Classify only from explicit evidence; missing/ambiguous means `unknown`. */
export function classifyFreePolicy(model: unknown, modelId?: string): FreePolicy {
  const record = isRecord(model) ? model : {}
  const explicitValues: unknown[] = [
    record.freePolicy,
    record.free_policy,
    record.freeTier,
    record.free_tier,
    record.access,
    record.accessLevel,
    record.access_level,
    record.billing,
    record.billingPolicy,
    record.billing_policy,
    record.plan,
    record.tier,
    record.availability,
    nestedValue(record, ['pricing', 'policy']),
    nestedValue(record, ['pricing', 'tier']),
    nestedValue(record, ['access', 'policy']),
  ]

  let explicit: FreePolicy | null = null
  for (const value of explicitValues) {
    const candidate = policyWord(value)
    if (candidate !== null) {
      explicit = candidate
      break
    }
  }

  if (booleanFlag(record, ['isTrial', 'is_trial', 'trial', 'freeTrial', 'free_trial', 'trialAvailable', 'trial_available']) === true) explicit = 'trial'
  if (booleanFlag(record, ['credit', 'credits', 'creditBased', 'credit_based', 'usesCredits', 'uses_credits', 'requiresCredits', 'requires_credits']) === true) explicit = 'credit'
  if (booleanFlag(record, ['isFree', 'is_free', 'free', 'freeTierAvailable', 'free_tier_available']) === true) explicit = 'free'
  if (booleanFlag(record, ['isPaid', 'is_paid', 'paid']) === true) explicit = 'paid'

  const prices = pricingState(record)
  if (explicit === 'paid' || (explicit === 'free' && prices.hasPositive)) return 'paid'
  if (explicit === 'trial' || explicit === 'credit') return explicit

  const id = typeof modelId === 'string' ? modelId.trim() : modelIdFromRecord(record, 'openrouter')
  if (typeof id === 'string' && /:free$/.test(id) && !prices.hasPositive) return 'free'
  if (explicit === 'free') return 'free'
  if (prices.hasInput && prices.hasOutput && prices.input === 0 && prices.output === 0 && !prices.hasPositive) return 'free'
  if (prices.hasPositive) return 'paid'
  return 'unknown'
}

export function explainFreePolicy(
  model: unknown,
  modelId?: string,
): { policy: FreePolicy; reason: string } {
  const policy = classifyFreePolicy(model, modelId)
  const reasons: Record<FreePolicy, string> = {
    free: 'provider explicitly reports a free policy or a complete zero input/output price',
    trial: 'provider explicitly reports a trial policy',
    credit: 'provider explicitly reports a credit-based policy',
    paid: 'provider reports a positive price or an explicit paid policy',
    unknown: 'provider metadata does not establish a free policy',
  }
  return { policy, reason: reasons[policy] }
}

export const classifyFreeTier = classifyFreePolicy

// ---------------------------------------------------------------------------
// Provider payload normalisation
// ---------------------------------------------------------------------------

function bflModelsFromOpenApi(payload: JsonRecord): JsonRecord[] {
  const paths = firstRecord(payload, ['paths'])
  if (paths === null) return []
  const result: JsonRecord[] = []
  for (const [path, pathValue] of Object.entries(paths)) {
    const match = /^\/v1\/([a-zA-Z0-9][a-zA-Z0-9._-]*)$/.exec(path)
    if (!match || !isRecord(pathValue)) continue
    const id = match[1]
    if (!/^(?:flux(?:-|$)|flt(?:-|$)|qwen(?:-|$)|sdxl(?:-|$)|stable-diffusion(?:-|$)|kolors(?:-|$))/i.test(id)) continue
    const summary = firstString(pathValue, ['summary', 'description', 'title'])
    result.push({
      id,
      name: summary ?? id,
      modality: /video/i.test(id) ? 'text->video' : 'text->image',
      supported_parameters: Object.keys(pathValue).filter((key) =>
        ['get', 'post', 'put', 'patch', 'delete', 'head', 'options'].includes(key),
      ),
    })
  }
  return result
}

function extractRawModels(payload: unknown, provider: ProviderId, depth = 0): unknown[] {
  if (depth > 4) return []
  if (Array.isArray(payload)) return payload
  if (!isRecord(payload)) return []
  if (provider === 'bfl') {
    const openApiModels = bflModelsFromOpenApi(payload)
    if (openApiModels.length > 0) return openApiModels
  }
  for (const key of ['data', 'models', 'items', 'results']) {
    if (Array.isArray(payload[key])) return payload[key]
    if (isRecord(payload[key])) {
      const nested = extractRawModels(payload[key], provider, depth + 1)
      if (nested.length > 0) return nested
    }
  }
  if (isRecord(payload.data) && modelIdFromRecord(payload.data, provider) !== null) return [payload.data]
  if (modelIdFromRecord(payload, provider) !== null) return [payload]

  const keyedModels: JsonRecord[] = []
  for (const [key, value] of Object.entries(payload)) {
    if (isRecord(value) && modelIdFromRecord(value, provider) !== null) keyedModels.push({ id: key, ...value })
  }
  return keyedModels
}

function isCatalogPayload(payload: unknown, provider: ProviderId, depth = 0): boolean {
  if (depth > 4) return false
  if (Array.isArray(payload)) return true
  if (!isRecord(payload)) return false
  if (provider === 'bfl' && isRecord(payload.paths)) return true
  for (const key of ['data', 'models', 'items', 'results']) {
    if (Array.isArray(payload[key])) return true
    if (isRecord(payload[key]) && isCatalogPayload(payload[key], provider, depth + 1)) return true
  }
  if (modelIdFromRecord(payload, provider) !== null) return true
  return Object.values(payload).some((value) => isRecord(value) && modelIdFromRecord(value, provider) !== null)
}

function contextWindowFrom(record: JsonRecord): number | null {
  const architecture = firstRecord(record, ['architecture', 'config', 'top_provider'])
  const sources = [record, architecture].filter((value): value is JsonRecord => value !== null)
  const keys = [
    'context_length', 'contextLength', 'context_window', 'contextWindow', 'context_window_tokens', 'contextWindowTokens',
    'max_context_length', 'maxContextLength', 'max_model_len', 'maxModelLen', 'max_sequence_length', 'maxSequenceLength',
    'inputTokenLimit', 'input_token_limit', 'max_input_tokens', 'maxInputTokens', 'max_position_embeddings',
  ]
  for (const source of sources) {
    for (const key of keys) {
      const value = positiveInteger(source[key])
      if (value !== null) return value
    }
  }
  return null
}

function outputLimitFrom(record: JsonRecord): number | null {
  const sources = [record, firstRecord(record, ['architecture', 'config', 'limits'])].filter(
    (value): value is JsonRecord => value !== null,
  )
  const keys = [
    'max_completion_tokens', 'maxCompletionTokens', 'max_output_tokens', 'maxOutputTokens', 'outputTokenLimit',
    'output_token_limit', 'max_tokens', 'maxTokens',
  ]
  for (const source of sources) {
    for (const key of keys) {
      const value = positiveInteger(source[key])
      if (value !== null) return value
    }
  }
  return null
}

function modalityPair(value: unknown): { input: string[]; output: string[] } {
  if (typeof value !== 'string') return { input: [], output: [] }
  const parts = value.split(/->|=>|\s+to\s+/i)
  if (parts.length < 2) return { input: [], output: [] }
  return { input: stringList(parts[0]), output: stringList(parts.slice(1).join('->')) }
}

function modalitiesFrom(record: JsonRecord): { input: string[]; output: string[] } {
  const architecture = firstRecord(record, ['architecture', 'modalities'])
  const sources = [record, architecture].filter((value): value is JsonRecord => value !== null)
  const input: string[] = []
  const output: string[] = []
  for (const source of sources) {
    input.push(...stringList(firstValue(source, ['input_modalities', 'inputModalities', 'input'])))
    output.push(...stringList(firstValue(source, ['output_modalities', 'outputModalities', 'output'])))
  }
  const pair = modalityPair(firstValue(record, ['modality', 'modalities']))
  input.push(...pair.input)
  output.push(...pair.output)
  return { input: unique(input.map(canonicalTag)), output: unique(output.map(canonicalTag)) }
}

function canonicalCapability(value: string): string | null {
  const normalized = canonicalTag(value)
  if (normalized.length === 0) return null
  const aliases: Record<string, string> = {
    chat: 'text-generation',
    completion: 'text-generation',
    completions: 'text-generation',
    'text-generation': 'text-generation',
    generate: 'text-generation',
    generatecontent: 'text-generation',
    streamgeneratecontent: 'text-generation',
    'image-text-to-text': 'vision',
    'text-to-image': 'image-generation',
    'image-to-image': 'image-generation',
    'text-to-video': 'video-generation',
    'image-to-video': 'video-generation',
    'text-to-audio': 'audio-generation',
    embedding: 'embeddings',
    embeddings: 'embeddings',
    embedcontent: 'embeddings',
    rerank: 'reranking',
    reranker: 'reranking',
    tools: 'tool-use',
    'tool-use': 'tool-use',
    functioncalling: 'tool-use',
    'function-calling': 'tool-use',
    reasoning: 'reasoning',
    thinking: 'reasoning',
    'structured-outputs': 'structured-output',
    'response-format': 'structured-output',
  }
  return aliases[normalized] ?? normalized
}

function capabilitiesFrom(record: JsonRecord, modalities: { input: string[]; output: string[] }): string[] {
  const values: string[] = []
  const architecture = firstRecord(record, ['architecture'])
  const sources = [record, architecture].filter((value): value is JsonRecord => value !== null)
  for (const source of sources) {
    values.push(...stringList(firstValue(source, ['capabilities', 'capability', 'supported_parameters', 'supportedParameters'])))
    values.push(...stringList(firstValue(source, ['supportedGenerationMethods', 'supported_generation_methods', 'pipeline_tag', 'pipelineTag'])))
  }
  if (modalities.input.includes('image') && modalities.output.includes('text')) values.push('vision')
  if (modalities.output.includes('image')) values.push('image-generation')
  if (modalities.output.includes('audio')) values.push('audio-generation')
  if (modalities.output.includes('video')) values.push('video-generation')
  return unique(values.map(canonicalCapability).filter((value): value is string => value !== null))
}

function pricingFrom(record: JsonRecord, provider: ProviderId): NormalizedPricing {
  const source = firstRecord(record, ['pricing', 'price', 'cost_pricing', 'costs']) ?? record
  const input = nonNegativeNumber(firstValue(source, [
    'input', 'prompt', 'input_price', 'inputPrice', 'prompt_price', 'input_per_million', 'inputPerMillion',
  ]))
  const output = nonNegativeNumber(firstValue(source, [
    'output', 'completion', 'output_price', 'outputPrice', 'completion_price', 'output_per_million', 'outputPerMillion',
  ]))
  const request = nonNegativeNumber(firstValue(source, ['request', 'request_price', 'requestPrice', 'per_request']))
  const currency = firstString(source, ['currency', 'price_currency', 'priceCurrency']) ?? (provider === 'openrouter' ? 'USD' : null)
  const declaredUnit = firstString(source, ['unit', 'price_unit', 'priceUnit'])
  const unit: NormalizedPricing['unit'] = declaredUnit === 'token' || declaredUnit === 'request'
    ? declaredUnit
    : provider === 'openrouter' && (input !== null || output !== null)
      ? 'token'
      : 'unknown'
  return { input, output, request, currency, unit }
}

function createdAtFrom(record: JsonRecord): number | null {
  const parsed = finiteNumber(firstValue(record, ['created', 'created_at', 'createdAt', 'published_at', 'publishedAt']))
  return parsed !== null && parsed >= 0 ? parsed : null
}

function ownerFrom(record: JsonRecord): string | null {
  return cleanText(firstValue(record, ['owned_by', 'ownedBy', 'owner', 'author']), MAX_DISPLAY_NAME_LENGTH)
}

function buildNormalizedModel(adapter: ProviderAdapter, record: JsonRecord): NormalizedModelMetadata | null {
  const modelId = modelIdFromRecord(record, adapter.id)
  if (modelId === null) return null
  const displayName = cleanText(firstValue(record, ['displayName', 'display_name', 'name', 'title', 'label']), MAX_DISPLAY_NAME_LENGTH)
  const modalities = modalitiesFrom(record)
  const capabilities = capabilitiesFrom(record, modalities)
  const free = explainFreePolicy(record, modelId)
  return {
    providerId: adapter.id,
    provider: adapter.id,
    id: modelId,
    modelId,
    displayName,
    owner: ownerFrom(record),
    contextWindowTokens: contextWindowFrom(record),
    maxOutputTokens: outputLimitFrom(record),
    inputModalities: modalities.input,
    outputModalities: modalities.output,
    capabilities,
    pricing: pricingFrom(record, adapter.id),
    createdAt: createdAtFrom(record),
    freePolicy: free.policy,
    freePolicyReason: free.reason,
    isFree: free.policy === 'free',
    isFreePolicyKnown: free.policy !== 'unknown',
    trust: 'untrusted',
    lifecycle: 'candidate',
    status: 'candidate',
    isCandidate: true,
    isTrusted: false,
    untrusted: true,
    validation: { status: 'pending', health: 'pending', capabilities: 'pending' },
    source: 'provider-metadata',
    sourceUrl: adapter.metadataUrl,
  }
}

export function normalizeProviderModels(provider: ProviderInput, payload: unknown): NormalizedModelMetadata[] {
  const adapter = getProviderAdapter(provider)
  const result: NormalizedModelMetadata[] = []
  const seen = new Set<string>()
  for (const raw of extractRawModels(payload, adapter.id)) {
    if (!isRecord(raw)) continue
    const model = buildNormalizedModel(adapter, raw)
    if (model === null || seen.has(model.modelId)) continue
    seen.add(model.modelId)
    result.push(model)
    if (result.length >= MAX_MODELS_PER_PROVIDER) break
  }
  return result
}

export const normalizeProviderCatalog = normalizeProviderModels
export const normalizeCatalogPayload = normalizeProviderModels

/** Promotion is explicit and evidence-based; discovery alone never promotes. */
export function markModelValidated(
  model: NormalizedModelMetadata,
  evidence: ValidationEvidence,
): NormalizedModelMetadata {
  const passed = evidence.health === 'passed' && evidence.capabilities === 'passed'
  return {
    ...model,
    trust: passed ? 'trusted' : 'untrusted',
    lifecycle: passed ? 'validated' : model.lifecycle,
    status: passed ? 'validated' : 'candidate',
    isCandidate: !passed,
    isTrusted: passed,
    untrusted: !passed,
    validation: {
      status: passed ? 'passed' : evidence.health === 'failed' || evidence.capabilities === 'failed' ? 'failed' : 'pending',
      health: evidence.health,
      capabilities: evidence.capabilities,
    },
  }
}

// ---------------------------------------------------------------------------
// Built-in adapters
// ---------------------------------------------------------------------------

function makeAdapter(
  id: ProviderId,
  name: string,
  auth: ProviderAuthScheme,
  requiresAuth: boolean,
  metadataKind: ProviderMetadataKind = 'models',
): ProviderAdapter {
  const metadataUrl = PROVIDER_METADATA_ENDPOINTS[id]
  return Object.freeze({
    id,
    provider: id,
    name,
    metadataUrl,
    catalogUrl: metadataUrl,
    metadataKind,
    auth,
    requiresAuth,
    normalize: (payload: unknown) => normalizeProviderModels(id, payload),
  })
}

export const PROVIDER_CATALOG_ADAPTERS: Readonly<Record<ProviderId, ProviderAdapter>> = Object.freeze({
  openrouter: makeAdapter('openrouter', 'OpenRouter', 'none', false),
  groq: makeAdapter('groq', 'Groq', 'bearer', true),
  'google-gemini': makeAdapter('google-gemini', 'Google Gemini', 'x-goog-api-key', true),
  cerebras: makeAdapter('cerebras', 'Cerebras', 'none', false),
  blablador: makeAdapter('blablador', 'Blablador', 'bearer', false),
  'hugging-face': makeAdapter('hugging-face', 'Hugging Face', 'bearer', false),
  siliconflow: makeAdapter('siliconflow', 'SiliconFlow', 'bearer', true),
  dmxapi: makeAdapter('dmxapi', 'DMXAPI', 'bearer', true),
  bfl: makeAdapter('bfl', 'Black Forest Labs', 'x-key', true, 'openapi'),
})

export const PROVIDER_ADAPTERS = PROVIDER_CATALOG_ADAPTERS

export function resolveProviderId(provider: string): ProviderId | null {
  return PROVIDER_ALIASES[provider.trim().toLowerCase()] ?? null
}

function isProviderAdapter(value: unknown): value is ProviderAdapter {
  return isRecord(value) && typeof value.id === 'string' && typeof value.metadataUrl === 'string' && typeof value.normalize === 'function'
}

export function getProviderAdapter(provider: ProviderInput | ProviderAdapter): ProviderAdapter {
  if (typeof provider !== 'string') {
    if (!isProviderAdapter(provider)) throw new ProviderCatalogError('invalid-options')
    const canonical = PROVIDER_CATALOG_ADAPTERS[provider.id]
    if (canonical === undefined) throw new ProviderCatalogError('unknown-provider')
    return canonical
  }
  const id = resolveProviderId(provider)
  if (id === null) throw new ProviderCatalogError('unknown-provider')
  const adapter = PROVIDER_CATALOG_ADAPTERS[id]
  if (adapter === undefined) throw new ProviderCatalogError('unknown-provider')
  return adapter
}

// ---------------------------------------------------------------------------
// Bounded metadata transport
// ---------------------------------------------------------------------------

function boundedInteger(value: number | undefined, fallback: number, min: number, max: number): number {
  if (value === undefined || !Number.isFinite(value)) return fallback
  return Math.min(max, Math.max(min, Math.floor(value)))
}

function timeoutFor(options: ProviderCatalogFetchOptions): number {
  return boundedInteger(options.timeoutMs, DEFAULT_TIMEOUT_MS, 1, MAX_TIMEOUT_MS)
}

function responseByteLimit(options: ProviderCatalogFetchOptions): number {
  return boundedInteger(options.maxResponseBytes, DEFAULT_MAX_RESPONSE_BYTES, 1, MAX_RESPONSE_BYTES)
}

function headersToRecord(headers: HeadersInit | undefined): Record<string, string> {
  const result: Record<string, string> = {}
  if (headers === undefined) return result
  if (typeof Headers !== 'undefined' && headers instanceof Headers) {
    headers.forEach((value, key) => {
      result[key] = value
    })
    return result
  }
  if (Array.isArray(headers)) {
    for (const entry of headers) {
      if (Array.isArray(entry) && entry.length >= 2) result[String(entry[0])] = String(entry[1])
    }
    return result
  }
  if (isRecord(headers)) {
    for (const [key, value] of Object.entries(headers)) {
      if (typeof value === 'string') result[key] = value
    }
  }
  return result
}

function safeHeaders(headers: HeadersInit | undefined): Record<string, string> {
  const result: Record<string, string> = {}
  for (const [name, value] of Object.entries(headersToRecord(headers))) {
    if (!/^[!#$%&'*+.^_`|~0-9A-Za-z-]+$/.test(name)) continue
    if (/[\r\n]/.test(value)) continue
    const lower = name.toLowerCase()
    if (lower === 'host' || lower === 'content-length' || lower === 'connection' || lower === 'transfer-encoding') continue
    result[name] = value
  }
  return result
}

function authHeader(adapter: ProviderAdapter, apiKey: string | undefined): Record<string, string> {
  if (apiKey === undefined || adapter.auth === 'none') return {}
  if (apiKey.length === 0 || apiKey.length > 512 || /[\r\n]/.test(apiKey)) {
    throw new ProviderCatalogError('invalid-options', adapter.id)
  }
  const header = AUTH_HEADER_BY_SCHEME[adapter.auth]
  return { [header]: adapter.auth === 'bearer' ? `Bearer ${apiKey}` : apiKey }
}

function validateMetadataUrl(adapter: ProviderAdapter): void {
  let parsed: URL
  try {
    parsed = new URL(adapter.metadataUrl)
  } catch {
    throw new ProviderCatalogError('invalid-options', adapter.id)
  }
  if (parsed.protocol !== 'https:') throw new ProviderCatalogError('invalid-options', adapter.id)
  for (const key of parsed.searchParams.keys()) {
    if (/(?:key|token|secret|password|auth)/i.test(key)) throw new ProviderCatalogError('invalid-options', adapter.id)
  }
  if (/\/(?:chat\/completions|completions|responses|messages|generate|images\/generations)(?:\/|$)/i.test(parsed.pathname)) {
    throw new ProviderCatalogError('invalid-options', adapter.id)
  }
}

function selectedFetch(options: ProviderCatalogFetchOptions): FetchLike {
  const candidate = options.fetchImpl ?? options.fetch
  if (candidate !== undefined) {
    if (typeof candidate !== 'function') throw new ProviderCatalogError('invalid-options')
    return candidate
  }
  if (typeof globalThis.fetch !== 'function') throw new ProviderCatalogError('network-error')
  return globalThis.fetch.bind(globalThis)
}

async function readBoundedJson(response: Response, maxBytes: number, provider: ProviderId): Promise<unknown> {
  const contentLength = finiteNumber(response.headers?.get?.('content-length'))
  if (contentLength !== null && contentLength > maxBytes) throw new ProviderCatalogError('response-too-large', provider)

  const body = response.body
  if (body !== null && body !== undefined && typeof body.getReader === 'function') {
    const reader = body.getReader()
    const chunks: Uint8Array[] = []
    let total = 0
    try {
      while (true) {
        const part = await reader.read()
        if (part.done) break
        const chunk = part.value instanceof Uint8Array ? part.value : new Uint8Array(part.value as ArrayBuffer)
        total += chunk.byteLength
        if (total > maxBytes) {
          await reader.cancel()
          throw new ProviderCatalogError('response-too-large', provider)
        }
        chunks.push(chunk)
      }
    } finally {
      reader.releaseLock()
    }
    const merged = new Uint8Array(total)
    let offset = 0
    for (const chunk of chunks) {
      merged.set(chunk, offset)
      offset += chunk.byteLength
    }
    try {
      return JSON.parse(new TextDecoder().decode(merged)) as unknown
    } catch {
      throw new ProviderCatalogError('invalid-json', provider)
    }
  }

  if (typeof response.text === 'function') {
    let text: string
    try {
      text = await response.text()
    } catch {
      throw new ProviderCatalogError('network-error', provider)
    }
    if (new TextEncoder().encode(text).byteLength > maxBytes) throw new ProviderCatalogError('response-too-large', provider)
    try {
      return JSON.parse(text) as unknown
    } catch {
      throw new ProviderCatalogError('invalid-json', provider)
    }
  }

  if (typeof response.json === 'function') {
    try {
      const value = (await response.json()) as unknown
      const serialized = JSON.stringify(value)
      if (serialized !== undefined && new TextEncoder().encode(serialized).byteLength > maxBytes) {
        throw new ProviderCatalogError('response-too-large', provider)
      }
      return value
    } catch (error) {
      if (error instanceof ProviderCatalogError) throw error
      throw new ProviderCatalogError('invalid-json', provider)
    }
  }
  throw new ProviderCatalogError('invalid-payload', provider)
}

async function fetchMetadataJson(
  adapter: ProviderAdapter,
  options: ProviderCatalogFetchOptions,
): Promise<unknown> {
  validateMetadataUrl(adapter)
  const fetchImpl = selectedFetch(options)
  const timeoutMs = timeoutFor(options)
  const maxBytes = responseByteLimit(options)
  const callerSignal = options.signal
  if (callerSignal?.aborted) throw new ProviderCatalogError('aborted', adapter.id)

  const controller = new AbortController()
  let timedOut = false
  let callerAborted = false
  let timer: ReturnType<typeof setTimeout> | undefined
  let removeAbortListener: (() => void) | undefined

  const abortPromise = new Promise<never>((_, reject) => {
    if (callerSignal === undefined) return
    const onAbort = (): void => {
      callerAborted = true
      controller.abort()
      reject(new ProviderCatalogError('aborted', adapter.id))
    }
    callerSignal.addEventListener('abort', onAbort, { once: true })
    removeAbortListener = (): void => callerSignal.removeEventListener('abort', onAbort)
  })

  const timeoutPromise = new Promise<never>((_, reject) => {
    timer = setTimeout(() => {
      timedOut = true
      controller.abort()
      reject(new ProviderCatalogError('timeout', adapter.id))
    }, timeoutMs)
  })

  try {
    const request = fetchImpl(adapter.metadataUrl, {
      method: 'GET',
      headers: {
        accept: 'application/json',
        ...authHeader(adapter, options.apiKey),
        ...safeHeaders(options.headers),
      },
      redirect: 'error',
      cache: 'no-store',
      signal: controller.signal,
    })
    const response = await Promise.race<Response>([request, timeoutPromise, abortPromise])
    const status = typeof response?.status === 'number' ? response.status : 0
    const ok = typeof response?.ok === 'boolean' ? response.ok : status >= 200 && status < 300
    if (!ok) throw new ProviderCatalogError('http-error', adapter.id, status || null)
    const bodyPromise = readBoundedJson(response, maxBytes, adapter.id)
    return await Promise.race<unknown>([bodyPromise, timeoutPromise, abortPromise])
  } catch (error) {
    if (error instanceof ProviderCatalogError) throw error
    if (timedOut) throw new ProviderCatalogError('timeout', adapter.id)
    if (callerAborted) throw new ProviderCatalogError('aborted', adapter.id)
    throw new ProviderCatalogError('network-error', adapter.id)
  } finally {
    if (timer !== undefined) clearTimeout(timer)
    removeAbortListener?.()
  }
}

export async function fetchProviderCatalog(
  provider: ProviderInput | ProviderAdapter,
  options: ProviderCatalogFetchOptions = {},
): Promise<ProviderCatalogSnapshot> {
  const adapter = getProviderAdapter(provider)
  const started = Date.now()
  const payload = await fetchMetadataJson(adapter, options)
  if (!isCatalogPayload(payload, adapter.id)) throw new ProviderCatalogError('invalid-payload', adapter.id)
  let models: NormalizedModelMetadata[]
  try {
    models = adapter.normalize(payload)
  } catch {
    throw new ProviderCatalogError('invalid-payload', adapter.id)
  }
  return {
    provider: adapter.id,
    models,
    modelCount: models.length,
    fetchedAt: new Date().toISOString(),
    durationMs: Date.now() - started,
    sourceUrl: adapter.metadataUrl,
    truncated: models.length >= MAX_MODELS_PER_PROVIDER,
  }
}

export async function fetchProviderModels(
  provider: ProviderInput | ProviderAdapter,
  options: ProviderCatalogFetchOptions = {},
): Promise<NormalizedModelMetadata[]> {
  return (await fetchProviderCatalog(provider, options)).models
}

export const fetchProviderCatalogModels = fetchProviderModels

// ---------------------------------------------------------------------------
// Isolated multi-provider synchronisation
// ---------------------------------------------------------------------------

function failureFrom(error: unknown, provider: ProviderId): ProviderCatalogFailure {
  if (error instanceof ProviderCatalogError) {
    return {
      code: error.code,
      message: `${provider}: ${error.code}${error.status === null ? '' : ` (${error.status})`}`,
      status: error.status,
    }
  }
  return { code: 'network-error', message: `${provider}: network-error`, status: null }
}

function requestedProviders(options: ProviderCatalogSyncOptions): ProviderId[] {
  const requested = options.providers ?? PROVIDER_IDS
  const result: ProviderId[] = []
  const seen = new Set<ProviderId>()
  for (const provider of requested) {
    const id = resolveProviderId(provider)
    if (id === null || seen.has(id)) continue
    seen.add(id)
    result.push(id)
  }
  if (result.length === 0 && requested.length > 0) throw new ProviderCatalogError('invalid-options')
  return result
}

async function syncOne(
  provider: ProviderId,
  options: ProviderCatalogSyncOptions,
): Promise<ProviderSyncResult> {
  const started = Date.now()
  const fetchedAt = new Date().toISOString()
  try {
    const snapshot = await fetchProviderCatalog(provider, options)
    return {
      ok: true,
      provider,
      models: snapshot.models,
      modelCount: snapshot.modelCount,
      fetchedAt: snapshot.fetchedAt,
      durationMs: snapshot.durationMs,
      sourceUrl: snapshot.sourceUrl,
      truncated: snapshot.truncated,
    }
  } catch (error) {
    return {
      ok: false,
      provider,
      models: [],
      modelCount: 0,
      fetchedAt,
      durationMs: Date.now() - started,
      error: failureFrom(error, provider),
    }
  }
}

export async function syncProviderCatalog(
  options: ProviderCatalogSyncOptions = {},
): Promise<ProviderCatalogSyncSummary> {
  const providers = requestedProviders(options)
  const concurrency = boundedInteger(options.concurrency, DEFAULT_CONCURRENCY, 1, MAX_CONCURRENCY)
  const started = Date.now()
  const results = new Array<ProviderSyncResult>(providers.length)
  let cursor = 0

  const worker = async (): Promise<void> => {
    while (true) {
      const index = cursor++
      if (index >= providers.length) return
      results[index] = await syncOne(providers[index], options)
    }
  }

  await Promise.all(Array.from({ length: Math.min(concurrency, providers.length) }, () => worker()))
  const successes = results.filter((result): result is ProviderSyncSuccess => result.ok)
  const failures = results.filter((result): result is ProviderSyncFailure => !result.ok)
  const models = successes.flatMap((result) => result.models)
  const freePolicyCounts: Record<FreePolicy, number> = { free: 0, trial: 0, credit: 0, unknown: 0, paid: 0 }
  const modelsByProvider: Partial<Record<ProviderId, number>> = {}
  for (const provider of providers) modelsByProvider[provider] = 0
  for (const model of models) {
    freePolicyCounts[model.freePolicy] += 1
    modelsByProvider[model.providerId] = (modelsByProvider[model.providerId] ?? 0) + 1
  }

  const finished = Date.now()
  return {
    ok: failures.length === 0,
    startedAt: new Date(started).toISOString(),
    finishedAt: new Date(finished).toISOString(),
    durationMs: finished - started,
    requestedProviders: providers,
    providers: results,
    results,
    successes,
    failures,
    successfulProviders: successes.length,
    failedProviders: failures.length,
    totalModels: models.length,
    candidateModels: models.filter((model) => model.isCandidate && model.untrusted).length,
    freeModels: freePolicyCounts.free,
    freePolicyCounts,
    modelsByProvider,
    models,
  }
}

export const syncProviderModels = syncProviderCatalog
