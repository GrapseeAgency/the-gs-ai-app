'use client'

import { useState } from 'react'
import type { AppPayload } from '../types'
import { RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { AppShell } from './AppShell'
import { EmptyState } from '../primitives/EmptyState'

/** Sheet — an editable grid with live numeric column sums. */
export function AppSheet({ payload }: { payload: AppPayload }) {
  const { accent, theme } = useWidgetIdentity()
  const header = (payload.rows ?? [])[0] ?? []
  const dataRows = (payload.rows ?? []).slice(1)
  const [cells, setCells] = useState(() => dataRows.map((r) => [...r]))
  if (!header.length) return <EmptyState label="No sheet data" />
  const setCell = (r: number, c: number, v: string) => setCells((cs) => cs.map((row, ri) => (ri === r ? row.map((cell, ci) => (ci === c ? v : cell)) : row)))
  const isNumeric = (c: number) => cells.every((row) => row[c] === undefined || /^-?[\d.,]+$/.test(String(row[c]).trim()) || String(row[c]).trim() === '')
  return (
    <AppShell title={payload.title}>
      <div style={{ overflowX: 'auto', borderRadius: RADIUS.md, border: `1px solid ${theme.border}` }}>
        <table style={{ borderCollapse: 'collapse', width: '100%', fontSize: TYPE.micro }}>
          <thead>
            <tr>
              {header.map((h, i) => (
                <th key={i} style={{ padding: '7px 9px', textAlign: 'left', background: theme.raised, color: theme.text2, borderBottom: `1px solid ${theme.border}`, whiteSpace: 'nowrap' }}>
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {cells.map((row, ri) => (
              <tr key={ri}>
                {header.map((_, ci) => (
                  <td key={ci} style={{ borderBottom: `1px solid ${theme.dark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.04)'}`, padding: 0 }}>
                    <input
                      value={String(row[ci] ?? '')}
                      onChange={(e) => setCell(ri, ci, e.target.value)}
                      style={{
                        width: '100%',
                        border: 'none',
                        background: 'transparent',
                        color: theme.text,
                        fontSize: TYPE.micro,
                        padding: '7px 9px',
                        outline: 'none',
                        textAlign: isNumeric(ci) ? 'right' : 'left',
                        fontVariantNumeric: 'tabular-nums',
                      }}
                    />
                  </td>
                ))}
              </tr>
            ))}
            {isNumericAny() && (
              <tr>
                {header.map((_, ci) => {
                  const numeric = cells.every((row) => row[ci] === undefined || /^-?[\d.,]+$/.test(String(row[ci]).trim()) || String(row[ci]).trim() === '')
                  const sum = numeric ? cells.reduce((a, row) => a + (Number(String(row[ci]).replace(/,/g, '')) || 0), 0) : null
                  return (
                    <td key={ci} style={{ padding: '6px 9px', textAlign: 'right', fontWeight: 700, color: sum !== null ? accent : 'transparent', borderTop: `1.5px solid ${theme.border}`, fontVariantNumeric: 'tabular-nums' }}>
                      {sum !== null ? sum.toLocaleString() : '·'}
                    </td>
                  )
                })}
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </AppShell>
  )
  function isNumericAny(): boolean {
    return header.some((_, ci) => cells.some((row) => /^-?[\d.,]+$/.test(String(row[ci] ?? '').trim())))
  }
}
