"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.CorrelationChartView = CorrelationChartView;
const jsx_runtime_1 = require("react/jsx-runtime");
const ChartFrame_1 = require("./ChartFrame");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/**
 * Correlation matrix — values -1..1: crimson for negative, azure for positive.
 * (Same reading as the Android renderer.)
 */
function CorrelationChartView({ payload, height }) {
    const { theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const labels = payload.labels ?? [];
    const series = payload.series ?? [];
    const n = series.length;
    const POS = '#4C6FFF';
    const NEG = '#E03131';
    if (n === 0)
        return (0, jsx_runtime_1.jsx)(ChartFrame_1.ChartFrame, { title: payload.title, seriesNames: [], legend: false, children: () => null });
    return ((0, jsx_runtime_1.jsx)(ChartFrame_1.ChartFrame, { title: payload.title, legend: false, height: height, children: () => ((0, jsx_runtime_1.jsxs)("div", { style: { display: 'grid', gridTemplateColumns: `90px repeat(${n}, 1fr)`, gap: 3 }, children: [(0, jsx_runtime_1.jsx)("span", {}), series.map((s) => ((0, jsx_runtime_1.jsx)("span", { style: { fontSize: tokens_1.TYPE.micro, color: theme.text3, textAlign: 'center', overflow: 'hidden', textOverflow: 'ellipsis' }, children: s.name }, s.name))), series.map((row, r) => ((0, jsx_runtime_1.jsxs)("div", { style: { display: 'contents' }, children: [(0, jsx_runtime_1.jsx)("span", { style: { fontSize: tokens_1.TYPE.micro, color: theme.text2, alignSelf: 'center', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }, children: row.name || labels[r] }), series.map((col, c) => {
                            const v = row.data[c]?.[row.data[c].length - 1] ?? 0;
                            const pos = v >= 0;
                            const a = 0.08 + Math.min(1, Math.abs(v)) * 0.82;
                            const hue = pos ? POS : NEG;
                            return ((0, jsx_runtime_1.jsx)("div", { title: `${row.name} × ${col.name}: ${v.toFixed(2)}`, style: {
                                    height: 32,
                                    borderRadius: 7,
                                    background: (0, tokens_1.alpha)(hue, a),
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    fontSize: tokens_1.TYPE.micro,
                                    fontWeight: 700,
                                    color: a > 0.5 ? (0, tokens_1.inkOn)(hue) : theme.text2,
                                }, children: v.toFixed(2).replace('0.', '.') }, c));
                        })] }, row.name)))] })) }));
}
