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

import {
  APP_TYPES,
  CALC_TYPES,
  CHART_TYPES,
  FORM_FIELD_TYPES,
  LIMITS,
  MEDIA_TYPES,
  injectWidgetType,
  isChartType,
  isWidgetKind,
  injectChartType,
  widgetKindOf,
  type WidgetKind,
} from './catalog'

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

/** ```kind\n{...}\n``` at the start of a line. */
const FENCE = /^```([a-zA-Z][\w-]*)[ \t]*\r?\n([\s\S]*?)^```[ \t]*$/gm

export function extractWidgetFences(text: string): WidgetFence[] {
  const out: WidgetFence[] = []
  for (const m of text.matchAll(FENCE)) {
    const language = m[1]
    // A chart sub-type fence (```funnel, ```gantt …) is a chart widget with the
    // type implied by the fence. Normalising here means the persisted answer
    // only ever carries the canonical ```chart form, and the client never sees
    // a payload it would render as a code block.
    const kind = widgetKindOf(language)
    if (!kind) continue
    const start = m.index ?? 0
    const body = m[2].trim()
    const json = language.toLowerCase() !== kind
      ? injectWidgetType(body, language, kind)
      : body
    out.push({ kind, json, start, end: start + m[0].length })
  }
  return out
}

/** A fenced block whose body is plausibly a bare widget payload. */const JSON_FENCE = /^```(json|jsonc|widget|genui|data)[ \t]*\r?\n([\s\S]*?)^```[ \t]*$/gm

/**
 * The widget kind a BARE payload describes, or null when it is not a widget.
 *
 * The model sometimes answers with no fence at all (or a ```json one). Left
 * alone, that JSON reaches the client as body text. Kind is inferred from the
 * payload's own discriminator and then CONFIRMED by the real validator, so
 * prose containing braces is never promoted. Mirrors inferWidgetKind() on the
 * clients.
 */
export function inferWidgetKind(json: string): WidgetKind | null {
  let parsed: unknown
  try {
    parsed = JSON.parse(json)
  } catch {
    return null
  }
  if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) return null
  const obj = parsed as Record<string, unknown>
  const has = (k: string) => k in obj
  const candidates: WidgetKind[] = []
  if (has('series')) candidates.push('chart')
  if (has('columns') && has('rows')) candidates.push('table')
  if (has('cards')) candidates.push('stat')
  if (has('entries')) candidates.push('timeline')
  if (has('questions')) candidates.push('quiz')
  if (has('fields')) candidates.push('form')
  if (has('prompts')) candidates.push('followups')
  if (has('buttons')) candidates.push('actions')
  if (has('items')) candidates.push('progress', 'gauge', 'carousel', 'app', 'media')
  if (has('pages')) candidates.push('media')
  if (has('steps') || has('groups')) candidates.push('app')
  for (const kind of candidates) {
    if (validateWidget(kind, json).ok) return kind
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
  if (whole.startsWith('{') && whole.endsWith('}')) {
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
  return null
}

/**
 * Repairs the failures models actually produce: smart quotes, trailing commas,
 * comments, a stray "Here is the JSON:" prefix, or a single missing brace.
 * Returns null when the text is not recoverable.
 */
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
  let inString = false
  let escaped = false
  for (let i = 0; i < input.length; i++) {
    const ch = input[i]
    if (inString) {
      out += ch
      if (escaped) escaped = false
      else if (ch === '\\') escaped = true
      else if (ch === '"') inString = false
      continue
    }
    if (ch === '"') {
      inString = true
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

export function repairJson(raw: string): string | null {
  let s = raw.trim()
  if (!s) return null

  // Strip a prose prefix before the first brace.
  const firstBrace = s.indexOf('{')
  if (firstBrace > 0) s = s.slice(firstBrace)
  const lastBrace = s.lastIndexOf('}')
  if (lastBrace >= 0) s = s.slice(0, lastBrace + 1)

  s = stripJsonComments(s)
    .replace(/[\u201c\u201d]/g, '"')
    .replace(/[\u2018\u2019]/g, "'")
    .replace(/,\s*([}\]])/g, '$1')

  try {
    JSON.parse(s)
    return s
  } catch {
    // One missing closing brace is the single most common truncation.
    const opens = (s.match(/\{/g) ?? []).length
    const closes = (s.match(/\}/g) ?? []).length
    if (opens === closes + 1) {
      try {
        JSON.parse(s + '}')
        return s + '}'
      } catch {
        return null
      }
    }
    return null
  }
}

// ---------------------------------------------------------------------------
// Primitive coercions
// ---------------------------------------------------------------------------

const isObj = (v: unknown): v is Record<string, unknown> =>
  typeof v === 'object' && v !== null && !Array.isArray(v)

const asArray = (v: unknown): unknown[] => (Array.isArray(v) ? v : [])

const asString = (v: unknown, fallback = ''): string =>
  typeof v === 'string' ? v : typeof v === 'number' ? String(v) : fallback

const asNumber = (v: unknown): number | null => {
  if (typeof v === 'number' && Number.isFinite(v)) return v
  if (typeof v === 'string') {
    const n = Number(v.replace(/[,\s]/g, ''))
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

  let payload: unknown
  try {
    payload = JSON.parse(repairedJson)
  } catch {
    return { ok: false, kind, errors: ['invalid JSON'] }
  }
  if (!isObj(payload)) return { ok: false, kind, errors: ['payload is not an object'] }

  const jsonRepaired = repairedJson !== rawJson.trim()
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
  if (['plain', 'tinted', 'hero', 'outline'].includes(variant)) styled.variant = variant

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
    if (typeof item === 'number' && Number.isFinite(item)) {
      out.push([index, item])
      return
    }
    if (Array.isArray(item)) {
      const first = item[0]
      if (typeof first === 'string' && first.trim()) labelHints.push(first.trim())
      const nums = item.map(asNumber).filter((n): n is number => n !== null)
      if (nums.length >= 2) out.push([nums[0], nums[1]])
      else if (nums.length === 1) out.push([index, nums[0]])
      return
    }
    if (isObj(item)) {
      const y = asNumber(item.y) ?? asNumber(item.value)
      if (y === null) return
      out.push([asNumber(item.x) ?? index, y])
    }
  })
  return out.slice(0, LIMITS.maxPointsPerSeries)
}

