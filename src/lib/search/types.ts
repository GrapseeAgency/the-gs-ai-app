/**
 * PHASE 8.1/8.2 — shared types for the backend-owned search foundation.
 *
 * Architecture rule (8.2 §1): the model is the reasoning/synthesis layer;
 * search is OUR backend capability. The model never invents the result set —
 * it only ever sees evidence produced by `runResearch()`, and every visible
 * research step in the clients maps 1:1 to an event emitted here.
 *
 * Wire contract: docs/search-event-protocol.md (FROZEN v2).
 */

export type SearchEngineId =
  | 'z-ai'
  | 'bing-news-rss'
  | 'google-news-rss'
  | 'bing-web'
  | 'duckduckgo-lite'
  | 'wikipedia'
  | 'searxng'
  // OPT-IN: Brave Search API — free tier (2k/month) behind a key, official
  // JSON, no scraping. Present only when BRAVE_API_KEY is set — see engines.ts.
  | 'brave'
  // OPT-IN: treg WebAccess (one token in front of a vendor chain). Present in
  // the registry only when TREG_TOKEN is set — see engines.ts.
  | 'treg'
  // PHASE 8.2 — books & scholarly (lawful/no-key APIs only, 8.2 §10)
  | 'openlibrary'
  | 'gutenberg'
  | 'arxiv'
  | 'crossref'
  | 'github-release'
  // Google's index reached through the Gemini API's google_search grounding
  // (a real search, not scraping). Present only when GOOGLE_API_KEYS /
  // GOOGLE_API_KEY is configured — see engines.ts. The model emits the exact
  // source URLs it used; our retrieval layer then fetches and verifies them,
  // so a hallucinated URL dies on fetch and never reaches evidence.
  | 'gemini-grounded'

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

/** Retrieval state of a source — persisted and rendered verbatim (no fake visuals). */
export type SourceStatus =
  | 'discovered'
  | 'retrieved'
  | 'snippet_only'
  | 'failed'
  | 'skipped'
  | 'used'

/**
 * PHASE 8.2 (§3) — source-class + authority tiers. Assigned by the AGGREGATOR
 * from URL/engine facts, never from model text.
 */
export type SourceType = 'news' | 'general' | 'reference' | 'academic' | 'book' | 'primary'
export type Authority = 'primary' | 'academic' | 'reputable' | 'reference' | 'discovery'

/**
 * Canonical source object (§11 source evidence object / 8.2 ResearchEvidence).
 * Everything except `pageExtract` is provider metadata or HTTP fact — never
 * model text.
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
  /** 8.2 §3 — source class + authority tier (aggregator-assigned). */
  sourceType: SourceType
  authority: Authority
  /** 8.2 §12 — ordinal of the representative source this duplicates (syndicated wire copy). */
  syndicatedOf?: number
  /** 8.2 §14 — set when the page was read via the bounded browser fallback. */
  retrievalVia?: 'http' | 'browser'
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
  // PHASE 8.2 (§7) — finer intent classes
  | 'academic'
  | 'book_source'
  | 'historical_religious'
  | 'comparison'

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
  /** "Search again for official sources" (§29-L / 8.2 §23). */
  officialOnly?: boolean
  /** "Stop searching and just summarise" (§19/§29-K). */
  stopSignal?: boolean
  /** Answerable from earlier evidence already in the conversation (§29-M). */
  reuseEvidence?: boolean
}

/**
 * 8.2 §28 — `research` joins the wire event names (protocol v2).
 * PHASE 5 — `ledger` joins the wire event names: deep-research branch
 * progress (branch started/completed/failed, gap spawns, contradiction
 * counts). The SSE transport forwards event names generically, so this
 * entry is what put the whole branching event stream on the wire — before
 * it, every `emit('ledger', …)` was a type error and never shipped.
 */
export type SearchEventName = 'search' | 'source' | 'clarify' | 'status' | 'research' | 'ledger'

/**
 * PHASE 5 — the `ledger` payload contract (discriminated on `type`), emitted
 * by runBranchingResearch. Clients render what arrives and ignore what they
 * do not model — the union is the documentation, not a runtime guard.
 */
export type LedgerEvent =
  | { type: 'ledger_started'; question: string; branches: number }
  | { type: 'branch_started'; branchId: string; query: string }
  | { type: 'branch_completed'; branchId: string; query: string; sources: number; retrieved: number; ok: boolean; via?: 'subagent' }
  | { type: 'branch_failed'; branchId: string; query: string; error: string }
  | { type: 'gaps_open'; gaps: string[]; queries: string[] }
  | { type: 'subagents_launched'; count: number }
  | { type: 'contradictions'; count: number; note: string }
  | { type: 'ledger_final'; claims: number; contradictions: number; openGaps: number; branches: number }
  | { type: 'stopped_by_user'; note: string }

/**
 * Event emitter seam — the SSE route implements this; tests capture events.
 * Payloads are JSON-serializable objects (the route stringifies them onto the
 * wire per the protocol).
 */
export type SearchEventEmitter = (event: SearchEventName, payload: unknown) => void

export const NOOP_EMIT: SearchEventEmitter = () => undefined
