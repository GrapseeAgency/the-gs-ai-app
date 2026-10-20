"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.HeatmapChartView = HeatmapChartView;
const jsx_runtime_1 = require("react/jsx-runtime");
const ChartFrame_1 = require("./ChartFrame");
const EmptyState_1 = require("../primitives/EmptyState");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/** Heatmap — one series per row, [col,value] points; magnitude as colour. */
function HeatmapChartView({ payload, height }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const labels = payload.labels ?? [];
    const series = payload.series ?? [];
    const values = [];
    series.forEach((s, r) => {
        s.data.forEach((d, c) => {
            const colLabel = labels[d[0]] ?? labels[c] ?? String(c + 1);
            values.push({ row: r, col: c, v: d[d.length - 1], label: colLabel });
        });
    });
    if (values.length === 0)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "No heat data" });
    const vs = values.map((v) => v.v);
    const min = Math.min(...vs);
    const max = Math.max(...vs);
    const norm = (v) => (max === min ? 0.5 : (v - min) / (max - min));
    const cols = Math.max(...values.map((v) => v.col)) + 1;
    return ((0, jsx_runtime_1.jsx)(ChartFrame_1.ChartFrame, { title: payload.title, unit: payload.unit, legend: false, height: height, children: () => ((0, jsx_runtime_1.jsx)("div", { style: { overflowX: 'auto' }, children: (0, jsx_runtime_1.jsxs)("div", { style: { display: 'grid', gridTemplateColumns: `86px repeat(${cols}, 1fr)`, gap: 3, minWidth: cols * 36 }, children: [(0, jsx_runtime_1.jsx)("span", {}), Array.from({ length: cols }, (_, c) => ((0, jsx_runtime_1.jsx)("span", { style: { fontSize: tokens_1.TYPE.micro, color: theme.text3, textAlign: 'center' }, children: labels[c] ?? c + 1 }, c))), series.map((s, r) => ((0, jsx_runtime_1.jsxs)("div", { style: { display: 'contents' }, children: [(0, jsx_runtime_1.jsx)("span", { style: { fontSize: tokens_1.TYPE.micro, color: theme.text2, alignSelf: 'center', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }, children: s.name }), Array.from({ length: cols }, (_, c) => {
                                const cell = values.find((v) => v.row === r && v.col === c);
                                const a = cell ? 0.15 + norm(cell.v) * 0.85 : 0.04;
                                return ((0, jsx_runtime_1.jsx)("div", { title: cell ? `${s.name} · ${cell.label}: ${cell.v}` : '', style: {
                                        height: 30,
                                        borderRadius: 7,
                                        background: (0, tokens_1.alpha)(accent, a),
                                        display: 'flex',
                                        alignItems: 'center',
                                        justifyContent: 'center',
                                        fontSize: tokens_1.TYPE.micro,
                                        fontWeight: 600,
                                        color: a > 0.55 ? (0, tokens_1.inkOn)(accent) : theme.text2,
                                    }, children: cell ? cell.v : '' }, c));
                            })] }, s.name)))] }) })) }));
}
