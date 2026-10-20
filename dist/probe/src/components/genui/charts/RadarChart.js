"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.RadarChartView = RadarChartView;
const jsx_runtime_1 = require("react/jsx-runtime");
const recharts_1 = require("recharts");
const ChartFrame_1 = require("./ChartFrame");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/** Radar — a handful of metrics on shared axes (skill/profile comparison). */
function RadarChartView({ payload, height = 230 }) {
    const { theme, ramp } = (0, WidgetSurface_1.useWidgetIdentity)();
    const series = payload.series ?? [];
    const labels = payload.labels ?? [];
    const rows = labels.map((label, i) => {
        const row = { axis: label };
        for (const s of series)
            row[s.name] = s.data[i]?.[0] ?? 0;
        return row;
    });
    return ((0, jsx_runtime_1.jsx)(ChartFrame_1.ChartFrame, { title: payload.title, unit: payload.unit, seriesNames: series.map((s) => s.name), height: height, children: ({ width }) => ((0, jsx_runtime_1.jsx)(recharts_1.ResponsiveContainer, { width: width, height: height, children: (0, jsx_runtime_1.jsxs)(recharts_1.RadarChart, { data: rows, outerRadius: "72%", children: [(0, jsx_runtime_1.jsx)(recharts_1.PolarGrid, { stroke: theme.border }), (0, jsx_runtime_1.jsx)(recharts_1.PolarAngleAxis, { dataKey: "axis", tick: { fontSize: tokens_1.TYPE.micro, fill: theme.text2 } }), (0, jsx_runtime_1.jsx)(recharts_1.PolarRadiusAxis, { tick: false, axisLine: false }), (0, jsx_runtime_1.jsx)(recharts_1.Tooltip, { contentStyle: { background: theme.card, border: `1px solid ${theme.border}`, borderRadius: 12, fontSize: tokens_1.TYPE.label, color: theme.text } }), series.map((s, i) => ((0, jsx_runtime_1.jsx)(recharts_1.Radar, { name: s.name, dataKey: s.name, stroke: ramp(series.length)[i], fill: ramp(series.length)[i], fillOpacity: 0.22, strokeWidth: 2 }, s.name)))] }) })) }));
}
