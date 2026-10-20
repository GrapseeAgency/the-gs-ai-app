'use client'

import { Funnel, FunnelChart as RcFunnel, LabelList, ResponsiveContainer, Tooltip } from 'recharts'
import type { ChartPayload } from '../types'
import { ChartFrame } from './ChartFrame'
import { inkOn, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

/** Funnel — stage-by-stage drop-off (acquisition, pipelines). */
export function FunnelChartView({ payload, height }: { payload: ChartPayload; height?: number }) {
  const { ramp, theme } = useWidgetIdentity()
  const labels = payload.labels ?? []
  const values = payload.series?.[0]?.data ?? []
  const data = values.map((d, i) => ({ name: labels[i] ?? `Stage ${i + 1}`, value: d[d.length - 1] }))
  const h = height ?? Math.max(140, data.length * 42 + 20)
  const colors = ramp(data.length)
  return (
    <ChartFrame title={payload.title} unit={payload.unit} legend={false} height={h}>
      {({ width }) => (
        <ResponsiveContainer width={width} height={h}>
          <RcFunnel>
            <Funnel dataKey="value" data={data} isAnimationActive>
              {data.map((_, i) => (
                <LabelList key={i} position="center" fill={inkOn(colors[i])} stroke="none" dataKey="name" fontSize={TYPE.micro} fontWeight={600} />
              ))}
            </Funnel>
            <Tooltip contentStyle={{ background: theme.card, border: `1px solid ${theme.border}`, borderRadius: 12, fontSize: TYPE.label, color: theme.text }} />
          </RcFunnel>
        </ResponsiveContainer>
      )}
    </ChartFrame>
  )
}
