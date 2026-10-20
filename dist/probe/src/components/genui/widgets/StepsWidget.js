"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.StepsWidget = StepsWidget;
const jsx_runtime_1 = require("react/jsx-runtime");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const TitleRow_1 = require("../primitives/TitleRow");
const EmptyState_1 = require("../primitives/EmptyState");
/** Steps — a vertical stepper: done / current / todo states. */
function StepsWidget({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const steps = (payload.steps ?? []).slice(0, 12);
    if (!steps.length)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "No steps" });
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title, right: `${steps.filter((s) => s.state === 'done').length}/${steps.length}` }), (0, jsx_runtime_1.jsxs)("div", { style: { position: 'relative', paddingLeft: 30 }, children: [(0, jsx_runtime_1.jsx)("span", { "aria-hidden": true, style: { position: 'absolute', left: 11, top: 8, bottom: 8, width: 2, background: theme.border } }), steps.map((step, i) => {
                        const done = step.state === 'done';
                        const current = step.state === 'current';
                        return ((0, jsx_runtime_1.jsxs)("div", { style: { position: 'relative', paddingBottom: i === steps.length - 1 ? 0 : 16 }, children: [(0, jsx_runtime_1.jsx)("span", { "aria-hidden": true, style: {
                                        position: 'absolute',
                                        left: -30,
                                        top: 0,
                                        width: 24,
                                        height: 24,
                                        borderRadius: 999,
                                        display: 'inline-flex',
                                        alignItems: 'center',
                                        justifyContent: 'center',
                                        fontSize: tokens_1.TYPE.micro,
                                        fontWeight: 700,
                                        background: done ? accent : current ? (0, tokens_1.alpha)(accent, 0.16) : theme.card,
                                        border: `2px solid ${done || current ? accent : theme.border}`,
                                        color: done ? theme.dark ? '#0F1216' : '#fff' : current ? accent : theme.text3,
                                    }, children: done ? '✓' : i + 1 }), (0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.body, fontWeight: current ? 700 : 600, color: current ? accent : theme.text }, children: step.title }), step.body ? (0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.label, color: theme.text2, lineHeight: 1.55, marginTop: 3 }, children: step.body }) : null] }, i));
                    })] })] }));
}
