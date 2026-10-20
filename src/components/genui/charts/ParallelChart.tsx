'use client'

import type { ChartPayload } from '../types'
import { ChartFrame } from './ChartFrame'
import { alpha, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

/** Parallel coordinates — each row is a polyline across normalised axes. */
export function ParallelChartView({ payload, height = 230 }: { payload: ChartPayload; height?: number }) {
  const { ramp, theme } = useWidgetIdentity()
  const axes = (payload.axes ?? []) as string[]
  const series = (payload.series ?? []) as unknown as Array<{ name: string; values: number[] }>
  const W = 320
  const H = height
  if (axes.length < 3 || !series.length) return <ChartFrame title={payload.title} seriesNames={[]} legend={false}>{() => null}</ChartFrame>
  const colors = ramp(Math.max(1, series.length))
  const x = (i: number) => 30 + (i / (axes.length - 1)) * (W - 46)
  return (
    <ChartFrame title={payload.title} legend={series.length > 1 && series.length <= 6} seriesNames={series.map((s) => s.name)} height={height}>
      {() => (
        <svg width="100%" height={H} viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="xMidYMid meet" role="img" aria-label="parallel coordinates">
          {axes.map((axis, i) => {
            const col = series.map((s) => s.values[i] ?? 0)
            const lo = Math.min(...col)
            const hi = Math.max(...col)
            return (
              <g key={axis}>
                <line x1={x(i)} x2={x(i)} y1={10} y2={H - 26} stroke={theme.border} />
                <text x={x(i)} y={H - 14} textAnchor="middle" fontSize={TYPE.micro} fill={theme.text2}>{axis}</text>
                <text x={x(i)} y={8} textAnchor="middle" fontSize={9} fill={theme.text3}>{hi.toFixed(0)}</text>
                <text x={x(i)} y={H - 26 + 9} textAnchor="middle" fontSize={9} fill={theme.text3}>{lo.toFixed(0)}</text>
              </g>
            )
          })}
          {series.map((s, si) => {
            const pts = axes.map((_, i) => {
              const col = series.map((row) => row.values[i] ?? 0)
              const lo = Math.min(...col)
              const hi = Math.max(...col)
              const t = hi === lo ? 0.5 : (s.values[i] - lo) / (hi - lo)
              return `${x(i)},${14 + (1 - t) * (H - 46)}`
            })
            return (
              <polyline
                key={s.name}
                points={pts.join(' ')}
                fill="none"
                stroke={colors[si % colors.length]}
                strokeWidth={1.8}
                strokeOpacity={series.length > 4 ? 0.55 : 0.9}
                strokeLinejoin="round"
              >
                <title>{s.name}</title>
              </polyline>
            )
          })}
        </svg>
      )}
    </ChartFrame>
  )
}
