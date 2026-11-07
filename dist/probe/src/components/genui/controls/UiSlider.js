"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.UiSlider = UiSlider;
exports.formatNumber = formatNumber;
const jsx_runtime_1 = require("react/jsx-runtime");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/** The labelled range slider — the workhorse of calc and form. */
function UiSlider({ label, value, min = 0, max = 100, step = 1, unit, onChange, }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const pct = max > min ? ((value - min) / (max - min)) * 100 : 0;
    return ((0, jsx_runtime_1.jsxs)("label", { style: { display: 'block', padding: '6px 0' }, children: [(label || unit) && ((0, jsx_runtime_1.jsxs)("span", { style: { display: 'flex', justifyContent: 'space-between', fontSize: tokens_1.TYPE.label, color: theme.text2, marginBottom: 6 }, children: [(0, jsx_runtime_1.jsx)("span", { children: label }), (0, jsx_runtime_1.jsxs)("span", { style: { fontWeight: 700, color: accent }, children: [formatNumber(value), unit ? ` ${unit}` : ''] })] })), (0, jsx_runtime_1.jsxs)("span", { style: { position: 'relative', display: 'block', height: 24 }, children: [(0, jsx_runtime_1.jsx)("span", { "aria-hidden": true, style: {
                            position: 'absolute',
                            left: 0,
                            right: 0,
                            top: 10,
                            height: 4,
                            borderRadius: 2,
                            background: theme.raised,
                        } }), (0, jsx_runtime_1.jsx)("span", { "aria-hidden": true, style: {
                            position: 'absolute',
                            left: 0,
                            width: `${pct}%`,
                            top: 10,
                            height: 4,
                            borderRadius: 2,
                            background: accent,
                        } }), (0, jsx_runtime_1.jsx)("input", { type: "range", min: min, max: max, step: step, value: value, onChange: (e) => onChange(Number(e.target.value)), style: {
                            position: 'absolute',
                            inset: 0,
                            width: '100%',
                            margin: 0,
                            appearance: 'none',
                            WebkitAppearance: 'none',
                            background: 'transparent',
                            cursor: 'pointer',
                        } }), (0, jsx_runtime_1.jsx)("span", { "aria-hidden": true, style: {
                            position: 'absolute',
                            left: `calc(${pct}% - 9px)`,
                            top: 3,
                            width: 18,
                            height: 18,
                            borderRadius: 999,
                            background: accent,
                            border: `2px solid ${theme.dark ? '#0F1216' : '#FFFFFF'}`,
                            boxShadow: `0 0 0 1px ${(0, tokens_1.alpha)(accent, 0.4)}`,
                            pointerEvents: 'none',
                        } }), (0, jsx_runtime_1.jsx)("style", { children: `input[type=range]::-webkit-slider-thumb { appearance:none; width:18px; height:18px; border-radius:999px; background:transparent; } input[type=range]::-moz-range-thumb { appearance:none; width:18px; height:18px; border:none; border-radius:999px; background:transparent; }` })] })] }));
}
function formatNumber(n) {
    if (!Number.isFinite(n))
        return String(n);
    if (Math.abs(n) >= 1000000)
        return `${(n / 1000000).toFixed(n % 1000000 === 0 ? 0 : 1)}M`;
    if (Math.abs(n) >= 10000)
        return `${(n / 1000).toFixed(0)}k`;
    return n.toLocaleString(undefined, { maximumFractionDigits: 2 });
}
