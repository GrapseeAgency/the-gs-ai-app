/**
 * PHASE 8.1 — readable-article extraction (§10) + Google News link
 * resolution. Regex/DOM-lite on purpose: zero new dependencies, bounded
 * input (the fetcher caps bytes), bounded output (chars caps below).
 *
 * A search result becomes EVIDENCE only when extractArticle() actually
 * pulls text out of the page. Extraction failure is honest and local:
 * the source is marked failed/snippet_only, never summarised from its
 * headline.
 */

import { fetchPage, FetchPageError, isSafePublicHttpUrl } from './fetcher'

export type ExtractedArticle = {
  title: string | null
  byline: string | null
  datePublished: string | null
  text: string
  chars: number
}

const EXTRACT_MAX_TEXT_CHARS = 6_000

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

function metaContent(html: string, keys: RegExp): string | null {
  // NOTE: keys.source is interpolated as a GROUPED alternation — ungrouped,
  // "og:title|twitter:title" would bind | to the WHOLE pattern and the left
  // branch could match with the capture group never participating (m[1] ===
  // undefined → crash; found live on xda-developers/polygon pages).
  const src = `(?:${keys.source})`
  const re = new RegExp(
    `<meta[^>]+(?:property|name)=["']${src}["'][^>]*content=["']([^"']*)["']`,
    'i'
  )
  const alt = new RegExp(
    `<meta[^>]+content=["']([^"']*)["'][^>]*(?:property|name)=["']${src}["']`,
    'i'
  )
  const m = html.match(re) ?? html.match(alt)
  return m && typeof m[1] === 'string' ? decodeEntities(m[1]).trim() || null : null
}

function findBlock(html: string, tagRe: RegExp): string | null {
  const m = html.match(tagRe)
  return m ? m[1] : null
}

/** Remove non-content regions before paragraph mining. */
function stripChrome(html: string): string {
  return html
    .replace(/<script[^>]*>[\s\S]*?<\/script>/gi, ' ')
    .replace(/<style[^>]*>[\s\S]*?<\/style>/gi, ' ')
    .replace(/<noscript[^>]*>[\s\S]*?<\/noscript>/gi, ' ')
    .replace(/<svg[^>]*>[\s\S]*?<\/svg>/gi, ' ')
    .replace(/<iframe[^>]*>[\s\S]*?<\/iframe>/gi, ' ')
    .replace(/<(nav|header|footer|aside|form|button)[^>]*>[\s\S]*?<\/\1>/gi, ' ')
    .replace(/<!--[\s\S]*?-->/g, ' ')
}

/**
 * Main-article extraction. Falls back through: <article> → <main> /
 * [role=main] → whole-page paragraphs with a density floor.
 *
 * PHASE 8.2 §33 — long documents (public-domain book full texts) may request
 * a larger cap; the kept window centers on the QUERY terms so a real relevant
 * passage — not the document head — reaches the evidence block.
 */
