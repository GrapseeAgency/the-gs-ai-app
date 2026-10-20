"use strict";
'use client';
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.CanvasWidget = CanvasWidget;
const jsx_runtime_1 = require("react/jsx-runtime");
/**
 * The canvas kind: four freeform pointer surfaces.
 * whiteboard (draw) · mindmap (draggable nodes) · diagram (connect nodes) ·
 * polygons (draw polygons over a live Leaflet map)
 */
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const TitleRow_1 = require("../primitives/TitleRow");
function CanvasWidget({ payload }) {
    const surface = payload.surface ?? 'whiteboard';
    if (surface === 'mindmap')
        return (0, jsx_runtime_1.jsx)(MindMapSurface, { payload: payload });
    if (surface === 'diagram')
        return (0, jsx_runtime_1.jsx)(DiagramSurface, { payload: payload });
    if (surface === 'polygons')
        return (0, jsx_runtime_1.jsx)(PolygonSurface, { payload: payload });
    return (0, jsx_runtime_1.jsx)(WhiteboardSurface, { payload: payload });
}
// ---------------------------------------------------------------- whiteboard
const PENS = ['#4C6FFF', '#0CA678', '#E8590C', '#D6336C', '#6741D9'];
function WhiteboardSurface({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const canvasRef = (0, react_1.useRef)(null);
    const drawing = (0, react_1.useRef)(false);
    const last = (0, react_1.useRef)(null);
    const [pen, setPen] = (0, react_1.useState)(accent);
    const [eraser, setEraser] = (0, react_1.useState)(false);
    const [strokes, setStrokes] = (0, react_1.useState)(0);
    (0, react_1.useEffect)(() => {
        const canvas = canvasRef.current;
        if (!canvas)
            return;
        const ctx = canvas.getContext('2d');
        if (!ctx)
            return;
        const dpr = Math.min(2, window.devicePixelRatio || 1);
        const resize = () => {
            const snapshot = canvas.toDataURL();
            canvas.width = canvas.clientWidth * dpr;
            canvas.height = 260 * dpr;
            ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
            ctx.lineCap = 'round';
            ctx.lineJoin = 'round';
            const img = new Image();
            img.onload = () => ctx.drawImage(img, 0, 0, canvas.clientWidth, 260);
            img.src = snapshot;
        };
        resize();
        const ro = new ResizeObserver(resize);
        ro.observe(canvas);
        return () => ro.disconnect();
    }, []);
    const pos = (e) => {
        const r = e.currentTarget.getBoundingClientRect();
        return { x: e.clientX - r.left, y: e.clientY - r.top };
    };
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title ?? 'Whiteboard', right: `${strokes} strokes` }), (0, jsx_runtime_1.jsx)("canvas", { ref: canvasRef, style: { width: '100%', height: 260, display: 'block', borderRadius: tokens_1.RADIUS.md, border: `1px solid ${theme.border}`, background: theme.dark ? 'rgba(0,0,0,0.32)' : '#fff', touchAction: 'none', cursor: 'crosshair' }, onPointerDown: (e) => {
                    drawing.current = true;
                    last.current = pos(e);
                    e.currentTarget.setPointerCapture(e.pointerId);
                }, onPointerMove: (e) => {
                    if (!drawing.current || !last.current)
                        return;
                    const ctx = canvasRef.current?.getContext('2d');
                    if (!ctx)
                        return;
                    const p = pos(e);
                    ctx.strokeStyle = eraser ? (theme.dark ? 'rgba(0,0,0,1)' : '#ffffff') : pen;
                    ctx.lineWidth = eraser ? 22 : 3;
                    ctx.beginPath();
                    ctx.moveTo(last.current.x, last.current.y);
                    ctx.lineTo(p.x, p.y);
                    ctx.stroke();
                    last.current = p;
                    setStrokes((s) => s + 1);
                }, onPointerUp: () => {
                    drawing.current = false;
                    last.current = null;
                } }), (0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', alignItems: 'center', gap: 6, marginTop: 8, flexWrap: 'wrap' }, children: [PENS.map((c) => ((0, jsx_runtime_1.jsx)("button", { type: "button", "aria-label": `pen ${c}`, onClick: () => {
                            setPen(c);
                            setEraser(false);
                        }, style: { width: 24, height: 24, borderRadius: 999, background: c, border: `2px solid ${pen === c && !eraser ? theme.text : 'transparent'}`, cursor: 'pointer' } }, c))), (0, jsx_runtime_1.jsx)("button", { type: "button", onClick: () => setEraser((v) => !v), style: { padding: '4px 12px', borderRadius: 999, border: `1px solid ${eraser ? accent : theme.border}`, background: eraser ? `${accent}1f` : 'transparent', color: eraser ? accent : theme.text2, fontSize: tokens_1.TYPE.micro, fontWeight: 700, cursor: 'pointer' }, children: "Eraser" }), (0, jsx_runtime_1.jsx)("button", { type: "button", onClick: () => {
                            const canvas = canvasRef.current;
                            const ctx = canvas?.getContext('2d');
                            if (canvas && ctx)
                                ctx.clearRect(0, 0, canvas.clientWidth, 260);
                            setStrokes(0);
                        }, style: { padding: '4px 12px', borderRadius: 999, border: `1px solid ${theme.border}`, background: 'transparent', color: theme.text2, fontSize: tokens_1.TYPE.micro, fontWeight: 600, cursor: 'pointer' }, children: "Clear" })] })] }));
}
// ------------------------------------------------------------------- mindmap
function MindMapSurface({ payload }) {
    const { accent, ramp, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const [nodes, setNodes] = (0, react_1.useState)(() => (payload.nodes ?? []).map((n, i) => ({ ...n, id: n.id ?? i, label: n.label ?? `Node ${i + 1}`, x: n.x ?? 0.2 + (i % 4) * 0.2, y: n.y ?? 0.25 + Math.floor(i / 4) * 0.3 })));
    const [edges] = (0, react_1.useState)(() => (payload.edges ?? []).filter(([a, b]) => a < (payload.nodes?.length ?? 0) && b < (payload.nodes?.length ?? 0)));
    const drag = (0, react_1.useRef)(null);
    const boxRef = (0, react_1.useRef)(null);
    const groups = [...new Set(nodes.map((n) => n.group).filter(Boolean))];
    const colors = ramp(Math.max(1, groups.length || 1));
    const move = (e) => {
        if (drag.current === null || !boxRef.current)
            return;
        const r = boxRef.current.getBoundingClientRect();
        const x = Math.max(0.02, Math.min(0.98, (e.clientX - r.left) / r.width));
        const y = Math.max(0.04, Math.min(0.96, (e.clientY - r.top) / r.height));
        setNodes((ns) => ns.map((n) => (n.id === drag.current ? { ...n, x, y } : n)));
    };
    const addChild = (parent) => {
        setNodes((ns) => {
            const id = Math.max(-1, ...ns.map((n) => n.id)) + 1;
            const p = ns.find((n) => n.id === parent);
            const node = { id, label: `Idea ${ns.length + 1}`, x: (p?.x ?? 0.5) + 0.16, y: (p?.y ?? 0.5) + (ns.length % 2 ? 0.14 : -0.14), group: p?.group };
            edges.push([parent, id]);
            setEdgesReal([...edges, [parent, id]]);
            return [...ns, node];
        });
    };
    const [realEdges, setEdgesReal] = (0, react_1.useState)(edges);
    const posOf = (id) => nodes.find((n) => n.id === id);
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title ?? 'Mind map', right: "drag nodes \u00B7 tap \u2295 to branch" }), (0, jsx_runtime_1.jsxs)("div", { ref: boxRef, style: { position: 'relative', height: 280, borderRadius: tokens_1.RADIUS.md, border: `1px solid ${theme.border}`, background: theme.dark ? 'rgba(0,0,0,0.28)' : 'rgba(0,0,0,0.03)', overflow: 'hidden', touchAction: 'none' }, onPointerMove: move, onPointerUp: () => (drag.current = null), children: [(0, jsx_runtime_1.jsx)("svg", { width: "100%", height: "100%", style: { position: 'absolute', inset: 0 }, children: realEdges.map(([a, b], i) => {
                            const na = posOf(a);
                            const nb = posOf(b);
                            if (!na || !nb)
                                return null;
                            const x1 = na.x * 100;
                            const y1 = na.y * 100;
                            const x2 = nb.x * 100;
                            const y2 = nb.y * 100;
                            return ((0, jsx_runtime_1.jsx)("path", { d: `M ${x1}% ${y1}% C ${(x1 + x2) / 2}% ${y1}%, ${(x1 + x2) / 2}% ${y2}%, ${x2}% ${y2}%`, fill: "none", stroke: (0, tokens_1.alpha)(accent, 0.5), strokeWidth: 2 }, i));
                        }) }), nodes.map((n) => ((0, jsx_runtime_1.jsxs)("div", { style: {
                            position: 'absolute',
                            left: `${n.x * 100}%`,
                            top: `${n.y * 100}%`,
                            transform: 'translate(-50%,-50%)',
                            padding: '6px 12px',
                            borderRadius: 999,
                            background: n.group && groups.length ? (0, tokens_1.alpha)(colors[groups.indexOf(n.group) % colors.length], 0.16) : (0, tokens_1.alpha)(accent, 0.14),
                            border: `1.5px solid ${n.group && groups.length ? colors[groups.indexOf(n.group) % colors.length] : accent}`,
                            fontSize: tokens_1.TYPE.label,
                            fontWeight: 600,
                            color: theme.text,
                            cursor: 'grab',
                            userSelect: 'none',
                            whiteSpace: 'nowrap',
                            display: 'flex',
                            alignItems: 'center',
                            gap: 6,
                        }, onPointerDown: (e) => {
                            drag.current = n.id;
                            e.target.setPointerCapture?.(e.pointerId);
                        }, children: [n.label, (0, jsx_runtime_1.jsx)("button", { type: "button", "aria-label": "add branch", onPointerDown: (e) => e.stopPropagation(), onClick: () => addChild(n.id), style: { width: 16, height: 16, borderRadius: 999, border: 'none', background: accent, color: theme.dark ? '#0F1216' : '#fff', fontSize: 11, lineHeight: 1, cursor: 'pointer', fontWeight: 800 }, children: "+" })] }, n.id)))] })] }));
}
// ------------------------------------------------------------------- diagram
function DiagramSurface({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const shapes = ['start', 'step', 'decision', 'end'];
    const [nodes, setNodes] = (0, react_1.useState)(() => (payload.nodes ?? []).map((n, i) => ({ id: n.id ?? i, label: n.label ?? `Step ${i + 1}`, x: n.x ?? 0.15 + (i % 3) * 0.3, y: n.y ?? 0.3 + Math.floor(i / 3) * 0.35, shape: shapes[Math.min(shapes.length - 1, i % 4)] })));
    const [links, setLinks] = (0, react_1.useState)(() => (payload.edges ?? []).filter(([a, b]) => a < (payload.nodes?.length ?? 0) && b < (payload.nodes?.length ?? 0)));
    const [linkFrom, setLinkFrom] = (0, react_1.useState)(null);
    const drag = (0, react_1.useRef)(null);
    const boxRef = (0, react_1.useRef)(null);
    const move = (e) => {
        if (drag.current === null || !boxRef.current)
            return;
        const r = boxRef.current.getBoundingClientRect();
        const x = Math.max(0.08, Math.min(0.92, (e.clientX - r.left) / r.width));
        const y = Math.max(0.08, Math.min(0.92, (e.clientY - r.top) / r.height));
        setNodes((ns) => ns.map((n) => (n.id === drag.current ? { ...n, x, y } : n)));
    };
    const tap = (id) => {
        if (linkFrom === null) {
            setLinkFrom(id);
            return;
        }
        if (linkFrom !== id)
            setLinks((ls) => (ls.some(([a, b]) => a === linkFrom && b === id) ? ls : [...ls, [linkFrom, id]]));
        setLinkFrom(null);
    };
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title ?? 'Diagram editor', right: linkFrom !== null ? 'tap a target node…' : 'drag to move · tap two nodes to connect' }), (0, jsx_runtime_1.jsxs)("div", { ref: boxRef, style: { position: 'relative', height: 300, borderRadius: tokens_1.RADIUS.md, border: `1px solid ${theme.border}`, background: theme.dark ? 'rgba(0,0,0,0.28)' : 'rgba(0,0,0,0.03)', overflow: 'hidden', touchAction: 'none' }, onPointerMove: move, onPointerUp: () => (drag.current = null), children: [(0, jsx_runtime_1.jsxs)("svg", { width: "100%", height: "100%", style: { position: 'absolute', inset: 0, pointerEvents: 'none' }, children: [(0, jsx_runtime_1.jsx)("defs", { children: (0, jsx_runtime_1.jsx)("marker", { id: "gs-arrow", viewBox: "0 0 10 10", refX: "9", refY: "5", markerWidth: "7", markerHeight: "7", orient: "auto-start-reverse", children: (0, jsx_runtime_1.jsx)("path", { d: "M 0 0 L 10 5 L 0 10 z", fill: (0, tokens_1.alpha)(accent, 0.8) }) }) }), links.map(([a, b], i) => {
                                const na = nodes.find((n) => n.id === a);
                                const nb = nodes.find((n) => n.id === b);
                                if (!na || !nb)
                                    return null;
                                const x1 = na.x * 100;
                                const y1 = na.y * 100;
                                const x2 = nb.x * 100;
                                const y2 = nb.y * 100;
                                return (0, jsx_runtime_1.jsx)("line", { x1: `${x1}%`, y1: `${y1}%`, x2: `${x2}%`, y2: `${y2}%`, stroke: (0, tokens_1.alpha)(accent, 0.75), strokeWidth: 2, markerEnd: "url(#gs-arrow)" }, i);
                            })] }), nodes.map((n) => {
                        const isDecision = n.shape === 'decision';
                        const selected = linkFrom === n.id;
                        return ((0, jsx_runtime_1.jsx)("button", { type: "button", style: {
                                position: 'absolute',
                                left: `${n.x * 100}%`,
                                top: `${n.y * 100}%`,
                                transform: 'translate(-50%,-50%)',
                                padding: isDecision ? '10px 18px' : '8px 14px',
                                borderRadius: isDecision ? 4 : n.shape === 'start' || n.shape === 'end' ? 999 : 10,
                                background: selected ? accent : (0, tokens_1.alpha)(accent, 0.13),
                                border: `1.5px solid ${selected ? accent : (0, tokens_1.alpha)(accent, 0.5)}`,
                                color: selected ? (theme.dark ? '#0F1216' : '#fff') : theme.text,
                                fontSize: tokens_1.TYPE.label,
                                fontWeight: 600,
                                cursor: 'grab',
                                userSelect: 'none',
                                whiteSpace: 'nowrap',
                            }, onPointerDown: (e) => {
                                drag.current = n.id;
                                e.target.setPointerCapture?.(e.pointerId);
                            }, onClick: () => tap(n.id), children: n.label }, n.id));
                    })] }), links.length > 0 && ((0, jsx_runtime_1.jsxs)("button", { type: "button", onClick: () => setLinks([]), style: { marginTop: 8, padding: '4px 12px', borderRadius: 999, border: `1px solid ${theme.border}`, background: 'transparent', color: theme.text2, fontSize: tokens_1.TYPE.micro, cursor: 'pointer' }, children: ["Clear connections (", links.length, ")"] }))] }));
}
// ------------------------------------------------------------------ polygons
function PolygonSurface({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const holder = (0, react_1.useRef)(null);
    const mapRef = (0, react_1.useRef)(null);
    const polyRef = (0, react_1.useRef)(null);
    const [pts, setPts] = (0, react_1.useState)([]);
    const [mode, setMode] = (0, react_1.useState)(false);
    (0, react_1.useEffect)(() => {
        let cancelled = false;
        const boot = async () => {
            const L = (await Promise.resolve().then(() => __importStar(require('leaflet')))).default;
            if (cancelled || !holder.current || mapRef.current)
                return;
            const map = L.map(holder.current, { scrollWheelZoom: false, attributionControl: false });
            window.__gsL = L;
            L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19 }).addTo(map);
            map.setView([23.8103, 90.4125], 12);
            map.on('click', (e) => {
                if (!mode)
                    return;
                setPts((p) => [...p, [e.latlng.lat, e.latlng.lng]]);
            });
            mapRef.current = map;
        };
        boot();
        return () => {
            cancelled = true;
            mapRef.current?.remove();
            mapRef.current = null;
        };
    }, []);
    (0, react_1.useEffect)(() => {
        const L = window.__gsL;
        const map = mapRef.current;
        if (!L || !map)
            return;
        if (polyRef.current) {
            polyRef.current.remove();
            polyRef.current = null;
        }
        if (pts.length >= 3) {
            polyRef.current = L.polygon(pts, { color: accent, weight: 2, fillColor: accent, fillOpacity: 0.25 }).addTo(map);
        }
        else if (pts.length >= 1) {
            polyRef.current = L.polyline(pts, { color: accent, dashArray: '5 6' }).addTo(map);
        }
    }, [pts, accent]);
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title ?? 'Zone editor', right: `${pts.length} vertices` }), (0, jsx_runtime_1.jsx)("div", { ref: holder, style: { height: 260, borderRadius: tokens_1.RADIUS.md, overflow: 'hidden', border: `1px solid ${theme.border}`, cursor: mode ? 'crosshair' : 'grab' } }), (0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', gap: 6, marginTop: 8 }, children: [(0, jsx_runtime_1.jsx)("button", { type: "button", onClick: () => setMode((m) => !m), style: { padding: '5px 13px', borderRadius: 999, border: `1px solid ${mode ? accent : theme.border}`, background: mode ? `${accent}1f` : 'transparent', color: mode ? accent : theme.text2, fontSize: tokens_1.TYPE.micro, fontWeight: 700, cursor: 'pointer' }, children: mode ? 'Drawing — tap the map' : 'Draw zone' }), (0, jsx_runtime_1.jsx)("button", { type: "button", onClick: () => setPts([]), style: { padding: '5px 13px', borderRadius: 999, border: `1px solid ${theme.border}`, background: 'transparent', color: theme.text2, fontSize: tokens_1.TYPE.micro, fontWeight: 600, cursor: 'pointer' }, children: "Reset" }), pts.length >= 3 && ((0, jsx_runtime_1.jsxs)("span", { style: { alignSelf: 'center', fontSize: tokens_1.TYPE.micro, color: theme.text3 }, children: ["\u2248 ", Math.round(polygonAreaKm2(pts)), " km\u00B2"] }))] })] }));
}
function polygonAreaKm2(pts) {
    let a = 0;
    for (let i = 0; i < pts.length; i++) {
        const [lat1, lng1] = pts[i];
        const [lat2, lng2] = pts[(i + 1) % pts.length];
        a += (lng2 - lng1) * Math.cos(((lat1 + lat2) / 2) * (Math.PI / 180)) * (lat2 + lat1);
    }
    return (Math.abs(a) / 2) * (111.32 * 111.32);
}
