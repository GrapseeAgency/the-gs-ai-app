'use client'

import type { ProgressPayload } from '../types'
import { alpha, inkOn, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { TitleRow } from '../primitives/TitleRow'
import { EmptyState } from '../primitives/EmptyState'

/** Progress — animated bars; outline surface, label + percent per row. */
export function ProgressWidget({ payload }: { payload: ProgressPayload }) {
  const { accent, theme } = useWidgetIdentity()
  if (!payload.items?.length) return <EmptyState label="No progress items" />
  return (
    <div>
      <TitleRow title={payload.title} />
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {payload.items.map((item) => (
          <div key={item.label}>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: TYPE.label, marginBottom: 5 }}>
              <span style={{ color: theme.text2, fontWeight: 600 }}>{item.label}</span>
              <span style={{ color: theme.text, fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>{Math.round(item.percent)}%</span>
            </div>
            <div style={{ height: 8, borderRadius: 4, background: theme.dark ? 'rgba(255,255,255,0.07)' : 'rgba(0,0,0,0.06)', overflow: 'hidden' }}>
              <div
                style={{
                  width: `${Math.max(0, Math.min(100, item.percent))}%`,
                  height: '100%',
                  borderRadius: 4,
                  background: `linear-gradient(90deg, ${alpha(accent, 0.75)}, ${accent})`,
                  transition: 'width .6s cubic-bezier(.22,1,.36,1)',
                }}
              />
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
