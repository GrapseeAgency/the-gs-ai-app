/**
 * PHASE 8.1 — search intent planner (§5/§6/§35).
 *
 * This is the piece that fixes "what is today's news?" returning an arbitrary
 * mixture of sectors: the planner classifies the request BEFORE anything is
 * searched, and an ambiguous broad-news request produces a CLARIFICATION with
 * native quick choices — never a silently invented interest profile.
 *
 * Provider policy (§2): planning is an LLM task but the search layer must not
 * depend on any single LLM provider. Chain: primary provider (fast) →
 * OpenRouter free chain → deterministic regex fallback (websearch.ts gate).
 * If every LLM fails, search still runs with the deterministic query.
 *
 * The planner NEVER sees web content — intent comes from the user's words
 * and the conversation, never from search results (§20 hierarchy).
 */

import { completeChat, zaiThrottleActive } from '@/lib/ai'
import { orCompleteChat } from '@/lib/openrouter'
import { OPENROUTER_MODELS } from '@/lib/models'
import {
  SUPPRESSION_PATTERNS,
  extractSearchQuery,
  evaluateWebSearchGate,
} from '@/lib/websearch'
import type { ClarifyOption, SearchIntent, SearchPlan, TimeRange } from './types'

// 2026-09-21 live audit: the planner sits BEFORE any visible event, so a slow
// planner is pure dead air on the client and pushes quick turns past the ~30s
// outer-proxy SSE window. 8s (+4s OpenRouter) keeps the classification sharp
// while capping the silent phase.
const PLANNER_TIMEOUT_MS = 8_000

/** §6 — the native quick-choice set for ambiguous news requests. */
export const NEWS_CATEGORY_OPTIONS: ClarifyOption[] = [
  { id: 'world', label: 'World' },
  { id: 'technology', label: 'Technology' },
  { id: 'business', label: 'Business' },
  { id: 'science', label: 'Science' },
  { id: 'sports', label: 'Sports' },
  { id: 'entertainment', label: 'Entertainment' },
  { id: 'health', label: 'Health' },
  { id: 'bangladesh', label: 'Bangladesh' },
]

const DEFAULT_NEWS_CLARIFY_QUESTION = 'What kind of news would you like today?'

// ---------------------------------------------------------------------------
// Deterministic layers (run before/behind the LLM)
// ---------------------------------------------------------------------------

function isSuppressed(text: string): boolean {
  return SUPPRESSION_PATTERNS.some((p) => p.test(text))
}

/** Region detection for geo news (§29-E "What happened in Bangladesh today?"). */
const REGION_HINTS: { region: string; name: string }[] = [
  { region: 'BD', name: 'bangladesh' },
  { region: 'US', name: 'united states|america|u\\.s\\.' },
  { region: 'GB', name: 'uk|britain|united kingdom|england' },
  { region: 'IN', name: 'india' },
  { region: 'JP', name: 'japan' },
  { region: 'AU', name: 'australia' },
  { region: 'DE', name: 'germany' },
  { region: 'FR', name: 'france' },
]

function detectRegion(text: string): string | undefined {
  for (const r of REGION_HINTS) {
    if (new RegExp(`\\b(?:in|across|about|from)\\s+${r.name}\\b|\\b${r.name}\\b`, 'i').test(text)) {
      return r.region
    }
  }
  return undefined
}

/**
 * §29-K — "Stop searching and just summarise what you've already found."
 * The stop case must short-circuit even if the LLM planner is down.
 */
export function detectStopSignal(text: string): boolean {
  return isSuppressed(text) && !/\b(?:search|google|look)\b[^.!?]{0,40}\b(?:instead|again|now|official|more)\b/i.test(text)
}

/** 8.2 §23 — "use only official sources" must survive even when the LLM is down. */
export function detectOfficialOnly(text: string): boolean {
  return /\bofficial sources? only\b|\bonly (?:use )?official\b|\bofficial (?:docs?|documentation) only\b|\brestrict (?:the )?search to official\b/i.test(text)
}

/**
 * 8.2 §8 — historical/religious factual questions must SEARCH before answering
 * (evidence first, memory second). Deterministic backstop so this product rule
 * holds even when both LLM planners are unavailable.
 */
