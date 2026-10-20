"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.Stage2D = Stage2D;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const TitleRow_1 = require("../primitives/TitleRow");
/**
 * Stage2D — the shared simulation frame: a themed canvas, a rAF loop with
 * pause/reset, and the payload's knobs (gravity/speed/damping). Each sim
 * supplies a step(ctx, w, h, dt, state) closure.
 */
function Stage2D({ title, height = 240, knobs, onStep, onReset, hint, }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const canvasRef = (0, react_1.useRef)(null);
    const pointerRef = (0, react_1.useRef)({ x: -999, y: -999, down: false });
    const resetRef = (0, react_1.useRef)({});
    const [running, setRunning] = (0, react_1.useState)(true);
    const K = {
        gravity: knobs.gravity ?? 9.8,
        speed: knobs.speed ?? 1,
        damping: knobs.damping ?? 0.99,
        count: knobs.count ?? 60,
        size: knobs.size ?? 64,
        seed: knobs.seed ?? 7,
        interactive: knobs.interactive !== false,
    };
    const uiRef = (0, react_1.useRef)({ gravity: K.gravity, speed: K.speed, damping: K.damping });
    (0, react_1.useEffect)(() => {
        uiRef.current = { gravity: K.gravity, speed: K.speed, damping: K.damping };
    });
    (0, react_1.useEffect)(() => {
        const canvas = canvasRef.current;
        if (!canvas)
            return;
        const ctx = canvas.getContext('2d');
        if (!ctx)
            return;
        let raf = 0;
        let last = performance.now();
        const dpr = Math.min(2, typeof window !== 'undefined' ? window.devicePixelRatio || 1 : 1);
        const resize = () => {
            const w = canvas.clientWidth;
            canvas.width = w * dpr;
            canvas.height = height * dpr;
            ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        };
        resize();
        const loop = (now) => {
            const dt = Math.min(0.033, (now - last) / 1000);
            last = now;
            if (running) {
                const w = canvas.clientWidth;
                ctx.clearRect(0, 0, w, height);
                onStep(ctx, w, height, dt * uiRef.current.speed, now / 1000, pointerRef.current, uiRef.current, resetRef.current);
            }
            raf = requestAnimationFrame(loop);
        };
        raf = requestAnimationFrame(loop);
        const ro = new ResizeObserver(resize);
        ro.observe(canvas);
        return () => {
            cancelAnimationFrame(raf);
            ro.disconnect();
        };
    }, [running, height, onStep, knobs.seed]);
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: title }), (0, jsx_runtime_1.jsx)("canvas", { ref: canvasRef, style: {
                    width: '100%',
                    height,
                    display: 'block',
                    borderRadius: tokens_1.RADIUS.md,
                    border: `1px solid ${theme.border}`,
                    background: theme.dark ? 'rgba(0,0,0,0.32)' : 'rgba(0,0,0,0.04)',
                    touchAction: 'none',
                    cursor: knobs.interactive ? 'crosshair' : 'default',
                }, onPointerDown: (e) => {
                    const r = e.currentTarget.getBoundingClientRect();
                    pointerRef.current = { x: e.clientX - r.left, y: e.clientY - r.top, down: true };
                    e.currentTarget.setPointerCapture(e.pointerId);
                }, onPointerMove: (e) => {
                    const r = e.currentTarget.getBoundingClientRect();
                    pointerRef.current.x = e.clientX - r.left;
                    pointerRef.current.y = e.clientY - r.top;
                }, onPointerUp: () => (pointerRef.current.down = false), onPointerLeave: () => (pointerRef.current = { x: -999, y: -999, down: false }) }), (0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', alignItems: 'center', gap: 8, marginTop: 8 }, children: [(0, jsx_runtime_1.jsx)("button", { type: "button", onClick: () => setRunning((r) => !r), style: { padding: '4px 12px', borderRadius: 999, border: `1px solid ${accent}`, background: running ? `${accent}1f` : 'transparent', color: accent, fontSize: tokens_1.TYPE.micro, fontWeight: 700, cursor: 'pointer' }, children: running ? 'Pause' : 'Run' }), (0, jsx_runtime_1.jsx)("button", { type: "button", onClick: () => (resetRef.current = {}), style: { padding: '4px 12px', borderRadius: 999, border: `1px solid ${theme.border}`, background: 'transparent', color: theme.text2, fontSize: tokens_1.TYPE.micro, fontWeight: 600, cursor: 'pointer' }, children: "Reset" }), hint ? (0, jsx_runtime_1.jsx)("span", { style: { fontSize: tokens_1.TYPE.micro, color: theme.text3 }, children: hint }) : null] })] }));
}
