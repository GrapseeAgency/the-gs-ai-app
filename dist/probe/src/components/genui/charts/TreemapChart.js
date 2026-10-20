"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.TreemapChartView = TreemapChartView;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const ChartFrame_1 = require("./ChartFrame");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/** Classic squarified treemap on a plain container — no dependency. */
function squarify(values, x, y, w, h) {
    const total = values.reduce((a, b) => a + b, 0) || 1;
    const rects = [];
    let i = 0;
    let cx = x;
    let cy = y;
    let cw = w;
    let ch = h;
    let remaining = total;
    while (i < values.length) {
        const vertical = cw >= ch;
        const take = Math.max(1, Math.round((vertical ? cw / ch : ch / cw)));
        const group = values.slice(i, i + take);
        const gsum = group.reduce((a, b) => a + b, 0);
        const frac = gsum / remaining;
        if (vertical) {
            const gw = cw * frac;
            let gy = cy;
            for (const v of group) {
                const gh = (v / gsum) * ch;
                rects.push({ x: cx, y: gy, w: gw, h: gh });
                gy += gh;
            }
            cx += gw;
            cw -= gw;
        }
        else {
            const gh = ch * frac;
            let gx = cx;
            for (const v of group) {
                const gw = (v / gsum) * cw;
                rects.push({ x: gx, y: cy, w: gw, h: gh });
                gx += gw;
            }
            cy += gh;
            ch -= gh;
        }
        remaining -= gsum;
        i += take;
    }
    return rects;
}
function TreemapChartView({ payload, height = 220 }) {
    const { ramp, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const labels = payload.labels ?? [];
    const data = (payload.series?.[0]?.data ?? [])
        .map((d, i) => ({ name: payload.series?.[0] && labels[i] ? labels[i] : `#${i + 1}`, value: d[d.length - 1] }))
        .filter((d) => d.value > 0);
    const rects = (0, react_1.useMemo)(() => squarify(data.map((d) => d.value), 0, 0, 320, height - 24), [data, height]);
    const colors = ramp(Math.max(1, data.length));
    return ((0, jsx_runtime_1.jsx)(ChartFrame_1.ChartFrame, { title: payload.title, unit: payload.unit, legend: false, height: height, children: ({ width }) => ((0, jsx_runtime_1.jsx)("div", { style: { position: 'relative', width: '100%', height: height - 24 }, children: rects.map((r, i) => ((0, jsx_runtime_1.jsxs)("div", { title: `${data[i]?.name}: ${data[i]?.value}`, style: {
                    position: 'absolute',
                    left: `${(r.x / 320) * 100}%`,
                    top: `${(r.y / (height - 24)) * 100}%`,
                    width: `${(r.w / 320) * 100}%`,
                    height: `${(r.h / (height - 24)) * 100}%`,
                    background: colors[i % colors.length],
                    outline: `2px solid ${theme.dark ? '#0F1216' : '#fff'}`,
                    borderRadius: 6,
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    justifyContent: 'center',
                    overflow: 'hidden',
                    padding: 2,
                }, children: [(0, jsx_runtime_1.jsx)("span", { style: { fontSize: tokens_1.TYPE.micro, fontWeight: 700, color: (0, tokens_1.inkOn)(colors[i % colors.length]), whiteSpace: 'nowrap', textOverflow: 'ellipsis', maxWidth: '100%', overflow: 'hidden' }, children: data[i]?.name }), r.h > 30 && ((0, jsx_runtime_1.jsx)("span", { style: { fontSize: tokens_1.TYPE.micro, color: (0, tokens_1.inkOn)(colors[i % colors.length]), opacity: 0.85 }, children: data[i]?.value }))] }, i))) })) }));
}
