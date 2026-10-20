"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.BubbleChartView = BubbleChartView;
const jsx_runtime_1 = require("react/jsx-runtime");
const recharts_1 = require("recharts");
const ChartFrame_1 = require("./ChartFrame");
const rechartsData_1 = require("./rechartsData");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/** Bubble — scatter with a third dimension as size. */
function BubbleChartView({ payload, height = 230 }) {
    const { accent, theme, ramp } = (0, WidgetSurface_1.useWidgetIdentity)();
    const series = payload.series ?? [];
    const points = (0, rechartsData_1.toPoints)(series);
    const zMax = Math.max(1, ...points.map((p) => p.z));
    return ((0, jsx_runtime_1.jsx)(ChartFrame_1.ChartFrame, { title: payload.title, unit: payload.unit, seriesNames: series.length > 1 ? series.map((s) => s.name) : undefined, height: height, children: ({ width }) => ((0, jsx_runtime_1.jsx)(recharts_1.ResponsiveContainer, { width: width, height: height, children: (0, jsx_runtime_1.jsxs)(recharts_1.ScatterChart, { margin: { top: 8, right: 12, bottom: 4, left: -18 }, children: [(0, jsx_runtime_1.jsx)(recharts_1.CartesianGrid, { stroke: theme.border, strokeDasharray: "3 6" }), (0, jsx_runtime_1.jsx)(recharts_1.XAxis, { type: "number", dataKey: "x", domain: ['auto', 'auto'], tick: { fontSize: tokens_1.TYPE.micro, fill: theme.text3 }, axisLine: { stroke: theme.border }, tickLine: false }), (0, jsx_runtime_1.jsx)(recharts_1.YAxis, { type: "number", dataKey: "y", domain: ['auto', 'auto'], tick: { fontSize: tokens_1.TYPE.micro, fill: theme.text3 }, axisLine: false, tickLine: false }), (0, jsx_runtime_1.jsx)(recharts_1.ZAxis, { type: "number", dataKey: "z", range: [40, Math.min(400, 60 + zMax * 6)] }), (0, jsx_runtime_1.jsx)(recharts_1.Tooltip, { contentStyle: { background: theme.card, border: `1px solid ${theme.border}`, borderRadius: 12, fontSize: tokens_1.TYPE.label, color: theme.text } }), series.map((s, i) => ((0, jsx_runtime_1.jsx)(recharts_1.Scatter, { name: s.name, data: points.filter((p) => p.si === i), fill: series.length > 1 ? ramp(series.length)[i] : accent, fillOpacity: 0.5, stroke: (0, tokens_1.alpha)(accent, 0.5) }, s.name)))] }) })) }));
}
