"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.TreeWidget = TreeWidget;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const TitleRow_1 = require("../primitives/TitleRow");
const EmptyState_1 = require("../primitives/EmptyState");
/** Tree — collapsible hierarchy (files, org, taxonomy). */
function TreeWidget({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    if (!(payload.roots ?? []).length)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "No tree" });
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title }), (payload.roots ?? []).map((root, i) => ((0, jsx_runtime_1.jsx)(TreeBranch, { node: root, depth: 0 }, i)))] }));
}
function TreeBranch({ node, depth }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const hasKids = (node.children ?? []).length > 0;
    const [open, setOpen] = (0, react_1.useState)(depth < 2);
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', alignItems: 'center', gap: 6, padding: '4px 0', paddingLeft: depth * 16 }, children: [hasKids ? ((0, jsx_runtime_1.jsx)("button", { type: "button", onClick: () => setOpen((o) => !o), "aria-expanded": open, style: { border: 'none', background: 'transparent', cursor: 'pointer', color: accent, fontSize: 11, width: 16, padding: 0, transition: 'transform .15s ease', transform: open ? 'rotate(90deg)' : 'none' }, children: "\u25B8" })) : ((0, jsx_runtime_1.jsx)("span", { style: { width: 16, textAlign: 'center', color: theme.text3, fontSize: 11 }, children: "\u00B7" })), (0, jsx_runtime_1.jsx)("span", { style: { fontSize: tokens_1.TYPE.label, color: theme.text, fontWeight: depth === 0 ? 700 : 400 }, children: node.label }), node.detail ? (0, jsx_runtime_1.jsx)("span", { style: { fontSize: tokens_1.TYPE.micro, color: theme.text3 }, children: node.detail }) : null] }), open && (node.children ?? []).map((child, i) => (0, jsx_runtime_1.jsx)(TreeBranch, { node: child, depth: depth + 1 }, i))] }));
}
