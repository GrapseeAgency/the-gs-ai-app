'use client'

/** The chart kind dispatcher: type -> the 34 chart components + Screenshot 6 Interactive Chrome. */
import { useMemo, useState } from 'react'
import type { ChartPayload } from '../types'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { AreaChartView } from '../charts/AreaChart'
import { BarChartView } from '../charts/BarChart'
import { BubbleChartView } from '../charts/BubbleChart'
import { BoxPlotChartView } from '../charts/BoxPlotChart'
import { BulletChartView } from '../charts/BulletChart'
import { CandlestickChartView } from '../charts/CandlestickChart'
import { ChordChartView } from '../charts/ChordChart'
import { CorrelationChartView } from '../charts/CorrelationChart'
import { DonutChartView, PieChartView } from '../charts/PieChart'
import { FunnelChartView } from '../charts/FunnelChart'
import { GanttChartView } from '../charts/GanttChart'
import { GroupedBarChartView } from '../charts/GroupedBarChart'
import { HBarChartView } from '../charts/HBarChart'
import { HeatmapChartView } from '../charts/HeatmapChart'
import { HistogramChartView } from '../charts/HistogramChart'
import { LineChartView } from '../charts/LineChart'
import { NetworkChartView } from '../charts/NetworkChart'
import { QuadrantChartView } from '../charts/QuadrantChart'
import { RadarChartView } from '../charts/RadarChart'
import { SankeyChartView } from '../charts/SankeyChart'
import { ScatterChartView } from '../charts/ScatterChart'
import { StackedAreaChartView } from '../charts/StackedAreaChart'
import { StackedBarChartView } from '../charts/StackedBarChart'
import { SunburstChartView } from '../charts/SunburstChart'
import { TreemapChartView } from '../charts/TreemapChart'
import { WaterfallChartView } from '../charts/WaterfallChart'
import { WordcloudChartView } from '../charts/WordcloudChart'
import { ViolinChartView } from '../charts/ViolinChart'
import { ContourChartView } from '../charts/ContourChart'
import { ParallelChartView } from '../charts/ParallelChart'
import { DendrogramChartView } from '../charts/DendrogramChart'
import { ForceGraphChartView } from '../charts/ForceGraphChart'
import { DepthChartView } from '../charts/DepthChart'

const SWITCHABLE_TYPES = new Set(['line', 'bar', 'area', 'pie', 'donut'])
const RANGE_OPTIONS = ['Last 7 Days', 'Last 30 Days', 'Last 6 Months', 'Last 12 Months']

function formatCompactMetric(n: number, unit?: string): string {
  const prefix = unit === '$' || unit === '€' || unit === '£' ? unit : ''
  const suffix = unit && !prefix ? ` ${unit}` : ''
  const abs = Math.abs(n)
  if (abs >= 1_000_000) return `${prefix}${(n / 1_000_000).toFixed(1)}M${suffix}`
  if (abs >= 1_000) return `${prefix}${(n / 1_000).toFixed(1)}K${suffix}`
  return `${prefix}${Number.isInteger(n) ? n : n.toFixed(1)}${suffix}`
}

