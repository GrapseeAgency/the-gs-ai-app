"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.UiTextInput = UiTextInput;
const jsx_runtime_1 = require("react/jsx-runtime");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/** Single-line text family: text | email | url | number | password | otp. */
function UiTextInput({ placeholder, value, onChange, inputType = 'text', inputMode, maxLength, ariaLabel, }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    return ((0, jsx_runtime_1.jsx)("input", { "aria-label": ariaLabel, type: inputType, inputMode: inputMode, maxLength: maxLength, placeholder: placeholder, value: value, onChange: (e) => onChange(e.target.value), style: {
            width: '100%',
            padding: '9px 12px',
            borderRadius: tokens_1.RADIUS.md,
            border: `1px solid ${theme.border}`,
            background: theme.dark ? 'rgba(0,0,0,0.25)' : '#fff',
            color: theme.text,
            fontSize: tokens_1.TYPE.body,
            outline: '2px solid transparent',
            outlineOffset: 1,
            caretColor: accent,
        }, onFocus: (e) => (e.currentTarget.style.outlineColor = `${accent}66`), onBlur: (e) => (e.currentTarget.style.outlineColor = 'transparent') }));
}
