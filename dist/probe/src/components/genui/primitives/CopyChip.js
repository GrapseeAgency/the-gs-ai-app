"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.CopyChip = CopyChip;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/** Copy-to-clipboard as a quiet control. The clipboard write is the action. */
function CopyChip({ value, label = 'Copy' }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const [done, setDone] = (0, react_1.useState)(false);
    return ((0, jsx_runtime_1.jsx)("button", { type: "button", onClick: async () => {
            try {
                await navigator.clipboard.writeText(value);
                setDone(true);
                setTimeout(() => setDone(false), 1600);
            }
            catch {
                setDone(false);
            }
        }, style: {
            display: 'inline-flex',
            alignItems: 'center',
            gap: 6,
            padding: '5px 12px',
            borderRadius: 999,
            border: `1px solid ${done ? accent : theme.border}`,
            background: 'transparent',
            color: done ? accent : theme.text2,
            fontSize: tokens_1.TYPE.label,
            fontWeight: 600,
            cursor: 'pointer',
        }, children: done ? 'Copied' : label }));
}
