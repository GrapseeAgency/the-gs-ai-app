'use client'

import { useState } from 'react'
import type { AppPayload } from '../types'
import { RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { AppShell } from './AppShell'
import { EmptyState } from '../primitives/EmptyState'

/** Report — a sectioned document with a per-section open state. */
export function AppReport({ payload }: { payload: AppPayload }) {
  const { accent, theme } = useWidgetIdentity()
  const groups = (payload.groups ?? []).slice(0, 8)
  const [open, setOpen] = useState(0)
  if (!groups.length) return <EmptyState label="No report sections" />
  return (
    <AppShell title={payload.title}>
      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 12 }}>
        {groups.map((g, i) => (
          <button
            key={g.title}
            type="button"
            onClick={() => setOpen(i)}
            style={{
              padding: '6px 12px',
              borderRadius: 999,
              border: `1px solid ${open === i ? accent : 'transparent'}`,
              background: open === i ? `${accent}1a` : 'transparent',
              color: open === i ? accent : theme.text2,
              fontSize: TYPE.micro,
              fontWeight: 700,
              cursor: 'pointer',
            }}
          >
            {g.title}
          </button>
        ))}
      </div>
      <div style={{ padding: 14, borderRadius: RADIUS.lg, border: `1px solid ${theme.border}` }}>
        {(groups[open]?.options ?? []).map((o, i) => (
          <div key={i} style={{ marginBottom: i === (groups[open]?.options ?? []).length - 1 ? 0 : 10 }}>
            <div style={{ fontSize: TYPE.label, fontWeight: 700, color: theme.text }}>{String(o.label ?? '')}</div>
            {o.detail ? <div style={{ fontSize: TYPE.label, color: theme.text2, lineHeight: 1.6, marginTop: 3 }}>{String(o.detail)}</div> : null}
          </div>
        ))}
      </div>
    </AppShell>
  )
}
