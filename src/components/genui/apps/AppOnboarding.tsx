'use client'

import { useState } from 'react'
import type { AppPayload } from '../types'
import { alpha, RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { AppShell } from './AppShell'
import { UiButton } from '../controls/UiButton'

/** Onboarding — swipeable welcome steps ending in a single CTA. */
export function AppOnboarding({ payload }: { payload: AppPayload }) {
  const { accent, theme } = useWidgetIdentity()
  const steps = (payload.steps ?? []).slice(0, 8)
  const [i, setI] = useState(0)
  if (!steps.length) return null
  const step = steps[Math.min(i, steps.length - 1)]
  const last = i === steps.length - 1
  return (
    <AppShell submitLabel={last ? (payload.submitLabel ?? 'Get started') : undefined}>
      <div
        style={{
          minHeight: 150,
          borderRadius: RADIUS.xl,
          background: `linear-gradient(150deg, ${alpha(accent, theme.dark ? 0.22 : 0.14)}, transparent)`,
          border: `1px solid ${alpha(accent, 0.25)}`,
          padding: 20,
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          gap: 8,
        }}
      >
        <div style={{ fontSize: TYPE.title, fontWeight: 700, color: theme.text }}>{String(step.title ?? '')}</div>
        <div style={{ fontSize: TYPE.body, color: theme.text2, lineHeight: 1.6 }}>{String(step.body ?? '')}</div>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: 14 }}>
        <div style={{ display: 'flex', gap: 5 }}>
          {steps.map((_, d) => (
            <span key={d} style={{ width: d === i ? 18 : 6, height: 6, borderRadius: 3, background: d === i ? accent : theme.dark ? 'rgba(255,255,255,0.14)' : 'rgba(0,0,0,0.12)', transition: 'width .25s ease' }} />
          ))}
        </div>
        {!last ? <UiButton buttonStyle="filled" onClick={() => setI((v) => v + 1)}>Next</UiButton> : null}
      </div>
    </AppShell>
  )
}
