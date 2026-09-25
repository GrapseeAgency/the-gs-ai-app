/**
 * GS AI provider router for benchmark/shim traffic.
 *
 * The benchmark target is the GS AI system, not a vendor. This module owns
 * provider selection for the OpenAI-compatible shim and scaffold roles. It
 * never imports the z-ai SDK and never receives a credential from a client.
 * Health is probed once per provider, cached for five minutes, and dead
 * providers are skipped for subsequent calls.
 */

export type GsProviderId =
  | 'google'
  | 'groq'
  | 'cerebras'
  | 'openrouter'
  | 'siliconflow'
  | 'huggingface'
  | 'dmx'
  | 'helmholtz'
  | 'bfl'

export type GsRouterRole = 'generator' | 'cheap' | 'planner'
export type GsMessage = { role: string; content: string }

export type GsProviderAttempt = {
  provider: GsProviderId
  code: string
  status: number | null
  message: string
}

export type GsHealthRecord = {
  provider: GsProviderId
  healthy: boolean
  checkedAt: number
  status: number | null
  code: string
  message: string
  model: string
  /**
   * Absolute time before which a known-dead provider is skipped. Set only for
   * transient failures (throttle, timeout, 5xx) so they recover on their own;
   * permanent failures carry no retry time and wait for the next health probe.
   */
  retryAfter?: number
}

export type GsProviderChatResult = {
  provider: GsProviderId
  model: string
  servedModel: string
  text: string
  status: number
}

export class GsAllProvidersUnavailableError extends Error {
  readonly code = 'all_providers_unavailable' as const
  readonly attempts: GsProviderAttempt[]

  constructor(attempts: GsProviderAttempt[]) {
    super('No GS AI provider is currently live.')
    this.name = 'GsAllProvidersUnavailableError'
    this.attempts = attempts
  }
}

type ProviderConfig = {
  id: GsProviderId
  baseUrl: string
  authHeader: 'Authorization' | 'x-goog-api-key' | 'x-key'
  authPrefix: string
  keyPrefixes: readonly string[]
  healthModel: string
  models: Record<GsRouterRole, string>
  /**
   * Hard ceiling for a single completion. Free tiers enforce a per-minute
   * output-token limit and reject an oversized request outright, so a request
   * that omits `max_tokens` can be refused by the provider's own default.
   */
  maxOutputTokens: number
  baseUrlEnv?: string
}

const HEALTH_TTL_MS = 5 * 60 * 1_000
const HEALTH_TIMEOUT_MS = 12_000
const REQUEST_TIMEOUT_MS = 90_000
/** Used when the caller does not pin a budget, clamped per provider. */
const DEFAULT_MAX_TOKENS = 1_000
/**
 * A throttled provider is not a dead provider. Rate limits and 5xx recover on
 * their own, so they earn a short cooldown instead of poisoning the health
 * cache for the full TTL — otherwise one burst turns into five minutes of 503.
 */
const TRANSIENT_COOLDOWN_MS = 15_000
const TRANSIENT_MAX_ATTEMPTS = 3
const TRANSIENT_BASE_DELAY_MS = 750

/**
 * `false` means "this will not fix itself": auth, quota and missing-resource
 * errors, plus malformed/empty content, which repeats deterministically for
 * the same request. Everything else (429, 5xx, timeouts, transport) is worth
 * retrying.
 */
function isTransientFailure(attempt: GsProviderAttempt): boolean {
  if (attempt.code === 'empty_completion' || attempt.code === 'invalid_response') return false
  if (attempt.code === 'timeout' || attempt.code === 'network_error') return true
  // An HTTP failure carries its status in the code; `status` is only populated
  // when the probe path supplies it. Reading status alone would misclassify a
  // 402 as transient and retry a dead credential.
  const status = attempt.status ?? /^http_(\d{3})$/.exec(attempt.code)?.[1]
  if (status === undefined) return false
  const code = Number(status)
  if (code === 429 || code === 408) return true
  return code >= 500
}

