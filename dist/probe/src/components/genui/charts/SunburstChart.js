"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.SunburstChartView = SunburstChartView;
const jsx_runtime_1 = require("react/jsx-runtime");
const ChartFrame_1 = require("./ChartFrame");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
function leafSum(node) {
    if (!node.children || node.children.length === 0)
        return node.value ?? 0;
    return node.children.reduce((a, c) => a + leafSum(c), 0) || (node.value ?? 0);
}
function arcPath(cx, cy, r0, r1, a0, a1) {
    const p = (r, a) => `${cx + r * Math.sin(a)} ${cy - r * Math.cos(a)}`;
    const large = a1 - a0 > Math.PI ? 1 : 0;
    return `M ${p(r1, a0)} A ${r1} ${r1} 0 ${large} 1 ${p(r1, a1)} L ${p(r0, a1)} A ${r0} ${r0} 0 ${large} 0 ${p(r0, a0)} Z`;
}
/** Sunburst — hierarchical rings; ring width per depth, palette ramp by depth. */
function SunburstChartView({ payload, height = 240 }) {
    const { ramp, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const root = payload.root;
    if (!root)
        return (0, jsx_runtime_1.jsx)(ChartFrame_1.ChartFrame, { title: payload.title, seriesNames: [], legend: false, children: () => null });
    const total = leafSum(root) || 1;
    const arcs = [];
    const cx = 130;
    const cy = (height - 24) / 2;
    const maxR = Math.min(cx - 4, cy - 4);
    const ringW = maxR / 3;
    const colors = ramp(3);
    const walk = (node, a0, a1, depth) => {
        arcs.push({ d: arcPath(cx, cy, depth * ringW, (depth + 1) * ringW - 1.5, a0, a1), color: colors[Math.min(2, depth)], label: node.label, value: leafSum(node), depth });
        const sum = a1 - a0;
        const kids = node.children ?? [];
        const kidTotal = kids.reduce((acc, c) => acc + leafSum(c), 0) || 1;
        let angle = a0;
        for (const kid of kids) {
            const frac = (leafSum(kid) / kidTotal) * sum;
            walk(kid, angle, angle + frac, depth + 1);
            angle += frac;
        }
    };
    walk(root, 0, Math.PI * 2, 0);
    return ((0, jsx_runtime_1.jsx)(ChartFrame_1.ChartFrame, { title: payload.title, unit: payload.unit, legend: false, height: height, children: ({ width }) => ((0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', alignItems: 'center', gap: 12 }, children: [(0, jsx_runtime_1.jsxs)("svg", { width: Math.min(260, width), height: height - 24, role: "img", "aria-label": "sunburst", children: [arcs.map((a, i) => ((0, jsx_runtime_1.jsx)("path", { d: a.d, fill: a.color, fillOpacity: a.depth === 0 ? 0.28 : 0.92 - a.depth * 0.18, stroke: theme.dark ? '#0F1216' : '#fff', strokeWidth: 1.5, children: (0, jsx_runtime_1.jsx)("title", { children: `${a.label}: ${a.value}` }) }, i))), (0, jsx_runtime_1.jsx)("text", { x: cx, y: cy + 4, textAnchor: "middle", fontSize: tokens_1.TYPE.label, fontWeight: 700, fill: theme.text, children: total })] }), (0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', flexDirection: 'column', gap: 4, minWidth: 0 }, children: (root.children ?? []).slice(0, 6).map((c, i) => ((0, jsx_runtime_1.jsxs)("span", { style: { fontSize: tokens_1.TYPE.micro, color: theme.text2, display: 'inline-flex', alignItems: 'center', gap: 6 }, children: [(0, jsx_runtime_1.jsx)("span", { style: { width: 8, height: 8, borderRadius: 2, background: colors[1] ?? colors[0], flexShrink: 0, opacity: 0.92 - i * 0.12 } }), c.label] }, c.label))) })] })) }));
}
