/**
 * PHASE 8.1 — metasearch aggregator (§3/§8/§9/§22).
 *
 * Runs the intent-selected engine set in parallel (independent failures),
 * merges, dedupes, and RANKS by source quality — never by raw engine order:
 *
 *   FIRST-PARTY/official  >  high-quality journalism  >  specialist  >  aggregators
 *   + recency (news), + multi-engine agreement, + query-term overlap
 *   − wire-story repetition (max 2 per domain), − discovery-only aggregators
 *
 * The model MUST NOT invent this result set; it only ever receives what
 * comes out of here.
 */

import { ENGINE_REGISTRY, enginesForIntent, type EngineQuery } from './engines'
import type { RawResult, SearchIntent, TimeRange } from './types'

export type AggregatedResult = RawResult & {
  score: number
  engines: string[]
}

export type MetaSearchOutcome = {
  results: AggregatedResult[]
  enginesUsed: string[]
  enginesFailed: { engine: string; error: string }[]
}

// ---------------------------------------------------------------------------
// Source-quality lists (§8) — bounded, curated, heuristics on top.
// ---------------------------------------------------------------------------

const FIRST_PARTY_DOMAINS = new Set([
  'developer.android.com', 'android.com', 'blog.google', 'googleblog.com', 'developers.google.com',
  'developer.apple.com', 'apple.com', 'apple.newsroom',
  'microsoft.com', 'learn.microsoft.com', 'blogs.microsoft.com', 'azure.microsoft.com',
  'openai.com', 'anthropic.com', 'deepmind.google', 'ai.googleblog.com',
  'who.int', 'un.org', 'worldbank.org', 'imf.org', 'nasa.gov', 'nih.gov', 'cdc.gov',
  'sec.gov', 'esa.int', 'nvd.nist.gov', 'cve.org', 'cisa.gov', 'europa.eu',
  'kernel.org', 'python.org', 'nodejs.org', 'golang.org', 'rust-lang.org',
  'kotlinlang.org', 'developer.mozilla.org', 'w3.org', 'ietf.org', 'iso.org',
])

const JOURNALISM_DOMAINS = new Set([
  'reuters.com', 'apnews.com', 'afp.com', 'bbc.com', 'bbc.co.uk', 'theguardian.com',
  'nytimes.com', 'washingtonpost.com', 'wsj.com', 'ft.com', 'bloomberg.com',
  'economist.com', 'ft.com', 'aljazeera.com', 'cnn.com', 'edition.cnn.com',
  'nbcnews.com', 'cbsnews.com', 'abcnews.go.com', 'npr.org', 'pbs.org',
  'time.com', 'newsweek.com', 'usatoday.com', 'latimes.com', 'irrawaddy.com',
  // Bangladesh regional journalism (§7 geo intent)
  'thedailystar.net', 'prothomalo.com', 'dhakatribune.com', 'bdnews24.com',
  'theindependentbd.com', 'newagebd.net', 'tbsnews.net', 'thedailycountry.com',
])

// Specialist publications by sector keyword (§8 specialist sources).
const SPECIALIST_DOMAINS: { match: RegExp; domains: Set<string> }[] = [
  { match: /\b(cybersecurity|security|hack|breach|malware|ransomware|vulnerab)/i, domains: new Set(['thehackernews.com', 'bleepingcomputer.com', 'krebsonsecurity.com', 'securityweek.com', 'darkreading.com', 'therecord.media', 'schneier.com']) },
  { match: /\b(artificial intelligence|\bai\b|machine learning|llm|chatgpt|gemini)\b/i, domains: new Set(['techcrunch.com', 'theverge.com', 'arstechnica.com', 'venturebeat.com', 'spectrum.ieee.org', 'technologyreview.com']) },
  { match: /\b(android|smartphone|mobile|galaxy|iphone)\b/i, domains: new Set(['9to5google.com', '9to5mac.com', 'androidauthority.com', 'xdadevelopers.com', 'theverge.com', 'gsmaresena.com']) },
  { match: /\b(football|soccer|cricket|sports?\b|olympic)/i, domains: new Set(['espn.com', 'bbc.com', 'skysports.com', 'goal.com', 'cricbuzz.com', 'espncricinfo.com']) },
  { match: /\b(science|space|physics|climate|research)/i, domains: new Set(['nature.com', 'science.org', 'newscientist.com', 'scientificamerican.com', 'space.com', 'phys.org']) },
  { match: /\b(health|medicine|disease|vaccine|hospital)/i, domains: new Set(['statnews.com', 'medicalnewstoday.com', 'healthline.com', 'who.int']) },
  { match: /\b(finance|market|economy|stocks?|crypto|banking)/i, domains: new Set(['cnbc.com', 'marketwatch.com', 'investing.com', 'coinjournal.com', 'coindesk.com']) },
]

