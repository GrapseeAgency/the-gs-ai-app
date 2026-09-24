/**
 * GS ROUTER — the backend-owned CAPABILITY router (Architecture Lock, 2026-09-21;
 * capability-registry revision, 2026-09-24; shortfall-matching revision, 2026-09-24).
 *
 * PRODUCT RULE: GS AI is ONE assistant experience. The user never selects a
 * model, never sees a model name, never sees a provider name, and never
 * carries a model preference. Routing is entirely the system's responsibility.
 *
 * This module is the SINGLE decision point that turns a raw request into an
 * INTERNAL execution plan: which internal model serves the turn, how much
 * retrieval budget it gets, and (via models.ts) which backend executes it.
 *
 * SHORTFALL MATCHING (HyDRA-style, 2026-09-24): routing is driven by MEASURED
 * CAPABILITY, not by tier labels. The complexity-tier assumption ("stronger
 * class" serves everything hard) was measured false: the register suite
 * (5 trials × 10 cases, judge glm-4.6 temp 0) caught the router sending
 * register turns to the one model class that systematically fails them
 * (zai/gs-balanced register 0.143) while the efficient class passes 1.000.
 * planRoute() now (1) classifies the turn into a required CAPABILITY,
 * (2) predicts per-query REQUIREMENTS (aggregate floor + sub-capability
 * minimums for the exact register flavors the query hits), (3) filters the
 * registry to models that meet ALL requirements (neverServe bar + aggregate
 * floor + per-sub shortfall, with the measured aggregate as the documented
 * fallback while a sub-cell is UNMEASURED), and (4) serves the CHEAPEST
 * candidate — measured p50 latency is the only measured cost axis across
 * internal models, so it is the cost proxy ($ cost is UNMEASURED and is never
 * guessed). No candidate → the turn routes BLOCKED: an honest refusal beats
 * serving a model known to fail the requirement. Capabilities without a
 * measured column (chat/code/vision/document/research) keep the standing
 * tier model — logged as `unmeasured`, never presented as a measurement.
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

import {
  CAPABILITY_THRESHOLD,
  MODEL_REGISTRY,
  REGISTER_SUBCAP_REQUIREMENT,
  measuredScore,
  subScore,
  type CapabilityTag,
  type ModelProfile,
} from './model-capabilities'

export type CapabilityRoute =
  | 'TEXT_SIMPLE' // efficient model — greetings, short factual chat
  | 'TEXT_COMPLEX' // stronger reasoning — multi-part, analytical, long context
  | 'CODING' // coding-capable model
  | 'VISION' // vision-capable model
  | 'DOCUMENT' // document synthesis route
  | 'WEB' // search service + synthesis (search is a BACKEND capability)
  | 'DEEP_RESEARCH' // bounded research pipeline + stronger synthesis route
  | 'BLOCKED' // no measured model for the required capability — honest refusal

export interface RouterPlan {
  route: CapabilityRoute
  /** Internal catalogue id — NEVER exposed to a client. */
  internalModelId: string
  /** Research budget depth for this turn (RESEARCH_BUDGET key). */
  depth: 'quick' | 'deep'
  /** True when the intent planner must run even without a search trigger. */
  plannerRuns: boolean
  /**
   * True when the turn is a REGISTER-class turn (joke, banter, sarcasm,
   * emotional, social-nuance, AI-tease). The execution layer routes these to
   * the model the REGISTRY measures best for register. NEVER exposed to a
   * client.
   */
  registerClass: boolean
  /**
   * The required capability this turn was classified into (the registry
   * lookup key). Observability + logs only — never the wire.
   */
  capability: CapabilityTag
  /**
   * Provider the REGISTRY selected for the capability ('zai' | 'openrouter'),
   * or null when the tier default applies (capability unmeasured / no
   * registry entry). The execution layer honors this lock: a registry-chosen
   * zai model must not be re-routed onto an openrouter chain and vice versa.
   */
  providerLock: 'zai' | 'openrouter' | null
  /**
   * The registry's measured score for (selected model, capability) — null
   * when the capability is unmeasured for the selected model. Logs only.
   */
  measuredScore: number | null
  /**
   * The predicted requirements this turn was matched against (shortfall
   * matching input). Observability + CI-gate logs only — never the wire.
   */
  requirements: RequirementSet
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

// --- requirements schema (shortfall matching input) --------------------------

