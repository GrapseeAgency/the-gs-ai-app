"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.CompareWidget = CompareWidget;
const jsx_runtime_1 = require("react/jsx-runtime");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const TitleRow_1 = require("../primitives/TitleRow");
const EmptyState_1 = require("../primitives/EmptyState");
/** Compare — two columns side by side, centre rule, accent headers. */
function CompareWidget({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    if (!payload.left?.items?.length && !payload.right?.items?.length)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "Nothing to compare" });
    const side = (title, items, filled) => ((0, jsx_runtime_1.jsxs)("div", { style: { flex: 1, minWidth: 0 }, children: [(0, jsx_runtime_1.jsx)("div", { style: {
                    padding: '7px 10px',
                    borderRadius: 10,
                    textAlign: 'center',
                    fontSize: tokens_1.TYPE.label,
                    fontWeight: 700,
                    marginBottom: 8,
                    background: filled ? accent : (0, tokens_1.alpha)(theme.dark ? '#FFFFFF' : '#14171C', 0.05),
                    color: filled ? theme.dark ? '#0F1216' : '#fff' : theme.text2,
                }, children: title }), items.map((item, i) => ((0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.body, color: theme.text, padding: '7px 2px', borderBottom: `1px solid ${(0, tokens_1.alpha)(theme.dark ? '#FFFFFF' : '#14171C', 0.06)}` }, children: item }, i)))] }));
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title }), (0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', gap: 12 }, children: [side(payload.left?.title ?? 'Left', payload.left?.items ?? [], false), (0, jsx_runtime_1.jsx)("div", { style: { width: 1, background: theme.border, alignSelf: 'stretch' } }), side(payload.right?.title ?? 'Right', payload.right?.items ?? [], true)] })] }));
}
