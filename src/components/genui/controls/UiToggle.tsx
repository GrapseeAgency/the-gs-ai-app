'use client'

import { alpha, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

export function UiToggle({
  label,
  checked,
  onChange,
}: {
  label?: string
  checked: boolean
  onChange: (v: boolean) => void
}) {
  const { accent, theme } = useWidgetIdentity()
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      onClick={() => onChange(!checked)}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 10,
        background: 'transparent',
        border: 'none',
        padding: 0,
        cursor: 'pointer',
      }}
    >
      <span
        style={{
          width: 40,
          height: 22,
          borderRadius: 999,
          padding: 2,
          background: checked ? accent : theme.raised,
          border: `1px solid ${checked ? accent : theme.border}`,
          display: 'inline-flex',
          justifyContent: checked ? 'flex-end' : 'flex-start',
          transition: 'background .18s ease',
        }}
      >
        <span
          style={{
            width: 16,
            height: 16,
            borderRadius: 999,
            background: checked ? (theme.dark ? '#0F1216' : '#fff') : theme.text3,
          }}
        />
      </span>
      {label ? <span style={{ fontSize: TYPE.body, color: theme.text }}>{label}</span> : null}
    </button>
  )
}
