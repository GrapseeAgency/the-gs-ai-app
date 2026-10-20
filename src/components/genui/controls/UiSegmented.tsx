'use client'

import { alpha, inkOn, RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

/** Segmented control — exclusive choice, always visible. */
export function UiSegmented({
  options,
  value,
  onChange,
}: {
  options: Array<string | { label: string; value: string }>
  value: string
  onChange: (v: string) => void
}) {
  const { accent, theme } = useWidgetIdentity()
  const norm = options.map((o) => (typeof o === 'string' ? { label: o, value: o } : o))
  return (
    <div
      role="radiogroup"
      style={{
        display: 'inline-flex',
        padding: 3,
        gap: 2,
        borderRadius: RADIUS.pill,
        background: theme.dark ? 'rgba(0,0,0,0.3)' : 'rgba(0,0,0,0.05)',
      }}
    >
      {norm.map((o) => {
        const active = o.value === value
        return (
          <button
            key={o.value}
            type="button"
            role="radio"
            aria-checked={active}
            onClick={() => onChange(o.value)}
            style={{
              border: 'none',
              padding: '5px 14px',
              borderRadius: RADIUS.pill,
              fontSize: TYPE.label,
              fontWeight: 600,
              cursor: 'pointer',
              background: active ? accent : 'transparent',
              color: active ? inkOn(accent) : theme.text2,
            }}
          >
            {o.label}
          </button>
        )
      })}
    </div>
  )
}
