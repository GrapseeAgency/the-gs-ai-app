'use client'

import { Area, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis, AreaChart as RcArea } from 'recharts'
import type { ChartPayload } from '../types'
import { ChartFrame, niceTicks } from './ChartFrame'
import { toRows } from './rechartsData'
import { alpha, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

/** Area — cumulative or volume trends; the editorial surface reads it best. */
export function AreaChartView({ payload, height = 200 }: { payload: ChartPayload; height?: number }) {
  const { theme, ramp } = useWidgetIdentity()
  const series = payload.series ?? []
  const rows = toRows(series, payload.labels, Math.ceil((payload.labels?.length ?? 0) / 6))
  const ys = series.flatMap((s) => s.data.map((d) => d[d.length - 1]))
  const ticks = niceTicks(Math.min(...ys), Math.max(...ys))
  return (
    <ChartFrame title={payload.title} unit={payload.unit} seriesNames={series.map((s) => s.name)} height={height}>
      {({ width }) => (
        <ResponsiveContainer width={width} height={height}>
          <RcArea data={rows} margin={{ top: 8, right: 8, bottom: 0, left: -18 }}>
            <defs>
              {series.map((s, i) => (
                <linearGradient key={s.name} id={`gs-area-${i}-${s.name.replace(/\W/g, '')}`} x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor={ramp(series.length)[i]} stopOpacity={theme.dark ? 0.45 : 0.35} />
                  <stop offset="100%" stopColor={ramp(series.length)[i]} stopOpacity={0.02} />
                </linearGradient>
              ))}
            </defs>
            <CartesianGrid stroke={theme.border} strokeDasharray="3 6" vertical={false} />
            <XAxis dataKey="label" tick={{ fontSize: TYPE.micro, fill: theme.text3 }} axisLine={{ stroke: theme.border }} tickLine={false} />
            <YAxis tick={{ fontSize: TYPE.micro, fill: theme.text3 }} axisLine={false} tickLine={false} ticks={ticks.slice(0, 5)} />
            <Tooltip contentStyle={{ background: theme.card, border: `1px solid ${theme.border}`, borderRadius: 12, fontSize: TYPE.label, color: theme.text }} />
            {series.map((s, i) => (
              <Area key={s.name} type="monotone" dataKey={s.name} stroke={ramp(series.length)[i]} strokeWidth={2} fill={`url(#gs-area-${i}-${s.name.replace(/\W/g, '')})`} />
            ))}
          </RcArea>
        </ResponsiveContainer>
      )}
    </ChartFrame>
  )
}
