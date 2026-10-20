'use client'

import { useState } from 'react'
import type { AppPayload } from '../types'
import { alpha, RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { AppShell } from './AppShell'

/** Calendar — a month grid with event dots; tap a day to select. */
export function AppCalendar({ payload }: { payload: AppPayload }) {
  const { accent, theme } = useWidgetIdentity()
  const monthStr = payload.month ?? new Date().toISOString().slice(0, 7)
  const [year, month] = monthStr.split('-').map(Number)
  const [selected, setSelected] = useState<number | null>(null)
  const first = new Date(year ?? 2026, (month ?? 1) - 1, 1)
  const days = new Date(year ?? 2026, month ?? 1, 0).getDate()
  const offset = (first.getDay() + 6) % 7 // Monday-first
  const eventDays = new Set(
    (payload.items ?? []).map((it) => Number(String(it.date ?? '').split('-')[2])).filter((d) => Number.isFinite(d) && d > 0)
  )
  const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December']
  return (
    <AppShell title={payload.title ?? `${MONTHS[(month ?? 1) - 1]} ${year}`}>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 3 }}>
        {['M', 'T', 'W', 'T', 'F', 'S', 'S'].map((d, i) => (
          <span key={i} style={{ textAlign: 'center', fontSize: TYPE.micro, color: theme.text3, fontWeight: 700 }}>
            {d}
          </span>
        ))}
        {Array.from({ length: offset }, (_, i) => (
          <span key={`pad-${i}`} />
        ))}
        {Array.from({ length: days }, (_, d) => {
          const day = d + 1
          const isSel = selected === day
          return (
            <button
              key={day}
              type="button"
              onClick={() => setSelected(day)}
              style={{
                position: 'relative',
                height: 34,
                borderRadius: 9,
                border: `1.5px solid ${isSel ? accent : 'transparent'}`,
                background: isSel ? alpha(accent, 0.16) : 'transparent',
                color: isSel ? accent : theme.text2,
                fontSize: TYPE.label,
                fontWeight: isSel ? 700 : 400,
                cursor: 'pointer',
              }}
            >
              {day}
              {eventDays.has(day) && <span style={{ position: 'absolute', bottom: 3, left: '50%', marginLeft: -2, width: 4, height: 4, borderRadius: 999, background: accent }} />}
            </button>
          )
        })}
      </div>
      {selected !== null && (
        <div style={{ marginTop: 10, fontSize: TYPE.micro, color: theme.text2 }}>
          {selected} {String((payload.items ?? []).find((it) => Number(String(it.date ?? '').split('-')[2]) === selected)?.title ?? '') || '— no events'}
        </div>
      )}
    </AppShell>
  )
}
