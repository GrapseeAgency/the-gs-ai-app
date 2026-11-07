"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.CodeBlock = CodeBlock;
const jsx_runtime_1 = require("react/jsx-runtime");
/**
 * CodeBlock — full code experience: syntax highlighting, inline editing,
 * execution preview (JS/TS console runner, HTML/SVG sandboxed iframe, JSON
 * validator), and error diagnostics.
 */
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const CopyChip_1 = require("../primitives/CopyChip");
const GlassIcon_1 = require("@/components/nexly/GlassIcon");
const arithmetic_preview_1 = require("@/lib/code/arithmetic-preview");
const KEYWORD_RE = /\b(const|let|var|function|return|if|else|for|while|switch|case|break|continue|new|class|extends|import|from|export|default|async|await|try|catch|finally|throw|typeof|instanceof|in|of|def|elif|lambda|yield|with|as|pass|raise|True|False|None|fun|val|when|object|interface|struct|enum|guard|protocol|impl|pub|fn|match|mut|use|SELECT|FROM|WHERE|JOIN|LEFT|RIGHT|INNER|GROUP|BY|ORDER|INSERT|INTO|VALUES|UPDATE|SET|DELETE|CREATE|TABLE|LIMIT)\b/g;
const RUNNABLE_JS = new Set(['js', 'javascript', 'ts', 'typescript', 'jsx', 'tsx', 'node']);
const RUNNABLE_HTML = new Set(['html', 'htm', 'svg']);
const RUNNABLE_PY = new Set(['py', 'python', 'python3']);
const RUNNABLE_SQL = new Set(['sql', 'sqlite', 'postgres', 'postgresql', 'mysql']);
const RUNNABLE_SH = new Set(['sh', 'bash', 'zsh', 'shell']);
const ERROR_LANGS = new Set(['error', 'stderr', 'traceback', 'stacktrace', 'exception']);
function previewNonJsExecution(lang, source) {
    if (RUNNABLE_PY.has(lang))
        return (0, arithmetic_preview_1.arithmeticPreview)(source);
    if (RUNNABLE_SQL.has(lang))
        return { output: '', error: 'Not executed: no database is connected. Copy this SQL into your database client.' };
    return { output: '', error: `Not executed: no ${lang.toUpperCase()} runtime is available here.` };
}
function highlightLine(line, dark) {
    const commentIdx = (() => {
        const slashIdx = line.indexOf('//');
        if (slashIdx >= 0 && (slashIdx === 0 || line[slashIdx - 1] !== ':'))
            return slashIdx;
        const hashIdx = line.trimStart().startsWith('#') ? line.indexOf('#') : -1;
        return hashIdx;
    })();
    const codePart = commentIdx >= 0 ? line.slice(0, commentIdx) : line;
    const commentPart = commentIdx >= 0 ? line.slice(commentIdx) : '';
    const kwColor = dark ? '#7AB8FF' : '#2563EB';
    const strColor = dark ? '#6EE7B7' : '#047857';
    const numColor = dark ? '#FBBF24' : '#B45309';
    const cmtColor = dark ? '#6E7681' : '#8C959F';
    const tokens = [];
    const tokenRe = /("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|`(?:\\.|[^`\\])*`|\b\d+(?:\.\d+)?\b|\b[A-Za-z_]\w*\b)/g;
    let last = 0;
    let m;
    let k = 0;
    while ((m = tokenRe.exec(codePart)) !== null) {
        if (m.index > last)
            tokens.push(codePart.slice(last, m.index));
        const tok = m[0];
        if (tok.startsWith('"') || tok.startsWith("'") || tok.startsWith('`')) {
            tokens.push((0, jsx_runtime_1.jsx)("span", { style: { color: strColor }, children: tok }, `s-${k++}`));
        }
        else if (/^\d/.test(tok)) {
            tokens.push((0, jsx_runtime_1.jsx)("span", { style: { color: numColor }, children: tok }, `n-${k++}`));
        }
        else if (KEYWORD_RE.test(tok)) {
            KEYWORD_RE.lastIndex = 0;
            tokens.push((0, jsx_runtime_1.jsx)("span", { style: { color: kwColor, fontWeight: 600 }, children: tok }, `kw-${k++}`));
        }
        else {
            tokens.push(tok);
        }
        last = m.index + tok.length;
    }
    if (last < codePart.length)
        tokens.push(codePart.slice(last));
    if (commentPart) {
        tokens.push((0, jsx_runtime_1.jsx)("span", { style: { color: cmtColor, fontStyle: 'italic' }, children: commentPart }, "cmt"));
    }
    return tokens;
}
function CodeBlock({ code, language }) {
    const { accent } = (0, WidgetSurface_1.useWidgetIdentity)();
    const lang = (language ?? 'code').toLowerCase().trim();
    const [source, setSource] = (0, react_1.useState)(code);
    const [editing, setEditing] = (0, react_1.useState)(false);
    const [expanded, setExpanded] = (0, react_1.useState)(false);
    const [runCount, setRunCount] = (0, react_1.useState)(0);
    const [output, setOutput] = (0, react_1.useState)(null);
    const [errorText, setErrorText] = (0, react_1.useState)(ERROR_LANGS.has(lang) ? code : null);
    const [htmlPreview, setHtmlPreview] = (0, react_1.useState)(null);
    const canRun = RUNNABLE_JS.has(lang) ||
        RUNNABLE_HTML.has(lang) ||
        RUNNABLE_PY.has(lang) ||
        RUNNABLE_SQL.has(lang) ||
        RUNNABLE_SH.has(lang) ||
        lang === 'json';
    const allLines = (0, react_1.useMemo)(() => source.split('\n'), [source]);
    const isCollapsible = allLines.length > 14;
    const visibleLines = isCollapsible && !expanded ? allLines.slice(0, 12) : allLines;
    const highlighted = (0, react_1.useMemo)(() => {
        return visibleLines.map((line, idx) => ((0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', minHeight: '1.4em' }, children: [(0, jsx_runtime_1.jsx)("span", { "aria-hidden": true, style: {
                        width: 26,
                        paddingRight: 10,
                        textAlign: 'right',
                        color: '#6E6A94',
                        userSelect: 'none',
                        flexShrink: 0,
                        fontSize: 11,
                    }, children: idx + 1 }), (0, jsx_runtime_1.jsx)("span", { style: { flex: 1, minWidth: 0 }, children: highlightLine(line, true) })] }, idx)));
    }, [visibleLines]);
    const runCode = () => {
        setRunCount((c) => c + 1);
        setErrorText(null);
        setOutput(null);
        setHtmlPreview(null);
        if (RUNNABLE_HTML.has(lang)) {
            setHtmlPreview(source);
            setOutput('Live HTML/SVG preview opened in sandbox');
            return;
        }
        if (lang === 'json') {
            try {
                const parsed = JSON.parse(source);
                setOutput(JSON.stringify(parsed, null, 2));
            }
            catch (err) {
                setErrorText(err instanceof Error ? err.message : String(err));
            }
            return;
        }
        if (!RUNNABLE_JS.has(lang)) {
            const sim = previewNonJsExecution(lang, source);
            if (sim.error)
                setErrorText(sim.error);
            if (sim.output)
                setOutput(sim.output);
            return;
        }
        const logs = [];
        const fmt = (v) => {
            if (typeof v === 'string')
                return v;
            try {
                return JSON.stringify(v);
            }
            catch {
                return String(v);
            }
        };
        const mockConsole = {
            log: (...args) => logs.push(args.map(fmt).join(' ')),
            info: (...args) => logs.push(args.map(fmt).join(' ')),
            warn: (...args) => logs.push('[warn] ' + args.map(fmt).join(' ')),
            error: (...args) => logs.push('[error] ' + args.map(fmt).join(' ')),
        };
        try {
            const syncSetTimeout = (cb) => {
                if (typeof cb === 'function')
                    cb();
                return 1;
            };
            const fn = new Function('console', 'setTimeout', `"use strict";\n${source}`);
            const ret = fn(mockConsole, syncSetTimeout);
            if (ret !== undefined)
                logs.push(`→ ${fmt(ret)}`);
            setOutput(logs.length > 0 ? logs.join('\n') : 'Completed (no console output)');
        }
        catch (err) {
            if (logs.length > 0)
                setOutput(logs.join('\n'));
            setErrorText(err instanceof Error ? `${err.name}: ${err.message}` : String(err));
        }
    };
    const chipStyle = {
        fontSize: 11,
        fontWeight: 600,
        padding: '3px 9px',
        borderRadius: 7,
        border: '1px solid rgba(255,255,255,0.14)',
        background: 'rgba(255,255,255,0.08)',
        color: '#E4E0FF',
        cursor: 'pointer',
    };
    const isBrowserLang = RUNNABLE_HTML.has(lang) || lang === 'jsx' || lang === 'tsx';
    return ((0, jsx_runtime_1.jsxs)("div", { style: {
            borderRadius: 14,
            border: '1px solid #353059',
            background: '#221F3B',
            overflow: 'hidden',
            boxShadow: '0 8px 22px rgba(20, 16, 44, 0.22)',
        }, children: [(0, jsx_runtime_1.jsxs)("div", { style: {
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    padding: '8px 12px',
                    background: '#1B1830',
                    borderBottom: '1px solid rgba(255,255,255,0.08)',
                    gap: 6,
                }, children: [(0, jsx_runtime_1.jsxs)("span", { style: {
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: 6,
                            fontSize: tokens_1.TYPE.micro,
                            fontWeight: 700,
                            color: '#E5E0FF',
                            textTransform: 'uppercase',
                            letterSpacing: '0.05em',
                        }, children: [(0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { name: "code", tone: "lavender", size: 18, glyph: 11 }), (0, jsx_runtime_1.jsx)("span", { children: lang })] }), (0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', alignItems: 'center', gap: 6 }, children: [isCollapsible && !editing && ((0, jsx_runtime_1.jsx)("button", { type: "button", onClick: () => setExpanded((v) => !v), style: chipStyle, children: expanded ? 'Collapse' : `Expand (${allLines.length})` })), (0, jsx_runtime_1.jsx)("button", { type: "button", onClick: () => setEditing((v) => !v), style: chipStyle, children: editing ? 'Done' : 'Edit' }), source !== code ? ((0, jsx_runtime_1.jsx)("button", { type: "button", onClick: () => {
                                    setSource(code);
                                    setOutput(null);
                                    setErrorText(null);
                                    setHtmlPreview(null);
                                }, style: chipStyle, children: "Reset" })) : null, (0, jsx_runtime_1.jsx)(CopyChip_1.CopyChip, { value: source })] })] }), editing ? ((0, jsx_runtime_1.jsx)("textarea", { value: source, onChange: (e) => setSource(e.target.value), spellCheck: false, rows: Math.min(18, Math.max(4, allLines.length + 1)), style: {
                    width: '100%',
                    boxSizing: 'border-box',
                    padding: 12,
                    border: 'none',
                    borderBottom: '1px solid rgba(255,255,255,0.08)',
                    background: '#1E1B36',
                    color: '#F3F0FF',
                    fontSize: tokens_1.TYPE.micro,
                    lineHeight: 1.6,
                    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
                    resize: 'vertical',
                    outline: 'none',
                } })) : ((0, jsx_runtime_1.jsx)("pre", { style: {
                    margin: 0,
                    padding: '12px 14px',
                    background: '#221F3B',
                    color: '#EAE6FF',
                    fontSize: tokens_1.TYPE.micro,
                    lineHeight: 1.6,
                    overflowX: 'auto',
                    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
                }, children: (0, jsx_runtime_1.jsx)("code", { children: highlighted }) })), canRun && ((0, jsx_runtime_1.jsxs)("div", { style: {
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '8px 12px',
                    background: '#1B1830',
                    borderTop: '1px solid rgba(255,255,255,0.08)',
                    gap: 8,
                }, children: [(0, jsx_runtime_1.jsxs)("button", { type: "button", onClick: runCode, style: {
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: 6,
                            padding: '5px 12px',
                            borderRadius: 8,
                            border: `1px solid ${(0, tokens_1.alpha)(accent, 0.45)}`,
                            background: '#6C4CF6',
                            color: '#FFFFFF',
                            fontSize: 11.5,
                            fontWeight: 700,
                            cursor: 'pointer',
                        }, children: [(0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { name: "player-play", tone: "indigo", size: 16, glyph: 10 }), (0, jsx_runtime_1.jsx)("span", { children: isBrowserLang
                                    ? 'Run in Browser'
                                    : lang === 'json'
                                        ? 'Validate JSON'
                                        : 'Run Code' })] }), (0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', alignItems: 'center', gap: 8 }, children: [(output !== null || errorText !== null) && ((0, jsx_runtime_1.jsxs)(jsx_runtime_1.Fragment, { children: [(0, jsx_runtime_1.jsxs)("span", { style: {
                                            display: 'inline-flex',
                                            alignItems: 'center',
                                            gap: 5,
                                            padding: '2px 8px',
                                            borderRadius: 999,
                                            fontSize: 11,
                                            fontWeight: 700,
                                            background: errorText ? 'rgba(239,68,68,0.18)' : 'rgba(34,197,94,0.18)',
                                            color: errorText ? '#FCA5A5' : '#86EFAC',
                                        }, children: [(0, jsx_runtime_1.jsx)("span", { children: "\u25CF" }), (0, jsx_runtime_1.jsx)("span", { children: errorText ? 'Error' : 'Success' })] }), (0, jsx_runtime_1.jsxs)("button", { type: "button", onClick: runCode, style: { ...chipStyle, display: 'inline-flex', alignItems: 'center', gap: 4 }, children: [(0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { name: "refresh", tone: "lavender", size: 14, glyph: 9 }), (0, jsx_runtime_1.jsx)("span", { children: "Rerun" })] })] })), (0, jsx_runtime_1.jsxs)("span", { style: { fontSize: 11, color: '#8E89B2' }, children: [allLines.length, " ", allLines.length === 1 ? 'line' : 'lines'] })] })] })), htmlPreview ? ((0, jsx_runtime_1.jsx)("iframe", { title: "Code preview", sandbox: "allow-scripts", srcDoc: htmlPreview, style: {
                    width: '100%',
                    height: 220,
                    border: 'none',
                    borderTop: '1px solid rgba(255,255,255,0.1)',
                    background: '#fff',
                } })) : null, (output !== null || errorText !== null) && ((0, jsx_runtime_1.jsxs)("div", { style: {
                    borderTop: '1px solid rgba(255,255,255,0.1)',
                    background: '#151226',
                    padding: '10px 12px',
                    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
                    fontSize: tokens_1.TYPE.micro,
                }, children: [(0, jsx_runtime_1.jsxs)("div", { style: {
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'space-between',
                            marginBottom: 6,
                            color: '#8E89B2',
                            fontSize: 10.5,
                        }, children: [(0, jsx_runtime_1.jsxs)("span", { children: ["$ ", RUNNABLE_PY.has(lang) ? 'python3 main.py' : RUNNABLE_SQL.has(lang) ? 'sql --execute' : `run ${lang}`, " (run #", Math.max(1, runCount), ")"] }), (0, jsx_runtime_1.jsxs)("button", { type: "button", onClick: () => {
                                    setOutput(null);
                                    setErrorText(null);
                                    setHtmlPreview(null);
                                }, style: {
                                    border: 'none',
                                    background: 'transparent',
                                    color: '#8E89B2',
                                    cursor: 'pointer',
                                    fontSize: 10.5,
                                    display: 'inline-flex',
                                    alignItems: 'center',
                                    gap: 4,
                                }, children: [(0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { name: "x", tone: "slate", size: 14, glyph: 9 }), (0, jsx_runtime_1.jsx)("span", { children: "Clear" })] })] }), output ? ((0, jsx_runtime_1.jsx)("pre", { style: {
                            margin: 0,
                            color: '#D8F3DC',
                            lineHeight: 1.5,
                            whiteSpace: 'pre-wrap',
                        }, children: output })) : null, errorText ? ((0, jsx_runtime_1.jsx)("pre", { style: {
                            margin: output ? '6px 0 0' : 0,
                            padding: '8px 10px',
                            borderRadius: 8,
                            border: '1px solid rgba(239,68,68,0.4)',
                            background: 'rgba(239,68,68,0.12)',
                            color: '#FCA5A5',
                            lineHeight: 1.5,
                            whiteSpace: 'pre-wrap',
                        }, children: errorText })) : null] })), (() => {
                const seriesPreview = extractCodeSeriesPreview(source);
                if (!seriesPreview)
                    return null;
                const maxVal = Math.max(1, ...seriesPreview.points.map((p) => p.value));
                return ((0, jsx_runtime_1.jsxs)("div", { style: {
                        background: '#F7F4FF',
                        padding: '10px 14px',
                        borderTop: '1px solid rgba(124,92,252,0.22)',
                    }, children: [(0, jsx_runtime_1.jsx)("div", { style: { fontSize: 12, fontWeight: 700, color: '#221A47', marginBottom: 8 }, children: seriesPreview.title }), (0, jsx_runtime_1.jsx)("div", { style: {
                                display: 'flex',
                                alignItems: 'flex-end',
                                gap: 10,
                                height: 68,
                            }, children: seriesPreview.points.map((pt, idx) => {
                                const frac = Math.min(1, Math.max(0.15, pt.value / maxVal));
                                return ((0, jsx_runtime_1.jsxs)("div", { style: {
                                        flex: 1,
                                        display: 'flex',
                                        flexDirection: 'column',
                                        alignItems: 'center',
                                        gap: 4,
                                    }, children: [(0, jsx_runtime_1.jsx)("div", { style: {
                                                width: '68%',
                                                height: Math.round(46 * frac),
                                                borderRadius: '5px 5px 0 0',
                                                background: '#6C4CF6',
                                            } }), (0, jsx_runtime_1.jsx)("span", { style: { fontSize: 10, color: '#5E5680' }, children: pt.label })] }, idx));
                            }) })] }));
            })()] }));
}
function extractCodeSeriesPreview(code) {
    const labelMatch = code.match(/['"](?:Month|Label|Category|Quarter|Day|Year)s?['"]\s*:\s*\[([^\]]+)\]/i);
    const valMatch = code.match(/['"](Revenue|Sales|Value|Score|Users|Growth|Amount)s?['"]\s*:\s*\[([^\]]+)\]/i);
    if (!labelMatch || !valMatch)
        return null;
    const labels = labelMatch[1]
        .split(',')
        .map((s) => s.trim().replace(/^['"]|['"]$/g, ''))
        .filter(Boolean);
    const values = valMatch[2]
        .split(',')
        .map((s) => Number(s.trim()))
        .filter((n) => Number.isFinite(n));
    if (labels.length < 2 || values.length < 2)
        return null;
    const count = Math.min(labels.length, values.length);
    const seriesName = valMatch[1].charAt(0).toUpperCase() + valMatch[1].slice(1);
    return {
        title: `${seriesName} Trend`,
        points: Array.from({ length: count }, (_, i) => ({ label: labels[i], value: values[i] })),
    };
}
