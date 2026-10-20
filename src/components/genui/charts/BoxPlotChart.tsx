'use client'

import type { ChartPayload } from '../types'
import { ChartFrame } from './ChartFrame'
import { alpha, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

function quantile(sorted: number[], q: number): number {
  const pos = (sorted.length - 1) * q
  const base = Math.floor(pos)
  const rest = pos - base
  return sorted[base + 1] !== undefined ? sorted[base] + rest * (sorted[base + 1] - sorted[base]) : sorted[base]
}

/** Box plot — median, IQR and whiskers per series from raw samples. */
export function BoxPlotChartView({ payload, height = 210 }: { payload: ChartPayload; height?: number }) {
  const { accent, theme, ramp } = useWidgetIdentity()
  const series = payload.series ?? []
  const labels = payload.labels ?? []
  const stats = series.map((s, i) => {
    // documented contract is raw samples (flat numbers); array rows take the last element
    const sorted = s.data.map((d) => (Array.isArray(d) ? d[d.length - 1] : d)).filter(Number.isFinite).sort((a, b) => a - b)
    return {
      name: s.name || labels[i] || `#${i + 1}`,
      min: sorted[0] ?? 0,
      q1: quantile(sorted, 0.25),
      med: quantile(sorted, 0.5),
      q3: quantile(sorted, 0.75),
      max: sorted[sorted.length - 1] ?? 0,
    }
  })
  const lo = Math.min(...stats.map((s) => s.min))
  const hi = Math.max(...stats.map((s) => s.max))
  const span = hi - lo || 1
  const y = (v: number) => height - 24 - ((v - lo) / span) * (height - 44)
  const slot = 100 / Math.max(1, stats.length)
  return (
    <ChartFrame title={payload.title} unit={payload.unit} legend={false} height={height}>
      {() => (
        <div>
          <svg width="100%" height={height} role="img" aria-label="box plot">
            {[lo, (lo + hi) / 2, hi].map((v, i) => (
              <g key={i}>
                <line x1={0} x2="100%" y1={y(v)} y2={y(v)} stroke={theme.border} strokeDasharray="3 6" />
                <text x={2} y={y(v) - 3} fontSize={TYPE.micro} fill={theme.text3}>
                  {v.toFixed(1)}
                </text>
              </g>
            ))}
            {stats.map((s, i) => {
              const cx = `${(i + 0.5) * slot}%`
              const w = Math.min(34, (300 / stats.length) * 0.5)
              const color = ramp(stats.length)[i]
              return (
                <g key={s.name}>
                  <line x1={cx} x2={cx} y1={y(s.min)} y2={y(s.max)} stroke={color} strokeWidth={1.5} />
                  <line x1={`calc(${cx} - ${w / 3}px)`} x2={`calc(${cx} + ${w / 3}px)`} y1={y(s.min)} y2={y(s.min)} stroke={color} strokeWidth={1.5} />
                  <line x1={`calc(${cx} - ${w / 3}px)`} x2={`calc(${cx} + ${w / 3}px)`} y1={y(s.max)} y2={y(s.max)} stroke={color} strokeWidth={1.5} />
                  <rect x={`calc(${cx} - ${w / 2}px)`} y={y(s.q3)} width={w} height={Math.max(2, y(s.q1) - y(s.q3))} rx={5} fill={alpha(color, 0.3)} stroke={color} strokeWidth={1.5} />
                  <line x1={`calc(${cx} - ${w / 2}px)`} x2={`calc(${cx} + ${w / 2}px)`} y1={y(s.med)} y2={y(s.med)} stroke={color} strokeWidth={2.5} />
                  <text x={cx} y={height - 6} textAnchor="middle" fontSize={TYPE.micro} fill={theme.text2}>
                    {s.name}
                  </text>
                </g>
              )
            })}
          </svg>
        </div>
      )}
    </ChartFrame>
  )
}
