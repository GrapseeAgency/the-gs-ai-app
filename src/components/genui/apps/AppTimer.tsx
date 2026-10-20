'use client'

import { useEffect, useRef, useState } from 'react'
import type { AppPayload } from '../types'
import { alpha, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { AppShell } from './AppShell'
import { UiButton } from '../controls/UiButton'
import { UiStepper } from '../controls/UiStepper'

/** Timer — countdown with start/pause/reset and a progress ring. */
export function AppTimer({ payload }: { payload: AppPayload }) {
  const { accent, theme } = useWidgetIdentity()
  const defaultMinutes = Math.max(1, Math.min(180, Number((payload.items ?? [])[0]?.price ?? 25)))
  const [minutes, setMinutes] = useState(defaultMinutes)
  const [left, setLeft] = useState(defaultMinutes * 60)
  const [running, setRunning] = useState(false)
  const raf = useRef<number | null>(null)
  useEffect(() => {
    if (!running) return
    const id = window.setInterval(() => {
      setLeft((l) => {
        if (l <= 1) {
          setRunning(false)
          return 0
        }
        return l - 1
      })
    }, 1000)
    raf.current = id
    return () => window.clearInterval(id)
  }, [running])
  const total = minutes * 60
  const frac = total > 0 ? left / total : 0
  const size = 150
  const r = (size - 14) / 2
  const circ = 2 * Math.PI * r
  const mm = String(Math.floor(left / 60)).padStart(2, '0')
  const ss = String(left % 60).padStart(2, '0')
  return (
    <AppShell title={payload.title ?? 'Focus timer'}>
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 14 }}>
        <div style={{ position: 'relative', width: size, height: size }}>
          <svg width={size} height={size} style={{ transform: 'rotate(-90deg)' }}>
            <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke={theme.dark ? 'rgba(255,255,255,0.07)' : 'rgba(0,0,0,0.06)'} strokeWidth={11} />
            <circle
              cx={size / 2}
              cy={size / 2}
              r={r}
              fill="none"
              stroke={accent}
              strokeWidth={11}
              strokeLinecap="round"
              strokeDasharray={`${frac * circ} ${circ}`}
              style={{ transition: 'stroke-dasharray .5s linear' }}
            />
          </svg>
          <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 30, fontWeight: 700, color: theme.text, fontVariantNumeric: 'tabular-nums' }}>
            {mm}:{ss}
          </div>
        </div>
        {!running && left === total && <UiStepper value={minutes} min={1} max={180} unit="min" onChange={(v) => { setMinutes(v); setLeft(v * 60) }} />}
        <div style={{ display: 'flex', gap: 8 }}>
          <UiButton buttonStyle="filled" onClick={() => setRunning((r2) => !r2)} disabled={left === 0}>
            {running ? 'Pause' : 'Start'}
          </UiButton>
          <UiButton
            onClick={() => {
              setRunning(false)
              setLeft(minutes * 60)
            }}
          >
            Reset
          </UiButton>
        </div>
        {left === 0 && <span style={{ fontSize: TYPE.label, color: accent, fontWeight: 700 }}>Done — take a break.</span>}
      </div>
    </AppShell>
  )
}
