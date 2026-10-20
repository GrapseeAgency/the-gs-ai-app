"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.EditorWidget = EditorWidget;
const jsx_runtime_1 = require("react/jsx-runtime");
/**
 * The editor kind: seven in-chat application editors with live evaluation.
 * sheet (formula engine) · nodes (visual programming) · pivot · site ·
 * css (layout playground) · video (clip timeline) · audio (waveform/spectrogram)
 */
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const TitleRow_1 = require("../primitives/TitleRow");
const UiButton_1 = require("../controls/UiButton");
const UiSegmented_1 = require("../controls/UiSegmented");
const UiSelect_1 = require("../controls/UiSelect");
function EditorWidget({ payload }) {
    switch (payload.editor) {
        case 'nodes': return (0, jsx_runtime_1.jsx)(NodesEditor, { payload: payload });
        case 'pivot': return (0, jsx_runtime_1.jsx)(PivotEditor, { payload: payload });
        case 'site': return (0, jsx_runtime_1.jsx)(SiteEditor, { payload: payload });
        case 'css': return (0, jsx_runtime_1.jsx)(CssEditor, { payload: payload });
        case 'video': return (0, jsx_runtime_1.jsx)(VideoEditor, { payload: payload });
        case 'audio': return (0, jsx_runtime_1.jsx)(AudioEditor, { payload: payload });
        case 'sheet':
        default: return (0, jsx_runtime_1.jsx)(SheetEditor, { payload: payload });
    }
}
// ------------------------------------------------------------ formula engine
/**
 * A real little spreadsheet engine: A1 refs, ranges, SUM/AVG/MIN/MAX/COUNT,
 * arithmetic — evaluated to fixed point with cycle protection.
 */
