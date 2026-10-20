'use client'

import type { TimelinePayload } from '../types'
import { alpha, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { TitleRow } from '../primitives/TitleRow'
import { EmptyState } from '../primitives/EmptyState'

/** Timeline — dated entries down a hairline with accent nodes. */
export function TimelineWidget({ payload }: { payload: TimelinePayload }) {
  const { accent, theme } = useWidgetIdentity()
  if (!payload.entries?.length) return <EmptyState label="No entries" />
  return (
    <div>
      <TitleRow title={payload.title} />
      <div style={{ position: 'relative', paddingLeft: 22 }}>
        <span aria-hidden style={{ position: 'absolute', left: 5, top: 6, bottom: 6, width: 2, background: alpha(accent, 0.3), borderRadius: 1 }} />
        {payload.entries.map((entry, i) => (
          <div key={i} style={{ position: 'relative', paddingBottom: i === payload.entries.length - 1 ? 0 : 14 }}>
            <span
              aria-hidden
              style={{
                position: 'absolute',
                left: -22,
                top: 4,
                width: 12,
                height: 12,
                borderRadius: 999,
                background: i === 0 ? accent : theme.card,
                border: `2.5px solid ${accent}`,
              }}
            />
            <div style={{ fontSize: TYPE.micro, fontWeight: 700, color: accent, letterSpacing: '0.02em' }}>{entry.date}</div>
            <div style={{ fontSize: TYPE.body, fontWeight: 600, color: theme.text, margin: '2px 0' }}>{entry.title}</div>
            {entry.detail ? <div style={{ fontSize: TYPE.label, color: theme.text2, lineHeight: 1.5 }}>{entry.detail}</div> : null}
          </div>
        ))}
      </div>
    </div>
  )
}
