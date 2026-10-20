'use client'

import type { ReactNode } from 'react'
import { TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

/** Shared app chrome: title + optional submit row. */
export function AppShell({ title, right, children, submitLabel }: { title?: string; right?: string; children: ReactNode; submitLabel?: string }) {
  const { accent, theme } = useWidgetIdentity()
  return (
    <div>
      {title ? (
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 12 }}>
          <span style={{ fontSize: TYPE.heading, fontWeight: 700, color: theme.text }}>{title}</span>
          {right ? <span style={{ fontSize: TYPE.micro, color: theme.text3 }}>{right}</span> : null}
        </div>
      ) : null}
      {children}
      {submitLabel ? (
        <div style={{ marginTop: 14 }}>
          <button
            type="button"
            style={{
              width: '100%',
              padding: '11px 0',
              borderRadius: 999,
              border: 'none',
              background: accent,
              color: theme.dark ? '#0F1216' : '#fff',
              fontSize: TYPE.label,
              fontWeight: 700,
              cursor: 'pointer',
            }}
          >
            {submitLabel}
          </button>
        </div>
      ) : null}
    </div>
  )
}
