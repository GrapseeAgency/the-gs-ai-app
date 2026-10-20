'use client'

import type { MenuPayload } from '../types'
import { alpha, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { TitleRow } from '../primitives/TitleRow'
import { EmptyState } from '../primitives/EmptyState'

/** Menu — a tappable list of paths forward. */
export function MenuWidget({ payload, onPrompt }: { payload: MenuPayload; onPrompt?: (p: string) => void }) {
  const { accent, theme } = useWidgetIdentity()
  if (!(payload.options ?? []).length) return <EmptyState label="No options" />
  return (
    <div>
      <TitleRow title={payload.title} />
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        {(payload.options ?? []).map((opt, i) => (
          <button
            key={i}
            type="button"
            onClick={() => opt.prompt && onPrompt?.(opt.prompt)}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 10,
              width: '100%',
              textAlign: 'left',
              padding: '11px 12px',
              borderRadius: 12,
              border: `1px solid ${theme.border}`,
              background: 'transparent',
              cursor: 'pointer',
              transition: 'border-color .15s ease, background .15s ease',
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.borderColor = alpha(accent, 0.5)
              e.currentTarget.style.background = alpha(accent, 0.05)
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.borderColor = theme.border
              e.currentTarget.style.background = 'transparent'
            }}
          >
            <span style={{ flex: 1, minWidth: 0 }}>
              <span style={{ display: 'block', fontSize: TYPE.body, fontWeight: 600, color: theme.text }}>{opt.label}</span>
              {opt.detail ? (
                <span style={{ display: 'block', fontSize: TYPE.micro, color: theme.text3, marginTop: 2 }}>{opt.detail}</span>
              ) : null}
            </span>
            <span style={{ color: accent, fontSize: 16, flexShrink: 0 }}>→</span>
          </button>
        ))}
      </div>
    </div>
  )
}