const CHECKS: Record<WidgetKind, (p: Record<string, unknown>) => Check> = {
  chart: (p) => {
    const errors: string[] = []
    let repaired = false
    let type = asString(p.type, 'line')
    if (!isChartType(type)) {
      type = 'bar'
      repaired = true
    }
    const pieLike = type === 'pie' || type === 'donut'
    const minPoints = pieLike ? 1 : 2

    // Models send [[x,y]] (documented), [12,18] (flat), [["Q1",120]] (string
    // label as x) and [{"x":0,"y":12}]. Only the first used to survive, so the
    // other three were silently DROPPED as invalid — a correct answer thrown
    // away by our own validator.
    // Models also put the data at the top level instead of in a series:
    //   {"type":"bar","labels":["Q1","Q2"],"values":[120,180]}
    //   {"type":"line","data":[[0,1],[1,2]]}
    // Both are unambiguous, and both used to be rejected as "chart needs at
    // least one series" — a correct answer discarded by the validator.
    let rawSeries = asArray(p.series)
    if (rawSeries.length === 0) {
      const flat = asArray(p.values).length > 0 ? asArray(p.values) : asArray(p.data)
      if (flat.length > 0) {
        rawSeries = [{ name: asString(p.name, 'Value'), data: flat }]
        repaired = true
      }
    }

    const labelHints: string[] = []
    const series = rawSeries
      .map((s) => {
        const row = isObj(s) ? s : {}
        const data = normalisePoints(asArray(row.data), labelHints)
        return { name: asString(row.name, 'Series'), data }
      })
      .filter((s) => s.data.length >= minPoints)
      .slice(0, LIMITS.maxChartSeries)

    if (series.length === 0) errors.push('chart needs at least one series with enough points')
    const declared = asArray(p.labels).map((l) => asString(l)).filter(Boolean)
    const labels = declared.length > 0 ? declared : labelHints

    return {
      widget: { type, title: asString(p.title), unit: asString(p.unit), labels, series },
      errors,
      repaired,
    }
  },

  stat: (p) => {
    const cards = asArray(p.cards)
      .map((c) => {
        const row = isObj(c) ? c : {}
        return {
          label: asString(row.label),
          value: asString(row.value),
          unit: asString(row.unit),
          delta: asString(row.delta),
          direction: asString(row.direction),
        }
      })
      .filter((c) => c.label && c.value)
      .slice(0, LIMITS.maxCards)
    return {
      widget: { title: asString(p.title), cards },
      errors: cards.length ? [] : ['stat needs at least one card'],
      repaired: false,
    }
  },

  table: (p) => {
    const columns = asArray(p.columns)
      .map((c) => asString(c))
      .filter(Boolean)
      .slice(0, LIMITS.maxTableColumns)
    const rows = asArray(p.rows)
      .map((r) => asArray(r).map((c) => asString(c)).slice(0, columns.length))
      .filter((r) => r.length > 0)
      .slice(0, LIMITS.maxTableRows)
    const errors: string[] = []
    if (!columns.length) errors.push('table needs columns')
    if (!rows.length) errors.push('table needs rows')
    return {
      widget: { title: asString(p.title), columns, rows, sortable: p.sortable === true },
      errors,
      repaired: false,
    }
  },

  compare: (p) => {
    const side = (v: unknown) => {
      const o = isObj(v) ? v : {}
      return {
        title: asString(o.title),
        items: asArray(o.items).map((i) => asString(i)).filter(Boolean),
      }
    }
    const left = side(p.left)
    const right = side(p.right)
    return {
      widget: { title: asString(p.title), left, right },
      errors: left.items.length || right.items.length ? [] : ['compare needs items on a side'],
      repaired: false,
    }
  },

  progress: (p) => {
    const items = asArray(p.items)
      .map((i) => {
        const o = isObj(i) ? i : {}
        const percent = asNumber(o.percent)
        return { label: asString(o.label), percent: percent === null ? 0 : clamp(percent, 0, 100) }
      })
      .filter((i) => i.label)
    return {
      widget: { title: asString(p.title), items },
      errors: items.length ? [] : ['progress needs items'],
      repaired: false,
    }
  },

  timeline: (p) => {
    const entries = asArray(p.entries)
      .map((e) => {
        const o = isObj(e) ? e : {}
        return { date: asString(o.date), title: asString(o.title), detail: asString(o.detail) }
      })
      .filter((e) => e.date && e.title)
    return {
      widget: { title: asString(p.title), entries },
      errors: entries.length ? [] : ['timeline needs dated entries'],
      repaired: false,
    }
  },

  gauge: (p) => {
    const items = asArray(p.items)
      .map((i) => {
        const o = isObj(i) ? i : {}
        const value = asNumber(o.value)
        const max = asNumber(o.max)
        return {
          label: asString(o.label),
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
      repaired: false,
    }
  },

  quiz: (p) => {
    const questions = asArray(p.questions)
      .map((q) => {
        const o = isObj(q) ? q : {}
        const options = asArray(o.options).map((x) => asString(x)).filter(Boolean)
        const answer = asNumber(o.answer)
        return {
          q: asString(o.q),
          options,
          answer: options.length ? clamp(answer === null ? 0 : answer, 0, options.length - 1) : 0,
          explain: asString(o.explain),
        }
      })
      .filter((q) => q.q && q.options.length >= 2)
      .slice(0, LIMITS.maxQuizQuestions)
    return {
      widget: { title: asString(p.title), questions },
      errors: questions.length ? [] : ['quiz needs questions with options'],
      repaired: false,
    }
  },

  form: (p) => {
    let repaired = false
    const fields = asArray(p.fields)
      .map((f) => {
        const o = isObj(f) ? f : {}
        let type = asString(o.type, 'text')
        if (!(FORM_FIELD_TYPES as readonly string[]).includes(type)) {
          type = 'text'
          repaired = true
        }
        return {
          type,
          label: asString(o.label),
          options: asArray(o.options).map((x) => asString(x)).filter(Boolean),
          required: o.required === true,
          min: asNumber(o.min),
          max: asNumber(o.max),
          value: asNumber(o.value),
          unit: asString(o.unit),
        }
      })
      .filter((f) => f.label)
      .slice(0, LIMITS.maxFormFields)
    return {
      widget: { title: asString(p.title), submitLabel: asString(p.submitLabel), fields },
      errors: fields.length ? [] : ['form needs labelled fields'],
      repaired,
    }
  },

  carousel: (p) => {
    const items = asArray(p.items)
      .map((i) => {
        const o = isObj(i) ? i : {}
        return { image: asString(o.image), caption: asString(o.caption) }
      })
      .filter((i) => isHttps(i.image))
      .slice(0, 20)
    return {
      widget: { title: asString(p.title), items },
      errors: items.length ? [] : ['carousel needs https images'],
      repaired: false,
    }
  },

  cards: (p) => {
    const cards = asArray(p.cards)
      .map((c) => {
        const o = isObj(c) ? c : {}
        return {
          title: asString(o.title),
          subtitle: asString(o.subtitle),
          prompt: asString(o.prompt),
        }
      })
      .filter((c) => c.title)
      .slice(0, LIMITS.maxCards)
    return {
      widget: { title: asString(p.title), cards },
      errors: cards.length ? [] : ['cards need titles'],
      repaired: false,
    }
  },

  followups: (p) => {
    const prompts = asArray(p.prompts).map((x) => asString(x)).filter(Boolean).slice(0, 12)
    return {
      widget: { title: asString(p.title), prompts },
      errors: prompts.length ? [] : ['followups need prompts'],
      repaired: false,
    }
  },

  media: (p) => {
    let repaired = false
    // Two live shapes for a document body, both seen in the probe:
    //   {"title":…,"pages":[{"page":1,"text":…}]} — the pages themselves
    //   {"title":…,"pages":3}                      — a page COUNT only
    const pages = asArray(p.pages)
      .map((x) => (x && typeof x === 'object' ? (x as Record<string, unknown>) : {}))
      .map((o) => ({ page: asNumber(o.page) ?? 0, text: asString(o.text) }))
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
    if (rawItems.length === 0 && asArray(p.results).length > 0) {
      rawItems = asArray(p.results)
      repaired = true
    }
    if (rawItems.length === 0 && asString(p.url)) {
      rawItems = [{ url: p.url, thumb: p.thumb, title: p.title, caption: p.caption }]
    }
    const items = rawItems
      .map((i) => {
        const o = isObj(i) ? i : {}
        return {
          url: asString(o.url),
          thumb: asString(o.thumb),
          title: asString(o.title),
          subtitle: asString(o.subtitle),
          caption: asString(o.caption),
          meta: asString(o.meta),
          prompt: asString(o.prompt),
          domain: asString(o.domain),
          snippet: asString(o.snippet),
          price: asString(o.price),
          badge: asString(o.badge),
          rating: asString(o.rating),
          mime: asString(o.mime),
        }
      })
      .slice(0, 20)
    const code = asString(p.code)
    const text = asString(p.text)
    const ok = items.length > 0 || code.length > 0 || text.length > 0 || pages.length > 0 || pageCount > 0
    return {
      widget: { type, title: asString(p.title), items, code, language: asString(p.language), text, pages, pageCount },
      errors: ok ? [] : ['media needs items, code or text'],
      repaired,
    }
  },

  actions: (p) => {
    let repaired = false
    const buttons = asArray(p.buttons)
      .map((b) => {
        const o = isObj(b) ? b : {}
        let kind = asString(o.kind, 'prompt')
        if (!['prompt', 'copy', 'open'].includes(kind)) {
          kind = 'prompt'
          repaired = true
        }
        return {
          label: asString(o.label),
          kind,
          value: asString(o.value),
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
    const widget = {
      title: asString(p.title),
      summary: asString(p.summary),
      body: asString(p.body),
      expanded: p.expanded === true,
      badges: asArray(p.badges).map((b) => {
        const o = isObj(b) ? b : {}
        return { label: asString(o.label), tone: asString(o.tone, 'accent') }
      }),
      popover: asString(p.popover),
    }
    return {
      widget,
      errors: widget.title || widget.body || widget.summary ? [] : ['panel needs content'],
      repaired: false,
    }
  },

  voice: (p) => {
    const widget = {
      title: asString(p.title),
      prompt: asString(p.prompt),
      hint: asString(p.hint),
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
    const fields = asArray(p.fields)
      .map((f) => {
        const o = isObj(f) ? f : {}
        return {
          key: asString(o.key),
          label: asString(o.label),
          value: asNumber(o.value) ?? 0,
          unit: asString(o.unit),
          min: asNumber(o.min),
          max: asNumber(o.max),
          step: asNumber(o.step),
          choices: asArray(o.choices).map((c) => {
            const ch = isObj(c) ? c : {}
            return { label: asString(ch.label), value: asNumber(ch.value) ?? 0 }
          }),
        }
      })
      .filter((f) => f.key && f.label)
    const outputs = asArray(p.outputs)
      .map((o) => {
        const row = isObj(o) ? o : {}
        return {
          label: asString(row.label),
          expr: asString(row.expr),
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
    const widget = {
      type,
      title: asString(p.title),
      steps: asArray(p.steps),
      columns: asArray(p.columns),
      items: asArray(p.items).slice(0, LIMITS.maxAppItems),
      groups: asArray(p.groups),
      rows: asArray(p.rows),
      slots: asArray(p.slots),
      month: asString(p.month),
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
}

export { CHART_TYPES }
