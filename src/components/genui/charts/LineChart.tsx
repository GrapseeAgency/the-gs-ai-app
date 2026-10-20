'use client'

import { CartesianGrid, Line, LineChart as RcLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { ChartPayload } from '../types'
import { ChartFrame, niceTicks } from './ChartFrame'
import { toRows } from './rechartsData'
import { alpha, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

/** Line — trends over an ordered axis. Multi-series with the palette ramp. */
export function LineChartView({ payload, height = 200 }: { payload: ChartPayload; height?: number }) {
  const { accent, theme, ramp } = useWidgetIdentity()
  const series = payload.series ?? []
  const rows = toRows(series, payload.labels, Math.ceil((payload.labels?.length ?? 0) / 6))
  const ys = series.flatMap((s) => s.data.map((d) => d[d.length - 1]))
  const ticks = niceTicks(Math.min(...ys), Math.max(...ys))
  return (
    <ChartFrame title={payload.title} unit={payload.unit} seriesNames={series.map((s) => s.name)} height={height}>
      {({ width }) => (
        <ResponsiveContainer width={width} height={height}>
          <RcLine data={rows} margin={{ top: 8, right: 8, bottom: 0, left: -18 }}>
            <CartesianGrid stroke={theme.border} strokeDasharray="3 6" vertical={false} />
            <XAxis dataKey="label" tick={{ fontSize: TYPE.micro, fill: theme.text3 }} axisLine={{ stroke: theme.border }} tickLine={false} />
            <YAxis tick={{ fontSize: TYPE.micro, fill: theme.text3 }} axisLine={false} tickLine={false} domain={['auto', 'auto']} ticks={ticks.slice(0, 5)} />
            <Tooltip
              contentStyle={{ background: theme.card, border: `1px solid ${theme.border}`, borderRadius: 12, fontSize: TYPE.label, color: theme.text }}
              labelFormatter={(l, p) => String((p?.[0]?.payload as { fullLabel?: string })?.fullLabel ?? l)}
            />
            {series.map((s, i) => (
              <Line
                key={s.name}
                type="monotone"
                dataKey={s.name}
                stroke={ramp(series.length)[i]}
                strokeWidth={2}
                dot={series.length === 1 && s.data.length <= 12 ? { r: 3, fill: accent, strokeWidth: 0 } : false}
                activeDot={{ r: 4, fill: accent, stroke: alpha(accent, 0.3), strokeWidth: 4 }}
              />
            ))}
          </RcLine>
        </ResponsiveContainer>
      )}
    </ChartFrame>
  )
}
