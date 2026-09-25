/**
 * Safe execution for the configured OpenAI-compatible text providers.
 *
 * This module is deliberately transport-only.  It uses the platform fetch
 * implementation, accepts the credential from its caller, and never reads a
 * process environment variable, a key pool, or a provider SDK.  The endpoint
 * table is an allow-list; Google Gemini and Black Forest Labs are deliberately
 * not translated to a guessed OpenAI-compatible route.
 *
 * Streaming uses the same line-oriented SSE contract as `consumeSseStream` in
 * `ai.ts`: comments/heartbeats are ignored, `data:` payloads are parsed, `[DONE]`
 * terminates the stream, and a plain-text payload is treated as a delta.
 */

import { consumeSseStream, type ChatMessageInput } from './ai'
import {
  FreeCapacityUnavailableError,
  isFreeCapacityUnavailableError,
} from './capacity-policy'
import {
  PROVIDER_IDS,
  resolveProviderId,
  type ProviderId,
  type ProviderInput,
} from './provider-catalog'

export type ProviderExecutionFetch = (
  input: string | URL | Request,
  init?: RequestInit,
) => Promise<Response>

export const GENERIC_TEXT_PROVIDER_IDS = [
  'openrouter',
  'groq',
  'cerebras',
  'blablador',
  'hugging-face',
  'siliconflow',
  'dmxapi',
] as const

export type GenericTextProviderId = (typeof GENERIC_TEXT_PROVIDER_IDS)[number]

/** Alias useful to callers that use the shorter catalogue terminology. */
export const SUPPORTED_GENERIC_TEXT_PROVIDERS = GENERIC_TEXT_PROVIDER_IDS

/**
 * These providers are present in the catalogue, but their text APIs are not
 * interchangeable with the OpenAI chat-completions contract.  Keep them
 * explicit instead of silently routing them to a guessed endpoint.
 */
export const UNSUPPORTED_GENERIC_TEXT_PROVIDER_IDS = ['google-gemini', 'bfl'] as const
export type UnsupportedGenericTextProviderId =
  (typeof UNSUPPORTED_GENERIC_TEXT_PROVIDER_IDS)[number]

export const UNSUPPORTED_GENERIC_TEXT_PROVIDERS = UNSUPPORTED_GENERIC_TEXT_PROVIDER_IDS

export interface ProviderExecutionProviderConfig {
  readonly id: GenericTextProviderId
  readonly baseUrl: string
  readonly chatCompletionsUrl: string
  readonly authHeader: 'Authorization'
  readonly authPrefix: 'Bearer '
  readonly defaultHeaders: Readonly<Record<string, string>>
}

function makeProviderConfig(
  id: GenericTextProviderId,
  baseUrl: string,
  chatCompletionsUrl: string,
  defaultHeaders: Readonly<Record<string, string>> = {},
): ProviderExecutionProviderConfig {
  return Object.freeze({
    id,
    baseUrl,
    chatCompletionsUrl,
    authHeader: 'Authorization' as const,
    authPrefix: 'Bearer ' as const,
    defaultHeaders: Object.freeze({ ...defaultHeaders }),
  })
}

/** Fixed HTTPS chat-completions endpoints for the supported text providers. */
export const PROVIDER_EXECUTION_CONFIGS: Readonly<
  Record<GenericTextProviderId, ProviderExecutionProviderConfig>
> = Object.freeze({
  openrouter: makeProviderConfig(
    'openrouter',
    'https://openrouter.ai/api/v1',
    'https://openrouter.ai/api/v1/chat/completions',
    {
      'HTTP-Referer': 'https://grapsee.agency',
      'X-Title': 'GS AI',
    },
  ),
  groq: makeProviderConfig(
    'groq',
    'https://api.groq.com/openai/v1',
    'https://api.groq.com/openai/v1/chat/completions',
  ),
  cerebras: makeProviderConfig(
    'cerebras',
    'https://api.cerebras.ai/v1',
    'https://api.cerebras.ai/v1/chat/completions',
  ),
  blablador: makeProviderConfig(
    'blablador',
    'https://blablador.fz-juelich.de/v1',
    'https://blablador.fz-juelich.de/v1/chat/completions',
  ),
  'hugging-face': makeProviderConfig(
    'hugging-face',
    'https://router.huggingface.co/v1',
    'https://router.huggingface.co/v1/chat/completions',
  ),
  siliconflow: makeProviderConfig(
    'siliconflow',
    'https://api.siliconflow.cn/v1',
    'https://api.siliconflow.cn/v1/chat/completions',
  ),
  dmxapi: makeProviderConfig(
    'dmxapi',
    'https://www.dmxapi.com/v1',
    'https://www.dmxapi.com/v1/chat/completions',
  ),
})

/** Endpoint-only view for diagnostics and configuration UIs. */
export const PROVIDER_EXECUTION_ENDPOINTS: Readonly<
  Record<GenericTextProviderId, string>