// Discovery-only aggregators (§8): fine for finding, weak as sole evidence.
const AGGREGATOR_PENALTY_DOMAINS = new Set([
  'news.google.com', 'msn.com', 'news.yahoo.com', 'yahoo.com', 'sports.yahoo.com',
  'flipboard.com', 'trends.google.com',
])

function domainMatches(url: string, set: Set<string>): boolean {
  try {
    const host = new URL(url).hostname.replace(/^www\./, '')
    if (set.has(host)) return true
    for (const d of set) {
      if (host.endsWith(`.${d}`) || host === d) return true
    }
    return false
  } catch {
    return false
  }
}

function isGovOrEdu(url: string): boolean {
  try {
    const host = new URL(url).hostname
    return /\.(gov|edu|mil)(\.[a-z]{2})?$/.test(host) || host.endsWith('.gov.uk')
  } catch {
    return false
  }
}

// ---------------------------------------------------------------------------
// normalisation + dedupe
// ---------------------------------------------------------------------------

function normalizeUrlForDedupe(raw: string): string {
  try {
    const u = new URL(raw)
    u.hash = ''
    for (const key of [...u.searchParams.keys()]) {
      if (/^utm_|^fbclid$|^gclid$|^ref$|^ref_src$|^cmp$|^ncid$/i.test(key)) u.searchParams.delete(key)
    }
    u.hostname = u.hostname.replace(/^www\./, '').toLowerCase()
    u.protocol = 'https:'
    let s = u.toString()
    if (s.endsWith('/')) s = s.slice(0, -1)
    return s
  } catch {
    return raw
  }
}

function titleWords(title: string): Set<string> {
  return new Set(
    title
      .toLowerCase()
      .replace(/[^a-z0-9\s]/g, ' ')
      .split(/\s+/)
      .filter((w) => w.length > 3)
  )
}

function termOverlap(query: string, title: string): number {
  const qw = titleWords(query)
  if (qw.size === 0) return 0
  const tw = titleWords(title)
  let hits = 0
  for (const w of qw) if (tw.has(w)) hits += 1
  return hits / qw.size
}

function recencyScore(publishedDate: string | null, timeRange: TimeRange, isNews: boolean): number {
  if (!publishedDate) return isNews && timeRange !== 'none' ? -1.5 : 0
  const ageHours = (Date.now() - new Date(publishedDate).getTime()) / 3_600_000
  if (timeRange === 'day' || (isNews && timeRange === 'none')) {
    if (ageHours <= 30) return 3
    if (ageHours <= 48) return 1.5
    if (ageHours <= 24 * 4) return 0
    return -3 // stale pages must not ride "today's news" (§17)
  }
  if (timeRange === 'week') return ageHours <= 24 * 8 ? 2.5 : ageHours <= 24 * 14 ? 0.5 : -2
  if (timeRange === 'month') return ageHours <= 24 * 35 ? 2 : -1
  return ageHours <= 24 * 2 ? 1.5 : 0
}

// ---------------------------------------------------------------------------
// the aggregator
// ---------------------------------------------------------------------------

export type MetaSearchInput = {
  queries: string[]
  intent: SearchIntent
  timeRange: TimeRange
  region?: string
  sourceHint?: string
  officialOnly?: boolean
  limit: number
  engineTimeoutMs?: number
}

