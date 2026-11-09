'use client'

import { Bar, CartesianGrid, LabelList, ResponsiveContainer, Tooltip, XAxis, YAxis, BarChart as RcBar } from 'recharts'
import type { ChartPayload } from '../types'
import { ChartFrame } from './ChartFrame'
import { alpha, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

/** Horizontal bar — rankings; the right answer past ~7 categories. */
export function HBarChartView({ payload, height }: { payload: ChartPayload; height?: number }) {
  const { accent, theme, ramp } = useWidgetIdentity()
  const series = payload.series ?? []
  const labels = payload.labels ?? []
  const count = Math.max(0, ...series.map(item => item.data.length))
  const data = Array.from({ length: count }, (_, index) => ({
    label: labels[index] ?? `#${index + 1}`,
    ...Object.fromEntries(series.map((item, seriesIndex) => [`series_${seriesIndex}`, item.data[index]?.[1]])),
  }))
  const h = height ?? Math.max(120, data.length * 30 * Math.max(1, series.length) + 40)
  return (
    <ChartFrame title={payload.title} unit={payload.unit} legend={series.length > 1} seriesNames={series.map(item => item.name)} height={h}>
      {({ width }) => (
        <ResponsiveContainer width={width} height={h}>
          <RcBar data={data} layout="vertical" margin={{ top: 4, right: 40, bottom: 0, left: 0 }}>
            <CartesianGrid stroke={theme.border} strokeDasharray="3 6" horizontal={false} />
            <XAxis type="number" tick={{ fontSize: TYPE.micro, fill: theme.text3 }} axisLine={false} tickLine={false} />
            <YAxis type="category" dataKey="label" width={110} tick={{ fontSize: TYPE.micro, fill: theme.text2 }} axisLine={false} tickLine={false} />
            <Tooltip cursor={{ fill: alpha(theme.dark ? '#FFFFFF' : '#14171C', 0.06) }} contentStyle={{ background: theme.card, border: `1px solid ${theme.border}`, borderRadius: 12, fontSize: TYPE.label, color: theme.text }} />
            {series.map((item, index) => <Bar key={index} dataKey={`series_${index}`} name={item.name} fill={index === 0 ? accent : ramp(series.length)[index]} radius={[0, 6, 6, 0]} maxBarSize={18}>
              <LabelList dataKey={`series_${index}`} position="right" style={{ fontSize: TYPE.micro, fill: theme.text2, fontWeight: 600 }} />
            </Bar>)}
          </RcBar>
        </ResponsiveContainer>
      )}
    </ChartFrame>
  )
}
