package com.grapsee.gsai.ui.chat.content

// ---------------------------------------------------------------------------
// Extended charts + interactive widgets (0.78.0 "interactive chat").
// The base set lives in WidgetBlocks.kt (line/area/bar/pie/stat/table/
// compare/progress/timeline); everything below rides the SAME models and the
// SAME card shell, so a JSON fence and an ASCII-converted fence are visually
// indistinguishable.
// ---------------------------------------------------------------------------

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.theme.GsTheme
import kotlinx.serialization.Serializable

// --- models -----------------------------------------------------------------

@Serializable
data class GaugeItem(val label: String = "", val value: Float = 0f, val max: Float = 100f, val unit: String = "")

@Serializable
data class GaugeWidget(val title: String = "", val items: List<GaugeItem> = emptyList())

@Serializable
data class QuizQuestion(
    val q: String = "",
    val options: List<String> = emptyList(),
    val answer: Int = -1,
    val explain: String = ""
)

@Serializable
data class QuizWidget(val title: String = "", val questions: List<QuizQuestion> = emptyList())

@Serializable
data class FormField(
    val type: String = "text",
    val label: String = "",
    val placeholder: String = "",
    val options: List<String> = emptyList(),
    val required: Boolean = false,
    val min: Float = 0f,
    val max: Float = 100f,
    val value: Float = 50f,
    val unit: String = ""
)

@Serializable
data class FormWidget(
    val title: String = "",
    val fields: List<FormField> = emptyList(),
    val submitLabel: String = "Submit"
)

@Serializable
data class CarouselItem(val image: String = "", val caption: String = "")

@Serializable
data class CarouselWidget(val title: String = "", val items: List<CarouselItem> = emptyList())

@Serializable
data class ActionCard(val title: String = "", val subtitle: String = "", val prompt: String = "")

@Serializable
data class CardsWidget(val title: String = "", val cards: List<ActionCard> = emptyList())

@Serializable
data class FollowupsWidget(val title: String = "", val prompts: List<String> = emptyList())

// --- shared helpers ----------------------------------------------------------

private fun yValues(w: ChartWidget): List<Float> =
    w.series.flatMap { s -> s.data.map { it.getOrElse(1) { 0f } } }

private fun chartMax(w: ChartWidget): Float =
    yValues(w).maxOrNull()?.takeIf { it > 0f } ?: 1f

@Composable
internal fun SeriesLegend(w: ChartWidget) {
    if (w.series.size <= 1) return
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        w.series.forEachIndexed { i, s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(width = 12.dp, height = 3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(seriesColor(i))
                )
                Text(
                    s.name.ifEmpty { "Series ${i + 1}" },
                    style = MaterialTheme.typography.labelSmall,
                    color = GsTheme.colors.textSecondary,
                    modifier = Modifier.padding(start = 5.dp)
                )
            }
        }
    }
}

// --- donut -------------------------------------------------------------------

@Composable
internal fun DonutChart(w: ChartWidget) {
    var animate by remember { mutableStateOf(false) }
    LaunchedEffect(w) { animate = true }
    val progress by animateFloatAsState(if (animate) 1f else 0f, tween(700), label = "donut")
    val colors = w.series.indices.map { seriesColor(it) }
    val slices = remember(w.series, w.labels) { pieSlices(w) }
    val total = slices.sumOf { it.second.toDouble() }.toFloat().takeIf { it > 0f } ?: 1f
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(140.dp)) {
                val stroke = 26.dp.toPx()
                val inset = stroke / 2f
                var start = -90f
                slices.forEachIndexed { i, (_, v) ->
                    val sweep = 360f * (v / total) * progress
                    drawArc(
                        color = colors[i % colors.size],
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke)
                    )
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (progress >= 0.99f) "${total.toInt()}" else "",
                    style = MaterialTheme.typography.titleLarge,
                    color = GsTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text("total", style = MaterialTheme.typography.labelSmall, color = GsTheme.colors.textTertiary)
            }
        }
        Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            slices.forEachIndexed { i, (name, v) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(colors[i % colors.size]))
                    Text(
                        "${name.ifEmpty { "Slice ${i + 1}" }} — ${((v / total) * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = GsTheme.colors.textSecondary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

// --- horizontal bar ----------------------------------------------------------

@Composable
internal fun HBarChart(w: ChartWidget) {
    var animate by remember { mutableStateOf(false) }
    LaunchedEffect(w) { animate = true }
    val progress by animateFloatAsState(if (animate) 1f else 0f, tween(700), label = "hbar")
    val s = w.series.first()
    val labels = if (w.labels.size >= s.data.size) w.labels else s.data.indices.map { "S${it + 1}" }
    val maxV = chartMax(w)
    val c = GsTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        s.data.take(10).forEachIndexed { i, point ->
            val v = point.getOrElse(1) { 0f }.coerceAtLeast(0f)
            val frac = (v / maxV).coerceIn(0f, 1f) * progress
            val label = labels.getOrElse(i) { "" }
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = c.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        if (progress >= 0.99f) formatChartValue(v) else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = c.textSecondary,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                Box(
                    Modifier.fillMaxWidth().padding(top = 4.dp).height(12.dp)
                        .clip(RoundedCornerShape(99.dp)).background(c.raisedSurface)
                ) {
                    Box(
                        Modifier.fillMaxWidth(frac).height(12.dp)
                            .clip(RoundedCornerShape(99.dp)).background(seriesColor(i))
                    )
                }
            }
        }
    }
}

