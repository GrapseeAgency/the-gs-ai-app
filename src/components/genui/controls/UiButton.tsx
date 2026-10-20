'use client'

import type { CSSProperties, ReactNode } from 'react'
import { alpha, inkOn, RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

export type ButtonStyle = 'filled' | 'tonal' | 'text'

/** The GenUI button: filled / tonal / text, accent-driven. */
export function UiButton({
  children,
  onClick,
  buttonStyle = 'tonal',
  disabled = false,
  style,
}: {
  children: ReactNode
  onClick?: () => void
  buttonStyle?: ButtonStyle
  disabled?: boolean
  style?: CSSProperties
}) {
  const { accent, theme } = useWidgetIdentity()
  const faces: Record<ButtonStyle, CSSProperties> = {
    filled: { background: accent, color: inkOn(accent), border: `1px solid ${accent}` },
    tonal: { background: alpha(accent, theme.dark ? 0.2 : 0.14), color: accent, border: `1px solid ${alpha(accent, 0.3)}` },
    text: { background: 'transparent', color: accent, border: '1px solid transparent' },
  }
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 6,
        padding: '8px 16px',
        borderRadius: RADIUS.pill,
        fontSize: TYPE.label,
        fontWeight: 600,
        cursor: disabled ? 'default' : 'pointer',
        opacity: disabled ? 0.5 : 1,
        transition: 'filter .15s ease, transform .06s ease',
        ...faces[buttonStyle],
        ...style,
      }}
      onMouseDown={(e) => {
        if (!disabled) e.currentTarget.style.filter = 'brightness(0.92)'
      }}
      onMouseUp={(e) => {
        e.currentTarget.style.filter = ''
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.filter = ''
      }}
    >
      {children}
    </button>
  )
}
