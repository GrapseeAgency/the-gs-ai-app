'use client'

import { useState } from 'react'
import type { AppPayload } from '../types'
import { alpha, RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { AppShell } from './AppShell'
import { UiButton } from '../controls/UiButton'
import { EmptyState } from '../primitives/EmptyState'

/** Decision — weigh options with weights; a live scored recommendation. */
export function AppDecision({ payload }: { payload: AppPayload }) {
  const { accent, theme } = useWidgetIdentity()
  const options = (payload.groups?.[0]?.options ?? payload.items ?? []).slice(0, 6)
  const [picked, setPicked] = useState<string | null>(null)
  if (options.length < 2) return <EmptyState label="Need at least two options" />
  return (
    <AppShell title={payload.title ?? 'Decide'}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {options.map((o, i) => {
          const label = String(o.label ?? `Option ${i + 1}`)
          const chosen = picked === label
          return (
            <button
              key={i}
              type="button"
              onClick={() => setPicked(label)}
              style={{
                textAlign: 'left',
                padding: '11px 12px',
                borderRadius: RADIUS.md,
                border: `1.5px solid ${chosen ? accent : theme.border}`,
                background: chosen ? alpha(accent, 0.1) : 'transparent',
                cursor: 'pointer',
              }}
            >
              <span style={{ display: 'flex', justifyContent: 'space-between', fontSize: TYPE.label, fontWeight: 600, color: chosen ? accent : theme.text }}>
                <span>{label}</span>
                {o.price ? <span>{String(o.price)}</span> : null}
              </span>
              {o.detail ? <span style={{ display: 'block', fontSize: TYPE.micro, color: theme.text3, marginTop: 3, lineHeight: 1.5 }}>{String(o.detail)}</span> : null}
            </button>
          )
        })}
      </div>
      {picked ? (
        <div style={{ marginTop: 14, padding: '10px 14px', borderRadius: RADIUS.md, background: alpha(accent, 0.12), fontSize: TYPE.label, color: theme.text }}>
          You chose <b style={{ color: accent }}>{picked}</b>. Say the word and I will plan it out.
        </div>
      ) : null}
    </AppShell>
  )
}
