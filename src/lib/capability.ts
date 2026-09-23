/**
 * PHASE 8.3 — DETERMINISTIC GS CAPABILITY GATE.
 *
 * ARCHITECTURE (user directive, "STOP USING THE SYSTEM PROMPT AS THE
 * APPLICATION BRAIN"):
 *
 *   USER → DETERMINISTIC GS CAPABILITY ROUTER → REAL TOOL EXECUTION →
 *   STRUCTURED EVIDENCE → MINIMAL SYSTEM CONTRACT → MODEL SYNTHESIS →
 *   VALIDATION → USER
 *
 * This module is the deterministic router. It decides the capability of a
 * turn BEFORE any model runs — the model is never asked "do you think you
 * should search?" and can never veto a capability the application already
 * knows (§2 of the 8.3 spec). The LLM intent planner (planner.ts) remains in
 * the pipeline ONLY as a query refiner for search turns; it can never flip a
 * forced search off and can never turn a non-search turn into a search.
 *
 * Every decision here is plain regex/keyword code: zero latency, zero
 * provider dependency, fully unit-auditable. Forensic baseline defects this
 * gate closes:
 *   - time request had no deterministic time capability  → TIME
 *   - explicit search depended on planner interpretation → forced WEB
 *   - stop-search failed to reopen the research context  → REUSE
 *   - explicit Wikipedia request did not trigger retrieval → SOURCE_REF
 *   - "current president of Chile" freshness retrieval    → FRESHNESS
 *
 * INTERNAL ONLY: nothing from a CapabilityDecision may reach a client.
 */

import {
  SUPPRESSION_PATTERNS,
  evaluateWebSearchGate,
  type WebSearchGate,
} from '@/lib/websearch'
import { detectStopSignal, detectHistoricalReligious, detectOfficialOnly } from '@/lib/search/planner'
import { DEEP_RESEARCH_RE } from '@/lib/router'

// ---------------------------------------------------------------------------
// capability vocabulary
// ---------------------------------------------------------------------------

export type GsCapability =
  | 'CHAT' // ordinary conversation — no tool
  | 'TIME' // deterministic application clock (§11)
  | 'WEB' // search-backed answer (explicit / freshness / weather / source-specific / historical)
  | 'DEEP_RESEARCH' // bounded research pipeline
  | 'VISION' // image understanding turn
  | 'DOCUMENT' // document understanding turn

export type CapabilityTrigger =
  | 'explicit'
  | 'research_again'
  | 'freshness'
  | 'weather'
  | 'source_specific'
  | 'historical_religious'
  | 'time'
  | 'deep_research'
  | 'stop_reuse'
  | 'context_return'
  | null

export interface SearchPolicy {
  /** Bias retrieval toward this domain (aggregate.ts scoring +6). */
  sourceHint?: string
  /** Restrict to official/first-party sources. */
  officialOnly?: boolean
  /** The user asked to re-search with PRIMARY sources — policy change preserved across executions (§9). */
  primarySources?: boolean
  /** Freshness window for recency-class searches. */
  timeRange?: 'day' | 'week' | 'month' | 'none'
}

export interface CapabilityDecision {
  capability: GsCapability
  trigger: CapabilityTrigger
  /** CODE DECISION: a search MUST execute this turn. The model cannot veto it. */
  searchPlanned: boolean
  /** CODE DECISION: suppress new search and answer from an existing ResearchContext (§10). */
  reuseResearch: boolean
  policy: SearchPolicy
  /** The cheap deterministic gate verdict (telemetry + router input). */
  webGateTrigger: WebSearchGate['trigger']
  reason: string
}

// ---------------------------------------------------------------------------
// deterministic detectors
// ---------------------------------------------------------------------------

/**
 * §11 — clock/date questions the APPLICATION must answer with the real clock.
 * Narrow by design: conversational time references ("see you tomorrow",
 * "what time is the meeting") must NOT route to the clock — only genuine
 * "what time/date/day is it" requests.
 */
