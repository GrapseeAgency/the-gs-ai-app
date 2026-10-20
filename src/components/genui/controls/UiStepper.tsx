'use client'

import { RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

/** A numeric stepper — minus / value / plus. */
export function UiStepper({
  value,
  onChange,
  min = 0,
  max = 99,
  unit,
}: {
  value: number
  onChange: (v: number) => void
  min?: number
  max?: number
  unit?: string
}) {
  const { accent, theme } = useWidgetIdentity()
  const round = (n: number) => Math.min(max, Math.max(min, n))
  const btn = (label: string, delta: number): React.ReactElement => (
    <button
      type="button"
      onClick={() => onChange(round(value + delta))}
      style={{
        width: 30,
        height: 30,
        borderRadius: RADIUS.md,
        border: `1px solid ${theme.border}`,
        background: 'transparent',
        color: accent,
        fontSize: 17,
        fontWeight: 700,
        cursor: 'pointer',
        lineHeight: 1,
      }}
    >
      {label}
    </button>
  )
  return (
    <div style={{ display: 'inline-flex', alignItems: 'center', gap: 10 }}>
      {btn('−', -1)}
      <span style={{ minWidth: 44, textAlign: 'center', fontSize: TYPE.body, fontWeight: 700, color: theme.text }}>
        {value}
        {unit ? <span style={{ color: theme.text2, fontWeight: 400 }}> {unit}</span> : null}
      </span>
      {btn('+', 1)}
    </div>
  )
}
