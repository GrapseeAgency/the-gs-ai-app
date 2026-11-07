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
const GlassIcon_1 = require("@/components/nexly/GlassIcon");
const icons_1 = require("@/lib/nexly/icons");
function iconBadgeForCell(value) {
    const clean = (0, icons_1.cleanPlainText)(value);
    if (!clean || /^[\d#$€£%+↑↓.\-\s]+$/.test(clean) || clean.length > 32)
        return null;
    const lower = clean.toLowerCase();
    if (lower.startsWith('python'))
        return { icon: 'brand-python', tone: 'blue' };
    if (lower === 'java')
        return { icon: 'coffee', tone: 'amber' };
    if (lower.startsWith('c++') || lower === 'cpp')
        return { icon: 'brand-cpp', tone: 'blue' };
    if (lower === 'c' || lower === 'c#')
        return { icon: 'code', tone: 'lavender' };
    if (lower.startsWith('javascript') || lower === 'js')
        return { icon: 'brand-javascript', tone: 'amber' };
    if (lower.startsWith('typescript') || lower === 'ts')
        return { icon: 'brand-typescript', tone: 'blue' };
    if (lower.startsWith('rust'))
        return { icon: 'brand-rust', tone: 'peach' };
    if (lower === 'go' || lower === 'golang')
        return { icon: 'brand-golang', tone: 'mint' };
    if (lower.startsWith('swift'))
        return { icon: 'brand-apple', tone: 'lavender' };
    if (lower.startsWith('kotlin') || lower.startsWith('android'))
        return { icon: 'brand-android', tone: 'green' };
    return (0, icons_1.semanticIconFor)(value, 'sparkles');
}
function parseNumericCellWeb(raw) {
    const str = String(raw ?? '').trim();
    if (!str)
        return null;
    const m = str.match(/^[↑↓↗↘+-]?\s*[$€£¥₹৳]?\s*([+-]?\d+(?:,\d{3})*(?:\.\d+)?|\d+(?:\.\d+)?)\s*([kKmMbBtT%])?$/);
    if (!m)
        return null;
    const base = Number(m[1].replace(/,/g, ''));
    if (!Number.isFinite(base))
        return null;
    const suffix = (m[2] ?? '').toLowerCase();
    const mult = suffix === 'k' ? 1e3 : suffix === 'm' ? 1e6 : suffix === 'b' ? 1e9 : suffix === 't' ? 1e12 : 1;
    const sign = (str.startsWith('-') || str.startsWith('↓') || str.startsWith('↘')) && base > 0 ? -1 : 1;
    return base * mult * sign;
}
/** Sortable data grid with ranking badges, trend coloring, pagination, and Key Insights (Screenshot 5). */
function TableWidget({ payload }) {
    const { theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const [sort, setSort] = (0, react_1.useState)(null);
    const [query, setQuery] = (0, react_1.useState)('');
    const [pageSize, setPageSize] = (0, react_1.useState)(5);
    const [page, setPage] = (0, react_1.useState)(0);
    const numericCols = (0, react_1.useMemo)(() => {
        const cols = new Set();
        payload.columns.forEach((_, ci) => {
            const values = payload.rows.map((r) => r[ci]).filter(Boolean);
            if (values.length > 0 && values.every((v) => parseNumericCellWeb(v) !== null))
                cols.add(ci);
        });
        return cols;
    }, [payload.columns, payload.rows]);
    const primaryNameCol = (0, react_1.useMemo)(() => {
        const idx = payload.columns.findIndex((c) => /language|product|name|model|framework|tool|company|item/i.test(c));
        if (idx >= 0)
            return idx;
        return payload.columns[0]?.trim() === '#' || /rank/i.test(payload.columns[0] ?? '') ? 1 : 0;
    }, [payload.columns]);
    const rows = (0, react_1.useMemo)(() => {
        const q = query.trim().toLowerCase();
        const filtered = q
            ? payload.rows.filter((r) => r.some((cell) => String(cell ?? '').toLowerCase().includes(q)))
            : payload.rows;
        if (!sort)
            return filtered;
        const sorted = [...filtered];
        sorted.sort((a, b) => {
            const av = a[sort.col] ?? '';
            const bv = b[sort.col] ?? '';
            const an = parseNumericCellWeb(av);
            const bn = parseNumericCellWeb(bv);
            if (an !== null && bn !== null)
                return (an - bn) * sort.dir;
            return String(av).localeCompare(String(bv)) * sort.dir;
        });
        return sorted;
    }, [payload.rows, sort, query]);
    if (!payload.columns.length || !payload.rows.length)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "No rows" });
    const totalPages = Math.max(1, Math.ceil(rows.length / pageSize));
    const safePage = Math.min(page, totalPages - 1);
    const visibleRows = rows.slice(safePage * pageSize, safePage * pageSize + pageSize);
    const rawPayload = payload;
    const explicitInsights = Array.isArray(rawPayload.insights)
        ? rawPayload.insights.map((x) => String(x))
        : typeof rawPayload.insight === 'string'
            ? [rawPayload.insight]
            : typeof rawPayload.notes === 'string'
                ? [rawPayload.notes]
                : [];
    const renderCellContent = (rawVal, ci) => {
        const text = String(rawVal ?? '').trim();
        const trendMatch = text.match(/^([↑↓↗↘+-])?\s*([+-]\d+(?:\.\d+)?%|[↑↓↗↘]\s*\d+(?:\.\d+)?%)$/);
        if (trendMatch || (/trend|change|growth|delta|yoy/i.test(payload.columns[ci] ?? '') && /^[↑↓↗↘+-]?\s*\d+(?:\.\d+)?%$/.test(text))) {
            const isNeg = text.includes('-') || text.startsWith('↓') || text.startsWith('↘');
            const cleanNum = text.replace(/^[↑↓↗↘\s]+/, '');
            const formatted = cleanNum.startsWith('+') || cleanNum.startsWith('-') ? cleanNum : `${isNeg ? '-' : '+'}${cleanNum}`;
            return ((0, jsx_runtime_1.jsxs)("span", { style: {
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 3,
                    fontWeight: 700,
                    color: isNeg ? '#E04F5F' : '#129A5A',
                }, children: [(0, jsx_runtime_1.jsx)("span", { children: isNeg ? '↓' : '↑' }), (0, jsx_runtime_1.jsx)("span", { children: formatted })] }));
        }
        if (ci === primaryNameCol) {
            const badge = iconBadgeForCell(text);
            if (badge) {
                return ((0, jsx_runtime_1.jsxs)("span", { style: { display: 'inline-flex', alignItems: 'center', gap: 8 }, children: [(0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { name: badge.icon, tone: badge.tone, size: 22 }), (0, jsx_runtime_1.jsx)("span", { style: { fontWeight: 600, color: theme.text }, children: (0, icons_1.cleanPlainText)(text) })] }));
            }
        }
        return text;
    };
    return ((0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', flexDirection: 'column', gap: 10 }, children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title }), payload.rows.length >= 4 ? ((0, jsx_runtime_1.jsx)("div", { children: (0, jsx_runtime_1.jsx)("input", { type: "search", value: query, onChange: (e) => {
                        setQuery(e.target.value);
                        setPage(0);
                    }, placeholder: "Filter rows\u2026", style: {
                        width: '100%',
                        boxSizing: 'border-box',
                        padding: '6px 10px',
                        borderRadius: 8,
                        border: `1px solid ${theme.border}`,
                        background: theme.raised,
                        color: theme.text,
                        fontSize: tokens_1.TYPE.micro,
                        outline: 'none',
                    } }) })) : null, (0, jsx_runtime_1.jsxs)("div", { style: { overflowX: 'auto', borderRadius: 12, border: `1px solid ${theme.border}` }, children: [(0, jsx_runtime_1.jsxs)("table", { style: { width: '100%', borderCollapse: 'collapse', fontSize: tokens_1.TYPE.label }, children: [(0, jsx_runtime_1.jsx)("thead", { children: (0, jsx_runtime_1.jsx)("tr", { children: payload.columns.map((col, ci) => ((0, jsx_runtime_1.jsxs)("th", { onClick: payload.sortable === false
                                            ? undefined
                                            : () => setSort((s) => (s?.col === ci ? { col: ci, dir: s.dir === 1 ? -1 : 1 } : { col: ci, dir: 1 })), style: {
                                            textAlign: numericCols.has(ci) && ci > 0 ? 'right' : 'left',
                                            padding: '9px 11px',
                                            background: 'rgba(124, 92, 252, 0.12)',
                                            color: theme.text,
                                            fontWeight: 700,
                                            fontSize: tokens_1.TYPE.micro,
                                            letterSpacing: '0.02em',
                                            cursor: payload.sortable === false ? 'default' : 'pointer',
                                            whiteSpace: 'nowrap',
                                            borderBottom: `1px solid ${theme.border}`,
                                        }, children: [col, sort?.col === ci ? (sort.dir === 1 ? ' ↑' : ' ↓') : ''] }, `${col}-${ci}`))) }) }), (0, jsx_runtime_1.jsx)("tbody", { children: visibleRows.map((row, ri) => ((0, jsx_runtime_1.jsx)("tr", { style: { background: ri % 2 === 1 ? (0, tokens_1.alpha)(theme.dark ? '#FFFFFF' : '#14171C', 0.025) : 'transparent' }, children: payload.columns.map((_, ci) => ((0, jsx_runtime_1.jsx)("td", { style: {
                                            padding: '8px 11px',
                                            textAlign: numericCols.has(ci) && ci > 0 ? 'right' : 'left',
                                            color: ci === 0 ? theme.text : theme.text2,
                                            fontWeight: ci === 0 ? 600 : 400,
                                            fontVariantNumeric: numericCols.has(ci) ? 'tabular-nums' : undefined,
                                            borderBottom: `1px solid ${(0, tokens_1.alpha)(theme.dark ? '#FFFFFF' : '#14171C', 0.05)}`,
                                            whiteSpace: 'nowrap',
                                        }, children: renderCellContent(row[ci], ci) }, ci))) }, ri))) })] }), (0, jsx_runtime_1.jsxs)("div", { style: {
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'space-between',
                            padding: '8px 11px',
                            borderTop: `1px solid ${theme.border}`,
                            background: theme.raised,
                            fontSize: 11.5,
                            color: theme.text2,
                        }, children: [(0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', alignItems: 'center', gap: 4 }, children: [(0, jsx_runtime_1.jsx)("button", { type: "button", onClick: () => setPage(Math.max(0, safePage - 1)), disabled: safePage === 0, style: {
                                            border: 'none',
                                            background: 'transparent',
                                            color: theme.text2,
                                            cursor: safePage === 0 ? 'default' : 'pointer',
                                            opacity: safePage === 0 ? 0.4 : 1,
                                            padding: '2px 6px',
                                        }, children: "\u2039" }), Array.from({ length: totalPages }, (_, p) => ((0, jsx_runtime_1.jsx)("button", { type: "button", onClick: () => setPage(p), style: {
                                            width: 24,
                                            height: 24,
                                            borderRadius: 12,
                                            border: 'none',
                                            background: p === safePage ? '#6C4CF6' : 'transparent',
                                            color: p === safePage ? '#FFFFFF' : theme.text2,
                                            fontWeight: 700,
                                            fontSize: 11.5,
                                            cursor: 'pointer',
                                        }, children: p + 1 }, p))), (0, jsx_runtime_1.jsx)("button", { type: "button", onClick: () => setPage(Math.min(totalPages - 1, safePage + 1)), disabled: safePage >= totalPages - 1, style: {
                                            border: 'none',
                                            background: 'transparent',
                                            color: theme.text2,
                                            cursor: safePage >= totalPages - 1 ? 'default' : 'pointer',
                                            opacity: safePage >= totalPages - 1 ? 0.4 : 1,
                                            padding: '2px 6px',
                                        }, children: "\u203A" }), (0, jsx_runtime_1.jsxs)("span", { style: { fontSize: 11, color: theme.text2, marginLeft: 4 }, children: [safePage * pageSize + 1, "\u2013", Math.min(rows.length, (safePage + 1) * pageSize), " of ", rows.length] })] }), (0, jsx_runtime_1.jsxs)("button", { type: "button", onClick: () => {
                                    const next = pageSize === 3 ? 5 : pageSize === 5 ? 10 : pageSize === 10 ? 25 : 3;
                                    setPageSize(next);
                                    setPage(0);
                                }, style: {
                                    border: `1px solid ${theme.border}`,
                                    background: 'rgba(124, 92, 252, 0.10)',
                                    color: theme.text,
                                    borderRadius: 8,
                                    padding: '3px 9px',
                                    fontSize: 11.5,
                                    fontWeight: 600,
                                    cursor: 'pointer',
                                }, children: ["Show: ", pageSize >= rows.length ? 'All' : pageSize, " \u25BE"] })] })] }), (() => {
                const computedInsights = explicitInsights.length > 0
                    ? explicitInsights
                    : rows.length > 0
                        ? [
                            `${String(rows[0]?.[primaryNameCol] ?? rows[0]?.[0] ?? 'Top entry')} leads across ${rows.length} compared rows.`,
                            ...(rows.length >= 2
                                ? [`Runner-up: ${String(rows[1]?.[primaryNameCol] ?? rows[1]?.[0] ?? 'Second entry')}.`]
                                : []),
                        ]
                        : [];
                if (computedInsights.length === 0)
                    return null;
                return ((0, jsx_runtime_1.jsxs)("div", { style: {
                        padding: '11px 13px',
                        borderRadius: 12,
                        background: 'rgba(124, 92, 252, 0.10)',
                        border: '1px solid rgba(124, 92, 252, 0.24)',
                        fontSize: 12.5,
                        color: theme.text,
                    }, children: [(0, jsx_runtime_1.jsxs)("div", { style: { fontWeight: 700, marginBottom: 4, display: 'flex', alignItems: 'center', gap: 6 }, children: [(0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { name: "bulb", tone: "lavender", size: 20, glyph: 11 }), (0, jsx_runtime_1.jsx)("span", { children: "Key Insights" })] }), (0, jsx_runtime_1.jsx)("ul", { style: { margin: 0, paddingLeft: 18, display: 'flex', flexDirection: 'column', gap: 3 }, children: computedInsights.map((ins, idx) => ((0, jsx_runtime_1.jsx)("li", { style: { color: theme.text2 }, children: ins }, idx))) })] }));
            })()] }));
}
