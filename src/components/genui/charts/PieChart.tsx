'use client'

import { Cell, Pie, PieChart as RcPie, ResponsiveContainer, Tooltip } from 'recharts'
import type { ChartPayload } from '../types'
import { ChartFrame } from './ChartFrame'
import { inkOn, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

/** Shared pie data: one series per slice, a single point each. */
export function pieData(payload: ChartPayload) {
  const labels = payload.labels ?? []
  return (payload.series ?? []).map((s, i) => ({
    name: s.name || labels[i] || `#${i + 1}`,
    value: s.data[0]?.[s.data[0].length - 1] ?? 0,
  }))
}

function PieDonutBase({ payload, donut, height = 210 }: { payload: ChartPayload; donut: boolean; height?: number }) {
  const { ramp, theme } = useWidgetIdentity()
  const colors = ramp(8)
  const data = pieData(payload).filter((d) => d.value > 0)
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
