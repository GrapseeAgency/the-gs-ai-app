"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.parseProseBlocks = parseProseBlocks;
exports.MessageContent = MessageContent;
const jsx_runtime_1 = require("react/jsx-runtime");
/**
 * MessageContent — the full assistant answer: rich markdown prose and widgets
 * inline, streamed-aware. Unclosed fences render as skeletons; a tap on any
 * prompt affordance flows up through onPrompt.
 *
 * Prose is parsed into the same block + inline grammar as the Android
 * (ContentBlocks.kt / InlineMarkdown.kt) and iOS (ContentBlocks.swift /
 * InlineMarkdown.swift) twins: headings, bullet/ordered/nested/task lists,
 * blockquotes, markdown tables with alignment, dividers, $$math$$ blocks,
 * <details>/<summary> collapsibles, standalone images, inline code, math,
 * safe http(s) links, bold/italic/strike, [N] citation chips, and :icon:
 * glass glyphs.
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
const icons_1 = require("@/lib/nexly/icons");
const GlassIcon_1 = require("@/components/nexly/GlassIcon");
const HEADING_RE = /^(#{1,6})\s+(.+?)\s*#*\s*$/;
const HR_RE = /^ {0,3}((\* *){3,}|(- *){3,}|(_ *){3,})$/;
const BULLET_RE = /^(\s*)([-*+•·])\s+(.*)$/;
const ORDERED_RE = /^(\s*)(\d{1,9})[.)]\s+(.*)$/;
const TASK_PREFIX_RE = /^\[([ xX])\]\s+(.*)$/;
const QUOTE_RE = /^\s{0,3}>\s?(.*)$/;
const IMAGE_ONLY_RE = /^!\[([^\]]*)\]\(([^)\s]+)(?:\s+"[^"]*")?\)\s*$/;
const DETAILS_OPEN_RE = /^\s*<details[^>]*>\s*$/i;
const DETAILS_INLINE_RE = /^\s*<details[^>]*>\s*<summary>(.*?)<\/summary>\s*$/i;
const SUMMARY_RE = /^\s*<summary>(.*?)<\/summary>\s*$/i;
const DETAILS_CLOSE_RE = /^\s*<\/details>\s*$/i;
const BLOCKED_SCHEMES = ['javascript:', 'data:', 'file:', 'vbscript:', 'blob:'];
function isSafeUrl(url) {
    const lower = url.trim().toLowerCase();
    if (BLOCKED_SCHEMES.some((s) => lower.startsWith(s)))
        return false;
    return lower.startsWith('https://') || lower.startsWith('http://') || lower.startsWith('mailto:');
}
function isTableSeparator(line) {
    const t = line.trim();
    if (!t || !t.includes('|') || !t.includes('-'))
        return false;
    if (!/^[|\-: \t]+$/.test(t))
        return false;
    return (t.match(/-/g) ?? []).length >= 3;
}
function splitTableCells(line) {
    let t = line.trim();
    if (t.startsWith('|'))
        t = t.slice(1);
    if (t.endsWith('|') && !t.endsWith('\\|'))
        t = t.slice(0, -1);
    const cells = [];
    let cur = '';
    let escaped = false;
    for (const ch of t) {
        if (escaped) {
            cur += ch === '|' ? '|' : '\\' + ch;
            escaped = false;
        }
        else if (ch === '\\') {
            escaped = true;
        }
        else if (ch === '|') {
            cells.push(cur.trim());
            cur = '';
        }
        else {
            cur += ch;
        }
    }
    if (escaped)
        cur += '\\';
    cells.push(cur.trim());
    return cells;
}
function isProseContinuation(lines, idx) {
    const raw = lines[idx];
    if (!raw || !raw.trim())
        return false;
    const t = raw.trimEnd();
    const lead = t.trimStart();
    if (lead.startsWith('$$'))
        return false;
    if (HEADING_RE.test(t))
        return false;
    if (HR_RE.test(t))
        return false;
    if (QUOTE_RE.test(raw))
        return false;
    if (BULLET_RE.test(raw) || ORDERED_RE.test(raw))
        return false;
    if (DETAILS_OPEN_RE.test(t) || DETAILS_INLINE_RE.test(t))
        return false;
    if (IMAGE_ONLY_RE.test(t.trim()))
        return false;
    if (t.includes('|') && idx + 1 < lines.length && isTableSeparator(lines[idx + 1]))
        return false;
    return true;
}
function buildListTree(records, from, level) {
    const ordered = records[from].ordered;
    const items = [];
    let i = from;
    while (i < records.length && records[i].level >= level) {
        if (records[i].level > level) {
            i++;
            continue;
        }
        if (records[i].ordered !== ordered)
            break;
        const rec = records[i];
        let j = i + 1;
        const childRecords = [];
        while (j < records.length && records[j].level > level) {
            childRecords.push(records[j]);
            j++;
        }
        const children = [];
        if (childRecords.length > 0) {
            children.push(buildListTree(childRecords, 0, childRecords[0].level)[0]);
        }
        items.push({
            number: rec.ordered ? rec.number : undefined,
            checked: rec.checked,
            text: rec.text,
            continuations: rec.continuations,
            children,
        });
        i = j;
    }
    const block = ordered
        ? { kind: 'orderedList', start: records[from].number || 1, items }
        : { kind: 'bulletList', items };
    return [block, i - from];
}
function parseProseBlocks(rawText) {
    const lines = rawText.replace(/\r\n/g, '\n').split('\n');
    const blocks = [];
    let i = 0;
    while (i < lines.length) {
        const line = lines[i];
        const trimmed = line.trimEnd();
        const lead = trimmed.trimStart();
        if (!line.trim()) {
            i++;
            continue;
        }
        if (lead.startsWith('$$')) {
            const rest = lead.slice(2);
            let body = '';
            const closeSame = rest.indexOf('$$');
            if (closeSame !== -1) {
                body = rest.slice(0, closeSame);
                i++;
            }
            else {
                if (rest.trim())
                    body = rest.trim();
                i++;
                while (i < lines.length) {
                    const l = lines[i];
                    const cIdx = l.indexOf('$$');
                    if (cIdx !== -1) {
                        const before = l.slice(0, cIdx).trim();
                        if (before)
                            body += (body ? '\n' : '') + before;
                        i++;
                        break;
                    }
                    body += (body ? '\n' : '') + l.trim();
                    i++;
                }
            }
            if (body.trim())
                blocks.push({ kind: 'math', latex: body.trim() });
            continue;
        }
        const hMatch = HEADING_RE.exec(trimmed);
        if (hMatch) {
            blocks.push({ kind: 'heading', level: hMatch[1].length, text: hMatch[2] });
            i++;
            continue;
        }
        if (HR_RE.test(trimmed)) {
            blocks.push({ kind: 'divider' });
            i++;
            continue;
        }
        if (line.includes('|') && i + 1 < lines.length && isTableSeparator(lines[i + 1])) {
            const headerCells = splitTableCells(lines[i]);
            const alignCells = splitTableCells(lines[i + 1]);
            const colCount = Math.max(headerCells.length, alignCells.length);
            const headers = Array.from({ length: colCount }, (_, c) => headerCells[c] ?? '');
            const aligns = Array.from({ length: colCount }, (_, c) => {
                const cell = (alignCells[c] ?? '').trim();
                const l = cell.startsWith(':');
                const r = cell.endsWith(':');
                return l && r ? 'center' : r ? 'right' : 'left';
            });
            const rows = [];
            i += 2;
            while (i < lines.length) {
                const rowLine = lines[i];
                if (!rowLine.trim() || !rowLine.includes('|'))
                    break;
                if (isTableSeparator(rowLine)) {
                    i++;
                    continue;
                }
                const cells = splitTableCells(rowLine);
                rows.push(Array.from({ length: colCount }, (_, c) => cells[c] ?? ''));
                i++;
            }
            blocks.push({ kind: 'table', headers, aligns, rows });
            continue;
        }
        if (QUOTE_RE.test(line)) {
            const inner = [];
            while (i < lines.length) {
                const qm = QUOTE_RE.exec(lines[i]);
                if (!qm)
                    break;
                inner.push(qm[1]);
                i++;
            }
            blocks.push({ kind: 'blockquote', blocks: parseProseBlocks(inner.join('\n')) });
            continue;
        }
        const detInline = DETAILS_INLINE_RE.exec(trimmed);
        if (detInline || DETAILS_OPEN_RE.test(trimmed)) {
            let summary = detInline ? detInline[1].trim() : '';
            i++;
            if (!detInline) {
                while (i < lines.length) {
                    const sm = SUMMARY_RE.exec(lines[i].trimEnd());
                    if (sm) {
                        summary = sm[1].trim();
                        i++;
                        break;
                    }
                    if (lines[i].trim())
                        break;
                    i++;
                }
            }
            const inner = [];
            while (i < lines.length) {
                if (DETAILS_CLOSE_RE.test(lines[i].trimEnd())) {
                    i++;
                    break;
                }
                inner.push(lines[i]);
                i++;
            }
            blocks.push({
                kind: 'collapsible',
                summary: summary || 'Details',
                blocks: parseProseBlocks(inner.join('\n')),
            });
            continue;
        }
        if (BULLET_RE.test(line) || ORDERED_RE.test(line)) {
            const records = [];
            while (i < lines.length) {
                const l = lines[i];
                const bm = BULLET_RE.exec(l);
                const om = ORDERED_RE.exec(l);
                if (bm) {
                    const indent = (bm[1].match(/ /g) ?? []).length + (bm[1].match(/\t/g) ?? []).length * 2;
                    const rawItem = bm[3];
                    const tm = TASK_PREFIX_RE.exec(rawItem);
                    records.push({
                        level: Math.floor(indent / 2),
                        ordered: false,
                        number: 0,
                        checked: tm ? tm[1].toLowerCase() === 'x' : undefined,
                        text: tm ? tm[2] : rawItem,
                        continuations: [],
                    });
                    i++;
                }
                else if (om) {
                    const indent = (om[1].match(/ /g) ?? []).length + (om[1].match(/\t/g) ?? []).length * 2;
                    records.push({
                        level: Math.floor(indent / 2),
                        ordered: true,
                        number: Number.parseInt(om[2], 10) || 1,
                        text: om[3],
                        continuations: [],
                    });
                    i++;
                }
                else if (l.trim() && records.length > 0) {
                    const last = records[records.length - 1];
                    const req = ' '.repeat(Math.max(last.level * 2 + 2, 1));
                    if (l.startsWith(req)) {
                        last.continuations.push(l.trim());
                        i++;
                    }
                    else {
                        break;
                    }
                }
                else {
                    break;
                }
            }
            let r = 0;
            while (r < records.length) {
                const [listBlock, used] = buildListTree(records, r, records[r].level);
                blocks.push(listBlock);
                r += used;
            }
            continue;
        }
        const imgMatch = IMAGE_ONLY_RE.exec(trimmed.trim());
        if (imgMatch && isSafeUrl(imgMatch[2])) {
            blocks.push({ kind: 'image', alt: imgMatch[1], url: imgMatch[2] });
            i++;
            continue;
        }
        const paraLines = [];
        while (i < lines.length && isProseContinuation(lines, i)) {
            paraLines.push(lines[i].trimEnd());
            i++;
        }
        if (paraLines.length > 0) {
            blocks.push({ kind: 'paragraph', text: paraLines.join('\n') });
        }
        else {
            blocks.push({ kind: 'paragraph', text: line });
            i++;
        }
    }
    return blocks;
}
const INLINE_RE = /`([^`\n]+)`|\\\(([\s\S]+?)\\\)|\\\[([\s\S]+?)\\\]|\$\$(?!\s)([\s\S]+?)\$\$|\$(?=[^$\n]*[\\^_])([^$\n]+?)\$|!\[([^\]]*)\]\(([^)\s]+)\)|\[([^\]\n]+)\]\(([^)\s]+)\)|(https?:\/\/[^\s<>[\]{}"'()]+[^\s<>[\]{}"'().,;:!?])|\*\*([\s\S]+?)\*\*|__([\s\S]+?)__|(?<!\*)\*([^*\n]+?)\*(?!\*)|(?<![\w])_(?!_)([^_\n]+?)_(?![\w])|~~([\s\S]+?)~~|\\([*_#`[\]()~\\-])|[\[【〚［]\s*(\d{1,2})\s*[\]】〛］]/g;
function renderTextLeaf(text, keyPrefix) {
    const normalised = (0, icons_1.emojiToIconTokens)(text);
    if (!normalised.includes(':'))
        return normalised;
    const nodes = [];
    let last = 0;
    let k = 0;
    for (const m of normalised.matchAll(icons_1.ICON_TOKEN_RE)) {
        const idx = m.index ?? 0;
        if (idx > last)
            nodes.push((0, jsx_runtime_1.jsx)(react_1.Fragment, { children: normalised.slice(last, idx) }, `${keyPrefix}-t${k++}`));
        const name = (0, icons_1.canonicalIconName)(m[1]);
        if (name) {
            nodes.push((0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { name: name, tile: 18, size: 11, className: "nx-inline-icon" }, `${keyPrefix}-i${k++}`));
        }
        else {
            nodes.push((0, jsx_runtime_1.jsx)(react_1.Fragment, { children: m[0] }, `${keyPrefix}-u${k++}`));
        }
        last = idx + m[0].length;
    }
    if (last < normalised.length)
        nodes.push((0, jsx_runtime_1.jsx)(react_1.Fragment, { children: normalised.slice(last) }, `${keyPrefix}-t${k++}`));
    return nodes;
}
function renderInline(text, theme, accent, citations, keyPrefix = 'in', depth = 0) {
    if (!text)
        return [];
    if (depth > 6)
        return [renderTextLeaf(text, keyPrefix)];
    const out = [];
    let last = 0;
    let k = 0;
    const re = new RegExp(INLINE_RE.source, 'g');
    for (const m of text.matchAll(re)) {
        const idx = m.index ?? 0;
        if (idx > last) {
            out.push((0, jsx_runtime_1.jsx)(react_1.Fragment, { children: renderTextLeaf(text.slice(last, idx), `${keyPrefix}-l${k}`) }, `${keyPrefix}-p${k++}`));
        }
        const [full, code, mathParen, mathBracket, mathDisplay, mathDollar, imgAlt, imgUrl, linkText, linkUrl, bareUrl, boldStar, boldUnder, italicStar, italicUnder, strike, escaped, citeNum,] = m;
        if (code !== undefined) {
            out.push((0, jsx_runtime_1.jsx)("code", { style: {
                    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
                    fontSize: '0.88em',
                    padding: '1px 5px',
                    borderRadius: 5,
                    background: theme.raised,
                    border: `1px solid ${theme.border}`,
                }, children: code }, `${keyPrefix}-c${k++}`));
        }
        else if (mathParen !== undefined || mathBracket !== undefined || mathDisplay !== undefined || mathDollar !== undefined) {
            const latex = mathParen ?? mathBracket ?? mathDisplay ?? mathDollar ?? '';
            out.push((0, jsx_runtime_1.jsx)("span", { style: {
                    fontFamily: 'ui-serif, Georgia, Cambria, "Times New Roman", serif',
                    fontStyle: 'italic',
                    padding: '0 2px',
                }, children: latex }, `${keyPrefix}-m${k++}`));
        }
        else if (imgUrl !== undefined) {
            const label = imgAlt || imgUrl;
            if (isSafeUrl(imgUrl)) {
                out.push((0, jsx_runtime_1.jsx)("a", { href: imgUrl, target: "_blank", rel: "noopener noreferrer", style: { color: accent, textDecoration: 'underline' }, children: label }, `${keyPrefix}-im${k++}`));
            }
            else {
                out.push((0, jsx_runtime_1.jsx)(react_1.Fragment, { children: label }, `${keyPrefix}-im${k++}`));
            }
        }
        else if (linkUrl !== undefined) {
            if (isSafeUrl(linkUrl)) {
                out.push((0, jsx_runtime_1.jsx)("a", { href: linkUrl, target: "_blank", rel: "noopener noreferrer", style: { color: accent, textDecoration: 'underline' }, children: renderInline(linkText, theme, accent, citations, `${keyPrefix}-lkt${k}`, depth + 1) }, `${keyPrefix}-lk${k++}`));
            }
            else {
                out.push((0, jsx_runtime_1.jsx)(react_1.Fragment, { children: renderInline(linkText, theme, accent, citations, `${keyPrefix}-lkt${k}`, depth + 1) }, `${keyPrefix}-lk${k++}`));
            }
        }
        else if (bareUrl !== undefined) {
            if (isSafeUrl(bareUrl)) {
                out.push((0, jsx_runtime_1.jsx)("a", { href: bareUrl, target: "_blank", rel: "noopener noreferrer", style: { color: accent, textDecoration: 'underline' }, children: bareUrl }, `${keyPrefix}-bu${k++}`));
            }
            else {
                out.push((0, jsx_runtime_1.jsx)(react_1.Fragment, { children: bareUrl }, `${keyPrefix}-bu${k++}`));
            }
        }
        else if (boldStar !== undefined || boldUnder !== undefined) {
            const inner = boldStar ?? boldUnder ?? '';
            out.push((0, jsx_runtime_1.jsx)("strong", { style: { fontWeight: 650 }, children: renderInline(inner, theme, accent, citations, `${keyPrefix}-bi${k}`, depth + 1) }, `${keyPrefix}-b${k++}`));
        }
        else if (italicStar !== undefined || italicUnder !== undefined) {
            const inner = italicStar ?? italicUnder ?? '';
            out.push((0, jsx_runtime_1.jsx)("em", { children: renderInline(inner, theme, accent, citations, `${keyPrefix}-ii${k}`, depth + 1) }, `${keyPrefix}-i${k++}`));
        }
        else if (strike !== undefined) {
            out.push((0, jsx_runtime_1.jsx)("del", { children: renderInline(strike, theme, accent, citations, `${keyPrefix}-si${k}`, depth + 1) }, `${keyPrefix}-s${k++}`));
        }
        else if (escaped !== undefined) {
            out.push((0, jsx_runtime_1.jsx)(react_1.Fragment, { children: escaped }, `${keyPrefix}-e${k++}`));
        }
        else if (citeNum !== undefined) {
            const n = Number.parseInt(citeNum, 10);
            const src = citations?.find((c) => c.ordinal === n);
            if (src) {
                out.push((0, jsx_runtime_1.jsx)("button", { onClick: () => window.open(src.url, '_blank', 'noopener,noreferrer'), "aria-label": `Open citation ${n}: ${src.title}`, title: `${src.title}${src.domain ? ` — ${src.domain}` : ''}`, style: {
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
                    }, children: n }, `${keyPrefix}-ct${k++}`));
            }
            else {
                out.push((0, jsx_runtime_1.jsx)(react_1.Fragment, { children: full }, `${keyPrefix}-ct${k++}`));
            }
        }
        last = idx + full.length;
    }
    if (last < text.length) {
        out.push((0, jsx_runtime_1.jsx)(react_1.Fragment, { children: renderTextLeaf(text.slice(last), `${keyPrefix}-l${k}`) }, `${keyPrefix}-p${k++}`));
    }
    return out;
}
function matchCalloutText(raw) {
    const trimmed = raw.trim();
    const alertMatch = trimmed.match(/^\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\]\s*([\s\S]*)$/i);
    if (alertMatch) {
        const kind = alertMatch[1].toUpperCase();
        const body = alertMatch[2].trim();
        if (kind === 'WARNING' || kind === 'CAUTION' || kind === 'IMPORTANT') {
            return { tone: 'warning', icon: 'alert-triangle', title: kind === 'IMPORTANT' ? 'Important' : 'Warning', body };
        }
        return { tone: 'fact', icon: 'bulb', title: kind === 'TIP' ? 'Key Insight' : 'Note', body };
    }
    const normalized = (0, icons_1.emojiToIconTokens)(trimmed);
    const leadMatch = normalized.match(/^(?:(?::[a-z0-9-]+:|✦)\s*)?\*\*(Quick summary|Summary|TL;DR|Important|Warning|Caution|Key Insights?|Quick Fact|Pro Tip|Tip|Note)\s*:?\*\*\s*:?\s*([\s\S]+)$/i);
    if (!leadMatch)
        return null;
    const label = leadMatch[1].trim();
    const body = leadMatch[2].trim();
    const lower = label.toLowerCase();
    if (lower === 'important' || lower === 'warning' || lower === 'caution') {
        return { tone: 'warning', icon: 'alert-triangle', title: label, body };
    }
    if (lower.includes('fact') || lower.includes('insight') || lower.includes('tip') || lower === 'note') {
        return { tone: 'fact', icon: 'bulb', title: label, body };
    }
    return { tone: 'summary', icon: 'bolt', title: label, body };
}
function sectionBadgeForHeading(raw) {
    const { icon: explicitIcon, rest } = (0, icons_1.extractLeadingIcon)(raw.replace(/[*_`~]/g, '').trim());
    if (explicitIcon)
        return { icon: explicitIcon, tone: 'lavender' };
    const clean = (0, icons_1.cleanPlainText)(rest).trim();
    if (!clean)
        return null;
    const lower = clean.toLowerCase();
    if (lower.includes('priorit') || lower.includes('goal') || lower.includes('objective'))
        return { icon: 'target', tone: 'rose' };
    if (lower.includes('step-by-step') || lower.includes('steps') || lower.includes('plan') || lower.includes('roadmap') || lower.includes('what to do'))
        return { icon: 'list-check', tone: 'lavender' };
    if (lower.includes('next action') || lower.includes('action item') || lower.includes('checklist') || lower.includes('todo') || lower.includes('next step'))
        return { icon: 'checkbox', tone: 'green' };
    if (lower.includes('key source') || lower.includes('sources'))
        return { icon: 'link', tone: 'blue' };
    if (lower.includes('reference') || lower.includes('citation') || lower.includes('further reading'))
        return { icon: 'book-2', tone: 'indigo' };
    if (lower.includes('feature') || lower.includes('highlight') || lower.includes('benefit'))
        return { icon: 'sparkles', tone: 'amber' };
    if (lower.startsWith('top ') || lower.includes('chart') || lower.includes('metric') || lower.includes('overview'))
        return { icon: 'chart-bar', tone: 'cyan' };
    if (lower.includes('calculator') || lower.includes('budget') || lower.includes('pricing'))
        return { icon: 'calculator', tone: 'amber' };
    if (lower.includes('fix') || lower.includes('solution') || lower.includes('repair') || lower.includes('troubleshoot'))
        return { icon: 'tool', tone: 'green' };
    if (lower.includes('why ') || lower.includes('how ') || lower.includes('explain') || lower.includes('reason'))
        return { icon: 'bulb', tone: 'amber' };
    if (lower.includes('bottom line') || lower.includes('conclusion') || lower.includes('verdict') || lower.includes('takeaway'))
        return { icon: 'circle-check', tone: 'green' };
    if (lower.includes('difference') || lower.includes('compare') || lower.includes('optional') || lower.includes('alternative'))
        return { icon: 'arrows-diff', tone: 'blue' };
    if (lower.includes('code') || lower.includes('implementation') || lower.includes('snippet') || lower.includes('sql') || lower.includes('query'))
        return { icon: 'code', tone: 'slate' };
    return (0, icons_1.semanticIconFor)(clean, 'sparkles');
}
function splitStepTitleAndDetail(raw) {
    const match = raw.trim().match(/^\*\*([^*]+)\*\*\s*(?:[—–\-:]\s*|\n+)([\s\S]+)$/);
    if (!match)
        return null;
    return { title: match[1].trim(), detail: match[2].trim() };
}
function classifyCalloutHeader(raw) {
    const { rest } = (0, icons_1.extractLeadingIcon)(raw.replace(/^[#*>`~\s]+|[*_`~:\s]+$/g, '').trim());
    const clean = (0, icons_1.cleanPlainText)(rest).replace(/[:\s]+$/, '').trim();
    const lower = clean.toLowerCase();
    if (['important', 'warning', 'caution', 'attention', 'important notice'].includes(lower)) {
        return { tone: 'warning', icon: 'alert-triangle', title: clean };
    }
    if (['quick summary', 'summary', 'tl;dr', 'tldr', 'executive summary'].includes(lower)) {
        return { tone: 'summary', icon: 'bolt', title: clean };
    }
    if (['key insight', 'key insights', 'quick fact', 'quick facts', 'pro tip', 'tip', 'note', 'key takeaway', 'key takeaways'].includes(lower)) {
        return { tone: 'fact', icon: 'bulb', title: clean };
    }
    return null;
}
function classifySectionHeadingMode(raw) {
    const lower = raw.replace(/[*_`~]/g, '').trim().toLowerCase();
    if (lower.includes('next action') ||
        lower.includes('action item') ||
        lower.includes('todo') ||
        lower.includes('to-do') ||
        lower.includes('checklist') ||
        lower === 'tasks' ||
        lower.includes('your tasks') ||
        lower.includes('what to do') ||
        lower.includes('next step') ||
        lower.includes('first step') ||
        lower.includes('how to fix') ||
        lower.includes('routine')) {
        return 'todo';
    }
    if (lower.includes('key source') || lower === 'sources' || lower.includes('trusted source')) {
        return 'sources';
    }
    if (lower.includes('priorit') ||
        lower.includes('feature') ||
        lower.includes('highlight') ||
        lower.includes('benefit') ||
        lower.startsWith('why ') ||
        lower.includes('bottom line') ||
        lower.includes('takeaway')) {
        return 'checkmark';
    }
    return 'default';
}
function extractCitationFromProseItem(ordinal, raw) {
    const linkMatch = raw.match(/\[([^\]\n]+)\]\((https?:\/\/[^)\s]+)\)/);
    const bareUrlMatch = raw.match(/(https?:\/\/[^\s)\]]+|www\.[a-zA-Z0-9.-]+\.[a-zA-Z]{2,})/);
    const url = linkMatch ? linkMatch[2] : bareUrlMatch ? (bareUrlMatch[1].startsWith('http') ? bareUrlMatch[1] : `https://${bareUrlMatch[1]}`) : '';
    if (!url)
        return null;
    const domainRaw = url.replace(/^https?:\/\//i, '').split('/')[0].replace(/^www\./i, '');
    const boldMatch = raw.match(/^\*\*([^*]+)\*\*/);
    const title = boldMatch
        ? boldMatch[1].replace(/[:\-—–\s]+$/, '').trim()
        : linkMatch
            ? linkMatch[1].trim()
            : raw.split(/[—–-]/)[0].replace(/[*_`]/g, '').trim();
    const snippet = raw
        .replace(/^\*\*[^*]+\*\*\s*[:\-—–]?\s*/, '')
        .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
        .replace(/https?:\/\/[^\s)\]]+|www\.[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/g, '')
        .replace(/^[\s:\-—–()[\]]+|[\s:\-—–()[\]]+$/g, '')
        .trim();
    return {
        ordinal,
        title: title || domainRaw,
        url,
        domain: `www.${domainRaw}`,
        snippet,
    };
}
function renderCalloutCard(callout, key, theme, accent, citations, extraBlocks) {
    const isWarning = callout.tone === 'warning';
    const bg = isWarning ? 'rgba(255, 244, 230, 0.92)' : 'rgba(242, 236, 255, 0.92)';
    const border = isWarning ? 'rgba(245, 158, 11, 0.34)' : 'rgba(124, 92, 252, 0.28)';
    const titleColor = isWarning ? '#7C3A0A' : '#241B4B';
    const bodyColor = isWarning ? '#5C3412' : '#3B345F';
    return ((0, jsx_runtime_1.jsxs)("div", { style: {
            display: 'flex',
            alignItems: 'flex-start',
            gap: 10,
            padding: '12px 14px',
            borderRadius: 14,
            background: bg,
            border: `1px solid ${border}`,
            boxShadow: '0 4px 14px rgba(90, 65, 180, 0.06)',
        }, children: [(0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { name: callout.icon, tone: isWarning ? 'amber' : callout.tone === 'fact' ? 'lavender' : 'indigo', size: 26, glyph: 14, style: { flexShrink: 0, marginTop: 1 } }), (0, jsx_runtime_1.jsxs)("div", { style: { flex: 1, minWidth: 0, color: bodyColor, fontSize: 13.5, lineHeight: 1.55, display: 'flex', flexDirection: 'column', gap: 4 }, children: [(0, jsx_runtime_1.jsx)("div", { style: { fontWeight: 700, color: titleColor, fontSize: 14 }, children: callout.title }), callout.body ? ((0, jsx_runtime_1.jsx)("div", { children: renderInline(callout.body, theme, accent, citations, `co-${key}`) })) : null, extraBlocks] })] }, key));
}
function InteractiveProseChecklistItem({ item, idx, j, theme, accent, citations, checkedOverride, onToggleOverride, }) {
    const [localChecked, setLocalChecked] = (0, react_1.useState)(Boolean(item.checked));
    const checked = checkedOverride !== undefined ? checkedOverride : localChecked;
    return ((0, jsx_runtime_1.jsxs)("li", { onClick: () => (onToggleOverride ? onToggleOverride() : setLocalChecked((prev) => !prev)), style: {
            listStyleType: 'none',
            display: 'flex',
            alignItems: 'flex-start',
            gap: 10,
            padding: '5px 8px',
            borderRadius: 10,
            cursor: 'pointer',
            background: checked ? 'rgba(124, 92, 252, 0.08)' : 'transparent',
            transition: 'background 0.15s ease',
        }, children: [(0, jsx_runtime_1.jsx)("span", { role: "checkbox", "aria-checked": checked, style: {
                    display: 'inline-flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    width: 18,
                    height: 18,
                    marginTop: 2,
                    borderRadius: 5,
                    border: checked ? 'none' : '1.5px solid rgba(124, 92, 252, 0.45)',
                    background: checked ? '#6C4CF6' : 'rgba(255, 255, 255, 0.85)',
                    color: '#FFFFFF',
                    fontSize: 11,
                    fontWeight: 800,
                    flexShrink: 0,
                }, children: checked && (0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { name: "check", size: 11, tile: 16 }) }), (0, jsx_runtime_1.jsxs)("div", { style: { flex: 1, minWidth: 0 }, children: [(0, jsx_runtime_1.jsx)("span", { style: { textDecoration: checked ? 'line-through' : 'none', opacity: checked ? 0.68 : 1 }, children: renderInline(item.text, theme, accent, citations, `li${idx}-${j}`) }), item.continuations.map((c, ci) => ((0, jsx_runtime_1.jsx)("div", { style: { marginTop: 2 }, children: renderInline(c, theme, accent, citations, `lic${idx}-${j}-${ci}`) }, ci)))] })] }));
}
function ProseTableGrid({ block, idx, theme, accent, citations, }) {
    const [pageSize, setPageSize] = (0, react_1.useState)(5);
    const [page, setPage] = (0, react_1.useState)(0);
    const [sortCol, setSortCol] = (0, react_1.useState)(null);
    const [sortAsc, setSortAsc] = (0, react_1.useState)(true);
    const sortedRows = (0, react_1.useMemo)(() => {
        if (sortCol === null)
            return block.rows;
        const parseSortableNum = (cell) => {
            const cleaned = cell.replace(/[*_`~,$%€£¥₹↑↓↗↘+,\s]/g, '').trim();
            const m = cleaned.match(/^(-?\d+(?:\.\d+)?)([kKmMbBtT])?$/);
            if (!m)
                return null;
            const base = Number(m[1]);
            if (!Number.isFinite(base))
                return null;
            const suf = (m[2] ?? '').toLowerCase();
            const mult = suf === 'k' ? 1e3 : suf === 'm' ? 1e6 : suf === 'b' ? 1e9 : suf === 't' ? 1e12 : 1;
            return base * mult;
        };
        return [...block.rows].sort((a, b) => {
            const ca = (a[sortCol] ?? '').replace(/[*_`~]/g, '').trim();
            const cb = (b[sortCol] ?? '').replace(/[*_`~]/g, '').trim();
            const na = parseSortableNum(ca);
            const nb = parseSortableNum(cb);
            const cmp = na !== null && nb !== null ? na - nb : ca.localeCompare(cb, undefined, { numeric: true, sensitivity: 'base' });
            return sortAsc ? cmp : -cmp;
        });
    }, [block.rows, sortCol, sortAsc]);
    const totalPages = Math.max(1, Math.ceil(sortedRows.length / pageSize));
    const safePage = Math.min(page, totalPages - 1);
    const visibleRows = sortedRows.slice(safePage * pageSize, safePage * pageSize + pageSize);
    const firstHeaderClean = (block.headers[0] ?? '').replace(/[*_`]/g, '').trim();
    const primaryNameCol = ['#', 'Rank', 'No', 'ID'].includes(firstHeaderClean) ? 1 : 0;
    const renderCellContent = (raw, colIdx, keyPrefix) => {
        const clean = raw.replace(/[*_`]/g, '').trim();
        const m = clean.match(/^([↑↓↗↘+-])?\s*([+-]?\d+(?:\.\d+)?%)$/);
        if (m && (clean.startsWith('+') || clean.startsWith('-') || clean.startsWith('↑') || clean.startsWith('↓') || clean.startsWith('↗') || clean.startsWith('↘'))) {
            const isNeg = clean.startsWith('-') || clean.startsWith('↓') || clean.startsWith('↘');
            const arrow = isNeg ? '↓' : '↑';
            const num = m[2].startsWith('+') || m[2].startsWith('-') ? m[2] : `${isNeg ? '-' : '+'}${m[2]}`;
            return ((0, jsx_runtime_1.jsxs)("span", { style: {
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 3,
                    fontWeight: 700,
                    color: isNeg ? '#E04F5F' : '#129A5A',
                }, children: [(0, jsx_runtime_1.jsx)("span", { children: arrow }), (0, jsx_runtime_1.jsx)("span", { children: num })] }));
        }
        if (colIdx === primaryNameCol) {
            const { icon: leadTok, rest: strippedCell } = (0, icons_1.extractLeadingIcon)(clean);
            const entityName = (0, icons_1.cleanPlainText)(strippedCell || clean).trim();
            const sem = leadTok ? { icon: leadTok, tone: 'lavender' } : (0, icons_1.semanticIconFor)(entityName, '');
            if (sem.icon) {
                return ((0, jsx_runtime_1.jsxs)("span", { style: { display: 'inline-flex', alignItems: 'center', gap: 7, fontWeight: 650, color: theme.text }, children: [(0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { name: sem.icon, tone: sem.tone, size: 22, glyph: 13 }), (0, jsx_runtime_1.jsx)("span", { children: renderInline(leadTok ? strippedCell : raw, theme, accent, citations, keyPrefix) })] }));
            }
        }
        return renderInline(raw, theme, accent, citations, keyPrefix);
    };
    return ((0, jsx_runtime_1.jsxs)("div", { style: {
            overflowX: 'auto',
            borderRadius: 14,
            border: `1px solid ${theme.border}`,
            background: theme.card,
            boxShadow: '0 6px 18px rgba(70, 52, 150, 0.06)',
        }, children: [(0, jsx_runtime_1.jsxs)("table", { style: { width: '100%', borderCollapse: 'collapse', fontSize: tokens_1.TYPE.label }, children: [(0, jsx_runtime_1.jsx)("thead", { children: (0, jsx_runtime_1.jsx)("tr", { style: { background: 'rgba(124, 92, 252, 0.11)', borderBottom: `1px solid ${theme.border}` }, children: block.headers.map((h, c) => ((0, jsx_runtime_1.jsx)("th", { onClick: () => {
                                    if (sortCol === c) {
                                        setSortAsc((prev) => !prev);
                                    }
                                    else {
                                        setSortCol(c);
                                        setSortAsc(true);
                                    }
                                    setPage(0);
                                }, style: {
                                    padding: '9px 12px',
                                    textAlign: block.aligns[c] ?? 'left',
                                    fontWeight: 700,
                                    color: theme.text,
                                    cursor: 'pointer',
                                    userSelect: 'none',
                                }, children: (0, jsx_runtime_1.jsxs)("span", { style: { display: 'inline-flex', alignItems: 'center', gap: 4 }, children: [(0, jsx_runtime_1.jsx)("span", { children: renderInline(h, theme, accent, citations, `th${idx}-${c}`) }), sortCol === c && ((0, jsx_runtime_1.jsx)("span", { style: { fontSize: 10, color: '#6C4CF6' }, children: sortAsc ? '↑' : '↓' }))] }) }, c))) }) }), (0, jsx_runtime_1.jsx)("tbody", { children: visibleRows.map((row, r) => ((0, jsx_runtime_1.jsx)("tr", { style: { borderTop: r > 0 ? `1px solid ${theme.border}` : undefined }, children: row.map((cell, c) => ((0, jsx_runtime_1.jsx)("td", { style: {
                                    padding: '8px 12px',
                                    textAlign: block.aligns[c] ?? 'left',
                                    color: c === primaryNameCol ? theme.text : theme.text2,
                                    fontWeight: c === primaryNameCol ? 600 : 400,
                                }, children: renderCellContent(cell, c, `td${idx}-${r}-${c}`) }, c))) }, r))) })] }), sortedRows.length >= 4 && ((0, jsx_runtime_1.jsxs)("div", { style: {
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '8px 12px',
                    borderTop: `1px solid ${theme.border}`,
                    fontSize: 12,
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
                                }, children: "\u203A" }), (0, jsx_runtime_1.jsxs)("span", { style: { fontSize: 11, color: theme.text2, marginLeft: 4 }, children: [safePage * pageSize + 1, "\u2013", Math.min(sortedRows.length, (safePage + 1) * pageSize), " of ", sortedRows.length] })] }), (0, jsx_runtime_1.jsxs)("button", { type: "button", onClick: () => {
                            const next = pageSize === 3 ? 5 : pageSize === 5 ? 10 : pageSize === 10 ? 25 : 3;
                            setPageSize(next);
                            setPage(0);
                        }, style: {
                            border: `1px solid ${theme.border}`,
                            background: 'rgba(124, 92, 252, 0.08)',
                            color: theme.text,
                            borderRadius: 8,
                            padding: '3px 8px',
                            fontSize: 11.5,
                            fontWeight: 600,
                            cursor: 'pointer',
                        }, children: ["Show: ", pageSize >= sortedRows.length ? 'All' : pageSize, " \u25BE"] })] }))] }));
}
function InteractiveProseTodoList({ block, idx, theme, accent, citations, }) {
    const [checkedMap, setCheckedMap] = (0, react_1.useState)(() => {
        const init = {};
        block.items.forEach((it, j) => {
            init[`base-${j}`] = Boolean(it.checked);
        });
        return init;
    });
    const [extraTasks, setExtraTasks] = (0, react_1.useState)([]);
    const [isAdding, setIsAdding] = (0, react_1.useState)(false);
    const [newTaskText, setNewTaskText] = (0, react_1.useState)('');
    const totalCount = block.items.length + extraTasks.length;
    const completedCount = block.items.reduce((acc, it, j) => acc + ((checkedMap[`base-${j}`] ?? Boolean(it.checked)) ? 1 : 0), 0) +
        extraTasks.reduce((acc, _, ti) => acc + (checkedMap[`extra-${ti}`] ? 1 : 0), 0);
    const pct = totalCount > 0 ? Math.round((completedCount / totalCount) * 100) : 0;
    return ((0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', flexDirection: 'column', gap: 6 }, children: [totalCount > 0 && ((0, jsx_runtime_1.jsxs)("div", { style: {
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    fontSize: 11,
                    fontWeight: 650,
                    color: theme.text2,
                    padding: '2px 4px',
                }, children: [(0, jsx_runtime_1.jsx)("span", { children: `${completedCount}/${totalCount} completed` }), (0, jsx_runtime_1.jsxs)("span", { style: { color: completedCount === totalCount ? '#129A5A' : '#5B3DF5', fontWeight: 700 }, children: [pct, "%"] })] })), (0, jsx_runtime_1.jsxs)("ul", { style: {
                    margin: 0,
                    paddingLeft: 0,
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 4,
                    color: theme.text,
                    listStyleType: 'none',
                }, children: [block.items.map((item, j) => {
                        const isC = checkedMap[`base-${j}`] ?? Boolean(item.checked);
                        return ((0, jsx_runtime_1.jsx)(InteractiveProseChecklistItem, { item: item.checked !== undefined ? item : { ...item, checked: false }, idx: idx, j: j, theme: theme, accent: accent, citations: citations, checkedOverride: isC, onToggleOverride: () => setCheckedMap((prev) => ({ ...prev, [`base-${j}`]: !isC })) }, j));
                    }), extraTasks.map((task, ti) => {
                        const isC = Boolean(checkedMap[`extra-${ti}`]);
                        return ((0, jsx_runtime_1.jsx)(InteractiveProseChecklistItem, { item: { checked: false, text: task, continuations: [], children: [] }, idx: idx, j: block.items.length + ti, theme: theme, accent: accent, citations: citations, checkedOverride: isC, onToggleOverride: () => setCheckedMap((prev) => ({ ...prev, [`extra-${ti}`]: !isC })) }, `extra-${ti}`));
                    })] }), isAdding ? ((0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', alignItems: 'center', gap: 6, marginTop: 2 }, children: [(0, jsx_runtime_1.jsx)("input", { type: "text", value: newTaskText, onChange: (e) => setNewTaskText(e.target.value), placeholder: "Type a new task\u2026", style: {
                            flex: 1,
                            padding: '5px 10px',
                            borderRadius: 8,
                            border: '1px solid rgba(124, 92, 252, 0.35)',
                            background: theme.card,
                            color: theme.text,
                            fontSize: 12.5,
                        } }), (0, jsx_runtime_1.jsx)("button", { type: "button", onClick: () => {
                            const t = newTaskText.trim();
                            if (t) {
                                setExtraTasks((prev) => [...prev, t]);
                                setNewTaskText('');
                                setIsAdding(false);
                            }
                        }, style: {
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: 4,
                            padding: '4px 10px',
                            borderRadius: 999,
                            border: 'none',
                            background: '#6C4CF6',
                            color: '#FFFFFF',
                            fontSize: 11.5,
                            fontWeight: 700,
                            cursor: 'pointer',
                        }, children: "Add" }), (0, jsx_runtime_1.jsx)("button", { type: "button", onClick: () => {
                            setNewTaskText('');
                            setIsAdding(false);
                        }, style: {
                            border: 'none',
                            background: 'transparent',
                            color: theme.text2,
                            fontSize: 11.5,
                            cursor: 'pointer',
                        }, children: "Cancel" })] })) : ((0, jsx_runtime_1.jsx)("div", { children: (0, jsx_runtime_1.jsxs)("button", { type: "button", onClick: () => setIsAdding(true), style: {
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: 5,
                        padding: '4px 10px',
                        borderRadius: 999,
                        fontSize: 11,
                        fontWeight: 700,
                        color: '#5B3DF5',
                        background: 'rgba(124, 92, 252, 0.10)',
                        border: '1px solid rgba(124, 92, 252, 0.24)',
                        cursor: 'pointer',
                    }, children: [(0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { name: "plus", tone: "lavender", size: 16, glyph: 10 }), (0, jsx_runtime_1.jsx)("span", { children: "Add Task" })] }) }))] }));
}
function renderProseBlock(block, idx, theme, accent, citations, sectionMode = 'default') {
    switch (block.kind) {
        case 'paragraph': {
            const callout = matchCalloutText(block.text);
            if (callout) {
                return renderCalloutCard(callout, idx, theme, accent, citations);
            }
            return ((0, jsx_runtime_1.jsx)("div", { style: { color: theme.text, whiteSpace: 'pre-wrap' }, children: renderInline(block.text, theme, accent, citations, `b${idx}`) }, idx));
        }
        case 'heading': {
            const sizes = { 1: 21, 2: 17.5, 3: 15.5, 4: 14.5, 5: 13.5, 6: 13 };
            const Tag = `h${Math.min(6, Math.max(1, block.level))}`;
            const badge = block.level >= 2 && block.level <= 3 ? sectionBadgeForHeading(block.text) : null;
            const { rest } = (0, icons_1.extractLeadingIcon)(block.text.trim());
            const cleanHeadingText = badge ? (0, icons_1.cleanPlainText)(rest).trim() || block.text : block.text;
            return ((0, jsx_runtime_1.jsxs)(Tag, { style: {
                    margin: '6px 0 0',
                    fontSize: sizes[block.level] ?? 15,
                    fontWeight: 700,
                    lineHeight: 1.35,
                    color: block.level === 6 ? theme.text2 : theme.text,
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                }, children: [badge && (0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { name: badge.icon, tone: badge.tone, size: 24, glyph: 13 }), (0, jsx_runtime_1.jsx)("span", { children: renderInline(cleanHeadingText, theme, accent, citations, `h${idx}`) })] }, idx));
        }
        case 'orderedList': {
            const extractedSources = block.items
                .map((it, j) => extractCitationFromProseItem(block.start + j, it.text))
                .filter((x) => x !== null);
            if (extractedSources.length > 0 && (sectionMode === 'sources' || extractedSources.length === block.items.length)) {
                return (0, jsx_runtime_1.jsx)(CitationsSourceCards, { citations: extractedSources, theme: theme, hideHeader: sectionMode === 'sources' }, idx);
            }
            return ((0, jsx_runtime_1.jsx)("ol", { start: block.start, style: {
                    margin: 0,
                    paddingLeft: 0,
                    listStyleType: 'none',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 8,
                    color: theme.text,
                }, children: block.items.map((item, j) => {
                    const stepNum = block.start + j;
                    const split = splitStepTitleAndDetail(item.text);
                    return ((0, jsx_runtime_1.jsxs)("li", { style: {
                            display: 'flex',
                            alignItems: 'flex-start',
                            gap: 10,
                        }, children: [(0, jsx_runtime_1.jsx)("span", { "aria-hidden": true, style: {
                                    display: 'inline-flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    width: 24,
                                    height: 24,
                                    borderRadius: 12,
                                    background: 'rgba(124, 92, 252, 0.14)',
                                    border: '1px solid rgba(124, 92, 252, 0.26)',
                                    color: '#5B3DF5',
                                    fontSize: 12,
                                    fontWeight: 700,
                                    flexShrink: 0,
                                    marginTop: 1,
                                }, children: stepNum }), (0, jsx_runtime_1.jsxs)("div", { style: { flex: 1, minWidth: 0 }, children: [split ? ((0, jsx_runtime_1.jsxs)(jsx_runtime_1.Fragment, { children: [(0, jsx_runtime_1.jsx)("div", { style: { fontWeight: 700, color: theme.text, lineHeight: 1.35 }, children: renderInline(split.title, theme, accent, citations, `olt${idx}-${j}`) }), (0, jsx_runtime_1.jsx)("div", { style: { fontSize: 13, color: theme.text2, marginTop: 2, lineHeight: 1.45 }, children: renderInline(split.detail, theme, accent, citations, `old${idx}-${j}`) })] })) : ((0, jsx_runtime_1.jsx)("span", { children: renderInline(item.text, theme, accent, citations, `li${idx}-${j}`) })), item.continuations.map((c, ci) => ((0, jsx_runtime_1.jsx)("div", { style: { marginTop: 2, fontSize: 13, color: theme.text2 }, children: renderInline(c, theme, accent, citations, `lic${idx}-${j}-${ci}`) }, ci))), item.children.length > 0 && ((0, jsx_runtime_1.jsx)("div", { style: { marginTop: 6 }, children: item.children.map((ch, chi) => renderProseBlock(ch, chi, theme, accent, citations, sectionMode)) }))] })] }, j));
                }) }, idx));
        }
        case 'bulletList': {
            if (sectionMode === 'sources') {
                const extractedSources = block.items
                    .map((it, j) => extractCitationFromProseItem(j + 1, it.text))
                    .filter((x) => x !== null);
                if (extractedSources.length > 0) {
                    return (0, jsx_runtime_1.jsx)(CitationsSourceCards, { citations: extractedSources, theme: theme, hideHeader: true }, idx);
                }
            }
            const isTodoSection = sectionMode === 'todo' || block.items.some((it) => it.checked !== undefined);
            if (isTodoSection) {
                return ((0, jsx_runtime_1.jsx)(InteractiveProseTodoList, { block: block, idx: idx, theme: theme, accent: accent, citations: citations }, idx));
            }
            const isCheckmarkSection = sectionMode === 'checkmark';
            return ((0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', flexDirection: 'column', gap: 6 }, children: (0, jsx_runtime_1.jsx)("ul", { style: {
                        margin: 0,
                        paddingLeft: 0,
                        display: 'flex',
                        flexDirection: 'column',
                        gap: 6,
                        color: theme.text,
                        listStyleType: 'none',
                    }, children: block.items.map((item, j) => {
                        const { icon: leadTok, rest: stripped } = (0, icons_1.extractLeadingIcon)(item.text);
                        const boldLeadMatch = !leadTok ? item.text.trim().match(/^\*\*([^*]+)\*\*/) : null;
                        const sem = leadTok
                            ? { icon: leadTok, tone: 'lavender' }
                            : boldLeadMatch
                                ? (0, icons_1.semanticIconFor)(boldLeadMatch[1], '')
                                : { icon: '', tone: 'lavender' };
                        const resolvedIcon = isCheckmarkSection ? (leadTok || sem.icon || 'circle-check') : (leadTok || (sem.icon && sem.icon !== 'sparkles' ? sem.icon : ''));
                        const resolvedTone = isCheckmarkSection && !leadTok && !sem.icon ? 'green' : sem.tone;
                        const bodyStr = leadTok ? stripped : item.text;
                        return ((0, jsx_runtime_1.jsxs)("li", { style: { display: 'flex', alignItems: 'flex-start', gap: 8 }, children: [resolvedIcon ? ((0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { name: resolvedIcon, tone: resolvedTone, size: 20, glyph: 12, style: { marginTop: 2, flexShrink: 0 } })) : ((0, jsx_runtime_1.jsx)("span", { style: { color: accent, fontWeight: 800, marginTop: 1, flexShrink: 0 }, children: "\u2022" })), (0, jsx_runtime_1.jsxs)("div", { style: { flex: 1, minWidth: 0 }, children: [(0, jsx_runtime_1.jsx)("span", { children: renderInline(bodyStr, theme, accent, citations, `li${idx}-${j}`) }), item.continuations.map((c, ci) => ((0, jsx_runtime_1.jsx)("div", { style: { marginTop: 2 }, children: renderInline(c, theme, accent, citations, `lic${idx}-${j}-${ci}`) }, ci))), item.children.length > 0 && ((0, jsx_runtime_1.jsx)("div", { style: { marginTop: 4 }, children: item.children.map((ch, chi) => renderProseBlock(ch, chi, theme, accent, citations, sectionMode)) }))] })] }, j));
                    }) }) }, idx));
        }
        case 'blockquote': {
            const firstBlock = block.blocks[0];
            if (firstBlock?.kind === 'paragraph') {
                const callout = matchCalloutText(firstBlock.text);
                if (callout) {
                    const rest = block.blocks.slice(1);
                    return renderCalloutCard(callout, idx, theme, accent, citations, rest.length > 0 ? ((0, jsx_runtime_1.jsx)("div", { style: { marginTop: 6, display: 'flex', flexDirection: 'column', gap: 6 }, children: rest.map((inner, j) => renderProseBlock(inner, j, theme, accent, citations)) })) : undefined);
                }
            }
            return ((0, jsx_runtime_1.jsx)("blockquote", { style: {
                    margin: 0,
                    padding: '10px 14px',
                    borderRadius: 12,
                    background: 'rgba(124, 92, 252, 0.08)',
                    borderLeft: `3px solid ${accent}`,
                    color: theme.text2,
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 8,
                }, children: block.blocks.map((inner, j) => renderProseBlock(inner, j, theme, accent, citations)) }, idx));
        }
        case 'table':
            return (0, jsx_runtime_1.jsx)(ProseTableGrid, { block: block, idx: idx, theme: theme, accent: accent, citations: citations }, idx);
        case 'divider':
            return (0, jsx_runtime_1.jsx)("hr", { style: { border: 'none', borderTop: `1px solid ${theme.border}`, margin: '4px 0' } }, idx);
        case 'math':
            return ((0, jsx_runtime_1.jsx)("div", { style: {
                    padding: '10px 14px',
                    borderRadius: 10,
                    background: theme.card,
                    border: `1px solid ${theme.border}`,
                    fontFamily: 'ui-serif, Georgia, Cambria, "Times New Roman", serif',
                    fontStyle: 'italic',
                    color: theme.text,
                    overflowX: 'auto',
                }, children: block.latex }, idx));
        case 'image':
            return ((0, jsx_runtime_1.jsxs)("figure", { style: { margin: 0 }, children: [(0, jsx_runtime_1.jsx)("img", { src: block.url, alt: block.alt, loading: "lazy", style: { maxWidth: '100%', borderRadius: 10, border: `1px solid ${theme.border}` } }), block.alt && ((0, jsx_runtime_1.jsx)("figcaption", { style: { fontSize: tokens_1.TYPE.micro, color: theme.text3, marginTop: 4 }, children: block.alt }))] }, idx));
        case 'collapsible':
            return ((0, jsx_runtime_1.jsxs)("details", { style: {
                    padding: '8px 12px',
                    borderRadius: 10,
                    background: theme.card,
                    border: `1px solid ${theme.border}`,
                    color: theme.text,
                }, children: [(0, jsx_runtime_1.jsx)("summary", { style: { cursor: 'pointer', fontWeight: 600 }, children: renderInline(block.summary, theme, accent, citations, `sum${idx}`) }), (0, jsx_runtime_1.jsx)("div", { style: { marginTop: 8, display: 'flex', flexDirection: 'column', gap: 8 }, children: block.blocks.map((inner, j) => renderProseBlock(inner, j, theme, accent, citations)) })] }, idx));
    }
}
function Prose({ text, theme, accent, citations, }) {
    const blocks = (0, react_1.useMemo)(() => {
        const raw = parseProseBlocks(text);
        return raw.map((b) => {
            if (b.kind === 'paragraph') {
                const m = b.text.trim().match(/^\*\*([^*]+)\*\*\s*:?$/);
                if (m) {
                    const clean = (0, icons_1.cleanPlainText)(m[1]).trim();
                    if (clean.length >= 3 && clean.length <= 72 && !clean.endsWith('.') && !classifyCalloutHeader(b.text)) {
                        return { kind: 'heading', level: 3, text: m[1].trim() };
                    }
                }
            }
            return b;
        });
    }, [text]);
    const rendered = [];
    let currentMode = 'default';
    let i = 0;
    while (i < blocks.length) {
        const block = blocks[i];
        const nextBlock = blocks[i + 1];
        // Coalesce Heading("Quick summary" | "Important" | "Key Insights" | "Quick Fact") + Paragraph/BulletList into one Callout Card
        const headerCallout = block.kind === 'heading'
            ? classifyCalloutHeader(block.text)
            : block.kind === 'paragraph' && /^\*\*[^*]+\*\*\s*:?$/.test(block.text.trim())
                ? classifyCalloutHeader(block.text)
                : null;
        if (headerCallout && (nextBlock?.kind === 'paragraph' || nextBlock?.kind === 'bulletList')) {
            const bodyText = nextBlock.kind === 'paragraph' ? nextBlock.text : '';
            const extra = nextBlock.kind === 'bulletList'
                ? renderProseBlock(nextBlock, i + 1, theme, accent, citations, 'default')
                : undefined;
            rendered.push(renderCalloutCard({
                tone: headerCallout.tone,
                icon: headerCallout.icon,
                title: headerCallout.title,
                body: bodyText,
            }, `coal-${i}`, theme, accent, citations, extra));
            i += 2;
            continue;
        }
        if (block.kind === 'heading') {
            currentMode = classifySectionHeadingMode(block.text);
        }
        else if (block.kind !== 'bulletList' && block.kind !== 'orderedList') {
            currentMode = 'default';
        }
        rendered.push(renderProseBlock(block, i, theme, accent, citations, currentMode));
        i += 1;
    }
    return ((0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', flexDirection: 'column', gap: 10, color: theme.text }, children: rendered }));
}
function isAcademicCitationDomain(domain, url) {
    const hay = `${domain} ${url}`.toLowerCase();
    return (hay.includes('.edu') ||
        hay.includes('nature.com') ||
        hay.includes('arxiv.org') ||
        hay.includes('ieee.org') ||
        hay.includes('acm.org') ||
        hay.includes('springer.') ||
        hay.includes('sciencedirect.') ||
        hay.includes('oxford') ||
        hay.includes('cambridge') ||
        hay.includes('nih.gov') ||
        hay.includes('pubmed'));
}
function CitationsSourceCards({ citations, theme, hideHeader = false, }) {
    return ((0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', flexDirection: 'column', gap: 8, marginTop: 4 }, children: [!hideHeader && ((0, jsx_runtime_1.jsxs)("div", { style: {
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    fontSize: 13.5,
                    fontWeight: 700,
                    color: theme.text,
                }, children: [(0, jsx_runtime_1.jsxs)("span", { style: { display: 'inline-flex', alignItems: 'center', gap: 6 }, children: [(0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { name: "link", tone: "blue", size: 22, glyph: 12 }), (0, jsx_runtime_1.jsx)("span", { children: "Key Sources" })] }), (0, jsx_runtime_1.jsxs)("span", { style: { fontSize: 12, fontWeight: 600, color: '#6C4CF6' }, children: [citations.length, " ", citations.length === 1 ? 'source' : 'sources'] })] })), citations.map((src) => {
                const domain = src.domain ||
                    (() => {
                        try {
                            return new URL(src.url).hostname.replace(/^www\./, '');
                        }
                        catch {
                            return src.url;
                        }
                    })();
                const academic = isAcademicCitationDomain(domain, src.url);
                return ((0, jsx_runtime_1.jsxs)("a", { href: src.url, target: "_blank", rel: "noopener noreferrer", style: {
                        display: 'flex',
                        alignItems: 'flex-start',
                        gap: 10,
                        padding: '11px 13px',
                        borderRadius: 14,
                        background: theme.card,
                        border: `1px solid ${theme.border}`,
                        textDecoration: 'none',
                        color: theme.text,
                        boxShadow: '0 4px 14px rgba(70, 52, 150, 0.05)',
                    }, children: [(0, jsx_runtime_1.jsx)("span", { style: {
                                display: 'inline-flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                width: 26,
                                height: 26,
                                borderRadius: 13,
                                background: 'rgba(124, 92, 252, 0.14)',
                                border: '1px solid rgba(124, 92, 252, 0.25)',
                                color: '#5B3DF5',
                                fontSize: 12,
                                fontWeight: 700,
                                flexShrink: 0,
                                marginTop: 1,
                            }, children: src.ordinal }), (0, jsx_runtime_1.jsxs)("div", { style: { flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 4 }, children: [(0, jsx_runtime_1.jsx)("div", { style: { fontWeight: 700, fontSize: 13.5, color: theme.text, lineHeight: 1.35 }, children: src.title }), src.snippet && ((0, jsx_runtime_1.jsx)("div", { style: { fontSize: 12.5, color: theme.text2, lineHeight: 1.45 }, children: src.snippet })), (0, jsx_runtime_1.jsxs)("div", { style: {
                                        display: 'flex',
                                        alignItems: 'center',
                                        justifyContent: 'space-between',
                                        gap: 8,
                                        marginTop: 2,
                                        flexWrap: 'wrap',
                                    }, children: [(0, jsx_runtime_1.jsxs)("span", { style: {
                                                display: 'inline-flex',
                                                alignItems: 'center',
                                                gap: 4,
                                                fontSize: 12,
                                                fontWeight: 600,
                                                color: '#5B3DF5',
                                            }, children: [(0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { name: "external-link", tone: "indigo", size: 16, glyph: 10 }), (0, jsx_runtime_1.jsx)("span", { children: domain })] }), (0, jsx_runtime_1.jsxs)("span", { style: {
                                                display: 'inline-flex',
                                                alignItems: 'center',
                                                gap: 4,
                                                padding: '2px 8px',
                                                borderRadius: 999,
                                                fontSize: 11,
                                                fontWeight: 700,
                                                background: academic ? 'rgba(124, 92, 252, 0.14)' : 'rgba(18, 154, 90, 0.14)',
                                                color: academic ? '#5B3DF5' : '#129A5A',
                                            }, children: [(0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { name: academic ? 'building-bank' : 'shield-check', tone: academic ? 'indigo' : 'green', size: 15, glyph: 9 }), (0, jsx_runtime_1.jsx)("span", { children: academic ? 'Academic' : 'Trusted Source' })] })] })] })] }, src.ordinal));
            })] }));
}
function deriveContextualActionPills(segments, rawText, citations) {
    const lastSeg = segments[segments.length - 1];
    if (lastSeg?.kind === 'widget' && ['actions', 'followups', 'chips', 'menu'].includes(lastSeg.widget)) {
        return [];
    }
    const hasChart = segments.some((s) => s.kind === 'widget' && s.widget === 'chart');
    const hasTable = segments.some((s) => s.kind === 'widget' && s.widget === 'table') || /\|[^\n]+\|\s*\n\s*\|[\s:-]+\|/.test(rawText);
    const hasCode = segments.some((s) => s.kind === 'code');
    const normalizedText = (0, icons_1.emojiToIconTokens)(rawText);
    const hasSources = (citations && citations.length > 0) ||
        /##+\s*(?::[a-z0-9-]+:\s*)?(?:Key\s+)?Sources/i.test(normalizedText);
    const hasPlanOrSteps = /^\s*1\.\s+\*\*/m.test(rawText) ||
        /^\s*[-*]\s+\[[ xX]\]/m.test(rawText) ||
        /##+\s*(?::[a-z0-9-]+:\s*)?(?:Next\s+actions|Your\s+Todo\s+List)/i.test(normalizedText);
    if (hasChart && hasTable && hasCode) {
        return [
            { icon: 'message-circle', label: 'Ask follow-up' },
            { icon: 'chart-bar', label: 'Show me a detailed report' },
            { icon: 'sparkles', label: 'Create a presentation' },
        ];
    }
    if (hasChart) {
        return [
            { icon: 'download', label: 'Download Report' },
            { icon: 'arrows-maximize', label: 'View Details' },
        ];
    }
    if (hasTable) {
        return [
            { icon: 'chart-bar', label: 'View Full Report' },
            { icon: 'share', label: 'Share' },
        ];
    }
    if (hasSources) {
        return [
            { icon: 'external-link', label: 'View All Sources' },
            { icon: 'clipboard', label: 'Copy Citations' },
            { icon: 'book-2', label: 'Learn More' },
        ];
    }
    if (hasCode) {
        return [
            { icon: 'code', label: 'Edit Code' },
            { icon: 'download', label: 'Download Project' },
            { icon: 'message-circle', label: 'Ask Follow Up' },
        ];
    }
    if (hasPlanOrSteps) {
        return [
            { icon: 'calendar', label: 'Make a timeline' },
            { icon: 'arrows-maximize', label: 'Expand plan' },
            { icon: 'share', label: 'Share' },
        ];
    }
    return [];
}
function MessageContent({ text, dark = true, onPrompt, streaming = false, citations, }) {
    const segments = (0, react_1.useMemo)(() => (0, stream_1.parseStream)((0, icons_1.sanitizeReplyEmojis)(text)), [text]);
    const theme = (0, react_1.useMemo)(() => (0, tokens_1.themeFor)(dark), [dark]);
    const accent = segments.find((s) => s.kind === 'widget')?.payload.accent;
    const contextualPills = (0, react_1.useMemo)(() => (streaming ? [] : deriveContextualActionPills(segments, text, citations)), [segments, text, citations, streaming]);
    return ((0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', flexDirection: 'column', gap: 14, fontSize: tokens_1.TYPE.body, lineHeight: 1.65 }, children: [segments.map((segment, i) => {
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
            }), citations && citations.length > 0 && (0, jsx_runtime_1.jsx)(CitationsSourceCards, { citations: citations, theme: theme }), contextualPills.length > 0 && ((0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', flexWrap: 'wrap', gap: 8, marginTop: 2 }, children: contextualPills.map((pill, idx) => ((0, jsx_runtime_1.jsxs)("button", { type: "button", onClick: () => onPrompt?.(pill.label), style: {
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: 6,
                        padding: '6px 12px',
                        borderRadius: 999,
                        border: `1px solid ${theme.border}`,
                        background: theme.card,
                        color: theme.text,
                        fontSize: 12,
                        fontWeight: 650,
                        cursor: 'pointer',
                    }, children: [(0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { name: pill.icon, tone: "lavender", size: 18, glyph: 11 }), (0, jsx_runtime_1.jsx)("span", { children: pill.label })] }, idx))) }))] }));
}
