"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.AppKanban = AppKanban;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const AppShell_1 = require("./AppShell");
const EmptyState_1 = require("../primitives/EmptyState");
/** Kanban — tap a card to advance it across the columns. */
function AppKanban({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const columns = (payload.columns ?? []).slice(0, 4);
    const [lanes, setLanes] = (0, react_1.useState)(() => columns.map((c) => (c.items ?? []).map((it) => ({ ...it }))));
    if (!columns.length)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "No columns" });
    const advance = (col, item) => {
        if (col >= lanes.length - 1)
            return;
        setLanes((ls) => {
            const next = ls.map((l) => [...l]);
            const [moved] = next[col].splice(item, 1);
            if (moved)
                next[col + 1].push(moved);
            return next;
        });
    };
    return ((0, jsx_runtime_1.jsx)(AppShell_1.AppShell, { title: payload.title, children: (0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', gap: 8, overflowX: 'auto', paddingBottom: 4 }, children: columns.map((c, ci) => ((0, jsx_runtime_1.jsxs)("div", { style: { flex: 1, minWidth: 118 }, children: [(0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', alignItems: 'center', gap: 6, marginBottom: 8 }, children: [(0, jsx_runtime_1.jsx)("span", { style: { fontSize: tokens_1.TYPE.micro, fontWeight: 700, color: theme.text2, textTransform: 'uppercase', letterSpacing: '0.04em' }, children: c.title }), (0, jsx_runtime_1.jsx)("span", { style: { fontSize: tokens_1.TYPE.micro, color: theme.text3, background: theme.dark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.06)', borderRadius: 999, padding: '1px 7px' }, children: lanes[ci]?.length ?? 0 })] }), (0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', flexDirection: 'column', gap: 6, minHeight: 40 }, children: (lanes[ci] ?? []).map((item, ii) => ((0, jsx_runtime_1.jsxs)("button", { type: "button", onClick: () => advance(ci, ii), title: ci < columns.length - 1 ? 'Tap to advance' : 'Done', style: {
                                textAlign: 'left',
                                width: '100%',
                                padding: '9px 10px',
                                borderRadius: tokens_1.RADIUS.md,
                                border: `1px solid ${theme.border}`,
                                background: theme.dark ? 'rgba(255,255,255,0.04)' : '#fff',
                                cursor: 'pointer',
                            }, children: [(0, jsx_runtime_1.jsx)("span", { style: { display: 'block', fontSize: tokens_1.TYPE.micro, fontWeight: 700, color: theme.text }, children: String(item.title ?? '') }), item.subtitle ? (0, jsx_runtime_1.jsx)("span", { style: { display: 'block', fontSize: tokens_1.TYPE.micro, color: theme.text3, marginTop: 2 }, children: String(item.subtitle) }) : null, item.badge ? (0, jsx_runtime_1.jsx)("span", { style: { display: 'inline-block', fontSize: 10, fontWeight: 700, color: accent, marginTop: 4 }, children: String(item.badge) }) : null] }, `${ci}-${ii}`))) })] }, c.title))) }) }));
}
