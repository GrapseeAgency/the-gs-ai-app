'use client'

import { useMemo, useState } from 'react'
import type { AppPayload } from '../types'
import { alpha, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { AppShell } from './AppShell'
import { UiRadio } from '../controls/UiRadio'
import { formatNumber } from '../controls/UiSlider'
import { EmptyState } from '../primitives/EmptyState'

/** Configurator — priced option groups with a live total. */
export function AppConfigurator({ payload }: { payload: AppPayload }) {
  const { accent, theme } = useWidgetIdentity()
  const groups = (payload.groups ?? []).slice(0, 5)
  const [picks, setPicks] = useState<Record<string, number>>(() => Object.fromEntries(groups.map((g) => [g.title, 0])))
  if (!groups.length) return <EmptyState label="No options" />
  const total = useMemo(
    () => groups.reduce((sum, g) => sum + (Number((g.options ?? [])[picks[g.title] ?? 0]?.price) || 0), 0),
    [groups, picks]
  )
  return (
    <AppShell title={payload.title}>
      {groups.map((g) => (
        <div key={g.title} style={{ marginBottom: 14 }}>
          <div style={{ fontSize: TYPE.micro, fontWeight: 700, color: theme.text3, textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 8 }}>{g.title}</div>
          {(g.options ?? []).map((o, oi) => (
            <UiRadio
              key={oi}
              label={`${o.label}${o.detail ? ` — ${o.detail}` : ''}${o.price ? ` (+${formatNumber(Number(o.price))})` : ''}`}
              selected={picks[g.title] === oi}
              onSelect={() => setPicks((p) => ({ ...p, [g.title]: oi }))}
            />
          ))}
        </div>
      ))}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'baseline',
          padding: '12px 14px',
          borderRadius: 14,
          background: alpha(accent, theme.dark ? 0.13 : 0.08),
          border: `1px solid ${alpha(accent, 0.3)}`,
        }}
      >
        <span style={{ fontSize: TYPE.label, fontWeight: 600, color: theme.text2 }}>Total</span>
        <span style={{ fontSize: TYPE.title, fontWeight: 700, color: accent }}>${formatNumber(total)}</span>
      </div>
    </AppShell>
  )
}
