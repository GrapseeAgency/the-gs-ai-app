/**
 * Validation and repair for model-emitted widgets.
 *
 * The model is untrusted input. Every payload is parsed, checked against the
 * catalogue, normalised (numbers, caps, enums) and only then allowed to reach
 * a client. A payload that cannot be repaired is dropped, never shipped.
 *
 * This is the layer that makes "the model emitted JSON" and "the UI can render
 * it" two different questions — the second one is answered here.
 */

import { evaluateExpr } from '../calc/evaluate'
import {
  APP_TYPES,
  COLLAB_TYPES,
  CALC_TYPES,
  CANVAS_TYPES,
  CHART_TYPES,
  EDITOR_TYPES,
  FORM_FIELD_TYPES,
  LIMITS,
  SIM_TYPES,
  STAGE3D_TYPES,
  MEDIA_TYPES,
  WIDGET_KINDS,
  canonicalChartType,
  injectWidgetType,
  isChartType,
  isWidgetKind,
  injectChartType,
  widgetKindOf,
  type WidgetKind,
} from './catalog'
import { SURFACES, PALETTES, type Palette, type Surface } from '../design/rulebook'
import { isNexlyPlanPayload, parseNexlyPlan } from '../nexly/plan'

export interface WidgetFence {
  kind: string
  json: string
  /** Byte offsets in the source text, so callers can rewrite precisely. */
  start: number
  end: number
}

export type ValidationResult =
  | {
      ok: true
      kind: WidgetKind
      widget: Record<string, unknown>
      json: string
      repaired: boolean
    }
  | { ok: false; kind: string; errors: string[] }

/** ```kind\n{...}\n``` (or ~~~kind, optional indent up to 8 spaces for list items, optional language/info string). */
const FENCE = /^[ \t]{0,8}(?:```|~~~)([a-zA-Z0-9][\w+-]*)?[ \t]*[^\r\n]*\r?\n([\s\S]*?)^[ \t]{0,8}(?:```|~~~)[ \t]*$/gm

/** Trailing unclosed fence when the stream or model truncated before the closing ```. */
const UNCLOSED_TAIL_FENCE = /(?:^|\r?\n)[ \t]{0,8}(?:```|~~~)([a-zA-Z0-9][\w+-]*)[ \t]*[^\r\n]*\r?\n([\s\S]+)$/

/** Generic fence labels models use when wrapping a widget JSON payload. */
const GENERIC_WIDGET_FENCES = new Set(['json', 'jsonc', 'widget', 'genui', 'data', 'ui', 'gsui', 'card_ui', 'block'])

/** Non-widget visual fences handled by dedicated client renderers. */
const NON_WIDGET_VISUAL_FENCES = new Set([
  'mermaid', 'flowchart', 'sequence', 'sequencediagram', 'classdiagram',
  'statediagram', 'erdiagram', 'gitgraph', 'c4', 'architecture', 'diagram',
  'html', 'htm', 'web', 'webapp', 'svg',
  'map', 'maps', 'location', 'locations', 'places', 'route', 'directions', 'geo', 'geojson',
  'sandbox', 'playground', 'runnable', 'livecode',
])

/** Fences whose non-JSON body can be CSV / TSV / Markdown pipe rows for a table widget. */
const TABULAR_FENCES = new Set(['table', 'tables', 'datatable', 'data_table', 'csv', 'tsv', 'grid', 'spreadsheet', 'leaderboard', 'matrix'])

/**
 * Fence languages a model naturally reaches for when it means the Nexly
 * timetable. `schedule` is also a real interactive app type, so it is only
 * re-routed when the payload actually has timed rows.
 */
const PLAN_FENCE_LANGUAGES = new Set(['plan', 'schedule', 'timetable', 'itinerary', 'routine', 'day'])

/**
 * Convert a Markdown pipe table or CSV/TSV block inside a ```table / ```csv
 * fence into a canonical JSON `{ columns, rows, sortable: true }` payload.
 */
export function tabularTextToJson(raw: string): string | null {
  const lines = raw
    .trim()
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter(Boolean)
  if (lines.length < 2) return null

  // 1. Markdown pipe table
  if (lines[0].includes('|') && lines[1].includes('|')) {
    const splitPipe = (line: string) => {
      let s = line.trim()
      if (s.startsWith('|')) s = s.slice(1)
      if (s.endsWith('|')) s = s.slice(0, -1)
      return s.split('|').map((c) => c.trim())
    }
    const isSep = (line: string) => /^[|\-: \t]+$/.test(line) && line.includes('-')
    const columns = splitPipe(lines[0]).filter((_, i, a) => a.length > 1 || Boolean(a[i]))
    const dataLines = lines.slice(1).filter((l) => !isSep(l))
    if (columns.length >= 1 && dataLines.length >= 1) {
      const rows = dataLines.map((l) => splitPipe(l))
      return JSON.stringify({ columns, rows, sortable: true })
    }
  }

  // 2. CSV / TSV lines
  const delim = lines[0].includes('\t') ? '\t' : lines[0].includes(',') ? ',' : null
  if (!delim) return null
  const parseRow = (line: string): string[] => {
    const cells: string[] = []
    let cur = ''
    let inQuotes = false
    for (let i = 0; i < line.length; i++) {
      const ch = line[i]
      if (ch === '"') {
        if (inQuotes && line[i + 1] === '"') {
          cur += '"'
          i++
        } else {
          inQuotes = !inQuotes
        }
      } else if (ch === delim && !inQuotes) {
        cells.push(cur.trim())
        cur = ''
      } else {
        cur += ch
      }
    }
    cells.push(cur.trim())
    return cells
  }
  const columns = parseRow(lines[0])
  if (columns.length < 2) return null
  const rows = lines.slice(1).map(parseRow).filter((r) => r.some(Boolean))
  if (rows.length === 0) return null
  return JSON.stringify({ columns, rows, sortable: true })
}

export function extractWidgetFences(text: string): WidgetFence[] {
  const out: WidgetFence[] = []
  let lastEnd = 0
  for (const m of text.matchAll(FENCE)) {
    const language = m[1] ?? ''
    const lower = language.toLowerCase()
    let body = m[2].trim()
    const start = m.index ?? 0
    const end = start + m[0].length
    lastEnd = Math.max(lastEnd, end)

    if (NON_WIDGET_VISUAL_FENCES.has(lower)) continue

    // Convert CSV/TSV/pipe tables inside a ```table or ```csv fence into JSON.
    if (!body.startsWith('{') && !body.startsWith('[') && TABULAR_FENCES.has(lower)) {
      const converted = tabularTextToJson(body)
      if (converted) body = converted
    }

    // A widget payload is a JSON object or array. A ```gantt fence holding
    // mermaid's gantt syntax (dateFormat/section lines — the model does this
    // constantly) is NOT a widget: leave it untouched for the diagram renderer.
    if (!body.startsWith('{') && !body.startsWith('[')) continue

    let kind = language ? widgetKindOf(language) : null
    if (PLAN_FENCE_LANGUAGES.has(lower)) {
      try {
        const repaired = repairJson(body)
        if (repaired && isNexlyPlanPayload(JSON.parse(repaired))) kind = 'plan'
      } catch {
        // The validator below reports the malformed body.
      }
    }
    if (!kind && (GENERIC_WIDGET_FENCES.has(lower) || !language || body.startsWith('{') || body.startsWith('['))) {
      kind = inferWidgetKind(body)
    }
    if (!kind) continue
    const json =
      kind === 'plan' || GENERIC_WIDGET_FENCES.has(lower) || !language || !body.startsWith('{')
        ? body
        : lower !== kind
          ? injectWidgetType(body, language, kind)
          : body
    out.push({ kind, json, start, end })
  }

  // Recover a trailing UNCLOSED widget fence if the reply was truncated after opening it.
  const tailSlice = text.slice(lastEnd)
  const tailMatch = UNCLOSED_TAIL_FENCE.exec(tailSlice)
  if (tailMatch && !tailMatch[2].includes('```') && !tailMatch[2].includes('~~~')) {
    const language = tailMatch[1]
    const lower = language.toLowerCase()
    const body = tailMatch[2].trim()
    if (!NON_WIDGET_VISUAL_FENCES.has(lower) && (body.startsWith('{') || body.startsWith('['))) {
      let kind = widgetKindOf(language)
      if (!kind && GENERIC_WIDGET_FENCES.has(lower)) kind = inferWidgetKind(body)
      if (kind) {
        const offsetInTail = tailMatch.index + (tailMatch[0].startsWith('\n') || tailMatch[0].startsWith('\r') ? 1 : 0)
        const start = lastEnd + offsetInTail
        const json =
          kind === 'plan' || GENERIC_WIDGET_FENCES.has(lower) || !body.startsWith('{')
            ? body
            : lower !== kind
              ? injectWidgetType(body, language, kind)
              : body
        if (validateWidget(kind, json).ok) {
          out.push({ kind, json, start, end: text.length })
        }
      }
    }
  }

  return out
}

/** A fenced block whose body is plausibly a bare widget payload. */
const JSON_FENCE = /^[ \t]{0,8}(?:```|~~~)(json|jsonc|widget|genui|data|ui|gsui)[ \t]*[^\r\n]*\r?\n([\s\S]*?)^[ \t]{0,8}(?:```|~~~)[ \t]*$/gm

/**
 * Unwrap a root envelope (`{"chart":{...}}`, `{"widget":{...}}`, `{"data":{...}}`)
 * or wrap a top-level array (`[{...}]`) into an object so validators and
 * inference see the canonical keys regardless of outer wrapping.
 */
function normaliseRootPayload(
  parsed: unknown,
  targetKind?: WidgetKind
): { obj: Record<string, unknown>; wrappedOrUnwrapped: boolean } | null {
  if (Array.isArray(parsed)) {
    if (parsed.length === 0) return null
    return {
      obj: {
        items: parsed,
        cards: parsed,
        rows: parsed,
        entries: parsed,
        questions: parsed,
        fields: parsed,
        prompts: parsed,
        buttons: parsed,
        tabs: parsed,
        sections: parsed,
        steps: parsed,
        options: parsed,
        roots: parsed,
        series: parsed,
      },
      wrappedOrUnwrapped: true,
    }
  }
  if (!isObj(parsed)) return null
  const envelopeKeys = [
    'widget',
    'payload',
    'data',
    'content',
    'result',
    'spec',
    'config',
    ...(targetKind ? [targetKind] : WIDGET_KINDS),
  ]
  for (const key of envelopeKeys) {
    const inner = parsed[key]
    if (isObj(inner) && Object.keys(inner).length > 0) {
      // Only unwrap when the outer object doesn't already own another primary collection key.
      const otherKeys = Object.keys(parsed).filter(
        (k) => k !== key && !['title', 'accent', 'variant', 'palette', 'kind', 'type', 'unit', 'subtitle'].includes(k)
      )
      if (otherKeys.length === 0) {
        return {
          obj: { ...parsed, ...inner },
          wrappedOrUnwrapped: true,
        }
      }
    }
  }
  return { obj: parsed, wrappedOrUnwrapped: false }
}

/**
 * The widget kind a BARE payload describes, or null when it is not a widget.
 *
 * The model sometimes answers with no fence at all (or a ```json / ```widget
 * one). Left alone, that JSON reaches the client as body text. Kind is inferred
 * from an explicit discriminator (`kind`, `widget`, `type`) or structural keys
 * across all 36 catalogue kinds and then CONFIRMED by the real validator, so
 * prose containing braces is never promoted. Mirrors inferWidgetKind() on the
 * clients.
 */
