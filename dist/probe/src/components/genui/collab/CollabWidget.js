"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.CollabWidget = CollabWidget;
const jsx_runtime_1 = require("react/jsx-runtime");
/**
 * The collab kind — LIVE shared state across everyone viewing the answer.
 *
 * Four surfaces over one SSE room:
 *   counter — taps apply server-side deltas (concurrent taps never lose state)
 *   vote    — shared options, live counts, one local vote per option
 *   todo    — shared checklist (list-replace, LWW: the widget says so)
 *   pointer — presence dots + live cursors only, nothing persisted
 *
 * HONEST TIER 2: the status row always labels it a prototype — per-server
 * memory, last-write-wins, resets when the server restarts.
 */
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const TitleRow_1 = require("../primitives/TitleRow");
const UiButton_1 = require("../controls/UiButton");
const UiTextInput_1 = require("../controls/UiTextInput");
function useCollabRoom(room, me) {
    const [shared, setShared] = (0, react_1.useState)({ status: 'connecting', state: {}, presence: [] });
    const meRef = (0, react_1.useRef)(me);
    (0, react_1.useEffect)(() => {
        meRef.current = me;
    });
    (0, react_1.useEffect)(() => {
        const q = new URLSearchParams({ room, client: me.id, name: me.name, color: me.color });
        const es = new EventSource(`/api/v1/collab?${q}`);
        es.addEventListener('snapshot', (ev) => {
            const d = JSON.parse(ev.data);
            setShared({ status: 'live', state: d.state ?? {}, presence: d.presence ?? [] });
        });
        es.addEventListener('rev', (ev) => {
            const d = JSON.parse(ev.data);
            setShared({ status: 'live', state: d.state ?? {}, presence: d.presence ?? [] });
        });
        es.onopen = () => setShared((s) => ({ ...s, status: 'live' }));
        es.onerror = () => setShared((s) => (s.status === 'live' ? { ...s, status: 'offline' } : s));
        // presence heartbeat — the broker expires us after 30s without one
        const beat = setInterval(() => {
            void fetch('/api/v1/collab', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ room, heartbeat: { id: meRef.current.id, name: meRef.current.name, color: meRef.current.color } }),
            }).catch(() => { });
        }, 10000);
        // first beat immediately so everyone sees us before the first interval
        void fetch('/api/v1/collab', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ room, heartbeat: { id: me.id, name: me.name, color: me.color } }),
        }).catch(() => { });
        return () => {
            clearInterval(beat);
            es.close();
        };
    }, [room, me.id, me.name, me.color]);
    return shared;
}
function usePublish(room) {
    return (0, react_1.useCallback)(async (body) => {
        try {
            const res = await fetch('/api/v1/collab', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ room, ...body }),
            });
            if (!res.ok)
                return null;
            return (await res.json());
        }
        catch {
            return null;
        }
    }, [room]);
}
const GUEST_NAMES = ['Falcon', 'Otter', 'Heron', 'Lynx', 'Marmot', 'Ibis', 'Gecko', 'Tapir'];
function makeMe(rampSeed) {
    const id = Math.random().toString(36).slice(2, 10);
    const name = `${GUEST_NAMES[Math.abs(rampSeed) % GUEST_NAMES.length]}-${id.slice(0, 3)}`;
    const hue = 190 + (Math.abs(rampSeed * 7919) % 140);
    return { id, name, color: `hsl(${hue} 70% 55%)` };
}
function CollabWidget({ payload }) {
    const { ramp } = (0, WidgetSurface_1.useWidgetIdentity)();
    const me = (0, react_1.useMemo)(() => makeMe((payload.room ?? '').length + 1), [payload.room]);
    const room = payload.room ?? 'lobby';
    const shared = useCollabRoom(room, me);
    const publish = usePublish(room);
    const surface = payload.surface ?? 'counter';
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title ?? 'Shared space', right: (0, jsx_runtime_1.jsxs)("span", { style: { display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: tokens_1.TYPE.micro, color: 'inherit' }, children: [shared.presence.slice(0, 4).map((p) => ((0, jsx_runtime_1.jsx)("span", { title: p.name, style: { width: 10, height: 10, borderRadius: 999, background: p.color, display: 'inline-block' } }, p.id))), shared.presence.length > 4 ? `+${shared.presence.length - 4}` : '', shared.presence.length ? ` · ${shared.presence.length} live` : ' · you'] }) }), surface === 'counter' && (0, jsx_runtime_1.jsx)(CounterSurface, { payload: payload, shared: shared, publish: publish }), surface === 'vote' && (0, jsx_runtime_1.jsx)(VoteSurface, { payload: payload, shared: shared, publish: publish }), surface === 'todo' && (0, jsx_runtime_1.jsx)(TodoSurface, { payload: payload, shared: shared, publish: publish }), surface === 'pointer' && (0, jsx_runtime_1.jsx)(PointerSurface, { payload: payload, shared: shared, me: me, publish: publish }), (0, jsx_runtime_1.jsx)(StatusRow, { room: room, status: shared.status, note: payload.note })] }));
}
// ------------------------------------------------------------------ surfaces
function CounterSurface({ payload, shared, publish }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const n = typeof shared.state.n === 'number' ? shared.state.n : (payload.start ?? 0);
    return ((0, jsx_runtime_1.jsxs)("div", { style: { textAlign: 'center', padding: '18px 0 14px' }, children: [(0, jsx_runtime_1.jsx)("div", { style: { fontSize: 54, fontWeight: 800, fontVariantNumeric: 'tabular-nums', color: accent, lineHeight: 1 }, children: n }), (0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.micro, color: theme.text3, margin: '6px 0 12px' }, children: payload.label ?? 'this number is the same for everyone, right now' }), (0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', gap: 6, justifyContent: 'center' }, children: [-1, 1, 5].map((d) => ((0, jsx_runtime_1.jsx)(UiButton_1.UiButton, { buttonStyle: d > 0 ? 'filled' : 'tonal', onClick: () => void publish({ delta: { n: d } }), children: d > 0 ? `+${d}` : d }, d))) })] }));
}
function VoteSurface({ payload, shared, publish }) {
    const { accent, theme, ramp } = (0, WidgetSurface_1.useWidgetIdentity)();
    const items = (payload.items ?? []).slice(0, 12);
    const [mine, setMine] = (0, react_1.useState)(new Set());
    const votes = items.map((_, i) => (typeof shared.state[`v${i}`] === 'number' ? shared.state[`v${i}`] : 0));
    const total = Math.max(1, votes.reduce((a, b) => a + b, 0));
    const leader = votes.indexOf(Math.max(...votes));
    const colors = ramp(Math.max(1, items.length));
    const cast = (i) => {
        if (mine.has(i))
            return;
        setMine((s) => new Set(s).add(i));
        void publish({ delta: { [`v${i}`]: 1 } });
    };
    if (!items.length)
        return (0, jsx_runtime_1.jsx)("div", { style: { padding: 12, color: theme.text3, fontSize: tokens_1.TYPE.micro }, children: "vote needs items" });
    return ((0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', flexDirection: 'column', gap: 6, padding: '4px 0' }, children: items.map((label, i) => ((0, jsx_runtime_1.jsxs)("button", { type: "button", onClick: () => cast(i), disabled: mine.has(i), style: {
                position: 'relative',
                border: `1px solid ${mine.has(i) ? accent : theme.border}`,
                borderRadius: 8,
                background: 'transparent',
                padding: '9px 12px',
                cursor: mine.has(i) ? 'default' : 'pointer',
                overflow: 'hidden',
                textAlign: 'left',
            }, children: [(0, jsx_runtime_1.jsx)("span", { "aria-hidden": true, style: { position: 'absolute', inset: 0, width: `${(votes[i] / total) * 100}%`, background: (0, tokens_1.alpha)(colors[i % colors.length], 0.28), transition: 'width .35s' } }), (0, jsx_runtime_1.jsxs)("span", { style: { position: 'relative', display: 'flex', justifyContent: 'space-between', fontSize: tokens_1.TYPE.body, color: theme.text, fontWeight: i === leader && votes[i] > 0 ? 700 : 500 }, children: [(0, jsx_runtime_1.jsxs)("span", { children: [label, " ", mine.has(i) ? (0, jsx_runtime_1.jsx)("span", { style: { color: accent }, children: "\u00B7 yours" }) : ''] }), (0, jsx_runtime_1.jsx)("span", { style: { fontVariantNumeric: 'tabular-nums', color: theme.text2 }, children: votes[i] })] })] }, i))) }));
}
function TodoSurface({ payload, shared, publish }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const rows = Array.isArray(shared.state.todos) ? shared.state.todos : (payload.items ?? []).map((text) => ({ text }));
    const [draft, setDraft] = (0, react_1.useState)('');
    const replace = (next) => void publish({ set: { todos: next.slice(0, 12) } });
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', gap: 6, marginBottom: 8 }, children: [(0, jsx_runtime_1.jsx)(UiTextInput_1.UiTextInput, { value: draft, onChange: setDraft, ariaLabel: "new shared item", placeholder: "add a shared item\u2026", onKeyDown: (e) => {
                            if (e.key === 'Enter' && draft.trim()) {
                                replace([...rows, { text: draft.trim() }]);
                                setDraft('');
                            }
                        } }), (0, jsx_runtime_1.jsx)(UiButton_1.UiButton, { buttonStyle: "filled", onClick: () => { if (draft.trim()) {
                            replace([...rows, { text: draft.trim() }]);
                            setDraft('');
                        } }, children: "Add" })] }), (0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', flexDirection: 'column', gap: 4 }, children: [rows.length === 0 && (0, jsx_runtime_1.jsx)("div", { style: { color: theme.text3, fontSize: tokens_1.TYPE.micro }, children: "empty \u2014 add something, watch it appear for everyone" }), rows.map((row, i) => ((0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', alignItems: 'center', gap: 8, padding: '6px 8px', borderRadius: 8, border: `1px solid ${theme.border}` }, children: [(0, jsx_runtime_1.jsx)("input", { type: "checkbox", checked: !!row.done, onChange: () => replace(rows.map((r, ii) => (ii === i ? { ...r, done: !r.done } : r))), style: { accentColor: accent } }), (0, jsx_runtime_1.jsx)("span", { style: { flex: 1, fontSize: tokens_1.TYPE.body, color: theme.text, textDecoration: row.done ? 'line-through' : 'none', opacity: row.done ? 0.6 : 1 }, children: row.text }), (0, jsx_runtime_1.jsx)("button", { type: "button", "aria-label": "remove", onClick: () => replace(rows.filter((_, ii) => ii !== i)), style: { border: 'none', background: 'transparent', color: theme.text3, cursor: 'pointer' }, children: "\u00D7" })] }, i)))] })] }));
}
function PointerSurface({ payload, shared, me, publish }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const lastSent = (0, react_1.useRef)(0);
    const boxRef = (0, react_1.useRef)(null);
    const [selfPt, setSelfPt] = (0, react_1.useState)(null);
    const move = (e) => {
        const box = boxRef.current?.getBoundingClientRect();
        if (!box)
            return;
        const x = (e.clientX - box.left) / box.width;
        const y = (e.clientY - box.top) / box.height;
        setSelfPt({ x, y });
        const now = Date.now();
        if (now - lastSent.current < 90)
            return;
        lastSent.current = now;
        void publish({ set: { [`px.${me.id}`]: Math.round(x * 1000) / 1000, [`py.${me.id}`]: Math.round(y * 1000) / 1000 }, heartbeat: { id: me.id, name: me.name, color: me.color } });
    };
    const others = shared.presence.filter((p) => p.id !== me.id);
    const ptOf = (id) => {
        const x = shared.state[`px.${id}`];
        const y = shared.state[`py.${id}`];
        return typeof x === 'number' && typeof y === 'number' ? { x, y } : null;
    };
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsxs)("div", { ref: boxRef, onPointerMove: move, style: {
                    position: 'relative',
                    height: 210,
                    borderRadius: tokens_1.RADIUS.md,
                    border: `1px solid ${theme.border}`,
                    background: theme.dark ? 'rgba(0,0,0,0.28)' : 'rgba(0,0,0,0.03)',
                    overflow: 'hidden',
                    cursor: 'crosshair',
                    touchAction: 'none',
                }, children: [(0, jsx_runtime_1.jsx)("span", { style: { position: 'absolute', left: '46%', top: '44%', fontSize: tokens_1.TYPE.micro, color: theme.text3, pointerEvents: 'none' }, children: payload.label ?? 'move here — everyone sees your cursor' }), others.map((p) => {
                        const pt = ptOf(p.id);
                        if (!pt)
                            return null;
                        return ((0, jsx_runtime_1.jsxs)("span", { style: { position: 'absolute', left: `${pt.x * 100}%`, top: `${pt.y * 100}%`, transform: 'translate(-2px,-2px)', pointerEvents: 'none', transition: 'left .12s linear, top .12s linear' }, children: [(0, jsx_runtime_1.jsx)("svg", { width: "14", height: "16", viewBox: "0 0 14 16", children: (0, jsx_runtime_1.jsx)("path", { d: "M1 1 L13 7 L7 9 L5 15 Z", fill: p.color, stroke: theme.dark ? '#0F1216' : '#fff', strokeWidth: "1" }) }), (0, jsx_runtime_1.jsx)("span", { style: { position: 'absolute', left: 14, top: 12, fontSize: 10, fontWeight: 700, color: '#fff', background: p.color, borderRadius: 999, padding: '1px 7px', whiteSpace: 'nowrap' }, children: p.name })] }, p.id));
                    }), selfPt && ((0, jsx_runtime_1.jsx)("span", { style: { position: 'absolute', left: `${selfPt.x * 100}%`, top: `${selfPt.y * 100}%`, pointerEvents: 'none' }, children: (0, jsx_runtime_1.jsx)("svg", { width: "14", height: "16", viewBox: "0 0 14 16", children: (0, jsx_runtime_1.jsx)("path", { d: "M1 1 L13 7 L7 9 L5 15 Z", fill: accent, stroke: theme.dark ? '#0F1216' : '#fff', strokeWidth: "1" }) }) }))] }), others.length > 0 && ((0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.micro, color: theme.text3, marginTop: 6 }, children: "cursors vanish 30s after someone stops moving (presence TTL) \u2014 nothing here is stored" }))] }));
}
function StatusRow({ room, status, note }) {
    const { theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const dot = status === 'live' ? '#0CA678' : status === 'connecting' ? '#F08C00' : '#E03131';
    return ((0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', alignItems: 'center', gap: 7, marginTop: 10, fontSize: tokens_1.TYPE.micro, color: theme.text3, flexWrap: 'wrap' }, children: [(0, jsx_runtime_1.jsx)("span", { style: { width: 7, height: 7, borderRadius: 999, background: dot, display: 'inline-block' } }), (0, jsx_runtime_1.jsxs)("span", { children: [status, " \u00B7 room ", (0, jsx_runtime_1.jsx)("code", { style: { color: theme.text2 }, children: room }), " \u00B7 shared prototype: per-server memory, last-write-wins, resets on restart"] }), note ? (0, jsx_runtime_1.jsxs)("span", { children: ["\u00B7 ", note] }) : null] }));
}
