'use client'

import { useState } from 'react'
import type { PollPayload } from '../types'
import { alpha, inkOn, RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { TitleRow } from '../primitives/TitleRow'
import { EmptyState } from '../primitives/EmptyState'

/** Poll — vote once, bars animate to the new tally. */
export function PollWidget({ payload }: { payload: PollPayload }) {
  const { accent, theme } = useWidgetIdentity()
  const [voted, setVoted] = useState<number | null>(null)
  const [counts, setCounts] = useState<number[]>(() => (payload.options ?? []).map((o) => o.votes ?? 0))
  if ((payload.options ?? []).length < 2) return <EmptyState label="Poll needs options" />
  const vote = (i: number) => {
    if (voted !== null) return
    setVoted(i)
    setCounts((c) => c.map((v, ci) => (ci === i ? v + 1 : v)))
  }
  const total = counts.reduce((a, b) => a + b, 0) || 1
  return (
    <div>
      <TitleRow title={payload.title} right={`${total} vote${total === 1 ? '' : 's'}`} />
      {payload.question ? (
        <div style={{ fontSize: TYPE.body, fontWeight: 600, color: theme.text, marginBottom: 10 }}>{payload.question}</div>
      ) : null}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {(payload.options ?? []).map((opt, i) => {
          const pct = Math.round((counts[i] / total) * 100)
          const mine = voted === i
          return (
            <button
              key={i}
              type="button"
              onClick={() => vote(i)}
              style={{
                position: 'relative',
                overflow: 'hidden',
                textAlign: 'left',
                padding: '10px 12px',
                borderRadius: RADIUS.md,
                border: `1.5px solid ${mine ? accent : theme.border}`,
                cursor: voted === null ? 'pointer' : 'default',
                background: 'transparent',
              }}
            >
              {voted !== null && (
                <span
                  aria-hidden
                  style={{
                    position: 'absolute',
                    inset: 0,
                    width: `${pct}%`,
                    background: alpha(accent, mine ? 0.28 : 0.14),
                    transition: 'width .6s cubic-bezier(.22,1,.36,1)',
                  }}
                />
              )}
              <span style={{ position: 'relative', display: 'flex', justifyContent: 'space-between', fontSize: TYPE.label, color: theme.text }}>
                <span style={{ fontWeight: mine ? 700 : 400 }}>
                  {mine ? '◉' : voted !== null ? '○' : ''} {opt.label}
                </span>
                {voted !== null && <span style={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>{pct}%</span>}
              </span>
            </button>
          )
        })}
      </div>
    </div>
  )
}
