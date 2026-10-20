'use client'

import { PolarAngleAxis, PolarGrid, PolarRadiusAxis, Radar, RadarChart as RcRadar, ResponsiveContainer, Tooltip } from 'recharts'
import type { ChartPayload } from '../types'
import { ChartFrame } from './ChartFrame'
import { alpha, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

/** Radar — a handful of metrics on shared axes (skill/profile comparison). */
export function RadarChartView({ payload, height = 230 }: { payload: ChartPayload; height?: number }) {
  const { theme, ramp } = useWidgetIdentity()
  const series = payload.series ?? []
  const labels = payload.labels ?? []
  const rows = labels.map((label, i) => {
    const row: Record<string, string | number> = { axis: label }
    for (const s of series) row[s.name] = s.data[i]?.[0] ?? 0
    return row
  })
  return (
    <ChartFrame title={payload.title} unit={payload.unit} seriesNames={series.map((s) => s.name)} height={height}>
      {({ width }) => (
        <ResponsiveContainer width={width} height={height}>
          <RcRadar data={rows} outerRadius="72%">
            <PolarGrid stroke={theme.border} />
            <PolarAngleAxis dataKey="axis" tick={{ fontSize: TYPE.micro, fill: theme.text2 }} />
            <PolarRadiusAxis tick={false} axisLine={false} />
            <Tooltip contentStyle={{ background: theme.card, border: `1px solid ${theme.border}`, borderRadius: 12, fontSize: TYPE.label, color: theme.text }} />
            {series.map((s, i) => (
              <Radar key={s.name} name={s.name} dataKey={s.name} stroke={ramp(series.length)[i]} fill={ramp(series.length)[i]} fillOpacity={0.22} strokeWidth={2} />
            ))}
          </RcRadar>
        </ResponsiveContainer>
      )}
    </ChartFrame>
  )
}
