"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.GaugeWidget = GaugeWidget;
const jsx_runtime_1 = require("react/jsx-runtime");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const TitleRow_1 = require("../primitives/TitleRow");
const EmptyState_1 = require("../primitives/EmptyState");
/** Gauge — radial meters for a handful of metrics. */
function GaugeWidget({ payload }) {
    const { accent, ramp, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const items = (payload.items ?? []).slice(0, 6);
    if (!items.length)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "No gauges" });
    const size = 84;
    const stroke = 9;
    const r = (size - stroke) / 2;
    const circ = 2 * Math.PI * r;
    const colors = ramp(Math.max(1, items.length));
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title }), (0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', flexWrap: 'wrap', gap: 12, justifyContent: items.length > 2 ? 'space-between' : 'flex-start' }, children: items.map((item, i) => {
                    const frac = Math.max(0, Math.min(1, item.value / (item.max || 100)));
                    const color = colors[i % colors.length];
                    return ((0, jsx_runtime_1.jsxs)("div", { style: { textAlign: 'center' }, children: [(0, jsx_runtime_1.jsxs)("div", { style: { position: 'relative', width: size, height: size }, children: [(0, jsx_runtime_1.jsxs)("svg", { width: size, height: size, style: { transform: 'rotate(-90deg)' }, children: [(0, jsx_runtime_1.jsx)("circle", { cx: size / 2, cy: size / 2, r: r, fill: "none", stroke: theme.dark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.07)', strokeWidth: stroke }), (0, jsx_runtime_1.jsx)("circle", { cx: size / 2, cy: size / 2, r: r, fill: "none", stroke: color, strokeWidth: stroke, strokeLinecap: "round", strokeDasharray: `${frac * circ} ${circ}`, style: { transition: 'stroke-dasharray .7s cubic-bezier(.22,1,.36,1)' } })] }), (0, jsx_runtime_1.jsx)("div", { style: {
                                            position: 'absolute',
                                            inset: 0,
                                            display: 'flex',
                                            flexDirection: 'column',
                                            alignItems: 'center',
                                            justifyContent: 'center',
                                        }, children: (0, jsx_runtime_1.jsxs)("span", { style: { fontSize: tokens_1.TYPE.heading, fontWeight: 700, color: theme.text, lineHeight: 1 }, children: [item.value, (0, jsx_runtime_1.jsx)("span", { style: { fontSize: tokens_1.TYPE.micro, color: theme.text3 }, children: item.unit })] }) })] }), (0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.micro, color: theme.text2, marginTop: 4, maxWidth: 92, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }, children: item.label })] }, item.label));
                }) })] }));
}
