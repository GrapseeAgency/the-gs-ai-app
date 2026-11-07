"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.VoiceWidget = VoiceWidget;
const jsx_runtime_1 = require("react/jsx-runtime");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/** Voice — the mic card; on web it sends the prompt as a normal message. */
function VoiceWidget({ payload, onPrompt }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    return ((0, jsx_runtime_1.jsxs)("button", { type: "button", onClick: () => payload.prompt && onPrompt?.(payload.prompt), style: {
            width: '100%',
            display: 'flex',
            alignItems: 'center',
            gap: 14,
            padding: 16,
            borderRadius: tokens_1.RADIUS.lg,
            border: `1.5px solid ${(0, tokens_1.alpha)(accent, 0.4)}`,
            background: (0, tokens_1.alpha)(accent, theme.dark ? 0.1 : 0.06),
            cursor: 'pointer',
            textAlign: 'left',
        }, children: [(0, jsx_runtime_1.jsx)("span", { style: {
                    width: 40,
                    height: 40,
                    borderRadius: 999,
                    background: accent,
                    display: 'inline-flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    flexShrink: 0,
                    animation: 'gs-mic 2s ease infinite',
                }, children: (0, jsx_runtime_1.jsxs)("svg", { width: "18", height: "18", viewBox: "0 0 24 24", fill: "none", stroke: theme.dark ? '#0F1216' : '#fff', strokeWidth: "2.2", strokeLinecap: "round", children: [(0, jsx_runtime_1.jsx)("rect", { x: "9", y: "2.5", width: "6", height: "11", rx: "3" }), (0, jsx_runtime_1.jsx)("path", { d: "M5 11a7 7 0 0 0 14 0M12 18v3.5" })] }) }), (0, jsx_runtime_1.jsxs)("span", { children: [(0, jsx_runtime_1.jsx)("span", { style: { display: 'block', fontSize: tokens_1.TYPE.body, fontWeight: 700, color: theme.text }, children: payload.title ?? 'Talk instead' }), (0, jsx_runtime_1.jsx)("span", { style: { display: 'block', fontSize: tokens_1.TYPE.label, color: theme.text2, marginTop: 2 }, children: payload.hint ?? payload.prompt })] }), (0, jsx_runtime_1.jsx)("style", { children: `@keyframes gs-mic { 0%,100% { box-shadow: 0 0 0 0 ${(0, tokens_1.alpha)(accent, 0.35)} } 50% { box-shadow: 0 0 0 9px ${(0, tokens_1.alpha)(accent, 0)} } }` })] }));
}
