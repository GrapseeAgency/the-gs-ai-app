"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.BulletChartView = BulletChartView;
const jsx_runtime_1 = require("react/jsx-runtime");
const ChartFrame_1 = require("./ChartFrame");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/** Bullet — value vs target vs qualitative ranges; the KPI-with-context bar. */
function BulletChartView({ payload, height }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const items = (payload.items ?? []).filter((i) => i.label);
    const globalMax = (payload.max ?? Math.max(1, ...items.map((i) => Math.max(i.value ?? 0, i.target ?? 0, ...(i.ranges ?? [])))) * 1.05);
    const h = height ?? Math.max(90, items.length * 52 + 16);
    return ((0, jsx_runtime_1.jsx)(ChartFrame_1.ChartFrame, { title: payload.title, unit: payload.unit, legend: false, height: h, children: () => ((0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', flexDirection: 'column', gap: 14 }, children: items.map((item) => {
                const max = item.max && item.max > 0 ? item.max : globalMax;
                const pct = (v) => `${Math.min(100, ((v ?? 0) / max) * 100)}%`;
                const ranges = (item.ranges && item.ranges.length ? item.ranges : [max * 0.5, max * 0.75, max]).sort((a, b) => a - b);
                return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', justifyContent: 'space-between', fontSize: tokens_1.TYPE.label, marginBottom: 4 }, children: [(0, jsx_runtime_1.jsx)("span", { style: { color: theme.text2, fontWeight: 600 }, children: item.label }), (0, jsx_runtime_1.jsxs)("span", { style: { color: theme.text, fontWeight: 700 }, children: [item.value, item.unit ? ` ${item.unit}` : '', item.target !== null && item.target !== undefined ? (0, jsx_runtime_1.jsxs)("span", { style: { color: theme.text3, fontWeight: 400 }, children: [" / ", item.target, item.unit ? ` ${item.unit}` : ''] }) : null] })] }), (0, jsx_runtime_1.jsxs)("div", { style: { position: 'relative', height: 14, borderRadius: 7, background: theme.dark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.05)', overflow: 'hidden' }, children: [ranges.map((r, ri) => ((0, jsx_runtime_1.jsx)("div", { style: {
                                        position: 'absolute',
                                        left: ri === 0 ? 0 : pct(ranges[ri - 1]),
                                        width: pct(ri === 0 ? r : r - ranges[ri - 1]),
                                        top: 0,
                                        bottom: 0,
                                        background: (0, tokens_1.alpha)(accent, 0.10 + ri * 0.09),
                                    } }, ri))), (0, jsx_runtime_1.jsx)("div", { style: {
                                        position: 'absolute',
                                        left: 0,
                                        width: pct(item.value),
                                        top: 3,
                                        bottom: 3,
                                        borderRadius: 4,
                                        background: accent,
                                    } }), item.target !== null && item.target !== undefined && ((0, jsx_runtime_1.jsx)("div", { title: `target ${item.target}`, style: {
                                        position: 'absolute',
                                        left: pct(item.target),
                                        top: 0,
                                        bottom: 0,
                                        width: 2.5,
                                        background: theme.dark ? '#F2F5F9' : '#14171C',
                                        borderRadius: 2,
                                    } }))] })] }, item.label));
            }) })) }));
}
