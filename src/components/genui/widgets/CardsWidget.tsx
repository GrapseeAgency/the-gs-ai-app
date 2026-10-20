'use client'

import type { CardsPayload } from '../types'
import { alpha, RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { TitleRow } from '../primitives/TitleRow'
import { EmptyState } from '../primitives/EmptyState'

/** Cards — tappable prompt cards; a tap sends the card's prompt back. */
export function CardsWidget({ payload, onPrompt }: { payload: CardsPayload; onPrompt?: (p: string) => void }) {
  const { accent, theme } = useWidgetIdentity()
  const cards = (payload.cards ?? []).slice(0, 8)
  if (!cards.length) return <EmptyState label="No cards" />
  return (
    <div>
      <TitleRow title={payload.title} />
      <div style={{ display: 'grid', gridTemplateColumns: cards.length > 2 ? '1fr 1fr' : '1fr', gap: 8 }}>
        {cards.map((card, i) => (
          <button
            key={i}
            type="button"
            onClick={() => card.prompt && onPrompt?.(card.prompt)}
            style={{
              textAlign: 'left',
              padding: '12px 14px',
              borderRadius: RADIUS.lg,
              border: `1px solid ${theme.border}`,
              background: alpha(accent, theme.dark ? 0.07 : 0.04),
              cursor: card.prompt ? 'pointer' : 'default',
            }}
          >
            <span style={{ display: 'block', fontSize: TYPE.body, fontWeight: 700, color: theme.text }}>{card.title}</span>
            {card.subtitle ? (
              <span style={{ display: 'block', fontSize: TYPE.micro, color: theme.text2, marginTop: 3, lineHeight: 1.5 }}>{card.subtitle}</span>
            ) : null}
            {card.prompt ? <span style={{ display: 'block', fontSize: TYPE.micro, color: accent, fontWeight: 600, marginTop: 8 }}>Tap to ask →</span> : null}
          </button>
        ))}
      </div>
    </div>
  )
}
