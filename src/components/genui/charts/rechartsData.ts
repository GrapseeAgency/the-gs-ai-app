'use client'

/** Shared series -> recharts row transforms. */
export interface SeriesInput {
  name: string
  data: number[][]
}

export function toRows(series: SeriesInput[], labels: string[] | undefined, labelEvery = 1) {
  const n = Math.max(0, ...series.map((s) => s.data.length))
  const rows: Array<Record<string, number | string>> = []
  for (let i = 0; i < n; i++) {
    const row: Record<string, number | string> = {}
    const label = labels?.[i]
    if (label !== undefined) {
      row.label = i % labelEvery === 0 || i === n - 1 ? label : ''
      row.fullLabel = label
    } else {
      row.label = String(i + 1)
      row.fullLabel = row.label
    }
    for (const s of series) {
      const pt = s.data[i]
      if (pt) row[s.name] = pt[pt.length - 1]
    }
    rows.push(row)
  }
  return rows
}

export function toPoints(series: SeriesInput[]) {
  return series.flatMap((s, si) => s.data.map((d) => ({ x: d[0], y: d[1], z: d[2] ?? 1, series: s.name, si })))
}
