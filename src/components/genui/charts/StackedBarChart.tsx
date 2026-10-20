'use client'

import { Bar, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis, BarChart as RcBar } from 'recharts'
import type { ChartPayload } from '../types'
import { ChartFrame } from './ChartFrame'
import { toRows } from './rechartsData'
import { alpha, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

/** Stacked bar — parts of a whole per category. */
export function StackedBarChartView({ payload, height = 210 }: { payload: ChartPayload; height?: number }) {
  const { theme, ramp } = useWidgetIdentity()
  const series = payload.series ?? []
  const rows = toRows(series, payload.labels)
  return (
    <ChartFrame title={payload.title} unit={payload.unit} seriesNames={series.map((s) => s.name)} height={height}>
      {({ width }) => (
        <ResponsiveContainer width={width} height={height}>
          <RcBar data={rows} margin={{ top: 8, right: 8, bottom: 0, left: -18 }}>
            <CartesianGrid stroke={theme.border} strokeDasharray="3 6" vertical={false} />
            <XAxis dataKey="label" tick={{ fontSize: TYPE.micro, fill: theme.text3 }} axisLine={{ stroke: theme.border }} tickLine={false} />
            <YAxis tick={{ fontSize: TYPE.micro, fill: theme.text3 }} axisLine={false} tickLine={false} />
            <Tooltip cursor={{ fill: alpha(theme.dark ? '#FFFFFF' : '#14171C', 0.06) }} contentStyle={{ background: theme.card, border: `1px solid ${theme.border}`, borderRadius: 12, fontSize: TYPE.label, color: theme.text }} />
            {series.map((s, i) => (
              <Bar key={s.name} dataKey={s.name} stackId="gs" fill={ramp(series.length)[i]} radius={i === series.length - 1 ? [6, 6, 0, 0] : [0, 0, 0, 0]} maxBarSize={44} />
            ))}
          </RcBar>
        </ResponsiveContainer>
      )}
    </ChartFrame>
  )
}