const TIME_RES: RegExp[] = [
  /\bwhat(?:'s| is)?\s+(?:the\s+)?time\b/i,
  /\bwhat\s+time\s+is\s+it\b/i,
  /\bwhat(?:'s| is)?\s+(?:the\s+)?(?:current|local|exact|present)\s+time\b/i,
  /\b(?:current|local|exact)\s+time\s+(?:is|now|right now)\b/i,
  /\bwhat(?:'s| is)?\s+(?:the\s+)?(?:date|day)\s+(?:is\s+it|today|now)\b/i,
  /\bwhat(?:'s| is)?\s+today(?:'s)?\s+date\b/i,
  /\bwhat(?:'s| is)?\s+(?:today|the\s+date\s+today)\b/i,
  /\bwhat\s+day\s+is\s+(?:it|today)\b/i,
  /\bwhat\s+year\s+is\s+it\b/i,
  /\btoday(?:'s)?\s+date\b/i,
]

/**
 * Explicit internet directives that the bare websearch gate can miss —
 * "go to the internet and find today's AI news" contains no "search" word.
 * These upgrade the turn to a FORCED web capability (§2: CASE C).
 */
const INTERNET_DIRECTIVE_RES: RegExp[] = [
  /\b(?:go|going|went|jump|hop|get|head|take)\s+(?:to|on|onto|into|over\s+to)\s+(?:the\s+)?(?:internet|web|net|world\s*wide\s+web)\b/i,
  /\bgo\s+online\b/i,
  /\b(?:use|via|through|from|check|browse|look\s+(?:on|at|into)|find(?:\s+\w+){0,12}\s+on)\s+(?:the\s+)?internet\b/i,
  /\bbrowse\s+the\s+(?:web|internet)\b/i,
  /\b(?:find|get|bring|fetch|pull|grab|check)\s+(?:me\s+)?[^.?!]{0,60}\b(?:on|from|via)\s+the\s+(?:internet|web)\b/i,
]

/** §11-adjacent — weather is a freshness capability: real retrieval, forced. */
const WEATHER_RE =
  /\bweather\b|\bforecast\b|\btemperature\b|\brain(?:ing|fall)?\s+(?:today|tomorrow|now|right\s+now|in\b)|\bis\s+it\s+(?:raining|sunny|snowing|hot|cold)\s*(?:there|today|now|outside)?\b/i

/**
 * Baseline C26 (audit [9] class) — BARE, anchorless weather register questions.
 * "what's the weather?" has NO location to retrieve against: the forced search
 * burned 8 sources on worldwide stories and the answer still had to ask
 * "which location you're asking about" (baseline evidence, 19.4s wasted).
 * Time words are not location anchors — without a place there is nothing to
 * ground. These are CHAT turns (the answer asks for the location), not WEB.
 * End-anchored + tightly scoped so "weather in Tokyo" / "rain in Dhaka" /
 * "today's weather" still search via the normal triggers.
 */
const WEATHER_BARE_RES: RegExp[] = [
  /^(?:(?:ok(?:ay)?|so|um|well|hey)[,\s]+)?(?:what(?:'s|s|\s+is)|how(?:'s|s|\s+is))\s+(?:the\s+)?(?:weather|temperature|forecast)(?:\s+(?:like|outside|there|here|today|now|tonight|right\s+now))?\s*[?.!]*$/i,
  /^(?:(?:ok(?:ay)?|so|um|well|hey)[,\s]+)?is\s+it\s+(?:raining|snowing|sunny|cloudy|windy|hot|cold)(?:\s+(?:outside|there|here|today|now|right\s+now))?\s*[?.!]*$/i,
  /^(?:(?:ok(?:ay)?|so|um|well|hey)[,\s]+)?(?:the\s+)?(?:weather|temperature|forecast)\s*[?.!]*$/i,
]

/** Deterministic: a bare anchorless weather register question — never grounds. */
export function isBareWeatherRegister(text: string): boolean {
  return WEATHER_BARE_RES.some((re) => re.test(text.trim()))
}

/**
 * §13 — the user NAMES an information source and asks what IT says. The
 * source name maps to a domain hint; retrieval is FORCED (the model must not
 * answer a "what does Wikipedia say about X" question from memory).
 */
const SOURCE_NAME = String.raw`(?:wikipedia|wiki|reuters|ap\s?news|associated\s+press|bbc|cnn|al\s?jazeera|github|stack\s?overflow|mdn(?:\s+web\s+docs)?|npmjs?|arxiv|nature\.com|microsoft|apple|google|python\.org|openai|anthropic|nasa|unesco|imf|world\s+bank|who\.int)`

const SOURCE_REF_RES: RegExp[] = [
  new RegExp(
    String.raw`\b(?:what|how)\s+(?:does|do|did|is|are)\s+(?:the\s+)?${SOURCE_NAME}\s+(?:say|says|said|report|reports|reported|describe|describes|describe[sd]?|define|defines|rank|ranks|list|lists|cover|covers|show|shows|classify|treat|refer[sd]?|spell|spell[sd]?|write|writes|call|record|record[sd]?)\b`,
    'i'
  ),
  new RegExp(String.raw`\baccording\s+to\s+(?:the\s+)?${SOURCE_NAME}\b`, 'i'),
  new RegExp(String.raw`\b(?:search|look\s*up|find|check|get|read)\s+(?:it\s+|this\s+|that\s+)?(?:on|in|at|via|through|from)\s+(?:the\s+)?${SOURCE_NAME}\b`, 'i'),
  new RegExp(String.raw`\bwhat(?:'s| is| are)\s+(?:the\s+)?${SOURCE_NAME}\s+(?:entry|article|page|saying)\b`, 'i'),
]

/** Source name → retrieval domain hint (aggregate.ts boosts the hint +6). */
const SOURCE_DOMAINS: Record<string, string> = {
  wikipedia: 'en.wikipedia.org',
  wiki: 'en.wikipedia.org',
  reuters: 'reuters.com',
  'ap news': 'apnews.com',
  'associated press': 'apnews.com',
  bbc: 'bbc.com',
  cnn: 'cnn.com',
  'al jazeera': 'aljazeera.com',
  aljazeera: 'aljazeera.com',
  github: 'github.com',
  'stack overflow': 'stackoverflow.com',
  stackoverflow: 'stackoverflow.com',
  mdn: 'developer.mozilla.org',
  'mdn web docs': 'developer.mozilla.org',
  npm: 'npmjs.com',
  npmjs: 'npmjs.com',
  arxiv: 'arxiv.org',
  'nature.com': 'nature.com',
  microsoft: 'learn.microsoft.com',
  apple: 'apple.com',
  google: 'blog.google',
  'python.org': 'python.org',
  openai: 'openai.com',
  anthropic: 'anthropic.com',
  nasa: 'nasa.gov',
  unesco: 'unesco.org',
  imf: 'imf.org',
  'world bank': 'worldbank.org',
  'who.int': 'who.int',
}

/**
 * §12 — freshness-sensitive factual questions need real retrieval, decided by
 * CODE ("who is the current president of Chile?"). Deliberately narrow so
 * subjective/creative questions ("what's the most beautiful diagram chart?")
 * and bare broad news (which owns its clarify path) never force a search.
 */
const FRESHNESS_FORCE_RES: RegExp[] = [
  /\b(?:who|what|where|which)\s+(?:is|are|was|were|has|have|does|do)\b[^.?!]{0,80}\b(?:current(?:ly)?|present|presently|today|right\s+now|as\s+of\s+(?:now|today)|latest|newest|newly|this\s+(?:year|month|week)|incumbent|at\s+the\s+moment|so\s+far\s+this\s+(?:year|season)|nowaday[sz]?)\b/i,
  /\b(?:current|latest|newest|today'?s?)\s+(?:president|prime\s+minister|chancellor|ceo|c\.e\.o|champion|winner|population|capital(?:\s+city)?|ruler|king|queen|pope|price[s]?|version|release|world\s+record|record\s+holder|lineup|squad|roster|weather)\b/i,
]

/**
 * §9 — "search again using primary sources": an explicit RE-search whose
 * topic lives in the originating research execution, with a POLICY CHANGE
 * that code must preserve. Detected deterministically so the application can
 * reconstruct the search from the ResearchContext — never delegated to the
 * model.
 */
const RESEARCH_AGAIN_RES: RegExp[] = [
  /\b(?:search|look|check|dig|go|find)\b[^.?!]{0,40}\bagain\b/i,
  /\bagain\b[^.?!]{0,30}\b(?:search|look|check|find)\b/i,
  /\b(?:search|look|check|find)\b[^.?!]{0,30}\b(?:once\s+more|one\s+more\s+time|more\s+sources?|further|deeper)\b/i,
  /\b(?:primary|original|scholarly|academic|first[- ]hand)\s+sources?\b/i,
]

/**
 * §8 — explicit reopen of an earlier research context ("Go back to the first
 * question."). Reuses the stored ResearchContext; fresh retrieval only if the
 * stored context cannot answer.
 */
const CONTEXT_RETURN_RES: RegExp[] = [
  /\bgo\s+back\s+to\b/i,
  /\b(?:the|that|our|your)\s+(?:first|original|previous|earlier|last|initial)\s+(?:question|topic|request|search|research|query|one)\b/i,
  /\bback\s+to\s+the\s+(?:first|previous|earlier|original)\b/i,
  /\breturn\s+to\s+the\s+(?:first|previous|earlier|original)\b/i,
]

// ---------------------------------------------------------------------------
// decision
// ---------------------------------------------------------------------------

export interface CapabilityInput {
  content: string
  hasImages: boolean
  hasDocuments: boolean
  /** deterministic historical/religious evidence-first rule (planner.ts). */
  historicalReligious?: boolean
}

function hasExplicitDirective(text: string, gate: WebSearchGate): boolean {
  if (gate.trigger === 'explicit') return true
  return INTERNET_DIRECTIVE_RES.some((p) => p.test(text))
}

function matchSourceHint(text: string): string | undefined {
  for (const re of SOURCE_REF_RES) {
    const m = text.match(re)
    if (!m) continue
    const lowered = m[0].toLowerCase()
    for (const [name, domain] of Object.entries(SOURCE_DOMAINS)) {
      if (lowered.includes(name)) return domain
    }
  }
  return undefined
}

/** Is this an explicit re-search whose topic should come from the stored context? */
export function isResearchAgainRequest(text: string, hasExplicit: boolean): boolean {
  if (!hasExplicit) return false
  return RESEARCH_AGAIN_RES.some((p) => p.test(text))
}

/**
 * ONE deterministic capability decision per turn. Precedence:
 * TIME → stop/reuse → explicit/re-again → weather → source-specific →
 * freshness → historical/religious → deep-research → attachment routes → chat.
 */
export function decideCapability(input: CapabilityInput): CapabilityDecision {
  const text = input.content.trim()
  const gate = evaluateWebSearchGate(text)
  const suppressed = SUPPRESSION_PATTERNS.some((p) => p.test(text))
  const explicit = text.length > 0 && hasExplicitDirective(text, gate)
  const again = text.length > 0 && isResearchAgainRequest(text, explicit)
  const stop = text.length > 0 && detectStopSignal(text)
  const backToContext = !explicit && CONTEXT_RETURN_RES.some((p) => p.test(text))

  const base = (over: Partial<CapabilityDecision>): CapabilityDecision => ({
    capability: 'CHAT',
    trigger: null,
    searchPlanned: false,
    reuseResearch: false,
    policy: {},
    webGateTrigger: gate.trigger,
    reason: 'chat',
    ...over,
  })

  // 1) TIME — the application owns the clock (§11). No search. (Explicit
  //    search directives are checked first below, so "search the web for the
  //    current time" still searches.)
  if (text.length > 0 && !explicit && !again && !stop && TIME_RES.some((p) => p.test(text))) {
    return base({ capability: 'TIME', trigger: 'time', reason: 'clock/date request' })
  }

  // 2) STOP / CONTEXT RETURN — code-driven reuse of the stored ResearchContext (§10).
  if (stop || backToContext) {
    return base({
      capability: 'CHAT',
      trigger: stop ? 'stop_reuse' : 'context_return',
      reuseResearch: true,
      reason: stop ? 'stop-signal: reuse existing research context' : 'explicit context return',
    })
  }

  // 3) EXPLICIT — search is a COMMAND (§2). Includes "search again …" (§9).
  if (explicit) {
    const policy: SearchPolicy = {
      officialOnly: detectOfficialOnly(text) || undefined,
      timeRange: /\b(today|now|tonight)\b/i.test(text)
        ? 'day'
        : /\bthis\s+week\b/i.test(text)
          ? 'week'
          : 'none',
    }
    const hint = matchSourceHint(text)
    if (hint) policy.sourceHint = hint
    if (RESEARCH_AGAIN_RES.some((p) => p.test(text))) policy.primarySources = true
    return base({
      capability: 'WEB',
      trigger: again ? 'research_again' : 'explicit',
      searchPlanned: true,
      policy,
      reason: again ? 'explicit re-search directive (policy change preserved)' : 'explicit search directive',
    })
  }

  // 4) WEATHER — forced freshness retrieval with a day window. A BARE,
  // anchorless register question ("what's the weather?") is NOT a grounding
  // request (baseline C26: no location → worldwide story pile → still asks
  // "which location?") — those stay CHAT; anchored weather turns search.
  if (text.length > 0 && WEATHER_RE.test(text) && !isBareWeatherRegister(text)) {
    return base({
      capability: 'WEB',
      trigger: 'weather',
      searchPlanned: true,
      policy: { timeRange: 'day' },
      reason: 'weather request → real retrieval',
    })
  }

  // 5) SOURCE-SPECIFIC — "what does Wikipedia say about X" (§13).
  if (text.length > 0 && SOURCE_REF_RES.some((p) => p.test(text))) {
    const hint = matchSourceHint(text)
    return base({
      capability: 'WEB',
      trigger: 'source_specific',
      searchPlanned: true,
      policy: hint ? { sourceHint: hint } : { officialOnly: false },
      reason: `named-source question → retrieval (hint: ${hint ?? 'none'})`,
    })
  }

  // 6) FRESHNESS — "who is the current president of Chile" (§12).
  if (text.length > 0 && FRESHNESS_FORCE_RES.some((p) => p.test(text))) {
    return base({
      capability: 'WEB',
      trigger: 'freshness',
      searchPlanned: true,
      policy: { timeRange: 'none' },
      reason: 'freshness-sensitive factual → real retrieval',
    })
  }

  // 7) HISTORICAL / RELIGIOUS — evidence before memory (product rule).
  if (input.historicalReligious) {
    return base({
      capability: 'WEB',
      trigger: 'historical_religious',
      searchPlanned: true,
      reason: 'historical/religious evidence-first rule',
    })
  }

  // 8) DEEP RESEARCH — explicit depth language runs the research pipeline.
  if (text.length > 0 && DEEP_RESEARCH_RE.test(text)) {
    return base({
      capability: 'DEEP_RESEARCH',
      trigger: 'deep_research',
      searchPlanned: true,
      reason: 'deep-research language',
    })
  }

  // 9) Attachment routes (a document/image turn stays a document/image turn
  //    unless the words above forced a search).
  if (input.hasImages) return base({ capability: 'VISION', reason: 'images on turn' })
  if (input.hasDocuments) return base({ capability: 'DOCUMENT', reason: 'documents on turn' })

  // 10) Recency-but-not-forced turns (bare broad news etc.) keep the existing
  //     planner-owned clarify behavior; capability stays CHAT with the gate
  //     verdict surfaced for the router.
  if (suppressed) return base({ reason: 'suppressed text — no new search' })
  return base({ reason: 'ordinary conversation' })
}

// ---------------------------------------------------------------------------
// timezone helper (§11 — real timezone awareness)
// ---------------------------------------------------------------------------

/** Validate a client-supplied IANA timezone; null when absent/invalid. */
export function validTimeZone(tz: string | null | undefined): string | null {
  if (!tz || typeof tz !== 'string') return null
  try {
    new Intl.DateTimeFormat('en-GB', { timeZone: tz }).format(new Date())
    return tz
  } catch {
    return null
  }
}
