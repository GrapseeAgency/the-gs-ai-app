package com.grapsee.gsai.ui.chat.content

// ---------------------------------------------------------------------------
// Advanced charts (0.79.0) — the chart kinds the base renderer did not have:
// grouped bar, box plot, heatmap, treemap, waterfall, candlestick, gantt and
// correlation matrix. Same ChartWidget model, same card shell, same degrade
// contract (bad JSON never reaches here).
// ---------------------------------------------------------------------------

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.theme.GsTheme
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

// --- shared ------------------------------------------------------------------

private data class BoxStats(val min: Float, val q1: Float, val med: Float, val q3: Float, val max: Float)

private fun boxStats(values: List<Float>): BoxStats? {
    if (values.isEmpty()) return null
    val s = values.sorted()
    fun q(p: Float): Float {
        val idx = (s.size - 1) * p
        val lo = floor(idx).toInt()
        val hi = ceil(idx).toInt().coerceAtMost(s.lastIndex)
        return if (lo == hi) s[lo] else s[lo] + (s[hi] - s[lo]) * (idx - lo)
    }
    return BoxStats(s.first(), q(0.25f), q(0.5f), q(0.75f), s.last())
}

private fun categoryLabels(w: ChartWidget, count: Int): List<String> =
    if (w.labels.size >= count) w.labels else (0 until count).map { "S${it + 1}" }

// --- grouped bar -------------------------------------------------------------

