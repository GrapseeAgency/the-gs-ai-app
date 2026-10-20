'use client'

import { useMemo, useState } from 'react'
import type { DiffPayload } from '../types'
import { alpha, RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { TitleRow } from '../primitives/TitleRow'
import { EmptyState } from '../primitives/EmptyState'
import { CopyChip } from '../primitives/CopyChip'

interface DiffRow {
  kind: 'same' | 'add' | 'del'
  text: string
}

/** LCS line diff — the real red/green view of a before/after pair. */
function diffLines(before: string[], after: string[]): DiffRow[] {
  const n = before.length
  const m = after.length
  const lcs: number[][] = Array.from({ length: n + 1 }, () => new Array(m + 1).fill(0))
  for (let i = n - 1; i >= 0; i--)
    for (let j = m - 1; j >= 0; j--) lcs[i][j] = before[i] === after[j] ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1])
  const rows: DiffRow[] = []
  let i = 0
  let j = 0
  while (i < n && j < m) {
    if (before[i] === after[j]) {
      rows.push({ kind: 'same', text: before[i] })
      i++
      j++
    } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
      rows.push({ kind: 'del', text: before[i++] })
    } else {
      rows.push({ kind: 'add', text: after[j++] })
    }
  }
  while (i < n) rows.push({ kind: 'del', text: before[i++] })
  while (j < m) rows.push({ kind: 'add', text: after[j++] })
  return rows
}

function parseUnified(unified: string): DiffRow[] {
  return unified
    .split('\n')
    .filter((l) => !l.startsWith('---') && !l.startsWith('+++') && !l.startsWith('@@'))
    .map<DiffRow>((l) => (l.startsWith('+') ? { kind: 'add', text: l.slice(1) } : l.startsWith('-') ? { kind: 'del', text: l.slice(1) } : { kind: 'same', text: l.replace(/^ /, '') }))
}

export function DiffWidget({ payload }: { payload: DiffPayload }) {
  const { theme } = useWidgetIdentity()
  const [sideBySide] = useState(false)
  const rows = useMemo(() => {
    if (payload.unified) return parseUnified(payload.unified)
    return diffLines((payload.before ?? '').split('\n'), (payload.after ?? '').split('\n'))
  }, [payload.unified, payload.before, payload.after])
  if (!rows.length) return <EmptyState label="No changes" />
  const adds = rows.filter((r) => r.kind === 'add').length
  const dels = rows.filter((r) => r.kind === 'del').length
  const ADD = { bg: alpha('#0CA678', 0.13), fg: '#0CA678' }
  const DEL = { bg: alpha('#E03131', 0.11), fg: '#E03131' }
  return (
    <div>
      <TitleRow
        title={payload.title}
        right={`+${adds} −${dels}`}
        style={undefined}
      />
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 8 }}>
        <CopyChip value={rows.map((r) => (r.kind === 'add' ? '+ ' : r.kind === 'del' ? '- ' : '  ') + r.text).join('\n')} />
      </div>
      <div
        style={{
          borderRadius: RADIUS.md,
          border: `1px solid ${theme.border}`,
          background: theme.dark ? 'rgba(0,0,0,0.3)' : '#fff',
          overflow: 'hidden',
          maxHeight: 420,
          overflowY: 'auto',
        }}
      >
        {rows.slice(0, 200).map((row, i) => {
          const tone = row.kind === 'add' ? ADD : row.kind === 'del' ? DEL : null
          return (
            <div
              key={i}
              style={{
                display: 'flex',
                background: tone?.bg ?? 'transparent',
                borderLeft: `2.5px solid ${tone ? tone.fg : 'transparent'}`,
              }}
            >
              <span style={{ width: 34, flexShrink: 0, textAlign: 'right', paddingRight: 10, fontSize: TYPE.micro, color: theme.text3, lineHeight: '20px', userSelect: 'none', fontVariantNumeric: 'tabular-nums' }}>
                {row.kind === 'same' ? i + 1 : ''}
              </span>
              <code style={{ whiteSpace: 'pre-wrap', fontSize: TYPE.micro, lineHeight: '20px', color: row.kind === 'same' ? theme.text2 : tone?.fg, fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace' }}>
                {row.kind === 'add' ? '+ ' : row.kind === 'del' ? '− ' : '  '}
                {row.text}
              </code>
            </div>
          )
        })}
      </div>
    </div>
  )
}
