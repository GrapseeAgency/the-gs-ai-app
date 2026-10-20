"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.default = Home;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const genui_1 = require("@/components/genui");
/**
 * GS AI — Platform Command Centre (web preview)
 * Aeruo Kinetic design language: obsidian surfaces, aurora accent,
 * serif editorial display. This page exercises the SAME live API
 * contract (shared-contracts/openapi.yaml) that the Android/iOS
 * clients consume — including real SSE streaming, the assistant
 * catalogue, personas and per-conversation controls.
 *
 * PHASE 8.1 — REAL RESEARCH TRACE. The page now renders the backend's
 * actual search events (search.started → query → results → source
 * opening/reading/completed/failed → composing): every visible research
 * step corresponds 1:1 to a real backend action (§31 — no fake visuals,
 * no timers). Source cards open the real canonical URLs; [N] citation
 * markers in answers are tappable and resolve to the persisted source;
 * ambiguous news requests surface the backend's native quick choices.
 */
/** JSON with an honest failure. The send/attach paths surface `e.message`
 * verbatim, so a raw `res.json()` TypeError ("Failed to execute 'json' on
 * 'Response': Unexpected end of JSON input") leaked into the error surface
 * when a proxy/breaker answered with an empty body (raw: operator
 * screenshot, red error box + the '1 Issue' dev-overlay badge). Text-first
 * parsing turns every unreadable body into one readable sentence.
 */
async function readJson(res) {
    const text = await res.text();
    try {
        return JSON.parse(text);
    }
    catch {
        throw new Error(`GS backend answered HTTP ${res.status} with a body that is not readable JSON`);
    }
}
const C = {
    bg: '#FFF4EF',
    surface: '#FFFBF8',
    raised: '#FFFFFF',
    container: '#FFE8DE',
    text: '#2B1A17',
    muted: '#8F7169',
    outline: '#F0D2C6',
    accent: '#FF9142',
    accentDeep: '#F36F2C',
    danger: '#D95C55',
};
const AURORA = 'linear-gradient(90deg, #FFB36D, #FF8847, #F36F2C)';
const MODE_STORAGE_PREFIX = 'gs.mode.';
function loadMode(conversationId) {
    if (typeof window === 'undefined')
        return 'flash';
    const key = `${MODE_STORAGE_PREFIX}${conversationId ?? 'new'}`;
    const raw = window.localStorage.getItem(key);
    return raw === 'thinking' || raw === 'auto' || raw === 'flash' ? raw : 'flash';
}
function storeMode(conversationId, mode) {
    if (typeof window === 'undefined')
        return;
    try {
        window.localStorage.setItem(`${MODE_STORAGE_PREFIX}${conversationId ?? 'new'}`, mode);
    }
    catch {
        // private-mode / storage-disabled — mode just won't persist
    }
}
const MODE_META = {
    flash: { label: 'Flash', hint: 'Instant replies' },
    thinking: { label: 'Thinking', hint: 'Deep reasoning' },
    auto: { label: 'Auto', hint: 'System decides' },
};
// PHASE 8.2 §16 — engine display names for the per-engine trace rows.
const ENGINE_LABELS = {
    'bing-news-rss': 'Bing News',
    'google-news-rss': 'Google News',
    'bing-web': 'Bing Web',
    'duckduckgo-lite': 'DuckDuckGo',
    wikipedia: 'Wikipedia',
    searxng: 'SearXNG',
    'z-ai': 'GS Web',
    openlibrary: 'Open Library',
    gutenberg: 'Gutenberg',
    arxiv: 'arXiv',
    crossref: 'Crossref',
    // Only ever present when TREG_TOKEN is configured (see lib/search/engines.ts).
    treg: 'WebAccess',
    // Only ever present when BRAVE_API_KEY is configured.
    brave: 'Brave',
};
/* ---------- small helpers ---------- */
function relTime(iso) {
    const diff = Date.now() - new Date(iso).getTime();
    const m = Math.floor(diff / 60000);
    if (m < 1)
        return 'now';
    if (m < 60)
        return `${m}m ago`;
    const h = Math.floor(m / 60);
    if (h < 24)
        return `${h}h ago`;
    const d = Math.floor(h / 24);
    return `${d}d ago`;
}
function greet() {
    const h = new Date().getHours();
    if (h < 5)
        return 'Working late';
    if (h < 12)
        return 'Good morning';
    if (h < 18)
        return 'Good afternoon';
    return 'Good evening';
}
function fmtUses(n) {
    return n >= 1000 ? `${(n / 1000).toFixed(n >= 10000 ? 0 : 1)}k` : String(n);
}
function domainInitial(domain) {
    return (domain.replace(/^www\./, '')[0] ?? 'W').toUpperCase();
}
function shortDate(iso) {
    if (!iso)
        return null;
    const d = new Date(iso);
    if (Number.isNaN(d.getTime()))
        return null;
    return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}
const CATEGORY_LABELS = {
    research: 'Research',
    writing: 'Writing',
    coding: 'Coding',
    productivity: 'Productivity',
    learning: 'Learning',
    general: 'General',
};
/* ---------- PHASE 8.1 building blocks ---------- */
/** One real research step — rendered verbatim from backend events. */
function SearchTracePanel({ steps, phase }) {
    const phaseLabel = phase === 'searching'
        ? 'Searching the web'
        : phase === 'working'
            ? 'Reading sources'
            : phase === 'search_failed'
                ? 'Search failed'
                : phase === 'composing'
                    ? 'Composing'
                    : null;
    return ((0, jsx_runtime_1.jsxs)("div", { className: "rounded-xl border px-3.5 py-3", style: { background: C.container, borderColor: C.outline }, "aria-label": "Live research trace", "aria-live": "polite", children: [(0, jsx_runtime_1.jsxs)("div", { className: "mb-2 flex items-center gap-2", children: [(0, jsx_runtime_1.jsx)("span", { className: "inline-block h-1.5 w-1.5 animate-pulse rounded-full", style: { background: C.accent } }), (0, jsx_runtime_1.jsx)("span", { className: "text-[11px] font-semibold uppercase tracking-wider", style: { color: C.accent }, children: phaseLabel ?? 'Researching' })] }), (0, jsx_runtime_1.jsx)("ul", { className: "space-y-1.5", children: steps.map((s) => ((0, jsx_runtime_1.jsxs)("li", { className: "flex items-start gap-2 text-xs leading-relaxed", style: { color: s.tone === 'fail' ? C.danger : s.tone === 'ok' ? C.text : C.muted }, children: [(0, jsx_runtime_1.jsx)("span", { className: "mt-[1px] w-3.5 shrink-0 text-center", style: { color: s.tone === 'fail' ? C.danger : C.accent }, "aria-hidden": "true", children: s.icon }), (0, jsx_runtime_1.jsx)("span", { className: "min-w-0 flex-1", children: (0, jsx_runtime_1.jsx)("span", { className: "font-medium", children: s.text }) })] }, s.id))) })] }));
}
/** Compact source card — favicon when reachable, monogram always. */
function SourceCardView({ s }) {
    const [imgOk, setImgOk] = (0, react_1.useState)(true);
    const cited = s.used !== false;
    return ((0, jsx_runtime_1.jsx)("a", { href: s.url, target: "_blank", rel: "noopener noreferrer", className: "gs-card block w-full min-w-0 rounded-xl border p-3", style: {
            background: C.container,
            borderColor: cited ? C.outline : C.outline,
            opacity: cited ? 1 : 0.75,
        }, "aria-label": `Source ${s.ordinal}: ${s.title} (${s.domain})`, children: (0, jsx_runtime_1.jsxs)("div", { className: "flex items-start gap-2.5", children: [(0, jsx_runtime_1.jsxs)("span", { className: "relative flex h-8 w-8 shrink-0 items-center justify-center overflow-hidden rounded-lg text-[11px] font-semibold", style: { background: C.raised, color: C.accent }, children: [imgOk && ((0, jsx_runtime_1.jsx)("img", { src: `https://icons.duckduckgo.com/ip3/${s.domain}.ico`, alt: "", width: 16, height: 16, className: "absolute inset-0 m-auto", onError: () => setImgOk(false) })), (0, jsx_runtime_1.jsx)("span", { "aria-hidden": imgOk, children: domainInitial(s.domain) })] }), (0, jsx_runtime_1.jsxs)("span", { className: "min-w-0 flex-1", children: [(0, jsx_runtime_1.jsxs)("span", { className: "flex items-center gap-1.5", children: [(0, jsx_runtime_1.jsx)("span", { className: "flex h-4 w-4 shrink-0 items-center justify-center rounded text-[9px] font-bold", style: { background: 'rgba(255,145,66,0.14)', color: C.accent }, children: s.ordinal }), (0, jsx_runtime_1.jsxs)("span", { className: "truncate text-[11px] font-medium", style: { color: C.muted }, children: [s.domain, shortDate(s.publishedDate) ? ` · ${shortDate(s.publishedDate)}` : ''] })] }), (0, jsx_runtime_1.jsx)("span", { className: "mt-0.5 line-clamp-2 block text-xs leading-snug", style: { color: C.text }, children: s.title }), s.status === 'snippet_only' && ((0, jsx_runtime_1.jsx)("span", { className: "mt-0.5 block text-[10px]", style: { color: C.muted }, children: "headline only \u2014 not retrieved" }))] })] }) }));
}
function SourceCards({ sources }) {
    if (sources.length === 0)
        return null;
    return ((0, jsx_runtime_1.jsxs)("div", { className: "mt-2", children: [(0, jsx_runtime_1.jsxs)("p", { className: "mb-1.5 text-[10px] font-semibold uppercase tracking-wider", style: { color: C.muted }, children: ["Sources \u00B7 ", sources.filter((s) => s.used !== false).length, " used"] }), (0, jsx_runtime_1.jsx)("div", { className: "grid gap-2 sm:grid-cols-2", children: sources.map((s) => ((0, jsx_runtime_1.jsx)(SourceCardView, { s: s }, `${s.ordinal}-${s.url}`))) })] }));
}
/**
 * Answer text with tappable [N] citations. A marker resolves ONLY to a
 * persisted source with that ordinal — unmatched markers stay plain text
 * (the server already strips dead ones before persistence).
 */
