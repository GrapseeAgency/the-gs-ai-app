"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.AccordionWidget = AccordionWidget;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const TitleRow_1 = require("../primitives/TitleRow");
const EmptyState_1 = require("../primitives/EmptyState");
/** Accordion — stacked reveal sections; one open at a time, first optional. */
function AccordionWidget({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const firstOpen = (payload.sections ?? []).findIndex((s) => s.expanded === true);
    const [open, setOpen] = (0, react_1.useState)(firstOpen >= 0 ? firstOpen : null);
    const sections = (payload.sections ?? []).slice(0, 10);
    if (!sections.length)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "No sections" });
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title }), (0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', flexDirection: 'column', gap: 6 }, children: sections.map((section, i) => {
                    const isOpen = open === i;
                    return ((0, jsx_runtime_1.jsxs)("div", { style: { borderRadius: tokens_1.RADIUS.md, border: `1px solid ${isOpen ? (0, tokens_1.alpha)(accent, 0.4) : theme.border}`, overflow: 'hidden' }, children: [(0, jsx_runtime_1.jsxs)("button", { type: "button", "aria-expanded": isOpen, onClick: () => setOpen(isOpen ? null : i), style: {
                                    width: '100%',
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: 10,
                                    padding: '10px 12px',
                                    background: isOpen ? (0, tokens_1.alpha)(accent, theme.dark ? 0.1 : 0.05) : 'transparent',
                                    border: 'none',
                                    cursor: 'pointer',
                                    textAlign: 'left',
                                }, children: [(0, jsx_runtime_1.jsx)("span", { style: { color: accent, fontSize: 12, transition: 'transform .2s ease', transform: isOpen ? 'rotate(90deg)' : 'none' }, children: "\u25B8" }), (0, jsx_runtime_1.jsx)("span", { style: { flex: 1, fontSize: tokens_1.TYPE.body, fontWeight: 600, color: theme.text }, children: section.label })] }), isOpen && ((0, jsx_runtime_1.jsx)("div", { style: { padding: '0 12px 12px 34px', fontSize: tokens_1.TYPE.label, color: theme.text2, lineHeight: 1.6 }, children: section.body }))] }, i));
                }) })] }));
}
