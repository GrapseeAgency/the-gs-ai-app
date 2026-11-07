"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.CarouselWidget = CarouselWidget;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const TitleRow_1 = require("../primitives/TitleRow");
const EmptyState_1 = require("../primitives/EmptyState");
/** Carousel — swipe/scroll media cards with dots. */
function CarouselWidget({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const [index, setIndex] = (0, react_1.useState)(0);
    const ref = (0, react_1.useRef)(null);
    const items = (payload.items ?? []).slice(0, 8);
    if (!items.length)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "No images" });
    const onScroll = () => {
        const el = ref.current;
        if (!el)
            return;
        setIndex(Math.round(el.scrollLeft / el.clientWidth));
    };
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title, right: `${index + 1}/${items.length}` }), (0, jsx_runtime_1.jsx)("div", { ref: ref, onScroll: onScroll, style: { display: 'flex', overflowX: 'auto', scrollSnapType: 'x mandatory', gap: 10, borderRadius: tokens_1.RADIUS.lg, scrollbarWidth: 'none' }, children: items.map((item, i) => ((0, jsx_runtime_1.jsxs)("figure", { style: { flex: '0 0 100%', scrollSnapAlign: 'center', margin: 0 }, children: [item.image ? (
                        // eslint-disable-next-line @next/next/no-img-element
                        (0, jsx_runtime_1.jsx)("img", { src: item.image, alt: item.caption ?? `slide ${i + 1}`, loading: "lazy", style: { width: '100%', height: 190, objectFit: 'cover', borderRadius: tokens_1.RADIUS.lg, border: `1px solid ${theme.border}` } })) : null, item.caption ? ((0, jsx_runtime_1.jsx)("figcaption", { style: { fontSize: tokens_1.TYPE.label, color: theme.text2, marginTop: 6, textAlign: 'center' }, children: item.caption })) : null] }, i))) }), (0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', justifyContent: 'center', gap: 6, marginTop: 8 }, children: items.map((_, i) => ((0, jsx_runtime_1.jsx)("span", { style: { width: i === index ? 16 : 6, height: 6, borderRadius: 3, background: i === index ? accent : (0, tokens_1.alpha)(theme.dark ? '#FFFFFF' : '#14171C', 0.2), transition: 'width .25s ease' } }, i))) })] }));
}
