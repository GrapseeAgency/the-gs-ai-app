'use client'

import { useMemo, useState } from 'react'
import type { CalcPayload } from '../types'
import { alpha, RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { TitleRow } from '../primitives/TitleRow'
import { EmptyState } from '../primitives/EmptyState'
import { evaluateExpr, formatOutput } from '../calc/evaluate'
import { UiSlider } from '../controls/UiSlider'
import { UiSegmented } from '../controls/UiSegmented'
import { formatNumber } from '../controls/UiSlider'

/** Calc — live parameter-driven calculator; expr outputs recompute on input. */
export function CalcWidget({ payload }: { payload: CalcPayload }) {
  const { accent, theme } = useWidgetIdentity()
  const fields = (payload.fields ?? []).filter((f) => f.key && f.label)
  const outputs = (payload.outputs ?? []).filter((o) => o.label && o.expr)
  const [values, setValues] = useState<Record<string, number>>(() =>
    Object.fromEntries(fields.map((f) => [f.key, f.choices?.length ? f.choices[0].value : f.value ?? f.min ?? 0]))
  )
  if (!fields.length || !outputs.length) return <EmptyState label="Calculator misconfigured" />
  const set = (key: string, v: number) => setValues((s) => ({ ...s, [key]: v }))
  const results = useMemo(() => {
    const out: Array<{ label: string; text: string; primary: boolean; error?: boolean }> = []
    for (const o of outputs) {
      try {
        out.push({ label: o.label, text: formatOutput(evaluateExpr(o.expr, values), o.format, o.unit), primary: o.primary === true })
      } catch {
        out.push({ label: o.label, text: '—', primary: o.primary === true, error: true })
      }
    }
    return out
  }, [outputs, values])
  return (
    <div>
      <TitleRow title={payload.title} />
      {(payload.presets ?? []).length > 0 && (
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 12 }}>
          {(payload.presets ?? []).map((preset) => (
            <button
              key={preset.label}
              type="button"
              onClick={() => setValues((s) => ({ ...s, ...preset.values }))}
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
            <UiSlider
              key={f.key}
              label={f.label}
              value={values[f.key] ?? 0}
              min={f.min ?? 0}
              max={f.max ?? 100}
              step={f.step ?? Math.max(1, ((f.max ?? 100) - (f.min ?? 0)) / 100)}
              unit={f.unit}
              onChange={(v) => set(f.key, v)}
            />
          )
        )}
      </div>
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
