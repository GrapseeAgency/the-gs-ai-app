"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.AppFilter = AppFilter;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const AppShell_1 = require("./AppShell");
const EmptyState_1 = require("../primitives/EmptyState");
const UiChipToggle_1 = require("../controls/UiChipToggle");
/** Filter — facet chips narrow a list live. */
function AppFilter({ payload }) {
    const { theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const items = (payload.items ?? []).slice(0, 40);
    const facets = [...new Set(items.map((it) => String(it.badge ?? '')).filter(Boolean))];
    const [active, setActive] = (0, react_1.useState)(new Set());
    const hits = (0, react_1.useMemo)(() => (active.size === 0 ? items : items.filter((it) => active.has(String(it.badge ?? '')))), [items, active]);
    if (!items.length)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "No items" });
    return ((0, jsx_runtime_1.jsxs)(AppShell_1.AppShell, { title: payload.title, right: `${hits.length}/${items.length}`, children: [(0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 10 }, children: facets.map((f) => ((0, jsx_runtime_1.jsx)(UiChipToggle_1.UiChipToggle, { label: f, selected: active.has(f), onToggle: () => setActive((s) => {
                        const next = new Set(s);
                        if (next.has(f))
                            next.delete(f);
                        else
                            next.add(f);
                        return next;
                    }) }, f))) }), (0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', flexDirection: 'column', gap: 5, maxHeight: 240, overflowY: 'auto' }, children: hits.map((it, i) => ((0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', justifyContent: 'space-between', gap: 8, padding: '7px 10px', borderRadius: 9 }, children: [(0, jsx_runtime_1.jsx)("span", { style: { fontSize: tokens_1.TYPE.label, color: theme.text }, children: String(it.title ?? '') }), (0, jsx_runtime_1.jsx)("span", { style: { fontSize: tokens_1.TYPE.micro, color: theme.text3 }, children: String(it.meta ?? it.subtitle ?? '') })] }, i))) })] }));
}
