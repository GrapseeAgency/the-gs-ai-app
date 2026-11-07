"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.BarChartView = BarChartView;
const jsx_runtime_1 = require("react/jsx-runtime");
const recharts_1 = require("recharts");
const ChartFrame_1 = require("./ChartFrame");
const rechartsData_1 = require("./rechartsData");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/** Bar — categorical comparison, the default answer to "chart this". */
function BarChartView({ payload, height = 200 }) {
    const { accent, theme, ramp } = (0, WidgetSurface_1.useWidgetIdentity)();
    const series = payload.series ?? [];
    const single = series.length === 1;
    const rows = (0, rechartsData_1.toRows)(series, payload.labels);
    return ((0, jsx_runtime_1.jsx)(ChartFrame_1.ChartFrame, { title: payload.title, unit: payload.unit, seriesNames: single ? undefined : series.map((s) => s.name), height: height, children: ({ width }) => ((0, jsx_runtime_1.jsx)(recharts_1.ResponsiveContainer, { width: width, height: height, children: (0, jsx_runtime_1.jsxs)(recharts_1.BarChart, { data: rows, margin: { top: 14, right: 8, bottom: 0, left: -18 }, children: [(0, jsx_runtime_1.jsx)(recharts_1.CartesianGrid, { stroke: theme.border, strokeDasharray: "3 6", vertical: false }), (0, jsx_runtime_1.jsx)(recharts_1.XAxis, { dataKey: "label", tick: { fontSize: tokens_1.TYPE.micro, fill: theme.text3 }, axisLine: { stroke: theme.border }, tickLine: false, interval: 0 }), (0, jsx_runtime_1.jsx)(recharts_1.YAxis, { tick: { fontSize: tokens_1.TYPE.micro, fill: theme.text3 }, axisLine: false, tickLine: false }), (0, jsx_runtime_1.jsx)(recharts_1.Tooltip, { cursor: { fill: alphaSafe(theme.dark ? '#FFFFFF' : '#14171C', 0.06) }, contentStyle: { background: theme.card, border: `1px solid ${theme.border}`, borderRadius: 12, fontSize: tokens_1.TYPE.label, color: theme.text } }), series.map((s, i) => ((0, jsx_runtime_1.jsx)(recharts_1.Bar, { dataKey: s.name, fill: single ? accent : ramp(series.length)[i], radius: [6, 6, 0, 0], maxBarSize: 44, children: single && rows.length <= 7 && ((0, jsx_runtime_1.jsx)(recharts_1.LabelList, { dataKey: s.name, position: "top", style: { fontSize: tokens_1.TYPE.micro, fill: theme.text2, fontWeight: 600 } })) }, s.name)))] }) })) }));
}
function alphaSafe(hex, a) {
    const v = hex.replace('#', '');
    return `rgba(${parseInt(v.slice(0, 2), 16)},${parseInt(v.slice(2, 4), 16)},${parseInt(v.slice(4, 6), 16)},${a})`;
}
