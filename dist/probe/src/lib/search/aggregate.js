"use strict";
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
Object.defineProperty(exports, "__esModule", { value: true });
exports.isPoisonedSerp = isPoisonedSerp;
exports.runMetaSearch = runMetaSearch;
const engines_1 = require("./engines");
const tokens_1 = require("./tokens");
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
]);
const JOURNALISM_DOMAINS = new Set([
    'reuters.com', 'apnews.com', 'afp.com', 'bbc.com', 'bbc.co.uk', 'theguardian.com',
    'nytimes.com', 'washingtonpost.com', 'wsj.com', 'ft.com', 'bloomberg.com',
    'economist.com', 'ft.com', 'aljazeera.com', 'cnn.com', 'edition.cnn.com',
    'nbcnews.com', 'cbsnews.com', 'abcnews.go.com', 'npr.org', 'pbs.org',
    'time.com', 'newsweek.com', 'usatoday.com', 'latimes.com', 'irrawaddy.com',
    // Bangladesh regional journalism (§7 geo intent)
    'thedailystar.net', 'prothomalo.com', 'dhakatribune.com', 'bdnews24.com',
    'theindependentbd.com', 'newagebd.net', 'tbsnews.net', 'thedailycountry.com',
]);
// Specialist publications by sector keyword (§8 specialist sources).
const SPECIALIST_DOMAINS = [
    { match: /\b(cybersecurity|security|hack|breach|malware|ransomware|vulnerab)/i, domains: new Set(['thehackernews.com', 'bleepingcomputer.com', 'krebsonsecurity.com', 'securityweek.com', 'darkreading.com', 'therecord.media', 'schneier.com']) },
    { match: /\b(artificial intelligence|\bai\b|machine learning|llm|chatgpt|gemini)\b/i, domains: new Set(['techcrunch.com', 'theverge.com', 'arstechnica.com', 'venturebeat.com', 'spectrum.ieee.org', 'technologyreview.com']) },
    { match: /\b(android|smartphone|mobile|galaxy|iphone)\b/i, domains: new Set(['9to5google.com', '9to5mac.com', 'androidauthority.com', 'xdadevelopers.com', 'theverge.com', 'gsmaresena.com']) },
    { match: /\b(football|soccer|cricket|sports?\b|olympic)/i, domains: new Set(['espn.com', 'bbc.com', 'skysports.com', 'goal.com', 'cricbuzz.com', 'espncricinfo.com']) },
    { match: /\b(science|space|physics|climate|research)/i, domains: new Set(['nature.com', 'science.org', 'newscientist.com', 'scientificamerican.com', 'space.com', 'phys.org']) },
    { match: /\b(health|medicine|disease|vaccine|hospital)/i, domains: new Set(['statnews.com', 'medicalnewstoday.com', 'healthline.com', 'who.int']) },
    { match: /\b(finance|market|economy|stocks?|crypto|banking)/i, domains: new Set(['cnbc.com', 'marketwatch.com', 'investing.com', 'coinjournal.com', 'coindesk.com']) },
];
// Discovery-only aggregators (§8): fine for finding, weak as sole evidence.
const AGGREGATOR_PENALTY_DOMAINS = new Set([
    'news.google.com', 'msn.com', 'news.yahoo.com', 'yahoo.com', 'sports.yahoo.com',
    'flipboard.com', 'trends.google.com',
]);
function domainMatches(url, set) {
    try {
        const host = new URL(url).hostname.replace(/^www\./, '');
        if (set.has(host))
            return true;
        for (const d of set) {
            if (host.endsWith(`.${d}`) || host === d)
                return true;
        }
        return false;
    }
    catch {
        return false;
    }
}
function isGovOrEdu(url) {
    try {
        const host = new URL(url).hostname;
        return /\.(gov|edu|mil)(\.[a-z]{2})?$/.test(host) || host.endsWith('.gov.uk');
    }
    catch {
        return false;
    }
}
// ---------------------------------------------------------------------------
// PHASE 8.2 (§3/§11) — source class + authority tiers. Assigned from URL and
// engine-kind FACTS, never from model text.
// ---------------------------------------------------------------------------
const ACADEMIC_DOMAINS = new Set([
    'arxiv.org', 'doi.org', 'nature.com', 'science.org', 'jstor.org', 'springer.com',
    'link.springer.com', 'sciencedirect.com', 'tandfonline.com', 'mdpi.com', 'journals.plos.org',
    'plos.org', 'ncbi.nlm.nih.gov', 'pubmed.ncbi.nlm.nih.gov', 'scholar.google.com',
    'acm.org', 'dl.acm.org', 'ieee.org', 'ieeexplore.ieee.org', 'cambridge.org', 'oup.com',
    'academic.oup.com', 'mitpress.mit.edu', 'apa.org', 'royalsocietypublishing.org',
]);
function classifyAuthority(url, engineKind) {
    if (engineKind === 'academic' || domainMatches(url, ACADEMIC_DOMAINS))
        return 'academic';
    if (engineKind === 'book')
        return 'primary'; // public-domain full text / lawful book record
    if (isGovOrEdu(url) || domainMatches(url, FIRST_PARTY_DOMAINS))
        return 'primary';
    if (domainMatches(url, JOURNALISM_DOMAINS))
        return 'reputable';
    if (domainMatches(url, AGGREGATOR_PENALTY_DOMAINS))
        return 'discovery';
    try {
        const host = new URL(url).hostname.replace(/^www\./, '');
        if (host.endsWith('wikipedia.org'))
            return 'reference';
    }
    catch {
        // fall through
    }
    return 'discovery'; // unclassified sites are NOT granted authority they did not earn
}
function classifySourceType(url, engineKind, isNews) {
    if (engineKind === 'academic')
        return 'academic';
    if (engineKind === 'book')
        return 'book';
    if (engineKind === 'news' || isNews)
        return 'news';
    if (isGovOrEdu(url) || domainMatches(url, FIRST_PARTY_DOMAINS))
        return 'primary';
    try {
        const host = new URL(url).hostname.replace(/^www\./, '');
        if (host.endsWith('wikipedia.org'))
            return 'reference';
    }
    catch {
        // fall through
    }
    return 'general';
}
// ---------------------------------------------------------------------------
// normalisation + dedupe
// ---------------------------------------------------------------------------
function normalizeUrlForDedupe(raw) {
    try {
        const u = new URL(raw);
        u.hash = '';
        for (const key of [...u.searchParams.keys()]) {
            if (/^utm_|^fbclid$|^gclid$|^ref$|^ref_src$|^cmp$|^ncid$/i.test(key))
                u.searchParams.delete(key);
        }
        u.hostname = u.hostname.replace(/^www\./, '').toLowerCase();
        u.protocol = 'https:';
        let s = u.toString();
        if (s.endsWith('/'))
            s = s.slice(0, -1);
        return s;
    }
    catch {
        return raw;
    }
}
/**
 * Relevance tokeniser — SCRIPT-AWARE. The implementation lives in tokens.ts
 * because five relevance paths had their own ASCII-only copy; see that file
 * for the measured consequences.
 */