// Priority is intentional: Google → Groq → Cerebras → OpenRouter →
// SiliconFlow → HF → the rest. BFL is last because its public API is image
// oriented; it will be marked dead by the health probe if no chat route works.
const PROVIDER_CONFIGS: readonly ProviderConfig[] = [
  {
    id: 'google',
    baseUrl: 'https://generativelanguage.googleapis.com/v1beta/openai/chat/completions',
    authHeader: 'Authorization',
    authPrefix: 'Bearer ',
    keyPrefixes: ['GOOGLE_API_KEY', 'GEMINI_API_KEY'],
    healthModel: 'gemini-2.5-flash',
    models: {
      generator: 'gemini-2.5-flash',
      cheap: 'gemini-2.5-flash',
      planner: 'gemini-2.5-pro',
    },
    maxOutputTokens: 8_192,
    baseUrlEnv: 'GS_AI_GOOGLE_BASE_URL',
  },
  {
    id: 'groq',
    baseUrl: 'https://api.groq.com/openai/v1/chat/completions',
    authHeader: 'Authorization',
    authPrefix: 'Bearer ',
    keyPrefixes: ['GROQ_API_KEY'],
    healthModel: 'qwen/qwen3.8-27b',
    models: {
      generator: 'qwen/qwen3.8-27b',
      cheap: 'openai/gpt-oss-20b',
      planner: 'qwen/qwen3.8-27b',
    },
    // Groq's on_demand tier enforces OTPM=1000: any request asking for more
    // output tokens is rejected before generation starts.
    maxOutputTokens: 1_000,
    baseUrlEnv: 'GS_AI_GROQ_BASE_URL',
  },
  {
    id: 'cerebras',
    baseUrl: 'https://api.cerebras.ai/v1/chat/completions',
    authHeader: 'Authorization',
    authPrefix: 'Bearer ',
    keyPrefixes: ['CEREBRAS_API_KEY'],
    healthModel: 'qwen-3.8-27b',
    models: {
      generator: 'qwen-3.8-27b',
      cheap: 'gpt-oss-120b',
      planner: 'qwen-3.8-27b',
    },
    maxOutputTokens: 8_192,
    baseUrlEnv: 'GS_AI_CEREBRAS_BASE_URL',
  },
  {
    id: 'openrouter',
    baseUrl: 'https://openrouter.ai/api/v1/chat/completions',
    authHeader: 'Authorization',
    authPrefix: 'Bearer ',
    keyPrefixes: ['OPENROUTER_API_KEY'],
    healthModel: 'qwen/qwen3.8-max-prime',
    models: {
      generator: 'qwen/qwen3.8-max-prime',
      cheap: 'aion-labs/aion-3.5-mini',
      planner: 'qwen/qwen3.8-max-prime',
    },
    maxOutputTokens: 8_192,
    baseUrlEnv: 'GS_AI_OPENROUTER_BASE_URL',
  },
  {
    id: 'siliconflow',
    baseUrl: 'https://api.siliconflow.cn/v1/chat/completions',
    authHeader: 'Authorization',
    authPrefix: 'Bearer ',
    keyPrefixes: ['SILICONFLOW_API_KEY'],
    healthModel: 'Qwen/Qwen3-32B',
    models: {
      generator: 'Qwen/Qwen3-32B',
      cheap: 'Qwen/Qwen3-8B',
      planner: 'Qwen/Qwen3-32B',
    },
    maxOutputTokens: 4_096,
    baseUrlEnv: 'GS_AI_SILICONFLOW_BASE_URL',
  },
  {
    id: 'huggingface',
    baseUrl: 'https://api-inference.huggingface.co/v1/chat/completions',
    authHeader: 'Authorization',
    authPrefix: 'Bearer ',
    keyPrefixes: ['HF_API_KEY', 'HUGGINGFACE_API_KEY'],
    healthModel: 'meta-llama/Llama-3.1-8B-Instruct',
    models: {
      generator: 'meta-llama/Llama-3.1-8B-Instruct',
      cheap: 'meta-llama/Llama-3.1-8B-Instruct',
      planner: 'meta-llama/Llama-3.1-8B-Instruct',
    },
    maxOutputTokens: 2_048,
    baseUrlEnv: 'GS_AI_HUGGINGFACE_BASE_URL',
  },
  {
    id: 'dmx',
    baseUrl: 'https://www.dmxapi.com/v1/chat/completions',
    authHeader: 'Authorization',
    authPrefix: 'Bearer ',
    keyPrefixes: ['DMX_API_KEY', 'DMXAPI_API_KEY'],
    healthModel: 'abab6.5s-chat',
    models: {
      generator: 'abab6.5s-chat',
      cheap: 'abab6.5s-chat',
      planner: 'abab6.5s-chat',
    },
    maxOutputTokens: 4_096,
    baseUrlEnv: 'GS_AI_DMX_BASE_URL',
  },
  {
    id: 'helmholtz',
    baseUrl: 'https://blablador.fz-juelich.de/v1/chat/completions',
    authHeader: 'Authorization',
    authPrefix: 'Bearer ',
    keyPrefixes: ['HELMHOLTZ_API_KEY', 'BLABLADOR_API_KEY'],
    healthModel: 'Llama-3.3-70B-Instruct',
    models: {
      generator: 'Llama-3.3-70B-Instruct',
      cheap: 'Llama-3.3-70B-Instruct',
      planner: 'Llama-3.3-70B-Instruct',
    },
    maxOutputTokens: 2_048,
    baseUrlEnv: 'GS_AI_HELMHOLTZ_BASE_URL',
  },
  {
    id: 'bfl',
    baseUrl: 'https://api.bfl.ai/v1/chat/completions',
    authHeader: 'x-key',
    authPrefix: '',
    keyPrefixes: ['BFL_API_KEY', 'BLACK_FOREST_LABS_API_KEY'],
    healthModel: 'flux-pro',
    models: {
      generator: 'flux-pro',
      cheap: 'flux-pro',
      planner: 'flux-pro',
    },
    maxOutputTokens: 1_024,
    baseUrlEnv: 'GS_AI_BFL_BASE_URL',
  },
]