> = Object.freeze({
  openrouter: PROVIDER_EXECUTION_CONFIGS.openrouter.chatCompletionsUrl,
  groq: PROVIDER_EXECUTION_CONFIGS.groq.chatCompletionsUrl,
  cerebras: PROVIDER_EXECUTION_CONFIGS.cerebras.chatCompletionsUrl,
  blablador: PROVIDER_EXECUTION_CONFIGS.blablador.chatCompletionsUrl,
  'hugging-face': PROVIDER_EXECUTION_CONFIGS['hugging-face'].chatCompletionsUrl,
  siliconflow: PROVIDER_EXECUTION_CONFIGS.siliconflow.chatCompletionsUrl,
  dmxapi: PROVIDER_EXECUTION_CONFIGS.dmxapi.chatCompletionsUrl,
})

export function isGenericTextProviderId(value: unknown): value is GenericTextProviderId {
  return (
    typeof value === 'string' &&
    (GENERIC_TEXT_PROVIDER_IDS as readonly string[]).includes(value)
  )
}

export type ProviderExecutionErrorCode =
  | 'invalid-options'
  | 'unsupported-provider'
  | 'invalid-api-key'
  | 'timeout'
  | 'aborted'
  | 'network-error'
  | 'http-error'
  | 'invalid-response'
  | 'response-too-large'

const DEFAULT_CONNECT_TIMEOUT_MS = 15_000
const DEFAULT_COMPLETION_TIMEOUT_MS = 60_000
const DEFAULT_STREAM_TIMEOUT_MS = 90_000
const MAX_TIMEOUT_MS = 5 * 60_000
const DEFAULT_MAX_RESPONSE_BYTES = 8 * 1024 * 1024
const MAX_RESPONSE_BYTES = 32 * 1024 * 1024
const MAX_MESSAGES = 1_000
const MAX_MESSAGE_CHARS = 1_000_000
const MAX_MODEL_CHARS = 512
const MAX_API_KEY_CHARS = 512
const DEFAULT_CAPACITY_RETRY_AFTER_MS = 5_000
const MAX_CAPACITY_RETRY_AFTER_MS = 5 * 60_000

const KNOWN_PROVIDER_IDS = new Set<string>(PROVIDER_IDS)

function safeProviderLabel(provider: ProviderId | null | undefined): string {
  return provider !== null && provider !== undefined && KNOWN_PROVIDER_IDS.has(provider)
    ? provider
    : 'provider'
}

function defaultRetryable(code: ProviderExecutionErrorCode): boolean {
  return code === 'timeout' || code === 'network-error'
}

function boundedInteger(value: number | undefined, fallback: number, maximum: number): number {
  if (value === undefined || !Number.isFinite(value)) return fallback
  return Math.min(maximum, Math.max(1, Math.floor(value)))
}

function boundedRetryAfter(value: number | undefined): number {
  if (value === undefined || !Number.isFinite(value)) return DEFAULT_CAPACITY_RETRY_AFTER_MS
  return Math.min(MAX_CAPACITY_RETRY_AFTER_MS, Math.max(0, Math.floor(value)))
}

/** A transport error whose fields are safe to expose to queue/diagnostic code. */
export class ProviderExecutionError extends Error {
  readonly code: ProviderExecutionErrorCode
  readonly provider: ProviderId | null
  readonly status: number | null
  readonly retryable: boolean
  readonly retryAfterMs: number | null

  constructor(
    code: ProviderExecutionErrorCode,
    provider: ProviderId | null = null,
    status: number | null = null,
    retryable = defaultRetryable(code),
    retryAfterMs: number | null = null,
  ) {
    const statusSuffix = status === null ? '' : ` (${status})`
    // Never interpolate a response body, URL, header, thrown error, or key.
    super(`${safeProviderLabel(provider)}: ${code}${statusSuffix}`)
    this.name = 'ProviderExecutionError'
    this.code = code
    this.provider = provider
    this.status = status
    this.retryable = retryable
    this.retryAfterMs = retryAfterMs
    Object.setPrototypeOf(this, new.target.prototype)
  }
}

export class UnsupportedProviderError extends ProviderExecutionError {
  constructor(provider: ProviderId) {
    super('unsupported-provider', provider, null, false)
    this.name = 'UnsupportedProviderError'
  }
}

/**
 * Capacity failures remain compatible with the durable queue's existing
 * `FreeCapacityUnavailableError` contract while carrying provider/status
 * metadata for routing diagnostics.
 */
export class ProviderCapacityError extends FreeCapacityUnavailableError {
  readonly provider: ProviderId
  readonly status: number | null
  readonly retryable = true as const

  constructor(
    provider: ProviderId,
    status: number | null = null,
    retryAfterMs: number | null = null,
  ) {
    const statusSuffix = status === null ? '' : ` (${status})`
    super(
      `Provider capacity unavailable${statusSuffix}`,
      boundedRetryAfter(retryAfterMs ?? DEFAULT_CAPACITY_RETRY_AFTER_MS),
    )
    this.name = 'ProviderCapacityError'
    this.provider = provider
    this.status = status
    Object.setPrototypeOf(this, new.target.prototype)
  }
}

