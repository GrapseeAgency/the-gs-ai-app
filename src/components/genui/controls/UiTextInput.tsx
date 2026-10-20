'use client'

import { RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

/** Single-line text family: text | email | url | number | password | otp. */
export function UiTextInput({
  placeholder,
  value,
  onChange,
  inputType = 'text',
  inputMode,
  maxLength,
  ariaLabel,
}: {
  placeholder?: string
  value: string
  onChange: (v: string) => void
  inputType?: string
  inputMode?: 'text' | 'numeric' | 'email' | 'url' | 'tel'
  maxLength?: number
  ariaLabel?: string
}) {
  const { accent, theme } = useWidgetIdentity()
  return (
    <input
      aria-label={ariaLabel}
      type={inputType}
      inputMode={inputMode}
      maxLength={maxLength}
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
        outline: '2px solid transparent',
        outlineOffset: 1,
        caretColor: accent,
      }}
      onFocus={(e) => (e.currentTarget.style.outlineColor = `${accent}66`)}
      onBlur={(e) => (e.currentTarget.style.outlineColor = 'transparent')}
    />
  )
}
