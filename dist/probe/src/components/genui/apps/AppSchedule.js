"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.AppSchedule = AppSchedule;
const jsx_runtime_1 = require("react/jsx-runtime");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const AppShell_1 = require("./AppShell");
const EmptyState_1 = require("../primitives/EmptyState");
/** Schedule — a day timeline of appointments. */
function AppSchedule({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const items = (payload.items ?? []).slice(0, 12);
    if (!items.length)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "Nothing scheduled" });
    return ((0, jsx_runtime_1.jsx)(AppShell_1.AppShell, { title: payload.title, children: (0, jsx_runtime_1.jsxs)("div", { style: { position: 'relative', paddingLeft: 46 }, children: [(0, jsx_runtime_1.jsx)("span", { "aria-hidden": true, style: { position: 'absolute', left: 38, top: 8, bottom: 8, width: 2, background: theme.border } }), items.map((item, i) => ((0, jsx_runtime_1.jsxs)("div", { style: { position: 'relative', paddingBottom: 12 }, children: [(0, jsx_runtime_1.jsx)("span", { style: { position: 'absolute', left: -46, top: 2, fontSize: tokens_1.TYPE.micro, color: theme.text3, fontVariantNumeric: 'tabular-nums', width: 34, textAlign: 'right' }, children: String(item.date ?? item.meta ?? '') }), (0, jsx_runtime_1.jsx)("span", { "aria-hidden": true, style: { position: 'absolute', left: -12, top: 6, width: 9, height: 9, borderRadius: 999, background: i === 0 ? accent : theme.raised, border: `2px solid ${accent}` } }), (0, jsx_runtime_1.jsxs)("div", { style: { padding: '8px 12px', borderRadius: 10, background: (0, tokens_1.alpha)(accent, theme.dark ? 0.08 : 0.05), border: `1px solid ${(0, tokens_1.alpha)(accent, 0.18)}` }, children: [(0, jsx_runtime_1.jsx)("span", { style: { fontSize: tokens_1.TYPE.label, fontWeight: 600, color: theme.text }, children: String(item.title ?? '') }), item.subtitle ? (0, jsx_runtime_1.jsx)("span", { style: { display: 'block', fontSize: tokens_1.TYPE.micro, color: theme.text3, marginTop: 2 }, children: String(item.subtitle) }) : null] })] }, i)))] }) }));
}
