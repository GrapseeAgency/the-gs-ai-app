/** Calendar labels must be explicit ISO dates. A category or a month name
 * without a year is not a timestamp: those charts get point-count controls. */
export function chartRange(labels: string[], count: number, range: number, now = new Date()) {
  const dates = labels.map((label) => {
    if (!/^\d{4}-\d{2}(?:-\d{2})?$/.test(label)) return null
    const time = Date.parse(label.length === 7 ? label + '-01T00:00:00Z' : label + 'T00:00:00Z')
    return Number.isFinite(time) && new Date(time).toISOString().slice(0, label.length) === label ? time : null
  })
  const calendar = labels.length === count && dates.length > 0 && dates.every((d) => d !== null)
  const options = calendar ? ['Last 3M', 'Last 6M', 'YTD', 'All'] : ['Last 3 points', 'Last 6 points', 'First half', 'All']
  let indices = Array.from({ length: count }, (_, i) => i)
  if (range !== 3) {
    if (calendar) {
      const year = now.getUTCFullYear(), month = now.getUTCMonth()
      const start = range === 2 ? Date.UTC(year, 0, 1) : Date.UTC(year, month - (range === 0 ? 2 : 5), 1)
      indices = indices.filter((i) => dates[i] >= start && dates[i] <= now.getTime())
    } else indices = range === 2 ? indices.slice(0, Math.ceil(count / 2)) : indices.slice(-(range === 0 ? 3 : 6))
  }
  return { indices, options }
}