type RuntimeState = {
  records: Map<GsProviderId, GsHealthRecord>
  refreshing: Promise<GsHealthRecord[]> | null
}

type FetchLike = (input: string | URL | Request, init?: RequestInit) => Promise<Response>
type RouterOptions = {
  role?: GsRouterRole
  temperature?: number
  maxTokens?: number
  env?: NodeJS.ProcessEnv
  fetchImpl?: FetchLike
  timeoutMs?: number
  forceHealth?: boolean
  /** Test seam: how long a transient failure is skipped. */
  cooldownMs?: number
}

const globalState = globalThis as typeof globalThis & { __gsProviderRouterState?: RuntimeState }
const state: RuntimeState = globalState.__gsProviderRouterState ?? {
  records: new Map(),
  refreshing: null,
}
globalState.__gsProviderRouterState = state

function safeMessage(value: unknown, fallback: string): string {
  if (typeof value !== 'string') return fallback
  const cleaned = value.replace(/[\u0000-\u001f\u007f]/g, ' ').trim()
  return cleaned.length > 0 ? cleaned.slice(0, 240) : fallback
}

function providerKeys(config: ProviderConfig, env: NodeJS.ProcessEnv): string[] {
  const found = new Set<string>()
  for (const [name, raw] of Object.entries(env)) {
    const matches = config.keyPrefixes.some((prefix) => name === prefix || name.startsWith(`${prefix}_`))
    if (!matches || typeof raw !== 'string') continue
    for (const item of raw.split(/[\n,]/)) {
      const key = item.trim()
      if (key) found.add(key)
    }
  }
  return [...found]
}

