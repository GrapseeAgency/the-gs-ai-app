"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.pieData = pieData;
exports.PieChartView = PieChartView;
exports.DonutChartView = DonutChartView;
const jsx_runtime_1 = require("react/jsx-runtime");
const recharts_1 = require("recharts");
const ChartFrame_1 = require("./ChartFrame");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/** Shared pie data: one series per slice, a single point each. */
function pieData(payload) {
    const labels = payload.labels ?? [];
    return (payload.series ?? []).map((s, i) => ({
        name: s.name || labels[i] || `#${i + 1}`,
        value: s.data[0]?.[s.data[0].length - 1] ?? 0,
    }));
}
function PieDonutBase({ payload, donut, height = 210 }) {
    const { ramp, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const colors = ramp(8);
    const data = pieData(payload).filter((d) => d.value > 0);
    return ((0, jsx_runtime_1.jsx)(ChartFrame_1.ChartFrame, { title: payload.title, unit: payload.unit, seriesNames: data.map((d) => d.name), height: height, children: ({ width }) => ((0, jsx_runtime_1.jsx)(recharts_1.ResponsiveContainer, { width: width, height: height, children: (0, jsx_runtime_1.jsxs)(recharts_1.PieChart, { children: [(0, jsx_runtime_1.jsx)(recharts_1.Pie, { data: data, dataKey: "value", nameKey: "name", innerRadius: donut ? '58%' : 0, outerRadius: "86%", paddingAngle: donut ? 2 : 1, stroke: "none", label: donut ? undefined : { fontSize: tokens_1.TYPE.micro, fill: theme.text2, fontWeight: 600 }, children: data.map((_, i) => ((0, jsx_runtime_1.jsx)(recharts_1.Cell, { fill: colors[i % colors.length] }, i))) }), (0, jsx_runtime_1.jsx)(recharts_1.Tooltip, { contentStyle: { background: theme.card, border: `1px solid ${theme.border}`, borderRadius: 12, fontSize: tokens_1.TYPE.label, color: theme.text } })] }) })) }));
}
function PieChartView({ payload, height }) {
    return (0, jsx_runtime_1.jsx)(PieDonutBase, { payload: payload, donut: false, height: height });
}
function DonutChartView({ payload, height }) {
    return (0, jsx_runtime_1.jsx)(PieDonutBase, { payload: payload, donut: true, height: height });
}
