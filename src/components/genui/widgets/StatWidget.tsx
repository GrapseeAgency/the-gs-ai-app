'use client'

import type { StatPayload } from '../types'
import { alpha, inkOn, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { TitleRow } from '../primitives/TitleRow'
import { EmptyState } from '../primitives/EmptyState'

/** Stat cards — headline numbers with deltas; the hero surface is theirs. */
export function StatWidget({ payload }: { payload: StatPayload }) {
  const { accent, theme } = useWidgetIdentity()
  const cards = (payload.cards ?? []).filter((c) => c.label && c.value)
  if (cards.length === 0) return <EmptyState label="No metrics" />
  return (
    <div>
      <TitleRow title={payload.title} />
      <div style={{ display: 'grid', gridTemplateColumns: `repeat(${Math.min(cards.length, 2)}, 1fr)`, gap: 8 }}>
        {cards.map((card) => {
          const up = card.direction === 'up'
          const down = card.direction === 'down'
          const deltaColor = up ? '#0CA678' : down ? '#E03131' : theme.text3
          return (
            <div
              key={card.label}
              style={{
                padding: '12px 14px',
                borderRadius: 12,
                background: alpha(accent, theme.dark ? 0.10 : 0.06),
                border: `1px solid ${alpha(accent, 0.16)}`,
              }}
            >
              <div style={{ fontSize: TYPE.label, color: theme.text2, marginBottom: 4 }}>{card.label}</div>
              <div style={{ display: 'flex', alignItems: 'baseline', gap: 6 }}>
                <span style={{ fontSize: TYPE.display, fontWeight: 700, color: theme.text, lineHeight: 1.1 }}>{card.value}</span>
                {card.unit ? <span style={{ fontSize: TYPE.label, color: theme.text2 }}>{card.unit}</span> : null}
              </div>
              {(card.delta || up || down) && (
                <div style={{ fontSize: TYPE.micro, fontWeight: 600, color: deltaColor, marginTop: 4 }}>
                  {up ? '▲' : down ? '▼' : '■'} {card.delta}
                </div>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
