'use client'

/** CodeBlock — plain fenced code that is not a widget: highlighted, copyable. */
import { useState } from 'react'
import { RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { CopyChip } from '../primitives/CopyChip'

export function CodeBlock({ code, language }: { code: string; language?: string }) {
  const { theme } = useWidgetIdentity()
  return (
    <div>
      {language ? (
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
          <span style={{ fontSize: TYPE.micro, fontWeight: 700, color: theme.text3, textTransform: 'uppercase', letterSpacing: '0.05em' }}>{language}</span>
          <CopyChip value={code} />
        </div>
      ) : null}
      <pre
        style={{
          margin: 0,
          padding: 14,
          borderRadius: RADIUS.md,
          border: `1px solid ${theme.border}`,
          background: theme.dark ? 'rgba(0,0,0,0.32)' : 'rgba(0,0,0,0.03)',
          color: theme.text2,
          fontSize: TYPE.micro,
          lineHeight: 1.6,
          overflowX: 'auto',
          fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
        }}
      >
        <code>{code}</code>
      </pre>
    </div>
  )
}