@Composable
internal fun GroupedBarChart(w: ChartWidget) {
    var animate by remember { mutableStateOf(false) }
    LaunchedEffect(w) { animate = true }
    val progress by animateFloatAsState(if (animate) 1f else 0f, tween(700), label = "grouped")
    val colors = w.series.indices.map { seriesColor(it) }
    val gridColor = GsTheme.colors.divider
    val cats = w.series.maxOfOrNull { it.data.size } ?: 0
    if (cats == 0) return
    val maxV = (0 until cats).maxOfOrNull { ci ->
        w.series.maxOfOrNull { s -> s.data.getOrNull(ci)?.getOrElse(1) { 0f } ?: 0f } ?: 0f
    }?.takeIf { it > 0f } ?: 1f
    val labels = categoryLabels(w, cats)
    Canvas(Modifier.fillMaxWidth().height(180.dp).padding(top = 8.dp)) {
        val cw = size.width
        val ch = size.height
        repeat(4) { g ->
            val y = ch * g / 3f
            drawLine(gridColor, Offset(0f, y), Offset(cw, y), strokeWidth = 1f)
        }
        val groupW = cw / cats.coerceAtLeast(1)
        val n = w.series.size.coerceAtLeast(1)
        val barW = groupW * 0.82f / n
        w.series.forEachIndexed { si, s ->
            s.data.forEachIndexed { ci, point ->
                val v = point.getOrElse(1) { 0f }.coerceAtLeast(0f)
                val h = ch * (v / maxV) * progress
                val x = ci * groupW + groupW * 0.09f + si * barW
                drawRect(colors[si % colors.size], topLeft = Offset(x, ch - h), size = Size(barW * 0.9f, h))
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.take(8).forEach {
            Text(it, style = MaterialTheme.typography.labelSmall, color = GsTheme.colors.textTertiary)
        }
    }
    SeriesLegend(w)
}

// --- box plot ----------------------------------------------------------------

@Composable
internal fun BoxPlotChart(w: ChartWidget) {
    var animate by remember { mutableStateOf(false) }
    LaunchedEffect(w) { animate = true }
    val progress by animateFloatAsState(if (animate) 1f else 0f, tween(700), label = "box")
    val colors = w.series.indices.map { seriesColor(it) }
    val gridColor = GsTheme.colors.divider
    val boxes = w.series.map { boxStats(it.data.map { p -> p.getOrElse(1) { 0f } }) }
    val allMin = boxes.filterNotNull().minOfOrNull { it.min } ?: 0f
    val allMax = boxes.filterNotNull().maxOfOrNull { it.max } ?: 1f
    val span = (allMax - allMin).takeIf { it > 0f } ?: 1f
    if (boxes.all { it == null }) return
    val labels = categoryLabels(w, w.series.size)
    Canvas(Modifier.fillMaxWidth().height(180.dp).padding(top = 8.dp)) {
        val cw = size.width
        val ch = size.height
        repeat(4) { g ->
            val y = ch * g / 3f
            drawLine(gridColor, Offset(0f, y), Offset(cw, y), strokeWidth = 1f)
        }
        val slot = cw / w.series.size.coerceAtLeast(1)
        fun yOf(v: Float) = ch * (1f - ((v - allMin) / span) * progress)
        boxes.forEachIndexed { i, st ->
            if (st == null) return@forEachIndexed
            val cx = slot * i + slot / 2f
            val bw = slot * 0.42f
            val col = colors[i % colors.size]
            drawLine(col, Offset(cx, yOf(st.max)), Offset(cx, yOf(st.min)), strokeWidth = 2f)
            drawLine(col, Offset(cx - bw * 0.3f, yOf(st.max)), Offset(cx + bw * 0.3f, yOf(st.max)), strokeWidth = 2f)
            drawLine(col, Offset(cx - bw * 0.3f, yOf(st.min)), Offset(cx + bw * 0.3f, yOf(st.min)), strokeWidth = 2f)
            val top = yOf(st.q3)
            val bottom = yOf(st.q1)
            drawRect(
                col.copy(alpha = 0.35f),
                topLeft = Offset(cx - bw / 2f, top),
                size = Size(bw, (bottom - top).coerceAtLeast(1f))
            )
            drawLine(col, Offset(cx - bw / 2f, yOf(st.med)), Offset(cx + bw / 2f, yOf(st.med)), strokeWidth = 2.5f)
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.take(8).forEach {
            Text(it, style = MaterialTheme.typography.labelSmall, color = GsTheme.colors.textTertiary)
        }
    }
}

// --- heatmap / correlation matrix -------------------------------------------

@Composable
internal fun HeatmapChart(w: ChartWidget, diverging: Boolean = false) {
    var animate by remember { mutableStateOf(false) }
    LaunchedEffect(w) { animate = true }
    val progress by animateFloatAsState(if (animate) 1f else 0f, tween(700), label = "heat")
    val accent = GsTheme.colors.accent
    val success = GsTheme.colors.success
    val warning = GsTheme.colors.warning
    val rows = w.series
    val cols = rows.maxOfOrNull { it.data.size } ?: 0
    if (cols == 0 || rows.isEmpty()) return
    val values = rows.flatMap { s -> s.data.map { it.getOrElse(1) { 0f } } }
    val vmax = values.maxOrNull() ?: 1f
    val vmin = values.minOrNull() ?: 0f
    val span = (vmax - vmin).takeIf { it > 0f } ?: 1f
    Canvas(
        Modifier.fillMaxWidth()
            .height(if (rows.size > 6) 220.dp else 160.dp)
            .padding(top = 8.dp)
    ) {
        val cw = size.width
        val ch = size.height
        val cellW = cw / cols
        val cellH = ch / rows.size
        rows.forEachIndexed { ri, s ->
            s.data.forEachIndexed { ci, point ->
                val v = point.getOrElse(1) { 0f }
                val t = if (diverging) ((v + 1f) / 2f).coerceIn(0f, 1f)
                else ((v - vmin) / span).coerceIn(0f, 1f)
                val col = if (diverging) {
                    if (t < 0.5f) success.copy(alpha = (0.22f + (0.5f - t) * 1.5f) * progress)
                    else warning.copy(alpha = (0.22f + (t - 0.5f) * 1.5f) * progress)
                } else {
                    accent.copy(alpha = (0.10f + 0.90f * t) * progress)
                }
                drawRect(
                    col,
                    topLeft = Offset(ci * cellW, ri * cellH),
                    size = Size((cellW - 1f).coerceAtLeast(1f), (cellH - 1f).coerceAtLeast(1f))
                )
            }
        }
    }
    if (w.labels.isNotEmpty()) {
        Row(
            Modifier.fillMaxWidth().padding(top = 5.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            w.labels.take(12).forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = GsTheme.colors.textTertiary)
            }
        }
    }
    SeriesLegend(w)
}

// --- treemap -----------------------------------------------------------------

@Composable
internal fun TreemapChart(w: ChartWidget) {
    var animate by remember { mutableStateOf(false) }
    LaunchedEffect(w) { animate = true }
    val progress by animateFloatAsState(if (animate) 1f else 0f, tween(700), label = "treemap")
    val palette = (0 until 5).map { seriesColor(it) }
    val s = w.series.firstOrNull() ?: return
    val vals = s.data.map { it.getOrElse(1) { 0f }.coerceAtLeast(0f) }
    val total = vals.sum().takeIf { it > 0f } ?: 1f
    if (vals.isEmpty()) return
    val labels = categoryLabels(w, vals.size)
    Canvas(Modifier.fillMaxWidth().height(170.dp).padding(top = 8.dp)) {
        var x = 0f
        val h = size.height
        vals.forEachIndexed { i, v ->
            val ww = size.width * (v / total) * progress
            drawRect(palette[i % palette.size], topLeft = Offset(x, 0f), size = Size(ww.coerceAtLeast(1f), h))
            x += ww
        }
    }
    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        labels.take(6).forEachIndexed { i, label ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.width(10.dp).height(3.dp).clip(RoundedCornerShape(2.dp))
                        .background(palette[i % palette.size])
                )
                Text(
                    "$label — ${formatChartValue(vals.getOrElse(i) { 0f })}",
                    style = MaterialTheme.typography.labelSmall,
                    color = GsTheme.colors.textSecondary,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }
}

// --- waterfall ---------------------------------------------------------------

@Composable
internal fun WaterfallChart(w: ChartWidget) {
    var animate by remember { mutableStateOf(false) }
    LaunchedEffect(w) { animate = true }
    val progress by animateFloatAsState(if (animate) 1f else 0f, tween(700), label = "waterfall")
    val success = GsTheme.colors.success
    val warning = GsTheme.colors.warning
    val gridColor = GsTheme.colors.divider
    val s = w.series.firstOrNull() ?: return
    val deltas = s.data.map { it.getOrElse(1) { 0f } }
    if (deltas.isEmpty()) return
    var run = 0f
    val steps = deltas.map { d -> val a = run; run += d; a to run }
    val minV = minOf(0f, steps.minOfOrNull { minOf(it.first, it.second) } ?: 0f)
    val maxV = maxOf(0f, steps.maxOfOrNull { maxOf(it.first, it.second) } ?: 0f)
    val span = (maxV - minV).takeIf { it > 0f } ?: 1f
    val labels = categoryLabels(w, steps.size)
    Canvas(Modifier.fillMaxWidth().height(180.dp).padding(top = 8.dp)) {
        val cw = size.width
        val ch = size.height
        repeat(4) { g ->
            val y = ch * g / 3f
            drawLine(gridColor, Offset(0f, y), Offset(cw, y), strokeWidth = 1f)
        }
        val slot = cw / steps.size.coerceAtLeast(1)
        val bw = slot * 0.58f
        fun yOf(v: Float) = ch * (1f - (v - minV) / span)
        steps.forEachIndexed { i, (a, b) ->
            val top = yOf(maxOf(a, b))
            val bottom = yOf(minOf(a, b))
            val col = if (b >= a) success else warning
            drawRect(
                col.copy(alpha = 0.85f),
                topLeft = Offset(i * slot + (slot - bw) / 2f, top),
                size = Size(bw, (bottom - top).coerceAtLeast(1f) * progress)
            )
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.take(8).forEach {
            Text(it, style = MaterialTheme.typography.labelSmall, color = GsTheme.colors.textTertiary)
        }
    }
}

// --- candlestick -------------------------------------------------------------

@Composable
internal fun CandlestickChart(w: ChartWidget) {
    var animate by remember { mutableStateOf(false) }
    LaunchedEffect(w) { animate = true }
    val progress by animateFloatAsState(if (animate) 1f else 0f, tween(700), label = "candle")
    val success = GsTheme.colors.success
    val warning = GsTheme.colors.warning
    val gridColor = GsTheme.colors.divider
    val s = w.series.firstOrNull() ?: return
    // [open,high,low,close] or [x,open,high,low,close]
    val candles = s.data.mapNotNull { p ->
        when {
            p.size >= 5 -> listOf(p[1], p[2], p[3], p[4])
            p.size >= 4 -> listOf(p[0], p[1], p[2], p[3])
            else -> null
        }
    }
    if (candles.isEmpty()) return
    val highs = candles.map { it[1] }
    val lows = candles.map { it[2] }
    val hi = highs.maxOrNull() ?: 1f
    val lo = lows.minOrNull() ?: 0f
    val span = (hi - lo).takeIf { it > 0f } ?: 1f
    Canvas(Modifier.fillMaxWidth().height(190.dp).padding(top = 8.dp)) {
        val cw = size.width
        val ch = size.height
        repeat(4) { g ->
            val y = ch * g / 3f
            drawLine(gridColor, Offset(0f, y), Offset(cw, y), strokeWidth = 1f)
        }
        val slot = cw / candles.size.coerceAtLeast(1)
        val bw = slot * 0.58f
        fun yOf(v: Float) = ch * (1f - (v - lo) / span)
        candles.forEachIndexed { i, c ->
            val open = c[0]
            val high = c[1]
            val low = c[2]
            val close = c[3]
            val cx = i * slot + slot / 2f
            val col = if (close >= open) success else warning
            drawLine(col, Offset(cx, yOf(high)), Offset(cx, yOf(low)), strokeWidth = 1.5f)
            val top = yOf(maxOf(open, close))
            val bottom = yOf(minOf(open, close))
            drawRect(
                col,
                topLeft = Offset(cx - bw / 2f, top),
                size = Size(bw, ((bottom - top).coerceAtLeast(1f)) * progress)
            )
        }
    }
    Text(
        "${candles.size} candles",
        style = MaterialTheme.typography.labelSmall,
        color = GsTheme.colors.textTertiary,
        modifier = Modifier.padding(top = 5.dp)
    )
}

// --- gantt -------------------------------------------------------------------

@Composable
internal fun GanttChart(w: ChartWidget) {
    val c = GsTheme.colors
    val s = w.series.firstOrNull() ?: return
    val tasks = s.data.filter { it.size >= 2 }.map { it[0] to it[1] }
    if (tasks.isEmpty()) return
    val maxEnd = tasks.maxOfOrNull { maxOf(it.first, it.second) }?.takeIf { it > 0f } ?: 1f
    val labels = categoryLabels(w, tasks.size)
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        tasks.take(12).forEachIndexed { i, pair ->
            val start = pair.first.coerceAtLeast(0f)
            val end = pair.second.coerceAtLeast(start)
            val left = (start / maxEnd).coerceIn(0f, 0.98f)
            val width = ((end - start) / maxEnd).coerceIn(0.02f, 1f - left)
            val rest = (1f - left - width).coerceAtLeast(0.001f)
            Column {
                Text(
                    labels.getOrElse(i) { "Task ${i + 1}" },
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(Modifier.fillMaxWidth().padding(top = 3.dp).height(13.dp)) {
                    if (left > 0f) Spacer(Modifier.weight(left))
                    Box(
                        Modifier.weight(width).height(13.dp)
                            .clip(RoundedCornerShape(99.dp)).background(seriesColor(i))
                    )
                    Spacer(Modifier.weight(rest))
                }
            }
        }
    }
}

// --- chord diagram -----------------------------------------------------------

/**
 * Chord diagram. `labels` are the nodes; each series is a source node and its
 * points are `[targetIndex, flow]`. Arcs are sized by each node's total flow and
 * ribbons connect the two ends of every flow.
 */
@Composable
internal fun ChordChart(w: ChartWidget) {
    val c = GsTheme.colors
    val nodes = w.labels.take(10)
    if (nodes.size < 2) {
        Text("Chord needs at least two labels", style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
        return
    }
    val flows = ArrayList<Triple<Int, Int, Float>>()
    w.series.take(nodes.size).forEachIndexed { si, s ->
        s.data.forEach { p ->
            val target = p.getOrNull(0)?.toInt() ?: return@forEach
            val value = p.getOrNull(1) ?: 0f
            if (target in nodes.indices && target != si && value > 0f) {
                flows += Triple(si, target, value)
            }
        }
    }
    if (flows.isEmpty()) {
        Text("No flows to draw", style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
        return
    }
    val colors = nodes.indices.map { seriesColor(it) }
    val totals = FloatArray(nodes.size)
    flows.forEach { (a, b, v) ->
        totals[a] += v
        totals[b] += v
    }

    Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(240.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = minOf(size.width, size.height) / 2f * 0.78f
            val gap = 0.05f
            val total = totals.sum().takeIf { it > 0f } ?: return@Canvas
            val free = (2f * Math.PI.toFloat()) - gap * nodes.size
            val start = -Math.PI.toFloat() / 2f
            val nodeStart = FloatArray(nodes.size)
            val nodeSweep = FloatArray(nodes.size)
            var cursor = start
            nodes.indices.forEach { i ->
                nodeStart[i] = cursor
                nodeSweep[i] = free * (totals[i] / total)
                cursor += nodeSweep[i] + gap
            }

            fun point(angle: Float): Offset =
                Offset(center.x + radius * cos(angle), center.y + radius * sin(angle))

            // Outer arcs.
            nodes.indices.forEach { i ->
                drawArc(
                    color = colors[i],
                    startAngle = Math.toDegrees(nodeStart[i].toDouble()).toFloat(),
                    sweepAngle = Math.toDegrees(nodeSweep[i].toDouble()).toFloat(),
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = 7f)
                )
            }

            // Ribbons, allocated in flow order on both ends.
            val cursorA = nodeStart.copyOf()
            val cursorB = nodeStart.copyOf()
            flows.forEach { (a, b, v) ->
                val wa = nodeSweep[a] * (v / totals[a].coerceAtLeast(1e-6f))
                val wb = nodeSweep[b] * (v / totals[b].coerceAtLeast(1e-6f))
                val a0 = cursorA[a]
                val a1 = a0 + wa
                val b0 = cursorB[b]
                val b1 = b0 + wb
                cursorA[a] = a1
                cursorB[b] = b1

                val path = androidx.compose.ui.graphics.Path().apply {
                    val p0 = point(a0)
                    moveTo(p0.x, p0.y)
                    cubicTo(center.x, center.y, center.x, center.y, point(b0).x, point(b0).y)
                    // Short arc along the target node.
                    val steps = 4
                    for (s in 1..steps) {
                        val p = point(b0 + (b1 - b0) * s / steps)
                        lineTo(p.x, p.y)
                    }
                    cubicTo(center.x, center.y, center.x, center.y, point(a1).x, point(a1).y)
                    close()
                }
                drawPath(path, color = colors[a].copy(alpha = 0.34f))
            }
        }
    }

    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        nodes.forEachIndexed { i, label ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(8.dp).height(8.dp).clip(RoundedCornerShape(2.dp)).background(colors[i]))
                Text(
                    "  $label",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
