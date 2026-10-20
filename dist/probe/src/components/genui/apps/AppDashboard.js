"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.AppDashboard = AppDashboard;
const jsx_runtime_1 = require("react/jsx-runtime");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const AppShell_1 = require("./AppShell");
const EmptyState_1 = require("../primitives/EmptyState");
/** Dashboard — metric tiles grid. */
function AppDashboard({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const items = (payload.items ?? []).slice(0, 8);
    if (!items.length)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "No metrics" });
    return ((0, jsx_runtime_1.jsx)(AppShell_1.AppShell, { title: payload.title, children: (0, jsx_runtime_1.jsx)("div", { style: { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }, children: items.map((item, i) => ((0, jsx_runtime_1.jsxs)("div", { style: { padding: 12, borderRadius: RADIUS_T, background: (0, tokens_1.alpha)(accent, theme.dark ? 0.08 : 0.05), border: `1px solid ${(0, tokens_1.alpha)(accent, 0.15)}` }, children: [(0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.micro, color: theme.text2, marginBottom: 4 }, children: String(item.title ?? '') }), (0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.title, fontWeight: 700, color: theme.text }, children: String(item.subtitle ?? item.meta ?? '') }), item.badge ? (0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.micro, fontWeight: 700, color: accent, marginTop: 3 }, children: String(item.badge) }) : null] }, i))) }) }));
}
const RADIUS_T = 12;
