'use client'

import { useState } from 'react'
import type { ActionsPayload } from '../types'
import { alpha, inkOn, RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { TitleRow } from '../primitives/TitleRow'
import { EmptyState } from '../primitives/EmptyState'

/** Actions — prompt / copy / open buttons. */
export function ActionsWidget({ payload, onPrompt }: { payload: ActionsPayload; onPrompt?: (p: string) => void }) {
  const { accent, theme } = useWidgetIdentity()
  const [copied, setCopied] = useState<string | null>(null)
  const buttons = (payload.buttons ?? []).slice(0, 6)
  if (!buttons.length) return <EmptyState label="No actions" />
  const face = (style: string | undefined): React.CSSProperties => {
    if (style === 'filled') return { background: accent, color: inkOn(accent), border: `1px solid ${accent}` }
    if (style === 'text') return { background: 'transparent', color: accent, border: '1px solid transparent' }
    return { background: alpha(accent, theme.dark ? 0.16 : 0.1), color: accent, border: `1px solid ${alpha(accent, 0.3)}` }
  }
  return (
    <div>
      <TitleRow title={payload.title} />
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
        {buttons.map((btn, i) => (
          <button
            key={i}
            type="button"
            onClick={async () => {
              if (btn.kind === 'prompt') onPrompt?.(btn.value)
              else if (btn.kind === 'copy') {
                try {
                  await navigator.clipboard.writeText(btn.value)
                  setCopied(btn.value)
                  setTimeout(() => setCopied(null), 1600)
                } catch {
                  /* clipboard unavailable */
                }
              } else if (btn.kind === 'open') window.open(btn.value, '_blank', 'noopener')
            }}
            style={{
              padding: '9px 16px',
              borderRadius: RADIUS.pill,
              fontSize: TYPE.label,
              fontWeight: 600,
              cursor: 'pointer',
              ...face(btn.style),
            }}
          >
            {btn.kind === 'copy' && copied === btn.value ? 'Copied ✓' : btn.label}
          </button>
        ))}
      </div>
    </div>
  )
}
