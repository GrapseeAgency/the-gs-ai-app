/** Lossless data presentation when the reader declines an interactive surface.
 * No model call, inferred totals, calculated values, or content-length cuts.
 * The raw payload, not the validator's potentially clamped projection, is used.
 */
const appearanceKeys = new Set(['accent', 'variant', 'palette', 'icon'])

function literal(value: unknown): string {
  const text = value === null ? 'null' : String(value)
  // Data must not become a second widget fence, executable HTML, task control,
  // citation or Markdown heading when it is put back into the answer.
  return text.replace(/[\\`*_{}\[\]<>#~|]/g, '\\$&')
}

/** Common data widgets read as an answer, not an internal schema dump.
 * Unknown shapes retain the general lossless field projection below.
 */
function readableData(source: unknown): string | null {
  if (!source || typeof source !== 'object' || Array.isArray(source)) return null
  const object = source as Record<string, unknown>
  const title = typeof object.title === 'string' ? literal(object.title) : ''
  const allowed = (keys: string[]) => Object.keys(object).every(key => appearanceKeys.has(key) || keys.includes(key))
  if (Array.isArray(object.columns) && object.columns.every(c => typeof c === 'string') && object.columns.length &&
      Array.isArray(object.rows) && object.rows.every(row => Array.isArray(row) && row.length === (object.columns as unknown[]).length && row.every(v => v === null || typeof v !== 'object')) &&
      allowed(['title','columns','rows','sortable','searchable','pageSize'])) {
    const columns = object.columns as string[]
    return [title, ...object.rows.map(row => '- ' + (row as unknown[]).map((value, i) =>
      `${literal(columns[i])}: ${typeof value === 'string' && !value ? '""' : literal(value)}`
    ).join('; '))].filter(Boolean).join('\n')
  }
  if (!['bar','hbar','line','area','pie','donut','grouped','stacked','stackedArea'].includes(String(object.type)) ||
      !Array.isArray(object.series) || !object.series.length ||
      !allowed(['title','type','unit','labels','series'])) return null
  if (object.labels !== undefined && (!Array.isArray(object.labels) || !object.labels.every(v => typeof v === 'string'))) return null
  if (object.unit !== undefined && typeof object.unit !== 'string') return null
  const labels = (object.labels ?? []) as string[]
  const lines = [title, object.unit ? `Unit: ${literal(object.unit)}` : ''].filter(Boolean)
  const used = new Set<number>()
  for (const series of object.series) {
    if (!series || typeof series !== 'object' || Array.isArray(series) ||
        Object.keys(series).some(key => !['name','data'].includes(key)) ||
        (series.name !== undefined && typeof series.name !== 'string') || !Array.isArray(series.data)) return null
    if (series.name) lines.push(`${literal(series.name)}:`)
    for (const point of series.data) {
      if (!Array.isArray(point) || point.length !== 2 || !point.every(v => typeof v === 'number' && Number.isFinite(v))) return null
      const [x, y] = point
      const labelled = Number.isInteger(x) && x >= 0 && x < labels.length
      if (labelled) used.add(x)
      lines.push(`- ${labelled ? literal(labels[x]) : `x = ${x}`}: ${y}`)
    }
  }
  // Do not silently discard labels that have no supplied observation.
  if (labels.length && used.size !== labels.length) return null
  return lines.join('\n')
}

export function widgetReadableText(raw: string): string {
  let source: unknown
  try { source = JSON.parse(raw) } catch { return literal(raw) }
  const data = readableData(source)
  if (data !== null) return data
  const lines: string[] = []
  type Entry = { key: string; value: unknown; depth: number }
  const stack: Entry[] = [{ key: '', value: source, depth: 0 }]
  // Iterative traversal also handles deeply nested valid source without a
  // recursive call-stack limit. Only indentation is bounded, not the data.
  while (stack.length) {
    const { key, value, depth } = stack.pop()!
    const prefix = '  '.repeat(Math.min(depth, 12))
    const label = key ? `${literal(key)}: ` : ''
    if (Array.isArray(value) && value.every(v => v === null || typeof v !== 'object')) {
      const cells = value.map(v => literal(typeof v === 'string' ? JSON.stringify(v) : v))
      lines.push(`${prefix}- ${label}${value.length ? cells.join(', ') : '(empty list)'}`)
    } else if (value !== null && typeof value === 'object') {
      if (key) lines.push(`${prefix}- ${literal(key)}:`)
      const entries = Array.isArray(value)
        ? value.map((v, i) => [String(i + 1), v] as const)
        : Object.entries(value).filter(([k]) => depth !== 0 || !appearanceKeys.has(k))
      if (!entries.length) lines.push(`${prefix}  (empty)`)
      for (const [k, v] of entries.reverse()) stack.push({ key: k, value: v, depth: key ? depth + 1 : depth })
    } else {
      const text = literal(value).replace(/\r?\n/g, `\n${prefix}  `)
      lines.push(`${prefix}- ${label}${text}`)
    }
  }
  return lines.join('\n')
}

export type PresentationPreference = 'plain' | 'table' | 'no-charts' | 'automatic'

/** Only explicit presentation constraints, never a topic-to-widget decision. */
export function presentationPreference(prompt: string): PresentationPreference {
  // Quoted examples and executable source are content, not presentation
  // instructions (e.g. explain the phrase "no charts"). Apostrophes in
  // don't/doesn't are deliberately not treated as quotation marks.
  const text = prompt.replace(/```[\s\S]*?(?:```|$)|~~~[\s\S]*?(?:~~~|$)|`[^`\n]*`|"(?:\\.|[^"\\])*"|“[^”]*”|(?<!\w)'(?:\\.|[^'\\])*'(?!\w)/g, ' ')
  if (/\b(?:plain text only|text only|prose only|only prose|just text)\b|^\s*plain text[.!?]?\s*$|\b(?:in|as)\s+(?:only\s+|just\s+)?plain text\b|\buse\s+(?:only\s+|just\s+)?plain text\b/i.test(text)) return 'plain'
  if (/\b(?:only (?:a )?table|table only)\b/i.test(text)) return 'table'
  if (/\b(?:no|without|avoid|skip)\s+(?:any\s+)?(?:charts?|graphs?|plots?|visuali[sz]ations?)\b|\b(?:don['’]t|do not|never)\s+(?:add|use|include|draw|render|show|make|want)?\s*(?:(?:a|any|the)\s+)?(?:charts?|graphs?|plots?|visuali[sz]ations?)\b/i.test(text)) return 'no-charts'
  return 'automatic'
}

export function suppressWidget(kind: string, preference: PresentationPreference): boolean {
  return preference === 'plain' || (preference === 'table' && kind !== 'table') || (preference === 'no-charts' && kind === 'chart')
}
