"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.AppConfigurator = AppConfigurator;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const AppShell_1 = require("./AppShell");
const UiRadio_1 = require("../controls/UiRadio");
const UiSlider_1 = require("../controls/UiSlider");
const EmptyState_1 = require("../primitives/EmptyState");
/** Configurator — priced option groups with a live total. */
function AppConfigurator({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const groups = (payload.groups ?? []).slice(0, 5);
    const [picks, setPicks] = (0, react_1.useState)(() => Object.fromEntries(groups.map((g) => [g.title, 0])));
    if (!groups.length)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "No options" });
    const total = (0, react_1.useMemo)(() => groups.reduce((sum, g) => sum + (Number((g.options ?? [])[picks[g.title] ?? 0]?.price) || 0), 0), [groups, picks]);
    return ((0, jsx_runtime_1.jsxs)(AppShell_1.AppShell, { title: payload.title, children: [groups.map((g) => ((0, jsx_runtime_1.jsxs)("div", { style: { marginBottom: 14 }, children: [(0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.micro, fontWeight: 700, color: theme.text3, textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 8 }, children: g.title }), (g.options ?? []).map((o, oi) => ((0, jsx_runtime_1.jsx)(UiRadio_1.UiRadio, { label: `${o.label}${o.detail ? ` — ${o.detail}` : ''}${o.price ? ` (+${(0, UiSlider_1.formatNumber)(Number(o.price))})` : ''}`, selected: picks[g.title] === oi, onSelect: () => setPicks((p) => ({ ...p, [g.title]: oi })) }, oi)))] }, g.title))), (0, jsx_runtime_1.jsxs)("div", { style: {
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'baseline',
                    padding: '12px 14px',
                    borderRadius: 14,
                    background: (0, tokens_1.alpha)(accent, theme.dark ? 0.13 : 0.08),
                    border: `1px solid ${(0, tokens_1.alpha)(accent, 0.3)}`,
                }, children: [(0, jsx_runtime_1.jsx)("span", { style: { fontSize: tokens_1.TYPE.label, fontWeight: 600, color: theme.text2 }, children: "Total" }), (0, jsx_runtime_1.jsxs)("span", { style: { fontSize: tokens_1.TYPE.title, fontWeight: 700, color: accent }, children: ["$", (0, UiSlider_1.formatNumber)(total)] })] })] }));
}
