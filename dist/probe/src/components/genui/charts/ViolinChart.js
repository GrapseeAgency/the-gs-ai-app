"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.ViolinChartView = ViolinChartView;
const jsx_runtime_1 = require("react/jsx-runtime");
const ChartFrame_1 = require("./ChartFrame");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/** Violin — kernel-density silhouettes per group (Gaussian kernel, Silverman bw). */
function ViolinChartView({ payload, height = 210 }) {
    const { ramp, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const series = (payload.series ?? []).map((s) => ({ name: s.name, samples: s.data.map((d) => d[d.length - 1]) }));
    const h = height ?? 210;
    if (!series.length)
        return (0, jsx_runtime_1.jsx)(ChartFrame_1.ChartFrame, { title: payload.title, seriesNames: [], legend: false, children: () => null });
    const all = series.flatMap((s) => s.samples);
    const lo = Math.min(...all);
    const hi = Math.max(...all);
    const span = hi - lo || 1;
    const colors = ramp(Math.max(1, series.length));
    const W = 320;
    const y = (v) => h - 24 - ((v - lo) / span) * (h - 44);
    return ((0, jsx_runtime_1.jsx)(ChartFrame_1.ChartFrame, { title: payload.title, unit: payload.unit, seriesNames: series.map((s) => s.name), height: h, children: ({ width }) => ((0, jsx_runtime_1.jsxs)("svg", { width: "100%", height: h, viewBox: `0 0 ${W} ${h}`, preserveAspectRatio: "xMidYMid meet", role: "img", "aria-label": "violin plot", children: [[lo, (lo + hi) / 2, hi].map((v, i) => ((0, jsx_runtime_1.jsxs)("g", { children: [(0, jsx_runtime_1.jsx)("line", { x1: 26, x2: W - 6, y1: y(v), y2: y(v), stroke: theme.border, strokeDasharray: "3 6" }), (0, jsx_runtime_1.jsx)("text", { x: 2, y: y(v) - 3, fontSize: tokens_1.TYPE.micro, fill: theme.text3, children: v.toFixed(1) })] }, i))), series.map((s, si) => {
                    const n = s.samples.length;
                    if (n < 3)
                        return null;
                    const sorted = [...s.samples].sort((a, b) => a - b);
                    const sd = Math.sqrt(s.samples.reduce((a, v) => a + (v - s.samples.reduce((x, y2) => x + y2, 0) / n) ** 2, 0) / n) || span / 10;
                    const bw = 1.06 * sd * Math.pow(n, -1 / 5) || span / 20;
                    const cx = ((si + 0.5) / series.length) * W;
                    const steps = 40;
                    const dens = [];
                    let dmax = 0;
                    for (let i = 0; i <= steps; i++) {
                        const v = lo + (i / steps) * span;
                        const d = s.samples.reduce((acc, x) => acc + Math.exp(-0.5 * ((v - x) / bw) ** 2), 0) / (n * bw * 2.5066);
                        dmax = Math.max(dmax, d);
                        dens.push({ v, d });
                    }
                    const halfW = (W / series.length) * 0.42;
                    const pts = dens.map((p) => `${cx + (p.d / dmax) * halfW},${y(p.v)}`);
                    const mirrored = [...dens].reverse().map((p) => `${cx - (p.d / dmax) * halfW},${y(p.v)}`);
                    const med = quantile(sorted, 0.5);
                    return ((0, jsx_runtime_1.jsxs)("g", { children: [(0, jsx_runtime_1.jsx)("polygon", { points: [...pts, ...mirrored].join(' '), fill: (0, tokens_1.alpha)(colors[si % colors.length], 0.55), stroke: colors[si % colors.length], strokeWidth: 1.4 }), (0, jsx_runtime_1.jsx)("line", { x1: cx - 8, x2: cx + 8, y1: y(med), y2: y(med), stroke: theme.text, strokeWidth: 2 }), (0, jsx_runtime_1.jsx)("text", { x: cx, y: h - 6, textAnchor: "middle", fontSize: tokens_1.TYPE.micro, fill: theme.text2, children: s.name })] }, s.name));
                })] })) }));
}
function quantile(sorted, q) {
    const pos = (sorted.length - 1) * q;
    const base = Math.floor(pos);
    return sorted[base + 1] !== undefined ? sorted[base] + (pos - base) * (sorted[base + 1] - sorted[base]) : sorted[base];
}
