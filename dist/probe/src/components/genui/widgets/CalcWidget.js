"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.CalcWidget = CalcWidget;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const TitleRow_1 = require("../primitives/TitleRow");
const EmptyState_1 = require("../primitives/EmptyState");
const evaluate_1 = require("../calc/evaluate");
const UiSlider_1 = require("../controls/UiSlider");
const UiSegmented_1 = require("../controls/UiSegmented");
/** Calc — live parameter-driven calculator; expr outputs recompute on input. */
function CalcWidget({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const fields = (payload.fields ?? []).filter((f) => f.key && f.label);
    const outputs = (payload.outputs ?? []).filter((o) => o.label && o.expr);
    const [values, setValues] = (0, react_1.useState)(() => Object.fromEntries(fields.map((f) => [f.key, f.choices?.length ? f.choices[0].value : f.value ?? f.min ?? 0])));
    if (!fields.length || !outputs.length)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "Calculator misconfigured" });
    const set = (key, v) => setValues((s) => ({ ...s, [key]: v }));
    const results = (0, react_1.useMemo)(() => {
        const out = [];
        for (const o of outputs) {
            try {
                out.push({ label: o.label, text: (0, evaluate_1.formatOutput)((0, evaluate_1.evaluateExpr)(o.expr, values), o.format, o.unit), primary: o.primary === true });
            }
            catch {
                out.push({ label: o.label, text: '—', primary: o.primary === true, error: true });
            }
        }
        return out;
    }, [outputs, values]);
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title }), (payload.presets ?? []).length > 0 && ((0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 12 }, children: (payload.presets ?? []).map((preset) => ((0, jsx_runtime_1.jsx)("button", { type: "button", onClick: () => setValues((s) => ({ ...s, ...preset.values })), style: {
                        padding: '5px 12px',
                        borderRadius: 999,
                        border: `1px solid ${theme.border}`,
                        background: 'transparent',
                        color: theme.text2,
                        fontSize: tokens_1.TYPE.micro,
                        fontWeight: 600,
                        cursor: 'pointer',
                    }, children: preset.label }, preset.label))) })), (0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', flexDirection: 'column', gap: 6 }, children: fields.map((f) => f.choices?.length ? ((0, jsx_runtime_1.jsxs)("div", { style: { padding: '4px 0' }, children: [(0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.label, color: theme.text2, fontWeight: 600, marginBottom: 6 }, children: f.label }), (0, jsx_runtime_1.jsx)(UiSegmented_1.UiSegmented, { options: f.choices.map((c) => ({ label: c.label, value: String(c.value) })), value: String(values[f.key]), onChange: (v) => set(f.key, Number(v)) })] }, f.key)) : ((0, jsx_runtime_1.jsx)(UiSlider_1.UiSlider, { label: f.label, value: values[f.key] ?? 0, min: f.min ?? 0, max: f.max ?? 100, step: f.step ?? Math.max(1, ((f.max ?? 100) - (f.min ?? 0)) / 100), unit: f.unit, onChange: (v) => set(f.key, v) }, f.key))) }), (0, jsx_runtime_1.jsx)("div", { style: { marginTop: 14, display: 'flex', flexDirection: 'column', gap: 8 }, children: results.map((r, i) => ((0, jsx_runtime_1.jsxs)("div", { style: {
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'baseline',
                        padding: r.primary ? '12px 14px' : '8px 2px',
                        borderRadius: r.primary ? tokens_1.RADIUS.lg : 0,
                        background: r.primary ? (0, tokens_1.alpha)(accent, theme.dark ? 0.14 : 0.08) : 'transparent',
                        border: r.primary ? `1px solid ${(0, tokens_1.alpha)(accent, 0.3)}` : 'none',
                    }, children: [(0, jsx_runtime_1.jsx)("span", { style: { fontSize: r.primary ? tokens_1.TYPE.label : tokens_1.TYPE.micro, color: theme.text2, fontWeight: 600 }, children: r.label }), (0, jsx_runtime_1.jsx)("span", { style: { fontSize: r.primary ? tokens_1.TYPE.title : tokens_1.TYPE.body, fontWeight: 700, color: r.error ? '#E03131' : r.primary ? accent : theme.text, fontVariantNumeric: 'tabular-nums' }, children: r.text })] }, i))) }), payload.note ? (0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.micro, color: theme.text3, marginTop: 10, lineHeight: 1.5 }, children: payload.note }) : null] }));
}
