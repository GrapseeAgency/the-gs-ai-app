'use client'

import { useState } from 'react'
import type { AppPayload } from '../types'
import { RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { AppShell } from './AppShell'
import { EmptyState } from '../primitives/EmptyState'
import { UiButton } from '../controls/UiButton'

/** Booking — pick a slot, confirm. */
export function AppBooking({ payload }: { payload: AppPayload }) {
  const { accent, theme } = useWidgetIdentity()
  const slots = (payload.slots ?? []).slice(0, 12)
  const [picked, setPicked] = useState<string | null>(null)
  const [done, setDone] = useState(false)
  if (!slots.length) return <EmptyState label="No slots available" />
  return (
    <AppShell title={payload.title} submitLabel={picked && !done ? (payload.submitLabel ?? 'Confirm booking') : undefined}>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 8 }}>
        {slots.map((slot) => (
          <button
            key={slot}
            type="button"
            onClick={() => !done && setPicked(slot)}
            style={{
              padding: '10px 0',
              borderRadius: RADIUS.md,
              border: `1.5px solid ${picked === slot ? accent : theme.border}`,
              background: picked === slot ? `${accent}1f` : 'transparent',
              color: picked === slot ? accent : theme.text2,
              fontSize: TYPE.label,
              fontWeight: picked === slot ? 700 : 500,
              cursor: done ? 'default' : 'pointer',
              fontVariantNumeric: 'tabular-nums',
            }}
          >
            {slot}
          </button>
        ))}
      </div>
      {done && picked ? <div style={{ marginTop: 12, fontSize: TYPE.label, color: '#0CA678', fontWeight: 700 }}>Booked for {picked} ✓</div> : null}
    </AppShell>
  )
}
