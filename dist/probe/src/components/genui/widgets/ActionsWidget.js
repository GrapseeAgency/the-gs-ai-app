"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.ActionsWidget = ActionsWidget;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const TitleRow_1 = require("../primitives/TitleRow");
const EmptyState_1 = require("../primitives/EmptyState");
/** Actions — prompt / copy / open buttons. */
function ActionsWidget({ payload, onPrompt }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const [copied, setCopied] = (0, react_1.useState)(null);
    const buttons = (payload.buttons ?? []).slice(0, 6);
    if (!buttons.length)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "No actions" });
    const face = (style) => {
        if (style === 'filled')
            return { background: accent, color: (0, tokens_1.inkOn)(accent), border: `1px solid ${accent}` };
        if (style === 'text')
            return { background: 'transparent', color: accent, border: '1px solid transparent' };
        return { background: (0, tokens_1.alpha)(accent, theme.dark ? 0.16 : 0.1), color: accent, border: `1px solid ${(0, tokens_1.alpha)(accent, 0.3)}` };
    };
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title }), (0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', flexWrap: 'wrap', gap: 8 }, children: buttons.map((btn, i) => ((0, jsx_runtime_1.jsx)("button", { type: "button", onClick: async () => {
                        if (btn.kind === 'prompt')
                            onPrompt?.(btn.value);
                        else if (btn.kind === 'copy') {
                            try {
                                await navigator.clipboard.writeText(btn.value);
                                setCopied(btn.value);
                                setTimeout(() => setCopied(null), 1600);
                            }
                            catch {
                                /* clipboard unavailable */
                            }
                        }
                        else if (btn.kind === 'open')
                            window.open(btn.value, '_blank', 'noopener');
                    }, style: {
                        padding: '9px 16px',
                        borderRadius: tokens_1.RADIUS.pill,
                        fontSize: tokens_1.TYPE.label,
                        fontWeight: 600,
                        cursor: 'pointer',
                        ...face(btn.style),
                    }, children: btn.kind === 'copy' && copied === btn.value ? 'Copied ✓' : btn.label }, i))) })] }));
}
