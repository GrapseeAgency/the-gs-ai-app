"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.ChipsWidget = ChipsWidget;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const TitleRow_1 = require("../primitives/TitleRow");
const EmptyState_1 = require("../primitives/EmptyState");
const UiChipToggle_1 = require("../controls/UiChipToggle");
/** Chips — filter chips; multi by default, tap sends the chip's prompt. */
function ChipsWidget({ payload, onPrompt }) {
    const { theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const [selected, setSelected] = (0, react_1.useState)(new Set());
    if (!(payload.options ?? []).length)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "No chips" });
    const toggle = (i, prompt) => {
        setSelected((current) => {
            if (payload.multi === false)
                return current.has(i) ? current : new Set([i]);
            const next = new Set(current);
            if (next.has(i))
                next.delete(i);
            else
                next.add(i);
            return next;
        });
        if (prompt && payload.multi === false)
            onPrompt?.(prompt);
    };
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title }), (0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', flexWrap: 'wrap', gap: 8 }, children: (payload.options ?? []).map((opt, i) => ((0, jsx_runtime_1.jsx)(UiChipToggle_1.UiChipToggle, { label: opt.label, selected: selected.has(i), onToggle: () => toggle(i, opt.prompt) }, i))) })] }));
}