/**
 * A per-capability requirement: the aggregate measured floor plus optional
 * sub-capability minimums for exactly the query flavors this turn hits.
 */
export interface CapabilityRequirement {
  /** Minimum measured aggregate pass rate for the capability. */
  min: number
  /** Sub-capability → required minimum (only flavors the query demands). */
  subs: Record<string, number>
}

/** Requirements per capability for one query, e.g. `{ register: { min: 0.5, subs: { sarcasm: 0.7 } } }`. */
export type RequirementSet = Partial<Record<CapabilityTag, CapabilityRequirement>>

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

/**
 * FORENSIC AUDIT [20]/[23]/[24] — ROUTE-AROUND DETECTORS (model-class
 * limitations are NEVER prompt-fixed; the turn routes to the model the
 * registry measures capable instead):
 *
 *  [20] literalist parser — meta-linguistic instructions ("just say why",
 *       "answer this question 5 times", "ignore line 3", "two-line rhyme");
 *  [23] subtext collapse — emotional/social-nuance turns ("3am, call me when
 *       you can", interpersonal dilemmas);
 *  [24] over-correction on unknowability — judgment/nuance asks.
 */
const LITERALIST_META_RE =
  /\b(just\s+say|say\s+exactly|word\s+for\s+word|exactly\s+as\s+(?:written|written\s+above)|answer\s+(?:this|the)\s+question\s+\d+\s+times|\d+\s+times\b|ignore\s+line\s+\d|answer\s+line\s+\d|line\s+\d\s+(?:only|of)|two[- ]line\s+(?:rhyme|poem|joke)|rhyme\b|spell\s+(?:it|this|that)\s+out)\b/i

