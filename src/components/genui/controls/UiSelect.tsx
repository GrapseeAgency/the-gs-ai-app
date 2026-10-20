'use client'

import { RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

/** A native select skinned to the widget identity — reliable on every device. */
export function UiSelect({
  value,
  options,
  onChange,
  ariaLabel,
}: {
  value: string
  options: string[]
  onChange: (v: string) => void
  ariaLabel?: string
}) {
  const { accent, theme } = useWidgetIdentity()
  return (
    <select
      aria-label={ariaLabel}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      style={{
        width: '100%',
        padding: '9px 12px',
        borderRadius: RADIUS.md,
        border: `1px solid ${theme.border}`,
        background: theme.dark ? 'rgba(0,0,0,0.25)' : '#fff',
        color: theme.text,
        fontSize: TYPE.body,
        appearance: 'none' as const,
        outline: `2px solid transparent`,
        outlineOffset: 1,
      }}
      onFocus={(e) => (e.currentTarget.style.outlineColor = alphaSafe(accent))}
      onBlur={(e) => (e.currentTarget.style.outlineColor = 'transparent')}
    >
      {options.map((o) => (
        <option key={o} value={o}>
          {o}
        </option>
      ))}
    </select>
  )
}

function alphaSafe(hex: string) {
  return `${hex}66`
}
