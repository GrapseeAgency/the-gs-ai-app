'use client'

import { TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

/** The honest empty state — a widget whose payload arrived empty. */
export function EmptyState({ label }: { label: string }) {
  const { theme } = useWidgetIdentity()
  return (
    <div
      style={{
        padding: '20px 0',
        textAlign: 'center',
        fontSize: TYPE.label,
        color: theme.text3,
      }}
    >
      {label}
    </div>
  )
}
