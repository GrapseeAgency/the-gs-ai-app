'use client'

import { useRef } from 'react'
import { Stage2D } from './Stage2D'

interface Puff { x: number; y: number; vx: number; vy: number; r: number; heat: number }

/** Smoke — buoyant puffs with turbulence and cooling; light the fire low. */
export function SmokeSim({ payload }: { payload: Record<string, unknown> & { title?: string; gravity: number; size: number; count: number; seed: number; interactive: boolean } }) {
  const state = useRef<{ puffs: Puff[]; init: boolean; token: object | null; acc: number }>({ puffs: [], init: false, token: null, acc: 0 })
  return (
    <Stage2D
      title={payload.title || 'Fire & smoke'}
      height={payload.size * 3}
      knobs={payload}
      hint="hold to feed the fire"
      onStep={(ctx, w, h, dt, t, pointer, ui, resetToken) => {
        const s = state.current
        if (!s.init || s.token !== resetToken) {
          s.init = true
          s.token = resetToken
          s.puffs = []
          s.acc = 0
        }
        const baseY = h - 12
        const rate = pointer.down ? 60 : 18
        s.acc += dt * rate
        while (s.acc > 1) {
          s.acc -= 1
          s.puffs.push({
            x: w / 2 + (Math.random() - 0.5) * 26,
            y: baseY,
            vx: (Math.random() - 0.5) * 14,
            vy: -40 - Math.random() * 40 * (pointer.down ? 2 : 1),
            r: 6 + Math.random() * 8,
            heat: 1,
          })
        }
        for (const p of s.puffs) {
          const turb = Math.sin(p.y * 0.035 + t * 2.2 + p.r) * 26
          p.vy -= ui.gravity * 9 * dt * (0.5 + p.heat * 0.9) // buoyancy fights gravity
          p.vx += turb * dt
          p.vx *= 0.99
          p.x += p.vx * dt
          p.y += p.vy * dt
          p.r += 14 * dt
          p.heat *= 0.988
        }
        s.puffs = s.puffs.filter((p) => p.y > -30 && p.heat > 0.06).slice(-160)
        for (const p of s.puffs) {
          const hot = Math.max(0, Math.min(1, p.heat))
          const hue = 12 + hot * 42
          ctx.fillStyle = `hsla(${hue}, ${60 + hot * 35}%, ${30 + hot * 40}%, ${0.12 + hot * 0.4})`
          ctx.beginPath()
          ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2)
          ctx.fill()
        }
        ctx.fillStyle = `hsla(28, 95%, 55%, ${pointer.down ? 0.95 : 0.7})`
        ctx.beginPath()
        ctx.ellipse(w / 2, baseY + 3, 16 + (pointer.down ? 8 : 0), 5, 0, 0, Math.PI * 2)
        ctx.fill()
      }}
    />
  )
}
