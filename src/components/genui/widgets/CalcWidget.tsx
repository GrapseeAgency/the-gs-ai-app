'use client'

import { useState } from 'react'
import type { CalcPayload } from '../types'
import { alpha, RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { TitleRow } from '../primitives/TitleRow'
import { EmptyState } from '../primitives/EmptyState'
import { evaluateExpr, formatOutput } from '../calc/evaluate'
import { UiSlider } from '../controls/UiSlider'
import { UiSegmented } from '../controls/UiSegmented'


/** Calc — live parameter-driven calculator; expr outputs recompute on input. */
export function CalcWidget({ payload }: { payload: CalcPayload }) {
  // A new model payload is a new calculator, not stale state from the old one.
  return <CalculatorControls key={JSON.stringify(payload)} payload={payload} />
}

function CalculatorControls({ payload }: { payload: CalcPayload }) {
  const { accent, theme } = useWidgetIdentity()
  const fields = (payload.fields ?? []).filter((f) => f.key && f.label)
  const outputs = (payload.outputs ?? []).filter((o) => o.label && o.expr)
  const defaults = Object.fromEntries(fields.map((f) => [f.key,
    f.value ?? f.choices?.[0]?.value ?? NaN]))
  const [values, setValues] = useState<Record<string, number>>(defaults)
  if (!fields.length || !outputs.length) return <EmptyState label="Calculator misconfigured" />
  const set = (key: string, v: number) => setValues((s) => ({ ...s, [key]: v }))
  const invalid = fields.some((f) => !Number.isFinite(values[f.key]) ||
    (f.min != null && values[f.key] < f.min) || (f.max != null && values[f.key] > f.max))
  const hasChanged = fields.some((f) => !Object.is(values[f.key], defaults[f.key]))
  const results = outputs.map((o) => {
    try {
      if (invalid) throw new Error('Enter valid numbers within the supplied limits')
      const value = evaluateExpr(o.expr, values)
      if (!Number.isFinite(value)) throw new Error('Undefined for these inputs')
      return { label: o.label, text: formatOutput(value, o.format, o.unit), primary: o.primary === true, error: false }
    } catch {
      return { label: o.label, text: invalid ? 'Enter valid inputs' : 'Undefined for these inputs', primary: o.primary === true, error: true }
    }
  })
  return (
    <div>
      <TitleRow title={payload.title} />
      {(payload.presets ?? []).length > 0 && (
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 12 }}>
          {(payload.presets ?? []).map((preset) => (
            <button
              key={preset.label}
              type="button"
              onClick={() => setValues((s) => ({ ...s, ...Object.fromEntries(
                Object.entries(preset.values).filter(([key, value]) => key in defaults && Number.isFinite(value))
              ) }))}
              style={{
                padding: '5px 12px',
                borderRadius: 999,
                border: `1px solid ${theme.border}`,
                background: 'transparent',
                color: theme.text2,
                fontSize: TYPE.micro,
                fontWeight: 600,
                cursor: 'pointer',
              }}
            >
              {preset.label}
            </button>
          ))}
        </div>
      )}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        {fields.map((f) =>
          f.choices?.length ? (
            <div key={f.key} style={{ padding: '4px 0' }}>
              <div style={{ fontSize: TYPE.label, color: theme.text2, fontWeight: 600, marginBottom: 6 }}>{f.label}</div>
              <UiSegmented options={f.choices.map((c) => ({ label: c.label, value: String(c.value) }))} value={String(values[f.key])} onChange={(v) => set(f.key, Number(v))} />
            </div>
          ) : (
            <div key={f.key} style={{ padding: '6px 0' }}>
              <label style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12 }}>
                <span style={{ color: theme.text2, fontSize: TYPE.label }}>{f.label}{f.unit ? ` (${f.unit})` : ''}</span>
                <input
                  type="number"
                  aria-label={f.label}
                  aria-invalid={!Number.isFinite(values[f.key]) || (f.min != null && values[f.key] < f.min) || (f.max != null && values[f.key] > f.max)}
                  value={Number.isFinite(values[f.key]) ? values[f.key] : ''}
                  min={f.min ?? undefined}
                  max={f.max ?? undefined}
                  step={f.step ?? 'any'}
                  onChange={(e) => set(f.key, e.target.value === '' ? NaN : Number(e.target.value))}
                  style={{ width: 120, padding: '6px 8px', borderRadius: 8, border: `1px solid ${theme.border}`, background: theme.raised, color: theme.text }}
                />
              </label>
              {f.min != null && f.max != null && Number.isFinite(f.min) && Number.isFinite(f.max) && f.max > f.min && (
                <UiSlider
                  label={f.label}
                  value={Number.isFinite(values[f.key]) ? Math.min(f.max, Math.max(f.min, values[f.key])) : f.min}
                  min={f.min}
                  max={f.max}
                  step={f.step ?? (f.max - f.min) / 100}
                  unit={f.unit}
                  onChange={(v) => set(f.key, v)}
                />
              )}
            </div>
          )
        )}
      </div>
      {hasChanged && (
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 6 }}>
          <button
            type="button"
            onClick={() => setValues(defaults)}
            style={{
              padding: '4px 10px',
              borderRadius: 999,
              border: `1px solid ${theme.border}`,
              background: 'transparent',
              color: accent,
              fontSize: TYPE.micro,
              fontWeight: 600,
              cursor: 'pointer',
            }}
          >
            Reset
          </button>
        </div>
      )}
      <div style={{ marginTop: 14, display: 'flex', flexDirection: 'column', gap: 8 }}>
        {results.map((r, i) => (
          <div
            key={i}
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'baseline',
              padding: r.primary ? '12px 14px' : '8px 2px',
              borderRadius: r.primary ? RADIUS.lg : 0,
              background: r.primary ? alpha(accent, theme.dark ? 0.14 : 0.08) : 'transparent',
              border: r.primary ? `1px solid ${alpha(accent, 0.3)}` : 'none',
            }}
          >
            <span style={{ fontSize: r.primary ? TYPE.label : TYPE.micro, color: theme.text2, fontWeight: 600 }}>{r.label}</span>
            <span style={{ fontSize: r.primary ? TYPE.title : TYPE.body, fontWeight: 700, color: r.error ? '#E03131' : r.primary ? accent : theme.text, fontVariantNumeric: 'tabular-nums' }}>
              {r.text}
            </span>
          </div>
        ))}
      </div>
      {payload.note ? <div style={{ fontSize: TYPE.micro, color: theme.text3, marginTop: 10, lineHeight: 1.5 }}>{payload.note}</div> : null}
    </div>
  )
}
