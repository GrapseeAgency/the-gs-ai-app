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
