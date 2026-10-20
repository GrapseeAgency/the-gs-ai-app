'use client'

import type { ChartPayload } from '../types'
import { ChartFrame } from './ChartFrame'
import { alpha, inkOn, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

/** Bullet — value vs target vs qualitative ranges; the KPI-with-context bar. */
export function BulletChartView({ payload, height }: { payload: ChartPayload; height?: number }) {
  const { accent, theme } = useWidgetIdentity()
  const items = (payload.items ?? []).filter((i) => i.label)
  const globalMax = (payload.max ?? Math.max(1, ...items.map((i) => Math.max(i.value ?? 0, i.target ?? 0, ...(i.ranges ?? [])))) * 1.05)
  const h = height ?? Math.max(90, items.length * 52 + 16)
  return (
    <ChartFrame title={payload.title} unit={payload.unit} legend={false} height={h}>
      {() => (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          {items.map((item) => {
            const max = item.max && item.max > 0 ? item.max : globalMax
            const pct = (v?: number | null) => `${Math.min(100, ((v ?? 0) / max) * 100)}%`
            const ranges = (item.ranges && item.ranges.length ? item.ranges : [max * 0.5, max * 0.75, max]).sort((a, b) => a - b)
            return (
              <div key={item.label}>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: TYPE.label, marginBottom: 4 }}>
                  <span style={{ color: theme.text2, fontWeight: 600 }}>{item.label}</span>
                  <span style={{ color: theme.text, fontWeight: 700 }}>
                    {item.value}
                    {item.unit ? ` ${item.unit}` : ''}
                    {item.target !== null && item.target !== undefined ? <span style={{ color: theme.text3, fontWeight: 400 }}> / {item.target}{item.unit ? ` ${item.unit}` : ''}</span> : null}
                  </span>
                </div>
                <div style={{ position: 'relative', height: 14, borderRadius: 7, background: theme.dark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.05)', overflow: 'hidden' }}>
                  {ranges.map((r, ri) => (
                    <div
                      key={ri}
                      style={{
                        position: 'absolute',
                        left: ri === 0 ? 0 : pct(ranges[ri - 1]),
                        width: pct(ri === 0 ? r : r - ranges[ri - 1]),
                        top: 0,
                        bottom: 0,
                        background: alpha(accent, 0.10 + ri * 0.09),
                      }}
                    />
                  ))}
                  <div
                    style={{
                      position: 'absolute',
                      left: 0,
                      width: pct(item.value),
                      top: 3,
                      bottom: 3,
                      borderRadius: 4,
                      background: accent,
                    }}
                  />
                  {item.target !== null && item.target !== undefined && (
                    <div
                      title={`target ${item.target}`}
                      style={{
                        position: 'absolute',
                        left: pct(item.target),
                        top: 0,
                        bottom: 0,
                        width: 2.5,
                        background: theme.dark ? '#F2F5F9' : '#14171C',
                        borderRadius: 2,
                      }}
                    />
                  )}
                </div>
              </div>
            )
          })}
        </div>
      )}
    </ChartFrame>
  )
}
