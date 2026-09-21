import { NextRequest, NextResponse } from 'next/server'
import { db } from '@/lib/db'
import { messageToJson } from '@/lib/serializers'
import { resolveModelRoute, OPENROUTER_MODELS } from '@/lib/models'
import { orCompleteChat, orStreamChat } from '@/lib/openrouter'
import { planRoute } from '@/lib/router'
import {
  SYSTEM_PROMPT,
  VISION_GROUNDING_PROMPT,
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
  evaluateWebSearchGate,
  buildHistoryEvidenceBlock,
  shouldInjectHistoryEvidence,
  parseCitedOrdinals,
  sanitizeCitationMarkers,
  normalizeCitationBrackets,
  searchFailureNote,
  SEARCH_HISTORY_TURNS,
  type WebSource,
} from '@/lib/websearch'
import {
  detectHistoricalReligious,
  planSearch,
} from '@/lib/search/planner'
import {
  runResearch,
  buildResearchEvidenceBlock,
  RESEARCH_BUDGET,
} from '@/lib/search/research'
import { NOOP_EMIT, type ClarifyOption, type ResearchSource, type SearchEventEmitter } from '@/lib/search/types'

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

// POST /api/v1/conversations/:id/messages — { content, stream?, attachments? }
// ARCHITECTURE LOCK: a client-sent modelId (legacy wire compat) is accepted
// and IGNORED — the GS Router derives all routing server-side.
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

  // ARCHITECTURE LOCK — GS AI is ONE assistant experience. A client-sent
  // modelId (legacy) is accepted for wire compatibility and IGNORED: routing
  // is derived server-side by the GS Router (below). No user model preference
  // exists, is persisted, or is honored.
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
  // Persist the user message + apply auto-title (no model override — GS Router owns routing).
  if (shouldAutoTitle) {
    await db.conversation.update({
      where: { id },
      data: {
        title: (
          content.trim().length > 0
            ? content
            : claimedAttachments[0]?.displayName ?? DEFAULT_TITLE
        ).slice(0, 40),
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

  // ---- PHASE 8.1 web search gate + intent planner -----------------------------
  // Backend-owned tool routing (§1/§21). The gate is the CHEAP deterministic
  // signal (avoids an LLM roundtrip on every ordinary chat turn); when it
  // fires — or when the previous turn asked a clarification and this is the
  // short answer to it — the PLANNER classifies intent (§5): broad vs sector
  // vs geo vs source-specific, ambiguity detection, time window, depth.
  // "what is today's news?" with no topic now CLARIFIES (§6) instead of
  // silently picking arbitrary sectors (§35).
  const webGate =
    content.trim().length > 0 ? evaluateWebSearchGate(content) : { trigger: null as 'explicit' | 'recency' | null }

  let previousClarifyOptions: ClarifyOption[] | null = null
  if (content.trim().length > 0) {
    const lastClarify = await db.message.findFirst({
      where: { conversationId: id, role: 'assistant', clarifyOptions: { not: null } },
      orderBy: { createdAt: 'desc' },
      select: { clarifyOptions: true },
    })
    if (lastClarify?.clarifyOptions) {
      try {
        const parsed = JSON.parse(lastClarify.clarifyOptions) as { options?: unknown }
        if (Array.isArray(parsed.options)) {
          previousClarifyOptions = parsed.options
            .filter(
              (o): o is ClarifyOption =>
                typeof o === 'object' && o !== null && typeof (o as ClarifyOption).id === 'string' && typeof (o as ClarifyOption).label === 'string'
            )
            .slice(0, 8)
        }
      } catch {
        // malformed persisted options — treat as absent
      }
    }
  }
  // A short reply right after a clarification is resolved by the planner's
  // DETERMINISTIC chip matcher — no LLM needed for the chip path.
  const clarifyFollowUp =
    previousClarifyOptions !== null && content.trim().length > 0 && content.length <= 60
  // PHASE 8.2 §1/§8/§25 + ARCHITECTURE LOCK — the GS Router is the ONE
  // backend-owned decision per turn (capability route, internal model,
  // retrieval depth). Deep research is inferred from the REQUEST (depth
  // language), never from a user-visible model choice. The intent planner
  // runs when the router says retrieval is due — the search gate, a clarify
  // follow-up, the historical/religious evidence rule, or explicit
  // deep-research language.
  const routerPlan = planRoute({
    content,
    hasImages: useVision,
    hasDocuments: currentTurnDocs.length > 0 || historyDocTurns.length > 0,
    webGateTrigger: webGate.trigger,
    clarifyFollowUp,
    historicalReligious: detectHistoricalReligious(content),
    historyChars: history.reduce((n, m) => n + m.content.length, 0),
  })
  const plannerRuns = routerPlan.plannerRuns
  console.log(
    `GS-ROUTER conv=${id} route=${routerPlan.route} depth=${routerPlan.depth} reason=${routerPlan.reason}`
  )
  // Internal execution route for synthesis (models.ts mapping — OpenRouter
  // free chains bypass primary-provider quota windows; z-ai carries the
  // optional concrete model mapping + modelless fallback). INTERNAL ONLY:
  // none of these values are ever sent to a client.
  const modelRoute = resolveModelRoute(routerPlan.internalModelId)
  const providerModel = modelRoute.backend === 'zai' ? modelRoute.providerModel : null
  const openRouterModels = modelRoute.backend === 'openrouter' ? modelRoute.models : null

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
  const historyLines = history
    .slice(-8)
    .map((m) => `${m.role.toLowerCase() === 'assistant' ? 'assistant' : 'user'}: ${historyRowText(m).slice(0, 220)}`)
  console.log(
    `WEBSEARCH-GATE conv=${id} trigger=${webGate.trigger ?? 'none'} planner=${plannerRuns} clarifyFollowUp=${clarifyFollowUp} historySources=${historyWebSources.length} historyInject=${includeHistoryEvidence}`
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
   * PHASE 8.1 — the search turn, in one bounded execution. `emit === null`
   * (non-streaming path) runs the identical pipeline silently.
   */
  type TurnSearch =
    | { kind: 'none' }
    | { kind: 'clarify'; question: string; options: ClarifyOption[] }
    | {
        kind: 'research'
        outcome: Awaited<ReturnType<typeof runResearch>>
        evidenceBlock: string | null
        maxOrdinal: number
        historySources: WebSource[]
        startedAt: number
      }

  const executeSearchPhase = async (emit: SearchEventEmitter | null): Promise<TurnSearch> => {
    if (allImagesFailed || allDocsFailed) return { kind: 'none' }
    if (!plannerRuns) return { kind: 'none' }

    // 2026-09-21 live audit: the planner used to run in TOTAL silence (8-16s
    // of nothing on the wire before the first visible event). The search phase
    // HAS started the moment the planner runs — say so honestly so clients
    // light up the searching state immediately.
    emit?.('status', 'searching')

    const plan = await planSearch({
      userText: content,
      historyLines,
      previousClarifyOptions,
    })
    console.log(
      `SEARCH-PLAN conv=${id} intent=${plan.intent} needs=${plan.needsSearch} ambiguous=${plan.ambiguity.isAmbiguous} queries=[${plan.queries.join(' | ')}] range=${plan.timeRange} depth=${plan.depth} stop=${plan.stopSignal}`
    )

    // §6/§35 — ambiguous broad request: clarify with native quick choices.
    // No search runs; the assistant message IS the clarification question.
    if (plan.ambiguity.isAmbiguous && plan.ambiguity.prompt) {
      return { kind: 'clarify', question: plan.ambiguity.prompt.question, options: plan.ambiguity.prompt.options }
    }

    // §19 — stop/reuse/no-search turns answer from what already exists.
    if (!plan.needsSearch || plan.stopSignal) return { kind: 'none' }

    // 8.2 §9/§25 + ARCHITECTURE LOCK — depth combines the planner's intent
    // decision with the GS Router's depth decision. The SEARCH SERVICE is
    // identical either way — only the budget differs. No model tier exists.
    const depth: 'quick' | 'deep' =
      plan.depth === 'deep' || routerPlan.depth === 'deep' ? 'deep' : 'quick'

    const researchStartedAt = Date.now()
    emit?.('research', {
      type: 'started',
      intent: plan.intent,
      depth,
      roundsPlanned: RESEARCH_BUDGET[depth].maxRounds,
    })
    emit?.('search', { type: 'started', intent: plan.intent, depth, label: plan.queries[0] ?? '' })

    const outcome = await runResearch({
      queries: plan.queries,
      intent: plan.intent,
      timeRange: plan.timeRange,
      region: plan.region,
      sourceHint: plan.sourceHint,
      officialOnly: plan.officialOnly,
      depth,
      emit: emit ?? NOOP_EMIT,
      deadlineAt: Date.now() + RESEARCH_BUDGET[depth].wallClockMs + 2_000,
    })
    console.log(
      `RESEARCH conv=${id} intent=${plan.intent} queries=${outcome.queriesRun.length} sources=${outcome.sources.length} retrieved=${outcome.sources.filter((s) => s.status === 'retrieved').length} engines=${outcome.enginesUsed.join('+') || 'none'} ok=${outcome.ok} kind=${outcome.failure?.kind ?? '-'}`
    )

    if (!outcome.ok || outcome.sources.length === 0) {
      emit?.('search', { type: 'failed', reason: outcome.failure?.kind ?? 'no_results' })
      emit?.('research', {
        type: 'failed',
        reason: outcome.failure?.kind ?? 'no_results',
        message: outcome.failure?.message,
      })
      return { kind: 'research', outcome, evidenceBlock: null, maxOrdinal: 0, historySources: [], startedAt: researchStartedAt }
    }

    const { block, maxOrdinal } = buildResearchEvidenceBlock(outcome.sources, outcome.queriesRun)
    return { kind: 'research', outcome, evidenceBlock: block, maxOrdinal, historySources: [], startedAt: researchStartedAt }
  }

  /**
   * PHASE 8 — assemble the model messages for this turn given the search
   * outcome. Evidence ordering (Phase 7.1 hierarchy extended to web data):
   *   [document block] → [earlier web evidence] → [fresh search evidence] →
   *   [end marker] → [the user's words]
   * so the CURRENT user message is always the final controlling content.
   */
  const buildModelMessages = async (
    turn: TurnSearch
  ): Promise<{ messages: ChatMessageInput[] | VisionChatMessage[]; systemPrompt: string; historySources: WebSource[] }> => {
    const researchOk = turn.kind === 'research' && turn.outcome.ok && turn.evidenceBlock !== null
    const freshBlock = researchOk ? turn.evidenceBlock : null
    const freshCount = researchOk ? turn.maxOrdinal : 0
    const historyBlock = includeHistoryEvidence
      ? buildHistoryEvidenceBlock(historyWebSources, freshCount + 1)
      : null
    const historySources = historyBlock?.sources ?? []
    const historyCount = historySources.length
    const searchFailed =
      turn.kind === 'research' && (!turn.outcome.ok || turn.outcome.failure != null)
    // 2026-09-21 NATURAL-FLOW FIX: the grounding appendix attaches ONLY when
    // THIS turn actually ran a research attempt — never because older turns
    // carry sources (historyCount no longer triggers it; the injection switch
    // in shouldInjectHistoryEvidence now requires an explicit source reference).
    const webSearchUsed = turn.kind === 'research'

    const systemPrompt = webSearchUsed
      ? `${baseSystemPrompt}\n\n${SEARCH_GROUNDING_PROMPT}`
      : baseSystemPrompt

    const evidenceBlocks: string[] = []
    if (docContext.block) evidenceBlocks.push(docContext.block)
    if (historyBlock?.block) evidenceBlocks.push(historyBlock.block)
    if (searchFailed) {
      const note =
        turn.kind === 'research'
          ? turn.outcome.failure?.message ?? 'The search could not be completed.'
          : 'The search could not be completed.'
      evidenceBlocks.push(
        `(System note about this turn's search: ${searchFailureNote(
          // Map the research failure kind onto the user-facing failure classes.
          turn.kind === 'research' && turn.outcome.failure
            ? turn.outcome.failure.kind === 'timeout'
              ? ({ kind: 'timeout', message: turn.outcome.failure.message } as const)
              : turn.outcome.failure.kind === 'no_results'
                ? ({ kind: 'no_results', message: turn.outcome.failure.message } as const)
                : ({ kind: 'provider_error', message: turn.outcome.failure.message } as const)
            : ({ kind: 'provider_error', message: note } as const)
        )} If the question can be answered from your own knowledge, just answer it normally — mention the failed search in a few passing words only if it is relevant. Never present internal knowledge as search results and never emit [N] markers. The user's request below still stands.)`
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
      return { messages: visionContext, systemPrompt, historySources }
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
      historySources,
    }
  }

  /**
   * PHASE 8.1 — citation-marker hygiene: strip markers whose sources did not
   * survive persistence (failed retrievals etc.) so every visible [N] opens a
   * real source (§15). Returns the sanitized text.
   */
  const sanitizeAgainstPersisted = (text: string, turn: TurnSearch, historySources: WebSource[]): string => {
    const freshCount = turn.kind === 'research' && turn.outcome.ok ? turn.maxOrdinal : 0
    const totalOrdinals = freshCount + historySources.length
    if (totalOrdinals === 0) return text
    const allowed = new Set<number>()
    if (turn.kind === 'research' && turn.outcome.ok) {
      for (const s of turn.outcome.sources) {
        if (s.status === 'retrieved' || s.status === 'snippet_only') allowed.add(s.ordinal)
      }
    }
    // History ordinals are kept only when actually cited (they persist below).
    for (const n of parseCitedOrdinals(text, totalOrdinals)) {
      if (n > freshCount) allowed.add(n)
    }
    return sanitizeCitationMarkers(text, totalOrdinals, allowed)
  }

  /**
   * PHASE 8.1 — persist the turn's source evidence (§11). ALL discovered
   * sources that carry real metadata are stored with their honest retrieval
   * status; `used` is set exactly for the ordinals the answer cited. The
   * citation mapping predates synthesis (evidence is numbered BEFORE the
   * model writes), and persistence only ever reflects provider metadata —
   * nothing here can be fabricated by the model.
   */
  const persistTurnSources = async (
    messageId: string,
    answerText: string,
    turn: TurnSearch,
    historySources: WebSource[]
  ): Promise<void> => {
    const fresh: ResearchSource[] =
      turn.kind === 'research' && turn.outcome.ok ? turn.outcome.sources : []
    const maxOrdinal = fresh.length + historySources.length
    if (maxOrdinal === 0) return
    const cited = parseCitedOrdinals(answerText, maxOrdinal)
    const citedSet = new Set(cited)

    const rows = [
      ...fresh
        .filter((s) => s.status === 'retrieved' || s.status === 'snippet_only')
        .map((s) => ({
          messageId,
          ordinal: s.ordinal,
          title: s.title.slice(0, 500),
          url: s.url,
          domain: s.domain.slice(0, 200),
          snippet: (s.snippet || '').slice(0, 600),
          ...(s.publishedDate ? { publishedDate: s.publishedDate.slice(0, 100) } : {}),
          query: s.query.slice(0, 300),
          status: s.status,
          used: citedSet.has(s.ordinal),
          rank: s.searchRank,
        })),
      ...historySources
        .filter((s) => citedSet.has(s.ordinal))
        .map((s) => ({
          messageId,
          ordinal: s.ordinal,
          title: s.title.slice(0, 500),
          url: s.url,
          domain: s.domain.slice(0, 200),
          snippet: (s.snippet || '').slice(0, 600),
          ...(s.publishedDate ? { publishedDate: s.publishedDate.slice(0, 100) } : {}),
          query: s.query.slice(0, 300),
          // Honest: this turn did not re-read the page; it rides as earlier evidence.
          status: 'snippet_only',
          used: true,
          rank: null,
        })),
    ]
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

  const openRouterAvailable = (): boolean =>
    (process.env.OPENROUTER_API_KEYS ?? '').trim().length > 0

  /**
   * ARCHITECTURE LOCK §5 — infra failures degrade to ONE clean sentence.
   * The raw provider/error text is server-log-only; the user must never see
   * provider names, key-pool state, quota windows or retry counters.
   */
  const userFacingTurnError = (raw: string): string => {
    console.error(`TURN-ERROR conv=${id}: ${raw.slice(0, 300)}`)
    return 'GS AI is temporarily unavailable. Please try again shortly.'
  }

  /**
   * PHASE 8.1 — synthesis with graceful degradation (§22). The model is the
   * reasoning layer (§2): when the primary provider is down at ACCOUNT level
   * (the proven 429 window), a search turn must still synthesize its real
   * evidence — the same messages retry over the OpenRouter free pool. Vision
   * turns never fall back (free text models cannot read images).
   */
  const synthesize = async (
    modelMessages: ChatMessageInput[] | VisionChatMessage[],
    onDelta?: (d: string) => void
  ): Promise<string> => {
    if (useVision) {
      return onDelta
        ? (await streamVisionChat(modelMessages as VisionChatMessage[], onDelta)).text
        : (await completeVisionChat(modelMessages as VisionChatMessage[])).text
    }
    const msgs = modelMessages as ChatMessageInput[]
    try {
      if (openRouterModels) {
        return onDelta
          ? await orStreamChat(msgs, openRouterModels, onDelta)
          : await orCompleteChat(msgs, openRouterModels).then((r) => r.text)
      }
      return onDelta
        ? await streamChat(msgs, onDelta, providerModel)
        : await completeChat(msgs, providerModel)
    } catch (e) {
      if (!openRouterModels && openRouterAvailable()) {
        console.log(
          `SYNTHESIS-FALLBACK conv=${id} primary failed (${(e instanceof Error ? e.message : String(e)).slice(0, 80)}) → OpenRouter free chain`
        )
        return onDelta
          ? await orStreamChat(msgs, OPENROUTER_MODELS['gs-free'], onDelta)
          : await orCompleteChat(msgs, OPENROUTER_MODELS['gs-free']).then((r) => r.text)
      }
      throw e
    }
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
      const turn = await executeSearchPhase(null)

      // §6 — clarification turn: the question IS the answer.
      if (turn.kind === 'clarify') {
        const saved = await db.message.create({
          data: {
            conversationId: id,
            role: 'assistant',
            content: turn.question,
            clarifyOptions: JSON.stringify({ question: turn.question, options: turn.options }),
          },
        })
        return NextResponse.json(messageToJson(saved))
      }

      const { messages: modelMessages, historySources } = await buildModelMessages(turn)
      const text = await synthesize(modelMessages)
      const finalText = sanitizeAgainstPersisted(normalizeCitationBrackets(text), turn, historySources)
      const assistantMessage = await db.message.create({
        data: { conversationId: id, role: 'assistant', content: finalText },
      })
      await persistTurnSources(assistantMessage.id, finalText, turn, historySources)
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
          message: useVision ? visionErrorMessage(raw) : userFacingTurnError(raw),
        },
        { status: 502 }
      )
    }
  }

  // Streaming: text/event-stream — `status` events (search phases, understood
  // by older clients), structured `search`/`source`/`clarify` events (the
  // PHASE 8.1 research trace; older clients ignore unknown events), then
  // `delta` events, then a final `done` (or `error`).
  //
  // PHASE 8.1 ARCHITECTURE — the TURN IS DECOUPLED FROM THE CONNECTION.
  // The public origin's outer proxy caps a response at ~30s (measured live:
  // a 54s research turn was cut mid-deltas while our own gateway has no
  // such limit). A search turn (plan + search + retrieval + synthesis)
  // legitimately exceeds that, so the turn now runs in a DETACHED promise
  // that always runs to completion and always persists the answer — the
  // ReadableStream is only a live VIEW of its event queue. If the view dies
  // (proxy cap, network blip, app backgrounding), the client simply re-fetches
  // the conversation and finds the finished, fully-sourced answer. Keep-alive
  // comment pings ride the stream during silent phases for intermediate
  // proxies with IDLE timeouts.
  const eventQueue: { event: string; data: string }[] = []
  let wakeup: (() => void) | null = null
  // Indirection: `wakeup` is assigned inside the nextEvent closure, so the
  // call sites below must read it at call time (TS narrows the bare variable).
  const fireWakeup = () => {
    const w = wakeup
    if (w) w()
  }
  let turnSettled = false
  const push = (event: string, data: string) => {
    eventQueue.push({ event, data })
    fireWakeup()
  }
  const nextEvent = async (): Promise<{ event: string; data: string } | null> => {
    if (eventQueue.length > 0) return eventQueue.shift()!
    return new Promise<{ event: string; data: string } | null>((resolve) => {
      wakeup = () => {
        wakeup = null
        resolve(eventQueue.shift() ?? null)
      }
    })
  }
  const emit: SearchEventEmitter = (event, payload) => push(event, JSON.stringify(payload))

  // The detached turn is fired (not awaited) — its lifetime is independent
  // of the Response/ReadableStream below.
  void (async () => {
    if (allImagesFailed) {
      push(
        'error',
        `None of the attached images could be opened. ${visionFailures
          .map(visionFailureMessage)
          .join(' ')}`
      )
      return
    }
    if (allDocsFailed) {
      push('error', docContext.failures.map(documentFailureMessage).join(' '))
      return
    }
    try {
      // §14 — real search activity gets real state events. SEARCHING is
      // only ever emitted for an actual search turn, WORKING only while
      // pages are actually being retrieved (emitted by runResearch).
      let turn: TurnSearch = { kind: 'none' }
      if (plannerRuns) {
        turn = await executeSearchPhase(emit)

        // §6 — clarification turn: emit the native quick choices and end
        // the turn; the question is the assistant message.
        if (turn.kind === 'clarify') {
          emit('clarify', { question: turn.question, options: turn.options })
          const saved = await db.message.create({
            data: {
              conversationId: id,
              role: 'assistant',
              content: turn.question,
              clarifyOptions: JSON.stringify({ question: turn.question, options: turn.options }),
            },
          })
          push('done', JSON.stringify(messageToJson(saved)))
          return
        }
      }

      const researchOk = turn.kind === 'research' && turn.outcome.ok && turn.evidenceBlock !== null
      // Old-client status vocabulary preserved exactly: searching was sent
      // inside executeSearchPhase (only when a search really runs);
      // composing/search_failed only follow a real search phase. Plain
      // text turns emit NO status — deltas carry their progress.
      if (turn.kind === 'research') {
        push('status', researchOk ? 'composing' : 'search_failed')
        if (researchOk) {
          // 8.2 §28 synthesis.started — orb COMPOSING maps to this.
          emit('research', {
            type: 'synthesis_started',
          })
        }
      }

      const { messages: modelMessages, historySources } = await buildModelMessages(turn)
      const full = await synthesize(modelMessages, (d) => push('delta', d))
      const finalText = sanitizeAgainstPersisted(normalizeCitationBrackets(full), turn, historySources)
      const saved = await db.message.create({
        data: { conversationId: id, role: 'assistant', content: finalText },
      })
      await persistTurnSources(saved.id, finalText, turn, historySources)
      const savedWithSources = await db.message.findUnique({
        where: { id: saved.id },
        include: { sources: { orderBy: { ordinal: 'asc' } } },
      })
      // §12 — the collapsed trace summary the clients render after done.
      if (turn.kind === 'research' && turn.outcome.ok) {
        const totalOrdinals = turn.maxOrdinal + historySources.length
        const usedCitations = parseCitedOrdinals(finalText, totalOrdinals)
        emit('search', {
          type: 'completed',
          queries: turn.outcome.queriesRun.length,
          sources: turn.outcome.sources.length,
          retrieved: turn.outcome.sources.filter((s) => s.status === 'retrieved').length,
          usedCitations,
        })
        // 8.2 §28 — citation.bound + research.completed + performance metrics.
        emit('research', {
          type: 'completed',
          queries: turn.outcome.queriesRun.length,
          sources: turn.outcome.sources.length,
          retrieved: turn.outcome.sources.filter((s) => s.status === 'retrieved').length,
          usedCitations,
          totalMs: Date.now() - turn.startedAt,
          timings: turn.outcome.timings,
        })
      }
      push('done', JSON.stringify(messageToJson(savedWithSources ?? saved)))
    } catch (e) {
      const raw = String(e instanceof Error ? e.message : e)
      push('error', useVision ? visionErrorMessage(raw) : userFacingTurnError(raw))
    } finally {
      turnSettled = true
      fireWakeup()
    }
  })()

  return new Response(
    new ReadableStream({
      async start(controller) {
        const enc = new TextEncoder()
        let clientGone = false
        const write = (chunk: string) => {
          if (clientGone) return
          try {
            controller.enqueue(enc.encode(chunk))
          } catch {
            clientGone = true // view is dead — the detached turn continues
          }
        }
        // Keep-alive during silent phases (planner, retrieval, first token).
        const ping = setInterval(() => write(': ping\n\n'), 4_000)
        try {
          for (;;) {
            const ev = await nextEvent()
            if (ev) write(`data: ${JSON.stringify({ event: ev.event, data: ev.data })}\n\n`)
            if (ev && (ev.event === 'done' || ev.event === 'error')) break
            if (!ev && turnSettled) break
            if (clientGone) break
          }
        } catch {
          clientGone = true
        } finally {
          clearInterval(ping)
        }
        try {
          controller.close()
        } catch {
          // already closed by the runtime on client disconnect
        }
      },
      // Client disconnect (proxy cap / abort / navigation): the detached
      // turn is NOT cancelled — it finishes and persists server-side.
      cancel() {},
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
