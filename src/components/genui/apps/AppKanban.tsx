'use client'

import { useState } from 'react'
import type { AppPayload } from '../types'
import { RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { AppShell } from './AppShell'
import { EmptyState } from '../primitives/EmptyState'

/** Kanban — tap a card to advance it across the columns. */
export function AppKanban({ payload }: { payload: AppPayload }) {
  const { accent, theme } = useWidgetIdentity()
  const columns = (payload.columns ?? []).slice(0, 4).map((rawCol, idx) => {
    const c = (rawCol && typeof rawCol === 'object' ? rawCol : {}) as Record<string, unknown>
    const title = String(c.title ?? c.name ?? c.label ?? c.status ?? `Column ${idx + 1}`)
    const rawItems = Array.isArray(c.items ?? c.cards ?? c.tasks) ? (c.items ?? c.cards ?? c.tasks) as unknown[] : []
    const items = rawItems.map((it) => {
      if (typeof it === 'string') return { title: it, subtitle: undefined as unknown, badge: undefined as unknown }
      const o = (it && typeof it === 'object' ? it : {}) as Record<string, unknown>
      return {
        ...o,
        title: String(o.title ?? o.label ?? o.text ?? o.task ?? o.name ?? ''),
        subtitle: o.subtitle ?? o.description ?? o.assignee ?? o.detail,
        badge: o.badge ?? o.priority ?? o.tag ?? o.status,
      }
    })
    return { title, items }
  })
  const [lanes, setLanes] = useState(() => columns.map((c) => c.items.map((it) => ({ ...it }))))
  if (!columns.length) return <EmptyState label="No columns" />
  const advance = (col: number, item: number) => {
    if (col >= lanes.length - 1) return
    setLanes((ls) => {
      const next = ls.map((l) => [...l])
      const [moved] = next[col].splice(item, 1)
      if (moved) next[col + 1].push(moved)
      return next
    })
  }
  return (
    <AppShell title={payload.title}>
      <div style={{ display: 'flex', gap: 8, overflowX: 'auto', paddingBottom: 4 }}>
        {columns.map((c, ci) => (
          <div key={c.title} style={{ flex: 1, minWidth: 118 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 8 }}>
              <span style={{ fontSize: TYPE.micro, fontWeight: 700, color: theme.text2, textTransform: 'uppercase', letterSpacing: '0.04em' }}>{c.title}</span>
              <span style={{ fontSize: TYPE.micro, color: theme.text3, background: theme.dark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.06)', borderRadius: 999, padding: '1px 7px' }}>{lanes[ci]?.length ?? 0}</span>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6, minHeight: 40 }}>
              {(lanes[ci] ?? []).map((item, ii) => (
                <button
                  key={`${ci}-${ii}`}
                  type="button"
                  onClick={() => advance(ci, ii)}
                  title={ci < columns.length - 1 ? 'Tap to advance' : 'Done'}
                  style={{
                    textAlign: 'left',
                    width: '100%',
                    padding: '9px 10px',
                    borderRadius: RADIUS.md,
                    border: `1px solid ${theme.border}`,
                    background: theme.dark ? 'rgba(255,255,255,0.04)' : '#fff',
                    cursor: 'pointer',
                  }}
                >
                  <span style={{ display: 'block', fontSize: TYPE.micro, fontWeight: 700, color: theme.text }}>{String(item.title ?? '')}</span>
                  {item.subtitle ? <span style={{ display: 'block', fontSize: TYPE.micro, color: theme.text3, marginTop: 2 }}>{String(item.subtitle)}</span> : null}
                  {item.badge ? <span style={{ display: 'inline-block', fontSize: 10, fontWeight: 700, color: accent, marginTop: 4 }}>{String(item.badge)}</span> : null}
                </button>
              ))}
            </div>
          </div>
        ))}
      </div>
    </AppShell>
  )
}
