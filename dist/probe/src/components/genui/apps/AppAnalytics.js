"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.AppAnalytics = AppAnalytics;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const AppShell_1 = require("./AppShell");
const UiSegmented_1 = require("../controls/UiSegmented");
const LineChart_1 = require("../charts/LineChart");
const EmptyState_1 = require("../primitives/EmptyState");
/** Analytics — a range switcher over an inline trend chart + KPI row. */
function AppAnalytics({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const [range, setRange] = (0, react_1.useState)('30d');
    const items = (payload.items ?? []).slice(0, 4);
    if (!items.length)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "No metrics" });
    const factor = range === '7d' ? 0.25 : range === '90d' ? 2.8 : 1;
    const series = [
        {
            name: 'trend',
            data: Array.from({ length: 12 }, (_, i) => [i, Math.round((Number(items[0]?.subtitle ?? items[0]?.meta ?? 100) || 100) * factor * (0.7 + 0.05 * i))]),
        },
    ];
    return ((0, jsx_runtime_1.jsxs)(AppShell_1.AppShell, { title: payload.title, children: [(0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', justifyContent: 'flex-end', marginBottom: 10 }, children: (0, jsx_runtime_1.jsx)(UiSegmented_1.UiSegmented, { options: ['7d', '30d', '90d'], value: range, onChange: setRange }) }), (0, jsx_runtime_1.jsx)("div", { style: { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, marginBottom: 12 }, children: items.map((item, i) => ((0, jsx_runtime_1.jsxs)("div", { style: { padding: '10px 12px', borderRadius: 12, background: (0, tokens_1.alpha)(accent, theme.dark ? 0.08 : 0.05) }, children: [(0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.micro, color: theme.text2 }, children: String(item.title ?? '') }), (0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.heading, fontWeight: 700, color: theme.text }, children: String(item.subtitle ?? '') }), item.badge ? (0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.micro, fontWeight: 700, color: accent }, children: String(item.badge) }) : null] }, i))) }), (0, jsx_runtime_1.jsx)(LineChart_1.LineChartView, { payload: { type: 'line', series, title: undefined }, height: 140 })] }));
}
