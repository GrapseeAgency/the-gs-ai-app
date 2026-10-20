'use client'

import { useState } from 'react'
import type { AppPayload } from '../types'
import { TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { AppShell } from './AppShell'
import { EmptyState } from '../primitives/EmptyState'
import { UiCheckbox } from '../controls/UiCheckbox'

/** Tasks — a personal task list with done state. */
export function AppTasks({ payload }: { payload: AppPayload }) {
  const { theme } = useWidgetIdentity()
  const [items, setItems] = useState(() => (payload.items ?? []).map((it) => ({ title: String(it.title ?? ''), meta: String(it.meta ?? ''), done: false })))
  if (!items.length) return <EmptyState label="No tasks" />
  const left = items.filter((i) => !i.done).length
  return (
    <AppShell title={payload.title} right={`${left} left`}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        {items.map((item, i) => (
          <div key={i} style={{ padding: '7px 0', borderBottom: `1px solid ${theme.dark ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.04)'}` }}>
            <UiCheckbox
              label={item.title}
              checked={item.done}
              onChange={(v) => setItems((s) => s.map((it, ii) => (ii === i ? { ...it, done: v } : it)))}
            />
            {item.meta ? <div style={{ fontSize: TYPE.micro, color: theme.text3, marginLeft: 30, marginTop: 2 }}>{item.meta}</div> : null}
          </div>
        ))}
      </div>
    </AppShell>
  )
}
