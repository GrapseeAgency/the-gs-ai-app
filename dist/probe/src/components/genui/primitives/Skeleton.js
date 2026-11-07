"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.Skeleton = Skeleton;
const jsx_runtime_1 = require("react/jsx-runtime");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const tokens_1 = require("../theme/tokens");
/**
 * The streaming placeholder. A widget fence that is still arriving renders this
 * — never raw JSON, never a flicker.
 */
function Skeleton({ label = 'Building…', height = 120, style, }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    return ((0, jsx_runtime_1.jsxs)("div", { style: {
            height,
            borderRadius: 14,
            border: `1px solid ${theme.border}`,
            background: `linear-gradient(100deg, ${theme.card} 40%, ${(0, tokens_1.alpha)(accent, 0.10)} 50%, ${theme.card} 60%)`,
            backgroundSize: '200% 100%',
            animation: 'gs-sweep 1.4s ease infinite',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: tokens_1.TYPE.label,
            color: theme.text3,
            gap: 8,
            ...style,
        }, children: [(0, jsx_runtime_1.jsx)("span", { style: {
                    width: 8,
                    height: 8,
                    borderRadius: 999,
                    background: accent,
                    animation: 'gs-pulse 1.2s ease infinite',
                } }), label, (0, jsx_runtime_1.jsx)("style", { children: `@keyframes gs-sweep { to { background-position: -200% 0 } } @keyframes gs-pulse { 50% { opacity: .3 } }` })] }));
}
