'use client'

import type { CSSProperties } from 'react'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { TYPE } from '../theme/tokens'

/** The standard widget header: title left, unit/label right. */
export function TitleRow({
  title,
  right,
  style,
}: {
  title?: string
  right?: string
  style?: CSSProperties
}) {
  const { theme } = useWidgetIdentity()
  if (!title && !right) return null
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'baseline',
        justifyContent: 'space-between',
        gap: 12,
        marginBottom: 10,
        ...style,
      }}
    >
      {title ? (
        <div style={{ fontSize: TYPE.heading, fontWeight: 600, lineHeight: 1.25 }}>{title}</div>
      ) : (
        <span />
      )}
      {right ? <span style={{ fontSize: TYPE.label, color: theme.text2, whiteSpace: 'nowrap' }}>{right}</span> : null}
    </div>
  )
}
