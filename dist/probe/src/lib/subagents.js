"use strict";
/**
 * PHASE 6 — ASYNC SUBAGENT DISPATCH (LangChain Deep Agents v0.5 pattern).
 *
 * Long-running research branches must not block the supervisor turn: the
 * supervisor LAUNCHES a task and continues working (ledger consolidation,
 * contradiction detection), then collects results bounded by the deadline.
 *
 * TOOLS: launch_subagent → taskId | check_subagent → status|result |
 * cancel_subagent | list_subagents | get_subagent_result | steer_subagent
 * (course-correct mid-task).
 *
 * HETEROGENEOUS DEPLOYMENT: a task may run on a LOCAL executor (the default
 * registry below) or on a REMOTE agent server (url + graphId — different
 * hardware, different model, own tool set). The remote client is the
 * deployment seam: POST {graphId, input} → {taskId}, poll GET ?taskId=.
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.registerLocalHandler = registerLocalHandler;
exports.launchSubagent = launchSubagent;
exports.checkSubagent = checkSubagent;
exports.getSubagentResult = getSubagentResult;
exports.cancelSubagent = cancelSubagent;
exports.listSubagents = listSubagents;
exports.steerSubagent = steerSubagent;
exports.waitForSubagent = waitForSubagent;
const db_1 = require("@/lib/db");
const research_1 = require("@/lib/search/research");
const types_1 = require("@/lib/search/types");
const aborts = new Map();
const steerNotes = new Map();
const localHandlers = {
    // research_branch: one bounded research execution (the Phase-5 branch unit)
    research_branch: async (input, taskId) => {
        const query = String(input.query ?? '');
        const deadlineAt = typeof input.deadlineAt === 'number' ? input.deadlineAt : Date.now() + 45000;
        const steerKey = `steer:${taskId}`;
        const outcome = await (0, research_1.runResearch)({
            queries: [query],
            intent: input.intent ?? 'research',
            timeRange: input.timeRange ?? 'none',
            region: input.region,
            sourceHint: input.sourceHint,
            officialOnly: input.officialOnly === true,
            depth: 'quick',
            emit: types_1.NOOP_EMIT,
            deadlineAt,
        });
        const note = steerNotes.get(steerKey)?.[0];
        return {
            query,
            ...(note ? { steered: note } : {}),
            ok: outcome.ok,
            sources: outcome.sources.map((s) => ({
                ordinal: s.ordinal,
                title: s.title,
                url: s.url,
                domain: s.domain,
                snippet: s.snippet,
                status: s.status,
                query: s.query,
                // Aggregator-assigned facts (§8.2 §3) — class, authority tier, the
                // REAL engine and rank. Dropping them here used to make the
                // supervisor fabricate `engine: 'subagent'` + `searchRank: 0` for
                // every gap-branch source, losing which engine actually found the
                // page and how it ranked.
                sourceType: s.sourceType,
                authority: s.authority,
                engine: s.engine,
                searchRank: s.searchRank,
                publishedDate: s.publishedDate,
                // trimmed extract — enough for claim extraction without bloating
                // the task result row (the supervisor ledger re-claims from it)
                ...(s.pageExtract ? { extract: s.pageExtract.slice(0, 4000) } : {}),
            })),
            queriesRun: outcome.queriesRun,
            failure: outcome.failure ?? null,
        };
    },
};
function registerLocalHandler(name, handler) {
    localHandlers[name] = handler;
}
/** launch_subagent(name, input) → taskId — returns IMMEDIATELY. */
async function launchSubagent(params) {
    const task = await db_1.db.subagentTask.create({
        data: {
            runId: params.runId,
            name: params.name,
            url: params.url ?? null,
            graphId: params.graphId ?? null,
            input: JSON.stringify(params.input),
            status: 'pending',
        },
    });
    const taskId = task.id;
    const ac = new AbortController();
    aborts.set(taskId, ac);
    // fire-and-continue: the supervisor's next line runs without waiting
    void (async () => {
        await db_1.db.subagentTask.update({ where: { id: taskId }, data: { status: 'running' } });
        try {
            let result;
            if (params.url && params.graphId) {
                // REMOTE agent server (heterogeneous deployment seam)
                const res = await fetch(params.url, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ graphId: params.graphId, input: params.input }),
                    signal: ac.signal,
                });
                result = (await res.json());
            }
            else {
                const handler = localHandlers[params.name];
                if (!handler)
                    throw new Error(`no local executor for subagent '${params.name}'`);
                result = await handler(params.input, taskId);
            }
            if (aborts.get(taskId)?.signal.aborted)
                return; // cancelled mid-flight
            await db_1.db.subagentTask.update({
                where: { id: taskId },
                data: { status: 'done', result: JSON.stringify(result) },
            });
        }
        catch (e) {
            const raw = String(e instanceof Error ? e.message : e);
            await db_1.db.subagentTask
                .update({
                where: { id: taskId },
                data: { status: ac.signal.aborted ? 'cancelled' : 'failed', error: raw.slice(0, 500) },
            })
                .catch(() => undefined);
        }
        finally {
            aborts.delete(taskId);
        }
    })();
    return taskId;
}
/** check_subagent(taskId) → status | result */
async function checkSubagent(taskId) {
    const t = await db_1.db.subagentTask.findUnique({ where: { id: taskId } });
    if (!t)
        throw new Error(`subagent task ${taskId} not found`);
    return {
        id: t.id,
        runId: t.runId,
        name: t.name,
        status: t.status,
        input: safeJson(t.input),
        result: t.result ? safeJson(t.result) : null,
        error: t.error,
    };
}
/** get_subagent_result(taskId) → result (throws unless done) */
async function getSubagentResult(taskId) {
    const t = await checkSubagent(taskId);
    if (t.status !== 'done' || !t.result) {
        throw new Error(`subagent ${taskId} not done (status=${t.status})`);
    }
    return t.result;
}
/** cancel_subagent(taskId) — course-correct primitive + abort. */
async function cancelSubagent(taskId) {
    aborts.get(taskId)?.abort();
    await db_1.db.subagentTask
        .updateMany({ where: { id: taskId, status: { in: ['pending', 'running'] } }, data: { status: 'cancelled' } });
}
/** list_subagents(runId) */
async function listSubagents(runId) {
    const rows = await db_1.db.subagentTask.findMany({ where: { runId }, orderBy: { createdAt: 'asc' } });
    return rows.map((t) => ({
        id: t.id,
        runId: t.runId,
        name: t.name,
        status: t.status,
        input: safeJson(t.input),
        result: t.result ? safeJson(t.result) : null,
        error: t.error,
    }));
}
/** steer_subagent(taskId, instruction) — mid-task course-correction. */
function steerSubagent(taskId, instruction) {
    const notes = steerNotes.get(`steer:${taskId}`) ?? [];
    notes.push(instruction);
    steerNotes.set(`steer:${taskId}`, notes);
}
/** Await a task's completion, bounded — the supervisor collects, never blocks unbounded. */
async function waitForSubagent(taskId, deadlineAt, pollMs = 250) {
    for (;;) {
        const t = await checkSubagent(taskId);
        if (t.status === 'done' || t.status === 'failed' || t.status === 'cancelled')
            return t;
        if (Date.now() >= deadlineAt)
            return t; // supervisor budget wins
        await new Promise((r) => setTimeout(r, pollMs));
    }
}
const safeJson = (raw) => {
    try {
        return JSON.parse(raw);
    }
    catch {
        return {};
    }
};
