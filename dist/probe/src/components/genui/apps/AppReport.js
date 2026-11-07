"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.AppReport = AppReport;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const AppShell_1 = require("./AppShell");
const EmptyState_1 = require("../primitives/EmptyState");
/** Report — a sectioned document with a per-section open state. */
function AppReport({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const groups = (payload.groups ?? []).slice(0, 8);
    const [open, setOpen] = (0, react_1.useState)(0);
    if (!groups.length)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "No report sections" });
    return ((0, jsx_runtime_1.jsxs)(AppShell_1.AppShell, { title: payload.title, children: [(0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 12 }, children: groups.map((g, i) => ((0, jsx_runtime_1.jsx)("button", { type: "button", onClick: () => setOpen(i), style: {
                        padding: '6px 12px',
                        borderRadius: 999,
                        border: `1px solid ${open === i ? accent : 'transparent'}`,
                        background: open === i ? `${accent}1a` : 'transparent',
                        color: open === i ? accent : theme.text2,
                        fontSize: tokens_1.TYPE.micro,
                        fontWeight: 700,
                        cursor: 'pointer',
                    }, children: g.title }, g.title))) }), (0, jsx_runtime_1.jsx)("div", { style: { padding: 14, borderRadius: tokens_1.RADIUS.lg, border: `1px solid ${theme.border}` }, children: (groups[open]?.options ?? []).map((o, i) => ((0, jsx_runtime_1.jsxs)("div", { style: { marginBottom: i === (groups[open]?.options ?? []).length - 1 ? 0 : 10 }, children: [(0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.label, fontWeight: 700, color: theme.text }, children: String(o.label ?? '') }), o.detail ? (0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.label, color: theme.text2, lineHeight: 1.6, marginTop: 3 }, children: String(o.detail) }) : null] }, i))) })] }));
}
