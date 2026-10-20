"use strict";
/**
 * TURN EXECUTOR — the turn pipeline, extracted verbatim from the messages
 * route (PHASE 1 — detached turn execution, ADR-117 pattern).
 *
 * ONE pipeline, two transports:
 *   - legacy route  → push = in-memory queue (unchanged inline SSE / JSON)
 *   - detached run  → push = stream-broker publish (POST /messages/start +
 *                     GET /api/v1/stream/{streamId}); the HTTP connection is
 *                     a mere subscriber and can die without touching the run.
 *
 * NO prompt changes, NO routing changes — every decision (capability gate,
 * router, planner, guards, evidence assembly) is the code that already
 * shipped, moved into this module so the detached producer executes the
 * identical turn.
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.prepareTurn = prepareTurn;
exports.runTurn = runTurn;
const server_1 = require("next/server");
const db_1 = require("@/lib/db");
const serializers_1 = require("@/lib/serializers");
const models_1 = require("@/lib/models");
const model_capabilities_1 = require("@/lib/model-capabilities");
const keypool_1 = require("@/lib/keypool");
const openrouter_1 = require("@/lib/openrouter");
const router_1 = require("@/lib/router");
const ai_1 = require("@/lib/ai");
const capability_1 = require("@/lib/capability");
const concurrency_1 = require("@/lib/concurrency");
const trace_1 = require("@/lib/trace");
const revision_1 = require("@/lib/revision");
const compile_1 = require("@/lib/widgets/compile");
const research_context_1 = require("@/lib/research-context");
const evidence_1 = require("@/lib/evidence");
const synthesis_guard_1 = require("@/lib/synthesis-guard");
const numeric_guard_1 = require("@/lib/numeric-guard");
const silent_failure_gate_1 = require("@/lib/silent-failure-gate");
const output_directive_1 = require("@/lib/output-directive");
const constraint_guard_1 = require("@/lib/constraint-guard");
const document_1 = require("@/lib/document");
const vision_1 = require("@/lib/vision");
const websearch_1 = require("@/lib/websearch");
const planner_1 = require("@/lib/search/planner");
const research_1 = require("@/lib/search/research");
const branching_1 = require("@/lib/research/branching");
const context_crystallize_1 = require("@/lib/context-crystallize");
const types_1 = require("@/lib/search/types");
const turn_events_1 = require("@/lib/turn-events");
const run_store_1 = require("@/lib/run-store");
const HISTORY_LIMIT = 20;
const MAX_CONTENT_LENGTH = 32000;
const DEFAULT_TITLE = 'New chat';
const CURRENT_APP_VERSION = '0.73.0';
// ---------------------------------------------------------------------------
// PREPARE — request validation + user-message persistence + context build
// (verbatim move of the messages-route preflight)
// ---------------------------------------------------------------------------
async function prepareTurn(args, rawBody) {
    const id = args.conversationId;
    const content = args.content;
    // Validate every attachment up-front: must exist, belong to this
    // conversation (or be unbound), and not already be attached to a message.
    const attachmentsProvided = args.attachmentIds.length > 0;
    let claimedAttachments = [];
    if (attachmentsProvided) {
        claimedAttachments = await db_1.db.attachment.findMany({ where: { id: { in: args.attachmentIds } } });
        if (claimedAttachments.length !== args.attachmentIds.length) {
            return {
                kind: 'invalid',
                response: server_1.NextResponse.json({ code: 'invalid_attachments', message: 'One or more attachments do not exist' }, { status: 400 }),
            };
        }
        const foreign = claimedAttachments.find((a) => (a.conversationId !== null && a.conversationId !== id) || a.messageId !== null);
        if (foreign) {
            return {
                kind: 'invalid',
                response: server_1.NextResponse.json({ code: 'invalid_attachments', message: 'One or more attachments are already in use' }, { status: 400 }),
            };
        }
    }
    const imageAttachments = claimedAttachments.filter((a) => a.kind === 'image');
    const conversation = await db_1.db.conversation.findUnique({
        where: { id },
        include: { assistant: true },
    });
    if (!conversation) {
        return {
            kind: 'invalid',
            response: server_1.NextResponse.json({ code: 'not_found', message: 'Conversation not found' }, { status: 404 }),
        };
    }
    const shouldAutoTitle = conversation.title === DEFAULT_TITLE;
    // RESUME SEMANTICS: a resumed run reuses the user message persisted by the
    // original start — the side effect already happened, it must not happen
    // twice (the idempotency guarantee, at the persistence layer).
    const userMessage = args.existingUserMessageId
        ? (await db_1.db.message.findUnique({ where: { id: args.existingUserMessageId } })) ??
            (await db_1.db.message.create({ data: { conversationId: id, role: 'user', content } }))
        : await db_1.db.message.create({ data: { conversationId: id, role: 'user', content } });
    if (attachmentsProvided && !args.existingUserMessageId) {
        await db_1.db.attachment.updateMany({
            where: { id: { in: args.attachmentIds } },
            data: { messageId: userMessage.id, conversationId: id },
        });
    }
    if (shouldAutoTitle && !args.existingUserMessageId) {
        await db_1.db.conversation.update({
            where: { id },
            data: {
                title: (content.trim().length > 0
                    ? content
                    : claimedAttachments[0]?.displayName ?? DEFAULT_TITLE).slice(0, 40),
            },
        });
    }
    // The model's memory has a cutoff; the app has a clock. Without this line a
    // request like "chart the last six months" is answered from the training
    // year, and every date in the chart is wrong — the single most common way a
    // correct-looking answer is silently incorrect.
    const clock = (0, evidence_1.buildTimeEvidence)(args.timezone ?? null).meta;
    const runtimeContext = `\n\nCONTEXT\n- Today is ${clock.localIso.slice(0, 10)} in ${clock.timezone} (local time ${clock.localIso.slice(11, 16)}).\n- Resolve every relative date (\"last 6 months\", \"this quarter\", \"next Friday\") from this clock, never from memory.\n- If a widget needs dates, use these dates.`;
    const baseSystemPrompt = conversation.assistant?.instructions
        ? `${conversation.assistant.instructions}\n\n(Platform persona base: ${ai_1.SYSTEM_PROMPT})\n\n${ai_1.WIDGET_PROMPT}${runtimeContext}`
        : `${ai_1.SYSTEM_PROMPT}\n\n${ai_1.WIDGET_PROMPT}${runtimeContext}`;
    const recentDesc = await db_1.db.message.findMany({
        where: { conversationId: id, id: { not: userMessage.id } },
        orderBy: { createdAt: 'desc' },
        take: HISTORY_LIMIT,
        include: { attachments: { orderBy: { createdAt: 'asc' } } },
    });
    const history = recentDesc.reverse();
    const historyRowText = (m) => {
        if (m.role.toLowerCase() !== 'user' || m.content.trim().length > 0)
            return m.content;
        if (m.attachments.some(document_1.isDocumentAttachment))
            return document_1.DOCUMENT_ONLY_DEFAULT;
        if (m.attachments.some((a) => a.kind === 'image'))
            return 'Describe this image.';
        return m.content;
    };
    const recentImageTurnIds = recentDesc
        .filter((m) => m.role.toLowerCase() === 'user' && m.attachments.some((a) => a.kind === 'image'))
        .slice(0, vision_1.VISION_HISTORY_IMAGE_TURNS)
        .map((m) => m.id);
    const useVision = imageAttachments.length > 0 || recentImageTurnIds.length > 0;
    // TASK 3 vision input contract — OCR sidecar. Clients run OCR at attach
    // time and upload it alongside the bytes. Here it is only COLLECTED; it is
    // consumed in exactly two places:
    //   - buildModelMessages: when the image bytes are unusable, the OCR text
    //     rides as evidence with the honest prefix (never as a description).
    //   - the pre-turn gate: images unusable AND no OCR text -> the reply is
    //     the deterministic "I cannot read this image." — no model call.
    const ocrFallbackText = imageAttachments
        .map((a) => (a.ocrText ?? '').trim())
        .filter((t) => t.length > 0)
        .join('\n\n') || null;
    const currentTurnDocs = claimedAttachments.filter(document_1.isDocumentAttachment);
    const historyDocTurns = recentDesc
        .filter((m) => m.role.toLowerCase() === 'user' && m.attachments.some(document_1.isDocumentAttachment))
        .slice(0, document_1.DOC_HISTORY_TURNS)
        .map((m) => ({ attachments: m.attachments.filter(document_1.isDocumentAttachment) }));
    const docContext = currentTurnDocs.length > 0 || historyDocTurns.length > 0
        ? await (0, document_1.collectDocumentContext)(currentTurnDocs, historyDocTurns, content)
        : { block: null, failures: [], readableCount: 0 };
    const allDocsFailed = currentTurnDocs.length > 0 && docContext.readableCount === 0 && imageAttachments.length === 0;
    const webGate = content.trim().length > 0 ? (0, websearch_1.evaluateWebSearchGate)(content) : { trigger: null };
    let previousClarifyOptions = null;
    if (content.trim().length > 0) {
        const lastClarify = await db_1.db.message.findFirst({
            where: { conversationId: id, role: 'assistant', clarifyOptions: { not: null } },
            orderBy: { createdAt: 'desc' },
            select: { clarifyOptions: true },
        });
        if (lastClarify?.clarifyOptions) {
            try {
                const parsed = JSON.parse(lastClarify.clarifyOptions);
                if (Array.isArray(parsed.options)) {
                    previousClarifyOptions = parsed.options
                        .filter((o) => typeof o === 'object' && o !== null && typeof o.id === 'string' && typeof o.label === 'string')
                        .slice(0, 8);
                }
            }
            catch {
                // malformed persisted options — treat as absent
            }
        }
    }
    const clarifyFollowUp = previousClarifyOptions !== null && content.trim().length > 0 && content.length <= 60;
    const historicalReligious = content.trim().length > 0 ? (0, planner_1.detectHistoricalReligious)(content) : false;
    const numericCheck = content.trim().length > 0 && (0, numeric_guard_1.isCountingRequest)(content);
    const directive = content.trim().length > 0 ? (0, output_directive_1.detectOutputDirective)(content) : null;
    const constraints = content.trim().length > 0 ? (0, constraint_guard_1.detectOutputConstraints)(content) : null;
    const clientTimezone = args.timezone ?? null;
    const cap = (0, capability_1.decideCapability)({
        content,
        hasImages: useVision,
        hasDocuments: currentTurnDocs.length > 0 || historyDocTurns.length > 0,
        historicalReligious,
    });
    const requestId = args.requestId;
    const turnStartedAt = Date.now();
    const clientVersion = args.clientVersion;
    const backendRevision = await (0, revision_1.getBackendRevision)();
    const versionMismatch = clientVersion !== 'web' && clientVersion !== CURRENT_APP_VERSION;
    if (versionMismatch) {
        console.warn(`GS-VERSION-MISMATCH requestId=${requestId} conv=${id} clientVersion=${clientVersion} currentAppVersion=${CURRENT_APP_VERSION} backendRevision=${backendRevision} — audit results may reflect an older client`);
    }
    const routerPlan = (0, router_1.planRoute)({
        content,
        hasImages: useVision,
        hasDocuments: currentTurnDocs.length > 0 || historyDocTurns.length > 0,
        webGateTrigger: cap.searchPlanned ? 'explicit' : webGate.trigger,
        clarifyFollowUp,
        historicalReligious,
        historyChars: history.reduce((n, m) => n + m.content.length, 0),
        trafficKey: requestId,
    });
    const plannerRuns = routerPlan.plannerRuns;
    console.log(`GS-ROUTER conv=${id} route=${routerPlan.route} depth=${routerPlan.depth} reason=${routerPlan.reason} capability=${cap.capability} trigger=${cap.trigger ?? 'none'} forced=${cap.searchPlanned}`);
    console.log(`GS-CAPABILITY conv=${id} requiredCapability=${routerPlan.capability} model=${routerPlan.internalModelId} providerLock=${routerPlan.providerLock ?? 'none'} measured=${routerPlan.measuredScore !== null ? routerPlan.measuredScore.toFixed(3) : 'unmeasured'}`);
    const keypool = await (0, keypool_1.loadKeyPool)();
    const freeAvailable = keypool.keys.length > 0;
    const forceFree = process.env.GS_FORCE_FREE_CHAIN === '1';
    const lock = routerPlan.providerLock;
    const tierFreeEligible = routerPlan.route === 'TEXT_SIMPLE' || routerPlan.route === 'WEB' || routerPlan.registerClass;
    const forceFreeEligible = forceFree &&
        (tierFreeEligible ||
            routerPlan.route === 'TEXT_COMPLEX' ||
            routerPlan.route === 'CODING' ||
            routerPlan.route === 'DEEP_RESEARCH');
    const baseRoute = (0, models_1.resolveModelRoute)(routerPlan.internalModelId);
    const freeChain = forceFreeEligible
        ? routerPlan.route === 'TEXT_SIMPLE' && !routerPlan.registerClass
            ? models_1.OPENROUTER_MODELS['gs-free']
            : models_1.OPENROUTER_MODELS['gs-free-big']
        : lock === 'openrouter'
            ? models_1.OPENROUTER_MODELS[routerPlan.internalModelId] ?? models_1.OPENROUTER_MODELS['gs-free-big']
            : lock === 'zai'
                ? null
                : freeAvailable && tierFreeEligible
                    ? routerPlan.route === 'TEXT_SIMPLE' && !routerPlan.registerClass
                        ? models_1.OPENROUTER_MODELS['gs-free']
                        : models_1.OPENROUTER_MODELS['gs-free-big']
                    : null;
    const modelRoute = freeChain
        ? { backend: 'openrouter', models: freeChain }
        : baseRoute;
    const providerModel = modelRoute.backend === 'zai' ? modelRoute.providerModel : null;
    const openRouterModels = modelRoute.backend === 'openrouter' ? modelRoute.models : null;
    if (freeChain) {
        console.log(`GS-FREE-ROUTE conv=${id} route=${routerPlan.route} chain=${freeChain.join('|')} keys=${keypool.keys.length}${forceFree ? ' forced=1' : ''}${routerPlan.registerClass ? ' register=1' : ''}${lock ? ` lock=${lock}` : ''}`);
    }
    // FLASH MODE (Phase 2/4) — resolve the requested mode against the SELECTED
    // model's thinking profile WITH the turn-class overrides: TIME always
    // flash, DEEP_RESEARCH always thinking, auto resolved by the router class
    // (flash on TEXT_SIMPLE, thinking elsewhere), vision/no-thinking models
    // flash-forced, forced-thinking models remapped for Flash (GS-MODE-REMAP).
    // The mode only sets the model's thinking config — capability routing above
    // is untouched. Register and thinking are orthogonal — a register-class
    // turn with mode=thinking still thinks (the config applies identically).
    const requestedMode = (0, model_capabilities_1.parseThinkingMode)(args.mode);
    const thinking = (0, model_capabilities_1.resolveThinkingConfig)(requestedMode, routerPlan.internalModelId, routerPlan.route, cap.capability);
    if (thinking.remapNote) {
        console.log(`GS-MODE-REMAP conv=${id} ${thinking.remapNote}`);
    }
    if (openRouterModels && thinking.config) {
        console.log(`GS-MODE conv=${id} mode=${requestedMode} effective=${thinking.effective} reasoning=${thinking.config.mode === 'thinking' ? 'enabled' : 'disabled'}`);
    }
    else if (thinking.config) {
        console.log(`GS-MODE conv=${id} mode=${requestedMode} effective=${thinking.effective} thinking=${thinking.config.mode === 'thinking' ? `enabled${thinking.config.effort ? `:${thinking.config.effort}` : ''}` : 'disabled'}`);
    }
    else {
        console.log(`GS-MODE conv=${id} mode=${requestedMode} effective=provider-default (auto)`);
    }
    // The forensic log + trace writer, attached to prep (both transports share it).
    const prep = {
        conversationId: id,
        content,
        userMessage,
        conversation: { id: conversation.id, assistantId: conversation.assistantId, title: conversation.title },
        baseSystemPrompt,
        history,
        historyRowText,
        attachmentsProvided,
        attachmentIds: args.attachmentIds,
        claimedAttachments,
        imageAttachments,
        currentTurnDocs,
        useVision,
        recentImageTurnIds,
        visionPrepared: [],
        visionFailures: [],
        allImagesFailed: false,
        ocrFallbackText,
        docContext,
        allDocsFailed,
        webGate,
        previousClarifyOptions,
        clarifyFollowUp,
        historicalReligious,
        numericCheck,
        directive,
        constraints,
        clientTimezone,
        cap,
        requestId,
        turnStartedAt,
        clientVersion,
        backendRevision,
        routerPlan,
        plannerRuns,
        modelRoute,
        providerModel,
        openRouterModels,
        freeAvailable,
        requestedMode,
        thinking,
        thinkingLatencyMs: null,
        historyWebSources: [],
        includeHistoryEvidence: false,
        historyLines: [],
        runId: args.runId,
        gsCapLog: () => undefined, // attached below
    };
    prep.gsCapLog = buildGsCapLog(prep);
    // CAPABILITY REGISTRY — BLOCKED guard (verbatim; unreachable while every
    // capability has a measured model, but the honest refusal is preserved).
    if (routerPlan.route === 'BLOCKED') {
        prep.gsCapLog('blocked', null, { error: routerPlan.reason });
        console.warn(`GS-ROUTER-BLOCKED conv=${id} reason=${routerPlan.reason}`);
        return {
            kind: 'blocked',
            response: server_1.NextResponse.json({
                code: 'no_measured_model',
                message: 'This turn needs a capability no serving model is measured for yet. It was refused rather than answered badly.',
            }, { status: 503 }),
        };
    }
    let historyWebSources = [];
    let includeHistoryEvidence = false;
    if (content.trim().length > 0) {
        const recentSourceMessages = await db_1.db.message.findMany({
            where: { conversationId: id, role: 'assistant', sources: { some: {} } },
            orderBy: { createdAt: 'desc' },
            take: websearch_1.SEARCH_HISTORY_TURNS,
            include: { sources: { orderBy: { ordinal: 'asc' } } },
        });
        historyWebSources = recentSourceMessages
            .reverse()
            .flatMap((m) => m.sources.map((s) => ({
            title: s.title,
            url: s.url,
            domain: s.domain,
            snippet: s.snippet,
            query: s.query,
            publishedDate: s.publishedDate,
        })));
        includeHistoryEvidence = (0, websearch_1.shouldInjectHistoryEvidence)(content, historyWebSources.length > 0);
    }
    prep.historyWebSources = historyWebSources;
    prep.includeHistoryEvidence = includeHistoryEvidence;
    prep.historyLines = history
        .slice(-8)
        .map((m) => `${m.role.toLowerCase() === 'assistant' ? 'assistant' : 'user'}: ${historyRowText(m).slice(0, 220)}`);
    console.log(`WEBSEARCH-GATE conv=${id} trigger=${webGate.trigger ?? 'none'} planner=${plannerRuns} clarifyFollowUp=${clarifyFollowUp} historySources=${historyWebSources.length} historyInject=${includeHistoryEvidence}`);
    if (imageAttachments.length > 0) {
        // HARD CAP, and it is not theoretical: MAX_ATTACHMENTS_PER_MESSAGE lets six
        // images through and a detail crop doubles each one, so the old loop could
        // put TWELVE images in a request whose own contract says six. The provider
        // either rejects that or takes seconds longer — the exact opposite of the
        // reason the crop exists.
        //
        // Policy, in priority order:
        //   1. every image the user actually sent keeps its full frame — context is
        //      never traded away;
        //   2. history image slots are reserved when a previous image turn exists,
        //      so follow-ups still see the earlier photo;
        //   3. whatever is left buys detail crops, for the first images in order.
        const currentImages = imageAttachments.slice(0, vision_1.VISION_MAX_IMAGES_PER_REQUEST);
        const detailAllowance = (0, vision_1.detailCropAllowance)(currentImages.length, recentImageTurnIds.length > 0);
        // withDetail for the first `detailAllowance` images: a native-resolution
        // crop of the densest region (see VISION_DETAIL_* in vision.ts) — that is
        // what makes small printed text legible to the model instead of mush.
        const prepared = await (0, concurrency_1.mapWithConcurrency)(currentImages, 2, (att, i) => (0, vision_1.prepareVisionImage)(att, { withDetail: i < detailAllowance }));
        for (const result of prepared) {
            if (result.ok) {
                prep.visionPrepared.push(result.image);
                if (result.detail)
                    prep.visionPrepared.push(result.detail);
            }
            else {
                prep.visionFailures.push(result.failure);
            }
        }
        if (prep.visionPrepared.length === 0) {
            prep.allImagesFailed = true;
        }
    }
    // Catalogue telemetry — count a use when this is the first message.
    if (conversation.assistantId && history.length === 0) {
        db_1.db.assistant
            .update({ where: { id: conversation.assistantId }, data: { uses: { increment: 1 } } })
            .catch(() => undefined);
    }
    return { kind: 'ready', prep };
}
/** §14 forensic log + trace row — shared by both transports. */
function buildGsCapLog(prep) {
    const { requestId, conversationId: id, userMessage, clientVersion, backendRevision, cap, modelRoute, routerPlan } = prep;
    return (finalStatus, turn, extra) => {
        const sourceCount = turn?.kind === 'research'
            ? turn.outcome.sources.length
            : turn?.kind === 'reuse'
                ? turn.sources.length
                : 0;
        const evidenceCount = turn?.kind === 'research' && turn.outcome.ok
            ? turn.maxOrdinal
            : turn?.kind === 'reuse'
                ? turn.sources.length
                : turn?.kind === 'time'
                    ? 1
                    : 0;
        console.log(`GS-CAP requestId=${requestId} conv=${id} msg=${userMessage.id} clientVersion=${clientVersion} backendRevision=${backendRevision} capability=${cap.capability} trigger=${cap.trigger ?? 'none'} searchExecuted=${turn?.kind === 'research'} researchExecuted=${turn?.kind === 'research'} evidenceCount=${evidenceCount} sourceCount=${sourceCount} modelRoute=${modelRoute.backend}/${routerPlan.internalModelId} mode=${prep.requestedMode} effective=${prep.thinking.effective} thinkingMs=${prep.thinkingLatencyMs !== null ? prep.thinkingLatencyMs : 'null'} finalStatus=${finalStatus}${extra?.cited ? ` cited=[${extra.cited.join(',')}]` : ''}`);
        const freshSources = turn?.kind === 'research' ? turn.outcome.sources : [];
        const reuseSources = turn?.kind === 'reuse' ? turn.sources : [];
        const gateSourcesRead = turn?.kind === 'research'
            ? freshSources.filter((s) => s.status === 'retrieved').length
            : turn?.kind === 'reuse'
                ? turn.sources.length
                : 0;
        const gateVerdict = (0, silent_failure_gate_1.runSilentFailureGate)({
            turnKind: (turn?.kind ?? 'none'),
            finalStatus,
            searchExecuted: turn?.kind === 'research',
            noResults: turn?.kind === 'research' &&
                (turn.outcome.failure?.kind === 'no_results' ||
                    (!turn.outcome.ok && turn.outcome.sources.length === 0)),
            registerClass: routerPlan.registerClass,
            sourceCount,
            sourcesRead: gateSourcesRead,
            sourcesFailed: freshSources.filter((s) => s.status === 'failed').length,
            evidenceCount,
            citedOrdinals: extra?.cited ? Array.from(new Set(extra.cited)) : [],
            freshSourceStatuses: turn?.kind === 'research' ? freshSources.map((s) => s.status) : [],
            historySourceCount: turn?.kind === 'research'
                ? prep.includeHistoryEvidence
                    ? prep.historyWebSources.length
                    : 0
                : turn?.kind === 'reuse'
                    ? turn.sources.length
                    : 0,
            evidenceBlock: turn?.kind === 'research'
                ? turn.evidenceBlock
                : turn?.kind === 'reuse'
                    ? turn.evidenceBlock
                    : null,
            answerText: extra?.answer ?? null,
        });
        if (!gateVerdict.ok) {
            console.error(`GS-SILENT-FAILURE requestId=${requestId} conv=${id} msg=${userMessage.id} capability=${cap.capability} modelRoute=${modelRoute.backend}/${routerPlan.internalModelId} finalStatus=${finalStatus} violations=[${gateVerdict.violations.join(' | ')}]`);
        }
        void (0, trace_1.writeTurnTrace)({
            requestId,
            conversationId: id,
            messageId: userMessage.id,
            clientVersion,
            backendRevision,
            capability: cap.capability,
            trigger: cap.trigger ?? 'none',
            searchExecuted: turn?.kind === 'research',
            researchExecuted: turn?.kind === 'research',
            evidenceCount,
            sourceCount,
            sourcesRead: freshSources.filter((s) => s.status === 'retrieved').length,
            sourcesFailed: freshSources.filter((s) => s.status === 'failed').length,
            domains: Array.from(new Set([...freshSources, ...reuseSources].map((s) => s.domain).filter((d) => d.length > 0))),
            modelRoute: `${modelRoute.backend}/${routerPlan.internalModelId}`,
            finalStatus,
            cited: extra?.cited ?? [],
            latencyMs: Date.now() - prep.turnStartedAt,
            errorType: finalStatus === 'done' || finalStatus === 'clarify'
                ? null
                : (0, trace_1.classifyErrorType)(extra?.error ?? null),
            silentFailures: gateVerdict.violations,
            // FLASH MODE (Phase 5) — the mode dimension rides the trace row.
            requestedMode: prep.requestedMode,
            effectiveMode: prep.thinking.effective,
            thinkingLatencyMs: prep.thinkingLatencyMs,
            modeNote: prep.thinking.remapNote,
        });
    };
}
// ---------------------------------------------------------------------------
// RUN — the bounded capability turn (verbatim move of the route body)
// ---------------------------------------------------------------------------
const MAX_GUARD_ATTEMPTS = 3;
async function runTurn(prep, push) {
    const id = prep.conversationId;
    const content = prep.content;
    const userMessage = prep.userMessage;
    const cap = prep.cap;
    const routerPlan = prep.routerPlan;
    const modelRoute = prep.modelRoute;
    const providerModel = prep.providerModel;
    const openRouterModels = prep.openRouterModels;
    const freeAvailable = prep.freeAvailable;
    const gsCapLog = prep.gsCapLog;
    const history = prep.history;
    const historyRowText = prep.historyRowText;
    const docContext = prep.docContext;
    const useVision = prep.useVision;
    const numericCheck = prep.numericCheck;
    const directive = prep.directive;
    const constraints = prep.constraints;
    const clientTimezone = prep.clientTimezone;
    const allImagesFailed = prep.allImagesFailed;
    const allDocsFailed = prep.allDocsFailed;
    const historyWebSources = prep.historyWebSources;
    const includeHistoryEvidence = prep.includeHistoryEvidence;
    const historyLines = prep.historyLines;
    // FLASH-MODE BUG 1 (server side) — the FIRST event of every streamed turn
    // carries the resolved effective mode: {"state":"streaming","effectiveMode":
    // "flash"|"thinking","requested":...}. Clients render "Thinking…" ONLY for
    // effectiveMode=thinking; flash shows a neutral indicator. The existing
    // string-typed `status` events are untouched (all three clients parse those
    // as plain strings); unknown events are ignored by every parser (web
    // else-if chain, Android `when` without else, iOS switch without default).
    if (push) {
        push('mode', JSON.stringify({ state: 'streaming', effectiveMode: prep.thinking.effective, requested: prep.requestedMode }));
    }
    // PHASE 5 stop condition — a cancelled run synthesizes immediately with
    // whatever the ledger already holds (checked between search phases).
    const runStopFlag = (runId) => {
        // synchronous flag cache refreshed by a background read; DB probes are
        // only made between branches, never inside hot loops
        return stopFlagCache.get(runId) === true;
    };
    const stopFlagCache = new Map();
    if (prep.runId) {
        const runId = prep.runId;
        void (async () => {
            for (;;) {
                await new Promise((r) => setTimeout(r, 2000));
                try {
                    const run = await (0, run_store_1.getRun)(runId);
                    if (!run)
                        return;
                    stopFlagCache.set(runId, run.status === 'cancelled');
                    if (run.status === 'cancelled')
                        return;
                }
                catch {
                    return;
                }
            }
        })();
    }
    /** §9 — record every executed research as an explicit ResearchContext. */
    const recordContext = (outcome) => {
        const researchId = `rc-${userMessage.id.slice(0, 8)}-${Date.now().toString(36)}`;
        (0, research_context_1.recordResearchContext)(id, {
            researchId,
            originatingMessageId: userMessage.id,
            originatingUserRequest: content.trim(),
            queries: outcome.queriesRun,
            sources: outcome.sources
                .filter((s) => s.status === 'retrieved' || s.status === 'snippet_only')
                .map((s) => ({
                ordinal: s.ordinal,
                title: s.title,
                url: s.url,
                domain: s.domain,
                snippet: s.snippet,
                query: s.query,
                status: s.status,
                publishedDate: s.publishedDate,
            })),
            citations: [],
            createdAt: Date.now(),
            status: outcome.ok ? 'complete' : 'failed',
        });
        return researchId;
    };
    /** Shared execution tail for every search execution (planner or code-driven). */
    const runSearchExecution = async (params, emit, startedAt) => {
        emit?.('research', {
            type: 'started',
            intent: params.intent,
            depth: params.depth,
            roundsPlanned: research_1.RESEARCH_BUDGET[params.depth].maxRounds,
        });
        emit?.('search', { type: 'started', intent: params.intent, depth: params.depth, label: params.queries[0] ?? '' });
        // PHASE 5 — LEDGER-DRIVEN BRANCHING SEARCH for deep turns: the question
        // is decomposed into sub-questions, each runs as a parallel branch, and
        // the Research Ledger (claims/gaps/contradictions) drives EAQL follow-up
        // queries. Quick turns keep the proven linear pipeline. GS_LEDGER_SEARCH
        // =off reverts to linear (instant rollback). Output type is identical —
        // evidence assembly and synthesis are untouched.
        const useLedgerSearch = params.depth === 'deep' && process.env.GS_LEDGER_SEARCH !== 'off';
        const deadlineAt = Date.now() + research_1.RESEARCH_BUDGET[params.depth].wallClockMs + 2000;
        const outcome = useLedgerSearch
            ? await (0, branching_1.runBranchingResearch)({
                question: content,
                queries: params.queries,
                intent: params.intent,
                timeRange: params.timeRange,
                region: params.region,
                sourceHint: params.sourceHint,
                officialOnly: params.officialOnly,
                depth: params.depth,
                emit: emit ?? types_1.NOOP_EMIT,
                deadlineAt,
                // prep.requestId, not a bare `requestId`: the legacy inline path runs
                // without a runId, and `prep.runId ?? requestId` was a ReferenceError
                // there (the identifier does not exist in this scope) — a deep search
                // would have died before its first query.
                ledgerKey: prep.runId ?? prep.requestId,
                isStopped: prep.runId
                    ? () => {
                        // stop condition: user cancelled the run → synthesize now
                        return runStopFlag(prep.runId);
                    }
                    : undefined,
            })
            : await (0, research_1.runResearch)({
                queries: params.queries,
                intent: params.intent,
                timeRange: params.timeRange,
                region: params.region,
                sourceHint: params.sourceHint,
                officialOnly: params.officialOnly,
                depth: params.depth,
                emit: emit ?? types_1.NOOP_EMIT,
                deadlineAt,
            });
        console.log(`RESEARCH conv=${id} intent=${params.intent} queries=${outcome.queriesRun.length} sources=${outcome.sources.length} retrieved=${outcome.sources.filter((s) => s.status === 'retrieved').length} engines=${outcome.enginesUsed.join('+') || 'none'} ok=${outcome.ok} kind=${outcome.failure?.kind ?? '-'}${useLedgerSearch ? ' ledger=on' : ''}`);
        const researchId = recordContext(outcome);
        if (!outcome.ok || outcome.sources.length === 0) {
            emit?.('search', { type: 'failed', reason: outcome.failure?.kind ?? 'no_results' });
            emit?.('research', {
                type: 'failed',
                reason: outcome.failure?.kind ?? 'no_results',
                message: outcome.failure?.message,
            });
            return { kind: 'research', outcome, evidenceBlock: null, maxOrdinal: 0, historySources: [], startedAt, researchId };
        }
        const { block, maxOrdinal } = (0, research_1.buildResearchEvidenceBlock)(outcome.sources, outcome.queriesRun);
        return { kind: 'research', outcome, evidenceBlock: block, maxOrdinal, historySources: [], startedAt, researchId };
    };
    const executeSearchPhase = async (emit) => {
        if (allImagesFailed || allDocsFailed)
            return { kind: 'none' };
        if (cap.capability === 'TIME') {
            console.log(`GS-TIME conv=${id} tz=${(0, capability_1.validTimeZone)(clientTimezone) ?? 'UTC'}`);
            return { kind: 'time', evidence: (0, evidence_1.buildTimeEvidence)(clientTimezone) };
        }
        if (cap.reuseResearch) {
            const ctx = (0, research_context_1.findReferencedResearchContext)(content, (0, research_context_1.getResearchContexts)(id));
            if (ctx) {
                const reuse = (0, research_context_1.buildReuseEvidenceBlock)(ctx);
                console.log(`RESEARCH-REUSE conv=${id} ctx=${ctx.researchId} sources=${reuse.sources.length} trigger=${cap.trigger} request="${content.slice(0, 60)}"`);
                return {
                    kind: 'reuse',
                    ctx,
                    evidenceBlock: reuse.block,
                    sources: reuse.sources.map((s) => ({
                        ordinal: s.ordinal,
                        id: s.url,
                        title: s.title,
                        url: s.url,
                        domain: s.domain,
                        snippet: s.snippet,
                        publishedDate: s.publishedDate,
                        retrievedAt: new Date().toISOString(),
                        query: s.query,
                    })),
                    researchId: ctx.researchId,
                };
            }
            console.log(`RESEARCH-REUSE conv=${id} trigger=${cap.trigger} requested but no stored research context — plain chat`);
            return { kind: 'none', note: 'reuse requested but no research context exists' };
        }
        if (!prep.plannerRuns)
            return { kind: 'none' };
        emit?.('status', 'searching');
        if (cap.trigger === 'research_again') {
            const prior = (0, research_context_1.getResearchContexts)(id)
                .slice()
                .reverse()
                .find((c) => c.status === 'complete' && c.sources.length > 0);
            if (prior) {
                const derived = (0, websearch_1.extractSearchQuery)(prior.originatingUserRequest, 'explicit');
                const topic = derived.length >= 3 ? derived : prior.queries[0] ?? prior.originatingUserRequest;
                const queries = cap.policy.primarySources
                    ? [topic, `${topic} primary sources`, `${topic} original text`]
                    : [topic, ...prior.queries.slice(0, 1)];
                const againDepth = routerPlan.depth === 'deep' ? 'deep' : 'quick';
                console.log(`RESEARCH-AGAIN conv=${id} prior=${prior.researchId} topic="${topic.slice(0, 60)}" primarySources=${cap.policy.primarySources === true}`);
                return await runSearchExecution({
                    queries: queries.filter((q) => q.trim().length >= 3).slice(0, 4),
                    intent: 'research',
                    timeRange: 'none',
                    sourceHint: cap.policy.sourceHint,
                    officialOnly: cap.policy.officialOnly,
                    depth: againDepth,
                }, emit, Date.now());
            }
            console.log(`RESEARCH-AGAIN conv=${id} no prior research context — falling back to the planner`);
        }
        const plan = await (0, planner_1.planSearch)({
            userText: content,
            historyLines,
            previousClarifyOptions: prep.previousClarifyOptions,
        });
        console.log(`SEARCH-PLAN conv=${id} intent=${plan.intent} needs=${plan.needsSearch} ambiguous=${plan.ambiguity.isAmbiguous} queries=[${plan.queries.join(' | ')}] range=${plan.timeRange} depth=${plan.depth} stop=${plan.stopSignal} forced=${cap.searchPlanned}`);
        if (plan.ambiguity.isAmbiguous && plan.ambiguity.prompt && !cap.searchPlanned) {
            return { kind: 'clarify', question: plan.ambiguity.prompt.question, options: plan.ambiguity.prompt.options };
        }
        if ((!plan.needsSearch || plan.stopSignal) && !cap.searchPlanned)
            return { kind: 'none' };
        if (cap.searchPlanned) {
            plan.needsSearch = true;
            plan.ambiguity = { isAmbiguous: false, prompt: null };
            plan.stopSignal = false;
            if (cap.policy.sourceHint)
                plan.sourceHint = cap.policy.sourceHint;
            if (cap.policy.officialOnly)
                plan.officialOnly = true;
            if (cap.policy.timeRange)
                plan.timeRange = cap.policy.timeRange;
            if (plan.queries.length === 0) {
                const q = prep.webGate.trigger !== null
                    ? (0, websearch_1.extractSearchQuery)(content, prep.webGate.trigger)
                    : content.replace(/\s+/g, ' ').trim().slice(0, 120);
                plan.queries = [q].filter((x) => x.length >= 3);
            }
            if (plan.queries.length === 0)
                return { kind: 'none' };
        }
        const depth = plan.depth === 'deep' || routerPlan.depth === 'deep' ? 'deep' : 'quick';
        return await runSearchExecution({
            queries: plan.queries,
            intent: plan.intent,
            timeRange: plan.timeRange,
            region: plan.region,
            sourceHint: plan.sourceHint,
            officialOnly: plan.officialOnly,
            depth,
        }, emit, Date.now());
    };
    const buildModelMessages = async (turn) => {
        // PHASE 7 — CONTEXT-NOISE MITIGATION: when the working context saturates,
        // the oldest history is crystallized into exact event snapshots (numbers
        // byte-exact) + dual-path retrieval recovers matching nodes for this
        // question. Short conversations are untouched (no-op).
        const crystallization = await (0, context_crystallize_1.crystallizeIfNeeded)(id, history, content);
        const effectiveHistory = crystallization.keepFromIndex > 0 ? history.slice(crystallization.keepFromIndex) : history;
        const researchOk = turn.kind === 'research' && turn.outcome.ok && turn.evidenceBlock !== null;
        const freshCount = researchOk ? turn.maxOrdinal : 0;
        const historyBlock = includeHistoryEvidence
            ? (0, websearch_1.buildHistoryEvidenceBlock)(historyWebSources, freshCount + 1)
            : null;
        const historySources = historyBlock?.sources ?? [];
        let toolResultBlock = null;
        if (turn.kind === 'time')
            toolResultBlock = turn.evidence.block;
        else if (turn.kind === 'reuse')
            toolResultBlock = turn.evidenceBlock;
        else if (turn.kind === 'research') {
            toolResultBlock = researchOk
                ? (0, evidence_1.buildResearchToolResultBlock)(turn.outcome)
                : (0, evidence_1.buildResearchFailureToolResultBlock)(turn.outcome);
        }
        const systemPrompt = prep.baseSystemPrompt;
        const verbatimRequest = content.trim().length > 0
            ? content
            : prep.currentTurnDocs.length > 0
                ? document_1.DOCUMENT_ONLY_DEFAULT
                : useVision
                    ? prep.ocrFallbackText
                        ? 'Read the image text provided above and answer.'
                        : 'Describe this image.'
                    : '';
        const evidenceBlocks = [
            crystallization.block,
            docContext.block,
            historyBlock?.block ?? null,
            toolResultBlock,
            // TASK 3 — OCR sidecar evidence. Present ONLY when the image bytes
            // could not be sent (allImagesFailed); the prefix is the operator's
            // exact contract wording, so the model reads it as text, not a
            // description, and never as an invitation to guess.
            prep.ocrFallbackText
                ? `${ai_1.VISION_OCR_FALLBACK_PREFIX}\n\n${prep.ocrFallbackText}`
                : null,
        ];
        if (useVision && !allImagesFailed) {
            const textPayload = content.trim().length > 0 ? content : 'Describe this image.';
            const notes = [];
            if (prep.visionFailures.length > 0) {
                notes.push(...prep.visionFailures.map(vision_1.visionFailureMessage));
            }
            const finalText = notes.length > 0 ? `${textPayload}\n\n(${notes.join(' ')})` : textPayload;
            const selectedTurnIds = new Set(prep.recentImageTurnIds);
            // Clamped: with detail crops the prepared list can reach the cap, and a
            // negative budget is meaningless (it made the history branch below look
            // like it had room in logs while it silently skipped every image).
            let imageBudget = Math.max(0, vision_1.VISION_MAX_IMAGES_PER_REQUEST - prep.visionPrepared.length);
            const visionContext = [
                { role: 'system', content: `${systemPrompt}\n\n${ai_1.VISION_GROUNDING_PROMPT}` },
            ];
            for (const m of effectiveHistory) {
                if (m.role.toLowerCase() === 'user' && selectedTurnIds.has(m.id) && imageBudget > 0) {
                    const turnImages = m.attachments.filter((a) => a.kind === 'image');
                    const parts = [
                        { type: 'text', text: m.content.trim().length > 0 ? m.content : 'Describe this image.' },
                    ];
                    for (const a of turnImages) {
                        if (imageBudget <= 0)
                            break;
                        const result = await (0, vision_1.prepareVisionImage)(a);
                        if (result.ok) {
                            parts.push({ type: 'image_url', image_url: { url: result.image.dataUrl } });
                            imageBudget -= 1;
                        }
                    }
                    visionContext.push({ role: 'user', content: parts });
                }
                else {
                    visionContext.push({
                        role: m.role.toLowerCase() === 'assistant' ? 'assistant' : 'user',
                        content: historyRowText(m),
                    });
                }
            }
            const presentEvidence = evidenceBlocks.filter((b) => !!b);
            const visionUserParts = [
                ...(presentEvidence.length > 0
                    ? [{ type: 'text', text: `${presentEvidence.join('\n\n')}\n\n${evidence_1.TOOL_RESULT_END_MARKER}` }]
                    : []),
                { type: 'text', text: finalText },
                // Full frame first, then its detail crop labelled as the same photo —
                // never as a second image the user sent.
                ...prep.visionPrepared.flatMap((p) => p.role === 'detail'
                    ? [
                        { type: 'text', text: vision_1.VISION_DETAIL_CROP_NOTE },
                        { type: 'image_url', image_url: { url: p.dataUrl } },
                    ]
                    : [{ type: 'image_url', image_url: { url: p.dataUrl } }]),
            ];
            visionContext.push({ role: 'user', content: visionUserParts });
            const reuseSources = turn.kind === 'reuse' ? turn.sources : [];
            return {
                messages: visionContext,
                systemPrompt,
                historySources: reuseSources.length > 0 ? reuseSources : historySources,
                userTurnText: finalText,
            };
        }
        let finalUserContent = (0, evidence_1.buildFinalUserTurn)({ verbatim: verbatimRequest, blocks: evidenceBlocks });
        if (prep.attachmentsProvided && evidenceBlocks.every((b) => !b)) {
            finalUserContent = `${finalUserContent}${finalUserContent.length > 0 ? '\n\n' : ''}[The user attached ${prep.claimedAttachments
                .map((a) => a.displayName)
                .join(', ')}. Attachment contents could not be read.]`;
        }
        const reuseSources = turn.kind === 'reuse' ? turn.sources : [];
        return {
            messages: [
                { role: 'system', content: systemPrompt },
                ...effectiveHistory.map((m) => ({ role: m.role.toLowerCase(), content: historyRowText(m) })),
                { role: 'user', content: finalUserContent },
            ],
            systemPrompt,
            historySources: reuseSources.length > 0 ? reuseSources : historySources,
            userTurnText: finalUserContent,
        };
    };
    const sanitizeAgainstPersisted = (text, turn, historySources) => {
        const freshCount = turn.kind === 'research' && turn.outcome.ok ? turn.maxOrdinal : 0;
        const totalOrdinals = freshCount + historySources.length;
        if (totalOrdinals === 0)
            return text;
        const allowed = new Set();
        if (turn.kind === 'research' && turn.outcome.ok) {
            for (const s of turn.outcome.sources) {
                if (s.status === 'retrieved' || s.status === 'snippet_only')
                    allowed.add(s.ordinal);
            }
        }
        for (const n of (0, websearch_1.parseCitedOrdinals)(text, totalOrdinals)) {
            if (n > freshCount)
                allowed.add(n);
        }
        return (0, websearch_1.sanitizeCitationMarkers)(text, totalOrdinals, allowed);
    };
    const persistTurnSources = async (messageId, answerText, turn, historySources) => {
        const fresh = turn.kind === 'research' && turn.outcome.ok ? turn.outcome.sources : [];
        const maxOrdinal = fresh.length + historySources.length;
        if (maxOrdinal === 0)
            return;
        const cited = (0, websearch_1.parseCitedOrdinals)(answerText, maxOrdinal);
        const citedSet = new Set(cited);
        const rows = [
            ...fresh
                .filter((s) => s.status === 'retrieved' || s.status === 'snippet_only')
                .map((s) => ({
                messageId,
                ordinal: s.ordinal,
                title: s.title.slice(0, 500),
                url: s.url,
                domain: s.domain.slice(0, 200),
                snippet: (s.snippet || '').slice(0, 600),
                ...(s.publishedDate ? { publishedDate: s.publishedDate.slice(0, 100) } : {}),
                query: s.query.slice(0, 300),
                status: s.status,
                used: citedSet.has(s.ordinal),
                rank: s.searchRank,
            })),
            ...historySources
                .filter((s) => citedSet.has(s.ordinal))
                .map((s) => ({
                messageId,
                ordinal: s.ordinal,
                title: s.title.slice(0, 500),
                url: s.url,
                domain: s.domain.slice(0, 200),
                snippet: (s.snippet || '').slice(0, 600),
                ...(s.publishedDate ? { publishedDate: s.publishedDate.slice(0, 100) } : {}),
                query: s.query.slice(0, 300),
                status: 'snippet_only',
                used: true,
                rank: null,
            })),
        ];
        if (rows.length > 0) {
            await db_1.db.messageSource.createMany({ data: rows });
        }
        console.log(`WEBSEARCH-CITED conv=${id} maxOrdinal=${maxOrdinal} cited=[${cited.join(',')}] persisted=${rows.length}`);
    };
    const visionErrorMessage = (raw) => {
        if ((0, vision_1.isProviderImageRejection)(raw)) {
            return 'The image could not be processed — try a JPG, PNG or WebP version.';
        }
        return 'Image understanding is unavailable right now. Please try again.';
    };
    const userFacingTurnError = (raw) => {
        console.error(`TURN-ERROR conv=${id}: ${raw.slice(0, 300)}`);
        return 'GS AI is temporarily unavailable. Please try again shortly.';
    };
    const synthesizeRaw = async (modelMessages, onDelta) => {
        // TASK 3 — the vision endpoint is used only when images actually ride the
        // request. An OCR-fallback turn (allImagesFailed, OCR text present) is a
        // TEXT turn end to end: text-shaped messages, text model, flash speed.
        if (useVision && !allImagesFailed) {
            return onDelta
                ? (await (0, ai_1.streamVisionChat)(modelMessages, onDelta)).text
                : (await (0, ai_1.completeVisionChat)(modelMessages)).text;
        }
        const msgs = modelMessages;
        // FLASH MODE (Phase 2) — the resolved thinking decision rides EVERY text
        // call path, including provider fallbacks. The effective zai model is the
        // remap target when a forced-thinking model was remapped for Flash.
        const activeProviderModel = prep.thinking.providerModelOverride ?? providerModel;
        const orReasoning = prep.thinking.config
            ? { enabled: prep.thinking.config.mode === 'thinking' }
            : null;
        // thinkingLatencyMs — TTFT of the thinking path (measured at this layer:
        // call start → first forwarded delta). Buffered calls never set it.
        const thinkingOn = prep.thinking.config?.mode === 'thinking';
        let callStartedAt = Date.now();
        let ttftMarked = false;
        // The forwarder exists only when a client is listening AND thinking is on
        // AND the call is streaming. flush() after each await: an unflushed tail
        // would leave the client's panel missing its last words at `done`.
        const reasoningForward = onDelta && thinkingOn && push ? makeReasoningForwarder(push) : null;
        const wrappedOnDelta = onDelta
            ? (d) => {
                if (thinkingOn && !ttftMarked && prep.thinkingLatencyMs === null) {
                    ttftMarked = true;
                    prep.thinkingLatencyMs = Date.now() - callStartedAt;
                }
                return onDelta(d);
            }
            : undefined;
        // GS-CONSTRAINT-GUARD (2026-10-08): a 200-with-empty-body from ANY layer
        // must not ship as a bubble. Both measured failure shapes (OR chain 429
        // exhaustion, primary empty completion under throttle) now fail the call
        // so the fallback ladder or the route's honest error path handles it.
        const emptyRejection = (where) => new Error(`${where}: empty completion (upstream returned no content)`);
        try {
            if (openRouterModels) {
                callStartedAt = Date.now();
                const text = wrappedOnDelta
                    ? await (0, openrouter_1.orStreamChat)(msgs, openRouterModels, wrappedOnDelta, orReasoning, reasoningForward?.onDelta)
                    : await (0, openrouter_1.orCompleteChat)(msgs, openRouterModels, orReasoning).then((r) => r.text);
                reasoningForward?.flush();
                if (!text.trim())
                    throw emptyRejection('OpenRouter chain');
                return text;
            }
            callStartedAt = Date.now();
            const primaryText = wrappedOnDelta
                ? await (0, ai_1.streamChat)(msgs, wrappedOnDelta, activeProviderModel, prep.thinking.config, reasoningForward?.onDelta)
                : await (0, ai_1.completeChat)(msgs, activeProviderModel, prep.thinking.config);
            reasoningForward?.flush();
            if (!primaryText.trim())
                throw emptyRejection('primary provider');
            return primaryText;
        }
        catch (e) {
            const message = e instanceof Error ? e.message : String(e);
            if (openRouterModels) {
                if (message.startsWith('OpenRouter ')) {
                    console.log(`SYNTHESIS-FALLBACK conv=${id} OpenRouter chain exhausted (${message.slice(0, 80)}) → primary provider`);
                    callStartedAt = Date.now();
                    const fb = wrappedOnDelta
                        ? await (0, ai_1.streamChat)(msgs, wrappedOnDelta, activeProviderModel, prep.thinking.config, reasoningForward?.onDelta)
                        : await (0, ai_1.completeChat)(msgs, activeProviderModel, prep.thinking.config);
                    reasoningForward?.flush();
                    if (!fb.trim())
                        throw emptyRejection('primary provider (fallback)');
                    return fb;
                }
                throw e;
            }
            if (freeAvailable) {
                console.log(`SYNTHESIS-FALLBACK conv=${id} primary failed (${message.slice(0, 80)}) → OpenRouter free chain`);
                callStartedAt = Date.now();
                const fb = wrappedOnDelta
                    ? await (0, openrouter_1.orStreamChat)(msgs, models_1.OPENROUTER_MODELS['gs-free'], wrappedOnDelta, orReasoning, reasoningForward?.onDelta)
                    : await (0, openrouter_1.orCompleteChat)(msgs, models_1.OPENROUTER_MODELS['gs-free'], orReasoning).then((r) => r.text);
                reasoningForward?.flush();
                if (!fb.trim())
                    throw emptyRejection('OpenRouter free chain (fallback)');
                return fb;
            }
            throw e;
        }
    };
    const executionStateFor = (turn) => ({
        capability: cap.capability,
        searchExecuted: turn.kind === 'research',
        evidenceProvided: turn.kind === 'research' && turn.outcome.ok && turn.evidenceBlock !== null,
        timeProvided: turn.kind === 'time',
        documentProvided: !!docContext.block,
        reuseProvided: turn.kind === 'reuse',
    });
    const applyCorrection = (messages, userTurnText, correction) => {
        const correctedText = (0, synthesis_guard_1.injectCorrection)(userTurnText, correction);
        const next = [...messages];
        for (let i = next.length - 1; i >= 0; i--) {
            if (next[i].role !== 'user')
                continue;
            const m = next[i];
            if (typeof m.content === 'string') {
                next[i] = { ...m, content: correctedText };
            }
            else if (Array.isArray(m.content)) {
                next[i] = { ...m, content: [...m.content, { type: 'text', text: correction }] };
            }
            break;
        }
        return next;
    };
    const synthesizeValidated = async (turn, modelMessages, userTurnText) => {
        const state = executionStateFor(turn);
        let activeMessages = modelMessages;
        let activeUserTurn = userTurnText;
        let last = { text: '', violation: null };
        for (let attempt = 1; attempt <= MAX_GUARD_ATTEMPTS; attempt++) {
            const text = await synthesize(activeMessages);
            const violation = (0, synthesis_guard_1.detectExecutionContradiction)(text, state);
            last = { text, violation };
            if (!violation)
                return last;
            console.log(`GS-GUARD conv=${id} attempt=${attempt}/${MAX_GUARD_ATTEMPTS} violation=${violation.kind} match="${violation.match.slice(0, 70)}"`);
            if (attempt === MAX_GUARD_ATTEMPTS)
                break;
            const correction = (0, synthesis_guard_1.buildCorrectionInstruction)(state, violation, attempt);
            activeMessages = applyCorrection(activeMessages, activeUserTurn, correction);
            activeUserTurn = (0, synthesis_guard_1.injectCorrection)(activeUserTurn, correction);
        }
        return last;
    };
    /** Flush a validated full text as delta events (buffered-synthesis path). */
    const chunkDeltas = (text) => {
        const size = 140;
        const chunks = [];
        for (let i = 0; i < text.length; i += size)
            chunks.push(text.slice(i, i + size));
        return chunks.length > 0 ? chunks : [''];
    };
    // REASONING TRACE (chat-reasoning panel) — thinking-enabled STREAM calls
    // forward the provider's reasoning deltas as `reasoning` events, batched
    // (raw deltas outpace the SSE fan-out). Clients that predate the event
    // ignore it by contract (web else-if chain, Android when-without-else,
    // iOS switch-without-default). Buffered paths carry no reasoning — the
    // provider never streams one there to forward.
    const makeReasoningForwarder = (pushFn) => {
        let buf = '';
        let lastFlush = 0;
        const onDelta = (t) => {
            buf += t;
            const now = Date.now();
            if (now - lastFlush >= 300) {
                pushFn('reasoning', JSON.stringify({ delta: buf }));
                buf = '';
                lastFlush = now;
            }
        };
        const flush = () => {
            if (buf) {
                pushFn('reasoning', JSON.stringify({ delta: buf }));
                buf = '';
            }
            lastFlush = Date.now();
        };
        return { onDelta, flush };
    };
    const emit = push
        ? (event, payload) => {
            push(event, JSON.stringify(payload));
            // DURABLE EXECUTION — source-level facts ride the event log when the
            // turn is a run (SourceOpened / SourceRead from the REAL wire events).
            if (prep.runId && event === 'source') {
                const p = payload;
                if (p.type === 'opening')
                    void (0, turn_events_1.appendEvent)(prep.runId, 'SourceOpened', { ordinal: p.ordinal });
                if (p.type === 'read')
                    void (0, turn_events_1.appendEvent)(prep.runId, 'SourceRead', { ordinal: p.ordinal, chars: p.chars ?? null });
            }
        }
        : null;
    // DURABLE EXECUTION — every model call, tool call, and side effect is
    // appended to the log BEFORE and AFTER it happens (run-scoped only; the
    // legacy inline path runs without a runId and logs nothing).
    let llmStep = 0;
    const synthesize = async (modelMessages, onDelta) => {
        if (!prep.runId)
            return synthesizeRaw(modelMessages, onDelta);
        return (0, turn_events_1.tracked)(prep.runId, { request: 'LLMCallRequested', completed: 'LLMCallCompleted', failed: 'LLMCallFailed' }, { step: ++llmStep, kind: useVision ? 'vision' : 'text' }, () => synthesizeRaw(modelMessages, onDelta));
    };
    const executeSearchLogged = async (emit) => {
        if (!prep.runId)
            return executeSearchPhase(emit);
        return (0, turn_events_1.tracked)(prep.runId, { request: 'SearchRequested', completed: 'SearchCompleted', failed: 'ToolCallFailed' }, { capability: cap.capability }, () => executeSearchPhase(emit));
    };
    /** The ONE side effect of a turn: persisting the assistant answer. */
    const createAssistantMessage = async (data) => {
        if (!prep.runId)
            return db_1.db.message.create({ data });
        return (0, turn_events_1.tracked)(prep.runId, { request: 'ToolCallRequested', completed: 'ToolCallCompleted', failed: 'ToolCallFailed' }, { tool: 'persist_assistant_message', userMessageId: prep.userMessage.id }, async () => {
            // Idempotency across restarts: if a prior attempt already persisted
            // this run's answer, reuse it — one answer exists, not two.
            const run = await (0, run_store_1.getRun)(prep.runId);
            if (run?.assistantMessageId) {
                const existing = await db_1.db.message.findUnique({ where: { id: run.assistantMessageId } });
                if (existing)
                    return existing;
            }
            return db_1.db.message.create({ data });
        }, { tool: 'persist_assistant_message' });
    };
    const result = { finalStatus: 'error', userMessageId: userMessage.id };
    // §3/§16 — pre-turn honesty gates (verbatim from the route): all images or
    // all documents unreadable is a 422-class outcome, never a fabricated answer.
    // TASK 3 contract: with an OCR sidecar the turn STILL PROCEEDS (text path,
    // OCR text as evidence). With neither bytes nor OCR the reply is the
    // deterministic, honest refusal below — no model call, no guessing.
    if (allImagesFailed && !prep.ocrFallbackText) {
        const reply = 'I cannot read this image.';
        console.log(`GS-VISION-REFUSAL conv=${id} msg=${userMessage.id} reasons=${prep.visionFailures
            .map((f) => f.reason)
            .join(',') || 'unknown'} — deterministic refusal, no model call`);
        if (push)
            for (const chunk of chunkDeltas(reply))
                push('delta', chunk);
        const saved = await createAssistantMessage({
            conversationId: id,
            role: 'assistant',
            content: reply,
        });
        gsCapLog('done', null);
        if (push)
            push('done', JSON.stringify((0, serializers_1.messageToJson)(saved)));
        result.finalStatus = 'done';
        result.assistantMessageId = saved.id;
        result.savedMessage = (0, serializers_1.messageToJson)(saved);
        return result;
    }
    if (allDocsFailed) {
        gsCapLog('error', null, { error: 'all document attachments unreadable' });
        const message = docContext.failures.map(document_1.documentFailureMessage).join(' ');
        if (push)
            push('error', message);
        result.finalStatus = 'error';
        result.errorRaw = 'all document attachments unreadable';
        result.errorResponse = { status: 422, code: 'document_unreadable', message };
        return result;
    }
    // ---- the turn (verbatim from the route's stream body; the json body is
    // the same pipeline with push === null and response mapping) -----------
    try {
        const turn = await executeSearchLogged(emit);
        if (turn.kind === 'clarify') {
            emit?.('clarify', { question: turn.question, options: turn.options });
            const saved = await createAssistantMessage({
                conversationId: id,
                role: 'assistant',
                content: turn.question,
                clarifyOptions: JSON.stringify({ question: turn.question, options: turn.options }),
            });
            gsCapLog('clarify', turn);
            if (push)
                push('done', JSON.stringify((0, serializers_1.messageToJson)(saved)));
            result.finalStatus = 'clarify';
            result.assistantMessageId = saved.id;
            result.savedMessage = (0, serializers_1.messageToJson)(saved);
            return result;
        }
        const researchOk = turn.kind === 'research' && turn.outcome.ok && turn.evidenceBlock !== null;
        if (push && turn.kind === 'research') {
            push('status', researchOk ? 'composing' : 'search_failed');
            if (researchOk) {
                emit?.('research', { type: 'synthesis_started' });
            }
        }
        const { messages: modelMessages, historySources, userTurnText } = await buildModelMessages(turn);
        let full;
        if (turn.kind !== 'none' || docContext.block) {
            // GS-CONSTRAINT-GUARD (Failure D, proactive): freshness-critical turns
            // get the grounding requirement injected BEFORE the first synthesis, so
            // the answer is composed from retrieved evidence, not model memory.
            const freshnessActive = (0, constraint_guard_1.isFreshnessRequest)(content) && turn.kind === 'research' && !!turn.evidenceBlock;
            const synthesisMessages = freshnessActive
                ? [
                    { role: 'system', content: (0, constraint_guard_1.buildFreshnessInstruction)() },
                    ...modelMessages,
                ]
                : modelMessages;
            const { text, violation } = await synthesizeValidated(turn, synthesisMessages, userTurnText);
            if (violation) {
                console.log(`GS-GUARD conv=${id} emitted-after-retries violation=${violation.kind}`);
            }
            full = text;
            // GS-CONSTRAINT-GUARD (Failure C): verify draft arithmetic against a
            // CSV-shaped table in the document context; regenerate once, then
            // deterministically rewrite wrong totals. Raw evidence: t17 summed
            // West to 7,350 when the rows total 6,150.
            if (docContext.block) {
                const table = (0, constraint_guard_1.parseCsvTable)(docContext.block);
                if (table) {
                    let aggv = (0, constraint_guard_1.verifyCsvAggregates)(full, table);
                    if (aggv.length > 0) {
                        console.log(`GS-CONSTRAINT-GUARD conv=${id} csv-aggregate mismatch ${aggv
                            .map((x) => `${x.label} ${x.claimed}->${x.actual}`)
                            .join('; ')} — regenerating`);
                        const corrected = applyCorrection(modelMessages, userTurnText, (0, constraint_guard_1.buildAggregateCorrection)(aggv));
                        const retry = await synthesize(corrected);
                        aggv = (0, constraint_guard_1.verifyCsvAggregates)(retry, table);
                        if (aggv.length === 0) {
                            full = retry;
                            console.log(`GS-CONSTRAINT-GUARD conv=${id} csv retry accepted`);
                        }
                        else {
                            const repaired = (0, constraint_guard_1.applyAggregateCorrection)(full, aggv);
                            if (repaired !== null) {
                                full = repaired;
                                console.log(`GS-CONSTRAINT-GUARD conv=${id} deterministic aggregate rewrite applied`);
                            }
                            else {
                                console.log(`GS-CONSTRAINT-GUARD conv=${id} aggregate retry still wrong — emitting honest draft`);
                            }
                        }
                    }
                }
            }
            // GS-CONSTRAINT-GUARD (Failure D): freshness-critical "latest version"
            // answers may only claim versions that appear in the retrieved
            // evidence. Raw evidence: t06 claimed Next.js 15 while the server ran
            // 16.1.3 and the search had returned newer sources.
            if ((0, constraint_guard_1.isFreshnessRequest)(content) && turn.kind === 'research' && turn.evidenceBlock) {
                const ungrounded = (0, constraint_guard_1.freshnessViolations)(full, turn.evidenceBlock);
                if (ungrounded.length > 0) {
                    console.log(`GS-CONSTRAINT-GUARD conv=${id} freshness ungrounded versions ${ungrounded.join(', ')} — regenerating`);
                    const retryMsgs = (0, constraint_guard_1.buildFreshnessRetryMessages)(modelMessages, ungrounded);
                    const retry = await synthesize(retryMsgs);
                    const still = (0, constraint_guard_1.freshnessViolations)(retry, turn.evidenceBlock);
                    full = still.length === 0 ? retry : (0, constraint_guard_1.honestFreshnessFallback)(retry, turn.evidenceBlock);
                    console.log(`GS-CONSTRAINT-GUARD conv=${id} freshness ${still.length === 0 ? 'retry accepted' : 'honest fallback emitted'}`);
                }
            }
            // GS-CONSTRAINT-GUARD: an EMPTY answer must never ship. Measured
            // 2026-10-08 (t06 re-run): the OR chain exhausted (11 keys 429,
            // openrouter/free demands reasoning), the primary fallback failed
            // silently, and an empty bubble persisted with finalStatus=done.
            if (full.trim().length === 0) {
                console.log(`GS-CONSTRAINT-GUARD conv=${id} empty synthesis after chain exhaustion — honest fallback emitted`);
                full =
                    turn.kind === 'research' && turn.evidenceBlock
                        ? (0, constraint_guard_1.honestFreshnessFallback)('', turn.evidenceBlock)
                        : 'I could not complete this answer: the synthesis provider failed after every fallback. Please retry.';
            }
            if (push)
                for (const chunk of chunkDeltas(full))
                    push('delta', chunk);
        }
        else if (numericCheck) {
            let draft = await synthesize(modelMessages);
            let nv = (0, numeric_guard_1.checkNumericClaims)(draft, userTurnText);
            if (nv) {
                console.log(`GS-NUMERIC-GUARD conv=${id} claimed=${nv.claimed} actual=${nv.actual} unit=${nv.unit} — regenerating`);
                const corrected = applyCorrection(modelMessages, userTurnText, (0, numeric_guard_1.buildNumericCorrection)(nv, 1));
                draft = await synthesize(corrected);
                nv = (0, numeric_guard_1.checkNumericClaims)(draft, userTurnText);
                if (nv) {
                    const repaired = (0, numeric_guard_1.applyNumericCorrection)(draft);
                    if (repaired) {
                        draft = repaired;
                        console.log(`GS-NUMERIC-GUARD conv=${id} retry still inconsistent — deterministic rewrite applied`);
                    }
                    else {
                        console.log(`GS-NUMERIC-GUARD conv=${id} retry still inconsistent (claimed=${nv.claimed} actual=${nv.actual}) — emitting honest draft`);
                    }
                }
                else {
                    console.log(`GS-NUMERIC-GUARD conv=${id} retry accepted`);
                }
            }
            full = draft;
            if (push)
                for (const chunk of chunkDeltas(full))
                    push('delta', chunk);
        }
        else if (directive) {
            console.log(`GS-DIRECTIVE conv=${id} target=${directive.target} — approach B rewrite applied`);
            const dMsgs = (0, output_directive_1.buildDirectiveMessages)(modelMessages, directive);
            let draft = await synthesize(dMsgs);
            const first = (0, output_directive_1.enforceDirective)(draft, directive);
            if (first !== null) {
                console.log(`GS-DIRECTIVE conv=${id} draft non-compliant — approach C constraint retry`);
                draft = await synthesize((0, output_directive_1.buildDirectiveRetryMessages)(dMsgs, directive));
                const stripped = (0, output_directive_1.enforceDirective)(draft, directive);
                if (stripped !== null) {
                    console.log(`GS-DIRECTIVE conv=${id} retry non-compliant — approach A deterministic strip`);
                    draft = stripped;
                }
            }
            full = draft;
            if (push)
                for (const chunk of chunkDeltas(full))
                    push('delta', chunk);
        }
        else if (constraints) {
            // GS-CONSTRAINT-GUARD (Failures A and B): mid-prompt word-count
            // constraints and forbidden-character constraints. Ladder: regenerate
            // once with the constraint as the only system message, then repair
            // deterministically, then state the failure honestly.
            let draft = await synthesize(modelMessages);
            let viols = (0, constraint_guard_1.constraintViolations)(draft, constraints);
            if (viols.length > 0) {
                console.log(`GS-CONSTRAINT-GUARD conv=${id} ${viols.join('; ')} — regenerating with constraint restated`);
                draft = await synthesize((0, constraint_guard_1.buildConstraintRetryMessages)(modelMessages, constraints));
                viols = (0, constraint_guard_1.constraintViolations)(draft, constraints);
                if (viols.length > 0) {
                    const repaired = (0, constraint_guard_1.repairConstraints)(draft, constraints);
                    if (repaired !== null) {
                        draft = repaired;
                        console.log(`GS-CONSTRAINT-GUARD conv=${id} retry still violating — deterministic repair applied`);
                    }
                    else {
                        draft = (0, constraint_guard_1.honestConstraintFailure)(draft, constraints);
                        console.log(`GS-CONSTRAINT-GUARD conv=${id} unrepairable — honest failure statement emitted`);
                    }
                }
                else {
                    console.log(`GS-CONSTRAINT-GUARD conv=${id} retry accepted`);
                }
            }
            full = draft;
            if (push)
                for (const chunk of chunkDeltas(full))
                    push('delta', chunk);
        }
        else if (push) {
            full = await synthesize(modelMessages, (d) => push('delta', d));
        }
        else {
            full = await synthesize(modelMessages);
        }
        const baseText = sanitizeAgainstPersisted((0, websearch_1.normalizeCitationBrackets)(full), turn, historySources);
        // GENUI production pipeline: validate every widget fence, repair the
        // malformed ones, and — only when the user asked for a visual and the
        // answer already states the numbers — build the widget deterministically.
        // This runs before persistence, so every client (Android, iOS, web) gets
        // the same renderable content regardless of model compliance.
        const priorAssistant = [...history].reverse().find((m) => m.role.toLowerCase() === 'assistant')?.content ?? '';
        const compiled = (0, compile_1.ensureWidgets)(baseText, { userPrompt: content, priorText: priorAssistant });
        if (compiled.errors.length > 0 || compiled.path !== 'none') {
            console.log(`GS-GENUI conv=${id} path=${compiled.path} widgets=${compiled.widgets}` +
                (compiled.errors.length ? ` errors=${compiled.errors.join(' | ')}` : ''));
        }
        const finalText = compiled.text;
        const saved = await createAssistantMessage({
            conversationId: id,
            role: 'assistant',
            content: finalText,
        });
        await persistTurnSources(saved.id, finalText, turn, historySources);
        if (turn.kind === 'research' && turn.researchId) {
            (0, research_context_1.updateResearchContextCitations)(id, turn.researchId, (0, websearch_1.parseCitedOrdinals)(finalText, turn.maxOrdinal + historySources.length));
        }
        const savedWithSources = await db_1.db.message.findUnique({
            where: { id: saved.id },
            include: { sources: { orderBy: { ordinal: 'asc' } } },
        });
        if (push && turn.kind === 'research' && turn.outcome.ok) {
            const totalOrdinals = turn.maxOrdinal + historySources.length;
            const usedCitations = (0, websearch_1.parseCitedOrdinals)(finalText, totalOrdinals);
            emit?.('search', {
                type: 'completed',
                queries: turn.outcome.queriesRun.length,
                sources: turn.outcome.sources.length,
                retrieved: turn.outcome.sources.filter((s) => s.status === 'retrieved').length,
                usedCitations,
            });
            emit?.('research', {
                type: 'completed',
                queries: turn.outcome.queriesRun.length,
                sources: turn.outcome.sources.length,
                retrieved: turn.outcome.sources.filter((s) => s.status === 'retrieved').length,
                usedCitations,
                totalMs: Date.now() - turn.startedAt,
                timings: turn.outcome.timings,
            });
        }
        gsCapLog('done', turn, {
            cited: (0, websearch_1.parseCitedOrdinals)(finalText, turn.kind === 'research' ? turn.maxOrdinal + historySources.length : historySources.length),
            answer: finalText,
        });
        if (push)
            push('done', JSON.stringify((0, serializers_1.messageToJson)(savedWithSources ?? saved)));
        result.finalStatus = 'done';
        result.assistantMessageId = saved.id;
        result.savedMessage = (0, serializers_1.messageToJson)(savedWithSources ?? saved);
        return result;
    }
    catch (e) {
        const raw = String(e instanceof Error ? e.message : e);
        gsCapLog('error', null, { error: raw });
        const message = useVision ? visionErrorMessage(raw) : userFacingTurnError(raw);
        if (push)
            push('error', message);
        result.finalStatus = 'error';
        result.errorRaw = raw;
        result.errorResponse = {
            status: 502,
            code: 'upstream_error',
            message,
        };
        return result;
    }
}
