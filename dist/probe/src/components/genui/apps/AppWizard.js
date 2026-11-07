"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.AppWizard = AppWizard;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const AppShell_1 = require("./AppShell");
const UiButton_1 = require("../controls/UiButton");
const UiTextInput_1 = require("../controls/UiTextInput");
const UiTextArea_1 = require("../controls/UiTextArea");
const UiSelect_1 = require("../controls/UiSelect");
const UiRadio_1 = require("../controls/UiRadio");
const UiSlider_1 = require("../controls/UiSlider");
/** Wizard — multi-step flow with per-step fields and a progress rail. */
function AppWizard({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const steps = (payload.steps ?? []).slice(0, 12);
    const [step, setStep] = (0, react_1.useState)(0);
    const [answers, setAnswers] = (0, react_1.useState)({});
    if (!steps.length)
        return null;
    const current = steps[Math.min(step, steps.length - 1)];
    const last = step === steps.length - 1;
    const field = (sf, i) => {
        const type = String(sf.type ?? 'text');
        const label = String(sf.label ?? `Field ${i + 1}`);
        const key = `${step}-${i}`;
        const value = answers[key] ?? '';
        const set = (v) => setAnswers((a) => ({ ...a, [key]: v }));
        if (type === 'textarea')
            return (0, jsx_runtime_1.jsx)(UiTextArea_1.UiTextArea, { placeholder: label, value: value, onChange: set }, key);
        if (type === 'select')
            return (0, jsx_runtime_1.jsx)(UiSelect_1.UiSelect, { ariaLabel: label, value: value || String(sf.options?.[0] ?? ''), options: sf.options ?? [], onChange: set }, key);
        if (type === 'radio')
            return ((0, jsx_runtime_1.jsx)("div", { children: (sf.options ?? []).map((o) => ((0, jsx_runtime_1.jsx)(UiRadio_1.UiRadio, { label: o, selected: value === o, onSelect: () => set(o) }, o))) }, key));
        if (type === 'slider')
            return (0, jsx_runtime_1.jsx)(UiSlider_1.UiSlider, { label: label, value: Number(value) || 50, onChange: (v) => set(String(v)) }, key);
        return (0, jsx_runtime_1.jsx)(UiTextInput_1.UiTextInput, { placeholder: label, value: value, onChange: set }, key);
    };
    return ((0, jsx_runtime_1.jsxs)(AppShell_1.AppShell, { title: payload.title, right: `Step ${step + 1} of ${steps.length}`, submitLabel: last ? (payload.submitLabel ?? 'Finish') : undefined, children: [(0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', gap: 4, marginBottom: 14 }, children: steps.map((_, i) => ((0, jsx_runtime_1.jsx)("span", { style: { flex: 1, height: 4, borderRadius: 2, background: i <= step ? accent : theme.dark ? 'rgba(255,255,255,0.09)' : 'rgba(0,0,0,0.08)', transition: 'background .3s ease' } }, i))) }), (0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.body, fontWeight: 700, color: theme.text, marginBottom: 4 }, children: String(current.title ?? `Step ${step + 1}`) }), current.body ? ((0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.label, color: theme.text2, lineHeight: 1.55, marginBottom: 12 }, children: String(current.body) })) : null, (0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', flexDirection: 'column', gap: 12 }, children: (current.fields ?? []).map(field) }), (0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', justifyContent: 'space-between', marginTop: 16 }, children: [(0, jsx_runtime_1.jsx)(UiButton_1.UiButton, { disabled: step === 0, onClick: () => setStep((s) => Math.max(0, s - 1)), children: "Back" }), !last && ((0, jsx_runtime_1.jsx)(UiButton_1.UiButton, { buttonStyle: "filled", onClick: () => setStep((s) => Math.min(steps.length - 1, s + 1)), children: "Continue" }))] }), last && Object.keys(answers).length > 0 && ((0, jsx_runtime_1.jsxs)("div", { style: { marginTop: 12, fontSize: tokens_1.TYPE.micro, color: theme.text3 }, children: [Object.entries(answers).length, " answer(s) captured"] }))] }));
}
