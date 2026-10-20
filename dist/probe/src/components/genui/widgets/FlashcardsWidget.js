"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.FlashcardsWidget = FlashcardsWidget;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const TitleRow_1 = require("../primitives/TitleRow");
const EmptyState_1 = require("../primitives/EmptyState");
const UiButton_1 = require("../controls/UiButton");
/** Flashcards — tap to flip, prev/next, deterministic order. */
function FlashcardsWidget({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const [index, setIndex] = (0, react_1.useState)(0);
    const [flipped, setFlipped] = (0, react_1.useState)(false);
    const cards = (payload.cards ?? []).slice(0, 20);
    if (!cards.length)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "No cards" });
    const card = cards[Math.min(index, cards.length - 1)];
    const go = (delta) => {
        setFlipped(false);
        setIndex((i) => (i + delta + cards.length) % cards.length);
    };
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title, right: `${index + 1}/${cards.length}` }), (0, jsx_runtime_1.jsxs)("button", { type: "button", onClick: () => setFlipped((f) => !f), style: {
                    width: '100%',
                    minHeight: 130,
                    borderRadius: tokens_1.RADIUS.xl,
                    border: `1.5px solid ${(0, tokens_1.alpha)(accent, 0.4)}`,
                    background: flipped ? (0, tokens_1.alpha)(accent, theme.dark ? 0.14 : 0.07) : theme.dark ? 'rgba(0,0,0,0.25)' : '#fff',
                    cursor: 'pointer',
                    padding: 18,
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: 8,
                }, children: [(0, jsx_runtime_1.jsx)("span", { style: { fontSize: tokens_1.TYPE.micro, fontWeight: 700, letterSpacing: '0.08em', color: accent, textTransform: 'uppercase' }, children: flipped ? 'Answer' : 'Question' }), (0, jsx_runtime_1.jsx)("span", { style: { fontSize: flipped ? tokens_1.TYPE.body : tokens_1.TYPE.title, fontWeight: flipped ? 400 : 600, color: theme.text, lineHeight: 1.45, textAlign: 'center' }, children: flipped ? card.back : card.front }), (0, jsx_runtime_1.jsx)("span", { style: { fontSize: tokens_1.TYPE.micro, color: theme.text3 }, children: "tap to flip" })] }), (0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', justifyContent: 'center', gap: 8, marginTop: 10 }, children: [(0, jsx_runtime_1.jsx)(UiButton_1.UiButton, { onClick: () => go(-1), children: "\u2190" }), (0, jsx_runtime_1.jsx)(UiButton_1.UiButton, { onClick: () => go(1), children: "\u2192" })] })] }));
}
