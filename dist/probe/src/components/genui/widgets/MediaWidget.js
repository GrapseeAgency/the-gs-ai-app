"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.MediaWidget = MediaWidget;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const TitleRow_1 = require("../primitives/TitleRow");
const EmptyState_1 = require("../primitives/EmptyState");
const CopyChip_1 = require("../primitives/CopyChip");
const GlassIcon_1 = require("@/components/nexly/GlassIcon");
/** Media — image/gallery/video/audio/code/file/link/citation/search/entity/product/listing/doc/embed/pdf. */
function MediaWidget({ payload, onPrompt }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const items = (payload.items ?? []).slice(0, 20);
    const type = payload.type ?? 'image';
    if (type === 'code' || payload.code) {
        return (0, jsx_runtime_1.jsx)(CodePreview, { code: payload.code ?? '', language: payload.language, title: payload.title });
    }
    if (type === 'video') {
        const src = items[0]?.url;
        return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title }), src ? ((0, jsx_runtime_1.jsx)("video", { src: src, controls: true, preload: "metadata", style: { width: '100%', borderRadius: tokens_1.RADIUS.lg, border: `1px solid ${theme.border}`, maxHeight: 300, background: '#000' } })) : ((0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "No video URL" }))] }));
    }
    if (type === 'audio') {
        const src = items[0]?.url;
        return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title, right: items[0]?.meta }), src ? ((0, jsx_runtime_1.jsx)("audio", { src: src, controls: true, preload: "metadata", style: { width: '100%' }, children: (0, jsx_runtime_1.jsx)("track", { kind: "captions" }) })) : ((0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "No audio URL" }))] }));
    }
    if (type === 'gallery' && items.length > 1) {
        return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title }), (0, jsx_runtime_1.jsx)("div", { style: { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }, children: items.map((item, i) => (
                    // eslint-disable-next-line @next/next/no-img-element
                    (0, jsx_runtime_1.jsx)("img", { src: item.url ?? item.thumb, alt: item.caption ?? `image ${i + 1}`, loading: "lazy", style: { width: '100%', height: 110, objectFit: 'cover', borderRadius: tokens_1.RADIUS.md, border: `1px solid ${theme.border}` } }, i))) })] }));
    }
    if (items.length > 0) {
        return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title }), (0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', flexDirection: 'column', gap: 8 }, children: items.map((item, i) => ((0, jsx_runtime_1.jsx)(MediaRow, { index: i, item: item, type: type, onPrompt: onPrompt }, i))) })] }));
    }
    if (payload.text) {
        return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title }), (0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.body, color: theme.text, lineHeight: 1.6, whiteSpace: 'pre-wrap' }, children: payload.text })] }));
    }
    return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "No media" });
}
function MediaRow({ index, item, type, onPrompt, }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const clickable = Boolean(item.prompt || item.url);
    const isCitationLike = type === 'citation' || type === 'search' || type === 'link';
    const domain = item.domain ||
        (() => {
            try {
                return item.url ? new URL(item.url).hostname.replace(/^www\./, '') : '';
            }
            catch {
                return '';
            }
        })();
    const hay = `${domain} ${item.url ?? ''}`.toLowerCase();
    const academic = hay.includes('.edu') ||
        hay.includes('nature.com') ||
        hay.includes('arxiv.org') ||
        hay.includes('ieee.org') ||
        hay.includes('acm.org');
    const action = () => {
        if (item.prompt)
            onPrompt?.(item.prompt);
        else if (item.url)
            window.open(item.url, '_blank', 'noopener');
    };
    const Row = ((0, jsx_runtime_1.jsxs)("div", { onClick: action, role: clickable ? 'button' : undefined, style: {
            display: 'flex',
            gap: 11,
            alignItems: isCitationLike ? 'flex-start' : 'center',
            padding: '10px 12px',
            borderRadius: tokens_1.RADIUS.md,
            border: `1px solid ${theme.border}`,
            cursor: clickable ? 'pointer' : 'default',
            transition: 'border-color .15s ease',
        }, onMouseEnter: (e) => clickable && (e.currentTarget.style.borderColor = (0, tokens_1.alpha)(accent, 0.5)), onMouseLeave: (e) => clickable && (e.currentTarget.style.borderColor = theme.border), children: [isCitationLike ? ((0, jsx_runtime_1.jsx)("span", { style: {
                    display: 'inline-flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    width: 24,
                    height: 24,
                    borderRadius: 12,
                    background: 'rgba(124, 92, 252, 0.14)',
                    border: '1px solid rgba(124, 92, 252, 0.25)',
                    color: '#5B3DF5',
                    fontSize: 11.5,
                    fontWeight: 700,
                    flexShrink: 0,
                    marginTop: 1,
                }, children: index + 1 })) : item.thumb ? (
            // eslint-disable-next-line @next/next/no-img-element
            (0, jsx_runtime_1.jsx)("img", { src: item.thumb, alt: "", loading: "lazy", style: { width: 46, height: 46, objectFit: 'cover', borderRadius: 10, flexShrink: 0 } })) : null, (0, jsx_runtime_1.jsxs)("span", { style: { flex: 1, minWidth: 0 }, children: [(0, jsx_runtime_1.jsx)("span", { style: { display: 'block', fontSize: tokens_1.TYPE.label, fontWeight: 700, color: theme.text, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }, children: item.title ?? domain ?? item.url }), item.subtitle || item.snippet ? ((0, jsx_runtime_1.jsx)("span", { style: { display: '-webkit-box', fontSize: tokens_1.TYPE.micro, color: theme.text3, marginTop: 2, overflow: 'hidden', textOverflow: 'ellipsis', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical' }, children: item.subtitle ?? item.snippet })) : null, isCitationLike ? ((0, jsx_runtime_1.jsxs)("span", { style: { display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8, marginTop: 4, flexWrap: 'wrap' }, children: [(0, jsx_runtime_1.jsxs)("span", { style: { display: 'inline-flex', alignItems: 'center', gap: 4, fontSize: tokens_1.TYPE.micro, fontWeight: 600, color: '#5B3DF5' }, children: [(0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { name: "external-link", tone: "indigo", size: 15, glyph: 9 }), (0, jsx_runtime_1.jsx)("span", { children: domain || item.url || 'Source' })] }), (0, jsx_runtime_1.jsxs)("span", { style: {
                                    display: 'inline-flex',
                                    alignItems: 'center',
                                    gap: 4,
                                    padding: '1px 7px',
                                    borderRadius: 999,
                                    fontSize: 10.5,
                                    fontWeight: 700,
                                    background: academic ? 'rgba(124, 92, 252, 0.14)' : 'rgba(18, 154, 90, 0.14)',
                                    color: academic ? '#5B3DF5' : '#129A5A',
                                }, children: [(0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { name: academic ? 'building-bank' : 'shield-check', tone: academic ? 'indigo' : 'green', size: 14, glyph: 9 }), (0, jsx_runtime_1.jsx)("span", { children: academic ? 'Academic' : 'Trusted Source' })] })] })) : ((item.meta || item.price || item.rating || item.badge || item.domain) && ((0, jsx_runtime_1.jsxs)("span", { style: { display: 'flex', gap: 8, marginTop: 4, fontSize: tokens_1.TYPE.micro, color: theme.text3, flexWrap: 'wrap' }, children: [item.price ? (0, jsx_runtime_1.jsx)("b", { style: { color: accent }, children: item.price }) : null, item.rating ? ((0, jsx_runtime_1.jsxs)("span", { style: { display: 'inline-flex', alignItems: 'center', gap: 3 }, children: [(0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { name: "star", tone: "amber", size: 14, glyph: 9 }), (0, jsx_runtime_1.jsx)("span", { children: item.rating })] })) : null, item.badge ? (0, jsx_runtime_1.jsx)("span", { style: { fontWeight: 700, color: accent }, children: item.badge }) : null, (0, jsx_runtime_1.jsx)("span", { children: item.meta ?? item.domain })] })))] }), clickable && !isCitationLike ? (0, jsx_runtime_1.jsx)("span", { style: { color: accent, flexShrink: 0 }, children: "\u2192" }) : null] }));
    if (type === 'citation' && item.url && !item.prompt) {
        return ((0, jsx_runtime_1.jsx)("a", { href: item.url, target: "_blank", rel: "noopener noreferrer", style: { textDecoration: 'none' }, children: Row }));
    }
    return Row;
}
function CodePreview({ code, language, title }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const [src, setSrc] = (0, react_1.useState)(code);
    const [running, setRunning] = (0, react_1.useState)(false);
    const runnable = ['javascript', 'js', 'html', 'svg'].includes((language ?? '').toLowerCase());
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }, children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: title, style: { marginBottom: 0 } }), (0, jsx_runtime_1.jsxs)("span", { style: { display: 'inline-flex', gap: 8 }, children: [(0, jsx_runtime_1.jsx)(CopyChip_1.CopyChip, { value: code }), runnable && ((0, jsx_runtime_1.jsx)("button", { type: "button", onClick: () => setRunning((r) => !r), style: {
                                    padding: '5px 12px',
                                    borderRadius: 999,
                                    border: `1px solid ${accent}`,
                                    background: running ? (0, tokens_1.alpha)(accent, 0.16) : 'transparent',
                                    color: accent,
                                    fontSize: tokens_1.TYPE.label,
                                    fontWeight: 600,
                                    cursor: 'pointer',
                                }, children: running ? 'Stop' : 'Run' }))] })] }), (0, jsx_runtime_1.jsx)("textarea", { value: src, onChange: (e) => setSrc(e.target.value), rows: Math.min(16, Math.max(3, src.split('\n').length)), spellCheck: false, style: {
                    width: '100%',
                    borderRadius: tokens_1.RADIUS.md,
                    border: `1px solid ${theme.border}`,
                    background: theme.dark ? 'rgba(0,0,0,0.32)' : 'rgba(0,0,0,0.03)',
                    color: theme.text,
                    fontSize: tokens_1.TYPE.micro,
                    lineHeight: 1.6,
                    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
                    padding: 12,
                    resize: 'vertical',
                    outline: 'none',
                } }), running && ((0, jsx_runtime_1.jsx)("iframe", { title: "preview", sandbox: "allow-scripts", srcDoc: `<!doctype html><html><body style="margin:0;background:transparent;font-family:sans-serif"><script>${src.replace(/<\/script>/g, '<\\/script>')}<\/script></body></html>`, style: { width: '100%', height: 180, border: `1px solid ${theme.border}`, borderRadius: tokens_1.RADIUS.md, marginTop: 8, background: theme.card } }))] }));
}
