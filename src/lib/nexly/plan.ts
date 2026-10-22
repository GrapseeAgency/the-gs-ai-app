/**
 * The Nexly plan contract.
 *
 * The model does not write a markdown table for a timetable. It writes one
 * fenced `plan` payload. The renderer validates every row and every icon; the
 * wire is therefore safe to render in a glass card without a markdown parser
 * guessing at alignment.
 */

import {
  canonicalIconName,
  emojiToIconTokens,
  glassToneFor,
  iconNameFor,
  NEXLY_ICON_COUNT,
  NEXLY_ICON_GUIDE,
  type GlassTone,
} from './icons'

export interface NexlyPlanRow {
  time: string
  text: string
  icon: string
  tone?: GlassTone
}

export interface NexlyPlan {
  title: string
  rows: NexlyPlanRow[]
  closing?: string
  closingIcon?: string
  timestamp?: string
}

export interface NexlyThinkingRow {
  icon: string
  title: string
  subtitle: string
  tone?: GlassTone
}

export interface NexlyThinking {
  rows: NexlyThinkingRow[]
  active?: boolean
}

export interface NexlyReply {
  preamble: string
  thinking?: NexlyThinking
  plan?: NexlyPlan
}

const str = (value: unknown, max = 400): string =>
  typeof value === 'string'
    ? value
        .replace(/\*\*(.+?)\*\*/g, '$1')
        .replace(/__(.+?)__/g, '$1')
        .replace(/~~(.+?)~~/g, '$1')
        .replace(/`([^`]+)`/g, '$1')
        .replace(/\s+/g, ' ')
        .trim()
        .slice(0, max)
    : ''

const PLAN_WRAPPER_KEYS = ['plan', 'schedule', 'timetable'] as const

function unwrapPlan(raw: Record<string, unknown>): Record<string, unknown> {
  for (const key of PLAN_WRAPPER_KEYS) {
    const inner = raw[key]
    if (inner && typeof inner === 'object' && !Array.isArray(inner)) {
      return inner as Record<string, unknown>
    }
  }
  return raw
}

function planRows(value: Record<string, unknown>): unknown[] {
  for (const key of ['rows', 'items'] as const) {
    const rows = value[key]
    if (Array.isArray(rows)) return rows
  }
  return []
}

/** True when a payload contains at least one timed row. Used by inference. */
export function isNexlyPlanPayload(value: unknown): boolean {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false
  const root = unwrapPlan(value as Record<string, unknown>)
  return planRows(root).some((row) => {
    if (!row || typeof row !== 'object' || Array.isArray(row)) return false
    const r = row as Record<string, unknown>
    return Boolean(str(r.time ?? r.start ?? r.from ?? r.date ?? r.slot, 40)) &&
      Boolean(str(r.text ?? r.label ?? r.title ?? r.activity ?? r.detail ?? r.task, 180))
  })
}

/** Validate one plan payload from the wire. Returns null if there is no plan. */
export function parseNexlyPlan(value: unknown): NexlyPlan | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null
  const root = unwrapPlan(value as Record<string, unknown>)
  const rows: NexlyPlanRow[] = planRows(root)
    .slice(0, 14)
    .map((row): NexlyPlanRow | null => {
      if (!row || typeof row !== 'object' || Array.isArray(row)) return null
      const r = row as Record<string, unknown>
      const time = str(r.time ?? r.start ?? r.from ?? r.date ?? r.slot, 40)
      const text = str(r.text ?? r.label ?? r.title ?? r.activity ?? r.detail ?? r.task, 180)
      if (!time || !text) return null
      const icon = iconNameFor(r.icon)
      return {
        time,
        text,
        icon,
        tone: glassToneFor(icon, r.tone),
      }
    })
    .filter((row): row is NexlyPlanRow => row !== null)

  if (!rows.length) return null

  const closing = str(root.closing ?? root.footer, 220)
  const closingIcon = root.closingIcon !== undefined ? iconNameFor(root.closingIcon) : undefined
  return {
    title: str(root.title ?? root.heading, 160) || "Here's your plan:",
    rows,
    closing: closing ? emojiToIconTokens(closing) : undefined,
    closingIcon,
    timestamp: str(root.timestamp, 24) || undefined,
  }
}

/** Extract the first ```plan fence from a raw model reply. */
export function extractNexlyPlan(text: string): { preamble: string; plan: NexlyPlan | null; raw?: string } {
  const re = /```plan[ \t]*\r?\n([\s\S]*?)```/i
  const match = re.exec(text)
  if (!match) return { preamble: text.trim(), plan: null }
  const preamble = text.slice(0, match.index).trim()
  try {
    const parsed = JSON.parse(match[1]) as unknown
    return { preamble, plan: parseNexlyPlan(parsed), raw: match[1] }
  } catch {
    return { preamble, plan: null, raw: match[1] }
  }
}

/** Parse a full reply (preamble + optional plan fence). */
export function parseNexlyReply(text: string): NexlyReply {
  const { preamble, plan } = extractNexlyPlan(text)
  return { preamble, plan: plan ?? undefined }
}

export const NEXLY_PLAN_PROMPT = `SCHEDULE / TIMETABLE RESPONSES

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
1. rows: 5–9 for a full day (2–14 accepted). EVERY row needs a time, an icon and text.
2. time: "8:00 – 8:30 AM" style, with an en dash; keep both endpoints inside one field.
3. icon: required, kebab-case, from the ${NEXLY_ICON_COUNT.toLocaleString('en-US')}-glyph Nexly glass registry. Prefer this vocabulary — ${NEXLY_ICON_GUIDE} — or any other exact Tabler kebab-case id. Do not invent decorative names; an unknown name falls back to a sparkle.
4. tone: optional but recommended — amber for morning, lavender for study, blue for work/play, green for food, pink for health, lilac for rest, peach for motivation, mint for effort. The renderer has a semantic default for the reference icons when tone is omitted.
5. text: the activity itself in 2–8 words, with no time prefix (time is a separate field).
6. closing: one short encouragement. closingIcon is optional; :flame: inline inside closing is also allowed.
7. title: a single short sentence. timestamp is optional, e.g. "9:45 PM".
8. NEVER use emoji, emoticons or unicode pictographs anywhere in the reply. Use :sunrise: for a single inline icon or the icon field for a tile. The renderer replaces known emoji with glass glyphs and drops the rest.
9. The fence body must be strict JSON: double quotes, no trailing comma, no comment, no ellipsis, no placeholder.
10. Do not wrap the plan in another fence and do not repeat the rows after it.
11. Write the title, row text and closing in the user's language; icon names stay English kebab-case.`

export { canonicalIconName, NEXLY_ICON_COUNT, NEXLY_ICON_GUIDE }
export type { GlassTone }
