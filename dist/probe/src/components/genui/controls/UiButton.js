"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.UiButton = UiButton;
const jsx_runtime_1 = require("react/jsx-runtime");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/** The GenUI button: filled / tonal / text, accent-driven. */
function UiButton({ children, onClick, buttonStyle = 'tonal', disabled = false, style, }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const faces = {
        filled: { background: accent, color: (0, tokens_1.inkOn)(accent), border: `1px solid ${accent}` },
        tonal: { background: (0, tokens_1.alpha)(accent, theme.dark ? 0.2 : 0.14), color: accent, border: `1px solid ${(0, tokens_1.alpha)(accent, 0.3)}` },
        text: { background: 'transparent', color: accent, border: '1px solid transparent' },
    };
    return ((0, jsx_runtime_1.jsx)("button", { type: "button", onClick: onClick, disabled: disabled, style: {
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 6,
            padding: '8px 16px',
            borderRadius: tokens_1.RADIUS.pill,
            fontSize: tokens_1.TYPE.label,
            fontWeight: 600,
            cursor: disabled ? 'default' : 'pointer',
            opacity: disabled ? 0.5 : 1,
            transition: 'filter .15s ease, transform .06s ease',
            ...faces[buttonStyle],
            ...style,
        }, onMouseDown: (e) => {
            if (!disabled)
                e.currentTarget.style.filter = 'brightness(0.92)';
        }, onMouseUp: (e) => {
            e.currentTarget.style.filter = '';
        }, onMouseLeave: (e) => {
            e.currentTarget.style.filter = '';
        }, children: children }));
}
