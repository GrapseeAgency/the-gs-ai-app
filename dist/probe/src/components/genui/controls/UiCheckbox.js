"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.UiCheckbox = UiCheckbox;
const jsx_runtime_1 = require("react/jsx-runtime");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
function UiCheckbox({ label, checked, onChange, }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    return ((0, jsx_runtime_1.jsxs)("button", { type: "button", role: "checkbox", "aria-checked": checked, onClick: () => onChange(!checked), style: {
            display: 'inline-flex',
            alignItems: 'center',
            gap: 10,
            background: 'transparent',
            border: 'none',
            padding: 0,
            cursor: 'pointer',
            textAlign: 'left',
        }, children: [(0, jsx_runtime_1.jsx)("span", { style: {
                    width: 20,
                    height: 20,
                    borderRadius: 6,
                    flexShrink: 0,
                    display: 'inline-flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    background: checked ? accent : 'transparent',
                    border: `2px solid ${checked ? accent : theme.border}`,
                    color: checked ? (theme.dark ? '#0F1216' : '#fff') : 'transparent',
                    fontSize: 13,
                    fontWeight: 700,
                    lineHeight: 1,
                }, children: "\u2713" }), label ? (0, jsx_runtime_1.jsx)("span", { style: { fontSize: tokens_1.TYPE.body, color: theme.text }, children: label }) : null] }));
}
