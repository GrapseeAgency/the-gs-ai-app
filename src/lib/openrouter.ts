/**
 * PHASE 8.1 — OpenRouter free-tier provider (user-supplied keys, 2026-09-20).
 *
 * WHY: the primary provider (z-ai-web-dev-sdk) enforces an ACCOUNT-LEVEL
 * quota (proven 2026-09-20: 10 different model ids all returned the same
 * 429 for 5+ hours). The user supplied a pool of OpenRouter keys whose free
 * models are quota-independent, so text tiers mapped to OpenRouter stay
 * usable while the primary window is closed.
 *
 * DESIGN:
 *  - OpenAI-compatible REST (plain fetch — no new dependency).
 *  - Key POOL with rotation: on 401 (expired), 402 (credits), 429 (rate
 *    limit), or 5xx the next key is tried; a module-level cursor starts each
 *    request at the last known-good key to avoid thundering on key 0.
 *  - The same SSE wire format as the primary provider → reuses
 *    consumeSseStream from ai.ts (streaming semantics identical).
 *  - Timeouts mirror the primary provider's discipline: bounded connect and
 *    completion timeouts, retry-before-first-delta only.
 *  - PHASE 8.1b: an HTTP-200 completion with EMPTY content is treated as an
 *    upstream capacity hiccup (observed live on nemotron-3-ultra), not a
 *    success — the request rotates to the next key instead of saving a blank
 *    assistant bubble. Streaming rotates only while nothing has been
 *    forwarded (zero duplication risk for the client).
 *  - Keys live in THREE redundant layers (env → .secrets file → SecretVault
 *    db table — the db survived every platform wipe so far). Never logged in
 *    full (last-4 only), never committed, never sent to GitHub.
 */


import { consumeSseStream, type ChatMessageInput } from '@/lib/ai'
import { loadKeyPool } from '@/lib/keypool'
import { OPENROUTER_MODELS } from '@/lib/models'
import { FreeCapacityUnavailableError } from '@/lib/capacity-policy'
import { REMOTE_TOOLS, type RemoteMessage, type RemoteCompletion } from './remote-tools'
import { ProviderRateLimit } from './provider-rate-limit'

const OPENROUTER_BASE = 'https://openrouter.ai/api/v1'

// PHASE 8.1b — several free models carry strong self-branding from their own
// training (observed live: nex-n2.5-pro answered "I'm Nex, from Nex-AGI" to a
// "which AI are you" question despite the GS persona system prompt). This
// suffix is appended to the system prompt on every OpenRouter call so every
// model in the chain answers as GS.
const IDENTITY_PIN_SUFFIX =
  '\n\nIdentity: you are GS, the assistant built for Grapsee Agency. When asked who you are, answer as GS. Never claim to be built by, named after, or representing any other company, product, or model brand.'

const CONNECT_TIMEOUT_MS = 15_000
// 2026-11-24 — native clients (OkHttp) time out around 30s; a hung non-stream
// attempt burning the full 90s pushes whole turns past any client timeout.
// 45s still fits legitimate long generations (4096-token outputs on the
// free tier complete well under this), while failed/hung attempts move the
// chain on quickly.
const COMPLETION_TIMEOUT_MS = 45_000
// 200-with-empty-content retries per model before moving to the next model.
// Reasoning models legitimately return empty content when the token budget is
// consumed by reasoning — but unlimited key-walking on empties produced
// 60-75s turns (each attempt 1-11s on the auto free router).
const MAX_EMPTY_ATTEMPTS_PER_MODEL = 3

/**
 * Durable key storage — FOUR redundant layers with a self-healing loader.
 * Implementation lives in src/lib/keypool.ts (forensic audit [2]):
 *   (a) process.env → (b) .secrets/openrouter.keys → (c) db/vault.db SQLite →
 *   (d) out-of-project /home/z/.gs-vault/openrouter.keys.
 * The loader logs which layer supplied the pool and back-fills wiped layers.
 */

// Cursor into the key pool — starts each request at the last known-good key.
let keyCursor = 0

