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
 *  - Keys live ONLY in .env (gitignored). Never logged in full (last-4 only).
 */

import { consumeSseStream, type ChatMessageInput } from '@/lib/ai'

const OPENROUTER_BASE = 'https://openrouter.ai/api/v1'

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
        messages: messages.map((m) => ({
          role: (['assistant', 'system'].includes(m.role.toLowerCase())
            ? m.role.toLowerCase()
            : 'user') as 'assistant' | 'system' | 'user',
          content: m.content,
        })),
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

/** Non-streaming OpenRouter completion with key-pool rotation. */
export async function orCompleteChat(
  messages: ChatMessageInput[],
  model: string
): Promise<{ text: string; model: string | null }> {
  const keys = loadKeyPool()
  if (keys.length === 0) throw new Error('OpenRouter keys not configured')
  let lastError = 'OpenRouter unavailable'

  for (let i = 0; i < keys.length; i++) {
    const key = keys[(keyCursor + i) % keys.length]
    try {
      const response = await postChat(key, model, messages, false)
      if (!response.ok) {
        lastError = await extractError(response)
        if (shouldRotate(response.status)) continue
        throw new Error(`OpenRouter ${model}: ${lastError}`)
      }
      const completion = (await response.json()) as {
        choices?: { message?: { content?: unknown } }[]
        model?: unknown
      }
      keyCursor = (keyCursor + i) % keys.length
      const text = completion?.choices?.[0]?.message?.content
      return {
        text: typeof text === 'string' ? text : '',
        model: typeof completion?.model === 'string' ? completion.model : null,
      }
    } catch (e) {
      lastError = e instanceof Error ? e.message : String(e)
      if (lastError.startsWith('OpenRouter ')) throw e // permanent — surfaced
    }
  }
  throw new Error(`OpenRouter ${model}: ${lastError}`)
}

/**
 * Streaming OpenRouter completion. Same semantics as streamChat: deltas go
 * to `onDelta`; a mid-stream failure after output was forwarded throws
 * (restarting would duplicate text on the client).
 */
export async function orStreamChat(
  messages: ChatMessageInput[],
  model: string,
  onDelta: (t: string) => Promise<void> | void
): Promise<string> {
  const keys = loadKeyPool()
  if (keys.length === 0) throw new Error('OpenRouter keys not configured')
  let lastError = 'OpenRouter unavailable'

  for (let i = 0; i < keys.length; i++) {
    const key = keys[(keyCursor + i) % keys.length]
    let forwarded = false
    try {
      const response = await postChat(key, model, messages, true)
      if (!response.ok) {
        lastError = await extractError(response)
        if (shouldRotate(response.status)) continue
        throw new Error(`OpenRouter ${model}: ${lastError}`)
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
      return await consumeSseStream(response.body, guardedOnDelta)
    } catch (e) {
      lastError = e instanceof Error ? e.message : String(e)
      if (forwarded) throw new Error(lastError)
      if (lastError.startsWith('OpenRouter ')) throw e
      // rotate to next key after a brief pause
      if (i < keys.length - 1) await sleep(300)
    }
  }
  throw new Error(`OpenRouter ${model}: ${lastError}`)
}

/** Diagnostic: pool size + masked fingerprints (never full keys). */
export function orPoolStatus(): { size: number; fingerprints: string[] } {
  const keys = loadKeyPool()
  return { size: keys.length, fingerprints: keys.map(maskKey) }
}
