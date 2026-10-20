"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.UiStepper = UiStepper;
const jsx_runtime_1 = require("react/jsx-runtime");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/** A numeric stepper — minus / value / plus. */
function UiStepper({ value, onChange, min = 0, max = 99, unit, }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const round = (n) => Math.min(max, Math.max(min, n));
    const btn = (label, delta) => ((0, jsx_runtime_1.jsx)("button", { type: "button", onClick: () => onChange(round(value + delta)), style: {
            width: 30,
            height: 30,
            borderRadius: tokens_1.RADIUS.md,
            border: `1px solid ${theme.border}`,
            background: 'transparent',
            color: accent,
            fontSize: 17,
            fontWeight: 700,
            cursor: 'pointer',
            lineHeight: 1,
        }, children: label }));
    return ((0, jsx_runtime_1.jsxs)("div", { style: { display: 'inline-flex', alignItems: 'center', gap: 10 }, children: [btn('−', -1), (0, jsx_runtime_1.jsxs)("span", { style: { minWidth: 44, textAlign: 'center', fontSize: tokens_1.TYPE.body, fontWeight: 700, color: theme.text }, children: [value, unit ? (0, jsx_runtime_1.jsxs)("span", { style: { color: theme.text2, fontWeight: 400 }, children: [" ", unit] }) : null] }), btn('+', 1)] }));
}
