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

import {
  CHART_TYPES,
  LIMITS,
  isChartType,
  type WidgetKind,
} from './catalog'
import { extractBareWidget, extractWidgetFences, inferWidgetKind, validateWidget, validationErrors } from './validate'
import { directWidgetArt, type ArtDirection } from './art-direct'
import { sanitizeReplyEmojis } from '../nexly/icons'
import { chartPreference, categoricalChartType, chronologicalLabels, supportsCategoryRecovery } from '../nexly/chart-selection'

export interface CompileInput {
  reply: string
  userPrompt: string
  priorText?: string
}

export type CompilePath = 'model' | 'repaired' | 'fallback' | 'none'

export interface CompileResult {
  text: string
  widgets: number
  path: CompilePath
  errors: string[]
}

interface Point {
  label: string
  value: number
  display: string
  percent: boolean
  unit: string
}

/** Null means no explicit type, not an instruction to invent a bar chart. */
export function requestedChartType(prompt: string): (typeof CHART_TYPES)[number] | null {
  return chartPreference(prompt).type
}

export function compileWidgets(input: CompileInput): CompileResult {
  const { reply, userPrompt, priorText = '' } = input
  const fences = extractWidgetFences(reply)
  // No fenced widget? The payload may still be there, unfenced or inside a
  // ```json block — the model does this constantly. Promote it to a canonical
  // fence instead of shipping raw JSON to the user as body text.
  if (fences.length === 0) {
    const bare = extractBareWidget(reply)
    if (bare) fences.push(bare)
  }

  const valid: Array<{ start: number; end: number; replacement: string; repaired: boolean }> = []
  const invalid: Array<{ start: number; end: number; replacement: string }> = []
  const errors: string[] = []
  // Tracks the block above so two adjacent widgets never share a face. The
  // model's explicit accent/variant wins; this only breaks ties.
  const directions: ArtDirection[] = []

  for (const fence of fences) {
    let result = validateWidget(fence.kind, fence.json)
    // Cross-kind self-healing: when the fence language and the payload's real
    // structure disagree (e.g. a ```gantt fence holding dated timeline entries,
    // a ```cards fence holding stat KPIs or flashcards, a ```table fence
    // holding a left/right comparison), infer the true kind from the payload
    // and validate against it before dropping the block.
    if (!result.ok && fence.kind !== 'checklist' && fence.kind !== 'collab') {
      const healedKind = inferWidgetKind(fence.json)
      if (healedKind && healedKind !== fence.kind) {
        const healed = validateWidget(healedKind, fence.json)
        if (healed.ok) result = healed
      }
    }
    if (result.ok) {
      const payload = JSON.parse(result.json) as Record<string, unknown>
      const direction = directWidgetArt({
        kind: result.kind,
        payload,
        prior: directions.length > 0 ? directions[directions.length - 1] : null,
        seen: directions,
        priorText,
      })
      directions.push(direction)
      const styled = JSON.stringify({
        ...payload,
        accent: direction.accent,
        variant: direction.variant,
      })
      valid.push({
        start: fence.start,
        end: fence.end,
        // Re-serialise with the guaranteed face. The style keys are validated
        // upstream, so this is the one place that decides what the client sees.
        // A payload already hugging the 64KB cap keeps its renderer default
        // rather than overflow the client's own limit by forty bytes.
        replacement: fenceBlock(
          result.kind,
          styled.length <= LIMITS.maxWidgetBytes ? styled : result.json
        ),
        repaired: result.repaired,
      })
    } else {
      const reason = `${fence.kind}: ${validationErrors(result).join(', ')}`
      invalid.push({ start: fence.start, end: fence.end, replacement: widgetSourceFallback(fence.json, `Interactive content could not be rendered (${reason}). Original source is preserved below.`) })
      errors.push(reason)
    }
  }

  // Cap the answer at the documented maximum; extra widgets become prose.
  const kept = valid.slice(0, LIMITS.maxWidgetsPerAnswer)
  const overflow = valid.slice(LIMITS.maxWidgetsPerAnswer)

  if (kept.length > 0) {
    let text = rewrite(reply, [...kept, ...overflow.map((v) => ({ ...v, replacement: widgetSourceFallback(reply.slice(v.start, v.end), 'Additional interactive content exceeds this response’s widget limit. Original source is preserved below.') }))], invalid)
    return {
      text: text.trimEnd(),
      widgets: kept.length,
      path: kept.some((v) => v.repaired) ? 'repaired' : 'model',
      errors,
    }
  }

  // An invalid data widget is not evidence for an invented replacement chart.
  if (invalid.length > 0) return { text: strip(reply, invalid), widgets: 0, path: 'none', errors }

  // Recover only data the answer really contains. Respect presentation opt-outs;
  // a generic request does not identify either a chart type or a data schema.
  const preference = chartPreference(userPrompt)
  if (preference.suppressed) return { text: reply, widgets: 0, path: 'none', errors }
  const fromReply = extractPoints(reply)
  const usePrior = preference.requested && /\b(?:this|that|these|those|above|previous|same|it)\b|^(?:please\s+)?(?:show|draw|make|create|render|plot)\s+(?:me\s+)?(?:a\s+)?(?:chart|graph|plot)[.!?]?$/i.test(userPrompt.trim())
  const points = fromReply.length >= 2 ? fromReply : usePrior ? extractPoints(priorText) : []
  if (points.length < 2 || new Set(points.map(point => point.unit)).size !== 1) {
    return { text: reply, widgets: 0, path: 'none', errors }
  }
  // A real multi-observation trend can be useful without a magic "chart" word.
  // Constants, unrelated numbers and ordinary conversation never trigger this.
  if (!preference.requested && !(points.length >= 3 && chronologicalLabels(points.map(point => point.label)))) {
    return { text: reply, widgets: 0, path: 'none', errors }
  }
  const chartType = preference.type ?? categoricalChartType(points.map(point => point.label))
  if (!supportsCategoryRecovery(chartType, points.map(point => point.value))) {
    return { text: reply, widgets: 0, path: 'none', errors }
  }
  const widget = buildChart(points, chartType)
  const direction = directWidgetArt({ kind: 'chart', payload: widget, priorText })
  const styled = { ...widget, accent: direction.accent, variant: direction.variant, palette: direction.palette }
  const validated = validateWidget('chart', JSON.stringify(styled))
  if (!validated.ok) return { text: strip(reply, invalid), widgets: 0, path: 'none', errors: [...errors, ...validationErrors(validated)] }
  const body = strip(reply, invalid).trimEnd()
  return {
    text: `${body}${body ? '\n\n' : ''}${fenceBlock('chart', validated.json)}`,
    widgets: 1,
    path: 'fallback',
    errors,
  }
}

