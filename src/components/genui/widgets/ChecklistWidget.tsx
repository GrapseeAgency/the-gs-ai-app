'use client'

import { useState } from 'react'
import type { ChecklistPayload } from '../types'
import { RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { TitleRow } from '../primitives/TitleRow'
import { EmptyState } from '../primitives/EmptyState'
import { UiCheckbox } from '../controls/UiCheckbox'

/** Checklist — tap to check, live progress bar up top. */
export function ChecklistWidget({ payload }: { payload: ChecklistPayload }) {
  const { accent, theme } = useWidgetIdentity()
  const [items, setItems] = useState(() => (payload.items ?? []).map((i) => ({ ...i })))
  if (!(payload.items ?? []).length) return <EmptyState label="No items" />
  const done = items.filter((i) => i.done).length
  const pct = Math.round((done / items.length) * 100)
  return (
    <div>
      <TitleRow title={payload.title} right={`${done}/${items.length}`} />
      <div style={{ height: 6, borderRadius: 3, background: theme.dark ? 'rgba(255,255,255,0.07)' : 'rgba(0,0,0,0.06)', overflow: 'hidden', marginBottom: 12 }}>
        <div style={{ width: `${pct}%`, height: '100%', background: accent, borderRadius: 3, transition: 'width .4s ease' }} />
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        {items.map((item, i) => (
          <div key={i} style={{ padding: '6px 0', borderBottom: `1px solid ${theme.dark ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.04)'}` }}>
            <UiCheckbox
              label={item.label}
              checked={item.done === true}
              onChange={(v) => setItems((s) => s.map((it, ii) => (ii === i ? { ...it, done: v } : it)))}
            />
            {item.detail ? (
              <div style={{ fontSize: TYPE.micro, color: theme.text3, marginLeft: 30, marginTop: 2 }}>{item.detail}</div>
            ) : null}
          </div>
        ))}
      </div>
    </div>
  )
}
