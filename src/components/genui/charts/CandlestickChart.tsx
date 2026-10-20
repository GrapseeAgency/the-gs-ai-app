'use client'

import type { ChartPayload } from '../types'
import { ChartFrame } from './ChartFrame'
import { TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

/** Candlestick — [open, high, low, close] per point; green up, red down. */
export function CandlestickChartView({ payload, height = 220 }: { payload: ChartPayload; height?: number }) {
  const { theme } = useWidgetIdentity()
  const labels = payload.labels ?? []
  const data = (payload.series?.[0]?.data ?? [])
    .map((d, i) => ({ label: labels[i] ?? `#${i + 1}`, o: d[0], h: d[1], l: d[2], c: d[3] }))
    .filter((d) => [d.o, d.h, d.l, d.c].every(Number.isFinite))
  if (data.length === 0) return <ChartFrame title={payload.title} seriesNames={[]} legend={false}>{() => null}</ChartFrame>
  const lo = Math.min(...data.map((d) => d.l))
  const hi = Math.max(...data.map((d) => d.h))
  const span = hi - lo || 1
  const y = (v: number) => height - 26 - ((v - lo) / span) * (height - 46)
  const UP = '#0CA678'
  const DOWN = '#E03131'
  const slot = 100 / data.length
  return (
    <ChartFrame title={payload.title} unit={payload.unit} legend={false} height={height}>
      {() => (
        <svg width="100%" height={height} role="img" aria-label="candlestick chart">
          {[lo, (lo + hi) / 2, hi].map((v, i) => (
            <g key={i}>
              <line x1={30} x2="100%" y1={y(v)} y2={y(v)} stroke={theme.border} strokeDasharray="3 6" />
              <text x={2} y={y(v) - 3} fontSize={TYPE.micro} fill={theme.text3}>
                {v.toFixed(1)}
              </text>
            </g>
          ))}
          {data.map((d, i) => {
            const cx = `${(i + 0.5) * slot}%`
            const up = d.c >= d.o
            const color = up ? UP : DOWN
            const top = Math.min(y(d.o), y(d.c))
            const bodyH = Math.max(2, Math.abs(y(d.o) - y(d.c)))
            const bw = Math.max(3, Math.min(16, (300 / data.length) * 0.55))
            return (
              <g key={i}><title>{`${d.label}: O ${d.o} H ${d.h} L ${d.l} C ${d.c}`}</title>
                <line x1={cx} x2={cx} y1={y(d.h)} y2={y(d.l)} stroke={color} strokeWidth={1.5} />
                <rect x={`calc(${cx} - ${bw / 2}px)`} y={top} width={bw} height={bodyH} rx={2} fill={up ? 'transparent' : color} stroke={color} strokeWidth={1.5} />
                {i % Math.ceil(data.length / 7) === 0 && (
                  <text x={cx} y={height - 6} textAnchor="middle" fontSize={TYPE.micro} fill={theme.text3}>
                    {d.label}
                  </text>
                )}
              </g>
            )
          })}
        </svg>
      )}
    </ChartFrame>
  )
}
