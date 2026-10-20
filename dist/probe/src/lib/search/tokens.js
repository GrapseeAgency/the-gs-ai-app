"use strict";
/**
 * Script-aware tokenisation, in ONE place.
 *
 * WHY THIS MODULE EXISTS: the same ASCII-only regex
 * (`replace(/[^a-z0-9\s]/g, ' ')`) had been copy-pasted into five different
 * relevance paths. For any non-Latin language it produces an EMPTY token set,
 * and each copy failed differently:
 *
 *   aggregate.ts   termOverlap() = 0 -> ranking lost its relevance signal;
 *                  titleWords() empty -> syndication never grouped;
 *                  isPoisonedSerp() discarded whole engine responses for a
 *                  Latin query answered in Bengali.
 *   engines.ts     the bing decoy guard compared an empty query set to an empty
 *                  title set and threw away real results.
 *   extract.ts     the query-centred window never found its query, so long
 *                  Bengali articles were always read from the top.
 *   research.ts    same, for the evidence window sent to the model.
 *   ledger.ts      key terms were empty -> no claims, no gap detection.
 *
 * One implementation, one set of rules, one place to be wrong.
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.tokensOf = tokensOf;
exports.hasLatin = hasLatin;
exports.crossScript = crossScript;
exports.overlapRatio = overlapRatio;
exports.dominantScript = dominantScript;
exports.searxLanguage = searxLanguage;
/** Scripts that do not space-delimit words: match on character bigrams. */
const CJK_RE = /[\u3040-\u30ff\u3400-\u4dbf\u4e00-\u9fff\uac00-\ud7af]/;
const LATIN_RE = /[a-z0-9]/;
/**
 * Tokens for relevance comparison.
 *
 * `\p{M}` is NOT optional: Bengali, Hindi and Arabic vowel signs are COMBINING
 * MARKS, not letters. Without it "খেলার" shatters into "খ ল র", every piece
 * falls under the length floor, and the whole title tokenises to nothing.
 *
 * The length floor is script-dependent: Latin needs 4+ characters to be
 * meaningful (drops "the", "for"), while Bengali/Hindi/Arabic words are often
 * 2-3 characters and still carry the entire meaning.
 */
function tokensOf(text, minLen = 2) {
    const out = new Set();
    if (!text)
        return out;
    const cleaned = text.toLowerCase().replace(/[^\p{L}\p{M}\p{N}\s]/gu, ' ');
    for (const w of cleaned.split(/\s+/)) {
        if (w.length === 0)
            continue;
        if (CJK_RE.test(w)) {
            for (let i = 0; i + 1 < w.length; i++)
                out.add(w.slice(i, i + 2));
            continue;
        }
        const floor = LATIN_RE.test(w) ? Math.max(minLen, 4) : minLen;
        if (w.length >= floor)
            out.add(w);
    }
    return out;
}
/** Does this token set contain any Latin-script token? */
function hasLatin(tokens) {
    for (const t of tokens)
        if (LATIN_RE.test(t))
            return true;
    return false;
}
/**
 * True when comparing the two sets is meaningless because they are written in
 * different scripts — i.e. one side is translated, not unrelated.
 */
function crossScript(a, b) {
    return hasLatin(a) !== hasLatin(b);
}
/** How many of the query's tokens appear in the title, as a 0..1 ratio. */
function overlapRatio(queryTokens, titleTokens) {
    if (queryTokens.size === 0)
        return 0;
    let hits = 0;
    for (const w of queryTokens)
        if (titleTokens.has(w))
            hits += 1;
    return hits / queryTokens.size;
}
const SCRIPT_RANGES = [
    { script: 'bengali', re: /[\u0980-\u09ff]/ },
    { script: 'devanagari', re: /[\u0900-\u097f]/ },
    { script: 'arabic', re: /[\u0600-\u06ff\u0750-\u077f]/ },
    { script: 'cyrillic', re: /[\u0400-\u04ff]/ },
    { script: 'han', re: /[\u3040-\u30ff\u3400-\u4dbf\u4e00-\u9fff\uac00-\ud7af]/ },
];
/**
 * The script most of the LETTERS in `text` are written in. Punctuation, digits
 * and whitespace are ignored, so "নাইটকপ 500" is Bengali rather than "mixed".
 */
function dominantScript(text) {
    if (!text)
        return 'other';
    const counts = new Map();
    let total = 0;
    for (const ch of text) {
        if (!/\p{L}/u.test(ch))
            continue;
        let hit = 'latin';
        if (!LATIN_RE.test(ch.toLowerCase())) {
            hit = SCRIPT_RANGES.find((r) => r.re.test(ch))?.script ?? 'other';
        }
        counts.set(hit, (counts.get(hit) ?? 0) + 1);
        total += 1;
    }
    let best = 'other';
    let bestN = 0;
    for (const [s, n] of counts)
        if (n > bestN) {
            best = s;
            bestN = n;
        }
    if (total === 0)
        return 'other';
    // MIXED QUERIES. "iPhone দাম" is 6 Latin letters and 2 Bengali ones (the
    // vowel sign is a mark, not a letter), so a pure majority vote says English —
    // and SearXNG would then filter out the Bengali shops the user is asking
    // about. A non-Latin script holding >= 20% of the letters wins: the user is
    // writing in that language and merely quoting a brand name in Latin. 20% not
    // 30% because Bengali words are short — 30% missed this exact case.
    if (best === 'latin') {
        for (const [s, n] of counts) {
            if (s !== 'latin' && s !== 'other' && n / total >= 0.2)
                return s;
        }
    }
    return best;
}
/**
 * SearXNG `language=` value for a query. 'all' is deliberately NOT used as the
 * fallback: measured, `all` returns zero for Bengali while `bn` returns 20.
 * Unknown scripts get 'all' only because there is nothing better to send.
 */
function searxLanguage(text) {
    switch (dominantScript(text)) {
        case 'bengali':
            return 'bn';
        case 'devanagari':
            return 'hi';
        case 'arabic':
            return 'ar';
        case 'cyrillic':
            return 'ru';
        case 'han':
            return 'zh-CN';
        case 'latin':
            return 'en';
        default:
            return 'all';
    }
}
