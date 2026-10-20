"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.GroupedBarChartView = GroupedBarChartView;
const jsx_runtime_1 = require("react/jsx-runtime");
const recharts_1 = require("recharts");
const ChartFrame_1 = require("./ChartFrame");
const rechartsData_1 = require("./rechartsData");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/** Grouped bar — side-by-side comparison of several series. */
function GroupedBarChartView({ payload, height = 210 }) {
    const { theme, ramp } = (0, WidgetSurface_1.useWidgetIdentity)();
    const series = payload.series ?? [];
    const rows = (0, rechartsData_1.toRows)(series, payload.labels);
    return ((0, jsx_runtime_1.jsx)(ChartFrame_1.ChartFrame, { title: payload.title, unit: payload.unit, seriesNames: series.map((s) => s.name), height: height, children: ({ width }) => ((0, jsx_runtime_1.jsx)(recharts_1.ResponsiveContainer, { width: width, height: height, children: (0, jsx_runtime_1.jsxs)(recharts_1.BarChart, { data: rows, margin: { top: 8, right: 8, bottom: 0, left: -18 }, children: [(0, jsx_runtime_1.jsx)(recharts_1.CartesianGrid, { stroke: theme.border, strokeDasharray: "3 6", vertical: false }), (0, jsx_runtime_1.jsx)(recharts_1.XAxis, { dataKey: "label", tick: { fontSize: tokens_1.TYPE.micro, fill: theme.text3 }, axisLine: { stroke: theme.border }, tickLine: false }), (0, jsx_runtime_1.jsx)(recharts_1.YAxis, { tick: { fontSize: tokens_1.TYPE.micro, fill: theme.text3 }, axisLine: false, tickLine: false }), (0, jsx_runtime_1.jsx)(recharts_1.Tooltip, { cursor: { fill: (0, tokens_1.alpha)(theme.dark ? '#FFFFFF' : '#14171C', 0.06) }, contentStyle: { background: theme.card, border: `1px solid ${theme.border}`, borderRadius: 12, fontSize: tokens_1.TYPE.label, color: theme.text } }), series.map((s, i) => ((0, jsx_runtime_1.jsx)(recharts_1.Bar, { dataKey: s.name, fill: ramp(series.length)[i], radius: [5, 5, 0, 0], maxBarSize: 20 }, s.name)))] }) })) }));
}
