'use client'

import type { AppPayload } from '../types'
import { alpha, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { AppShell } from './AppShell'
import { EmptyState } from '../primitives/EmptyState'

/** Schedule — a day timeline of appointments. */
export function AppSchedule({ payload }: { payload: AppPayload }) {
  const { accent, theme } = useWidgetIdentity()
  const items = (payload.items ?? []).slice(0, 12)
  if (!items.length) return <EmptyState label="Nothing scheduled" />
  return (
    <AppShell title={payload.title}>
      <div style={{ position: 'relative', paddingLeft: 46 }}>
        <span aria-hidden style={{ position: 'absolute', left: 38, top: 8, bottom: 8, width: 2, background: theme.border }} />
        {items.map((item, i) => (
          <div key={i} style={{ position: 'relative', paddingBottom: 12 }}>
            <span style={{ position: 'absolute', left: -46, top: 2, fontSize: TYPE.micro, color: theme.text3, fontVariantNumeric: 'tabular-nums', width: 34, textAlign: 'right' }}>
              {String(item.date ?? item.meta ?? '')}
            </span>
            <span aria-hidden style={{ position: 'absolute', left: -12, top: 6, width: 9, height: 9, borderRadius: 999, background: i === 0 ? accent : theme.raised, border: `2px solid ${accent}` }} />
            <div style={{ padding: '8px 12px', borderRadius: 10, background: alpha(accent, theme.dark ? 0.08 : 0.05), border: `1px solid ${alpha(accent, 0.18)}` }}>
              <span style={{ fontSize: TYPE.label, fontWeight: 600, color: theme.text }}>{String(item.title ?? '')}</span>
              {item.subtitle ? <span style={{ display: 'block', fontSize: TYPE.micro, color: theme.text3, marginTop: 2 }}>{String(item.subtitle)}</span> : null}
            </div>
          </div>
        ))}
      </div>
    </AppShell>
  )
}
