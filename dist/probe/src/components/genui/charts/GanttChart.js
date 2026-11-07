"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.GanttChartView = GanttChartView;
const jsx_runtime_1 = require("react/jsx-runtime");
const ChartFrame_1 = require("./ChartFrame");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/** Gantt — one range [start,end] per series; labels name the tasks. */
function GanttChartView({ payload, height }) {
    const { ramp, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const series = payload.series ?? [];
    const labels = payload.labels ?? [];
    const tasks = series.map((s, i) => ({
        name: s.name || labels[i] || `Task ${i + 1}`,
        start: Math.min(s.data[0]?.[0] ?? 0, s.data[0]?.[1] ?? 0),
        end: Math.max(s.data[0]?.[0] ?? 0, s.data[0]?.[1] ?? 0),
    }));
    const lo = Math.min(...tasks.map((t) => t.start));
    const hi = Math.max(...tasks.map((t) => t.end));
    const span = hi - lo || 1;
    const h = height ?? Math.max(110, tasks.length * 34 + 42);
    const colors = ramp(Math.max(1, tasks.length));
    return ((0, jsx_runtime_1.jsx)(ChartFrame_1.ChartFrame, { title: payload.title, unit: payload.unit, legend: false, height: h, children: () => ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', justifyContent: 'space-between', fontSize: tokens_1.TYPE.micro, color: theme.text3, marginBottom: 4 }, children: [(0, jsx_runtime_1.jsx)("span", { children: lo }), (0, jsx_runtime_1.jsx)("span", { children: hi })] }), tasks.map((t, i) => ((0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', alignItems: 'center', gap: 8, height: 28 }, children: [(0, jsx_runtime_1.jsx)("span", { style: { width: 92, fontSize: tokens_1.TYPE.micro, color: theme.text2, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', textAlign: 'right' }, children: t.name }), (0, jsx_runtime_1.jsx)("div", { style: { flex: 1, height: 14, borderRadius: 7, background: theme.dark ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.04)', position: 'relative' }, children: (0, jsx_runtime_1.jsx)("div", { title: `${t.name}: ${t.start} → ${t.end}`, style: {
                                    position: 'absolute',
                                    left: `${((t.start - lo) / span) * 100}%`,
                                    width: `${Math.max(2, ((t.end - t.start) / span) * 100)}%`,
                                    top: 0,
                                    bottom: 0,
                                    borderRadius: 7,
                                    background: colors[i % colors.length],
                                } }) })] }, t.name)))] })) }));
}
