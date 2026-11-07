"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.ChecklistWidget = ChecklistWidget;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const TitleRow_1 = require("../primitives/TitleRow");
const EmptyState_1 = require("../primitives/EmptyState");
const UiCheckbox_1 = require("../controls/UiCheckbox");
/** Checklist — tap to check, live progress bar up top. */
function ChecklistWidget({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const [items, setItems] = (0, react_1.useState)(() => (payload.items ?? []).map((i) => ({ ...i })));
    if (!(payload.items ?? []).length)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "No items" });
    const done = items.filter((i) => i.done).length;
    const pct = Math.round((done / items.length) * 100);
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title, right: `${done}/${items.length}` }), (0, jsx_runtime_1.jsx)("div", { style: { height: 6, borderRadius: 3, background: theme.dark ? 'rgba(255,255,255,0.07)' : 'rgba(0,0,0,0.06)', overflow: 'hidden', marginBottom: 12 }, children: (0, jsx_runtime_1.jsx)("div", { style: { width: `${pct}%`, height: '100%', background: accent, borderRadius: 3, transition: 'width .4s ease' } }) }), (0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', flexDirection: 'column', gap: 2 }, children: items.map((item, i) => ((0, jsx_runtime_1.jsxs)("div", { style: { padding: '6px 0', borderBottom: `1px solid ${theme.dark ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.04)'}` }, children: [(0, jsx_runtime_1.jsx)(UiCheckbox_1.UiCheckbox, { label: item.label, checked: item.done === true, onChange: (v) => setItems((s) => s.map((it, ii) => (ii === i ? { ...it, done: v } : it))) }), item.detail ? ((0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.micro, color: theme.text3, marginLeft: 30, marginTop: 2 }, children: item.detail })) : null] }, i))) })] }));
}
