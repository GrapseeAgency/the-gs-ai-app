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
import { DESIGN_RULEBOOK } from './design/rulebook'

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

/**
 * PHASE 8.3 (user directive: "STOP USING THE SYSTEM PROMPT AS THE
 * APPLICATION BRAIN") — stripped to the operator's five-clause contract
 * (2026-10-14 directive, TASK 4). Identity, the request, tool integrity,
 * citation integrity, voice. Nothing else: no behaviour rules, no
 * formatting instructions, no hedge language, no self-limitation copy.
 * Every "do not" that is not integrity-critical is gone — the renderer
 * owns formatting, the scaffold owns feeding the model correctly.
 *
 * Word count: 33 (was 98).
 */
export const SYSTEM_PROMPT = `You are GS. Answer the user's actual request. Use the tools the application provides and do not invent tool results. Cite sources when you use retrieved evidence. Match the user's language and register. Never use emoji anywhere in your replies. Write plain prose: prefer commas, colons and parentheses over em dashes.`

/**
 * GENUI widget contract (docs/GENUI.md). The provider has no tool-calling, so
 * interactive rendering is prompt-driven: the model embeds widget fences in the
 * markdown stream and the clients render them natively (chart/stat/table/
 * compare/progress/timeline). Hard rules keep this safe:
 *
 *  - a widget SUPPLEMENTS the prose around it, never replaces it;
 *  - the body is strict JSON, double-quoted, no comments, no trailing commas;
 *  - a widget only ever contains data the answer already established — it is a
 *    visualisation of said facts, never a place for new claims;
 *  - at most 3 widgets per answer, only when genuinely useful.
 *
 * Invalid payloads degrade to code blocks on the client, so a malformed widget
 * costs one ugly block, not the answer.
 */
