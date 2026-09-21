/**
 * GS ROUTER — the backend-owned capability router (Architecture Lock, 2026-09-21).
 *
 * PRODUCT RULE: GS AI is ONE assistant experience. The user never selects a
 * model, never sees a model name, never sees a provider name, and never
 * carries a model preference. Routing is entirely the system's responsibility.
 *
 * This module is the SINGLE decision point that turns a raw request into an
 * INTERNAL execution plan: which internal model serves the turn, how much
 * retrieval budget it gets, and (via models.ts) which backend executes it.
 *
 * CONTRACT:
 *  - Every field of RouterPlan is an implementation detail. NOTHING from a
 *    plan may reach a client: not model ids, not provider names, not budgets,
 *    not route names, not reasons. The wire contract carries user intent only.
 *  - Client-sent `modelId` (legacy) is accepted for wire compatibility and
 *    IGNORED upstream — this router is the only input to routing.
 *  - Detection is deterministic and zero-latency (no LLM roundtrip): the
 *    intent planner (planner.ts) remains the search INTENT layer, this is the
 *    capability/execution layer.
 *  - Failure handling is invisible: the primary internal model maps to the
 *    primary provider; on provider failure the existing cross-provider
 *    fallback chain (messages route §synthesize + openrouter key rotation)
 *    continues transparently. The user always sees one continuous GS AI.
 */

export type CapabilityRoute =
  | 'TEXT_SIMPLE' // efficient model — greetings, short factual chat
  | 'TEXT_COMPLEX' // stronger reasoning — multi-part, analytical, long context
  | 'CODING' // coding-capable model
  | 'VISION' // vision-capable model
  | 'DOCUMENT' // document synthesis route
  | 'WEB' // search service + synthesis (search is a BACKEND capability)
  | 'DEEP_RESEARCH' // bounded research pipeline + stronger synthesis route

export interface RouterPlan {
  route: CapabilityRoute
  /** Internal catalogue id — NEVER exposed to a client. */
  internalModelId: string
  /** Research budget depth for this turn (RESEARCH_BUDGET key). */
  depth: 'quick' | 'deep'
  /** True when the intent planner must run even without a search trigger. */
  plannerRuns: boolean
  /** Observability only — server logs, never the wire. */
  reason: string
}

export interface RouteRequest {
  /** The current user message (may be empty for attachment-only turns). */
  content: string
  /** Current turn or follow-up window carries images (vision contract). */
  hasImages: boolean
  /** Current turn or follow-up window carries readable documents. */
  hasDocuments: boolean
  /** Deterministic web-search gate verdict (websearch.ts). */
  webGateTrigger: 'explicit' | 'recency' | null
  /** The previous assistant turn asked a clarification and this is a short answer to it. */
  clarifyFollowUp: boolean
  /** Historical/religious question — evidence-before-memory is a hard product rule. */
  historicalReligious: boolean
  /** Approximate serialized conversation context size (chars). */
  historyChars: number
}

// --- deterministic signal detectors -----------------------------------------

/** Explicit deep-research language: the user asked for depth, not a chat.
 *  Exported for the PHASE 8.3 deterministic capability gate (single source
 *  of truth for depth-language detection). */
export const DEEP_RESEARCH_RE =
  /\b(research|deep dive|in[\s-]depth|comprehensive|exhaustive|thorough(?:ly)?|detailed report|full report|write (?:me )?a report|deep analysis|investigat\w*|fact[\s-]check|with sources?|cite(?:d|s)? sources?)\b/i

