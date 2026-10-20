'use client'

import { useMemo } from 'react'
import type { ChartPayload } from '../types'
import { ChartFrame } from './ChartFrame'
import { alpha, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

/**
 * Chord — flows between entities on a circle. The matrix is symmetric flow
 * volume derived from a pie-like series list (entity, value) or real
 * matrix data in series[i].data[j].
 */
export function ChordChartView({ payload, height = 240 }: { payload: ChartPayload; height?: number }) {
  const { ramp, theme } = useWidgetIdentity()
  const labels = payload.labels ?? []
  const series = payload.series ?? []
  const W = 320
  const H = height - 24
  const layout = useMemo(() => {
    const n = series.length
    const cx = W / 2
    const cy = H / 2
    const R = Math.min(cx, cy) - 18
    const names = series.map((s, i) => s.name || labels[i] || `#${i + 1}`)
    const total = series.reduce((a, s) => a + (s.data?.[0]?.[0] ?? 1), 0) || 1
    let a0 = -Math.PI / 2
    const arcs = series.map((s, i) => {
      const frac = (s.data?.[0]?.[0] ?? 1) / total
      const a1 = a0 + frac * Math.PI * 2
      const arc = { name: names[i], a0, a1, i }
      a0 = a1
      return arc
    })
    const ribbons: Array<{ d: string; color: string; name: string }> = []
    for (let i = 0; i < n; i++) {
      for (let j = i + 1; j < n; j++) {
        const vi = series[i]?.data?.[j]?.[0]
        const vj = series[j]?.data?.[i]?.[0]
        if (vi === undefined && vj === undefined) continue
        const p = (arcIndex: number, frac: number, r: number) => {
          const a = arcs[arcIndex]
          const angle = a.a0 + frac * (a.a1 - a.a0)
          return `${cx + r * Math.sin(angle)} ${cy - r * Math.cos(angle)}`
        }
        const w = 0.06
        const d = `M ${p(i, 0, R - 4)} C ${cx * 0.9} ${cy * 0.9}, ${cx * 0.9} ${cy * 0.9}, ${p(j, 0, R - 4)} L ${p(j, w, R - 4)} C ${cx * 0.9} ${cy * 0.9}, ${cx * 0.9} ${cy * 0.9}, ${p(i, w, R - 4)} Z`
        ribbons.push({ d, color: ramp(n)[i], name: `${names[i]} ↔ ${names[j]}` })
      }
    }
    return { arcs, ribbons, cx, cy, R }
  }, [series, labels, H])
  const colors = ramp(Math.max(2, series.length || 2))
  return (
    <ChartFrame title={payload.title} unit={payload.unit} legend={false} height={height}>
      {({ width }) => (
        <svg width="100%" height={H} viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="xMidYMid meet" role="img" aria-label="chord diagram">
          {layout.ribbons.map((r, i) => (
            <path key={i} d={r.d} fill={r.color} fillOpacity={0.28} stroke="none">
              <title>{r.name}</title>
            </path>
          ))}
          {layout.arcs.map((a, i) => (
            <g key={i}>
              <path
                d={
                  `M ${layout.cx + layout.R * Math.sin(a.a0)} ${layout.cy - layout.R * Math.cos(a.a0)}` +
                  ` A ${layout.R} ${layout.R} 0 ${a.a1 - a.a0 > Math.PI ? 1 : 0} 1 ${layout.cx + layout.R * Math.sin(a.a1)} ${layout.cy - layout.R * Math.cos(a.a1)}` +
                  ` L ${layout.cx + (layout.R - 10) * Math.sin(a.a1)} ${layout.cy - (layout.R - 10) * Math.cos(a.a1)}` +
                  ` A ${layout.R - 10} ${layout.R - 10} 0 ${a.a1 - a.a0 > Math.PI ? 1 : 0} 0 ${layout.cx + (layout.R - 10) * Math.sin(a.a0)} ${layout.cy - (layout.R - 10) * Math.cos(a.a0)} Z`
                }
                fill={colors[i % colors.length]}
                stroke={theme.dark ? '#0F1216' : '#fff'}
                strokeWidth={1}
              />
              <text
                x={layout.cx + (layout.R + 8) * Math.sin((a.a0 + a.a1) / 2)}
                y={layout.cy - (layout.R + 8) * Math.cos((a.a0 + a.a1) / 2) + 3}
                textAnchor={Math.sin((a.a0 + a.a1) / 2) > 0.1 ? 'start' : Math.sin((a.a0 + a.a1) / 2) < -0.1 ? 'end' : 'middle'}
                fontSize={TYPE.micro}
                fill={theme.text2}
                fontWeight={600}
              >
                {a.name}
              </text>
            </g>
          ))}
        </svg>
      )}
    </ChartFrame>
  )
}
