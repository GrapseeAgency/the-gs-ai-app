"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.DiffWidget = DiffWidget;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const TitleRow_1 = require("../primitives/TitleRow");
const EmptyState_1 = require("../primitives/EmptyState");
const CopyChip_1 = require("../primitives/CopyChip");
/** LCS line diff — the real red/green view of a before/after pair. */
function diffLines(before, after) {
    const n = before.length;
    const m = after.length;
    const lcs = Array.from({ length: n + 1 }, () => new Array(m + 1).fill(0));
    for (let i = n - 1; i >= 0; i--)
        for (let j = m - 1; j >= 0; j--)
            lcs[i][j] = before[i] === after[j] ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
    const rows = [];
    let i = 0;
    let j = 0;
    while (i < n && j < m) {
        if (before[i] === after[j]) {
            rows.push({ kind: 'same', text: before[i] });
            i++;
            j++;
        }
        else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
            rows.push({ kind: 'del', text: before[i++] });
        }
        else {
            rows.push({ kind: 'add', text: after[j++] });
        }
    }
    while (i < n)
        rows.push({ kind: 'del', text: before[i++] });
    while (j < m)
        rows.push({ kind: 'add', text: after[j++] });
    return rows;
}
function parseUnified(unified) {
    return unified
        .split('\n')
        .filter((l) => !l.startsWith('---') && !l.startsWith('+++') && !l.startsWith('@@'))
        .map((l) => (l.startsWith('+') ? { kind: 'add', text: l.slice(1) } : l.startsWith('-') ? { kind: 'del', text: l.slice(1) } : { kind: 'same', text: l.replace(/^ /, '') }));
}
function DiffWidget({ payload }) {
    const { theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const [sideBySide] = (0, react_1.useState)(false);
    const rows = (0, react_1.useMemo)(() => {
        if (payload.unified)
            return parseUnified(payload.unified);
        return diffLines((payload.before ?? '').split('\n'), (payload.after ?? '').split('\n'));
    }, [payload.unified, payload.before, payload.after]);
    if (!rows.length)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "No changes" });
    const adds = rows.filter((r) => r.kind === 'add').length;
    const dels = rows.filter((r) => r.kind === 'del').length;
    const ADD = { bg: (0, tokens_1.alpha)('#0CA678', 0.13), fg: '#0CA678' };
    const DEL = { bg: (0, tokens_1.alpha)('#E03131', 0.11), fg: '#E03131' };
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title, right: `+${adds} −${dels}`, style: undefined }), (0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', justifyContent: 'flex-end', marginBottom: 8 }, children: (0, jsx_runtime_1.jsx)(CopyChip_1.CopyChip, { value: rows.map((r) => (r.kind === 'add' ? '+ ' : r.kind === 'del' ? '- ' : '  ') + r.text).join('\n') }) }), (0, jsx_runtime_1.jsx)("div", { style: {
                    borderRadius: tokens_1.RADIUS.md,
                    border: `1px solid ${theme.border}`,
                    background: theme.dark ? 'rgba(0,0,0,0.3)' : '#fff',
                    overflow: 'hidden',
                    maxHeight: 420,
                    overflowY: 'auto',
                }, children: rows.slice(0, 200).map((row, i) => {
                    const tone = row.kind === 'add' ? ADD : row.kind === 'del' ? DEL : null;
                    return ((0, jsx_runtime_1.jsxs)("div", { style: {
                            display: 'flex',
                            background: tone?.bg ?? 'transparent',
                            borderLeft: `2.5px solid ${tone ? tone.fg : 'transparent'}`,
                        }, children: [(0, jsx_runtime_1.jsx)("span", { style: { width: 34, flexShrink: 0, textAlign: 'right', paddingRight: 10, fontSize: tokens_1.TYPE.micro, color: theme.text3, lineHeight: '20px', userSelect: 'none', fontVariantNumeric: 'tabular-nums' }, children: row.kind === 'same' ? i + 1 : '' }), (0, jsx_runtime_1.jsxs)("code", { style: { whiteSpace: 'pre-wrap', fontSize: tokens_1.TYPE.micro, lineHeight: '20px', color: row.kind === 'same' ? theme.text2 : tone?.fg, fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace' }, children: [row.kind === 'add' ? '+ ' : row.kind === 'del' ? '− ' : '  ', row.text] })] }, i));
                }) })] }));
}