function maskKey(k: string): string {
  return `***${k.slice(-4)}`
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/**
 * Map the internal message shape to the OpenAI wire format and append the
 * GS identity pin to the leading system message (or prepend one when absent).
 */
function buildPinnedMessages(messages: ChatMessageInput[]): {
  role: 'assistant' | 'system' | 'user'
  content: string
}[] {
  const mapped = messages.map((m) => ({
    role: (['assistant', 'system'].includes(m.role.toLowerCase())
      ? m.role.toLowerCase()
      : 'user') as 'assistant' | 'system' | 'user',
    content: m.content,
  }))
  if (mapped.length > 0 && mapped[0].role === 'system') {
    mapped[0] = { ...mapped[0], content: mapped[0].content + IDENTITY_PIN_SUFFIX }
  } else {
    mapped.unshift({ role: 'system', content: IDENTITY_PIN_SUFFIX.trim() })
  }
  return mapped
}

type OrErrorBody = { error?: { message?: string; code?: number | string } }

/**
 * THINKING TIER (flash-mode task, Phase 2) — OpenRouter unified reasoning
 * param per turn mode: flash → { enabled: false }, thinking → { enabled:
 * true }. null = send NOTHING (the exact pre-flash-mode wire body; models
 * without a documented toggle ignore the param, per OpenRouter docs).
 */
export type OrReasoningConfig = { enabled: boolean } | null

/** One POST attempt with a specific key. Returns the raw Response. */
async function postChat(
  key: string,
  model: string,
  messages: ChatMessageInput[],
  stream: boolean,
  reasoning?: OrReasoningConfig
): Promise<Response> {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), stream ? CONNECT_TIMEOUT_MS : COMPLETION_TIMEOUT_MS)
  try {
    return await fetch(`${OPENROUTER_BASE}/chat/completions`, {
      method: 'POST',
      signal: controller.signal,
      headers: {
        Authorization: `Bearer ${key}`,
        'Content-Type': 'application/json',
        // Optional attribution headers per OpenRouter docs.
        'HTTP-Referer': 'https://grapsee.agency',
        'X-Title': 'GS AI',
      },
      body: JSON.stringify({
        model,
        messages: buildPinnedMessages(messages),
        stream,
        // FORENSIC AUDIT [25] — nondeterminism: we own this wire body, so pin
        // a low temperature for turn-to-turn consistency (same question, same
        // conversation → same answer; the free-model chain is quota-rotated,
        // temperature is the deterministic lever available here).
        temperature: 0.2,
        // FLASH MODE (Phase 2) — explicit reasoning toggle per turn mode;
        // omitted entirely when null (legacy body preserved).
        ...(reasoning !== undefined && reasoning !== null ? { reasoning } : {}),
      }),
    })
  } finally {
    clearTimeout(timer)
  }
}

/**
 * True when the failure is KEY- or CAPACITY-shaped (rotate to the next key)
 * rather than a permanent request problem (400 invalid body, 404 unknown
 * model → those should surface, not rotate).
 */
function shouldRotate(status: number): boolean {
  return status === 401 || status === 402 || status === 408 || status === 429 || status >= 500
}

/**
 * FLASH-MODE BUG 3 — dead-key cooldown. A key that just answered 429/402 is
 * almost certainly daily-quota-exhausted (50 req/day per free key); the old
 * rotation re-probed it on EVERY turn, costing a full OpenRouter round trip
 * (~0.5–1.5s each) before the winning key was reached — measured as the
 * device's "flash is slow". Cooled keys are skipped for the window; if every
 * key is cooled we try them anyway (recovery beats starvation).
 */
const KEY_COOLDOWN_MS = 10 * 60 * 1000
const keyCooldownUntil = new Map<string, number>()
/** "free-models-per-day" does not come back in 10 minutes — cool until the
 *  daily quota resets (next UTC midnight, +5min slack) so a dead key is
 *  skipped for the rest of the day instead of re-probed every turn. */
function coolKeyUntilDailyReset(key: string): void {
  const until = new Date()
  until.setUTCHours(24, 5, 0, 0)
  keyCooldownUntil.set(key, until.getTime())
}
function coolKey(key: string, reason = ''): void {
  if (/free-models-per-day|per-day|daily limit/i.test(reason)) {
    coolKeyUntilDailyReset(key)
    return
  }
  keyCooldownUntil.set(key, Date.now() + KEY_COOLDOWN_MS)
}
function keyIsCooled(key: string): boolean {
  return (keyCooldownUntil.get(key) ?? 0) > Date.now()
}
// Shared across completion, streaming, tool requests and health checks in
// this process. Never retry a 429 by walking keys or switching models.
const providerRateLimit = new ProviderRateLimit()
export function openRouterCapacityAvailable(keys: readonly string[]): boolean {
  return providerRateLimit.remaining() === 0 && keys.some(key=>!keyIsCooled(key))
}
function checkProviderCooldown(): void {
  const remaining = providerRateLimit.remaining()
  if (remaining > 0) throw new FreeCapacityUnavailableError('Free provider is rate limited.', remaining)
}
function observeProvider429(response: Response, reason: string): never {
  providerRateLimit.observe(response.headers.get('retry-after'), reason)
  throw new FreeCapacityUnavailableError('Free provider is rate limited.', providerRateLimit.remaining())
}

