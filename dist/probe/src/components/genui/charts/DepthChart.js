"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.DepthChartView = DepthChartView;
const jsx_runtime_1 = require("react/jsx-runtime");
const ChartFrame_1 = require("./ChartFrame");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/** Depth — live order-book ladder: bids (jade) vs asks (crimson) by price. */
function DepthChartView({ payload, height = 200 }) {
    const { theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const bids = (payload.bids ?? []).slice(0, 40);
    const asks = (payload.asks ?? []).slice(0, 40);
    if (!bids.length && !asks.length)
        return (0, jsx_runtime_1.jsx)(ChartFrame_1.ChartFrame, { title: payload.title, seriesNames: [], legend: false, children: () => null });
    const rows = [...asks].reverse().concat(bids); // asks desc, then bids desc
    const maxQty = Math.max(...rows.map((r) => r[1]), 1);
    const mid = asks.length && bids.length ? (asks[0][0] + bids[0][0]) / 2 : null;
    const BID = '#0CA678';
    const ASK = '#E03131';
    return ((0, jsx_runtime_1.jsx)(ChartFrame_1.ChartFrame, { title: payload.title, unit: payload.unit, legend: false, height: height, children: () => ((0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', flexDirection: 'column', gap: 1, fontFamily: 'inherit' }, children: [rows.map(([price, qty], i) => {
                    const isBid = mid === null ? i >= (rows.length) / 2 : price <= mid;
                    const color = isBid ? BID : ASK;
                    return ((0, jsx_runtime_1.jsxs)("div", { style: { position: 'relative', height: 16, borderRadius: 3, overflow: 'hidden' }, children: [(0, jsx_runtime_1.jsx)("span", { "aria-hidden": true, style: { position: 'absolute', inset: 0, width: `${(qty / maxQty) * 100}%`, background: (0, tokens_1.alpha)(color, 0.28), transition: 'width .3s ease' } }), (0, jsx_runtime_1.jsxs)("span", { style: { position: 'relative', display: 'flex', justifyContent: 'space-between', fontSize: tokens_1.TYPE.micro, padding: '0 6px', lineHeight: '16px' }, children: [(0, jsx_runtime_1.jsx)("b", { style: { color }, children: price.toLocaleString(undefined, { maximumFractionDigits: 4 }) }), (0, jsx_runtime_1.jsx)("span", { style: { color: theme.text2, fontVariantNumeric: 'tabular-nums' }, children: qty.toLocaleString() })] })] }, `${price}-${i}`));
                }), mid !== null && ((0, jsx_runtime_1.jsxs)("div", { style: { textAlign: 'center', fontSize: tokens_1.TYPE.micro, fontWeight: 700, color: (0, tokens_1.inkOn)(theme.card), background: theme.raised, borderRadius: 4, padding: '2px 0' }, children: ["mid ", mid.toLocaleString(undefined, { maximumFractionDigits: 4 })] }))] })) }));
}
