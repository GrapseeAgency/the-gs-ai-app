'use client'

import { useState } from 'react'
import type { FlashcardsPayload } from '../types'
import { alpha, RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { TitleRow } from '../primitives/TitleRow'
import { EmptyState } from '../primitives/EmptyState'
import { UiButton } from '../controls/UiButton'

/** Flashcards — tap to flip, prev/next, deterministic order. */
export function FlashcardsWidget({ payload }: { payload: FlashcardsPayload }) {
  const { accent, theme } = useWidgetIdentity()
  const [index, setIndex] = useState(0)
  const [flipped, setFlipped] = useState(false)
  const cards = (payload.cards ?? []).slice(0, 20)
  if (!cards.length) return <EmptyState label="No cards" />
  const card = cards[Math.min(index, cards.length - 1)]
  const go = (delta: number) => {
    setFlipped(false)
    setIndex((i) => (i + delta + cards.length) % cards.length)
  }
  return (
    <div>
      <TitleRow title={payload.title} right={`${index + 1}/${cards.length}`} />
      <button
        type="button"
        onClick={() => setFlipped((f) => !f)}
        style={{
          width: '100%',
          minHeight: 130,
          borderRadius: RADIUS.xl,
          border: `1.5px solid ${alpha(accent, 0.4)}`,
          background: flipped ? alpha(accent, theme.dark ? 0.14 : 0.07) : theme.dark ? 'rgba(0,0,0,0.25)' : '#fff',
          cursor: 'pointer',
          padding: 18,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 8,
        }}
      >
        <span style={{ fontSize: TYPE.micro, fontWeight: 700, letterSpacing: '0.08em', color: accent, textTransform: 'uppercase' }}>
          {flipped ? 'Answer' : 'Question'}
        </span>
        <span style={{ fontSize: flipped ? TYPE.body : TYPE.title, fontWeight: flipped ? 400 : 600, color: theme.text, lineHeight: 1.45, textAlign: 'center' }}>
          {flipped ? card.back : card.front}
        </span>
        <span style={{ fontSize: TYPE.micro, color: theme.text3 }}>tap to flip</span>
      </button>
      <div style={{ display: 'flex', justifyContent: 'center', gap: 8, marginTop: 10 }}>
        <UiButton onClick={() => go(-1)}>←</UiButton>
        <UiButton onClick={() => go(1)}>→</UiButton>
      </div>
    </div>
  )
}