export function inferWidgetKind(json: string): WidgetKind | null {
  const repaired = repairJson(json)
  if (!repaired) return null
  let parsed: unknown
  try {
    parsed = JSON.parse(repaired)
  } catch {
    return null
  }
  if (Array.isArray(parsed)) {
    if (parsed.length === 0) return null
    const first = parsed[0]
    const arrayCandidates: WidgetKind[] = []
    if (typeof first === 'string') {
      arrayCandidates.push('followups', 'checklist', 'chips')
    } else if (isObj(first)) {
      if (('time' in first || 'slot' in first) && ('text' in first || 'title' in first || 'activity' in first)) arrayCandidates.push('plan')
      if (('date' in first || 'year' in first || 'era' in first) && ('title' in first || 'event' in first || 'label' in first)) arrayCandidates.push('timeline')
      if ('front' in first && 'back' in first) arrayCandidates.push('flashcards')
      if (('q' in first || 'question' in first) && ('options' in first || 'choices' in first)) arrayCandidates.push('quiz')
      if ('question' in first && 'answer' in first) arrayCandidates.push('accordion')
      if ('percent' in first || 'percentage' in first) arrayCandidates.push('progress')
      if (('label' in first || 'name' in first || 'metric' in first) && 'value' in first) arrayCandidates.push('stat', 'gauge')
      if ('data' in first && Array.isArray(first.data)) arrayCandidates.push('chart')
      if (Object.keys(first).length >= 2) arrayCandidates.push('table', 'cards', 'checklist')
    }
    for (const k of arrayCandidates) {
      if (validateWidget(k, repaired).ok) return k
    }
    return null
  }
  const norm = normaliseRootPayload(parsed)
  if (!norm) return null
  const obj = norm.obj
  const has = (k: string) => k in obj

  const candidates: WidgetKind[] = []
  const push = (...ks: WidgetKind[]) => {
    for (const k of ks) {
      if (!candidates.includes(k)) candidates.push(k)
    }
  }

  // 1. Explicit kind/widget discriminator (`{"kind":"stat",...}` or `{"widget":"chart",...}`).
  for (const key of ['kind', 'widget']) {
    const raw = obj[key]
    if (typeof raw === 'string' && raw.trim()) {
      const k = widgetKindOf(raw)
      if (k) push(k)
    }
  }

  // 2. Explicit sub-type discriminator (`type`, `sim`, `view`, `surface`, `editor`).
  for (const key of ['type', 'sim', 'view', 'surface', 'editor']) {
    const raw = obj[key]
    if (typeof raw === 'string' && raw.trim()) {
      const k = widgetKindOf(raw)
      if (k) push(k)
    }
  }

  // 2b. Single-key envelope named after a widget kind (`{"chart":{...}}`, `{"stat":{...}}`).
  for (const key of Object.keys( parsed as Record<string, unknown> )) {
    const k = widgetKindOf(key)
    if (k && isObj((parsed as Record<string, unknown>)[key])) push(k)
  }

  // 3. Structural discriminators across all 36 catalogue kinds.
  if (has('rows') && isNexlyPlanPayload(obj)) push('plan')
  if (has('items') && isNexlyPlanPayload(obj)) push('plan')
  if (has('series') || has('datasets') || has('values') || has('slices') || has('points') || has('axes') || has('bids') || has('asks') || has('words') || has('links')) {
    push('chart')
  }
  if ((has('columns') || has('headers')) && (has('rows') || has('data'))) push('table')
  if (has('cards') || has('stats') || has('metrics') || has('kpis')) push('stat', 'flashcards', 'cards')
  if (has('deck') || has('pairs')) push('flashcards')
  if ((has('left') && has('right')) || (has('pros') && has('cons')) || (has('before') && has('after')) || has('sides')) {
    push('compare', 'diff')
  }
  if (has('unified') || has('patch') || (has('before') && has('after'))) push('diff')
  if (has('entries') || has('events') || has('milestones')) push('timeline')
  if (has('fields') && (has('outputs') || has('results') || has('formulas'))) push('calc')
  if (has('inputs') && (has('outputs') || has('results') || has('formulas'))) push('calc')
  if (has('questions')) push('quiz', 'accordion', 'followups')
  if (has('fields') || has('inputs') || has('controls')) push('form', 'calc')
  if (has('prompts') || has('suggestions') || has('followups')) push('followups')
  if (has('buttons') || has('actions')) push('actions')
  if (has('tabs') || has('views')) push('tabs')
  if (has('sections') || has('faqs') || has('panels')) push('accordion', 'tabs')
  if (has('steps') || has('stages') || has('phases')) push('steps', 'app')
  if (has('roots') || has('root') || has('hierarchy')) push('tree', 'chart')
  if (has('question') && (has('options') || has('choices') || has('answers'))) push('poll', 'quiz')
  if (has('options') || has('choices') || has('chips')) push('chips', 'menu', 'poll')
  if (has('style') && ['star', 'heart', 'thumb', 'nps'].includes(String(obj.style ?? '').toLowerCase())) push('rating')
  if (has('tasks') || has('todos')) push('checklist', 'app')
  if (has('slides') || has('images') || has('photos')) push('carousel', 'media')
  if (has('pages') || has('results') || has('url') || has('code')) push('media')
  if (has('bars')) push('progress')
  if (has('gauges') || has('meters')) push('gauge')
  if (has('items')) push('checklist', 'progress', 'gauge', 'carousel', 'app', 'media', 'chart', 'menu', 'chips')
  if (has('groups') || has('slots') || has('columns') || has('rows')) push('app', 'table')
  if (has('summary') || has('badges') || has('popover') || has('callout')) push('panel')
  if (has('hint') && has('prompt')) push('voice')
  if (has('commands') && (has('welcome') || has('title'))) push('terminal')
  if (has('room') || has('channel')) push('collab')
  if (has('gravity') || has('damping') || has('sim')) push('sim')
  if (has('bonds') || has('z') || has('view')) push('stage3d')
  if (has('nodes') && (has('edges') || has('links'))) push('chart', 'canvas', 'editor', 'stage3d')
  if (has('cells') || has('tracks') || has('layout') || has('editor')) push('editor')

  for (const kind of candidates) {
    if (validateWidget(kind, repaired).ok) return kind
  }
  return null
}

/**
 * A widget payload that arrived without a usable fence: either the whole reply
 * is one JSON object, or a ```json block holds one. Returns offsets so the
 * compiler can replace it with a canonical fence.
 */
export function extractBareWidget(text: string): WidgetFence | null {
  const whole = text.trim()
  if ((whole.startsWith('{') && whole.endsWith('}')) || (whole.startsWith('[') && whole.endsWith(']'))) {
    const kind = inferWidgetKind(whole)
    if (kind) {
      const start = text.indexOf(whole)
      return { kind, json: whole, start, end: start + whole.length }
    }
  }
  for (const m of text.matchAll(JSON_FENCE)) {
    const body = m[2].trim()
    const kind = inferWidgetKind(body)
    if (!kind) continue
    const start = m.index ?? 0
    return { kind, json: body, start, end: start + m[0].length }
  }
  // Also check standalone JSON object paragraphs (`\n\n{ ... }\n\n`) that the
  // model emitted between prose paragraphs without opening a code fence.
  const paraRe = /(?:^|\n\s*\n)([ \t]*\{[\s\S]*?\}[ \t]*)(?=\n\s*\n|$)/g
  for (const m of text.matchAll(paraRe)) {
    const rawBlock = m[1]
    const trimmed = rawBlock.trim()
    if (trimmed.includes('```') || trimmed.includes('~~~')) continue
    const kind = inferWidgetKind(trimmed)
    if (!kind) continue
    const blockOffset = m[0].indexOf(rawBlock)
    const start = (m.index ?? 0) + blockOffset
    return { kind, json: trimmed, start, end: start + rawBlock.length }
  }
  return null
}

/**
 * Remove `//` and slash-star comments WITHOUT touching string contents.
 *
 * The naive regex version deleted everything after `//` on a line — including
 * the `//` in `https://…`. Every payload carrying a URL (media, carousel,
 * links, citations, images) therefore failed to parse, was reported as
 * "invalid JSON", and the whole widget was dropped. A comment stripper has to
 * know whether it is inside a string; there is no regex that does.
 */
function stripJsonComments(input: string): string {
  let out = ''
  let inDouble = false
  let inSingle = false
  let escaped = false
  for (let i = 0; i < input.length; i++) {
    const ch = input[i]
    if (inDouble) {
      out += ch
      if (escaped) escaped = false
      else if (ch === '\\') escaped = true
      else if (ch === '"') inDouble = false
      continue
    }
    if (inSingle) {
      out += ch
      if (escaped) escaped = false
      else if (ch === '\\') escaped = true
      else if (ch === "'") inSingle = false
      continue
    }
    if (ch === '"') {
      inDouble = true
      out += ch
      continue
    }
    if (ch === "'") {
      inSingle = true
      out += ch
      continue
    }
    if (ch === '/' && input[i + 1] === '/') {
      while (i < input.length && input[i] !== '\n') i++
      out += '\n'
      continue
    }
    if (ch === '/' && input[i + 1] === '*') {
      i += 2
      while (i < input.length && !(input[i] === '*' && input[i + 1] === '/')) i++
      i++
      continue
    }
    out += ch
  }
  return out
}

/**
 * Normalise LLM JSON dialects into strict RFC 8259 JSON in one string-aware
 * pass:
 *  - single-quoted strings `'hello "world"'` -> `"hello \"world\""`
 *  - raw unescaped newlines/tabs inside strings -> `\n`, `\r`, `\t`
 *  - unquoted object keys `{title: "KPI", retry_2: 1}` -> `{"title": "KPI", "retry_2": 1}`
 *  - Python/JS value literals `True`, `False`, `None`, `undefined`, `NaN`,
 *    `Infinity`, `-Infinity` -> `true`, `false`, `null`
 *  - leading `+` on numbers `:+12.5` -> `:12.5`
 */
function normaliseJsonDialect(input: string): string {
  let out = ''
  let i = 0
  let inDouble = false
  let inSingle = false
  let escaped = false

  while (i < input.length) {
    const ch = input[i]

    if (inDouble) {
      if (escaped) {
        // `\'` is common in JS strings but invalid in strict JSON.
        if (ch === "'") out = out.slice(0, -1) + "'"
        else out += ch
        escaped = false
      } else if (ch === '\\') {
        out += ch
        escaped = true
      } else if (ch === '"') {
        out += ch
        inDouble = false
      } else if (ch === '\n') {
        out += '\\n'
      } else if (ch === '\r') {
        out += '\\r'
      } else if (ch === '\t') {
        out += '\\t'
      } else {
        out += ch
      }
      i++
      continue
    }

    if (inSingle) {
      if (escaped) {
        if (ch === "'") out += "'"
        else if (ch === '"') out += '\\"'
        else out += '\\' + ch
        escaped = false
      } else if (ch === '\\') {
        escaped = true
      } else if (ch === "'") {
        out += '"'
        inSingle = false
      } else if (ch === '"') {
        out += '\\"'
      } else if (ch === '\n') {
        out += '\\n'
      } else if (ch === '\r') {
        out += '\\r'
      } else if (ch === '\t') {
        out += '\\t'
      } else {
        out += ch
      }
      i++
      continue
    }

    if (ch === '"') {
      inDouble = true
      out += '"'
      i++
      continue
    }
    if (ch === "'") {
      inSingle = true
      out += '"'
      i++
      continue
    }

    // Outside strings: check for unquoted keys or bare Python/JS literals.
    const rest = input.slice(i)

    // Unquoted key after `{` or `,` (look back in `out` past whitespace).
    const keyMatch = /^([a-zA-Z_$][\w$-]*)(\s*:)/.exec(rest)
    if (keyMatch) {
      const prev = out.trimEnd().slice(-1)
      if (prev === '{' || prev === ',') {
        out += `"${keyMatch[1]}"${keyMatch[2]}`
        i += keyMatch[0].length
        continue
      }
    }

    // Bare value literals after `:`, `[`, or `,`.
    const prev = out.trimEnd().slice(-1)
    if (prev === ':' || prev === '[' || prev === ',') {
      const litMatch = /^(True|False|None|undefined|NaN|-?Infinity)(?=[\s,}\]]|$)/.exec(rest)
      if (litMatch) {
        const word = litMatch[1]
        out += word === 'True' ? 'true' : word === 'False' ? 'false' : 'null'
        i += word.length
        continue
      }
      if (ch === '+' && /\d/.test(input[i + 1] ?? '')) {
        i++
        continue
      }
    }

    out += ch
    i++
  }

  return out
}

