"use strict";
/**
 * The Nexly plan contract.
 *
 * The model does not write a markdown table for a timetable. It writes one
 * fenced `plan` payload. The renderer validates every row and every icon; the
 * wire is therefore safe to render in a glass card without a markdown parser
 * guessing at alignment.
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.NEXLY_ICON_GUIDE = exports.NEXLY_ICON_COUNT = exports.canonicalIconName = exports.NEXLY_PLAN_PROMPT = void 0;
exports.isNexlyPlanPayload = isNexlyPlanPayload;
exports.parseNexlyPlan = parseNexlyPlan;
exports.extractNexlyPlan = extractNexlyPlan;
exports.parseNexlyReply = parseNexlyReply;
const icons_1 = require("./icons");
Object.defineProperty(exports, "canonicalIconName", { enumerable: true, get: function () { return icons_1.canonicalIconName; } });
Object.defineProperty(exports, "NEXLY_ICON_COUNT", { enumerable: true, get: function () { return icons_1.NEXLY_ICON_COUNT; } });
Object.defineProperty(exports, "NEXLY_ICON_GUIDE", { enumerable: true, get: function () { return icons_1.NEXLY_ICON_GUIDE; } });
const str = (value, max = 400) => typeof value === 'string'
    ? value
        .replace(/\*\*(.+?)\*\*/g, '$1')
        .replace(/__(.+?)__/g, '$1')
        .replace(/~~(.+?)~~/g, '$1')
        .replace(/`([^`]+)`/g, '$1')
        .replace(/\s+/g, ' ')
        .trim()
        .slice(0, max)
    : '';
const PLAN_WRAPPER_KEYS = ['plan', 'schedule', 'timetable'];
function unwrapPlan(raw) {
    for (const key of PLAN_WRAPPER_KEYS) {
        const inner = raw[key];
        if (inner && typeof inner === 'object' && !Array.isArray(inner)) {
            return inner;
        }
    }
    return raw;
}
function planRows(value) {
    for (const key of ['rows', 'items']) {
        const rows = value[key];
        if (Array.isArray(rows))
            return rows;
    }
    return [];
}
/** True when a payload contains at least one timed row. Used by inference. */
function isNexlyPlanPayload(value) {
    if (!value || typeof value !== 'object' || Array.isArray(value))
        return false;
    const root = unwrapPlan(value);
    return planRows(root).some((row) => {
        if (!row || typeof row !== 'object' || Array.isArray(row))
            return false;
        const r = row;
        return Boolean(str(r.time ?? r.start ?? r.from ?? r.date ?? r.slot, 40)) &&
            Boolean(str(r.text ?? r.label ?? r.title ?? r.activity ?? r.detail ?? r.task, 180));
    });
}
/** Validate one plan payload from the wire. Returns null if there is no plan. */
function parseNexlyPlan(value) {
    if (!value || typeof value !== 'object' || Array.isArray(value))
        return null;
    const root = unwrapPlan(value);
    const rows = planRows(root)
        .slice(0, 14)
        .map((row) => {
        if (!row || typeof row !== 'object' || Array.isArray(row))
            return null;
        const r = row;
        const time = str(r.time ?? r.start ?? r.from ?? r.date ?? r.slot, 40);
        const text = str(r.text ?? r.label ?? r.title ?? r.activity ?? r.detail ?? r.task, 180);
        if (!time || !text)
            return null;
        const icon = (0, icons_1.iconNameFor)(r.icon);
        return {
            time,
            text,
            icon,
            tone: (0, icons_1.glassToneFor)(icon, r.tone),
        };
    })
        .filter((row) => row !== null);
    if (!rows.length)
        return null;
    const closing = str(root.closing ?? root.footer, 220);
    const closingIcon = root.closingIcon !== undefined ? (0, icons_1.iconNameFor)(root.closingIcon) : undefined;
    return {
        title: str(root.title ?? root.heading, 160) || "Here's your plan:",
        rows,
        closing: closing ? (0, icons_1.emojiToIconTokens)(closing) : undefined,
        closingIcon,
        timestamp: str(root.timestamp, 24) || undefined,
    };
}
/** Extract the first ```plan fence from a raw model reply. */
function extractNexlyPlan(text) {
    const re = /```plan[ \t]*\r?\n([\s\S]*?)```/i;
    const match = re.exec(text);
    if (!match)
        return { preamble: text.trim(), plan: null };
    const preamble = text.slice(0, match.index).trim();
    try {
        const parsed = JSON.parse(match[1]);
        return { preamble, plan: parseNexlyPlan(parsed), raw: match[1] };
    }
    catch {
        return { preamble, plan: null, raw: match[1] };
    }
}
/** Parse a full reply (preamble + optional plan fence). */
function parseNexlyReply(text) {
    const { preamble, plan } = extractNexlyPlan(text);
    return { preamble, plan: plan ?? undefined };
}
exports.NEXLY_PLAN_PROMPT = `SCHEDULE / TIMETABLE RESPONSES

When the user asks for a schedule, timetable, itinerary, routine or day plan, you MUST answer with exactly ONE fenced plan block. Never answer a schedule request with a markdown table, bullet list, numbered list, or a sequence of prose paragraphs describing times.

Put one short lead-in sentence before the fence. The app renders the Thinking / Reasoning / Effort card itself, so do not narrate those stages row by row; the fence is the answer.

\`\`\`plan
{
  "title": "Here's your personalized plan for tomorrow:",
  "rows": [
    { "time": "8:00 – 8:30 AM", "icon": "sunrise", "tone": "amber", "text": "Morning routine + light exercise" },
    { "time": "9:00 – 11:00 AM", "icon": "book-2", "text": "Classes" }
  ],
  "closing": "You got this, Arafat!",
  "closingIcon": "flame",
  "timestamp": "9:45 PM"
}
\`\`\`

Rules:
1. rows: EXACTLY 9 for a full-day plan. Do not emit 10 or more. A shorter plan may use 5–8; never exceed 9 in a normal answer. EVERY row needs a time, an icon and text.
2. time: "8:00 – 8:30 AM" style, with an en dash; keep both endpoints inside one field.
3. icon: required, kebab-case, from the ${icons_1.NEXLY_ICON_COUNT.toLocaleString('en-US')}-glyph Nexly glass registry. Prefer this vocabulary — ${icons_1.NEXLY_ICON_GUIDE} — or any other exact Tabler kebab-case id. Do not invent decorative names; an unknown name falls back to a sparkle.
4. tone: optional but recommended — amber for morning, lavender for study, blue for work/play, green for food, pink for health, lilac for rest, peach for motivation, mint for effort. The renderer has a semantic default for the reference icons when tone is omitted.
5. text: the activity itself in 2–8 words, with no time prefix (time is a separate field).
6. closing: one short encouragement. closingIcon is optional; :flame: inline inside closing is also allowed.
7. title: a single short sentence. timestamp is optional, e.g. "9:45 PM".
8. NEVER use emoji, emoticons or unicode pictographs anywhere in the reply. Use :sunrise: for a single inline icon or the icon field for a tile. The renderer replaces known emoji with glass glyphs and drops the rest.
9. The fence body must be strict JSON: double quotes, no trailing comma, no comment, no ellipsis, no placeholder.
10. Do not wrap the plan in another fence and do not repeat the rows after it.
11. Write the title, row text and closing in the user's language; icon names stay English kebab-case.`;