export const WIDGET_PROMPT = `${DESIGN_RULEBOOK}

HOW THIS WORKS
You are not filling in a template. You compose an answer from primitives, and
you decide the layout, the accent and the surface every time. A widget is a
fenced block: the fence language is the kind, the body is strict JSON. The app
renders it natively, inline, in the conversation.

Choose the shape of the answer BEFORE choosing widgets:
1. What is the user actually going to do with this? Read it, compare it, explore
   it, change a number, or follow steps?
2. Pick the smallest set of primitives that serves that.
3. Give the lead visual an accent that fits the subject, and vary it across the
   reply. Two adjacent blocks never share an accent or a surface.

Every widget accepts two optional keys, and using them is the difference
between "a card" and "designed":
- "accent":"#hex"      the hue for this block (pick from the data hues above)
- "variant":"plain|tinted|hero|outline"  the surface (tinted for charts, hero for
  a KPI, outline for quiet lists, plain for dense tables)

Available kinds (fenced block, language = kind, body = strict JSON):
- chart: {"type":"line|area|stackedArea|bar|hbar|stacked|grouped|pie|donut|scatter|bubble|histogram|boxplot|heatmap|correlation|treemap|waterfall|candlestick|gantt|funnel|radar|chord","title":"...","unit":"...","labels":["..."],"series":[{"name":"...","data":[[x,y],...]}]}  (pie/donut: one series per slice with a single point; funnel/radar: labels + one series of values; scatter/bubble: [x,y] or [x,y,z]; grouped: several series side by side; boxplot: raw samples per series; heatmap/correlation: one series per row, data [col,value] (correlation values -1..1); treemap/waterfall: one series of values + labels; candlestick: [open,high,low,close] per point; gantt: one series, each point [start,end] + labels as task names)
- stat: {"title":"...","cards":[{"label":"...","value":"...","unit":"...","delta":"...","direction":"up"|"down"|"flat"}]}
- table: {"title":"...","columns":["..."],"rows":[["...","..."]],"sortable":true}
- compare: {"title":"...","left":{"title":"...","items":["..."]},"right":{"title":"...","items":["..."]}}
- progress: {"title":"...","items":[{"label":"...","percent":0-100}]}
- timeline: {"title":"...","entries":[{"date":"...","title":"...","detail":"..."}]}
- gauge: {"title":"...","items":[{"label":"...","value":N,"max":M,"unit":"%"}]}
- gauge-style progress meters per metric.
- quiz: {"title":"...","questions":[{"q":"...","options":["..."],"answer":index,"explain":"..."}]}  (real tap-to-answer quiz rendered in the chat)
- form: {"title":"...","submitLabel":"...","fields":[{"type":"text|textarea|select|radio|checkbox|slider|toggle|date|segmented","label":"...","options":["..."],"required":true|false,"min":N,"max":M,"value":N,"unit":"..."}]}
- carousel: {"title":"...","items":[{"image":"https://...","caption":"..."}]}
- cards: {"title":"...","cards":[{"title":"...","subtitle":"...","prompt":"..."}]}  (a tap sends the prompt back)
- followups: {"title":"...","prompts":["..."]}  (tap-to-send next prompts)
- media: {"type":"image|gallery|video|audio|code|file|link|citation|search|entity|product|listing|doc","title":"...","items":[{"url":"https://...","thumb":"https://...","title":"...","subtitle":"...","caption":"...","meta":"...","prompt":"...","domain":"...","snippet":"...","price":"...","badge":"...","rating":"...","mime":"..."}],"code":"...","language":"...","text":"..."}  (images/galleries load from https; video plays inline from a direct https mp4/webm URL, audio plays inline from a direct https mp3/m4a/ogg URL; code is editable + runnable when JavaScript/HTML and copyable otherwise; doc previews PDFs page-by-page, images and text in-chat; product/listing/entity \`prompt\` sends on tap; citations and search results are tappable)
- actions: {"title":"...","buttons":[{"label":"...","kind":"prompt|copy|open","value":"...","style":"filled|tonal|text"}]}  (real buttons; prompt sends value, copy copies it, open launches the URL)
- panel: {"title":"...","summary":"...","body":"...","expanded":true|false,"badges":[{"label":"...","tone":"accent|success|warning|danger"}],"popover":"..."}  (tap the header to expand/collapse; badges are inline chips; non-blank popover adds a "More info" reveal)
- voice: {"title":"...","prompt":"...","hint":"..."}  (a mic card that opens the app's voice mode; use it when the user asks to talk instead of type)
- map: {"title":"...","center":[lat,lng],"zoom":N,"pins":[{"lat":N,"lng":N,"label":"...","detail":"..."}],"route":[[lat,lng],...],"mode":"driving|walking|cycling","stops":[{"lat":N,"lng":N,"label":"..."}],"steps":[{"instruction":"...","distance":"1.2 km"}],"cluster":true|false,"cards":true|false,"compare":[{"title":"...","center":[lat,lng],"zoom":N,"pins":[...],"route":[...]}]}  (interactive map with pins/routes. Give \`mode\` + 2 or more \`stops\` for real routing with turn-by-turn directions; \`route\` is only a fallback polyline. \`cluster\` groups dense pins; \`cards\` adds a tappable place carousel under the map; \`compare\` renders 2-4 maps side by side — do not use \`compare\` together with top-level \`pins\`)
- mermaid: a fenced block with mermaid source — flowchart, sequence, gantt, class, state, ER, journey, mindmap, pie, timeline. Use this for ANY diagram request.
- calc: {"type":"unit|currency|finance|loan|mortgage|pricing|tax|budget|investment|probability|statistics|scientific|datetime","title":"...","fields":[{"key":"p","label":"Principal","value":250000,"unit":"$","min":0,"max":1000000,"step":1000,"choices":[{"label":"USD","value":1}]}],"outputs":[{"label":"Monthly payment","expr":"p*r*pow(1+r,n)/(pow(1+r,n)-1)","unit":"$","format":"money","primary":true}],"note":"...","presets":[{"label":"30-year","values":{"n":360}}]}  (a real calculator. \`fields\` become sliders, or segmented chips when \`choices\` is present. Each output \`expr\` is evaluated live with the field keys as variables; supported: + - * / % ^, parentheses, pi/e, sqrt abs ln log10 exp sin cos tan floor ceil round min max pow clamp. Use this for EVERY calculator/converter request instead of html.)
- app: {"type":"wizard|onboarding|kanban|calendar|sheet|tasks|dashboard|config|configurator|booking|schedule|search|filter|analytics|report|files|connected|decision|scenario","title":"...","steps":[{"title":"...","body":"...","fields":[{"type":"text|textarea|select|radio|slider","label":"...","options":["..."]}]}],"columns":[{"title":"...","items":[{"title":"...","subtitle":"...","badge":"..."}]}],"items":[{"title":"...","subtitle":"...","meta":"...","badge":"...","prompt":"...","date":"2026-10-20","price":0}],"groups":[{"title":"...","options":[{"label":"...","detail":"...","price":0}]}],"rows":[["Header","Header"],["a","1"]],"slots":["09:00","10:00"],"month":"2026-10","submitLabel":"..."}  (native app surfaces with real state: wizard/onboarding steps with per-step fields, kanban boards you tap to advance, a month calendar with event dots, an editable sheet with column sums, task checklists, dashboard metric tiles, priced configurators, booking/scheduling slot pickers, live search and facet filters. Use this for app-style requests instead of html.)
- html: a fenced block with ONE self-contained HTML document (inline CSS+JS; CDN only for map tiles/everything else inline). The app renders it in a live interactive view. Reserve it for genuinely custom experiences that no other kind covers — prototypes, bespoke visualisations, embedded video/audio. Prefer calc/app/chart/table/form/media when one of them fits. Keep it under 200KB, mobile-sized (no fixed pixel widths wider than the screen), and set <body data-gs-height="Npx"> when a specific height suits the content.

When the user asks for a chart, graph, plot, diagram, table or any visual ("make a chart", "visualise this", "surprise me visually"), you MUST answer with at least one widget. You CAN create visualizations in this chat — never say you cannot, never answer a visual request with prose only. If you catch yourself writing "I'm unable to create or display visualizations", "as a text-based AI", "I don't have the ability to generate charts", or "you could build this in Excel", stop: those sentences are FALSE in this client, which renders every kind above natively. Emit the widget fence instead.
- If the answer states the data, chart exactly that.
- If the user asks for a visual with no data in play (e.g. "surprise me"), build an illustrative dataset yourself, keep it plausible and small, and say plainly in the prose that the numbers are illustrative, not measured.

BESPOKE WORK
The kinds above are the common shapes, not the limit. When the request needs
something no kind covers — a playable game, a simulation, a custom layout, an
animated explainer, a bespoke diagram — emit a fenced html block and build it
yourself with inline CSS and JavaScript. It runs in a live sandbox in the app.
Do not force a bespoke request into a native kind that nearly fits; that is how
answers end up looking generic. Follow the rulebook there too: the data hues,
the type scale, the spacing scale, and the same dark-mode rules.

Never draw ASCII art, never draw bar charts with box characters, never put data in a plain code fence. Charts, tables and timelines exist ONLY as widget fences; a plain fence is for source code and nothing else.

Rules: keep the surrounding prose — a widget supplements the answer, never replaces it. Prefer 1-3 widgets per answer and only when they genuinely help. If you are unsure the JSON is valid, write prose instead.`

