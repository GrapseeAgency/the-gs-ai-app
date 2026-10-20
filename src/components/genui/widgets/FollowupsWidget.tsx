'use client'

import type { FollowupsPayload } from '../types'
import { RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { EmptyState } from '../primitives/EmptyState'

/** Follow-ups — quiet tappable next prompts. */
export function FollowupsWidget({ payload, onPrompt }: { payload: FollowupsPayload; onPrompt?: (p: string) => void }) {
  const { accent, theme } = useWidgetIdentity()
  const prompts = (payload.prompts ?? []).slice(0, 6)
  if (!prompts.length) return <EmptyState label="No suggestions" />
  return (
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
      {prompts.map((prompt, i) => (
        <button
          key={i}
          type="button"
          onClick={() => onPrompt?.(prompt)}
          style={{
            padding: '8px 14px',
            borderRadius: RADIUS.pill,
            border: `1px solid ${theme.border}`,
            background: 'transparent',
            color: theme.text2,
            fontSize: TYPE.label,
            fontWeight: 500,
            cursor: 'pointer',
          }}
          onMouseEnter={(e) => (e.currentTarget.style.color = accent)}
          onMouseLeave={(e) => (e.currentTarget.style.color = theme.text2)}
        >
          {prompt}
        </button>
      ))}
    </div>
  )
}
