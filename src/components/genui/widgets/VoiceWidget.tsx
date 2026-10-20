'use client'

import type { VoicePayload } from '../types'
import { alpha, RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

/** Voice — the mic card; on web it sends the prompt as a normal message. */
export function VoiceWidget({ payload, onPrompt }: { payload: VoicePayload; onPrompt?: (p: string) => void }) {
  const { accent, theme } = useWidgetIdentity()
  return (
    <button
      type="button"
      onClick={() => payload.prompt && onPrompt?.(payload.prompt)}
      style={{
        width: '100%',
        display: 'flex',
        alignItems: 'center',
        gap: 14,
        padding: 16,
        borderRadius: RADIUS.lg,
        border: `1.5px solid ${alpha(accent, 0.4)}`,
        background: alpha(accent, theme.dark ? 0.1 : 0.06),
        cursor: 'pointer',
        textAlign: 'left',
      }}
    >
      <span
        style={{
          width: 40,
          height: 40,
          borderRadius: 999,
          background: accent,
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          flexShrink: 0,
          animation: 'gs-mic 2s ease infinite',
        }}
      >
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke={theme.dark ? '#0F1216' : '#fff'} strokeWidth="2.2" strokeLinecap="round">
          <rect x="9" y="2.5" width="6" height="11" rx="3" />
          <path d="M5 11a7 7 0 0 0 14 0M12 18v3.5" />
        </svg>
      </span>
      <span>
        <span style={{ display: 'block', fontSize: TYPE.body, fontWeight: 700, color: theme.text }}>{payload.title ?? 'Talk instead'}</span>
        <span style={{ display: 'block', fontSize: TYPE.label, color: theme.text2, marginTop: 2 }}>{payload.hint ?? payload.prompt}</span>
      </span>
      <style>{`@keyframes gs-mic { 0%,100% { box-shadow: 0 0 0 0 ${alpha(accent, 0.35)} } 50% { box-shadow: 0 0 0 9px ${alpha(accent, 0)} } }`}</style>
    </button>
  )
}