export function isProviderCapacityError(
  error: unknown,
): error is ProviderCapacityError | FreeCapacityUnavailableError {
  return error instanceof ProviderCapacityError || isFreeCapacityUnavailableError(error)
}

export const isRetryableCapacityError = isProviderCapacityError

export type ProviderExecutionDeltaHandler = (delta: string) => Promise<void> | void

export interface ProviderExecutionOptions {
  /** Explicit caller-supplied credential. No environment lookup is performed. */
  apiKey?: string
  headers?: HeadersInit
  timeoutMs?: number
  connectTimeoutMs?: number
  completionTimeoutMs?: number
  streamTimeoutMs?: number
  maxResponseBytes?: number
  signal?: AbortSignal
  fetchImpl?: ProviderExecutionFetch
  /** Alias for `fetchImpl`; `fetchImpl` wins when both are supplied. */
  fetch?: ProviderExecutionFetch
  stream?: boolean
  onDelta?: ProviderExecutionDeltaHandler
}

export interface ProviderExecutionRequest extends ProviderExecutionOptions {
  provider: ProviderInput
  model: string
  messages: readonly ChatMessageInput[]
  temperature?: number
  maxTokens?: number
  topP?: number
  stop?: string | readonly string[]
  seed?: number
}

export interface ProviderExecutionResult {
  provider: GenericTextProviderId
  text: string
  model: string | null
}

export type ProviderStreamResult = ProviderExecutionResult
export type ProviderCompletionResult = ProviderExecutionResult

type WireRole = 'system' | 'developer' | 'user' | 'assistant' | 'tool'
type WireMessage = { role: WireRole; content: string }

interface NormalizedRequest {
  provider: ProviderInput
  model: string
  messages: readonly ChatMessageInput[]
  apiKey: string
  stream: boolean
  onDelta?: ProviderExecutionDeltaHandler
  headers?: HeadersInit
  timeoutMs?: number
  connectTimeoutMs?: number
  completionTimeoutMs?: number
  streamTimeoutMs?: number
  maxResponseBytes?: number
  signal?: AbortSignal
  fetchImpl?: ProviderExecutionFetch
  fetch?: ProviderExecutionFetch
  temperature?: number
  maxTokens?: number
  topP?: number
  stop?: string | readonly string[]
  seed?: number
}

function mergeRequest(
  request: ProviderExecutionRequest,
  options: ProviderExecutionOptions,
): NormalizedRequest {
  return {
    provider: request.provider,
    model: request.model,
    messages: request.messages,
    apiKey: request.apiKey ?? options.apiKey ?? '',
    stream: request.stream ?? options.stream ?? false,
    onDelta: request.onDelta ?? options.onDelta,
    headers: request.headers ?? options.headers,
    timeoutMs: request.timeoutMs ?? options.timeoutMs,
    connectTimeoutMs: request.connectTimeoutMs ?? options.connectTimeoutMs,
    completionTimeoutMs: request.completionTimeoutMs ?? options.completionTimeoutMs,
    streamTimeoutMs: request.streamTimeoutMs ?? options.streamTimeoutMs,
    maxResponseBytes: request.maxResponseBytes ?? options.maxResponseBytes,
    signal: request.signal ?? options.signal,
    fetchImpl: request.fetchImpl ?? options.fetchImpl,
    fetch: request.fetch ?? options.fetch,
    temperature: request.temperature,
    maxTokens: request.maxTokens,
    topP: request.topP,
    stop: request.stop,
    seed: request.seed,
  }
}

function resolveExecutionProvider(provider: ProviderInput): GenericTextProviderId {
  const canonical = typeof provider === 'string' ? resolveProviderId(provider) : null
  if (canonical === null) throw new ProviderExecutionError('invalid-options')
  if (!isGenericTextProviderId(canonical)) throw new UnsupportedProviderError(canonical)
  return canonical
}

function validateApiKey(value: unknown, provider: ProviderId): string {
  if (
    typeof value !== 'string' ||
    value.length === 0 ||
    value.length > MAX_API_KEY_CHARS ||
    /[\u0000-\u001f\u007f]/.test(value) ||
    value.trim() !== value
  ) {
    throw new ProviderExecutionError('invalid-api-key', provider)
  }
  return value
}

function validateModel(value: unknown, provider: ProviderId): string {
  if (
    typeof value !== 'string' ||
    value.trim().length === 0 ||
    value.length > MAX_MODEL_CHARS ||
    /[\u0000-\u001f\u007f]/.test(value)
  ) {
    throw new ProviderExecutionError('invalid-options', provider)
  }
  return value
}

function normalizeRole(value: unknown): WireRole {
  const role = typeof value === 'string' ? value.toLowerCase() : ''
  if (
    role === 'system' ||
    role === 'developer' ||
    role === 'user' ||
    role === 'assistant' ||
    role === 'tool'
  ) {
    return role
  }
  // Match the defensive role normalisation used by ai.ts.
  return 'user'
}