/** Convenience wrapper for the turn executor: always returns a string. */
export function ensureWidgets(
  text: string,
  opts: { userPrompt: string; priorText?: string }
): CompileResult {
  const sanitized = sanitizeReplyEmojis(text)
  return compileWidgets({ reply: sanitized, userPrompt: opts.userPrompt, priorText: opts.priorText })
}

// ---------------------------------------------------------------------------
// Text surgery
// ---------------------------------------------------------------------------

function fenceBlock(kind: WidgetKind, json: string): string {
  return '```' + kind + '\n' + json + '\n```'
}

function rewrite(
  text: string,
  replacements: Array<{ start: number; end: number; replacement: string }>,
  removals: Array<{ start: number; end: number; replacement?: string }>
): string {
  const edits = [...replacements, ...removals.map((r) => ({ ...r, replacement: r.replacement ?? '' }))]
    .sort((a, b) => b.start - a.start)
  let out = text
  for (const edit of edits) {
    out = out.slice(0, edit.start) + edit.replacement + out.slice(edit.end)
  }
  return out
}

function widgetSourceFallback(source: string, message: string): string {
  const runs: string[] = source.match(/`+/g) ?? []
  const longest = runs.reduce<number>((max, run) => Math.max(max, run.length), 0)
  const fence = '`'.repeat(Math.max(3, longest + 1))
  // A text fence cannot be re-promoted into the same invalid widget by clients.
  return `> ${message}\n\n${fence}text\n${source}\n${fence}`
}

function strip(text: string, removals: Array<{ start: number; end: number; replacement?: string }>): string {
  return rewrite(text, [], removals)
}

// ---------------------------------------------------------------------------
// Deterministic extraction (mirrors the Android WidgetRecovery)
// ---------------------------------------------------------------------------

const BULLET = /^\s*(?:[-*+•·]|\d{1,2}[.)])\s+/
const HEADING = /^#{1,6}\s+(.+)$/
const NUMBERED = /^\d{1,2}[.)]\s+(.+)$/
const SEPARATOR = /\s*[:=]\s*|\s+[—–]\s+/
// Whole values only: prose, ranges, mixed dimensions and code are not observations.
const NUMBER_AT_START = /^\s*(?:([A-Z]{3}|[$€£¥₹৳])\s*)?(-?(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d+)?(?:e[+-]?\d+)?)\s*(trillion|billion|million|thousand|tn|bn|mn|[kmbt](?![a-z]))?\s*(%|percent|[a-z°][a-z0-9/°^]{0,15})?\s*$/i

