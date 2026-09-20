import { NextRequest, NextResponse } from 'next/server'
import { db } from '@/lib/db'
import { messageToJson } from '@/lib/serializers'
import { resolveModelRoute } from '@/lib/models'
import { orCompleteChat, orStreamChat } from '@/lib/openrouter'
import {
  SYSTEM_PROMPT,
  VISION_GROUNDING_PROMPT,
  SEARCH_FAILURE_DISCLOSURE_PROMPT,
  SEARCH_GROUNDING_PROMPT,
  streamChat,
  completeChat,
  streamVisionChat,
  completeVisionChat,
  type ChatMessageInput,
  type VisionChatMessage,
  type VisionContentPart,
} from '@/lib/ai'
import { clientKey, rateLimit } from '@/lib/rate-limit'
import { MAX_ATTACHMENTS_PER_MESSAGE } from '@/lib/attachments'
import {
  collectDocumentContext,
  documentFailureMessage,
  isDocumentAttachment,
  assembleDocumentUserTurn,
  DOCUMENT_ONLY_DEFAULT,
  DOC_HISTORY_TURNS,
} from '@/lib/document'
import {
  prepareVisionImage,
  visionFailureMessage,
  isProviderImageRejection,
  VISION_HISTORY_IMAGE_TURNS,
  VISION_MAX_IMAGES_PER_REQUEST,
  type PreparedVisionImage,
  type VisionImageFailure,
} from '@/lib/vision'
import {
  runWebSearch,
  evaluateWebSearchGate,
  buildSearchEvidenceBlock,
  buildHistoryEvidenceBlock,
  shouldInjectHistoryEvidence,
  parseCitedOrdinals,
  sanitizeCitationMarkers,
  searchFailureNote,
  SEARCH_HISTORY_TURNS,
  type SearchOutcome,
  type WebSource,
} from '@/lib/websearch'

export const dynamic = 'force-dynamic'

type RouteContext = { params: Promise<{ id: string }> }

const HISTORY_LIMIT = 20
const MAX_CONTENT_LENGTH = 32000 // shared-contracts SendMessageInput.maxLength
const DEFAULT_TITLE = 'New chat'

// GET /api/v1/conversations/:id/messages — history, oldest first
export async function GET(_req: NextRequest, { params }: RouteContext) {
  const { id } = await params
  const conversation = await db.conversation.findUnique({ where: { id } })
  if (!conversation) {
    return NextResponse.json({ code: 'not_found', message: 'Conversation not found' }, { status: 404 })
  }
  const messages = await db.message.findMany({
    where: { conversationId: id },
    orderBy: { createdAt: 'asc' },
    include: {
      attachments: { orderBy: { createdAt: 'asc' } },
      sources: { orderBy: { ordinal: 'asc' } },
    },
  })
  return NextResponse.json({ items: messages.map(messageToJson) })
}

