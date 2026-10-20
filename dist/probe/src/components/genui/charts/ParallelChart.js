"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.ParallelChartView = ParallelChartView;
const jsx_runtime_1 = require("react/jsx-runtime");
const ChartFrame_1 = require("./ChartFrame");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/** Parallel coordinates — each row is a polyline across normalised axes. */
function ParallelChartView({ payload, height = 230 }) {
    const { ramp, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const axes = (payload.axes ?? []);
    const series = (payload.series ?? []);
    const W = 320;
    const H = height;
    if (axes.length < 3 || !series.length)
        return (0, jsx_runtime_1.jsx)(ChartFrame_1.ChartFrame, { title: payload.title, seriesNames: [], legend: false, children: () => null });
    const colors = ramp(Math.max(1, series.length));
    const x = (i) => 30 + (i / (axes.length - 1)) * (W - 46);
    return ((0, jsx_runtime_1.jsx)(ChartFrame_1.ChartFrame, { title: payload.title, legend: series.length > 1 && series.length <= 6, seriesNames: series.map((s) => s.name), height: height, children: () => ((0, jsx_runtime_1.jsxs)("svg", { width: "100%", height: H, viewBox: `0 0 ${W} ${H}`, preserveAspectRatio: "xMidYMid meet", role: "img", "aria-label": "parallel coordinates", children: [axes.map((axis, i) => {
                    const col = series.map((s) => s.values[i] ?? 0);
                    const lo = Math.min(...col);
                    const hi = Math.max(...col);
                    return ((0, jsx_runtime_1.jsxs)("g", { children: [(0, jsx_runtime_1.jsx)("line", { x1: x(i), x2: x(i), y1: 10, y2: H - 26, stroke: theme.border }), (0, jsx_runtime_1.jsx)("text", { x: x(i), y: H - 14, textAnchor: "middle", fontSize: tokens_1.TYPE.micro, fill: theme.text2, children: axis }), (0, jsx_runtime_1.jsx)("text", { x: x(i), y: 8, textAnchor: "middle", fontSize: 9, fill: theme.text3, children: hi.toFixed(0) }), (0, jsx_runtime_1.jsx)("text", { x: x(i), y: H - 26 + 9, textAnchor: "middle", fontSize: 9, fill: theme.text3, children: lo.toFixed(0) })] }, axis));
                }), series.map((s, si) => {
                    const pts = axes.map((_, i) => {
                        const col = series.map((row) => row.values[i] ?? 0);
                        const lo = Math.min(...col);
                        const hi = Math.max(...col);
                        const t = hi === lo ? 0.5 : (s.values[i] - lo) / (hi - lo);
                        return `${x(i)},${14 + (1 - t) * (H - 46)}`;
                    });
                    return ((0, jsx_runtime_1.jsx)("polyline", { points: pts.join(' '), fill: "none", stroke: colors[si % colors.length], strokeWidth: 1.8, strokeOpacity: series.length > 4 ? 0.55 : 0.9, strokeLinejoin: "round", children: (0, jsx_runtime_1.jsx)("title", { children: s.name }) }, s.name));
                })] })) }));
}
