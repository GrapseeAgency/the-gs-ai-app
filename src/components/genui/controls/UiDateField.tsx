'use client'

import { RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

/** date | time | datetime — the native picker, skinned. */
export function UiDateField({
  value,
  onChange,
  inputType = 'date',
}: {
  value: string
  onChange: (v: string) => void
  inputType?: 'date' | 'time' | 'datetime-local'
}) {
  const { accent, theme } = useWidgetIdentity()
  return (
    <input
      type={inputType}
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
        colorScheme: theme.dark ? 'dark' : 'light',
        outline: '2px solid transparent',
        outlineOffset: 1,
      }}
      onFocus={(e) => (e.currentTarget.style.outlineColor = `${accent}66`)}
      onBlur={(e) => (e.currentTarget.style.outlineColor = 'transparent')}
    />
  )
}
