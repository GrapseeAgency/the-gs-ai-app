'use client'

import { useState } from 'react'
import type { TabsPayload } from '../types'
import { alpha, inkOn, RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { TitleRow } from '../primitives/TitleRow'
import { EmptyState } from '../primitives/EmptyState'

/** Tabs — 2-4 parallel facets of one topic. */
export function TabsWidget({ payload }: { payload: TabsPayload }) {
  const { accent, theme } = useWidgetIdentity()
  const [active, setActive] = useState(0)
  const tabs = (payload.tabs ?? []).slice(0, 8)
  if (!tabs.length) return <EmptyState label="No tabs" />
  const current = tabs[Math.min(active, tabs.length - 1)]
  return (
    <div>
      <TitleRow title={payload.title} />
      <div role="tablist" style={{ display: 'flex', gap: 2, padding: 3, borderRadius: RADIUS.pill, background: theme.dark ? 'rgba(0,0,0,0.3)' : 'rgba(0,0,0,0.05)', width: 'fit-content', maxWidth: '100%', overflowX: 'auto' }}>
        {tabs.map((t, i) => (
          <button
            key={i}
            role="tab"
            aria-selected={i === active}
            type="button"
            onClick={() => setActive(i)}
            style={{
              border: 'none',
              padding: '6px 14px',
              borderRadius: RADIUS.pill,
              fontSize: TYPE.label,
              fontWeight: 600,
              whiteSpace: 'nowrap',
              cursor: 'pointer',
              background: i === active ? accent : 'transparent',
              color: i === active ? inkOn(accent) : theme.text2,
            }}
          >
            {t.label}
          </button>
        ))}
      </div>
      <div style={{ marginTop: 12, fontSize: TYPE.body, color: theme.text, lineHeight: 1.6 }}>
        {current?.body}
        {current?.items?.length ? (
          <ul style={{ margin: '8px 0 0', paddingLeft: 18 }}>
            {current.items.map((item, i) => (
              <li key={i} style={{ marginBottom: 4, color: theme.text2, fontSize: TYPE.label }}>
                {item}
              </li>
            ))}
          </ul>
        ) : null}
      </div>
    </div>
  )
}
