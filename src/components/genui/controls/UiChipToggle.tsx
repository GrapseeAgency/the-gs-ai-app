'use client'

import { alpha, inkOn, RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

/** A toggleable filter chip — the multi/single-select surface. */
export function UiChipToggle({
  label,
  selected,
  onToggle,
}: {
  label: string
  selected: boolean
  onToggle: () => void
}) {
  const { accent, theme } = useWidgetIdentity()
  return (
    <button
      type="button"
      aria-pressed={selected}
      onClick={onToggle}
      style={{
        padding: '7px 14px',
        borderRadius: RADIUS.pill,
        fontSize: TYPE.label,
        fontWeight: 600,
        cursor: 'pointer',
        background: selected ? accent : 'transparent',
        color: selected ? inkOn(accent) : theme.text2,
        border: `1px solid ${selected ? accent : alpha(theme.dark ? '#FFFFFF' : '#14171C', 0.14)}`,
      }}
    >
      {label}
    </button>
  )
}
