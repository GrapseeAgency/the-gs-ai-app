'use client'

import type { ComparePayload } from '../types'
import { alpha, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { TitleRow } from '../primitives/TitleRow'
import { EmptyState } from '../primitives/EmptyState'

/** Compare — two columns side by side, centre rule, accent headers. */
export function CompareWidget({ payload }: { payload: ComparePayload }) {
  const { accent, theme } = useWidgetIdentity()
  if (!payload.left?.items?.length && !payload.right?.items?.length) return <EmptyState label="Nothing to compare" />
  const side = (title: string, items: string[], filled: boolean) => (
    <div style={{ flex: 1, minWidth: 0 }}>
      <div
        style={{
          padding: '7px 10px',
          borderRadius: 10,
          textAlign: 'center',
          fontSize: TYPE.label,
          fontWeight: 700,
          marginBottom: 8,
          background: filled ? accent : alpha(theme.dark ? '#FFFFFF' : '#14171C', 0.05),
          color: filled ? theme.dark ? '#0F1216' : '#fff' : theme.text2,
        }}
      >
        {title}
      </div>
      {items.map((item, i) => (
        <div key={i} style={{ fontSize: TYPE.body, color: theme.text, padding: '7px 2px', borderBottom: `1px solid ${alpha(theme.dark ? '#FFFFFF' : '#14171C', 0.06)}` }}>
          {item}
        </div>
      ))}
    </div>
  )
  return (
    <div>
      <TitleRow title={payload.title} />
      <div style={{ display: 'flex', gap: 12 }}>
        {side(payload.left?.title ?? 'Left', payload.left?.items ?? [], false)}
        <div style={{ width: 1, background: theme.border, alignSelf: 'stretch' }} />
        {side(payload.right?.title ?? 'Right', payload.right?.items ?? [], true)}
      </div>
    </div>
  )
}