internal fun formatChartValue(v: Float): String =
    if (v % 1f == 0f) v.toLong().toString() else "%.1f".format(v)

// --- stacked bar -------------------------------------------------------------

@Composable
internal fun StackedBarChart(w: ChartWidget) {
    var animate by remember { mutableStateOf(false) }
    LaunchedEffect(w) { animate = true }
    val progress by animateFloatAsState(if (animate) 1f else 0f, tween(700), label = "stacked")
    val gridColor = GsTheme.colors.divider
    val colors = w.series.indices.map { seriesColor(it) }
    val cats = w.series.first().data.size
    val stackMax = (0 until cats).maxOfOrNull { ci ->
        w.series.sumOf { s -> s.data.getOrNull(ci)?.getOrElse(1) { 0f }?.toDouble() ?: 0.0 }
    }?.toFloat()?.takeIf { it > 0f } ?: 1f
    val labels = if (w.labels.size >= cats) w.labels else (0 until cats).map { "S${it + 1}" }
    Canvas(Modifier.fillMaxWidth().height(180.dp).padding(top = 8.dp)) {
        val cw = size.width; val ch = size.height
        repeat(4) { g ->
            val y = ch * g / 3f
            drawLine(gridColor, Offset(0f, y), Offset(cw, y), strokeWidth = 1f)
        }
        val groupW = cw / cats.coerceAtLeast(1)
        val barW = groupW * 0.56f
        w.series.forEachIndexed { si, s ->
            s.data.forEachIndexed { ci, point ->
                val v = point.getOrElse(1) { 0f }.coerceAtLeast(0f)
                val below = (0 until ci).sumOf { cIdx ->
                    w.series.sumOf { s2 -> s2.data.getOrNull(cIdx)?.getOrElse(1) { 0f }?.toDouble() ?: 0.0 }
                }.toFloat()
                val h = ch * (v / stackMax) * progress
                val x = ci * groupW + groupW * 0.22f
                val bottom = ch - ch * (below / stackMax) * progress
                drawRect(colors[si], topLeft = Offset(x, bottom - h), size = Size(barW, h))
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

// --- stacked area ------------------------------------------------------------

@Composable
internal fun StackedAreaChart(w: ChartWidget) {
    var animate by remember { mutableStateOf(false) }
    LaunchedEffect(w) { animate = true }
    val progress by animateFloatAsState(if (animate) 1f else 0f, tween(700), label = "sarea")
    val colors = w.series.indices.map { seriesColor(it) }
    val gridColor = GsTheme.colors.divider
    val cats = w.series.first().data.size.coerceAtLeast(2)
    val maxStack = (0 until cats).maxOfOrNull { ci ->
        w.series.sumOf { s -> s.data.getOrNull(ci)?.getOrElse(1) { 0f }?.toDouble() ?: 0.0 }
    }?.toFloat()?.takeIf { it > 0f } ?: 1f
    Canvas(Modifier.fillMaxWidth().height(180.dp).padding(top = 8.dp)) {
        val cw = size.width; val ch = size.height
        repeat(4) { g ->
            val y = ch * g / 3f
            drawLine(gridColor, Offset(0f, y), Offset(cw, y), strokeWidth = 1f)
        }
        w.series.forEachIndexed { si, s ->
            val upper = Path()
            s.data.forEachIndexed { ci, point ->
                val x = cw * ci / (cats - 1).toFloat()
                val v = point.getOrElse(1) { 0f }.coerceAtLeast(0f)
                val below = (0 until ci).sumOf { cIdx ->
                    w.series.sumOf { s2 -> s2.data.getOrNull(cIdx)?.getOrElse(1) { 0f }?.toDouble() ?: 0.0 }
                }.toFloat()
                val yTop = ch * (1f - ((below + v) / maxStack) * progress)
                val yBot = ch * (1f - (below / maxStack) * progress)
                if (ci == 0) upper.moveTo(x, yTop) else upper.lineTo(x, yTop)
            }
            val fill = Path().apply {
                addPath(upper)
                (cats - 1 downTo 0).forEach { ci ->
                    val x = cw * ci / (cats - 1).toFloat()
                    val below = (0 until ci).sumOf { cIdx ->
                        w.series.sumOf { s2 -> s2.data.getOrNull(cIdx)?.getOrElse(1) { 0f }?.toDouble() ?: 0.0 }
                    }.toFloat()
                    lineTo(x, ch * (1f - (below / maxStack) * progress))
                }
                close()
            }
            drawPath(fill, colors[si % colors.size].copy(alpha = 0.18f))
            drawPath(upper, colors[si % colors.size], style = Stroke(width = 2.5.dp.toPx()))
        }
    }
    SeriesLegend(w)
}

// --- scatter / bubble --------------------------------------------------------

@Composable
internal fun ScatterChart(w: ChartWidget, bubble: Boolean) {
    var animate by remember { mutableStateOf(false) }
    LaunchedEffect(w) { animate = true }
    val progress by animateFloatAsState(if (animate) 1f else 0f, tween(700), label = "scatter")
    val colors = w.series.indices.map { seriesColor(it) }
    val gridColor = GsTheme.colors.divider
    val xs = w.series.flatMap { s -> s.data.map { it.getOrElse(0) { 0f } } }
    val ys = w.series.flatMap { s -> s.data.map { it.getOrElse(1) { 0f } } }
    val zs = w.series.flatMap { s -> s.data.map { it.getOrNull(2) ?: 0f } }
    val minX = xs.minOrNull() ?: 0f
    val maxX = xs.maxOrNull() ?: 1f
    val minY = minOf(0f, ys.minOrNull() ?: 0f)
    val maxY = ys.maxOrNull() ?: 1f
    val spanX = (maxX - minX).takeIf { it > 0f } ?: 1f
    val spanY = (maxY - minY).takeIf { it > 0f } ?: 1f
    val maxZ = zs.maxOrNull()?.takeIf { it > 0f } ?: 1f
    Canvas(Modifier.fillMaxWidth().height(180.dp).padding(top = 8.dp)) {
        val cw = size.width; val ch = size.height
        repeat(4) { g ->
            val y = ch * g / 3f
            drawLine(gridColor, Offset(0f, y), Offset(cw, y), strokeWidth = 1f)
        }
        w.series.forEachIndexed { si, s ->
            s.data.forEach { p ->
                val cx = cw * ((p.getOrElse(0) { 0f } - minX) / spanX)
                val cy = ch * (1f - ((p.getOrElse(1) { 0f } - minY) / spanY))
                val r = if (bubble) {
                    5.dp.toPx() + 13.dp.toPx() * ((p.getOrNull(2) ?: 0f) / maxZ)
                } else {
                    5.dp.toPx() * progress + 1.dp.toPx()
                }
                drawCircle(colors[si % colors.size], radius = r, center = Offset(cx, cy), alpha = 0.85f)
            }
        }
    }
    SeriesLegend(w)
}

// --- histogram ---------------------------------------------------------------

@Composable
internal fun HistogramChart(w: ChartWidget) {
    var animate by remember { mutableStateOf(false) }
    LaunchedEffect(w) { animate = true }
    val progress by animateFloatAsState(if (animate) 1f else 0f, tween(700), label = "hist")
    val gridColor = GsTheme.colors.divider
    val fillColor = seriesColor(0)
    val s = w.series.first()
    val cats = s.data.size.coerceAtLeast(1)
    val maxV = chartMax(w)
    val labels = if (w.labels.size >= cats) w.labels else s.data.indices.map { "B${it + 1}" }
    Canvas(Modifier.fillMaxWidth().height(180.dp).padding(top = 8.dp)) {
        val cw = size.width; val ch = size.height
        repeat(4) { g ->
            val y = ch * g / 3f
            drawLine(gridColor, Offset(0f, y), Offset(cw, y), strokeWidth = 1f)
        }
        val barW = cw / cats
        s.data.forEachIndexed { ci, point ->
            val v = point.getOrElse(1) { 0f }.coerceAtLeast(0f)
            val h = ch * (v / maxV) * progress
            drawRect(fillColor, topLeft = Offset(ci * barW, ch - h), size = Size(barW * 0.94f, h))
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.take(8).forEach {
            Text(it, style = MaterialTheme.typography.labelSmall, color = GsTheme.colors.textTertiary)
        }
    }
}

// --- funnel ------------------------------------------------------------------

@Composable
internal fun FunnelChart(w: ChartWidget) {
    var animate by remember { mutableStateOf(false) }
    LaunchedEffect(w) { animate = true }
    val progress by animateFloatAsState(if (animate) 1f else 0f, tween(700), label = "funnel")
    val s = w.series.first()
    val labels = if (w.labels.size >= s.data.size) w.labels else s.data.indices.map { "Step ${it + 1}" }
    val maxV = chartMax(w)
    val c = GsTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        s.data.take(10).forEachIndexed { i, point ->
            val v = point.getOrElse(1) { 0f }.coerceAtLeast(0f)
            val frac = ((v / maxV) * progress).coerceIn(0.10f, 1f)
            val label = labels.getOrElse(i) { "" }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Box(
                    Modifier.fillMaxWidth(frac).height(38.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(seriesColor(i)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$label · ${formatChartValue(v)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }
}

// --- radar -------------------------------------------------------------------

@Composable
internal fun RadarChart(w: ChartWidget) {
    var animate by remember { mutableStateOf(false) }
    LaunchedEffect(w) { animate = true }
    val progress by animateFloatAsState(if (animate) 1f else 0f, tween(700), label = "radar")
    val colors = w.series.indices.map { seriesColor(it) }
    val gridColor = GsTheme.colors.divider
    val s0 = w.series.first()
    val n = s0.data.size.coerceIn(3, 12)
    val labels = if (w.labels.size >= n) w.labels else (0 until n).map { "A${it + 1}" }
    val maxV = chartMax(w)
    Canvas(Modifier.fillMaxWidth().height(210.dp).padding(top = 8.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = minOf(size.width, size.height) / 2f - 14.dp.toPx()
        fun vertex(i: Int, r: Float): Offset {
            val ang = -Math.PI / 2 + 2 * Math.PI * i / n
            return Offset(cx + (r * kotlin.math.cos(ang)).toFloat(), cy + (r * kotlin.math.sin(ang)).toFloat())
        }
        repeat(4) { ring ->
            val r = radius * (ring + 1) / 4f
            val path = Path()
            for (i in 0 until n) {
                val p = vertex(i, r)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            path.close()
            drawPath(path, gridColor, style = Stroke(width = 1f))
        }
        for (i in 0 until n) {
            drawLine(gridColor, Offset(cx, cy), vertex(i, radius), strokeWidth = 1f)
        }
        w.series.forEachIndexed { si, s ->
            val path = Path()
            s.data.take(n).forEachIndexed { i, point ->
                val v = (point.getOrElse(1) { 0f } / maxV).coerceIn(0f, 1f) * progress
                val p = vertex(i, radius * v)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            path.close()
            drawPath(path, colors[si % colors.size].copy(alpha = 0.16f))
            drawPath(path, colors[si % colors.size], style = Stroke(width = 2.5.dp.toPx()))
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.take(8).forEach {
            Text(it, style = MaterialTheme.typography.labelSmall, color = GsTheme.colors.textTertiary)
        }
    }
}
