'use client'

import { useState } from 'react'
import type { AppPayload } from '../types'
import { alpha, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { AppShell } from './AppShell'
import { UiSegmented } from '../controls/UiSegmented'
import { LineChartView } from '../charts/LineChart'
import { EmptyState } from '../primitives/EmptyState'

/** Analytics — a range switcher over an inline trend chart + KPI row. */
export function AppAnalytics({ payload }: { payload: AppPayload }) {
  const { accent, theme } = useWidgetIdentity()
  const [range, setRange] = useState('30d')
  const items = (payload.items ?? []).slice(0, 4)
  if (!items.length) return <EmptyState label="No metrics" />
  const factor = range === '7d' ? 0.25 : range === '90d' ? 2.8 : 1
  const series = [
    {
      name: 'trend',
      data: Array.from({ length: 12 }, (_, i) => [i, Math.round((Number(items[0]?.subtitle ?? items[0]?.meta ?? 100) || 100) * factor * (0.7 + 0.05 * i))]),
    },
  ]
  return (
    <AppShell title={payload.title}>
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 10 }}>
        <UiSegmented options={['7d', '30d', '90d']} value={range} onChange={setRange} />
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, marginBottom: 12 }}>
        {items.map((item, i) => (
          <div key={i} style={{ padding: '10px 12px', borderRadius: 12, background: alpha(accent, theme.dark ? 0.08 : 0.05) }}>
            <div style={{ fontSize: TYPE.micro, color: theme.text2 }}>{String(item.title ?? '')}</div>
            <div style={{ fontSize: TYPE.heading, fontWeight: 700, color: theme.text }}>{String(item.subtitle ?? '')}</div>
            {item.badge ? <div style={{ fontSize: TYPE.micro, fontWeight: 700, color: accent }}>{String(item.badge)}</div> : null}
          </div>
        ))}
      </div>
      <LineChartView payload={{ type: 'line', series, title: undefined }} height={140} />
    </AppShell>
  )
}
