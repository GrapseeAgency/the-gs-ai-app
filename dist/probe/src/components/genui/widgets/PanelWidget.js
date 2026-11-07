"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.PanelWidget = PanelWidget;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const Chip_1 = require("../primitives/Chip");
/** Panel — expandable section with badges and an optional popover reveal. */
function PanelWidget({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const [open, setOpen] = (0, react_1.useState)(payload.expanded === true);
    const [more, setMore] = (0, react_1.useState)(false);
    const toneMap = {
        accent: 'accent',
        success: 'success',
        warning: 'warning',
        danger: 'danger',
    };
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsxs)("button", { type: "button", "aria-expanded": open, onClick: () => setOpen((o) => !o), style: {
                    width: '100%',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 10,
                    background: 'transparent',
                    border: 'none',
                    padding: 0,
                    cursor: 'pointer',
                    textAlign: 'left',
                }, children: [(0, jsx_runtime_1.jsx)("span", { style: {
                            width: 22,
                            height: 22,
                            borderRadius: 999,
                            background: (0, tokens_1.alpha)(accent, theme.dark ? 0.18 : 0.12),
                            color: accent,
                            fontSize: 12,
                            display: 'inline-flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            fontWeight: 700,
                            flexShrink: 0,
                            transition: 'transform .2s ease',
                            transform: open ? 'rotate(90deg)' : 'none',
                        }, children: "\u25B8" }), (0, jsx_runtime_1.jsx)("span", { style: { flex: 1, minWidth: 0 }, children: (0, jsx_runtime_1.jsxs)("span", { style: { display: 'block', fontSize: tokens_1.TYPE.heading, fontWeight: 600, color: theme.text }, children: [payload.title, payload.summary ? (0, jsx_runtime_1.jsxs)("span", { style: { fontWeight: 400, color: theme.text2 }, children: [" \u2014 ", payload.summary] }) : null] }) })] }), (payload.badges ?? []).length > 0 && ((0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', gap: 6, flexWrap: 'wrap', margin: '8px 0 0 32px' }, children: (payload.badges ?? []).map((b, i) => ((0, jsx_runtime_1.jsx)(Chip_1.Chip, { tone: toneMap[b.tone ?? 'accent'] ?? 'accent', children: b.label }, i))) })), open && payload.body ? ((0, jsx_runtime_1.jsx)("div", { style: { margin: '10px 0 0 32px', fontSize: tokens_1.TYPE.body, color: theme.text, lineHeight: 1.6 }, children: payload.body })) : null, open && payload.popover ? ((0, jsx_runtime_1.jsxs)("div", { style: { margin: '8px 0 0 32px' }, children: [(0, jsx_runtime_1.jsx)("button", { type: "button", onClick: () => setMore((m) => !m), style: {
                            background: 'transparent',
                            border: 'none',
                            color: accent,
                            fontSize: tokens_1.TYPE.label,
                            fontWeight: 600,
                            cursor: 'pointer',
                            padding: 0,
                        }, children: more ? 'Hide details' : 'More info' }), more && (0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.label, color: theme.text2, lineHeight: 1.6, marginTop: 6 }, children: payload.popover })] })) : null] }));
}
