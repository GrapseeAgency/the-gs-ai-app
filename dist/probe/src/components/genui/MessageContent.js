"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.MessageContent = MessageContent;
const jsx_runtime_1 = require("react/jsx-runtime");
/**
 * MessageContent — the full assistant answer: prose and widgets inline,
 * streamed-aware. Unclosed fences render as skeletons; a tap on any prompt
 * affordance flows up through onPrompt.
 */
const react_1 = require("react");
const stream_1 = require("./stream");
const WidgetRenderer_1 = require("./WidgetRenderer");
const Skeleton_1 = require("./primitives/Skeleton");
const MermaidBlock_1 = require("./blocks/MermaidBlock");
const HtmlBlock_1 = require("./blocks/HtmlBlock");
const SvgBlock_1 = require("./blocks/SvgBlock");
const CodeBlock_1 = require("./blocks/CodeBlock");
const tokens_1 = require("./theme/tokens");
/** Prose with tappable [N] citations, matching the host page's behaviour. */
function Prose({ text, theme, accent, citations }) {
    if (!citations || citations.length === 0) {
        return (0, jsx_runtime_1.jsx)("div", { style: { color: theme.text, whiteSpace: 'pre-wrap' }, children: text });
    }
    const nodes = [];
    const re = /[\[【〚［]\s*(\d{1,2})\s*[\]】〛］]/g;
    let last = 0;
    let key = 0;
    for (const m of text.matchAll(re)) {
        const idx = m.index ?? 0;
        if (idx > last)
            nodes.push((0, jsx_runtime_1.jsx)("span", { children: text.slice(last, idx) }, `t${key++}`));
        const n = Number.parseInt(m[1], 10);
        const src = citations.find((c) => c.ordinal === n);
        if (src) {
            nodes.push((0, jsx_runtime_1.jsx)("button", { onClick: () => window.open(src.url, '_blank', 'noopener,noreferrer'), "aria-label": `Open citation ${n}: ${src.title}`, title: `${src.title} — ${src.domain ?? ''}`, style: {
                    display: 'inline-flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    minWidth: 16,
                    height: 16,
                    margin: '0 2px',
                    padding: '0 3px',
                    borderRadius: 4,
                    verticalAlign: '2px',
                    border: 'none',
                    background: `${accent}29`,
                    color: accent,
                    fontSize: 10,
                    fontWeight: 700,
                    cursor: 'pointer',
                }, children: n }, `c${key++}`));
        }
        else {
            nodes.push((0, jsx_runtime_1.jsx)("span", { children: m[0] }, `c${key++}`));
        }
        last = idx + m[0].length;
    }
    if (last < text.length)
        nodes.push((0, jsx_runtime_1.jsx)("span", { children: text.slice(last) }, `t${key++}`));
    return (0, jsx_runtime_1.jsx)("div", { style: { color: theme.text, whiteSpace: 'pre-wrap' }, children: nodes });
}
function MessageContent({ text, dark = true, onPrompt, streaming = false, citations, }) {
    const segments = (0, react_1.useMemo)(() => (0, stream_1.parseStream)(text), [text]);
    const theme = (0, react_1.useMemo)(() => (0, tokens_1.themeFor)(dark), [dark]);
    const accent = segments.find((s) => s.kind === 'widget')?.payload.accent;
    return ((0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', flexDirection: 'column', gap: 14, fontSize: tokens_1.TYPE.body, lineHeight: 1.65 }, children: segments.map((segment, i) => {
            switch (segment.kind) {
                case 'text':
                    return ((0, jsx_runtime_1.jsx)(Prose, { text: segment.text, theme: theme, accent: accent ?? '#2DD4A8', citations: citations }, i));
                case 'widget':
                    return segment.complete ? ((0, jsx_runtime_1.jsx)(WidgetRenderer_1.WidgetRenderer, { kind: segment.widget, payload: segment.payload, dark: dark, onPrompt: onPrompt }, i)) : ((0, jsx_runtime_1.jsx)(Skeleton_1.Skeleton, { label: `Building ${segment.widget}…` }, i));
                case 'mermaid':
                    return ((0, jsx_runtime_1.jsx)(MermaidBlock_1.MermaidBlock, { source: segment.source }, i));
                case 'html':
                    return (0, jsx_runtime_1.jsx)(HtmlBlock_1.HtmlBlock, { source: segment.source }, i);
                case 'svg':
                    return (0, jsx_runtime_1.jsx)(SvgBlock_1.SvgBlock, { source: segment.source }, i);
                case 'code':
                    return (0, jsx_runtime_1.jsx)(CodeBlock_1.CodeBlock, { code: segment.code, language: segment.language }, i);
                case 'map':
                    return (0, jsx_runtime_1.jsx)(WidgetRenderer_1.WidgetRenderer, { kind: "map", payload: segment.payload, dark: dark }, i);
                default:
                    return null;
            }
        }) }));
}
