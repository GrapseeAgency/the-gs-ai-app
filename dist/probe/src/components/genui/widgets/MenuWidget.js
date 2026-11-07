"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.MenuWidget = MenuWidget;
const jsx_runtime_1 = require("react/jsx-runtime");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const TitleRow_1 = require("../primitives/TitleRow");
const EmptyState_1 = require("../primitives/EmptyState");
/** Menu — a tappable list of paths forward. */
function MenuWidget({ payload, onPrompt }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    if (!(payload.options ?? []).length)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "No options" });
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title }), (0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', flexDirection: 'column', gap: 6 }, children: (payload.options ?? []).map((opt, i) => ((0, jsx_runtime_1.jsxs)("button", { type: "button", onClick: () => opt.prompt && onPrompt?.(opt.prompt), style: {
                        display: 'flex',
                        alignItems: 'center',
                        gap: 10,
                        width: '100%',
                        textAlign: 'left',
                        padding: '11px 12px',
                        borderRadius: 12,
                        border: `1px solid ${theme.border}`,
                        background: 'transparent',
                        cursor: 'pointer',
                        transition: 'border-color .15s ease, background .15s ease',
                    }, onMouseEnter: (e) => {
                        e.currentTarget.style.borderColor = (0, tokens_1.alpha)(accent, 0.5);
                        e.currentTarget.style.background = (0, tokens_1.alpha)(accent, 0.05);
                    }, onMouseLeave: (e) => {
                        e.currentTarget.style.borderColor = theme.border;
                        e.currentTarget.style.background = 'transparent';
                    }, children: [(0, jsx_runtime_1.jsxs)("span", { style: { flex: 1, minWidth: 0 }, children: [(0, jsx_runtime_1.jsx)("span", { style: { display: 'block', fontSize: tokens_1.TYPE.body, fontWeight: 600, color: theme.text }, children: opt.label }), opt.detail ? ((0, jsx_runtime_1.jsx)("span", { style: { display: 'block', fontSize: tokens_1.TYPE.micro, color: theme.text3, marginTop: 2 }, children: opt.detail })) : null] }), (0, jsx_runtime_1.jsx)("span", { style: { color: accent, fontSize: 16, flexShrink: 0 }, children: "\u2192" })] }, i))) })] }));
}
