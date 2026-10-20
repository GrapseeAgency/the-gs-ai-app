'use client'

import { useMemo, useState } from 'react'
import type { AppPayload } from '../types'
import { RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { AppShell } from './AppShell'
import { EmptyState } from '../primitives/EmptyState'
import { UiTextInput } from '../controls/UiTextInput'

/** Search — live client-side filtering over a result list. */
export function AppSearch({ payload }: { payload: AppPayload }) {
  const { accent, theme } = useWidgetIdentity()
  const items = (payload.items ?? []).slice(0, 40)
  const [q, setQ] = useState('')
  if (!items.length) return <EmptyState label="No searchable items" />
  const hits = useMemo(() => {
    const needle = q.trim().toLowerCase()
    if (!needle) return items
    return items.filter((it) => `${it.title ?? ''} ${it.subtitle ?? ''} ${it.meta ?? ''}`.toLowerCase().includes(needle))
  }, [items, q])
  return (
    <AppShell title={payload.title} right={`${hits.length}/${items.length}`}>
      <UiTextInput placeholder="Search…" value={q} onChange={setQ} ariaLabel="search" />
      <div style={{ marginTop: 10, display: 'flex', flexDirection: 'column', gap: 6, maxHeight: 260, overflowY: 'auto' }}>
        {hits.map((it, i) => (
          <div key={i} style={{ padding: '8px 10px', borderRadius: 10, border: `1px solid ${theme.border}` }}>
            <span style={{ display: 'block', fontSize: TYPE.label, fontWeight: 600, color: theme.text }}>{String(it.title ?? '')}</span>
            {it.subtitle ? <span style={{ display: 'block', fontSize: TYPE.micro, color: theme.text3, marginTop: 2 }}>{String(it.subtitle)}</span> : null}
          </div>
        ))}
        {hits.length === 0 && <div style={{ fontSize: TYPE.label, color: theme.text3, textAlign: 'center', padding: 16 }}>Nothing for “{q}”</div>}
      </div>
      {q && hits.length > 0 && <div style={{ marginTop: 6, fontSize: TYPE.micro, color: accent }}>Ask “{q}” →</div>}
    </AppShell>
  )
}
