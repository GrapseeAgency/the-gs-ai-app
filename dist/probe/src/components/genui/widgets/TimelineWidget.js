"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.TimelineWidget = TimelineWidget;
const jsx_runtime_1 = require("react/jsx-runtime");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const TitleRow_1 = require("../primitives/TitleRow");
const EmptyState_1 = require("../primitives/EmptyState");
/** Timeline — dated entries down a hairline with accent nodes. */
function TimelineWidget({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    if (!payload.entries?.length)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "No entries" });
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title }), (0, jsx_runtime_1.jsxs)("div", { style: { position: 'relative', paddingLeft: 22 }, children: [(0, jsx_runtime_1.jsx)("span", { "aria-hidden": true, style: { position: 'absolute', left: 5, top: 6, bottom: 6, width: 2, background: (0, tokens_1.alpha)(accent, 0.3), borderRadius: 1 } }), payload.entries.map((entry, i) => ((0, jsx_runtime_1.jsxs)("div", { style: { position: 'relative', paddingBottom: i === payload.entries.length - 1 ? 0 : 14 }, children: [(0, jsx_runtime_1.jsx)("span", { "aria-hidden": true, style: {
                                    position: 'absolute',
                                    left: -22,
                                    top: 4,
                                    width: 12,
                                    height: 12,
                                    borderRadius: 999,
                                    background: i === 0 ? accent : theme.card,
                                    border: `2.5px solid ${accent}`,
                                } }), (0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.micro, fontWeight: 700, color: accent, letterSpacing: '0.02em' }, children: entry.date }), (0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.body, fontWeight: 600, color: theme.text, margin: '2px 0' }, children: entry.title }), entry.detail ? (0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.label, color: theme.text2, lineHeight: 1.5 }, children: entry.detail }) : null] }, i)))] })] }));
}
