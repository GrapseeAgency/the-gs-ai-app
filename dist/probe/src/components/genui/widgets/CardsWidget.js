"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.CardsWidget = CardsWidget;
const jsx_runtime_1 = require("react/jsx-runtime");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const TitleRow_1 = require("../primitives/TitleRow");
const EmptyState_1 = require("../primitives/EmptyState");
/** Cards — tappable prompt cards; a tap sends the card's prompt back. */
function CardsWidget({ payload, onPrompt }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const cards = (payload.cards ?? []).slice(0, 8);
    if (!cards.length)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "No cards" });
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title }), (0, jsx_runtime_1.jsx)("div", { style: { display: 'grid', gridTemplateColumns: cards.length > 2 ? '1fr 1fr' : '1fr', gap: 8 }, children: cards.map((card, i) => ((0, jsx_runtime_1.jsxs)("button", { type: "button", onClick: () => card.prompt && onPrompt?.(card.prompt), style: {
                        textAlign: 'left',
                        padding: '12px 14px',
                        borderRadius: tokens_1.RADIUS.lg,
                        border: `1px solid ${theme.border}`,
                        background: (0, tokens_1.alpha)(accent, theme.dark ? 0.07 : 0.04),
                        cursor: card.prompt ? 'pointer' : 'default',
                    }, children: [(0, jsx_runtime_1.jsx)("span", { style: { display: 'block', fontSize: tokens_1.TYPE.body, fontWeight: 700, color: theme.text }, children: card.title }), card.subtitle ? ((0, jsx_runtime_1.jsx)("span", { style: { display: 'block', fontSize: tokens_1.TYPE.micro, color: theme.text2, marginTop: 3, lineHeight: 1.5 }, children: card.subtitle })) : null, card.prompt ? (0, jsx_runtime_1.jsx)("span", { style: { display: 'block', fontSize: tokens_1.TYPE.micro, color: accent, fontWeight: 600, marginTop: 8 }, children: "Tap to ask \u2192" }) : null] }, i))) })] }));
}
