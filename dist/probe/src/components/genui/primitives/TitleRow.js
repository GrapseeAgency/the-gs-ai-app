"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.TitleRow = TitleRow;
const jsx_runtime_1 = require("react/jsx-runtime");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const tokens_1 = require("../theme/tokens");
/** The standard widget header: title left, unit/label right. */
function TitleRow({ title, right, style, }) {
    const { theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    if (!title && !right)
        return null;
    return ((0, jsx_runtime_1.jsxs)("div", { style: {
            display: 'flex',
            alignItems: 'baseline',
            justifyContent: 'space-between',
            gap: 12,
            marginBottom: 10,
            ...style,
        }, children: [title ? ((0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.heading, fontWeight: 600, lineHeight: 1.25 }, children: title })) : ((0, jsx_runtime_1.jsx)("span", {})), right ? (0, jsx_runtime_1.jsx)("span", { style: { fontSize: tokens_1.TYPE.label, color: theme.text2, whiteSpace: 'nowrap' }, children: right }) : null] }));
}
