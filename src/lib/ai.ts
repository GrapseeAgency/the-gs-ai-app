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

/**
 * PHASE 6 — vision message shapes, mirroring the SDK's VisionMessage /
 * VisionMultimodalContentItem contract (index.d.ts of z-ai-web-dev-sdk):
 * a user message content is either plain text or an array of typed parts
 * ({ type: 'text' } / { type: 'image_url', image_url: { url } }).
 */
export type VisionContentPart =
  | { type: 'text'; text: string }
  | { type: 'image_url'; image_url: { url: string } }

export type VisionChatMessage = {
  role: 'system' | 'user' | 'assistant'
  content: string | VisionContentPart[]
}

export const SYSTEM_PROMPT =
  "You are GS, Grapsee Agency's intelligent assistant. Warm, precise, editorial. Use clean markdown."

/**
 * PHASE 7 — vision grounding. Appended to the system prompt on VISION TURNS
 * ONLY (messages route, `useVision` branch). Text-only chat prompting is
 * deliberately left unchanged. It instructs the model to treat attached
 * images as primary evidence, separate observation from interpretation, admit
 * uncertainty rather than invent identifications, weigh (not adopt)
 * user-suggested identities, explicitly acknowledge a wrong earlier answer
 * when new evidence overturns it, and treat replayed assistant history as
 * claims to re-check against the image — not established facts.
 */
export const VISION_GROUNDING_PROMPT = `When this conversation includes attached images, ground every answer in them:

1. The attached images are your primary visual evidence. Answer from what they actually show. Never invent or assume details that are not visibly present.
2. Separate observation from interpretation. What is directly visible is fact; recognition and background knowledge are interpretation — mark it as such ("appears to be", "resembles").
3. If you cannot confidently identify a person, character, brand, product or place from the image alone, say so plainly (e.g. "I can't identify it confidently from the image alone"). An honest "I don't know" is always better than an invented name.
4. Do not accept an identity just because the user suggests it. Weigh their suggestion against what is actually visible; agree only if the visual evidence supports it, and say what matches or doesn't.
5. If genuinely new evidence changes your conclusion, correct yourself explicitly and acknowledge your earlier answer was wrong. Do not quietly rewrite it.
6. Earlier assistant messages are previous claims to be re-checked against the image, not established facts. When they conflict with what the image shows, trust the image and say so.`

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
  onDelta: (t: string) => Promise<void> | void,
  onModel?: (model: string) => void
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
      const parsed = JSON.parse(payload) as { model?: unknown }
      if (onModel && typeof parsed?.model === 'string' && parsed.model.length > 0) onModel(parsed.model)
      delta = extractDelta(parsed)
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
  return (await completeChatWithMeta(messages)).text
}

/**
 * PHASE 6 — non-streaming vision completion via the provider's real vision
 * endpoint. Resolves with the assistant text and the serving model id.
 *
 * MODEL SELECTION (§4/§12, proven empirically): the `model` key is OMITTED,
 * exactly like the SDK's own vision CLI — the vision endpoint then serves its
 * compatible default (observed live as `glm-5v-turbo` in the response `model`
 * field). Omitting keeps the pipeline resilient to gateway-side model
 * upgrades; the serving model is logged per request for forensics.
 */
export async function completeChatWithMeta(
  messages: ChatMessageInput[]
): Promise<{ text: string; model: string | null }> {
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
      ])) as { choices?: { message?: { content?: unknown } }[]; model?: unknown } | null
      const text = completion?.choices?.[0]?.message?.content
      return {
        text: typeof text === 'string' ? text : '',
        model: typeof completion?.model === 'string' ? completion.model : null,
      }
    } catch (e) {
      lastError = errMessage(e)
      if (attempt < MAX_ATTEMPTS) await sleep(400 * attempt)
    }
  }
  throw new Error(lastError)
}

/**
 * PHASE 6 — streaming vision completion. Same SSE wire, same timeout /
 * retry-before-first-delta semantics as [streamChat]; the request goes to the
 * provider's real vision endpoint with typed content parts (text + image
 * data URLs). The vision endpoint was probed to stream the same
 * OpenAI-ish `choices[0].delta.content` chunks.
 *
 * The `model` key is omitted from the body (cast required — the SDK's type
 * marks it required, but the SDK's own vision CLI omits it and the endpoint
 * serves its default; see docs/VISION.md for the audit evidence).
 */
export async function streamVisionChat(
  messages: VisionChatMessage[],
  onDelta: (t: string) => Promise<void> | void
): Promise<{ text: string; model: string | null }> {
  let lastError = 'Upstream unavailable'

  for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
    let forwarded = false
    try {
      const zai = await ZAI.create()
      const body = {
        messages,
        stream: true,
        thinking: { type: 'disabled' as const },
      }
      const response: unknown = await Promise.race([
        zai.chat.completions.createVision(
          // `model` deliberately omitted — CLI-parity; see doc comment.
          body as unknown as Parameters<typeof zai.chat.completions.createVision>[0]
        ),
        new Promise((_, reject) =>
          setTimeout(() => reject(new Error('Upstream connect timeout')), CONNECT_TIMEOUT_MS)
        ),
      ])

      if (isWebReadableStream(response)) {
        let servedModel: string | null = null
        const guardedOnDelta = (t: string) => {
          forwarded = true
          return onDelta(t)
        }
        const text = await consumeSseStream(response, guardedOnDelta, (m) => (servedModel = m))
        return { text, model: servedModel }
      }

      // Upstream ignored `stream: true` — fall back to a single full-text delta.
      const completion = response as {
        choices?: { message?: { content?: unknown } }[]
        model?: unknown
      } | null
      const text = completion?.choices?.[0]?.message?.content
      const full = typeof text === 'string' ? text : ''
      if (full) {
        forwarded = true
        await onDelta(full)
      }
      return {
        text: full,
        model: typeof completion?.model === 'string' ? completion.model : null,
      }
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

/** Non-streaming vision completion. */
export async function completeVisionChat(
  messages: VisionChatMessage[]
): Promise<{ text: string; model: string | null }> {
  let lastError = 'Upstream unavailable'

  for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
    try {
      const zai = await ZAI.create()
      const body = {
        messages,
        stream: false,
        thinking: { type: 'disabled' as const },
      }
      const completion = (await Promise.race([
        zai.chat.completions.createVision(
          // `model` deliberately omitted — CLI-parity; see doc comment.
          body as unknown as Parameters<typeof zai.chat.completions.createVision>[0]
        ),
        new Promise((_, reject) =>
          setTimeout(() => reject(new Error('Upstream completion timeout')), COMPLETION_TIMEOUT_MS)
        ),
      ])) as { choices?: { message?: { content?: unknown } }[]; model?: unknown } | null
      const text = completion?.choices?.[0]?.message?.content
      return {
        text: typeof text === 'string' ? text : '',
        model: typeof completion?.model === 'string' ? completion.model : null,
      }
    } catch (e) {
      lastError = errMessage(e)
      if (attempt < MAX_ATTEMPTS) await sleep(400 * attempt)
    }
  }
  throw new Error(lastError)
}
