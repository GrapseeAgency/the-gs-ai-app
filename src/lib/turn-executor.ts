/**
 * TURN EXECUTOR — the turn pipeline, extracted verbatim from the messages
 * route (PHASE 1 — detached turn execution, ADR-117 pattern).
 *
 * ONE pipeline, two transports:
 *   - legacy route  → push = in-memory queue (unchanged inline SSE / JSON)
 *   - detached run  → push = stream-broker publish (POST /messages/start +
 *                     GET /api/v1/stream/{streamId}); the HTTP connection is
 *                     a mere subscriber and can die without touching the run.
 *
 * NO prompt changes, NO routing changes — every decision (capability gate,
 * router, planner, guards, evidence assembly) is the code that already
 * shipped, moved into this module so the detached producer executes the
 * identical turn.
 */

import { NextResponse } from 'next/server'
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
import { getBackendRevision } from '@/lib/revision'
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
import { runSilentFailureGate } from '@/lib/silent-failure-gate'
import {
  detectOutputDirective,
  enforceDirective,
  buildDirectiveMessages,
  buildDirectiveRetryMessages,
  type OutputDirective,
} from '@/lib/output-directive'
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
import { runBranchingResearch } from '@/lib/research/branching'
import {
  NOOP_EMIT,
  type ClarifyOption,
  type ResearchSource,
  type SearchEventEmitter,
  type SearchIntent,
  type TimeRange,
} from '@/lib/search/types'
import { MAX_ATTACHMENTS_PER_MESSAGE } from '@/lib/attachments'
import { appendEvent, tracked } from '@/lib/turn-events'
import { getRun } from '@/lib/run-store'

const HISTORY_LIMIT = 20
const MAX_CONTENT_LENGTH = 32000
const DEFAULT_TITLE = 'New chat'
const CURRENT_APP_VERSION = '0.68.2'

export type TurnPush = (event: string, data: string) => void

export type TurnRequest = {
  conversationId: string
  content: string
  timezone?: string | null
  clientVersion: string
  requestId: string
  attachmentIds: string[]
  /** Detached mode: run-store correlation id (billing/HITL). */
  runId?: string
  /**
   * Resume mode: the user message for this turn already exists (persisted by
   * the original start) — reuse it instead of creating a duplicate.
   */
  existingUserMessageId?: string
}

export type Prepared =
  | { kind: 'invalid'; response: NextResponse }
  | { kind: 'blocked'; response: NextResponse }
  | { kind: 'ready'; prep: Prep }

// The prepared turn: everything the request head computed, the pipeline consumes.
export type Prep = {
  conversationId: string
  content: string
  userMessage: { id: string }
  conversation: { id: string; assistantId: string | null; title: string }
  baseSystemPrompt: string
  history: {
    id: string
    role: string
    content: string
    attachments: { id: string; kind: string; displayName: string }[]
  }[]
  historyRowText: (m: Prep['history'][number]) => string
  attachmentsProvided: boolean
  attachmentIds: string[]
  claimedAttachments: { kind: string; displayName: string }[]
  imageAttachments: { kind: string; displayName: string }[]
  currentTurnDocs: { kind: string }[]
  useVision: boolean
  recentImageTurnIds: string[]
  visionPrepared: PreparedVisionImage[]
  visionFailures: VisionImageFailure[]
  allImagesFailed: boolean
  docContext: { block: string | null; failures: { displayName?: string }[]; readableCount: number }
  allDocsFailed: boolean
  webGate: { trigger: 'explicit' | 'recency' | null }
  previousClarifyOptions: ClarifyOption[] | null
  clarifyFollowUp: boolean
  historicalReligious: boolean
  numericCheck: boolean
  directive: OutputDirective | null
  clientTimezone?: string | null
  cap: ReturnType<typeof decideCapability>
  requestId: string
  turnStartedAt: number
  clientVersion: string
  backendRevision: string
  routerPlan: ReturnType<typeof planRoute>
  plannerRuns: boolean
  modelRoute: ModelRoute
  providerModel: string | null
  openRouterModels: string[] | null
  freeAvailable: boolean
  historyWebSources: WebSource[]
  includeHistoryEvidence: boolean
  historyLines: string[]
  runId?: string
  gsCapLog: (
    finalStatus: string,
    turn: TurnSearch | null,
    extra?: { cited?: number[]; error?: string; answer?: string }
  ) => void
}

export type TurnSearch =
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

