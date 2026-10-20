'use client'

import { useState } from 'react'
import type { RatingPayload } from '../types'
import { alpha, RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { UiRatingInput } from '../controls/UiRatingInput'
import { UiButton } from '../controls/UiButton'

/** Rating — star/heart/thumb input with a confirm; nps uses a 0-10 row. */
export function RatingWidget({ payload }: { payload: RatingPayload }) {
  const { accent, theme } = useWidgetIdentity()
  const [value, setValue] = useState(0)
  const [sent, setSent] = useState(false)
  const nps = payload.style === 'nps'
  const max = payload.max ?? (nps ? 11 : 5)
  return (
    <div>
      <div style={{ fontSize: TYPE.heading, fontWeight: 600, color: theme.text }}>{payload.title}</div>
      {payload.prompt ? (
        <div style={{ fontSize: TYPE.label, color: theme.text2, margin: '4px 0 12px' }}>{payload.prompt}</div>
      ) : (
        <div style={{ height: 8 }} />
      )}
      {nps ? (
        <div style={{ display: 'flex', gap: 4 }}>
          {Array.from({ length: max }, (_, i) => i).map((i) => (
            <button
              key={i}
              type="button"
              onClick={() => setValue(i)}
              style={{
                flex: 1,
                height: 36,
                borderRadius: 8,
                border: `1.5px solid ${value === i ? accent : theme.border}`,
                background: value === i ? accent : i < value ? alpha(accent, 0.2) : 'transparent',
                color: value === i ? theme.dark ? '#0F1216' : '#fff' : theme.text2,
                fontSize: TYPE.label,
                fontWeight: 700,
                cursor: 'pointer',
              }}
            >
              {i}
            </button>
          ))}
        </div>
      ) : (
        <UiRatingInput max={Math.min(max, 10)} style={payload.style ?? 'star'} value={value} onChange={setValue} />
      )}
      {(payload.labels ?? []).length === 2 && (
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: TYPE.micro, color: theme.text3, marginTop: 6 }}>
          <span>{payload.labels?.[0]}</span>
          <span>{payload.labels?.[1]}</span>
        </div>
      )}
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginTop: 14 }}>
        <UiButton buttonStyle="filled" disabled={value === 0 || sent} onClick={() => setSent(true)}>
          {sent ? 'Thanks ✓' : 'Send'}
        </UiButton>
      </div>
    </div>
  )
}
