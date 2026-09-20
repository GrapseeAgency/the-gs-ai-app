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
  kind: 'news' | 'general' | 'reference'
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

async function fetchText(url: string, accept: string): Promise<string> {
  const res = await fetch(url, {
    headers: { 'User-Agent': UA, Accept: accept, 'Accept-Language': 'en-US,en;q=0.9' },
    signal: AbortSignal.timeout(ENGINE_TIMEOUT_MS),
  })
  if (!res.ok) throw new Error(`engine http ${res.status}`)
  return res.text()
}

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

/** Wikipedia API — encyclopedic/factual grounding for entity questions. */
export const wikipedia: EngineAdapter = {
  id: 'wikipedia',
  kind: 'reference',
  run: async (q) => {
    const params = new URLSearchParams({
      action: 'query',
      list: 'search',
      srsearch: q.query,
      format: 'json',
      srlimit: String(Math.min(q.limit, 5)),
    })
    const json = JSON.parse(
      await fetchText(`https://en.wikipedia.org/w/api.php?${params.toString()}`, 'application/json')
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
  },
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

export const ENGINE_REGISTRY: Partial<Record<SearchEngineId, EngineAdapter>> = {
  'bing-news-rss': bingNewsRss,
  'google-news-rss': googleNewsRss,
  'bing-web': bingWeb,
  'duckduckgo-lite': duckduckgoLite,
  wikipedia,
  'z-ai': zaiWebSearch,
  searxng,
}

/**
 * Engine selection per intent (§7/§8/§17). Order matters only as tie-break
 * priority — the aggregator runs the set in parallel.
 * SearXNG, when configured, leads the general set.
 */
export function enginesForIntent(intent: string, isNews: boolean, officialOnly: boolean): SearchEngineId[] {
  const searx = process.env.SEARXNG_URL ? (['searxng'] as SearchEngineId[]) : []
  if (isNews) {
    return officialOnly
      ? [...searx, 'bing-web', 'duckduckgo-lite']
      : [...searx, 'bing-news-rss', 'google-news-rss', 'duckduckgo-lite', 'z-ai']
  }
  if (intent === 'factual' || intent === 'research') {
    return [...searx, 'bing-web', 'duckduckgo-lite', 'wikipedia', 'bing-news-rss', 'z-ai']
  }
  return [...searx, 'bing-web', 'duckduckgo-lite', 'z-ai']
}
