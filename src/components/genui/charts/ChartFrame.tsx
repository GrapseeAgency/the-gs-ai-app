'use client'

/**
 * ChartFrame — the shared scaffolding for every chart component: header, unit
 * legend, responsive measurement and the empty state. Each chart component
 * owns only its marks; this owns the rest, exactly like Android's shared
 * chart scaffolding in WidgetsCharts2.kt.
 */

import { useLayoutEffect, useRef, useState, type ReactNode } from 'react'
import { TitleRow } from '../primitives/TitleRow'
import { EmptyState } from '../primitives/EmptyState'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { TYPE } from '../theme/tokens'

export function ChartFrame({
  title,
  unit,
  seriesNames,
  height = 200,
  emptyLabel = 'No data to plot',
  legend = true,
  children,
}: {
  title?: string
  unit?: string
  seriesNames?: string[]
  height?: number
  emptyLabel?: string
  legend?: boolean
  children: (box: { width: number; height: number }) => ReactNode
}) {
  const { theme, ramp } = useWidgetIdentity()
  const ref = useRef<HTMLDivElement>(null)
  const [width, setWidth] = useState(320)

  useLayoutEffect(() => {
    if (!ref.current) return
    const el = ref.current
    const update = () => setWidth(el.clientWidth)
    update()
    const ro = new ResizeObserver(update)
    ro.observe(el)
    return () => ro.disconnect()
  }, [])

  const hasData = seriesNames === undefined || seriesNames.length > 0

  return (
    <div>
      <TitleRow title={title} right={unit} />
      {!hasData ? (
        <EmptyState label={emptyLabel} />
      ) : (
        <div ref={ref} style={{ width: '100%' }}>
          {children({ width, height })}
        </div>
      )}
      {legend && seriesNames && seriesNames.length > 1 && (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px 14px', marginTop: 8 }}>
          {seriesNames.slice(0, 6).map((name, i) => (
            <span key={`${name}-${i}`} style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: TYPE.micro, color: theme.text2 }}>
              <span style={{ width: 8, height: 8, borderRadius: 2, background: ramp(6)[i] }} />
              {name}
            </span>
          ))}
        </div>
      )}
    </div>
  )
}

export function niceTicks(min: number, max: number, count = 4): number[] {
  if (!Number.isFinite(min) || !Number.isFinite(max) || min === max) return [min]
  const span = max - min
  const step = Math.pow(10, Math.floor(Math.log10(span / count)))
  const err = (span / count) / step
  const mult = err >= 7.5 ? 10 : err >= 3.5 ? 5 : err >= 1.5 ? 2 : 1
  const niceStep = step * mult
  const start = Math.ceil(min / niceStep) * niceStep
  const out: number[] = []
  for (let v = start; v <= max + 1e-9; v += niceStep) out.push(Number(v.toFixed(10)))
  return out
}
