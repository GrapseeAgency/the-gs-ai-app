"use strict";
/**
 * PHASE 8.3 — STRUCTURED EVIDENCE ASSEMBLY (spec §3/§4/§7).
 *
 * Tool output is NEVER dumped into an ambiguous giant text blob. Every
 * capability turn assembles:
 *
 *   TOOL RESULT  (capability / execution / searchExecuted / queries /
 *                 sources / evidence — machine-readable execution facts)
 *   --- EVIDENCE BEGIN / END ---
 *   EXECUTION CONTRACT  (what the execution state means for THIS turn)
 *   — END OF TOOL RESULT —
 *   CURRENT USER REQUEST
 *   <verbatim user message>          ← ALWAYS last, always the request
 *
 * The backend OWNS the execution state: `searchExecuted: true` is a fact the
 * application guarantees, not a hope the prompt enforces (§4). The verbatim
 * current request being last is the lesson learned from the document-control
 * failure (§7).
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.CURRENT_REQUEST_HEADER = exports.TOOL_RESULT_END_MARKER = void 0;
exports.buildFinalUserTurn = buildFinalUserTurn;
exports.buildResearchToolResultBlock = buildResearchToolResultBlock;
exports.buildResearchFailureToolResultBlock = buildResearchFailureToolResultBlock;
exports.buildTimeEvidence = buildTimeEvidence;
const capability_1 = require("@/lib/capability");
const research_1 = require("@/lib/search/research");
/** Marker that separates all tool/evidence data from the user's request. */
exports.TOOL_RESULT_END_MARKER = '— END OF TOOL RESULT — everything above is application tool output for this turn. The current user request follows below and is the request to answer. —';
exports.CURRENT_REQUEST_HEADER = 'CURRENT USER REQUEST';
/**
 * Assemble the final user message for an evidence-bearing turn:
 *   [TOOL RESULT blocks...] → [END MARKER] → [CURRENT USER REQUEST verbatim]
 * The latest user request remains unmistakably the request being answered (§7).
 */
function buildFinalUserTurn(params) {
    const present = params.blocks.filter((b) => typeof b === 'string' && b.length > 0);
    // Plain chat (no tool ran this turn) keeps its exact wire shape — the
    // verbatim user words and nothing else (§19: the model stays free).
    if (present.length === 0)
        return params.verbatim;
    const head = `${present.join('\n\n')}\n\n${exports.TOOL_RESULT_END_MARKER}\n\n`;
    return `${head}${exports.CURRENT_REQUEST_HEADER}\n${params.verbatim}`;
}
// ---------------------------------------------------------------------------
// WEB research tool result
// ---------------------------------------------------------------------------
/**
 * Wrap the numbered research evidence in the structured TOOL RESULT header.
 * `searchExecuted: true` is the machine-readable execution fact that fixes
 * CASE I (the model can no longer pretend the search did not happen — and
 * the post-generation guard enforces it, see synthesis-guard.ts).
 */
function buildResearchToolResultBlock(outcome) {
    const { block, maxOrdinal } = (0, research_1.buildResearchEvidenceBlock)(outcome.sources, outcome.queriesRun);
    const queriesLine = outcome.queriesRun.length > 0
        ? outcome.queriesRun.map((q) => `"${q}"`).join(', ')
        : '(none)';
    const retrieved = outcome.sources.filter((s) => s.status === 'retrieved').length;
    const header = [
        'TOOL RESULT — application execution state (authoritative, not model opinion)',
        'capability: web',
        `execution: completed`,
        'searchExecuted: true',
        `queries executed: ${queriesLine}`,
        `sources discovered: ${outcome.sources.length} (fully read: ${retrieved}, headline-only: ${outcome.sources.length - retrieved})`,
        `evidence units: ${maxOrdinal}`,
    ].join('\n');
    return `${header}\n${block}`;
}
/** Structured TOOL RESULT for an executed-but-failed search (honest failure). */
function buildResearchFailureToolResultBlock(outcome) {
    const reason = outcome.failure?.message ?? 'The search returned no usable results.';
    return [
        'TOOL RESULT — application execution state (authoritative, not model opinion)',
        'capability: web',
        'execution: failed',
        'searchExecuted: true',
        `queries attempted: ${outcome.queriesRun.map((q) => `"${q}"`).join(', ') || '(none)'}`,
        `failure: ${reason}`,
        'EXECUTION CONTRACT: the application DID attempt this search and it failed — the state above is authoritative. Do NOT pretend results were retrieved, and do NOT say you cannot search: the failure was in the retrieval, not in a missing capability. Briefly acknowledge that the search did not return usable results, then answer the CURRENT USER REQUEST below from your own knowledge if you can.',
    ].join('\n');
}
function tzOffsetLabel(tz, at) {
    try {
        const parts = new Intl.DateTimeFormat('en-GB', {
            timeZone: tz,
            timeZoneName: 'longOffset',
        }).formatToParts(at);
        const name = parts.find((p) => p.type === 'timeZoneName')?.value ?? '';
        return name === 'GMT' ? 'UTC+00:00' : name.replace(/^GMT/, 'UTC');
    }
    catch {
        return 'UTC+00:00';
    }
}
/**
 * REAL time capability: the backend computes the current time in the user's
 * timezone (client-supplied IANA zone; UTC fallback). The model only turns
 * the fact into natural language — it never invents the time and never needs
 * a web search for it.
 */
function buildTimeEvidence(clientTz) {
    const timezone = (0, capability_1.validTimeZone)(clientTz) ?? 'UTC';
    const now = new Date();
    const utcIso = now.toISOString();
    const epochMs = now.getTime();
    let localDateTime = utcIso;
    let localDate = '';
    let localWeekday = '';
    try {
        const fmt = new Intl.DateTimeFormat('en-GB', {
            timeZone: timezone,
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
            hour12: false,
            weekday: 'long',
        });
        const parts = Object.fromEntries(fmt.formatToParts(now).map((p) => [p.type, p.value]));
        localDate = `${parts.year}-${parts.month}-${parts.day}`;
        localWeekday = parts.weekday ?? '';
        localDateTime = `${localDate}T${parts.hour === '24' ? '00' : parts.hour}:${parts.minute}:${parts.second}`;
    }
    catch {
        // formatting failure → ISO fallback already set
    }
    const block = [
        'TOOL RESULT — application execution state (authoritative, not model opinion)',
        'capability: time',
        'execution: completed',
        'searchExecuted: false',
        `utc_now: ${utcIso}`,
        `local_now: ${localWeekday}, ${localDateTime} (${timezone}, ${tzOffsetLabel(timezone, now)})`,
        `timezone: ${timezone}`,
        `epoch_ms: ${epochMs}`,
        'EXECUTION CONTRACT: this is the APPLICATION CLOCK — the authoritative current time for this turn, computed in the user\'s timezone. Answer the CURRENT USER REQUEST below using this clock fact naturally. Do not say you cannot know the time, and do not invent a different one. No web search ran this turn and none is needed to answer time questions.',
    ].join('\n');
    return { block, meta: { timezone, utcIso, localIso: localDateTime, epochMs } };
}
