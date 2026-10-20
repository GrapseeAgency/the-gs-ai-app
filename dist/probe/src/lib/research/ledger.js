"use strict";
/**
 * PHASE 5 — RESEARCH LEDGER (dossier pattern, ACM CAIS 2026).
 *
 * A dynamic knowledge structure that partitions the agent's belief state
 * into VERIFIED CLAIMS and UNRESOLVED INFORMATION GAPS. Search branches are
 * planned against the ledger, not against the raw question — queries are
 * contextually grounded in the agent's evolving state (EAQL) rather than
 * isolated re-queries of the original prompt.
 *
 * Persisted per run (research_ledgers) so the ledger survives the turn and
 * is auditable after it.
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.emptyLedger = emptyLedger;
exports.saveLedger = saveLedger;
exports.loadLedger = loadLedger;
exports.stripCommandPrefix = stripCommandPrefix;
exports.keyTerms = keyTerms;
exports.claimsFromExtract = claimsFromExtract;
exports.detectContradictions = detectContradictions;
exports.detectGaps = detectGaps;
exports.queryForGap = queryForGap;
exports.decomposeQuestion = decomposeQuestion;
const db_1 = require("@/lib/db");
const tokens_1 = require("@/lib/search/tokens");
function emptyLedger(runId, question) {
    return {
        runId,
        question,
        claims: [],
        contradictions: [],
        gaps: [],
        branches: [],
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
    };
}
async function saveLedger(ledger) {
    const payload = JSON.stringify({ ...ledger, updatedAt: new Date().toISOString() });
    try {
        await db_1.db.researchLedgerRow.upsert({
            where: { runId: ledger.runId },
            create: { runId: ledger.runId, ledger: payload },
            update: { ledger: payload },
        });
    }
    catch (e) {
        console.error(`LEDGER-SAVE-FAIL run=${ledger.runId}: ${e instanceof Error ? e.message : String(e)}`);
    }
}
async function loadLedger(runId) {
    const row = await db_1.db.researchLedgerRow.findUnique({ where: { runId } });
    if (!row)
        return null;
    try {
        return JSON.parse(row.ledger);
    }
    catch {
        return null;
    }
}
// ---------------------------------------------------------------------------
// Deterministic claim / gap / contradiction machinery
// ---------------------------------------------------------------------------
const STOP_TERMS = new Set([
    'the', 'and', 'for', 'with', 'that', 'this', 'from', 'what', 'which', 'their', 'have',
    'are', 'was', 'were', 'has', 'had', 'how', 'why', 'who', 'when', 'where', 'into', 'about',
    'compare', 'search', 'research', 'today', 'current', 'latest', 'state', 'states', 'give',
    'tell', 'need', 'many', 'much', 'does', 'did', 'will', 'would', 'could', 'should', 'each',
    // command vocabulary — never a topic term (measured live: "do deep research"
    // leaked 'deep' into gaps and produced queries like "deep population tokyo")
    'deep', 'quick', 'please', 'also', 'using',
]);
/**
 * Strip the leading COMMAND phrase from a research request — the topic is
 * what follows. "do deep research: compare X vs Y" → "compare X vs Y".
 * Deterministic, applied once before decomposition/gap analysis.
 */
function stripCommandPrefix(q) {
    const stripped = q
        .replace(/^\s*(?:please\s+)?(?:do\s+(?:a\s+|some\s+|the\s+)?)?(?:deep\s+|quick\s+)?(?:web\s+)?(?:research|search|look\s*up|find\s+out|dig\s+into)\b[:,-]?\s*/i, '')
        .trim();
    return stripped.length >= 8 ? stripped : q.trim();
}
/** Key terms of a question — the coverage vocabulary for gaps. */
function keyTerms(question) {
    // Script-aware: the old ASCII-only filter returned [] for a Bengali question,
    // which silently disabled gap detection and left claimsFromExtract with no
    // query terms to score against.
    const words = [...(0, tokens_1.tokensOf)(question, 2)].filter((w) => !STOP_TERMS.has(w));
    return Array.from(new Set(words)).slice(0, 12);
}
/** Extract candidate claims from a source's readable extract (deterministic). */
function claimsFromExtract(extract, sourceId, queryTerms) {
    const sentences = extract
        .replace(/\s+/g, ' ')
        // Bengali ends a sentence with "।" (danda), and a Bengali sentence starts
        // with a Bengali letter — neither matched, so the whole article arrived as
        // ONE "sentence" and claim extraction produced a single unusable blob.
        .split(/(?<=[.!?।])\s+(?=[A-Z0-9\u0980-\u09FF])/)
        .slice(0, 40);
    const claims = [];
    for (const sentence of sentences) {
        if (sentence.length < 40 || sentence.length > 320)
            continue;
        const hasNumber = /\d/.test(sentence);
        const lower = sentence.toLowerCase();
        const termHits = queryTerms.filter((t) => lower.includes(t)).length;
        // A claim is a sentence that asserts something specific: a figure, a
        // date, or multiple question-term overlaps. Never a paraphrase — the
        // span IS the sentence, preserved byte-exact.
        if (!hasNumber && termHits < 2)
            continue;
        claims.push({
            id: `cl-${sourceId}-${claims.length}`,
            text: sentence,
            sourceId,
            confidence: Math.min(1, 0.4 + termHits * 0.15 + (hasNumber ? 0.2 : 0)),
            evidenceSpans: [sentence],
            contradictedBy: [],
        });
        if (claims.length >= 6)
            break;
    }
    return claims;
}
/** Numbers with units preserved EXACTLY as written (no rounding, no parsing).
 * Alternation is LONGEST-FIRST — measured live: `mi` swallowed `million`
 * ("24 million" → "24 mi", inventing a miles figure). */
