'use client'

import { Bar, CartesianGrid, LabelList, ResponsiveContainer, Tooltip, XAxis, YAxis, BarChart as RcBar } from 'recharts'
import type { ChartPayload } from '../types'
import { ChartFrame } from './ChartFrame'
import { alpha, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

/** Horizontal bar — rankings; the right answer past ~7 categories. */
export function HBarChartView({ payload, height }: { payload: ChartPayload; height?: number }) {
  const { accent, theme } = useWidgetIdentity()
  const series = payload.series ?? []
  const labels = payload.labels ?? []
  const data = (series[0]?.data ?? []).map((d, i) => ({ label: labels[i] ?? `#${i + 1}`, value: d[d.length - 1] }))
  const h = height ?? Math.max(120, data.length * 30 + 40)
  return (
    <ChartFrame title={payload.title} unit={payload.unit} legend={false} height={h}>
      {({ width }) => (
        <ResponsiveContainer width={width} height={h}>
          <RcBar data={data} layout="vertical" margin={{ top: 4, right: 40, bottom: 0, left: 0 }}>
            <CartesianGrid stroke={theme.border} strokeDasharray="3 6" horizontal={false} />
            <XAxis type="number" tick={{ fontSize: TYPE.micro, fill: theme.text3 }} axisLine={false} tickLine={false} />
            <YAxis type="category" dataKey="label" width={110} tick={{ fontSize: TYPE.micro, fill: theme.text2 }} axisLine={false} tickLine={false} />
            <Tooltip cursor={{ fill: alpha(theme.dark ? '#FFFFFF' : '#14171C', 0.06) }} contentStyle={{ background: theme.card, border: `1px solid ${theme.border}`, borderRadius: 12, fontSize: TYPE.label, color: theme.text }} />
            <Bar dataKey="value" fill={accent} radius={[0, 6, 6, 0]} maxBarSize={18}>
              <LabelList dataKey="value" position="right" style={{ fontSize: TYPE.micro, fill: theme.text2, fontWeight: 600 }} />
            </Bar>
          </RcBar>
        </ResponsiveContainer>
      )}
    </ChartFrame>
  )
}
