"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.WordcloudChartView = WordcloudChartView;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const ChartFrame_1 = require("./ChartFrame");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/**
 * Wordcloud — deterministic spiral placement (archimedean), font size by
 * weight. Same reading as the Android renderer; no external layout engine.
 */
function WordcloudChartView({ payload, height = 200 }) {
    const { accent, ramp, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const items = (payload.items ?? []).filter((i) => i.text).slice(0, 40);
    const W = 320;
    const H = height;
    const placed = (0, react_1.useMemo)(() => {
        const maxW = Math.max(1, ...items.map((i) => i.weight ?? 1));
        const minW = Math.min(...items.map((i) => i.weight ?? 1));
        const sorted = [...items].sort((a, b) => (b.weight ?? 0) - (a.weight ?? 0));
        const boxes = [];
        const est = (text, size) => ({ w: text.length * size * 0.58, h: size * 1.15 });
        for (const item of sorted) {
            const t = ((item.weight ?? 1) - minW) / Math.max(1e-9, maxW - minW);
            const size = 11 + Math.round(t * 21);
            const { w, h } = est(item.text, size);
            let angle = Math.random(); // replaced below — deterministic spiral anyway
            angle = boxes.length * 2.39996; // golden-angle-ish ordering, deterministic
            let x = 0;
            let y = 0;
            const rot = boxes.length % 4 === 3 ? 90 : 0;
            const bw = rot === 90 ? h : w;
            const bh = rot === 90 ? w : h;
            let radius = 0;
            for (let step = 0; step < 400; step++) {
                const a = angle + step * 0.35;
                radius = 4 * Math.sqrt(step);
                x = Math.cos(a) * radius * 1.35;
                y = Math.sin(a) * radius * 0.75;
                const overlaps = boxes.some((b) => Math.abs(b.x - x) < (b.w + bw) / 2 + 3 && Math.abs(b.y - y) < (b.h + bh) / 2 + 2);
                if (!overlaps)
                    break;
            }
            boxes.push({ text: item.text, size, x, y, rot, w: bw, h: bh });
        }
        const maxX = Math.max(40, ...boxes.map((b) => Math.abs(b.x) + b.w / 2));
        const maxY = Math.max(20, ...boxes.map((b) => Math.abs(b.y) + b.h / 2));
        return { boxes, sx: (W / 2 - 8) / maxX, sy: (H / 2 - 8) / maxY };
    }, [items, H]);
    const colors = ramp(4);
    return ((0, jsx_runtime_1.jsx)(ChartFrame_1.ChartFrame, { title: payload.title, legend: false, height: height, children: () => ((0, jsx_runtime_1.jsx)("div", { style: { position: 'relative', height, overflow: 'hidden' }, children: placed.boxes.map((b, i) => ((0, jsx_runtime_1.jsx)("span", { title: `${b.text} (${items.find((it) => it.text === b.text)?.weight ?? 1})`, style: {
                    position: 'absolute',
                    left: `calc(50% + ${b.x * placed.sx}px)`,
                    top: `calc(50% + ${b.y * placed.sy}px)`,
                    transform: `translate(-50%,-50%) rotate(${b.rot}deg)`,
                    fontSize: b.size,
                    fontWeight: b.size > 20 ? 700 : 600,
                    color: i < 3 ? colors[i % colors.length] : theme.dark ? (0, tokens_1.alpha)('#FFFFFF', 0.82) : 'rgba(20,23,28,0.78)',
                    whiteSpace: 'nowrap',
                    lineHeight: 1,
                }, children: b.text }, `${b.text}-${i}`))) })) }));
}