function normalizeMessages(
  messages: unknown,
  provider: ProviderId,
): WireMessage[] {
  if (!Array.isArray(messages) || messages.length === 0 || messages.length > MAX_MESSAGES) {
    throw new ProviderExecutionError('invalid-options', provider)
  }
  return messages.map((message) => {
    if (typeof message !== 'object' || message === null) {
      throw new ProviderExecutionError('invalid-options', provider)
    }
    const record = message as { role?: unknown; content?: unknown }
    if (typeof record.content !== 'string' || record.content.length > MAX_MESSAGE_CHARS) {
      throw new ProviderExecutionError('invalid-options', provider)
    }
    return { role: normalizeRole(record.role), content: record.content }
  })
}

function validateOptionalNumber(
  value: number | undefined,
  provider: ProviderId,
  name: string,
  minimum = 0,
  maximum = Number.MAX_SAFE_INTEGER,
): void {
  if (value === undefined) return
  if (!Number.isFinite(value) || value < minimum || value > maximum) {
    throw new ProviderExecutionError('invalid-options', provider)
  }
  // Keep the name out of the error message; it is an internal validation hint.
  void name
}

function headersToRecord(headers: HeadersInit | undefined): Record<string, string> {
  if (headers === undefined) return {}
  if (typeof Headers !== 'undefined' && headers instanceof Headers) {
    const result: Record<string, string> = {}
    headers.forEach((value, key) => {
      result[key] = value
    })
    return result
  }
  if (Array.isArray(headers)) {
    const result: Record<string, string> = {}
    for (const entry of headers) {
      if (Array.isArray(entry) && entry.length >= 2) result[String(entry[0])] = String(entry[1])
    }
    return result
  }
  if (typeof headers === 'object') {
    const result: Record<string, string> = {}
    for (const [key, value] of Object.entries(headers)) {
      if (typeof value === 'string') result[key] = value
    }
    return result
  }
  return {}
}

const RESERVED_REQUEST_HEADERS = new Set([
  'authorization',
  'proxy-authorization',
  'x-api-key',
  'api-key',
  'x-goog-api-key',
  'x-key',
  'content-length',
  'host',
  'connection',
  'transfer-encoding',
  'accept',
  'content-type',
])