const SOCIAL_EMOTIONAL_RE =
  /\b(i\s+feel|i'?m\s+feeling|im\s+feeling|feels\s+like|so\s+lonely|feeling\s+(?:sad|down|low|anxious|stressed|exhausted)|miss\s+(?:her|him|them|you)|heartbroken|broke\s+up|breakup|my\s+crush|in\s+love|break\s+up\s+with)\b/i

const SOCIAL_NUANCE_RE =
  /\b(should\s+i\s+(?:call|text|reply|apologi[sz]e|wait|leave|say)|what\s+should\s+i\s+say|what\s+do\s+you\s+think\s+(?:she|he|they)\s+(?:meant|means|thinks)|call\s+me\s+when\s+you\s+can|text\s+me\s+(?:later|back)|late\s+night\s+(?:call|text)|\d\s?am[,\s]|my\s+(?:friend|boss|mom|dad|mum|girlfriend|boyfriend|wife|husband|brother|sister|partner))\b/i

const HUMOR_BANTER_RE =
  /\b(tell\s+(?:me\s+)?a\s+joke|make\s+me\s+laugh|got\s+any\s+jokes|haha+|lol+|lmao+|rofl+|that'?s\s+funny|so\s+funny|funniest)\b|[\u{1F600}-\u{1F64F}]/u

/** Sarcasm register — mock-enthusiasm constructions ("great, another test").
 *  CLASS-level detector: it matches the register (deadpan enthusiasm about a
 *  burden), never a specific case prompt. */
const SARCASM_RE =
  /\b(?:oh\s+)?(?:great|fantastic|perfect|wonderful|awesome),?\s+(?:another|just)\b|\bjust\s+what\s+i\s+(?:needed|wanted)\b|\bperfect,?\s+just\s+perfect\b|\byeah,?\s+right\b/i

/** AI-tease register — mock insults/challenges directed at the assistant
 *  ("you're dumb", "why don't you grow up?"). CLASS-level detector. */
const AI_TEASE_RE =
  /\b(?:you'?re\s+(?:so\s+|really\s+|such\s+a\s+)?(?:dumb|stupid|useless|trash|terrible)|why\s+don'?t\s+you\s+grow\s+up|grow\s+up|you\s+suck|shut\s+up|i\s+hate\s+you)\b/i

/** Above this many chars the turn itself is "complex" (≈ a paragraph+ of ask). */
const LONG_TURN_CHARS = 600
/** Above this much serialized history the efficient model's window gets tight. */
const BIG_CONTEXT_CHARS = 24_000

/**
 * CLASSIFIER — the single required CAPABILITY for a turn, in precedence
 * order: capability attachments → explicit depth language → search service →
 * coding → register-class → reasoning complexity → chat. The register
 * detectors ([23]/[24] family) classify the turn's capability as 'register';
 * which MODEL serves it is decided by the registry, never here.
 */
export function classifyCapabilities(req: RouteRequest): CapabilityTag {
  const text = req.content.trim()
  if (req.hasImages) return 'vision'
  if (req.hasDocuments) return 'document'
  if (DEEP_RESEARCH_RE.test(text)) return 'research'
  if (req.webGateTrigger !== null) return 'search'
  if (text.length > 0 && CODING_RE.test(text)) return 'code'
  if (
    text.length > 0 &&
    (SOCIAL_EMOTIONAL_RE.test(text) ||
      SOCIAL_NUANCE_RE.test(text) ||
      SARCASM_RE.test(text) ||
      HUMOR_BANTER_RE.test(text) ||
      AI_TEASE_RE.test(text))
  ) {
    return 'register'
  }
  if (
    text.length > 0 &&
    (LITERALIST_META_RE.test(text) ||
      COMPLEX_RE.test(text) ||
      text.length > LONG_TURN_CHARS ||
      req.historicalReligious ||
      text.length + req.historyChars > BIG_CONTEXT_CHARS)
  ) {
    return 'reasoning'
  }
  return 'chat'
}

/**
 * REQUIREMENT PREDICTION — deterministic, zero-latency, per-query.
 *
 * For register turns the predicted requirements name the EXACT sub-register
 * flavors the class detectors fired on (each at REGISTER_SUBCAP_REQUIREMENT):
 * a sarcasm turn demands measured sarcasm ≥ 0.7; an ai-tease turn demands
 * teasing ≥ 0.7; a banter turn demands banter ≥ 0.7; emotional/social-nuance
 * turns demand emotional ≥ 0.7. For search turns the standing requirements
 * are citation + retrieval (the two silent-failure axes the search gate
 * polices). Reasoning/instruction carry the aggregate floor only — their
 * sub-cells are UNMEASURED in the registry and a requirement on an
 * unmeasurable axis would be a pretense, not a requirement.
 */
export function predictRequirements(req: RouteRequest, capability: CapabilityTag): RequirementSet {
  const text = req.content.trim()
  const requirements: RequirementSet = {}
  if (capability === 'register') {
    const subs: Record<string, number> = {}
    if (SOCIAL_EMOTIONAL_RE.test(text) || SOCIAL_NUANCE_RE.test(text)) subs.emotional = REGISTER_SUBCAP_REQUIREMENT
    if (SARCASM_RE.test(text)) subs.sarcasm = REGISTER_SUBCAP_REQUIREMENT
    if (HUMOR_BANTER_RE.test(text)) subs.banter = REGISTER_SUBCAP_REQUIREMENT
    if (AI_TEASE_RE.test(text)) subs.teasing = REGISTER_SUBCAP_REQUIREMENT
    requirements.register = { min: CAPABILITY_THRESHOLD, subs }
  } else if (capability === 'search') {
    requirements.search = { min: CAPABILITY_THRESHOLD, subs: { citation: REGISTER_SUBCAP_REQUIREMENT, retrieval: REGISTER_SUBCAP_REQUIREMENT } }
  } else if (capability === 'reasoning' || capability === 'instruction') {
    requirements[capability] = { min: CAPABILITY_THRESHOLD, subs: {} }
  }
  return requirements
}

/**
 * SHORTFALL MATCH — a model serves the query only when, for EVERY required
 * capability: it is not barred (neverServe), its measured aggregate is at
 * least the floor, and every named sub-requirement is met — by the model's
 * measured sub-score where one exists, else by the aggregate (documented
 * fallback: an UNMEASURED sub-cell defers to the measured aggregate; a
 * model with NO measured aggregate cannot meet a measured requirement at all).
 */
export function meetsAllRequirements(profile: ModelProfile, requirements: RequirementSet): boolean {
  for (const [cap, req] of Object.entries(requirements) as [CapabilityTag, CapabilityRequirement][]) {
    if (profile.neverServe.includes(cap)) return false
    const agg = measuredScore(profile, cap)
    if (agg === null) return false
    if (agg < req.min) return false
    for (const [sub, need] of Object.entries(req.subs)) {
      const score = subScore(profile, cap, sub) ?? agg
      if (score < need) return false
    }
  }
  return true
}

/**
 * COST — measured production p50 latency is the only measured cost axis
 * across internal models (per-turn seconds are a real user cost; $/token is
 * UNMEASURED and is never guessed). Lowest p50 wins; a model without a
 * measured p50 is maximally expensive. Ties keep registry order.
 */
export function cheapest(candidates: ModelProfile[]): ModelProfile | null {
  const withCost = candidates.map((m, i) => ({ m, i, cost: m.latency.p50 ?? Number.POSITIVE_INFINITY }))
  withCost.sort((a, b) => a.cost - b.cost || a.i - b.i)
  return withCost[0]?.m ?? null
}

/**
 * One routing decision per turn: classify → predict requirements → shortfall
 * match the registry → cheapest candidate. Measured capabilities select
 * through the registry; unmeasured capabilities keep the standing tier model.
 */
export function planRoute(req: RouteRequest): RouterPlan {
  const text = req.content.trim()
  const plannerTriggered =
    req.webGateTrigger !== null || req.clarifyFollowUp || req.historicalReligious
  const capability = classifyCapabilities(req)
  const requirements = predictRequirements(req, capability)

  const plan = (
    route: CapabilityRoute,
    internalModelId: string,
    depth: 'quick' | 'deep',
    plannerRuns: boolean,
    registerClass: boolean,
    providerLock: 'zai' | 'openrouter' | null,
    measuredScore: number | null,
    reason: string,
  ): RouterPlan => ({ route, internalModelId, depth, plannerRuns, registerClass, capability, providerLock, measuredScore, requirements, reason })

  /** Shortfall match + cheapest pick for a capability, with detector-hit annotation. */
  const shortfallPick = (cap: CapabilityTag): { chosen: ModelProfile; score: number } | { blocked: string } | null => {
    const candidates = MODEL_REGISTRY.filter((m) => meetsAllRequirements(m, requirements))
    if (candidates.length === 0) {
      return {
        blocked: `no registry model meets the predicted requirements for ${cap} (floor ${CAPABILITY_THRESHOLD}${Object.entries(requirements[cap]?.subs ?? {}).map(([s, v]) => `, ${s} ≥ ${v}`).join('')})`,
      }
    }
    const chosen = cheapest(candidates)
    if (!chosen) return { blocked: `no candidate model for ${cap}` }
    const score = measuredScore(chosen, cap)
    if (score === null) return { blocked: `registry pick ${chosen.modelId} lacks a measured ${cap} score` }
    return { chosen, score }
  }

  // 1) Vision — image understanding rides the vision-capable model.
  if (capability === 'vision') {
    return plan('VISION', 'gs-vision', 'quick', plannerTriggered, false, null, null, 'images on turn') // intentionally unmapped → provider vision default (Phase 6 design)
  }

  // 2) Documents — document-capable synthesis on the flagship internal model.
  //    Explicit depth language still upgrades the research budget.
  if (capability === 'document') {
    const deep = DEEP_RESEARCH_RE.test(text)
    return plan('DOCUMENT', 'gs-balanced', deep ? 'deep' : 'quick', plannerTriggered || deep, false, null, null, deep ? 'documents + depth language' : 'documents on turn')
  }

  // 3) Deep research — the user asked for depth: bounded research pipeline,
  //    stronger synthesis route, retrieval always on.
  if (capability === 'research') {
    return plan('DEEP_RESEARCH', 'gs-deep', 'deep', true, false, null, null, 'deep-research language')
  }

  // 4) Web — the search gate fired: SEARCH IS A GS BACKEND CAPABILITY. The
  //    search service runs first; synthesis follows the shortfall-matched
  //    search model. The measured search registry has one candidate
  //    (gs-free-big, citation/retrieval 1.000): it is an openrouter chain, so
  //    the plan keeps the standing tier label and a null provider lock — the
  //    execution layer resolves the chain (free chain when the keypool is
  //    alive, z-ai fallback when it is not). A z-ai search candidate would
  //    take the lock instead.
  if (capability === 'search') {
    const pick = shortfallPick('search')
    if (pick && 'blocked' in pick) {
      return plan('BLOCKED', 'none', 'quick', true, false, null, null, `${pick.blocked} [search gate: ${req.webGateTrigger}]`)
    }
    if (pick && 'chosen' in pick && pick.chosen.provider === 'zai') {
      return plan('WEB', pick.chosen.modelId, 'quick', true, false, 'zai', pick.score, `shortfall: search → ${pick.chosen.modelId} (measured ${pick.score.toFixed(3)}) [gate: ${req.webGateTrigger}]`)
    }
    return plan('WEB', 'gs-balanced', 'quick', true, false, null, null, `search gate: ${req.webGateTrigger} (chain resolved by execution layer)`)
  }

  // 5) Coding — code-shaped requests take the coding route (capability
  //    'code' is UNMEASURED — tier model stands, never claimed as measured).
  if (capability === 'code') {
    return plan('CODING', 'gs-coder', 'quick', plannerTriggered, false, null, null, 'coding markers')
  }

  // 6) Register — SHORTFALL-MATCHED. The detectors classify the capability
  //    AND predict the exact sub-register requirements (sarcasm/teasing/
  //    banter/emotional at 0.7 each). gs-balanced fails both the neverServe
  //    bar and its measured sub-scores (banter 0.333, sarcasm 0.0, teasing
  //    0.0, emotional 0.0) — the tier-era inversion cannot recur. Cheapest
  //    surviving candidate (measured p50) serves.
  if (capability === 'register') {
    const registerHits: string[] = []
    if (SOCIAL_EMOTIONAL_RE.test(text) || SOCIAL_NUANCE_RE.test(text)) registerHits.push('emotional')
    if (SARCASM_RE.test(text)) registerHits.push('sarcasm')
    if (HUMOR_BANTER_RE.test(text)) registerHits.push('banter')
    if (AI_TEASE_RE.test(text)) registerHits.push('ai-tease')
    const hits = registerHits.length > 0 ? ` [${registerHits.join('+')}]` : ''
    const pick = shortfallPick('register')
    if (!pick || 'blocked' in pick) {
      const why = pick && 'blocked' in pick ? pick.blocked : `no candidate model for register`
      return plan('BLOCKED', 'none', 'quick', plannerTriggered, true, null, null, `${why}${hits}`)
    }
    if (pick && 'chosen' in pick) {
      const { chosen, score } = pick
      const route = chosen.provider === 'zai' && chosen.modelId === 'gs-swift' ? 'TEXT_SIMPLE' : 'TEXT_COMPLEX'
      return plan(
        route,
        chosen.modelId,
        'quick',
        plannerTriggered,
        true,
        chosen.provider,
        score,
        `shortfall: register${hits} → ${chosen.modelId} (measured ${score.toFixed(3)} ≥ ${CAPABILITY_THRESHOLD}, cheapest p50 ${chosen.latency.p50 ?? '∞'}ms)`,
      )
    }
  }

  // 7) Reasoning — analytical language, long asks, heavy context, or the
  //    literalist route-around ([20]). Shortfall-matched: the cheapest
  //    candidate meeting the measured reasoning floor serves (currently
  //    zai/gs-balanced 1.000 — also the standing tier model, so the SCORE is
  //    the reason rather than the label).
  if (capability === 'reasoning') {
    const literalist = LITERALIST_META_RE.test(text)
    const pick = shortfallPick('reasoning')
    if (!pick || 'blocked' in pick) {
      const why = pick && 'blocked' in pick ? pick.blocked : 'no candidate model for reasoning'
      return plan('BLOCKED', 'none', 'quick', plannerTriggered, false, null, null, `${literalist ? 'route-around: literalist meta-instruction — ' : ''}${why}`)
    }
    if (pick && 'chosen' in pick) {
      return plan(
        'TEXT_COMPLEX',
        pick.chosen.modelId,
        'quick',
        plannerTriggered,
        false,
        pick.chosen.provider,
        pick.score,
        `${literalist ? 'route-around: literalist meta-instruction — ' : ''}shortfall: reasoning → ${pick.chosen.modelId} (measured ${pick.score.toFixed(3)}, cheapest p50 ${pick.chosen.latency.p50 ?? '∞'}ms)`,
      )
    }
  }

  // 8) Default — ordinary conversation on the efficient model (capability
  //    'chat' is UNMEASURED; the tier model stands). Clarify-follow-ups still
  //    reach the planner (short chip replies).
  return plan('TEXT_SIMPLE', 'gs-swift', 'quick', plannerTriggered, false, null, null, SIMPLE_RE.test(text) ? 'social turn' : 'default')
}
