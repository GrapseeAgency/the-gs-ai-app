'use client'

import { useMemo } from 'react'
import type { ChartPayload } from '../types'
import { ChartFrame } from './ChartFrame'
import { alpha, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

interface SNode {
  id: string
  x: number
  y: number
  h: number
}
interface SLink {
  source: number
  target: number
  value: number
  w: number
}

/** Sankey — two-pass layered layout (sources left, sinks right, mid spread). */
export function SankeyChartView({ payload, height = 220 }: { payload: ChartPayload; height?: number }) {
  const { accent, ramp, theme } = useWidgetIdentity()
  const W = 320
  const linksRaw = payload.links ?? []
  const layout = useMemo(() => {
    const ids: string[] = [...new Set(linksRaw.flatMap((l) => [l.source, l.target]))]
    const index = new Map(ids.map((id, i) => [id, i]))
    const outSum = new Array(ids.length).fill(0)
    const inSum = new Array(ids.length).fill(0)
    for (const l of linksRaw) {
      outSum[index.get(l.source)!] += l.value
      inSum[index.get(l.target)!] += l.value
    }
    const maxFlow = Math.max(1, ...outSum, ...inSum)
    const scale = (height - 40) / maxFlow
    const nodes: SNode[] = ids.map((id, i) => {
      const h = Math.max(4, Math.max(outSum[i], inSum[i]) * scale)
      const col = outSum[i] > 0 && inSum[i] === 0 ? 0 : inSum[i] > 0 && outSum[i] === 0 ? 2 : 1
      return { id, x: (col * (W - 70)) / 2, y: 0, h }
    })
    // simple vertical stacking per column
    const cols = new Map<number, SNode[]>()
    nodes.forEach((n) => {
      const col = n.x === 0 ? 0 : n.x > 0 && n.x < W - 70 ? 1 : 2
      cols.set(col, [...(cols.get(col) ?? []), n])
    })
    for (const list of cols.values()) {
      const total = list.reduce((a, n) => a + n.h + 8, -8)
      let y = (height - 24 - total) / 2
      for (const n of list) {
        n.y = y
        y += n.h + 8
      }
    }
    const links: SLink[] = linksRaw.map((l) => ({
      source: index.get(l.source)!,
      target: index.get(l.target)!,
      value: l.value,
      w: Math.max(1.5, l.value * scale),
    }))
    return { nodes, links }
  }, [linksRaw, height])

  const colors = ramp(Math.max(1, layout.nodes.length))
  const colorOf = (i: number) => colors[i % colors.length]
  return (
    <ChartFrame title={payload.title} unit={payload.unit} legend={false} height={height}>
      {({ width }) => (
        <svg width="100%" height={height - 24} viewBox={`0 0 ${W} ${height - 24}`} preserveAspectRatio="xMidYMid meet" role="img" aria-label="sankey">
          {layout.links.map((l, i) => {
            const s = layout.nodes[l.source]
            const t = layout.nodes[l.target]
            if (!s || !t) return null
            const x0 = s.x + 34
            const x1 = t.x
            const sy = s.y + l.w / 2 + 2
            const ty = t.y + l.w / 2 + 2
            const c = colorOf(l.source)
            return (
              <path
                key={i}
                d={`M ${x0} ${sy} C ${(x0 + x1) / 2} ${sy}, ${(x0 + x1) / 2} ${ty}, ${x1} ${ty}`}
                stroke={alpha(c, 0.4)}
                strokeWidth={l.w}
                fill="none"
              >
                <title>{`${s.id} → ${t.id}: ${l.value}`}</title>
              </path>
            )
          })}
          {layout.nodes.map((n, i) => (
            <g key={n.id}>
              <rect x={n.x} y={n.y} width={34} height={n.h} rx={4} fill={colorOf(i)} />
              <text x={n.x + (n.x > W / 2 ? -6 : 40)} y={n.y + n.h / 2 + 3} textAnchor={n.x > W / 2 ? 'end' : 'start'} fontSize={TYPE.micro} fill={theme.text2} fontWeight={600}>
                {n.id}
              </text>
            </g>
          ))}
        </svg>
      )}
    </ChartFrame>
  )
}
