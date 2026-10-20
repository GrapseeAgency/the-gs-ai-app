'use client'

import type { CSSProperties } from 'react'
import { alpha, inkOn, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

/** A small filled badge — counts, statuses, deltas. */
export function Badge({
  children,
  filled = false,
  style,
}: {
  children: React.ReactNode
  filled?: boolean
  style?: CSSProperties
}) {
  const { accent, theme } = useWidgetIdentity()
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        minWidth: 20,
        height: 20,
        padding: '0 6px',
        borderRadius: 999,
        fontSize: TYPE.micro,
        fontWeight: 700,
        background: filled ? accent : alpha(accent, theme.dark ? 0.18 : 0.12),
        color: filled ? inkOn(accent) : theme.dark ? accent : accent,
        ...style,
      }}
    >
      {children}
    </span>
  )
}
