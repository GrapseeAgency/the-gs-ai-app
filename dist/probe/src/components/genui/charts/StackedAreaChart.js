"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.StackedAreaChartView = StackedAreaChartView;
const jsx_runtime_1 = require("react/jsx-runtime");
const recharts_1 = require("recharts");
const ChartFrame_1 = require("./ChartFrame");
const rechartsData_1 = require("./rechartsData");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/** Stacked area — composition over time. */
function StackedAreaChartView({ payload, height = 210 }) {
    const { theme, ramp } = (0, WidgetSurface_1.useWidgetIdentity)();
    const series = payload.series ?? [];
    const rows = (0, rechartsData_1.toRows)(series, payload.labels, Math.ceil((payload.labels?.length ?? 0) / 6));
    return ((0, jsx_runtime_1.jsx)(ChartFrame_1.ChartFrame, { title: payload.title, unit: payload.unit, seriesNames: series.map((s) => s.name), height: height, children: ({ width }) => ((0, jsx_runtime_1.jsx)(recharts_1.ResponsiveContainer, { width: width, height: height, children: (0, jsx_runtime_1.jsxs)(recharts_1.AreaChart, { data: rows, margin: { top: 8, right: 8, bottom: 0, left: -18 }, children: [(0, jsx_runtime_1.jsx)(recharts_1.CartesianGrid, { stroke: theme.border, strokeDasharray: "3 6", vertical: false }), (0, jsx_runtime_1.jsx)(recharts_1.XAxis, { dataKey: "label", tick: { fontSize: tokens_1.TYPE.micro, fill: theme.text3 }, axisLine: { stroke: theme.border }, tickLine: false }), (0, jsx_runtime_1.jsx)(recharts_1.YAxis, { tick: { fontSize: tokens_1.TYPE.micro, fill: theme.text3 }, axisLine: false, tickLine: false }), (0, jsx_runtime_1.jsx)(recharts_1.Tooltip, { contentStyle: { background: theme.card, border: `1px solid ${theme.border}`, borderRadius: 12, fontSize: tokens_1.TYPE.label, color: theme.text } }), series.map((s, i) => ((0, jsx_runtime_1.jsx)(recharts_1.Area, { type: "monotone", dataKey: s.name, stackId: "gs", stroke: ramp(series.length)[i], strokeWidth: 1.5, fill: ramp(series.length)[i], fillOpacity: theme.dark ? 0.55 : 0.75 }, s.name)))] }) })) }));
}