function requestHeaders(
  config: ProviderExecutionProviderConfig,
  apiKey: string,
  stream: boolean,
  extraHeaders: HeadersInit | undefined,
): Record<string, string> {
  const headers: Record<string, string> = {
    Accept: stream ? 'text/event-stream' : 'application/json',
    'Content-Type': 'application/json',
    ...config.defaultHeaders,
  }
  for (const [name, value] of Object.entries(headersToRecord(extraHeaders))) {
    if (!/^[!#$%&'*+.^_`|~0-9A-Za-z-]+$/.test(name)) continue
    if (/[\r\n]/.test(value)) continue
    if (RESERVED_REQUEST_HEADERS.has(name.toLowerCase())) continue
    headers[name] = value
  }
  // The explicit caller credential is always the final auth header. Extra
  // headers cannot replace it or smuggle a different credential.
  headers[config.authHeader] = `${config.authPrefix}${apiKey}`
  return headers
}

function buildPayload(
  provider: ProviderId,
  model: string,
  messages: WireMessage[],
  stream: boolean,
  request: NormalizedRequest,
): Record<string, unknown> {
  validateOptionalNumber(request.temperature, provider, 'temperature')
  validateOptionalNumber(request.topP, provider, 'topP', 0, 1)
  validateOptionalNumber(request.maxTokens, provider, 'maxTokens', 1, 1_000_000)
  validateOptionalNumber(request.seed, provider, 'seed', -(Number.MAX_SAFE_INTEGER), Number.MAX_SAFE_INTEGER)
  if (request.stop !== undefined) {
    const validStop =
      typeof request.stop === 'string' ||
      (Array.isArray(request.stop) &&
        request.stop.length <= 16 &&
        request.stop.every((item) => typeof item === 'string' && item.length <= 256))
    if (!validStop) throw new ProviderExecutionError('invalid-options', provider)
  }

  const payload: Record<string, unknown> = { model, messages, stream }
  if (request.temperature !== undefined) payload.temperature = request.temperature
  if (request.topP !== undefined) payload.top_p = request.topP
  if (request.maxTokens !== undefined) payload.max_tokens = Math.floor(request.maxTokens)
  if (request.stop !== undefined) payload.stop = request.stop
  if (request.seed !== undefined) payload.seed = Math.floor(request.seed)
  return payload
}

function selectFetch(request: NormalizedRequest): ProviderExecutionFetch {
  const candidate = request.fetchImpl ?? request.fetch
  if (candidate !== undefined) {
    if (typeof candidate !== 'function') {
      throw new ProviderExecutionError('invalid-options')
    }
    return candidate
  }
  if (typeof globalThis.fetch !== 'function') {
    throw new ProviderExecutionError('network-error')
  }
  return globalThis.fetch.bind(globalThis)
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function responseStatus(response: Response): number | null {
  return typeof response.status === 'number' && Number.isFinite(response.status)
    ? response.status
    : null
}

function responseSucceeded(response: Response, status: number | null): boolean {
  if (typeof response.ok === 'boolean') return response.ok
  return status !== null && status >= 200 && status < 300
}

function responseHeader(response: Response, name: string): string | null {
  try {
    const headers = response.headers as unknown
    if (headers !== null && typeof headers === 'object' && typeof (headers as Headers).get === 'function') {
      return (headers as Headers).get(name)
    }
  } catch {
    return null
  }
  return null
}

function parseContentLength(response: Response): number | null {
  const value = responseHeader(response, 'content-length')
  if (value === null || !/^\d+$/.test(value.trim())) return null
  const parsed = Number(value.trim())
  return Number.isSafeInteger(parsed) && parsed >= 0 ? parsed : null
}

function toBytes(value: unknown): Uint8Array {
  if (value instanceof Uint8Array) return value
  if (value instanceof ArrayBuffer) return new Uint8Array(value)
  if (ArrayBuffer.isView(value)) {
    return new Uint8Array(value.buffer, value.byteOffset, value.byteLength)
  }
  if (typeof value === 'string') return new TextEncoder().encode(value)
  return new Uint8Array(0)
}

function isReadableBody(value: unknown): value is ReadableStream<Uint8Array> {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as { getReader?: unknown }).getReader === 'function'
  )
}

async function cancelResponseBody(response: Response): Promise<void> {
  const body = (response as Response & { body?: unknown }).body
  if (!isReadableBody(body) || typeof body.cancel !== 'function') return
  try {
    await body.cancel()
  } catch {
    // A locked/already-consumed body is safe to leave to fetch's GC path.
  }
}

function responseByteLimit(request: NormalizedRequest): number {
  return boundedInteger(request.maxResponseBytes, DEFAULT_MAX_RESPONSE_BYTES, MAX_RESPONSE_BYTES)
}

function readBoundedText(
  response: Response,
  maxBytes: number,
  provider: ProviderId,
): Promise<string> {
  return (async () => {
    const declaredLength = parseContentLength(response)
    if (declaredLength !== null && declaredLength > maxBytes) {
      throw new ProviderExecutionError('response-too-large', provider)
    }

    const body = (response as Response & { body?: unknown }).body
    if (isReadableBody(body)) {
      const reader = body.getReader()
      const chunks: Uint8Array[] = []
      let total = 0
      try {
        while (true) {
          const part = await reader.read()
          if (part.done) break
          const chunk = toBytes(part.value)
          total += chunk.byteLength
          if (total > maxBytes) {
            try {
              await reader.cancel()
            } catch {
              // Preserve the bounded-size error below.
            }
            throw new ProviderExecutionError('response-too-large', provider)
          }
          chunks.push(chunk)
        }
      } finally {
        try {
          reader.releaseLock()
        } catch {
          // Some test/native readers do not expose a releasable lock.
        }
      }
      const merged = new Uint8Array(total)
      let offset = 0
      for (const chunk of chunks) {
        merged.set(chunk, offset)
        offset += chunk.byteLength
      }
      return new TextDecoder().decode(merged)
    }

    if (typeof response.text === 'function') {
      const text = await response.text()
      if (new TextEncoder().encode(text).byteLength > maxBytes) {
        throw new ProviderExecutionError('response-too-large', provider)
      }
      return text
    }

    if (typeof response.json === 'function') {
      const value = (await response.json()) as unknown
      const serialized = JSON.stringify(value)
      if (serialized === undefined || new TextEncoder().encode(serialized).byteLength > maxBytes) {
        throw new ProviderExecutionError('response-too-large', provider)
      }
      return serialized
    }

    throw new ProviderExecutionError('invalid-response', provider)
  })()
}

function streamFromText(text: string): ReadableStream<Uint8Array> {
  const bytes = new TextEncoder().encode(text)
  return new ReadableStream<Uint8Array>({
    start(controller) {
      if (bytes.byteLength > 0) controller.enqueue(bytes)
      controller.close()
    },
  })
}

interface StreamResult {
  text: string
  model: string | null
}

async function consumeBoundedSse(
  body: ReadableStream<Uint8Array>,
  maxBytes: number,
  provider: ProviderId,
  onDelta: ProviderExecutionDeltaHandler,
  deadline: RequestDeadline,
): Promise<StreamResult> {
  const sourceReader = body.getReader()
  let total = 0
  let limitExceeded = false
  const bounded = new ReadableStream<Uint8Array>({
    async pull(controller) {
      try {
        const part = await sourceReader.read()
        if (part.done) {
          controller.close()
          return
        }
        const chunk = toBytes(part.value)
        total += chunk.byteLength
        if (total > maxBytes) {
          limitExceeded = true
          try {
            await sourceReader.cancel()
          } catch {
            // The typed size error below is the useful result.
          }
          controller.error(new Error('provider response exceeded the byte limit'))
          return
        }
        controller.enqueue(chunk)
      } catch (error) {
        controller.error(error)
      }
    },
    async cancel(reason) {
      try {
        await sourceReader.cancel(reason)
      } catch {
        // Cancellation is best effort after the caller has stopped consuming.
      }
    },
  })

  let servedModel: string | null = null
  let forwarded = false
  const guardedOnDelta: ProviderExecutionDeltaHandler = async (delta) => {
    if (deadline.timedOut || deadline.callerAborted) return
    forwarded = true
    await onDelta(delta)
  }

  try {
    const text = await consumeSseStream(bounded, guardedOnDelta, (model) => {
      servedModel = model
    })
    if (limitExceeded) throw new ProviderExecutionError('response-too-large', provider)
    return { text, model: servedModel }
  } finally {
    // consumeSseStream intentionally mirrors the small ai.ts parser and does
    // not own the source reader. Release/cancel it here on every path.
    try {
      await sourceReader.cancel()
    } catch {
      // Already closed or locked by a platform implementation.
    }
    try {
      sourceReader.releaseLock()
    } catch {
      // Best effort; no user data is retained here.
    }
    // `forwarded` is intentionally not used for success classification. An
    // empty stream is capacity-shaped even if a provider emitted only roles.
    void forwarded
  }
}

function contentTypeIsEventStream(response: Response): boolean {
  const contentType = responseHeader(response, 'content-type') ?? ''
  return /text\/event-stream/i.test(contentType)
}

function contentTypeIsJson(response: Response): boolean {
  const contentType = responseHeader(response, 'content-type') ?? ''
  return /application\/json/i.test(contentType)
}

function textFromContent(value: unknown): string {
  if (typeof value === 'string') return value
  if (!Array.isArray(value)) return ''
  return value
    .map((part) => {
      if (typeof part === 'string') return part
      if (!isRecord(part)) return ''
      if (typeof part.text === 'string') return part.text
      if (isRecord(part.text)) return textFromContent(part.text)
      return ''
    })
    .join('')
}

function completionText(payload: unknown): string {
  if (!isRecord(payload)) return ''
  const choices = payload.choices
  if (Array.isArray(choices)) {
    for (const choice of choices) {
      if (!isRecord(choice)) continue
      const message = isRecord(choice.message) ? choice.message : undefined
      const delta = isRecord(choice.delta) ? choice.delta : undefined
      const candidates = [
        message?.content,
        choice.text,
        delta?.content,
      ]
      for (const candidate of candidates) {
        const text = textFromContent(candidate)
        if (text.length > 0) return text
      }
    }
  }
  return ''
}

function completionModel(payload: unknown): string | null {
  if (!isRecord(payload) || typeof payload.model !== 'string') return null
  const model = payload.model.trim()
  return model.length > 0 && model.length <= MAX_MODEL_CHARS && !/[\u0000-\u001f\u007f]/.test(model)
    ? model
    : null
}

function payloadLooksLikeCapacityFailure(payload: unknown): boolean {
  if (!isRecord(payload)) return false
  const values: string[] = []
  const collect = (value: unknown, depth: number): void => {
    if (depth > 3) return
    if (typeof value === 'string') {
      values.push(value)
      return
    }
    if (Array.isArray(value)) {
      for (const item of value) collect(item, depth + 1)
      return
    }
    if (isRecord(value)) {
      for (const item of Object.values(value)) collect(item, depth + 1)
    }
  }
  collect(payload.error ?? payload, 0)
  return values.some((value) =>
    /(?:\b429\b|rate.?limit|too many requests|capacity|overload|quota|temporar(?:y|ily) unavailable)/i.test(value),
  )
}

function retryAfterMs(response: Response): number {
  const value = responseHeader(response, 'retry-after')
  if (value === null) return DEFAULT_CAPACITY_RETRY_AFTER_MS
  const trimmed = value.trim()
  if (/^\d+(?:\.\d+)?$/.test(trimmed)) {
    return boundedRetryAfter(Number(trimmed) * 1_000)
  }
  const date = Date.parse(trimmed)
  if (Number.isFinite(date)) return boundedRetryAfter(date - Date.now())
  return DEFAULT_CAPACITY_RETRY_AFTER_MS
}

export function isRetryableCapacityStatus(status: number): boolean {
  return status === 402 || status === 408 || status === 425 || status === 429 || status >= 500
}

function classifyHttpFailure(response: Response, provider: ProviderId): Error {
  const status = responseStatus(response)
  if (status === null) return new ProviderExecutionError('network-error', provider, null, true)
  if (isRetryableCapacityStatus(status)) {
    return new ProviderCapacityError(provider, status, retryAfterMs(response))
  }
  return new ProviderExecutionError('http-error', provider, status, false)
}

interface RequestDeadline {
  controller: AbortController
  timedOut: boolean
  callerAborted: boolean
  abortPromise: Promise<never>
  dispose: () => void
}

function createDeadline(signal: AbortSignal | undefined, provider: ProviderId): RequestDeadline {
  if (signal?.aborted) throw new ProviderExecutionError('aborted', provider, null, false)

  const controller = new AbortController()
  let rejectAbort: ((reason?: unknown) => void) | undefined
  const abortPromise = new Promise<never>((_, reject) => {
    // The signal listener below fills this closure when a caller signal exists.
    // With no caller signal, the promise intentionally remains pending.
    rejectAbort = reject
  })
  let removeListener: (() => void) | undefined
  const deadline: RequestDeadline = {
    controller,
    timedOut: false,
    callerAborted: false,
    abortPromise,
    dispose: () => {
      removeListener?.()
    },
  }

  if (signal !== undefined) {
    const onAbort = (): void => {
      deadline.callerAborted = true
      controller.abort()
      rejectAbort?.(new ProviderExecutionError('aborted', provider, null, false))
    }
    signal.addEventListener('abort', onAbort, { once: true })
    removeListener = (): void => signal.removeEventListener('abort', onAbort)
  }

  return deadline
}

async function withTimeout<T>(
  operation: Promise<T>,
  timeoutMs: number,
  deadline: RequestDeadline,
  provider: ProviderId,
  onTimeout?: () => void,
): Promise<T> {
  let timer: ReturnType<typeof setTimeout> | undefined
  const timeoutPromise = new Promise<never>((_, reject) => {
    timer = setTimeout(() => {
      // The deadline owns the classification flag. `controller.abort()` is
      // what stops a native fetch/body reader without exposing its raw error.
      deadline.timedOut = true
      deadline.controller.abort()
      try {
        onTimeout?.()
      } catch {
        // Timeout classification remains authoritative.
      }
      reject(new ProviderExecutionError('timeout', provider, null, true, 1_000))
    }, timeoutMs)
  })

  try {
    return await Promise.race([operation, timeoutPromise, deadline.abortPromise])
  } finally {
    if (timer !== undefined) clearTimeout(timer)
  }
}

function connectTimeoutFor(request: NormalizedRequest): number {
  return boundedInteger(
    request.connectTimeoutMs ?? request.timeoutMs,
    DEFAULT_CONNECT_TIMEOUT_MS,
    MAX_TIMEOUT_MS,
  )
}

function completionTimeoutFor(request: NormalizedRequest): number {
  return boundedInteger(
    request.completionTimeoutMs ?? request.timeoutMs,
    DEFAULT_COMPLETION_TIMEOUT_MS,
    MAX_TIMEOUT_MS,
  )
}

function streamTimeoutFor(request: NormalizedRequest): number {
  return boundedInteger(
    request.streamTimeoutMs ?? request.timeoutMs,
    DEFAULT_STREAM_TIMEOUT_MS,
    MAX_TIMEOUT_MS,
  )
}

async function consumeStreamingResponse(
  response: Response,
  request: NormalizedRequest,
  provider: ProviderId,
  config: GenericTextProviderId,
  deadline: RequestDeadline,
): Promise<StreamResult> {
  const maxBytes = responseByteLimit(request)
  const onDelta = request.onDelta
  if (onDelta === undefined) throw new ProviderExecutionError('invalid-options', provider)

  // A provider that ignores stream=true and returns a normal JSON completion
  // is handled as one bounded fallback delta, while true SSE remains live.
  if (contentTypeIsJson(response) && !contentTypeIsEventStream(response)) {
    const text = await readBoundedText(response, maxBytes, provider)
    if (text.trim().length === 0) throw new ProviderCapacityError(provider)
    const payload = (() => {
      try {
        return JSON.parse(text) as unknown
      } catch {
        throw new ProviderExecutionError('invalid-response', provider)
      }
    })()
    const completion = completionText(payload)
    if (completion.length === 0) {
      if (payloadLooksLikeCapacityFailure(payload)) throw new ProviderCapacityError(provider)
      throw new ProviderCapacityError(provider)
    }
    await onDelta(completion)
    return { text: completion, model: completionModel(payload) }
  }

  const body = (response as Response & { body?: unknown }).body
  let stream: ReadableStream<Uint8Array>
  if (isReadableBody(body)) {
    stream = body
  } else {
    // This path is primarily for small Response doubles and platforms that
    // expose only text/json(). It is still bounded and uses the same parser.
    const text = await readBoundedText(response, maxBytes, provider)
    stream = streamFromText(text)
  }
  const result = await consumeBoundedSse(stream, maxBytes, provider, onDelta, deadline)
  void config // retained in the signature to make provider routing explicit
  return result
}

async function executeNormalized(request: NormalizedRequest): Promise<ProviderExecutionResult> {
  const configId = resolveExecutionProvider(request.provider)
  const config = PROVIDER_EXECUTION_CONFIGS[configId]
  const provider: ProviderId = configId
  const model = validateModel(request.model, provider)
  const apiKey = validateApiKey(request.apiKey, provider)
  const messages = normalizeMessages(request.messages, provider)
  const stream = request.stream
  if (stream && typeof request.onDelta !== 'function') {
    throw new ProviderExecutionError('invalid-options', provider)
  }
  const payload = buildPayload(provider, model, messages, stream, request)
  const fetchImpl = selectFetch(request)
  const deadline = createDeadline(request.signal, provider)
  const connectTimeout = connectTimeoutFor(request)
  const bodyTimeout = stream ? streamTimeoutFor(request) : completionTimeoutFor(request)
  let response: Response | undefined

  try {
    const requestPromise = Promise.resolve().then(() =>
      fetchImpl(config.chatCompletionsUrl, {
        method: 'POST',
        headers: requestHeaders(config, apiKey, stream, request.headers),
        body: JSON.stringify(payload),
        signal: deadline.controller.signal,
        redirect: 'error',
        cache: 'no-store',
      }),
    )
    response = await withTimeout(
      requestPromise,
      connectTimeout,
      deadline,
      provider,
    )

    if (!responseSucceeded(response, responseStatus(response))) {
      await cancelResponseBody(response)
      throw classifyHttpFailure(response, provider)
    }

    if (stream) {
      const result = await withTimeout(
        consumeStreamingResponse(response, request, provider, configId, deadline),
        bodyTimeout,
        deadline,
        provider,
        () => {
          void cancelResponseBody(response as Response)
        },
      )
      if (result.text.trim().length === 0) throw new ProviderCapacityError(provider)
      return { provider: configId, text: result.text, model: result.model }
    }

    const raw = await withTimeout(
      readBoundedText(response, responseByteLimit(request), provider),
      bodyTimeout,
      deadline,
      provider,
      () => {
        void cancelResponseBody(response as Response)
      },
    )
    if (raw.trim().length === 0) throw new ProviderCapacityError(provider)
    let parsed: unknown
    try {
      parsed = JSON.parse(raw) as unknown
    } catch {
      throw new ProviderExecutionError('invalid-response', provider)
    }
    const text = completionText(parsed)
    if (text.length === 0) {
      if (payloadLooksLikeCapacityFailure(parsed)) throw new ProviderCapacityError(provider)
      throw new ProviderCapacityError(provider)
    }
    return { provider: configId, text, model: completionModel(parsed) }
  } catch (error) {
    if (error instanceof ProviderExecutionError || isProviderCapacityError(error)) throw error
    if (deadline.timedOut) {
      throw new ProviderExecutionError('timeout', provider, null, true, 1_000)
    }
    if (deadline.callerAborted) {
      throw new ProviderExecutionError('aborted', provider, null, false)
    }
    // Do not use String(error): fetch errors and provider bodies can contain a
    // credential. The typed code is sufficient for queue/diagnostic handling.
    throw new ProviderExecutionError('network-error', provider, null, true)
  } finally {
    deadline.dispose()
  }
}

/**
 * Execute one provider request. Set `stream: true` and provide `onDelta` for a
 * streaming call; the result always contains the complete text and any model
 * id reported by the provider.
 */
export async function executeProvider(
  request: ProviderExecutionRequest,
  options: ProviderExecutionOptions = {},
): Promise<ProviderExecutionResult> {
  return executeNormalized(mergeRequest(request, options))
}

/** Non-streaming convenience wrapper. */
export async function executeProviderCompletion(
  request: ProviderExecutionRequest,
  options: ProviderExecutionOptions = {},
): Promise<ProviderCompletionResult> {
  return executeProvider({ ...request, stream: false }, options)
}

/**
 * Streaming convenience wrapper. The callback may be supplied either on the
 * request or as the second argument; an options object may be supplied third.
 */
export async function executeProviderStream(
  request: ProviderExecutionRequest,
  onDeltaOrOptions?: ProviderExecutionDeltaHandler | ProviderExecutionOptions,
  maybeOptions: ProviderExecutionOptions = {},
): Promise<ProviderStreamResult> {
  const onDelta =
    typeof onDeltaOrOptions === 'function' ? onDeltaOrOptions : request.onDelta
  const options =
    typeof onDeltaOrOptions === 'function' || onDeltaOrOptions === undefined
      ? maybeOptions
      : onDeltaOrOptions
  return executeProvider({ ...request, stream: true, onDelta }, options)
}

export async function executeProviderText(
  request: ProviderExecutionRequest,
  options: ProviderExecutionOptions = {},
): Promise<string> {
  return (await executeProvider(request, options)).text
}

export async function executeProviderCompletionText(
  request: ProviderExecutionRequest,
  options: ProviderExecutionOptions = {},
): Promise<string> {
  return (await executeProviderCompletion(request, options)).text
}

export async function executeProviderStreamText(
  request: ProviderExecutionRequest,
  onDeltaOrOptions?: ProviderExecutionDeltaHandler | ProviderExecutionOptions,
  maybeOptions: ProviderExecutionOptions = {},
): Promise<string> {
  return (
    await executeProviderStream(request, onDeltaOrOptions as ProviderExecutionDeltaHandler, maybeOptions)
  ).text
}

// Small aliases make the adapter easy to discover beside the existing
// `completeChat`/`streamChat` helpers without introducing another transport.
export const completeProvider = executeProviderCompletion
export const streamProvider = executeProviderStream
export const completeProviderCompletion = executeProviderCompletion
export const streamProviderCompletion = executeProviderStream
export const executeConfiguredProvider = executeProvider
