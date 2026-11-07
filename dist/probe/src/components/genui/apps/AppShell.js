"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.AppShell = AppShell;
const jsx_runtime_1 = require("react/jsx-runtime");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/** Shared app chrome: title + optional submit row. */
function AppShell({ title, right, children, submitLabel }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    return ((0, jsx_runtime_1.jsxs)("div", { children: [title ? ((0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 12 }, children: [(0, jsx_runtime_1.jsx)("span", { style: { fontSize: tokens_1.TYPE.heading, fontWeight: 700, color: theme.text }, children: title }), right ? (0, jsx_runtime_1.jsx)("span", { style: { fontSize: tokens_1.TYPE.micro, color: theme.text3 }, children: right }) : null] })) : null, children, submitLabel ? ((0, jsx_runtime_1.jsx)("div", { style: { marginTop: 14 }, children: (0, jsx_runtime_1.jsx)("button", { type: "button", style: {
                        width: '100%',
                        padding: '11px 0',
                        borderRadius: 999,
                        border: 'none',
                        background: accent,
                        color: theme.dark ? '#0F1216' : '#fff',
                        fontSize: tokens_1.TYPE.label,
                        fontWeight: 700,
                        cursor: 'pointer',
                    }, children: submitLabel }) })) : null] }));
}
