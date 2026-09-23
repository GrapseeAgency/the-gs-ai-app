import { NextRequest, NextResponse } from 'next/server'
import { db } from '@/lib/db'
import { messageToJson } from '@/lib/serializers'
import { resolveModelRoute, OPENROUTER_MODELS, type ModelRoute } from '@/lib/models'
import { loadKeyPool } from '@/lib/keypool'
import { orCompleteChat, orStreamChat } from '@/lib/openrouter'
import { planRoute } from '@/lib/router'
import {
  SYSTEM_PROMPT,
  VISION_GROUNDING_PROMPT,
  streamChat,
  completeChat,
  streamVisionChat,
  completeVisionChat,
  type ChatMessageInput,
  type VisionChatMessage,
  type VisionContentPart,
} from '@/lib/ai'
import { decideCapability, validTimeZone } from '@/lib/capability'
import { classifyErrorType, writeTurnTrace } from '@/lib/trace'
import {
  recordResearchContext,
  getResearchContexts,
  findReferencedResearchContext,
  buildReuseEvidenceBlock,
  updateResearchContextCitations,
  type ResearchContext,
} from '@/lib/research-context'
import {
  TOOL_RESULT_END_MARKER,
  buildFinalUserTurn,
  buildResearchToolResultBlock,
  buildResearchFailureToolResultBlock,
  buildTimeEvidence,
  type TimeEvidence,
} from '@/lib/evidence'
import {
  detectExecutionContradiction,
  buildCorrectionInstruction,
  injectCorrection,
  type ExecutionState,
  type GuardViolation,
} from '@/lib/synthesis-guard'
import {
  isCountingRequest,
  checkNumericClaims,
  applyNumericCorrection,
  buildNumericCorrection,
} from '@/lib/numeric-guard'
import {
  detectOutputDirective,
  enforceDirective,
  buildDirectiveMessages,
  buildDirectiveRetryMessages,
  type OutputDirective,
} from '@/lib/output-directive'
import { clientKey, rateLimit } from '@/lib/rate-limit'
import { MAX_ATTACHMENTS_PER_MESSAGE } from '@/lib/attachments'
import {
  collectDocumentContext,
  documentFailureMessage,
  isDocumentAttachment,
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
  extractSearchQuery,
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
import {
  NOOP_EMIT,
  type ClarifyOption,
  type ResearchSource,
  type SearchEventEmitter,
  type SearchIntent,
  type TimeRange,
} from '@/lib/search/types'

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

  let body: { content?: unknown; stream?: unknown; modelId?: unknown; attachments?: unknown; timezone?: unknown }
  try {
    body = (await req.json()) as { content?: unknown; stream?: unknown; modelId?: unknown; attachments?: unknown; timezone?: unknown }
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

  // ---- PHASE 8.3 §2 — DETERMINISTIC CAPABILITY GATE ---------------------------
  // The capability of this turn — chat, time, web, deep research, document,
  // vision — is decided by CODE before ANY model runs (no planner, no
  // synthesis). The model is never asked "do you think you should search?"
  // and can never veto a capability the application already knows. A forced
  // search reaches execution unconditionally; the LLM planner downstream can
  // only refine queries.
  const historicalReligious = content.trim().length > 0 ? detectHistoricalReligious(content) : false
  // FORENSIC AUDIT [21] — counting-shaped chat turns take the buffered +
  // numerically-validated synthesis path (deterministic detector, zero cost).
  const numericCheck = content.trim().length > 0 && isCountingRequest(content)
  // FORENSIC AUDIT [22]/[20] — literalist output-directive turns ("just say
  // why") take the buffered directive pipeline: B rewrite → C constraint
  // retry → A deterministic strip (see src/lib/output-directive.ts).
  const directive = content.trim().length > 0 ? detectOutputDirective(content) : null
  const clientTimezone =
    typeof body.timezone === 'string' ? body.timezone : req.headers.get('x-client-timezone')
  const cap = decideCapability({
    content,
    hasImages: useVision,
    hasDocuments: currentTurnDocs.length > 0 || historyDocTurns.length > 0,
    historicalReligious,
  })
  // §14 — per-turn handshake facts for the internal forensic log.
  const requestId = crypto.randomUUID()
  // EVAL LAYER 1 — turn latency starts at the capability decision (everything
  // before this line is request parsing; everything after is the turn).
  const turnStartedAt = Date.now()
  const clientVersion = req.headers.get('x-gs-app-version') ?? 'web'
  const backendRevision = process.env.GS_BACKEND_REVISION ?? 'dev'
  // FORENSIC AUDIT [3] — stale-APK detection: every turn logs the client
  // version against the backend revision, and a version mismatch is logged as
  // a WARNING so "stale APK / stale server" investigations end in one line.
  const CURRENT_APP_VERSION = '0.68.1'
  const versionMismatch =
    clientVersion !== 'web' && clientVersion !== CURRENT_APP_VERSION
  if (versionMismatch) {
    console.warn(
      `GS-VERSION-MISMATCH requestId=${requestId} conv=${id} clientVersion=${clientVersion} currentAppVersion=${CURRENT_APP_VERSION} backendRevision=${backendRevision} — audit results may reflect an older client`
    )
  }
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
    // A code-forced search reaches the router as an explicit gate hit so the
    // turn routes onto the search-capable model — the planner cannot veto it.
    webGateTrigger: cap.searchPlanned ? 'explicit' : webGate.trigger,
    clarifyFollowUp,
    historicalReligious,
    historyChars: history.reduce((n, m) => n + m.content.length, 0),
  })
  const plannerRuns = routerPlan.plannerRuns
  console.log(
    `GS-ROUTER conv=${id} route=${routerPlan.route} depth=${routerPlan.depth} reason=${routerPlan.reason} capability=${cap.capability} trigger=${cap.trigger ?? 'none'} forced=${cap.searchPlanned}`
  )
  // Internal execution route for synthesis (models.ts mapping — OpenRouter
  // free chains bypass primary-provider quota windows; z-ai carries the
  // optional concrete model mapping + modelless fallback). INTERNAL ONLY:
  // none of these values are ever sent to a client.
  //
  // GS FREE WIRING (forensic follow-up): the OpenRouter free chains were
  // previously UNREACHABLE — no planRoute() tier maps to 'gs-free', so the
  // backend never served a turn. They are now wired as the execution backend
  // for exactly two routes, gated on keypool availability so an empty pool
  // can never break a turn:
  //   TEXT_SIMPLE (CHAT)  → 'gs-free' chain
  //   WEB (search synthesis) → 'gs-free-big' chain
  // Every other tier stays on the primary provider. OpenRouter failure with
  // nothing yet streamed degrades to the primary provider (§synthesize).
  const keypool = await loadKeyPool()
  const freeAvailable = keypool.keys.length > 0
  const baseRoute = resolveModelRoute(routerPlan.internalModelId)
  const freeChain =
    freeAvailable && (routerPlan.route === 'TEXT_SIMPLE' || routerPlan.route === 'WEB')
      ? routerPlan.route === 'WEB'
        ? OPENROUTER_MODELS['gs-free-big']
        : OPENROUTER_MODELS['gs-free']
      : null
  const modelRoute: ModelRoute = freeChain
    ? { backend: 'openrouter', models: freeChain }
    : baseRoute
  const providerModel = modelRoute.backend === 'zai' ? modelRoute.providerModel : null
  const openRouterModels = modelRoute.backend === 'openrouter' ? modelRoute.models : null
  if (freeChain) {
    console.log(
      `GS-FREE-ROUTE conv=${id} route=${routerPlan.route} chain=${freeChain.join('|')} keys=${keypool.keys.length}`
    )
  }

  // ---- PHASE 8.3 §3 — INTERNAL FORENSIC LOG ----------------------------------
  // One line per turn with the full execution facts. INTERNAL ONLY — server
  // logs, never sent to a client.
  const gsCapLog = (
    finalStatus: string,
    turn: TurnSearch | null,
    extra?: { cited?: number[]; error?: string }
  ): void => {
    const sourceCount =
      turn?.kind === 'research'
        ? turn.outcome.sources.length
        : turn?.kind === 'reuse'
          ? turn.sources.length
          : 0
    const evidenceCount =
      turn?.kind === 'research' && turn.outcome.ok
        ? turn.maxOrdinal
        : turn?.kind === 'reuse'
          ? turn.sources.length
          : turn?.kind === 'time'
            ? 1
            : 0
    console.log(
      `GS-CAP requestId=${requestId} conv=${id} msg=${userMessage.id} clientVersion=${clientVersion} backendRevision=${backendRevision} capability=${cap.capability} trigger=${cap.trigger ?? 'none'} searchExecuted=${turn?.kind === 'research'} researchExecuted=${turn?.kind === 'research'} evidenceCount=${evidenceCount} sourceCount=${sourceCount} modelRoute=${modelRoute.backend}/${routerPlan.internalModelId} finalStatus=${finalStatus}${extra?.cited ? ` cited=[${extra.cited.join(',')}]` : ''}`
    )
    // EVAL LAYER 1 — structured trace row for this turn. Queryable by the
    // eval suite (Layer 4) and the production monitor (Layer 5); graders read
    // this row, never the console. Fire-and-forget — never breaks the turn.
    const freshSources = turn?.kind === 'research' ? turn.outcome.sources : []
    const reuseSources = turn?.kind === 'reuse' ? turn.sources : []
    void writeTurnTrace({
      requestId,
      conversationId: id,
      messageId: userMessage.id,
      clientVersion,
      backendRevision,
      capability: cap.capability,
      trigger: cap.trigger ?? 'none',
      searchExecuted: turn?.kind === 'research',
      researchExecuted: turn?.kind === 'research',
      evidenceCount,
      sourceCount,
      sourcesRead: freshSources.filter((s) => s.status === 'retrieved').length,
      sourcesFailed: freshSources.filter((s) => s.status === 'failed').length,
      domains: Array.from(
        new Set([...freshSources, ...reuseSources].map((s) => s.domain).filter((d) => d.length > 0))
      ),
      modelRoute: `${modelRoute.backend}/${routerPlan.internalModelId}`,
      finalStatus,
      cited: extra?.cited ?? [],
      latencyMs: Date.now() - turnStartedAt,
      errorType:
        finalStatus === 'done' || finalStatus === 'clarify'
          ? null
          : classifyErrorType(extra?.error ?? null),
    })
  }

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
   * PHASE 8.3 — the capability turn, in one bounded execution. The capability
   * was already decided by code (decideCapability) BEFORE anything here runs;
   * the LLM planner only refines search queries and can never flip a forced
   * search off, never clarify away a forced search, and never start a search
   * on a non-search capability. `emit === null` (non-streaming path) runs the
   * identical pipeline silently.
   */
  type TurnSearch =
    | { kind: 'none'; note?: string }
    | { kind: 'clarify'; question: string; options: ClarifyOption[] }
    | { kind: 'time'; evidence: TimeEvidence }
    | {
        kind: 'reuse'
        ctx: ResearchContext
        evidenceBlock: string
        sources: WebSource[]
        researchId: string
      }
    | {
        kind: 'research'
        outcome: Awaited<ReturnType<typeof runResearch>>
        evidenceBlock: string | null
        maxOrdinal: number
        historySources: WebSource[]
        startedAt: number
        researchId: string | null
      }

  /** §9 — record every executed research as an explicit ResearchContext. */
  const recordContext = (outcome: {
    ok: boolean
    queriesRun: string[]
    sources: { ordinal: number; title: string; url: string; domain: string; snippet: string; query: string; status: string; publishedDate: string | null }[]
  }): string => {
    const researchId = `rc-${userMessage.id.slice(0, 8)}-${Date.now().toString(36)}`
    recordResearchContext(id, {
      researchId,
      originatingMessageId: userMessage.id,
      originatingUserRequest: content.trim(),
      queries: outcome.queriesRun,
      sources: outcome.sources
        .filter((s) => s.status === 'retrieved' || s.status === 'snippet_only')
        .map((s) => ({
          ordinal: s.ordinal,
          title: s.title,
          url: s.url,
          domain: s.domain,
          snippet: s.snippet,
          query: s.query,
          status: s.status as ResearchSource['status'],
          publishedDate: s.publishedDate,
        })),
      citations: [],
      createdAt: Date.now(),
      status: outcome.ok ? 'complete' : 'failed',
    })
    return researchId
  }

  /** Shared execution tail for every search execution (planner or code-driven). */
  const runSearchExecution = async (
    params: {
      queries: string[]
      intent: SearchIntent
      timeRange: TimeRange
      region?: string
      sourceHint?: string
      officialOnly?: boolean
      depth: 'quick' | 'deep'
    },
    emit: SearchEventEmitter | null,
    startedAt: number
  ): Promise<TurnSearch> => {
    emit?.('research', {
      type: 'started',
      intent: params.intent,
      depth: params.depth,
      roundsPlanned: RESEARCH_BUDGET[params.depth].maxRounds,
    })
    emit?.('search', { type: 'started', intent: params.intent, depth: params.depth, label: params.queries[0] ?? '' })
    const outcome = await runResearch({
      queries: params.queries,
      intent: params.intent,
      timeRange: params.timeRange,
      region: params.region,
      sourceHint: params.sourceHint,
      officialOnly: params.officialOnly,
      depth: params.depth,
      emit: emit ?? NOOP_EMIT,
      deadlineAt: Date.now() + RESEARCH_BUDGET[params.depth].wallClockMs + 2_000,
    })
    console.log(
      `RESEARCH conv=${id} intent=${params.intent} queries=${outcome.queriesRun.length} sources=${outcome.sources.length} retrieved=${outcome.sources.filter((s) => s.status === 'retrieved').length} engines=${outcome.enginesUsed.join('+') || 'none'} ok=${outcome.ok} kind=${outcome.failure?.kind ?? '-'}`
    )
    const researchId = recordContext(outcome)
    if (!outcome.ok || outcome.sources.length === 0) {
      emit?.('search', { type: 'failed', reason: outcome.failure?.kind ?? 'no_results' })
      emit?.('research', {
        type: 'failed',
        reason: outcome.failure?.kind ?? 'no_results',
        message: outcome.failure?.message,
      })
      return { kind: 'research', outcome, evidenceBlock: null, maxOrdinal: 0, historySources: [], startedAt, researchId }
    }
    const { block, maxOrdinal } = buildResearchEvidenceBlock(outcome.sources, outcome.queriesRun)
    return { kind: 'research', outcome, evidenceBlock: block, maxOrdinal, historySources: [], startedAt, researchId }
  }

  const executeSearchPhase = async (emit: SearchEventEmitter | null): Promise<TurnSearch> => {
    if (allImagesFailed || allDocsFailed) return { kind: 'none' }

    // §11 — TIME capability: the application clock IS the tool. No planner,
    // no search, no model opinion about whether it "knows" the time.
    if (cap.capability === 'TIME') {
      console.log(`GS-TIME conv=${id} tz=${validTimeZone(clientTimezone) ?? 'UTC'}`)
      return { kind: 'time', evidence: buildTimeEvidence(clientTimezone) }
    }

    // §10 — STOP / CONTEXT RETURN: suppress NEW search execution and provide
    // the stored ResearchContext explicitly. Code-driven — the model never
    // infers whether it should search.
    if (cap.reuseResearch) {
      const ctx = findReferencedResearchContext(content, getResearchContexts(id))
      if (ctx) {
        const reuse = buildReuseEvidenceBlock(ctx)
        console.log(
          `RESEARCH-REUSE conv=${id} ctx=${ctx.researchId} sources=${reuse.sources.length} trigger=${cap.trigger} request="${content.slice(0, 60)}"`
        )
        return {
          kind: 'reuse',
          ctx,
          evidenceBlock: reuse.block,
          sources: reuse.sources.map((s) => ({
            ordinal: s.ordinal,
            id: s.url,
            title: s.title,
            url: s.url,
            domain: s.domain,
            snippet: s.snippet,
            publishedDate: s.publishedDate,
            retrievedAt: new Date().toISOString(),
            query: s.query,
          })),
          researchId: ctx.researchId,
        }
      }
      console.log(`RESEARCH-REUSE conv=${id} trigger=${cap.trigger} requested but no stored research context — plain chat`)
      return { kind: 'none', note: 'reuse requested but no research context exists' }
    }

    if (!plannerRuns) return { kind: 'none' }

    // 2026-09-21 live audit: the search phase HAS started the moment the
    // planner runs — say so honestly so clients light up the searching state
    // immediately.
    emit?.('status', 'searching')

    // §9 — explicit RE-search ("search again using primary sources"): the
    // application locates the originating research execution, preserves the
    // requested policy change and executes a FRESH search, creating a new
    // context. Code-driven reconstruction — the model only synthesizes.
    if (cap.trigger === 'research_again') {
      const prior = getResearchContexts(id)
        .slice()
        .reverse()
        .find((c) => c.status === 'complete' && c.sources.length > 0)
      if (prior) {
        const derived = extractSearchQuery(prior.originatingUserRequest, 'explicit')
        const topic =
          derived.length >= 3 ? derived : prior.queries[0] ?? prior.originatingUserRequest
        const queries = cap.policy.primarySources
          ? [topic, `${topic} primary sources`, `${topic} original text`]
          : [topic, ...prior.queries.slice(0, 1)]
        const againDepth: 'quick' | 'deep' = routerPlan.depth === 'deep' ? 'deep' : 'quick'
        console.log(
          `RESEARCH-AGAIN conv=${id} prior=${prior.researchId} topic="${topic.slice(0, 60)}" primarySources=${cap.policy.primarySources === true}`
        )
        return await runSearchExecution(
          {
            queries: queries.filter((q) => q.trim().length >= 3).slice(0, 4),
            intent: 'research',
            timeRange: 'none',
            sourceHint: cap.policy.sourceHint,
            officialOnly: cap.policy.officialOnly,
            depth: againDepth,
          },
          emit,
          Date.now()
        )
      }
      console.log(`RESEARCH-AGAIN conv=${id} no prior research context — falling back to the planner`)
    }

    const plan = await planSearch({
      userText: content,
      historyLines,
      previousClarifyOptions,
    })
    console.log(
      `SEARCH-PLAN conv=${id} intent=${plan.intent} needs=${plan.needsSearch} ambiguous=${plan.ambiguity.isAmbiguous} queries=[${plan.queries.join(' | ')}] range=${plan.timeRange} depth=${plan.depth} stop=${plan.stopSignal} forced=${cap.searchPlanned}`
    )

    // §6/§35 — ambiguous broad request: clarify ONLY when the capability was
    // not forced. A forced capability is a COMMAND (§2) — never a question.
    if (plan.ambiguity.isAmbiguous && plan.ambiguity.prompt && !cap.searchPlanned) {
      return { kind: 'clarify', question: plan.ambiguity.prompt.question, options: plan.ambiguity.prompt.options }
    }

    // §19 — stop/reuse/no-search turns answer from what already exists —
    // unless the capability gate FORCED a search, which the planner cannot veto.
    if ((!plan.needsSearch || plan.stopSignal) && !cap.searchPlanned) return { kind: 'none' }

    // §2 — forced-capability overrides: the planner refines, code decides.
    if (cap.searchPlanned) {
      plan.needsSearch = true
      plan.ambiguity = { isAmbiguous: false, prompt: null }
      plan.stopSignal = false
      if (cap.policy.sourceHint) plan.sourceHint = cap.policy.sourceHint
      if (cap.policy.officialOnly) plan.officialOnly = true
      if (cap.policy.timeRange) plan.timeRange = cap.policy.timeRange
      if (plan.queries.length === 0) {
        const q =
          webGate.trigger !== null
            ? extractSearchQuery(content, webGate.trigger)
            : content.replace(/\s+/g, ' ').trim().slice(0, 120)
        plan.queries = [q].filter((x) => x.length >= 3)
      }
      if (plan.queries.length === 0) return { kind: 'none' }
    }

    // 8.2 §9/§25 + ARCHITECTURE LOCK — depth combines the planner's intent
    // decision with the GS Router's depth decision. The SEARCH SERVICE is
    // identical either way — only the budget differs. No model tier exists.
    const depth: 'quick' | 'deep' =
      plan.depth === 'deep' || routerPlan.depth === 'deep' ? 'deep' : 'quick'

    return await runSearchExecution(
      {
        queries: plan.queries,
        intent: plan.intent,
        timeRange: plan.timeRange,
        region: plan.region,
        sourceHint: plan.sourceHint,
        officialOnly: plan.officialOnly,
        depth,
      },
      emit,
      Date.now()
    )
  }

  /**
   * PHASE 8.3 — assemble the model messages for this turn (spec §3/§4/§7).
   *
   * Every tool/evidence block rides as a structured TOOL RESULT terminated by
   * the END marker, and the VERBATIM CURRENT USER REQUEST is always the final
   * controlling content:
   *   [document block] → [earlier web evidence] → [TOOL RESULT] →
   *   [— END OF TOOL RESULT —] → CURRENT USER REQUEST → <the user's words>
   * The model receives the machine-readable execution facts (capability,
   * execution, searchExecuted) — the application state is authoritative.
   */
  const buildModelMessages = async (
    turn: TurnSearch
  ): Promise<{
    messages: ChatMessageInput[] | VisionChatMessage[]
    systemPrompt: string
    historySources: WebSource[]
    userTurnText: string
  }> => {
    const researchOk = turn.kind === 'research' && turn.outcome.ok && turn.evidenceBlock !== null
    const freshCount = researchOk ? turn.maxOrdinal : 0
    const historyBlock = includeHistoryEvidence
      ? buildHistoryEvidenceBlock(historyWebSources, freshCount + 1)
      : null
    const historySources = historyBlock?.sources ?? []

    // §3/§4 — ONE structured TOOL RESULT per capability turn.
    let toolResultBlock: string | null = null
    if (turn.kind === 'time') toolResultBlock = turn.evidence.block
    else if (turn.kind === 'reuse') toolResultBlock = turn.evidenceBlock
    else if (turn.kind === 'research') {
      toolResultBlock = researchOk
        ? buildResearchToolResultBlock(turn.outcome)
        : buildResearchFailureToolResultBlock(turn.outcome)
    }

    // PHASE 8.3 §1 — minimal product contract. No grounding appendices, no
    // routing prose: execution state rides the TOOL RESULT, not the persona.
    const systemPrompt = baseSystemPrompt

    // The verbatim request (with the documented defaults for attachment-only turns).
    const verbatimRequest =
      content.trim().length > 0
        ? content
        : currentTurnDocs.length > 0
          ? DOCUMENT_ONLY_DEFAULT
          : useVision
            ? 'Describe this image.'
            : ''

    const evidenceBlocks = [docContext.block, historyBlock?.block ?? null, toolResultBlock]

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
        { role: 'system', content: `${systemPrompt}\n\n${VISION_GROUNDING_PROMPT}` },
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
      // §7 — evidence FIRST (terminated by the END marker), the user's
      // request LAST before the images.
      const presentEvidence = evidenceBlocks.filter((b): b is string => !!b)
      const visionUserParts: VisionContentPart[] = [
        ...(presentEvidence.length > 0
          ? [{ type: 'text' as const, text: `${presentEvidence.join('\n\n')}\n\n${TOOL_RESULT_END_MARKER}` }]
          : []),
        { type: 'text', text: finalText },
        ...visionPrepared.map((p) => ({ type: 'image_url' as const, image_url: { url: p.dataUrl } })),
      ]
      visionContext.push({ role: 'user', content: visionUserParts })
      const reuseSources = turn.kind === 'reuse' ? turn.sources : []
      return {
        messages: visionContext,
        systemPrompt,
        historySources: reuseSources.length > 0 ? reuseSources : historySources,
        userTurnText: finalText,
      }
    }

    // Text-only context. §7 — ONE user message:
    //   [evidence blocks] → [END MARKER] → CURRENT USER REQUEST → <verbatim>
    // (Plain-chat turns with no evidence keep their exact wire shape.)
    let finalUserContent = buildFinalUserTurn({ verbatim: verbatimRequest, blocks: evidenceBlocks })
    if (attachmentsProvided && evidenceBlocks.every((b) => !b)) {
      // Defensive fallback: an attachment turn that produced no readable
      // context and no images (never reachable through the error gates).
      finalUserContent = `${finalUserContent}${finalUserContent.length > 0 ? '\n\n' : ''}[The user attached ${claimedAttachments
        .map((a) => a.displayName)
        .join(', ')}. Attachment contents could not be read.]`
    }
    const reuseSources = turn.kind === 'reuse' ? turn.sources : []
    return {
      messages: [
        { role: 'system', content: systemPrompt },
        ...history.map((m) => ({ role: m.role.toLowerCase(), content: historyRowText(m) })),
        { role: 'user', content: finalUserContent },
      ],
      systemPrompt,
      historySources: reuseSources.length > 0 ? reuseSources : historySources,
      userTurnText: finalUserContent,
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
      const message = e instanceof Error ? e.message : String(e)
      if (openRouterModels) {
        // orStreamChat/orCompleteChat prefix every exhausted-chain error with
        // 'OpenRouter ' — those failures happened BEFORE any byte reached the
        // client, so the turn degrades to the primary provider transparently.
        // An unprefixed error means the stream was cut MID-FLIGHT after
        // forwarding — falling back would duplicate text, so it rethrows
        // (client recovers via the persisted-answer refetch, audit [4]/[5]).
        if (message.startsWith('OpenRouter ')) {
          console.log(
            `SYNTHESIS-FALLBACK conv=${id} OpenRouter chain exhausted (${message.slice(0, 80)}) → primary provider`
          )
          return onDelta
            ? await streamChat(msgs, onDelta, providerModel)
            : await completeChat(msgs, providerModel)
        }
        throw e
      }
      if (freeAvailable) {
        console.log(
          `SYNTHESIS-FALLBACK conv=${id} primary failed (${message.slice(0, 80)}) → OpenRouter free chain`
        )
        return onDelta
          ? await orStreamChat(msgs, OPENROUTER_MODELS['gs-free'], onDelta)
          : await orCompleteChat(msgs, OPENROUTER_MODELS['gs-free']).then((r) => r.text)
      }
      throw e
    }
  }

  // ---- PHASE 8.3 §5/§18 — post-generation execution-state validation ---------
  // The orchestration layer OWNS the execution state. A draft that contradicts
  // it ("I cannot search…") is INVALID and is regenerated with a corrected
  // evidence-state instruction before anything reaches the user. Evidence
  // turns are therefore BUFFERED: no byte streams until the text passes.

  const executionStateFor = (turn: TurnSearch): ExecutionState => ({
    capability: cap.capability,
    searchExecuted: turn.kind === 'research',
    evidenceProvided: turn.kind === 'research' && turn.outcome.ok && turn.evidenceBlock !== null,
    timeProvided: turn.kind === 'time',
    documentProvided: !!docContext.block,
    reuseProvided: turn.kind === 'reuse',
  })

  /** Replace the final user turn with the corrected version (request kept last). */
  const applyCorrection = (
    messages: ChatMessageInput[] | VisionChatMessage[],
    userTurnText: string,
    correction: string
  ): ChatMessageInput[] | VisionChatMessage[] => {
    const correctedText = injectCorrection(userTurnText, correction)
    const next = [...messages] as unknown as (ChatMessageInput | VisionChatMessage)[]
    for (let i = next.length - 1; i >= 0; i--) {
      if (next[i].role !== 'user') continue
      const m = next[i]
      if (typeof m.content === 'string') {
        next[i] = { ...m, content: correctedText }
      } else if (Array.isArray(m.content)) {
        next[i] = { ...m, content: [...m.content, { type: 'text', text: correction }] } as VisionChatMessage
      }
      break
    }
    return next as ChatMessageInput[] | VisionChatMessage[]
  }

  const MAX_GUARD_ATTEMPTS = 3

  const synthesizeValidated = async (
    turn: TurnSearch,
    modelMessages: ChatMessageInput[] | VisionChatMessage[],
    userTurnText: string
  ): Promise<{ text: string; violation: GuardViolation | null }> => {
    const state = executionStateFor(turn)
    let activeMessages = modelMessages
    let activeUserTurn = userTurnText
    let last: { text: string; violation: GuardViolation | null } = { text: '', violation: null }
    for (let attempt = 1; attempt <= MAX_GUARD_ATTEMPTS; attempt++) {
      const text = await synthesize(activeMessages)
      const violation = detectExecutionContradiction(text, state)
      last = { text, violation }
      if (!violation) return last
      console.log(
        `GS-GUARD conv=${id} attempt=${attempt}/${MAX_GUARD_ATTEMPTS} violation=${violation.kind} match="${violation.match.slice(0, 70)}"`
      )
      if (attempt === MAX_GUARD_ATTEMPTS) break
      const correction = buildCorrectionInstruction(state, violation, attempt)
      activeMessages = applyCorrection(activeMessages, activeUserTurn, correction)
      activeUserTurn = injectCorrection(activeUserTurn, correction)
    }
    return last
  }

  /** Flush a validated full text as delta events (buffered-synthesis path). */
  const chunkDeltas = (text: string): string[] => {
    const size = 140
    const chunks: string[] = []
    for (let i = 0; i < text.length; i += size) chunks.push(text.slice(i, i + size))
    return chunks.length > 0 ? chunks : ['']
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
      gsCapLog('error', null, { error: 'all image attachments unreadable' })
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
      gsCapLog('error', null, { error: 'all document attachments unreadable' })
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
        gsCapLog('clarify', turn)
        return NextResponse.json(messageToJson(saved))
      }

      const { messages: modelMessages, historySources, userTurnText } = await buildModelMessages(turn)
      // FORENSIC AUDIT [22]/[20] — literalist output-directive turns take the
      // B→C→A pipeline (rewrite → constraint retry → deterministic strip).
      // Measured live: baseline 0/3 compliant, B 3/3, C 3/3, A by construction.
      if (directive && turn.kind === 'none' && !docContext.block) {
        console.log(`GS-DIRECTIVE conv=${id} target=${directive.target} — approach B rewrite applied`)
        const dMsgs = buildDirectiveMessages(modelMessages as ChatMessageInput[], directive)
        let draft = await synthesize(dMsgs)
        const first = enforceDirective(draft, directive)
        if (first !== null) {
          console.log(`GS-DIRECTIVE conv=${id} draft non-compliant — approach C constraint retry`)
          draft = await synthesize(buildDirectiveRetryMessages(dMsgs, directive))
          const stripped = enforceDirective(draft, directive)
          if (stripped !== null) {
            console.log(`GS-DIRECTIVE conv=${id} retry non-compliant — approach A deterministic strip`)
            draft = stripped
          }
        }
        const finalText = sanitizeAgainstPersisted(normalizeCitationBrackets(draft), turn, historySources)
        const assistantMessage = await db.message.create({
          data: { conversationId: id, role: 'assistant', content: finalText },
        })
        await persistTurnSources(assistantMessage.id, finalText, turn, historySources)
        gsCapLog('done', turn)
        return NextResponse.json(messageToJson(assistantMessage))
      }
      // §5 — synthesis is validated against the authoritative execution state.
      let { text, violation } = await synthesizeValidated(turn, modelMessages, userTurnText)
      if (violation) {
        console.log(`GS-GUARD conv=${id} emitted-after-retries violation=${violation.kind}`)
      }
      // FORENSIC AUDIT [21] — numeric verification for counting-shaped turns.
      if (!violation && numericCheck && turn.kind === 'none' && !docContext.block) {
        let nv = checkNumericClaims(text, userTurnText)
        if (nv) {
          console.log(`GS-NUMERIC-GUARD conv=${id} claimed=${nv.claimed} actual=${nv.actual} unit=${nv.unit} — regenerating`)
          const corrected = applyCorrection(modelMessages, userTurnText, buildNumericCorrection(nv, 1))
          text = await synthesize(corrected)
          nv = checkNumericClaims(text, userTurnText)
          if (nv) {
            // Deterministic repair: a "Line N" count the retry STILL gets wrong
            // is rewritten locally — a provably wrong number is never emitted.
            const repaired = applyNumericCorrection(text)
            if (repaired) {
              text = repaired
              console.log(`GS-NUMERIC-GUARD conv=${id} retry still inconsistent — deterministic rewrite applied`)
            } else {
              console.log(`GS-NUMERIC-GUARD conv=${id} retry still inconsistent (claimed=${nv.claimed} actual=${nv.actual}) — emitting honest draft`)
            }
          } else {
            console.log(`GS-NUMERIC-GUARD conv=${id} retry accepted`)
          }
        }
      }
      const finalText = sanitizeAgainstPersisted(normalizeCitationBrackets(text), turn, historySources)
      const assistantMessage = await db.message.create({
        data: { conversationId: id, role: 'assistant', content: finalText },
      })
      await persistTurnSources(assistantMessage.id, finalText, turn, historySources)
      if (turn.kind === 'research' && turn.researchId) {
        updateResearchContextCitations(
          id,
          turn.researchId,
          parseCitedOrdinals(finalText, turn.maxOrdinal + historySources.length)
        )
      }
      gsCapLog('done', turn, { cited: parseCitedOrdinals(finalText, turn.kind === 'research' ? turn.maxOrdinal + historySources.length : historySources.length) })
      const saved = await db.message.findUnique({
        where: { id: assistantMessage.id },
        include: { sources: { orderBy: { ordinal: 'asc' } } },
      })
      return NextResponse.json(messageToJson(saved ?? assistantMessage))
    } catch (e) {
      const raw = e instanceof Error ? e.message : String(e)
      gsCapLog('error', null, { error: raw })
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
      gsCapLog('error', null, { error: 'all image attachments unreadable' })
      push(
        'error',
        `None of the attached images could be opened. ${visionFailures
          .map(visionFailureMessage)
          .join(' ')}`
      )
      return
    }
    if (allDocsFailed) {
      gsCapLog('error', null, { error: 'all document attachments unreadable' })
      push('error', docContext.failures.map(documentFailureMessage).join(' '))
      return
    }
    try {
      // §14 — real search activity gets real state events. SEARCHING is
      // only ever emitted for an actual search turn, WORKING only while
      // pages are actually being retrieved (emitted by runResearch).
      // PHASE 8.3 — the phase ALWAYS runs: TIME and REUSE turns execute
      // their capability here even though the LLM planner never runs.
      const turn = await executeSearchPhase(emit)

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
        gsCapLog('clarify', turn)
        push('done', JSON.stringify(messageToJson(saved)))
        return
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

      const { messages: modelMessages, historySources, userTurnText } = await buildModelMessages(turn)
      let full: string
      if (turn.kind !== 'none' || docContext.block) {
        // §5 — evidence-bearing turns are BUFFERED and validated before any
        // byte reaches the wire: a contradictory draft can never be streamed.
        const { text, violation } = await synthesizeValidated(turn, modelMessages, userTurnText)
        if (violation) {
          console.log(`GS-GUARD conv=${id} emitted-after-retries violation=${violation.kind}`)
        }
        full = text
        for (const chunk of chunkDeltas(full)) push('delta', chunk)
      } else if (numericCheck) {
        // FORENSIC AUDIT [21] — counting-shaped chat turns are BUFFERED and
        // the draft's numeric claims verified deterministically before any
        // byte reaches the wire; a wrong count regenerates ONCE with the
        // recomputed value injected (≤2 drafts total).
        let draft = await synthesize(modelMessages)
        let nv = checkNumericClaims(draft, userTurnText)
        if (nv) {
          console.log(
            `GS-NUMERIC-GUARD conv=${id} claimed=${nv.claimed} actual=${nv.actual} unit=${nv.unit} — regenerating`
          )
          const corrected = applyCorrection(
            modelMessages,
            userTurnText,
            buildNumericCorrection(nv, 1)
          )
          draft = await synthesize(corrected)
          nv = checkNumericClaims(draft, userTurnText)
          if (nv) {
            // Deterministic repair: a "Line N" count the retry STILL gets
            // wrong is rewritten locally — a provably wrong number is never
            // emitted to the user.
            const repaired = applyNumericCorrection(draft)
            if (repaired) {
              draft = repaired
              console.log(`GS-NUMERIC-GUARD conv=${id} retry still inconsistent — deterministic rewrite applied`)
            } else {
              console.log(
                `GS-NUMERIC-GUARD conv=${id} retry still inconsistent (claimed=${nv.claimed} actual=${nv.actual}) — emitting honest draft`
              )
            }
          } else {
            console.log(`GS-NUMERIC-GUARD conv=${id} retry accepted`)
          }
        }
        full = draft
        for (const chunk of chunkDeltas(full)) push('delta', chunk)
      } else if (directive) {
        // FORENSIC AUDIT [22]/[20] — literalist output-directive turns are
        // BUFFERED through the B→C→A pipeline; the shipped text is the
        // demanded token (measured live: baseline 0/3, B 3/3, C 3/3, A det.).
        console.log(`GS-DIRECTIVE conv=${id} target=${directive.target} — approach B rewrite applied`)
        const dMsgs = buildDirectiveMessages(modelMessages as ChatMessageInput[], directive)
        let draft = await synthesize(dMsgs)
        const first = enforceDirective(draft, directive)
        if (first !== null) {
          console.log(`GS-DIRECTIVE conv=${id} draft non-compliant — approach C constraint retry`)
          draft = await synthesize(buildDirectiveRetryMessages(dMsgs, directive))
          const stripped = enforceDirective(draft, directive)
          if (stripped !== null) {
            console.log(`GS-DIRECTIVE conv=${id} retry non-compliant — approach A deterministic strip`)
            draft = stripped
          }
        }
        full = draft
        for (const chunk of chunkDeltas(full)) push('delta', chunk)
      } else {
        full = await synthesize(modelMessages, (d) => push('delta', d))
      }
      const finalText = sanitizeAgainstPersisted(normalizeCitationBrackets(full), turn, historySources)
      const saved = await db.message.create({
        data: { conversationId: id, role: 'assistant', content: finalText },
      })
      await persistTurnSources(saved.id, finalText, turn, historySources)
      if (turn.kind === 'research' && turn.researchId) {
        updateResearchContextCitations(
          id,
          turn.researchId,
          parseCitedOrdinals(finalText, turn.maxOrdinal + historySources.length)
        )
      }
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
      gsCapLog('done', turn, {
        cited: parseCitedOrdinals(
          finalText,
          turn.kind === 'research' ? turn.maxOrdinal + historySources.length : historySources.length
        ),
      })
      push('done', JSON.stringify(messageToJson(savedWithSources ?? saved)))
    } catch (e) {
      const raw = String(e instanceof Error ? e.message : e)
      gsCapLog('error', null, { error: raw })
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
        // FORENSIC AUDIT [3] — every response carries the version handshake so
        // a device can always tell which client build is talking to which
        // backend revision (mismatch investigations end in one log line).
        'X-GS-App-Version': clientVersion,
        'X-GS-Backend-Revision': backendRevision,
        'X-GS-Request-Id': requestId,
      },
    }
  )
}
