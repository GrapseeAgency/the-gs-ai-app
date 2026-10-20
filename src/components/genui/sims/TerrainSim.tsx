'use client'

import { useRef } from 'react'
type Rec<K extends string = string, V = unknown> = Record<K, V>
import { Stage2D } from './Stage2D'

/** Terrain — sculptable heightfield: raise/lower with the pointer, water below. */
export function TerrainSim({ payload }: { payload: Rec<string, unknown> & { title?: string; gravity: number; size: number; seed: number; count: number; interactive: boolean } }) {
  const state = useRef<{ h: number[]; N: number; init: boolean; token: object | null }>({ h: [], N: 0, init: false, token: null })
  return (
    <Stage2D
      title={payload.title || 'Terrain sculptor'}
      height={payload.size * 3}
      knobs={payload}
      hint="drag: raise · shift-drag: lower"
      onStep={(ctx, w, h, _dt, _t, pointer, _ui, resetToken) => {
        const s = state.current
        const N = 64
        if (!s.init || s.token !== resetToken || s.N !== N) {
          s.init = true
          s.token = resetToken
          s.N = N
          // seeded value-noise hills
          let a = payload.seed * 104729
          const rnd = () => ((a = (a * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff)
          const coarse = Array.from({ length: 9 }, () => rnd())
          s.h = Array.from({ length: N }, (_, i) => {
            const t = (i / (N - 1)) * (coarse.length - 1)
            const i0 = Math.floor(t)
            const f = t - i0
            const lerp = coarse[i0] * (1 - f) + coarse[Math.min(coarse.length - 1, i0 + 1)] * f
            return 0.35 + lerp * 0.3 + Math.sin(i * 0.4 + payload.seed) * 0.05
          })
        }
        const cellW = w / (N - 1)
        if (pointer.down) {
          const gi = Math.round(pointer.x / cellW)
          const dir = pointer.y !== undefined && (typeof navigator !== 'undefined' && navigator.userAgent ? false : false) ? 0 : 1
          const lower = (globalThis as { event?: { shiftKey?: boolean } }).event?.shiftKey
          for (let d = -6; d <= 6; d++) {
            const i = Math.max(0, Math.min(N - 1, gi + d))
            const falloff = Math.cos((d / 7) * (Math.PI / 2))
            s.h[i] = Math.max(0.05, Math.min(1, s.h[i] + (lower ? -1 : dir) * falloff * 0.02))
          }
        }
        const baseY = h - 18
        const amp = h * 0.5
        // terrain body
        ctx.beginPath()
        ctx.moveTo(0, h)
        for (let i = 0; i < N; i++) ctx.lineTo(i * cellW, baseY - s.h[i] * amp)
        ctx.lineTo(w, h)
        ctx.closePath()
        const grad = ctx.createLinearGradient(0, baseY - amp, 0, h)
        grad.addColorStop(0, 'rgba(94,206,123,0.9)')
        grad.addColorStop(0.55, 'rgba(70,150,100,0.9)')
        grad.addColorStop(1, 'rgba(40,90,70,0.9)')
        ctx.fillStyle = grad
        ctx.fill()
        // water line
        ctx.fillStyle = 'rgba(64,150,220,0.30)'
        ctx.fillRect(0, baseY - amp * 0.28, w, h - (baseY - amp * 0.28))
        // brush indicator
        if (pointer.x > 0 && pointer.x < w && pointer.y > 0) {
          ctx.strokeStyle = 'rgba(255,255,255,0.5)'
          ctx.beginPath()
          ctx.arc(pointer.x, baseY - s.h[Math.max(0, Math.min(N - 1, Math.round(pointer.x / cellW)))] * amp, 10, 0, Math.PI * 2)
          ctx.stroke()
        }
      }}
    />
  )
}
