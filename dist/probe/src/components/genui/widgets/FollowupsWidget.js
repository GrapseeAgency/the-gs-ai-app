"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.FollowupsWidget = FollowupsWidget;
const jsx_runtime_1 = require("react/jsx-runtime");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const EmptyState_1 = require("../primitives/EmptyState");
/** Follow-ups — quiet tappable next prompts. */
function FollowupsWidget({ payload, onPrompt }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const prompts = (payload.prompts ?? []).slice(0, 6);
    if (!prompts.length)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "No suggestions" });
    return ((0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', flexWrap: 'wrap', gap: 8 }, children: prompts.map((prompt, i) => ((0, jsx_runtime_1.jsx)("button", { type: "button", onClick: () => onPrompt?.(prompt), style: {
                padding: '8px 14px',
                borderRadius: tokens_1.RADIUS.pill,
                border: `1px solid ${theme.border}`,
                background: 'transparent',
                color: theme.text2,
                fontSize: tokens_1.TYPE.label,
                fontWeight: 500,
                cursor: 'pointer',
            }, onMouseEnter: (e) => (e.currentTarget.style.color = accent), onMouseLeave: (e) => (e.currentTarget.style.color = theme.text2), children: prompt }, i))) }));
}
