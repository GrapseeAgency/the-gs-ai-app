"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.UiToggle = UiToggle;
const jsx_runtime_1 = require("react/jsx-runtime");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
function UiToggle({ label, checked, onChange, }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    return ((0, jsx_runtime_1.jsxs)("button", { type: "button", role: "switch", "aria-checked": checked, onClick: () => onChange(!checked), style: {
            display: 'inline-flex',
            alignItems: 'center',
            gap: 10,
            background: 'transparent',
            border: 'none',
            padding: 0,
            cursor: 'pointer',
        }, children: [(0, jsx_runtime_1.jsx)("span", { style: {
                    width: 40,
                    height: 22,
                    borderRadius: 999,
                    padding: 2,
                    background: checked ? accent : theme.raised,
                    border: `1px solid ${checked ? accent : theme.border}`,
                    display: 'inline-flex',
                    justifyContent: checked ? 'flex-end' : 'flex-start',
                    transition: 'background .18s ease',
                }, children: (0, jsx_runtime_1.jsx)("span", { style: {
                        width: 16,
                        height: 16,
                        borderRadius: 999,
                        background: checked ? (theme.dark ? '#0F1216' : '#fff') : theme.text3,
                    } }) }), label ? (0, jsx_runtime_1.jsx)("span", { style: { fontSize: tokens_1.TYPE.body, color: theme.text }, children: label }) : null] }));
}
