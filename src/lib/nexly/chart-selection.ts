import { CHART_TYPES, type ChartType } from '../widgets/catalog'

/** Preferences constrain presentation, never supply observations or pick a random skin. */
export function chartPreference(prompt: string): { requested: boolean; suppressed: boolean; type: ChartType | null } {
  const text = prompt.toLowerCase()
  const suppressed = /\b(?:no|without|avoid|skip)\s+(?:any\s+)?(?:charts?|graphs?|plots?|visuali[sz]ations?)\b|\b(?:don['’]t|do not|never)\s+(?:add|use|include|draw|render|show|make)?\s*(?:a\s+)?(?:charts?|graphs?|plots?)\b|\b(?:plain text|text only|prose only|only prose|just text|only (?:a )?table)\b/.test(text)
  const requested = !suppressed && /\b(?:charts?|graphs?|plots?|visuali[sz]e|visuali[sz]ation|sankey|histogram|scatterplot|boxplot|heatmap|treemap|wordcloud)\b/.test(text)
  if (!requested) return { requested, suppressed, type: null }
  const aliases: Array<[RegExp, ChartType]> = [
    [/\bstacked[ -]area\b/, 'stackedArea'], [/\bhorizontal[ -]bar\b/, 'hbar'],
    [/\bstacked(?:[ -]bar)?\b/, 'stacked'], [/\b(?:grouped|clustered)(?:[ -]bar)?\b/, 'grouped'],
    [/\bdoughnut\b/, 'donut'], [/\bcolumn(?: chart| graph)?\b/, 'bar'],
    [/\bscatter(?:plot| plot)?\b/, 'scatter'], [/\bbox[ -]plot\b/, 'boxplot'],
  ]
  const alias = aliases.find(([pattern]) => pattern.test(text))
  if (alias) return { requested, suppressed, type: alias[1] }
  const type = CHART_TYPES.find(kind => new RegExp(`\\b${kind.toLowerCase()}\\b`).test(text)) ?? null
  return { requested, suppressed, type }
}

/** Only genuine increasing time coordinates imply a trend. Keep source order. */
export function chronologicalLabels(labels: string[]): boolean {
  if (labels.length < 2) return false
  const months = ['jan', 'feb', 'mar', 'apr', 'may', 'jun', 'jul', 'aug', 'sep', 'oct', 'nov', 'dec']
  let family = ''
  const coordinates: number[] = []
  for (const source of labels) {
    const label = source.trim().toLowerCase()
    let kind = '', coordinate = NaN
    if (/^(?:19|20|21)\d{2}$/.test(label)) { kind = 'year'; coordinate = Number(label) }
    else if (/^\d{4}-\d{2}(?:-\d{2})?$/.test(label)) {
      const date = new Date(label.length === 7 ? `${label}-01T00:00:00Z` : `${label}T00:00:00Z`)
      if (!Number.isNaN(date.valueOf()) && date.toISOString().startsWith(label)) { kind = 'date'; coordinate = date.valueOf() }
    } else {
      const quarter = /^q([1-4])(?:\s+((?:19|20|21)\d{2}))?$/.exec(label)
      const month = /^([a-z]+)(?:\s+((?:19|20|21)\d{2}))?$/.exec(label)
      if (quarter) { kind = quarter[2] ? 'dated-quarter' : 'quarter'; coordinate = Number(quarter[2] ?? 0) * 4 + Number(quarter[1]) }
      else if (month && months.includes(month[1].slice(0, 3)) && /^(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)$/.test(month[1])) {
        kind = month[2] ? 'dated-month' : 'month'; coordinate = Number(month[2] ?? 0) * 12 + months.indexOf(month[1].slice(0, 3))
      }
    }
    if (!kind || (family && kind !== family) || !Number.isFinite(coordinate)) return false
    family = kind; coordinates.push(coordinate)
  }
  return coordinates.every((value, index) => index === 0 || value > coordinates[index - 1])
}

export function categoricalChartType(labels: string[]): 'line' | 'bar' | 'hbar' {
  if (chronologicalLabels(labels)) return 'line'
  return labels.length > 7 || labels.some(label => label.length > 18) ? 'hbar' : 'bar'
}

/** Category/value pairs cannot honestly supply graph edges, OHLC or a second numeric axis. */
export function supportsCategoryRecovery(type: ChartType, values: number[]): boolean {
  if (!['line', 'area', 'bar', 'hbar', 'pie', 'donut', 'radar', 'funnel'].includes(type)) return false
  if (['pie', 'donut', 'funnel', 'radar'].includes(type)) return values.every(value => value >= 0) && values.some(value => value > 0)
  return true
}
