'use client'

import { useMemo, useState } from 'react'
import type { TablePayload } from '../types'
import { alpha, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { TitleRow } from '../primitives/TitleRow'
import { EmptyState } from '../primitives/EmptyState'

function monogramBadgeForCell(value: string): { code: string; bg: string; fg: string } | null {
  const clean = value.trim()
  if (!clean || /^[\d#$€£%+↑↓.\-\s]+$/.test(clean) || clean.length > 28) return null
  const lower = clean.toLowerCase()
  if (lower.startsWith('python')) return { code: 'Py', bg: '#E0F2FE', fg: '#0284C7' }
  if (lower === 'java') return { code: 'Jv', bg: '#FFEDD5', fg: '#EA580C' }
  if (lower.startsWith('c++')) return { code: 'C+', bg: '#DBEAFE', fg: '#2563EB' }
  if (lower === 'c' || lower === 'c#') return { code: clean, bg: '#E0E7FF', fg: '#4F46E5' }
  if (lower.startsWith('javascript')) return { code: 'JS', bg: '#FEF3C7', fg: '#D97706' }
  if (lower.startsWith('typescript')) return { code: 'TS', bg: '#DBEAFE', fg: '#1D4ED8' }
  if (lower.startsWith('rust')) return { code: 'Rs', bg: '#FEE2E2', fg: '#DC2626' }
  if (lower === 'go' || lower === 'golang') return { code: 'Go', bg: '#CFFAFE', fg: '#0891B2' }
  const words = clean.split(/\s+/).filter(Boolean)
  const code = words.length >= 2 ? `${words[0][0]}${words[1][0]}`.toUpperCase() : clean.slice(0, 2).toUpperCase()
  return { code, bg: 'rgba(124, 92, 252, 0.14)', fg: '#5B3DF5' }
}

/** Sortable data grid with ranking badges, trend coloring, pagination, and Key Insights (Screenshot 5). */
export function TableWidget({ payload }: { payload: TablePayload }) {
  const { theme } = useWidgetIdentity()
  const [sort, setSort] = useState<{ col: number; dir: 1 | -1 } | null>(null)
  const [query, setQuery] = useState('')
  const [pageSize, setPageSize] = useState<number>(5)
  const [page, setPage] = useState<number>(0)

  const numericCols = useMemo(() => {
    const cols = new Set<number>()
    payload.columns.forEach((_, ci) => {
      const values = payload.rows.map((r) => r[ci]).filter(Boolean)
      if (values.length > 0 && values.every((v) => /^[↑↓↗↘+-]?\s*[$€£¥₹]?\s*[+-]?[\d,.]+%?$/.test(String(v).trim()))) cols.add(ci)
    })
    return cols
  }, [payload.columns, payload.rows])

  const primaryNameCol = useMemo(() => {
    const idx = payload.columns.findIndex((c) => /language|product|name|model|framework|tool|company|item/i.test(c))
    if (idx >= 0) return idx
    return payload.columns[0]?.trim() === '#' || /rank/i.test(payload.columns[0] ?? '') ? 1 : 0
  }, [payload.columns])

  const rows = useMemo(() => {
    const q = query.trim().toLowerCase()
    const filtered = q
      ? payload.rows.filter((r) => r.some((cell) => String(cell ?? '').toLowerCase().includes(q)))
      : payload.rows
    if (!sort) return filtered
    const sorted = [...filtered]
    sorted.sort((a, b) => {
      const av = a[sort.col] ?? ''
      const bv = b[sort.col] ?? ''
      const an = Number(String(av).replace(/[^\d.-]/g, ''))
      const bn = Number(String(bv).replace(/[^\d.-]/g, ''))
      if (Number.isFinite(an) && Number.isFinite(bn) && String(av).trim() !== '' && String(bv).trim() !== '') return (an - bn) * sort.dir
      return String(av).localeCompare(String(bv)) * sort.dir
    })
    return sorted
  }, [payload.rows, sort, query])

  if (!payload.columns.length || !payload.rows.length) return <EmptyState label="No rows" />

  const totalPages = Math.max(1, Math.ceil(rows.length / pageSize))
  const safePage = Math.min(page, totalPages - 1)
  const visibleRows = rows.slice(safePage * pageSize, safePage * pageSize + pageSize)

  const rawPayload = payload as unknown as Record<string, unknown>
  const explicitInsights: string[] = Array.isArray(rawPayload.insights)
    ? (rawPayload.insights as unknown[]).map((x) => String(x))
    : typeof rawPayload.insight === 'string'
    ? [rawPayload.insight]
    : typeof rawPayload.notes === 'string'
    ? [rawPayload.notes]
    : []

  const renderCellContent = (rawVal: unknown, ci: number) => {
    const text = String(rawVal ?? '').trim()
    const trendMatch = text.match(/^([↑↓↗↘+-])?\s*([+-]\d+(?:\.\d+)?%|[↑↓↗↘]\s*\d+(?:\.\d+)?%)$/)
    if (trendMatch || (/trend|change|growth|delta|yoy/i.test(payload.columns[ci] ?? '') && /^[↑↓↗↘+-]?\s*\d+(?:\.\d+)?%$/.test(text))) {
      const isNeg = text.includes('-') || text.startsWith('↓') || text.startsWith('↘')
      const cleanNum = text.replace(/^[↑↓↗↘\s]+/, '')
      const formatted = cleanNum.startsWith('+') || cleanNum.startsWith('-') ? cleanNum : `${isNeg ? '-' : '+'}${cleanNum}`
      return (
        <span
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 3,
            fontWeight: 700,
            color: isNeg ? '#E04F5F' : '#129A5A',
          }}
        >
          <span>{isNeg ? '↓' : '↑'}</span>
          <span>{formatted}</span>
        </span>
      )
    }

    if (ci === primaryNameCol) {
      const badge = monogramBadgeForCell(text)
      if (badge) {
        return (
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
            <span
              aria-hidden
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                width: 22,
                height: 22,
                borderRadius: 6,
                background: badge.bg,
                color: badge.fg,
                fontSize: 10.5,
                fontWeight: 800,
                flexShrink: 0,
              }}
            >
              {badge.code}
            </span>
            <span style={{ fontWeight: 600, color: theme.text }}>{text}</span>
          </span>
        )
      }
    }

    return text
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
      <TitleRow title={payload.title} />
      {payload.rows.length >= 4 ? (
        <div>
          <input
            type="search"
            value={query}
            onChange={(e) => {
              setQuery(e.target.value)
              setPage(0)
            }}
            placeholder="Filter rows…"
            style={{
              width: '100%',
              boxSizing: 'border-box',
              padding: '6px 10px',
              borderRadius: 8,
              border: `1px solid ${theme.border}`,
              background: theme.raised,
              color: theme.text,
              fontSize: TYPE.micro,
              outline: 'none',
            }}
          />
        </div>
      ) : null}
      <div style={{ overflowX: 'auto', borderRadius: 12, border: `1px solid ${theme.border}` }}>
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
                    textAlign: numericCols.has(ci) && ci > 0 ? 'right' : 'left',
                    padding: '9px 11px',
                    background: 'rgba(124, 92, 252, 0.12)',
                    color: theme.text,
                    fontWeight: 700,
                    fontSize: TYPE.micro,
                    letterSpacing: '0.02em',
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
            {visibleRows.map((row, ri) => (
              <tr key={ri} style={{ background: ri % 2 === 1 ? alpha(theme.dark ? '#FFFFFF' : '#14171C', 0.025) : 'transparent' }}>
                {payload.columns.map((_, ci) => (
                  <td
                    key={ci}
                    style={{
                      padding: '8px 11px',
                      textAlign: numericCols.has(ci) && ci > 0 ? 'right' : 'left',
                      color: ci === 0 ? theme.text : theme.text2,
                      fontWeight: ci === 0 ? 600 : 400,
                      fontVariantNumeric: numericCols.has(ci) ? 'tabular-nums' : undefined,
                      borderBottom: `1px solid ${alpha(theme.dark ? '#FFFFFF' : '#14171C', 0.05)}`,
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {renderCellContent(row[ci], ci)}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>

        {/* Pagination & Display Controls (Screenshot 5) */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '8px 11px',
            borderTop: `1px solid ${theme.border}`,
            background: theme.raised,
            fontSize: 11.5,
            color: theme.text2,
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
            <button
              type="button"
              onClick={() => setPage(Math.max(0, safePage - 1))}
              disabled={safePage === 0}
              style={{
                border: 'none',
                background: 'transparent',
                color: theme.text2,
                cursor: safePage === 0 ? 'default' : 'pointer',
                opacity: safePage === 0 ? 0.4 : 1,
                padding: '2px 6px',
              }}
            >
              ‹
            </button>
            {Array.from({ length: totalPages }, (_, p) => (
              <button
                key={p}
                type="button"
                onClick={() => setPage(p)}
                style={{
                  width: 24,
                  height: 24,
                  borderRadius: 12,
                  border: 'none',
                  background: p === safePage ? '#6C4CF6' : 'transparent',
                  color: p === safePage ? '#FFFFFF' : theme.text2,
                  fontWeight: 700,
                  fontSize: 11.5,
                  cursor: 'pointer',
                }}
              >
                {p + 1}
              </button>
            ))}
            <button
              type="button"
              onClick={() => setPage(Math.min(totalPages - 1, safePage + 1))}
              disabled={safePage >= totalPages - 1}
              style={{
                border: 'none',
                background: 'transparent',
                color: theme.text2,
                cursor: safePage >= totalPages - 1 ? 'default' : 'pointer',
                opacity: safePage >= totalPages - 1 ? 0.4 : 1,
                padding: '2px 6px',
              }}
            >
              ›
            </button>
          </div>
          <button
            type="button"
            onClick={() => {
              const next = pageSize === 5 ? 10 : pageSize === 10 ? 25 : 5
              setPageSize(next)
              setPage(0)
            }}
            style={{
              border: `1px solid ${theme.border}`,
              background: 'rgba(124, 92, 252, 0.10)',
              color: theme.text,
              borderRadius: 8,
              padding: '3px 9px',
              fontSize: 11.5,
              fontWeight: 600,
              cursor: 'pointer',
            }}
          >
            Show: {pageSize} ▾
          </button>
        </div>
      </div>

      {/* Key Insights Card (Screenshot 5) */}
      {explicitInsights.length > 0 && (
        <div
          style={{
            padding: '11px 13px',
            borderRadius: 12,
            background: 'rgba(124, 92, 252, 0.10)',
            border: '1px solid rgba(124, 92, 252, 0.24)',
            fontSize: 12.5,
            color: theme.text,
          }}
        >
          <div style={{ fontWeight: 700, marginBottom: 4, display: 'flex', alignItems: 'center', gap: 6 }}>
            <span aria-hidden>💡</span>
            <span>Key Insights</span>
          </div>
          <ul style={{ margin: 0, paddingLeft: 18, display: 'flex', flexDirection: 'column', gap: 3 }}>
            {explicitInsights.map((ins, idx) => (
              <li key={idx} style={{ color: theme.text2 }}>
                {ins}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}