/**
 * Close what the model left open: an unterminated string (the calendar probe
 * watched `"All day]}` — a missing quote and brace in one payload) and any
 * unclosed object/array. Walks with string awareness so a `}` inside a string
 * never counts, and inserts the closing quote BEFORE the structural character
 * that terminated the string.
 */
function closeOpenStructures(s: string): string | null {
  let out = ''
  let inString = false
  let escaped = false
  const stack: string[] = []
  for (const ch of s) {
    if (inString) {
      if (!escaped && (ch === ']' || ch === '}' || ch === ',')) {
        out += '"'
        inString = false
      } else {
        out += ch
        if (escaped) escaped = false
        else if (ch === '\\') escaped = true
        else if (ch === '"') inString = false
        continue
      }
    }
    if (ch === '"') {
      inString = true
      out += ch
      continue
    }
    if (ch === '{' || ch === '[') {
      stack.push(ch === '{' ? '}' : ']')
      out += ch
      continue
    }
    if (ch === '}' || ch === ']') {
      // An array close that arrives while objects are still open ("All day]}
      // — the model omitted a string close AND a brace): close the objects
      // first, then the array.
      const want = ch
      while (stack.length > 0 && stack[stack.length - 1] !== want) {
        if (want === ']' && stack[stack.length - 1] === '}') {
          out += '}'
          stack.pop()
        } else break
      }
      if (stack.length > 0 && stack[stack.length - 1] === ch) stack.pop()
      out += ch
      continue
    }
    out += ch
  }
  if (inString) out += '"'
  // Strip any dangling incomplete key/colon or trailing comma at the truncation point.
  if (stack[stack.length - 1] === '}') {
    out = out.replace(/,\s*"[^"]*"\s*:\s*$/, '')
    out = out.replace(/,\s*"[^"]*"\s*$/, '')
    out = out.replace(/\{\s*"[^"]*"\s*:\s*$/, '{')
    out = out.replace(/\{\s*"[^"]*"\s*$/, '{')
  }
  out = out.replace(/,\s*$/, '')
  while (stack.length > 0) out += stack.pop()
  out = out.replace(/,\s*([}\]])/g, '$1')
  try {
    JSON.parse(out)
    return out
  } catch {
    return null
  }
}

export function repairJson(raw: string): string | null {
  let s = raw.trim()
  if (!s) return null

  // Strip a prose prefix before the first `{` or `[`.
  const firstBrace = s.indexOf('{')
  const firstBracket = s.indexOf('[')
  const isArrayRoot = firstBracket >= 0 && (firstBrace < 0 || firstBracket < firstBrace)
  const firstOpen = isArrayRoot ? firstBracket : firstBrace
  if (firstOpen < 0) return null
  if (firstOpen > 0) s = s.slice(firstOpen)
  const lastClose = isArrayRoot ? Math.max(s.lastIndexOf(']'), s.lastIndexOf('}')) : s.lastIndexOf('}')
  if (lastClose >= 0) s = s.slice(0, lastClose + 1)

  s = stripJsonComments(s)
    .replace(/[\u201c\u201d]/g, '"')
    .replace(/[\u2018\u2019]/g, "'")

  s = normaliseJsonDialect(s).replace(/,\s*([}\]])/g, '$1')

  try {
    JSON.parse(s)
    return s
  } catch {
    // Truncation and typos are the same failure: something the model opened
    // was never closed. Close strings first, then structures.
    return closeOpenStructures(s)
  }
}

// ---------------------------------------------------------------------------
// Primitive coercions
// ---------------------------------------------------------------------------

const isObj = (v: unknown): v is Record<string, unknown> =>
  typeof v === 'object' && v !== null && !Array.isArray(v)

const asArray = (v: unknown): unknown[] => (Array.isArray(v) ? v : [])

const asString = (v: unknown, fallback = ''): string =>
  typeof v === 'string'
    ? v
    : typeof v === 'number' && Number.isFinite(v)
      ? String(v)
      : typeof v === 'boolean'
        ? String(v)
        : fallback

const asNumber = (v: unknown): number | null => {
  if (typeof v === 'number' && Number.isFinite(v)) return v
  if (typeof v === 'string') {
    const cleaned = v
      .trim()
      .replace(/^[+$€£¥₹৳\s]+/, '')
      .replace(/[%$,\s]+$/g, '')
      .replace(/,/g, '')
    if (!cleaned) return null
    const n = Number(cleaned)
    return Number.isFinite(n) ? n : null
  }
  return null
}

const clamp = (n: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, n))

const isHttps = (v: string) => /^https:\/\//i.test(v)

// ---------------------------------------------------------------------------
// Per-kind validators
// ---------------------------------------------------------------------------

type Check = {
  widget: Record<string, unknown>
  errors: string[]
  repaired: boolean
}

export function validateWidget(kind: string, rawJson: string): ValidationResult {
  if (!isWidgetKind(kind)) return { ok: false, kind, errors: [`unknown kind: ${kind}`] }

  const repairedJson = repairJson(rawJson)
  if (repairedJson === null) return { ok: false, kind, errors: ['invalid JSON'] }

  let rawParsed: unknown
  try {
    rawParsed = JSON.parse(repairedJson)
  } catch {
    return { ok: false, kind, errors: ['invalid JSON'] }
  }
  const normalised = normaliseRootPayload(rawParsed, kind)
  if (!normalised) return { ok: false, kind, errors: ['payload is not an object'] }
  const payload = normalised.obj

  const jsonRepaired = repairedJson !== rawJson.trim() || normalised.wrappedOrUnwrapped
  const check = CHECKS[kind](payload)
    if (check.errors.length > 0) return { ok: false, kind, errors: check.errors }

    // Art direction belongs to the model: it may choose an accent and a surface
    // for THIS widget instead of accepting the renderer's default identity. The
    // validator carries the two keys through untouched (and rejects anything
    // that is not a real colour/variant) — dropping them here silently threw
    // away every deliberate design decision the model made.
    const styled: Record<string, unknown> = { ...check.widget }
    const accent = typeof payload.accent === 'string' ? payload.accent.trim() : ''
    if (/^#?[0-9a-fA-F]{6}$/.test(accent)) {
      styled.accent = accent.startsWith('#') ? accent.toLowerCase() : `#${accent.toLowerCase()}`
    }
    const variant = typeof payload.variant === 'string' ? payload.variant.trim().toLowerCase() : ''
    if ((SURFACES as readonly string[]).includes(variant)) styled.variant = variant as Surface
    // The reply-level palette: one id, every widget in the answer inherits it,
    // so a reply reads as one designed object. An unknown id is dropped and the
    // art director picks the subject-fit palette instead.
    const palette = typeof payload.palette === 'string' ? payload.palette.trim().toLowerCase() : ''
    if ((PALETTES as readonly Palette[]).some((p) => p.id === palette)) styled.palette = palette

  const json = JSON.stringify(styled)
  if (json.length > LIMITS.maxWidgetBytes) {
    return { ok: false, kind, errors: [`payload exceeds ${LIMITS.maxWidgetBytes} bytes`] }
  }
  return {
    ok: true,
    kind,
    widget: styled,
    json,
    repaired: check.repaired || jsonRepaired,
  }
}

/** Narrowing-safe accessor: `[]` for valid results. */
export function validationErrors(result: ValidationResult): string[] {
  if (result.ok) return []
  return (result as { ok: false; kind: string; errors: string[] }).errors
}

/**
 * Point normalisation for every shape a real model emits:
 *
 *   [[0, 12], [1, 18]]      documented pair
 *   [12, 18]                flat values, x implied by index
 *   [["Q1", 12], ["Q2", 18]] string label as x  (labels are harvested)
 *   [{ "x": 0, "y": 12 }]   object form
 *
 * Only the first used to pass, so the other three were dropped as invalid —
 * our own validator threw away correct answers.
 */
function normalisePoints(raw: unknown[], labelHints: string[]): number[][] {
  const out: number[][] = []
  raw.forEach((item, index) => {
    const direct = asNumber(item)
    if (direct !== null && !Array.isArray(item) && !isObj(item)) {
      out.push([index, direct])
      return
    }
    if (Array.isArray(item)) {
      const first = item[0]
      if (typeof first === 'string' && first.trim() && asNumber(first) === null) {
        labelHints.push(first.trim())
        const restNums = item.slice(1).map(asNumber).filter((n): n is number => n !== null)
        if (restNums.length >= 2) out.push(restNums.slice(0, 5))
        else if (restNums.length === 1) out.push([index, restNums[0]])
        return
      }
      const nums = item.map(asNumber).filter((n): n is number => n !== null)
      // Preserve 2D [x,y], 3D bubble [x,y,z], and 4D/5D candlestick [o,h,l,c].
      if (nums.length >= 2) out.push(nums.slice(0, 5))
      else if (nums.length === 1) out.push([index, nums[0]])
      return
    }
    if (isObj(item)) {
      const hint = asString(item.label ?? item.name ?? (typeof item.x === 'string' && asNumber(item.x) === null ? item.x : '')).trim()
      if (hint) labelHints.push(hint)
      // Candlestick object shape: {open/o, high/h, low/l, close/c}
      const o = asNumber(item.open ?? item.o)
      const h = asNumber(item.high ?? item.h)
      const l = asNumber(item.low ?? item.l)
      const c = asNumber(item.close ?? item.c)
      if (o !== null && h !== null && l !== null && c !== null) {
        out.push([o, h, l, c])
        return
      }
      const y = asNumber(item.y ?? item.value ?? item.val ?? item.count ?? item.amount)
      if (y === null) return
      const x = asNumber(item.x) ?? index
      const z = asNumber(item.z ?? item.size ?? item.r)
      out.push(z !== null ? [x, y, z] : [x, y])
    }
  })
  return out.slice(0, LIMITS.maxPointsPerSeries)
}

/**
 * v2 chart payload families whose shape is a GRAPH or a composition, not a
 * series list. Each has its own normaliser below.
 */
const GRAPH_PAYLOAD_TYPES = new Set(['sankey', 'network', 'wordcloud', 'sunburst', 'bullet', 'force', 'depth', 'violin', 'contour', 'parallel', 'dendrogram'])

