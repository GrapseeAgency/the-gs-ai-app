"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.AreaChartView = AreaChartView;
const jsx_runtime_1 = require("react/jsx-runtime");
const recharts_1 = require("recharts");
const ChartFrame_1 = require("./ChartFrame");
const rechartsData_1 = require("./rechartsData");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/** Area — cumulative or volume trends; the editorial surface reads it best. */
function AreaChartView({ payload, height = 200 }) {
    const { theme, ramp } = (0, WidgetSurface_1.useWidgetIdentity)();
    const series = payload.series ?? [];
    const rows = (0, rechartsData_1.toRows)(series, payload.labels, Math.ceil((payload.labels?.length ?? 0) / 6));
    const ys = series.flatMap((s) => s.data.map((d) => d[d.length - 1]));
    const ticks = (0, ChartFrame_1.niceTicks)(Math.min(...ys), Math.max(...ys));
    return ((0, jsx_runtime_1.jsx)(ChartFrame_1.ChartFrame, { title: payload.title, unit: payload.unit, seriesNames: series.map((s) => s.name), height: height, children: ({ width }) => ((0, jsx_runtime_1.jsx)(recharts_1.ResponsiveContainer, { width: width, height: height, children: (0, jsx_runtime_1.jsxs)(recharts_1.AreaChart, { data: rows, margin: { top: 8, right: 8, bottom: 0, left: -18 }, children: [(0, jsx_runtime_1.jsx)("defs", { children: series.map((s, i) => ((0, jsx_runtime_1.jsxs)("linearGradient", { id: `gs-area-${i}-${s.name.replace(/\W/g, '')}`, x1: "0", y1: "0", x2: "0", y2: "1", children: [(0, jsx_runtime_1.jsx)("stop", { offset: "0%", stopColor: ramp(series.length)[i], stopOpacity: theme.dark ? 0.45 : 0.35 }), (0, jsx_runtime_1.jsx)("stop", { offset: "100%", stopColor: ramp(series.length)[i], stopOpacity: 0.02 })] }, s.name))) }), (0, jsx_runtime_1.jsx)(recharts_1.CartesianGrid, { stroke: theme.border, strokeDasharray: "3 6", vertical: false }), (0, jsx_runtime_1.jsx)(recharts_1.XAxis, { dataKey: "label", tick: { fontSize: tokens_1.TYPE.micro, fill: theme.text3 }, axisLine: { stroke: theme.border }, tickLine: false }), (0, jsx_runtime_1.jsx)(recharts_1.YAxis, { tick: { fontSize: tokens_1.TYPE.micro, fill: theme.text3 }, axisLine: false, tickLine: false, ticks: ticks.slice(0, 5) }), (0, jsx_runtime_1.jsx)(recharts_1.Tooltip, { contentStyle: { background: theme.card, border: `1px solid ${theme.border}`, borderRadius: 12, fontSize: tokens_1.TYPE.label, color: theme.text } }), series.map((s, i) => ((0, jsx_runtime_1.jsx)(recharts_1.Area, { type: "monotone", dataKey: s.name, stroke: ramp(series.length)[i], strokeWidth: 2, fill: `url(#gs-area-${i}-${s.name.replace(/\W/g, '')})` }, s.name)))] }) })) }));
}
