/**
 * The stream parser — markdown text with widget fences -> renderable segments.
 *
 * This is the client twin of the backend's extractWidgetFences(): the same
 * fence grammar (```kind\n{...}\n``` or ~~~kind), the same bare-payload and
 * ```json / ```widget promotion, the same synonym normalisation, and the same
 * streaming rule — an unclosed fence is a SKELETON, never raw JSON.
 * Runs on the client so a streamed answer renders block-by-block as it
 * arrives, and on completed answers verbatim.
 */

import { injectWidgetType, widgetKindOf } from '@/lib/widgets/catalog'
import { extractBareWidget, inferWidgetKind, repairJson, tabularTextToJson, validateWidget } from '@/lib/widgets/validate'
import { isNexlyPlanPayload } from '@/lib/nexly/plan'

export type Segment =
  | { kind: 'text'; text: string }
  | { kind: 'widget'; widget: string; payload: Record<string, unknown>; complete: boolean }
  | { kind: 'code'; code: string; language?: string }
  | { kind: 'mermaid'; source: string }
  | { kind: 'html'; source: string }
  | { kind: 'svg'; source: string }
  | { kind: 'map'; payload: Record<string, unknown> }

const GENERIC_WIDGET_FENCES = new Set(['json', 'jsonc', 'widget', 'genui', 'data', 'ui', 'gsui', 'card_ui', 'block'])
const PLAN_FENCES = new Set(['plan', 'schedule', 'timetable', 'itinerary', 'routine', 'day'])
const HTML_FENCES = new Set(['html', 'htm', 'web', 'webapp', 'page', 'interactive', 'miniapp'])
const MERMAID_FENCES = new Set([
  'mermaid', 'flowchart', 'sequence', 'sequencediagram', 'classdiagram',
  'statediagram', 'erdiagram', 'gitgraph', 'c4', 'architecture', 'diagram',
  'mindmap', 'gantt',
])
const MAP_FENCES = new Set(['map', 'maps', 'location', 'locations', 'places', 'route', 'directions', 'geo', 'geojson'])

function isMapPayload(obj: Record<string, unknown> | null): boolean {
  if (!obj) return false
  if (Array.isArray(obj.pins ?? obj.markers ?? obj.waypoints)) return true
  const lat = Number(obj.lat ?? obj.latitude)
  const lng = Number(obj.lng ?? obj.lon ?? obj.longitude)
  return Number.isFinite(lat) && Number.isFinite(lng) && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180
}

