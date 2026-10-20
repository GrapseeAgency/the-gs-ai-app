"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.UiChipToggle = UiChipToggle;
const jsx_runtime_1 = require("react/jsx-runtime");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/** A toggleable filter chip — the multi/single-select surface. */
function UiChipToggle({ label, selected, onToggle, }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    return ((0, jsx_runtime_1.jsx)("button", { type: "button", "aria-pressed": selected, onClick: onToggle, style: {
            padding: '7px 14px',
            borderRadius: tokens_1.RADIUS.pill,
            fontSize: tokens_1.TYPE.label,
            fontWeight: 600,
            cursor: 'pointer',
            background: selected ? accent : 'transparent',
            color: selected ? (0, tokens_1.inkOn)(accent) : theme.text2,
            border: `1px solid ${selected ? accent : (0, tokens_1.alpha)(theme.dark ? '#FFFFFF' : '#14171C', 0.14)}`,
        }, children: label }));
}
