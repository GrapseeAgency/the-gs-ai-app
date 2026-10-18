package com.grapsee.gsai.ui.chat.content

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.theme.GsTheme
import com.grapsee.gsai.ui.theme.skeletonSurface
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ---------------------------------------------------------------------------
// GENUI widgets — model-emitted JSON rendered natively (see docs/GENUI.md).
//
// Wire format: a fenced block whose language is the widget kind and whose body
// is JSON. Parsed HERE, at render time, never in the stream parser — so a
// malformed payload degrades to a code block and nothing else can break.
// ---------------------------------------------------------------------------

/** Hard caps. A widget is data, not a document; anything bigger is a bug. */
private const val MAX_WIDGET_BYTES = 64 * 1024
private const val MAX_CHART_SERIES = 8
private const val MAX_POINTS_PER_SERIES = 200
private const val MAX_TABLE_ROWS = 100
private const val MAX_TABLE_COLUMNS = 8
private const val MAX_STAT_CARDS = 8
private const val MAX_PROGRESS_ITEMS = 12
private const val MAX_TIMELINE_ENTRIES = 24
private const val MAX_COMPARE_ITEMS = 12

@Serializable
data class ChartSeries(val name: String = "", val data: List<List<Float>> = emptyList())

@Serializable
data class ChartWidget(
    val type: String = "line", // line | area | bar | pie
    val title: String = "",
    val unit: String = "",
    val series: List<ChartSeries> = emptyList()
)

@Serializable
data class StatCardData(
    val label: String,
    val value: String,
    val unit: String = "",
    val delta: String = "",
    val direction: String = "" // up | down | flat
)

@Serializable
data class StatWidget(val title: String = "", val cards: List<StatCardData> = emptyList())

@Serializable
data class TableWidget(
    val title: String = "",
    val columns: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList(),
    val sortable: Boolean = false
)

@Serializable
data class CompareSide(val title: String = "", val items: List<String> = emptyList())

@Serializable
data class CompareWidget(
    val title: String = "",
    val left: CompareSide = CompareSide(),
    val right: CompareSide = CompareSide()
)

@Serializable
data class ProgressItem(val label: String, val percent: Float)

@Serializable
data class ProgressWidget(val title: String = "", val items: List<ProgressItem> = emptyList())

@Serializable
data class TimelineEntry(val date: String, val title: String, val detail: String = "")

@Serializable
data class TimelineWidget(val title: String = "", val entries: List<TimelineEntry> = emptyList())

/** A validated widget payload. */
sealed interface Widget {
    val title: String

    data class Chart(val w: ChartWidget) : Widget { override val title get() = w.title }
    data class Stat(val w: StatWidget) : Widget { override val title get() = w.title }
    data class Table(val w: TableWidget) : Widget { override val title get() = w.title }
    data class Compare(val w: CompareWidget) : Widget { override val title get() = w.title }
    data class Progress(val w: ProgressWidget) : Widget { override val title get() = w.title }
    data class Timeline(val w: TimelineWidget) : Widget { override val title get() = w.title }
}

private val widgetJson = Json { ignoreUnknownKeys = true }

/**
 * Parse + validate a widget payload. Returns null when anything is off — the
 * caller renders the raw JSON as a code block, so the model can never break
 * the transcript with a bad widget.
 */
