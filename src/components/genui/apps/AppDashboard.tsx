'use client'

import type { AppPayload } from '../types'
import { alpha, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { AppShell } from './AppShell'
import { EmptyState } from '../primitives/EmptyState'

/** Dashboard — metric tiles grid. */
export function AppDashboard({ payload }: { payload: AppPayload }) {
  const { accent, theme } = useWidgetIdentity()
  const items = (payload.items ?? []).slice(0, 8)
  if (!items.length) return <EmptyState label="No metrics" />
  return (
    <AppShell title={payload.title}>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
        {items.map((item, i) => (
          <div key={i} style={{ padding: 12, borderRadius: RADIUS_T, background: alpha(accent, theme.dark ? 0.08 : 0.05), border: `1px solid ${alpha(accent, 0.15)}` }}>
            <div style={{ fontSize: TYPE.micro, color: theme.text2, marginBottom: 4 }}>{String(item.title ?? '')}</div>
            <div style={{ fontSize: TYPE.title, fontWeight: 700, color: theme.text }}>{String(item.subtitle ?? item.meta ?? '')}</div>
            {item.badge ? <div style={{ fontSize: TYPE.micro, fontWeight: 700, color: accent, marginTop: 3 }}>{String(item.badge)}</div> : null}
          </div>
        ))}
      </div>
    </AppShell>
  )
}

const RADIUS_T = 12