/** sankey / network / wordcloud / sunburst / bullet payloads. */
function normaliseGraphChart(type: string, p: Record<string, unknown>, alreadyRepaired: boolean): Check {
  const errors: string[] = []
  const repaired = alreadyRepaired

  if (type === 'sankey') {
    const links = asArray(p.links)
      .map((l) => {
        const o = isObj(l) ? l : {}
        const value = asNumber(o.value)
        return { source: asString(o.source ?? o.from), target: asString(o.target ?? o.to), value: value === null ? 0 : value }
      })
      .filter((l) => l.source && l.target && l.value > 0)
      .slice(0, 60)
    const declaredNodes = asArray(p.nodes)
      .map((n) => asString(typeof n === 'string' ? n : isObj(n) ? String((n as Record<string, unknown>).id ?? (n as Record<string, unknown>).name ?? '') : ''))
      .filter(Boolean)
    const derived = new Set<string>()
    for (const l of links) {
      derived.add(l.source)
      derived.add(l.target)
    }
    const nodes = declaredNodes.length > 0 ? declaredNodes.slice(0, 40) : [...derived]
    if (links.length === 0) errors.push('sankey needs links with source, target and value')
    return { widget: { type, title: asString(p.title), unit: asString(p.unit), nodes, links }, errors, repaired }
  }

  if (type === 'network') {
    const nodes = asArray(p.nodes)
      .map((n) => {
        if (typeof n === 'string') return { id: n, label: n, group: '' }
        const o = isObj(n) ? n : {}
        const id = asString(o.id ?? o.name)
        return { id, label: asString(o.label, id), group: asString(o.group) }
      })
      .filter((n) => n.id)
      .slice(0, 40)
    const rawEdges = asArray(p.edges).length > 0 ? asArray(p.edges) : asArray(p.links)
    const edges = asArray(rawEdges)
      .map((e) => {
        const o = isObj(e) ? e : {}
        const weight = asNumber(o.weight ?? o.value)
        return { source: asString(o.source ?? o.from), target: asString(o.target ?? o.to), weight: weight === null ? 1 : weight }
      })
      .filter((e) => e.source && e.target)
      .slice(0, 80)
    const ids = new Set(nodes.map((n) => n.id))
    const known = edges.filter((e) => ids.has(e.source) && ids.has(e.target))
    if (nodes.length === 0 || known.length === 0) errors.push('network needs nodes and edges between them')
    return { widget: { type, title: asString(p.title), nodes, edges: known }, errors, repaired }
  }

  if (type === 'wordcloud') {
    const raw = asArray(p.items).length > 0 ? asArray(p.items) : asArray(p.words)
    const items = raw
      .map((w) => {
        if (typeof w === 'string') return { text: w, weight: 1 }
        const o = isObj(w) ? w : {}
        const weight = asNumber(o.weight ?? o.value)
        return { text: asString(o.text ?? o.word ?? o.label), weight: weight === null ? 1 : Math.max(0, weight) }
      })
      .filter((w) => w.text)
      .slice(0, 60)
    if (items.length === 0) errors.push('wordcloud needs items with text and weight')
    return { widget: { type, title: asString(p.title), items }, errors, repaired }
  }

  if (type === 'sunburst') {
    const readNode = (v: unknown, depth: number): { label: string; value: number; children: unknown[] } | null => {
      if (depth > LIMITS.maxTreeDepth) return null
      if (typeof v === 'string') return { label: v, value: 0, children: [] }
      const o = isObj(v) ? v : {}
      const value = asNumber(o.value)
      const children = asArray(o.children)
        .map((c) => readNode(c, depth + 1))
        .filter((c): c is { label: string; value: number; children: unknown[] } => c !== null)
      const label = asString(o.label ?? o.name)
      if (!label) return null
      return { label, value: value === null ? 0 : value, children }
    }
    const root = readNode(p.root ?? p.hierarchy, 0)
    if (!root || (root.children.length === 0 && root.value === 0)) errors.push('sunburst needs a root with children or a value')
    return { widget: { type, title: asString(p.title), unit: asString(p.unit), root: root ?? { label: '', value: 0, children: [] } }, errors, repaired }
  }

  if (type === 'force') {
    // same shape as network; the renderer adds a force-directed layout
    const asNetwork = normaliseGraphChart('network', p, alreadyRepaired)
    if (asNetwork.errors.length === 0) {
      return { ...asNetwork, widget: { ...asNetwork.widget, type: 'force' } }
    }
    return asNetwork
  }

  if (type === 'depth') {
    const side = (v: unknown) =>
      asArray(v)
        .map((row) => {
          const pair = asArray(row).map((x) => asNumber(x)).filter((n): n is number => n !== null)
          return pair.length >= 2 ? [pair[0], pair[1]] : null
        })
        .filter((row): row is [number, number] => row !== null)
        .slice(0, 40)
    const bids = side(p.bids)
    const asks = side(p.asks)
    if (bids.length === 0 && asks.length === 0) errors.push('depth needs bids and asks as [price,size] pairs')
    return { widget: { type, title: asString(p.title), unit: asString(p.unit), bids, asks }, errors, repaired }
  }

  if (type === 'violin') {
    const series = asArray(p.series)
      .map((ser) => {
        const row = isObj(ser) ? ser : {}
        const samples = asArray(row.data).map((d) => asNumber(Array.isArray(d) ? d[d.length - 1] : d)).filter((n): n is number => n !== null)
        return { name: asString(row.name, 'Group'), data: samples.slice(0, 400) }
      })
      .filter((ser) => ser.data.length >= 3)
      .slice(0, 8)
    if (series.length === 0) errors.push('violin needs series with at least 3 samples')
    return { widget: { type, title: asString(p.title), unit: asString(p.unit), series }, errors, repaired }
  }

  if (type === 'contour') {
    const points = asArray(p.points)
      .map((pt) => {
        const nums = asArray(pt).map((x) => asNumber(x)).filter((n): n is number => n !== null)
        return nums.length >= 3 ? [nums[0], nums[1], nums[2]] : null
      })
      .filter((pt): pt is [number, number, number] => pt !== null)
      .slice(0, 2500)
    if (points.length < 9) errors.push('contour needs a grid of [x,y,z] points')
    return { widget: { type, title: asString(p.title), unit: asString(p.unit), points, levels: clamp(asNumber(p.levels) ?? 6, 3, 12) }, errors, repaired }
  }

  if (type === 'parallel') {
    const axes = asArray(p.axes).map((a) => asString(a)).filter(Boolean).slice(0, 10)
    const series = asArray(p.series)
      .map((ser) => {
        const row = isObj(ser) ? ser : {}
        const values = asArray(row.values).map((v) => asNumber(v) ?? 0)
        return { name: asString(row.name, 'Row'), values: values.slice(0, 10) }
      })
      .filter((ser) => ser.values.length === axes.length && axes.length >= 3)
      .slice(0, 12)
    if (axes.length < 3 || series.length === 0) errors.push('parallel needs 3+ axes and series aligned to them')
    return { widget: { type, title: asString(p.title), axes, series }, errors, repaired }
  }

  if (type === 'dendrogram') {
    const read = (v: unknown, depth: number): { label: string; distance: number; children: unknown[] } | null => {
      if (depth > LIMITS.maxTreeDepth) return null
      const o = isObj(v) ? v : {}
      const label = asString(o.label ?? o.name)
      if (!label) return null
      const dist = asNumber(o.distance) ?? 0
      return { label, distance: dist, children: asArray(o.children).map((c) => read(c, depth + 1)).filter((c): c is { label: string; distance: number; children: unknown[] } => c !== null) }
    }
    const rawRoots = asArray(p.roots).length > 0 ? asArray(p.roots) : isObj(p.root) ? [p.root] : []
    const roots = rawRoots.map((r) => read(r, 0)).filter((r): r is { label: string; distance: number; children: unknown[] } => r !== null).slice(0, 8)
    if (!roots.length) errors.push('dendrogram needs a merge tree with labels')
    return { widget: { type, title: asString(p.title), roots }, errors, repaired }
  }

  // bullet
  const max = asNumber(p.max)
  const items = asArray(p.items)
    .map((i) => {
      const o = isObj(i) ? i : {}
      const value = asNumber(o.value)
      const target = asNumber(o.target)
      const itemMax = asNumber(o.max)
      const ranges = asArray(o.ranges).map((r) => asNumber(r)).filter((n): n is number => n !== null)
      return {
        label: asString(o.label),
        value: value === null ? 0 : value,
        target: target === null ? null : target,
        max: itemMax === null || itemMax <= 0 ? null : itemMax,
        ranges: ranges.slice(0, 4),
        unit: asString(o.unit),
      }
    })
    .filter((i) => i.label)
    .slice(0, 8)
  if (items.length === 0) errors.push('bullet needs items with label and value')
  return { widget: { type, title: asString(p.title), unit: asString(p.unit), max: max === null || max <= 0 ? null : max, items }, errors, repaired }
}

