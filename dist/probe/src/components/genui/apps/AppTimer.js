"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.AppTimer = AppTimer;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const AppShell_1 = require("./AppShell");
const UiButton_1 = require("../controls/UiButton");
const UiStepper_1 = require("../controls/UiStepper");
/** Timer — countdown with start/pause/reset and a progress ring. */
function AppTimer({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const defaultMinutes = Math.max(1, Math.min(180, Number((payload.items ?? [])[0]?.price ?? 25)));
    const [minutes, setMinutes] = (0, react_1.useState)(defaultMinutes);
    const [left, setLeft] = (0, react_1.useState)(defaultMinutes * 60);
    const [running, setRunning] = (0, react_1.useState)(false);
    const raf = (0, react_1.useRef)(null);
    (0, react_1.useEffect)(() => {
        if (!running)
            return;
        const id = window.setInterval(() => {
            setLeft((l) => {
                if (l <= 1) {
                    setRunning(false);
                    return 0;
                }
                return l - 1;
            });
        }, 1000);
        raf.current = id;
        return () => window.clearInterval(id);
    }, [running]);
    const total = minutes * 60;
    const frac = total > 0 ? left / total : 0;
    const size = 150;
    const r = (size - 14) / 2;
    const circ = 2 * Math.PI * r;
    const mm = String(Math.floor(left / 60)).padStart(2, '0');
    const ss = String(left % 60).padStart(2, '0');
    return ((0, jsx_runtime_1.jsx)(AppShell_1.AppShell, { title: payload.title ?? 'Focus timer', children: (0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 14 }, children: [(0, jsx_runtime_1.jsxs)("div", { style: { position: 'relative', width: size, height: size }, children: [(0, jsx_runtime_1.jsxs)("svg", { width: size, height: size, style: { transform: 'rotate(-90deg)' }, children: [(0, jsx_runtime_1.jsx)("circle", { cx: size / 2, cy: size / 2, r: r, fill: "none", stroke: theme.dark ? 'rgba(255,255,255,0.07)' : 'rgba(0,0,0,0.06)', strokeWidth: 11 }), (0, jsx_runtime_1.jsx)("circle", { cx: size / 2, cy: size / 2, r: r, fill: "none", stroke: accent, strokeWidth: 11, strokeLinecap: "round", strokeDasharray: `${frac * circ} ${circ}`, style: { transition: 'stroke-dasharray .5s linear' } })] }), (0, jsx_runtime_1.jsxs)("div", { style: { position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 30, fontWeight: 700, color: theme.text, fontVariantNumeric: 'tabular-nums' }, children: [mm, ":", ss] })] }), !running && left === total && (0, jsx_runtime_1.jsx)(UiStepper_1.UiStepper, { value: minutes, min: 1, max: 180, unit: "min", onChange: (v) => { setMinutes(v); setLeft(v * 60); } }), (0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', gap: 8 }, children: [(0, jsx_runtime_1.jsx)(UiButton_1.UiButton, { buttonStyle: "filled", onClick: () => setRunning((r2) => !r2), disabled: left === 0, children: running ? 'Pause' : 'Start' }), (0, jsx_runtime_1.jsx)(UiButton_1.UiButton, { onClick: () => {
                                setRunning(false);
                                setLeft(minutes * 60);
                            }, children: "Reset" })] }), left === 0 && (0, jsx_runtime_1.jsx)("span", { style: { fontSize: tokens_1.TYPE.label, color: accent, fontWeight: 700 }, children: "Done \u2014 take a break." })] }) }));
}
