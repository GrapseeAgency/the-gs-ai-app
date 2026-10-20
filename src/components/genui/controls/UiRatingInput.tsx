'use client'

import { useState } from 'react'
import { TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

/** star | heart | thumb — the tappable rating input. */
export function UiRatingInput({
  max = 5,
  style = 'star',
  value,
  onChange,
}: {
  max?: number
  style?: string
  value: number
  onChange: (v: number) => void
}) {
  const { accent, theme } = useWidgetIdentity()
  const [hover, setHover] = useState(0)
  const glyph = style === 'heart' ? '♥' : style === 'thumb' ? '👍' : '★'
  const shown = hover || value
  return (
    <div role="radiogroup" style={{ display: 'inline-flex', gap: 4 }}>
      {Array.from({ length: max }, (_, i) => i + 1).map((i) => (
        <button
          key={i}
          type="button"
          role="radio"
          aria-checked={value === i}
          aria-label={`${i} of ${max}`}
          onMouseEnter={() => setHover(i)}
          onMouseLeave={() => setHover(0)}
          onClick={() => onChange(i)}
          style={{
            border: 'none',
            background: 'transparent',
            fontSize: style === 'thumb' ? 22 : 26,
            lineHeight: 1,
            padding: 2,
            cursor: 'pointer',
            filter: i <= shown ? 'none' : 'grayscale(1)',
            opacity: i <= shown ? 1 : 0.35,
            color: accent,
          }}
        >
          {glyph}
        </button>
      ))}
      <span style={{ fontSize: TYPE.label, color: theme.text2, alignSelf: 'center', marginLeft: 6 }}>
        {shown > 0 ? `${shown}/${max}` : ''}
      </span>
    </div>
  )
}
