'use client'

import { useState } from 'react'
import { TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { GlassIcon } from '@/components/nexly/GlassIcon'

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
  const { theme } = useWidgetIdentity()
  const [hover, setHover] = useState(0)
  const iconName = style === 'heart' ? 'heart' : style === 'thumb' ? 'circle-check' : 'star'
  const shown = hover || value
  return (
    <div role="radiogroup" style={{ display: 'inline-flex', gap: 6 }}>
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
            lineHeight: 1,
            padding: 2,
            cursor: 'pointer',
            filter: i <= shown ? 'none' : 'grayscale(1)',
            opacity: i <= shown ? 1 : 0.35,
          }}
        >
          <GlassIcon name={iconName} tone={style === 'heart' ? 'rose' : 'amber'} size={28} glyph={15} />
        </button>
      ))}
      <span style={{ fontSize: TYPE.label, color: theme.text2, alignSelf: 'center', marginLeft: 6 }}>
        {shown > 0 ? `${shown}/${max}` : ''}
      </span>
    </div>
  )
}