/**
 * PHASE 7 vision grounding, REWRITTEN by the operator's 2026-10-14 directive
 * (TASK 3). The old rule 2 literally taught the model to hedge ("mark it as
 * such ('appears to be', 'resembles')") — with a flash model that instruction
 * is the whole failure: every identification arrived padded as "likely a
 * couch", "appear to be candies". The rewritten contract is the opposite:
 * read what is actually visible, quote image text verbatim when asked, refuse
 * plainly when the image cannot be read, and never pad. Grounding stays;
 * hedge-teaching is deleted.
 */
export const VISION_GROUNDING_PROMPT = `When this conversation includes attached images, ground every answer in them. The images are primary evidence: state what they actually show, and when the user asks about text in an image, quote that text verbatim. Never invent details that are not visibly present. If the image does not contain the answer, say so plainly and name what is missing. If something cannot be identified from the image, describe what is visible and say it cannot be identified. Do not use "likely", "appears to be", "possibly" or similar qualifiers: either the image identifies it or it does not. Read text exactly as written; if text cannot be read reliably, say which text is unreadable and offer no candidate readings or brand guesses.`

/**
 * TASK 3 vision input contract — the exact prefix the operator specified for
 * the OCR fallback path. Applied ONLY when the image bytes cannot be sent to
 * the vision model (storage missing / corrupt / unsupported format) and an
 * on-device OCR sidecar exists. It tells the model the text is complete
 * content, not a description, so it reads instead of guessing.
 */
