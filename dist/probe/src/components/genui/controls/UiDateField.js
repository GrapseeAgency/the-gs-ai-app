"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.UiDateField = UiDateField;
const jsx_runtime_1 = require("react/jsx-runtime");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/** date | time | datetime — the native picker, skinned. */
function UiDateField({ value, onChange, inputType = 'date', }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    return ((0, jsx_runtime_1.jsx)("input", { type: inputType, value: value, onChange: (e) => onChange(e.target.value), style: {
            width: '100%',
            padding: '9px 12px',
            borderRadius: tokens_1.RADIUS.md,
            border: `1px solid ${theme.border}`,
            background: theme.dark ? 'rgba(0,0,0,0.25)' : '#fff',
            color: theme.text,
            fontSize: tokens_1.TYPE.body,
            colorScheme: theme.dark ? 'dark' : 'light',
            outline: '2px solid transparent',
            outlineOffset: 1,
        }, onFocus: (e) => (e.currentTarget.style.outlineColor = `${accent}66`), onBlur: (e) => (e.currentTarget.style.outlineColor = 'transparent') }));
}