const NUM_UNIT_RE = /(\d[\d,.,]*(?:\s?(?:billion|million|percent|years?|USD|km|mi|bn|%|\$|€|£|m\b))?)/gi;
/**
 * Contradiction detection: two claims asserting DIFFERENT numbers (same
 * unit class) for overlapping entity contexts. Deterministic, explicit —
 * a late-arriving contradiction is never silently dropped.
 * Noise guards (measured live: 71 raw pairs on a 3-branch turn, most false):
 *   - year-like integers (1900-2100) are LABELS, never quantities;
 *   - "24 M" vs "2011" (unit vs bare) is not comparable;
 *   - at most ONE contradiction per claim pair (first numeric mismatch).
 */
function detectContradictions(claims) {
    const contradictions = [];
    const isYearLike = (s) => /^(19|20)\d{2}$/.test(s.replace(/[^0-9]/g, ''));
    for (let i = 0; i < claims.length; i++) {
        for (let j = i + 1; j < claims.length; j++) {
            const a = claims[i];
            const b = claims[j];
            if (a.sourceId === b.sourceId)
                continue;
            const numsA = [...a.text.matchAll(NUM_UNIT_RE)].map((m) => m[0]);
            const numsB = [...b.text.matchAll(NUM_UNIT_RE)].map((m) => m[0]);
            if (numsA.length === 0 || numsB.length === 0)
                continue;
            // entity-context overlap: at least 3 shared significant words
            const wordsA = new Set(a.text.toLowerCase().match(/[a-z]{5,}/g) ?? []);
            const wordsB = new Set(b.text.toLowerCase().match(/[a-z]{5,}/g) ?? []);
            let shared = 0;
            for (const w of wordsA)
                if (wordsB.has(w))
                    shared++;
            if (shared < 3)
                continue;
            let fired = false;
            for (const na of numsA) {
                if (fired)
                    break;
                for (const nb of numsB) {
                    if (isYearLike(na) || isYearLike(nb))
                        continue;
                    const hasUnitA = /[a-z%$€£]/i.test(na);
                    const hasUnitB = /[a-z%$€£]/i.test(nb);
                    if (hasUnitA !== hasUnitB)
                        continue; // unit vs bare — not comparable
                    const va = parseFloat(na.replace(/[^0-9.]/g, ''));
                    const vb = parseFloat(nb.replace(/[^0-9.]/g, ''));
                    if (!isFinite(va) || !isFinite(vb) || va === 0 || vb === 0)
                        continue;
                    const rel = Math.abs(va - vb) / Math.max(va, vb);
                    const sameUnitClass = /km|mi|m\b|%|billion|bn|million|\$/.test(na) === /km|mi|m\b|%|billion|bn|million|\$/.test(nb);
                    if (sameUnitClass && rel >= 0.05) {
                        contradictions.push({
                            id: `cx-${a.id}-${b.id}`,
                            claimA: a.id,
                            claimB: b.id,
                            resolution: `conflicting figures "${na}" vs "${nb}" from different sources — both preserved, neither silently dropped`,
                        });
                        a.contradictedBy.push(b.id);
                        b.contradictedBy.push(a.id);
                        fired = true;
                        break;
                    }
                }
            }
        }
    }
    return contradictions;
}
/** Open gaps: question terms no claim or source title covers. */
function detectGaps(question, claims, coveredBySources) {
    const coveredText = (claims.map((c) => c.text).join(' ') +
        ' ' +
        coveredBySources.join(' ')).toLowerCase();
    const terms = keyTerms(question);
    const gaps = [];
    for (const term of terms) {
        if (!coveredText.includes(term)) {
            gaps.push({
                id: `gap-${term}`,
                description: `need sourced information about "${term}"`,
                attemptedQueries: [],
                status: 'open',
            });
        }
    }
    return gaps.slice(0, 3);
}
/** EAQL — evidence-aligned query learning: the query is conditioned on the
 * ledger's open gap, not an isolated re-query of the original question. */
function queryForGap(gap, question) {
    const term = gap.id.replace(/^gap-/, '');
    const context = keyTerms(question)
        .filter((t) => t !== term)
        .slice(0, 2)
        .join(' ');
    return `${term} ${context}`.trim();
}
/** Deterministic sub-question decomposition (first-wave branches). */
function decomposeQuestion(question) {
    const q = question.replace(/\s+/g, ' ').trim();
    // comparison pattern
    const vs = q.match(/(.+?)\s+(?:vs\.?|versus)\s+(.+?)(?:[?.]|$)/i);
    if (vs)
        return [vs[1].trim(), vs[2].trim()];
    // multi-part question (bounded)
    const parts = q
        .split(/\s+and\s+|\s+;\s*|\?/i)
        .map((p) => p.trim())
        .filter((p) => p.length >= 8);
    if (parts.length >= 2 && parts.length <= 4)
        return parts;
    return [q];
}
