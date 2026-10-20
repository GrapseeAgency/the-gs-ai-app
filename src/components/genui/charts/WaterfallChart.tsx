'use client'

import type { ChartPayload } from '../types'
import { ChartFrame } from './ChartFrame'
import { alpha, inkOn, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

/** Waterfall — cumulative build-up from a start value with up/down steps. */
export function WaterfallChartView({ payload, height = 200 }: { payload: ChartPayload; height?: number }) {
  const { accent, theme } = useWidgetIdentity()
  const labels = payload.labels ?? []
  const values = (payload.series?.[0]?.data ?? []).map((d) => d[d.length - 1])
  if (values.length === 0) return <ChartFrame title={payload.title} seriesNames={[]} legend={false}>{() => null}</ChartFrame>
  const UP = '#0CA678'
  const DOWN = '#E03131'
  const steps = values.map((v, i) => {
    const start = i === 0 ? 0 : values.slice(0, i).reduce((a, b) => a + b, 0)
    return { label: labels[i] ?? `#${i + 1}`, start, end: start + v, delta: v }
  })
  const lo = Math.min(0, ...steps.map((s) => Math.min(s.start, s.end)))
  const hi = Math.max(...steps.map((s) => Math.max(s.start, s.end)))
  const span = hi - lo || 1
  const y = (v: number) => height - 26 - ((v - lo) / span) * (height - 46)
  return (
    <ChartFrame title={payload.title} unit={payload.unit} legend={false} height={height}>
      {() => (
        <div style={{ display: 'flex', alignItems: 'flex-end', gap: 4, height, position: 'relative' }}>
          {steps.map((s, i) => {
            const top = Math.min(y(s.start), y(s.end))
            const bottom = Math.max(y(s.start), y(s.end))
            const color = i === 0 ? accent : s.delta >= 0 ? UP : DOWN
            return (
              <div key={i} style={{ flex: 1, position: 'relative', height: '100%' }}>
                <div
                  title={`${s.label}: ${s.delta >= 0 ? '+' : ''}${s.delta}`}
                  style={{
                    position: 'absolute',
                    left: 0,
                    right: 0,
                    top,
                    height: Math.max(3, bottom - top),
                    background: color,
                    borderRadius: 4,
                    opacity: 0.9,
                  }}
                />
                <div
                  style={{
                    position: 'absolute',
                    left: 0,
                    right: 0,
                    top: top - 15,
                    textAlign: 'center',
                    fontSize: TYPE.micro,
                    fontWeight: 700,
                    color,
                  }}
                >
                  {i > 0 ? (s.delta >= 0 ? '+' : '') + s.delta : s.end}
                </div>
                <div
                  style={{
                    position: 'absolute',
                    left: '-20%',
                    right: '-20%',
                    top: bottom + 4,
                    textAlign: 'center',
                    fontSize: TYPE.micro,
                    color: theme.text3,
                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                  }}
                >
                  {s.label}
                </div>
              </div>
            )
          })}
        </div>
      )}
    </ChartFrame>
  )
}
