'use client'

import type { TreeNode } from '../types'
import type { ChartPayload } from '../types'
import { ChartFrame } from './ChartFrame'
import { inkOn, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

interface Arc {
  d: string
  color: string
  label: string
  value: number
  depth: number
}

function leafSum(node: TreeNode): number {
  if (!node.children || node.children.length === 0) return node.value ?? 0
  return node.children.reduce((a, c) => a + leafSum(c), 0) || (node.value ?? 0)
}

function arcPath(cx: number, cy: number, r0: number, r1: number, a0: number, a1: number): string {
  const p = (r: number, a: number) => `${cx + r * Math.sin(a)} ${cy - r * Math.cos(a)}`
  const large = a1 - a0 > Math.PI ? 1 : 0
  return `M ${p(r1, a0)} A ${r1} ${r1} 0 ${large} 1 ${p(r1, a1)} L ${p(r0, a1)} A ${r0} ${r0} 0 ${large} 0 ${p(r0, a0)} Z`
}

/** Sunburst — hierarchical rings; ring width per depth, palette ramp by depth. */
export function SunburstChartView({ payload, height = 240 }: { payload: ChartPayload; height?: number }) {
  const { ramp, theme } = useWidgetIdentity()
  const root = payload.root
  if (!root) return <ChartFrame title={payload.title} seriesNames={[]} legend={false}>{() => null}</ChartFrame>
  const total = leafSum(root) || 1
  const arcs: Arc[] = []
  const cx = 130
  const cy = (height - 24) / 2
  const maxR = Math.min(cx - 4, cy - 4)
  const ringW = maxR / 3
  const colors = ramp(3)
  const walk = (node: TreeNode, a0: number, a1: number, depth: number) => {
    arcs.push({ d: arcPath(cx, cy, depth * ringW, (depth + 1) * ringW - 1.5, a0, a1), color: colors[Math.min(2, depth)], label: node.label, value: leafSum(node), depth })
    const sum = a1 - a0
    const kids = node.children ?? []
    const kidTotal = kids.reduce((acc, c) => acc + leafSum(c), 0) || 1
    let angle = a0
    for (const kid of kids) {
      const frac = (leafSum(kid) / kidTotal) * sum
      walk(kid, angle, angle + frac, depth + 1)
      angle += frac
    }
  }
  walk(root, 0, Math.PI * 2, 0)
  return (
    <ChartFrame title={payload.title} unit={payload.unit} legend={false} height={height}>
      {({ width }) => (
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <svg width={Math.min(260, width)} height={height - 24} role="img" aria-label="sunburst">
            {arcs.map((a, i) => (
              <path key={i} d={a.d} fill={a.color} fillOpacity={a.depth === 0 ? 0.28 : 0.92 - a.depth * 0.18} stroke={theme.dark ? '#0F1216' : '#fff'} strokeWidth={1.5}>
                <title>{`${a.label}: ${a.value}`}</title>
              </path>
            ))}
            <text x={cx} y={cy + 4} textAnchor="middle" fontSize={TYPE.label} fontWeight={700} fill={theme.text}>
              {total}
            </text>
          </svg>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4, minWidth: 0 }}>
            {(root.children ?? []).slice(0, 6).map((c, i) => (
              <span key={c.label} style={{ fontSize: TYPE.micro, color: theme.text2, display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                <span style={{ width: 8, height: 8, borderRadius: 2, background: colors[1] ?? colors[0], flexShrink: 0, opacity: 0.92 - i * 0.12 }} />
                {c.label}
              </span>
            ))}
          </div>
        </div>
      )}
    </ChartFrame>
  )
}
