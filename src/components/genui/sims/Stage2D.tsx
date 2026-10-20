'use client'

import { useEffect, useRef, useState, type ReactNode } from 'react'
import { alpha, RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { TitleRow } from '../primitives/TitleRow'

/**
 * Stage2D — the shared simulation frame: a themed canvas, a rAF loop with
 * pause/reset, and the payload's knobs (gravity/speed/damping). Each sim
 * supplies a step(ctx, w, h, dt, state) closure.
 */
export function Stage2D({
  title,
  height = 240,
  knobs,
  onStep,
  onReset,
  hint,
}: {
  title?: string
  height?: number
  knobs: Partial<{ gravity: number; speed: number; damping: number; count: number; size: number; seed: number; interactive: boolean }>
  onStep: (ctx: CanvasRenderingContext2D, w: number, h: number, dt: number, t: number, pointer: { x: number; y: number; down: boolean }, ui: { gravity: number; speed: number; damping: number }, shouldReset: object) => void
  onReset?: () => void
  hint?: string
}) {
  const { accent, theme } = useWidgetIdentity()
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const pointerRef = useRef({ x: -999, y: -999, down: false })
  const resetRef = useRef({})
  const [running, setRunning] = useState(true)
  const K = {
    gravity: knobs.gravity ?? 9.8,
    speed: knobs.speed ?? 1,
    damping: knobs.damping ?? 0.99,
    count: knobs.count ?? 60,
    size: knobs.size ?? 64,
    seed: knobs.seed ?? 7,
    interactive: knobs.interactive !== false,
  }
  const uiRef = useRef({ gravity: K.gravity, speed: K.speed, damping: K.damping })
  useEffect(() => {
    uiRef.current = { gravity: K.gravity, speed: K.speed, damping: K.damping }
  })

  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    let raf = 0
    let last = performance.now()
    const dpr = Math.min(2, typeof window !== 'undefined' ? window.devicePixelRatio || 1 : 1)
    const resize = () => {
      const w = canvas.clientWidth
      canvas.width = w * dpr
      canvas.height = height * dpr
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
    }
    resize()
    const loop = (now: number) => {
      const dt = Math.min(0.033, (now - last) / 1000)
      last = now
      if (running) {
        const w = canvas.clientWidth
        ctx.clearRect(0, 0, w, height)
        onStep(ctx, w, height, dt * uiRef.current.speed, now / 1000, pointerRef.current, uiRef.current, resetRef.current)
      }
      raf = requestAnimationFrame(loop)
    }
    raf = requestAnimationFrame(loop)
    const ro = new ResizeObserver(resize)
    ro.observe(canvas)
    return () => {
      cancelAnimationFrame(raf)
      ro.disconnect()
    }
  }, [running, height, onStep, knobs.seed])

  return (
    <div>
      <TitleRow title={title} />
      <canvas
        ref={canvasRef}
        style={{
          width: '100%',
          height,
          display: 'block',
          borderRadius: RADIUS.md,
          border: `1px solid ${theme.border}`,
          background: theme.dark ? 'rgba(0,0,0,0.32)' : 'rgba(0,0,0,0.04)',
          touchAction: 'none',
          cursor: knobs.interactive ? 'crosshair' : 'default',
        }}
        onPointerDown={(e) => {
          const r = e.currentTarget.getBoundingClientRect()
          pointerRef.current = { x: e.clientX - r.left, y: e.clientY - r.top, down: true }
          e.currentTarget.setPointerCapture(e.pointerId)
        }}
        onPointerMove={(e) => {
          const r = e.currentTarget.getBoundingClientRect()
          pointerRef.current.x = e.clientX - r.left
          pointerRef.current.y = e.clientY - r.top
        }}
        onPointerUp={() => (pointerRef.current.down = false)}
        onPointerLeave={() => (pointerRef.current = { x: -999, y: -999, down: false })}
      />
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 8 }}>
        <button
          type="button"
          onClick={() => setRunning((r) => !r)}
          style={{ padding: '4px 12px', borderRadius: 999, border: `1px solid ${accent}`, background: running ? `${accent}1f` : 'transparent', color: accent, fontSize: TYPE.micro, fontWeight: 700, cursor: 'pointer' }}
        >
          {running ? 'Pause' : 'Run'}
        </button>
        <button
          type="button"
          onClick={() => (resetRef.current = {})}
          style={{ padding: '4px 12px', borderRadius: 999, border: `1px solid ${theme.border}`, background: 'transparent', color: theme.text2, fontSize: TYPE.micro, fontWeight: 600, cursor: 'pointer' }}
        >
          Reset
        </button>
        {hint ? <span style={{ fontSize: TYPE.micro, color: theme.text3 }}>{hint}</span> : null}
      </div>
    </div>
  )
}