function titleWords(title) {
    return (0, tokens_1.tokensOf)(title, 2);
}
function termOverlap(query, title) {
    const qw = titleWords(query);
    if (qw.size === 0)
        return 0;
    const tw = titleWords(title);
    let hits = 0;
    for (const w of qw)
        if (tw.has(w))
            hits += 1;
    return hits / qw.size;
}
// ---------------------------------------------------------------------------
// FORENSIC AUDIT [17] — SERP RELEVANCE GUARD (Bing decoy SERP).
// From a datacenter IP, Bing sometimes serves a completely unrelated cached
// SERP (live case: an "UP Scholarship" page for an Android query). Trusting
// it produces confident nonsense with citations. Guard: when the TOP-5 titles
// of an engine's response share ZERO content words with the query, that
// engine's results are DISCARDED for the turn — not merely down-ranked.
// ---------------------------------------------------------------------------
const SERP_STOPWORDS = new Set([
    'the', 'and', 'for', 'with', 'about', 'from', 'that', 'this', 'what', 'when',
    'where', 'who', 'how', 'was', 'were', 'are', 'have', 'has', 'did', 'does',
    'into', 'over', 'under', 'your', 'ours', 'their', 'them', 'will', 'would',
    'should', 'could', 'does', 'www', 'http', 'https', 'com', 'html', 'page',
]);
function serpWords(text) {
    const out = new Set();
    for (const w of (0, tokens_1.tokensOf)(text, 2))
        if (!SERP_STOPWORDS.has(w))
            out.add(w);
    return out;
}
function serpSharedWords(query, title) {
    const qw = serpWords(query);
    if (qw.size === 0)
        return 1; // guard cannot judge — never discard
    const tw = serpWords(title);
    if (tw.size === 0)
        return 1; // guard cannot judge — never discard
    // CROSS-SCRIPT IS TRANSLATION, NOT POISON. A Latin query ("dhaka news")
    // against Bengali-language results shares no tokens BY CONSTRUCTION; the old
    // ASCII tokeniser saw exactly that and discarded the entire engine response.
    if ((0, tokens_1.crossScript)(qw, tw))
        return 1;
    let shared = 0;
    for (const w of qw)
        if (tw.has(w))
            shared += 1;
    return shared;
}
/**
 * True when the SERP is poison: none of the first five unique titles shares
 * ANY content word with the query. A real result set almost always echoes at
 * least one query term in a top-5 title.
 */
