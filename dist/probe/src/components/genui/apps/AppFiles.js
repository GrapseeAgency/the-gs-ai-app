"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.AppFiles = AppFiles;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const AppShell_1 = require("./AppShell");
const EmptyState_1 = require("../primitives/EmptyState");
/** Files — a simple file browser list. */
function AppFiles({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const items = (payload.items ?? []).slice(0, 30);
    const [selected, setSelected] = (0, react_1.useState)(null);
    if (!items.length)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "No files" });
    const icon = (name) => {
        const ext = name.split('.').pop()?.toLowerCase();
        if (['png', 'jpg', 'jpeg', 'gif', 'webp'].includes(ext ?? ''))
            return '🖼';
        if (['mp4', 'webm', 'mov'].includes(ext ?? ''))
            return '🎬';
        if (['mp3', 'm4a', 'wav', 'ogg'].includes(ext ?? ''))
            return '🎵';
        if (['pdf', 'doc', 'docx'].includes(ext ?? ''))
            return '📄';
        if (['zip', 'tar', 'gz'].includes(ext ?? ''))
            return '🗜';
        return '📃';
    };
    return ((0, jsx_runtime_1.jsx)(AppShell_1.AppShell, { title: payload.title ?? 'Files', children: (0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', flexDirection: 'column', gap: 4 }, children: items.map((it, i) => {
                const name = String(it.title ?? `file-${i}`);
                return ((0, jsx_runtime_1.jsxs)("button", { type: "button", onClick: () => setSelected(name), style: {
                        display: 'flex',
                        alignItems: 'center',
                        gap: 10,
                        width: '100%',
                        textAlign: 'left',
                        padding: '9px 10px',
                        borderRadius: tokens_1.RADIUS.md,
                        border: `1px solid ${selected === name ? accent : 'transparent'}`,
                        background: selected === name ? `${accent}12` : 'transparent',
                        cursor: 'pointer',
                    }, children: [(0, jsx_runtime_1.jsx)("span", { style: { fontSize: 18 }, children: icon(name) }), (0, jsx_runtime_1.jsxs)("span", { style: { flex: 1, minWidth: 0 }, children: [(0, jsx_runtime_1.jsx)("span", { style: { display: 'block', fontSize: tokens_1.TYPE.label, color: theme.text, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }, children: name }), (0, jsx_runtime_1.jsx)("span", { style: { display: 'block', fontSize: tokens_1.TYPE.micro, color: theme.text3 }, children: String(it.meta ?? it.subtitle ?? '') })] })] }, i));
            }) }) }));
}
