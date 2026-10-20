'use client'

import { useMemo, useState } from 'react'
import type { TablePayload } from '../types'
import { alpha, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { TitleRow } from '../primitives/TitleRow'
import { EmptyState } from '../primitives/EmptyState'

/** Sortable table — numbers right-aligned, header tappable to sort. */
export function TableWidget({ payload }: { payload: TablePayload }) {
  const { accent, theme } = useWidgetIdentity()
  const [sort, setSort] = useState<{ col: number; dir: 1 | -1 } | null>(null)
  const numericCols = useMemo(() => {
    const cols = new Set<number>()
    payload.columns.forEach((_, ci) => {
      const values = payload.rows.map((r) => r[ci]).filter(Boolean)
      if (values.length > 0 && values.every((v) => /^[$€£¥₹]?\s*-?[\d,.]+%?$/.test(String(v).trim()))) cols.add(ci)
    })
    return cols
  }, [payload.columns, payload.rows])

  const rows = useMemo(() => {
    if (!sort) return payload.rows
    const sorted = [...payload.rows]
    sorted.sort((a, b) => {
      const av = a[sort.col] ?? ''
      const bv = b[sort.col] ?? ''
      const an = Number(String(av).replace(/[^\d.-]/g, ''))
      const bn = Number(String(bv).replace(/[^\d.-]/g, ''))
      if (Number.isFinite(an) && Number.isFinite(bn) && String(av).trim() !== '' && String(bv).trim() !== '') return (an - bn) * sort.dir
      return String(av).localeCompare(String(bv)) * sort.dir
    })
    return sorted
  }, [payload.rows, sort])

  if (!payload.columns.length || !payload.rows.length) return <EmptyState label="No rows" />

  return (
    <div>
      <TitleRow title={payload.title} />
      <div style={{ overflowX: 'auto', borderRadius: 10, border: `1px solid ${theme.border}` }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: TYPE.label }}>
          <thead>
            <tr>
              {payload.columns.map((col, ci) => (
                <th
                  key={`${col}-${ci}`}
                  onClick={
                    payload.sortable === false
                      ? undefined
                      : () => setSort((s) => (s?.col === ci ? { col: ci, dir: s.dir === 1 ? -1 : 1 } : { col: ci, dir: 1 }))
                  }
                  style={{
                    textAlign: numericCols.has(ci) ? 'right' : 'left',
                    padding: '8px 10px',
                    background: theme.raised,
                    color: theme.text2,
                    fontWeight: 600,
                    fontSize: TYPE.micro,
                    textTransform: 'uppercase',
                    letterSpacing: '0.03em',
                    cursor: payload.sortable === false ? 'default' : 'pointer',
                    whiteSpace: 'nowrap',
                    borderBottom: `1px solid ${theme.border}`,
                  }}
                >
                  {col}
                  {sort?.col === ci ? (sort.dir === 1 ? ' ↑' : ' ↓') : ''}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row, ri) => (
              <tr key={ri} style={{ background: ri % 2 === 1 ? alpha(theme.dark ? '#FFFFFF' : '#14171C', 0.025) : 'transparent' }}>
                {payload.columns.map((_, ci) => (
                  <td
                    key={ci}
                    style={{
                      padding: '7px 10px',
                      textAlign: numericCols.has(ci) ? 'right' : 'left',
                      color: ci === 0 ? theme.text : theme.text2,
                      fontWeight: ci === 0 ? 600 : 400,
                      fontVariantNumeric: numericCols.has(ci) ? 'tabular-nums' : undefined,
                      borderBottom: `1px solid ${alpha(theme.dark ? '#FFFFFF' : '#14171C', 0.05)}`,
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {row[ci]}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {payload.sortable !== false && payload.rows.length > 1 && (
        <div style={{ fontSize: TYPE.micro, color: theme.text3, marginTop: 6 }}>Tap a column to sort</div>
      )}
    </div>
  )
}
