'use client'

import { useState } from 'react'
import type { AppPayload } from '../types'
import { TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { AppShell } from './AppShell'
import { EmptyState } from '../primitives/EmptyState'
import { UiToggle } from '../controls/UiToggle'
import { UiSelect } from '../controls/UiSelect'

/** Config — a settings panel of toggles and selects. */
export function AppConfig({ payload }: { payload: AppPayload }) {
  const { theme } = useWidgetIdentity()
  const groups = (payload.groups ?? []).slice(0, 6)
  const [state, setState] = useState<Record<string, boolean>>(() =>
    Object.fromEntries(groups.flatMap((g) => (g.options ?? []).map((o, oi) => [`${g.title}-${oi}`, oi % 2 === 0])))
  )
  if (!groups.length) return <EmptyState label="No settings" />
  return (
    <AppShell title={payload.title}>
      {groups.map((g) => (
        <div key={g.title} style={{ marginBottom: 14 }}>
          <div style={{ fontSize: TYPE.micro, fontWeight: 700, color: theme.text3, textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 8 }}>{g.title}</div>
          {(g.options ?? []).map((o, oi) => (
            <div key={oi} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 10, padding: '8px 0' }}>
              <span>
                <span style={{ display: 'block', fontSize: TYPE.label, color: theme.text }}>{String(o.label ?? '')}</span>
                {o.detail ? <span style={{ display: 'block', fontSize: TYPE.micro, color: theme.text3, marginTop: 1 }}>{String(o.detail)}</span> : null}
              </span>
              <UiToggle checked={state[`${g.title}-${oi}`] === true} onChange={(v) => setState((s) => ({ ...s, [`${g.title}-${oi}`]: v }))} />
            </div>
          ))}
        </div>
      ))}
    </AppShell>
  )
}