function modelFor(config: ProviderConfig, role: GsRouterRole, env: NodeJS.ProcessEnv): string {
  const envName = `GS_AI_${config.id.toUpperCase().replace(/[^A-Z0-9]+/g, '_')}_${role.toUpperCase()}_MODEL`
  const override = env[envName]
  return typeof override === 'string' && override.trim() ? override.trim() : config.models[role]
}

function baseUrlFor(config: ProviderConfig, env: NodeJS.ProcessEnv): string {
  const override = config.baseUrlEnv ? env[config.baseUrlEnv] : undefined
  return typeof override === 'string' && override.trim() ? override.trim() : config.baseUrl
}

function attemptFromError(config: ProviderConfig, error: unknown, status: number | null = null): GsProviderAttempt {
  const record = error as { code?: unknown; message?: unknown; name?: unknown }
  const code = typeof record?.code === 'string' ? record.code : status !== null ? `http_${status}` : 'network_error'
  return {
    provider: config.id,
    code,
    status,
    message: safeMessage(record?.message, `${config.id}: provider request failed`),
  }
}

async function readErrorMessage(response: Response): Promise<string> {
  try {
    const text = await response.text()
    if (!text) return `HTTP ${response.status}`
    try {
      const parsed: unknown = JSON.parse(text)
      if (parsed && typeof parsed === 'object') {
        const error = (parsed as { error?: unknown }).error
        if (typeof error === 'string') return error
        if (error && typeof error === 'object' && typeof (error as { message?: unknown }).message === 'string') {
          return (error as { message: string }).message
        }
      }
    } catch {
      // fall through to the bounded plain-text message
    }
    return text
  } catch {
    return `HTTP ${response.status}`
  }
}

async function fetchWithTimeout(
  fetchImpl: FetchLike,
  url: string,
  init: RequestInit,
  timeoutMs: number,
): Promise<Response> {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  try {
    return await fetchImpl(url, { ...init, signal: controller.signal })
  } catch (error) {
    if (controller.signal.aborted) {
      const timeout = new Error(`timeout after ${timeoutMs}ms`)
      ;(timeout as Error & { code?: string }).code = 'timeout'
      throw timeout
    }
    throw error
  } finally {
    clearTimeout(timer)
  }
}

function codedError(provider: GsProviderId, message: string, code: string): Error {
  const error = new Error(message)
  ;(error as Error & { code?: string }).code = code
  return error
}

function parseChatResponse(
  payload: unknown,
  provider: GsProviderId,
  fallbackModel: string,
  options: { requireText?: boolean } = {},
): { text: string; model: string } {
  if (!payload || typeof payload !== 'object') throw codedError(provider, `${provider}: invalid JSON response`, 'invalid_response')
  const record = payload as { choices?: unknown; model?: unknown }
  const choice = Array.isArray(record.choices) ? record.choices[0] : null
  if (!choice || typeof choice !== 'object') {
    throw codedError(provider, `${provider}: no completion choice in response`, 'invalid_response')
  }
  const message = (choice as { message?: unknown }).message
  const textValue = message && typeof message === 'object' ? (message as { content?: unknown }).content : null
  const text = typeof textValue === 'string' ? textValue : ''
  // A well-formed choice with no text is a truncated completion, not a dead
  // provider: the 1-token health probe and thinking models legitimately return
  // `finish_reason: length` with empty content. Only the real request path
  // requires usable text.
  if (!text && options.requireText) throw codedError(provider, `${provider}: empty completion`, 'empty_completion')
  const model = typeof record.model === 'string' && record.model.trim() ? record.model.trim() : fallbackModel
  return { text, model }
}

