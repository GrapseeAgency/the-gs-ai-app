"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.UiFileField = UiFileField;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/** File attach — name-only in chat (the file never leaves the device here). */
function UiFileField({ label = 'Attach a file' }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const ref = (0, react_1.useRef)(null);
    const [name, setName] = (0, react_1.useState)('');
    return ((0, jsx_runtime_1.jsxs)("span", { children: [(0, jsx_runtime_1.jsx)("button", { type: "button", onClick: () => ref.current?.click(), style: {
                    padding: '9px 14px',
                    borderRadius: tokens_1.RADIUS.md,
                    border: `1px dashed ${theme.border}`,
                    background: 'transparent',
                    color: name ? accent : theme.text2,
                    fontSize: tokens_1.TYPE.label,
                    fontWeight: 600,
                    cursor: 'pointer',
                }, children: name || label }), (0, jsx_runtime_1.jsx)("input", { ref: ref, type: "file", style: { display: 'none' }, onChange: (e) => setName(e.target.files?.[0]?.name ?? '') })] }));
}
