"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.WaterfallChartView = WaterfallChartView;
const jsx_runtime_1 = require("react/jsx-runtime");
const ChartFrame_1 = require("./ChartFrame");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/** Waterfall — cumulative build-up from a start value with up/down steps. */
function WaterfallChartView({ payload, height = 200 }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const labels = payload.labels ?? [];
    const values = (payload.series?.[0]?.data ?? []).map((d) => d[d.length - 1]);
    if (values.length === 0)
        return (0, jsx_runtime_1.jsx)(ChartFrame_1.ChartFrame, { title: payload.title, seriesNames: [], legend: false, children: () => null });
    const UP = '#0CA678';
    const DOWN = '#E03131';
    const steps = values.map((v, i) => {
        const start = i === 0 ? 0 : values.slice(0, i).reduce((a, b) => a + b, 0);
        return { label: labels[i] ?? `#${i + 1}`, start, end: start + v, delta: v };
    });
    const lo = Math.min(0, ...steps.map((s) => Math.min(s.start, s.end)));
    const hi = Math.max(...steps.map((s) => Math.max(s.start, s.end)));
    const span = hi - lo || 1;
    const y = (v) => height - 26 - ((v - lo) / span) * (height - 46);
    return ((0, jsx_runtime_1.jsx)(ChartFrame_1.ChartFrame, { title: payload.title, unit: payload.unit, legend: false, height: height, children: () => ((0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', alignItems: 'flex-end', gap: 4, height, position: 'relative' }, children: steps.map((s, i) => {
                const top = Math.min(y(s.start), y(s.end));
                const bottom = Math.max(y(s.start), y(s.end));
                const color = i === 0 ? accent : s.delta >= 0 ? UP : DOWN;
                return ((0, jsx_runtime_1.jsxs)("div", { style: { flex: 1, position: 'relative', height: '100%' }, children: [(0, jsx_runtime_1.jsx)("div", { title: `${s.label}: ${s.delta >= 0 ? '+' : ''}${s.delta}`, style: {
                                position: 'absolute',
                                left: 0,
                                right: 0,
                                top,
                                height: Math.max(3, bottom - top),
                                background: color,
                                borderRadius: 4,
                                opacity: 0.9,
                            } }), (0, jsx_runtime_1.jsx)("div", { style: {
                                position: 'absolute',
                                left: 0,
                                right: 0,
                                top: top - 15,
                                textAlign: 'center',
                                fontSize: tokens_1.TYPE.micro,
                                fontWeight: 700,
                                color,
                            }, children: i > 0 ? (s.delta >= 0 ? '+' : '') + s.delta : s.end }), (0, jsx_runtime_1.jsx)("div", { style: {
                                position: 'absolute',
                                left: '-20%',
                                right: '-20%',
                                top: bottom + 4,
                                textAlign: 'center',
                                fontSize: tokens_1.TYPE.micro,
                                color: theme.text3,
                                whiteSpace: 'nowrap',
                                overflow: 'hidden',
                                textOverflow: 'ellipsis',
                            }, children: s.label })] }, i));
            }) })) }));
}