async function probeProvider(
  config: ProviderConfig,
  env: NodeJS.ProcessEnv,
  fetchImpl: FetchLike,
): Promise<GsHealthRecord> {
  const checkedAt = Date.now()
  const keys = providerKeys(config, env)
  const model = modelFor(config, 'generator', env)
  if (keys.length === 0) {
    return {
      provider: config.id,
      healthy: false,
      checkedAt,
      status: null,
      code: 'missing_key',
      message: `${config.id}: no configured API key`,
      model,
    }
  }
  try {
    const response = await fetchWithTimeout(
      fetchImpl,
      baseUrlFor(config, env),
      {
        method: 'POST',
        headers: {
          Accept: 'application/json',
          'Content-Type': 'application/json',
          [config.authHeader]: `${config.authPrefix}${keys[0]}`,
        },
        body: JSON.stringify({
          model,
          messages: [{ role: 'user', content: 'hi' }],
          max_tokens: 1,
          temperature: 0,
        }),
      },
      HEALTH_TIMEOUT_MS,
    )
    if (!response.ok) {
      const message = await readErrorMessage(response)
      const code = `http_${response.status}`
      const attempt: GsProviderAttempt = {
        provider: config.id,
        code,
        status: response.status,
        message: safeMessage(message, `${config.id}: health check failed`),
      }
      return {
        provider: config.id,
        healthy: false,
        checkedAt,
        status: response.status,
        code,
        message: attempt.message,
        model,
        ...retryAfterFor(attempt, checkedAt),
      }
    }
    const payload: unknown = await response.json()
    parseChatResponse(payload, config.id, model)
    return { provider: config.id, healthy: true, checkedAt, status: response.status, code: 'ok', message: 'ok', model }
  } catch (error) {
    const attempt = attemptFromError(config, error)
    return {
      provider: config.id,
      healthy: false,
      checkedAt,
      status: attempt.status,
      code: attempt.code,
      message: attempt.message,
      model,
      ...retryAfterFor(attempt, checkedAt),
    }
  }
}

/** A transient failure is skipped only until its short cooldown expires. */
function retryAfterFor(attempt: GsProviderAttempt, now = Date.now(), cooldownMs = TRANSIENT_COOLDOWN_MS): { retryAfter?: number } {
  return isTransientFailure(attempt) ? { retryAfter: now + cooldownMs } : {}
}

function freshRecords(now = Date.now()): boolean {
  if (state.records.size === 0) return false
  for (const record of state.records.values()) {
    if (now - record.checkedAt >= HEALTH_TTL_MS) return false
  }
  return true
}

/** A dead provider is skippable while it is in cooldown, or dead for the full TTL. */
function skipUntil(record: GsHealthRecord | undefined, now = Date.now()): boolean {
  if (!record) return true
  if (record.healthy) return false
  // A transient failure is skipped only until its cooldown expires; once it
  // does, the provider is worth dialling again without waiting for a re-probe.
  if (record.retryAfter !== undefined) return now < record.retryAfter
  // A permanent failure waits for the record to go stale and be re-probed.
  return now - record.checkedAt < HEALTH_TTL_MS
}

export function getGsProviderHealth(): GsHealthRecord[] {
  return PROVIDER_CONFIGS.map((config) => state.records.get(config.id) ?? {
    provider: config.id,
    healthy: false,
    checkedAt: 0,
    status: null,
    code: 'unchecked',
    message: `${config.id}: not health-checked yet`,
    model: config.healthModel,
  })
}

export async function ensureGsProviderHealth(
  options: Pick<RouterOptions, 'env' | 'fetchImpl' | 'forceHealth'> = {},
): Promise<GsHealthRecord[]> {
  const env = options.env ?? process.env
  if (!options.forceHealth && freshRecords()) return getGsProviderHealth()
  if (state.refreshing) return state.refreshing
  const fetchImpl = options.fetchImpl ?? globalThis.fetch.bind(globalThis)
  const refresh = Promise.all(PROVIDER_CONFIGS.map((config) => probeProvider(config, env, fetchImpl))).then((records) => {
    for (const record of records) state.records.set(record.provider, record)
    return getGsProviderHealth()
  })
  state.refreshing = refresh
  try {
    return await refresh
  } finally {
    state.refreshing = null
  }
}