function clean(s: string): string {
  return s
    .replace(/[*_`]{1,3}/g, '')
    .trim()
    .replace(/^["'“”]+|["'“”]+$/g, '')
    .replace(/[.:;,]+$/, '')
    .trim()
}

function parseNumber(text: string): { value: number; display: string; percent: boolean; unit: string } | null {
  const m = NUMBER_AT_START.exec(text.trim())
  if (!m) return null
  const scale = (m[3] ?? '').toLowerCase()
  const factor = ['trillion', 'tn', 't'].includes(scale) ? 1e12
    : ['billion', 'bn', 'b'].includes(scale) ? 1e9 : ['million', 'mn', 'm'].includes(scale) ? 1e6
    : ['thousand', 'k'].includes(scale) ? 1e3 : 1
  const value = Number(m[2].replace(/,/g, '')) * factor
  if (!Number.isFinite(value)) return null
  const suffix = m[4] ?? ''
  const currency: Record<string, string> = { USD: '$', EUR: '€', GBP: '£', JPY: '¥', INR: '₹', BDT: '৳' }
  const prefix = m[1] ?? ''
  const unit = (currency[prefix] ?? prefix) || (suffix.toLowerCase() === 'percent' ? '%' : currency[suffix] ?? suffix)
  if (prefix && suffix && (currency[suffix] ?? suffix) !== (currency[prefix] ?? prefix)) return null
  return { value, display: text.trim(), percent: unit === '%', unit }
}

function extractPoints(text: string): Point[] {
  if (!text.trim()) return []
  const rows: Array<{ section: string; label: string; point: Point }> = []
  let section = ''
  let fence: string | null = null

  for (const rawLine of text.split(/\r?\n/)) {
    const line = rawLine.trim()
    const marker = /^(`{3,}|~{3,})/.exec(line)?.[1]
    if (marker) {
      if (!fence) fence = marker
      else if (marker[0] === fence[0] && marker.length >= fence.length) fence = null
      continue
    }
    if (!line || fence) continue

    const h = HEADING.exec(line)
    if (h) {
      section = clean(h[1])
      continue
    }
    const n = NUMBERED.exec(line)
    if (n) {
      const title = clean(n[1])
      if (title.length >= 2 && !/\d/.test(title) && !SEPARATOR.test(title)) {
        section = title
        continue
      }
    }

    const body = clean(line.replace(BULLET, ''))
    const sep = SEPARATOR.exec(body)
    if (!sep || sep.index === undefined) continue
    const label = body.slice(0, sep.index).trim()
    if (label.length < 2 || label.length > 60 || !/[\p{L}\p{N}]/u.test(label)) continue
    if (label.includes('http')) continue
    const rest = body.slice(sep.index + sep[0].length).trim()
    const num = parseNumber(rest)
    if (!num) continue
    rows.push({ section, label, point: { label, ...num } })
  }

  if (!rows.length) return []

  const counts = new Map<string, number>()
  for (const r of rows) counts.set(r.label.toLowerCase(), (counts.get(r.label.toLowerCase()) ?? 0) + 1)
  const dominant = [...counts.entries()].sort((a, b) => b[1] - a[1])[0]
  const useSection =
    dominant !== undefined &&
    dominant[1] >= 2 &&
    dominant[1] * 2 >= rows.length &&
    rows.filter((r) => r.label.toLowerCase() === dominant[0] && r.section).length >= 2

  const points = useSection
    ? rows
        .filter((r) => r.label.toLowerCase() === dominant[0] && r.section)
        .map((r) => ({ ...r.point, label: r.section }))
    : rows.map((r) => r.point)

  const seen = new Set<string>()
  const unique = points.filter((p) => {
    const key = p.label.toLowerCase()
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
  return unique.length === points.length && unique.length <= LIMITS.maxPointsPerSeries ? unique : []
}

function buildChart(points: Point[], type: (typeof CHART_TYPES)[number]) {
  const labels = points.map((p) => p.label)
  const series = [{ name: 'Value', data: points.map((p, i) => [i, p.value]) }]
  return {
    type,
    title: 'From the numbers in this answer',
    unit: points[0]?.unit ?? '',
    labels,
    series,
  }
}
