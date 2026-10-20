"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.BoxPlotChartView = BoxPlotChartView;
const jsx_runtime_1 = require("react/jsx-runtime");
const ChartFrame_1 = require("./ChartFrame");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
function quantile(sorted, q) {
    const pos = (sorted.length - 1) * q;
    const base = Math.floor(pos);
    const rest = pos - base;
    return sorted[base + 1] !== undefined ? sorted[base] + rest * (sorted[base + 1] - sorted[base]) : sorted[base];
}
/** Box plot — median, IQR and whiskers per series from raw samples. */
function BoxPlotChartView({ payload, height = 210 }) {
    const { accent, theme, ramp } = (0, WidgetSurface_1.useWidgetIdentity)();
    const series = payload.series ?? [];
    const labels = payload.labels ?? [];
    const stats = series.map((s, i) => {
        const sorted = s.data.map((d) => d[d.length - 1]).filter(Number.isFinite).sort((a, b) => a - b);
        return {
            name: s.name || labels[i] || `#${i + 1}`,
            min: sorted[0] ?? 0,
            q1: quantile(sorted, 0.25),
            med: quantile(sorted, 0.5),
            q3: quantile(sorted, 0.75),
            max: sorted[sorted.length - 1] ?? 0,
        };
    });
    const lo = Math.min(...stats.map((s) => s.min));
    const hi = Math.max(...stats.map((s) => s.max));
    const span = hi - lo || 1;
    const y = (v) => height - 24 - ((v - lo) / span) * (height - 44);
    const slot = 100 / Math.max(1, stats.length);
    return ((0, jsx_runtime_1.jsx)(ChartFrame_1.ChartFrame, { title: payload.title, unit: payload.unit, legend: false, height: height, children: () => ((0, jsx_runtime_1.jsx)("div", { children: (0, jsx_runtime_1.jsxs)("svg", { width: "100%", height: height, role: "img", "aria-label": "box plot", children: [[lo, (lo + hi) / 2, hi].map((v, i) => ((0, jsx_runtime_1.jsxs)("g", { children: [(0, jsx_runtime_1.jsx)("line", { x1: 0, x2: "100%", y1: y(v), y2: y(v), stroke: theme.border, strokeDasharray: "3 6" }), (0, jsx_runtime_1.jsx)("text", { x: 2, y: y(v) - 3, fontSize: tokens_1.TYPE.micro, fill: theme.text3, children: v.toFixed(1) })] }, i))), stats.map((s, i) => {
                        const cx = `${(i + 0.5) * slot}%`;
                        const w = Math.min(34, (300 / stats.length) * 0.5);
                        const color = ramp(stats.length)[i];
                        return ((0, jsx_runtime_1.jsxs)("g", { children: [(0, jsx_runtime_1.jsx)("line", { x1: cx, x2: cx, y1: y(s.min), y2: y(s.max), stroke: color, strokeWidth: 1.5 }), (0, jsx_runtime_1.jsx)("line", { x1: `calc(${cx} - ${w / 3}px)`, x2: `calc(${cx} + ${w / 3}px)`, y1: y(s.min), y2: y(s.min), stroke: color, strokeWidth: 1.5 }), (0, jsx_runtime_1.jsx)("line", { x1: `calc(${cx} - ${w / 3}px)`, x2: `calc(${cx} + ${w / 3}px)`, y1: y(s.max), y2: y(s.max), stroke: color, strokeWidth: 1.5 }), (0, jsx_runtime_1.jsx)("rect", { x: `calc(${cx} - ${w / 2}px)`, y: y(s.q3), width: w, height: Math.max(2, y(s.q1) - y(s.q3)), rx: 5, fill: (0, tokens_1.alpha)(color, 0.3), stroke: color, strokeWidth: 1.5 }), (0, jsx_runtime_1.jsx)("line", { x1: `calc(${cx} - ${w / 2}px)`, x2: `calc(${cx} + ${w / 2}px)`, y1: y(s.med), y2: y(s.med), stroke: color, strokeWidth: 2.5 }), (0, jsx_runtime_1.jsx)("text", { x: cx, y: height - 6, textAnchor: "middle", fontSize: tokens_1.TYPE.micro, fill: theme.text2, children: s.name })] }, s.name));
                    })] }) })) }));
}
