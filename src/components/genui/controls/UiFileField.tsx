'use client'

import { useRef, useState } from 'react'
import { RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

/** File attach — name-only in chat (the file never leaves the device here). */
export function UiFileField({ label = 'Attach a file' }: { label?: string }) {
  const { accent, theme } = useWidgetIdentity()
  const ref = useRef<HTMLInputElement>(null)
  const [name, setName] = useState('')
  return (
    <span>
      <button
        type="button"
        onClick={() => ref.current?.click()}
        style={{
          padding: '9px 14px',
          borderRadius: RADIUS.md,
          border: `1px dashed ${theme.border}`,
          background: 'transparent',
          color: name ? accent : theme.text2,
          fontSize: TYPE.label,
          fontWeight: 600,
          cursor: 'pointer',
        }}
      >
        {name || label}
      </button>
      <input
        ref={ref}
        type="file"
        style={{ display: 'none' }}
        onChange={(e) => setName(e.target.files?.[0]?.name ?? '')}
      />
    </span>
  )
}
