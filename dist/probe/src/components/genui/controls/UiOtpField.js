"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.UiOtpField = UiOtpField;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/** One-time-code input — N boxes, auto-advance, backspace retreat. */
function UiOtpField({ length = 6, value, onChange }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const refs = (0, react_1.useRef)([]);
    const chars = Array.from({ length }, (_, i) => value[i] ?? '');
    return ((0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', gap: 6 }, role: "group", "aria-label": "verification code", children: chars.map((c, i) => ((0, jsx_runtime_1.jsx)("input", { ref: (el) => {
                refs.current[i] = el;
            }, value: c, inputMode: "numeric", maxLength: 1, "aria-label": `digit ${i + 1}`, onChange: (e) => {
                const d = e.target.value.replace(/\D/g, '');
                const next = (value.slice(0, i) + d + value.slice(i + 1)).slice(0, length);
                onChange(next);
                if (d && i < length - 1)
                    refs.current[i + 1]?.focus();
            }, onKeyDown: (e) => {
                if (e.key === 'Backspace' && !chars[i] && i > 0)
                    refs.current[i - 1]?.focus();
            }, style: {
                width: 38,
                height: 44,
                textAlign: 'center',
                fontSize: tokens_1.TYPE.title,
                fontWeight: 700,
                borderRadius: tokens_1.RADIUS.md,
                border: `1px solid ${c ? accent : theme.border}`,
                background: theme.dark ? 'rgba(0,0,0,0.25)' : '#fff',
                color: theme.text,
                outline: 'none',
                caretColor: accent,
            } }, i))) }));
}
