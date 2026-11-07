"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.CandlestickChartView = CandlestickChartView;
const jsx_runtime_1 = require("react/jsx-runtime");
const ChartFrame_1 = require("./ChartFrame");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/** Candlestick — [open, high, low, close] per point; green up, red down. */
function CandlestickChartView({ payload, height = 220 }) {
    const { theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const labels = payload.labels ?? [];
    const data = (payload.series?.[0]?.data ?? [])
        .map((d, i) => ({ label: labels[i] ?? `#${i + 1}`, o: d[0], h: d[1], l: d[2], c: d[3] }))
        .filter((d) => [d.o, d.h, d.l, d.c].every(Number.isFinite));
    if (data.length === 0)
        return (0, jsx_runtime_1.jsx)(ChartFrame_1.ChartFrame, { title: payload.title, seriesNames: [], legend: false, children: () => null });
    const lo = Math.min(...data.map((d) => d.l));
    const hi = Math.max(...data.map((d) => d.h));
    const span = hi - lo || 1;
    const y = (v) => height - 26 - ((v - lo) / span) * (height - 46);
    const UP = '#0CA678';
    const DOWN = '#E03131';
    const slot = 100 / data.length;
    return ((0, jsx_runtime_1.jsx)(ChartFrame_1.ChartFrame, { title: payload.title, unit: payload.unit, legend: false, height: height, children: () => ((0, jsx_runtime_1.jsxs)("svg", { width: "100%", height: height, role: "img", "aria-label": "candlestick chart", children: [[lo, (lo + hi) / 2, hi].map((v, i) => ((0, jsx_runtime_1.jsxs)("g", { children: [(0, jsx_runtime_1.jsx)("line", { x1: 30, x2: "100%", y1: y(v), y2: y(v), stroke: theme.border, strokeDasharray: "3 6" }), (0, jsx_runtime_1.jsx)("text", { x: 2, y: y(v) - 3, fontSize: tokens_1.TYPE.micro, fill: theme.text3, children: v.toFixed(1) })] }, i))), data.map((d, i) => {
                    const cx = `${(i + 0.5) * slot}%`;
                    const up = d.c >= d.o;
                    const color = up ? UP : DOWN;
                    const top = Math.min(y(d.o), y(d.c));
                    const bodyH = Math.max(2, Math.abs(y(d.o) - y(d.c)));
                    const bw = Math.max(3, Math.min(16, (300 / data.length) * 0.55));
                    return ((0, jsx_runtime_1.jsxs)("g", { children: [(0, jsx_runtime_1.jsx)("title", { children: `${d.label}: O ${d.o} H ${d.h} L ${d.l} C ${d.c}` }), (0, jsx_runtime_1.jsx)("line", { x1: cx, x2: cx, y1: y(d.h), y2: y(d.l), stroke: color, strokeWidth: 1.5 }), (0, jsx_runtime_1.jsx)("rect", { x: `calc(${cx} - ${bw / 2}px)`, y: top, width: bw, height: bodyH, rx: 2, fill: up ? 'transparent' : color, stroke: color, strokeWidth: 1.5 }), i % Math.ceil(data.length / 7) === 0 && ((0, jsx_runtime_1.jsx)("text", { x: cx, y: height - 6, textAnchor: "middle", fontSize: tokens_1.TYPE.micro, fill: theme.text3, children: d.label }))] }, i));
                })] })) }));
}
