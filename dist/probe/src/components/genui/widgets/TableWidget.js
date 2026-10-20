"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.TableWidget = TableWidget;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const TitleRow_1 = require("../primitives/TitleRow");
const EmptyState_1 = require("../primitives/EmptyState");
/** Sortable table — numbers right-aligned, header tappable to sort. */
function TableWidget({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const [sort, setSort] = (0, react_1.useState)(null);
    const numericCols = (0, react_1.useMemo)(() => {
        const cols = new Set();
        payload.columns.forEach((_, ci) => {
            const values = payload.rows.map((r) => r[ci]).filter(Boolean);
            if (values.length > 0 && values.every((v) => /^[$€£¥₹]?\s*-?[\d,.]+%?$/.test(String(v).trim())))
                cols.add(ci);
        });
        return cols;
    }, [payload.columns, payload.rows]);
    const rows = (0, react_1.useMemo)(() => {
        if (!sort)
            return payload.rows;
        const sorted = [...payload.rows];
        sorted.sort((a, b) => {
            const av = a[sort.col] ?? '';
            const bv = b[sort.col] ?? '';
            const an = Number(String(av).replace(/[^\d.-]/g, ''));
            const bn = Number(String(bv).replace(/[^\d.-]/g, ''));
            if (Number.isFinite(an) && Number.isFinite(bn) && String(av).trim() !== '' && String(bv).trim() !== '')
                return (an - bn) * sort.dir;
            return String(av).localeCompare(String(bv)) * sort.dir;
        });
        return sorted;
    }, [payload.rows, sort]);
    if (!payload.columns.length || !payload.rows.length)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "No rows" });
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title }), (0, jsx_runtime_1.jsx)("div", { style: { overflowX: 'auto', borderRadius: 10, border: `1px solid ${theme.border}` }, children: (0, jsx_runtime_1.jsxs)("table", { style: { width: '100%', borderCollapse: 'collapse', fontSize: tokens_1.TYPE.label }, children: [(0, jsx_runtime_1.jsx)("thead", { children: (0, jsx_runtime_1.jsx)("tr", { children: payload.columns.map((col, ci) => ((0, jsx_runtime_1.jsxs)("th", { onClick: payload.sortable === false
                                        ? undefined
                                        : () => setSort((s) => (s?.col === ci ? { col: ci, dir: s.dir === 1 ? -1 : 1 } : { col: ci, dir: 1 })), style: {
                                        textAlign: numericCols.has(ci) ? 'right' : 'left',
                                        padding: '8px 10px',
                                        background: theme.raised,
                                        color: theme.text2,
                                        fontWeight: 600,
                                        fontSize: tokens_1.TYPE.micro,
                                        textTransform: 'uppercase',
                                        letterSpacing: '0.03em',
                                        cursor: payload.sortable === false ? 'default' : 'pointer',
                                        whiteSpace: 'nowrap',
                                        borderBottom: `1px solid ${theme.border}`,
                                    }, children: [col, sort?.col === ci ? (sort.dir === 1 ? ' ↑' : ' ↓') : ''] }, `${col}-${ci}`))) }) }), (0, jsx_runtime_1.jsx)("tbody", { children: rows.map((row, ri) => ((0, jsx_runtime_1.jsx)("tr", { style: { background: ri % 2 === 1 ? (0, tokens_1.alpha)(theme.dark ? '#FFFFFF' : '#14171C', 0.025) : 'transparent' }, children: payload.columns.map((_, ci) => ((0, jsx_runtime_1.jsx)("td", { style: {
                                        padding: '7px 10px',
                                        textAlign: numericCols.has(ci) ? 'right' : 'left',
                                        color: ci === 0 ? theme.text : theme.text2,
                                        fontWeight: ci === 0 ? 600 : 400,
                                        fontVariantNumeric: numericCols.has(ci) ? 'tabular-nums' : undefined,
                                        borderBottom: `1px solid ${(0, tokens_1.alpha)(theme.dark ? '#FFFFFF' : '#14171C', 0.05)}`,
                                        whiteSpace: 'nowrap',
                                    }, children: row[ci] }, ci))) }, ri))) })] }) }), payload.sortable !== false && payload.rows.length > 1 && ((0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.micro, color: theme.text3, marginTop: 6 }, children: "Tap a column to sort" }))] }));
}
