"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.TabsWidget = TabsWidget;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const TitleRow_1 = require("../primitives/TitleRow");
const EmptyState_1 = require("../primitives/EmptyState");
/** Tabs — 2-4 parallel facets of one topic. */
function TabsWidget({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const [active, setActive] = (0, react_1.useState)(0);
    const tabs = (payload.tabs ?? []).slice(0, 8);
    if (!tabs.length)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "No tabs" });
    const current = tabs[Math.min(active, tabs.length - 1)];
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title }), (0, jsx_runtime_1.jsx)("div", { role: "tablist", style: { display: 'flex', gap: 2, padding: 3, borderRadius: tokens_1.RADIUS.pill, background: theme.dark ? 'rgba(0,0,0,0.3)' : 'rgba(0,0,0,0.05)', width: 'fit-content', maxWidth: '100%', overflowX: 'auto' }, children: tabs.map((t, i) => ((0, jsx_runtime_1.jsx)("button", { role: "tab", "aria-selected": i === active, type: "button", onClick: () => setActive(i), style: {
                        border: 'none',
                        padding: '6px 14px',
                        borderRadius: tokens_1.RADIUS.pill,
                        fontSize: tokens_1.TYPE.label,
                        fontWeight: 600,
                        whiteSpace: 'nowrap',
                        cursor: 'pointer',
                        background: i === active ? accent : 'transparent',
                        color: i === active ? (0, tokens_1.inkOn)(accent) : theme.text2,
                    }, children: t.label }, i))) }), (0, jsx_runtime_1.jsxs)("div", { style: { marginTop: 12, fontSize: tokens_1.TYPE.body, color: theme.text, lineHeight: 1.6 }, children: [current?.body, current?.items?.length ? ((0, jsx_runtime_1.jsx)("ul", { style: { margin: '8px 0 0', paddingLeft: 18 }, children: current.items.map((item, i) => ((0, jsx_runtime_1.jsx)("li", { style: { marginBottom: 4, color: theme.text2, fontSize: tokens_1.TYPE.label }, children: item }, i))) })) : null] })] }));
}
