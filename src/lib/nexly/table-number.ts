/** Whole-cell numeric grammar. Never erase embedded operators or the sign
 * carried by a downward trend arrow while sorting real source data. */
export function parseTableNumber(raw: unknown): number | null {
  if (typeof raw === 'number') return Number.isFinite(raw) ? raw : null
  if (typeof raw !== 'string') return null
  const match = raw.trim().replace(/−/g, '-').match(/^(?:([↑↓↗↘])\s*)?([+-]?)\s*[$€£¥₹৳#]?\s*([+-]?)\s*((?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d+)?)\s*([kKmMbBtT]?)\s*[%xX]?$/)
  if (!match || (match[2] && match[3])) return null
  const [, arrow, outerSign, innerSign, digits, suffix] = match
  const sign = outerSign || innerSign
  const down = arrow === '↓' || arrow === '↘'
  if (arrow && ((down && sign === '+') || (!down && sign === '-'))) return null
  const multiplier = ({ k: 1e3, m: 1e6, b: 1e9, t: 1e12 } as Record<string, number>)[suffix.toLowerCase()] ?? 1
  const value = Number(digits.replace(/,/g, '')) * multiplier * (sign === '-' || down ? -1 : 1)
  return Number.isFinite(value) ? value : null
}

/** Flatten actual surrounding Markdown, never erase operators inside a cell. */
export function plainTableCell(text: string): string {
  let value = text.trim()
  let previous = ''
  while (previous !== value) {
    previous = value
    value = value.replace(/^(\*\*|__|~~|\*|_|`)([\s\S]*)\1$/, '$2').trim()
  }
  return value.replace(/:([a-z0-9][a-z0-9-]{1,42}):/g, '').replace(/\[([^\]]+)\]\([^)]+\)/g, '$1').trim()
}

/** A chart is offered only for a complete, compatible source column. */
export function chartFromTableCells(headers: string[], rows: string[][]): Record<string, unknown> | null {
  if (headers.length < 2 || rows.length < 2) return null
  const plain = plainTableCell
  const ranked = headers.length >= 3 && ['#', 'rank', 'no', 'id'].includes(plain(headers[0]).toLowerCase())
  const labelColumn = ranked ? 1 : 0
  const labels = rows.map(row => plain(row[labelColumn] ?? ''))
  if (labels.some(label => !label)) return null
  for (let column = 0; column < headers.length; column++) {
    if (column === labelColumn || (ranked && column === 0)) continue
    const source = rows.map(row => plain(row[column] ?? ''))
    const values = source.map(parseTableNumber)
    if (values.some(value => value === null)) continue
    const units = new Set(source.map(raw => raw.endsWith('%') ? '%' : raw.match(/[$€£¥₹৳]/)?.[0] ?? (/x$/i.test(raw) ? 'x' : '')))
    if (units.size !== 1) continue
    const title = plain(headers[column])
    return { type: 'bar', title, unit: [...units][0], labels, series: [{ name: title, data: values.map((value, index) => [index, value]) }] }
  }
  return null
}
