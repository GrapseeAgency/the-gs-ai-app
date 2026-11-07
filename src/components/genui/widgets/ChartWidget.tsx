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
import { GlassIcon } from '@/components/nexly/GlassIcon'
import { chartRange } from '@/lib/nexly/chart-range'

const SWITCHABLE_TYPES = new Set(['line', 'bar', 'area', 'pie', 'donut'])

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
  return <ChartControls key={JSON.stringify(payload)} payload={payload} />
}

function ChartControls({ payload }: { payload: ChartPayload }) {
  const { theme } = useWidgetIdentity()
  const isSwitchable = SWITCHABLE_TYPES.has(payload.type)
  const [activeType, setActiveType] = useState<ChartPayload['type']>(payload.type)
  const [rangeIdx, setRangeIdx] = useState<number>(3)

  const range = useMemo(() => chartRange(payload.labels ?? [], Math.max(0, ...(payload.series ?? []).map((series) => series.data?.length ?? 0), payload.labels?.length ?? 0), rangeIdx), [payload, rangeIdx])
  const RANGE_OPTIONS = range.options
  const slicedPayload = useMemo<ChartPayload>(() => ({
    ...payload,
    labels: payload.labels ? range.indices.map((i) => payload.labels?.[i] ?? "") : payload.labels,
    series: payload.series?.map((s) => !Array.isArray(s.data) ? s : ({ ...s, data: range.indices.filter((i) => i < s.data.length).map((i) => s.data[i]) })),
  }), [payload, range])

  const summary = useMemo(() => {
    const firstSeries = slicedPayload.series?.[0]?.data ?? []
    if (firstSeries.length < 2) return null
    const vals = firstSeries.map((pt) => pt[pt.length - 1])
    if (vals.some(value => !Number.isFinite(value))) return null
    const total = vals.reduce((a, b) => a + b, 0)
    let peakIdx = 0
    let minIdx = 0
    vals.forEach((v, i) => {
      if (v > vals[peakIdx]) peakIdx = i
      if (v < vals[minIdx]) minIdx = i
    })
    const first = vals[0]
    const last = vals[vals.length - 1] ?? 0
    const slices = activeType === 'pie' || activeType === 'donut'
    const growthPct = slices || first === 0 ? null : ((last - first) / Math.abs(first)) * 100
    const peakLabel = slicedPayload.labels?.[peakIdx] ?? String(firstSeries[peakIdx]?.[0] ?? `#${peakIdx + 1}`)
    const minLabel = slicedPayload.labels?.[minIdx] ?? String(firstSeries[minIdx]?.[0] ?? `#${minIdx + 1}`)
    return {
      seriesName: slicedPayload.series?.[0]?.name || 'Series 1',
      total: formatCompactMetric(total, slicedPayload.unit),
      peakLabel,
      peakVal: formatCompactMetric(vals[peakIdx] ?? 0, slicedPayload.unit),
      minLabel,
      minVal: formatCompactMetric(vals[minIdx] ?? 0, slicedPayload.unit),
      growthPct: growthPct === null ? (slices ? 'N/A (slice values)' : 'N/A (zero baseline)') : `${growthPct >= 0 ? '+' : ''}${growthPct.toFixed(1)}%`,
      growthPos: growthPct !== null && growthPct >= 0,
      growthDefined: growthPct !== null,
      pointCount: vals.length,
    }
  }, [slicedPayload.series, slicedPayload.labels, slicedPayload.unit, activeType])

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
    { id: 'line', icon: 'chart-line', label: 'Line Chart' },
    { id: 'bar', icon: 'chart-bar', label: 'Bar Chart' },
    { id: 'area', icon: 'chart-area', label: 'Area Chart' },
    { id: payload.type === 'donut' ? 'donut' : 'pie', icon: 'chart-pie', label: 'Pie Chart' },
  ]

  const computedInsights =
    summary
      ? [
          `${summary.seriesName}: ${summary.peakLabel} recorded the highest value (${summary.peakVal}) across ${summary.pointCount} points (${RANGE_OPTIONS[rangeIdx]}).`,
          `First-to-last change for ${summary.seriesName} is ${summary.growthPct}; sum of observations is ${summary.total} (low: ${summary.minLabel} at ${summary.minVal}).`,
        ]
      : rangeIdx === 3 ? explicitInsights : []

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
                  gap: 5,
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
                <GlassIcon name={tab.icon} tone={selected ? 'indigo' : 'lavender'} size={16} glyph={10} />
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
          <GlassIcon name="calendar" tone="lavender" size={16} glyph={10} />
          <span>{RANGE_OPTIONS[rangeIdx]} ▾</span>
        </button>
      </div>

      {/* Active Chart Canvas */}
      {range.indices.length ? dispatchChartView(activeType, { ...slicedPayload, type: activeType }) : <div>No observations in this range.</div>}

      {summary && <div style={{ fontSize: 11, color: theme.text2 }}>Summary: {summary.seriesName}</div>}
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
            <div style={{ fontSize: 10.5, color: theme.text3, fontWeight: 600 }}>Sum</div>
            <div style={{ fontSize: 14, fontWeight: 800, color: theme.text, marginTop: 2 }}>{summary.total}</div>
            <div style={{ fontSize: 10.5, fontWeight: 700, color: !summary.growthDefined ? theme.text3 : summary.growthPos ? '#129A5A' : '#E04F5F', marginTop: 2 }}>
              {summary.growthDefined ? (summary.growthPos ? '↑' : '↓') : ''} {summary.growthPct}
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
            <div style={{ display: 'inline-flex', alignItems: 'center', gap: 3, fontSize: 10.5, fontWeight: 700, color: '#6C4CF6', marginTop: 2 }}>
              <GlassIcon name="trophy" tone="amber" size={14} glyph={9} />
              <span>Highest</span>
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
            <div style={{ fontSize: 10.5, color: theme.text3, fontWeight: 600 }}>Trend</div>
            <div
              style={{
                fontSize: 14,
                fontWeight: 800,
                color: !summary.growthDefined ? theme.text3 : summary.growthPos ? '#129A5A' : '#E04F5F',
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
      {computedInsights.length > 0 && (
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
            <GlassIcon name="bulb" tone="lavender" size={20} glyph={11} />
            <span>Key Insights</span>
          </div>
          <ul style={{ margin: 0, paddingLeft: 18, display: 'flex', flexDirection: 'column', gap: 3 }}>
            {computedInsights.map((ins, i) => (
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

