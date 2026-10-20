'use client'

import { Area, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis, AreaChart as RcArea } from 'recharts'
import type { ChartPayload } from '../types'
import { ChartFrame } from './ChartFrame'
import { toRows } from './rechartsData'
import { TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

/** Stacked area — composition over time. */
export function StackedAreaChartView({ payload, height = 210 }: { payload: ChartPayload; height?: number }) {
  const { theme, ramp } = useWidgetIdentity()
  const series = payload.series ?? []
  const rows = toRows(series, payload.labels, Math.ceil((payload.labels?.length ?? 0) / 6))
  return (
    <ChartFrame title={payload.title} unit={payload.unit} seriesNames={series.map((s) => s.name)} height={height}>
      {({ width }) => (
        <ResponsiveContainer width={width} height={height}>
          <RcArea data={rows} margin={{ top: 8, right: 8, bottom: 0, left: -18 }}>
            <CartesianGrid stroke={theme.border} strokeDasharray="3 6" vertical={false} />
            <XAxis dataKey="label" tick={{ fontSize: TYPE.micro, fill: theme.text3 }} axisLine={{ stroke: theme.border }} tickLine={false} />
            <YAxis tick={{ fontSize: TYPE.micro, fill: theme.text3 }} axisLine={false} tickLine={false} />
            <Tooltip contentStyle={{ background: theme.card, border: `1px solid ${theme.border}`, borderRadius: 12, fontSize: TYPE.label, color: theme.text }} />
            {series.map((s, i) => (
              <Area key={s.name} type="monotone" dataKey={s.name} stackId="gs" stroke={ramp(series.length)[i]} strokeWidth={1.5} fill={ramp(series.length)[i]} fillOpacity={theme.dark ? 0.55 : 0.75} />
            ))}
          </RcArea>
        </ResponsiveContainer>
      )}
    </ChartFrame>
  )
}