async function extractError(response: Response): Promise<string> {
  try {
    const j = (await response.json()) as OrErrorBody
    return j?.error?.message || `HTTP ${response.status}`
  } catch {
    return `HTTP ${response.status}`
  }
}

/** Some OpenRouter endpoints force reasoning and reject {enabled:false}. */
function reasoningMandatory(error: string): boolean {
  return /reasoning is mandatory/i.test(error)
}

/** Non-streaming OpenRouter completion: key-pool rotation × model chain. */
export async function orCompleteChat(
  messages: ChatMessageInput[],
  models: string[],
  reasoning?: OrReasoningConfig
): Promise<{ text: string; model: string | null }> {
  const keys = (await loadKeyPool()).keys
  if (keys.length === 0) {
    throw new FreeCapacityUnavailableError(
      'GS Free is waiting: the OpenRouter key pool is empty. Re-provision keys to restore free-tier models.'
    )
  }
  checkProviderCooldown()
  if (keys.every(keyIsCooled)) throw new FreeCapacityUnavailableError('Free credentials are cooling down.')
  let lastError = 'OpenRouter unavailable'
  let sawCapacityFailure = false

  for (let mi = 0; mi < models.length; mi++) {
    const model = models[mi]
    const isLastModel = mi === models.length - 1
    let emptyAttempts = 0
    for (let i = 0; i < keys.length; i++) {
      const key = keys[(keyCursor + i) % keys.length]
      if (keyIsCooled(key)) {
        console.log(`OR-COOLDOWN model=${model} key=***${key.slice(-4)} skip (quota cooldown)`)
        continue
      }
      try {
        let response = await postChat(key, model, messages, false, reasoning)
        if (!response.ok) {
          lastError = await extractError(response)
          if (reasoningMandatory(lastError) && reasoning?.enabled !== true) {
            // Last-resort endpoint that refuses flash: let it think rather
            // than returning a 502. The UI will show the real reasoning event
            // if the provider emits one.
            response = await postChat(key, model, messages, false, { enabled: true })
            lastError = response.ok ? '' : await extractError(response)
          }
          if (response.ok) {
            // The reasoning-mandatory retry succeeded — fall through to the
            // success path below instead of misreading it as a failure (this
            // exact bug surfaced as "OpenRouter openrouter/free: " with an
            // empty message and a 502 to the client).
          } else if (shouldRotate(response.status)) {
            if (response.status === 429) observeProvider429(response, lastError)
            if (response.status === 401 || response.status === 402) coolKey(key, lastError)
            if (response.status !== 401) sawCapacityFailure = true
            console.log(`OR-ROTATE model=${model} status=${response.status} key=***${key.slice(-4)}`)
            continue
          } else {
            // Permanent for THIS model (400/404): fall through to the next
            // model in the chain; only surface after the last one.
            lastError = `OpenRouter ${model}: ${lastError}`
            if (isLastModel) throw new Error(lastError)
            break
          }
        }
        const completion = (await response.json()) as {
          choices?: { message?: { content?: unknown } }[]
          model?: unknown
        }
        keyCursor = (keyCursor + i) % keys.length
        const text = completion?.choices?.[0]?.message?.content
        if (typeof text !== 'string' || text.trim().length === 0) {
          // PHASE 8.1b — 200-with-empty-content is a capacity hiccup: rotate.
          // (Otherwise a blank bubble gets persisted as a real answer.)
          // 2026-11-24 — bounded: after MAX_EMPTY_ATTEMPTS_PER_MODEL the model
          // is exhausted; fall to the next model instead of walking all 36 keys.
          lastError = 'empty completion from upstream'
          emptyAttempts += 1
          if (emptyAttempts >= MAX_EMPTY_ATTEMPTS_PER_MODEL && !isLastModel) break
          continue
        }
        return {
          text,
          model: typeof completion?.model === 'string' ? completion.model : null,
        }
      } catch (e) {
        if (e instanceof FreeCapacityUnavailableError) throw e
        lastError = e instanceof Error ? e.message : String(e)
        if (lastError.startsWith('OpenRouter ') && isLastModel) throw e
        if (lastError.startsWith('OpenRouter ')) break // next model in chain
      }
    }
  }
  if (sawCapacityFailure) {
    throw new FreeCapacityUnavailableError(`OpenRouter free capacity is exhausted: ${lastError}`)
  }
  throw new Error(`OpenRouter ${models.join(' | ')}: ${lastError}`)
}

