"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.ChartFrame = ChartFrame;
exports.niceTicks = niceTicks;
const jsx_runtime_1 = require("react/jsx-runtime");
/**
 * ChartFrame — the shared scaffolding for every chart component: header, unit
 * legend, responsive measurement and the empty state. Each chart component
 * owns only its marks; this owns the rest, exactly like Android's shared
 * chart scaffolding in WidgetsCharts2.kt.
 */
const react_1 = require("react");
const TitleRow_1 = require("../primitives/TitleRow");
const EmptyState_1 = require("../primitives/EmptyState");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const tokens_1 = require("../theme/tokens");
function ChartFrame({ title, unit, seriesNames, height = 200, emptyLabel = 'No data to plot', legend = true, children, }) {
    const { theme, ramp } = (0, WidgetSurface_1.useWidgetIdentity)();
    const ref = (0, react_1.useRef)(null);
    const [width, setWidth] = (0, react_1.useState)(320);
    (0, react_1.useLayoutEffect)(() => {
        if (!ref.current)
            return;
        const el = ref.current;
        const update = () => setWidth(el.clientWidth);
        update();
        const ro = new ResizeObserver(update);
        ro.observe(el);
        return () => ro.disconnect();
    }, []);
    const hasData = seriesNames === undefined || seriesNames.length > 0;
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: title, right: unit }), !hasData ? ((0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: emptyLabel })) : ((0, jsx_runtime_1.jsx)("div", { ref: ref, style: { width: '100%' }, children: children({ width, height }) })), legend && seriesNames && seriesNames.length > 1 && ((0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', flexWrap: 'wrap', gap: '4px 14px', marginTop: 8 }, children: seriesNames.slice(0, 6).map((name, i) => ((0, jsx_runtime_1.jsxs)("span", { style: { display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: tokens_1.TYPE.micro, color: theme.text2 }, children: [(0, jsx_runtime_1.jsx)("span", { style: { width: 8, height: 8, borderRadius: 2, background: ramp(6)[i] } }), name] }, `${name}-${i}`))) }))] }));
}
function niceTicks(min, max, count = 4) {
    if (!Number.isFinite(min) || !Number.isFinite(max) || min === max)
        return [min];
    const span = max - min;
    const step = Math.pow(10, Math.floor(Math.log10(span / count)));
    const err = (span / count) / step;
    const mult = err >= 7.5 ? 10 : err >= 3.5 ? 5 : err >= 1.5 ? 2 : 1;
    const niceStep = step * mult;
    const start = Math.ceil(min / niceStep) * niceStep;
    const out = [];
    for (let v = start; v <= max + 1e-9; v += niceStep)
        out.push(Number(v.toFixed(10)));
    return out;
}
