'use client'

import { useState } from 'react'
import type { AppPayload } from '../types'
import { alpha, inkOn, RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { AppShell } from './AppShell'
import { UiButton } from '../controls/UiButton'
import { UiTextInput } from '../controls/UiTextInput'
import { UiTextArea } from '../controls/UiTextArea'
import { UiSelect } from '../controls/UiSelect'
import { UiRadio } from '../controls/UiRadio'
import { UiSlider } from '../controls/UiSlider'

/** Wizard — multi-step flow with per-step fields and a progress rail. */
export function AppWizard({ payload }: { payload: AppPayload }) {
  const { accent, theme } = useWidgetIdentity()
  const steps = (payload.steps ?? []).slice(0, 12)
  const [step, setStep] = useState(0)
  const [answers, setAnswers] = useState<Record<string, string>>({})
  if (!steps.length) return null
  const current = steps[Math.min(step, steps.length - 1)]
  const last = step === steps.length - 1
  const field = (sf: Record<string, unknown>, i: number) => {
    const type = String(sf.type ?? 'text')
    const label = String(sf.label ?? `Field ${i + 1}`)
    const key = `${step}-${i}`
    const value = answers[key] ?? ''
    const set = (v: string) => setAnswers((a) => ({ ...a, [key]: v }))
    if (type === 'textarea') return <UiTextArea key={key} placeholder={label} value={value} onChange={set} />
    if (type === 'select') return <UiSelect key={key} ariaLabel={label} value={value || String((sf.options as string[])?.[0] ?? '')} options={(sf.options as string[]) ?? []} onChange={set} />
    if (type === 'radio')
      return (
        <div key={key}>
          {((sf.options as string[]) ?? []).map((o) => (
            <UiRadio key={o} label={o} selected={value === o} onSelect={() => set(o)} />
          ))}
        </div>
      )
    if (type === 'slider') return <UiSlider key={key} label={label} value={Number(value) || 50} onChange={(v) => set(String(v))} />
    return <UiTextInput key={key} placeholder={label} value={value} onChange={set} />
  }
  return (
    <AppShell title={payload.title} right={`Step ${step + 1} of ${steps.length}`} submitLabel={last ? (payload.submitLabel ?? 'Finish') : undefined}>
      <div style={{ display: 'flex', gap: 4, marginBottom: 14 }}>
        {steps.map((_, i) => (
          <span key={i} style={{ flex: 1, height: 4, borderRadius: 2, background: i <= step ? accent : theme.dark ? 'rgba(255,255,255,0.09)' : 'rgba(0,0,0,0.08)', transition: 'background .3s ease' }} />
        ))}
      </div>
      <div style={{ fontSize: TYPE.body, fontWeight: 700, color: theme.text, marginBottom: 4 }}>{String(current.title ?? `Step ${step + 1}`)}</div>
      {current.body ? (
        <div style={{ fontSize: TYPE.label, color: theme.text2, lineHeight: 1.55, marginBottom: 12 }}>{String(current.body)}</div>
      ) : null}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {((current.fields as Array<Record<string, unknown>>) ?? []).map(field)}
      </div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 16 }}>
        <UiButton disabled={step === 0} onClick={() => setStep((s) => Math.max(0, s - 1))}>
          Back
        </UiButton>
        {!last && (
          <UiButton buttonStyle="filled" onClick={() => setStep((s) => Math.min(steps.length - 1, s + 1))}>
            Continue
          </UiButton>
        )}
      </div>
      {last && Object.keys(answers).length > 0 && (
        <div style={{ marginTop: 12, fontSize: TYPE.micro, color: theme.text3 }}>
          {Object.entries(answers).length} answer(s) captured
        </div>
      )}
    </AppShell>
  )
}
