'use client'

import { useState } from 'react'
import type { AppPayload } from '../types'
import { TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { AppShell } from './AppShell'
import { UiSlider } from '../controls/UiSlider'
import { EmptyState } from '../primitives/EmptyState'

/** Scenario — what-if sliders over a base number. */
export function AppScenario({ payload }: { payload: AppPayload }) {
  const { accent, theme } = useWidgetIdentity()
  const sliders = (payload.groups ?? []).slice(0, 4)
  const [values, setValues] = useState<Record<string, number>>(() => Object.fromEntries(sliders.map((s) => [s.title, 100])))
  if (!sliders.length) return <EmptyState label="No scenario knobs" />
  const base = Number((payload.items ?? [])[0]?.price ?? 1000)
  const total = sliders.reduce((acc, s) => acc * ((values[s.title] ?? 100) / 100), base)
  return (
    <AppShell title={payload.title}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
        {sliders.map((s) => (
          <UiSlider
            key={s.title}
            label={s.title}
            value={values[s.title] ?? 100}
            min={Number((s.options ?? [])[0]?.price ?? 0) || 0}
            max={Number((s.options ?? [])[1]?.price ?? 200) || 200}
            step={5}
            unit="%"
            onChange={(v) => setValues((vs) => ({ ...vs, [s.title]: v }))}
          />
        ))}
      </div>
      <div style={{ marginTop: 14, display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
        <span style={{ fontSize: TYPE.label, color: theme.text2 }}>Projected</span>
        <span style={{ fontSize: TYPE.title, fontWeight: 700, color: accent }}>{Math.round(total).toLocaleString()}</span>
      </div>
    </AppShell>
  )
}
