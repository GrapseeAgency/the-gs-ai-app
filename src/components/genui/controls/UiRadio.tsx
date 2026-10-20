'use client'

import { alpha, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

export function UiRadio({
  label,
  selected,
  onSelect,
}: {
  label: string
  selected: boolean
  onSelect: () => void
}) {
  const { accent, theme } = useWidgetIdentity()
  return (
    <button
      type="button"
      role="radio"
      aria-checked={selected}
      onClick={onSelect}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        width: '100%',
        textAlign: 'left',
        padding: '9px 12px',
        borderRadius: 12,
        border: `1px solid ${selected ? alpha(accent, 0.55) : theme.border}`,
        background: selected ? alpha(accent, theme.dark ? 0.14 : 0.08) : 'transparent',
        cursor: 'pointer',
        fontSize: TYPE.body,
        color: theme.text,
        marginBottom: 6,
      }}
    >
      <span
        style={{
          width: 18,
          height: 18,
          borderRadius: 999,
          flexShrink: 0,
          border: `2px solid ${selected ? accent : theme.border}`,
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        {selected && <span style={{ width: 8, height: 8, borderRadius: 999, background: accent }} />}
      </span>
      {label}
    </button>
  )
}
