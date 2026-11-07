"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.Chip = Chip;
const jsx_runtime_1 = require("react/jsx-runtime");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/** A static chip — inline metadata, not a control (see controls/UiChipToggle). */
function Chip({ children, tone = 'neutral', style, }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const tones = {
        neutral: { bg: theme.raised, fg: theme.text2 },
        accent: { bg: (0, tokens_1.alpha)(accent, theme.dark ? 0.2 : 0.14), fg: theme.dark ? accent : accent },
        success: { bg: 'rgba(12,166,120,0.16)', fg: '#0CA678' },
        warning: { bg: 'rgba(240,140,0,0.16)', fg: '#F08C00' },
        danger: { bg: 'rgba(224,49,49,0.16)', fg: '#E03131' },
    };
    const t = tones[tone] ?? tones.neutral;
    return ((0, jsx_runtime_1.jsx)("span", { style: {
            display: 'inline-flex',
            alignItems: 'center',
            gap: 4,
            padding: '3px 10px',
            borderRadius: 999,
            fontSize: tokens_1.TYPE.micro,
            fontWeight: 600,
            background: t.bg,
            color: t.fg,
            whiteSpace: 'nowrap',
            ...style,
        }, children: children }));
}
