"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.UiSelect = UiSelect;
const jsx_runtime_1 = require("react/jsx-runtime");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/** A native select skinned to the widget identity — reliable on every device. */
function UiSelect({ value, options, onChange, ariaLabel, }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    return ((0, jsx_runtime_1.jsx)("select", { "aria-label": ariaLabel, value: value, onChange: (e) => onChange(e.target.value), style: {
            width: '100%',
            padding: '9px 12px',
            borderRadius: tokens_1.RADIUS.md,
            border: `1px solid ${theme.border}`,
            background: theme.dark ? 'rgba(0,0,0,0.25)' : '#fff',
            color: theme.text,
            fontSize: tokens_1.TYPE.body,
            appearance: 'none',
            outline: `2px solid transparent`,
            outlineOffset: 1,
        }, onFocus: (e) => (e.currentTarget.style.outlineColor = alphaSafe(accent)), onBlur: (e) => (e.currentTarget.style.outlineColor = 'transparent'), children: options.map((o) => ((0, jsx_runtime_1.jsx)("option", { value: o, children: o }, o))) }));
}
function alphaSafe(hex) {
    return `${hex}66`;
}
