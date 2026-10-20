"use strict";
/**
 * The widget compiler: turns a finished answer into renderable widgets.
 *
 * Three paths, in order of preference:
 *
 *   1. MODEL     — the answer already contains valid widget fences. Validate,
 *                  normalise, cap. Nothing is added.
 *   2. REPAIRED  — a fence was malformed (trailing comma, smart quotes, one
 *                  missing brace). `repairJson` fixes it and the normalised
 *                  payload replaces the broken fence.
 *   3. FALLBACK  — the model produced no widget at all but the user asked for
 *                  a visual and the answer already states the numbers. The
 *                  compiler builds the widget deterministically, exactly like
 *                  the Android client does, so every client gets the same
 *                  result instead of each re-inventing it.
 *
 * This is provider-agnostic on purpose. GS's provider exposes no native tool
 * calling (see src/lib/websearch.ts), so the guarantee cannot come from the
 * model — it comes from this pipeline. When a tool-calling provider is wired
 * in, `tools.ts` plugs in at path 1 and this file still owns validation.
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.requestedChartType = requestedChartType;
exports.compileWidgets = compileWidgets;
exports.ensureWidgets = ensureWidgets;
const catalog_1 = require("./catalog");
const validate_1 = require("./validate");
const art_direct_1 = require("./art-direct");
const CHART_PHRASES = [
    ['horizontal bar', 'hbar'],
    ['stacked bar', 'stacked'],
    ['stacked area', 'stackedArea'],
    ['bar chart', 'bar'],
    ['bar graph', 'bar'],
    ['column chart', 'bar'],
    ['line chart', 'line'],
    ['line graph', 'line'],
    ['area chart', 'area'],
    ['pie chart', 'pie'],
    ['doughnut', 'donut'],
    ['donut', 'donut'],
    ['radar', 'radar'],
    ['funnel', 'funnel'],
    ['waterfall', 'waterfall'],
    ['treemap', 'treemap'],
    ['histogram', 'bar'],
    ['chart', 'bar'],
    ['graph', 'bar'],
    ['plot', 'bar'],
    ['visuali', 'bar'],
];
function requestedChartType(prompt) {
    const p = prompt.toLowerCase();
    if (p.includes('flowchart') ||
        p.includes('flow chart') ||
        p.includes('diagram') ||
        p.includes('mindmap') ||
        p.includes('org chart')) {
        return null;
    }
    const hit = CHART_PHRASES.find(([phrase]) => p.includes(phrase));
    return hit ? hit[1] : null;
}
function compileWidgets(input) {
    const { reply, userPrompt, priorText = '' } = input;
    const fences = (0, validate_1.extractWidgetFences)(reply);
    // No fenced widget? The payload may still be there, unfenced or inside a
    // ```json block — the model does this constantly. Promote it to a canonical
    // fence instead of shipping raw JSON to the user as body text.
    if (fences.length === 0) {
        const bare = (0, validate_1.extractBareWidget)(reply);
        if (bare)
            fences.push(bare);
    }
    const valid = [];
    const invalid = [];
    const errors = [];
    // Tracks the block above so two adjacent widgets never share a face. The
    // model's explicit accent/variant wins; this only breaks ties.
    const directions = [];
    for (const fence of fences) {
        let result = (0, validate_1.validateWidget)(fence.kind, fence.json);
        // A ```gantt fence whose payload is a SCHEDULE ({"entries":[{date,title}]})
        // and not a chart series: a gantt chart with no series is nothing, but a
        // timeline of dated entries is exactly what the model meant. Retry once as
        // a timeline before dropping the fence. The probe caught this live.
        if (!result.ok && fence.kind === 'chart' && /"entries"\s*:/.test(fence.json)) {
            const asTimeline = (0, validate_1.validateWidget)('timeline', fence.json);
            if (asTimeline.ok)
                result = asTimeline;
        }
        if (result.ok) {
            const payload = JSON.parse(result.json);
            const direction = (0, art_direct_1.directWidgetArt)({
                kind: result.kind,
                payload,
                prior: directions.length > 0 ? directions[directions.length - 1] : null,
                seen: directions,
                priorText,
            });
            directions.push(direction);
            const styled = JSON.stringify({
                ...payload,
                accent: direction.accent,
                variant: direction.variant,
            });
            valid.push({
                start: fence.start,
                end: fence.end,
                // Re-serialise with the guaranteed face. The style keys are validated
                // upstream, so this is the one place that decides what the client sees.
                // A payload already hugging the 64KB cap keeps its renderer default
                // rather than overflow the client's own limit by forty bytes.
                replacement: fenceBlock(result.kind, styled.length <= catalog_1.LIMITS.maxWidgetBytes ? styled : result.json),
                repaired: result.repaired,
            });
        }
        else {
            invalid.push({ start: fence.start, end: fence.end });
            errors.push(`${fence.kind}: ${(0, validate_1.validationErrors)(result).join(', ')}`);
        }
    }
    // Cap the answer at the documented maximum; extra widgets become prose.
    const kept = valid.slice(0, catalog_1.LIMITS.maxWidgetsPerAnswer);
    const overflow = valid.slice(catalog_1.LIMITS.maxWidgetsPerAnswer);
    if (kept.length > 0) {
        let text = rewrite(reply, [...kept, ...overflow.map((v) => ({ ...v, replacement: '' }))], invalid);
        return {
            text: text.trimEnd(),
            widgets: kept.length,
            path: kept.some((v) => v.repaired) ? 'repaired' : 'model',
            errors,
        };
    }
    // Path 3: no usable widget. Only build one when the user asked for it.
    const chartType = requestedChartType(userPrompt);
    if (!chartType) {
        return { text: strip(reply, invalid), widgets: 0, path: 'none', errors };
    }
    const points = pickPoints(reply, priorText);
    if (points.length < 2) {
        return { text: strip(reply, invalid), widgets: 0, path: 'none', errors };
    }
    const widget = buildChart(points, chartType);
    const direction = (0, art_direct_1.directWidgetArt)({ kind: 'chart', payload: widget, priorText });
    const styled = { ...widget, accent: direction.accent, variant: direction.variant, palette: direction.palette };
    const body = strip(reply, invalid).trimEnd();
    return {
        text: `${body}${body ? '\n\n' : ''}${fenceBlock('chart', JSON.stringify(styled))}`,
        widgets: 1,
        path: 'fallback',
        errors,
    };
}
/** Convenience wrapper for the turn executor: always returns a string. */
function ensureWidgets(text, opts) {
    return compileWidgets({ reply: text, userPrompt: opts.userPrompt, priorText: opts.priorText });
}
// ---------------------------------------------------------------------------
// Text surgery
// ---------------------------------------------------------------------------
function fenceBlock(kind, json) {
    return '```' + kind + '\n' + json + '\n```';
}
function rewrite(text, replacements, removals) {
    const edits = [...replacements, ...removals.map((r) => ({ ...r, replacement: '' }))]
        .sort((a, b) => b.start - a.start);
    let out = text;
    for (const edit of edits) {
        out = out.slice(0, edit.start) + edit.replacement + out.slice(edit.end);
    }
    return out;
}
function strip(text, removals) {
    return rewrite(text, [], removals);
}
// ---------------------------------------------------------------------------
// Deterministic extraction (mirrors the Android WidgetRecovery)
// ---------------------------------------------------------------------------
const BULLET = /^\s*(?:[-*+•·]|\d{1,2}[.)])\s+/;
const HEADING = /^#{1,6}\s+(.+)$/;
const NUMBERED = /^\d{1,2}[.)]\s+(.+)$/;
const SEPARATOR = /\s*[:=]\s*|\s+[—–]\s+/;
const NUMBER_AT_START = /^~?\s*(?:about\s+|approx\.?\s+|around\s+|over\s+|under\s+)?(?:[A-Z]{3}\s*)?[$€£¥₹]?\s*(-?\d{1,3}(?:,\d{3})+(?:\.\d+)?|-?\d+(?:\.\d+)?)(?:\s*[-–]\s*(\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d+(?:\.\d+)?))?\s*(%|percent|trillion|billion|million|thousand|tn|bn|mn|k|m|b|t)?/i;
function clean(s) {
    return s
        .replace(/[*_`]{1,3}/g, '')
        .trim()
        .replace(/^["'“”]+|["'“”]+$/g, '')
        .replace(/[.:;,]+$/, '')
        .trim();
}
function parseNumber(text) {
    const m = NUMBER_AT_START.exec(text.trim());
    if (!m)
        return null;
    const low = Number(m[1].replace(/,/g, ''));
    const high = m[2] ? Number(m[2].replace(/,/g, '')) : null;
    if (!Number.isFinite(low))
        return null;
    const raw = high !== null && Number.isFinite(high) ? (low + high) / 2 : low;
    const unit = (m[3] ?? '').toLowerCase();
    const factor = unit === 'trillion' || unit === 'tn' || unit === 't'
        ? 1e12
        : unit === 'billion' || unit === 'bn' || unit === 'b'
            ? 1e9
            : unit === 'million' || unit === 'mn' || unit === 'm'
                ? 1e6
                : unit === 'thousand' || unit === 'k'
                    ? 1e3
                    : 1;
    const display = unit === '%' || unit === 'percent'
        ? `${raw}%`
        : unit
            ? `${raw} ${unit}`
            : String(raw);
    return { value: raw * factor, display, percent: unit === '%' || unit === 'percent' };
}
function pickPoints(reply, prior) {
    const fromReply = extractPoints(reply);
    return fromReply.length >= 2 ? fromReply : extractPoints(prior);
}
function extractPoints(text) {
    if (!text.trim())
        return [];
    const rows = [];
    let section = '';
    for (const rawLine of text.split(/\r?\n/)) {
        const line = rawLine.trim();
        if (!line || line.startsWith('```'))
            continue;
        const h = HEADING.exec(line);
        if (h) {
            section = clean(h[1]);
            continue;
        }
        const n = NUMBERED.exec(line);
        if (n) {
            const title = clean(n[1]);
            if (title.length >= 2 && !/\d/.test(title) && !SEPARATOR.test(title)) {
                section = title;
                continue;
            }
        }
        const body = clean(line.replace(BULLET, ''));
        const sep = SEPARATOR.exec(body);
        if (!sep || sep.index === undefined)
            continue;
        const label = body.slice(0, sep.index).trim();
        if (label.length < 2 || label.length > 60 || !/[a-zA-Z]/.test(label))
            continue;
        if (label.includes('http'))
            continue;
        const rest = body.slice(sep.index + sep[0].length).trim();
        const num = parseNumber(rest);
        if (!num)
            continue;
        rows.push({ section, label, point: { label, ...num } });
    }
    if (!rows.length)
        return [];
    const counts = new Map();
    for (const r of rows)
        counts.set(r.label.toLowerCase(), (counts.get(r.label.toLowerCase()) ?? 0) + 1);
    const dominant = [...counts.entries()].sort((a, b) => b[1] - a[1])[0];
    const useSection = dominant !== undefined &&
        dominant[1] >= 2 &&
        dominant[1] * 2 >= rows.length &&
        rows.filter((r) => r.label.toLowerCase() === dominant[0] && r.section).length >= 2;
    const points = useSection
        ? rows
            .filter((r) => r.label.toLowerCase() === dominant[0] && r.section)
            .map((r) => ({ ...r.point, label: r.section }))
        : rows.map((r) => r.point);
    const seen = new Set();
    return points.filter((p) => {
        const key = p.label.toLowerCase();
        if (seen.has(key))
            return false;
        seen.add(key);
        return true;
    }).slice(0, 8);
}
function buildChart(points, type) {
    const labels = points.map((p) => p.label);
    const series = type === 'pie' || type === 'donut'
        ? points.map((p) => ({ name: p.label, data: [[0, p.value]] }))
        : [{ name: 'Value', data: points.map((p, i) => [i, p.value]) }];
    return {
        type,
        title: 'From the numbers in this answer',
        unit: '',
        labels,
        series,
    };
}
