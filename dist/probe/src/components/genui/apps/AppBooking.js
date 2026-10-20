"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.AppBooking = AppBooking;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const AppShell_1 = require("./AppShell");
const EmptyState_1 = require("../primitives/EmptyState");
/** Booking — pick a slot, confirm. */
function AppBooking({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const slots = (payload.slots ?? []).slice(0, 12);
    const [picked, setPicked] = (0, react_1.useState)(null);
    const [done, setDone] = (0, react_1.useState)(false);
    if (!slots.length)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "No slots available" });
    return ((0, jsx_runtime_1.jsxs)(AppShell_1.AppShell, { title: payload.title, submitLabel: picked && !done ? (payload.submitLabel ?? 'Confirm booking') : undefined, children: [(0, jsx_runtime_1.jsx)("div", { style: { display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 8 }, children: slots.map((slot) => ((0, jsx_runtime_1.jsx)("button", { type: "button", onClick: () => !done && setPicked(slot), style: {
                        padding: '10px 0',
                        borderRadius: tokens_1.RADIUS.md,
                        border: `1.5px solid ${picked === slot ? accent : theme.border}`,
                        background: picked === slot ? `${accent}1f` : 'transparent',
                        color: picked === slot ? accent : theme.text2,
                        fontSize: tokens_1.TYPE.label,
                        fontWeight: picked === slot ? 700 : 500,
                        cursor: done ? 'default' : 'pointer',
                        fontVariantNumeric: 'tabular-nums',
                    }, children: slot }, slot))) }), done && picked ? (0, jsx_runtime_1.jsxs)("div", { style: { marginTop: 12, fontSize: tokens_1.TYPE.label, color: '#0CA678', fontWeight: 700 }, children: ["Booked for ", picked, " \u2713"] }) : null] }));
}
