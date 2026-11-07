'use client'

import { GlassIcon } from '@/components/nexly/GlassIcon'

import { alpha, RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

export function UiCheckbox({
  label,
  ariaLabel,
  checked,
  onChange,
}: {
  label?: string
  ariaLabel?: string
  checked: boolean
  onChange: (v: boolean) => void
}) {
  const { accent, theme } = useWidgetIdentity()
  return (
    <button
      type="button"
      role="checkbox"
      aria-checked={checked}
      aria-label={ariaLabel}
      onClick={() => onChange(!checked)}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 10,
        background: 'transparent',
        border: 'none',
        padding: 0,
        cursor: 'pointer',
        textAlign: 'left',
      }}
    >
      <span
        style={{
          width: 20,
          height: 20,
          borderRadius: 6,
          flexShrink: 0,
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: checked ? accent : 'transparent',
          border: `2px solid ${checked ? accent : theme.border}`,
          color: checked ? (theme.dark ? '#0F1216' : '#fff') : 'transparent',
          fontSize: 13,
          fontWeight: 700,
          lineHeight: 1,
        }}
      >
        {checked && <GlassIcon name="check" size={12} tile={16} />}
      </span>
      {label ? <span style={{ fontSize: TYPE.body, color: theme.text }}>{label}</span> : null}
    </button>
  )
}