export function extractArticle(
  html: string,
  opts?: { maxTextChars?: number; windowQuery?: string }
): ExtractedArticle {
  const title =
    metaContent(html, /og:title|twitter:title/) ??
    (html.match(/<title[^>]*>([\s\S]*?)<\/title>/i)?.[1] ? stripTags(html.match(/<title[^>]*>([\s\S]*?)<\/title>/i)![1]) : null) ??
    (html.match(/<h1[^>]*>([\s\S]*?)<\/h1>/i)?.[1] ? stripTags(html.match(/<h1[^>]*>([\s\S]*?)<\/h1>/i)![1]) : null)

  const byline = metaContent(html, /author|byline/)

  let datePublished: string | null =
    metaContent(html, /article:published_time|pubdate|publish-date|dc\.date|parsely-pub-date/) ??
    (html.match(/<time[^>]+datetime=["']([^"']+)["']/i)?.[1] ?? null)
  if (!datePublished) {
    const ld = html.match(/"datePublished"\s*:\s*"([^"]+)"/i)
    if (ld) datePublished = ld[1]
  }
  if (datePublished) {
    const d = new Date(datePublished)
    datePublished = Number.isNaN(d.getTime()) ? datePublished : d.toISOString()
  }

  const cleaned = stripChrome(html)
  const candidates: (string | null)[] = [
    findBlock(cleaned, /<article[^>]*>([\s\S]*?)<\/article>/i),
    findBlock(cleaned, /<main[^>]*>([\s\S]*?)<\/main>/i),
    findBlock(cleaned, /<div[^>]+role=["']main["'][^>]*>([\s\S]*)/i),
  ]

  let text = ''
  for (const block of candidates) {
    if (!block) continue
    const parts = [...block.matchAll(/<(?:p|li|h2|h3|blockquote|pre)[^>]*>([\s\S]*?)<\/(?:p|li|h2|h3|blockquote|pre)>/gi)]
      .map((m) => stripTags(m[1]))
      .filter((t) => t.length >= 40)
    const joined = parts.join('\n\n')
    if (joined.length > text.length) text = joined
    if (text.length > 1200) break
  }
  if (text.length < 200) {
    // Low-density fallback: every paragraph of the page, then plain text.
    const parts = [...cleaned.matchAll(/<p[^>]*>([\s\S]*?)<\/p>/gi)].map((m) => stripTags(m[1])).filter((t) => t.length >= 60)
    text = parts.join('\n\n')
  }
  if (text.length < 120) {
    text = stripTags(cleaned)
  }

  const maxChars = Math.max(EXTRACT_MAX_TEXT_CHARS, opts?.maxTextChars ?? EXTRACT_MAX_TEXT_CHARS)
  if (text.length > maxChars) {
    const qWords = (opts?.windowQuery ?? '').toLowerCase().match(/[a-z0-9]{4,}/g) ?? []
    let kept: string | null = null
    if (qWords.length > 0) {
      let bestIdx = -1
      let bestHits = 0
      for (let i = 0; i < Math.min(text.length - 500, 400_000); i += 2_000) {
        const chunk = text.slice(i, i + 2_500).toLowerCase()
        const hits = qWords.reduce((n, w) => n + (chunk.includes(w) ? 1 : 0), 0)
        if (hits > bestHits) {
          bestHits = hits
          bestIdx = i
        }
      }
      if (bestHits > 0 && bestIdx > 0) {
        const start = Math.max(0, bestIdx - 400)
        kept = `…${text.slice(start, start + maxChars)}`
      }
    }
    text = kept ?? text.slice(0, maxChars)
  }
  text = text.slice(0, maxChars)
  // Cut at a word boundary so the evidence block never ends mid-word.
  if (text.length === maxChars) {
    const lastSpace = text.lastIndexOf(' ')
    if (lastSpace > maxChars * 0.8) text = text.slice(0, lastSpace)
  }

  return {
    title: title ? title.slice(0, 300) : null,
    byline: byline ? byline.slice(0, 200) : null,
    datePublished,
    text,
    chars: text.length,
  }
}

// ---------------------------------------------------------------------------
// Google News redirect resolution
// ---------------------------------------------------------------------------

/**
 * Google News RSS item links point at news.google.com/rss/articles/…
 * which is NOT the publisher URL. Resolve best-effort: fetch the redirect
 * page and pull the publisher link out of it. On failure the source keeps
 * the redirect URL and is retrieved as snippet-only — never fabricated.
 */
export async function resolveGoogleNewsLink(url: string, timeoutMs = 6_000): Promise<string> {
  if (!/news\.google\.com\/rss\/articles\//i.test(url)) return url
  if (!isSafePublicHttpUrl(url)) return url
  try {
    const page = await fetchPage(url, { timeoutMs, maxBytes: 300_000 })
    // 1) data-n-au attribute (older embeds carry the publisher URL there)
    const dataN = page.body.match(/data-n-au=["']([^"']+)["']/i)
    if (dataN && /^https?:\/\//.test(dataN[1]) && !/news\.google\./.test(dataN[1])) return dataN[1]
    // 2) <a href> anchors to non-Google publishers
    for (const m of page.body.matchAll(/<a[^>]+href=["'](https?:\/\/[^"']+)["']/gi)) {
      const href = decodeEntities(m[1])
      if (!/google\./i.test(href) && isSafePublicHttpUrl(href)) return href
    }
    // 3) meta refresh
    const refresh = page.body.match(/http-equiv=["']?refresh["']?[^>]*url=([^"'>]+)/i)
    if (refresh) {
      const target = refresh[1].replace(/['"]/g, '').trim()
      if (/^https?:\/\//.test(target) && !/google\./i.test(target)) return target
    }
  } catch {
    // fall through
  }
  return url
}

/**
 * Retrieve + extract one source. Returns the extraction, the FINAL url
 * (redirects + Google News resolution applied), and timing. Throws
 * FetchPageError on failure — callers mark the source honestly.
 */
export async function retrieveAndExtract(
  url: string,
  timeoutMs = 10_000,
  opts?: { maxTextChars?: number; windowQuery?: string }
): Promise<{ finalUrl: string; article: ExtractedArticle; elapsedMs: number }> {
  let target = url
  if (/news\.google\.com\/rss\/articles\//i.test(url)) {
    target = await resolveGoogleNewsLink(url, Math.min(timeoutMs, 6_000))
    if (target === url) {
      throw new FetchPageError('network', 'google news redirect could not be resolved')
    }
  }
  const page = await fetchPage(target, { timeoutMs })
  const article = extractArticle(page.body, opts)
  return { finalUrl: page.finalUrl, article, elapsedMs: page.elapsedMs }
}
