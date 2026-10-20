'use client'

import { useState } from 'react'
import type { FormFieldPayload, FormPayload } from '../types'
import { alpha, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { TitleRow } from '../primitives/TitleRow'
import { EmptyState } from '../primitives/EmptyState'
import { UiButton } from '../controls/UiButton'
import { UiCheckbox } from '../controls/UiCheckbox'
import { UiChipToggle } from '../controls/UiChipToggle'
import { UiDateField } from '../controls/UiDateField'
import { UiFileField } from '../controls/UiFileField'
import { UiOtpField } from '../controls/UiOtpField'
import { UiRadio } from '../controls/UiRadio'
import { UiRatingInput } from '../controls/UiRatingInput'
import { UiSelect } from '../controls/UiSelect'
import { UiSlider } from '../controls/UiSlider'
import { UiStepper } from '../controls/UiStepper'
import { UiTextArea } from '../controls/UiTextArea'
import { UiTextInput } from '../controls/UiTextInput'
import { UiToggle } from '../controls/UiToggle'

/**
 * Form — every catalogue field type rendered live and held in local state.
 * The submit surfaces a composed summary (and, when wired, sends it back).
 */
export function FormWidget({ payload, onPrompt }: { payload: FormPayload; onPrompt?: (p: string) => void }) {
  const { accent, theme } = useWidgetIdentity()
  const [values, setValues] = useState<Record<string, unknown>>(() =>
    Object.fromEntries((payload.fields ?? []).map((f, i) => [`f${i}`, f.type === 'checkbox' || f.type === 'toggle' ? false : f.type === 'slider' || f.type === 'number' ? (f.value as number) ?? f.min ?? 0 : f.type === 'rating' ? 0 : '']))
  )
  const [submitted, setSubmitted] = useState(false)
  const fields = (payload.fields ?? []).slice(0, 12)
  if (!fields.length) return <EmptyState label="No fields" />
  const set = (key: string) => (v: unknown) => setValues((s) => ({ ...s, [key]: v }))

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault()
        setSubmitted(true)
        if (onPrompt) {
          const summary = fields
            .map((f, i) => `${f.label}: ${String(values[`f${i}`])}`)
            .join(', ')
          onPrompt(`${payload.title ?? 'Form'} — ${summary}`)
        }
      }}
    >
      <TitleRow title={payload.title} />
      <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        {fields.map((f, i) => {
          const key = `f${i}`
          return (
            <div key={key}>
              {f.type !== 'checkbox' && f.type !== 'toggle' && (
                <label style={{ display: 'block', fontSize: TYPE.label, fontWeight: 600, color: theme.text2, marginBottom: 6 }}>
                  {f.label}
                  {f.required ? <span style={{ color: accent }}> *</span> : null}
                </label>
              )}
              {renderField(f, values[key], set(key), onPrompt)}
            </div>
          )
        })}
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginTop: 16 }}>
        <UiButton buttonStyle="filled">{payload.submitLabel ?? 'Submit'}</UiButton>
        {submitted && <span style={{ fontSize: TYPE.label, color: '#0CA678', fontWeight: 600 }}>Sent ✓</span>}
      </div>
    </form>
  )
}

function renderField(f: FormFieldPayload, value: unknown, set: (v: unknown) => void, _onPrompt?: (p: string) => void) {
  switch (f.type) {
    case 'textarea':
      return <UiTextArea placeholder={String(f.value ?? '')} value={String(value ?? '')} onChange={(v) => set(v)} rows={3} />
    case 'select':
      return <UiSelect value={String(value || f.options?.[0] || '')} options={f.options ?? []} onChange={(v) => set(v)} ariaLabel={f.label} />
    case 'radio':
      return (
        <div>
          {(f.options ?? []).map((o) => (
            <UiRadio key={o} label={o} selected={value === o} onSelect={() => set(o)} />
          ))}
        </div>
      )
    case 'checkbox':
      return <UiCheckbox label={f.label} checked={value === true} onChange={(v) => set(v)} />
    case 'toggle':
      return <UiToggle label={f.label} checked={value === true} onChange={(v) => set(v)} />
    case 'slider':
      return <UiSlider label={f.label} value={Number(value) || 0} min={f.min ?? 0} max={f.max ?? 100} step={f.max && f.max - (f.min ?? 0) <= 10 ? 1 : (f.max ?? 100 - (f.min ?? 0)) / 100} unit={f.unit} onChange={(v) => set(v)} />
    case 'segmented':
      return <UiSegmentedSafe options={f.options ?? []} value={String(value || f.options?.[0] || '')} onChange={(v) => set(v)} />
    case 'date':
      return <UiDateField inputType="date" value={String(value ?? '')} onChange={(v) => set(v)} />
    case 'time':
      return <UiDateField inputType="time" value={String(value ?? '')} onChange={(v) => set(v)} />
    case 'number':
      return (
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <UiStepper value={Number(value) || 0} min={f.min} max={f.max} unit={f.unit} onChange={(v) => set(v)} />
        </div>
      )
    case 'email':
      return <UiTextInput inputType="email" inputMode="email" placeholder="name@example.com" value={String(value ?? '')} onChange={(v) => set(v)} />
    case 'url':
      return <UiTextInput inputType="url" inputMode="url" placeholder="https://…" value={String(value ?? '')} onChange={(v) => set(v)} />
    case 'color':
      return <ColorField value={String(value || '#4C6FFF')} onChange={(v) => set(v)} />
    case 'file':
      return <UiFileField label={`Attach — ${f.label}`} />
    case 'rating':
      return <UiRatingInput max={f.max ?? 5} style="star" value={Number(value) || 0} onChange={(v) => set(v)} />
    case 'otp':
      return <UiOtpField length={6} value={String(value ?? '')} onChange={(v) => set(v)} />
    case 'text':
    default:
      return <UiTextInput placeholder={String(f.value ?? '')} value={String(value ?? '')} onChange={(v) => set(v)} />
  }
}

function UiSegmentedSafe({ options, value, onChange }: { options: string[]; value: string; onChange: (v: string) => void }) {
  return <Segmented options={options} value={value} onChange={onChange} />
}

function Segmented({ options, value, onChange }: { options: string[]; value: string; onChange: (v: string) => void }) {
  const { accent, theme } = useWidgetIdentity()
  return (
    <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
      {options.map((o) => (
        <UiChipToggle key={o} label={o} selected={o === value} onToggle={() => onChange(o)} />
      ))}
    </div>
  )
}

function ColorField({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  const { theme } = useWidgetIdentity()
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 10 }}>
      <input
        type="color"
        value={/^#[0-9a-fA-F]{6}$/.test(value) ? value : '#4C6FFF'}
        onChange={(e) => onChange(e.target.value)}
        style={{ width: 40, height: 32, padding: 0, border: `1px solid ${theme.border}`, borderRadius: 8, background: 'transparent', cursor: 'pointer' }}
      />
      <code style={{ fontSize: TYPE.label, color: theme.text2 }}>{value}</code>
    </span>
  )
}
