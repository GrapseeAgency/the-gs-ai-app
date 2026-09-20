/**
 * PHASE 8.1 — shared types for the backend-owned search foundation.
 *
 * Architecture rule (spec §2): the model is the reasoning/synthesis layer;
 * search is OUR backend capability. The model never invents the result set —
 * it only ever sees evidence produced by `runResearch()`, and every visible
 * research step in the clients maps 1:1 to an event emitted here.
 *
 * Wire contract: docs/search-event-protocol.md (FROZEN v1).
 */

export type SearchEngineId =
  | 'z-ai'
  | 'bing-news-rss'
  | 'google-news-rss'
  | 'bing-web'
  | 'duckduckgo-lite'
  | 'wikipedia'
  | 'searxng'

/** One normalized hit from any engine, before ranking/dedupe. */
export type RawResult = {
  url: string
  title: string
  snippet: string
  domain: string
  publishedDate: string | null
  /** 1-based position inside its engine's result list. */
  rank: number
  engine: SearchEngineId
}

/** Retrieval state of a source — persisted and rendered verbatim (§31: no fake visuals). */
export type SourceStatus =
  | 'discovered'
  | 'retrieved'
  | 'snippet_only'
  | 'failed'
  | 'skipped'
  | 'used'

/**
 * Canonical source object (§11 source evidence object). Everything except
 * `pageExtract` is provider metadata or HTTP fact — never model text.
 */
export type ResearchSource = {
  /** 1-based citation number shown to the model and the user. */
  ordinal: number
  /** Provider-stable short hash of the URL (websearch.sourceIdFor). */
  id: string
  title: string
  url: string
  domain: string
  snippet: string
  publishedDate: string | null
  retrievedAt: string
  /** The query that discovered this source. */
  query: string
  engine: SearchEngineId
  searchRank: number
  status: SourceStatus
  /** Plain-text article extract when the page was actually read (bounded). */
  pageExtract?: string
  /** Extracted facts when the page was actually read. */
  extractedTitle?: string
  extractedDate?: string | null
  extractedChars?: number
  failReason?: string
}

export type SearchIntent =
  | 'broad_news'
  | 'sector_news'
  | 'entity_news'
  | 'geo_news'
  | 'time_window_news'
  | 'source_specific'
  | 'factual'
  | 'research'
  | 'none'

export type TimeRange = 'day' | 'week' | 'month' | 'none'

export type ClarifyOption = { id: string; label: string }

export type ClarifyPrompt = { question: string; options: ClarifyOption[] }

/**
 * §5 — intent plan. Produced by the LLM planner (planner.ts) with a
 * deterministic fallback; NEVER by search-result content.
 */
export type SearchPlan = {
  needsSearch: boolean
  intent: SearchIntent
  ambiguity: {
    isAmbiguous: boolean
    prompt: ClarifyPrompt | null
  }
  queries: string[]
  timeRange: TimeRange
  /** ISO-ish region hint for news engines (e.g. "BD", "US"). */
  region?: string
  /** e.g. "reuters.com" for "Search Reuters for ..." (§29-F). */
  sourceHint?: string
  depth: 'quick' | 'deep'
  /** "Search again for official sources" (§29-L). */
  officialOnly?: boolean
  /** "Stop searching and just summarise" (§19/§29-K). */
  stopSignal?: boolean
  /** Answerable from earlier evidence already in the conversation (§29-M). */
  reuseEvidence?: boolean
}

export type SearchEventName = 'search' | 'source' | 'clarify' | 'status'

/**
 * Event emitter seam — the SSE route implements this; tests capture events.
 * Payloads are JSON-serializable objects (the route stringifies them onto the
 * wire per the protocol).
 */
export type SearchEventEmitter = (event: SearchEventName, payload: unknown) => void

export const NOOP_EMIT: SearchEventEmitter = () => undefined
