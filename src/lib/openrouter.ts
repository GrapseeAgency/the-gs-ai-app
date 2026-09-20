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
 *  - Keys live ONLY in .env (gitignored). Never logged in full (last-4 only).
 */

import { consumeSseStream, type ChatMessageInput } from '@/lib/ai'

const OPENROUTER_BASE = 'https://openrouter.ai/api/v1'

// PHASE 8.1b — several free models carry strong self-branding from their own
// training (observed live: nex-n2.5-pro answered "I'm Nex, from Nex-AGI" to a
// "which AI are you" question despite the GS persona system prompt). This
// suffix is appended to the system prompt on every OpenRouter call so every
// model in the chain answers as GS.
const IDENTITY_PIN_SUFFIX =
  '\n\nIdentity: you are GS, the assistant built for Grapsee Agency. When asked who you are, answer as GS. Never claim to be built by, named after, or representing any other company, product, or model brand.'

const CONNECT_TIMEOUT_MS = 15_000
const COMPLETION_TIMEOUT_MS = 90_000

function loadKeyPool(): string[] {
  return (process.env.OPENROUTER_API_KEYS ?? '')
    .split(',')
    .map((k) => k.trim())
    .filter((k) => k.length > 0)
}

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

/** One POST attempt with a specific key. Returns the raw Response. */
async function postChat(
  key: string,
  model: string,
  messages: ChatMessageInput[],
  stream: boolean
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

async function extractError(response: Response): Promise<string> {
  try {
    const j = (await response.json()) as OrErrorBody
    return j?.error?.message ?? `HTTP ${response.status}`
  } catch {
    return `HTTP ${response.status}`
  }
}

/** Non-streaming OpenRouter completion: key-pool rotation × model chain. */
export async function orCompleteChat(
  messages: ChatMessageInput[],
  models: string[]
): Promise<{ text: string; model: string | null }> {
  const keys = loadKeyPool()
  if (keys.length === 0) throw new Error('OpenRouter keys not configured')
  let lastError = 'OpenRouter unavailable'

  for (let mi = 0; mi < models.length; mi++) {
    const model = models[mi]
    const isLastModel = mi === models.length - 1
    for (let i = 0; i < keys.length; i++) {
      const key = keys[(keyCursor + i) % keys.length]
      try {
        const response = await postChat(key, model, messages, false)
        if (!response.ok) {
          lastError = await extractError(response)
          if (shouldRotate(response.status)) continue
          // Permanent for THIS model (400/404): fall through to the next
          // model in the chain; only surface after the last one.
          lastError = `OpenRouter ${model}: ${lastError}`
          if (isLastModel) throw new Error(lastError)
          break
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
          lastError = 'empty completion from upstream'
          continue
        }
        return {
          text,
          model: typeof completion?.model === 'string' ? completion.model : null,
        }
      } catch (e) {
        lastError = e instanceof Error ? e.message : String(e)
        if (lastError.startsWith('OpenRouter ') && isLastModel) throw e
        if (lastError.startsWith('OpenRouter ')) break // next model in chain
      }
    }
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
  onDelta: (t: string) => Promise<void> | void
): Promise<string> {
  const keys = loadKeyPool()
  if (keys.length === 0) throw new Error('OpenRouter keys not configured')
  let lastError = 'OpenRouter unavailable'

  for (let mi = 0; mi < models.length; mi++) {
    const model = models[mi]
    const isLastModel = mi === models.length - 1
    for (let i = 0; i < keys.length; i++) {
      const key = keys[(keyCursor + i) % keys.length]
      let forwarded = false
      try {
        const response = await postChat(key, model, messages, true)
        if (!response.ok) {
          lastError = await extractError(response)
          if (shouldRotate(response.status)) continue
          lastError = `OpenRouter ${model}: ${lastError}`
          if (isLastModel) throw new Error(lastError)
          break // next model in chain
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
        const full = await consumeSseStream(response.body, guardedOnDelta)
        if (full.trim().length === 0 && !forwarded) {
          // PHASE 8.1b — nothing reached the client yet: safe to rotate.
          lastError = 'empty completion from upstream'
          continue
        }
        return full
      } catch (e) {
        lastError = e instanceof Error ? e.message : String(e)
        if (forwarded) throw new Error(lastError)
        if (lastError.startsWith('OpenRouter ') && isLastModel) throw e
        if (lastError.startsWith('OpenRouter ')) break // next model in chain
        // rotate to next key after a brief pause
        if (i < keys.length - 1) await sleep(300)
      }
    }
  }
  throw new Error(`OpenRouter ${models.join(' | ')}: ${lastError}`)
}

/** Diagnostic: pool size + masked fingerprints (never full keys). */
export function orPoolStatus(): { size: number; fingerprints: string[] } {
  const keys = loadKeyPool()
  return { size: keys.length, fingerprints: keys.map(maskKey) }
}