export type TurnResult = {
  finalStatus: 'done' | 'error' | 'clarify'
  userMessageId: string
  assistantMessageId?: string
  /** json-transport result rows (message with sources) for the legacy route */
  savedMessage?: Record<string, unknown>
  /** pre-turn 4xx/5xx mapping (all-images/docs unreadable, upstream error) */
  errorResponse?: { status: number; code: string; message: string }
  errorRaw?: string
}

// ---------------------------------------------------------------------------
// PREPARE — request validation + user-message persistence + context build
// (verbatim move of the messages-route pre-work)
// ---------------------------------------------------------------------------

export async function prepareTurn(
  args: TurnRequest,
  rawBody: { content?: unknown; attachments?: unknown }
): Promise<Prepared> {
  const id = args.conversationId
  const content = args.content

  // Validate every attachment up-front: must exist, belong to this
  // conversation (or be unbound), and not already be attached to a message.
  const attachmentsProvided = args.attachmentIds.length > 0
  let claimedAttachments: Awaited<ReturnType<typeof db.attachment.findMany>> = []
  if (attachmentsProvided) {
    claimedAttachments = await db.attachment.findMany({ where: { id: { in: args.attachmentIds } } })
    if (claimedAttachments.length !== args.attachmentIds.length) {
      return {
        kind: 'invalid',
        response: NextResponse.json(
          { code: 'invalid_attachments', message: 'One or more attachments do not exist' },
          { status: 400 }
        ),
      }
    }
    const foreign = claimedAttachments.find(
      (a) => (a.conversationId !== null && a.conversationId !== id) || a.messageId !== null
    )
    if (foreign) {
      return {
        kind: 'invalid',
        response: NextResponse.json(
          { code: 'invalid_attachments', message: 'One or more attachments are already in use' },
          { status: 400 }
        ),
      }
    }
  }

  const imageAttachments = claimedAttachments.filter((a) => a.kind === 'image')

  const conversation = await db.conversation.findUnique({
    where: { id },
    include: { assistant: true },
  })
  if (!conversation) {
    return {
      kind: 'invalid',
      response: NextResponse.json(
        { code: 'not_found', message: 'Conversation not found' },
        { status: 404 }
      ),
    }
  }

  const shouldAutoTitle = conversation.title === DEFAULT_TITLE

  // RESUME SEMANTICS: a resumed run reuses the user message persisted by the
  // original start — the side effect already happened, it must not happen
  // twice (the idempotency guarantee, at the persistence layer).
  const userMessage = args.existingUserMessageId
    ? (await db.message.findUnique({ where: { id: args.existingUserMessageId } })) ??
      (await db.message.create({ data: { conversationId: id, role: 'user', content } }))
    : await db.message.create({ data: { conversationId: id, role: 'user', content } })
  if (attachmentsProvided && !args.existingUserMessageId) {
    await db.attachment.updateMany({
      where: { id: { in: args.attachmentIds } },
      data: { messageId: userMessage.id, conversationId: id },
    })
  }
  if (shouldAutoTitle && !args.existingUserMessageId) {
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

  const baseSystemPrompt = conversation.assistant?.instructions
    ? `${conversation.assistant.instructions}\n\n(Platform persona base: ${SYSTEM_PROMPT})`
    : SYSTEM_PROMPT

  const recentDesc = await db.message.findMany({
    where: { conversationId: id, id: { not: userMessage.id } },
    orderBy: { createdAt: 'desc' },
    take: HISTORY_LIMIT,
    include: { attachments: { orderBy: { createdAt: 'asc' } } },
  })
  const history = recentDesc.reverse()

  const historyRowText = (m: (typeof history)[number]): string => {
    if (m.role.toLowerCase() !== 'user' || m.content.trim().length > 0) return m.content
    if (m.attachments.some(isDocumentAttachment)) return DOCUMENT_ONLY_DEFAULT
    if (m.attachments.some((a) => a.kind === 'image')) return 'Describe this image.'
    return m.content
  }

  const recentImageTurnIds = recentDesc
    .filter(
      (m) => m.role.toLowerCase() === 'user' && m.attachments.some((a) => a.kind === 'image')
    )
    .slice(0, VISION_HISTORY_IMAGE_TURNS)
    .map((m) => m.id)
  const useVision = imageAttachments.length > 0 || recentImageTurnIds.length > 0

  const currentTurnDocs = claimedAttachments.filter(isDocumentAttachment)
  const historyDocTurns = recentDesc
    .filter((m) => m.role.toLowerCase() === 'user' && m.attachments.some(isDocumentAttachment))
    .slice(0, DOC_HISTORY_TURNS)
    .map((m) => ({ attachments: m.attachments.filter(isDocumentAttachment) }))
  const docContext =
    currentTurnDocs.length > 0 || historyDocTurns.length > 0
      ? await collectDocumentContext(currentTurnDocs, historyDocTurns, content)
      : { block: null as string | null, failures: [], readableCount: 0 }
  const allDocsFailed =
    currentTurnDocs.length > 0 && docContext.readableCount === 0 && imageAttachments.length === 0

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
  const clarifyFollowUp =
    previousClarifyOptions !== null && content.trim().length > 0 && content.length <= 60

  const historicalReligious = content.trim().length > 0 ? detectHistoricalReligious(content) : false
  const numericCheck = content.trim().length > 0 && isCountingRequest(content)
  const directive = content.trim().length > 0 ? detectOutputDirective(content) : null
  const clientTimezone = args.timezone ?? null
  const cap = decideCapability({
    content,
    hasImages: useVision,
    hasDocuments: currentTurnDocs.length > 0 || historyDocTurns.length > 0,
    historicalReligious,
  })

  const requestId = args.requestId
  const turnStartedAt = Date.now()
  const clientVersion = args.clientVersion
  const backendRevision = await getBackendRevision()
  const versionMismatch =
    clientVersion !== 'web' && clientVersion !== CURRENT_APP_VERSION
  if (versionMismatch) {
    console.warn(
      `GS-VERSION-MISMATCH requestId=${requestId} conv=${id} clientVersion=${clientVersion} currentAppVersion=${CURRENT_APP_VERSION} backendRevision=${backendRevision} — audit results may reflect an older client`
    )
  }

  const routerPlan = planRoute({
    content,
    hasImages: useVision,
    hasDocuments: currentTurnDocs.length > 0 || historyDocTurns.length > 0,
    webGateTrigger: cap.searchPlanned ? 'explicit' : webGate.trigger,
    clarifyFollowUp,
    historicalReligious,
    historyChars: history.reduce((n, m) => n + m.content.length, 0),
    trafficKey: requestId,
  })
  const plannerRuns = routerPlan.plannerRuns
  console.log(
    `GS-ROUTER conv=${id} route=${routerPlan.route} depth=${routerPlan.depth} reason=${routerPlan.reason} capability=${cap.capability} trigger=${cap.trigger ?? 'none'} forced=${cap.searchPlanned}`
  )
  console.log(
    `GS-CAPABILITY conv=${id} requiredCapability=${routerPlan.capability} model=${routerPlan.internalModelId} providerLock=${routerPlan.providerLock ?? 'none'} measured=${routerPlan.measuredScore !== null ? routerPlan.measuredScore.toFixed(3) : 'unmeasured'}`
  )

  const keypool = await loadKeyPool()
  const freeAvailable = keypool.keys.length > 0
  const forceFree = process.env.GS_FORCE_FREE_CHAIN === '1'
  const lock = routerPlan.providerLock
  const tierFreeEligible =
    routerPlan.route === 'TEXT_SIMPLE' || routerPlan.route === 'WEB' || routerPlan.registerClass
  const forceFreeEligible =
    forceFree &&
    (tierFreeEligible ||
      routerPlan.route === 'TEXT_COMPLEX' ||
      routerPlan.route === 'CODING' ||
      routerPlan.route === 'DEEP_RESEARCH')
  const baseRoute = resolveModelRoute(routerPlan.internalModelId)
  const freeChain = forceFreeEligible
    ? routerPlan.route === 'TEXT_SIMPLE' && !routerPlan.registerClass
      ? OPENROUTER_MODELS['gs-free']
      : OPENROUTER_MODELS['gs-free-big']
    : lock === 'openrouter'
      ? OPENROUTER_MODELS[routerPlan.internalModelId] ?? OPENROUTER_MODELS['gs-free-big']
      : lock === 'zai'
        ? null
        : freeAvailable && tierFreeEligible
          ? routerPlan.route === 'TEXT_SIMPLE' && !routerPlan.registerClass
            ? OPENROUTER_MODELS['gs-free']
            : OPENROUTER_MODELS['gs-free-big']
          : null
  const modelRoute: ModelRoute = freeChain
    ? { backend: 'openrouter', models: freeChain }
    : baseRoute
  const providerModel = modelRoute.backend === 'zai' ? modelRoute.providerModel : null
  const openRouterModels = modelRoute.backend === 'openrouter' ? modelRoute.models : null
  if (freeChain) {
    console.log(
      `GS-FREE-ROUTE conv=${id} route=${routerPlan.route} chain=${freeChain.join('|')} keys=${keypool.keys.length}${forceFree ? ' forced=1' : ''}${routerPlan.registerClass ? ' register=1' : ''}${lock ? ` lock=${lock}` : ''}`
    )
  }

  // The forensic log + trace writer, attached to prep (both transports share it).
  const prep: Prep = {
    conversationId: id,
    content,
    userMessage,
    conversation: { id: conversation.id, assistantId: conversation.assistantId, title: conversation.title },
    baseSystemPrompt,
    history,
    historyRowText,
    attachmentsProvided,
    attachmentIds: args.attachmentIds,
    claimedAttachments,
    imageAttachments,
    currentTurnDocs,
    useVision,
    recentImageTurnIds,
    visionPrepared: [],
    visionFailures: [],
    allImagesFailed: false,
    docContext,
    allDocsFailed,
    webGate,
    previousClarifyOptions,
    clarifyFollowUp,
    historicalReligious,
    numericCheck,
    directive,
    clientTimezone,
    cap,
    requestId,
    turnStartedAt,
    clientVersion,
    backendRevision,
    routerPlan,
    plannerRuns,
    modelRoute,
    providerModel,
    openRouterModels,
    freeAvailable,
    historyWebSources: [],
    includeHistoryEvidence: false,
    historyLines: [],
    runId: args.runId,
    gsCapLog: () => undefined, // attached below
  }

  prep.gsCapLog = buildGsCapLog(prep)

  // CAPABILITY REGISTRY — BLOCKED guard (verbatim; unreachable while every
  // capability has a measured model, but the honest refusal is preserved).
  if (routerPlan.route === 'BLOCKED') {
    prep.gsCapLog('blocked', null, { error: routerPlan.reason })
    console.warn(`GS-ROUTER-BLOCKED conv=${id} reason=${routerPlan.reason}`)
    return {
      kind: 'blocked',
      response: NextResponse.json(
        {
          code: 'no_measured_model',
          message: 'This turn needs a capability no serving model is measured for yet. It was refused rather than answered badly.',
        },
        { status: 503 }
      ),
    }
  }

  let historyWebSources: WebSource[] = []
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
  prep.historyWebSources = historyWebSources
  prep.includeHistoryEvidence = includeHistoryEvidence

  prep.historyLines = history
    .slice(-8)
    .map((m) => `${m.role.toLowerCase() === 'assistant' ? 'assistant' : 'user'}: ${historyRowText(m).slice(0, 220)}`)
  console.log(
    `WEBSEARCH-GATE conv=${id} trigger=${webGate.trigger ?? 'none'} planner=${plannerRuns} clarifyFollowUp=${clarifyFollowUp} historySources=${historyWebSources.length} historyInject=${includeHistoryEvidence}`
  )

  if (imageAttachments.length > 0) {
    for (const att of imageAttachments) {
      const result = await prepareVisionImage(att)
      if (result.ok) prep.visionPrepared.push(result.image)
      else prep.visionFailures.push(result.failure)
    }
    if (prep.visionPrepared.length === 0) {
      prep.allImagesFailed = true
    }
  }

  // Catalogue telemetry — count a use when this is the first message.
  if (conversation.assistantId && history.length === 0) {
    db.assistant
      .update({ where: { id: conversation.assistantId }, data: { uses: { increment: 1 } } })
      .catch(() => undefined)
  }

  return { kind: 'ready', prep }
}

/** §14 forensic log + trace row — shared by both transports. */
function buildGsCapLog(prep: Prep): Prep['gsCapLog'] {
  const { requestId, conversationId: id, userMessage, clientVersion, backendRevision, cap, modelRoute, routerPlan } = prep
  return (finalStatus, turn, extra) => {
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
    const freshSources = turn?.kind === 'research' ? turn.outcome.sources : []
    const reuseSources = turn?.kind === 'reuse' ? turn.sources : []
    const gateSourcesRead =
      turn?.kind === 'research'
        ? freshSources.filter((s) => s.status === 'retrieved').length
        : turn?.kind === 'reuse'
          ? turn.sources.length
          : 0
    const gateVerdict = runSilentFailureGate({
      turnKind: (turn?.kind ?? 'none') as 'research' | 'reuse' | 'time' | 'clarify' | 'none',
      finalStatus,
      searchExecuted: turn?.kind === 'research',
      noResults:
        turn?.kind === 'research' &&
        (turn.outcome.failure?.kind === 'no_results' ||
          (!turn.outcome.ok && turn.outcome.sources.length === 0)),
      registerClass: routerPlan.registerClass,
      sourceCount,
      sourcesRead: gateSourcesRead,
      sourcesFailed: freshSources.filter((s) => s.status === 'failed').length,
      evidenceCount,
      citationCount: extra?.cited ? new Set(extra.cited).size : 0,
      evidenceBlock:
        turn?.kind === 'research'
          ? turn.evidenceBlock
          : turn?.kind === 'reuse'
            ? turn.evidenceBlock
            : null,
      answerText: extra?.answer ?? null,
    })
    if (!gateVerdict.ok) {
      console.error(
        `GS-SILENT-FAILURE requestId=${requestId} conv=${id} msg=${userMessage.id} capability=${cap.capability} modelRoute=${modelRoute.backend}/${routerPlan.internalModelId} finalStatus=${finalStatus} violations=[${gateVerdict.violations.join(' | ')}]`
      )
    }
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
      latencyMs: Date.now() - prep.turnStartedAt,
      errorType:
        finalStatus === 'done' || finalStatus === 'clarify'
          ? null
          : classifyErrorType(extra?.error ?? null),
      silentFailures: gateVerdict.violations,
    })
  }
}

