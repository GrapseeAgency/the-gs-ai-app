'use client'

import type { ChartPayload } from '../types'
import { ChartFrame, niceTicks } from './ChartFrame'
import { alpha, inkOn, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

/** Histogram — a distribution of raw samples in bins. */
export function HistogramChartView({ payload, height = 190 }: { payload: ChartPayload; height?: number }) {
  const { accent, theme } = useWidgetIdentity()
  const raw = (payload.series?.[0]?.data ?? []).map((d) => d[d.length - 1]).filter((n) => Number.isFinite(n))
  if (raw.length === 0) return <ChartFrame title={payload.title} seriesNames={[]} legend={false}>{() => null}</ChartFrame>
  const bins = Math.min(12, Math.max(5, Math.ceil(Math.sqrt(raw.length))))
  const min = Math.min(...raw)
  const max = Math.max(...raw)
  const w = (max - min) / bins || 1
  const counts = Array.from({ length: bins }, (_, i) => ({
    from: min + i * w,
    to: min + (i + 1) * w,
    n: raw.filter((v) => v >= min + i * w && (i === bins - 1 ? v <= max : v < min + (i + 1) * w)).length,
  }))
  const peak = Math.max(...counts.map((c) => c.n))
  const ticks = niceTicks(min, max, 5)
  return (
    <ChartFrame title={payload.title} unit={payload.unit} legend={false} height={height}>
      {({ width }) => (
        <div>
          <div style={{ display: 'flex', alignItems: 'flex-end', gap: 3, height }}>
            {counts.map((c, i) => (
              <div key={i} style={{ flex: 1, textAlign: 'center' }}>
                <div style={{ fontSize: TYPE.micro, color: theme.text3, marginBottom: 2 }}>{c.n > 0 ? c.n : ''}</div>
                <div
                  style={{
                    height: Math.max(2, (c.n / peak) * (height - 26)),
                    background: alpha(accent, 0.85),
                    borderRadius: '4px 4px 0 0',
                  }}
                  title={`${c.from.toFixed(1)} – ${c.to.toFixed(1)}: ${c.n}`}
                />
              </div>
            ))}
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: TYPE.micro, color: theme.text3, marginTop: 4 }}>
            {ticks.map((t) => (
              <span key={t}>{t.toFixed(1)}</span>
            ))}
          </div>
        </div>
      )}
    </ChartFrame>
  )
}