/**
 * Streaming OpenRouter completion: key-pool rotation × model chain. Same
 * semantics as streamChat: deltas go to `onDelta`; a mid-stream failure after
 * output was forwarded throws (restarting would duplicate text on the client).
 */
export async function orStreamChat(
  messages: ChatMessageInput[],
  models: string[],
  onDelta: (t: string) => Promise<void> | void,
  reasoning?: OrReasoningConfig,
  onReasoning?: (t: string) => void,
  onModel?: (model:string|null)=>void
): Promise<string> {
  const keys = (await loadKeyPool()).keys
  if (keys.length === 0) {
    throw new FreeCapacityUnavailableError(
      'GS Free is waiting: the OpenRouter key pool is empty. Re-provision keys to restore free-tier models.'
    )
  }
  checkProviderCooldown()
  if (keys.every(keyIsCooled)) throw new FreeCapacityUnavailableError('Free credentials are cooling down.')
  let lastError = 'OpenRouter unavailable'
  let sawCapacityFailure = false

  for (let mi = 0; mi < models.length; mi++) {
    const model = models[mi]
    const isLastModel = mi === models.length - 1
    let emptyAttempts = 0
    for (let i = 0; i < keys.length; i++) {
      const key = keys[(keyCursor + i) % keys.length]
      if (keyIsCooled(key)) {
        console.log(`OR-COOLDOWN model=${model} key=***${key.slice(-4)} skip (quota cooldown)`)
        continue
      }
      let forwarded = false
      try {
        let response = await postChat(key, model, messages, true, reasoning)
        if (!response.ok) {
          lastError = await extractError(response)
          if (reasoningMandatory(lastError) && reasoning?.enabled !== true) {
            response = await postChat(key, model, messages, true, { enabled: true })
            lastError = response.ok ? '' : await extractError(response)
          }
          if (response.ok) {
            // reasoning-mandatory retry succeeded — continue into the stream
            // below (falling through to the failure branch here produced the
            // bogus "OpenRouter <model>: " 502).
          } else if (shouldRotate(response.status)) {
            if (response.status === 429) observeProvider429(response, lastError)
            if (response.status === 401 || response.status === 402) coolKey(key, lastError)
            if (response.status !== 401) sawCapacityFailure = true
            console.log(`OR-ROTATE model=${model} status=${response.status} key=***${key.slice(-4)}`)
            continue
          } else {
            lastError = `OpenRouter ${model}: ${lastError}`
            if (isLastModel) throw new Error(lastError)
            break // next model in chain
          }
        }
        if (!response.body) {
          lastError = 'OpenRouter returned no body'
          continue
        }
        keyCursor = (keyCursor + i) % keys.length
        const guardedOnDelta = (t: string) => {
          forwarded = true
          return onDelta(t)
        }
        let reportedModel:string|null=null
        const full = await consumeSseStream(response.body,guardedOnDelta,m=>{reportedModel=m},onReasoning)
        if (full.trim().length === 0 && !forwarded) {
          // PHASE 8.1b — nothing reached the client yet: safe to rotate.
          // 2026-11-24 — bounded like the non-stream walker below/above.
          lastError = 'empty completion from upstream'
          emptyAttempts += 1
          if (emptyAttempts >= MAX_EMPTY_ATTEMPTS_PER_MODEL && !isLastModel) break
          continue
        }
        onModel?.(reportedModel)
        return full
      } catch (e) {
        if (e instanceof FreeCapacityUnavailableError) throw e
        lastError = e instanceof Error ? e.message : String(e)
        if (forwarded) throw new Error(lastError)
        if (lastError.startsWith('OpenRouter ') && isLastModel) throw e
        if (lastError.startsWith('OpenRouter ')) break // next model in chain
        // rotate to next key after a brief pause
        if (i < keys.length - 1) await sleep(300)
      }
    }
  }
  if (sawCapacityFailure) {
    throw new FreeCapacityUnavailableError(`OpenRouter free capacity is exhausted: ${lastError}`)
  }
  throw new Error(`OpenRouter ${models.join(' | ')}: ${lastError}`)
}

/** Diagnostic: pool size + masked fingerprints (never full keys). */
export async function orPoolStatus(): Promise<{ size: number; fingerprints: string[] }> {
  const keys = (await loadKeyPool()).keys
  return { size: keys.length, fingerprints: keys.map(maskKey) }
}

