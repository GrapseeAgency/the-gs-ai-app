"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.StatWidget = StatWidget;
const jsx_runtime_1 = require("react/jsx-runtime");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const TitleRow_1 = require("../primitives/TitleRow");
const EmptyState_1 = require("../primitives/EmptyState");
/** Stat cards — headline numbers with deltas; the hero surface is theirs. */
function StatWidget({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const cards = (payload.cards ?? []).filter((c) => c.label && c.value);
    if (cards.length === 0)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "No metrics" });
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title }), (0, jsx_runtime_1.jsx)("div", { style: { display: 'grid', gridTemplateColumns: `repeat(${Math.min(cards.length, 2)}, 1fr)`, gap: 8 }, children: cards.map((card) => {
                    const up = card.direction === 'up';
                    const down = card.direction === 'down';
                    const deltaColor = up ? '#0CA678' : down ? '#E03131' : theme.text3;
                    return ((0, jsx_runtime_1.jsxs)("div", { style: {
                            padding: '12px 14px',
                            borderRadius: 12,
                            background: (0, tokens_1.alpha)(accent, theme.dark ? 0.10 : 0.06),
                            border: `1px solid ${(0, tokens_1.alpha)(accent, 0.16)}`,
                        }, children: [(0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.label, color: theme.text2, marginBottom: 4 }, children: card.label }), (0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', alignItems: 'baseline', gap: 6 }, children: [(0, jsx_runtime_1.jsx)("span", { style: { fontSize: tokens_1.TYPE.display, fontWeight: 700, color: theme.text, lineHeight: 1.1 }, children: card.value }), card.unit ? (0, jsx_runtime_1.jsx)("span", { style: { fontSize: tokens_1.TYPE.label, color: theme.text2 }, children: card.unit }) : null] }), (card.delta || up || down) && ((0, jsx_runtime_1.jsxs)("div", { style: { fontSize: tokens_1.TYPE.micro, fontWeight: 600, color: deltaColor, marginTop: 4 }, children: [up ? '▲' : down ? '▼' : '■', " ", card.delta] }))] }, card.label));
                }) })] }));
}
