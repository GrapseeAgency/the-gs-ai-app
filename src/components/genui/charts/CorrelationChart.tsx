'use client'

import type { ChartPayload } from '../types'
import { ChartFrame } from './ChartFrame'
import { alpha, inkOn, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

/**
 * Correlation matrix — values -1..1: crimson for negative, azure for positive.
 * (Same reading as the Android renderer.)
 */
export function CorrelationChartView({ payload, height }: { payload: ChartPayload; height?: number }) {
  const { theme } = useWidgetIdentity()
  const labels = payload.labels ?? []
  const series = payload.series ?? []
  const n = series.length
  const POS = '#4C6FFF'
  const NEG = '#E03131'
  if (n === 0) return <ChartFrame title={payload.title} seriesNames={[]} legend={false}>{() => null}</ChartFrame>
  return (
    <ChartFrame title={payload.title} legend={false} height={height}>
      {() => (
        <div style={{ display: 'grid', gridTemplateColumns: `90px repeat(${n}, 1fr)`, gap: 3 }}>
          <span />
          {series.map((s) => (
            <span key={s.name} style={{ fontSize: TYPE.micro, color: theme.text3, textAlign: 'center', overflow: 'hidden', textOverflow: 'ellipsis' }}>
              {s.name}
            </span>
          ))}
          {series.map((row, r) => (
            <div key={row.name} style={{ display: 'contents' }}>
              <span style={{ fontSize: TYPE.micro, color: theme.text2, alignSelf: 'center', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                {row.name || labels[r]}
              </span>
              {series.map((col, c) => {
                const v = row.data[c]?.[row.data[c].length - 1] ?? 0
                const pos = v >= 0
                const a = 0.08 + Math.min(1, Math.abs(v)) * 0.82
                const hue = pos ? POS : NEG
                return (
                  <div
                    key={c}
                    title={`${row.name} × ${col.name}: ${v.toFixed(2)}`}
                    style={{
                      height: 32,
                      borderRadius: 7,
                      background: alpha(hue, a),
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontSize: TYPE.micro,
                      fontWeight: 700,
                      color: a > 0.5 ? inkOn(hue) : theme.text2,
                    }}
                  >
                    {v.toFixed(2).replace('0.', '.')}
                  </div>
                )
              })}
            </div>
          ))}
        </div>
      )}
    </ChartFrame>
  )
}