const ROWS = 12;
const COLS = 6;
const colName = (i) => String.fromCharCode(65 + i);
function evalCell(raw, get, seen = new Set()) {
    const v = raw.trim();
    if (!v)
        return null;
    if (!v.startsWith('=')) {
        const n = Number(v.replace(/,/g, ''));
        return Number.isFinite(n) && v !== '' ? n : null;
    }
    if (seen.has(v))
        return null; // cycle
    seen.add(v);
    let expr = v.slice(1);
    // functions over ranges: SUM/AVG/MIN/MAX/COUNT(A1:B4)
    expr = expr.replace(/\b(SUM|AVG|MIN|MAX|COUNT)\(([A-L]\d{1,2}):([A-L]\d{1,2})\)/gi, (_m, fn, a, b) => {
        const vals = rangeValues(a, b, get, seen);
        if (!vals.length)
            return '0';
        switch (fn.toUpperCase()) {
            case 'SUM': return String(vals.reduce((x, y) => x + y, 0));
            case 'AVG': return String(vals.reduce((x, y) => x + y, 0) / vals.length);
            case 'MIN': return String(Math.min(...vals));
            case 'MAX': return String(Math.max(...vals));
            case 'COUNT': return String(vals.length);
            default: return '0';
        }
    });
    // single-cell refs
    expr = expr.replace(/\b([A-L])(\d{1,2})\b/g, (_m, c, r) => {
        const ref = `${c}${r}`;
        const val = evalCell(get(ref), get, seen);
        return String(val ?? 0);
    });
    seen.delete(v);
    // only a safe arithmetic expression may remain
    if (!/^[-+*/%().\d\s^]*$/.test(expr))
        return null;
    try {
        const out = Function(`"use strict";return (${expr.replace(/\^/g, '**') || '0'})`)();
        return typeof out === 'number' && Number.isFinite(out) ? out : null;
    }
    catch {
        return null;
    }
}
function rangeValues(a, b, get, seen) {
    const c1 = a.charCodeAt(0) - 65;
    const c2 = b.charCodeAt(0) - 65;
    const r1 = Number(a.slice(1));
    const r2 = Number(b.slice(1));
    const out = [];
    for (let c = Math.min(c1, c2); c <= Math.max(c1, c2); c++)
        for (let r = Math.min(r1, r2); r <= Math.max(r1, r2); r++) {
            const v = evalCell(get(`${colName(c)}${r}`), get, seen);
            if (v !== null)
                out.push(v);
        }
    return out;
}
function SheetEditor({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const [cells, setCells] = (0, react_1.useState)(() => {
        const seed = { B1: '120', B2: '180', B3: '95', B4: '=SUM(B1:B3)', B5: '=AVG(B1:B3)', C1: '=B4*1.2' };
        // apply model-seeded cells on top
        if (payload.cells && typeof payload.cells === 'object')
            Object.assign(seed, payload.cells);
        return seed;
    });
    const [editing, setEditing] = (0, react_1.useState)(null);
    const get = (ref) => cells[ref] ?? '';
    const display = (ref) => {
        const raw = get(ref);
        if (!raw)
            return '';
        const v = evalCell(raw, get);
        return v === null ? (raw.startsWith('=') ? '#ERR' : raw) : String(Math.round(v * 100) / 100);
    };
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title ?? 'Spreadsheet', right: "=SUM(B1:B3) \u00B7 =AVG \u00B7 =A1*B2" }), (0, jsx_runtime_1.jsx)("div", { style: { overflowX: 'auto', borderRadius: tokens_1.RADIUS.md, border: `1px solid ${theme.border}` }, children: (0, jsx_runtime_1.jsxs)("table", { style: { borderCollapse: 'collapse', width: '100%', fontSize: tokens_1.TYPE.micro }, children: [(0, jsx_runtime_1.jsx)("thead", { children: (0, jsx_runtime_1.jsxs)("tr", { children: [(0, jsx_runtime_1.jsx)("th", { style: { width: 34, background: theme.raised, color: theme.text3, padding: '5px', borderBottom: `1px solid ${theme.border}` } }), Array.from({ length: COLS }, (_, c) => ((0, jsx_runtime_1.jsx)("th", { style: { background: theme.raised, color: theme.text2, padding: '5px 8px', borderBottom: `1px solid ${theme.border}`, textAlign: 'left' }, children: colName(c) }, c)))] }) }), (0, jsx_runtime_1.jsx)("tbody", { children: Array.from({ length: ROWS }, (_, r) => ((0, jsx_runtime_1.jsxs)("tr", { children: [(0, jsx_runtime_1.jsx)("td", { style: { background: theme.raised, color: theme.text3, textAlign: 'center', padding: '4px', borderBottom: `1px solid ${theme.border}` }, children: r + 1 }), Array.from({ length: COLS }, (_, c) => {
                                        const ref = `${colName(c)}${r + 1}`;
                                        const isNum = Number.isFinite(Number(display(ref))) && display(ref) !== '';
                                        return ((0, jsx_runtime_1.jsx)("td", { onClick: () => setEditing(ref), style: {
                                                borderBottom: `1px solid ${theme.border}`,
                                                borderRight: `1px solid ${theme.border}`,
                                                padding: 0,
                                                minWidth: 64,
                                                background: editing === ref ? (0, tokens_1.alpha)(accent, 0.08) : 'transparent',
                                            }, children: editing === ref ? ((0, jsx_runtime_1.jsx)("input", { autoFocus: true, value: get(ref), onChange: (e) => setCells((s) => ({ ...s, [ref]: e.target.value })), onBlur: () => setEditing(null), onKeyDown: (e) => e.key === 'Enter' && setEditing(null), style: { width: '100%', border: 'none', background: 'transparent', color: accent, outline: 'none', padding: '6px 8px', fontSize: tokens_1.TYPE.micro, caretColor: accent } })) : ((0, jsx_runtime_1.jsx)("span", { style: { display: 'block', padding: '6px 8px', textAlign: isNum ? 'right' : 'left', color: get(ref)?.startsWith('=') ? accent : theme.text, fontVariantNumeric: 'tabular-nums', fontWeight: get(ref)?.startsWith('=') ? 700 : 400, whiteSpace: 'nowrap' }, children: display(ref) })) }, c));
                                    })] }, r))) })] }) }), (0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.micro, color: theme.text3, marginTop: 6 }, children: "Tap a cell to edit \u00B7 formulas start with =" })] }));
}
// -------------------------------------------------------- visual programming
function NodesEditor({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const [nodes, setNodes] = (0, react_1.useState)(() => {
        const seeded = (payload.nodes ?? []).map((n, i) => ({ id: n.id ?? i, label: n.label ?? `Node ${i + 1}`, kind: n.kind ?? (i === 0 ? 'input' : i === 1 ? 'input' : 'add'), x: n.x ?? 0.12 + i * 0.24, y: n.y ?? 0.4, value: n.value ?? (i === 0 ? 4 : i === 1 ? 7 : 0) }));
        return seeded.length ? seeded : [
            { id: 0, label: 'A', kind: 'input', x: 0.1, y: 0.3, value: 4 },
            { id: 1, label: 'B', kind: 'input', x: 0.1, y: 0.65, value: 7 },
            { id: 2, label: 'A + B', kind: 'add', x: 0.5, y: 0.45, value: 0 },
            { id: 3, label: 'Out', kind: 'output', x: 0.85, y: 0.45, value: 0 },
        ];
    });
    const [links, setLinks] = (0, react_1.useState)(() => {
        const seeded = (payload.edges ?? []).filter(([a, b]) => a < (payload.nodes?.length ?? 0) && b < (payload.nodes?.length ?? 0));
        return seeded.length ? seeded : [[0, 2], [1, 2], [2, 3]];
    });
    const [linkFrom, setLinkFrom] = (0, react_1.useState)(null);
    const drag = (0, react_1.useRef)(null);
    const boxRef = (0, react_1.useRef)(null);
    // live evaluation: inputs flow through add nodes
    const values = (0, react_1.useMemo)(() => {
        const out = new Map();
        const resolve = (id, depth = 0) => {
            if (depth > 12)
                return 0;
            if (out.has(id))
                return out.get(id);
            const n = nodes.find((x) => x.id === id);
            if (!n)
                return 0;
            let v = 0;
            if (n.kind === 'input')
                v = n.value ?? 0;
            else {
                const ins = links.filter(([, b]) => b === id).map(([a]) => resolve(a, depth + 1));
                v = n.kind === 'multiply' ? ins.reduce((x, y) => x * y, ins.length ? 1 : 0) : ins.reduce((x, y) => x + y, 0);
            }
            out.set(id, v);
            return v;
        };
        nodes.forEach((n) => resolve(n.id));
        return out;
    }, [nodes, links]);
    const move = (e) => {
        if (drag.current === null || !boxRef.current)
            return;
        const r = boxRef.current.getBoundingClientRect();
        setNodes((ns) => ns.map((n) => (n.id === drag.current ? { ...n, x: Math.max(0.06, Math.min(0.94, (e.clientX - r.left) / r.width)), y: Math.max(0.1, Math.min(0.9, (e.clientY - r.top) / r.height)) } : n)));
    };
    const tap = (id) => {
        if (linkFrom === null)
            return setLinkFrom(id);
        if (linkFrom !== id)
            setLinks((ls) => [...ls, [linkFrom, id]]);
        setLinkFrom(null);
    };
    const addInput = () => {
        setNodes((ns) => [...ns, { id: Math.max(-1, ...ns.map((n) => n.id)) + 1, label: `In ${ns.length + 1}`, kind: 'input', x: 0.1, y: 0.2 + Math.random() * 0.5, value: 1 }]);
    };
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title ?? 'Node graph', right: `out = ${[...values.entries()].filter(([id]) => nodes.find((n) => n.id === id)?.kind === 'output').map(([, v]) => Math.round(v * 100) / 100).join(', ') || '—'}` }), (0, jsx_runtime_1.jsxs)("div", { ref: boxRef, style: { position: 'relative', height: 260, borderRadius: tokens_1.RADIUS.md, border: `1px solid ${theme.border}`, background: theme.dark ? 'rgba(0,0,0,0.3)' : 'rgba(0,0,0,0.03)', overflow: 'hidden', touchAction: 'none' }, onPointerMove: move, onPointerUp: () => (drag.current = null), children: [(0, jsx_runtime_1.jsx)("svg", { width: "100%", height: "100%", style: { position: 'absolute', inset: 0, pointerEvents: 'none' }, children: links.map(([a, b], i) => {
                            const na = nodes.find((n) => n.id === a);
                            const nb = nodes.find((n) => n.id === b);
                            if (!na || !nb)
                                return null;
                            const x1 = na.x * 100;
                            const y1 = na.y * 100;
                            const x2 = nb.x * 100;
                            const y2 = nb.y * 100;
                            return (0, jsx_runtime_1.jsx)("path", { d: `M ${x1}% ${y1}% C ${(x1 + x2) / 2}% ${y1}%, ${(x1 + x2) / 2}% ${y2}%, ${x2}% ${y2}%`, fill: "none", stroke: (0, tokens_1.alpha)(accent, 0.7), strokeWidth: 2.2 }, i);
                        }) }), nodes.map((n) => ((0, jsx_runtime_1.jsxs)("div", { style: {
                            position: 'absolute',
                            left: `${n.x * 100}%`,
                            top: `${n.y * 100}%`,
                            transform: 'translate(-50%,-50%)',
                            padding: '7px 11px',
                            borderRadius: 10,
                            background: n.kind === 'output' ? accent : theme.dark ? 'rgba(255,255,255,0.06)' : '#fff',
                            border: `1.5px solid ${linkFrom === n.id ? accent : (0, tokens_1.alpha)(accent, 0.5)}`,
                            color: n.kind === 'output' ? (theme.dark ? '#0F1216' : '#fff') : theme.text,
                            fontSize: tokens_1.TYPE.micro,
                            fontWeight: 700,
                            cursor: 'grab',
                            userSelect: 'none',
                            display: 'flex',
                            alignItems: 'center',
                            gap: 7,
                            boxShadow: '0 2px 8px rgba(0,0,0,0.18)',
                        }, onPointerDown: (e) => {
                            drag.current = n.id;
                            e.target.setPointerCapture?.(e.pointerId);
                        }, onClick: () => tap(n.id), children: [n.kind === 'input' ? ((0, jsx_runtime_1.jsx)("input", { type: "number", value: n.value ?? 0, onPointerDown: (e) => e.stopPropagation(), onChange: (e) => setNodes((ns) => ns.map((x) => (x.id === n.id ? { ...x, value: Number(e.target.value) } : x))), style: { width: 44, border: 'none', background: 'transparent', color: theme.text2, fontSize: tokens_1.TYPE.micro, outline: 'none', borderBottom: `1px solid ${(0, tokens_1.alpha)(accent, 0.5)}` } })) : null, (0, jsx_runtime_1.jsx)("span", { children: n.label }), n.kind !== 'input' && (0, jsx_runtime_1.jsx)("span", { style: { opacity: 0.8 }, children: Math.round((values.get(n.id) ?? 0) * 100) / 100 })] }, n.id)))] }), (0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', gap: 6, marginTop: 8 }, children: [(0, jsx_runtime_1.jsx)(UiButton_1.UiButton, { onClick: addInput, children: "+ input" }), (0, jsx_runtime_1.jsx)("span", { style: { alignSelf: 'center', fontSize: tokens_1.TYPE.micro, color: theme.text3 }, children: "tap two nodes to wire \u00B7 numbers flow live" })] })] }));
}
// --------------------------------------------------------------------- pivot
function PivotEditor({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const header = (payload.rows ?? [])[0] ?? ['Region', 'Product', 'Revenue'];
    const data = (payload.rows ?? []).slice(1);
    const [dim, setDim] = (0, react_1.useState)(0);
    const [measure, setMeasure] = (0, react_1.useState)('sum');
    const measureCol = Math.max(0, header.length - 1);
    const table = (0, react_1.useMemo)(() => {
        const groups = new Map();
        for (const row of data) {
            const key = String(row[dim] ?? '—');
            const val = Number(String(row[measureCol] ?? '').replace(/[^\d.-]/g, '')) || 0;
            groups.set(key, [...(groups.get(key) ?? []), val]);
        }
        return [...groups.entries()]
            .map(([key, vals]) => ({
            key,
            n: vals.length,
            sum: vals.reduce((a, b) => a + b, 0),
            avg: vals.reduce((a, b) => a + b, 0) / (vals.length || 1),
        }))
            .sort((a, b) => b.sum - a.sum);
    }, [data, dim, measureCol]);
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title ?? 'Pivot builder' }), (0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 10 }, children: [(0, jsx_runtime_1.jsx)(UiSelect_1.UiSelect, { ariaLabel: "group by", value: `by ${header[dim]}`, options: [...header.keys()].map((i) => `by ${header[i]}`), onChange: (v) => setDim(header.indexOf(v.replace('by ', ''))) }), (0, jsx_runtime_1.jsx)(UiSegmented_1.UiSegmented, { options: ['sum', 'avg', 'count'], value: measure, onChange: (m) => setMeasure(m) })] }), (0, jsx_runtime_1.jsxs)("table", { style: { width: '100%', borderCollapse: 'collapse', fontSize: tokens_1.TYPE.label }, children: [(0, jsx_runtime_1.jsx)("thead", { children: (0, jsx_runtime_1.jsxs)("tr", { children: [(0, jsx_runtime_1.jsx)("th", { style: { textAlign: 'left', padding: '6px 8px', color: theme.text2, borderBottom: `1px solid ${theme.border}` }, children: header[dim] }), (0, jsx_runtime_1.jsx)("th", { style: { textAlign: 'right', padding: '6px 8px', color: theme.text2, borderBottom: `1px solid ${theme.border}` }, children: measure }), (0, jsx_runtime_1.jsx)("th", { style: { textAlign: 'right', padding: '6px 8px', color: theme.text2, borderBottom: `1px solid ${theme.border}` }, children: "rows" })] }) }), (0, jsx_runtime_1.jsx)("tbody", { children: table.map((r) => ((0, jsx_runtime_1.jsxs)("tr", { children: [(0, jsx_runtime_1.jsx)("td", { style: { padding: '6px 8px', color: theme.text, borderBottom: `1px solid ${theme.border}` }, children: r.key }), (0, jsx_runtime_1.jsx)("td", { style: { padding: '6px 8px', textAlign: 'right', fontWeight: 700, color: accent, fontVariantNumeric: 'tabular-nums', borderBottom: `1px solid ${theme.border}` }, children: Math.round((measure === 'avg' ? r.avg : measure === 'count' ? r.n : r.sum) * 100) / 100 }), (0, jsx_runtime_1.jsx)("td", { style: { padding: '6px 8px', textAlign: 'right', color: theme.text3, borderBottom: `1px solid ${theme.border}` }, children: r.n })] }, r.key))) })] })] }));
}
// -------------------------------------------------------------- site builder
const BLOCK_KINDS = ['hero', 'text', 'image', 'button'];
function SiteEditor({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const [blocks, setBlocks] = (0, react_1.useState)(() => (payload.blocks ?? []).length
        ? (payload.blocks ?? []).map((b) => ({ ...b }))
        : [
            { type: 'hero', text: 'Launch day' },
            { type: 'text', text: 'Everything ships today.' },
            { type: 'image', text: 'banner' },
            { type: 'button', text: 'Get started' },
        ]);
    const [dragIdx, setDragIdx] = (0, react_1.useState)(null);
    const reorder = (to) => {
        if (dragIdx === null || dragIdx === to)
            return;
        setBlocks((bs) => {
            const next = [...bs];
            const [moved] = next.splice(dragIdx, 1);
            next.splice(to > dragIdx ? to - 1 : to, 0, moved);
            return next;
        });
        setDragIdx(null);
    };
    const render = (b) => {
        if (b.type === 'hero')
            return (0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.title, fontWeight: 800, padding: '14px', background: `linear-gradient(120deg, ${(0, tokens_1.alpha)(accent, 0.25)}, transparent)`, borderRadius: 10 }, children: b.text });
        if (b.type === 'button')
            return (0, jsx_runtime_1.jsx)("span", { style: { display: 'inline-block', padding: '8px 18px', borderRadius: 999, background: accent, color: theme.dark ? '#0F1216' : '#fff', fontWeight: 700, fontSize: tokens_1.TYPE.label }, children: b.text });
        if (b.type === 'image')
            return (0, jsx_runtime_1.jsxs)("div", { style: { height: 64, borderRadius: 10, background: `repeating-linear-gradient(45deg, ${(0, tokens_1.alpha)(accent, 0.12)} 0 10px, ${(0, tokens_1.alpha)(accent, 0.05)} 10px 20px)`, display: 'flex', alignItems: 'center', justifyContent: 'center', color: theme.text3, fontSize: tokens_1.TYPE.micro }, children: ["\uD83D\uDDBC ", b.text] });
        return (0, jsx_runtime_1.jsx)("p", { style: { margin: 0, color: theme.text2, fontSize: tokens_1.TYPE.body, lineHeight: 1.55 }, children: b.text });
    };
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title ?? 'Site builder', right: "drag rows to reorder" }), (0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', flexDirection: 'column', gap: 6 }, children: blocks.map((b, i) => ((0, jsx_runtime_1.jsxs)("div", { draggable: true, onDragStart: () => setDragIdx(i), onDragOver: (e) => e.preventDefault(), onDrop: () => reorder(i), style: { display: 'flex', alignItems: 'center', gap: 8, padding: '6px 8px', borderRadius: 10, border: `1px dashed ${theme.border}`, cursor: 'grab', background: theme.dark ? 'rgba(255,255,255,0.03)' : 'rgba(0,0,0,0.02)' }, children: [(0, jsx_runtime_1.jsx)("span", { style: { color: theme.text3, fontSize: 12 }, children: "\u283F" }), (0, jsx_runtime_1.jsx)("div", { style: { flex: 1, minWidth: 0 }, children: render(b) }), (0, jsx_runtime_1.jsx)("button", { type: "button", "aria-label": "remove block", onClick: () => setBlocks((bs) => bs.filter((_, ii) => ii !== i)), style: { border: 'none', background: 'transparent', color: theme.text3, cursor: 'pointer', fontSize: 14 }, children: "\u00D7" })] }, i))) }), (0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', gap: 6, marginTop: 10, flexWrap: 'wrap' }, children: BLOCK_KINDS.map((k) => ((0, jsx_runtime_1.jsxs)("button", { type: "button", onClick: () => setBlocks((bs) => [...bs, { type: k, text: k === 'image' ? 'image' : `${k} block` }]), style: { padding: '5px 12px', borderRadius: 999, border: `1px solid ${theme.border}`, background: 'transparent', color: theme.text2, fontSize: tokens_1.TYPE.micro, fontWeight: 600, cursor: 'pointer' }, children: ["+ ", k] }, k))) })] }));
}
// ---------------------------------------------------------------- css editor
function CssEditor({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const [layout, setLayout] = (0, react_1.useState)({
        direction: payload.layout?.direction ?? 'row',
        gap: payload.layout?.gap ?? 12,
        justify: payload.layout?.justify ?? 'flex-start',
        align: payload.layout?.align ?? 'stretch',
        wrap: payload.layout?.wrap ?? 'nowrap',
    });
    const css = `display: flex;\nflex-direction: ${layout.direction};\ngap: ${layout.gap}px;\njustify-content: ${layout.justify};\nalign-items: ${layout.align};\nflex-wrap: ${layout.wrap};`;
    const hues = ['#4C6FFF', '#0CA678', '#F08C00', '#D6336C', '#6741D9'];
    const [count, setCount] = (0, react_1.useState)(4);
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title ?? 'CSS layout editor' }), (0, jsx_runtime_1.jsxs)("div", { style: { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, marginBottom: 10 }, children: [(0, jsx_runtime_1.jsx)(UiSelect_1.UiSelect, { ariaLabel: "flex-direction", value: layout.direction, options: ['row', 'column'], onChange: (v) => setLayout((l) => ({ ...l, direction: v })) }), (0, jsx_runtime_1.jsx)(UiSelect_1.UiSelect, { ariaLabel: "justify-content", value: layout.justify, options: ['flex-start', 'center', 'flex-end', 'space-between', 'space-around'], onChange: (v) => setLayout((l) => ({ ...l, justify: v })) }), (0, jsx_runtime_1.jsx)(UiSelect_1.UiSelect, { ariaLabel: "align-items", value: layout.align, options: ['stretch', 'flex-start', 'center', 'flex-end'], onChange: (v) => setLayout((l) => ({ ...l, align: v })) }), (0, jsx_runtime_1.jsx)(UiSelect_1.UiSelect, { ariaLabel: "flex-wrap", value: layout.wrap, options: ['nowrap', 'wrap'], onChange: (v) => setLayout((l) => ({ ...l, wrap: v })) })] }), (0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', alignItems: 'center', gap: 10, marginBottom: 10 }, children: [(0, jsx_runtime_1.jsxs)("label", { style: { fontSize: tokens_1.TYPE.label, color: theme.text2 }, children: ["gap ", layout.gap, "px", (0, jsx_runtime_1.jsx)("input", { type: "range", min: 0, max: 40, value: layout.gap, onChange: (e) => setLayout((l) => ({ ...l, gap: Number(e.target.value) })), style: { marginLeft: 8, accentColor: accent, verticalAlign: 'middle' } })] }), (0, jsx_runtime_1.jsx)(UiSegmented_1.UiSegmented, { options: ['3', '4', '5'], value: String(count), onChange: (v) => setCount(Number(v)) })] }), (0, jsx_runtime_1.jsx)("div", { style: {
                    display: 'flex',
                    flexDirection: layout.direction,
                    gap: layout.gap,
                    justifyContent: layout.justify,
                    alignItems: layout.align,
                    flexWrap: layout.wrap,
                    height: layout.direction === 'row' ? 120 : 190,
                    borderRadius: tokens_1.RADIUS.md,
                    border: `1px solid ${theme.border}`,
                    padding: 10,
                    background: theme.dark ? 'rgba(0,0,0,0.28)' : 'rgba(0,0,0,0.03)',
                }, children: Array.from({ length: count }, (_, i) => ((0, jsx_runtime_1.jsx)("div", { style: { width: layout.direction === 'row' ? 52 : '100%', height: layout.direction === 'row' ? '100%' : 26, borderRadius: 8, background: hues[i % hues.length], opacity: 0.85 } }, i))) }), (0, jsx_runtime_1.jsx)("pre", { style: { margin: '10px 0 0', padding: 12, borderRadius: 10, background: theme.dark ? 'rgba(0,0,0,0.32)' : 'rgba(0,0,0,0.04)', color: theme.text2, fontSize: tokens_1.TYPE.micro, fontFamily: 'ui-monospace, Menlo, monospace', whiteSpace: 'pre-wrap' }, children: css })] }));
}
// ------------------------------------------------------------ video timeline
function VideoEditor({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const [tracks, setTracks] = (0, react_1.useState)(() => (payload.tracks ?? []).length
        ? (payload.tracks ?? []).map((t) => ({ name: t.name, clips: [...t.clips] }))
        : [
            { name: 'Video A', clips: [{ start: 0, dur: 8, label: 'Intro' }, { start: 10, dur: 12, label: 'Demo' }] },
            { name: 'Video B', clips: [{ start: 4, dur: 6, label: 'B-roll' }] },
            { name: 'Audio', clips: [{ start: 0, dur: 20, label: 'Voiceover' }] },
        ]);
    const duration = Math.max(10, Number(payload.duration ?? 30));
    const [playhead, setPlayhead] = (0, react_1.useState)(0);
    const [playing, setPlaying] = (0, react_1.useState)(false);
    const rafRef = (0, react_1.useRef)(0);
    const lastRef = (0, react_1.useRef)(0);
    (0, react_1.useEffect)(() => {
        if (!playing)
            return;
        lastRef.current = performance.now();
        const loop = (now) => {
            const dt = (now - lastRef.current) / 1000;
            lastRef.current = now;
            setPlayhead((p) => (p + dt) % duration);
            rafRef.current = requestAnimationFrame(loop);
        };
        rafRef.current = requestAnimationFrame(loop);
        return () => cancelAnimationFrame(rafRef.current);
    }, [playing, duration]);
    const addClip = (ti) => {
        setTracks((ts) => ts.map((t, i) => (i === ti ? { ...t, clips: [...t.clips, { start: Math.round(playhead), dur: 4, label: `Clip ${t.clips.length + 1}` }] } : t)));
    };
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title ?? 'Clip timeline', right: `${playhead.toFixed(1)}s / ${duration}s` }), (0, jsx_runtime_1.jsxs)("div", { style: { position: 'relative' }, children: [tracks.map((track, ti) => ((0, jsx_runtime_1.jsxs)("div", { style: { marginBottom: 8 }, children: [(0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.micro, color: theme.text3, marginBottom: 3 }, children: track.name }), (0, jsx_runtime_1.jsx)("div", { onClick: (e) => {
                                    const r = e.currentTarget.getBoundingClientRect();
                                    setPlayhead(((e.clientX - r.left) / r.width) * duration);
                                }, style: { position: 'relative', height: 34, borderRadius: 8, background: theme.dark ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.05)', cursor: 'pointer' }, children: track.clips.map((clip, ci) => ((0, jsx_runtime_1.jsx)("div", { title: `${clip.label} @ ${clip.start}s`, style: {
                                        position: 'absolute',
                                        left: `${(clip.start / duration) * 100}%`,
                                        width: `${Math.max(3, (clip.dur / duration) * 100)}%`,
                                        top: 4,
                                        bottom: 4,
                                        borderRadius: 6,
                                        background: (0, tokens_1.alpha)(accent, 0.35 + (ti % 2) * 0.12),
                                        border: `1px solid ${(0, tokens_1.alpha)(accent, 0.7)}`,
                                        display: 'flex',
                                        alignItems: 'center',
                                        paddingLeft: 6,
                                        fontSize: tokens_1.TYPE.micro,
                                        fontWeight: 700,
                                        color: theme.text,
                                        overflow: 'hidden',
                                        whiteSpace: 'nowrap',
                                    }, children: clip.label }, ci))) })] }, track.name))), (0, jsx_runtime_1.jsx)("span", { "aria-hidden": true, style: { position: 'absolute', top: 0, bottom: 0, left: `${(playhead / duration) * 100}%`, width: 2, background: '#E03131', pointerEvents: 'none' } })] }), (0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', gap: 6, marginTop: 8 }, children: [(0, jsx_runtime_1.jsx)(UiButton_1.UiButton, { buttonStyle: "filled", onClick: () => setPlaying((p) => !p), children: playing ? 'Pause' : 'Play' }), tracks.map((t, i) => ((0, jsx_runtime_1.jsxs)(UiButton_1.UiButton, { onClick: () => addClip(i), children: ["+ ", t.name.split(' ')[0]] }, t.name)))] })] }));
}
// -------------------------------------------------------------- audio editor
function AudioEditor({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const NOTES = ['C4', 'D4', 'E4', 'G4', 'A4', 'C5'];
    const FREQ = { C4: 261.6, D4: 293.7, E4: 329.6, G4: 392.0, A4: 440.0, C5: 523.3 };
    const [seq, setSeq] = (0, react_1.useState)(() => ['C4', 'E4', 'G4', 'E4', 'A4', 'G4', 'C5', 'G4']);
    const [view, setView] = (0, react_1.useState)('wave');
    const canvasRef = (0, react_1.useRef)(null);
    const audioRef = (0, react_1.useRef)(null);
    const buffer = (0, react_1.useMemo)(() => {
        const sr = 8000;
        const per = 0.14;
        const n = Math.floor(seq.length * per * sr);
        const data = new Float32Array(n);
        seq.forEach((note, i) => {
            const f = FREQ[note] ?? 440;
            const start = Math.floor(i * per * sr);
            for (let j = 0; j < per * sr && start + j < n; j++) {
                const t = j / sr;
                const env = Math.min(1, j / 80) * Math.exp(-t * 6);
                data[start + j] += 0.5 * env * Math.sin(2 * Math.PI * f * t);
            }
        });
        return data;
    }, [seq]);
    (0, react_1.useEffect)(() => {
        const canvas = canvasRef.current;
        if (!canvas)
            return;
        const ctx = canvas.getContext('2d');
        if (!ctx)
            return;
        const dpr = Math.min(2, window.devicePixelRatio || 1);
        const W = canvas.clientWidth;
        const H = 150;
        canvas.width = W * dpr;
        canvas.height = H * dpr;
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        ctx.clearRect(0, 0, W, H);
        if (view === 'wave') {
            ctx.strokeStyle = accent;
            ctx.lineWidth = 1.2;
            ctx.beginPath();
            for (let x = 0; x < W; x++) {
                const v = buffer[Math.floor((x / W) * buffer.length)] ?? 0;
                const y = H / 2 - v * (H / 2 - 8);
                if (x === 0) {
                    ctx.moveTo(x, y);
                }
                else {
                    ctx.lineTo(x, y);
                }
            }
            ctx.stroke();
        }
        else {
            // spectrogram: STFT magnitude -> hue column
            const win = 128;
            const hop = 16;
            for (let x = 0; x + win < buffer.length; x += hop) {
                for (let bin = 0; bin < 32; bin++) {
                    let re = 0;
                    for (let k = 0; k < win; k++)
                        re += buffer[x + k] * Math.cos((2 * Math.PI * bin * k) / win);
                    const mag = Math.abs(re) / win;
                    const a = Math.min(1, mag * 8);
                    ctx.fillStyle = `hsla(${270 - a * 240}, 85%, ${30 + a * 40}%, ${0.15 + a * 0.85})`;
                    const col = (x / hop) * ((W / Math.max(1, buffer.length / hop)));
                    const bh = H / 32;
                    ctx.fillRect(col, H - (bin + 1) * bh, Math.max(1.5, col + 2) - col, bh + 0.5);
                }
            }
        }
    }, [buffer, view, accent]);
    const play = () => {
        audioRef.current?.stop();
        const Ctor = window.AudioContext ?? window.webkitAudioContext;
        if (!Ctor)
            return;
        const ctx = new Ctor();
        const sr = 8000;
        const buf = ctx.createBuffer(1, buffer.length, sr);
        buf.copyToChannel(buffer, 0);
        const src = ctx.createBufferSource();
        src.buffer = buf;
        const gain = ctx.createGain();
        gain.gain.value = 0.5;
        src.connect(gain).connect(ctx.destination);
        src.start();
        audioRef.current = { ctx, stop: () => { try {
                src.stop();
                ctx.close();
            }
            catch { /* stopped */ } } };
    };
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title ?? 'Audio editor', right: view === 'wave' ? 'waveform' : 'spectrogram' }), (0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }, children: [(0, jsx_runtime_1.jsx)(UiSegmented_1.UiSegmented, { options: [{ label: 'Waveform', value: 'wave' }, { label: 'Spectrogram', value: 'spec' }], value: view, onChange: (v) => setView(v) }), (0, jsx_runtime_1.jsx)(UiButton_1.UiButton, { buttonStyle: "filled", onClick: play, children: "\u25B6 Play" })] }), (0, jsx_runtime_1.jsx)("canvas", { ref: canvasRef, style: { width: '100%', height: 150, display: 'block', borderRadius: tokens_1.RADIUS.md, border: `1px solid ${theme.border}`, background: theme.dark ? 'rgba(0,0,0,0.32)' : 'rgba(0,0,0,0.04)' } }), (0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', gap: 5, flexWrap: 'wrap', marginTop: 8 }, children: seq.map((note, i) => ((0, jsx_runtime_1.jsx)("button", { type: "button", onClick: () => setSeq((s) => s.map((x, ii) => (ii === i ? NOTES[(NOTES.indexOf(x) + 1) % NOTES.length] : x))), style: { padding: '6px 12px', borderRadius: 8, border: `1.5px solid ${(0, tokens_1.alpha)(accent, 0.5)}`, background: (0, tokens_1.alpha)(accent, 0.1), color: accent, fontSize: tokens_1.TYPE.micro, fontWeight: 700, cursor: 'pointer', fontVariantNumeric: 'tabular-nums' }, children: note }, i))) }), (0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.micro, color: theme.text3, marginTop: 6 }, children: "Tap a note to change it \u2014 waveform, spectrogram and playback re-render live (WebAudio)." })] }));
}
