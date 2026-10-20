'use client'

import type { ChartPayload } from '../types'
import { ChartFrame } from './ChartFrame'
import { EmptyState } from '../primitives/EmptyState'
import { alpha, inkOn, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

/** Heatmap — one series per row, [col,value] points; magnitude as colour. */
export function HeatmapChartView({ payload, height }: { payload: ChartPayload; height?: number }) {
  const { accent, theme } = useWidgetIdentity()
  const labels = payload.labels ?? []
  const series = payload.series ?? []
  const values: Array<{ row: number; col: number; v: number; label: string }> = []
  series.forEach((s, r) => {
    s.data.forEach((d, c) => {
      const colLabel = labels[d[0]] ?? labels[c] ?? String(c + 1)
      values.push({ row: r, col: c, v: d[d.length - 1], label: colLabel })
    })
  })
  if (values.length === 0) return <EmptyState label="No heat data" />
  const vs = values.map((v) => v.v)
  const min = Math.min(...vs)
  const max = Math.max(...vs)
  const norm = (v: number) => (max === min ? 0.5 : (v - min) / (max - min))
  const cols = Math.max(...values.map((v) => v.col)) + 1
  return (
    <ChartFrame title={payload.title} unit={payload.unit} legend={false} height={height}>
      {() => (
        <div style={{ overflowX: 'auto' }}>
          <div style={{ display: 'grid', gridTemplateColumns: `86px repeat(${cols}, 1fr)`, gap: 3, minWidth: cols * 36 }}>
            <span />
            {Array.from({ length: cols }, (_, c) => (
              <span key={c} style={{ fontSize: TYPE.micro, color: theme.text3, textAlign: 'center' }}>
                {labels[c] ?? c + 1}
              </span>
            ))}
            {series.map((s, r) => (
              <div key={s.name} style={{ display: 'contents' }}>
                <span style={{ fontSize: TYPE.micro, color: theme.text2, alignSelf: 'center', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  {s.name}
                </span>
                {Array.from({ length: cols }, (_, c) => {
                  const cell = values.find((v) => v.row === r && v.col === c)
                  const a = cell ? 0.15 + norm(cell.v) * 0.85 : 0.04
                  return (
                    <div
                      key={c}
                      title={cell ? `${s.name} · ${cell.label}: ${cell.v}` : ''}
                      style={{
                        height: 30,
                        borderRadius: 7,
                        background: alpha(accent, a),
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        fontSize: TYPE.micro,
                        fontWeight: 600,
                        color: a > 0.55 ? inkOn(accent) : theme.text2,
                      }}
                    >
                      {cell ? cell.v : ''}
                    </div>
                  )
                })}
              </div>
            ))}
          </div>
        </div>
      )}
    </ChartFrame>
  )
}