function dispatchChartView(activeType: ChartPayload['type'], payload: ChartPayload) {
  switch (activeType) {
    case 'line': return <LineChartView payload={payload} />
    case 'area': return <AreaChartView payload={payload} />
    case 'stackedArea': return <StackedAreaChartView payload={payload} />
    case 'bar': return <BarChartView payload={payload} />
    case 'histogram': return <HistogramChartView payload={payload} />
    case 'hbar': return <HBarChartView payload={payload} />
    case 'stacked': return <StackedBarChartView payload={payload} />
    case 'grouped': return <GroupedBarChartView payload={payload} />
    case 'pie': return <PieChartView payload={payload} />
    case 'donut': return <DonutChartView payload={payload} />
    case 'scatter': return <ScatterChartView payload={payload} />
    case 'bubble': return <BubbleChartView payload={payload} />
    case 'boxplot': return <BoxPlotChartView payload={payload} />
    case 'heatmap': return <HeatmapChartView payload={payload} />
    case 'correlation': return <CorrelationChartView payload={payload} />
    case 'treemap': return <TreemapChartView payload={payload} />
    case 'waterfall': return <WaterfallChartView payload={payload} />
    case 'candlestick': return <CandlestickChartView payload={payload} />
    case 'gantt': return <GanttChartView payload={payload} />
    case 'funnel': return <FunnelChartView payload={payload} />
    case 'radar': return <RadarChartView payload={payload} />
    case 'chord': return <ChordChartView payload={payload} />
    case 'quadrant': return <QuadrantChartView payload={payload} />
    case 'sankey': return <SankeyChartView payload={payload} />
    case 'network': return <NetworkChartView payload={payload} />
    case 'wordcloud': return <WordcloudChartView payload={payload} />
    case 'sunburst': return <SunburstChartView payload={payload} />
    case 'bullet': return <BulletChartView payload={payload} />
    case 'violin': return <ViolinChartView payload={payload} />
    case 'contour': return <ContourChartView payload={payload} />
    case 'parallel': return <ParallelChartView payload={payload} />
    case 'dendrogram': return <DendrogramChartView payload={payload} />
    case 'force': return <ForceGraphChartView payload={payload} />
    case 'depth': return <DepthChartView payload={payload} />
    default: return <BarChartView payload={payload} />
  }
}

