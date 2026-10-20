'use client'

import type { ChartPayload } from '../types'
import { ChartFrame } from './ChartFrame'
import { alpha, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

interface MergeNode {
  label: string
  distance: number
  children: MergeNode[]
}

/** Dendrogram — merge distances as horizontal linkage lines. */
export function DendrogramChartView({ payload, height }: { payload: ChartPayload; height?: number }) {
  const { ramp, theme } = useWidgetIdentity()
  const roots = (payload.roots ?? []) as unknown as MergeNode[]
  if (!roots?.length) return <ChartFrame title={payload.title} seriesNames={[]} legend={false}>{() => null}</ChartFrame>
  const leaves: Array<{ label: string; depth: number }> = []
  let maxDist = 0
  const walk = (n: MergeNode, depth: number) => {
    maxDist = Math.max(maxDist, n.distance ?? 0)
    if (!n.children?.length) {
      leaves.push({ label: n.label, depth })
      return
    }
    for (const c of n.children) walk(c, depth + 1)
  }
  for (const r of roots) walk(r, 0)
  const H = height ?? Math.max(120, leaves.length * 26 + 34)
  const W = 320
  const labelW = 92
  const xOf = (d: number) => labelW + (d / (maxDist || 1)) * (W - labelW - 14)
  const leafY = new Map<string, number>()
  leaves.forEach((l, i) => leafY.set(l.label, 16 + i * ((H - 48) / Math.max(1, leaves.length - 1 || 1))))
  const colors = ramp(3)
  const lines: React.ReactNode[] = []
  const walkDraw = (n: MergeNode): number => {
    if (!n.children?.length) return leafY.get(n.label) ?? 20
    const kidYs = n.children.map((c) => walkDraw(c))
    const x = xOf(n.distance)
    const top = Math.min(...kidYs)
    const bot = Math.max(...kidYs)
    kidYs.forEach((ky, i) => {
      lines.push(<line key={`${n.label}-v${i}`} x1={xOf(n.children![i].distance)} x2={x} y1={ky} y2={ky} stroke={colors[1] ?? colors[0]} strokeWidth={1.6} />)
    })
    lines.push(<line key={`${n.label}-h`} x1={x} x2={x} y1={top} y2={bot} stroke={colors[1] ?? colors[0]} strokeWidth={1.6} />)
    return (top + bot) / 2
  }
  roots.forEach((r) => walkDraw(r))
  return (
    <ChartFrame title={payload.title} legend={false} height={H}>
      {() => (
        <svg width="100%" height={H} viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="xMidYMid meet" role="img" aria-label="dendrogram">
          {lines}
          {leaves.map((l) => (
            <g key={l.label}>
              <line x1={labelW - 4} x2={labelW} y1={leafY.get(l.label)} y2={leafY.get(l.label)} stroke={theme.text3} />
              <text x={labelW - 8} y={(leafY.get(l.label) ?? 0) + 3} textAnchor="end" fontSize={TYPE.micro} fill={theme.text2}>{l.label}</text>
            </g>
          ))}
          {roots.map((r) => (
            <circle key={r.label} cx={xOf(r.distance)} cy={walkYCache(r, leafY, xOf)} r={3} fill={alpha(colors[0], 0.9)} />
          ))}
        </svg>
      )}
    </ChartFrame>
  )
}

function walkYCache(n: MergeNode, leafY: Map<string, number>, xOf: (d: number) => number): number {
  if (!n.children?.length) return leafY.get(n.label) ?? 20
  const ys = n.children.map((c) => walkYCache(c, leafY, xOf))
  return (Math.min(...ys) + Math.max(...ys)) / 2
}
