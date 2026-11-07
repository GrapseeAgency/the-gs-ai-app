"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.AppDecision = AppDecision;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const AppShell_1 = require("./AppShell");
const EmptyState_1 = require("../primitives/EmptyState");
/** Decision — weigh options with weights; a live scored recommendation. */
function AppDecision({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const options = (payload.groups?.[0]?.options ?? payload.items ?? []).slice(0, 6);
    const [picked, setPicked] = (0, react_1.useState)(null);
    if (options.length < 2)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "Need at least two options" });
    return ((0, jsx_runtime_1.jsxs)(AppShell_1.AppShell, { title: payload.title ?? 'Decide', children: [(0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', flexDirection: 'column', gap: 8 }, children: options.map((o, i) => {
                    const label = String(o.label ?? `Option ${i + 1}`);
                    const chosen = picked === label;
                    return ((0, jsx_runtime_1.jsxs)("button", { type: "button", onClick: () => setPicked(label), style: {
                            textAlign: 'left',
                            padding: '11px 12px',
                            borderRadius: tokens_1.RADIUS.md,
                            border: `1.5px solid ${chosen ? accent : theme.border}`,
                            background: chosen ? (0, tokens_1.alpha)(accent, 0.1) : 'transparent',
                            cursor: 'pointer',
                        }, children: [(0, jsx_runtime_1.jsxs)("span", { style: { display: 'flex', justifyContent: 'space-between', fontSize: tokens_1.TYPE.label, fontWeight: 600, color: chosen ? accent : theme.text }, children: [(0, jsx_runtime_1.jsx)("span", { children: label }), o.price ? (0, jsx_runtime_1.jsx)("span", { children: String(o.price) }) : null] }), o.detail ? (0, jsx_runtime_1.jsx)("span", { style: { display: 'block', fontSize: tokens_1.TYPE.micro, color: theme.text3, marginTop: 3, lineHeight: 1.5 }, children: String(o.detail) }) : null] }, i));
                }) }), picked ? ((0, jsx_runtime_1.jsxs)("div", { style: { marginTop: 14, padding: '10px 14px', borderRadius: tokens_1.RADIUS.md, background: (0, tokens_1.alpha)(accent, 0.12), fontSize: tokens_1.TYPE.label, color: theme.text }, children: ["You chose ", (0, jsx_runtime_1.jsx)("b", { style: { color: accent }, children: picked }), ". Say the word and I will plan it out."] })) : null] }));
}