fun parseWidget(kind: String, json: String): Widget? {
    if (json.isBlank() || json.length > MAX_WIDGET_BYTES) return null
    return try {
        when (kind) {
            "chart" -> widgetJson.decodeFromString<ChartWidget>(json).let { w ->
                val series = w.series.take(MAX_CHART_SERIES).map {
                    it.copy(data = it.data.take(MAX_POINTS_PER_SERIES))
                }.filter { it.data.size >= 2 }
                if (series.isEmpty()) null
                else if (w.type in setOf("line", "area", "bar", "pie")) Widget.Chart(w.copy(series = series))
                else null
            }
            "stat" -> widgetJson.decodeFromString<StatWidget>(json).let { w ->
                val cards = w.cards.take(MAX_STAT_CARDS)
                if (cards.isEmpty()) null else Widget.Stat(w.copy(cards = cards))
            }
            "table" -> widgetJson.decodeFromString<TableWidget>(json).let { w ->
                val cols = w.columns.take(MAX_TABLE_COLUMNS)
                val rows = w.rows.take(MAX_TABLE_ROWS).filter { it.isNotEmpty() }
                if (cols.isEmpty() || rows.isEmpty()) null
                else Widget.Table(w.copy(columns = cols, rows = rows))
            }
            "compare" -> widgetJson.decodeFromString<CompareWidget>(json).let { w ->
                if (w.left.items.isEmpty() && w.right.items.isEmpty()) null
                else Widget.Compare(
                    w.copy(
                        left = w.left.copy(items = w.left.items.take(MAX_COMPARE_ITEMS)),
                        right = w.right.copy(items = w.right.items.take(MAX_COMPARE_ITEMS))
                    )
                )
            }
            "progress" -> widgetJson.decodeFromString<ProgressWidget>(json).let { w ->
                val items = w.items.take(MAX_PROGRESS_ITEMS).map { it.copy(percent = it.percent.coerceIn(0f, 100f)) }
                if (items.isEmpty()) null else Widget.Progress(w.copy(items = items))
            }
            "timeline" -> widgetJson.decodeFromString<TimelineWidget>(json).let { w ->
                val entries = w.entries.take(MAX_TIMELINE_ENTRIES)
                if (entries.isEmpty()) null else Widget.Timeline(w.copy(entries = entries))
            }
            else -> null
        }
    } catch (_: Throwable) {
        null // malformed JSON / wrong types -> degrade to a code block
    }
}

// ---------------------------------------------------------------------------
// Rendering
// ---------------------------------------------------------------------------

private val widgetShape = RoundedCornerShape(14.dp)

/** Series palette: theme-accented first, then fixed hues that read on both themes. */
@Composable
private fun seriesColor(i: Int): Color {
    val c = GsTheme.colors
    return when (i % 5) {
        0 -> c.accent
        1 -> c.success
        2 -> c.warning
        3 -> c.link
        else -> c.toolExecution
    }
}

@Composable
private fun WidgetCardShell(title: String, tag: String, content: @Composable () -> Unit) {
    val c = GsTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(widgetShape)
            .background(c.elevatedSurface)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (title.isNotEmpty()) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = c.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Box(Modifier.weight(1f))
            }
            Text(
                tag.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = c.textTertiary,
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(c.raisedSurface)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
        content()
    }
}

/** Mid-stream: the fence is still open, so show a skeleton instead of raw JSON. */
@Composable
private fun WidgetSkeleton(kind: String) {
    val c = GsTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .skeletonSurface(widgetShape, c.raisedSurface),
        contentAlignment = Alignment.Center
    ) {
        Text("Building $kind…", style = MaterialTheme.typography.labelMedium, color = c.textTertiary)
    }
}

/** Invalid payload: degrade to the raw JSON as a code block — never invisible. */
@Composable
private fun WidgetFallback(block: Block.WidgetBlock) {
    CodeBlockCard(
        language = block.kind,
        code = block.json,
        open = false,
        onCopyCode = {}
    )
}

@Composable
fun WidgetBlockView(block: Block.WidgetBlock, streaming: Boolean) {
    if (block.open && streaming) {
        WidgetSkeleton(block.kind)
        return
    }
    val widget = remember(block.kind, block.json) { parseWidget(block.kind, block.json) }
    if (widget == null) {
        WidgetFallback(block)
        return
    }
    when (widget) {
        is Widget.Chart -> ChartWidgetView(widget.w)
        is Widget.Stat -> StatWidgetView(widget.w)
        is Widget.Table -> TableWidgetView(widget.w)
        is Widget.Compare -> CompareWidgetView(widget.w)
        is Widget.Progress -> ProgressWidgetView(widget.w)
        is Widget.Timeline -> TimelineWidgetView(widget.w)
    }
}