const CHECKS: Record<WidgetKind, (p: Record<string, unknown>) => Check> = {
  chart: (p) => {
    const errors: string[] = []
    let repaired = false
    const rawType = asString(p.type, 'line')
    let type = canonicalChartType(rawType)
    if (type !== rawType) repaired = true
    if (!isChartType(type)) {
      type = 'bar'
      repaired = true
    }
    // v2 graph / composition charts have their own payload shapes and do not
    // fit the series model at all.
    if (GRAPH_PAYLOAD_TYPES.has(type)) {
      return normaliseGraphChart(type, p, repaired)
    }
    const pieLike = type === 'pie' || type === 'donut'
    // A gantt chart is encoded as ONE RANGE PER SERIES — Task 1 is [0,2],
    // Task 2 is [2,4] (probe 2026-10-20). Demanding two points per series
    // rejected every canonical gantt payload.
    const minPoints = pieLike || type === 'gantt' ? 1 : 2

    // Accept series, Chart.js datasets, or lines/bars.
    let rawSeries = asArray(p.series)
    if (rawSeries.length === 0 && asArray(p.datasets).length > 0) {
      rawSeries = asArray(p.datasets)
      repaired = true
    }
    if (rawSeries.length === 0 && asArray(p.lines ?? p.bars).length > 0) {
      rawSeries = asArray(p.lines ?? p.bars)
      repaired = true
    }

    const labelHints: string[] = []

    // Gantt task-object shape: {"type":"gantt","tasks":[{"name":"Design","start":0,"end":3}]}
    if (type === 'gantt' && rawSeries.length === 0 && asArray(p.tasks ?? p.items).length > 0) {
      const taskSeries: Array<{ name: string; data: number[][] }> = []
      for (const item of asArray(p.tasks ?? p.items)) {
        const o = isObj(item) ? item : {}
        const name = asString(o.name ?? o.title ?? o.label ?? o.task, 'Task')
        const start = asNumber(o.start ?? o.from ?? o.begin)
        const end = asNumber(o.end ?? o.to) ?? (start !== null && asNumber(o.duration) !== null ? start + (asNumber(o.duration) ?? 1) : null)
        if (start !== null && end !== null) {
          taskSeries.push({ name, data: [[start, end]] })
        }
      }
      if (taskSeries.length > 0) {
        rawSeries = taskSeries
        repaired = true
      }
    }

    if (rawSeries.length === 0) {
      const flat =
        asArray(p.values).length > 0
          ? asArray(p.values)
          : asArray(p.data).length > 0
            ? asArray(p.data)
            : asArray(p.points).length > 0
              ? asArray(p.points)
              : asArray(p.slices).length > 0
                ? asArray(p.slices)
                : asArray(p.items)
      if (flat.length > 0) {
        if (pieLike && flat.every((item) => isObj(item) && (asNumber((item as Record<string, unknown>).value ?? (item as Record<string, unknown>).y ?? (item as Record<string, unknown>).percent) !== null))) {
          rawSeries = flat.map((item, idx) => {
            const o = item as Record<string, unknown>
            const sliceName = asString(o.label ?? o.name ?? o.title, `Slice ${idx + 1}`)
            labelHints.push(sliceName)
            const val = asNumber(o.value ?? o.y ?? o.percent) ?? 0
            return { name: sliceName, data: [[0, val]] }
          })
        } else {
          rawSeries = [{ name: asString(p.name, 'Value'), data: flat }]
        }
        repaired = true
      } else if (isObj(p.data)) {
        // Object map: {"type":"bar","data":{"Q1":120,"Q2":180}}
        const entries = Object.entries(p.data)
          .map(([k, v]) => [k, asNumber(v)] as const)
          .filter((pair): pair is [string, number] => pair[1] !== null)
        if (entries.length > 0) {
          for (const [k] of entries) labelHints.push(k)
          rawSeries = [{ name: asString(p.name, 'Value'), data: entries.map(([, v], idx) => [idx, v]) }]
          repaired = true
        }
      }
    }

    const xAxisObj = isObj(p.xAxis) ? p.xAxis : null
    const declared = asArray(
      p.labels ?? p.categories ?? (Array.isArray(p.xAxis) ? p.xAxis : xAxisObj ? xAxisObj.categories ?? xAxisObj.data ?? xAxisObj.labels : undefined)
    )
      .map((l) => asString(l))
      .filter(Boolean)

    let series = rawSeries
      .map((s) => {
        const row = isObj(s) ? s : {}
        const rawPoints = asArray(row.data).length > 0 ? asArray(row.data) : asArray(row.values).length > 0 ? asArray(row.values) : asArray(row.points)
        const data = normalisePoints(rawPoints, labelHints)
        return { name: asString(row.name ?? row.label ?? row.title ?? row.id, 'Series'), data }
      })
      .filter((s) => s.data.length >= minPoints)
      .slice(0, LIMITS.maxChartSeries)

    const labels = declared.length > 0 ? declared : labelHints

    // If a pie/donut arrived as a SINGLE series with multiple slice points
    // ({"type":"pie","labels":["A","B"],"series":[{"name":"Share","data":[[0,40],[1,60]]}]}),
    // expand it to the canonical one-series-per-slice form so every renderer
    // (Web & Android) draws all slices instead of only the first point.
    if (pieLike && series.length === 1 && series[0].data.length > 1) {
      series = series[0].data
        .slice(0, LIMITS.maxChartSeries)
        .map((pt, idx) => ({
          name: labels[idx] ?? `${series[0].name} ${idx + 1}`,
          data: [[0, pt[pt.length - 1] ?? 0]],
        }))
      repaired = true
    }

    if (series.length === 0) errors.push('chart needs at least one series with enough points')

    return {
      widget: { type, title: asString(p.title), unit: asString(p.unit), labels, series },
      errors,
      repaired,
    }
  },

  stat: (p) => {
    let repaired = false
    let rawCards = asArray(p.cards)
    if (rawCards.length === 0) {
      const alt = asArray(p.stats ?? p.metrics ?? p.items ?? p.kpis ?? p.values)
      if (alt.length > 0) {
        rawCards = alt
        repaired = true
      } else if (isObj(p.metrics ?? p.stats ?? p.data)) {
        const mapObj = (p.metrics ?? p.stats ?? p.data) as Record<string, unknown>
        rawCards = Object.entries(mapObj).map(([k, v]) => ({ label: k, value: asString(v) }))
        repaired = true
      } else if (asString(p.label ?? p.metric) && asString(p.value ?? p.val ?? p.amount)) {
        rawCards = [p]
        repaired = true
      }
    }
    const cards = rawCards
      .map((c) => {
        const row = isObj(c) ? c : {}
        const delta = asString(row.delta ?? row.change ?? row.trend ?? row.diff)
        let direction = asString(row.direction)
        if (!direction && delta) {
          if (delta.startsWith('+') || /↑|up/i.test(delta)) direction = 'up'
          else if (delta.startsWith('-') || /↓|down/i.test(delta)) direction = 'down'
        }
        return {
          label: asString(row.label ?? row.title ?? row.name ?? row.metric ?? row.key),
          value: asString(row.value ?? row.val ?? row.amount ?? row.number ?? row.score),
          unit: asString(row.unit),
          delta,
          direction,
        }
      })
      .filter((c) => c.label && c.value)
      .slice(0, LIMITS.maxCards)
    return {
      widget: { title: asString(p.title), cards },
      errors: cards.length ? [] : ['stat needs at least one card'],
      repaired,
    }
  },

  table: (p) => {
    let repaired = false
    const rawCols = asArray(p.columns ?? p.headers ?? p.fields)
    const colKeys: string[] = []
    let columns = rawCols
      .map((c) => {
        if (isObj(c)) {
          const key = asString(c.key ?? c.field ?? c.id ?? c.label ?? c.title ?? c.header ?? c.name)
          const label = asString(c.label ?? c.title ?? c.header ?? c.name ?? c.key ?? c.field)
          if (key) colKeys.push(key)
          return label
        }
        const s = asString(c)
        if (s) colKeys.push(s)
        return s
      })
      .filter(Boolean)
      .slice(0, LIMITS.maxTableColumns)

    const rawRows = asArray(p.rows ?? p.data ?? p.items)
    // If rows are objects [{"Brand":"A","Price":"120"}] and columns were omitted,
    // derive columns from the keys of the first row object.
    if (columns.length === 0 && rawRows.length > 0 && isObj(rawRows[0])) {
      const derived = Object.keys(rawRows[0]).slice(0, LIMITS.maxTableColumns)
      columns = derived
      colKeys.push(...derived)
      repaired = true
    }

    const rows = rawRows
      .map((r) => {
        if (Array.isArray(r)) {
          return r.map((c) => asString(c)).slice(0, columns.length)
        }
        if (isObj(r)) {
          repaired = true
          const keys = colKeys.length > 0 ? colKeys.slice(0, columns.length) : columns
          return keys.map((k, idx) => asString(r[k] ?? r[columns[idx]] ?? Object.values(r)[idx]))
        }
        return []
      })
      .filter((r) => r.length > 0)
      .slice(0, LIMITS.maxTableRows)
    const errors: string[] = []
    if (!columns.length) errors.push('table needs columns')
    if (!rows.length) errors.push('table needs rows')
    return {
      widget: { title: asString(p.title), columns, rows, sortable: p.sortable === true },
      errors,
      repaired,
    }
  },

  compare: (p) => {
    let repaired = false
    const side = (v: unknown, fallbackTitle = '') => {
      if (Array.isArray(v)) {
        repaired = true
        return { title: fallbackTitle, items: v.map((i) => asString(i)).filter(Boolean) }
      }
      const o = isObj(v) ? v : {}
      const rawItems = asArray(o.items ?? o.points ?? o.bullets ?? o.features ?? o.list ?? o.pros ?? o.cons)
      return {
        title: asString(o.title ?? o.label ?? o.name ?? o.heading, fallbackTitle),
        items: rawItems.map((i) => (isObj(i) ? asString(i.text ?? i.label ?? i.title) : asString(i))).filter(Boolean),
      }
    }
    const sidesArr = asArray(p.sides ?? p.columns ?? p.options)
    let left = side(p.left)
    let right = side(p.right)
    if (!left.items.length && !right.items.length) {
      if (p.pros !== undefined || p.cons !== undefined) {
        left = side(p.pros, 'Pros')
        right = side(p.cons, 'Cons')
        repaired = true
      } else if (p.a !== undefined || p.b !== undefined) {
        left = side(p.a, 'Option A')
        right = side(p.b, 'Option B')
        repaired = true
      } else if (p.before !== undefined && p.after !== undefined && (isObj(p.before) || Array.isArray(p.before))) {
        left = side(p.before, 'Before')
        right = side(p.after, 'After')
        repaired = true
      } else if (sidesArr.length >= 2) {
        left = side(sidesArr[0], 'Left')
        right = side(sidesArr[1], 'Right')
        repaired = true
      }
    }
    return {
      widget: { title: asString(p.title), left, right },
      errors: left.items.length || right.items.length ? [] : ['compare needs items on a side'],
      repaired,
    }
  },

  progress: (p) => {
    let repaired = false
    const rawItems = asArray(p.items).length > 0 ? asArray(p.items) : asArray(p.bars ?? p.metrics ?? p.steps ?? p.tasks)
    if (!Array.isArray(p.items) && rawItems.length > 0) repaired = true
    const items = rawItems
      .map((i) => {
        const o = isObj(i) ? i : {}
        const percent = asNumber(o.percent ?? o.value ?? o.progress ?? o.pct ?? o.score)
        return { label: asString(o.label ?? o.title ?? o.name), percent: percent === null ? 0 : clamp(percent, 0, 100) }
      })
      .filter((i) => i.label)
    return {
      widget: { title: asString(p.title), items },
      errors: items.length ? [] : ['progress needs items'],
      repaired,
    }
  },

  timeline: (p) => {
    let repaired = false
    const rawEntries = asArray(p.entries).length > 0 ? asArray(p.entries) : asArray(p.events ?? p.items ?? p.milestones ?? p.steps ?? p.history)
    if (!Array.isArray(p.entries) && rawEntries.length > 0) repaired = true
    const entries = rawEntries
      .map((e) => {
        const o = isObj(e) ? e : {}
        return {
          date: asString(o.date ?? o.time ?? o.year ?? o.when ?? o.period ?? o.phase ?? o.step),
          title: asString(o.title ?? o.label ?? o.event ?? o.name ?? o.heading),
          detail: asString(o.detail ?? o.description ?? o.body ?? o.text ?? o.summary),
        }
      })
      .filter((e) => e.date && e.title)
    return {
      widget: { title: asString(p.title), entries },
      errors: entries.length ? [] : ['timeline needs dated entries'],
      repaired,
    }
  },

  plan: (p) => {
    const plan = parseNexlyPlan(p)
    return {
      widget: plan ? { ...plan } : {},
      errors: plan ? [] : ['plan needs at least one timed row with time and text'],
      repaired: false,
    }
  },

  gauge: (p) => {
    let repaired = false
    let rawItems = asArray(p.items)
    if (rawItems.length === 0) {
      const alt = asArray(p.gauges ?? p.metrics ?? p.meters)
      if (alt.length > 0) {
        rawItems = alt
        repaired = true
      } else if (asString(p.label) && asNumber(p.value ?? p.percent) !== null) {
        rawItems = [p]
        repaired = true
      }
    }
    const items = rawItems
      .map((i) => {
        const o = isObj(i) ? i : {}
        const value = asNumber(o.value ?? o.val ?? o.percent ?? o.score)
        const max = asNumber(o.max ?? o.total ?? o.target)
        return {
          label: asString(o.label ?? o.title ?? o.name),
          value: value === null ? 0 : Math.max(0, value),
          max: max === null || max <= 0 ? 100 : max,
          unit: asString(o.unit),
        }
      })
      .filter((i) => i.label)
      .slice(0, 6)
    return {
      widget: { title: asString(p.title), items },
      errors: items.length ? [] : ['gauge needs items'],
      repaired,
    }
  },

  quiz: (p) => {
    let repaired = false
    let rawQuestions = asArray(p.questions)
    if (rawQuestions.length === 0) {
      const alt = asArray(p.items ?? p.cards)
      if (alt.length > 0) {
        rawQuestions = alt
        repaired = true
      } else if (asString(p.q ?? p.question) && asArray(p.options ?? p.choices).length >= 2) {
        rawQuestions = [p]
        repaired = true
      }
    }
    const questions = rawQuestions
      .map((q) => {
        const o = isObj(q) ? q : {}
        const options = asArray(o.options ?? o.choices ?? o.answers)
          .map((x) => (isObj(x) ? asString(x.label ?? x.text ?? x.value) : asString(x)))
          .filter(Boolean)
        const rawAns = o.answer ?? o.correct ?? o.correctAnswer
        let answer = asNumber(rawAns)
        if (answer === null && typeof rawAns === 'string' && options.length > 0) {
          const trimmedAns = rawAns.trim()
          const exactIdx = options.findIndex((opt) => opt.toLowerCase() === trimmedAns.toLowerCase())
          if (exactIdx >= 0) {
            answer = exactIdx
            repaired = true
          } else if (/^[A-Fa-f]$/.test(trimmedAns)) {
            const letterIdx = trimmedAns.toUpperCase().charCodeAt(0) - 65
            if (letterIdx >= 0 && letterIdx < options.length) {
              answer = letterIdx
              repaired = true
            }
          }
        }
        return {
          q: asString(o.q ?? o.question ?? o.prompt ?? o.title),
          options,
          answer: options.length ? clamp(answer === null ? 0 : answer, 0, options.length - 1) : 0,
          explain: asString(o.explain ?? o.explanation ?? o.reason ?? o.rationale ?? o.detail),
        }
      })
      .filter((q) => q.q && q.options.length >= 2)
      .slice(0, LIMITS.maxQuizQuestions)
    return {
      widget: { title: asString(p.title), questions },
      errors: questions.length ? [] : ['quiz needs questions with options'],
      repaired,
    }
  },

  form: (p) => {
    let repaired = false
    const rawFields = asArray(p.fields).length > 0 ? asArray(p.fields) : asArray(p.inputs ?? p.controls ?? p.items ?? p.questions)
    if (!Array.isArray(p.fields) && rawFields.length > 0) repaired = true
    const fields = rawFields
      .map((f) => {
        const o = isObj(f) ? f : {}
        let type = asString(o.type, 'text')
        if (!(FORM_FIELD_TYPES as readonly string[]).includes(type)) {
          type = 'text'
          repaired = true
        }
        return {
          type,
          label: asString(o.label ?? o.name ?? o.title ?? o.placeholder ?? o.key),
          options: asArray(o.options ?? o.choices ?? o.values)
            .map((x) => (isObj(x) ? asString(x.label ?? x.value ?? x.text) : asString(x)))
            .filter(Boolean),
          required: o.required === true,
          min: asNumber(o.min),
          max: asNumber(o.max),
          value: asNumber(o.value ?? o.default),
          unit: asString(o.unit),
        }
      })
      .filter((f) => f.label)
      .slice(0, LIMITS.maxFormFields)
    return {
      widget: { title: asString(p.title), submitLabel: asString(p.submitLabel ?? p.button ?? p.cta), fields },
      errors: fields.length ? [] : ['form needs labelled fields'],
      repaired,
    }
  },

  carousel: (p) => {
    let repaired = false
    const rawItems = asArray(p.items).length > 0 ? asArray(p.items) : asArray(p.slides ?? p.images ?? p.cards ?? p.photos)
    if (!Array.isArray(p.items) && rawItems.length > 0) repaired = true
    const items = rawItems
      .map((i) => {
        if (typeof i === 'string') return { image: i, caption: '' }
        const o = isObj(i) ? i : {}
        return {
          image: asString(o.image ?? o.url ?? o.src ?? o.thumb),
          caption: asString(o.caption ?? o.title ?? o.label ?? o.alt ?? o.text),
        }
      })
      .filter((i) => isHttps(i.image))
      .slice(0, 20)
    return {
      widget: { title: asString(p.title), items },
      errors: items.length ? [] : ['carousel needs https images'],
      repaired,
    }
  },

  cards: (p) => {
    let repaired = false
    const rawCards = asArray(p.cards).length > 0 ? asArray(p.cards) : asArray(p.items ?? p.options)
    if (!Array.isArray(p.cards) && rawCards.length > 0) repaired = true
    const cards = rawCards
      .map((c) => {
        const o = isObj(c) ? c : {}
        // A {label, value} pair without `title` is a KPI stat card, not a browsing card.
        if (o.title === undefined && o.label !== undefined && o.value !== undefined) {
          return { title: '', subtitle: '', prompt: '' }
        }
        return {
          title: asString(o.title ?? o.label ?? o.name ?? o.heading),
          subtitle: asString(o.subtitle ?? o.detail ?? o.description ?? o.body ?? o.text ?? o.meta),
          prompt: asString(o.prompt ?? o.action ?? o.query ?? o.value),
        }
      })
      .filter((c) => c.title)
      .slice(0, LIMITS.maxCards)
    return {
      widget: { title: asString(p.title), cards },
      errors: cards.length ? [] : ['cards need titles'],
      repaired,
    }
  },

  followups: (p) => {
    let repaired = false
    const rawPrompts = asArray(p.prompts).length > 0 ? asArray(p.prompts) : asArray(p.questions ?? p.suggestions ?? p.items ?? p.options ?? p.followups)
    if (!Array.isArray(p.prompts) && rawPrompts.length > 0) repaired = true
    const prompts = rawPrompts
      .map((x) => (isObj(x) ? asString(x.prompt ?? x.label ?? x.text ?? x.title ?? x.question) : asString(x)))
      .filter(Boolean)
      .slice(0, 12)
    return {
      widget: { title: asString(p.title), prompts },
      errors: prompts.length ? [] : ['followups need prompts'],
      repaired,
    }
  },

  media: (p) => {
    let repaired = false
    // Two live shapes for a document body, both seen in the probe:
    //   {"title":…,"pages":[{"page":1,"text":…}]} — the pages themselves
    //   {"title":…,"pages":3}                      — a page COUNT only
    const pages = asArray(p.pages)
      .map((x) => (x && typeof x === 'object' ? (x as Record<string, unknown>) : {}))
      .map((o, idx) => ({ page: asNumber(o.page) ?? idx + 1, text: asString(o.text ?? o.body ?? o.content) }))
      .filter((x) => x.text.trim().length > 0)
      .slice(0, 20)
    const pageCount =
      typeof p.pages === 'number' && Number.isFinite(p.pages)
        ? Math.min(Math.max(Math.round(p.pages), 0), 999)
        : pages.length > 0
          ? pages.length
          : 0
    const stated = asString(p.type, '')
    let type = (MEDIA_TYPES as readonly string[]).includes(stated)
      ? stated
      : // A typeless payload that carries pages IS a document; typeless with
        // anything else stays an image, the historical default.
        stated === '' && (pages.length > 0 || pageCount > 0)
        ? 'pdf'
        : 'image'
    if (!(MEDIA_TYPES as readonly string[]).includes(stated)) repaired = true
    // {"type":"image","url":"https://…"} — a single item, not wrapped in items.
    // A ```search fence carries `results` for the same list — the probe watched
    // the live server send it, and the validator called it empty.
    let rawItems = asArray(p.items)
    if (rawItems.length === 0 && asArray(p.results ?? p.images ?? p.links ?? p.files ?? p.products ?? p.gallery ?? p.videos ?? p.tracks).length > 0) {
      rawItems = asArray(p.results ?? p.images ?? p.links ?? p.files ?? p.products ?? p.gallery ?? p.videos ?? p.tracks)
      repaired = true
    }
    if (rawItems.length === 0 && asString(p.url ?? p.src ?? p.image)) {
      rawItems = [{ url: p.url ?? p.src ?? p.image, thumb: p.thumb, title: p.title, caption: p.caption }]
    }
    if (
      rawItems.length === 0 &&
      asString(p.name ?? p.title ?? p.label ?? p.filename) &&
      asString(p.price ?? p.snippet ?? p.subtitle ?? p.caption ?? p.description ?? p.domain ?? p.meta ?? p.size ?? p.mime ?? p.author)
    ) {
      rawItems = [p]
      repaired = true
    }
    const items = rawItems
      .map((i) => {
        if (typeof i === 'string') return { url: i, thumb: '', title: '', subtitle: '', caption: '', meta: '', prompt: '', domain: '', snippet: '', price: '', badge: '', rating: '', mime: '' }
        const o = isObj(i) ? i : {}
        return {
          url: asString(o.url ?? o.src ?? o.image ?? o.href),
          thumb: asString(o.thumb ?? o.thumbnail),
          title: asString(o.title ?? o.name ?? o.label ?? o.filename),
          subtitle: asString(o.subtitle ?? o.author ?? o.artist),
          caption: asString(o.caption ?? o.description ?? o.alt),
          meta: asString(o.meta ?? o.size ?? o.duration),
          prompt: asString(o.prompt),
          domain: asString(o.domain ?? o.source),
          snippet: asString(o.snippet ?? o.summary ?? o.text),
          price: asString(o.price ?? o.cost),
          badge: asString(o.badge ?? o.tag ?? o.status),
          rating: asString(o.rating ?? o.score),
          mime: asString(o.mime ?? o.format),
        }
      })
      .slice(0, 20)
    const code = asString(p.code)
    const text = asString(p.text ?? p.content ?? p.body)
    const ok = items.length > 0 || code.length > 0 || text.length > 0 || pages.length > 0 || pageCount > 0
    return {
      widget: { type, title: asString(p.title), items, code, language: asString(p.language), text, pages, pageCount },
      errors: ok ? [] : ['media needs items, code or text'],
      repaired,
    }
  },

  actions: (p) => {
    let repaired = false
    const rawButtons = asArray(p.buttons).length > 0 ? asArray(p.buttons) : asArray(p.actions ?? p.items ?? p.options)
    if (!Array.isArray(p.buttons) && rawButtons.length > 0) repaired = true
    const buttons = rawButtons
      .map((b) => {
        if (typeof b === 'string') return { label: b, kind: 'prompt', value: b, style: 'filled' }
        const o = isObj(b) ? b : {}
        let kind = asString(o.kind ?? o.type, 'prompt')
        if (!['prompt', 'copy', 'open'].includes(kind)) {
          kind = 'prompt'
          repaired = true
        }
        const label = asString(o.label ?? o.title ?? o.text ?? o.name)
        return {
          label,
          kind,
          value: asString(o.value ?? o.prompt ?? o.url ?? o.href ?? o.action, label),
          style: asString(o.style, 'filled'),
        }
      })
      .filter((b) => b.label && b.value)
      .slice(0, 6)
    return {
      widget: { title: asString(p.title), buttons },
      errors: buttons.length ? [] : ['actions need labelled buttons'],
      repaired,
    }
  },

  panel: (p) => {
    let repaired = false
    const badges = asArray(p.badges ?? p.tags).map((b) => {
      if (typeof b === 'string') return { label: b, tone: 'accent' }
      const o = isObj(b) ? b : {}
      return { label: asString(o.label ?? o.text ?? o.name), tone: asString(o.tone, 'accent') }
    })
    const rawTone = asString(p.severity ?? p.tone ?? p.level ?? p.status).toLowerCase()
    if (rawTone && badges.length === 0) {
      const mappedTone =
        rawTone === 'error' || rawTone === 'critical' || rawTone === 'danger'
          ? 'danger'
          : rawTone === 'warn' || rawTone === 'warning' || rawTone === 'caution'
            ? 'warning'
            : rawTone === 'ok' || rawTone === 'good' || rawTone === 'success'
              ? 'success'
              : 'accent'
      badges.push({ label: rawTone.toUpperCase(), tone: mappedTone })
      repaired = true
    }
    let body = asString(p.body ?? p.text ?? p.content ?? p.detail ?? p.description ?? p.message ?? p.callout ?? p.note)
    if (!body) {
      const list = asArray(p.items ?? p.points ?? p.bullets ?? p.highlights)
        .map((x) => (isObj(x) ? asString(x.text ?? x.label ?? x.title) : asString(x)))
        .filter(Boolean)
      if (list.length > 0) {
        body = list.map((line) => `• ${line}`).join('\n')
        repaired = true
      }
    }
    const widget = {
      title: asString(p.title ?? p.heading ?? p.label ?? p.name),
      summary: asString(p.summary ?? p.subtitle ?? p.header),
      body,
      expanded: typeof p.expanded === 'boolean' ? p.expanded : Boolean(body),
      badges,
      popover: asString(p.popover),
    }
    return {
      widget,
      errors: widget.title || widget.body || widget.summary ? [] : ['panel needs content'],
      repaired,
    }
  },

  voice: (p) => {
    const widget = {
      title: asString(p.title ?? p.label),
      prompt: asString(p.prompt ?? p.text ?? p.query),
      hint: asString(p.hint ?? p.subtitle ?? p.detail),
    }
    return {
      widget,
      errors: widget.prompt || widget.title ? [] : ['voice needs a prompt'],
      repaired: false,
    }
  },

  calc: (p) => {
    let repaired = false
    let type = asString(p.type, 'finance')
    if (!(CALC_TYPES as readonly string[]).includes(type)) {
      type = 'finance'
      repaired = true
    }
    const rawFields = asArray(p.fields).length > 0 ? asArray(p.fields) : asArray(p.inputs ?? p.variables ?? p.parameters)
    if (!Array.isArray(p.fields) && rawFields.length > 0) repaired = true
    const fields = rawFields
      .map((f, idx) => {
        const o = isObj(f) ? f : {}
        const label = asString(o.label ?? o.title ?? o.name ?? o.key)
        const key =
          asString(o.key ?? o.id ?? o.name) ||
          label.toLowerCase().replace(/[^a-z0-9_]+/g, '_').replace(/^_+|_+$/g, '') ||
          `x${idx + 1}`
        return {
          key,
          label: label || key,
          value: asNumber(o.value ?? o.default) ?? 0,
          unit: asString(o.unit),
          min: asNumber(o.min),
          max: asNumber(o.max),
          step: asNumber(o.step),
          choices: asArray(o.choices ?? o.options).map((c) => {
            const ch = isObj(c) ? c : {}
            return { label: asString(ch.label ?? ch.name), value: asNumber(ch.value) ?? 0 }
          }),
        }
      })
      .filter((f) => f.key && f.label)
    const rawOutputs = asArray(p.outputs).length > 0 ? asArray(p.outputs) : asArray(p.results ?? p.formulas)
    if (!Array.isArray(p.outputs) && rawOutputs.length > 0) repaired = true
    const outputs = rawOutputs
      .map((o) => {
        const row = isObj(o) ? o : {}
        return {
          label: asString(row.label ?? row.title ?? row.name),
          expr: asString(row.expr ?? row.formula ?? row.expression ?? row.calc),
          unit: asString(row.unit),
          format: asString(row.format),
          primary: row.primary === true,
        }
      })
      .filter((o) => o.label && o.expr)
    const errors: string[] = []
    if (!fields.length) errors.push('calc needs fields')
    if (!outputs.length) errors.push('calc needs outputs')
    return {
      widget: { type, title: asString(p.title), fields, outputs, note: asString(p.note) },
      errors,
      repaired,
    }
  },

  app: (p) => {
    let repaired = false
    let type = asString(p.type, 'wizard')
    if (!(APP_TYPES as readonly string[]).includes(type)) {
      type = 'wizard'
      repaired = true
    }
    const normAppItem = (raw: unknown): Record<string, unknown> => {
      if (typeof raw === 'string') {
        repaired = true
        return { title: raw, label: raw }
      }
      const o = isObj(raw) ? raw : {}
      const title = asString(o.title ?? o.label ?? o.text ?? o.task ?? o.event ?? o.name)
      const subtitle = asString(o.subtitle ?? o.description ?? o.detail ?? o.assignee ?? o.time)
      const badge = asString(o.badge ?? o.priority ?? o.tag ?? o.status ?? o.value)
      const date = asString(o.date ?? o.day ?? o.when)
      return {
        ...o,
        ...(title ? { title } : {}),
        ...(subtitle ? { subtitle } : {}),
        ...(badge ? { badge } : {}),
        ...(date ? { date } : {}),
      }
    }
    const rawItems = asArray(p.items).length > 0 ? asArray(p.items) : asArray(p.events ?? p.tasks ?? p.cards ?? p.files ?? p.metrics ?? p.kpis ?? p.results)
    if (!Array.isArray(p.items) && rawItems.length > 0) repaired = true

    let rawCols = asArray(p.columns ?? p.lanes ?? p.boards ?? p.headers)
    if (rawCols.length === 0 && isObj(p.columns)) {
      rawCols = Object.entries(p.columns).map(([title, list]) => ({ title, items: asArray(list) }))
      repaired = true
    }
    if (rawCols.length === 0 && type === 'kanban' && (Array.isArray(p.todo) || Array.isArray(p.doing) || Array.isArray(p.in_progress) || Array.isArray(p.done))) {
      const lanes: Array<Record<string, unknown>> = []
      if (Array.isArray(p.todo)) lanes.push({ title: 'To Do', items: p.todo })
      if (Array.isArray(p.in_progress ?? p.doing)) lanes.push({ title: 'In Progress', items: (p.in_progress ?? p.doing) as unknown[] })
      if (Array.isArray(p.done)) lanes.push({ title: 'Done', items: p.done })
      rawCols = lanes
      repaired = true
    }
    const columns = rawCols.map((c) => {
      if (typeof c === 'string') return c
      const o = isObj(c) ? c : {}
      const colTitle = asString(o.title ?? o.name ?? o.label ?? o.status ?? o.header)
      const colItems = asArray(o.items ?? o.cards ?? o.tasks).map(normAppItem)
      return {
        ...o,
        ...(colTitle ? { title: colTitle } : {}),
        items: colItems,
      }
    })
    const steps = asArray(p.steps ?? p.stages).map((s) => {
      if (typeof s === 'string') {
        repaired = true
        return { title: s, body: '' }
      }
      const o = isObj(s) ? s : {}
      const title = asString(o.title ?? o.label ?? o.name ?? o.step)
      const body = asString(o.body ?? o.description ?? o.detail ?? o.text)
      return { ...o, ...(title ? { title } : {}), ...(body ? { body } : {}) }
    })

    const widget = {
      type,
      title: asString(p.title),
      steps,
      columns,
      items: rawItems.map(normAppItem).slice(0, LIMITS.maxAppItems),
      groups: asArray(p.groups ?? p.sections ?? p.categories),
      rows: asArray(p.rows ?? p.data),
      slots: asArray(p.slots ?? p.times),
      month: asString(p.month ?? p.date),
      submitLabel: asString(p.submitLabel),
    }
    const hasBody =
      widget.steps.length > 0 ||
      widget.columns.length > 0 ||
      widget.items.length > 0 ||
      widget.groups.length > 0 ||
      widget.rows.length > 0 ||
      widget.slots.length > 0
    return {
      widget,
      errors: hasBody ? [] : ['app needs steps, columns, items, groups, rows or slots'],
      repaired,
    }
  },

  // ------------------------------------------------------------------
  // v2 — layout, control and study surfaces
  // ------------------------------------------------------------------

  tabs: (p) => {
    let repaired = false
    const rawTabs = asArray(p.tabs).length > 0 ? asArray(p.tabs) : asArray(p.sections ?? p.panels ?? p.items ?? p.views)
    if (!Array.isArray(p.tabs) && rawTabs.length > 0) repaired = true
    const tabs = rawTabs
      .map((t) => {
        const o = isObj(t) ? t : {}
        return {
          label: asString(o.label ?? o.title ?? o.name ?? o.tab),
          body: asString(o.body ?? o.content ?? o.text ?? o.detail ?? o.description),
          items: asArray(o.items ?? o.bullets ?? o.points).map((i) => asString(i)).filter(Boolean).slice(0, LIMITS.maxAppItems),
        }
      })
      .filter((t) => t.label && (t.body || t.items.length > 0))
      .slice(0, LIMITS.maxTabs)
    return {
      widget: { title: asString(p.title), tabs },
      errors: tabs.length ? [] : ['tabs need at least one labelled tab'],
      repaired,
    }
  },

  accordion: (p) => {
    let repaired = false
    const rawSections = asArray(p.sections ?? p.items).length > 0 ? asArray(p.sections ?? p.items) : asArray(p.panels ?? p.faqs ?? p.questions)
    if (!Array.isArray(p.sections) && !Array.isArray(p.items) && rawSections.length > 0) repaired = true
    const sections = rawSections
      .map((s) => {
        const o = isObj(s) ? s : {}
        return {
          label: asString(o.label ?? o.title ?? o.question ?? o.q ?? o.heading ?? o.summary),
          body: asString(o.body ?? o.detail ?? o.content ?? o.text ?? o.answer ?? o.a ?? o.description),
          expanded: o.expanded === true || o.open === true,
        }
      })
      .filter((s) => s.label && s.body)
      .slice(0, LIMITS.maxSections)
    return {
      widget: { title: asString(p.title), sections },
      errors: sections.length ? [] : ['accordion needs labelled sections with body text'],
      repaired,
    }
  },

  steps: (p) => {
    let repaired = false
    const rawSteps = asArray(p.steps).length > 0 ? asArray(p.steps) : asArray(p.items ?? p.stages ?? p.phases)
    if (!Array.isArray(p.steps) && rawSteps.length > 0) repaired = true
    const stepList = rawSteps
      .map((s) => {
        if (typeof s === 'string') return { title: s, body: '', state: 'todo' }
        const o = isObj(s) ? s : {}
        const rawState = asString(o.state ?? o.status).toLowerCase()
        const state =
          rawState === 'done' || rawState === 'completed' || rawState === 'complete' || rawState === 'finished' || o.done === true
            ? 'done'
            : rawState === 'current' || rawState === 'active' || rawState === 'in_progress' || rawState === 'in-progress' || rawState === 'doing'
              ? 'current'
              : 'todo'
        return {
          title: asString(o.title ?? o.label ?? o.name ?? o.step),
          body: asString(o.body ?? o.detail ?? o.description ?? o.text),
          state,
        }
      })
      .filter((s) => s.title)
      .slice(0, LIMITS.maxSteps)
    return {
      widget: { title: asString(p.title), steps: stepList },
      errors: stepList.length ? [] : ['steps need titled steps'],
      repaired,
    }
  },

  menu: (p) => {
    const options = asArray(p.options ?? p.items ?? p.choices ?? p.actions)
      .map((o) => {
        const row = isObj(o) ? o : {}
        const label = typeof o === 'string' ? o : asString(row.label ?? row.title ?? row.text ?? row.name)
        return {
          label,
          detail: asString(row.detail ?? row.description ?? row.subtitle),
          prompt: asString(row.prompt ?? row.value ?? row.action, label),
        }
      })
      .filter((o) => o.label)
      .slice(0, LIMITS.maxOptions)
    return {
      widget: { title: asString(p.title), options },
      errors: options.length ? [] : ['menu needs options'],
      repaired: false,
    }
  },

  chips: (p) => {
    const options = asArray(p.options ?? p.chips ?? p.items ?? p.tags ?? p.choices)
      .map((o) => {
        const row = isObj(o) ? o : {}
        const label = typeof o === 'string' ? o : asString(row.label ?? row.title ?? row.text ?? row.name)
        return {
          label,
          prompt: asString(row.prompt ?? row.value ?? row.action, label),
        }
      })
      .filter((o) => o.label)
      .slice(0, LIMITS.maxOptions)
    return {
      widget: { title: asString(p.title), options, multi: p.multi !== false },
      errors: options.length ? [] : ['chips need options'],
      repaired: false,
    }
  },

  rating: (p) => {
    let repaired = false
    let style = asString(p.style ?? p.type, 'star')
    if (!['star', 'heart', 'thumb', 'nps'].includes(style)) {
      style = 'star'
      repaired = true
    }
    const widget = {
      title: asString(p.title ?? p.question),
      prompt: asString(p.prompt ?? p.subtitle ?? p.label),
      max: clamp(asNumber(p.max ?? p.scale) ?? (style === 'nps' ? 11 : 5), 2, 11),
      style,
      labels: asArray(p.labels).map((l) => asString(l)).slice(0, 2),
    }
    return {
      widget,
      errors: widget.title || widget.prompt ? [] : ['rating needs a title or prompt'],
      repaired,
    }
  },

  poll: (p) => {
    const options = asArray(p.options ?? p.choices ?? p.items ?? p.answers)
      .map((o) => {
        const row = isObj(o) ? o : {}
        return typeof o === 'string'
          ? { label: o, votes: 0 }
          : {
              label: asString(row.label ?? row.text ?? row.option ?? row.title ?? row.choice),
              votes: asNumber(row.votes ?? row.count ?? row.value) ?? 0,
            }
      })
      .filter((o) => o.label)
      .slice(0, LIMITS.maxOptions)
    return {
      widget: { title: asString(p.title), question: asString(p.question ?? p.prompt ?? p.q ?? p.title), options },
      errors: options.length >= 2 ? [] : ['poll needs at least two options'],
      repaired: false,
    }
  },

  diff: (p) => {
    const before = asString(p.before ?? p.old ?? p.original ?? p.left ?? p.oldCode)
    const after = asString(p.after ?? p.newText ?? p.updated ?? p.new ?? p.right ?? p.newCode)
    const unified = asString(p.unified ?? p.patch ?? p.diff)
    const ok = unified.length > 0 || before.length > 0 || after.length > 0
    return {
      widget: { title: asString(p.title), language: asString(p.language ?? p.lang), before, after, unified },
      errors: ok ? [] : ['diff needs before/after or a unified patch'],
      repaired: false,
    }
  },

  tree: (p) => {
    const read = (v: unknown, depth: number): { label: string; detail: string; children: unknown[] } | null => {
      if (depth > LIMITS.maxTreeDepth) return null
      if (typeof v === 'string') return { label: v, detail: '', children: [] }
      const o = isObj(v) ? v : {}
      const label = asString(o.label ?? o.name ?? o.title ?? o.id)
      if (!label) return null
      return {
        label,
        detail: asString(o.detail ?? o.meta ?? o.description ?? o.value),
        children: asArray(o.children ?? o.items ?? o.nodes ?? o.subitems)
          .map((c) => read(c, depth + 1))
          .filter((c): c is { label: string; detail: string; children: unknown[] } => c !== null),
      }
    }
    const rawRoots =
      asArray(p.roots).length > 0
        ? asArray(p.roots)
        : isObj(p.root ?? p.tree ?? p.hierarchy)
          ? [p.root ?? p.tree ?? p.hierarchy]
          : asArray(p.nodes ?? p.items ?? p.children)
    const roots = rawRoots
      .map((r) => read(r, 0))
      .filter((r): r is { label: string; detail: string; children: unknown[] } => r !== null)
      .slice(0, LIMITS.maxOptions)
    return {
      widget: { title: asString(p.title), roots },
      errors: roots.length ? [] : ['tree needs at least one root node'],
      repaired: false,
    }
  },

  flashcards: (p) => {
    const cards = asArray(p.cards ?? p.items ?? p.deck ?? p.pairs ?? p.questions)
      .map((c) => {
        const o = isObj(c) ? c : {}
        return {
          front: asString(o.front ?? o.q ?? o.question ?? o.term ?? o.prompt ?? o.title),
          back: asString(o.back ?? o.a ?? o.answer ?? o.definition ?? o.detail ?? o.body ?? o.text),
        }
      })
      .filter((c) => c.front && c.back)
      .slice(0, LIMITS.maxFlashcards)
    return {
      widget: { title: asString(p.title), cards },
      errors: cards.length ? [] : ['flashcards need front and back text'],
      repaired: false,
    }
  },

  checklist: (p) => {
    const items = asArray(p.items ?? p.tasks ?? p.todos ?? p.steps ?? p.list)
      .map((i) => {
        const o = isObj(i) ? i : {}
        return {
          label: typeof i === 'string' ? i : asString(o.label ?? o.title ?? o.text ?? o.task ?? o.name),
          detail: asString(o.detail ?? o.description ?? o.note ?? o.body),
          done: o.done === true || o.checked === true || o.completed === true || o.status === 'done',
        }
      })
      .filter((i) => i.label)
      .slice(0, LIMITS.maxChecklistItems)
    return {
      widget: { title: asString(p.title), items },
      errors: items.length ? [] : ['checklist needs items'],
      repaired: false,
    }
  },

  // ------------------------------------------------------------------
  // v3 — simulations, 3D stages, canvases, editors, runtime
  // ------------------------------------------------------------------

  sim: (p) => {
    let repaired = false
    let sim = asString(p.sim ?? p.type, 'particles')
    if (!(SIM_TYPES as readonly string[]).includes(sim)) {
      sim = 'particles'
      repaired = true
    }
    const knob = (key: string, lo: number, hi: number, dflt: number) => {
      const v = asNumber(p[key])
      return v === null ? dflt : clamp(v, lo, hi)
    }
    const widget = {
      sim,
      title: asString(p.title),
      gravity: knob('gravity', -30, 30, 9.8),
      speed: knob('speed', 0.1, 3, 1),
      damping: knob('damping', 0, 1, 0.99),
      size: knob('size', 40, 100, 64),
      count: Math.round(knob('count', 10, LIMITS.maxSimBodies, 60)),
      seed: Math.round(knob('seed', 1, 9999, 42)),
      interactive: p.interactive !== false,
      note: asString(p.note),
    }
    return { widget, errors: [], repaired }
  },

  stage3d: (p) => {
    let repaired = false
    let view = asString(p.view ?? p.type ?? p.mode, 'scene')
    if (!(STAGE3D_TYPES as readonly string[]).includes(view)) {
      view = 'scene'
      repaired = true
    }
    const nodes = asArray(p.nodes)
      .map((n, i) => {
        const o = isObj(n) ? n : {}
        const pick = (k: string) => asNumber(o[k]) ?? 0
        return {
          id: asString(o.id ?? o.label, `n${i}`),
          label: asString(o.label ?? o.id, `n${i}`),
          x: pick('x'),
          y: pick('y'),
          z: pick('z'),
          r: asNumber(o.r) ?? undefined,
          group: asString(o.group),
        }
      })
      .slice(0, LIMITS.max3dNodes)
    const bonds = asArray(p.bonds ?? p.links ?? p.edges)
      .map((b) => {
        const pair = asArray(b).map((x) => asNumber(x)).filter((n): n is number => n !== null)
        return pair.length >= 2 ? [Math.round(pair[0]), Math.round(pair[1])] : null
      })
      .filter((b): b is [number, number] => b !== null)
      .filter(([a, b]) => a >= 0 && b >= 0 && a < nodes.length && b < nodes.length)
      .slice(0, 200)
    let zExpr: string | undefined = asString(p.z)
    // surface fields are a whitelisted math expression over x,y. Two gates:
    // 1. obvious junk chars; 2. the expression must actually EVALUATE under
    // the shared calc engine (the same one the renderer uses) — so
    // "fetch(window)" or any non-math identifier is dropped here, in the
    // validator, not merely at render time.
    if (zExpr && !/^[\s()+\-*/%^,.a-z0-9]*$/i.test(zExpr)) {
      zExpr = undefined
      repaired = true
    }
    if (zExpr) {
      try {
        const probe = evaluateExpr(zExpr, { x: 0.5, y: 0.5 })
        if (!Number.isFinite(probe)) throw new Error('non-finite')
      } catch {
        zExpr = undefined
        repaired = true
      }
    }
    const widget = {
      view,
      title: asString(p.title),
      nodes,
      bonds,
      ...(zExpr && !repaired ? { z: zExpr } : {}),
      span: asNumber(p.span) ?? 2,
      spin: p.spin !== false,
      note: asString(p.note),
    }
    return { widget, errors: [], repaired }
  },

  canvas: (p) => {
    let repaired = false
    let surface = asString(p.surface ?? p.type, 'whiteboard')
    if (!(CANVAS_TYPES as readonly string[]).includes(surface)) {
      surface = 'whiteboard'
      repaired = true
    }
    const nodes = asArray(p.nodes)
      .map((n, i) => {
        const o = isObj(n) ? n : {}
        return {
          id: i,
          label: asString(o.label ?? o.title, `Node ${i + 1}`),
          x: clamp(asNumber(o.x) ?? 0.5, 0, 1),
          y: clamp(asNumber(o.y) ?? 0.5, 0, 1),
          group: asString(o.group),
        }
      })
      .slice(0, LIMITS.maxCanvasNodes)
    const edges = asArray(p.edges ?? p.links)
      .map((e) => {
        if (isObj(e)) {
          const a = asNumber(e.source ?? e.from)
          const b = asNumber(e.target ?? e.to)
          return a !== null && b !== null ? [Math.round(a), Math.round(b)] : null
        }
        const pair = asArray(e).map((x) => asNumber(x)).filter((n): n is number => n !== null)
        return pair.length >= 2 ? [Math.round(pair[0]), Math.round(pair[1])] : null
      })
      .filter((e): e is [number, number] => e !== null)
      .filter(([a, b]) => a >= 0 && b >= 0 && a < nodes.length && b < nodes.length)
    const widget = { surface, title: asString(p.title), nodes, edges, note: asString(p.note) }
    return { widget, errors: [], repaired }
  },

  editor: (p) => {
    let repaired = false
    let editor = asString(p.editor ?? p.type, 'sheet')
    if (!(EDITOR_TYPES as readonly string[]).includes(editor)) {
      editor = 'sheet'
      repaired = true
    }
    const columns = asArray(p.columns ?? p.headers).map((c) => (isObj(c) ? asString(c.label ?? c.title ?? c.name ?? c.key) : asString(c))).filter(Boolean).slice(0, 12)
    const rows = asArray(p.rows ?? (Array.isArray(p.data) ? p.data : undefined))
      .map((r) => {
        if (Array.isArray(r)) return r.map((x) => asString(x)).slice(0, 12)
        if (isObj(r) && columns.length > 0) return columns.map((col) => asString(r[col] ?? r[col.toLowerCase()]))
        return []
      })
      .filter((r) => r.length > 0)
      .slice(0, 60)
    const cells: Record<string, string> = {}
    const rawCells = isObj(p.cells) ? p.cells : isObj(p.data) ? p.data : {}
    for (const [k, v] of Object.entries(rawCells)) {
      cells[k.toUpperCase()] = asString(v)
    }
    if (editor === 'sheet' && Object.keys(cells).length === 0 && (columns.length > 0 || rows.length > 0)) {
      let rowOffset = 1
      if (columns.length > 0) {
        columns.slice(0, 12).forEach((col, ci) => {
          cells[`${String.fromCharCode(65 + ci)}1`] = col
        })
        rowOffset = 2
      }
      rows.slice(0, 11).forEach((row, ri) => {
        row.slice(0, 12).forEach((val, ci) => {
          cells[`${String.fromCharCode(65 + ci)}${ri + rowOffset}`] = val
        })
      })
      repaired = true
    }
    const widget = {
      editor,
      title: asString(p.title),
      // sheet
      columns,
      cells,
      // nodes / site / pivot
      nodes: asArray(p.nodes).slice(0, LIMITS.maxCanvasNodes),
      edges: asArray(p.edges).slice(0, 120),
      blocks: asArray(p.blocks).slice(0, 20),
      groups: asArray(p.groups).slice(0, 8),
      rows,
      // timeline editors
      tracks: asArray(p.tracks).slice(0, LIMITS.maxTracks),
      duration: Math.min(600, Math.max(1, asNumber(p.duration) ?? 60)),
      // css editor
      layout: isObj(p.layout) ? p.layout : {},
      note: asString(p.note),
      submitLabel: asString(p.submitLabel),
    }
    return { widget, errors: [], repaired }
  },

  collab: (p) => {
    let repaired = false
    let surface = asString(p.surface ?? p.type, 'counter')
    if (!(COLLAB_TYPES as readonly string[]).includes(surface)) {
      surface = 'counter'
      repaired = true
    }
    // room: slug — this is the shared key everyone converges on, so it must
    // survive a low-tier model: lowercase, [a-z0-9-], <=48 chars, default lobby.
    const rawRoom = asString(p.room ?? p.channel ?? p.id, 'lobby')
    const room = rawRoom.toLowerCase().replace(/[^a-z0-9-]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 48) || 'lobby'
    const widget = {
      surface,
      room,
      title: asString(p.title),
      label: asString(p.label),
      items: asArray(p.items ?? p.options).map((i) => asString(i)).filter(Boolean).slice(0, 12),
      start: asNumber(p.start ?? p.value) ?? 0,
      note: asString(p.note),
    }
    return { widget, errors: [], repaired }
  },

  terminal: (p) => {
    const widget = {
      title: asString(p.title, 'Terminal'),
      welcome: asString(p.welcome),
      commands: asArray(p.commands)
        .map((c) => {
          const o = isObj(c) ? c : {}
          return { name: asString(o.name), description: asString(o.description) }
        })
        .filter((c) => c.name)
        .slice(0, 12),
    }
    return { widget, errors: [], repaired: false }
  },
}

export { CHART_TYPES }
