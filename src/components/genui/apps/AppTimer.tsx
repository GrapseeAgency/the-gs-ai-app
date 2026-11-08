'use client'

import { useEffect, useRef, useState } from 'react'
import type { AppPayload } from '../types'
import { alpha, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { AppShell } from './AppShell'
import { UiButton } from '../controls/UiButton'
import { UiField } from '../controls/UiField'

/** Timer — countdown with start/pause/reset and a progress ring. */
export function AppTimer({ payload }: { payload: AppPayload }) {
  const { accent, theme } = useWidgetIdentity()
  const configured = payload.durationSeconds
  const validSource = configured === undefined || (Number.isInteger(configured) && configured >= 1 && configured <= 604800)
  const initial = validSource ? configured ?? 0 : 0
  const sourceIdentity = JSON.stringify(payload)
  const [durationText, setDurationText] = useState(initial ? String(initial) : '')
  const [left, setLeft] = useState(initial)
  const [running, setRunning] = useState(false)
  const [started, setStarted] = useState(false)
  const deadline = useRef<number | null>(null)
  useEffect(() => {
    deadline.current = null
    setDurationText(initial ? String(initial) : '')
    setLeft(initial); setRunning(false); setStarted(false)
  }, [sourceIdentity])
  useEffect(() => {
    if (!running) return
    const tick = () => {
      if (deadline.current === null) return
      const remaining = Math.max(0, (deadline.current - performance.now()) / 1000)
      setLeft(remaining)
      if (remaining === 0) { deadline.current = null; setRunning(false) }
    }
    const id = window.setInterval(tick, 200)
    document.addEventListener('visibilitychange', tick)
    return () => { window.clearInterval(id); document.removeEventListener('visibilitychange', tick) }
  }, [running])
  const total = Number(durationText)
  const validDuration = durationText.trim() !== '' && Number.isInteger(total) && total >= 1 && total <= 604800
  const frac = validDuration ? Math.min(1, left / total) : 0
  const seconds = Math.ceil(left)
  const size = 150
  const r = (size - 14) / 2
  const circ = 2 * Math.PI * r
  const mm = String(Math.floor(seconds / 60)).padStart(2, '0')
  const ss = String(seconds % 60).padStart(2, '0')
  if (!validSource) return <AppShell title={payload.title ?? 'Countdown timer'}><p role="alert">Timer durationSeconds must be whole seconds from 1 through 604800. Source durations are not clamped or taken from prices.</p></AppShell>
  return (
    <AppShell title={payload.title ?? 'Countdown timer'}>
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
          <div role="timer" aria-label="Remaining time" style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 26, fontWeight: 700, color: theme.text, fontVariantNumeric: 'tabular-nums' }}>
            {mm}:{ss}
          </div>
        </div>
        <UiField label="Duration (seconds)">
          <input aria-label="Duration (seconds)" type="number" min={1} max={604800} step={1} value={durationText} disabled={running || started}
            onChange={event => { const text = event.target.value; setDurationText(text); setLeft(Number.isInteger(Number(text)) && Number(text) >= 1 && Number(text) <= 604800 ? Number(text) : 0) }} />
        </UiField>
        {!validDuration && <span role="status">Enter whole seconds from 1 through 604800 to configure this local countdown.</span>}
        <div style={{ display: 'flex', gap: 8 }}>
          <UiButton buttonStyle="filled" onClick={() => {
            if (running) {
              setLeft(Math.max(0, ((deadline.current ?? performance.now()) - performance.now()) / 1000))
              deadline.current = null; setRunning(false)
            } else {
              deadline.current = performance.now() + left * 1000; setStarted(true); setRunning(true)
            }
          }} disabled={!validDuration || left === 0}>
            {running ? 'Pause' : 'Start'}
          </UiButton>
          <UiButton
            onClick={() => {
              deadline.current = null; setRunning(false); setStarted(false)
              setLeft(validDuration ? total : 0)
            }}
          >
            Reset
          </UiButton>
        </div>
        <span style={{ fontSize: TYPE.label, color: theme.text2 }}>Local countdown only. No background alarm or notification is scheduled.</span>
        {started && left === 0 && <span style={{ fontSize: TYPE.label, color: accent, fontWeight: 700 }}>Done — take a break.</span>}
      </div>
    </AppShell>
  )
}