export const VISION_OCR_FALLBACK_PREFIX = `The following is OCR text extracted from the user's image. It is complete text content, not a description.`

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

/** The provider's thinking trace (`choices[0].delta.reasoning_content`, the
 * glm/zai stream shape for enabled thinking). Empty for every non-thinking
 * response, so callers can wire it unconditionally. */
function extractReasoningDelta(parsed: unknown): string {
  const p = parsed as
    | {
        choices?: {
          delta?: { reasoning_content?: unknown; reasoning?: unknown }
          message?: { reasoning_content?: unknown }
        }[]
      }
    | null
  if (!p) return ''
  const r =
    p.choices?.[0]?.delta?.reasoning_content ??
    p.choices?.[0]?.delta?.reasoning ??
    p.choices?.[0]?.message?.reasoning_content ??
    ''
  return typeof r === 'string' ? r : ''
}

/**
 * Consume the SDK's raw SSE byte stream, forwarding each text delta to
 * `onDelta` and returning the accumulated full text.
 */
export async function consumeSseStream(
  body: ReadableStream<Uint8Array>,
  onDelta: (t: string) => Promise<void> | void,
  onModel?: (model: string) => void,
  onReasoning?: (t: string) => void
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
      // REASONING TRACE — thinking-enabled calls stream the model's reasoning
      // separately from the answer; carried out when a listener is wired
      // (turn-executor forwards it to clients as `reasoning` events).
      if (onReasoning) {
        const r = extractReasoningDelta(parsed)
        if (r) await onReasoning(r)
      }
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

/**
 * PHASE 8.1 — account-level throttle circuit breaker. The 2026-09-20 window
 * proved the primary provider can 429 for HOURS; without a breaker every turn
 * pays the full doomed-retry latency (~30s) before any OpenRouter fallback
 * fires. When a 429 is seen, all primary calls short-circuit for 10 minutes.
 * The breaker is per-process state — restarts simply re-probe.
 */
let zaiThrottledUntil = 0

export function zaiThrottleActive(): boolean {
  return Date.now() < zaiThrottledUntil
}

function markZaiThrottled(): void {
  zaiThrottledUntil = Date.now() + 10 * 60_000
  console.log('[ZAI-BREAKER] 429 observed — primary provider calls short-circuited for 10 minutes')
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function errMessage(e: unknown): string {
  return e instanceof Error ? e.message : String(e)
}

/**
 * THINKING TIER (flash-mode task, Phase 2) — per-call thinking config.
 *  - { mode: 'flash' }               → { thinking: { type: 'disabled' } }
 *  - { mode: 'thinking', effort? }   → { thinking: { type: 'enabled' },
 *                                       reasoning_effort?: effort }
 *  - null / undefined                → legacy behavior: disabled (the exact
 *                                      pre-flash-mode wire body).
 */
export type ThinkingCallConfig = { mode: 'flash' | 'thinking'; effort?: string } | null

function zaiThinkingBody(thinking: ThinkingCallConfig): Record<string, unknown> {
  if (thinking?.mode === 'thinking') {
    return thinking.effort
      ? { thinking: { type: 'enabled' }, reasoning_effort: thinking.effort }
      : { thinking: { type: 'enabled' } }
  }
  return { thinking: { type: 'disabled' } }
}

/**
 * Streaming chat completion. Calls `onDelta` for every token/chunk received
 * and resolves with the complete assistant text.
 *
 * PHASE 8.1 — `providerModel` (from the gs-* catalogue mapping) is sent when
 * present. If the provider rejects it as invalid/unknown (HTTP 400/404), the
 * loop retries modelless (the exact pre-8.1 behavior) — a bad mapping can
 * degrade to the default model, never break chat. Account-level 429s are
 * NOT retried modelless (same quota pool either way).
 *
 * FLASH MODE (Phase 2) — `thinking` carries the turn's resolved mode; the
 * provider body's `thinking` field is built from it (null = legacy disabled).
 */
export async function streamChat(
  messages: ChatMessageInput[],
  onDelta: (t: string) => Promise<void> | void,
  providerModel?: string | null,
  thinking?: ThinkingCallConfig,
  onReasoning?: (t: string) => void
): Promise<string> {
  let lastError = 'Upstream unavailable'
  let activeModel = typeof providerModel === 'string' && providerModel.length > 0 ? providerModel : null

  for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
    let forwarded = false
    try {
      if (zaiThrottleActive()) throw new Error('z-ai throttled (circuit open)')
      const zai = await ZAI.create()
      const body: Record<string, unknown> = {
        messages: toSdkMessages(messages),
        stream: true,
        ...zaiThinkingBody(thinking ?? null),
        // FORENSIC AUDIT [25] — consistency lever (ignored if the endpoint
        // does not accept it; never breaks the request).
        temperature: 0.2,
      }
      if (activeModel) body.model = activeModel
      const response: unknown = await Promise.race([
        zai.chat.completions.create(body as never),
        new Promise((_, reject) =>
          setTimeout(() => reject(new Error('Upstream connect timeout')), CONNECT_TIMEOUT_MS)
        ),
      ])

      if (isWebReadableStream(response)) {
        const guardedOnDelta = (t: string) => {
          forwarded = true
          return onDelta(t)
        }
        return await consumeSseStream(response, guardedOnDelta, undefined, onReasoning)
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
      if (/429|Too many requests/i.test(lastError)) markZaiThrottled()
      // PHASE 8.1 — mapped model rejected by the provider (invalid/unknown):
      // drop the model key and retry modelless immediately.
      if (activeModel && /status\s+(400|404)\b/.test(lastError)) {
        activeModel = null
        continue
      }
      // Mid-stream failure after output was already forwarded: restarting
      // would duplicate text on the client — surface the error instead.
      if (forwarded) throw new Error(lastError)
      if (attempt < MAX_ATTEMPTS) await sleep(400 * attempt)
    }
  }
  throw new Error(lastError)
}

/** Non-streaming chat completion. Resolves with the assistant text. */
export async function completeChat(
  messages: ChatMessageInput[],
  providerModel?: string | null,
  thinking?: ThinkingCallConfig
): Promise<string> {
  return (await completeChatWithMeta(messages, providerModel, thinking)).text
}

/**
 * PHASE 6 — non-streaming completion with serving-model metadata. Resolves
 * with the assistant text and the serving model id.
 *
 * MODEL SELECTION: the `model` key is OMITTED by default, exactly like the
 * SDK's own vision CLI — the endpoint then serves its compatible default
 * (observed live as `glm-5v-turbo` on the vision path). PHASE 8.1 adds the
 * optional `providerModel` for the TEXT path (gs-* catalogue mapping): sent
 * when present; on an invalid/unknown-model rejection (HTTP 400/404) the loop
 * retries modelless, so a wrong mapping degrades to the default, never breaks
 * chat. Account-level 429s are NOT retried modelless.
 */
export async function completeChatWithMeta(
  messages: ChatMessageInput[],
  providerModel?: string | null,
  thinking?: ThinkingCallConfig,
  // BENCH SCAFFOLD — optional sampling temperature (default 0.2 unchanged).
  // Additive-only: every existing caller keeps the exact previous behavior.
  temperature?: number
): Promise<{ text: string; model: string | null }> {
  let lastError = 'Upstream unavailable'
  let activeModel = typeof providerModel === 'string' && providerModel.length > 0 ? providerModel : null

  for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
    try {
      if (zaiThrottleActive()) throw new Error('z-ai throttled (circuit open)')
      const zai = await ZAI.create()
      const body: Record<string, unknown> = {
        messages: toSdkMessages(messages),
        stream: false,
        ...zaiThinkingBody(thinking ?? null),
        // FORENSIC AUDIT [25] — consistency lever (see streamChat).
        temperature: typeof temperature === 'number' && Number.isFinite(temperature) ? temperature : 0.2,
      }
      if (activeModel) body.model = activeModel
      const completion = (await Promise.race([
        zai.chat.completions.create(body as never),
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
      if (/429|Too many requests/i.test(lastError)) markZaiThrottled()
      // PHASE 8.1 — mapped model rejected by the provider: retry modelless.
      if (activeModel && /status\s+(400|404)\b/.test(lastError)) {
        activeModel = null
        continue
      }
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
