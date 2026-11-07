"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.Badge = Badge;
const jsx_runtime_1 = require("react/jsx-runtime");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/** A small filled badge — counts, statuses, deltas. */
function Badge({ children, filled = false, style, }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    return ((0, jsx_runtime_1.jsx)("span", { style: {
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            minWidth: 20,
            height: 20,
            padding: '0 6px',
            borderRadius: 999,
            fontSize: tokens_1.TYPE.micro,
            fontWeight: 700,
            background: filled ? accent : (0, tokens_1.alpha)(accent, theme.dark ? 0.18 : 0.12),
            color: filled ? (0, tokens_1.inkOn)(accent) : theme.dark ? accent : accent,
            ...style,
        }, children: children }));
}
