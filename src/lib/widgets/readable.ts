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

export function widgetReadableText(raw: string): string {
  let source: unknown
  try { source = JSON.parse(raw) } catch { return literal(raw) }
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