// ---------------------------------------------------------------------------
// RUN — the bounded capability turn (verbatim move of the route body)
// ---------------------------------------------------------------------------

const MAX_GUARD_ATTEMPTS = 3

export async function runTurn(prep: Prep, push: TurnPush | null): Promise<TurnResult> {
  const id = prep.conversationId
  const content = prep.content
  const userMessage = prep.userMessage
  const cap = prep.cap
  const routerPlan = prep.routerPlan
  const modelRoute = prep.modelRoute
  const providerModel = prep.providerModel
  const openRouterModels = prep.openRouterModels
  const freeAvailable = prep.freeAvailable
  const gsCapLog = prep.gsCapLog
  const history = prep.history
  const historyRowText = prep.historyRowText
  const docContext = prep.docContext
  const useVision = prep.useVision
  const numericCheck = prep.numericCheck
  const directive = prep.directive
  const clientTimezone = prep.clientTimezone
  const allImagesFailed = prep.allImagesFailed
  const allDocsFailed = prep.allDocsFailed
  const historyWebSources = prep.historyWebSources
  const includeHistoryEvidence = prep.includeHistoryEvidence
  const historyLines = prep.historyLines

  // PHASE 5 stop condition — a cancelled run synthesizes immediately with
  // whatever the ledger already holds (checked between search phases).
  const runStopFlag = (runId: string): boolean => {
    // synchronous flag cache refreshed by a background read; DB probes are
    // only made between branches, never inside hot loops
    return stopFlagCache.get(runId) === true
  }
  const stopFlagCache = new Map<string, boolean>()
  if (prep.runId) {
    const runId = prep.runId
    void (async () => {
      for (;;) {
        await new Promise((r) => setTimeout(r, 2_000))
        try {
          const run = await getRun(runId)
          if (!run) return
          stopFlagCache.set(runId, run.status === 'cancelled')
          if (run.status === 'cancelled') return
        } catch {
          return
        }
      }
    })()
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
    // PHASE 5 — LEDGER-DRIVEN BRANCHING SEARCH for deep turns: the question
    // is decomposed into sub-questions, each runs as a parallel branch, and
    // the Research Ledger (claims/gaps/contradictions) drives EAQL follow-up
    // queries. Quick turns keep the proven linear pipeline. GS_LEDGER_SEARCH
    // =off reverts to linear (instant rollback). Output type is identical —
    // evidence assembly and synthesis are untouched.
    const useLedgerSearch =
      params.depth === 'deep' && process.env.GS_LEDGER_SEARCH !== 'off'
    const deadlineAt = Date.now() + RESEARCH_BUDGET[params.depth].wallClockMs + 2_000
    const outcome = useLedgerSearch
      ? await runBranchingResearch({
          question: content,
          queries: params.queries,
          intent: params.intent,
          timeRange: params.timeRange,
          region: params.region,
          sourceHint: params.sourceHint,
          officialOnly: params.officialOnly,
          depth: params.depth,
          emit: emit ?? NOOP_EMIT,
          deadlineAt,
          ledgerKey: prep.runId ?? requestId,
          isStopped: prep.runId
            ? () => {
                // stop condition: user cancelled the run → synthesize now
                return runStopFlag(prep.runId!)
              }
            : undefined,
        })
      : await runResearch({
          queries: params.queries,
          intent: params.intent,
          timeRange: params.timeRange,
          region: params.region,
          sourceHint: params.sourceHint,
          officialOnly: params.officialOnly,
          depth: params.depth,
          emit: emit ?? NOOP_EMIT,
          deadlineAt,
        })
    console.log(
      `RESEARCH conv=${id} intent=${params.intent} queries=${outcome.queriesRun.length} sources=${outcome.sources.length} retrieved=${outcome.sources.filter((s) => s.status === 'retrieved').length} engines=${outcome.enginesUsed.join('+') || 'none'} ok=${outcome.ok} kind=${outcome.failure?.kind ?? '-'}${useLedgerSearch ? ' ledger=on' : ''}`
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

    if (cap.capability === 'TIME') {
      console.log(`GS-TIME conv=${id} tz=${validTimeZone(clientTimezone) ?? 'UTC'}`)
      return { kind: 'time', evidence: buildTimeEvidence(clientTimezone) }
    }

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

    if (!prep.plannerRuns) return { kind: 'none' }

    emit?.('status', 'searching')

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
      previousClarifyOptions: prep.previousClarifyOptions,
    })
    console.log(
      `SEARCH-PLAN conv=${id} intent=${plan.intent} needs=${plan.needsSearch} ambiguous=${plan.ambiguity.isAmbiguous} queries=[${plan.queries.join(' | ')}] range=${plan.timeRange} depth=${plan.depth} stop=${plan.stopSignal} forced=${cap.searchPlanned}`
    )

    if (plan.ambiguity.isAmbiguous && plan.ambiguity.prompt && !cap.searchPlanned) {
      return { kind: 'clarify', question: plan.ambiguity.prompt.question, options: plan.ambiguity.prompt.options }
    }

    if ((!plan.needsSearch || plan.stopSignal) && !cap.searchPlanned) return { kind: 'none' }

    if (cap.searchPlanned) {
      plan.needsSearch = true
      plan.ambiguity = { isAmbiguous: false, prompt: null }
      plan.stopSignal = false
      if (cap.policy.sourceHint) plan.sourceHint = cap.policy.sourceHint
      if (cap.policy.officialOnly) plan.officialOnly = true
      if (cap.policy.timeRange) plan.timeRange = cap.policy.timeRange
      if (plan.queries.length === 0) {
        const q =
          prep.webGate.trigger !== null
            ? extractSearchQuery(content, prep.webGate.trigger)
            : content.replace(/\s+/g, ' ').trim().slice(0, 120)
        plan.queries = [q].filter((x) => x.length >= 3)
      }
      if (plan.queries.length === 0) return { kind: 'none' }
    }

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

    let toolResultBlock: string | null = null
    if (turn.kind === 'time') toolResultBlock = turn.evidence.block
    else if (turn.kind === 'reuse') toolResultBlock = turn.evidenceBlock
    else if (turn.kind === 'research') {
      toolResultBlock = researchOk
        ? buildResearchToolResultBlock(turn.outcome)
        : buildResearchFailureToolResultBlock(turn.outcome)
    }

    const systemPrompt = prep.baseSystemPrompt

    const verbatimRequest =
      content.trim().length > 0
        ? content
        : prep.currentTurnDocs.length > 0
          ? DOCUMENT_ONLY_DEFAULT
          : useVision
            ? 'Describe this image.'
            : ''

    const evidenceBlocks = [docContext.block, historyBlock?.block ?? null, toolResultBlock]

    if (useVision && !allImagesFailed) {
      const textPayload = content.trim().length > 0 ? content : 'Describe this image.'
      const notes: string[] = []
      if (prep.visionFailures.length > 0) {
        notes.push(...prep.visionFailures.map(visionFailureMessage))
      }
      const finalText =
        notes.length > 0 ? `${textPayload}\n\n(${notes.join(' ')})` : textPayload

      const selectedTurnIds = new Set(prep.recentImageTurnIds)
      let imageBudget = VISION_MAX_IMAGES_PER_REQUEST - prep.visionPrepared.length

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
            const result = await prepareVisionImage(a as never)
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
      const presentEvidence = evidenceBlocks.filter((b): b is string => !!b)
      const visionUserParts: VisionContentPart[] = [
        ...(presentEvidence.length > 0
          ? [{ type: 'text' as const, text: `${presentEvidence.join('\n\n')}\n\n${TOOL_RESULT_END_MARKER}` }]
          : []),
        { type: 'text', text: finalText },
        ...prep.visionPrepared.map((p) => ({ type: 'image_url' as const, image_url: { url: p.dataUrl } })),
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

    let finalUserContent = buildFinalUserTurn({ verbatim: verbatimRequest, blocks: evidenceBlocks })
    if (prep.attachmentsProvided && evidenceBlocks.every((b) => !b)) {
      finalUserContent = `${finalUserContent}${finalUserContent.length > 0 ? '\n\n' : ''}[The user attached ${prep.claimedAttachments
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
    for (const n of parseCitedOrdinals(text, totalOrdinals)) {
      if (n > freshCount) allowed.add(n)
    }
    return sanitizeCitationMarkers(text, totalOrdinals, allowed)
  }

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

  const visionErrorMessage = (raw: string): string => {
    if (isProviderImageRejection(raw)) {
      return 'The image could not be processed — try a JPG, PNG or WebP version.'
    }
    return 'Image understanding is unavailable right now. Please try again.'
  }

  const userFacingTurnError = (raw: string): string => {
    console.error(`TURN-ERROR conv=${id}: ${raw.slice(0, 300)}`)
    return 'GS AI is temporarily unavailable. Please try again shortly.'
  }

  const synthesizeRaw = async (
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

  const executionStateFor = (turn: TurnSearch): ExecutionState => ({
    capability: cap.capability,
    searchExecuted: turn.kind === 'research',
    evidenceProvided: turn.kind === 'research' && turn.outcome.ok && turn.evidenceBlock !== null,
    timeProvided: turn.kind === 'time',
    documentProvided: !!docContext.block,
    reuseProvided: turn.kind === 'reuse',
  })

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

  const emit: SearchEventEmitter | null = push
    ? (event, payload) => {
        push(event, JSON.stringify(payload))
        // DURABLE EXECUTION — source-level facts ride the event log when the
        // turn is a run (SourceOpened / SourceRead from the REAL wire events).
        if (prep.runId && event === 'source') {
          const p = payload as { type?: string; ordinal?: number; chars?: number }
          if (p.type === 'opening') void appendEvent(prep.runId, 'SourceOpened', { ordinal: p.ordinal })
          if (p.type === 'read')
            void appendEvent(prep.runId, 'SourceRead', { ordinal: p.ordinal, chars: p.chars ?? null })
        }
      }
    : null

  // DURABLE EXECUTION — every model call, tool call, and side effect is
  // appended to the log BEFORE and AFTER it happens (run-scoped only; the
  // legacy inline path runs without a runId and logs nothing).
  let llmStep = 0
  const synthesize = async (
    modelMessages: ChatMessageInput[] | VisionChatMessage[],
    onDelta?: (d: string) => void
  ): Promise<string> => {
    if (!prep.runId) return synthesizeRaw(modelMessages, onDelta)
    return tracked(
      prep.runId,
      { request: 'LLMCallRequested', completed: 'LLMCallCompleted', failed: 'LLMCallFailed' },
      { step: ++llmStep, kind: useVision ? 'vision' : 'text' },
      () => synthesizeRaw(modelMessages, onDelta)
    )
  }
  const executeSearchLogged = async (emit: SearchEventEmitter | null): Promise<TurnSearch> => {
    if (!prep.runId) return executeSearchPhase(emit)
    return tracked(
      prep.runId,
      { request: 'SearchRequested', completed: 'SearchCompleted', failed: 'ToolCallFailed' },
      { capability: cap.capability },
      () => executeSearchPhase(emit)
    )
  }
  /** The ONE side effect of a turn: persisting the assistant answer. */
  const createAssistantMessage = async (data: {
    conversationId: string
    role: string
    content: string
    clarifyOptions?: string
  }) => {
    if (!prep.runId) return db.message.create({ data })
    return tracked(
      prep.runId,
      { request: 'ToolCallRequested', completed: 'ToolCallCompleted', failed: 'ToolCallFailed' },
      { tool: 'persist_assistant_message', userMessageId: prep.userMessage.id },
      async () => {
        // Idempotency across restarts: if a prior attempt already persisted
        // this run's answer, reuse it — one answer exists, not two.
        const run = await getRun(prep.runId!)
        if (run?.assistantMessageId) {
          const existing = await db.message.findUnique({ where: { id: run.assistantMessageId } })
          if (existing) return existing
        }
        return db.message.create({ data })
      },
      { tool: 'persist_assistant_message' }
    )
  }

  const result: TurnResult = { finalStatus: 'error', userMessageId: userMessage.id }

  // §3/§16 — pre-turn honesty gates (verbatim from the route): all images or
  // all documents unreadable is a 422-class outcome, never a fabricated answer.
  if (allImagesFailed) {
    gsCapLog('error', null, { error: 'all image attachments unreadable' })
    const message = `None of the attached images could be opened. ${prep.visionFailures
      .map(visionFailureMessage)
      .join(' ')}`
    if (push) push('error', message)
    result.finalStatus = 'error'
    result.errorRaw = 'all image attachments unreadable'
    result.errorResponse = { status: 422, code: 'unsupported_media', message }
    return result
  }
  if (allDocsFailed) {
    gsCapLog('error', null, { error: 'all document attachments unreadable' })
    const message = docContext.failures.map(documentFailureMessage).join(' ')
    if (push) push('error', message)
    result.finalStatus = 'error'
    result.errorRaw = 'all document attachments unreadable'
    result.errorResponse = { status: 422, code: 'document_unreadable', message }
    return result
  }

  // ---- the turn (verbatim from the route's stream body; the json body is
  // the same pipeline with push === null and response mapping) -----------
  try {
    const turn = await executeSearchLogged(emit)

    if (turn.kind === 'clarify') {
      emit?.('clarify', { question: turn.question, options: turn.options })
      const saved = await createAssistantMessage({
        conversationId: id,
        role: 'assistant',
        content: turn.question,
        clarifyOptions: JSON.stringify({ question: turn.question, options: turn.options }),
      })
      gsCapLog('clarify', turn)
      if (push) push('done', JSON.stringify(messageToJson(saved)))
      result.finalStatus = 'clarify'
      result.assistantMessageId = saved.id
      result.savedMessage = messageToJson(saved) as unknown as Record<string, unknown>
      return result
    }

    const researchOk = turn.kind === 'research' && turn.outcome.ok && turn.evidenceBlock !== null
    if (push && turn.kind === 'research') {
      push('status', researchOk ? 'composing' : 'search_failed')
      if (researchOk) {
        emit?.('research', { type: 'synthesis_started' })
      }
    }

    const { messages: modelMessages, historySources, userTurnText } = await buildModelMessages(turn)
    let full: string
    if (turn.kind !== 'none' || docContext.block) {
      const { text, violation } = await synthesizeValidated(turn, modelMessages, userTurnText)
      if (violation) {
        console.log(`GS-GUARD conv=${id} emitted-after-retries violation=${violation.kind}`)
      }
      full = text
      if (push) for (const chunk of chunkDeltas(full)) push('delta', chunk)
    } else if (numericCheck) {
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
      if (push) for (const chunk of chunkDeltas(full)) push('delta', chunk)
    } else if (directive) {
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
      if (push) for (const chunk of chunkDeltas(full)) push('delta', chunk)
    } else if (push) {
      full = await synthesize(modelMessages, (d) => push('delta', d))
    } else {
      full = await synthesize(modelMessages)
    }

    const finalText = sanitizeAgainstPersisted(normalizeCitationBrackets(full), turn, historySources)
    const saved = await createAssistantMessage({
      conversationId: id,
      role: 'assistant',
      content: finalText,
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
    if (push && turn.kind === 'research' && turn.outcome.ok) {
      const totalOrdinals = turn.maxOrdinal + historySources.length
      const usedCitations = parseCitedOrdinals(finalText, totalOrdinals)
      emit?.('search', {
        type: 'completed',
        queries: turn.outcome.queriesRun.length,
        sources: turn.outcome.sources.length,
        retrieved: turn.outcome.sources.filter((s) => s.status === 'retrieved').length,
        usedCitations,
      })
      emit?.('research', {
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
      answer: finalText,
    })
    if (push) push('done', JSON.stringify(messageToJson(savedWithSources ?? saved)))
    result.finalStatus = 'done'
    result.assistantMessageId = saved.id
    result.savedMessage = messageToJson(savedWithSources ?? saved) as unknown as Record<string, unknown>
    return result
  } catch (e) {
    const raw = String(e instanceof Error ? e.message : e)
    gsCapLog('error', null, { error: raw })
    const message = useVision ? visionErrorMessage(raw) : userFacingTurnError(raw)
    if (push) push('error', message)
    result.finalStatus = 'error'
    result.errorRaw = raw
    result.errorResponse = {
      status: 502,
      code: 'upstream_error',
      message,
    }
    return result
  }
}
