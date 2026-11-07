"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.AppScenario = AppScenario;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const AppShell_1 = require("./AppShell");
const UiSlider_1 = require("../controls/UiSlider");
const EmptyState_1 = require("../primitives/EmptyState");
/** Scenario — what-if sliders over a base number. */
function AppScenario({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const sliders = (payload.groups ?? []).slice(0, 4);
    const [values, setValues] = (0, react_1.useState)(() => Object.fromEntries(sliders.map((s) => [s.title, 100])));
    if (!sliders.length)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "No scenario knobs" });
    const base = Number((payload.items ?? [])[0]?.price ?? 1000);
    const total = sliders.reduce((acc, s) => acc * ((values[s.title] ?? 100) / 100), base);
    return ((0, jsx_runtime_1.jsxs)(AppShell_1.AppShell, { title: payload.title, children: [(0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', flexDirection: 'column', gap: 4 }, children: sliders.map((s) => ((0, jsx_runtime_1.jsx)(UiSlider_1.UiSlider, { label: s.title, value: values[s.title] ?? 100, min: Number((s.options ?? [])[0]?.price ?? 0) || 0, max: Number((s.options ?? [])[1]?.price ?? 200) || 200, step: 5, unit: "%", onChange: (v) => setValues((vs) => ({ ...vs, [s.title]: v })) }, s.title))) }), (0, jsx_runtime_1.jsxs)("div", { style: { marginTop: 14, display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }, children: [(0, jsx_runtime_1.jsx)("span", { style: { fontSize: tokens_1.TYPE.label, color: theme.text2 }, children: "Projected" }), (0, jsx_runtime_1.jsx)("span", { style: { fontSize: tokens_1.TYPE.title, fontWeight: 700, color: accent }, children: Math.round(total).toLocaleString() })] })] }));
}
