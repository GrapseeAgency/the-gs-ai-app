'use client'

import { CartesianGrid, ReferenceLine, ResponsiveContainer, Scatter, ScatterChart as RcScatter, Tooltip, XAxis, YAxis, ZAxis } from 'recharts'
import type { ChartPayload } from '../types'
import { ChartFrame } from './ChartFrame'
import { toPoints } from './rechartsData'
import { alpha, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

/**
 * Quadrant — a scatter cut into four named regions (priority matrices).
 * Optional payload.quadrants: [topLeft, topRight, bottomLeft, bottomRight].
 */
export function QuadrantChartView({ payload, height = 240 }: { payload: ChartPayload; height?: number }) {
  const { accent, theme } = useWidgetIdentity()
  const series = payload.series ?? []
  const points = toPoints(series)
  const xs = points.map((p) => p.x)
  const ys = points.map((p) => p.y)
  const xMid = payload.max ?? (Math.min(...xs) + Math.max(...xs)) / 2
  const yMid = (Math.min(...ys) + Math.max(...ys)) / 2
  const labels = (payload.items as Array<{ text?: string }> | undefined)?.map((i) => i.text ?? '') ?? (payload.labels ?? [])
  const quads = ((payload as { quadrants?: string[] }).quadrants ?? []).slice(0, 4)
  const xRange: [number, number] = [Math.min(...xs, xMid) - (Math.max(...xs) - Math.min(...xs)) * 0.1, Math.max(...xs, xMid) + (Math.max(...xs) - Math.min(...xs)) * 0.1]
  const yRange: [number, number] = [Math.min(...ys, yMid) - (Math.max(...ys) - Math.min(...ys)) * 0.1, Math.max(...ys, yMid) + (Math.max(...ys) - Math.min(...ys)) * 0.1]
  const corner = (x: number, y: number, anchor: 'start' | 'end'): React.ReactElement => (
    <text x={x} y={y} textAnchor={anchor} fontSize={TYPE.micro} fill={theme.text3} fontWeight={600}>
      {labels.shift() || quads.shift() || ''}
    </text>
  )
  return (
    <ChartFrame title={payload.title} unit={payload.unit} legend={false} height={height}>
      {({ width }) => (
        <ResponsiveContainer width={width} height={height}>
          <RcScatter margin={{ top: 16, right: 16, bottom: 8, left: -10 }}>
            <CartesianGrid stroke={theme.border} strokeDasharray="3 6" />
            <XAxis type="number" dataKey="x" domain={xRange} tick={{ fontSize: TYPE.micro, fill: theme.text3 }} axisLine={{ stroke: theme.border }} tickLine={false} />
            <YAxis type="number" dataKey="y" domain={yRange} tick={{ fontSize: TYPE.micro, fill: theme.text3 }} axisLine={false} tickLine={false} />
            <ZAxis dataKey="z" range={[60, 90]} />
            <ReferenceLine x={xMid} stroke={alpha(accent, 0.5)} strokeDasharray="4 4" label={corner(xMid + 6, 14, 'start') as never} />
            <ReferenceLine y={yMid} stroke={alpha(accent, 0.5)} strokeDasharray="4 4" />
            <Tooltip contentStyle={{ background: theme.card, border: `1px solid ${theme.border}`, borderRadius: 12, fontSize: TYPE.label, color: theme.text }} />
            <Scatter name={series[0]?.name ?? 'points'} data={points} fill={accent} fillOpacity={0.7} />
          </RcScatter>
        </ResponsiveContainer>
      )}
    </ChartFrame>
  )
}
