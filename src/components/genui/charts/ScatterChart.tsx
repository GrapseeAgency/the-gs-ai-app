'use client'

import { CartesianGrid, ResponsiveContainer, Scatter, ScatterChart as RcScatter, Tooltip, XAxis, YAxis, ZAxis } from 'recharts'
import type { ChartPayload } from '../types'
import { ChartFrame } from './ChartFrame'
import { toPoints } from './rechartsData'
import { alpha, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

/** Scatter — correlation between two variables. */
export function ScatterChartView({ payload, height = 210 }: { payload: ChartPayload; height?: number }) {
  const { accent, theme, ramp } = useWidgetIdentity()
  const series = payload.series ?? []
  const xs = series.flatMap((s) => s.data.map((d) => d[0]))
  const ys = series.flatMap((s) => s.data.map((d) => d[1]))
  const points = toPoints(series)
  return (
    <ChartFrame title={payload.title} unit={payload.unit} seriesNames={series.length > 1 ? series.map((s) => s.name) : undefined} height={height}>
      {({ width }) => (
        <ResponsiveContainer width={width} height={height}>
          <RcScatter margin={{ top: 8, right: 12, bottom: 4, left: -18 }}>
            <CartesianGrid stroke={theme.border} strokeDasharray="3 6" />
            <XAxis type="number" dataKey="x" domain={['auto', 'auto']} tick={{ fontSize: TYPE.micro, fill: theme.text3 }} axisLine={{ stroke: theme.border }} tickLine={false} />
            <YAxis type="number" dataKey="y" domain={['auto', 'auto']} tick={{ fontSize: TYPE.micro, fill: theme.text3 }} axisLine={false} tickLine={false} />
            <ZAxis type="number" dataKey="z" range={[30, 40]} />
            <Tooltip cursor={{ strokeDasharray: '3 3' }} contentStyle={{ background: theme.card, border: `1px solid ${theme.border}`, borderRadius: 12, fontSize: TYPE.label, color: theme.text }} />
            {series.map((s, i) => (
              <Scatter key={s.name} name={s.name} data={points.filter((p) => p.si === i)} fill={series.length > 1 ? ramp(series.length)[i] : accent} fillOpacity={0.75} stroke={alpha(accent, 0.4)} />
            ))}
          </RcScatter>
        </ResponsiveContainer>
      )}
    </ChartFrame>
  )
}
