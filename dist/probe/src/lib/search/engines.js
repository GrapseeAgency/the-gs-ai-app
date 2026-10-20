"use strict";
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
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.ENGINE_REGISTRY = exports.tregWebSearch = exports.braveWebSearch = exports.crossref = exports.arxiv = exports.gutenberg = exports.openLibrary = exports.searxng = exports.zaiWebSearch = exports.wikipedia = exports.duckduckgoLite = exports.bingWeb = exports.googleNewsRss = exports.bingNewsRss = void 0;
exports.parseDate = parseDate;
exports.enginesForIntent = enginesForIntent;
const tokens_1 = require("./tokens");
const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36';
const ENGINE_TIMEOUT_MS = 8000;
// ---------------------------------------------------------------------------
// shared parsing helpers
// ---------------------------------------------------------------------------
function decodeEntities(s) {
    return s
        .replace(/&lt;/g, '<')
        .replace(/&gt;/g, '>')
        .replace(/&quot;/g, '"')
        .replace(/&#39;/g, "'")
        .replace(/&apos;/g, "'")
        .replace(/&nbsp;/g, ' ')
        .replace(/&#(\d+);/g, (_, d) => {
        const code = Number(d);
        return Number.isFinite(code) && code > 0 && code < 0x110000 ? String.fromCodePoint(code) : ' ';
    })
        .replace(/&amp;/g, '&');
}
function stripTags(s) {
    return decodeEntities(s.replace(/<[^>]*>/g, ' ')).replace(/\s+/g, ' ').trim();
}
function tagContent(block, tag) {
    const m = block.match(new RegExp(`<${tag}[^>]*>([\\s\\S]*?)</${tag}>`, 'i'));
    return m ? decodeEntities(m[1]).trim() : '';
}
function safeHost(url) {
    try {
        return new URL(url).hostname.replace(/^www\./, '');
    }
    catch {
        return '';
    }
}
function unwrapBingLink(link) {
    try {
        const u = new URL(link);
        const target = u.searchParams.get('url');
        if (target && /^https?:\/\//i.test(target))
            return target;
    }
    catch {
        // keep original
    }
    return link;
}
function unwrapDdgLink(link) {
    if (link.startsWith('//'))
        link = `https:${link}`;
    try {
        const u = new URL(link);
        const target = u.searchParams.get('uddg');
        if (target && /^https?:\/\//i.test(target))
            return decodeURIComponent(target);
    }
    catch {
        // keep original
    }
    return link;
}
/** Parse common RSS/HTTP date formats → ISO, else null. */
function parseDate(raw) {
    if (!raw)
        return null;
    const t = raw.trim();
    if (!t)
        return null;
    const d = new Date(t);
    return Number.isNaN(d.getTime()) ? null : d.toISOString();
}
async function fetchText(url, accept, ua = UA) {
    const res = await fetch(url, {
        headers: { 'User-Agent': ua, Accept: accept, 'Accept-Language': 'en-US,en;q=0.9' },
        signal: AbortSignal.timeout(ENGINE_TIMEOUT_MS),
    });
    if (!res.ok)
        throw new Error(`engine http ${res.status}`);
    return res.text();
}
// Wikimedia UA policy (verified live 2026-09-21): generic browser UAs from
// datacenter/automation contexts get 403; a DESCRIPTIVE app UA is served.
const WIKIMEDIA_UA = 'GS-AI-App/0.67 (https://grapsee.agency; contact@grapsee.agency)';
// ---------------------------------------------------------------------------
// engines
// ---------------------------------------------------------------------------
/** Bing News RSS — direct publisher links, date-sortable. Primary news engine. */
exports.bingNewsRss = {
    id: 'bing-news-rss',
    kind: 'news',
    run: async (q) => {
        const mkt = q.region && /^[A-Za-z]{2}$/.test(q.region) ? `en-${q.region.toUpperCase()}` : 'en-US';
        const freshness = q.timeRange === 'day' ? `&qft=sortbydate%3D%221%22` : '';
        const xml = await fetchText(`https://www.bing.com/news/search?q=${encodeURIComponent(q.query)}${freshness}&format=RSS&mkt=${mkt}`, 'application/rss+xml, application/xml, text/xml, */*');
        const out = [];
        for (const block of xml.match(/<item[\s\S]*?<\/item>/gi) ?? []) {
            const title = stripTags(tagContent(block, 'title'));
            let link = tagContent(block, 'link');
            if (!title || !link)
                continue;
            link = unwrapBingLink(link);
            if (!/^https?:\/\//i.test(link))
                continue;
            out.push({
                url: link,
                title,
                snippet: stripTags(tagContent(block, 'description')).slice(0, 300),
                domain: safeHost(link),
                publishedDate: parseDate(tagContent(block, 'pubDate')),
                rank: out.length + 1,
                engine: 'bing-news-rss',
            });
            if (out.length >= q.limit)
                break;
        }
        return out;
    },
};
/** Google News RSS — widest news coverage; links are redirects resolved lazily at retrieval time. */
exports.googleNewsRss = {
    id: 'google-news-rss',
    kind: 'news',
    run: async (q) => {
        const when = q.timeRange === 'day' ? '+when:1d' : q.timeRange === 'week' ? '+when:7d' : q.timeRange === 'month' ? '+when:1m' : '';
        const gl = q.region && /^[A-Za-z]{2}$/.test(q.region) ? q.region.toUpperCase() : 'US';
        const xml = await fetchText(`https://news.google.com/rss/search?q=${encodeURIComponent(q.query)}${when}&hl=en-${gl}&gl=${gl}&ceid=${gl}:en`, 'application/rss+xml, application/xml, text/xml, */*');
        const out = [];
        for (const block of xml.match(/<item[\s\S]*?<\/item>/gi) ?? []) {
            const title = stripTags(tagContent(block, 'title'));
            const link = tagContent(block, 'link');
            if (!title || !link)
                continue;
            const src = block.match(/<source[^>]*url="([^"]+)"/i);
            const domain = src ? safeHost(decodeEntities(src[1])) : safeHost(link);
            out.push({
                url: link,
                title,
                snippet: stripTags(tagContent(block, 'description')).slice(0, 300),
                domain: domain || 'news.google.com',
                publishedDate: parseDate(tagContent(block, 'pubDate')),
                rank: out.length + 1,
                engine: 'google-news-rss',
            });
            if (out.length >= q.limit)
                break;
        }
        return out;
    },
};
/** Bing Web HTML — general web results (b_algo blocks parse fine server-side). */
exports.bingWeb = {
    id: 'bing-web',
    kind: 'general',
    run: async (q) => {
        const html = await fetchText(`https://www.bing.com/search?q=${encodeURIComponent(q.query)}&count=15&mkt=en-US`, 'text/html,application/xhtml+xml,*/*;q=0.8');
        const out = [];
        const blocks = html.match(/<li class="b_algo"[\s\S]*?<\/li>/gi) ?? [];
        for (const block of blocks) {
            const linkMatch = block.match(/<h2[^>]*><a[^>]*href="([^"]+)"[^>]*>([\s\S]*?)<\/a>/i);
            if (!linkMatch)
                continue;
            let url = unwrapBingLink(decodeEntities(linkMatch[1]));
            // PHASE 8.1 — Bing's current /ck/a redirect links carry NO resolvable
            // target param. The real publisher URL lives in the block's <cite>
            // element; use it when the link is a bing.com redirect we cannot unwrap.
            if (/^https?:\/\/(www\.)?bing\.com\//i.test(url)) {
                const cite = block.match(/<cite[^>]*>([\s\S]*?)<\/cite>/i);
                if (cite) {
                    const cleaned = stripTags(cite[1])
                        .split('›')[0]
                        .replace(/…$/, '')
                        .trim();
                    if (/^https?:\/\//i.test(cleaned))
                        url = cleaned;
                    else if (/^[\w.-]+\.[a-z]{2,}/i.test(cleaned))
                        url = `https://${cleaned}`;
                }
            }
            if (!/^https?:\/\//i.test(url) || /^https?:\/\/(www\.)?bing\.com\//i.test(url))
                continue;
            const title = stripTags(linkMatch[2]);
            if (!title)
                continue;
            const snippetMatch = block.match(/<p[^>]*>([\s\S]*?)<\/p>/i);
            out.push({
                url,
                title,
                snippet: snippetMatch ? stripTags(snippetMatch[1]).slice(0, 300) : '',
                domain: safeHost(url),
                publishedDate: null,
                rank: out.length + 1,
                engine: 'bing-web',
            });
            if (out.length >= q.limit)
                break;
        }
        // Poisoned-SERP guard (found live 2026-09-20): the datacenter IP is
        // occasionally served a DECOY b_algo page — perfectly formed results for
        // an entirely different query. A SERP whose titles share zero significant
        // words with the query is rejected outright rather than letting unrelated
        // results masquerade as evidence (spec §7: never return unrelated
        // industries because the provider happened to return them).
        if (out.length > 0) {
            const qWords = (0, tokens_1.tokensOf)(q.query, 2);
            if (qWords.size > 0) {
                let judged = 0;
                let bestOverlap = 0;
                for (const r of out) {
                    const tWords = (0, tokens_1.tokensOf)(r.title, 2);
                    // Same rule as the aggregator guard: a Latin query against Bengali
                    // results is a TRANSLATION, not a decoy, and cannot be judged by
                    // token overlap. Skipping the pair is what keeps the guard from
                    // deleting every result for the app's first language.
                    if (tWords.size === 0 || (0, tokens_1.crossScript)(qWords, tWords))
                        continue;
                    judged += 1;
                    let hits = 0;
                    for (const w of qWords)
                        if (tWords.has(w))
                            hits += 1;
                    bestOverlap = Math.max(bestOverlap, hits);
                }
                // Only discard when at least one title was actually comparable.
                if (judged > 0 && bestOverlap === 0)
                    return [];
            }
        }
        return out;
    },
};
/**
 * DuckDuckGo Lite — tolerant HTML endpoint. (html.duckduckgo.com is blocked
 * from this deployment's IP; lite.duckduckgo.com works.)
 */
exports.duckduckgoLite = {
    id: 'duckduckgo-lite',
    kind: 'general',
    run: async (q) => {
        const df = q.timeRange === 'day' ? '&df=d' : q.timeRange === 'week' ? '&df=w' : q.timeRange === 'month' ? '&df=m' : '';
        const html = await fetchText(`https://lite.duckduckgo.com/lite/?q=${encodeURIComponent(q.query)}${df}`, 'text/html,application/xhtml+xml,*/*;q=0.8');
        const out = [];
        const snippets = [];
        for (const m of html.matchAll(/<td[^>]*class="result-snippet"[^>]*>([\s\S]*?)<\/td>/gi)) {
            snippets.push(stripTags(m[1]));
        }
        for (const m of html.matchAll(/<a[^>]*class="result-link"[^>]*href="([^"]+)"[^>]*>([\s\S]*?)<\/a>/gi)) {
            const url = unwrapDdgLink(decodeEntities(m[1]));
            if (!/^https?:\/\//i.test(url))
                continue;
            const title = stripTags(m[2]);
            if (!title)
                continue;
            out.push({
                url,
                title,
                snippet: snippets[out.length] ?? '',
                domain: safeHost(url),
                publishedDate: null,
                rank: out.length + 1,
                engine: 'duckduckgo-lite',
            });
            if (out.length >= q.limit)
                break;
        }
        return out;
    },
};
/** Wikipedia API — encyclopedic/factual grounding for entity questions.
 *  One bounded retry: transient 403 bursts were observed live (2026-09-21). */
exports.wikipedia = {
    id: 'wikipedia',
    kind: 'reference',
    run: async (q) => {
        try {
            return await wikipediaSearch(q);
        }
        catch {
            await new Promise((r) => setTimeout(r, 800));
            return wikipediaSearch(q);
        }
    },
};
async function wikipediaSearch(q) {
    const params = new URLSearchParams({
        action: 'query',
        list: 'search',
        srsearch: q.query,
        format: 'json',
        srlimit: String(Math.min(q.limit, 5)),
    });
    const json = JSON.parse(await fetchText(`https://en.wikipedia.org/w/api.php?${params.toString()}`, 'application/json', WIKIMEDIA_UA));
    const hits = json?.query?.search ?? [];
    const out = [];
    for (const hit of hits) {
        const title = typeof hit.title === 'string' ? hit.title : '';
        if (!title)
            continue;
        const url = `https://en.wikipedia.org/wiki/${encodeURIComponent(title.replace(/\s/g, '_'))}`;
        out.push({
            url,
            title,
            snippet: typeof hit.snippet === 'string' ? stripTags(hit.snippet).slice(0, 300) : '',
            domain: 'en.wikipedia.org',
            publishedDate: null,
            rank: out.length + 1,
            engine: 'wikipedia',
        });
        if (out.length >= q.limit)
            break;
    }
    return out;
}
/**
 * z-ai web_search — the original primary, demoted to ONE engine among many
 * (spec §2). It serves again whenever its quota window allows; when it 429s
 * the other engines carry the turn.
 */
exports.zaiWebSearch = {
    id: 'z-ai',
    kind: 'general',
    run: async (q) => {
        const ZAI = (await Promise.resolve().then(() => __importStar(require('z-ai-web-dev-sdk')))).default;
        const zai = await ZAI.create();
        const raw = (await zai.functions.invoke('web_search', {
            query: q.query,
            num: Math.min(q.limit, 8),
            ...(q.timeRange === 'day' ? { recency_days: 1 } : q.timeRange === 'week' ? { recency_days: 7 } : {}),
        }));
        const items = Array.isArray(raw?.result) ? raw.result : [];
        const out = [];
        for (const item of items) {
            const url = typeof item.url === 'string' ? item.url : '';
            const title = typeof item.name === 'string' ? item.name : '';
            if (!url || !/^https?:\/\//i.test(url) || !title)
                continue;
            out.push({
                url,
                title,
                snippet: typeof item.snippet === 'string' ? item.snippet.slice(0, 300) : '',
                domain: typeof item.host_name === 'string' ? item.host_name.replace(/^www\./, '') : safeHost(url),
                publishedDate: parseDate(typeof item.date === 'string' ? item.date : null),
                rank: out.length + 1,
                engine: 'z-ai',
            });
            if (out.length >= q.limit)
                break;
        }
        return out;
    },
};
/**
 * Self-hosted SearXNG (§3) — drop-in primary when SEARXNG_URL is configured.
 * Maps the standard JSON API: q, categories, language, time_range, safesearch.
 */
exports.searxng = {
    id: 'searxng',
    kind: 'general',
    run: async (q) => {
        const base = process.env.SEARXNG_URL;
        if (!base)
            throw new Error('searxng not configured');
        const params = new URLSearchParams({
            q: q.query,
            format: 'json',
            safesearch: '1',
            // NOT hardcoded to 'en': SearXNG FILTERS by language instead of falling
            // back, and a Bengali query with language=en returned 0 results against a
            // real container while language=bn returned 20. See searxLanguage().
            language: (0, tokens_1.searxLanguage)(q.query),
            ...(q.timeRange === 'none' ? {} : { time_range: q.timeRange }),
        });
        const json = JSON.parse(await fetchText(`${base.replace(/\/$/, '')}/search?${params.toString()}`, 'application/json'));
        const out = [];
        for (const r of json.results ?? []) {
            const url = typeof r.url === 'string' ? r.url : '';
            const title = typeof r.title === 'string' ? r.title : '';
            if (!url || !/^https?:\/\//i.test(url) || !title)
                continue;
            out.push({
                url,
                title,
                snippet: typeof r.content === 'string' ? r.content.slice(0, 300) : '',
                domain: safeHost(url),
                publishedDate: parseDate(typeof r.publishedDate === 'string' ? r.publishedDate : null),
                rank: out.length + 1,
                engine: 'searxng',
            });
            if (out.length >= q.limit)
                break;
        }
        return out;
    },
};
// ---------------------------------------------------------------------------
// PHASE 8.2 engines — books & scholarly (§10). Lawful, key-less, metadata or
// legally free full-text APIs only. Full-text claims are made ONLY when the
// retrieval layer actually read the text (gutenberg plain-text files do);
// metadata-only hits stay snippet_only and are labelled honestly.
// ---------------------------------------------------------------------------
exports.openLibrary = {
    id: 'openlibrary',
    kind: 'book',
    run: async (q) => {
        // Title-match discipline mirrors gutenberg: drop content words progressively.
        const words = q.query.split(/\s+/).filter(Boolean);
        const attempts = [...new Set([q.query, words.slice(0, 3).join(' ')])].filter((a) => a.length >= 3);
        for (const attempt of attempts) {
            const out = await parseOpenLibrarySearch(attempt, q.limit);
            if (out.length > 0)
                return out;
        }
        return [];
    },
};
async function parseOpenLibrarySearch(query, limit) {
    const params = new URLSearchParams({ q: query, limit: String(Math.min(limit, 8)), fields: 'key,title,author_name,first_publish_year' });
    const json = JSON.parse(await fetchText(`https://openlibrary.org/search.json?${params.toString()}`, 'application/json'));
    const out = [];
    for (const d of json.docs ?? []) {
        const key = typeof d.key === 'string' ? d.key : '';
        const title = typeof d.title === 'string' ? d.title : '';
        if (!key.startsWith('/works/') || !title)
            continue;
        const authors = Array.isArray(d.author_name) ? d.author_name.filter((a) => typeof a === 'string').slice(0, 2) : [];
        const year = typeof d.first_publish_year === 'number' ? d.first_publish_year : null;
        out.push({
            url: `https://openlibrary.org${key}`,
            title: authors.length > 0 ? `${title} — ${authors.join(', ')}` : title,
            snippet: [authors.join(', '), year ? `first published ${year}` : ''].filter(Boolean).join(' · '),
            domain: 'openlibrary.org',
            publishedDate: null,
            rank: out.length + 1,
            engine: 'openlibrary',
        });
        if (out.length >= limit)
            break;
    }
    return out;
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
exports.gutenberg = {
    id: 'gutenberg',
    kind: 'book',
    run: async (q) => {
        const words = q.query.split(/\s+/).filter(Boolean);
        const attempts = [...new Set([q.query, words.slice(0, 3).join(' '), words.slice(0, 2).join(' ')])].filter((a) => a.length >= 2);
        for (const attempt of attempts) {
            const out = await parseGutenbergSearch(attempt, q.limit);
            if (out.length > 0)
                return out;
        }
        return [];
    },
};
async function parseGutenbergSearch(query, limit) {
    const html = await fetchText(`https://www.gutenberg.org/ebooks/search/?query=${encodeURIComponent(query)}`, 'text/html,application/xhtml+xml,*/*;q=0.8');
    const out = [];
    for (const block of html.match(/<li class="booklink">[\s\S]*?<\/li>/gi) ?? []) {
        const link = block.match(/<a[^>]*href="(?:https:\/\/www\.gutenberg\.org)?(\/ebooks\/\d+)"[^>]*>/i)?.[1];
        const title = stripTags(block.match(/<span class="title">([\s\S]*?)<\/span>/i)?.[1] ?? '');
        if (!link || !title)
            continue;
        const author = stripTags(block.match(/<span class="subtitle">([\s\S]*?)<\/span>/i)?.[1] ?? '');
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
        });
        if (out.length >= limit)
            break;
    }
    return out;
}
/** arXiv — scholarly preprints (Atom API, abstracts lawfully served). */
exports.arxiv = {
    id: 'arxiv',
    kind: 'academic',
    run: async (q) => {
        const xml = await fetchText(`http://export.arxiv.org/api/query?search_query=all:${encodeURIComponent(q.query)}&start=0&max_results=${Math.min(q.limit, 8)}&sortBy=relevance`, 'application/atom+xml, application/xml, text/xml, */*');
        const out = [];
        for (const block of xml.match(/<entry[\s\S]*?<\/entry>/gi) ?? []) {
            const title = stripTags(tagContent(block, 'title'));
            const idLink = block.match(/<id>([\s\S]*?)<\/id>/i)?.[1]?.trim() ?? '';
            if (!title || !/^https?:\/\//.test(idLink))
                continue;
            const summary = stripTags(tagContent(block, 'summary')).slice(0, 300);
            out.push({
                url: idLink,
                title,
                snippet: summary,
                domain: safeHost(idLink) || 'arxiv.org',
                publishedDate: parseDate(tagContent(block, 'published')),
                rank: out.length + 1,
                engine: 'arxiv',
            });
            if (out.length >= q.limit)
                break;
        }
        return out;
    },
};
/** Crossref — scholarly metadata index (journals, DOIs; lawful public API). */
exports.crossref = {
    id: 'crossref',
    kind: 'academic',
    run: async (q) => {
        const params = new URLSearchParams({ query: q.query, rows: String(Math.min(q.limit, 8)), select: 'title,URL,DOI,issued,container-title' });
        const json = JSON.parse(await fetchText(`https://api.crossref.org/works?${params.toString()}`, 'application/json'));
        const out = [];
        for (const it of json.message?.items ?? []) {
            const title = Array.isArray(it.title) && typeof it.title[0] === 'string' ? it.title[0] : '';
            const url = typeof it.URL === 'string' && /^https?:\/\//.test(it.URL) ? it.URL : typeof it.DOI === 'string' ? `https://doi.org/${it.DOI}` : '';
            if (!title || !url)
                continue;
            const venue = Array.isArray(it['container-title']) && typeof it['container-title'][0] === 'string' ? it['container-title'][0] : '';
            const parts = it.issued?.['date-parts']?.[0];
            const publishedDate = Array.isArray(parts) && parts.length >= 3 ? new Date(Date.UTC(parts[0], parts[1] - 1, parts[2])).toISOString() : null;
            out.push({
                url,
                title: venue ? `${title} — ${venue}` : title,
                snippet: venue ? `Published in ${venue}.` : '',
                domain: safeHost(url),
                publishedDate,
                rank: out.length + 1,
                engine: 'crossref',
            });
            if (out.length >= q.limit)
                break;
        }
        return out;
    },
};
/**
 * Brave Search API — a real index behind a free key
 * (https://api.search.brave.com/res/v1/web/search).
 *
 * WHY THIS ONE AND NOT MORE SCRAPERS: measured live 2026-10-18 from a
 * datacenter IP, every key-less general SERP except Bing's HTML page is gated —
 * DuckDuckGo Lite answers 202 + a JS challenge (0/5 runs), Mojeek 403, Ecosia
 * 403, Brave's own HTML 429, public SearXNG instances a captcha page. Bing's
 * `format=rss` endpoint answers 200 but served DECOY results ("nightkop 500mg"
 * → cyberpunk wallpapers), which the poison guard would throw away anyway.
 * A structured, licensed JSON API is the only free general-web source that is
 * neither gated nor a decoy, so it is the one worth wiring.
 *
 * OPT-IN BY CONSTRUCTION, same contract as treg: without BRAVE_API_KEY this
 * adapter is not registered and not selected.
 *
 * FREE TIER IS 1 REQUEST/SECOND. The aggregator fans out up to three queries in
 * parallel, so a naive adapter would spend two of its three calls on 429s.
 * SINGLE-FLIGHT instead: while one Brave call is in flight the others resolve
 * empty immediately. The primary query gets the index; the paraphrases do not
 * queue behind it and add no latency. A 429 is also returned as empty rather
 * than thrown — throttling on a free tier is expected, not a search failure.
 */
let braveInFlight = false;
exports.braveWebSearch = {
    id: 'brave',
    kind: 'general',
    run: async (q) => {
        const key = process.env.BRAVE_API_KEY;
        if (!key)
            throw new Error('brave not configured');
        if (braveInFlight)
            return [];
        braveInFlight = true;
        try {
            const limit = Math.min(Math.max(1, Math.floor(q.limit) || 1), 20);
            const url = 'https://api.search.brave.com/res/v1/web/search' +
                `?q=${encodeURIComponent(q.query)}` +
                `&count=${limit}` +
                (q.region && /^[A-Za-z]{2}$/.test(q.region) ? `&country=${q.region.toUpperCase()}` : '&country=US') +
                '&search_lang=en&safesearch=moderate&text_decorations=false';
            const res = await fetch(url, {
                headers: {
                    Accept: 'application/json',
                    // No explicit Accept-Encoding: undici negotiates and decompresses for
                    // us; setting it by hand hands back raw gzip bytes.
                    'X-Subscription-Token': key,
                },
                signal: AbortSignal.timeout(4000),
            });
            // 429 = free-tier throttle; empty, not failed. Everything else is real.
            if (res.status === 429)
                return [];
            if (!res.ok)
                throw new Error(`brave http ${res.status}`);
            const json = (await res.json());
            const rows = Array.isArray(json?.web?.results) ? json.web.results : [];
            const out = [];
            for (const row of rows) {
                const rawUrl = typeof row?.url === 'string' ? row.url.trim() : '';
                if (!/^https?:\/\//i.test(rawUrl))
                    continue;
                // Brave returns titles/descriptions with <strong> highlight tags.
                const title = stripTags(typeof row?.title === 'string' ? row.title : '');
                if (!title)
                    continue;
                out.push({
                    url: rawUrl,
                    title,
                    snippet: stripTags(typeof row?.description === 'string' ? row.description : '').slice(0, 300),
                    domain: safeHost(rawUrl),
                    // page_age is ISO and feeds recency scoring; age ("3 days ago") is not
                    // parseable, so it is only a fallback for feeds that omit page_age.
                    publishedDate: parseDate((typeof row?.page_age === 'string' ? row.page_age : null) ??
                        (typeof row?.age === 'string' ? row.age : null)),
                    rank: out.length + 1,
                    engine: 'brave',
                });
                if (out.length >= limit)
                    break;
            }
            return out;
        }
        finally {
            braveInFlight = false;
        }
    },
};
/**
 * treg WebAccess — one token in front of a chain of search vendors
 * (https://treg.to/web-access, POST /call/treg.web.search).
 *
 * OPT-IN BY CONSTRUCTION. Without TREG_TOKEN this adapter is neither
 * registered nor selected, so an unconfigured deployment behaves exactly as
 * before: no extra request, no extra latency, no surprise bill.
 *
 * Two hard bounds on a routed call:
 *   X-Treg-Route-Waterfall: 0  → stop at the first miss instead of walking the
 *                                whole chain (a miss can be billed at every
 *                                vendor tried before it);
 *   X-Treg-Route-Max-Cost      → ceiling for one call (default 2 cents).
 *
 * Timeout is 3s, deliberately below the aggregator's engine budget: a routed
 * search that has not answered by then will not improve this turn, and the
 * free engines have already reported. The aggregator waits for its slowest
 * engine, so this number is also the WORST-CASE added latency.
 *
 * Rows are PROVIDER-NATIVE (treg relays, it does not normalise), so every
 * field is read defensively across the names its children use.
 */
exports.tregWebSearch = {
    id: 'treg',
    kind: 'general',
    run: async (q) => {
        const token = process.env.TREG_TOKEN;
        if (!token)
            throw new Error('treg not configured');
        // One number decides both what we ASK for and how many rows we KEEP, so the
        // request and the loop can never disagree (and a caller passing limit=50
        // cannot end up merging 50 paid rows into a pool the free engines filled
        // with ten).
        const limit = Math.min(Math.max(1, Math.floor(q.limit) || 1), 10);
        const res = await fetch('https://treg.to/call/treg.web.search', {
            method: 'POST',
            headers: {
                'X-Treg-Token': token,
                'content-type': 'application/json',
                accept: 'application/json',
                'X-Treg-Route-Waterfall': '0',
                'X-Treg-Route-Max-Cost': process.env.TREG_MAX_COST || '0.02',
            },
            body: JSON.stringify({ q: q.query, limit }),
            signal: AbortSignal.timeout(3000),
        });
        if (!res.ok)
            throw new Error(`treg http ${res.status}`);
        const json = (await res.json());
        const rows = Array.isArray(json?.output?.results) ? json.output.results : [];
        const str = (row, keys) => {
            for (const k of keys) {
                const v = row[k];
                if (typeof v === 'string' && v.trim().length > 0)
                    return v.trim();
            }
            return '';
        };
        const out = [];
        for (const raw of rows) {
            if (!raw || typeof raw !== 'object')
                continue;
            const row = raw;
            const url = str(row, ['url', 'link', 'href', 'source_url']);
            if (!url || !/^https?:\/\//i.test(url))
                continue;
            const title = str(row, ['title', 'name', 'headline']);
            if (!title)
                continue;
            out.push({
                url,
                title,
                snippet: str(row, ['snippet', 'description', 'content', 'text', 'summary']).slice(0, 300),
                domain: safeHost(url),
                publishedDate: parseDate(str(row, ['published_date', 'publishedDate', 'date', 'published_at']) || null),
                rank: out.length + 1,
                engine: 'treg',
            });
            if (out.length >= limit)
                break;
        }
        return out;
    },
};
exports.ENGINE_REGISTRY = {
    'bing-news-rss': exports.bingNewsRss,
    'google-news-rss': exports.googleNewsRss,
    'bing-web': exports.bingWeb,
    'duckduckgo-lite': exports.duckduckgoLite,
    wikipedia: exports.wikipedia,
    'z-ai': exports.zaiWebSearch,
    searxng: exports.searxng,
    openlibrary: exports.openLibrary,
    gutenberg: exports.gutenberg,
    arxiv: exports.arxiv,
    crossref: exports.crossref,
    // Conditional by design: an engine that costs money per call never joins a
    // search the operator did not configure it for.
    ...(process.env.TREG_TOKEN ? { treg: exports.tregWebSearch } : {}),
    // Free-but-keyed, so it also never joins a search it was not configured for.
    ...(process.env.BRAVE_API_KEY ? { brave: exports.braveWebSearch } : {}),
};
/**
 * Engine selection per intent (8.2 §7/§10/§11). Order matters only as tie-break
 * priority — the aggregator runs the set in parallel.
 * SearXNG, when configured, leads the general set.
 *
 * PHASE 8.3 §13 — source-specific lookups are routed BY THE NAMED SOURCE:
 * "what does Wikipedia say about X" is a reference question (wikipedia
 * engine + web), "Search Reuters for X" is a news question (news engines).
 * The old behavior ran NEWS engines for every source_specific turn, which
 * produced the forensic J failure: a Wikipedia question answered (or failed)
 * from google-news-rss.
 */
const NEWS_HINT_DOMAINS = [
    'reuters.com', 'bbc.com', 'cnn.com', 'aljazeera.com', 'apnews.com',
    'nytimes.com', 'theguardian.com', 'bloomberg.com',
];
function enginesForIntent(intent, isNews, officialOnly, sourceHint) {
    const searx = process.env.SEARXNG_URL ? ['searxng'] : [];
    // Both are keyed. Brave is free-tier and answers from a real index, so it
    // outranks treg (paid per successful call); both stay last so they never
    // displace a key-less engine that already answered.
    const brave = process.env.BRAVE_API_KEY ? ['brave'] : [];
    // treg is paid-per-success; it only exists when the operator configured a
    // token, and it is always LAST so it loses every tie-break to a free engine.
    const treg = process.env.TREG_TOKEN ? ['treg'] : [];
    // 8.2 §11/§23 — "official sources only": first-party-friendly engines only;
    // no aggregator/news engines, no third-party provider search.
    if (officialOnly) {
        return [...searx, 'bing-web', 'wikipedia', 'duckduckgo-lite'];
    }
    // §13 — the user NAMED a source; the engine set must be able to reach it.
    if (intent === 'source_specific' && sourceHint) {
        const hint = sourceHint.toLowerCase();
        if (hint === 'wikipedia.org' || hint.endsWith('.wikipedia.org')) {
            return [...searx, 'wikipedia', 'bing-web', 'duckduckgo-lite'];
        }
        if (NEWS_HINT_DOMAINS.some((d) => hint === d || hint.endsWith(`.${d}`))) {
            return [...searx, 'bing-news-rss', 'google-news-rss', 'duckduckgo-lite', 'z-ai', ...brave, ...treg];
        }
        return [...searx, 'bing-web', 'duckduckgo-lite', 'z-ai', ...brave, ...treg];
    }
    if (intent === 'academic') {
        return [...searx, 'arxiv', 'crossref', 'wikipedia', 'bing-web', 'duckduckgo-lite'];
    }
    if (intent === 'book_source') {
        return [...searx, 'gutenberg', 'openlibrary', 'bing-web', 'duckduckgo-lite'];
    }
    if (intent === 'historical_religious') {
        // Primary/reference first (8.2 §11). 2026-09-21 live audit: CrossRef's
        // bibliographical entries (doi.org landing pages) flooded the candidate
        // pool with junk for questions like "who was the first Muslim?" — the
        // scholarly index answers LITERATURE questions, not religious-history
        // facts, so it stays reserved for the academic intent. z-ai promoted:
        // its index surfaces tradition-aware answers (Islamic sites etc.).
        return [...searx, 'z-ai', 'wikipedia', 'bing-web', 'duckduckgo-lite', ...brave, ...treg];
    }
    if (isNews) {
        return officialOnly
            ? [...searx, 'bing-web', 'duckduckgo-lite']
            : [...searx, 'bing-news-rss', 'google-news-rss', 'duckduckgo-lite', 'z-ai', ...brave, ...treg];
    }
    if (intent === 'factual' || intent === 'research' || intent === 'comparison') {
        return [...searx, 'bing-web', 'duckduckgo-lite', 'wikipedia', 'bing-news-rss', 'z-ai', ...brave, ...treg];
    }
    return [...searx, 'bing-web', 'duckduckgo-lite', 'z-ai', ...brave, ...treg];
}