export function ChartWidget({ payload }: { payload: ChartPayload }) {
  const { theme } = useWidgetIdentity()
  const isSwitchable = SWITCHABLE_TYPES.has(payload.type)
  const [activeType, setActiveType] = useState<ChartPayload['type']>(payload.type)
  const [rangeIdx, setRangeIdx] = useState<number>(2)

  const summary = useMemo(() => {
    const firstSeries = payload.series?.[0]?.data ?? []
    if (firstSeries.length < 2) return null
    const vals = firstSeries.map((pt) => Number(pt[1] ?? pt[0] ?? 0) || 0)
    const total = vals.reduce((a, b) => a + b, 0)
    let peakIdx = 0
    vals.forEach((v, i) => {
      if (v > vals[peakIdx]) peakIdx = i
    })
    const first = vals[0] || 1
    const last = vals[vals.length - 1] ?? 0
    const growthPct = ((last - first) / Math.abs(first)) * 100
    const peakLabel = payload.labels?.[peakIdx] ?? `#${peakIdx + 1}`
    return {
      total: formatCompactMetric(total, payload.unit),
      peakLabel,
      peakVal: formatCompactMetric(vals[peakIdx] ?? 0, payload.unit),
      growthPct: `${growthPct >= 0 ? '+' : ''}${growthPct.toFixed(1)}%`,
      growthPos: growthPct >= 0,
    }
  }, [payload.series, payload.labels, payload.unit])

  const rawPayload = payload as unknown as Record<string, unknown>
  const explicitInsights: string[] = Array.isArray(rawPayload.insights)
    ? (rawPayload.insights as unknown[]).map((x) => String(x))
    : typeof rawPayload.insight === 'string'
    ? [rawPayload.insight]
    : typeof rawPayload.notes === 'string'
    ? [rawPayload.notes]
    : []

  if (!isSwitchable) {
    return dispatchChartView(payload.type, payload)
  }

  const tabs: Array<{ id: ChartPayload['type']; icon: string; label: string }> = [
    { id: 'line', icon: '📈', label: 'Line Chart' },
    { id: 'bar', icon: '📊', label: 'Bar Chart' },
    { id: 'area', icon: '📉', label: 'Area Chart' },
    { id: payload.type === 'donut' ? 'donut' : 'pie', icon: '◔', label: 'Pie Chart' },
  ]

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
      {/* Chart Type Switcher Pill Bar + Range Selector (Screenshot 6) */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 6,
          flexWrap: 'wrap',
        }}
      >
        <div
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 4,
            padding: 3,
            borderRadius: 10,
            background: 'rgba(124, 92, 252, 0.10)',
            border: `1px solid ${theme.border}`,
            flexWrap: 'wrap',
          }}
        >
          {tabs.map((tab) => {
            const selected = activeType === tab.id || (tab.id === 'pie' && activeType === 'donut')
            return (
              <button
                key={tab.id}
                type="button"
                onClick={() => setActiveType(tab.id)}
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 4,
                  padding: '4px 9px',
                  borderRadius: 7,
                  border: 'none',
                  background: selected ? '#6C4CF6' : 'transparent',
                  color: selected ? '#FFFFFF' : theme.text2,
                  fontSize: 11,
                  fontWeight: 700,
                  cursor: 'pointer',
                }}
              >
                <span aria-hidden>{tab.icon}</span>
                <span>{tab.label}</span>
              </button>
            )
          })}
        </div>

        <button
          type="button"
          onClick={() => setRangeIdx((i) => (i + 1) % RANGE_OPTIONS.length)}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 5,
            padding: '4px 9px',
            borderRadius: 8,
            border: `1px solid ${theme.border}`,
            background: theme.raised,
            color: theme.text2,
            fontSize: 11,
            fontWeight: 600,
            cursor: 'pointer',
          }}
        >
          <span aria-hidden>📅</span>
          <span>{RANGE_OPTIONS[rangeIdx]} ▾</span>
        </button>
      </div>

      {/* Active Chart Canvas */}
      {dispatchChartView(activeType, { ...payload, type: activeType })}

      {/* 3-Card KPI Summary Strip (Screenshot 6) */}
      {summary && (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(3, minmax(0, 1fr))',
            gap: 8,
          }}
        >
          <div
            style={{
              padding: '8px 10px',
              borderRadius: 10,
              background: theme.raised,
              border: `1px solid ${theme.border}`,
            }}
          >
            <div style={{ fontSize: 10.5, color: theme.text3, fontWeight: 600 }}>Total</div>
            <div style={{ fontSize: 14, fontWeight: 800, color: theme.text, marginTop: 2 }}>{summary.total}</div>
            <div style={{ fontSize: 10.5, fontWeight: 700, color: summary.growthPos ? '#129A5A' : '#E04F5F', marginTop: 2 }}>
              {summary.growthPos ? '↑' : '↓'} {summary.growthPct}
            </div>
          </div>
          <div
            style={{
              padding: '8px 10px',
              borderRadius: 10,
              background: theme.raised,
              border: `1px solid ${theme.border}`,
            }}
          >
            <div style={{ fontSize: 10.5, color: theme.text3, fontWeight: 600 }}>Peak ({summary.peakLabel})</div>
            <div style={{ fontSize: 14, fontWeight: 800, color: theme.text, marginTop: 2 }}>{summary.peakVal}</div>
            <div style={{ fontSize: 10.5, fontWeight: 700, color: '#6C4CF6', marginTop: 2 }}>★ Highest</div>
          </div>
          <div
            style={{
              padding: '8px 10px',
              borderRadius: 10,
              background: theme.raised,
              border: `1px solid ${theme.border}`,
            }}
          >
            <div style={{ fontSize: 10.5, color: theme.text3, fontWeight: 600 }}>Trend</div>
            <div
              style={{
                fontSize: 14,
                fontWeight: 800,
                color: summary.growthPos ? '#129A5A' : '#E04F5F',
                marginTop: 2,
              }}
            >
              {summary.growthPct}
            </div>
            <div style={{ fontSize: 10.5, color: theme.text3, marginTop: 2 }}>{RANGE_OPTIONS[rangeIdx]}</div>
          </div>
        </div>
      )}

      {/* Key Insights Box (Screenshot 6) */}
      {explicitInsights.length > 0 && (
        <div
          style={{
            padding: '10px 12px',
            borderRadius: 12,
            background: 'rgba(124, 92, 252, 0.10)',
            border: '1px solid rgba(124, 92, 252, 0.24)',
            fontSize: 12,
            color: theme.text,
          }}
        >
          <div style={{ fontWeight: 700, marginBottom: 4, display: 'flex', alignItems: 'center', gap: 6 }}>
            <span aria-hidden>💡</span>
            <span>Key Insights</span>
          </div>
          <ul style={{ margin: 0, paddingLeft: 18, display: 'flex', flexDirection: 'column', gap: 3 }}>
            {explicitInsights.map((ins, i) => (
              <li key={i} style={{ color: theme.text2 }}>
                {ins}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}

