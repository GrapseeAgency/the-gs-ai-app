"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.AppSheet = AppSheet;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const AppShell_1 = require("./AppShell");
const EmptyState_1 = require("../primitives/EmptyState");
/** Sheet — an editable grid with live numeric column sums. */
function AppSheet({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const header = (payload.rows ?? [])[0] ?? [];
    const dataRows = (payload.rows ?? []).slice(1);
    const [cells, setCells] = (0, react_1.useState)(() => dataRows.map((r) => [...r]));
    if (!header.length)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "No sheet data" });
    const setCell = (r, c, v) => setCells((cs) => cs.map((row, ri) => (ri === r ? row.map((cell, ci) => (ci === c ? v : cell)) : row)));
    const isNumeric = (c) => cells.every((row) => row[c] === undefined || /^-?[\d.,]+$/.test(String(row[c]).trim()) || String(row[c]).trim() === '');
    return ((0, jsx_runtime_1.jsx)(AppShell_1.AppShell, { title: payload.title, children: (0, jsx_runtime_1.jsx)("div", { style: { overflowX: 'auto', borderRadius: tokens_1.RADIUS.md, border: `1px solid ${theme.border}` }, children: (0, jsx_runtime_1.jsxs)("table", { style: { borderCollapse: 'collapse', width: '100%', fontSize: tokens_1.TYPE.micro }, children: [(0, jsx_runtime_1.jsx)("thead", { children: (0, jsx_runtime_1.jsx)("tr", { children: header.map((h, i) => ((0, jsx_runtime_1.jsx)("th", { style: { padding: '7px 9px', textAlign: 'left', background: theme.raised, color: theme.text2, borderBottom: `1px solid ${theme.border}`, whiteSpace: 'nowrap' }, children: h }, i))) }) }), (0, jsx_runtime_1.jsxs)("tbody", { children: [cells.map((row, ri) => ((0, jsx_runtime_1.jsx)("tr", { children: header.map((_, ci) => ((0, jsx_runtime_1.jsx)("td", { style: { borderBottom: `1px solid ${theme.dark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.04)'}`, padding: 0 }, children: (0, jsx_runtime_1.jsx)("input", { value: String(row[ci] ?? ''), onChange: (e) => setCell(ri, ci, e.target.value), style: {
                                            width: '100%',
                                            border: 'none',
                                            background: 'transparent',
                                            color: theme.text,
                                            fontSize: tokens_1.TYPE.micro,
                                            padding: '7px 9px',
                                            outline: 'none',
                                            textAlign: isNumeric(ci) ? 'right' : 'left',
                                            fontVariantNumeric: 'tabular-nums',
                                        } }) }, ci))) }, ri))), isNumericAny() && ((0, jsx_runtime_1.jsx)("tr", { children: header.map((_, ci) => {
                                    const numeric = cells.every((row) => row[ci] === undefined || /^-?[\d.,]+$/.test(String(row[ci]).trim()) || String(row[ci]).trim() === '');
                                    const sum = numeric ? cells.reduce((a, row) => a + (Number(String(row[ci]).replace(/,/g, '')) || 0), 0) : null;
                                    return ((0, jsx_runtime_1.jsx)("td", { style: { padding: '6px 9px', textAlign: 'right', fontWeight: 700, color: sum !== null ? accent : 'transparent', borderTop: `1.5px solid ${theme.border}`, fontVariantNumeric: 'tabular-nums' }, children: sum !== null ? sum.toLocaleString() : '·' }, ci));
                                }) }))] })] }) }) }));
    function isNumericAny() {
        return header.some((_, ci) => cells.some((row) => /^-?[\d.,]+$/.test(String(row[ci] ?? '').trim())));
    }
}
