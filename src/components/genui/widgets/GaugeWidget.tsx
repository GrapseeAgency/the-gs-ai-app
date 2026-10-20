'use client'

import type { GaugePayload } from '../types'
import { alpha, inkOn, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { TitleRow } from '../primitives/TitleRow'
import { EmptyState } from '../primitives/EmptyState'

/** Gauge — radial meters for a handful of metrics. */
export function GaugeWidget({ payload }: { payload: GaugePayload }) {
  const { accent, ramp, theme } = useWidgetIdentity()
  const items = (payload.items ?? []).slice(0, 6)
  if (!items.length) return <EmptyState label="No gauges" />
  const size = 84
  const stroke = 9
  const r = (size - stroke) / 2
  const circ = 2 * Math.PI * r
  const colors = ramp(Math.max(1, items.length))
  return (
    <div>
      <TitleRow title={payload.title} />
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12, justifyContent: items.length > 2 ? 'space-between' : 'flex-start' }}>
        {items.map((item, i) => {
          const frac = Math.max(0, Math.min(1, item.value / (item.max || 100)))
          const color = colors[i % colors.length]
          return (
            <div key={item.label} style={{ textAlign: 'center' }}>
              <div style={{ position: 'relative', width: size, height: size }}>
                <svg width={size} height={size} style={{ transform: 'rotate(-90deg)' }}>
                  <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke={theme.dark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.07)'} strokeWidth={stroke} />
                  <circle
                    cx={size / 2}
                    cy={size / 2}
                    r={r}
                    fill="none"
                    stroke={color}
                    strokeWidth={stroke}
                    strokeLinecap="round"
                    strokeDasharray={`${frac * circ} ${circ}`}
                    style={{ transition: 'stroke-dasharray .7s cubic-bezier(.22,1,.36,1)' }}
                  />
                </svg>
                <div
                  style={{
                    position: 'absolute',
                    inset: 0,
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  <span style={{ fontSize: TYPE.heading, fontWeight: 700, color: theme.text, lineHeight: 1 }}>
                    {item.value}
                    <span style={{ fontSize: TYPE.micro, color: theme.text3 }}>{item.unit}</span>
                  </span>
                </div>
              </div>
              <div style={{ fontSize: TYPE.micro, color: theme.text2, marginTop: 4, maxWidth: 92, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {item.label}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
