'use client'

import { useState } from 'react'
import type { ChipsPayload } from '../types'
import { TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { TitleRow } from '../primitives/TitleRow'
import { EmptyState } from '../primitives/EmptyState'
import { UiChipToggle } from '../controls/UiChipToggle'

/** Chips — filter chips; multi by default, tap sends the chip's prompt. */
export function ChipsWidget({ payload, onPrompt }: { payload: ChipsPayload; onPrompt?: (p: string) => void }) {
  const { theme } = useWidgetIdentity()
  const [selected, setSelected] = useState<Set<number>>(new Set())
  if (!(payload.options ?? []).length) return <EmptyState label="No chips" />
  const toggle = (i: number, prompt?: string) => {
    setSelected((current) => {
      if (payload.multi === false) return current.has(i) ? current : new Set([i])
      const next = new Set(current)
      if (next.has(i)) next.delete(i)
      else next.add(i)
      return next
    })
    if (prompt && payload.multi === false) onPrompt?.(prompt)
  }
  return (
    <div>
      <TitleRow title={payload.title} />
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
        {(payload.options ?? []).map((opt, i) => (
          <UiChipToggle key={i} label={opt.label} selected={selected.has(i)} onToggle={() => toggle(i, opt.prompt)} />
        ))}
      </div>
    </div>
  )
}
