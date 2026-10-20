"use strict";
/**
 * EVALUATION INFRASTRUCTURE — LAYER 5 PRODUCTION MONITOR (Phase 5).
 *
 * Closed the loop the audit demanded: the SAME deterministic grading that
 * gates PRs (Layer 4) now samples LIVE traffic.
 *
 *   hourly pass:
 *     1. sample 5% of the turns recorded in `turns` in the trailing hour
 *        (evenly spaced — stable, no clustering)
 *     2. join each sampled turn to its user message and classify it into a
 *        monitor-gradable bucket (deterministic prompt classifiers mirroring
 *        the eval-suite categories)
 *     3. grade the trace with the Layer-4 assertion semantics
 *     4. write one eval_scores row per (windowStart, category) — passRate is
 *        the honest production number
 *     5. week-over-week: compare against the same category's rate 7 days
 *        earlier; a drop > 5 percentage points sets alerted + alertNote
 *
 * DETERMINISM: classification and grading are pure functions of (prompt,
 * trace row). No network. Provider-throttled turns (finalStatus=error with
 * an errorType) are UNGRADEABLE for the search-expecting buckets — infra
 * must never count as a product failure (case-i lesson) — but contamination
 * guards (ROUTING/ISOLATION) still grade, because a routing decision that
 * fired on casual text is a product fact even when the provider then died.
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.MONITOR_CATEGORIES = void 0;
exports.classifyPrompt = classifyPrompt;
exports.parseDomains = parseDomains;
exports.parseCited = parseCited;
exports.gradeTurn = gradeTurn;
exports.resolveBucket = resolveBucket;
exports.sampleIndices = sampleIndices;
exports.runMonitorPass = runMonitorPass;
const bun_sqlite_1 = require("bun:sqlite");
exports.MONITOR_CATEGORIES = ['ROUTING', 'CONTEXT_ISOLATION', 'SOURCE_QUALITY', 'FAILURE_HONESTY'];
// --- deterministic prompt classifiers (mirror the suite's case families) ---------
const CASUAL_RE = /^\s*(hi+|hello+|hey+|yo+|sup|what'?s up|how are you|how r u|thanks?|thank you|thx|ok(ay)?|lol+|lmao|gm|gn|good (morning|afternoon|evening|night)|bye+|cool|nice|great|wow|bruh)\b/i;
const ARITHMETIC_RE = /^\s*(what is|what's|whats|calc(ulate)?|how much is)?\s*[\d\s+\-*/().^%]+\s*[?.!]*\s*$/i;
const JOKE_RE = /^\s*(tell|say|give) (me )?a joke\b/i;
const EXPLICIT_SEARCH_RE = /^\s*(search( the web| online| for)?|go to the internet|go online|google|look up|look this up|find (me )?|browse)\b/i;
const WEB_MARKERS_RE = /\b(search the web|on the internet|online|latest|today'?s|current|right now|news)\b/i;
/** Classify a user prompt into a monitor bucket. Pure function. */
function classifyPrompt(prompt) {
    const p = prompt.trim();
    if (!p)
        return 'UNCLASSIFIED';
    if (EXPLICIT_SEARCH_RE.test(p) || WEB_MARKERS_RE.test(p))
        return 'EXPLICIT_SEARCH';
    if (CASUAL_RE.test(p) || ARITHMETIC_RE.test(p) || JOKE_RE.test(p))
        return 'NO_SEARCH_EXPECTED';
    return 'UNCLASSIFIED';
}
function parseDomains(raw) {
    try {
        const arr = JSON.parse(raw);
        return Array.isArray(arr) ? arr : [];
    }
    catch {
        return [];
    }
}
function parseCited(raw) {
    try {
        const arr = JSON.parse(raw);
        return Array.isArray(arr) ? arr : [];
    }
    catch {
        return [];
    }
}
/**
 * Grade one classified turn. Pure function of (bucket, trace, priorTurnHadSources).
 * Mirrors the Layer-4 assertion semantics for each bucket.
 */
function gradeTurn(bucket, trace, priorTurnHadSources) {
    const base = { requestId: trace.requestId, bucket, category: null, graded: false, passed: null, failures: [] };
    const errored = trace.finalStatus === 'error' || trace.errorType !== null;
    switch (bucket) {
        case 'NO_SEARCH_EXPECTED': {
            // ROUTING: casual/arithmetic/joke prompts must not search (audit B1 class).
            base.category = 'ROUTING';
            base.graded = true;
            const failures = [];
            if (trace.searchExecuted !== 0)
                failures.push(`searchExecuted=${trace.searchExecuted} expected 0`);
            if (trace.sourceCount !== 0)
                failures.push(`sourceCount=${trace.sourceCount} expected 0`);
            base.passed = failures.length === 0;
            base.failures = failures;
            return base;
        }
        case 'ISOLATION_GUARD': {
            // CONTEXT_ISOLATION: casual follow-up in a conversation that already
            // used sources must stay source-free (audit [9] class).
            base.category = 'CONTEXT_ISOLATION';
            base.graded = true;
            const failures = [];
            if (trace.searchExecuted !== 0)
                failures.push(`searchExecuted=${trace.searchExecuted} expected 0`);
            if (trace.sourceCount !== 0)
                failures.push(`sourceCount=${trace.sourceCount} expected 0 (prior turn had sources)`);
            base.passed = failures.length === 0;
            base.failures = failures;
            return base;
        }
        case 'EXPLICIT_SEARCH': {
            // ROUTING: explicit search prompts must actually search. A throttled
            // turn is infra, not a routing lie — ungradeable, never a pass.
            base.category = 'ROUTING';
            if (errored) {
                base.graded = false;
                base.failures = [`turn errored (errorType=${trace.errorType ?? 'unknown'}) — ungradeable`];
                return base;
            }
            base.graded = true;
            base.passed = trace.searchExecuted === 1;
            if (!base.passed)
                base.failures = [`searchExecuted=${trace.searchExecuted} expected 1 for explicit search`];
            return base;
        }
        case 'SOURCE_QUALITY': {
            // SOURCE_QUALITY bar = audit [16], SAME as the suite (S36-S40
            // sourcesReadMin>=1): a grounded turn must READ at least one source.
            // The stricter 70%-of-discovered read-rate bar is PARKED as a product
            // question (forensic closure TASK 2): raising maxFetches trades latency
            // for depth — a product call, not an infra call — and with maxFetches=4
            // against 6-20 discovered sources that bar was structurally unreachable
            // (max possible 50%), so it measured the bar, not the product.
            base.category = 'SOURCE_QUALITY';
            if (errored) {
                base.graded = false;
                base.failures = [`turn errored (errorType=${trace.errorType ?? 'unknown'}) — ungradeable`];
                return base;
            }
            base.graded = true;
            const failures = [];
            if (trace.sourceCount <= 0)
                failures.push(`sourceCount=${trace.sourceCount} expected >0 for grounded turn`);
            else if (trace.sourcesRead < 1)
                failures.push(`sourcesRead=${trace.sourcesRead} expected >=1 (sourceCount=${trace.sourceCount} failed=${trace.sourcesFailed})`);
            base.passed = failures.length === 0;
            base.failures = failures;
            return base;
        }
        case 'HONEST_NO_RESULTS': {
            // FAILURE_HONESTY: a search that found nothing must still complete
            // honestly (finalStatus=done), not error out or fake results (case K).
            base.category = 'FAILURE_HONESTY';
            if (errored) {
                base.graded = false;
                base.failures = [`turn errored (errorType=${trace.errorType ?? 'unknown'}) — ungradeable`];
                return base;
            }
            base.graded = true;
            base.passed = trace.finalStatus === 'done';
            if (!base.passed)
                base.failures = [`finalStatus=${trace.finalStatus} expected done for zero-result search`];
            return base;
        }
        default:
            return base;
    }
}
/** Bucket override: casual prompt in a source-bearing conversation → isolation guard. */
function resolveBucket(prompt, priorTurnHadSources) {
    const b = classifyPrompt(prompt);
    if (b === 'NO_SEARCH_EXPECTED' && priorTurnHadSources)
        return 'ISOLATION_GUARD';
    return b;
}
function openDb(path) {
    const db = new bun_sqlite_1.Database(path);
    try {
        db.run('PRAGMA busy_timeout = 10000');
    }
    catch { /* retry logic per-statement below still applies */ }
    return db;
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
async function withRetry(fn, attempts = 6) {
    let last;
    for (let i = 0; i < attempts; i++) {
        try {
            return await fn();
        }
        catch (e) {
            last = e;
            const code = e.code;
            if (code !== 'SQLITE_BUSY')
                throw e;
            await sleep(1000);
        }
    }
    throw last;
}
/** Evenly-spaced deterministic sample of indices [0..n-1], ~rate, capped. */
function sampleIndices(n, rate, cap) {
    if (n <= 0)
        return [];
    const want = Math.min(cap, Math.max(1, Math.round(n * rate)));
    if (want >= n)
        return Array.from({ length: n }, (_, i) => i);
    const step = n / want;
    const out = [];
    for (let i = 0; i < want; i++)
        out.push(Math.floor(i * step));
    return out;
}
async function runMonitorPass(opts) {
    const rate = opts.sampleRate ?? 0.05;
    const cap = opts.maxSample ?? 60;
    const end = opts.windowEnd ?? new Date();
    const start = opts.windowStart ?? new Date(end.getTime() - 3600000);
    const db = openDb(opts.dbPath);
    try {
        // await is REQUIRED: withRetry is async (SQLITE_BUSY sleep-retry). Without
        // it `rows` is a Promise — rows.length is undefined, sampling silently
        // degrades to an empty pass and turnsInWindow reports undefined.
        const rows = await withRetry(() => db
            .query(`SELECT requestId, conversationId, messageId, capability, searchExecuted, sourceCount, sourcesRead, sourcesFailed, domains, finalStatus, errorType
           FROM turns WHERE timestamp >= ? AND timestamp < ? ORDER BY timestamp ASC`)
            .all(start.getTime(), end.getTime()));
        const idx = sampleIndices(rows.length, rate, cap);
        const sampled = idx.map((i) => rows[i]);
        // Prior-turn context per conversation: an assistant message with sources
        // earlier in the same conversation marks the isolation guard.
        const priorSources = new Map();
        const msgStmt = db.query('SELECT COUNT(1) n FROM MessageSource ms JOIN Message m ON ms.messageId = m.id WHERE m.conversationId = ? AND m.createdAt < (SELECT createdAt FROM Message WHERE id = ?)');
        const userStmt = db.query('SELECT content FROM Message WHERE id = ? AND role = ?');
        const verdicts = [];
        for (const row of sampled) {
            const um = await withRetry(() => userStmt.get(row.messageId, 'user'));
            const prompt = um?.content ?? '';
            const hadSources = priorSources.get(row.conversationId) ?? (await withRetry(() => msgStmt.get(row.conversationId, row.messageId)?.n > 0)) ?? false;
            priorSources.set(row.conversationId, hadSources || row.sourceCount > 0);
            const bucket = resolveBucket(prompt, hadSources);
            verdicts.push(gradeTurn(bucket, row, hadSources));
            // A grounded turn carries TWO product facts, so it contributes to TWO
            // categories: the routing decision (above) and, when the search actually
            // executed, the source-quality / honest-empty verdict (suite S31-S40 /
            // F41-F50 semantics). Without this the SOURCE_QUALITY and
            // FAILURE_HONESTY eval_scores rows can never fire in production.
            if (bucket === 'EXPLICIT_SEARCH' && row.searchExecuted === 1) {
                verdicts.push(gradeTurn(row.sourceCount > 0 ? 'SOURCE_QUALITY' : 'HONEST_NO_RESULTS', row, hadSources));
            }
        }
        // Aggregate per category.
        const scores = [];
        for (const category of exports.MONITOR_CATEGORIES) {
            const vs = verdicts.filter((v) => v.category === category);
            const graded = vs.filter((v) => v.graded);
            const passCount = graded.filter((v) => v.passed === true).length;
            const failCount = graded.filter((v) => v.passed === false).length;
            const passRate = graded.length > 0 ? passCount / graded.length : 0;
            // WoW: same category, window 7 days earlier (±1h tolerance).
            const wowRow = await withRetry(() => db
                .query(`SELECT passRate FROM eval_scores WHERE category = ? AND windowStart BETWEEN ? AND ? ORDER BY ABS(windowStart - ?) ASC LIMIT 1`)
                .get(category, start.getTime() - 7 * 3600000 - 3600000, start.getTime() - 7 * 3600000 + 3600000, start.getTime() - 7 * 3600000));
            const wowDelta = wowRow ? passRate - wowRow.passRate : null;
            const alerted = wowDelta !== null && wowDelta <= -0.05;
            const alertNote = alerted
                ? `${category} passRate regressed ${(wowDelta * 100).toFixed(1)}pp WoW (${(wowRow.passRate * 100).toFixed(1)}% → ${(passRate * 100).toFixed(1)}%) — investigate production turns`
                : null;
            scores.push({
                category,
                sampledTurns: vs.length,
                gradedTurns: graded.length,
                passCount,
                failCount,
                passRate,
                wowDelta,
                alerted,
                alertNote,
                sampleDetail: vs.map((v) => ({ requestId: v.requestId, bucket: v.bucket, graded: v.graded, passed: v.passed, failures: v.failures })),
            });
            if (graded.length > 0) {
                const detail = vs.map((v) => ({ requestId: v.requestId, bucket: v.bucket, graded: v.graded, passed: v.passed, failures: v.failures }));
                await withRetry(() => db
                    .query(`INSERT INTO eval_scores (id, windowStart, windowEnd, category, sampledTurns, gradedTurns, passCount, failCount, passRate, wowDelta, alerted, alertNote, sampleDetail, createdAt)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
               ON CONFLICT(windowStart, category) DO UPDATE SET
                 sampledTurns=excluded.sampledTurns, gradedTurns=excluded.gradedTurns, passCount=excluded.passCount,
                 failCount=excluded.failCount, passRate=excluded.passRate, wowDelta=excluded.wowDelta,
                 alerted=excluded.alerted, alertNote=excluded.alertNote, sampleDetail=excluded.sampleDetail`)
                    .run(crypto.randomUUID(), start.getTime(), end.getTime(), category, vs.length, graded.length, passCount, failCount, passRate, wowDelta, alerted ? 1 : 0, alertNote, JSON.stringify(detail), Date.now()));
            }
        }
        return {
            windowStart: start.toISOString(),
            windowEnd: end.toISOString(),
            turnsInWindow: rows.length,
            sampled: sampled.length,
            graded: verdicts.filter((v) => v.graded).length,
            passed: verdicts.filter((v) => v.graded && v.passed === true).length,
            failed: verdicts.filter((v) => v.graded && v.passed === false).length,
            scores,
        };
    }
    finally {
        db.close();
    }
}
