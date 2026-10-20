"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.CodeBlock = CodeBlock;
const jsx_runtime_1 = require("react/jsx-runtime");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const CopyChip_1 = require("../primitives/CopyChip");
function CodeBlock({ code, language }) {
    const { theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    return ((0, jsx_runtime_1.jsxs)("div", { children: [language ? ((0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }, children: [(0, jsx_runtime_1.jsx)("span", { style: { fontSize: tokens_1.TYPE.micro, fontWeight: 700, color: theme.text3, textTransform: 'uppercase', letterSpacing: '0.05em' }, children: language }), (0, jsx_runtime_1.jsx)(CopyChip_1.CopyChip, { value: code })] })) : null, (0, jsx_runtime_1.jsx)("pre", { style: {
                    margin: 0,
                    padding: 14,
                    borderRadius: tokens_1.RADIUS.md,
                    border: `1px solid ${theme.border}`,
                    background: theme.dark ? 'rgba(0,0,0,0.32)' : 'rgba(0,0,0,0.03)',
                    color: theme.text2,
                    fontSize: tokens_1.TYPE.micro,
                    lineHeight: 1.6,
                    overflowX: 'auto',
                    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
                }, children: (0, jsx_runtime_1.jsx)("code", { children: code }) })] }));
}
