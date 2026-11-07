"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.AppConfig = AppConfig;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const AppShell_1 = require("./AppShell");
const EmptyState_1 = require("../primitives/EmptyState");
const UiToggle_1 = require("../controls/UiToggle");
/** Config — a settings panel of toggles and selects. */
function AppConfig({ payload }) {
    const { theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const groups = (payload.groups ?? []).slice(0, 6);
    const [state, setState] = (0, react_1.useState)(() => Object.fromEntries(groups.flatMap((g) => (g.options ?? []).map((o, oi) => [`${g.title}-${oi}`, oi % 2 === 0]))));
    if (!groups.length)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "No settings" });
    return ((0, jsx_runtime_1.jsx)(AppShell_1.AppShell, { title: payload.title, children: groups.map((g) => ((0, jsx_runtime_1.jsxs)("div", { style: { marginBottom: 14 }, children: [(0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.micro, fontWeight: 700, color: theme.text3, textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 8 }, children: g.title }), (g.options ?? []).map((o, oi) => ((0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 10, padding: '8px 0' }, children: [(0, jsx_runtime_1.jsxs)("span", { children: [(0, jsx_runtime_1.jsx)("span", { style: { display: 'block', fontSize: tokens_1.TYPE.label, color: theme.text }, children: String(o.label ?? '') }), o.detail ? (0, jsx_runtime_1.jsx)("span", { style: { display: 'block', fontSize: tokens_1.TYPE.micro, color: theme.text3, marginTop: 1 }, children: String(o.detail) }) : null] }), (0, jsx_runtime_1.jsx)(UiToggle_1.UiToggle, { checked: state[`${g.title}-${oi}`] === true, onChange: (v) => setState((s) => ({ ...s, [`${g.title}-${oi}`]: v })) })] }, oi)))] }, g.title))) }));
}
