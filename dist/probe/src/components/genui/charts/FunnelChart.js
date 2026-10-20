"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.FunnelChartView = FunnelChartView;
const jsx_runtime_1 = require("react/jsx-runtime");
const recharts_1 = require("recharts");
const ChartFrame_1 = require("./ChartFrame");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/** Funnel — stage-by-stage drop-off (acquisition, pipelines). */
function FunnelChartView({ payload, height }) {
    const { ramp, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const labels = payload.labels ?? [];
    const values = payload.series?.[0]?.data ?? [];
    const data = values.map((d, i) => ({ name: labels[i] ?? `Stage ${i + 1}`, value: d[d.length - 1] }));
    const h = height ?? Math.max(140, data.length * 42 + 20);
    const colors = ramp(data.length);
    return ((0, jsx_runtime_1.jsx)(ChartFrame_1.ChartFrame, { title: payload.title, unit: payload.unit, legend: false, height: h, children: ({ width }) => ((0, jsx_runtime_1.jsx)(recharts_1.ResponsiveContainer, { width: width, height: h, children: (0, jsx_runtime_1.jsxs)(recharts_1.FunnelChart, { children: [(0, jsx_runtime_1.jsx)(recharts_1.Funnel, { dataKey: "value", data: data, isAnimationActive: true, children: data.map((_, i) => ((0, jsx_runtime_1.jsx)(recharts_1.LabelList, { position: "center", fill: (0, tokens_1.inkOn)(colors[i]), stroke: "none", dataKey: "name", fontSize: tokens_1.TYPE.micro, fontWeight: 600 }, i))) }), (0, jsx_runtime_1.jsx)(recharts_1.Tooltip, { contentStyle: { background: theme.card, border: `1px solid ${theme.border}`, borderRadius: 12, fontSize: tokens_1.TYPE.label, color: theme.text } })] }) })) }));
}
