"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.HBarChartView = HBarChartView;
const jsx_runtime_1 = require("react/jsx-runtime");
const recharts_1 = require("recharts");
const ChartFrame_1 = require("./ChartFrame");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/** Horizontal bar — rankings; the right answer past ~7 categories. */
function HBarChartView({ payload, height }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const series = payload.series ?? [];
    const labels = payload.labels ?? [];
    const data = (series[0]?.data ?? []).map((d, i) => ({ label: labels[i] ?? `#${i + 1}`, value: d[d.length - 1] }));
    const h = height ?? Math.max(120, data.length * 30 + 40);
    return ((0, jsx_runtime_1.jsx)(ChartFrame_1.ChartFrame, { title: payload.title, unit: payload.unit, legend: false, height: h, children: ({ width }) => ((0, jsx_runtime_1.jsx)(recharts_1.ResponsiveContainer, { width: width, height: h, children: (0, jsx_runtime_1.jsxs)(recharts_1.BarChart, { data: data, layout: "vertical", margin: { top: 4, right: 40, bottom: 0, left: 0 }, children: [(0, jsx_runtime_1.jsx)(recharts_1.CartesianGrid, { stroke: theme.border, strokeDasharray: "3 6", horizontal: false }), (0, jsx_runtime_1.jsx)(recharts_1.XAxis, { type: "number", tick: { fontSize: tokens_1.TYPE.micro, fill: theme.text3 }, axisLine: false, tickLine: false }), (0, jsx_runtime_1.jsx)(recharts_1.YAxis, { type: "category", dataKey: "label", width: 110, tick: { fontSize: tokens_1.TYPE.micro, fill: theme.text2 }, axisLine: false, tickLine: false }), (0, jsx_runtime_1.jsx)(recharts_1.Tooltip, { cursor: { fill: (0, tokens_1.alpha)(theme.dark ? '#FFFFFF' : '#14171C', 0.06) }, contentStyle: { background: theme.card, border: `1px solid ${theme.border}`, borderRadius: 12, fontSize: tokens_1.TYPE.label, color: theme.text } }), (0, jsx_runtime_1.jsx)(recharts_1.Bar, { dataKey: "value", fill: accent, radius: [0, 6, 6, 0], maxBarSize: 18, children: (0, jsx_runtime_1.jsx)(recharts_1.LabelList, { dataKey: "value", position: "right", style: { fontSize: tokens_1.TYPE.micro, fill: theme.text2, fontWeight: 600 } }) })] }) })) }));
}