// POST /api/v1/conversations/:id/messages — { content, stream?, modelId? }
export async function POST(req: NextRequest, { params }: RouteContext) {
  const { id } = await params

  // Guardrail: 20 messages / minute / client (in-memory; Redis at scale).
  const limit = rateLimit(clientKey(req, `msgs:${id}`), 20, 60_000)
  if (!limit.allowed) {
    return NextResponse.json(
      { code: 'rate_limited', message: 'Too many messages — slow down a little.' },
      { status: 429, headers: { 'Retry-After': String(limit.retryAfterSec) } }
    )
  }

  let body: { content?: unknown; stream?: unknown; modelId?: unknown; attachments?: unknown }
  try {
    body = (await req.json()) as { content?: unknown; stream?: unknown; modelId?: unknown; attachments?: unknown }
  } catch {
    return NextResponse.json(
      { code: 'bad_request', message: 'Request body must be valid JSON' },
      { status: 400 }
    )
  }

  // PHASE 5: optional attachment ids (uploaded via POST /api/v1/uploads first).
  const rawAttachments = body?.attachments
  const attachmentIds = Array.isArray(rawAttachments)
    ? rawAttachments.filter((v): v is string => typeof v === 'string' && v.trim().length > 0)
    : []
  if (Array.isArray(rawAttachments) && attachmentIds.length !== rawAttachments.length) {
    return NextResponse.json(
      { code: 'bad_request', message: 'attachments must be an array of attachment ids' },
      { status: 400 }
    )
  }
  if (attachmentIds.length > MAX_ATTACHMENTS_PER_MESSAGE) {
    return NextResponse.json(
      { code: 'bad_request', message: `At most ${MAX_ATTACHMENTS_PER_MESSAGE} attachments per message` },
      { status: 400 }
    )
  }

  const content = typeof body?.content === 'string' ? body.content : ''
  const attachmentsProvided = attachmentIds.length > 0
  if (content.trim().length === 0 && !attachmentsProvided) {
    return NextResponse.json(
      { code: 'bad_request', message: 'content must be a non-empty string' },
      { status: 400 }
    )
  }
  if (content.length > MAX_CONTENT_LENGTH) {
    return NextResponse.json(
      { code: 'bad_request', message: `content must not exceed ${MAX_CONTENT_LENGTH} characters` },
      { status: 400 }
    )
  }

  // Validate every attachment up-front: must exist, belong to this
  // conversation (or be unbound), and not already be attached to a message.
  let claimedAttachments: Awaited<ReturnType<typeof db.attachment.findMany>> = []
  if (attachmentsProvided) {
    claimedAttachments = await db.attachment.findMany({ where: { id: { in: attachmentIds } } })
    if (claimedAttachments.length !== attachmentIds.length) {
      return NextResponse.json(
        { code: 'invalid_attachments', message: 'One or more attachments do not exist' },
        { status: 400 }
      )
    }
    const foreign = claimedAttachments.find(
      (a) => (a.conversationId !== null && a.conversationId !== id) || a.messageId !== null
    )
    if (foreign) {
      return NextResponse.json(
        { code: 'invalid_attachments', message: 'One or more attachments are already in use' },
        { status: 400 }
      )
    }
  }

  // PHASE 6: image attachments on the CURRENT turn take the real vision path.
  // PHASE 7: document attachments (pdf/document) take the real extraction path
  // below — no more "contents not readable" contract for PDF/TXT/MD/CSV.
  // `useVision` itself is decided after history is loaded below — a text-only
  // follow-up must still reach the vision model when recent turns carry
  // images (§13 follow-up-without-re-uploading).
  const imageAttachments = claimedAttachments.filter((a) => a.kind === 'image')

  const conversation = await db.conversation.findUnique({
    where: { id },
    include: { assistant: true },
  })
  if (!conversation) {
    return NextResponse.json({ code: 'not_found', message: 'Conversation not found' }, { status: 404 })
  }

  const stream = body?.stream === true

  const requestedModelId =
    typeof body?.modelId === 'string' && body.modelId.trim().length > 0
      ? body.modelId.trim()
      : null
  // PHASE 8.1 — single routing decision: OpenRouter free tiers bypass the
  // primary provider's account-level quota; everything else stays on z-ai
  // (with its optional concrete model mapping + modelless fallback).
  const modelRoute = resolveModelRoute(requestedModelId ?? conversation.modelId)
  const providerModel = modelRoute.backend === 'zai' ? modelRoute.providerModel : null
  const openRouterModels = modelRoute.backend === 'openrouter' ? modelRoute.models : null
  const shouldAutoTitle = conversation.title === DEFAULT_TITLE

  // Persist the user message + apply auto-title / model override.
  const userMessage = await db.message.create({
    data: { conversationId: id, role: 'user', content },
  })
  if (attachmentsProvided) {
    await db.attachment.updateMany({
      where: { id: { in: attachmentIds } },
      data: { messageId: userMessage.id, conversationId: id },
    })
  }
  if (shouldAutoTitle || requestedModelId) {
    await db.conversation.update({
      where: { id },
      data: {
        ...(shouldAutoTitle
          ? {
              title: (
                content.trim().length > 0
                  ? content
                  : claimedAttachments[0]?.displayName ?? DEFAULT_TITLE
              ).slice(0, 40),
            }
          : {}),
        ...(requestedModelId ? { modelId: requestedModelId } : {}),
      },
    })
  }

  // Assistant persona: when the conversation is linked to an assistant, its
  // instructions become the primary system context (platform prompt stays as
  // a base below it).
  const baseSystemPrompt = conversation.assistant?.instructions
    ? `${conversation.assistant.instructions}\n\n(Platform persona base: ${SYSTEM_PROMPT})`
    : SYSTEM_PROMPT

  // Build model context: system prompt + last 20 prior messages + the new user message.
  // PHASE 6: history rows now carry their attachments so user turns that
  // contained images can be re-shown to the vision model (follow-up
  // questions without re-uploading, §13).
  const recentDesc = await db.message.findMany({
    where: { conversationId: id, id: { not: userMessage.id } },
    orderBy: { createdAt: 'desc' },
    take: HISTORY_LIMIT,
    include: { attachments: { orderBy: { createdAt: 'asc' } } },
  })
  const history = recentDesc.reverse()

  /**
   * PHASE 7.1 — history rows are replayed as the words the model actually saw
   * on that turn. A doc-only turn stores content='' but was served the
   * document-only default; replaying an EMPTY user message corrupts the
   * reconstructed conversation (payload evidence: `u:0` rows). Same for
   * image-only turns and their documented default.
   */
  const historyRowText = (m: (typeof history)[number]): string => {
    if (m.role.toLowerCase() !== 'user' || m.content.trim().length > 0) return m.content
    if (m.attachments.some(isDocumentAttachment)) return DOCUMENT_ONLY_DEFAULT
    if (m.attachments.some((a) => a.kind === 'image')) return 'Describe this image.'
    return m.content
  }

  // ---- PHASE 6 vision context ------------------------------------------------
  // `visionPrepared`/`visionFailures` hold the current turn's conversion
  // results; `allImagesFailed` short-circuits the turn with an honest error.
  // Vision activates when the CURRENT turn carries images OR when recent user
  // turns carry images worth re-showing (bounded follow-up policy below).
  const recentImageTurnIds = recentDesc
    .filter(
      (m) => m.role.toLowerCase() === 'user' && m.attachments.some((a) => a.kind === 'image')
    )
    .slice(0, VISION_HISTORY_IMAGE_TURNS)
    .map((m) => m.id)
  const useVision = imageAttachments.length > 0 || recentImageTurnIds.length > 0

  // ---- PHASE 7 document context ----------------------------------------------
  // Documents on the CURRENT turn plus document attachments from the most
  // recent user turns (§6 follow-ups without re-upload). Extraction is cached
  // per attachment, bounded per request; failures stay honest per class.
  const currentTurnDocs = claimedAttachments.filter(isDocumentAttachment)
  const historyDocTurns = recentDesc
    .filter((m) => m.role.toLowerCase() === 'user' && m.attachments.some(isDocumentAttachment))
    .slice(0, DOC_HISTORY_TURNS)
    .map((m) => ({ attachments: m.attachments.filter(isDocumentAttachment) }))
  const docContext =
    currentTurnDocs.length > 0 || historyDocTurns.length > 0
      ? await collectDocumentContext(currentTurnDocs, historyDocTurns, content)
      : { block: null as string | null, failures: [], readableCount: 0 }
  // §16 — every current-turn document unreadable and nothing else readable:
  // short-circuit with the honest failure instead of a fabricated answer.
  const allDocsFailed =
    currentTurnDocs.length > 0 && docContext.readableCount === 0 && imageAttachments.length === 0

  // ---- PHASE 8 web search gate ------------------------------------------------
  // Backend-owned tool routing (§1/§21): the SDK chat endpoint has no native
  // function calling, so THIS route decides when a turn needs external
  // information — from the CURRENT user text only (§11: attachments never
  // trigger a search). Structural loop protection (§18): the gate runs once,
  // at most one bounded search executes, and there is no model-driven tool
  // loop at any point.
  const webGate =
    content.trim().length > 0 ? evaluateWebSearchGate(content) : { trigger: null as 'explicit' | 'recency' | null }
  let historyWebSources: {
    title: string
    url: string
    domain: string
    snippet: string
    query: string
    publishedDate: string | null
  }[] = []
  let includeHistoryEvidence = false
  if (content.trim().length > 0) {
    const recentSourceMessages = await db.message.findMany({
      where: { conversationId: id, role: 'assistant', sources: { some: {} } },
      orderBy: { createdAt: 'desc' },
      take: SEARCH_HISTORY_TURNS,
      include: { sources: { orderBy: { ordinal: 'asc' } } },
    })
    historyWebSources = recentSourceMessages
      .reverse()
      .flatMap((m) =>
        m.sources.map((s) => ({
          title: s.title,
          url: s.url,
          domain: s.domain,
          snippet: s.snippet,
          query: s.query,
          publishedDate: s.publishedDate,
        }))
      )
    includeHistoryEvidence = shouldInjectHistoryEvidence(content, historyWebSources.length > 0)
  }
  console.log(
    `WEBSEARCH-GATE conv=${id} trigger=${webGate.trigger ?? 'none'} historySources=${historyWebSources.length} historyInject=${includeHistoryEvidence}`
  )

  let visionPrepared: PreparedVisionImage[] = []
  let visionFailures: VisionImageFailure[] = []
  let allImagesFailed = false

  if (imageAttachments.length > 0) {
    // §3 — actual image retrieval: every claimed image is re-validated by the
    // real decoder, normalized and encoded. The server trusts stored bytes,
    // not client declarations.
    for (const att of imageAttachments) {
      const result = await prepareVisionImage(att)
      if (result.ok) visionPrepared.push(result.image)
      else visionFailures.push(result.failure)
    }

    if (visionPrepared.length === 0) {
      // Current turn had images but nothing was decodable — no fabricated
      // answer, no fake analysis (§14).
      allImagesFailed = true
    }
  }

  /**
   * PHASE 8 — run the search phase. Executed separately from assembly so the
   * streaming path can emit REAL status events around it (§14) while the
   * non-streaming path runs it inline. Never runs on short-circuit turns
   * (all-images-failed / all-docs-failed) — no tool work for a doomed turn.
   */
  const executeSearchPhase = async (): Promise<SearchOutcome | null> => {
    if (allImagesFailed || allDocsFailed) return null
    if (webGate.trigger === null) return null
    const outcome = await runWebSearch(content)
    console.log(
      `WEBSEARCH-EXEC conv=${id} trigger=${outcome?.trigger} query="${outcome?.query}" ok=${outcome?.ok} results=${outcome?.sources.length} fetchFailures=${outcome?.fetchFailures.length} kind=${outcome?.failure?.kind ?? '-'}`
    )
    return outcome
  }

  /**
   * PHASE 8 — assemble the model messages for this turn given the search
   * outcome. Evidence ordering (Phase 7.1 hierarchy extended to web data):
   *   [document block] → [earlier web evidence] → [fresh search evidence] →
   *   [end marker] → [the user's words]
   * so the CURRENT user message is always the final controlling content.
   */
  const buildModelMessages = async (
    outcome: SearchOutcome | null
  ): Promise<{ messages: ChatMessageInput[] | VisionChatMessage[]; systemPrompt: string }> => {
    const freshBlock = outcome?.ok ? buildSearchEvidenceBlock(outcome) : null
    const freshCount = outcome?.ok ? outcome.sources.length : 0
    const historyBlock = includeHistoryEvidence
      ? buildHistoryEvidenceBlock(historyWebSources, freshCount + 1)
      : null
    const historyCount = historyBlock ? historyWebSources.length : 0
    const webSearchUsed = freshCount > 0 || historyCount > 0 || outcome?.failure != null

    // PHASE 8.1 — failed searches get a SYSTEM-LEVEL disclosure requirement on
    // top of the grounding contract: in the wild the evidence-note alone was
    // dropped by the model (the last-position user message dominated), so the
    // answer looked grounded without ever saying the search never ran.
    const systemPrompt = webSearchUsed
      ? `${baseSystemPrompt}\n\n${SEARCH_GROUNDING_PROMPT}${
          outcome?.failure && webGate.trigger !== null
            ? `\n\n${SEARCH_FAILURE_DISCLOSURE_PROMPT}`
            : ''
        }`
      : baseSystemPrompt

    const evidenceBlocks: string[] = []
    if (docContext.block) evidenceBlocks.push(docContext.block)
    if (historyBlock?.block) evidenceBlocks.push(historyBlock.block)
    if (outcome?.failure && webGate.trigger !== null) {
      evidenceBlocks.push(
        `(System note about this turn's search: ${searchFailureNote(outcome.failure)} Do not present any internal knowledge as search results; if you answer from your own knowledge, say clearly that the search did not provide results. The user's request below still stands.)`
      )
    }
    if (freshBlock) evidenceBlocks.push(freshBlock)
    const combinedEvidence = evidenceBlocks.length > 0 ? evidenceBlocks.join('\n\n') : null

    if (useVision && !allImagesFailed) {
      const textPayload = content.trim().length > 0 ? content : 'Describe this image.'
      const notes: string[] = []
      if (visionFailures.length > 0) {
        notes.push(...visionFailures.map(visionFailureMessage))
      }
      const finalText =
        notes.length > 0 ? `${textPayload}\n\n(${notes.join(' ')})` : textPayload

      // Follow-up support: re-include images from the most recent image-bearing
      // user turns, bounded by turn count and a per-request image budget — the
      // current turn's images always win the budget first.
      const selectedTurnIds = new Set(recentImageTurnIds)
      let imageBudget = VISION_MAX_IMAGES_PER_REQUEST - visionPrepared.length

      const visionContext: VisionChatMessage[] = [
        {
          role: 'system',
          content:
            systemPrompt +
            // PHASE 6: vision grounding rides every vision turn (unchanged).
            `\n\n${VISION_GROUNDING_PROMPT}`,
        },
      ]
      for (const m of history) {
        if (m.role.toLowerCase() === 'user' && selectedTurnIds.has(m.id) && imageBudget > 0) {
          const turnImages = m.attachments.filter((a) => a.kind === 'image')
          const parts: VisionContentPart[] = [
            { type: 'text', text: m.content.trim().length > 0 ? m.content : 'Describe this image.' },
          ]
          for (const a of turnImages) {
            if (imageBudget <= 0) break
            const result = await prepareVisionImage(a)
            if (result.ok) {
              parts.push({ type: 'image_url', image_url: { url: result.image.dataUrl } })
              imageBudget -= 1
            }
          }
          visionContext.push({ role: 'user', content: parts })
        } else {
          visionContext.push({
            role: m.role.toLowerCase() === 'assistant' ? 'assistant' : 'user',
            content: historyRowText(m),
          })
        }
      }
      const visionUserParts: VisionContentPart[] = [
        // §4 — document context rides the same request on mixed image+document
        // turns. PHASE 7.1/8: evidence FIRST (documents → earlier web → fresh
        // search), user intent LAST.
        ...(combinedEvidence ? [{ type: 'text' as const, text: combinedEvidence }] : []),
        { type: 'text', text: finalText },
        ...visionPrepared.map((p) => ({ type: 'image_url' as const, image_url: { url: p.dataUrl } })),
      ]
      visionContext.push({ role: 'user', content: visionUserParts })
      return { messages: visionContext, systemPrompt }
    }

    // Text-only context. PHASE 7: extracted document context (current turn
    // and/or recent document turns) is included in the live user message —
    // history rows stay clean, so follow-ups re-collect context each turn.
    // §7 — one documented default when a document arrives with no question.
    //
    // PHASE 7.1 — CONVERSATIONAL-CONTROL FIX: evidence is data, not an
    // instruction, and it must NEVER sit between the user's words and
    // generation. assembleDocumentUserTurn orders the final message as:
    //   [all evidence blocks] → [end marker] → [user's words]
    // so the CURRENT user message is the final controlling content the
    // provider sees, whatever the evidence size. The document-only default
    // fires ONLY for a current-turn document with no user text — never for a
    // text-only follow-up to a historical document.
    const assembled = assembleDocumentUserTurn({
      content,
      currentTurnHasDocuments: currentTurnDocs.length > 0,
      docBlock: combinedEvidence,
    })
    const userTurnText = assembled.userText
    const textWithContext = assembled.finalText
    const finalUserMessage: ChatMessageInput =
      attachmentsProvided && !combinedEvidence
        ? {
            // Defensive fallback: an attachment turn that produced no readable
            // context and no images (never reachable through the error gates).
            role: 'user',
            content: `${textWithContext}${textWithContext.length > 0 ? '\n\n' : ''}[The user attached ${claimedAttachments
              .map((a) => a.displayName)
              .join(', ')}. Attachment contents could not be read.]`,
          }
        : { role: 'user', content: textWithContext }
    return {
      messages: [
        { role: 'system', content: systemPrompt },
        ...history.map((m) => ({ role: m.role.toLowerCase(), content: historyRowText(m) })),
        finalUserMessage,
      ],
      systemPrompt,
    }
  }

  /** PHASE 8 — persist exactly the sources the answer actually cited (§7). */
  const persistCitedSources = async (
    messageId: string,
    answerText: string,
    outcome: SearchOutcome | null,
    historySources: WebSource[]
  ): Promise<void> => {
    const freshSources = outcome?.ok ? outcome.sources : []
    const maxOrdinal = freshSources.length + historySources.length
    if (maxOrdinal === 0) return
    const cited = parseCitedOrdinals(answerText, maxOrdinal)
    const byOrdinal = new Map<number, WebSource>()
    for (const s of freshSources) byOrdinal.set(s.ordinal, s)
    for (const s of historySources) byOrdinal.set(s.ordinal, s)
    const rows = cited
      .map((n) => byOrdinal.get(n))
      .filter((s): s is WebSource => !!s)
      .map((s) => ({
        messageId,
        ordinal: s.ordinal,
        title: s.title.slice(0, 500),
        url: s.url,
        domain: s.domain.slice(0, 200),
        snippet: s.snippet.slice(0, 600),
        ...(s.publishedDate ? { publishedDate: s.publishedDate.slice(0, 100) } : {}),
        query: s.query.slice(0, 300),
      }))
    if (rows.length > 0) {
      await db.messageSource.createMany({ data: rows })
    }
    console.log(
      `WEBSEARCH-CITED conv=${id} maxOrdinal=${maxOrdinal} cited=[${cited.join(',')}] persisted=${rows.length}`
    )
  }

  /** §8 — one honest sentence per failure class; never a fabricated answer. */
  const visionErrorMessage = (raw: string): string => {
    if (isProviderImageRejection(raw)) {
      return 'The image could not be processed — try a JPG, PNG or WebP version.'
    }
    return 'Image understanding is unavailable right now. Please try again.'
  }

  // Catalogue telemetry — count a use when this is the first message.
  if (conversation.assistantId && history.length === 0) {
    db.assistant
      .update({ where: { id: conversation.assistantId }, data: { uses: { increment: 1 } } })
      .catch(() => undefined) // telemetry must never break the chat path
  }

  // Non-streaming: single JSON reply.
  if (!stream) {
    if (allImagesFailed) {
      return NextResponse.json(
        {
          code: 'unsupported_media',
          message: `None of the attached images could be opened. ${visionFailures
            .map(visionFailureMessage)
            .join(' ')}`,
        },
        { status: 422 }
      )
    }
    if (allDocsFailed) {
      return NextResponse.json(
        {
          code: 'document_unreadable',
          message: docContext.failures.map(documentFailureMessage).join(' '),
        },
        { status: 422 }
      )
    }
    try {
      const outcome = await executeSearchPhase()
      const historySources = includeHistoryEvidence
        ? buildHistoryEvidenceBlock(historyWebSources, (outcome?.ok ? outcome.sources.length : 0) + 1).sources
        : []
      const { messages: modelMessages } = await buildModelMessages(outcome)
      const text = useVision
        ? (await completeVisionChat(modelMessages as VisionChatMessage[])).text
        : openRouterModels
          ? await orCompleteChat(modelMessages as ChatMessageInput[], openRouterModels).then((r) => r.text)
          : await completeChat(modelMessages as ChatMessageInput[], providerModel)
      const finalText =
        outcome?.ok || historySources.length > 0
          ? sanitizeCitationMarkers(text, outcome?.ok ? outcome.sources.length + historySources.length : 0)
          : text
      const assistantMessage = await db.message.create({
        data: { conversationId: id, role: 'assistant', content: finalText },
      })
      await persistCitedSources(assistantMessage.id, finalText, outcome, historySources)
      const saved = await db.message.findUnique({
        where: { id: assistantMessage.id },
        include: { sources: { orderBy: { ordinal: 'asc' } } },
      })
      return NextResponse.json(messageToJson(saved ?? assistantMessage))
    } catch (e) {
      const raw = e instanceof Error ? e.message : String(e)
      return NextResponse.json(
        {
          code: 'upstream_error',
          message: useVision ? visionErrorMessage(raw) : raw,
        },
        { status: 502 }
      )
    }
  }

  // Streaming: text/event-stream — `status` events (PHASE 8 search phases,
  // ignored by older clients), then `delta` events, then a final `done` (or `error`).
  return new Response(
    new ReadableStream({
      async start(controller) {
        const enc = new TextEncoder()
        const send = (event: string, data: string) =>
          controller.enqueue(enc.encode(`data: ${JSON.stringify({ event, data })}\n\n`))
        if (allImagesFailed) {
          send(
            'error',
            `None of the attached images could be opened. ${visionFailures
              .map(visionFailureMessage)
              .join(' ')}`
          )
          controller.close()
          return
        }
        if (allDocsFailed) {
          send('error', docContext.failures.map(documentFailureMessage).join(' '))
          controller.close()
          return
        }
        try {
          // §14 — real search activity gets real state events. The gate has
          // already been evaluated; SEARCHING is only ever emitted for an
          // actual search turn, never for ordinary text generation.
          let outcome: SearchOutcome | null = null
          if (webGate.trigger !== null) {
            send('status', 'searching')
            outcome = await executeSearchPhase()
            send('status', outcome?.ok ? 'composing' : 'search_failed')
          }
          const historySources = includeHistoryEvidence
            ? buildHistoryEvidenceBlock(historyWebSources, (outcome?.ok ? outcome.sources.length : 0) + 1).sources
            : []
          const { messages: modelMessages } = await buildModelMessages(outcome)
          const full = useVision
            ? (await streamVisionChat(modelMessages as VisionChatMessage[], (d) => send('delta', d))).text
            : openRouterModels
              ? await orStreamChat(modelMessages as ChatMessageInput[], openRouterModels, (d) => send('delta', d))
              : await streamChat(modelMessages as ChatMessageInput[], (d) => send('delta', d), providerModel)
          const finalText =
            outcome?.ok || historySources.length > 0
              ? sanitizeCitationMarkers(full, outcome?.ok ? outcome.sources.length + historySources.length : 0)
              : full
          const saved = await db.message.create({
            data: { conversationId: id, role: 'assistant', content: finalText },
          })
          await persistCitedSources(saved.id, finalText, outcome, historySources)
          const savedWithSources = await db.message.findUnique({
            where: { id: saved.id },
            include: { sources: { orderBy: { ordinal: 'asc' } } },
          })
          send('done', JSON.stringify(messageToJson(savedWithSources ?? saved)))
          controller.close()
        } catch (e) {
          const raw = String(e instanceof Error ? e.message : e)
          send('error', useVision ? visionErrorMessage(raw) : raw)
          controller.close()
        }
      },
    }),
    {
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache, no-transform',
        Connection: 'keep-alive',
        'X-Accel-Buffering': 'no',
      },
    }
  )
}
