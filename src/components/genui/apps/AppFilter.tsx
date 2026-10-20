'use client'

import { useMemo, useState } from 'react'
import type { AppPayload } from '../types'
import { TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { AppShell } from './AppShell'
import { EmptyState } from '../primitives/EmptyState'
import { UiChipToggle } from '../controls/UiChipToggle'

/** Filter — facet chips narrow a list live. */
export function AppFilter({ payload }: { payload: AppPayload }) {
  const { theme } = useWidgetIdentity()
  const items = (payload.items ?? []).slice(0, 40)
  const facets = [...new Set(items.map((it) => String(it.badge ?? '')).filter(Boolean))]
  const [active, setActive] = useState<Set<string>>(new Set())
  const hits = useMemo(
    () => (active.size === 0 ? items : items.filter((it) => active.has(String(it.badge ?? '')))),
    [items, active]
  )
  if (!items.length) return <EmptyState label="No items" />
  return (
    <AppShell title={payload.title} right={`${hits.length}/${items.length}`}>
      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 10 }}>
        {facets.map((f) => (
          <UiChipToggle
            key={f}
            label={f}
            selected={active.has(f)}
            onToggle={() =>
              setActive((s) => {
                const next = new Set(s)
                if (next.has(f)) next.delete(f)
                else next.add(f)
                return next
              })
            }
          />
        ))}
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 5, maxHeight: 240, overflowY: 'auto' }}>
        {hits.map((it, i) => (
          <div key={i} style={{ display: 'flex', justifyContent: 'space-between', gap: 8, padding: '7px 10px', borderRadius: 9 }}>
            <span style={{ fontSize: TYPE.label, color: theme.text }}>{String(it.title ?? '')}</span>
            <span style={{ fontSize: TYPE.micro, color: theme.text3 }}>{String(it.meta ?? it.subtitle ?? '')}</span>
          </div>
        ))}
      </div>
    </AppShell>
  )
}
