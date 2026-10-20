"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.UiRadio = UiRadio;
const jsx_runtime_1 = require("react/jsx-runtime");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
function UiRadio({ label, selected, onSelect, }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    return ((0, jsx_runtime_1.jsxs)("button", { type: "button", role: "radio", "aria-checked": selected, onClick: onSelect, style: {
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            width: '100%',
            textAlign: 'left',
            padding: '9px 12px',
            borderRadius: 12,
            border: `1px solid ${selected ? (0, tokens_1.alpha)(accent, 0.55) : theme.border}`,
            background: selected ? (0, tokens_1.alpha)(accent, theme.dark ? 0.14 : 0.08) : 'transparent',
            cursor: 'pointer',
            fontSize: tokens_1.TYPE.body,
            color: theme.text,
            marginBottom: 6,
        }, children: [(0, jsx_runtime_1.jsx)("span", { style: {
                    width: 18,
                    height: 18,
                    borderRadius: 999,
                    flexShrink: 0,
                    border: `2px solid ${selected ? accent : theme.border}`,
                    display: 'inline-flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                }, children: selected && (0, jsx_runtime_1.jsx)("span", { style: { width: 8, height: 8, borderRadius: 999, background: accent } }) }), label] }));
}