async function callProvider(
  config: ProviderConfig,
  role: GsRouterRole,
  messages: GsMessage[],
  options: RouterOptions,
  fetchImpl: FetchLike,
): Promise<GsProviderChatResult> {
  const env = options.env ?? process.env
  const keys = providerKeys(config, env)
  if (keys.length === 0) throw new Error(`${config.id}: no configured API key`)
  const model = modelFor(config, role, env)
  // Always send an explicit budget, clamped to the provider ceiling. Omitting
  // it lets the provider apply its own default, which on rate-limited tiers is
  // larger than the account is allowed to request and is rejected pre-flight.
  const requested = options.maxTokens ?? DEFAULT_MAX_TOKENS
  const maxTokens = Math.max(1, Math.min(requested, config.maxOutputTokens))
  const response = await fetchWithTimeout(
    fetchImpl,
    baseUrlFor(config, env),
    {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        [config.authHeader]: `${config.authPrefix}${keys[0]}`,
      },
      body: JSON.stringify({
        model,
        messages,
        temperature: options.temperature ?? 0.2,
        max_tokens: maxTokens,
        stream: false,
      }),
    },
    options.timeoutMs ?? REQUEST_TIMEOUT_MS,
  )
  if (!response.ok) {
    const message = await readErrorMessage(response)
    const error = new Error(safeMessage(message, `${config.id}: HTTP ${response.status}`))
    ;(error as Error & { code?: string }).code = `http_${response.status}`
    throw error
  }
  const payload: unknown = await response.json()
  const parsed = parseChatResponse(payload, config.id, model, { requireText: true })
  return {
    provider: config.id,
    model: parsed.model,
    servedModel: `${config.id}/${parsed.model}`,
    text: parsed.text,
    status: response.status,
  }
}

/** Select the first live provider in the documented priority order. */
export async function runGsProviderChat(
  messages: GsMessage[],
  options: RouterOptions = {},
): Promise<GsProviderChatResult> {
  const env = options.env ?? process.env
  const fetchImpl = options.fetchImpl ?? globalThis.fetch.bind(globalThis)
  const role = options.role ?? 'generator'
  await ensureGsProviderHealth({ env, fetchImpl, forceHealth: options.forceHealth })
  const attempts: GsProviderAttempt[] = []
  for (const config of PROVIDER_CONFIGS) {
    const record = state.records.get(config.id)
    if (skipUntil(record)) {
      attempts.push({
        provider: config.id,
        code: record?.code ?? 'unchecked',
        status: record?.status ?? null,
        message: record?.message ?? `${config.id}: not health-checked`,
      })
      continue
    }
    for (let tries = 1; tries <= TRANSIENT_MAX_ATTEMPTS; tries += 1) {
      try {
        return await callProvider(config, role, messages, options, fetchImpl)
      } catch (error) {
        const attempt = attemptFromError(config, error)
        attempts.push(attempt)
        const now = Date.now()
        const transient = isTransientFailure(attempt)
        const current = state.records.get(config.id)
        if (current) {
          state.records.set(config.id, {
            ...current,
            healthy: false,
            checkedAt: now,
            code: attempt.code,
            status: attempt.status,
            message: attempt.message,
            ...retryAfterFor(attempt, now, options.cooldownMs),
          })
        }
        console.warn(
          `GS-PROVIDER-${transient ? 'THROTTLED' : 'DEAD'} provider=${config.id} code=${attempt.code} status=${attempt.status ?? 'none'} try=${tries}/${TRANSIENT_MAX_ATTEMPTS}`,
        )
        // Only transient failures are worth another attempt; a 401/402 will
        // not become valid by asking again.
        if (!transient || tries === TRANSIENT_MAX_ATTEMPTS) break
        await new Promise((resolve) => setTimeout(resolve, TRANSIENT_BASE_DELAY_MS * 2 ** (tries - 1)))
      }
    }
  }
  throw new GsAllProvidersUnavailableError(attempts)
}

export const GS_PROVIDER_PRIORITY: readonly GsProviderId[] = PROVIDER_CONFIGS.map((config) => config.id)
export const GS_PROVIDER_HEALTH_TTL_MS = HEALTH_TTL_MS
