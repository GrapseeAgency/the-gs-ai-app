'use client'

import type { CSSProperties } from 'react'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { alpha, TYPE } from '../theme/tokens'

/**
 * The streaming placeholder. A widget fence that is still arriving renders this
 * — never raw JSON, never a flicker.
 */
export function Skeleton({
  label = 'Building…',
  height = 120,
  style,
}: {
  label?: string
  height?: number
  style?: CSSProperties
}) {
  const { accent, theme } = useWidgetIdentity()
  return (
    <div
      style={{
        height,
        borderRadius: 14,
        border: `1px solid ${theme.border}`,
        background: `linear-gradient(100deg, ${theme.card} 40%, ${alpha(accent, 0.10)} 50%, ${theme.card} 60%)`,
        backgroundSize: '200% 100%',
        animation: 'gs-sweep 1.4s ease infinite',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontSize: TYPE.label,
        color: theme.text3,
        gap: 8,
        ...style,
      }}
    >
      <span
        style={{
          width: 8,
          height: 8,
          borderRadius: 999,
          background: accent,
          animation: 'gs-pulse 1.2s ease infinite',
        }}
      />
      {label}
      <style>{`@keyframes gs-sweep { to { background-position: -200% 0 } } @keyframes gs-pulse { 50% { opacity: .3 } }`}</style>
    </div>
  )
}
