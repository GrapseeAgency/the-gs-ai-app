"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.FormWidget = FormWidget;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const TitleRow_1 = require("../primitives/TitleRow");
const EmptyState_1 = require("../primitives/EmptyState");
const UiButton_1 = require("../controls/UiButton");
const UiCheckbox_1 = require("../controls/UiCheckbox");
const UiChipToggle_1 = require("../controls/UiChipToggle");
const UiDateField_1 = require("../controls/UiDateField");
const UiFileField_1 = require("../controls/UiFileField");
const UiOtpField_1 = require("../controls/UiOtpField");
const UiRadio_1 = require("../controls/UiRadio");
const UiRatingInput_1 = require("../controls/UiRatingInput");
const UiSelect_1 = require("../controls/UiSelect");
const UiSlider_1 = require("../controls/UiSlider");
const UiStepper_1 = require("../controls/UiStepper");
const UiTextArea_1 = require("../controls/UiTextArea");
const UiTextInput_1 = require("../controls/UiTextInput");
const UiToggle_1 = require("../controls/UiToggle");
/**
 * Form — every catalogue field type rendered live and held in local state.
 * The submit surfaces a composed summary (and, when wired, sends it back).
 */
function FormWidget({ payload, onPrompt }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const [values, setValues] = (0, react_1.useState)(() => Object.fromEntries((payload.fields ?? []).map((f, i) => [`f${i}`, f.type === 'checkbox' || f.type === 'toggle' ? false : f.type === 'slider' || f.type === 'number' ? f.value ?? f.min ?? 0 : f.type === 'rating' ? 0 : ''])));
    const [submitted, setSubmitted] = (0, react_1.useState)(false);
    const fields = (payload.fields ?? []).slice(0, 12);
    if (!fields.length)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "No fields" });
    const set = (key) => (v) => setValues((s) => ({ ...s, [key]: v }));
    return ((0, jsx_runtime_1.jsxs)("form", { onSubmit: (e) => {
            e.preventDefault();
            setSubmitted(true);
            if (onPrompt) {
                const summary = fields
                    .map((f, i) => `${f.label}: ${String(values[`f${i}`])}`)
                    .join(', ');
                onPrompt(`${payload.title ?? 'Form'} — ${summary}`);
            }
        }, children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title }), (0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', flexDirection: 'column', gap: 14 }, children: fields.map((f, i) => {
                    const key = `f${i}`;
                    return ((0, jsx_runtime_1.jsxs)("div", { children: [f.type !== 'checkbox' && f.type !== 'toggle' && ((0, jsx_runtime_1.jsxs)("label", { style: { display: 'block', fontSize: tokens_1.TYPE.label, fontWeight: 600, color: theme.text2, marginBottom: 6 }, children: [f.label, f.required ? (0, jsx_runtime_1.jsx)("span", { style: { color: accent }, children: " *" }) : null] })), renderField(f, values[key], set(key), onPrompt)] }, key));
                }) }), (0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', alignItems: 'center', gap: 10, marginTop: 16 }, children: [(0, jsx_runtime_1.jsx)(UiButton_1.UiButton, { buttonStyle: "filled", children: payload.submitLabel ?? 'Submit' }), submitted && (0, jsx_runtime_1.jsx)("span", { style: { fontSize: tokens_1.TYPE.label, color: '#0CA678', fontWeight: 600 }, children: "Sent \u2713" })] })] }));
}
function renderField(f, value, set, _onPrompt) {
    switch (f.type) {
        case 'textarea':
            return (0, jsx_runtime_1.jsx)(UiTextArea_1.UiTextArea, { placeholder: String(f.value ?? ''), value: String(value ?? ''), onChange: (v) => set(v), rows: 3 });
        case 'select':
            return (0, jsx_runtime_1.jsx)(UiSelect_1.UiSelect, { value: String(value || f.options?.[0] || ''), options: f.options ?? [], onChange: (v) => set(v), ariaLabel: f.label });
        case 'radio':
            return ((0, jsx_runtime_1.jsx)("div", { children: (f.options ?? []).map((o) => ((0, jsx_runtime_1.jsx)(UiRadio_1.UiRadio, { label: o, selected: value === o, onSelect: () => set(o) }, o))) }));
        case 'checkbox':
            return (0, jsx_runtime_1.jsx)(UiCheckbox_1.UiCheckbox, { label: f.label, checked: value === true, onChange: (v) => set(v) });
        case 'toggle':
            return (0, jsx_runtime_1.jsx)(UiToggle_1.UiToggle, { label: f.label, checked: value === true, onChange: (v) => set(v) });
        case 'slider':
            return (0, jsx_runtime_1.jsx)(UiSlider_1.UiSlider, { label: f.label, value: Number(value) || 0, min: f.min ?? 0, max: f.max ?? 100, step: f.max && f.max - (f.min ?? 0) <= 10 ? 1 : (f.max ?? 100 - (f.min ?? 0)) / 100, unit: f.unit, onChange: (v) => set(v) });
        case 'segmented':
            return (0, jsx_runtime_1.jsx)(UiSegmentedSafe, { options: f.options ?? [], value: String(value || f.options?.[0] || ''), onChange: (v) => set(v) });
        case 'date':
            return (0, jsx_runtime_1.jsx)(UiDateField_1.UiDateField, { inputType: "date", value: String(value ?? ''), onChange: (v) => set(v) });
        case 'time':
            return (0, jsx_runtime_1.jsx)(UiDateField_1.UiDateField, { inputType: "time", value: String(value ?? ''), onChange: (v) => set(v) });
        case 'number':
            return ((0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', alignItems: 'center', gap: 12 }, children: (0, jsx_runtime_1.jsx)(UiStepper_1.UiStepper, { value: Number(value) || 0, min: f.min, max: f.max, unit: f.unit, onChange: (v) => set(v) }) }));
        case 'email':
            return (0, jsx_runtime_1.jsx)(UiTextInput_1.UiTextInput, { inputType: "email", inputMode: "email", placeholder: "name@example.com", value: String(value ?? ''), onChange: (v) => set(v) });
        case 'url':
            return (0, jsx_runtime_1.jsx)(UiTextInput_1.UiTextInput, { inputType: "url", inputMode: "url", placeholder: "https://\u2026", value: String(value ?? ''), onChange: (v) => set(v) });
        case 'color':
            return (0, jsx_runtime_1.jsx)(ColorField, { value: String(value || '#4C6FFF'), onChange: (v) => set(v) });
        case 'file':
            return (0, jsx_runtime_1.jsx)(UiFileField_1.UiFileField, { label: `Attach — ${f.label}` });
        case 'rating':
            return (0, jsx_runtime_1.jsx)(UiRatingInput_1.UiRatingInput, { max: f.max ?? 5, style: "star", value: Number(value) || 0, onChange: (v) => set(v) });
        case 'otp':
            return (0, jsx_runtime_1.jsx)(UiOtpField_1.UiOtpField, { length: 6, value: String(value ?? ''), onChange: (v) => set(v) });
        case 'text':
        default:
            return (0, jsx_runtime_1.jsx)(UiTextInput_1.UiTextInput, { placeholder: String(f.value ?? ''), value: String(value ?? ''), onChange: (v) => set(v) });
    }
}
function UiSegmentedSafe({ options, value, onChange }) {
    return (0, jsx_runtime_1.jsx)(Segmented, { options: options, value: value, onChange: onChange });
}
function Segmented({ options, value, onChange }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    return ((0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', gap: 6, flexWrap: 'wrap' }, children: options.map((o) => ((0, jsx_runtime_1.jsx)(UiChipToggle_1.UiChipToggle, { label: o, selected: o === value, onToggle: () => onChange(o) }, o))) }));
}
function ColorField({ value, onChange }) {
    const { theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    return ((0, jsx_runtime_1.jsxs)("span", { style: { display: 'inline-flex', alignItems: 'center', gap: 10 }, children: [(0, jsx_runtime_1.jsx)("input", { type: "color", value: /^#[0-9a-fA-F]{6}$/.test(value) ? value : '#4C6FFF', onChange: (e) => onChange(e.target.value), style: { width: 40, height: 32, padding: 0, border: `1px solid ${theme.border}`, borderRadius: 8, background: 'transparent', cursor: 'pointer' } }), (0, jsx_runtime_1.jsx)("code", { style: { fontSize: tokens_1.TYPE.label, color: theme.text2 }, children: value })] }));
}
