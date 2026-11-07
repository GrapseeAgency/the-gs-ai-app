"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.ChartWidget = ChartWidget;
const jsx_runtime_1 = require("react/jsx-runtime");
/** The chart kind dispatcher: type -> the 34 chart components + Screenshot 6 Interactive Chrome. */
const react_1 = require("react");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const AreaChart_1 = require("../charts/AreaChart");
const BarChart_1 = require("../charts/BarChart");
const BubbleChart_1 = require("../charts/BubbleChart");
const BoxPlotChart_1 = require("../charts/BoxPlotChart");
const BulletChart_1 = require("../charts/BulletChart");
const CandlestickChart_1 = require("../charts/CandlestickChart");
const ChordChart_1 = require("../charts/ChordChart");
const CorrelationChart_1 = require("../charts/CorrelationChart");
const PieChart_1 = require("../charts/PieChart");
const FunnelChart_1 = require("../charts/FunnelChart");
const GanttChart_1 = require("../charts/GanttChart");
const GroupedBarChart_1 = require("../charts/GroupedBarChart");
const HBarChart_1 = require("../charts/HBarChart");
const HeatmapChart_1 = require("../charts/HeatmapChart");
const HistogramChart_1 = require("../charts/HistogramChart");
const LineChart_1 = require("../charts/LineChart");
const NetworkChart_1 = require("../charts/NetworkChart");
const QuadrantChart_1 = require("../charts/QuadrantChart");
const RadarChart_1 = require("../charts/RadarChart");
const SankeyChart_1 = require("../charts/SankeyChart");
const ScatterChart_1 = require("../charts/ScatterChart");
const StackedAreaChart_1 = require("../charts/StackedAreaChart");
const StackedBarChart_1 = require("../charts/StackedBarChart");
const SunburstChart_1 = require("../charts/SunburstChart");
const TreemapChart_1 = require("../charts/TreemapChart");
const WaterfallChart_1 = require("../charts/WaterfallChart");
const WordcloudChart_1 = require("../charts/WordcloudChart");
const ViolinChart_1 = require("../charts/ViolinChart");
const ContourChart_1 = require("../charts/ContourChart");
const ParallelChart_1 = require("../charts/ParallelChart");
const DendrogramChart_1 = require("../charts/DendrogramChart");
const ForceGraphChart_1 = require("../charts/ForceGraphChart");
const DepthChart_1 = require("../charts/DepthChart");
const GlassIcon_1 = require("@/components/nexly/GlassIcon");
const SWITCHABLE_TYPES = new Set(['line', 'bar', 'area', 'pie', 'donut']);
const RANGE_OPTIONS = ['Last 3M', 'Last 6M', 'YTD', 'All'];
function formatCompactMetric(n, unit) {
    const prefix = unit === '$' || unit === '€' || unit === '£' ? unit : '';
    const suffix = unit && !prefix ? ` ${unit}` : '';
    const abs = Math.abs(n);
    if (abs >= 1000000)
        return `${prefix}${(n / 1000000).toFixed(1)}M${suffix}`;
    if (abs >= 1000)
        return `${prefix}${(n / 1000).toFixed(1)}K${suffix}`;
    return `${prefix}${Number.isInteger(n) ? n : n.toFixed(1)}${suffix}`;
}
function dispatchChartView(activeType, payload) {
    switch (activeType) {
        case 'line': return (0, jsx_runtime_1.jsx)(LineChart_1.LineChartView, { payload: payload });
        case 'area': return (0, jsx_runtime_1.jsx)(AreaChart_1.AreaChartView, { payload: payload });
        case 'stackedArea': return (0, jsx_runtime_1.jsx)(StackedAreaChart_1.StackedAreaChartView, { payload: payload });
        case 'bar': return (0, jsx_runtime_1.jsx)(BarChart_1.BarChartView, { payload: payload });
        case 'histogram': return (0, jsx_runtime_1.jsx)(HistogramChart_1.HistogramChartView, { payload: payload });
        case 'hbar': return (0, jsx_runtime_1.jsx)(HBarChart_1.HBarChartView, { payload: payload });
        case 'stacked': return (0, jsx_runtime_1.jsx)(StackedBarChart_1.StackedBarChartView, { payload: payload });
        case 'grouped': return (0, jsx_runtime_1.jsx)(GroupedBarChart_1.GroupedBarChartView, { payload: payload });
        case 'pie': return (0, jsx_runtime_1.jsx)(PieChart_1.PieChartView, { payload: payload });
        case 'donut': return (0, jsx_runtime_1.jsx)(PieChart_1.DonutChartView, { payload: payload });
        case 'scatter': return (0, jsx_runtime_1.jsx)(ScatterChart_1.ScatterChartView, { payload: payload });
        case 'bubble': return (0, jsx_runtime_1.jsx)(BubbleChart_1.BubbleChartView, { payload: payload });
        case 'boxplot': return (0, jsx_runtime_1.jsx)(BoxPlotChart_1.BoxPlotChartView, { payload: payload });
        case 'heatmap': return (0, jsx_runtime_1.jsx)(HeatmapChart_1.HeatmapChartView, { payload: payload });
        case 'correlation': return (0, jsx_runtime_1.jsx)(CorrelationChart_1.CorrelationChartView, { payload: payload });
        case 'treemap': return (0, jsx_runtime_1.jsx)(TreemapChart_1.TreemapChartView, { payload: payload });
        case 'waterfall': return (0, jsx_runtime_1.jsx)(WaterfallChart_1.WaterfallChartView, { payload: payload });
        case 'candlestick': return (0, jsx_runtime_1.jsx)(CandlestickChart_1.CandlestickChartView, { payload: payload });
        case 'gantt': return (0, jsx_runtime_1.jsx)(GanttChart_1.GanttChartView, { payload: payload });
        case 'funnel': return (0, jsx_runtime_1.jsx)(FunnelChart_1.FunnelChartView, { payload: payload });
        case 'radar': return (0, jsx_runtime_1.jsx)(RadarChart_1.RadarChartView, { payload: payload });
        case 'chord': return (0, jsx_runtime_1.jsx)(ChordChart_1.ChordChartView, { payload: payload });
        case 'quadrant': return (0, jsx_runtime_1.jsx)(QuadrantChart_1.QuadrantChartView, { payload: payload });
        case 'sankey': return (0, jsx_runtime_1.jsx)(SankeyChart_1.SankeyChartView, { payload: payload });
        case 'network': return (0, jsx_runtime_1.jsx)(NetworkChart_1.NetworkChartView, { payload: payload });
        case 'wordcloud': return (0, jsx_runtime_1.jsx)(WordcloudChart_1.WordcloudChartView, { payload: payload });
        case 'sunburst': return (0, jsx_runtime_1.jsx)(SunburstChart_1.SunburstChartView, { payload: payload });
        case 'bullet': return (0, jsx_runtime_1.jsx)(BulletChart_1.BulletChartView, { payload: payload });
        case 'violin': return (0, jsx_runtime_1.jsx)(ViolinChart_1.ViolinChartView, { payload: payload });
        case 'contour': return (0, jsx_runtime_1.jsx)(ContourChart_1.ContourChartView, { payload: payload });
        case 'parallel': return (0, jsx_runtime_1.jsx)(ParallelChart_1.ParallelChartView, { payload: payload });
        case 'dendrogram': return (0, jsx_runtime_1.jsx)(DendrogramChart_1.DendrogramChartView, { payload: payload });
        case 'force': return (0, jsx_runtime_1.jsx)(ForceGraphChart_1.ForceGraphChartView, { payload: payload });
        case 'depth': return (0, jsx_runtime_1.jsx)(DepthChart_1.DepthChartView, { payload: payload });
        default: return (0, jsx_runtime_1.jsx)(BarChart_1.BarChartView, { payload: payload });
    }
}
function ChartWidget({ payload }) {
    const { theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const isSwitchable = SWITCHABLE_TYPES.has(payload.type);
    const [activeType, setActiveType] = (0, react_1.useState)(payload.type);
    const [rangeIdx, setRangeIdx] = (0, react_1.useState)(1);
    const slicedPayload = (0, react_1.useMemo)(() => {
        const rawFirst = payload.series?.[0]?.data ?? [];
        const totalPts = Math.max(rawFirst.length, payload.labels?.length ?? 0);
        if (totalPts <= 2)
            return payload;
        const windowSize = rangeIdx === 0
            ? Math.min(totalPts, Math.max(2, Math.min(3, Math.ceil(totalPts / 2))))
            : rangeIdx === 1
                ? Math.min(totalPts, Math.max(2, Math.min(6, Math.ceil((totalPts * 3) / 4))))
                : rangeIdx === 2
                    ? Math.min(totalPts, Math.max(2, Math.min(9, totalPts - 1)))
                    : totalPts;
        const startIdx = Math.max(0, totalPts - windowSize);
        return {
            ...payload,
            labels: payload.labels ? payload.labels.slice(startIdx) : payload.labels,
            series: payload.series?.map((s) => ({
                ...s,
                data: s.data.slice(startIdx),
            })),
        };
    }, [payload, rangeIdx]);
    const summary = (0, react_1.useMemo)(() => {
        const firstSeries = slicedPayload.series?.[0]?.data ?? [];
        if (firstSeries.length < 2)
            return null;
        const vals = firstSeries.map((pt) => Number(pt[1] ?? pt[0] ?? 0) || 0);
        const total = vals.reduce((a, b) => a + b, 0);
        let peakIdx = 0;
        let minIdx = 0;
        vals.forEach((v, i) => {
            if (v > vals[peakIdx])
                peakIdx = i;
            if (v < vals[minIdx])
                minIdx = i;
        });
        const first = vals[0] || 1;
        const last = vals[vals.length - 1] ?? 0;
        const growthPct = ((last - first) / Math.abs(first)) * 100;
        const peakLabel = slicedPayload.labels?.[peakIdx] ?? String(firstSeries[peakIdx]?.[0] ?? `#${peakIdx + 1}`);
        const minLabel = slicedPayload.labels?.[minIdx] ?? String(firstSeries[minIdx]?.[0] ?? `#${minIdx + 1}`);
        return {
            total: formatCompactMetric(total, slicedPayload.unit),
            peakLabel,
            peakVal: formatCompactMetric(vals[peakIdx] ?? 0, slicedPayload.unit),
            minLabel,
            minVal: formatCompactMetric(vals[minIdx] ?? 0, slicedPayload.unit),
            growthPct: `${growthPct >= 0 ? '+' : ''}${growthPct.toFixed(1)}%`,
            growthPos: growthPct >= 0,
            pointCount: vals.length,
        };
    }, [slicedPayload.series, slicedPayload.labels, slicedPayload.unit]);
    const rawPayload = payload;
    const explicitInsights = Array.isArray(rawPayload.insights)
        ? rawPayload.insights.map((x) => String(x))
        : typeof rawPayload.insight === 'string'
            ? [rawPayload.insight]
            : typeof rawPayload.notes === 'string'
                ? [rawPayload.notes]
                : [];
    if (!isSwitchable) {
        return dispatchChartView(payload.type, payload);
    }
    const tabs = [
        { id: 'line', icon: 'chart-line', label: 'Line Chart' },
        { id: 'bar', icon: 'chart-bar', label: 'Bar Chart' },
        { id: 'area', icon: 'chart-area', label: 'Area Chart' },
        { id: payload.type === 'donut' ? 'donut' : 'pie', icon: 'chart-pie', label: 'Pie Chart' },
    ];
    const computedInsights = explicitInsights.length > 0
        ? explicitInsights
        : summary
            ? [
                `${summary.peakLabel} recorded the highest value (${summary.peakVal}) across ${summary.pointCount} points (${RANGE_OPTIONS[rangeIdx]}).`,
                `Net trajectory is ${summary.growthPct} with cumulative volume ${summary.total} (low: ${summary.minLabel} at ${summary.minVal}).`,
            ]
            : [];
    return ((0, jsx_runtime_1.jsxs)("div", { style: { display: 'flex', flexDirection: 'column', gap: 10 }, children: [(0, jsx_runtime_1.jsxs)("div", { style: {
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    gap: 6,
                    flexWrap: 'wrap',
                }, children: [(0, jsx_runtime_1.jsx)("div", { style: {
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: 4,
                            padding: 3,
                            borderRadius: 10,
                            background: 'rgba(124, 92, 252, 0.10)',
                            border: `1px solid ${theme.border}`,
                            flexWrap: 'wrap',
                        }, children: tabs.map((tab) => {
                            const selected = activeType === tab.id || (tab.id === 'pie' && activeType === 'donut');
                            return ((0, jsx_runtime_1.jsxs)("button", { type: "button", onClick: () => setActiveType(tab.id), style: {
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
                                }, children: [(0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { name: tab.icon, tone: selected ? 'indigo' : 'lavender', size: 16, glyph: 10 }), (0, jsx_runtime_1.jsx)("span", { children: tab.label })] }, tab.id));
                        }) }), (0, jsx_runtime_1.jsxs)("button", { type: "button", onClick: () => setRangeIdx((i) => (i + 1) % RANGE_OPTIONS.length), style: {
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
                        }, children: [(0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { name: "calendar", tone: "lavender", size: 16, glyph: 10 }), (0, jsx_runtime_1.jsxs)("span", { children: [RANGE_OPTIONS[rangeIdx], " \u25BE"] })] })] }), dispatchChartView(activeType, { ...slicedPayload, type: activeType }), summary && ((0, jsx_runtime_1.jsxs)("div", { style: {
                    display: 'grid',
                    gridTemplateColumns: 'repeat(3, minmax(0, 1fr))',
                    gap: 8,
                }, children: [(0, jsx_runtime_1.jsxs)("div", { style: {
                            padding: '8px 10px',
                            borderRadius: 10,
                            background: theme.raised,
                            border: `1px solid ${theme.border}`,
                        }, children: [(0, jsx_runtime_1.jsx)("div", { style: { fontSize: 10.5, color: theme.text3, fontWeight: 600 }, children: "Total" }), (0, jsx_runtime_1.jsx)("div", { style: { fontSize: 14, fontWeight: 800, color: theme.text, marginTop: 2 }, children: summary.total }), (0, jsx_runtime_1.jsxs)("div", { style: { fontSize: 10.5, fontWeight: 700, color: summary.growthPos ? '#129A5A' : '#E04F5F', marginTop: 2 }, children: [summary.growthPos ? '↑' : '↓', " ", summary.growthPct] })] }), (0, jsx_runtime_1.jsxs)("div", { style: {
                            padding: '8px 10px',
                            borderRadius: 10,
                            background: theme.raised,
                            border: `1px solid ${theme.border}`,
                        }, children: [(0, jsx_runtime_1.jsxs)("div", { style: { fontSize: 10.5, color: theme.text3, fontWeight: 600 }, children: ["Peak (", summary.peakLabel, ")"] }), (0, jsx_runtime_1.jsx)("div", { style: { fontSize: 14, fontWeight: 800, color: theme.text, marginTop: 2 }, children: summary.peakVal }), (0, jsx_runtime_1.jsxs)("div", { style: { display: 'inline-flex', alignItems: 'center', gap: 3, fontSize: 10.5, fontWeight: 700, color: '#6C4CF6', marginTop: 2 }, children: [(0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { name: "trophy", tone: "amber", size: 14, glyph: 9 }), (0, jsx_runtime_1.jsx)("span", { children: "Highest" })] })] }), (0, jsx_runtime_1.jsxs)("div", { style: {
                            padding: '8px 10px',
                            borderRadius: 10,
                            background: theme.raised,
                            border: `1px solid ${theme.border}`,
                        }, children: [(0, jsx_runtime_1.jsx)("div", { style: { fontSize: 10.5, color: theme.text3, fontWeight: 600 }, children: "Trend" }), (0, jsx_runtime_1.jsx)("div", { style: {
                                    fontSize: 14,
                                    fontWeight: 800,
                                    color: summary.growthPos ? '#129A5A' : '#E04F5F',
                                    marginTop: 2,
                                }, children: summary.growthPct }), (0, jsx_runtime_1.jsx)("div", { style: { fontSize: 10.5, color: theme.text3, marginTop: 2 }, children: RANGE_OPTIONS[rangeIdx] })] })] })), computedInsights.length > 0 && ((0, jsx_runtime_1.jsxs)("div", { style: {
                    padding: '10px 12px',
                    borderRadius: 12,
                    background: 'rgba(124, 92, 252, 0.10)',
                    border: '1px solid rgba(124, 92, 252, 0.24)',
                    fontSize: 12,
                    color: theme.text,
                }, children: [(0, jsx_runtime_1.jsxs)("div", { style: { fontWeight: 700, marginBottom: 4, display: 'flex', alignItems: 'center', gap: 6 }, children: [(0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { name: "bulb", tone: "lavender", size: 20, glyph: 11 }), (0, jsx_runtime_1.jsx)("span", { children: "Key Insights" })] }), (0, jsx_runtime_1.jsx)("ul", { style: { margin: 0, paddingLeft: 18, display: 'flex', flexDirection: 'column', gap: 3 }, children: computedInsights.map((ins, i) => ((0, jsx_runtime_1.jsx)("li", { style: { color: theme.text2 }, children: ins }, i))) })] }))] }));
}
