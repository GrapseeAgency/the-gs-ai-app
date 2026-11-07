"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.AppOnboarding = AppOnboarding;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const AppShell_1 = require("./AppShell");
const UiButton_1 = require("../controls/UiButton");
/** Onboarding — swipeable welcome steps ending in a single CTA. */
function AppOnboarding({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const steps = (payload.steps ?? []).slice(0, 8);
    const [i, setI] = (0, react_1.useState)(0);
    if (!steps.length)
        return null;
    const step = steps[Math.min(i, steps.length - 1)];
    const last = i === steps.length - 1;
    return ((0, jsx_runtime_1.jsxs)(AppShell_1.AppShell, { submitLabel: last ? (payload.submitLabel ?? 'Get started') : undefined, children: [(0, jsx_runtime_1.jsxs)("div", { style: {
                    minHeight: 150,
                    borderRadius: tokens_1.RADIUS.xl,
                    background: `linear-gradient(150deg, ${(0, tokens_1.alpha)(accent, theme.dark ? 0.22 : 0.14)}, transparent)`,
                    border: `1px solid ${(0, tokens_1.alpha)(accent, 0.25)}`,
                    padding: 20,
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'center',
                    gap: 8,
                }, children: [(0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.title, fontWeight: 700, color: theme.text }, children: String(step.title ?? '') }), (0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.body, color: theme.text2, lineHeight: 1.6 }, children: String(step.body ?? '') })] }), (0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: 14 }, children: [(0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', gap: 5 }, children: steps.map((_, d) => ((0, jsx_runtime_1.jsx)("span", { style: { width: d === i ? 18 : 6, height: 6, borderRadius: 3, background: d === i ? accent : theme.dark ? 'rgba(255,255,255,0.14)' : 'rgba(0,0,0,0.12)', transition: 'width .25s ease' } }, d))) }), !last ? (0, jsx_runtime_1.jsx)(UiButton_1.UiButton, { buttonStyle: "filled", onClick: () => setI((v) => v + 1), children: "Next" }) : null] })] }));
}
