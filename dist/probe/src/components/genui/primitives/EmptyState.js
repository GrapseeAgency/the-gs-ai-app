"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.EmptyState = EmptyState;
const jsx_runtime_1 = require("react/jsx-runtime");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/** The honest empty state — a widget whose payload arrived empty. */
function EmptyState({ label }) {
    const { theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    return ((0, jsx_runtime_1.jsx)("div", { style: {
            padding: '20px 0',
            textAlign: 'center',
            fontSize: tokens_1.TYPE.label,
            color: theme.text3,
        }, children: label }));
}
