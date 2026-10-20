"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.NetworkChartView = NetworkChartView;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const ChartFrame_1 = require("./ChartFrame");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/**
 * Network — a deterministic circular layout with degree-weighted node size.
 * Force simulation is unnecessary at chat scale (≤40 nodes) and determinism
 * keeps replays and tests stable.
 */
function NetworkChartView({ payload, height = 240 }) {
    const { accent, ramp, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const nodes = (payload.nodes ?? []).map((n) => (typeof n === 'string' ? { id: n, label: n, group: '' } : { id: n.id, label: n.label ?? n.id, group: n.group ?? '' }));
    const edges = (payload.edges ?? payload.links ?? []).filter((e) => Boolean(e?.source && e?.target));
    const W = 320;
    const H = height - 24;
    const layout = (0, react_1.useMemo)(() => {
        const cx = W / 2;
        const cy = H / 2;
        const R = Math.min(cx, cy) - 26;
        const degree = new Map();
        for (const e of edges) {
            degree.set(e.source, (degree.get(e.source) ?? 0) + 1);
            degree.set(e.target, (degree.get(e.target) ?? 0) + 1);
        }
        const maxDeg = Math.max(1, ...degree.values());
        return nodes.map((n, i) => {
            const a = (i / Math.max(1, nodes.length)) * Math.PI * 2 - Math.PI / 2;
            return {
                ...n,
                x: cx + R * Math.cos(a),
                y: cy + R * Math.sin(a),
                r: 6 + ((degree.get(n.id) ?? 0) / maxDeg) * 8,
            };
        });
    }, [nodes, edges, H]);
    const groups = [...new Set(nodes.map((n) => n.group).filter(Boolean))];
    const colors = ramp(Math.max(1, groups.length || 1));
    const colorOf = (n) => (n.group && groups.length ? colors[groups.indexOf(n.group) % colors.length] : accent);
    const pos = new Map(layout.map((n) => [n.id, n]));
    return ((0, jsx_runtime_1.jsx)(ChartFrame_1.ChartFrame, { title: payload.title, legend: groups.length > 1, seriesNames: groups.length > 1 ? groups : undefined, height: height, children: ({ width }) => ((0, jsx_runtime_1.jsxs)("svg", { width: "100%", height: H, viewBox: `0 0 ${W} ${H}`, preserveAspectRatio: "xMidYMid meet", role: "img", "aria-label": "network graph", children: [edges.map((e, i) => {
                    const s = pos.get(e.source);
                    const t = pos.get(e.target);
                    if (!s || !t)
                        return null;
                    return (0, jsx_runtime_1.jsx)("line", { x1: s.x, y1: s.y, x2: t.x, y2: t.y, stroke: theme.border, strokeWidth: Math.min(3, Math.max(0.8, (e.weight ?? 1) * 0.8)), strokeOpacity: 0.55 }, i);
                }), layout.map((n) => ((0, jsx_runtime_1.jsxs)("g", { children: [(0, jsx_runtime_1.jsx)("circle", { cx: n.x, cy: n.y, r: n.r, fill: colorOf(n), fillOpacity: 0.9, stroke: theme.dark ? '#0F1216' : '#fff', strokeWidth: 1.5 }), (0, jsx_runtime_1.jsx)("text", { x: n.x, y: n.y - n.r - 5, textAnchor: "middle", fontSize: tokens_1.TYPE.micro, fill: theme.text2, fontWeight: 600, children: n.label })] }, n.id)))] })) }));
}
