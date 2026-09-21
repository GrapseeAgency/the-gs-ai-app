/**
 * PHASE 8.1 — engine adapters (§3: no single-provider dependency).
 *
 * Every engine is a self-contained adapter returning the same RawResult
 * shape. Engines FAIL INDEPENDENTLY (§22): one throttled/blocked engine
 * never kills the search — the aggregator continues with the rest and
 * reports which engines actually served.
 *
 * SearXNG status (spec §3 investigation, for the architecture report): a
 * self-hosted SearXNG instance is DROP-IN — set SEARXNG_URL and the
 * `searxng` engine below becomes the primary general engine (its JSON API,
 * categories, language, time_range and safesearch are all mapped). In THIS
 * sandbox deployment there is no Docker and a second always-on process is
 * fragile, so the default engine set is in-process aggregation of no-key
 * public endpoints (Bing News RSS, Google News RSS, Bing Web, DuckDuckGo
 * Lite, Wikipedia) + the existing z-ai search used as ONE engine among many.
 * Same contract, same fallback behaviour, no paid API anywhere (§36).
 */

import type { RawResult, SearchEngineId, TimeRange } from './types'

const UA =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36'
const ENGINE_TIMEOUT_MS = 8_000

export type EngineQuery = {
  query: string
  timeRange: TimeRange
  region?: string
  sourceHint?: string
  limit: number
}

export type EngineAdapter = {
  id: SearchEngineId
  kind: 'news' | 'general' | 'reference' | 'academic' | 'book'
  run: (q: EngineQuery) => Promise<RawResult[]>
}

// ---------------------------------------------------------------------------
// shared parsing helpers
// ---------------------------------------------------------------------------

function decodeEntities(s: string): string {
  return s
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&apos;/g, "'")
    .replace(/&nbsp;/g, ' ')
    .replace(/&#(\d+);/g, (_, d: string) => {
      const code = Number(d)
      return Number.isFinite(code) && code > 0 && code < 0x110000 ? String.fromCodePoint(code) : ' '
    })
    .replace(/&amp;/g, '&')
}

function stripTags(s: string): string {
  return decodeEntities(s.replace(/<[^>]*>/g, ' ')).replace(/\s+/g, ' ').trim()
}

function tagContent(block: string, tag: string): string {
  const m = block.match(new RegExp(`<${tag}[^>]*>([\\s\\S]*?)</${tag}>`, 'i'))
  return m ? decodeEntities(m[1]).trim() : ''
}

function safeHost(url: string): string {
  try {
    return new URL(url).hostname.replace(/^www\./, '')
  } catch {
    return ''
  }
}

