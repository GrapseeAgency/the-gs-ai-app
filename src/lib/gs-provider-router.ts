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
  baseUrlEnv?: string
}

const HEALTH_TTL_MS = 5 * 60 * 1_000
const HEALTH_TIMEOUT_MS = 12_000
const REQUEST_TIMEOUT_MS = 90_000

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

function parseChatResponse(payload: unknown, provider: GsProviderId, fallbackModel: string): { text: string; model: string } {
  if (!payload || typeof payload !== 'object') throw new Error(`${provider}: invalid JSON response`)
  const record = payload as { choices?: unknown; model?: unknown }
  const choice = Array.isArray(record.choices) ? record.choices[0] : null
  const message = choice && typeof choice === 'object' ? (choice as { message?: unknown }).message : null
  const textValue = message && typeof message === 'object' ? (message as { content?: unknown }).content : null
  const text = typeof textValue === 'string' ? textValue : ''
  if (!text) throw new Error(`${provider}: empty completion`)
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
      return {
        provider: config.id,
        healthy: false,
        checkedAt,
        status: response.status,
        code: `http_${response.status}`,
        message: safeMessage(message, `${config.id}: health check failed`),
        model,
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
    }
  }
}

function freshRecords(now = Date.now()): boolean {
  if (state.records.size === 0) return false
  for (const record of state.records.values()) {
    if (now - record.checkedAt >= HEALTH_TTL_MS) return false
  }
  return true
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
        ...(options.maxTokens !== undefined ? { max_tokens: options.maxTokens } : {}),
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
  const parsed = parseChatResponse(payload, config.id, model)
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
    if (!record?.healthy) {
      attempts.push({
        provider: config.id,
        code: record?.code ?? 'unchecked',
        status: record?.status ?? null,
        message: record?.message ?? `${config.id}: not health-checked`,
      })
      continue
    }
    try {
      const result = await callProvider(config, role, messages, options, fetchImpl)
      return result
    } catch (error) {
      const attempt = attemptFromError(config, error)
      attempts.push(attempt)
      const current = state.records.get(config.id)
      if (current) {
        state.records.set(config.id, { ...current, healthy: false, checkedAt: Date.now(), code: attempt.code, status: attempt.status, message: attempt.message })
      }
      console.warn(`GS-PROVIDER-DEAD provider=${config.id} code=${attempt.code} status=${attempt.status ?? 'none'}`)
    }
  }
  throw new GsAllProvidersUnavailableError(attempts)
}

export const GS_PROVIDER_PRIORITY: readonly GsProviderId[] = PROVIDER_CONFIGS.map((config) => config.id)
export const GS_PROVIDER_HEALTH_TTL_MS = HEALTH_TTL_MS
