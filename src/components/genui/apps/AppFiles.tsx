'use client'

import { useState } from 'react'
import type { AppPayload } from '../types'
import { RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { AppShell } from './AppShell'
import { EmptyState } from '../primitives/EmptyState'

/** Files — a simple file browser list. */
export function AppFiles({ payload }: { payload: AppPayload }) {
  const { accent, theme } = useWidgetIdentity()
  const items = (payload.items ?? []).slice(0, 30)
  const [selected, setSelected] = useState<string | null>(null)
  if (!items.length) return <EmptyState label="No files" />
  const icon = (name: string) => {
    const ext = name.split('.').pop()?.toLowerCase()
    if (['png', 'jpg', 'jpeg', 'gif', 'webp'].includes(ext ?? '')) return '🖼'
    if (['mp4', 'webm', 'mov'].includes(ext ?? '')) return '🎬'
    if (['mp3', 'm4a', 'wav', 'ogg'].includes(ext ?? '')) return '🎵'
    if (['pdf', 'doc', 'docx'].includes(ext ?? '')) return '📄'
    if (['zip', 'tar', 'gz'].includes(ext ?? '')) return '🗜'
    return '📃'
  }
  return (
    <AppShell title={payload.title ?? 'Files'}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
        {items.map((it, i) => {
          const name = String(it.title ?? `file-${i}`)
          return (
            <button
              key={i}
              type="button"
              onClick={() => setSelected(name)}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 10,
                width: '100%',
                textAlign: 'left',
                padding: '9px 10px',
                borderRadius: RADIUS.md,
                border: `1px solid ${selected === name ? accent : 'transparent'}`,
                background: selected === name ? `${accent}12` : 'transparent',
                cursor: 'pointer',
              }}
            >
              <span style={{ fontSize: 18 }}>{icon(name)}</span>
              <span style={{ flex: 1, minWidth: 0 }}>
                <span style={{ display: 'block', fontSize: TYPE.label, color: theme.text, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{name}</span>
                <span style={{ display: 'block', fontSize: TYPE.micro, color: theme.text3 }}>{String(it.meta ?? it.subtitle ?? '')}</span>
              </span>
            </button>
          )
        })}
      </div>
    </AppShell>
  )
}