function unwrapBingLink(link: string): string {
  try {
    const u = new URL(link)
    const target = u.searchParams.get('url')
    if (target && /^https?:\/\//i.test(target)) return target
  } catch {
    // keep original
  }
  return link
}

function unwrapDdgLink(link: string): string {
  if (link.startsWith('//')) link = `https:${link}`
  try {
    const u = new URL(link)
    const target = u.searchParams.get('uddg')
    if (target && /^https?:\/\//i.test(target)) return decodeURIComponent(target)
  } catch {
    // keep original
  }
  return link
}

/** Parse common RSS/HTTP date formats → ISO, else null. */
export function parseDate(raw: string | null | undefined): string | null {
  if (!raw) return null
  const t = raw.trim()
  if (!t) return null
  const d = new Date(t)
  return Number.isNaN(d.getTime()) ? null : d.toISOString()
}

async function fetchText(url: string, accept: string, ua: string = UA): Promise<string> {
  const res = await fetch(url, {
    headers: { 'User-Agent': ua, Accept: accept, 'Accept-Language': 'en-US,en;q=0.9' },
    signal: AbortSignal.timeout(ENGINE_TIMEOUT_MS),
  })
  if (!res.ok) throw new Error(`engine http ${res.status}`)
  return res.text()
}

// Wikimedia UA policy (verified live 2026-09-21): generic browser UAs from
// datacenter/automation contexts get 403; a DESCRIPTIVE app UA is served.
const WIKIMEDIA_UA = 'GS-AI-App/0.67 (https://grapsee.agency; contact@grapsee.agency)'

// ---------------------------------------------------------------------------
// engines
// ---------------------------------------------------------------------------

/** Bing News RSS — direct publisher links, date-sortable. Primary news engine. */
export const bingNewsRss: EngineAdapter = {
  id: 'bing-news-rss',
  kind: 'news',
  run: async (q) => {
    const mkt = q.region && /^[A-Za-z]{2}$/.test(q.region) ? `en-${q.region.toUpperCase()}` : 'en-US'
    const freshness = q.timeRange === 'day' ? `&qft=sortbydate%3D%221%22` : ''
    const xml = await fetchText(
      `https://www.bing.com/news/search?q=${encodeURIComponent(q.query)}${freshness}&format=RSS&mkt=${mkt}`,
      'application/rss+xml, application/xml, text/xml, */*'
    )
    const out: RawResult[] = []
    for (const block of xml.match(/<item[\s\S]*?<\/item>/gi) ?? []) {
      const title = stripTags(tagContent(block, 'title'))
      let link = tagContent(block, 'link')
      if (!title || !link) continue
      link = unwrapBingLink(link)
      if (!/^https?:\/\//i.test(link)) continue
      out.push({
        url: link,
        title,
        snippet: stripTags(tagContent(block, 'description')).slice(0, 300),
        domain: safeHost(link),
        publishedDate: parseDate(tagContent(block, 'pubDate')),
        rank: out.length + 1,
        engine: 'bing-news-rss',
      })
      if (out.length >= q.limit) break
    }
    return out
  },
}

/** Google News RSS — widest news coverage; links are redirects resolved lazily at retrieval time. */
export const googleNewsRss: EngineAdapter = {
  id: 'google-news-rss',
  kind: 'news',
  run: async (q) => {
    const when =
      q.timeRange === 'day' ? '+when:1d' : q.timeRange === 'week' ? '+when:7d' : q.timeRange === 'month' ? '+when:1m' : ''
    const gl = q.region && /^[A-Za-z]{2}$/.test(q.region) ? q.region.toUpperCase() : 'US'
    const xml = await fetchText(
      `https://news.google.com/rss/search?q=${encodeURIComponent(q.query)}${when}&hl=en-${gl}&gl=${gl}&ceid=${gl}:en`,
      'application/rss+xml, application/xml, text/xml, */*'
    )
    const out: RawResult[] = []
    for (const block of xml.match(/<item[\s\S]*?<\/item>/gi) ?? []) {
      const title = stripTags(tagContent(block, 'title'))
      const link = tagContent(block, 'link')
      if (!title || !link) continue
      const src = block.match(/<source[^>]*url="([^"]+)"/i)
      const domain = src ? safeHost(decodeEntities(src[1])) : safeHost(link)
      out.push({
        url: link,
        title,
        snippet: stripTags(tagContent(block, 'description')).slice(0, 300),
        domain: domain || 'news.google.com',
        publishedDate: parseDate(tagContent(block, 'pubDate')),
        rank: out.length + 1,
        engine: 'google-news-rss',
      })
      if (out.length >= q.limit) break
    }
    return out
  },
}

/** Bing Web HTML — general web results (b_algo blocks parse fine server-side). */
export const bingWeb: EngineAdapter = {
  id: 'bing-web',
  kind: 'general',
  run: async (q) => {
    const html = await fetchText(
      `https://www.bing.com/search?q=${encodeURIComponent(q.query)}&count=15&mkt=en-US`,
      'text/html,application/xhtml+xml,*/*;q=0.8'
    )
    const out: RawResult[] = []
    const blocks = html.match(/<li class="b_algo"[\s\S]*?<\/li>/gi) ?? []
    for (const block of blocks) {
      const linkMatch = block.match(/<h2[^>]*><a[^>]*href="([^"]+)"[^>]*>([\s\S]*?)<\/a>/i)
      if (!linkMatch) continue
      let url = unwrapBingLink(decodeEntities(linkMatch[1]))
      // PHASE 8.1 — Bing's current /ck/a redirect links carry NO resolvable
      // target param. The real publisher URL lives in the block's <cite>
      // element; use it when the link is a bing.com redirect we cannot unwrap.
      if (/^https?:\/\/(www\.)?bing\.com\//i.test(url)) {
        const cite = block.match(/<cite[^>]*>([\s\S]*?)<\/cite>/i)
        if (cite) {
          const cleaned = stripTags(cite[1])
            .split('›')[0]
            .replace(/…$/, '')
            .trim()
          if (/^https?:\/\//i.test(cleaned)) url = cleaned
          else if (/^[\w.-]+\.[a-z]{2,}/i.test(cleaned)) url = `https://${cleaned}`
        }
      }
      if (!/^https?:\/\//i.test(url) || /^https?:\/\/(www\.)?bing\.com\//i.test(url)) continue
      const title = stripTags(linkMatch[2])
      if (!title) continue
      const snippetMatch = block.match(/<p[^>]*>([\s\S]*?)<\/p>/i)
      out.push({
        url,
        title,
        snippet: snippetMatch ? stripTags(snippetMatch[1]).slice(0, 300) : '',
        domain: safeHost(url),
        publishedDate: null,
        rank: out.length + 1,
        engine: 'bing-web',
      })
      if (out.length >= q.limit) break
    }
    // Poisoned-SERP guard (found live 2026-09-20): the datacenter IP is
    // occasionally served a DECOY b_algo page — perfectly formed results for
    // an entirely different query. A SERP whose titles share zero significant
    // words with the query is rejected outright rather than letting unrelated
    // results masquerade as evidence (spec §7: never return unrelated
    // industries because the provider happened to return them).
    if (out.length > 0) {
      const qWords = new Set(
        q.query.toLowerCase().replace(/[^a-z0-9\s]/g, ' ').split(/\s+/).filter((w) => w.length > 3)
      )
      let bestOverlap = 0
      for (const r of out) {
        const tWords = r.title.toLowerCase().replace(/[^a-z0-9\s]/g, ' ').split(/\s+/)
        let hits = 0
        for (const w of qWords) if (tWords.includes(w)) hits += 1
        bestOverlap = Math.max(bestOverlap, hits)
      }
      if (qWords.size > 0 && bestOverlap === 0) return []
    }
    return out
  },
}

/**
 * DuckDuckGo Lite — tolerant HTML endpoint. (html.duckduckgo.com is blocked
 * from this deployment's IP; lite.duckduckgo.com works.)
 */
export const duckduckgoLite: EngineAdapter = {
  id: 'duckduckgo-lite',
  kind: 'general',
  run: async (q) => {
    const df =
      q.timeRange === 'day' ? '&df=d' : q.timeRange === 'week' ? '&df=w' : q.timeRange === 'month' ? '&df=m' : ''
    const html = await fetchText(
      `https://lite.duckduckgo.com/lite/?q=${encodeURIComponent(q.query)}${df}`,
      'text/html,application/xhtml+xml,*/*;q=0.8'
    )
    const out: RawResult[] = []
    const snippets: string[] = []
    for (const m of html.matchAll(/<td[^>]*class="result-snippet"[^>]*>([\s\S]*?)<\/td>/gi)) {
      snippets.push(stripTags(m[1]))
    }
    for (const m of html.matchAll(/<a[^>]*class="result-link"[^>]*href="([^"]+)"[^>]*>([\s\S]*?)<\/a>/gi)) {
      const url = unwrapDdgLink(decodeEntities(m[1]))
      if (!/^https?:\/\//i.test(url)) continue
      const title = stripTags(m[2])
      if (!title) continue
      out.push({
        url,
        title,
        snippet: snippets[out.length] ?? '',
        domain: safeHost(url),
        publishedDate: null,
        rank: out.length + 1,
        engine: 'duckduckgo-lite',
      })
      if (out.length >= q.limit) break
    }
    return out
  },
}

/** Wikipedia API — encyclopedic/factual grounding for entity questions.
 *  One bounded retry: transient 403 bursts were observed live (2026-09-21). */
export const wikipedia: EngineAdapter = {
  id: 'wikipedia',
  kind: 'reference',
  run: async (q) => {
    try {
      return await wikipediaSearch(q)
    } catch {
      await new Promise((r) => setTimeout(r, 800))
      return wikipediaSearch(q)
    }
  },
}

async function wikipediaSearch(q: EngineQuery): Promise<RawResult[]> {
    const params = new URLSearchParams({
      action: 'query',
      list: 'search',
      srsearch: q.query,
      format: 'json',
      srlimit: String(Math.min(q.limit, 5)),
    })
    const json = JSON.parse(
      await fetchText(`https://en.wikipedia.org/w/api.php?${params.toString()}`, 'application/json', WIKIMEDIA_UA)
    ) as { query?: { search?: { title?: unknown; snippet?: unknown }[] } }
    const hits = json?.query?.search ?? []
    const out: RawResult[] = []
    for (const hit of hits) {
      const title = typeof hit.title === 'string' ? hit.title : ''
      if (!title) continue
      const url = `https://en.wikipedia.org/wiki/${encodeURIComponent(title.replace(/\s/g, '_'))}`
      out.push({
        url,
        title,
        snippet: typeof hit.snippet === 'string' ? stripTags(hit.snippet).slice(0, 300) : '',
        domain: 'en.wikipedia.org',
        publishedDate: null,
        rank: out.length + 1,
        engine: 'wikipedia',
      })
      if (out.length >= q.limit) break
    }
    return out
}

/**
 * z-ai web_search — the original primary, demoted to ONE engine among many
 * (spec §2). It serves again whenever its quota window allows; when it 429s
 * the other engines carry the turn.
 */
export const zaiWebSearch: EngineAdapter = {
  id: 'z-ai',
  kind: 'general',
  run: async (q) => {
    const ZAI = (await import('z-ai-web-dev-sdk')).default
    const zai = await ZAI.create()
    const raw = (await zai.functions.invoke('web_search', {
      query: q.query,
      num: Math.min(q.limit, 8),
      ...(q.timeRange === 'day' ? { recency_days: 1 } : q.timeRange === 'week' ? { recency_days: 7 } : {}),
    })) as { result?: { url?: unknown; name?: unknown; snippet?: unknown; host_name?: unknown; date?: unknown }[] }
    const items = Array.isArray(raw?.result) ? raw.result : []
    const out: RawResult[] = []
    for (const item of items) {
      const url = typeof item.url === 'string' ? item.url : ''
      const title = typeof item.name === 'string' ? item.name : ''
      if (!url || !/^https?:\/\//i.test(url) || !title) continue
      out.push({
        url,
        title,
        snippet: typeof item.snippet === 'string' ? item.snippet.slice(0, 300) : '',
        domain: typeof item.host_name === 'string' ? item.host_name.replace(/^www\./, '') : safeHost(url),
        publishedDate: parseDate(typeof item.date === 'string' ? item.date : null),
        rank: out.length + 1,
        engine: 'z-ai',
      })
      if (out.length >= q.limit) break
    }
    return out
  },
}

/**
 * Self-hosted SearXNG (§3) — drop-in primary when SEARXNG_URL is configured.
 * Maps the standard JSON API: q, categories, language, time_range, safesearch.
 */
export const searxng: EngineAdapter = {
  id: 'searxng',
  kind: 'general',
  run: async (q) => {
    const base = process.env.SEARXNG_URL
    if (!base) throw new Error('searxng not configured')
    const params = new URLSearchParams({
      q: q.query,
      format: 'json',
      safesearch: '1',
      language: 'en',
      time_range: q.timeRange === 'none' ? '' : q.timeRange,
    })
    const json = JSON.parse(
      await fetchText(`${base.replace(/\/$/, '')}/search?${params.toString()}`, 'application/json')
    ) as { results?: { url?: unknown; title?: unknown; content?: unknown; publishedDate?: unknown }[] }
    const out: RawResult[] = []
    for (const r of json.results ?? []) {
      const url = typeof r.url === 'string' ? r.url : ''
      const title = typeof r.title === 'string' ? r.title : ''
      if (!url || !/^https?:\/\//i.test(url) || !title) continue
      out.push({
        url,
        title,
        snippet: typeof r.content === 'string' ? r.content.slice(0, 300) : '',
        domain: safeHost(url),
        publishedDate: parseDate(typeof r.publishedDate === 'string' ? r.publishedDate : null),
        rank: out.length + 1,
        engine: 'searxng',
      })
      if (out.length >= q.limit) break
    }
    return out
  },
}

// ---------------------------------------------------------------------------
// PHASE 8.2 engines — books & scholarly (§10). Lawful, key-less, metadata or
// legally free full-text APIs only. Full-text claims are made ONLY when the
// retrieval layer actually read the text (gutenberg plain-text files do);
// metadata-only hits stay snippet_only and are labelled honestly.
// ---------------------------------------------------------------------------

export const openLibrary: EngineAdapter = {
  id: 'openlibrary',
  kind: 'book',
  run: async (q) => {
    // Title-match discipline mirrors gutenberg: drop content words progressively.
    const words = q.query.split(/\s+/).filter(Boolean)
    const attempts = [...new Set([q.query, words.slice(0, 3).join(' ')])].filter((a) => a.length >= 3)
    for (const attempt of attempts) {
      const out = await parseOpenLibrarySearch(attempt, q.limit)
      if (out.length > 0) return out
    }
    return []
  },
}

async function parseOpenLibrarySearch(query: string, limit: number): Promise<RawResult[]> {
  const params = new URLSearchParams({ q: query, limit: String(Math.min(limit, 8)), fields: 'key,title,author_name,first_publish_year' })
  const json = JSON.parse(
    await fetchText(`https://openlibrary.org/search.json?${params.toString()}`, 'application/json')
  ) as { docs?: { key?: unknown; title?: unknown; author_name?: unknown; first_publish_year?: unknown }[] }
  const out: RawResult[] = []
  for (const d of json.docs ?? []) {
    const key = typeof d.key === 'string' ? d.key : ''
    const title = typeof d.title === 'string' ? d.title : ''
    if (!key.startsWith('/works/') || !title) continue
    const authors = Array.isArray(d.author_name) ? d.author_name.filter((a): a is string => typeof a === 'string').slice(0, 2) : []
    const year = typeof d.first_publish_year === 'number' ? d.first_publish_year : null
    out.push({
      url: `https://openlibrary.org${key}`,
      title: authors.length > 0 ? `${title} — ${authors.join(', ')}` : title,
      snippet: [authors.join(', '), year ? `first published ${year}` : ''].filter(Boolean).join(' · '),
      domain: 'openlibrary.org',
      publishedDate: null,
      rank: out.length + 1,
      engine: 'openlibrary',
    })
    if (out.length >= limit) break
  }
  return out
}

/**
 * Project Gutenberg — PUBLIC-DOMAIN full texts. Search uses gutenberg.org's
 * own HTML search (gutendex.com 403s datacenter IPs — observed live 2026-09-21).
 * Gutenberg search matches TITLE/AUTHOR words only, so content-word queries
 * ("Frankenstein creature demands Victor") return nothing — the adapter
 * retries with progressively shorter title-like prefixes (bounded, 8.2 §33).
 * The result IS the full text (/ebooks/{id}.txt.utf-8), so retrieval reads
 * REAL passages.
 */
export const gutenberg: EngineAdapter = {
  id: 'gutenberg',
  kind: 'book',
  run: async (q) => {
    const words = q.query.split(/\s+/).filter(Boolean)
    const attempts = [...new Set([q.query, words.slice(0, 3).join(' '), words.slice(0, 2).join(' ')])].filter((a) => a.length >= 2)
    for (const attempt of attempts) {
      const out = await parseGutenbergSearch(attempt, q.limit)
      if (out.length > 0) return out
    }
    return []
  },
}

async function parseGutenbergSearch(query: string, limit: number): Promise<RawResult[]> {
  const html = await fetchText(
    `https://www.gutenberg.org/ebooks/search/?query=${encodeURIComponent(query)}`,
    'text/html,application/xhtml+xml,*/*;q=0.8'
  )
  const out: RawResult[] = []
  for (const block of html.match(/<li class="booklink">[\s\S]*?<\/li>/gi) ?? []) {
    const link = block.match(/<a[^>]*href="(?:https:\/\/www\.gutenberg\.org)?(\/ebooks\/\d+)"[^>]*>/i)?.[1]
    const title = stripTags(block.match(/<span class="title">([\s\S]*?)<\/span>/i)?.[1] ?? '')
    if (!link || !title) continue
    const author = stripTags(block.match(/<span class="subtitle">([\s\S]*?)<\/span>/i)?.[1] ?? '')
    out.push({
      // §33 — the result IS the public-domain full text (gutenberg serves
      // /ebooks/{id}.txt.utf-8), so retrieval reads REAL passages.
      url: `https://www.gutenberg.org${link}.txt.utf-8`,
      title: author ? `${title} — ${author}` : title,
      snippet: 'Public-domain full text available at Project Gutenberg.',
      domain: 'gutenberg.org',
      publishedDate: null,
      rank: out.length + 1,
      engine: 'gutenberg',
    })
    if (out.length >= limit) break
  }
  return out
}

/** arXiv — scholarly preprints (Atom API, abstracts lawfully served). */
export const arxiv: EngineAdapter = {
  id: 'arxiv',
  kind: 'academic',
  run: async (q) => {
    const xml = await fetchText(
      `http://export.arxiv.org/api/query?search_query=all:${encodeURIComponent(q.query)}&start=0&max_results=${Math.min(q.limit, 8)}&sortBy=relevance`,
      'application/atom+xml, application/xml, text/xml, */*'
    )
    const out: RawResult[] = []
    for (const block of xml.match(/<entry[\s\S]*?<\/entry>/gi) ?? []) {
      const title = stripTags(tagContent(block, 'title'))
      const idLink = block.match(/<id>([\s\S]*?)<\/id>/i)?.[1]?.trim() ?? ''
      if (!title || !/^https?:\/\//.test(idLink)) continue
      const summary = stripTags(tagContent(block, 'summary')).slice(0, 300)
      out.push({
        url: idLink,
        title,
        snippet: summary,
        domain: safeHost(idLink) || 'arxiv.org',
        publishedDate: parseDate(tagContent(block, 'published')),
        rank: out.length + 1,
        engine: 'arxiv',
      })
      if (out.length >= q.limit) break
    }
    return out
  },
}

/** Crossref — scholarly metadata index (journals, DOIs; lawful public API). */
export const crossref: EngineAdapter = {
  id: 'crossref',
  kind: 'academic',
  run: async (q) => {
    const params = new URLSearchParams({ query: q.query, rows: String(Math.min(q.limit, 8)), select: 'title,URL,DOI,issued,container-title' })
    const json = JSON.parse(
      await fetchText(`https://api.crossref.org/works?${params.toString()}`, 'application/json')
    ) as { message?: { items?: { title?: unknown; URL?: unknown; DOI?: unknown; issued?: { 'date-parts'?: number[][] }; 'container-title'?: unknown }[] } }
    const out: RawResult[] = []
    for (const it of json.message?.items ?? []) {
      const title = Array.isArray(it.title) && typeof it.title[0] === 'string' ? it.title[0] : ''
      const url = typeof it.URL === 'string' && /^https?:\/\//.test(it.URL) ? it.URL : typeof it.DOI === 'string' ? `https://doi.org/${it.DOI}` : ''
      if (!title || !url) continue
      const venue = Array.isArray(it['container-title']) && typeof it['container-title'][0] === 'string' ? it['container-title'][0] : ''
      const parts = it.issued?.['date-parts']?.[0]
      const publishedDate = Array.isArray(parts) && parts.length >= 3 ? new Date(Date.UTC(parts[0], parts[1] - 1, parts[2])).toISOString() : null
      out.push({
        url,
        title: venue ? `${title} — ${venue}` : title,
        snippet: venue ? `Published in ${venue}.` : '',
        domain: safeHost(url),
        publishedDate,
        rank: out.length + 1,
        engine: 'crossref',
      })
      if (out.length >= q.limit) break
    }
    return out
  },
}

export const ENGINE_REGISTRY: Partial<Record<SearchEngineId, EngineAdapter>> = {
  'bing-news-rss': bingNewsRss,
  'google-news-rss': googleNewsRss,
  'bing-web': bingWeb,
  'duckduckgo-lite': duckduckgoLite,
  wikipedia,
  'z-ai': zaiWebSearch,
  searxng,
  openlibrary: openLibrary,
  gutenberg,
  arxiv,
  crossref,
}

/**
 * Engine selection per intent (8.2 §7/§10/§11). Order matters only as tie-break
 * priority — the aggregator runs the set in parallel.
 * SearXNG, when configured, leads the general set.
 */
export function enginesForIntent(intent: string, isNews: boolean, officialOnly: boolean): SearchEngineId[] {
  const searx = process.env.SEARXNG_URL ? (['searxng'] as SearchEngineId[]) : []
  // 8.2 §11/§23 — "official sources only": first-party-friendly engines only;
  // no aggregator/news engines, no third-party provider search.
  if (officialOnly) {
    return [...searx, 'bing-web', 'wikipedia', 'duckduckgo-lite']
  }
  if (intent === 'academic') {
    return [...searx, 'arxiv', 'crossref', 'wikipedia', 'bing-web', 'duckduckgo-lite']
  }
  if (intent === 'book_source') {
    return [...searx, 'gutenberg', 'openlibrary', 'bing-web', 'duckduckgo-lite']
  }
  if (intent === 'historical_religious') {
    // Primary/reference first (8.2 §11): encyclopedic grounding + scholarly index.
    return [...searx, 'wikipedia', 'bing-web', 'duckduckgo-lite', 'crossref', 'z-ai']
  }
  if (isNews) {
    return officialOnly
      ? [...searx, 'bing-web', 'duckduckgo-lite']
      : [...searx, 'bing-news-rss', 'google-news-rss', 'duckduckgo-lite', 'z-ai']
  }
  if (intent === 'factual' || intent === 'research' || intent === 'comparison') {
    return [...searx, 'bing-web', 'duckduckgo-lite', 'wikipedia', 'bing-news-rss', 'z-ai']
  }
  return [...searx, 'bing-web', 'duckduckgo-lite', 'z-ai']
}
