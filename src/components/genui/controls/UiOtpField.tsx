'use client'

import { useRef } from 'react'
import { RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

/** One-time-code input — N boxes, auto-advance, backspace retreat. */
export function UiOtpField({ length = 6, value, onChange }: { length?: number; value: string; onChange: (v: string) => void }) {
  const { accent, theme } = useWidgetIdentity()
  const refs = useRef<(HTMLInputElement | null)[]>([])
  const chars = Array.from({ length }, (_, i) => value[i] ?? '')
  return (
    <div style={{ display: 'flex', gap: 6 }} role="group" aria-label="verification code">
      {chars.map((c, i) => (
        <input
          key={i}
          ref={(el) => {
            refs.current[i] = el
          }}
          value={c}
          inputMode="numeric"
          maxLength={1}
          aria-label={`digit ${i + 1}`}
          onChange={(e) => {
            const d = e.target.value.replace(/\D/g, '')
            const next = (value.slice(0, i) + d + value.slice(i + 1)).slice(0, length)
            onChange(next)
            if (d && i < length - 1) refs.current[i + 1]?.focus()
          }}
          onKeyDown={(e) => {
            if (e.key === 'Backspace' && !chars[i] && i > 0) refs.current[i - 1]?.focus()
          }}
          style={{
            width: 38,
            height: 44,
            textAlign: 'center',
            fontSize: TYPE.title,
            fontWeight: 700,
            borderRadius: RADIUS.md,
            border: `1px solid ${c ? accent : theme.border}`,
            background: theme.dark ? 'rgba(0,0,0,0.25)' : '#fff',
            color: theme.text,
            outline: 'none',
            caretColor: accent,
          }}
        />
      ))}
    </div>
  )
}
