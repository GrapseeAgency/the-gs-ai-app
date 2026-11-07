"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.ProgressWidget = ProgressWidget;
const jsx_runtime_1 = require("react/jsx-runtime");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const TitleRow_1 = require("../primitives/TitleRow");
const EmptyState_1 = require("../primitives/EmptyState");
/** Progress — animated bars; outline surface, label + percent per row. */
function ProgressWidget({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    if (!payload.items?.length)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "No progress items" });
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title }), (0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', flexDirection: 'column', gap: 12 }, children: payload.items.map((item) => ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', justifyContent: 'space-between', fontSize: tokens_1.TYPE.label, marginBottom: 5 }, children: [(0, jsx_runtime_1.jsx)("span", { style: { color: theme.text2, fontWeight: 600 }, children: item.label }), (0, jsx_runtime_1.jsxs)("span", { style: { color: theme.text, fontWeight: 700, fontVariantNumeric: 'tabular-nums' }, children: [Math.round(item.percent), "%"] })] }), (0, jsx_runtime_1.jsx)("div", { style: { height: 8, borderRadius: 4, background: theme.dark ? 'rgba(255,255,255,0.07)' : 'rgba(0,0,0,0.06)', overflow: 'hidden' }, children: (0, jsx_runtime_1.jsx)("div", { style: {
                                    width: `${Math.max(0, Math.min(100, item.percent))}%`,
                                    height: '100%',
                                    borderRadius: 4,
                                    background: `linear-gradient(90deg, ${(0, tokens_1.alpha)(accent, 0.75)}, ${accent})`,
                                    transition: 'width .6s cubic-bezier(.22,1,.36,1)',
                                } }) })] }, item.label))) })] }));
}
