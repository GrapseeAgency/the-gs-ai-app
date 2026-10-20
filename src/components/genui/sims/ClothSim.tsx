'use client'

import { useRef } from 'react'
import { Stage2D } from './Stage2D'
import { useWidgetIdentity } from '../theme/WidgetSurface'

interface P { x: number; y: number; px: number; py: number; pin: boolean }
interface S { pts: P[]; links: Array<[number, number, number]>; cols: number; rows: number; init: boolean; drag: number }

/** Cloth — Verlet grid with structural shear constraints; drag to tear. */
export function ClothSim({ payload }: { payload: Record<string, unknown> & { title?: string; gravity: number; damping: number; size: number; seed: number; interactive: boolean; count: number } }) {
  const { accent, theme } = useWidgetIdentity()
  const state = useRef<S>({ pts: [], links: [], cols: 0, rows: 0, init: false, drag: -1 })
  const cols = Math.max(8, Math.min(24, Math.round(payload.count / 3) + 8))
  const rows = Math.max(6, Math.round(cols * 0.62))
  return (
    <Stage2D
      title={payload.title || 'Cloth — Verlet integration'}
      height={payload.size * 3}
      knobs={payload}
      hint="drag to tear · top row pinned"
      onStep={(ctx, w, h, dt, _t, pointer, ui, resetToken) => {
        const s = state.current
        if (!s.init || (resetToken as { n?: number }).n !== (s as S & { initTokens?: object }).initTokens) {
          s.init = true
          ;(s as S & { initTokens?: object }).initTokens = resetToken
          s.cols = cols
          s.rows = rows
          s.pts = []
          s.links = []
          const gap = Math.min((w - 40) / cols, (h - 60) / rows)
          const x0 = (w - gap * (cols - 1)) / 2
          for (let y = 0; y < rows; y++)
            for (let x = 0; x < cols; x++)
              s.pts.push({ x: x0 + x * gap, y: 26 + y * gap, px: x0 + x * gap, py: 26 + y * gap, pin: y === 0 })
          const id = (x: number, y: number) => y * cols + x
          for (let y = 0; y < rows; y++)
            for (let x = 0; x < cols; x++) {
              if (x < cols - 1) s.links.push([id(x, y), id(x + 1, y), gap])
              if (y < rows - 1) s.links.push([id(x, y), id(x, y + 1), gap])
            }
        }
        const g = ui.gravity * 60
        const damp = ui.damping * 0.999
        // nearest-point grab / tear
        if (pointer.down && s.drag < 0) {
          let best = -1
          let bd = 400
          s.pts.forEach((p, i) => {
            if (p.pin) return
            const d = (p.x - pointer.x) ** 2 + (p.y - pointer.y) ** 2
            if (d < bd) { bd = d; best = i }
          })
          s.drag = best
        } else if (!pointer.down) s.drag = -1
        const sub = 3
        const sdt = dt / sub
        for (let k = 0; k < sub; k++) {
          for (const p of s.pts) {
            if (p.pin) continue
            const vx = (p.x - p.px) * damp
            const vy = (p.y - p.py) * damp
            p.px = p.x
            p.py = p.y
            p.x += vx
            p.y += vy + g * sdt * sdt
          }
          if (s.drag >= 0 && pointer.down) {
            const p = s.pts[s.drag]
            const dx = pointer.x - p.x
            const dy = pointer.y - p.y
            const dist = Math.hypot(dx, dy)
            if (dist > 26) {
              // tear: drop every link touching the grabbed point
              s.links = s.links.filter(([a, b]) => a !== s.drag && b !== s.drag)
              s.drag = -1
            } else {
              p.px = p.x - dx
              p.py = p.y - dy
              p.x = pointer.x
              p.y = pointer.y
            }
          }
          for (const [a, b, rest] of s.links) {
            const pa = s.pts[a]
            const pb = s.pts[b]
            const dx = pb.x - pa.x
            const dy = pb.y - pa.y
            const d = Math.hypot(dx, dy) || 1e-6
            const diff = ((d - rest) / d) * 0.5
            if (!pa.pin) { pa.x += dx * diff; pa.y += dy * diff }
            if (!pb.pin) { pb.x -= dx * diff; pb.y -= dy * diff }
          }
        }
        ctx.strokeStyle = accent
        ctx.lineWidth = 1
        ctx.globalAlpha = 0.85
        ctx.beginPath()
        for (const [a, b] of s.links) {
          ctx.moveTo(s.pts[a].x, s.pts[a].y)
          ctx.lineTo(s.pts[b].x, s.pts[b].y)
        }
        ctx.stroke()
        ctx.globalAlpha = 1
        ctx.fillStyle = theme.text2
        for (const p of s.pts) if (p.pin) ctx.fillRect(p.x - 2, p.y - 2, 4, 4)
      }}
    />
  )
}
