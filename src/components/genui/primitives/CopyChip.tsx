'use client'

import { useState } from 'react'
import { TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

/** Copy-to-clipboard as a quiet control. The clipboard write is the action. */
export function CopyChip({ value, label = 'Copy' }: { value: string; label?: string }) {
  const { accent, theme } = useWidgetIdentity()
  const [done, setDone] = useState(false)
  return (
    <button
      type="button"
      onClick={async () => {
        try {
          await navigator.clipboard.writeText(value)
          setDone(true)
          setTimeout(() => setDone(false), 1600)
        } catch {
          setDone(false)
        }
      }}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 6,
        padding: '5px 12px',
        borderRadius: 999,
        border: `1px solid ${done ? accent : theme.border}`,
        background: 'transparent',
        color: done ? accent : theme.text2,
        fontSize: TYPE.label,
        fontWeight: 600,
        cursor: 'pointer',
      }}
    >
      {done ? 'Copied' : label}
    </button>
  )
}
