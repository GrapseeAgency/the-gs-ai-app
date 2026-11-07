'use client'

import { Cell, Pie, PieChart as RcPie, ResponsiveContainer, Tooltip } from 'recharts'
import type { ChartPayload } from '../types'
import { ChartFrame } from './ChartFrame'
import { inkOn, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

/** Preserve every supplied observation when switching a time series to pie.
 * Single-observation series retain their category names; longer series label
 * each actual point (and include the series name when several are present). */
export function pieData(payload: ChartPayload) {
  const series = payload.series ?? []
  return series.flatMap((s, seriesIndex) => s.data.map((point, pointIndex) => ({
    name: s.data.length === 1
      ? s.name || payload.labels?.[seriesIndex] || `#${seriesIndex + 1}`
      : `${series.length > 1 ? `${s.name}: ` : ''}${payload.labels?.[pointIndex] ?? String(point[0] ?? `#${pointIndex + 1}`)}`,
    value: point[point.length - 1] ?? Number.NaN,
  })))
}

function PieDonutBase({ payload, donut, height = 210 }: { payload: ChartPayload; donut: boolean; height?: number }) {
  const { ramp, theme } = useWidgetIdentity()
  const colors = ramp(8)
  const data = pieData(payload)
  if (data.some(d => !Number.isFinite(d.value) || d.value < 0) || (data.length > 0 && data.reduce((sum, d) => sum + d.value, 0) <= 0)) {
    return <div role="status">Pie charts require finite nonnegative observations and a positive total. Use a line or bar chart for signed data.</div>
  }
  return (
    <ChartFrame title={payload.title} unit={payload.unit} seriesNames={data.map((d) => d.name)} height={height}>
      {({ width }) => (
        <ResponsiveContainer width={width} height={height}>
          <RcPie>
            <Pie
              data={data}
              dataKey="value"
              nameKey="name"
              innerRadius={donut ? '58%' : 0}
              outerRadius="86%"
              paddingAngle={donut ? 2 : 1}
              stroke="none"
              label={donut ? undefined : { fontSize: TYPE.micro, fill: theme.text2, fontWeight: 600 }}
            >
              {data.map((_, i) => (
                <Cell key={i} fill={colors[i % colors.length]} />
              ))}
            </Pie>
            <Tooltip contentStyle={{ background: theme.card, border: `1px solid ${theme.border}`, borderRadius: 12, fontSize: TYPE.label, color: theme.text }} />
          </RcPie>
        </ResponsiveContainer>
      )}
    </ChartFrame>
  )
}

export function PieChartView({ payload, height }: { payload: ChartPayload; height?: number }) {
  return <PieDonutBase payload={payload} donut={false} height={height} />
}

export function DonutChartView({ payload, height }: { payload: ChartPayload; height?: number }) {
  return <PieDonutBase payload={payload} donut height={height} />
}