/** Coding request markers. */
const CODING_RE =
  /(```|\bfunction\b|\bclass\b|\bbug\b|stack trace|\bcompile\w*|\bregex\b|\bdebug\w*|\brefactor\w*|\bcode\b|\bcoding\b|typescript|javascript|python|kotlin|swift\b|\bsql\b|\bapi\b|\bunit tests?\b|\brepository\b|\bimplementation\b)/i

/** Analytical / multi-step reasoning markers. */
const COMPLEX_RE =
  /\b(why|how come|analys[ei]|compare|evaluate|explain|derive|prove|step[\s-]by[\s-]step|design|architect\w*|strategy|trade[\s-]offs?|implications?|pros and cons|walk me through|break(?:ing)? it down)\b/i

/** Pure social turns — always the efficient model, whatever their length. */
const SIMPLE_RE =
  /^(hi+|hey+|hello+|yo+|sup|thanks|thank you|thx|ok(?:ay)?|cool|nice|great|got it|sounds good|bye+|good (?:morning|afternoon|evening|night)|how are you)\b[\s!.?,-]*$/i

/** Above this many chars the turn itself is "complex" (≈ a paragraph+ of ask). */
const LONG_TURN_CHARS = 600
/** Above this much serialized history the efficient model's window gets tight. */
const BIG_CONTEXT_CHARS = 24_000

/**
 * One routing decision per turn. Precedence: capability attachments
 * (vision/document) → explicit depth language → search service → coding →
 * reasoning complexity → efficient default.
 */
export function planRoute(req: RouteRequest): RouterPlan {
  const text = req.content.trim()
  const plannerTriggered =
    req.webGateTrigger !== null || req.clarifyFollowUp || req.historicalReligious

  // 1) Vision — image understanding rides the vision-capable model.
  if (req.hasImages) {
    return {
      route: 'VISION',
      internalModelId: 'gs-vision', // intentionally unmapped → provider vision default (Phase 6 design)
      depth: 'quick',
      plannerRuns: plannerTriggered,
      reason: 'images on turn',
    }
  }

  // 2) Documents — document-capable synthesis on the flagship internal model.
  //    Explicit depth language still upgrades the research budget.
  if (req.hasDocuments) {
    const deep = DEEP_RESEARCH_RE.test(text)
    return {
      route: 'DOCUMENT',
      internalModelId: 'gs-balanced',
      depth: deep ? 'deep' : 'quick',
      plannerRuns: plannerTriggered || deep,
      reason: deep ? 'documents + depth language' : 'documents on turn',
    }
  }

  // 3) Deep research — the user asked for depth: bounded research pipeline,
  //    stronger synthesis route, retrieval always on.
  if (DEEP_RESEARCH_RE.test(text)) {
    return {
      route: 'DEEP_RESEARCH',
      internalModelId: 'gs-deep',
      depth: 'deep',
      plannerRuns: true,
      reason: 'deep-research language',
    }
  }

  // 4) Web — the search gate fired: SEARCH IS A GS BACKEND CAPABILITY. The
  //    search service runs first; synthesis follows on the flagship model.
  //    The planner may still upgrade depth by intent.
  if (req.webGateTrigger !== null) {
    return {
      route: 'WEB',
      internalModelId: 'gs-balanced',
      depth: 'quick',
      plannerRuns: true,
      reason: `search gate: ${req.webGateTrigger}`,
    }
  }

  // 5) Coding — code-shaped requests take the coding route.
  if (text.length > 0 && CODING_RE.test(text)) {
    return {
      route: 'CODING',
      internalModelId: 'gs-coder',
      depth: 'quick',
      plannerRuns: plannerTriggered,
      reason: 'coding markers',
    }
  }

  // 6) Complexity — analytical language, long asks, or a heavy conversation
  //    context take the stronger reasoning route.
  const totalContext = text.length + req.historyChars
  if (
    text.length > 0 &&
    (COMPLEX_RE.test(text) ||
      text.length > LONG_TURN_CHARS ||
      req.historicalReligious ||
      totalContext > BIG_CONTEXT_CHARS)
  ) {
    return {
      route: 'TEXT_COMPLEX',
      internalModelId: 'gs-balanced',
      depth: 'quick',
      plannerRuns: plannerTriggered,
      reason:
        req.historicalReligious
          ? 'historical/religious evidence rule'
          : totalContext > BIG_CONTEXT_CHARS
            ? 'large conversation context'
            : text.length > LONG_TURN_CHARS
              ? 'long turn'
              : 'analytical language',
    }
  }

  // 7) Default — everything else is ordinary conversation on the efficient
  //    model. Clarify-follow-ups still reach the planner (short chip replies).
  return {
    route: 'TEXT_SIMPLE',
    internalModelId: 'gs-swift',
    depth: 'quick',
    plannerRuns: plannerTriggered,
    reason: SIMPLE_RE.test(text) ? 'social turn' : 'default',
  }
}
