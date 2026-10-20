"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.UiSegmented = UiSegmented;
const jsx_runtime_1 = require("react/jsx-runtime");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/** Segmented control — exclusive choice, always visible. */
function UiSegmented({ options, value, onChange, }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const norm = options.map((o) => (typeof o === 'string' ? { label: o, value: o } : o));
    return ((0, jsx_runtime_1.jsx)("div", { role: "radiogroup", style: {
            display: 'inline-flex',
            padding: 3,
            gap: 2,
            borderRadius: tokens_1.RADIUS.pill,
            background: theme.dark ? 'rgba(0,0,0,0.3)' : 'rgba(0,0,0,0.05)',
        }, children: norm.map((o) => {
            const active = o.value === value;
            return ((0, jsx_runtime_1.jsx)("button", { type: "button", role: "radio", "aria-checked": active, onClick: () => onChange(o.value), style: {
                    border: 'none',
                    padding: '5px 14px',
                    borderRadius: tokens_1.RADIUS.pill,
                    fontSize: tokens_1.TYPE.label,
                    fontWeight: 600,
                    cursor: 'pointer',
                    background: active ? accent : 'transparent',
                    color: active ? (0, tokens_1.inkOn)(accent) : theme.text2,
                }, children: o.label }, o.value));
        }) }));
}
