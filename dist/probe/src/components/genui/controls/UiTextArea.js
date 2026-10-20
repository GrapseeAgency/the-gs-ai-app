"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.UiTextArea = UiTextArea;
const jsx_runtime_1 = require("react/jsx-runtime");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
function UiTextArea({ placeholder, value, rows = 3, onChange, }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    return ((0, jsx_runtime_1.jsx)("textarea", { rows: rows, placeholder: placeholder, value: value, onChange: (e) => onChange(e.target.value), style: {
            width: '100%',
            padding: '9px 12px',
            borderRadius: tokens_1.RADIUS.md,
            border: `1px solid ${theme.border}`,
            background: theme.dark ? 'rgba(0,0,0,0.25)' : '#fff',
            color: theme.text,
            fontSize: tokens_1.TYPE.body,
            resize: 'vertical',
            outline: '2px solid transparent',
            outlineOffset: 1,
            caretColor: accent,
            fontFamily: 'inherit',
        }, onFocus: (e) => (e.currentTarget.style.outlineColor = `${accent}66`), onBlur: (e) => (e.currentTarget.style.outlineColor = 'transparent') }));
}