const HISTORICAL_RELIGIOUS_RE =
  /\b(?:who|what) (?:was|were|is|are) the first\b|\borigin of\b|\bhistory of\b|\baccording to (?:the )?(?:quran|qur'an|bible|torah|hadith|gospel)\b|\b(?:early|first) (?:muslims?|christians?|caliph|caliphate|church)\b|\bwho (?:founded|established)\b/i

export function detectHistoricalReligious(text: string): boolean {
  return HISTORICAL_RELIGIOUS_RE.test(text)
}

const CATEGORY_WORDS: Record<string, string> = {
  world: 'world|global|international',
  technology: 'tech|technology|ai|gadgets?|software|cyber',
  business: 'business|finance|economy|markets?|stocks?',
  science: 'science|space|research',
  sports: 'sports?|football|cricket|soccer',
  entertainment: 'entertainment|movies?|music|celebrit|tv',
  health: 'health|medicine|disease|hospital',
  bangladesh: 'bangladesh|dhaka',
}

/**
 * Clarification-resolution (§29-A revisit): the previous assistant turn asked
 * "What kind of news…?" with quick choices; the user tapped/typed one. Detect
 * that locally — no LLM roundtrip, robust even when the planner is down.
 */
export function resolveClarifiedCategory(userText: string, clarifyOptions: ClarifyOption[] | null): string | null {
  const text = userText.trim().toLowerCase()
  if (!text || text.length > 60) return null
  const options = clarifyOptions ?? NEWS_CATEGORY_OPTIONS
  // Direct label match ("Technology", "tech news", "bangladesh news today").
  for (const opt of options) {
    const words = CATEGORY_WORDS[opt.id]
    const re = new RegExp(`^(?:the\\s+)?(?:latest\\s+|today'?s?\\s+|todays\\s+|some\\s+|more\\s+)?(${words})\\s*(?:news|updates?|headlines?)?\\s*[?.!]*$`, 'i')
    if (re.test(text)) return opt.label
  }
  return null
}

// ---------------------------------------------------------------------------
// LLM planner
// ---------------------------------------------------------------------------

const PLANNER_SYSTEM = `You are the search-intent planner of an AI chat product. You classify the user's latest message and decide what web search (if any) should run. You never see web content. Reply with ONE JSON object and nothing else.

Schema:
{
 "needsSearch": boolean,
 "intent": "broad_news"|"sector_news"|"entity_news"|"geo_news"|"time_window_news"|"source_specific"|"factual"|"research"|"academic"|"book_source"|"historical_religious"|"comparison"|"none",
 "ambiguous": boolean,
 "clarifyQuestion": string|null,
 "queries": string[],
 "timeRange": "day"|"week"|"month"|"none",
 "region": string|null,
 "sourceHint": string|null,
 "depth": "quick"|"deep",
 "officialOnly": boolean
}

Rules:
- "what is today's news?" / "what's the news?" with NO topic, region or source mentioned => needsSearch=false, ambiguous=true, clarifyQuestion="What kind of news would you like today?". NEVER invent a topic for it.
- If a topic is present ("today's AI news"), intent=sector_news, queries contain that topic ONLY (e.g. "AI news today"), never unrelated sectors.
- If a region is present ("What happened in Bangladesh today?"), intent=geo_news, region="BD" (ISO code), queries include the region.
- If a source is named ("Search Reuters for X"), intent=source_specific, sourceHint=domain (e.g. "reuters.com").
- Documentation/first-party questions ("latest Android documentation") => intent=factual, officialOnly=true.
- Scholarly/scientific literature questions ("studies on X", "papers about X", "what does research say about X") => intent=academic, queries 2 (one broad, one precise).
- Questions about a BOOK or its contents ("in Frankenstein, ...", "what does the Quran/Bible say about X", "according to <book>") => intent=book_source, queries include the book title, depth="deep" when the user wants passages/quotes.
- Historical or religious factual questions ("who was the first Muslim?", "origin of X tradition", "what happened in ... century") => intent=historical_religious, needsSearch=true: the answer must be grounded in sources, not memory. queries 2-3: one uses the question's own words, one reframes with the tradition's own terminology. When the question asks who was the FIRST person/figure of a religious tradition, one query MUST name that tradition's earliest figures so primary-tradition sources surface (e.g. for "first Muslim ever" include "Adam first prophet Islam" — Islamic theology counts Adam as the first prophet and first submitter to Allah; the Muhammad-era converts are a different, later sense of "first Muslim").
- Comparisons ("how does X compare with Y", "X vs Y documentation") => intent=comparison, queries cover BOTH sides.
- Deeper multi-part research ("explain the situation with X") => depth="deep", intent="research".
- Facts that need current data (weather, scores, prices, releases) => intent=factual.
- Ordinary chat/general knowledge => needsSearch=false, intent="none".
- timeRange: "day" for today/now, "week" for this week, "month" for this month, "none" otherwise. queries: 1-4 short search-engine queries, English, no conversational words.
- If the user says "use only official sources" or names an official documentation site, set officialOnly=true (deterministic layers also enforce this).
- If the user explicitly tells you to stop searching and summarise, set needsSearch=false (the orchestrator also enforces this deterministically).
- If earlier evidence in this conversation already answers the message, set needsSearch=false and add "reuseEvidence": true at top level.`

function extractJson(raw: string): Record<string, unknown> | null {
  const fenced = raw.match(/```(?:json)?\s*([\s\S]*?)```/i)
  const candidate = fenced ? fenced[1] : raw
  const start = candidate.indexOf('{')
  const end = candidate.lastIndexOf('}')
  if (start === -1 || end <= start) return null
  try {
    const parsed = JSON.parse(candidate.slice(start, end + 1))
    return typeof parsed === 'object' && parsed !== null ? (parsed as Record<string, unknown>) : null
  } catch {
    return null
  }
}

function asStringArray(v: unknown, max: number): string[] {
  if (!Array.isArray(v)) return []
  return v
    .filter((s): s is string => typeof s === 'string' && s.trim().length >= 2)
    .map((s) => s.trim().slice(0, 200))
    .slice(0, max)
}

function normalizePlan(raw: Record<string, unknown>, fallbackQuery: string): SearchPlan {
  const intent = (
    [
      'broad_news', 'sector_news', 'entity_news', 'geo_news', 'time_window_news',
      'source_specific', 'factual', 'research', 'none',
      'academic', 'book_source', 'historical_religious', 'comparison',
    ] as SearchIntent[]
  ).includes(raw.intent as SearchIntent)
    ? (raw.intent as SearchIntent)
    : 'none'
  const timeRange: TimeRange = (['day', 'week', 'month', 'none'] as TimeRange[]).includes(raw.timeRange as TimeRange)
    ? (raw.timeRange as TimeRange)
    : 'none'
  const queries = asStringArray(raw.queries, 4)
    // Strip directive scaffolding the model sometimes echoes back into the
    // query ("again for official sources…" → "official sources…").
    .map((q) =>
      q
        .replace(/^(?:(?:again|now|please|and|then)\s+)+(?:for\s+)?/i, '')
        .replace(/^(?:the\s+)?(?:search|searching|google|look\s*up)\s*(?:for|up|about)?\s*(?:again\s+)?/i, '')
        .trim()
    )
    .filter((q) => q.length >= 3)
  const region = typeof raw.region === 'string' && /^[A-Za-z]{2}$/.test(raw.region) ? raw.region.toUpperCase() : undefined
  const sourceHint =
    typeof raw.sourceHint === 'string' && /^[\w.-]+\.[a-z]{2,}$/i.test(raw.sourceHint.trim())
      ? raw.sourceHint.trim().toLowerCase()
      : undefined
  const ambiguous = raw.ambiguous === true && typeof raw.clarifyQuestion === 'string' && raw.clarifyQuestion.trim().length > 0
  return {
    needsSearch: raw.needsSearch === true && !ambiguous && queries.length > 0,
    intent,
    ambiguity: {
      isAmbiguous: ambiguous,
      prompt: ambiguous
        ? { question: (raw.clarifyQuestion as string).trim().slice(0, 200), options: NEWS_CATEGORY_OPTIONS }
        : null,
    },
    queries: queries.length > 0 ? queries : fallbackQuery ? [fallbackQuery] : [],
    timeRange,
    region,
    sourceHint,
    depth: raw.depth === 'deep' ? 'deep' : 'quick',
    officialOnly: raw.officialOnly === true,
    stopSignal: false,
    reuseEvidence: raw.reuseEvidence === true,
  }
}

async function llmPlan(userText: string, historyText: string, fallbackQuery: string): Promise<SearchPlan | null> {
  const userPayload = `Conversation so far (context only):\n${historyText || '(start of conversation)'}\n\nLatest user message:\n"""${userText.slice(0, 2000)}"""\n\nReturn the JSON classification now.`
  const messages = [
    { role: 'system', content: PLANNER_SYSTEM },
    { role: 'user', content: userPayload },
  ]

  // 1) primary provider, fast default model (omit `model` = provider default).
  // The throttle circuit breaker skips this attempt entirely while the
  // account-level 429 window is open — no doomed retries on the hot path.
  if (!zaiThrottleActive()) {
    try {
      const raw = await Promise.race([
        completeChat(messages),
        new Promise<never>((_, reject) =>
          setTimeout(() => reject(new Error('planner timeout')), PLANNER_TIMEOUT_MS)
        ),
      ])
      const parsed = extractJson(raw)
      if (parsed) return normalizePlan(parsed, fallbackQuery)
    } catch {
      // fall through to OpenRouter
    }
  }

  // 2) OpenRouter free chain (§2 — planner must not depend on one provider).
  try {
    const raw = await Promise.race([
      orCompleteChat(messages, OPENROUTER_MODELS['gs-free']),
      new Promise<never>((_, reject) =>
        setTimeout(() => reject(new Error('planner timeout')), PLANNER_TIMEOUT_MS + 4_000)
      ),
    ])
    const parsed = extractJson(raw.text)
    if (parsed) return normalizePlan(parsed, fallbackQuery)
  } catch {
    // fall through to deterministic plan
  }
  return null
}

// ---------------------------------------------------------------------------
// entry point
// ---------------------------------------------------------------------------

export type PlanInput = {
  userText: string
  /** Recent turns, oldest→newest, "role: text" lines (bounded). */
  historyLines: string[]
  /** clarifyOptions JSON of the PREVIOUS assistant message, when present. */
  previousClarifyOptions: ClarifyOption[] | null
}

export async function planSearch(input: PlanInput): Promise<SearchPlan> {
  const text = input.userText.trim()

  // Deterministic stop (§19): suppressed turns never search, even if the LLM
  // disagrees. An explicit NEW search directive still survives (websearch.ts
  // suppression semantics).
  if (detectStopSignal(text)) {
    return {
      needsSearch: false,
      intent: 'none',
      ambiguity: { isAmbiguous: false, prompt: null },
      queries: [],
      timeRange: 'none',
      depth: 'quick',
      stopSignal: true,
    }
  }

  // Clarification chip resolution — the previous turn asked, the user answered.
  const clarifiedCategory = resolveClarifiedCategory(text, input.previousClarifyOptions)
  if (clarifiedCategory) {
    const region = clarifiedCategory === 'Bangladesh' ? 'BD' : undefined
    return {
      needsSearch: true,
      intent: region ? 'geo_news' : 'sector_news',
      ambiguity: { isAmbiguous: false, prompt: null },
      queries: [`${clarifiedCategory} news today`],
      timeRange: 'day',
      region,
      depth: 'quick',
    }
  }

  const gate = evaluateWebSearchGate(text)
  const fallbackQuery = gate.trigger ? extractSearchQuery(text, gate.trigger) : ''

  // Broad-news ambiguity backstop (§35): if the LLM is unreachable, a bare
  // "today's news" MUST still clarify rather than pick random sectors.
  const bareBroadNews =
    gate.trigger !== null &&
    /^(?:(?:the\s+)?(?:latest|today'?s?|todays|current|breaking)?\s*(?:news|headlines?|world\s+news|whats\s+happening|what\s+is\s+happening)[?.!]*|(?:whats|what's|what\s+is)\s+(?:the\s+)?(?:latest\s+)?(?:in\s+the\s+)?(?:world\s+)?news[?.!]*|(?:whats|what's)\s+happening(?:\s+in\s+the\s+world)?[?.!]*|today'?s?\s+(?:news|headlines?)[?.!]*)$/i.test(
      fallbackQuery
    )

  const plan = await llmPlan(text, input.historyLines.slice(-6).join('\n').slice(0, 3000), fallbackQuery)

  if (plan) {
    // Deterministic veto still applies on top of the LLM output.
    if (detectStopSignal(text)) {
      return { ...plan, needsSearch: false, stopSignal: true, queries: [] }
    }
    // 8.2 §26 (2026-09-21 live-audit hardening) — an EXPLICIT user search
    // directive is a COMMAND, not a suggestion. The LLM planner's needs=false
    // or ambiguity verdict must NEVER override it. Proven in production:
    // "go to the internet to search the internet … there is a pirate job
    // available so check it out" produced needs=false (model answered
    // "I cannot access the live internet") and, on other attempts, a false
    // "Could you clarify…" — four failures across four conversations for the
    // same message. Explicit gate hit => search ALWAYS runs, clarification is
    // FORBIDDEN (clarify is only for bare broad-news requests).
    if (gate.trigger === 'explicit') {
      plan.needsSearch = true
      plan.ambiguity = { isAmbiguous: false, prompt: null }
      plan.reuseEvidence = false
      // A planner that just echoed the raw message (>14 words) produced a
      // useless query — re-derive the payload from the deterministic extractor.
      const echoed = plan.queries.some((q) => q.split(/\s+/).length > 14)
      if ((plan.queries.length === 0 || echoed) && fallbackQuery.length >= 3) {
        plan.queries = [fallbackQuery, ...plan.queries.filter((q) => q.split(/\s+/).length <= 14)].slice(0, 3)
      }
      if (plan.queries.length === 0) {
        plan.queries = [text.replace(/\s+/g, ' ').trim().slice(0, 120)].filter((q) => q.length >= 3)
      }
      plan.needsSearch = plan.queries.length > 0
    }
    if (detectOfficialOnly(text)) plan.officialOnly = true
    // 8.2 §8 — HARD RULE: historical/religious factual questions ALWAYS search
    // before answering (evidence first, memory second) — no planner wording
    // like "in the sources" may silently downgrade this to memory-only.
    if (detectHistoricalReligious(text) && !plan.ambiguity.isAmbiguous) {
      plan.needsSearch = true
      plan.intent = 'historical_religious'
      plan.reuseEvidence = false
      if (plan.queries.length === 0) {
        plan.queries = [text.replace(/\s+/g, ' ').replace(/[?!.]+$/, '').trim().slice(0, 120)].filter((q) => q.length >= 3)
      }
    }
    if (plan.needsSearch && plan.queries.length === 0) {
      plan.needsSearch = fallbackQuery.length >= 3
      if (plan.needsSearch) plan.queries = [fallbackQuery]
    }
    return plan
  }

  // Deterministic fallback (LLM fully unavailable). The explicit-directive
  // guarantee holds here too: gate.trigger === 'explicit' with a usable
  // extracted query ALWAYS searches (same production rule as above).
  if (bareBroadNews) {
    return {
      needsSearch: false,
      intent: 'broad_news',
      ambiguity: {
        isAmbiguous: true,
        prompt: { question: DEFAULT_NEWS_CLARIFY_QUESTION, options: NEWS_CATEGORY_OPTIONS },
      },
      queries: [],
      timeRange: 'day',
      depth: 'quick',
    }
  }
  // 8.2 §8 — evidence-first historical/religious questions survive planner outages.
  if (detectHistoricalReligious(text)) {
    const q = (fallbackQuery || text.replace(/\s+/g, ' ').replace(/[?!.]+$/, '').trim().slice(0, 120)).trim()
    return {
      needsSearch: q.length >= 3,
      intent: 'historical_religious',
      ambiguity: { isAmbiguous: false, prompt: null },
      queries: [q].filter((x) => x.length >= 3),
      timeRange: 'none',
      depth: 'quick',
      officialOnly: detectOfficialOnly(text),
    }
  }
  if (gate.trigger === null) {
    return {
      needsSearch: false,
      intent: 'none',
      ambiguity: { isAmbiguous: false, prompt: null },
      queries: [],
      timeRange: 'none',
      depth: 'quick',
    }
  }
  const region = detectRegion(text)
  return {
    needsSearch: fallbackQuery.trim().length >= 3,
    intent: region ? 'geo_news' : 'factual',
    ambiguity: { isAmbiguous: false, prompt: null },
    queries: [fallbackQuery].filter((q) => q.length >= 3),
    timeRange: /\b(today|tonight|now)\b/i.test(text) ? 'day' : /\bthis\s+week\b/i.test(text) ? 'week' : 'none',
    region,
    depth: 'quick',
  }
}
