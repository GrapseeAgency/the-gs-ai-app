"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.AppTasks = AppTasks;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const AppShell_1 = require("./AppShell");
const EmptyState_1 = require("../primitives/EmptyState");
const UiCheckbox_1 = require("../controls/UiCheckbox");
/** Tasks — a personal task list with done state. */
function AppTasks({ payload }) {
    const { theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const [items, setItems] = (0, react_1.useState)(() => (payload.items ?? []).map((it) => ({ title: String(it.title ?? ''), meta: String(it.meta ?? ''), done: false })));
    if (!items.length)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "No tasks" });
    const left = items.filter((i) => !i.done).length;
    return ((0, jsx_runtime_1.jsx)(AppShell_1.AppShell, { title: payload.title, right: `${left} left`, children: (0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', flexDirection: 'column', gap: 2 }, children: items.map((item, i) => ((0, jsx_runtime_1.jsxs)("div", { style: { padding: '7px 0', borderBottom: `1px solid ${theme.dark ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.04)'}` }, children: [(0, jsx_runtime_1.jsx)(UiCheckbox_1.UiCheckbox, { label: item.title, checked: item.done, onChange: (v) => setItems((s) => s.map((it, ii) => (ii === i ? { ...it, done: v } : it))) }), item.meta ? (0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.micro, color: theme.text3, marginLeft: 30, marginTop: 2 }, children: item.meta }) : null] }, i))) }) }));
}
