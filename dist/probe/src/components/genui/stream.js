"use strict";
/**
 * The stream parser — markdown text with widget fences -> renderable segments.
 *
 * This is the client twin of the backend's extractWidgetFences(): the same
 * fence grammar (```kind\n{...}\n```), the same bare-payload promotion, and
 * the same streaming rule — an unclosed fence is a SKELETON, never raw JSON.
 * Runs on the client so a streamed answer renders block-by-block as it
 * arrives, and on completed answers verbatim.
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.parseStream = parseStream;
/** Languages that are raw source blocks, not JSON widgets. */
const RAW_BLOCKS = new Set(['json', 'javascript', 'js', 'typescript', 'ts', 'python', 'py', 'bash', 'sh', 'shell', 'sql', 'java', 'kotlin', 'swift', 'go', 'rust', 'c', 'cpp', 'css', 'yaml', 'toml', 'text', '']);
/** The widget-ish kinds a fence language can name. */
const WIDGET_LANGS = new Set([
    'chart', 'stat', 'table', 'compare', 'progress', 'timeline', 'gauge', 'quiz', 'form',
    'calc', 'rating', 'poll', 'checklist', 'chips', 'menu', 'voice', 'tabs', 'accordion',
    'steps', 'tree', 'carousel', 'cards', 'followups', 'actions', 'panel', 'flashcards',
    'diff', 'media', 'app',
    // v3 kinds
    'sim', 'stage3d', 'canvas', 'editor', 'terminal',
    'collab',
    'counter', 'vote', 'todo', 'pointer',
    // v3 sub-type fences the backend promotes to kinds
    'cloth', 'softbody', 'fluid', 'smoke', 'particles', 'rigid', 'terrain', 'game',
    'scatter3d', 'surface3d', 'molecule', 'cad', 'anatomy', 'scene', 'vr', 'ar',
    'whiteboard', 'mindmap', 'diagram', 'polygons',
    'nodes', 'pivot', 'site', 'css',
    'violin', 'contour', 'parallel', 'dendrogram', 'force', 'depth',
    // sub-type fences the backend promotes to kinds
    'line', 'area', 'stackedarea', 'bar', 'hbar', 'stacked', 'grouped', 'pie', 'donut',
    'scatter', 'bubble', 'histogram', 'boxplot', 'heatmap', 'correlation', 'treemap',
    'waterfall', 'candlestick', 'gantt', 'funnel', 'radar', 'chord', 'quadrant', 'sankey',
    'network', 'wordcloud', 'sunburst', 'bullet',
    'image', 'gallery', 'video', 'audio', 'code', 'file', 'link', 'citation', 'search',
    'entity', 'product', 'listing', 'doc', 'document', 'pdf', 'embed',
    'unit', 'currency', 'finance', 'loan', 'mortgage', 'pricing', 'tax', 'budget',
    'investment', 'probability', 'statistics', 'scientific', 'datetime', 'health',
    'wizard', 'onboarding', 'kanban', 'calendar', 'sheet', 'tasks', 'dashboard', 'config',
    'configurator', 'booking', 'schedule', 'filter', 'analytics', 'report', 'files',
    'connected', 'decision', 'scenario', 'timer', 'map', 'mermaid', 'html', 'svg',
]);
const CANON = {
    stackedarea: 'chart', hbar: 'chart', stacked: 'chart', grouped: 'chart', donut: 'chart',
    boxplot: 'chart', heatmap: 'chart', correlation: 'chart', treemap: 'chart',
    waterfall: 'chart', candlestick: 'chart', gantt: 'chart', funnel: 'chart', radar: 'chart',
    chord: 'chart', quadrant: 'chart', sankey: 'chart', network: 'chart', wordcloud: 'chart',
    sunburst: 'chart', bullet: 'chart',
    violin: 'chart', contour: 'chart', parallel: 'chart', dendrogram: 'chart', force: 'chart', depth: 'chart',
    cloth: 'sim', softbody: 'sim', fluid: 'sim', smoke: 'sim', particles: 'sim', rigid: 'sim', terrain: 'sim', game: 'sim',
    scatter3d: 'stage3d', surface3d: 'stage3d', molecule: 'stage3d', cad: 'stage3d', anatomy: 'stage3d', scene: 'stage3d', vr: 'stage3d', ar: 'stage3d',
    whiteboard: 'canvas', mindmap: 'canvas', diagram: 'canvas', polygons: 'canvas',
    counter: 'collab', vote: 'collab', todo: 'collab', pointer: 'collab',
    nodes: 'editor', pivot: 'editor', site: 'editor', css: 'editor',
    image: 'media', gallery: 'media', video: 'media', audio: 'media', link: 'media',
    citation: 'media', entity: 'media', product: 'media', listing: 'media', doc: 'media',
    document: 'media', pdf: 'media', embed: 'media',
    unit: 'calc', currency: 'calc', finance: 'calc', loan: 'calc', mortgage: 'calc',
    pricing: 'calc', tax: 'calc', budget: 'calc', investment: 'calc', probability: 'calc',
    statistics: 'calc', scientific: 'calc', datetime: 'calc', health: 'calc',
    wizard: 'app', onboarding: 'app', kanban: 'app', calendar: 'app', sheet: 'app',
    tasks: 'app', dashboard: 'app', config: 'app', configurator: 'app', booking: 'app',
    schedule: 'app', filter: 'app', analytics: 'app', report: 'app', files: 'app',
    connected: 'app', decision: 'app', scenario: 'app', timer: 'app',
};
function canonicalKind(lang) {
    const l = lang.toLowerCase();
    if (WIDGET_LANGS.has(l)) {
        const kind = CANON[l] ?? (l === 'map' || l === 'mermaid' || l === 'html' || l === 'svg' ? l : l);
        if (CANON[l])
            return { kind, type: l };
        return { kind };
    }
    return { kind: l };
}
/** Parse a (possibly still-streaming) answer into segments. */
function parseStream(text) {
    const segments = [];
    const fence = /^```([a-zA-Z][\w+-]*)[ \t]*\r?\n/gm;
    let cursor = 0;
    const pushText = (chunk) => {
        if (chunk.trim().length > 0)
            segments.push({ kind: 'text', text: chunk.trim() });
    };
    while (cursor < text.length) {
        fence.lastIndex = cursor;
        const open = fence.exec(text);
        if (!open) {
            pushText(text.slice(cursor));
            break;
        }
        pushText(text.slice(cursor, open.index));
        const lang = open[1];
        const bodyStart = open.index + open[0].length;
        const close = text.indexOf('\n```', bodyStart);
        const complete = close !== -1;
        const body = complete ? text.slice(bodyStart, close) : text.slice(bodyStart);
        cursor = complete ? close + 4 : text.length;
        const lower = lang.toLowerCase();
        if (lower === 'mermaid') {
            segments.push({ kind: 'mermaid', source: body.trim() });
        }
        else if (lower === 'html') {
            segments.push({ kind: 'html', source: body.trim() });
        }
        else if (lower === 'svg') {
            segments.push({ kind: 'svg', source: body.trim() });
        }
        else if (RAW_BLOCKS.has(lower) && !WIDGET_LANGS.has(lower)) {
            segments.push({ kind: 'code', code: body.replace(/\n?$/, ''), language: lower || undefined });
        }
        else if (WIDGET_LANGS.has(lower)) {
            const payload = safeJson(body) ?? (complete ? undefined : {});
            if (payload !== undefined) {
                const { kind, type } = canonicalKind(lang);
                segments.push({ kind: 'widget', widget: kind, payload: type ? { type, ...payload } : payload, complete });
            }
            else {
                segments.push({ kind: 'code', code: body, language: lang });
            }
        }
        else {
            segments.push({ kind: 'code', code: body, language: lang });
        }
    }
    return segments;
}
function safeJson(raw) {
    const trimmed = raw.trim();
    if (!trimmed.startsWith('{'))
        return null;
    try {
        const parsed = JSON.parse(trimmed);
        return typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed) ? parsed : null;
    }
    catch {
        // best-effort streaming repair: close what is open so the skeleton
        // resolves into the real widget as chunks arrive
        const patched = closeBraces(trimmed);
        if (!patched)
            return null;
        try {
            const parsed = JSON.parse(patched);
            return typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed) ? parsed : null;
        }
        catch {
            return null;
        }
    }
}
function closeBraces(src) {
    let inString = false;
    let escaped = false;
    const stack = [];
    for (const ch of src) {
        if (inString) {
            if (escaped)
                escaped = false;
            else if (ch === '\\')
                escaped = true;
            else if (ch === '"')
                inString = false;
            continue;
        }
        if (ch === '"')
            inString = true;
        else if (ch === '{' || ch === '[')
            stack.push(ch);
        else if (ch === '}' || ch === ']')
            stack.pop();
    }
    if (!stack.length && !inString)
        return src;
    let out = src;
    if (inString)
        out += '"';
    // strip a dangling trailing comma, then close structures
    out = out.replace(/,\s*$/, '');
    while (stack.length)
        out += stack.pop() === '{' ? '}' : ']';
    return out;
}
