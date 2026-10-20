"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.HistogramChartView = HistogramChartView;
const jsx_runtime_1 = require("react/jsx-runtime");
const ChartFrame_1 = require("./ChartFrame");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/** Histogram — a distribution of raw samples in bins. */
function HistogramChartView({ payload, height = 190 }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const raw = (payload.series?.[0]?.data ?? []).map((d) => d[d.length - 1]).filter((n) => Number.isFinite(n));
    if (raw.length === 0)
        return (0, jsx_runtime_1.jsx)(ChartFrame_1.ChartFrame, { title: payload.title, seriesNames: [], legend: false, children: () => null });
    const bins = Math.min(12, Math.max(5, Math.ceil(Math.sqrt(raw.length))));
    const min = Math.min(...raw);
    const max = Math.max(...raw);
    const w = (max - min) / bins || 1;
    const counts = Array.from({ length: bins }, (_, i) => ({
        from: min + i * w,
        to: min + (i + 1) * w,
        n: raw.filter((v) => v >= min + i * w && (i === bins - 1 ? v <= max : v < min + (i + 1) * w)).length,
    }));
    const peak = Math.max(...counts.map((c) => c.n));
    const ticks = (0, ChartFrame_1.niceTicks)(min, max, 5);
    return ((0, jsx_runtime_1.jsx)(ChartFrame_1.ChartFrame, { title: payload.title, unit: payload.unit, legend: false, height: height, children: ({ width }) => ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', alignItems: 'flex-end', gap: 3, height }, children: counts.map((c, i) => ((0, jsx_runtime_1.jsxs)("div", { style: { flex: 1, textAlign: 'center' }, children: [(0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.micro, color: theme.text3, marginBottom: 2 }, children: c.n > 0 ? c.n : '' }), (0, jsx_runtime_1.jsx)("div", { style: {
                                    height: Math.max(2, (c.n / peak) * (height - 26)),
                                    background: (0, tokens_1.alpha)(accent, 0.85),
                                    borderRadius: '4px 4px 0 0',
                                }, title: `${c.from.toFixed(1)} – ${c.to.toFixed(1)}: ${c.n}` })] }, i))) }), (0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', justifyContent: 'space-between', fontSize: tokens_1.TYPE.micro, color: theme.text3, marginTop: 4 }, children: ticks.map((t) => ((0, jsx_runtime_1.jsx)("span", { children: t.toFixed(1) }, t))) })] })) }));
}