// --- chart ------------------------------------------------------------------

@Composable
private fun ChartWidgetView(w: ChartWidget) {
    WidgetCardShell(w.title.ifEmpty { "Chart" }, "chart") {
        when (w.type) {
            "bar" -> BarChart(w)
            "pie" -> PieChart(w)
            else -> LineChart(w, filled = w.type == "area")
        }
        if (w.series.size > 1 && w.type != "pie") {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                w.series.forEachIndexed { i, s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(width = 12.dp, height = 3.dp).clip(RoundedCornerShape(2.dp)).background(seriesColor(i)))
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
    }
}

@Composable
private fun LineChart(w: ChartWidget, filled: Boolean) {
    var animate by remember { mutableStateOf(false) }
    LaunchedEffect(w) { animate = true }
    val progress by animateFloatAsState(if (animate) 1f else 0f, tween(700), label = "chart")
    val colors = w.series.indices.map { seriesColor(it) }
    val gridColor = GsTheme.colors.divider
    val flat = w.series.flatMap { it.data }
    val xs = flat.map { it.getOrElse(0) { 0f } }
    val ys = flat.map { it.getOrElse(1) { 0f } }
    val minX = xs.minOrNull() ?: 0f
    val maxX = xs.maxOrNull() ?: 1f
    val minY = minOf(0f, ys.minOrNull() ?: 0f)
    val maxY = ys.maxOrNull() ?: 1f
    val spanX = (maxX - minX).takeIf { it > 0f } ?: 1f
    val spanY = (maxY - minY).takeIf { it > 0f } ?: 1f
    Canvas(Modifier.fillMaxWidth().height(180.dp).padding(top = 8.dp)) {
        val padL = 0f; val padB = 0f
        val cw = size.width - padL; val ch = size.height - padB
        repeat(4) { g ->
            val y = ch * g / 3f
            drawLine(gridColor, Offset(0f, y), Offset(cw, y), strokeWidth = 1f)
        }
        w.series.forEachIndexed { si, s ->
            val col = colors[si]
            val pts = s.data.map {
                Offset(
                    padL + cw * ((it.getOrElse(0) { 0f } - minX) / spanX),
                    ch * (1f - ((it.getOrElse(1) { 0f } - minY) / spanY))
                )
            }
            if (pts.size < 2) return@forEachIndexed
            val visible = (pts.size * progress).toInt().coerceAtLeast(1)
            val path = Path()
            pts.take(visible + 1).forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
            // partial segment between visible and next point for a smooth draw-in
            if (visible < pts.size) {
                val frac = pts.size * progress - visible
                val a = pts[visible - 1]; val b = pts[visible]
                path.lineTo(a.x + (b.x - a.x) * frac, a.y + (b.y - a.y) * frac)
            }
            if (filled) {
                val fillPath = Path().apply {
                    addPath(path)
                    pts.take(visible + 1).lastOrNull()?.let { lineTo(it.x, ch) }
                    lineTo(pts.first().x, ch)
                    close()
                }
                drawPath(fillPath, col.copy(alpha = 0.15f))
            }
            drawPath(path, col, style = Stroke(width = 3.dp.toPx()))
            if (progress >= 1f) pts.forEach { drawCircle(col, radius = 3.dp.toPx(), center = it) }
        }
    }
}

@Composable
private fun BarChart(w: ChartWidget) {
    var animate by remember { mutableStateOf(false) }
    LaunchedEffect(w) { animate = true }
    val progress by animateFloatAsState(if (animate) 1f else 0f, tween(700), label = "chart")
    val colors = w.series.indices.map { seriesColor(it) }
    val gridColor = GsTheme.colors.divider
    val labels = w.series.first().data.map { it.getOrElse(0) { 0f }.toInt().toString() }
    val maxV = w.series.flatMap { s -> s.data.map { it.getOrElse(1) { 0f } } }.maxOrNull()?.takeIf { it > 0f } ?: 1f
    Canvas(Modifier.fillMaxWidth().height(180.dp).padding(top = 8.dp)) {
        val groups = labels.size.coerceAtLeast(1)
        val seriesCount = w.series.size.coerceAtLeast(1)
        val cw = size.width; val ch = size.height
        repeat(4) { g ->
            val y = ch * g / 3f
            drawLine(gridColor, Offset(0f, y), Offset(cw, y), strokeWidth = 1f)
        }
        val groupW = cw / groups
        val barW = (groupW * 0.7f) / seriesCount
        w.series.forEachIndexed { si, s ->
            s.data.forEachIndexed { gi, point ->
                val v = point.getOrElse(1) { 0f }.coerceAtLeast(0f)
                val h = ch * (v / maxV) * progress
                val x = gi * groupW + groupW * 0.15f + si * barW
                drawRect(colors[si], topLeft = Offset(x, ch - h), size = androidx.compose.ui.geometry.Size(barW * 0.9f, h))
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.take(8).forEach {
            Text(it, style = MaterialTheme.typography.labelSmall, color = GsTheme.colors.textTertiary)
        }
    }
}

@Composable
private fun PieChart(w: ChartWidget) {
    var animate by remember { mutableStateOf(false) }
    LaunchedEffect(w) { animate = true }
    val progress by animateFloatAsState(if (animate) 1f else 0f, tween(700), label = "chart")
    val colors = w.series.indices.map { seriesColor(it) }
    // pie uses series[*].data[0] as (labelIndex, value); or name + single value
    val slices = w.series.map { s -> s.name to (s.data.firstOrNull()?.getOrElse(1) { 0f } ?: 0f) }.filter { it.second > 0f }
    val total = slices.sumOf { it.second.toDouble() }.toFloat().takeIf { it > 0f } ?: 1f
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(140.dp)) {
            var start = -90f
            slices.forEachIndexed { i, (_, v) ->
                val sweep = 360f * (v / total) * progress
                drawArc(colors[i % colors.size], startAngle = start, sweepAngle = sweep, useCenter = true)
                start += 360f * (v / total) * progress
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

// --- stat cards -------------------------------------------------------------

@Composable
private fun StatWidgetView(w: StatWidget) {
    WidgetCardShell(w.title.ifEmpty { "Stats" }, "stat") {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            w.cards.forEach { card ->
                val c = GsTheme.colors
                Column(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.surface)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .width(130.dp)
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(card.value, style = MaterialTheme.typography.titleLarge, color = c.textPrimary, fontWeight = FontWeight.Bold)
                        if (card.unit.isNotEmpty()) {
                            Text(card.unit, style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.padding(start = 3.dp, bottom = 2.dp))
                        }
                        if (card.delta.isNotEmpty()) {
                            val up = card.direction == "up"
                            Text(
                                card.delta,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (card.direction == "flat") c.textTertiary else if (up) c.success else c.error,
                                modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
                            )
                        }
                    }
                    Text(card.label, style = MaterialTheme.typography.labelSmall, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// --- table ------------------------------------------------------------------

@Composable
private fun TableWidgetView(w: TableWidget) {
    var sortCol by remember { mutableIntStateOf(-1) }
    var asc by remember { mutableStateOf(true) }
    val rows = remember(w.rows, sortCol, asc) {
        if (sortCol < 0) w.rows
        else w.rows.sortedWith { a, b ->
            val va = a.getOrNull(sortCol) ?: ""
            val vb = b.getOrNull(sortCol) ?: ""
            val na = va.toFloatOrNull(); val nb = vb.toFloatOrNull()
            val cmp = if (na != null && nb != null) na.compareTo(nb) else va.compareTo(vb)
            if (asc) cmp else -cmp
        }
    }
    WidgetCardShell(w.title.ifEmpty { "Table" }, "table") {
        Column(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp)) {
            Row {
                w.columns.forEachIndexed { ci, col ->
                    Text(
                        if (w.sortable) "$col ${if (sortCol == ci) (if (asc) "▲" else "▼") else ""}" else col,
                        style = MaterialTheme.typography.labelSmall,
                        color = GsTheme.colors.textTertiary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .width(140.dp)
                            .then(if (w.sortable) Modifier.clickable {
                                if (sortCol == ci) asc = !asc else { sortCol = ci; asc = true }
                            } else Modifier)
                            .padding(vertical = 6.dp)
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(GsTheme.colors.divider))
            rows.forEach { row ->
                Row {
                    w.columns.indices.forEach { ci ->
                        Text(
                            row.getOrNull(ci) ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = GsTheme.colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(140.dp).padding(vertical = 7.dp)
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(GsTheme.colors.divider.copy(alpha = 0.4f)))
            }
        }
    }
}

// --- compare ----------------------------------------------------------------

@Composable
private fun CompareWidgetView(w: CompareWidget) {
    WidgetCardShell(w.title.ifEmpty { "Compare" }, "compare") {
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(w.left, w.right).forEach { side ->
                val c = GsTheme.colors
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.surface)
                        .padding(12.dp)
                ) {
                    Text(side.title, style = MaterialTheme.typography.labelLarge, color = c.textPrimary, fontWeight = FontWeight.SemiBold)
                    side.items.forEach { item ->
                        Row(Modifier.padding(top = 6.dp)) {
                            Text("•", color = c.accent, modifier = Modifier.padding(end = 6.dp))
                            Text(item, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

// --- progress ---------------------------------------------------------------

@Composable
private fun ProgressWidgetView(w: ProgressWidget) {
    WidgetCardShell(w.title.ifEmpty { "Progress" }, "progress") {
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            w.items.forEachIndexed { i, item ->
                var animate by remember(item.label, item.percent) { mutableStateOf(false) }
                LaunchedEffect(item.label, item.percent) { animate = true }
                val p by animateFloatAsState(if (animate) item.percent / 100f else 0f, tween(900), label = "prog$i")
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(item.label, style = MaterialTheme.typography.labelMedium, color = GsTheme.colors.textPrimary)
                        Text("${item.percent.toInt()}%", style = MaterialTheme.typography.labelMedium, color = GsTheme.colors.textSecondary, fontFamily = FontFamily.Monospace)
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 5.dp)
                            .height(7.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(GsTheme.colors.raisedSurface)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(p)
                                .height(7.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(seriesColor(i))
                        )
                    }
                }
            }
        }
    }
}

// --- timeline ---------------------------------------------------------------

@Composable
private fun TimelineWidgetView(w: TimelineWidget) {
    WidgetCardShell(w.title.ifEmpty { "Timeline" }, "timeline") {
        Column(Modifier.padding(top = 8.dp)) {
            w.entries.forEachIndexed { i, e ->
                Row {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(seriesColor(i)))
                        if (i < w.entries.lastIndex) {
                            Box(Modifier.width(2.dp).height(34.dp).background(GsTheme.colors.divider))
                        }
                    }
                    Column(Modifier.padding(start = 10.dp, bottom = 12.dp)) {
                        Text(e.date, style = MaterialTheme.typography.labelSmall, color = GsTheme.colors.textTertiary)
                        Text(e.title, style = MaterialTheme.typography.bodySmall, color = GsTheme.colors.textPrimary, fontWeight = FontWeight.Medium)
                        if (e.detail.isNotEmpty()) {
                            Text(e.detail, style = MaterialTheme.typography.labelMedium, color = GsTheme.colors.textSecondary)
                        }
                    }
                }
            }
        }
    }
}
