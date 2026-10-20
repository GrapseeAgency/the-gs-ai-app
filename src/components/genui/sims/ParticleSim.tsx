'use client'

import { useRef } from 'react'
import { Stage2D } from './Stage2D'

interface Pt { x: number; y: number; vx: number; vy: number; hue: number; life: number }

/** Particles — gravity wells; hold the pointer to attract, release to repel. */
export function ParticleSim({ payload }: { payload: Record<string, unknown> & { title?: string; gravity: number; damping: number; count: number; seed: number; size: number; interactive: boolean } }) {
  const state = useRef<{ pts: Pt[]; init: boolean; token: object | null }>({ pts: [], init: false, token: null })
  const n = Math.max(30, Math.min(120, payload.count))
  return (
    <Stage2D
      title={payload.title || 'Particle playground'}
      height={payload.size * 3}
      knobs={payload}
      hint="hold to attract · release to repel"
      onStep={(ctx, w, h, dt, t, pointer, ui, resetToken) => {
        const s = state.current
        if (!s.init || s.token !== resetToken) {
          s.init = true
          s.token = resetToken
          s.pts = Array.from({ length: n }, () => ({
            x: Math.random() * w,
            y: Math.random() * h,
            vx: (Math.random() - 0.5) * 60,
            vy: (Math.random() - 0.5) * 60,
            hue: Math.random(),
            life: 1,
          }))
        }
        for (const p of s.pts) {
          if (pointer.down) {
            const dx = pointer.x - p.x
            const dy = pointer.y - p.y
            const d = Math.max(18, Math.hypot(dx, dy))
            const f = (2600 / (d * d)) * 60 * dt
            p.vx += (dx / d) * f
            p.vy += (dy / d) * f
          } else {
            const dx = pointer.x - p.x
            const dy = pointer.y - p.y
            const d = Math.max(30, Math.hypot(dx, dy))
            if (d < 120) {
              const f = -(1400 / (d * d)) * 60 * dt
              p.vx += (dx / d) * f
              p.vy += (dy / d) * f
            }
          }
          p.vy += ui.gravity * 12 * dt * 0.4
          p.vx *= ui.damping
          p.vy *= ui.damping
          p.x += p.vx * dt
          p.y += p.vy * dt
          if (p.x < 0 || p.x > w) { p.vx *= -0.9; p.x = Math.max(0, Math.min(w, p.x)) }
          if (p.y < 0 || p.y > h) { p.vy *= -0.9; p.y = Math.max(0, Math.min(h, p.y)) }
        }
        for (let i = 0; i < s.pts.length; i++) {
          const p = s.pts[i]
          const warm = pointer.down ? 1 : 0
          ctx.fillStyle = `hsla(${(p.hue * 60 + 160 + warm * 40 + t * 12) % 360}, 80%, ${55 + p.hue * 15}%, 0.85)`
          ctx.beginPath()
          ctx.arc(p.x, p.y, 1.6 + p.hue * 1.6, 0, Math.PI * 2)
          ctx.fill()
        }
      }}
    />
  )
}
