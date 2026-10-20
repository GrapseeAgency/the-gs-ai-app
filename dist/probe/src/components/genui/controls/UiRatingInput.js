"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.UiRatingInput = UiRatingInput;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/** star | heart | thumb — the tappable rating input. */
function UiRatingInput({ max = 5, style = 'star', value, onChange, }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const [hover, setHover] = (0, react_1.useState)(0);
    const glyph = style === 'heart' ? '♥' : style === 'thumb' ? '👍' : '★';
    const shown = hover || value;
    return ((0, jsx_runtime_1.jsxs)("div", { role: "radiogroup", style: { display: 'inline-flex', gap: 4 }, children: [Array.from({ length: max }, (_, i) => i + 1).map((i) => ((0, jsx_runtime_1.jsx)("button", { type: "button", role: "radio", "aria-checked": value === i, "aria-label": `${i} of ${max}`, onMouseEnter: () => setHover(i), onMouseLeave: () => setHover(0), onClick: () => onChange(i), style: {
                    border: 'none',
                    background: 'transparent',
                    fontSize: style === 'thumb' ? 22 : 26,
                    lineHeight: 1,
                    padding: 2,
                    cursor: 'pointer',
                    filter: i <= shown ? 'none' : 'grayscale(1)',
                    opacity: i <= shown ? 1 : 0.35,
                    color: accent,
                }, children: glyph }, i))), (0, jsx_runtime_1.jsx)("span", { style: { fontSize: tokens_1.TYPE.label, color: theme.text2, alignSelf: 'center', marginLeft: 6 }, children: shown > 0 ? `${shown}/${max}` : '' })] }));
}