/** Exported for the forensic verification harness ([17] deterministic repro). */
function isPoisonedSerp(query, results) {
    const seen = new Set();
    const topTitles = [];
    for (const r of results) {
        if (seen.has(r.title) || seen.size >= 5)
            continue;
        seen.add(r.title);
        topTitles.push(r.title);
        if (topTitles.length >= 5)
            break;
    }
    if (topTitles.length === 0)
        return false;
    return topTitles.every((t) => serpSharedWords(query, t) === 0);
}
function recencyScore(publishedDate, timeRange, isNews) {
    if (!publishedDate)
        return isNews && timeRange !== 'none' ? -1.5 : 0;
    const ageHours = (Date.now() - new Date(publishedDate).getTime()) / 3600000;
    if (timeRange === 'day' || (isNews && timeRange === 'none')) {
        if (ageHours <= 30)
            return 3;
        if (ageHours <= 48)
            return 1.5;
        if (ageHours <= 24 * 4)
            return 0;
        return -3; // stale pages must not ride "today's news" (§17)
    }
    if (timeRange === 'week')
        return ageHours <= 24 * 8 ? 2.5 : ageHours <= 24 * 14 ? 0.5 : -2;
    if (timeRange === 'month')
        return ageHours <= 24 * 35 ? 2 : -1;
    return ageHours <= 24 * 2 ? 1.5 : 0;
}
/**
 * Baseline S39 (audit [16]) — non-article ASSET URL shapes: figure pages,
 * binary downloads, gallery assets. They can never produce article text and
 * deterministically fail (403-bot-wall / parse-fail), so admitting them only
 * burns fetch slots that a real article could have used.
 */
