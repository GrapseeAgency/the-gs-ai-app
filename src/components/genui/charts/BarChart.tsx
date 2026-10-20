'use client'

import { Bar, CartesianGrid, LabelList, ResponsiveContainer, Tooltip, XAxis, YAxis, BarChart as RcBar } from 'recharts'
import type { ChartPayload } from '../types'
import { ChartFrame } from './ChartFrame'
import { toRows } from './rechartsData'
import { TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

/** Bar — categorical comparison, the default answer to "chart this". */
export function BarChartView({ payload, height = 200 }: { payload: ChartPayload; height?: number }) {
  const { accent, theme, ramp } = useWidgetIdentity()
  const series = payload.series ?? []
  const single = series.length === 1
  const rows = toRows(series, payload.labels)
  return (
    <ChartFrame title={payload.title} unit={payload.unit} seriesNames={single ? undefined : series.map((s) => s.name)} height={height}>
      {({ width }) => (
        <ResponsiveContainer width={width} height={height}>
          <RcBar data={rows} margin={{ top: 14, right: 8, bottom: 0, left: -18 }}>
            <CartesianGrid stroke={theme.border} strokeDasharray="3 6" vertical={false} />
            <XAxis dataKey="label" tick={{ fontSize: TYPE.micro, fill: theme.text3 }} axisLine={{ stroke: theme.border }} tickLine={false} interval={0} />
            <YAxis tick={{ fontSize: TYPE.micro, fill: theme.text3 }} axisLine={false} tickLine={false} />
            <Tooltip cursor={{ fill: alphaSafe(theme.dark ? '#FFFFFF' : '#14171C', 0.06) }} contentStyle={{ background: theme.card, border: `1px solid ${theme.border}`, borderRadius: 12, fontSize: TYPE.label, color: theme.text }} />
            {series.map((s, i) => (
              <Bar key={s.name} dataKey={s.name} fill={single ? accent : ramp(series.length)[i]} radius={[6, 6, 0, 0]} maxBarSize={44}>
                {single && rows.length <= 7 && (
                  <LabelList dataKey={s.name} position="top" style={{ fontSize: TYPE.micro, fill: theme.text2, fontWeight: 600 }} />
                )}
              </Bar>
            ))}
          </RcBar>
        </ResponsiveContainer>
      )}
    </ChartFrame>
  )
}

function alphaSafe(hex: string, a: number) {
  const v = hex.replace('#', '')
  return `rgba(${parseInt(v.slice(0, 2), 16)},${parseInt(v.slice(2, 4), 16)},${parseInt(v.slice(4, 6), 16)},${a})`
}
