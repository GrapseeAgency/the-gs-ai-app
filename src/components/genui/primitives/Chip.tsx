'use client'

import type { CSSProperties, ReactNode } from 'react'
import { alpha, inkOn, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

/** A static chip — inline metadata, not a control (see controls/UiChipToggle). */
export function Chip({
  children,
  tone = 'neutral',
  style,
}: {
  children: ReactNode
  tone?: 'neutral' | 'accent' | 'success' | 'warning' | 'danger'
  style?: CSSProperties
}) {
  const { accent, theme } = useWidgetIdentity()
  const tones: Record<string, { bg: string; fg: string }> = {
    neutral: { bg: theme.raised, fg: theme.text2 },
    accent: { bg: alpha(accent, theme.dark ? 0.2 : 0.14), fg: theme.dark ? accent : accent },
    success: { bg: 'rgba(12,166,120,0.16)', fg: '#0CA678' },
    warning: { bg: 'rgba(240,140,0,0.16)', fg: '#F08C00' },
    danger: { bg: 'rgba(224,49,49,0.16)', fg: '#E03131' },
  }
  const t = tones[tone] ?? tones.neutral
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 4,
        padding: '3px 10px',
        borderRadius: 999,
        fontSize: TYPE.micro,
        fontWeight: 600,
        background: t.bg,
        color: t.fg,
        whiteSpace: 'nowrap',
        ...style,
      }}
    >
      {children}
    </span>
  )
}
