"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.RatingWidget = RatingWidget;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const UiRatingInput_1 = require("../controls/UiRatingInput");
const UiButton_1 = require("../controls/UiButton");
/** Rating — star/heart/thumb input with a confirm; nps uses a 0-10 row. */
function RatingWidget({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const [value, setValue] = (0, react_1.useState)(0);
    const [sent, setSent] = (0, react_1.useState)(false);
    const nps = payload.style === 'nps';
    const max = payload.max ?? (nps ? 11 : 5);
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.heading, fontWeight: 600, color: theme.text }, children: payload.title }), payload.prompt ? ((0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.label, color: theme.text2, margin: '4px 0 12px' }, children: payload.prompt })) : ((0, jsx_runtime_1.jsx)("div", { style: { height: 8 } })), nps ? ((0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', gap: 4 }, children: Array.from({ length: max }, (_, i) => i).map((i) => ((0, jsx_runtime_1.jsx)("button", { type: "button", onClick: () => setValue(i), style: {
                        flex: 1,
                        height: 36,
                        borderRadius: 8,
                        border: `1.5px solid ${value === i ? accent : theme.border}`,
                        background: value === i ? accent : i < value ? (0, tokens_1.alpha)(accent, 0.2) : 'transparent',
                        color: value === i ? theme.dark ? '#0F1216' : '#fff' : theme.text2,
                        fontSize: tokens_1.TYPE.label,
                        fontWeight: 700,
                        cursor: 'pointer',
                    }, children: i }, i))) })) : ((0, jsx_runtime_1.jsx)(UiRatingInput_1.UiRatingInput, { max: Math.min(max, 10), style: payload.style ?? 'star', value: value, onChange: setValue })), (payload.labels ?? []).length === 2 && ((0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', justifyContent: 'space-between', fontSize: tokens_1.TYPE.micro, color: theme.text3, marginTop: 6 }, children: [(0, jsx_runtime_1.jsx)("span", { children: payload.labels?.[0] }), (0, jsx_runtime_1.jsx)("span", { children: payload.labels?.[1] })] })), (0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', alignItems: 'center', gap: 10, marginTop: 14 }, children: (0, jsx_runtime_1.jsx)(UiButton_1.UiButton, { buttonStyle: "filled", disabled: value === 0 || sent, onClick: () => setSent(true), children: sent ? 'Thanks ✓' : 'Send' }) })] }));
}
