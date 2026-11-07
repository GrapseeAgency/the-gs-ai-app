"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.AppCalendar = AppCalendar;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const AppShell_1 = require("./AppShell");
/** Calendar — a month grid with event dots; tap a day to select. */
function AppCalendar({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const monthStr = payload.month ?? new Date().toISOString().slice(0, 7);
    const [year, month] = monthStr.split('-').map(Number);
    const [selected, setSelected] = (0, react_1.useState)(null);
    const first = new Date(year ?? 2026, (month ?? 1) - 1, 1);
    const days = new Date(year ?? 2026, month ?? 1, 0).getDate();
    const offset = (first.getDay() + 6) % 7; // Monday-first
    const eventDays = new Set((payload.items ?? []).map((it) => Number(String(it.date ?? '').split('-')[2])).filter((d) => Number.isFinite(d) && d > 0));
    const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
    return ((0, jsx_runtime_1.jsxs)(AppShell_1.AppShell, { title: payload.title ?? `${MONTHS[(month ?? 1) - 1]} ${year}`, children: [(0, jsx_runtime_1.jsxs)("div", { style: { display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 3 }, children: [['M', 'T', 'W', 'T', 'F', 'S', 'S'].map((d, i) => ((0, jsx_runtime_1.jsx)("span", { style: { textAlign: 'center', fontSize: tokens_1.TYPE.micro, color: theme.text3, fontWeight: 700 }, children: d }, i))), Array.from({ length: offset }, (_, i) => ((0, jsx_runtime_1.jsx)("span", {}, `pad-${i}`))), Array.from({ length: days }, (_, d) => {
                        const day = d + 1;
                        const isSel = selected === day;
                        return ((0, jsx_runtime_1.jsxs)("button", { type: "button", onClick: () => setSelected(day), style: {
                                position: 'relative',
                                height: 34,
                                borderRadius: 9,
                                border: `1.5px solid ${isSel ? accent : 'transparent'}`,
                                background: isSel ? (0, tokens_1.alpha)(accent, 0.16) : 'transparent',
                                color: isSel ? accent : theme.text2,
                                fontSize: tokens_1.TYPE.label,
                                fontWeight: isSel ? 700 : 400,
                                cursor: 'pointer',
                            }, children: [day, eventDays.has(day) && (0, jsx_runtime_1.jsx)("span", { style: { position: 'absolute', bottom: 3, left: '50%', marginLeft: -2, width: 4, height: 4, borderRadius: 999, background: accent } })] }, day));
                    })] }), selected !== null && ((0, jsx_runtime_1.jsxs)("div", { style: { marginTop: 10, fontSize: tokens_1.TYPE.micro, color: theme.text2 }, children: [selected, " ", String((payload.items ?? []).find((it) => Number(String(it.date ?? '').split('-')[2]) === selected)?.title ?? '') || '— no events'] }))] }));
}