const NON_ARTICLE_ASSET_RES = [
    /\/fig-?\d+(?:[/?#]|$)/i,
    /\.(?:png|jpe?g|gif|webp|svg|pdf|docx?|xlsx?|pptx?|zip|gz|mp4|mp3)(?:[?#]|$)/i,
];
function isNonArticleAssetUrl(url) {
    return NON_ARTICLE_ASSET_RES.some((re) => re.test(url));
}
/**
 * Race an engine against a timeout WITHOUT leaking the timer.
 *
 * `Promise.race([work, new Promise(reject after 9s)])` leaves the losing timer
 * armed: every settled search keeps a 9-second timer alive, so a deep turn
 * (3 rounds x 3 queries x ~6 engines) parks ~50 timers on the event loop after
 * the answer is already streaming. Clearing on settle is one line and removes
 * the whole class.
 */
function withEngineTimeout(work, ms) {
    return new Promise((resolve, reject) => {
        const timer = setTimeout(() => reject(new Error('engine timeout')), ms);
        work.then((value) => {
            clearTimeout(timer);
            resolve(value);
        }, (error) => {
            clearTimeout(timer);
            reject(error);
        });
    });
}
async function runMetaSearch(input) {
    const isNews = input.intent === 'broad_news' ||
        input.intent === 'sector_news' ||
        input.intent === 'entity_news' ||
        input.intent === 'geo_news' ||
        input.intent === 'time_window_news' ||
        input.intent === 'source_specific';
    const engineIds = (0, engines_1.enginesForIntent)(input.intent, isNews, input.officialOnly === true, input.sourceHint);
    const perQuery = Math.max(input.limit, 6);
    // Fan out: every (query × engine) pair runs independently; failures are
    // collected, never fatal (§22 — one provider failure ≠ search failure).
    const tasks = [];
    const taskMeta = [];
    const boundedQueries = input.queries.slice(0, 3);
    for (const query of boundedQueries) {
        for (const engineId of engineIds) {
            const adapter = engines_1.ENGINE_REGISTRY[engineId];
            if (!adapter)
                continue;
            const eq = {
                query,
                timeRange: input.timeRange,
                region: input.region,
                sourceHint: input.sourceHint,
                limit: perQuery,
            };
            taskMeta.push({ engineId, query });
            tasks.push(withEngineTimeout(adapter.run(eq), input.engineTimeoutMs ?? 9000));
        }
    }
    const settled = await Promise.allSettled(tasks);
    const enginesUsed = new Set();
    const enginesFailed = [];
    const engineStats = new Map();
    const byUrl = new Map();
    const recordEngine = (id, status, detail) => {
        const prev = engineStats.get(id);
        if (status === 'ok') {
            engineStats.set(id, { id, status: 'ok', count: (prev?.count ?? 0) + (detail?.count ?? 0) });
        }
        else if (!prev || (prev.status !== 'ok' && status === 'empty')) {
            engineStats.set(id, { id, status, ...(detail ?? {}) });
        }
    };
    settled.forEach((res, i) => {
        const { engineId, query } = taskMeta[i];
        if (res.status === 'fulfilled') {
            // FORENSIC AUDIT [17] — poison-SERP rejection before ANY merging.
            if (isPoisonedSerp(query, res.value)) {
                console.log(`SERP-GUARD engine=${engineId} query="${query.slice(0, 60)}" discarded=${res.value.length} reason=zero-top-5-title-overlap`);
                enginesFailed.push({ engine: engineId, error: 'serp rejected: zero query-title overlap' });
                recordEngine(engineId, 'failed', { error: 'serp rejected: zero overlap' });
                return;
            }
            if (res.value.length > 0)
                enginesUsed.add(engineId);
            recordEngine(engineId, res.value.length > 0 ? 'ok' : 'empty', { count: res.value.length });
            for (const r of res.value) {
                // Baseline S39 (audit [16]) — non-article ASSET URLs can never yield
                // article text: PeerJ figure pages (doi.org/10.7717/peerj-cs.N/fig-4)
                // 403/parse-fail DETERMINISTICALLY and burn a fetch slot that a real
                // article could have used (live evidence: 4/4 slots lost to /fig-N
                // pages, sourcesRead=0 while real articles ranked below them).
                // Rejected at candidate intake — same moral class as the SERP guard.
                if (isNonArticleAssetUrl(r.url))
                    continue;
                const key = normalizeUrlForDedupe(r.url);
                const existing = byUrl.get(key);
                if (existing) {
                    // Multi-engine agreement is a quality signal (§8).
                    if (!existing.engines.includes(r.engine)) {
                        existing.engines.push(r.engine);
                        existing.score += 2;
                    }
                    if (!existing.publishedDate && r.publishedDate)
                        existing.publishedDate = r.publishedDate;
                    if (existing.snippet.length < 40 && r.snippet.length > existing.snippet.length) {
                        existing.snippet = r.snippet;
                    }
                }
                else {
                    byUrl.set(key, { ...r, url: r.url, score: 0, engines: [r.engine], sourceType: 'general', authority: 'discovery' });
                }
            }
        }
        else {
            const err = res.reason instanceof Error ? res.reason.message : String(res.reason);
            enginesFailed.push({ engine: engineId, error: err.slice(0, 120) });
            recordEngine(engineId, 'failed', { error: err.slice(0, 80) });
        }
    });
    // ---- ranking pass (§8 + 8.2 §11) ---------------------------------------
    const kindOf = (engineId) => engines_1.ENGINE_REGISTRY[engineId]?.kind ?? 'general';
    const candidates = [...byUrl.values()];
    for (const c of candidates) {
        const kind = kindOf(c.engine);
        c.sourceType = classifySourceType(c.url, kind, isNews);
        c.authority = classifyAuthority(c.url, kind);
        let score = 0;
        score += recencyScore(c.publishedDate, input.timeRange, isNews);
        if (isGovOrEdu(c.url) || domainMatches(c.url, FIRST_PARTY_DOMAINS))
            score += 3.5;
        if (domainMatches(c.url, JOURNALISM_DOMAINS))
            score += 2.5;
        // 8.2 §11 — authority matters per question class: an academic question
        // must not be answered from random blogs; a book question must surface
        // book sources; a historical/religious question prefers primary/scholarly.
        if (input.intent === 'academic' && c.authority === 'academic')
            score += 3;
        if (input.intent === 'book_source' && c.sourceType === 'book')
            score += 3;
        if (input.intent === 'historical_religious' && (c.authority === 'primary' || c.authority === 'academic'))
            score += 2.5;
        if (input.intent === 'historical_religious' && c.authority === 'reference')
            score += 1.5;
        for (const spec of SPECIALIST_DOMAINS) {
            if (spec.match.test(boundedQueries.join(' ')) && domainMatches(c.url, spec.domains)) {
                score += 2.5;
                break;
            }
        }
        if (domainMatches(c.url, AGGREGATOR_PENALTY_DOMAINS))
            score -= 2;
        score += Math.max(0, 1.2 - (c.rank - 1) * 0.15); // engine rank position
        score += termOverlap(boundedQueries.join(' '), c.title) * 2;
        if (input.sourceHint) {
            const hint = input.sourceHint.replace(/^https?:\/\//, '').replace(/^www\./, '');
            if (c.domain === hint || c.domain.endsWith(`.${hint}`) || hint.includes(c.domain))
                score += 6;
        }
        score += (c.engines.length - 1) * 0.5;
        c.score = Math.round(score * 100) / 100;
    }
    candidates.sort((a, b) => b.score - a.score);
    // 8.2 §12 — syndication detection: near-identical titles across DIFFERENT
    // domains are the same story re-reported. Tag groups; the first (best-ranked)
    // member is the representative. Not removed — the count is honest signal.
    {
        let groupId = 0;
        for (let i = 0; i < candidates.length; i++) {
            if (candidates[i].dupGroupId !== undefined)
                continue;
            const aWords = [...titleWords(candidates[i].title)];
            if (aWords.length < 4)
                continue;
            for (let j = i + 1; j < candidates.length; j++) {
                if (candidates[j].dupGroupId !== undefined || candidates[j].domain === candidates[i].domain)
                    continue;
                const bWords = [...titleWords(candidates[j].title)];
                if (bWords.length < 4)
                    continue;
                const shared = aWords.filter((w) => bWords.includes(w)).length;
                const union = new Set([...aWords, ...bWords]).size;
                if (union > 0 && shared / union >= 0.6) {
                    candidates[j].dupGroupId = candidates[i].dupGroupId ?? (candidates[i].dupGroupId = ++groupId);
                }
            }
        }
    }
    // Domain diversity: at most 2 per domain near the top (§9 — five repeats of
    // the same wire story is not evidence, it is echo). Academic/book intents
    // allow 4: a scholarly index legitimately returns many same-registry hits.
    const perDomainCap = input.intent === 'academic' || input.intent === 'book_source' ? 4 : 2;
    const perDomain = new Map();
    const diverse = [];
    for (const c of candidates) {
        const n = perDomain.get(c.domain) ?? 0;
        if (n >= perDomainCap && diverse.length < input.limit)
            continue;
        perDomain.set(c.domain, n + 1);
        diverse.push(c);
        if (diverse.length >= input.limit)
            break;
    }
    return { results: diverse, enginesUsed: [...enginesUsed], enginesFailed, engineOutcomes: [...engineStats.values()] };
}
