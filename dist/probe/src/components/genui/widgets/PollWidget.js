"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.PollWidget = PollWidget;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const TitleRow_1 = require("../primitives/TitleRow");
const EmptyState_1 = require("../primitives/EmptyState");
/** Poll — vote once, bars animate to the new tally. */
function PollWidget({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const [voted, setVoted] = (0, react_1.useState)(null);
    const [counts, setCounts] = (0, react_1.useState)(() => (payload.options ?? []).map((o) => o.votes ?? 0));
    if ((payload.options ?? []).length < 2)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "Poll needs options" });
    const vote = (i) => {
        if (voted !== null)
            return;
        setVoted(i);
        setCounts((c) => c.map((v, ci) => (ci === i ? v + 1 : v)));
    };
    const total = counts.reduce((a, b) => a + b, 0) || 1;
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title, right: `${total} vote${total === 1 ? '' : 's'}` }), payload.question ? ((0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.body, fontWeight: 600, color: theme.text, marginBottom: 10 }, children: payload.question })) : null, (0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', flexDirection: 'column', gap: 8 }, children: (payload.options ?? []).map((opt, i) => {
                    const pct = Math.round((counts[i] / total) * 100);
                    const mine = voted === i;
                    return ((0, jsx_runtime_1.jsxs)("button", { type: "button", onClick: () => vote(i), style: {
                            position: 'relative',
                            overflow: 'hidden',
                            textAlign: 'left',
                            padding: '10px 12px',
                            borderRadius: tokens_1.RADIUS.md,
                            border: `1.5px solid ${mine ? accent : theme.border}`,
                            cursor: voted === null ? 'pointer' : 'default',
                            background: 'transparent',
                        }, children: [voted !== null && ((0, jsx_runtime_1.jsx)("span", { "aria-hidden": true, style: {
                                    position: 'absolute',
                                    inset: 0,
                                    width: `${pct}%`,
                                    background: (0, tokens_1.alpha)(accent, mine ? 0.28 : 0.14),
                                    transition: 'width .6s cubic-bezier(.22,1,.36,1)',
                                } })), (0, jsx_runtime_1.jsxs)("span", { style: { position: 'relative', display: 'flex', justifyContent: 'space-between', fontSize: tokens_1.TYPE.label, color: theme.text }, children: [(0, jsx_runtime_1.jsxs)("span", { style: { fontWeight: mine ? 700 : 400 }, children: [mine ? '◉' : voted !== null ? '○' : '', " ", opt.label] }), voted !== null && (0, jsx_runtime_1.jsxs)("span", { style: { fontWeight: 700, fontVariantNumeric: 'tabular-nums' }, children: [pct, "%"] })] })] }, i));
                }) })] }));
}
