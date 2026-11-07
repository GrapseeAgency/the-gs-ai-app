"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.AppSearch = AppSearch;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const AppShell_1 = require("./AppShell");
const EmptyState_1 = require("../primitives/EmptyState");
const UiTextInput_1 = require("../controls/UiTextInput");
/** Search — live client-side filtering over a result list. */
function AppSearch({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const items = (payload.items ?? []).slice(0, 40);
    const [q, setQ] = (0, react_1.useState)('');
    if (!items.length)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "No searchable items" });
    const hits = (0, react_1.useMemo)(() => {
        const needle = q.trim().toLowerCase();
        if (!needle)
            return items;
        return items.filter((it) => `${it.title ?? ''} ${it.subtitle ?? ''} ${it.meta ?? ''}`.toLowerCase().includes(needle));
    }, [items, q]);
    return ((0, jsx_runtime_1.jsxs)(AppShell_1.AppShell, { title: payload.title, right: `${hits.length}/${items.length}`, children: [(0, jsx_runtime_1.jsx)(UiTextInput_1.UiTextInput, { placeholder: "Search\u2026", value: q, onChange: setQ, ariaLabel: "search" }), (0, jsx_runtime_1.jsxs)("div", { style: { marginTop: 10, display: 'flex', flexDirection: 'column', gap: 6, maxHeight: 260, overflowY: 'auto' }, children: [hits.map((it, i) => ((0, jsx_runtime_1.jsxs)("div", { style: { padding: '8px 10px', borderRadius: 10, border: `1px solid ${theme.border}` }, children: [(0, jsx_runtime_1.jsx)("span", { style: { display: 'block', fontSize: tokens_1.TYPE.label, fontWeight: 600, color: theme.text }, children: String(it.title ?? '') }), it.subtitle ? (0, jsx_runtime_1.jsx)("span", { style: { display: 'block', fontSize: tokens_1.TYPE.micro, color: theme.text3, marginTop: 2 }, children: String(it.subtitle) }) : null] }, i))), hits.length === 0 && (0, jsx_runtime_1.jsxs)("div", { style: { fontSize: tokens_1.TYPE.label, color: theme.text3, textAlign: 'center', padding: 16 }, children: ["Nothing for \u201C", q, "\u201D"] })] }), q && hits.length > 0 && (0, jsx_runtime_1.jsxs)("div", { style: { marginTop: 6, fontSize: tokens_1.TYPE.micro, color: accent }, children: ["Ask \u201C", q, "\u201D \u2192"] })] }));
}
