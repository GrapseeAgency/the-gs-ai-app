'use client'

import type { AppPayload } from '../types'
import { alpha, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { AppShell } from './AppShell'
import { Chip } from '../primitives/Chip'
import { EmptyState } from '../primitives/EmptyState'

/** Connected — the card row of connected services/accounts. */
export function AppConnected({ payload }: { payload: AppPayload }) {
  const { accent, theme } = useWidgetIdentity()
  const items = (payload.items ?? []).slice(0, 8)
  if (!items.length) return <EmptyState label="No connections" />
  return (
    <AppShell title={payload.title ?? 'Connected'}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {items.map((it, i) => (
          <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: 12, borderRadius: 12, border: `1px solid ${theme.border}` }}>
            <span
              style={{
                width: 34,
                height: 34,
                borderRadius: 10,
                background: alpha(accent, 0.14),
                color: accent,
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontWeight: 700,
                fontSize: 14,
                flexShrink: 0,
              }}
            >
              {String(it.title ?? '?').slice(0, 1).toUpperCase()}
            </span>
            <span style={{ flex: 1, minWidth: 0 }}>
              <span style={{ display: 'block', fontSize: TYPE.label, fontWeight: 600, color: theme.text }}>{String(it.title ?? '')}</span>
              <span style={{ display: 'block', fontSize: TYPE.micro, color: theme.text3, marginTop: 1 }}>{String(it.subtitle ?? it.meta ?? '')}</span>
            </span>
            <Chip tone={String(it.badge ?? '').toLowerCase() === 'offline' ? 'danger' : 'success'}>{String(it.badge ?? 'Connected')}</Chip>
          </div>
        ))}
      </div>
    </AppShell>
  )
}
