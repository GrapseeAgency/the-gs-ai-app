'use client'

import type { ChartPayload } from '../types'
import { ChartFrame } from './ChartFrame'
import { alpha, inkOn, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

/** Depth — live order-book ladder: bids (jade) vs asks (crimson) by price. */
export function DepthChartView({ payload, height = 200 }: { payload: ChartPayload; height?: number }) {
  const { theme } = useWidgetIdentity()
  const bids = ((payload as { bids?: Array<[number, number]> }).bids ?? []).slice(0, 40)
  const asks = ((payload as { asks?: Array<[number, number]> }).asks ?? []).slice(0, 40)
  if (!bids.length && !asks.length) return <ChartFrame title={payload.title} seriesNames={[]} legend={false}>{() => null}</ChartFrame>
  const rows = [...asks].reverse().concat(bids) // asks desc, then bids desc
  const maxQty = Math.max(...rows.map((r) => r[1]), 1)
  const mid = asks.length && bids.length ? (asks[0][0] + bids[0][0]) / 2 : null
  const BID = '#0CA678'
  const ASK = '#E03131'
  return (
    <ChartFrame title={payload.title} unit={payload.unit} legend={false} height={height}>
      {() => (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 1, fontFamily: 'inherit' }}>
          {rows.map(([price, qty], i) => {
            const isBid = mid === null ? i >= (rows.length) / 2 : price <= mid
            const color = isBid ? BID : ASK
            return (
              <div key={`${price}-${i}`} style={{ position: 'relative', height: 16, borderRadius: 3, overflow: 'hidden' }}>
                <span
                  aria-hidden
                  style={{ position: 'absolute', inset: 0, width: `${(qty / maxQty) * 100}%`, background: alpha(color, 0.28), transition: 'width .3s ease' }}
                />
                <span style={{ position: 'relative', display: 'flex', justifyContent: 'space-between', fontSize: TYPE.micro, padding: '0 6px', lineHeight: '16px' }}>
                  <b style={{ color }}>{price.toLocaleString(undefined, { maximumFractionDigits: 4 })}</b>
                  <span style={{ color: theme.text2, fontVariantNumeric: 'tabular-nums' }}>{qty.toLocaleString()}</span>
                </span>
              </div>
            )
          })}
          {mid !== null && (
            <div style={{ textAlign: 'center', fontSize: TYPE.micro, fontWeight: 700, color: inkOn(theme.card), background: theme.raised, borderRadius: 4, padding: '2px 0' }}>
              mid {mid.toLocaleString(undefined, { maximumFractionDigits: 4 })}
            </div>
          )}
        </div>
      )}
    </ChartFrame>
  )
}
