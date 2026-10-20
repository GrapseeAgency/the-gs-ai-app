"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.AppConnected = AppConnected;
const jsx_runtime_1 = require("react/jsx-runtime");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const AppShell_1 = require("./AppShell");
const Chip_1 = require("../primitives/Chip");
const EmptyState_1 = require("../primitives/EmptyState");
/** Connected — the card row of connected services/accounts. */
function AppConnected({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const items = (payload.items ?? []).slice(0, 8);
    if (!items.length)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "No connections" });
    return ((0, jsx_runtime_1.jsx)(AppShell_1.AppShell, { title: payload.title ?? 'Connected', children: (0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', flexDirection: 'column', gap: 8 }, children: items.map((it, i) => ((0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', alignItems: 'center', gap: 10, padding: 12, borderRadius: 12, border: `1px solid ${theme.border}` }, children: [(0, jsx_runtime_1.jsx)("span", { style: {
                            width: 34,
                            height: 34,
                            borderRadius: 10,
                            background: (0, tokens_1.alpha)(accent, 0.14),
                            color: accent,
                            display: 'inline-flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            fontWeight: 700,
                            fontSize: 14,
                            flexShrink: 0,
                        }, children: String(it.title ?? '?').slice(0, 1).toUpperCase() }), (0, jsx_runtime_1.jsxs)("span", { style: { flex: 1, minWidth: 0 }, children: [(0, jsx_runtime_1.jsx)("span", { style: { display: 'block', fontSize: tokens_1.TYPE.label, fontWeight: 600, color: theme.text }, children: String(it.title ?? '') }), (0, jsx_runtime_1.jsx)("span", { style: { display: 'block', fontSize: tokens_1.TYPE.micro, color: theme.text3, marginTop: 1 }, children: String(it.subtitle ?? it.meta ?? '') })] }), (0, jsx_runtime_1.jsx)(Chip_1.Chip, { tone: String(it.badge ?? '').toLowerCase() === 'offline' ? 'danger' : 'success', children: String(it.badge ?? 'Connected') })] }, i))) }) }));
}
