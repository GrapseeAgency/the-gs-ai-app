/**
 * Server-only AI wrapper around z-ai-web-dev-sdk.
 *
 * Do NOT import this module from client components — the SDK is
 * backend-only (see skills/LLM/SKILL.md).
 *
 * SDK shape (verified against node_modules/z-ai-web-dev-sdk/dist/index.d.ts):
 *   const zai = await ZAI.create()
 *   const res = await zai.chat.completions.create({ messages, stream?, thinking? })
 *
 * Streaming behaviour (verified in SDK source): when `stream: true` is set and
 * the upstream answers with an event-stream / text-plain content type, the SDK
 * returns the RAW Web ReadableStream (response.body) — the caller must parse
 * the SSE bytes itself. Otherwise a normal JSON completion object is returned.
 */

import ZAI from 'z-ai-web-dev-sdk'

type ChatRole = 'system' | 'user' | 'assistant'

export type ChatMessageInput = { role: string; content: string }

export const SYSTEM_PROMPT =
  "You are GS, Grapsee Agency's intelligent assistant. Warm, precise, editorial. Use clean markdown."

function toSdkMessages(messages: ChatMessageInput[]): { role: ChatRole; content: string }[] {
  return messages.map((m) => ({
    role: (m.role.toLowerCase() === 'assistant'
      ? 'assistant'
      : m.role.toLowerCase() === 'system'
        ? 'system'
        : 'user') as ChatRole,
    content: m.content,
  }))
}

function isWebReadableStream(value: unknown): value is ReadableStream<Uint8Array> {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as { getReader?: unknown }).getReader === 'function'
  )
}

/** Extract the text delta from one upstream SSE payload (defensive, OpenAI-ish shapes). */
function extractDelta(parsed: unknown): string {
  const p = parsed as
    | { choices?: { delta?: { content?: unknown }; message?: { content?: unknown } }[]; delta?: { content?: unknown } }
    | null
  if (!p) return ''
  const direct =
    p.choices?.[0]?.delta?.content ?? p.choices?.[0]?.message?.content ?? p.delta?.content ?? ''
  return typeof direct === 'string' ? direct : ''
}

/**
 * Consume the SDK's raw SSE byte stream, forwarding each text delta to
 * `onDelta` and returning the accumulated full text.
 */
async function consumeSseStream(
  body: ReadableStream<Uint8Array>,
  onDelta: (t: string) => Promise<void> | void
): Promise<string> {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let full = ''
  let terminated = false

  const handleLine = async (rawLine: string): Promise<boolean> => {
    const line = rawLine.trim()
    if (!line || line.startsWith(':')) return false // heartbeat / comment / empty
    let payload = line
    if (payload.startsWith('data:')) payload = payload.slice(5).trim()
    if (!payload) return false
    if (payload === '[DONE]') return true
    let delta = ''
    try {
      delta = extractDelta(JSON.parse(payload))
    } catch {
      // Non-JSON payload (plain-text chunk) — treat the whole payload as a delta.
      delta = payload
    }
    if (delta) {
      full += delta
      await onDelta(delta)
    }
    return false
  }

  while (!terminated) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() ?? '' // keep the trailing partial line in the buffer
    for (const line of lines) {
      if (await handleLine(line)) {
        terminated = true
        break
      }
    }
  }

  // Flush whatever is left in the buffer (upstream may omit the trailing newline).
  if (!terminated && buffer) await handleLine(buffer)

  return full
}

/**
 * Provider resilience:
 * - TIMEOUT: every upstream attempt is bounded (slow streams that have already
 *   produced output are NOT aborted mid-flight — only the initial connection
 *   is raced against the deadline).
 * - RETRY: non-streaming calls retry once on failure. Streaming calls retry
 *   only when the failure happens BEFORE the first delta was forwarded —
 *   restarting mid-stream would duplicate output for the consumer.
 */

const CONNECT_TIMEOUT_MS = 15_000
const COMPLETION_TIMEOUT_MS = 60_000
const MAX_ATTEMPTS = 2

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function errMessage(e: unknown): string {
  return e instanceof Error ? e.message : String(e)
}

/**
 * Streaming chat completion. Calls `onDelta` for every token/chunk received
 * and resolves with the complete assistant text.
 */
export async function streamChat(
  messages: ChatMessageInput[],
  onDelta: (t: string) => Promise<void> | void
): Promise<string> {
  let lastError = 'Upstream unavailable'

  for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
    let forwarded = false
    try {
      const zai = await ZAI.create()
      const response: unknown = await Promise.race([
        zai.chat.completions.create({
          messages: toSdkMessages(messages),
          stream: true,
          thinking: { type: 'disabled' },
        }),
        new Promise((_, reject) =>
          setTimeout(() => reject(new Error('Upstream connect timeout')), CONNECT_TIMEOUT_MS)
        ),
      ])

      if (isWebReadableStream(response)) {
        const guardedOnDelta = (t: string) => {
          forwarded = true
          return onDelta(t)
        }
        return await consumeSseStream(response, guardedOnDelta)
      }

      // Upstream ignored `stream: true` — fall back to a single full-text delta.
      const completion = response as { choices?: { message?: { content?: unknown } }[] } | null
      const text = completion?.choices?.[0]?.message?.content
      const full = typeof text === 'string' ? text : ''
      if (full) {
        forwarded = true
        await onDelta(full)
      }
      return full
    } catch (e) {
      lastError = errMessage(e)
      // Mid-stream failure after output was already forwarded: restarting
      // would duplicate text on the client — surface the error instead.
      if (forwarded) throw new Error(lastError)
      if (attempt < MAX_ATTEMPTS) await sleep(400 * attempt)
    }
  }
  throw new Error(lastError)
}

/** Non-streaming chat completion. Resolves with the assistant text. */
export async function completeChat(messages: ChatMessageInput[]): Promise<string> {
  let lastError = 'Upstream unavailable'

  for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
    try {
      const zai = await ZAI.create()
      const completion = (await Promise.race([
        zai.chat.completions.create({
          messages: toSdkMessages(messages),
          stream: false,
          thinking: { type: 'disabled' },
        }),
        new Promise((_, reject) =>
          setTimeout(() => reject(new Error('Upstream completion timeout')), COMPLETION_TIMEOUT_MS)
        ),
      ])) as { choices?: { message?: { content?: unknown } }[] } | null
      const text = completion?.choices?.[0]?.message?.content
      return typeof text === 'string' ? text : ''
    } catch (e) {
      lastError = errMessage(e)
      if (attempt < MAX_ATTEMPTS) await sleep(400 * attempt)
    }
  }
  throw new Error(lastError)
}
