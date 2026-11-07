"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.QuadrantChartView = QuadrantChartView;
const jsx_runtime_1 = require("react/jsx-runtime");
const recharts_1 = require("recharts");
const ChartFrame_1 = require("./ChartFrame");
const rechartsData_1 = require("./rechartsData");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/**
 * Quadrant — a scatter cut into four named regions (priority matrices).
 * Optional payload.quadrants: [topLeft, topRight, bottomLeft, bottomRight].
 */
function QuadrantChartView({ payload, height = 240 }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const series = payload.series ?? [];
    const points = (0, rechartsData_1.toPoints)(series);
    const xs = points.map((p) => p.x);
    const ys = points.map((p) => p.y);
    const xMid = payload.max ?? (Math.min(...xs) + Math.max(...xs)) / 2;
    const yMid = (Math.min(...ys) + Math.max(...ys)) / 2;
    const labels = payload.items?.map((i) => i.text ?? '') ?? (payload.labels ?? []);
    const quads = (payload.quadrants ?? []).slice(0, 4);
    const xRange = [Math.min(...xs, xMid) - (Math.max(...xs) - Math.min(...xs)) * 0.1, Math.max(...xs, xMid) + (Math.max(...xs) - Math.min(...xs)) * 0.1];
    const yRange = [Math.min(...ys, yMid) - (Math.max(...ys) - Math.min(...ys)) * 0.1, Math.max(...ys, yMid) + (Math.max(...ys) - Math.min(...ys)) * 0.1];
    const corner = (x, y, anchor) => ((0, jsx_runtime_1.jsx)("text", { x: x, y: y, textAnchor: anchor, fontSize: tokens_1.TYPE.micro, fill: theme.text3, fontWeight: 600, children: labels.shift() || quads.shift() || '' }));
    return ((0, jsx_runtime_1.jsx)(ChartFrame_1.ChartFrame, { title: payload.title, unit: payload.unit, legend: false, height: height, children: ({ width }) => ((0, jsx_runtime_1.jsx)(recharts_1.ResponsiveContainer, { width: width, height: height, children: (0, jsx_runtime_1.jsxs)(recharts_1.ScatterChart, { margin: { top: 16, right: 16, bottom: 8, left: -10 }, children: [(0, jsx_runtime_1.jsx)(recharts_1.CartesianGrid, { stroke: theme.border, strokeDasharray: "3 6" }), (0, jsx_runtime_1.jsx)(recharts_1.XAxis, { type: "number", dataKey: "x", domain: xRange, tick: { fontSize: tokens_1.TYPE.micro, fill: theme.text3 }, axisLine: { stroke: theme.border }, tickLine: false }), (0, jsx_runtime_1.jsx)(recharts_1.YAxis, { type: "number", dataKey: "y", domain: yRange, tick: { fontSize: tokens_1.TYPE.micro, fill: theme.text3 }, axisLine: false, tickLine: false }), (0, jsx_runtime_1.jsx)(recharts_1.ZAxis, { dataKey: "z", range: [60, 90] }), (0, jsx_runtime_1.jsx)(recharts_1.ReferenceLine, { x: xMid, stroke: (0, tokens_1.alpha)(accent, 0.5), strokeDasharray: "4 4", label: corner(xMid + 6, 14, 'start') }), (0, jsx_runtime_1.jsx)(recharts_1.ReferenceLine, { y: yMid, stroke: (0, tokens_1.alpha)(accent, 0.5), strokeDasharray: "4 4" }), (0, jsx_runtime_1.jsx)(recharts_1.Tooltip, { contentStyle: { background: theme.card, border: `1px solid ${theme.border}`, borderRadius: 12, fontSize: tokens_1.TYPE.label, color: theme.text } }), (0, jsx_runtime_1.jsx)(recharts_1.Scatter, { name: series[0]?.name ?? 'points', data: points, fill: accent, fillOpacity: 0.7 })] }) })) }));
}
