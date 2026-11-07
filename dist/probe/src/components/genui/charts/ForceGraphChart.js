"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.ForceGraphChartView = ForceGraphChartView;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const ChartFrame_1 = require("./ChartFrame");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/**
 * Force — a real force-directed layout (repulsion + spring + centering),
 * integrated with rAF until stable; nodes stay DRAGGABLE and the sim
 * re-relaxes around the pointer. Deterministic seeding keeps replays sane.
 */
function ForceGraphChartView({ payload, height = 260 }) {
    const { accent, ramp, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const nodes = (payload.nodes ?? []).map((n) => (typeof n === 'string' ? { id: n, label: n, group: '' } : { id: n.id, label: n.label ?? n.id, group: n.group ?? '' }));
    const edges = (payload.edges ?? payload.links ?? []).filter((e) => e?.source && e?.target);
    const simRef = (0, react_1.useRef)([]);
    // the rAF sim mutates positions in place; the store subscription is what
    // re-renders us each frame. Render reads the snapshot, never the ref.
    const notifyRef = (0, react_1.useRef)(() => { });
    const sim = (0, react_1.useSyncExternalStore)((onStoreChange) => {
        notifyRef.current = onStoreChange;
        return () => {
            notifyRef.current = () => { };
        };
    }, () => simRef.current, () => simRef.current);
    const dragRef = (0, react_1.useRef)(null);
    const boxRef = (0, react_1.useRef)(null);
    (0, react_1.useEffect)(() => {
        const W = 320;
        const H = height;
        const R = Math.min(W, H) / 2 - 24;
        const rnd = mulberry(Number(payload.span ?? 2));
        simRef.current = nodes.map((n, i) => {
            const a = (i / Math.max(1, nodes.length)) * Math.PI * 2;
            return { ...n, x: W / 2 + R * Math.cos(a) * (0.6 + rnd() * 0.4), y: H / 2 + R * Math.sin(a) * (0.6 + rnd() * 0.4), vx: 0, vy: 0 };
        });
        const pos = new Map(sim.map((n) => [n.id, n]));
        const links = edges.map((e) => ({ a: pos.get(e.source), b: pos.get(e.target), w: e.weight ?? 1 })).filter((l) => l.a && l.b);
        let alphaVal = 1;
        let raf = 0;
        const step = () => {
            // repulsion
            for (let i = 0; i < simRef.current.length; i++) {
                for (let j = i + 1; j < simRef.current.length; j++) {
                    const a = simRef.current[i];
                    const b = simRef.current[j];
                    const dx = b.x - a.x;
                    const dy = b.y - a.y;
                    const d2 = Math.max(80, dx * dx + dy * dy);
                    const f = (2200 / d2) * alphaVal;
                    const d = Math.sqrt(d2);
                    a.vx -= (dx / d) * f;
                    a.vy -= (dy / d) * f;
                    b.vx += (dx / d) * f;
                    b.vy += (dy / d) * f;
                }
            }
            // springs
            for (const l of links) {
                const dx = l.b.x - l.a.x;
                const dy = l.b.y - l.a.y;
                const d = Math.max(1, Math.hypot(dx, dy));
                const target = 78;
                const f = ((d - target) / d) * 0.06 * alphaVal;
                l.a.vx += dx * f;
                l.a.vy += dy * f;
                l.b.vx -= dx * f;
                l.b.vy -= dy * f;
            }
            // centering + integrate
            for (const n of simRef.current) {
                n.vx += (W / 2 - n.x) * 0.0035 * alphaVal;
                n.vy += (H / 2 - n.y) * 0.0035 * alphaVal;
                if (dragRef.current === n.id) {
                    n.vx = 0;
                    n.vy = 0;
                    continue;
                }
                n.vx *= 0.82;
                n.vy *= 0.82;
                n.x = Math.max(16, Math.min(W - 16, n.x + n.vx));
                n.y = Math.max(16, Math.min(H - 16, n.y + n.vy));
            }
            alphaVal *= 0.995;
            notifyRef.current();
            if (alphaVal > 0.02 || dragRef.current)
                raf = requestAnimationFrame(step);
        };
        raf = requestAnimationFrame(step);
        return () => cancelAnimationFrame(raf);
    }, [nodes.length, edges.length]);
    const groups = [...new Set(nodes.map((n) => n.group).filter(Boolean))];
    const colors = ramp(Math.max(1, groups.length || 1));
    const pos = new Map(sim.map((n) => [n.id, n]));
    const W = 320;
    const H = height;
    const degree = new Map();
    for (const e of edges) {
        degree.set(e.source, (degree.get(e.source) ?? 0) + 1);
        degree.set(e.target, (degree.get(e.target) ?? 0) + 1);
    }
    const maxDeg = Math.max(1, ...degree.values());
    const pointer = (e) => {
        const box = boxRef.current?.getBoundingClientRect();
        if (!box)
            return;
        const id = dragRef.current;
        const n = simRef.current.find((x) => x.id === id);
        if (!n)
            return;
        n.x = ((e.clientX - box.left) / box.width) * W;
        n.y = ((e.clientY - box.top) / box.height) * H;
    };
    return ((0, jsx_runtime_1.jsx)(ChartFrame_1.ChartFrame, { title: payload.title, legend: groups.length > 1, seriesNames: groups.length > 1 ? groups : undefined, height: height, children: () => ((0, jsx_runtime_1.jsx)("div", { ref: boxRef, style: { position: 'relative', touchAction: 'none' }, onPointerMove: (e) => dragRef.current && pointer(e), onPointerUp: () => (dragRef.current = null), onPointerLeave: () => (dragRef.current = null), children: (0, jsx_runtime_1.jsxs)("svg", { width: "100%", height: H, viewBox: `0 0 ${W} ${H}`, preserveAspectRatio: "xMidYMid meet", role: "img", "aria-label": "force graph", "data-tick": sim.length, children: [edges.map((e, i) => {
                        const a = pos.get(e.source);
                        const b = pos.get(e.target);
                        if (!a || !b)
                            return null;
                        return (0, jsx_runtime_1.jsx)("line", { x1: a.x, y1: a.y, x2: b.x, y2: b.y, stroke: theme.border, strokeOpacity: 0.6, strokeWidth: Math.min(3, Math.max(0.8, (e.weight ?? 1) * 0.8)) }, i);
                    }), sim.map((n) => {
                        const r = 7 + ((degree.get(n.id) ?? 0) / maxDeg) * 8;
                        const color = n.group && groups.length ? colors[groups.indexOf(n.group) % colors.length] : accent;
                        return ((0, jsx_runtime_1.jsxs)("g", { style: { cursor: 'grab' }, onPointerDown: (ev) => {
                                dragRef.current = n.id;
                                ev.target.setPointerCapture?.(ev.pointerId);
                                notifyRef.current();
                            }, children: [(0, jsx_runtime_1.jsx)("circle", { cx: n.x, cy: n.y, r: r, fill: color, fillOpacity: 0.92, stroke: theme.dark ? '#0F1216' : '#fff', strokeWidth: 1.5 }), (0, jsx_runtime_1.jsx)("text", { x: n.x, y: n.y - r - 4, textAnchor: "middle", fontSize: tokens_1.TYPE.micro, fill: theme.text2, fontWeight: 600, children: n.label })] }, n.id));
                    })] }) })) }));
}
function mulberry(seed) {
    let a = seed * 1000 + 1;
    return () => {
        a |= 0;
        a = (a + 0x6d2b79f5) | 0;
        let t = Math.imul(a ^ (a >>> 15), 1 | a);
        t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
        return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
}