export async function runMetaSearch(input: MetaSearchInput): Promise<MetaSearchOutcome> {
  const isNews =
    input.intent === 'broad_news' ||
    input.intent === 'sector_news' ||
    input.intent === 'entity_news' ||
    input.intent === 'geo_news' ||
    input.intent === 'time_window_news' ||
    input.intent === 'source_specific'

  const engineIds = enginesForIntent(input.intent, isNews, input.officialOnly === true)
  const perQuery = Math.max(input.limit, 6)

  // Fan out: every (query × engine) pair runs independently; failures are
  // collected, never fatal (§22 — one provider failure ≠ search failure).
  const tasks: Promise<RawResult[]>[] = []
  const taskLabels: string[] = []
  const boundedQueries = input.queries.slice(0, 3)
  for (const query of boundedQueries) {
    for (const engineId of engineIds) {
      const adapter = ENGINE_REGISTRY[engineId]
      if (!adapter) continue
      const eq: EngineQuery = {
        query,
        timeRange: input.timeRange,
        region: input.region,
        sourceHint: input.sourceHint,
        limit: perQuery,
      }
      const label = `${engineId}:"${query.slice(0, 60)}"`
      taskLabels.push(label)
      tasks.push(
        Promise.race([
          adapter.run(eq),
          new Promise<RawResult[]>((_, reject) =>
            setTimeout(() => reject(new Error('engine timeout')), input.engineTimeoutMs ?? 9_000)
          ),
        ]).catch((e: unknown) => {
          throw e
        })
      )
    }
  }

  const settled = await Promise.allSettled(tasks)
  const enginesUsed = new Set<string>()
  const enginesFailed: { engine: string; error: string }[] = []
  const byUrl = new Map<string, AggregatedResult>()

  settled.forEach((res, i) => {
    const label = taskLabels[i]
    const engineId = label.slice(0, label.indexOf(':'))
    if (res.status === 'fulfilled') {
      if (res.value.length > 0) enginesUsed.add(engineId)
      for (const r of res.value) {
        const key = normalizeUrlForDedupe(r.url)
        const existing = byUrl.get(key)
        if (existing) {
          // Multi-engine agreement is a quality signal (§8).
          if (!existing.engines.includes(r.engine)) {
            existing.engines.push(r.engine)
            existing.score += 2
          }
          if (!existing.publishedDate && r.publishedDate) existing.publishedDate = r.publishedDate
          if (existing.snippet.length < 40 && r.snippet.length > existing.snippet.length) {
            existing.snippet = r.snippet
          }
        } else {
          byUrl.set(key, { ...r, url: r.url, score: 0, engines: [r.engine] })
        }
      }
    } else {
      const err = res.reason instanceof Error ? res.reason.message : String(res.reason)
      enginesFailed.push({ engine: engineId, error: err.slice(0, 120) })
    }
  })

  // ---- ranking pass (§8) -------------------------------------------------
  const candidates = [...byUrl.values()]
  for (const c of candidates) {
    let score = 0
    score += recencyScore(c.publishedDate, input.timeRange, isNews)
    if (isGovOrEdu(c.url) || domainMatches(c.url, FIRST_PARTY_DOMAINS)) score += 3.5
    if (domainMatches(c.url, JOURNALISM_DOMAINS)) score += 2.5
    for (const spec of SPECIALIST_DOMAINS) {
      if (spec.match.test(boundedQueries.join(' ')) && domainMatches(c.url, spec.domains)) {
        score += 2.5
        break
      }
    }
    if (domainMatches(c.url, AGGREGATOR_PENALTY_DOMAINS)) score -= 2
    score += Math.max(0, 1.2 - (c.rank - 1) * 0.15) // engine rank position
    score += termOverlap(boundedQueries.join(' '), c.title) * 2
    if (input.sourceHint) {
      const hint = input.sourceHint.replace(/^https?:\/\//, '').replace(/^www\./, '')
      if (c.domain === hint || c.domain.endsWith(`.${hint}`) || hint.includes(c.domain)) score += 6
    }
    score += (c.engines.length - 1) * 0.5
    c.score = Math.round(score * 100) / 100
  }

  candidates.sort((a, b) => b.score - a.score)

  // Domain diversity: at most 2 per domain near the top (§9 — five repeats of
  // the same wire story is not evidence, it is echo).
  const perDomain = new Map<string, number>()
  const diverse: AggregatedResult[] = []
  for (const c of candidates) {
    const n = perDomain.get(c.domain) ?? 0
    if (n >= 2 && diverse.length < input.limit) continue
    perDomain.set(c.domain, n + 1)
    diverse.push(c)
    if (diverse.length >= input.limit) break
  }

  return { results: diverse, enginesUsed: [...enginesUsed], enginesFailed }
}
