'use client'

import { RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

export function UiTextArea({
  placeholder,
  value,
  rows = 3,
  onChange,
}: {
  placeholder?: string
  value: string
  rows?: number
  onChange: (v: string) => void
}) {
  const { accent, theme } = useWidgetIdentity()
  return (
    <textarea
      rows={rows}
      placeholder={placeholder}
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
        resize: 'vertical',
        outline: '2px solid transparent',
        outlineOffset: 1,
        caretColor: accent,
        fontFamily: 'inherit',
      }}
      onFocus={(e) => (e.currentTarget.style.outlineColor = `${accent}66`)}
      onBlur={(e) => (e.currentTarget.style.outlineColor = 'transparent')}
    />
  )
}
