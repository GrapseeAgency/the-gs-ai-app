'use client'

/** The chart kind dispatcher: type -> the 28 chart components. */
import type { ChartPayload } from '../types'
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

export function ChartWidget({ payload }: { payload: ChartPayload }) {
  switch (payload.type) {
    case 'line': return <LineChartView payload={payload} />
    case 'area': return <AreaChartView payload={payload} />
    case 'stackedArea': return <StackedAreaChartView payload={payload} />
    case 'bar': case 'histogram': return <BarChartView payload={payload} />
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
