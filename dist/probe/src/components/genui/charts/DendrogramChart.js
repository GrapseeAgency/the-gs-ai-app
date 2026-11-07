"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.DendrogramChartView = DendrogramChartView;
const jsx_runtime_1 = require("react/jsx-runtime");
const ChartFrame_1 = require("./ChartFrame");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/** Dendrogram — merge distances as horizontal linkage lines. */
function DendrogramChartView({ payload, height }) {
    const { ramp, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const roots = (payload.roots ?? []);
    if (!roots?.length)
        return (0, jsx_runtime_1.jsx)(ChartFrame_1.ChartFrame, { title: payload.title, seriesNames: [], legend: false, children: () => null });
    const leaves = [];
    let maxDist = 0;
    const walk = (n, depth) => {
        maxDist = Math.max(maxDist, n.distance ?? 0);
        if (!n.children?.length) {
            leaves.push({ label: n.label, depth });
            return;
        }
        for (const c of n.children)
            walk(c, depth + 1);
    };
    for (const r of roots)
        walk(r, 0);
    const H = height ?? Math.max(120, leaves.length * 26 + 34);
    const W = 320;
    const labelW = 92;
    const xOf = (d) => labelW + (d / (maxDist || 1)) * (W - labelW - 14);
    const leafY = new Map();
    leaves.forEach((l, i) => leafY.set(l.label, 16 + i * ((H - 48) / Math.max(1, leaves.length - 1 || 1))));
    const colors = ramp(3);
    const lines = [];
    const walkDraw = (n) => {
        if (!n.children?.length)
            return leafY.get(n.label) ?? 20;
        const kidYs = n.children.map((c) => walkDraw(c));
        const x = xOf(n.distance);
        const top = Math.min(...kidYs);
        const bot = Math.max(...kidYs);
        kidYs.forEach((ky, i) => {
            lines.push((0, jsx_runtime_1.jsx)("line", { x1: xOf(n.children[i].distance), x2: x, y1: ky, y2: ky, stroke: colors[1] ?? colors[0], strokeWidth: 1.6 }, `${n.label}-v${i}`));
        });
        lines.push((0, jsx_runtime_1.jsx)("line", { x1: x, x2: x, y1: top, y2: bot, stroke: colors[1] ?? colors[0], strokeWidth: 1.6 }, `${n.label}-h`));
        return (top + bot) / 2;
    };
    roots.forEach((r) => walkDraw(r));
    return ((0, jsx_runtime_1.jsx)(ChartFrame_1.ChartFrame, { title: payload.title, legend: false, height: H, children: () => ((0, jsx_runtime_1.jsxs)("svg", { width: "100%", height: H, viewBox: `0 0 ${W} ${H}`, preserveAspectRatio: "xMidYMid meet", role: "img", "aria-label": "dendrogram", children: [lines, leaves.map((l) => ((0, jsx_runtime_1.jsxs)("g", { children: [(0, jsx_runtime_1.jsx)("line", { x1: labelW - 4, x2: labelW, y1: leafY.get(l.label), y2: leafY.get(l.label), stroke: theme.text3 }), (0, jsx_runtime_1.jsx)("text", { x: labelW - 8, y: (leafY.get(l.label) ?? 0) + 3, textAnchor: "end", fontSize: tokens_1.TYPE.micro, fill: theme.text2, children: l.label })] }, l.label))), roots.map((r) => ((0, jsx_runtime_1.jsx)("circle", { cx: xOf(r.distance), cy: walkYCache(r, leafY, xOf), r: 3, fill: (0, tokens_1.alpha)(colors[0], 0.9) }, r.label)))] })) }));
}
function walkYCache(n, leafY, xOf) {
    if (!n.children?.length)
        return leafY.get(n.label) ?? 20;
    const ys = n.children.map((c) => walkYCache(c, leafY, xOf));
    return (Math.min(...ys) + Math.max(...ys)) / 2;
}