// ---------------------------------------------------------------------------
// HEALTH PROBE (outage 2026-10-05, ITEM 2) — one cheap upstream call that
// proves the OpenRouter send path is alive with the CURRENT pool. Any HTTP
// 200 with a parseable completion body counts as healthy: health measures
// the PIPE (auth + network + model serving), not output quality. max_tokens=1
// bounds cost; up to 3 distinct keys are tried so one cooled/dead key cannot
// produce a false negative for a pool that otherwise works. Key material is
// never logged or returned (last-4 fingerprints only, via loadKeyPool's own
// masking).
// ---------------------------------------------------------------------------

export type OrProbeResult = {
  /** false when the pool is empty — nothing was probed (layer offline). */
  probed: boolean
  ok: boolean
  /** Last HTTP status observed (null when the request never got a response). */
  status: number | null
  error: string | null
  latencyMs: number
}

const HEALTH_PROBE_TIMEOUT_MS = 10_000
const HEALTH_PROBE_MAX_KEYS = 3

export async function orHealthProbe(): Promise<OrProbeResult> {
  const t0 = Date.now()
  const keys = (await loadKeyPool()).keys
  if (keys.length === 0) {
    return { probed: false, ok: false, status: null, error: 'keypool empty', latencyMs: 0 }
  }
  if (providerRateLimit.remaining() > 0 || keys.every(keyIsCooled))
    return { probed:false, ok:false, status:429, error:'Free provider cooling down', latencyMs:0 }
  const model = OPENROUTER_MODELS['gs-free'][0]
  let lastError = 'OpenRouter unavailable'
  let lastStatus: number | null = null
  for (let i = 0; i < Math.min(HEALTH_PROBE_MAX_KEYS, keys.length); i++) {
    const key = keys[(keyCursor + i) % keys.length]
    if (keyIsCooled(key)) continue
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), HEALTH_PROBE_TIMEOUT_MS)
    try {
      const response = await fetch(`${OPENROUTER_BASE}/chat/completions`, {
        method: 'POST',
        signal: controller.signal,
        headers: {
          Authorization: `Bearer ${key}`,
          'Content-Type': 'application/json',
          'HTTP-Referer': 'https://grapsee.agency',
          'X-Title': 'GS AI',
        },
        body: JSON.stringify({
          model,
          max_tokens: 1,
          messages: [{ role: 'user', content: 'ping' }],
        }),
      })
      lastStatus = response.status
      if (response.ok) {
        return { probed: true, ok: true, status: 200, error: null, latencyMs: Date.now() - t0 }
      }
      lastError = await extractError(response)
      if (response.status === 429) {
        providerRateLimit.observe(response.headers.get('retry-after'), lastError)
        break
      }
      // 400/404 are permanent for this request shape — rotating keys cannot
      // fix them; report the failure as the layer's real state.
      if (!shouldRotate(response.status)) break
    } catch (e) {
      lastError = e instanceof Error ? e.message : String(e)
    } finally {
      clearTimeout(timer)
    }
  }
  return {
    probed: true,
    ok: false,
    status: lastStatus,
    error: lastError.slice(0, 200),
    latencyMs: Date.now() - t0,
  }
}

/** Native remote function-call adapter. Deliberately excludes the auto-router:
 * never send a paid or unverified implicit route a tool-enabled request.
 * One bounded request per round; failed compatibility falls back to existing
 * planner evidence, not a long key walk. The body is covered by the deadline. */
export async function orRequestTools(
  messages: RemoteMessage[], model: string, signal: AbortSignal
): Promise<RemoteCompletion> {
  if (!model.endsWith(':free') || !OPENROUTER_MODELS['gs-free-big'].includes(model))
    throw new Error('No eligible free tool route')
  const keys = (await loadKeyPool()).keys
  checkProviderCooldown()
  const key = keys.find(k => !keyIsCooled(k))
  if (!key) throw new Error('No free tool capacity')
  const response = await fetch(`${OPENROUTER_BASE}/chat/completions`,{
    method:'POST', signal, headers:{Authorization:`Bearer ${key}`,'Content-Type':'application/json','X-Title':'GS AI'},
    body:JSON.stringify({model,messages,stream:false,temperature:0.2,max_tokens:2048,
      tools:REMOTE_TOOLS,tool_choice:'auto',provider:{require_parameters:true}})
  })
  if (!response.ok) {
    const reason = await extractError(response)
    if (response.status===429) observeProvider429(response,reason)
    if (response.status===402 || response.status===401) coolKey(key,reason)
    throw new Error('Free tool request unavailable')
  }
  const result = await response.json() as RemoteCompletion
  if (result.model && result.model !== model) throw new Error('Unexpected tool model')
  return result
}
