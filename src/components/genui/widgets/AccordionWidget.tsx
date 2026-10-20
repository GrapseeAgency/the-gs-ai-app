'use client'

import { useState } from 'react'
import type { AccordionPayload } from '../types'
import { alpha, RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { TitleRow } from '../primitives/TitleRow'
import { EmptyState } from '../primitives/EmptyState'

/** Accordion — stacked reveal sections; one open at a time, first optional. */
export function AccordionWidget({ payload }: { payload: AccordionPayload }) {
  const { accent, theme } = useWidgetIdentity()
  const firstOpen = (payload.sections ?? []).findIndex((s) => s.expanded === true)
  const [open, setOpen] = useState<number | null>(firstOpen >= 0 ? firstOpen : null)
  const sections = (payload.sections ?? []).slice(0, 10)
  if (!sections.length) return <EmptyState label="No sections" />
  return (
    <div>
      <TitleRow title={payload.title} />
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        {sections.map((section, i) => {
          const isOpen = open === i
          return (
            <div key={i} style={{ borderRadius: RADIUS.md, border: `1px solid ${isOpen ? alpha(accent, 0.4) : theme.border}`, overflow: 'hidden' }}>
              <button
                type="button"
                aria-expanded={isOpen}
                onClick={() => setOpen(isOpen ? null : i)}
                style={{
                  width: '100%',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 10,
                  padding: '10px 12px',
                  background: isOpen ? alpha(accent, theme.dark ? 0.1 : 0.05) : 'transparent',
                  border: 'none',
                  cursor: 'pointer',
                  textAlign: 'left',
                }}
              >
                <span style={{ color: accent, fontSize: 12, transition: 'transform .2s ease', transform: isOpen ? 'rotate(90deg)' : 'none' }}>▸</span>
                <span style={{ flex: 1, fontSize: TYPE.body, fontWeight: 600, color: theme.text }}>{section.label}</span>
              </button>
              {isOpen && (
                <div style={{ padding: '0 12px 12px 34px', fontSize: TYPE.label, color: theme.text2, lineHeight: 1.6 }}>{section.body}</div>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
