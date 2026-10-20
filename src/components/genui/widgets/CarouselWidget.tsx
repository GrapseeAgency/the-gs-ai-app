'use client'

import { useRef, useState } from 'react'
import type { CarouselPayload } from '../types'
import { alpha, RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { TitleRow } from '../primitives/TitleRow'
import { EmptyState } from '../primitives/EmptyState'

/** Carousel — swipe/scroll media cards with dots. */
export function CarouselWidget({ payload }: { payload: CarouselPayload }) {
  const { accent, theme } = useWidgetIdentity()
  const [index, setIndex] = useState(0)
  const ref = useRef<HTMLDivElement>(null)
  const items = (payload.items ?? []).slice(0, 8)
  if (!items.length) return <EmptyState label="No images" />
  const onScroll = () => {
    const el = ref.current
    if (!el) return
    setIndex(Math.round(el.scrollLeft / el.clientWidth))
  }
  return (
    <div>
      <TitleRow title={payload.title} right={`${index + 1}/${items.length}`} />
      <div
        ref={ref}
        onScroll={onScroll}
        style={{ display: 'flex', overflowX: 'auto', scrollSnapType: 'x mandatory', gap: 10, borderRadius: RADIUS.lg, scrollbarWidth: 'none' }}
      >
        {items.map((item, i) => (
          <figure key={i} style={{ flex: '0 0 100%', scrollSnapAlign: 'center', margin: 0 }}>
            {item.image ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                src={item.image}
                alt={item.caption ?? `slide ${i + 1}`}
                loading="lazy"
                style={{ width: '100%', height: 190, objectFit: 'cover', borderRadius: RADIUS.lg, border: `1px solid ${theme.border}` }}
              />
            ) : null}
            {item.caption ? (
              <figcaption style={{ fontSize: TYPE.label, color: theme.text2, marginTop: 6, textAlign: 'center' }}>{item.caption}</figcaption>
            ) : null}
          </figure>
        ))}
      </div>
      <div style={{ display: 'flex', justifyContent: 'center', gap: 6, marginTop: 8 }}>
        {items.map((_, i) => (
          <span key={i} style={{ width: i === index ? 16 : 6, height: 6, borderRadius: 3, background: i === index ? accent : alpha(theme.dark ? '#FFFFFF' : '#14171C', 0.2), transition: 'width .25s ease' }} />
        ))}
      </div>
    </div>
  )
}