function AnswerText({ text, sources }) {
    const nodes = [];
    const re = /[\[【〚［]\s*(\d{1,2})\s*[\]】〛］]/g;
    let last = 0;
    let key = 0;
    for (const m of text.matchAll(re)) {
        const idx = m.index ?? 0;
        if (idx > last)
            nodes.push((0, jsx_runtime_1.jsx)("span", { children: text.slice(last, idx) }, `t${key++}`));
        const n = Number.parseInt(m[1], 10);
        const src = sources?.find((s) => s.ordinal === n);
        if (src) {
            nodes.push((0, jsx_runtime_1.jsx)("button", { onClick: () => window.open(src.url, '_blank', 'noopener,noreferrer'), "aria-label": `Open citation ${n}: ${src.title}`, title: `${src.title} — ${src.domain}`, className: "mx-0.5 inline-flex h-4 min-w-4 cursor-pointer items-center justify-center rounded px-1 align-[2px] text-[10px] font-bold transition-colors hover:brightness-125", style: { background: 'rgba(255,145,66,0.16)', color: C.accent }, children: n }, `c${key++}`));
        }
        else {
            nodes.push((0, jsx_runtime_1.jsx)("span", { children: m[0] }, `c${key++}`));
        }
        last = idx + m[0].length;
    }
    if (last < text.length)
        nodes.push((0, jsx_runtime_1.jsx)("span", { children: text.slice(last) }, `t${key++}`));
    return (0, jsx_runtime_1.jsx)(jsx_runtime_1.Fragment, { children: nodes });
}
/** Native quick choices for an ambiguous news request (§6) — conversation controls, not screens. */
function ClarifyChips({ clarify, onPick }) {
    return ((0, jsx_runtime_1.jsx)("div", { className: "flex justify-start", children: (0, jsx_runtime_1.jsxs)("div", { className: "max-w-[92%] rounded-2xl rounded-bl-md border px-4 py-3", style: { background: C.raised, borderColor: C.outline }, children: [(0, jsx_runtime_1.jsx)("p", { className: "text-sm leading-relaxed", children: clarify.question }), (0, jsx_runtime_1.jsx)("div", { className: "mt-2.5 flex flex-wrap gap-1.5", children: clarify.options.map((o) => ((0, jsx_runtime_1.jsx)("button", { onClick: () => onPick(o.label), className: "rounded-full border px-3 py-1.5 text-xs font-medium transition-all hover:border-[#FF9142] active:scale-95", style: { borderColor: C.outline, color: C.text, background: C.container }, "aria-label": `Ask for ${o.label} news`, children: o.label }, o.id))) })] }) }));
}
/* ---------- page ---------- */
function Home() {
    const [health, setHealth] = (0, react_1.useState)('checking');
    const [conversations, setConversations] = (0, react_1.useState)([]);
    const [assistants, setAssistants] = (0, react_1.useState)([]);
    const [category, setCategory] = (0, react_1.useState)('all');
    const [conversationId, setConversationId] = (0, react_1.useState)(null);
    const [activeAssistant, setActiveAssistant] = (0, react_1.useState)(null);
    const [historyLoading, setHistoryLoading] = (0, react_1.useState)(false);
    const [messages, setMessages] = (0, react_1.useState)([]);
    const [draft, setDraft] = (0, react_1.useState)('');
    const [streamText, setStreamText] = (0, react_1.useState)('');
    const [isStreaming, setIsStreaming] = (0, react_1.useState)(false);
    // REASONING TRACE (chat-reasoning panel): the model's thinking stream, shown
    // in a collapsible quiet panel above the answer. Open while the model
    // thinks, auto-collapses when the first answer token lands (reopenable);
    // the seconds label is the MEASURED span from first reasoning delta to
    // first answer delta, never a timer.
    const [reasoningText, setReasoningText] = (0, react_1.useState)('');
    const [reasoningOpen, setReasoningOpen] = (0, react_1.useState)(true);
    const [reasoningStart, setReasoningStart] = (0, react_1.useState)(null);
    const [reasoningEnd, setReasoningEnd] = (0, react_1.useState)(null);
    const [error, setError] = (0, react_1.useState)(null);
    const [copiedIdx, setCopiedIdx] = (0, react_1.useState)(null);
    const [startingChat, setStartingChat] = (0, react_1.useState)(null);
    // FLASH MODE (Phase 3) — composer sheet state.
    const [mode, setMode] = (0, react_1.useState)('flash');
    const [sheetOpen, setSheetOpen] = (0, react_1.useState)(false);
    const [pendingAttachments, setPendingAttachments] = (0, react_1.useState)([]);
    const fileInputRef = (0, react_1.useRef)(null);
    const imageInputRef = (0, react_1.useRef)(null);
    // Mode persists per conversation; a new chat reads/writes the 'new' slot and
    // the choice carries into the conversation when it is created.
    (0, react_1.useEffect)(() => {
        setMode(loadMode(conversationId));
    }, [conversationId]);
    const changeMode = (0, react_1.useCallback)((next) => {
        setMode(next);
        storeMode(conversationId, next);
    }, [conversationId]);
    // PHASE 8.1 live research state — every entry originates from a real SSE event.
    const [trace, setTrace] = (0, react_1.useState)([]);
    const [liveSources, setLiveSources] = (0, react_1.useState)([]);
    const [phase, setPhase] = (0, react_1.useState)(null);
    const [clarify, setClarify] = (0, react_1.useState)(null);
    const traceIdRef = (0, react_1.useRef)(0);
    const abortRef = (0, react_1.useRef)(null);
    const accRef = (0, react_1.useRef)('');
    const scrollRef = (0, react_1.useRef)(null);
    // PHASE 2 — background-runs transport flag (from /api/health).
    const backgroundRunsRef = (0, react_1.useRef)(false);
    // Latest `send`, callable from deferred starter sends without stale closures.
    const sendRef = (0, react_1.useRef)(() => undefined);
    /* ----- data loading ----- */
    const loadStatus = (0, react_1.useCallback)(async () => {
        try {
            const [h, c, a] = await Promise.all([
                fetch('/api/health').then((r) => r.json()),
                fetch('/api/v1/conversations?limit=8').then((r) => r.json()),
                fetch('/api/v1/assistants').then((r) => r.json()),
            ]);
            setHealth(h?.status === 'ok' ? 'ok' : 'down');
            setConversations(c?.items ?? []);
            setAssistants(a?.items ?? []);
            // PHASE 2 — pick the resumable transport when the deployment has it on.
            backgroundRunsRef.current = h?.backgroundRuns === true;
            // ARCHITECTURE LOCK: no model catalogue fetch — the user never sees or
            // selects models; routing is entirely backend-side (GS Router).
        }
        catch {
            setHealth('down');
        }
    }, []);
    (0, react_1.useEffect)(() => {
        loadStatus();
    }, [loadStatus]);
    (0, react_1.useEffect)(() => {
        scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: 'smooth' });
    }, [messages, streamText, trace]);
    const loadHistory = (0, react_1.useCallback)(async (id) => {
        setHistoryLoading(true);
        try {
            const data = await fetch(`/api/v1/conversations/${id}/messages`).then((r) => r.json());
            const items = (data?.items ?? []);
            setMessages(items
                .filter((m) => m.role === 'user' || m.role === 'assistant')
                .map((m) => {
                let clarifyChoices = null;
                if (m.clarifyOptions) {
                    try {
                        const parsed = JSON.parse(m.clarifyOptions);
                        if (parsed?.question && Array.isArray(parsed.options))
                            clarifyChoices = parsed;
                    }
                    catch {
                        // malformed persisted options — ignore
                    }
                }
                return {
                    role: m.role,
                    content: m.content,
                    ...(m.sources && m.sources.length > 0 ? { sources: m.sources } : {}),
                    ...(clarifyChoices ? { clarifyOptions: clarifyChoices } : {}),
                };
            }));
        }
        catch {
            setError('Could not load this conversation.');
        }
        finally {
            setHistoryLoading(false);
        }
    }, []);
    const openConversation = (0, react_1.useCallback)((c) => {
        if (isStreaming)
            abortRef.current?.abort();
        setConversationId(c.id);
        setActiveAssistant(c.assistantId ? { id: c.assistantId, name: c.assistantName ?? 'Assistant' } : null);
        setMessages([]);
        setStreamText('');
        setTrace([]);
        setLiveSources([]);
        setPhase(null);
        setClarify(null);
        setError(null);
        void loadHistory(c.id);
    }, [isStreaming, loadHistory]);
    const newChat = (0, react_1.useCallback)(() => {
        if (isStreaming)
            abortRef.current?.abort();
        setConversationId(null);
        setActiveAssistant(null);
        setMessages([]);
        setStreamText('');
        setTrace([]);
        setLiveSources([]);
        setPhase(null);
        setClarify(null);
        setError(null);
    }, [isStreaming]);
    const deleteConversation = (0, react_1.useCallback)((id, e) => {
        e.stopPropagation();
        setConversations((prev) => prev.filter((c) => c.id !== id));
        if (conversationId === id)
            newChat();
        fetch(`/api/v1/conversations/${id}`, { method: 'DELETE' }).catch(() => undefined);
    }, [conversationId, newChat]);
    const toggleFavourite = (0, react_1.useCallback)((a) => {
        setAssistants((prev) => prev.map((x) => (x.id === a.id ? { ...x, favourite: !x.favourite } : x)));
        fetch(`/api/v1/assistants/${a.id}`, {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ favourite: !a.favourite }),
        }).catch(() => undefined);
    }, []);
    const startAssistantChat = (0, react_1.useCallback)((a, starter) => {
        setStartingChat(a.id);
        fetch('/api/v1/conversations', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ title: starter ?? a.name, assistantId: a.id }),
        })
            .then((r) => r.json())
            .then((created) => {
            setConversations((prev) => [
                {
                    id: created.id,
                    title: created.title ?? a.name,
                    pinned: false,
                    archived: false,
                    assistantId: a.id,
                    assistantName: a.name,
                    updatedAt: new Date().toISOString(),
                },
                ...prev,
            ]);
            setConversationId(created.id);
            setActiveAssistant({ id: a.id, name: a.name });
            setMessages([]);
            setStreamText('');
            setTrace([]);
            setLiveSources([]);
            setPhase(null);
            setClarify(null);
            if (starter) {
                setTimeout(() => sendRef.current(starter), 60);
            }
        })
            .catch(() => setError('Could not start this chat.'))
            .finally(() => setStartingChat(null));
    }, []);
    /* ----- the send path (PHASE 8.1: full research-trace event handling) ----- */
    const send = (0, react_1.useCallback)(async (override) => {
        const text = (override ?? draft).trim();
        if (!text || isStreaming)
            return;
        const attachmentsUploading = pendingAttachments.some((a) => a.uploading);
        if (attachmentsUploading)
            return;
        const attachmentIds = pendingAttachments.map((a) => a.id);
        setDraft('');
        setPendingAttachments([]);
        setError(null);
        setTrace([]);
        setLiveSources([]);
        setPhase(null);
        setClarify(null);
        setReasoningText('');
        setReasoningOpen(true);
        setReasoningStart(null);
        setReasoningEnd(null);
        setMessages((prev) => [...prev, { role: 'user', content: text }]);
        setIsStreaming(true);
        const controller = new AbortController();
        abortRef.current = controller;
        let convId = conversationId;
        const traceSteps = [];
        const sourceByOrdinal = new Map();
        let traceSummary;
        let finalClarify = null;
        const pushStep = (icon, text, tone) => {
            traceSteps.push({ id: `s${++traceIdRef.current}`, icon, text, tone });
            setTrace([...traceSteps]);
        };
        try {
            if (!convId) {
                const created = await fetch('/api/v1/conversations', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ title: text.slice(0, 40) }),
                }).then((r) => readJson(r));
                convId = created?.id ?? null;
                setConversationId(convId);
            }
            if (!convId)
                throw new Error('Could not create conversation');
            // PHASE 1/2 — transport selection. When the deployment has
            // BACKGROUND_RUNS on, the turn is started detached
            // (POST /api/v1/messages/start → {streamId, runId}) and consumed via
            // EventSource on /api/v1/stream/{streamId} — auto-reconnecting with
            // Last-Event-ID — falling back to HTTP polling after 3 failed SSE
            // attempts. Otherwise the legacy inline SSE POST is used unchanged.
            // ALL transports share the ONE chunk consumer below, so rendering
            // behavior is identical whichever transport serves the turn.
            let finalized = false;
            let acc = '';
            let errorMessage = null;
            const handleChunk = (eventName, dataRaw) => {
                try {
                    const event = { event: eventName, data: dataRaw };
                    if (event.event === 'delta') {
                        // First answer token closes the thinking phase (panel collapses;
                        // the measured span freezes — no live counter, no timer).
                        setReasoningEnd((prev) => prev ?? Date.now());
                        setReasoningOpen(false);
                        acc += event.data ?? '';
                        accRef.current = acc;
                        setStreamText(acc);
                    }
                    else if (event.event === 'reasoning') {
                        try {
                            const p = JSON.parse(event.data || '{}');
                            if (typeof p.delta === 'string' && p.delta) {
                                setReasoningStart((prev) => prev ?? Date.now());
                                setReasoningText((prev) => prev + p.delta);
                            }
                        }
                        catch {
                            /* malformed reasoning frame — skip, never break the stream */
                        }
                    }
                    else if (event.event === 'status') {
                        setPhase(event.data);
                        if (event.data === 'search_failed') {
                            pushStep('✕', 'The search could not be completed', 'fail');
                        }
                    }
                    else if (event.event === 'search') {
                        const p = JSON.parse(event.data);
                        if (p.type === 'started') {
                            pushStep('◎', `Searching — “${p.label ?? ''}”`);
                        }
                        else if (p.type === 'engines') {
                            // 8.2 §16 — per-engine truth (✓ ok · ✕ failed · – empty).
                            for (const e of Array.isArray(p.engines) ? p.engines : []) {
                                const name = ENGINE_LABELS[e.id] ?? e.id;
                                if (e.status === 'ok')
                                    pushStep('✓', `${name} — ${e.count ?? 0} results`, 'ok');
                                else if (e.status === 'failed')
                                    pushStep('✕', `${name} — ${e.error ?? 'failed'}`, 'fail');
                                else
                                    pushStep('–', `${name} — no results`, undefined);
                            }
                        }
                        else if (p.type === 'results') {
                            pushStep('✓', `Found ${p.found} results${Array.isArray(p.engines) && p.engines.length ? ` · ${p.engines.join(', ')}` : ''}`, 'ok');
                        }
                        else if (p.type === 'round') {
                            pushStep('✓', `${p.sourcesVerified} source${p.sourcesVerified === 1 ? '' : 's'} verified${p.syndicatedGroups ? ` · ${p.syndicatedGroups} syndicated group${p.syndicatedGroups === 1 ? '' : 's'}` : ''}`, 'ok');
                        }
                        else if (p.type === 'failed') {
                            pushStep('✕', `Search failed — ${p.reason ?? 'unknown'}`, 'fail');
                        }
                        else if (p.type === 'completed') {
                            traceSummary = `Searched ${p.queries} ${p.queries === 1 ? 'query' : 'queries'} · read ${p.retrieved} of ${p.sources} sources`;
                        }
                    }
                    else if (event.event === 'research') {
                        // 8.2 §28 — research-level truth (protocol v2).
                        const p = JSON.parse(event.data);
                        if (p.type === 'round_completed') {
                            pushStep('✓', `Round ${p.round}: ${p.read ?? 0} read · ${p.failed ?? 0} failed${p.syndicatedGroups ? ` · ${p.syndicatedGroups} syndicated` : ''}`, 'ok');
                        }
                        else if (p.type === 'synthesis_started') {
                            pushStep('✎', 'Composing grounded answer…', 'live');
                        }
                        else if (p.type === 'completed') {
                            const secs = p.totalMs ? ` · ${(p.totalMs / 1000).toFixed(1)}s` : '';
                            traceSummary = `Searched ${p.queries} ${p.queries === 1 ? 'query' : 'queries'} · read ${p.retrieved} of ${p.sources} sources${secs}`;
                        }
                        else if (p.type === 'failed') {
                            const last = traceSteps[traceSteps.length - 1];
                            if (!last || !last.text.startsWith('Search failed')) {
                                pushStep('✕', `Research failed — ${p.reason ?? 'unknown'}`, 'fail');
                            }
                        }
                    }
                    else if (event.event === 'source') {
                        const p = JSON.parse(event.data);
                        const ordinal = p.source?.ordinal ?? p.ordinal;
                        if (p.type === 'discovered' && p.source) {
                            sourceByOrdinal.set(ordinal, p.source);
                            setLiveSources([...sourceByOrdinal.values()].sort((a, b) => a.ordinal - b.ordinal));
                        }
                        else if (p.type === 'opening' || p.type === 'reading') {
                            const s = sourceByOrdinal.get(ordinal);
                            pushStep('↗', `Reading ${s?.domain ?? `source ${ordinal}`}…`, 'live');
                        }
                        else if (p.type === 'completed' || p.type === 'read') {
                            // protocol v2 — `read` is canonical, `completed` the v1 alias;
                            // both arrive, the transition is idempotent (one step only).
                            const s = sourceByOrdinal.get(ordinal);
                            const alreadyRead = s?.status === 'retrieved';
                            if (s) {
                                s.status = 'retrieved';
                                if (p.publishedDate)
                                    s.publishedDate = p.publishedDate;
                                setLiveSources([...sourceByOrdinal.values()].sort((a, b) => a.ordinal - b.ordinal));
                            }
                            if (!alreadyRead) {
                                pushStep('✓', `Read ${s?.domain ?? `source ${ordinal}`} — ${(s?.title ?? '').slice(0, 70)}`, 'ok');
                            }
                        }
                        else if (p.type === 'failed') {
                            const s = sourceByOrdinal.get(ordinal);
                            pushStep('✕', `${s?.domain ?? `source ${ordinal}`} — ${p.reason ?? 'failed'}`, 'fail');
                        }
                    }
                    else if (event.event === 'clarify') {
                        const p = JSON.parse(event.data);
                        if (p?.question && Array.isArray(p.options)) {
                            finalClarify = { question: p.question, options: p.options };
                            setClarify(finalClarify);
                        }
                    }
                    else if (event.event === 'done') {
                        finalized = true;
                        const saved = typeof event.data === 'string' ? JSON.parse(event.data) : event.data;
                        const doneSources = Array.isArray(saved?.sources)
                            ? saved.sources.map((s) => ({ ...s, used: s.used ?? true }))
                            : [...sourceByOrdinal.values()].sort((a, b) => a.ordinal - b.ordinal);
                        // PHASE 8.1 — a clarification turn persists its quick choices;
                        // attach them to the rendered message so the chips survive done.
                        let doneClarify = null;
                        if (typeof saved?.clarifyOptions === 'string' && saved.clarifyOptions) {
                            try {
                                const parsed = JSON.parse(saved.clarifyOptions);
                                if (parsed?.question && Array.isArray(parsed.options))
                                    doneClarify = parsed;
                            }
                            catch {
                                // malformed — ignore
                            }
                        }
                        setMessages((prev) => [
                            ...prev,
                            {
                                role: 'assistant',
                                content: saved?.content ?? acc,
                                ...(doneSources.length > 0 ? { sources: doneSources } : {}),
                                ...(doneClarify ? { clarifyOptions: doneClarify } : {}),
                                ...(traceSummary ? { traceSummary } : {}),
                            },
                        ]);
                        setStreamText('');
                        setTrace([]);
                        setLiveSources([]);
                        setPhase(null);
                        setClarify(null);
                        return 'done';
                    }
                    else if (event.event === 'error') {
                        errorMessage = event.data || 'Stream error';
                        return 'error';
                    }
                    return 'continue';
                }
                catch {
                    /* keep-alive or partial line — skip */
                    return 'continue';
                }
            };
            if (backgroundRunsRef.current) {
                // ---- BACKGROUND-RUNS TRANSPORT (PHASE 1/2) ----------------------
                const start = await fetch('/api/v1/messages/start', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        conversationId: convId,
                        content: text,
                        timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
                        // FLASH MODE (Phase 3) — the composer's thinking mode.
                        mode,
                        ...(attachmentIds.length > 0 ? { attachments: attachmentIds } : {}),
                    }),
                    signal: controller.signal,
                });
                if (!start.ok)
                    throw new Error(`Backend responded ${start.status}`);
                const started = (await readJson(start));
                let sawFinal = false;
                let cursor = '';
                const applyChunk = (c) => {
                    if (c.id)
                        cursor = c.id;
                    const verdict = handleChunk(c.event, c.data);
                    if (verdict === 'done')
                        sawFinal = true;
                };
                await new Promise((resolve) => {
                    let sseAttempts = 0;
                    const MAX_SSE_ATTEMPTS = 3;
                    let es = null;
                    let pollTimer = null;
                    let settled = false;
                    const stopAll = () => {
                        if (settled)
                            return;
                        settled = true;
                        es?.close();
                        if (pollTimer)
                            clearInterval(pollTimer);
                        resolve();
                    };
                    controller.signal.addEventListener('abort', () => stopAll());
                    // POLLING FALLBACK — EventSource failed 3 times in a row
                    // (restrictive proxy / serverless): poll the turn endpoint
                    // instead; it is a full substitute for the subscription.
                    const startPolling = () => {
                        es?.close();
                        let polls = 0;
                        pollTimer = setInterval(async () => {
                            polls++;
                            try {
                                const res = await fetch(`/api/v1/turn/${started.runId}?after=${encodeURIComponent(cursor)}`);
                                const data = (await res.json());
                                for (const c of Array.isArray(data.chunks) ? data.chunks : [])
                                    applyChunk(c);
                                if (sawFinal || data.final || polls > 300)
                                    stopAll();
                            }
                            catch {
                                if (polls > 300)
                                    stopAll();
                            }
                        }, 2000);
                    };
                    const connect = () => {
                        es = new EventSource(`/api/v1/stream/${started.streamId}`);
                        es.onmessage = (e) => {
                            sseAttempts = 0;
                            if (e.lastEventId)
                                cursor = e.lastEventId;
                            try {
                                const env = JSON.parse(e.data);
                                applyChunk(env);
                                const s = errorMessage !== null || sawFinal;
                                if (s)
                                    stopAll();
                            }
                            catch {
                                /* keepalive comment or partial frame — EventSource ignores */
                            }
                        };
                        es.onerror = () => {
                            sseAttempts++;
                            if (sseAttempts >= MAX_SSE_ATTEMPTS) {
                                startPolling();
                            }
                            // otherwise EventSource auto-reconnects and sends
                            // Last-Event-ID automatically — no manual logic needed.
                        };
                    };
                    connect();
                });
                if (controller.signal.aborted) {
                    // Stop-generation with background runs: detach the VIEW only —
                    // the run continues server-side and persists (by design).
                    const abortErr = new Error('Aborted');
                    abortErr.name = 'AbortError';
                    throw abortErr;
                }
                if (errorMessage !== null)
                    throw new Error(errorMessage);
            }
            else {
                // ---- LEGACY INLINE SSE (unchanged transport) --------------------
                const res = await fetch(`/api/v1/conversations/${convId}/messages`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        content: text,
                        stream: true,
                        // PHASE 8.3 §11 — real timezone-aware time capability: the client
                        // supplies its IANA zone so the backend clock answers in local time.
                        timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
                        // FLASH MODE (Phase 3) — the composer's thinking mode.
                        mode,
                        ...(attachmentIds.length > 0 ? { attachments: attachmentIds } : {}),
                    }),
                    signal: controller.signal,
                });
                if (!res.ok || !res.body)
                    throw new Error(`Backend responded ${res.status}`);
                const reader = res.body.getReader();
                const decoder = new TextDecoder();
                let buffer = '';
                for (;;) {
                    const { done, value } = await reader.read();
                    if (done)
                        break;
                    buffer += decoder.decode(value, { stream: true });
                    const lines = buffer.split('\n');
                    buffer = lines.pop() ?? '';
                    for (const line of lines) {
                        const trimmed = line.trim();
                        if (!trimmed.startsWith('data:'))
                            continue;
                        try {
                            const event = JSON.parse(trimmed.slice(5).trim());
                            handleChunk(event.event, event.data);
                        }
                        catch {
                            /* keep-alive or partial line — skip */
                        }
                    }
                    if (finalized || errorMessage !== null)
                        break;
                }
                if (errorMessage !== null)
                    throw new Error(errorMessage);
            }
            if (!finalized && convId) {
                // PHASE 8.1 — the turn runs detached server-side (the public
                // origin's outer proxy caps responses at ~30s; a research turn can
                // legitimately outlive that). The view may end without `done` while
                // the server still finishes and persists the answer — poll the
                // conversation briefly and adopt the completed message instead of
                // keeping a partial.
                let adopted = false;
                for (let attempt = 0; attempt < 9 && !adopted; attempt++) {
                    await new Promise((r) => setTimeout(r, attempt === 0 ? 600 : 4000));
                    try {
                        const data = await fetch(`/api/v1/conversations/${convId}/messages`).then((r) => r.json());
                        const items = (data?.items ?? []);
                        // Our turn's assistant message = the first assistant message
                        // after OUR user message (matched by exact content).
                        let after = false;
                        let landed = null;
                        for (const m of items) {
                            if (!after && m.role === 'user' && m.content === text)
                                after = true;
                            else if (after && m.role === 'assistant' && m.content.trim().length > 0)
                                landed = m;
                        }
                        if (landed) {
                            const landedSources = Array.isArray(landed.sources)
                                ? landed.sources.map((s) => ({ ...s, used: s.used ?? true }))
                                : [];
                            setMessages((prev) => [
                                ...prev,
                                {
                                    role: 'assistant',
                                    content: landed.content,
                                    ...(landedSources.length > 0 ? { sources: landedSources } : {}),
                                },
                            ]);
                            adopted = true;
                        }
                    }
                    catch {
                        // transient — keep polling
                    }
                }
                if (!adopted && acc) {
                    setMessages((prev) => [...prev, { role: 'assistant', content: acc }]);
                }
                setStreamText('');
                setTrace([]);
                setLiveSources([]);
                setPhase(null);
                setClarify(null);
            }
            loadStatus();
        }
        catch (e) {
            if (e.name !== 'AbortError') {
                setError(e instanceof Error ? e.message : 'Something went wrong');
            }
            else if (accRef.current) {
                // Stop-generation: keep whatever streamed before the interrupt.
                setMessages((prev) => [...prev, { role: 'assistant', content: accRef.current }]);
                setStreamText('');
            }
        }
        finally {
            setIsStreaming(false);
            abortRef.current = null;
        }
    }, [conversationId, draft, isStreaming, loadStatus, mode, pendingAttachments]);
    // Keep the ref fresh — the deferred starter path always calls the latest send.
    (0, react_1.useEffect)(() => {
        sendRef.current = send;
    }, [send]);
    const stop = (0, react_1.useCallback)(() => {
        abortRef.current?.abort();
    }, []);
    /** FLASH MODE (Phase 3) — upload picked files through the real uploads API. */
    const uploadFiles = (0, react_1.useCallback)(async (files) => {
        if (!files || files.length === 0)
            return;
        for (const file of Array.from(files).slice(0, 4)) {
            const tempId = `pending-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
            setPendingAttachments((prev) => [
                ...prev,
                {
                    id: tempId,
                    displayName: file.name,
                    kind: file.type.startsWith('image/') ? 'image' : 'document',
                    uploading: true,
                },
            ]);
            try {
                const form = new FormData();
                form.append('file', file);
                const res = await fetch('/api/v1/uploads', { method: 'POST', body: form });
                if (!res.ok)
                    throw new Error(`upload failed (${res.status})`);
                const saved = (await readJson(res));
                setPendingAttachments((prev) => prev.map((a) => a.id === tempId
                    ? { ...a, id: saved.id, kind: saved.kind, displayName: saved.displayName ?? file.name, uploading: false }
                    : a));
            }
            catch {
                setPendingAttachments((prev) => prev.filter((a) => a.id !== tempId));
                setError('The attachment could not be uploaded.');
            }
        }
    }, []);
    const copyMessage = (0, react_1.useCallback)((idx, text) => {
        navigator.clipboard?.writeText(text).then(() => {
            setCopiedIdx(idx);
            setTimeout(() => setCopiedIdx((cur) => (cur === idx ? null : cur)), 1500);
        }, () => undefined);
    }, []);
    const visibleAssistants = category === 'all' ? assistants : assistants.filter((a) => a.category === category);
    const categories = ['all', ...Array.from(new Set(assistants.map((a) => a.category)))];
    const activeConv = conversations.find((c) => c.id === conversationId);
    return ((0, jsx_runtime_1.jsxs)("div", { className: "gs-shell-glow flex min-h-screen flex-col", style: { background: C.bg, color: C.text }, children: [(0, jsx_runtime_1.jsx)("style", { children: `
        .gs-scroll::-webkit-scrollbar { width: 8px; height: 8px; }
        .gs-scroll::-webkit-scrollbar-thumb { background: ${C.outline}; border-radius: 9999px; }
        .gs-scroll::-webkit-scrollbar-thumb:hover { background: ${C.accentDeep}; }
        .gs-scroll::-webkit-scrollbar-track { background: transparent; }
        .gs-card { transition: transform .22s ease, border-color .22s ease, box-shadow .22s ease; box-shadow: 0 12px 30px rgba(181, 91, 47, .08); }
        .gs-card:hover { transform: translateY(-3px); border-color: ${C.accentDeep}; box-shadow: 0 18px 38px rgba(181, 91, 47, .14); }
        .gs-card .gs-hairline { transform: scaleX(0); transform-origin: left; transition: transform .25s ease; }
        .gs-card:hover .gs-hairline { transform: scaleX(1); }
        .gs-shell-glow { background: radial-gradient(circle at 12% 0%, rgba(255, 181, 109, .30), transparent 34%), radial-gradient(circle at 92% 8%, rgba(255, 132, 83, .16), transparent 30%); }
        .gs-glass { box-shadow: 0 16px 40px rgba(181, 91, 47, .10); backdrop-filter: blur(18px); }
        .gs-soft-ring { box-shadow: 0 0 0 8px rgba(255, 145, 66, .10), 0 16px 34px rgba(181, 91, 47, .14); }
      ` }), (0, jsx_runtime_1.jsx)("header", { className: "sticky top-0 z-20 border-b px-6 py-4 gs-glass", style: { borderColor: C.outline, background: 'rgba(255,251,248,.78)', backdropFilter: 'blur(18px)' }, children: (0, jsx_runtime_1.jsxs)("div", { className: "mx-auto flex max-w-6xl items-center gap-4", children: [(0, jsx_runtime_1.jsx)("div", { className: "gs-soft-ring flex h-11 w-11 items-center justify-center rounded-2xl font-serif text-lg font-semibold", style: { background: C.container, color: C.accent }, children: "G" }), (0, jsx_runtime_1.jsxs)("div", { className: "flex-1", children: [(0, jsx_runtime_1.jsx)("h1", { className: "font-serif text-xl font-semibold leading-tight", children: "GS AI" }), (0, jsx_runtime_1.jsx)("p", { className: "text-xs", style: { color: C.muted }, children: "Grapsee Agency \u00B7 platform command centre" })] }), (0, jsx_runtime_1.jsx)("a", { href: "/benchmark-comparison.html", className: "hidden rounded-full border px-3.5 py-1.5 text-xs font-medium transition-transform active:scale-95 sm:block", style: { borderColor: C.outline, color: C.text }, "aria-label": "Open benchmark comparison page", children: "Benchmarks" }), (0, jsx_runtime_1.jsx)("button", { onClick: newChat, className: "mr-1 hidden rounded-full border px-3.5 py-1.5 text-xs font-medium transition-transform active:scale-95 sm:block", style: { borderColor: C.outline, color: C.text }, children: "+ New chat" }), (0, jsx_runtime_1.jsxs)("span", { className: "inline-flex items-center gap-2 rounded-full border px-3 py-1.5 text-xs font-medium", style: {
                                borderColor: health === 'ok' ? C.accent : C.outline,
                                color: health === 'ok' ? C.accent : C.muted,
                                background: health === 'ok' ? 'rgba(255,145,66,0.10)' : 'transparent',
                            }, children: [(0, jsx_runtime_1.jsx)("span", { className: `h-2 w-2 rounded-full ${health === 'ok' ? 'animate-pulse' : ''}`, style: { background: health === 'ok' ? C.accent : C.muted } }), health === 'ok' ? 'API live' : health === 'checking' ? 'Checking…' : 'API down'] })] }) }), (0, jsx_runtime_1.jsxs)("main", { className: "mx-auto w-full max-w-6xl flex-1 px-6 py-8", children: [(0, jsx_runtime_1.jsxs)("section", { className: "mb-8", children: [(0, jsx_runtime_1.jsxs)("h2", { className: "max-w-2xl font-serif text-4xl font-semibold leading-[1.05] sm:text-5xl", children: ["Intelligence reimagined.", (0, jsx_runtime_1.jsx)("br", {}), (0, jsx_runtime_1.jsx)("span", { style: { color: C.accentDeep }, children: "Discover a softer way to think." })] }), (0, jsx_runtime_1.jsx)("p", { className: "mt-3 max-w-xl text-sm leading-relaxed", style: { color: C.muted }, children: "Ask, create, explore, and get things done with your personal GS AI companion." })] }), (0, jsx_runtime_1.jsxs)("section", { className: "mb-8 grid grid-cols-2 gap-4 lg:grid-cols-4", children: [(0, jsx_runtime_1.jsx)(StatCard, { label: "Conversations", value: String(conversations.length), sub: "recent" }), (0, jsx_runtime_1.jsx)(StatCard, { label: "Assistants", value: String(assistants.length), sub: "live catalogue" }), (0, jsx_runtime_1.jsx)(StatCard, { label: "Clients", value: "2", sub: "Android \u00B7 iOS (Aeruo Kinetic)" })] }), (0, jsx_runtime_1.jsxs)("div", { className: "grid gap-6 lg:grid-cols-5", children: [(0, jsx_runtime_1.jsx)("section", { className: "space-y-6 lg:col-span-2", children: (0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(SectionHeader, { title: "Recent conversations", hint: "click to reopen" }), (0, jsx_runtime_1.jsxs)("div", { className: "gs-scroll max-h-[22rem] space-y-2 overflow-y-auto pr-1", children: [conversations.map((c) => {
                                                    const active = c.id === conversationId;
                                                    return ((0, jsx_runtime_1.jsxs)("div", { className: "group relative", children: [(0, jsx_runtime_1.jsxs)("button", { onClick: () => openConversation(c), "aria-current": active ? 'true' : undefined, className: "w-full rounded-xl border px-4 py-3 text-left transition-colors", style: {
                                                                    background: active ? C.raised : C.container,
                                                                    borderColor: active ? C.accentDeep : C.outline,
                                                                    borderLeft: active ? `2px solid ${C.accent}` : `2px solid transparent`,
                                                                }, children: [(0, jsx_runtime_1.jsxs)("span", { className: "flex items-center gap-2 pr-7", children: [c.pinned && ((0, jsx_runtime_1.jsx)("span", { className: "rounded px-1 py-0.5 text-[9px] font-semibold uppercase tracking-wide", style: { background: 'rgba(255,145,66,0.12)', color: C.accent }, children: "pinned" })), c.assistantName && ((0, jsx_runtime_1.jsx)("span", { className: "truncate rounded px-1 py-0.5 text-[9px] font-semibold uppercase tracking-wide", style: { background: C.raised, color: C.muted }, children: c.assistantName }))] }), (0, jsx_runtime_1.jsx)("span", { className: "block truncate pr-7 text-sm font-medium", children: c.title }), (0, jsx_runtime_1.jsx)("span", { className: "block text-xs", style: { color: C.muted }, children: relTime(c.updatedAt) })] }), (0, jsx_runtime_1.jsx)("button", { onClick: (e) => deleteConversation(c.id, e), "aria-label": `Delete ${c.title}`, className: "absolute right-2 top-2.5 hidden h-7 w-7 items-center justify-center rounded-lg border text-xs transition-colors hover:border-[#E5484D] hover:text-[#E5484D] group-hover:flex", style: { background: C.surface, borderColor: C.outline, color: C.muted }, children: "\u2715" })] }, c.id));
                                                }), conversations.length === 0 && ((0, jsx_runtime_1.jsx)("p", { className: "text-sm", style: { color: C.muted }, children: "No conversations yet \u2014 say hello on the right." }))] })] }) }), (0, jsx_runtime_1.jsxs)("section", { className: "flex flex-col rounded-2xl border lg:col-span-3", style: { background: C.surface, borderColor: C.outline }, children: [(0, jsx_runtime_1.jsxs)("div", { className: "flex items-center gap-2 border-b px-5 py-4", style: { borderColor: C.outline }, children: [(0, jsx_runtime_1.jsx)("span", { className: "font-serif text-lg font-semibold", children: activeConv?.title ?? 'Live streaming chat' }), activeAssistant && ((0, jsx_runtime_1.jsx)("span", { className: "inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-[10px] font-medium", style: { borderColor: C.accentDeep, color: C.accent, background: 'rgba(255,145,66,0.10)' }, children: activeAssistant.name })), (0, jsx_runtime_1.jsx)("div", { className: "flex-1" }), isStreaming && ((0, jsx_runtime_1.jsx)("button", { onClick: stop, className: "rounded-full border px-3 py-1 text-xs font-medium transition-transform active:scale-95", style: { borderColor: C.accent, color: C.accent }, children: "Stop generating" }))] }), (0, jsx_runtime_1.jsxs)("div", { ref: scrollRef, className: "gs-scroll flex-1 space-y-4 overflow-y-auto p-5", style: { maxHeight: 520 }, children: [historyLoading && ((0, jsx_runtime_1.jsx)("div", { className: "space-y-3 py-2", "aria-label": "Loading conversation", children: [80, 55, 70].map((w, i) => ((0, jsx_runtime_1.jsx)("div", { className: "flex justify-start", children: (0, jsx_runtime_1.jsx)("div", { className: "h-9 animate-pulse rounded-2xl", style: { width: `${w}%`, background: C.raised } }) }, i))) })), !historyLoading && messages.length === 0 && !streamText && !isStreaming && ((0, jsx_runtime_1.jsxs)("div", { className: "py-10 text-center", children: [(0, jsx_runtime_1.jsx)("div", { className: "mx-auto mb-3 flex h-14 w-14 items-center justify-center rounded-full", style: { background: C.container }, children: (0, jsx_runtime_1.jsx)("span", { className: "text-xs font-semibold", style: { color: C.accent }, "aria-hidden": "true", children: "GS" }) }), (0, jsx_runtime_1.jsx)("p", { className: "font-serif text-lg", children: activeAssistant ? `Chatting with ${activeAssistant.name}` : 'Ask anything' }), (0, jsx_runtime_1.jsx)("p", { className: "mt-1 text-sm", style: { color: C.muted }, children: activeAssistant
                                                            ? 'This conversation follows the assistant persona — its instructions steer every reply.'
                                                            : 'Try “today’s technology news” — you will see the real research trace: what was searched, opened, read and cited.' })] })), !historyLoading &&
                                                messages.map((m, i) => m.role === 'user' ? ((0, jsx_runtime_1.jsx)("div", { className: "flex justify-end", children: (0, jsx_runtime_1.jsx)("div", { className: "max-w-[85%] rounded-2xl rounded-br-md px-4 py-3 text-sm leading-relaxed whitespace-pre-wrap", style: { background: 'rgba(255,145,66,0.12)', color: C.text }, children: m.content }) }, i)) : ((0, jsx_runtime_1.jsxs)("div", { className: "group", children: [(0, jsx_runtime_1.jsx)("div", { className: "flex justify-start", children: (0, jsx_runtime_1.jsxs)("div", { className: "max-w-[92%] rounded-2xl rounded-bl-md px-4 py-3 text-sm leading-relaxed whitespace-pre-wrap", style: {
                                                                    background: C.raised,
                                                                    color: C.text,
                                                                    border: `1px solid ${C.outline}`,
                                                                }, children: [(0, jsx_runtime_1.jsx)(genui_1.MessageContent, { text: m.content, dark: true, onPrompt: (prompt) => sendRef.current(prompt), citations: m.sources?.map((src) => ({
                                                                            ordinal: src.ordinal,
                                                                            title: src.title,
                                                                            url: src.url,
                                                                            domain: src.domain,
                                                                        })) ?? undefined }), (0, jsx_runtime_1.jsx)("span", { className: "mt-2 flex items-center gap-1.5 opacity-0 transition-opacity group-hover:opacity-100", children: (0, jsx_runtime_1.jsxs)("button", { onClick: () => copyMessage(i, m.content), "aria-label": "Copy reply", className: "inline-flex items-center gap-1 rounded-md border px-1.5 py-0.5 text-[10px] font-medium transition-colors", style: { borderColor: C.outline, color: copiedIdx === i ? C.accent : C.muted }, children: [(0, jsx_runtime_1.jsx)(CopyIcon, {}), copiedIdx === i ? 'Copied' : 'Copy'] }) })] }) }), m.traceSummary && ((0, jsx_runtime_1.jsxs)("p", { className: "mt-1.5 pl-1 text-[11px]", style: { color: C.muted }, children: [(0, jsx_runtime_1.jsx)("span", { style: { color: C.accent }, children: "\u2713" }), " ", m.traceSummary] })), m.sources && m.sources.length > 0 && (0, jsx_runtime_1.jsx)(SourceCards, { sources: m.sources }), m.clarifyOptions && ((0, jsx_runtime_1.jsx)("div", { className: "mt-2", children: (0, jsx_runtime_1.jsx)(ClarifyChips, { clarify: m.clarifyOptions, onPick: (label) => sendRef.current(label) }) }))] }, i))), isStreaming && trace.length > 0 && ((0, jsx_runtime_1.jsx)(SearchTracePanel, { steps: trace, phase: phase })), isStreaming && trace.length > 0 && liveSources.length > 0 && ((0, jsx_runtime_1.jsx)(SourceCards, { sources: liveSources })), isStreaming && clarify && ((0, jsx_runtime_1.jsx)(ClarifyChips, { clarify: clarify, onPick: (label) => sendRef.current(label) })), reasoningText && ((0, jsx_runtime_1.jsx)("div", { className: "flex justify-start", children: (0, jsx_runtime_1.jsxs)("div", { className: "w-full max-w-[92%] overflow-hidden rounded-2xl", style: { border: `1px solid ${C.outline}`, background: C.container }, children: [(0, jsx_runtime_1.jsxs)("button", { onClick: () => setReasoningOpen((o) => !o), "aria-expanded": reasoningOpen, className: "flex w-full items-center gap-2 px-4 py-2.5 text-left transition-colors hover:opacity-80", children: [(0, jsx_runtime_1.jsx)("svg", { width: "12", height: "12", viewBox: "0 0 24 24", fill: "none", stroke: "currentColor", strokeWidth: "2", strokeLinecap: "round", strokeLinejoin: "round", className: `shrink-0 transition-transform duration-200 ${reasoningOpen ? 'rotate-90' : ''}`, style: { color: C.muted }, "aria-hidden": "true", children: (0, jsx_runtime_1.jsx)("path", { d: "m9 18 6-6-6-6" }) }), (0, jsx_runtime_1.jsx)("span", { className: "text-xs font-medium", style: { color: C.muted }, children: reasoningStart && reasoningEnd
                                                                        ? `Thought for ${((reasoningEnd - reasoningStart) / 1000).toFixed(1)}s`
                                                                        : 'Thinking…' })] }), reasoningOpen && ((0, jsx_runtime_1.jsx)("div", { className: "max-h-64 overflow-y-auto whitespace-pre-wrap px-4 pb-3 text-xs leading-relaxed", style: { color: C.muted, opacity: 0.9 }, children: reasoningText }))] }) })), streamText && ((0, jsx_runtime_1.jsx)("div", { className: "flex justify-start", children: (0, jsx_runtime_1.jsxs)("div", { className: "max-w-[92%] rounded-2xl rounded-bl-md px-4 py-3 text-sm leading-relaxed whitespace-pre-wrap", style: { background: C.raised, color: C.text, border: `1px solid ${C.outline}` }, children: [(0, jsx_runtime_1.jsx)(genui_1.MessageContent, { text: streamText, dark: true, streaming: true, onPrompt: (prompt) => sendRef.current(prompt) }), (0, jsx_runtime_1.jsx)("span", { className: "ml-0.5 inline-block h-4 w-0.5 animate-pulse align-middle", style: { background: C.accent } })] }) })), isStreaming && !streamText && trace.length === 0 && ((0, jsx_runtime_1.jsx)("div", { className: "flex justify-start", children: (0, jsx_runtime_1.jsx)("div", { className: "h-2 w-40 animate-pulse rounded-full", style: { background: AURORA } }) })), error && ((0, jsx_runtime_1.jsx)("div", { className: "rounded-xl border px-4 py-3 text-sm", style: {
                                                    borderColor: C.danger,
                                                    color: C.danger,
                                                    background: 'rgba(229,72,77,0.06)',
                                                }, role: "alert", children: error }))] }), (0, jsx_runtime_1.jsxs)("div", { className: "border-t p-4", style: { borderColor: C.outline }, children: [pendingAttachments.length > 0 && ((0, jsx_runtime_1.jsx)("div", { className: "mb-2 flex flex-wrap gap-1.5 pl-1", children: pendingAttachments.map((a) => ((0, jsx_runtime_1.jsxs)("span", { className: "inline-flex max-w-full items-center gap-1.5 rounded-full border px-2.5 py-1 text-[11px]", style: { borderColor: C.outline, color: a.uploading ? C.muted : C.text, background: C.raised }, children: [a.kind === 'image' ? 'Image' : 'File', " ", (0, jsx_runtime_1.jsx)("span", { className: "truncate", children: a.displayName }), a.uploading ? ((0, jsx_runtime_1.jsx)("span", { className: "animate-pulse", style: { color: C.accent }, children: "\u00B7\u00B7\u00B7" })) : ((0, jsx_runtime_1.jsx)("button", { onClick: () => setPendingAttachments((prev) => prev.filter((x) => x.id !== a.id)), "aria-label": `Remove ${a.displayName}`, className: "ml-0.5 transition-transform active:scale-90", style: { color: C.muted }, children: "\u2715" }))] }, a.id))) })), (0, jsx_runtime_1.jsxs)("div", { className: "flex items-center gap-2 rounded-full border px-4 py-2", style: { background: C.container, borderColor: C.outline }, children: [(0, jsx_runtime_1.jsx)("button", { onClick: () => setSheetOpen((v) => !v), "aria-expanded": sheetOpen, "aria-label": "Open options sheet \u2014 mode, attachments and web", className: "flex h-8 w-8 shrink-0 items-center justify-center rounded-full border text-base leading-none transition-all active:scale-90", style: {
                                                            borderColor: sheetOpen || mode !== 'flash' ? C.accent : C.outline,
                                                            color: sheetOpen || mode !== 'flash' ? C.accent : C.muted,
                                                            background: sheetOpen ? 'rgba(255,145,66,0.10)' : 'transparent',
                                                        }, children: "+" }), mode !== 'flash' && ((0, jsx_runtime_1.jsxs)("button", { onClick: () => setSheetOpen(true), "aria-label": `Mode: ${mode}. Open options sheet`, className: "inline-flex h-8 shrink-0 items-center gap-1 rounded-full border px-2.5 text-[11px] font-medium transition-all active:scale-95", style: {
                                                            borderColor: mode === 'thinking' ? C.accent : C.outline,
                                                            color: mode === 'thinking' ? C.accent : C.muted,
                                                            background: mode === 'thinking' ? 'rgba(255,145,66,0.10)' : 'transparent',
                                                        }, children: [mode === 'thinking' ? 'Thinking' : 'Auto', " \u25BE"] })), (0, jsx_runtime_1.jsx)("input", { value: draft, onChange: (e) => setDraft(e.target.value), onKeyDown: (e) => {
                                                            if (e.key === 'Enter' && !e.shiftKey) {
                                                                e.preventDefault();
                                                                send();
                                                            }
                                                        }, placeholder: activeAssistant ? `Message ${activeAssistant.name}…` : 'Ask anything…', className: "min-w-0 flex-1 bg-transparent text-sm outline-none", style: { color: C.text }, "aria-label": "Message GS AI" }), (0, jsx_runtime_1.jsx)("button", { onClick: () => send(), disabled: !draft.trim() || isStreaming, className: "rounded-full px-3.5 py-1.5 text-xs font-semibold transition-all hover:shadow-[0_0_14px_rgba(255,145,66,0.35)] active:scale-95 disabled:opacity-40", style: { background: C.accent, color: '#3A1B10' }, children: "Send" })] }), sheetOpen && ((0, jsx_runtime_1.jsx)("div", { className: "relative z-20", children: (0, jsx_runtime_1.jsxs)("div", { className: "mt-2 rounded-2xl border p-4 shadow-[0_18px_50px_rgba(0,0,0,0.45)]", style: { background: C.surface, borderColor: C.outline }, role: "dialog", "aria-label": "Composer options", children: [(0, jsx_runtime_1.jsx)("p", { className: "mb-2 text-[11px] font-semibold uppercase tracking-wider", style: { color: C.muted }, children: "Attach" }), (0, jsx_runtime_1.jsxs)("div", { className: "flex flex-wrap gap-2", children: [(0, jsx_runtime_1.jsx)("button", { onClick: () => fileInputRef.current?.click(), className: "rounded-full border px-3 py-1.5 text-xs font-medium transition-all active:scale-95", style: { borderColor: C.outline, color: C.text }, children: "File" }), (0, jsx_runtime_1.jsx)("button", { onClick: () => imageInputRef.current?.click(), className: "rounded-full border px-3 py-1.5 text-xs font-medium transition-all active:scale-95", style: { borderColor: C.outline, color: C.text }, children: "Image" }), (0, jsx_runtime_1.jsxs)("label", { className: "cursor-pointer rounded-full border px-3 py-1.5 text-xs font-medium transition-all active:scale-95 sm:hidden", style: { borderColor: C.outline, color: C.text }, children: ["\uD83D\uDCF7 Camera", (0, jsx_runtime_1.jsx)("input", { type: "file", accept: "image/*", capture: "environment", className: "hidden", onChange: (e) => {
                                                                                void uploadFiles(e.target.files);
                                                                                e.target.value = '';
                                                                            } })] })] }), (0, jsx_runtime_1.jsx)("input", { ref: fileInputRef, type: "file", multiple: true, className: "hidden", onChange: (e) => {
                                                                void uploadFiles(e.target.files);
                                                                e.target.value = '';
                                                            } }), (0, jsx_runtime_1.jsx)("input", { ref: imageInputRef, type: "file", accept: "image/*", multiple: true, className: "hidden", onChange: (e) => {
                                                                void uploadFiles(e.target.files);
                                                                e.target.value = '';
                                                            } }), (0, jsx_runtime_1.jsx)("div", { className: "my-4 h-px", style: { background: C.outline } }), (0, jsx_runtime_1.jsx)("p", { className: "mb-2 text-[11px] font-semibold uppercase tracking-wider", style: { color: C.muted }, children: "Mode" }), (0, jsx_runtime_1.jsx)("div", { className: "grid gap-1.5", children: Object.keys(MODE_META).map((m) => {
                                                                const active = mode === m;
                                                                return ((0, jsx_runtime_1.jsxs)("button", { onClick: () => changeMode(m), "aria-pressed": active, className: "flex items-center gap-3 rounded-xl border px-3 py-2.5 text-left transition-all active:scale-[0.99]", style: {
                                                                        borderColor: active ? C.accent : C.outline,
                                                                        background: active ? 'rgba(255,145,66,0.08)' : 'transparent',
                                                                    }, children: [(0, jsx_runtime_1.jsx)("span", { className: "flex h-4 w-4 shrink-0 items-center justify-center rounded-full border", style: { borderColor: active ? C.accent : C.muted }, children: active && (0, jsx_runtime_1.jsx)("span", { className: "h-2 w-2 rounded-full", style: { background: C.accent } }) }), (0, jsx_runtime_1.jsxs)("span", { className: "min-w-0", children: [(0, jsx_runtime_1.jsx)("span", { className: "block text-sm font-medium", style: { color: C.text }, children: MODE_META[m].label }), (0, jsx_runtime_1.jsxs)("span", { className: "block text-[11px]", style: { color: C.muted }, children: [MODE_META[m].hint, m === 'thinking' ? ' · applies to this chat' : m === 'auto' ? ' · router decides' : ''] })] })] }, m));
                                                            }) }), (0, jsx_runtime_1.jsx)("p", { className: "mb-2 mt-4 text-[11px] font-semibold uppercase tracking-wider", style: { color: C.muted }, children: "Web" }), (0, jsx_runtime_1.jsxs)("div", { className: "flex items-center justify-between rounded-xl border px-3 py-2.5", style: { borderColor: C.outline }, children: [(0, jsx_runtime_1.jsx)("span", { className: "text-sm", style: { color: C.text }, children: "Web search" }), (0, jsx_runtime_1.jsx)("span", { className: "text-[11px]", style: { color: C.muted }, children: "Auto \u2014 the backend gate decides when to search" })] })] }) }))] })] })] }), (0, jsx_runtime_1.jsxs)("section", { className: "mt-10", children: [(0, jsx_runtime_1.jsx)(SectionHeader, { title: "Assistant catalogue", hint: "Prisma-backed \u00B7 start one below" }), (0, jsx_runtime_1.jsx)("div", { className: "gs-scroll mb-4 flex gap-2 overflow-x-auto pb-1", children: categories.map((cat) => {
                                    const active = cat === category;
                                    return ((0, jsx_runtime_1.jsx)("button", { onClick: () => setCategory(cat), "aria-pressed": active, className: "shrink-0 rounded-full border px-3.5 py-1.5 text-xs font-medium transition-all active:scale-95", style: {
                                            borderColor: active ? C.accent : C.outline,
                                            color: active ? C.accent : C.muted,
                                            background: active ? 'rgba(255,145,66,0.10)' : 'transparent',
                                        }, children: cat === 'all' ? 'All' : (CATEGORY_LABELS[cat] ?? cat) }, cat));
                                }) }), (0, jsx_runtime_1.jsxs)("div", { className: "grid gap-4 sm:grid-cols-2 lg:grid-cols-3", children: [visibleAssistants.map((a) => ((0, jsx_runtime_1.jsxs)("div", { className: "gs-card relative overflow-hidden rounded-2xl border p-4", style: { background: C.surface, borderColor: C.outline }, children: [(0, jsx_runtime_1.jsx)("div", { className: "gs-hairline absolute inset-x-0 top-0 h-[2px]", style: { background: AURORA } }), (0, jsx_runtime_1.jsxs)("div", { className: "flex items-start gap-3", children: [(0, jsx_runtime_1.jsx)("span", { className: "flex h-10 w-10 shrink-0 items-center justify-center rounded-xl font-serif text-sm font-semibold", style: { background: C.container, color: C.accent }, children: a.name.slice(0, 1) }), (0, jsx_runtime_1.jsxs)("div", { className: "min-w-0 flex-1", children: [(0, jsx_runtime_1.jsx)("p", { className: "truncate text-sm font-semibold", children: a.name }), (0, jsx_runtime_1.jsxs)("p", { className: "text-[11px]", style: { color: C.muted }, children: [CATEGORY_LABELS[a.category] ?? a.category, " \u00B7 ", a.rating.toFixed(1), " \u00B7 ", fmtUses(a.uses), " uses"] })] }), (0, jsx_runtime_1.jsx)("button", { onClick: () => toggleFavourite(a), "aria-label": a.favourite ? `Unfavourite ${a.name}` : `Favourite ${a.name}`, "aria-pressed": a.favourite, className: "p-1 transition-transform active:scale-90", style: { color: a.favourite ? C.accent : C.muted }, children: (0, jsx_runtime_1.jsx)(StarIcon, { filled: a.favourite }) })] }), (0, jsx_runtime_1.jsx)("p", { className: "mt-3 line-clamp-2 min-h-[2.5rem] text-xs leading-relaxed", style: { color: C.muted }, children: a.description }), a.starters.length > 0 && ((0, jsx_runtime_1.jsx)("div", { className: "mt-3 flex flex-wrap gap-1.5", children: a.starters.slice(0, 2).map((s) => ((0, jsx_runtime_1.jsx)("button", { onClick: () => startAssistantChat(a, s), className: "max-w-full truncate rounded-full border px-2.5 py-1 text-[11px] transition-colors hover:border-[#FF9142]", style: { borderColor: C.outline, color: C.muted }, children: s }, s))) })), (0, jsx_runtime_1.jsx)("button", { onClick: () => startAssistantChat(a), disabled: startingChat === a.id || isStreaming, className: "mt-3 w-full rounded-xl border py-2 text-xs font-semibold transition-all active:scale-[0.98] disabled:opacity-50", style: { borderColor: C.accentDeep, color: C.accent, background: 'rgba(255,145,66,0.07)' }, children: startingChat === a.id ? 'Opening…' : 'Start chat' })] }, a.id))), assistants.length === 0 && ((0, jsx_runtime_1.jsx)("p", { className: "text-sm", style: { color: C.muted }, children: "Catalogue loading\u2026" }))] })] })] }), (0, jsx_runtime_1.jsx)("footer", { className: "mt-auto border-t px-6 py-5", style: { borderColor: C.outline }, children: (0, jsx_runtime_1.jsxs)("div", { className: "mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-2 text-xs", style: { color: C.muted }, children: [(0, jsx_runtime_1.jsx)("span", { children: "\u00A9 Grapsee Agency \u2014 The GS AI App" }), (0, jsx_runtime_1.jsx)("span", { children: "Aeruo Kinetic \u00B7 obsidian surfaces \u00B7 aurora accent \u00B7 one contract, two native clients" })] }) })] }));
}
/* ---------- shared building blocks (unchanged design) ---------- */
function StatCard({ label, value, sub }) {
    return ((0, jsx_runtime_1.jsxs)("div", { className: "rounded-2xl border p-4 transition-colors hover:border-[#FF9142]/40", style: { background: C.surface, borderColor: C.outline }, children: [(0, jsx_runtime_1.jsx)("p", { className: "text-xs font-medium uppercase tracking-wider", style: { color: C.muted }, children: label }), (0, jsx_runtime_1.jsx)("p", { className: "mt-1 font-serif text-2xl font-semibold", style: { color: C.text }, children: value }), sub && ((0, jsx_runtime_1.jsx)("p", { className: "mt-0.5 text-xs", style: { color: C.muted }, children: sub }))] }));
}
function SectionHeader({ title, hint }) {
    return ((0, jsx_runtime_1.jsxs)("div", { className: "mb-3 flex items-baseline justify-between gap-3", children: [(0, jsx_runtime_1.jsx)("h3", { className: "font-serif text-lg font-semibold", children: title }), hint && ((0, jsx_runtime_1.jsx)("span", { className: "text-xs", style: { color: C.muted }, children: hint }))] }));
}
function CopyIcon() {
    return ((0, jsx_runtime_1.jsxs)("svg", { width: "13", height: "13", viewBox: "0 0 24 24", fill: "none", stroke: "currentColor", strokeWidth: "2", "aria-hidden": "true", children: [(0, jsx_runtime_1.jsx)("rect", { x: "9", y: "9", width: "12", height: "12", rx: "2" }), (0, jsx_runtime_1.jsx)("path", { d: "M5 15V5a2 2 0 0 1 2-2h10" })] }));
}
function StarIcon({ filled }) {
    return ((0, jsx_runtime_1.jsx)("svg", { width: "15", height: "15", viewBox: "0 0 24 24", fill: filled ? 'currentColor' : 'none', stroke: "currentColor", strokeWidth: "1.8", "aria-hidden": "true", children: (0, jsx_runtime_1.jsx)("path", { d: "M12 2.5l2.9 6 6.6.9-4.8 4.6 1.2 6.5-5.9-3.2-5.9 3.2 1.2-6.5L2.5 9.4l6.6-.9z", strokeLinejoin: "round" }) }));
}
