"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.SankeyChartView = SankeyChartView;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const ChartFrame_1 = require("./ChartFrame");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/** Sankey — two-pass layered layout (sources left, sinks right, mid spread). */
function SankeyChartView({ payload, height = 220 }) {
    const { accent, ramp, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const W = 320;
    const linksRaw = payload.links ?? [];
    const layout = (0, react_1.useMemo)(() => {
        const ids = [...new Set(linksRaw.flatMap((l) => [l.source, l.target]))];
        const index = new Map(ids.map((id, i) => [id, i]));
        const outSum = new Array(ids.length).fill(0);
        const inSum = new Array(ids.length).fill(0);
        for (const l of linksRaw) {
            outSum[index.get(l.source)] += l.value;
            inSum[index.get(l.target)] += l.value;
        }
        const maxFlow = Math.max(1, ...outSum, ...inSum);
        const scale = (height - 40) / maxFlow;
        const nodes = ids.map((id, i) => {
            const h = Math.max(4, Math.max(outSum[i], inSum[i]) * scale);
            const col = outSum[i] > 0 && inSum[i] === 0 ? 0 : inSum[i] > 0 && outSum[i] === 0 ? 2 : 1;
            return { id, x: (col * (W - 70)) / 2, y: 0, h };
        });
        // simple vertical stacking per column
        const cols = new Map();
        nodes.forEach((n) => {
            const col = n.x === 0 ? 0 : n.x > 0 && n.x < W - 70 ? 1 : 2;
            cols.set(col, [...(cols.get(col) ?? []), n]);
        });
        for (const list of cols.values()) {
            const total = list.reduce((a, n) => a + n.h + 8, -8);
            let y = (height - 24 - total) / 2;
            for (const n of list) {
                n.y = y;
                y += n.h + 8;
            }
        }
        const links = linksRaw.map((l) => ({
            source: index.get(l.source),
            target: index.get(l.target),
            value: l.value,
            w: Math.max(1.5, l.value * scale),
        }));
        return { nodes, links };
    }, [linksRaw, height]);
    const colors = ramp(Math.max(1, layout.nodes.length));
    const colorOf = (i) => colors[i % colors.length];
    return ((0, jsx_runtime_1.jsx)(ChartFrame_1.ChartFrame, { title: payload.title, unit: payload.unit, legend: false, height: height, children: ({ width }) => ((0, jsx_runtime_1.jsxs)("svg", { width: "100%", height: height - 24, viewBox: `0 0 ${W} ${height - 24}`, preserveAspectRatio: "xMidYMid meet", role: "img", "aria-label": "sankey", children: [layout.links.map((l, i) => {
                    const s = layout.nodes[l.source];
                    const t = layout.nodes[l.target];
                    if (!s || !t)
                        return null;
                    const x0 = s.x + 34;
                    const x1 = t.x;
                    const sy = s.y + l.w / 2 + 2;
                    const ty = t.y + l.w / 2 + 2;
                    const c = colorOf(l.source);
                    return ((0, jsx_runtime_1.jsx)("path", { d: `M ${x0} ${sy} C ${(x0 + x1) / 2} ${sy}, ${(x0 + x1) / 2} ${ty}, ${x1} ${ty}`, stroke: (0, tokens_1.alpha)(c, 0.4), strokeWidth: l.w, fill: "none", children: (0, jsx_runtime_1.jsx)("title", { children: `${s.id} → ${t.id}: ${l.value}` }) }, i));
                }), layout.nodes.map((n, i) => ((0, jsx_runtime_1.jsxs)("g", { children: [(0, jsx_runtime_1.jsx)("rect", { x: n.x, y: n.y, width: 34, height: n.h, rx: 4, fill: colorOf(i) }), (0, jsx_runtime_1.jsx)("text", { x: n.x + (n.x > W / 2 ? -6 : 40), y: n.y + n.h / 2 + 3, textAnchor: n.x > W / 2 ? 'end' : 'start', fontSize: tokens_1.TYPE.micro, fill: theme.text2, fontWeight: 600, children: n.id })] }, n.id)))] })) }));
}
