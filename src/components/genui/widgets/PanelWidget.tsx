'use client'

import { useState } from 'react'
import type { PanelPayload } from '../types'
import { alpha, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { Chip } from '../primitives/Chip'

/** Panel — expandable section with badges and an optional popover reveal. */
export function PanelWidget({ payload }: { payload: PanelPayload }) {
  const { accent, theme } = useWidgetIdentity()
  const [open, setOpen] = useState(payload.expanded === true)
  const [more, setMore] = useState(false)
  const toneMap: Record<string, 'accent' | 'success' | 'warning' | 'danger'> = {
    accent: 'accent',
    success: 'success',
    warning: 'warning',
    danger: 'danger',
  }
  return (
    <div>
      <button
        type="button"
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
        style={{
          width: '100%',
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          background: 'transparent',
          border: 'none',
          padding: 0,
          cursor: 'pointer',
          textAlign: 'left',
        }}
      >
        <span
          style={{
            width: 22,
            height: 22,
            borderRadius: 999,
            background: alpha(accent, theme.dark ? 0.18 : 0.12),
            color: accent,
            fontSize: 12,
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontWeight: 700,
            flexShrink: 0,
            transition: 'transform .2s ease',
            transform: open ? 'rotate(90deg)' : 'none',
          }}
        >
          ▸
        </span>
        <span style={{ flex: 1, minWidth: 0 }}>
          <span style={{ display: 'block', fontSize: TYPE.heading, fontWeight: 600, color: theme.text }}>
            {payload.title}
            {payload.summary ? <span style={{ fontWeight: 400, color: theme.text2 }}> — {payload.summary}</span> : null}
          </span>
        </span>
      </button>
      {(payload.badges ?? []).length > 0 && (
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', margin: '8px 0 0 32px' }}>
          {(payload.badges ?? []).map((b, i) => (
            <Chip key={i} tone={toneMap[b.tone ?? 'accent'] ?? 'accent'}>
              {b.label}
            </Chip>
          ))}
        </div>
      )}
      {open && payload.body ? (
        <div style={{ margin: '10px 0 0 32px', fontSize: TYPE.body, color: theme.text, lineHeight: 1.6 }}>{payload.body}</div>
      ) : null}
      {open && payload.popover ? (
        <div style={{ margin: '8px 0 0 32px' }}>
          <button
            type="button"
            onClick={() => setMore((m) => !m)}
            style={{
              background: 'transparent',
              border: 'none',
              color: accent,
              fontSize: TYPE.label,
              fontWeight: 600,
              cursor: 'pointer',
              padding: 0,
            }}
          >
            {more ? 'Hide details' : 'More info'}
          </button>
          {more && <div style={{ fontSize: TYPE.label, color: theme.text2, lineHeight: 1.6, marginTop: 6 }}>{payload.popover}</div>}
        </div>
      ) : null}
    </div>
  )
}
