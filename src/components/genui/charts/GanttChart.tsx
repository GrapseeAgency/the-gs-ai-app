'use client'

import type { ChartPayload } from '../types'
import { ChartFrame } from './ChartFrame'
import { alpha, inkOn, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

/** Gantt — one range [start,end] per series; labels name the tasks. */
export function GanttChartView({ payload, height }: { payload: ChartPayload; height?: number }) {
  const { ramp, theme } = useWidgetIdentity()
  const series = payload.series ?? []
  const labels = payload.labels ?? []
  const tasks = series.map((s, i) => ({
    name: s.name || labels[i] || `Task ${i + 1}`,
    start: Math.min(s.data[0]?.[0] ?? 0, s.data[0]?.[1] ?? 0),
    end: Math.max(s.data[0]?.[0] ?? 0, s.data[0]?.[1] ?? 0),
  }))
  const lo = Math.min(...tasks.map((t) => t.start))
  const hi = Math.max(...tasks.map((t) => t.end))
  const span = hi - lo || 1
  const h = height ?? Math.max(110, tasks.length * 34 + 42)
  const colors = ramp(Math.max(1, tasks.length))
  return (
    <ChartFrame title={payload.title} unit={payload.unit} legend={false} height={h}>
      {() => (
        <div>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: TYPE.micro, color: theme.text3, marginBottom: 4 }}>
            <span>{lo}</span>
            <span>{hi}</span>
          </div>
          {tasks.map((t, i) => (
            <div key={t.name} style={{ display: 'flex', alignItems: 'center', gap: 8, height: 28 }}>
              <span style={{ width: 92, fontSize: TYPE.micro, color: theme.text2, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', textAlign: 'right' }}>
                {t.name}
              </span>
              <div style={{ flex: 1, height: 14, borderRadius: 7, background: theme.dark ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.04)', position: 'relative' }}>
                <div
                  title={`${t.name}: ${t.start} → ${t.end}`}
                  style={{
                    position: 'absolute',
                    left: `${((t.start - lo) / span) * 100}%`,
                    width: `${Math.max(2, ((t.end - t.start) / span) * 100)}%`,
                    top: 0,
                    bottom: 0,
                    borderRadius: 7,
                    background: colors[i % colors.length],
                  }}
                />
              </div>
            </div>
          ))}
        </div>
      )}
    </ChartFrame>
  )
}