/** Parse a (possibly still-streaming) answer into segments. */
export function parseStream(text: string): Segment[] {
  const trimmedWhole = text.trim()
  if (
    (trimmedWhole.startsWith('{') && trimmedWhole.endsWith('}')) ||
    (trimmedWhole.startsWith('[') && trimmedWhole.endsWith(']'))
  ) {
    const bareKind = inferWidgetKind(trimmedWhole)
    if (bareKind) {
      const v = validateWidget(bareKind, trimmedWhole)
      if (v.ok) {
        return [{ kind: 'widget', widget: v.kind, payload: v.widget, complete: true }]
      }
    }
  }

  const segments: Segment[] = []
  const fence = /^[ \t]{0,8}(`{3,}|~{3,})([a-zA-Z][\w:./+-]*)?[ \t]*[^\r\n]*\r?\n/gm
  let cursor = 0

  const pushText = (chunk: string) => {
    const trimmed = chunk.trim()
    if (trimmed.length === 0) return
    const bare = extractBareWidget(chunk)
    if (bare) {
      const v = validateWidget(bare.kind, bare.json)
      if (v.ok) {
        const before = chunk.slice(0, bare.start).trim()
        const after = chunk.slice(bare.end).trim()
        if (before.length > 0) segments.push({ kind: 'text', text: before })
        segments.push({ kind: 'widget', widget: v.kind, payload: v.widget, complete: true })
        if (after.length > 0) pushText(after)
        return
      }
    }
    segments.push({ kind: 'text', text: trimmed })
  }

  while (cursor < text.length) {
    fence.lastIndex = cursor
    const open = fence.exec(text)
    if (!open) {
      pushText(text.slice(cursor))
      break
    }
    pushText(text.slice(cursor, open.index))
    const marker = open[1]
    const lang = open[2] ?? ''
    const bodyStart = open.index + open[0].length
    const closeRe = new RegExp(`\\r?\\n[ \\t]{0,8}${marker[0]}{${marker.length},}[ \\t]*(?=\\r?\\n|$)`, 'g')
    closeRe.lastIndex = bodyStart
    const closeMatch = closeRe.exec(text)
    const complete = closeMatch !== null
    const body = complete ? text.slice(bodyStart, closeMatch.index) : text.slice(bodyStart)
    cursor = complete ? closeMatch.index + closeMatch[0].length : text.length

    const lower = lang.toLowerCase()
    const trimmedBody = body.trim()
    let isJsonBody = trimmedBody.startsWith('{') || trimmedBody.startsWith('[')

    // Mermaid diagrams, including ```mindmap, ```gantt, ```flowchart, etc. when
    // the body is Mermaid syntax rather than a JSON widget payload.
    if (lower === 'mermaid' || (MERMAID_FENCES.has(lower) && !isJsonBody && complete)) {
      segments.push({ kind: 'mermaid', source: trimmedBody })
      continue
    }
    if (lower === 'svg') {
      segments.push(complete ? { kind: 'svg', source: body } : { kind: 'widget', widget: 'svg', payload: {}, complete: false })
      continue
    }
    if (HTML_FENCES.has(lower) || ((lower === 'app' || lower === 'ui' || lower === 'site') && !isJsonBody && trimmedBody.startsWith('<'))) {
      segments.push(complete ? { kind: 'html', source: body } : { kind: 'widget', widget: 'html', payload: {}, complete: false })
      continue
    }
    if (MAP_FENCES.has(lower) && isJsonBody) {
      const payload = safeJson(body) ?? {}
      segments.push({ kind: 'map', payload })
      continue
    }

    let kind = lower ? widgetKindOf(lower) : null
    let effectiveBody = trimmedBody
    if (kind === 'table' && !isJsonBody && complete) {
      const coerced = tabularTextToJson(trimmedBody)
      if (coerced) {
        effectiveBody = coerced
        isJsonBody = true
      }
    }
    if (PLAN_FENCES.has(lower) && isJsonBody) {
      const parsed = safeJson(effectiveBody)
      if (parsed && isNexlyPlanPayload(parsed)) kind = 'plan'
    }
    if (!kind && isJsonBody && complete) {
      kind = inferWidgetKind(effectiveBody)
      if (!kind) {
        const parsed = safeJson(effectiveBody)
        if (isMapPayload(parsed)) {
          segments.push({ kind: 'map', payload: parsed ?? {} })
          continue
        }
      }
    }

    if (kind && (isJsonBody || !complete)) {
      if (!complete) {
        segments.push({ kind: 'widget', widget: kind, payload: safeJson(body) ?? {}, complete: false })
        continue
      }
      const injected =
        kind === 'plan' || GENERIC_WIDGET_FENCES.has(lower) || !lower
          ? effectiveBody
          : lower !== kind
            ? injectWidgetType(effectiveBody, lang, kind)
            : effectiveBody
      let v = validateWidget(kind, injected)
      if (!v.ok) {
        const healedKind = inferWidgetKind(injected)
        if (healedKind && healedKind !== kind) {
          const healed = validateWidget(healedKind, injected)
          if (healed.ok) v = healed
        }
      }
      if (v.ok) {
        segments.push({ kind: 'widget', widget: v.kind, payload: v.widget, complete: true })
        continue
      }
      // Parsing JSON is not validation. Never turn a rejected payload into a
      // "complete" live widget by falling back to the same unchecked object.
      segments.push({ kind: 'text', text: 'Interactive content could not be rendered. Original source is preserved below.' })
      segments.push({ kind: 'code', code: body, language: 'json' })
      continue
    }

    segments.push({ kind: 'code', code: body, language: lower || undefined })
  }
  return segments
}

function safeJson(raw: string): Record<string, unknown> | null {
  const repaired = repairJson(raw)
  if (!repaired) return null
  try {
    const parsed = JSON.parse(repaired)
    return typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed)
      ? (parsed as Record<string, unknown>)
      : null
  } catch {
    return null
  }
}
