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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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

/** Media sub-types the `media` kind renders (see WidgetsMedia.kt). */
internal val MEDIA_TYPES = setOf(
    "image", "gallery", "video", "audio", "code",
    "file", "link", "citation", "search", "entity", "product", "listing",
    "doc", "document", "pdf"
)

/** Chart sub-types the `chart` kind renders (see WidgetsExtra.kt / WidgetsCharts2.kt). */
internal val CHART_TYPES = setOf(
    "line", "area", "stackedArea", "bar", "hbar", "stacked", "pie", "donut",
    "scatter", "bubble", "histogram", "funnel", "radar",
    "grouped", "boxplot", "heatmap", "correlation", "treemap", "waterfall",
    "candlestick", "gantt", "chord"
)

@Serializable
data class ChartSeries(val name: String = "", val data: List<List<Float>> = emptyList())

@Serializable
data class ChartWidget(
    val type: String = "line", // line | area | stackedArea | bar | hbar | stacked | pie | donut | scatter | bubble | histogram | funnel | radar
    val title: String = "",
    val unit: String = "",
    /** Bar/pie category names; empty falls back to the x-index labels. */
    val labels: List<String> = emptyList(),
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
    data class Gauge(val w: GaugeWidget) : Widget { override val title get() = w.title }
    data class Quiz(val w: QuizWidget) : Widget { override val title get() = w.title }
    data class Form(val w: FormWidget) : Widget { override val title get() = w.title }
    data class Carousel(val w: CarouselWidget) : Widget { override val title get() = w.title }
    data class Cards(val w: CardsWidget) : Widget { override val title get() = w.title }
    data class Followups(val w: FollowupsWidget) : Widget { override val title get() = w.title }
    data class Media(val w: MediaWidget) : Widget { override val title get() = w.title }
    data class Actions(val w: ActionsWidget) : Widget { override val title get() = w.title }
    data class Panel(val w: PanelWidget) : Widget { override val title get() = w.title }
    data class Voice(val w: VoiceWidget) : Widget { override val title get() = w.title }
    data class Calc(val w: CalcWidget) : Widget { override val title get() = w.title }
    data class App(val w: AppWidget) : Widget { override val title get() = w.title }
}

internal val widgetJson = Json { ignoreUnknownKeys = true }

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
                // Pie/donut are documented as ONE SERIES PER SLICE with a
                // single point — requiring two points silently rejected every
                // correctly-shaped pie and rendered it as a code block.
                val minPoints = if (w.type == "pie" || w.type == "donut") 1 else 2
                val series = w.series.take(MAX_CHART_SERIES).map {
                    it.copy(data = it.data.take(MAX_POINTS_PER_SERIES))
                }.filter { it.data.size >= minPoints }
                if (series.isEmpty()) null
                else if (w.type in CHART_TYPES) Widget.Chart(w.copy(series = series))
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
            "gauge" -> widgetJson.decodeFromString<GaugeWidget>(json).let { w ->
                val items = w.items.take(6).map { it.copy(value = it.value.coerceAtLeast(0f), max = it.max.coerceAtLeast(1f)) }
                if (items.isEmpty()) null else Widget.Gauge(w.copy(items = items))
            }
            "quiz" -> widgetJson.decodeFromString<QuizWidget>(json).let { w ->
                val qs = w.questions.take(10).map { q ->
                    q.copy(options = q.options.take(6), answer = q.answer.coerceIn(0, q.options.lastIndex.coerceAtLeast(0)))
                }.filter { it.q.isNotBlank() && it.options.isNotEmpty() }
                if (qs.isEmpty()) null else Widget.Quiz(w.copy(questions = qs))
            }
            "form" -> widgetJson.decodeFromString<FormWidget>(json).let { w ->
                val fs = w.fields.take(12).filter { it.label.isNotBlank() }
                if (fs.isEmpty()) null else Widget.Form(w.copy(fields = fs))
            }
            "carousel" -> widgetJson.decodeFromString<CarouselWidget>(json).let { w ->
                val items = w.items.take(20).filter { it.image.startsWith("https://") }
                if (items.isEmpty()) null else Widget.Carousel(w.copy(items = items))
            }
            "cards" -> widgetJson.decodeFromString<CardsWidget>(json).let { w ->
                val cards = w.cards.take(8).filter { it.title.isNotBlank() }
                if (cards.isEmpty()) null else Widget.Cards(w.copy(cards = cards))
            }
            "followups" -> widgetJson.decodeFromString<FollowupsWidget>(json).let { w ->
                val prompts = w.prompts.take(12).filter { it.isNotBlank() }
                if (prompts.isEmpty()) null else Widget.Followups(w.copy(prompts = prompts))
            }
            "media" -> widgetJson.decodeFromString<MediaWidget>(json).let { w ->
                val raw = w.type.lowercase()
                val type = if (raw == "document" || raw == "pdf") "doc" else raw
                if (type !in MEDIA_TYPES) null
                else if (type == "code") {
                    if (w.code.isBlank()) null else Widget.Media(w.copy(type = type))
                } else if (type == "doc") {
                    val items = w.items.take(3).filter { it.url.startsWith("https://") }
                    if (items.isEmpty()) null else Widget.Media(w.copy(type = type, items = items))
                } else {
                    val items = w.items.take(20).filter { it.url.isNotBlank() || it.title.isNotBlank() }
                    if (items.isEmpty()) null else Widget.Media(w.copy(type = type, items = items))
                }
            }
            "actions" -> widgetJson.decodeFromString<ActionsWidget>(json).let { w ->
                val buttons = w.buttons.take(8).filter { it.label.isNotBlank() }
                if (buttons.isEmpty()) null else Widget.Actions(w.copy(buttons = buttons))
            }
            "panel" -> widgetJson.decodeFromString<PanelWidget>(json).let { w ->
                if (w.summary.isBlank() && w.body.isBlank() && w.title.isBlank() && w.popover.isBlank()) null
                else Widget.Panel(w.copy(badges = w.badges.take(8)))
            }
            "voice" -> widgetJson.decodeFromString<VoiceWidget>(json).let { w ->
                if (w.title.isBlank() && w.prompt.isBlank() && w.hint.isBlank()) null
                else Widget.Voice(w)
            }
            "calc" -> widgetJson.decodeFromString<CalcWidget>(json).let { w ->
                val fields = w.fields.take(10).filter { it.key.isNotBlank() }
                val outputs = w.outputs.take(8).filter { it.expr.isNotBlank() }
                if (w.type.lowercase() !in CALC_TYPES || fields.isEmpty() || outputs.isEmpty()) null
                else Widget.Calc(
                    w.copy(
                        type = w.type.lowercase(),
                        fields = fields.map { f -> f.copy(choices = f.choices.take(5)) },
                        outputs = outputs
                    )
                )
            }
            "app" -> widgetJson.decodeFromString<AppWidget>(json).let { w ->
                val type = w.type.lowercase()
                val steps = w.steps.take(10)
                val columns = w.columns.take(6)
                val items = w.items.take(40)
                val groups = w.groups.take(6)
                val rows = w.rows.take(30)
                val hasBody = when (type) {
                    "wizard", "onboarding", "decision" -> steps.any { it.title.isNotBlank() || it.body.isNotBlank() }
                    "kanban" -> columns.any { it.items.isNotEmpty() }
                    "sheet" -> rows.size >= 2
                    "config", "configurator" -> groups.any { it.options.isNotEmpty() }
                    "booking", "schedule" -> items.isNotEmpty() || w.slots.isNotEmpty()
                    else -> items.isNotEmpty() || columns.isNotEmpty()
                }
                if (type !in APP_TYPES || !hasBody) null
                else Widget.App(
                    w.copy(
                        type = type,
                        steps = steps,
                        columns = columns,
                        items = items,
                        groups = groups,
                        rows = rows,
                        slots = w.slots.take(16)
                    )
                )
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
internal fun seriesColor(i: Int): Color {
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
internal fun WidgetCardShell(title: String, tag: String, action: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
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
            if (action != null) {
                Box(Modifier.padding(end = 8.dp)) { action() }
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
fun WidgetBlockView(block: Block.WidgetBlock, streaming: Boolean, onSendPrompt: ((String) -> Unit)? = null) {
    if (block.open && streaming) {
        WidgetSkeleton(block.kind)
        return
    }
    val widget = remember(block.kind, block.json) { parseWidget(block.kind, block.json) }
    if (widget == null) {
        WidgetFallback(block)
        return
    }
    WidgetView(widget, onSendPrompt)
}

/**
 * Render an already-validated widget — the ASCII/markdown-table conversion
 * paths (AsciiVisual.kt) reuse the same views so a plain-fence chart and a
 * GENUI chart are visually indistinguishable.
 */
@Composable
fun WidgetView(widget: Widget, onSendPrompt: ((String) -> Unit)? = null) {
    when (widget) {
        is Widget.Chart -> ChartWidgetView(widget.w)
        is Widget.Stat -> StatWidgetView(widget.w)
        is Widget.Table -> TableWidgetView(widget.w)
        is Widget.Compare -> CompareWidgetView(widget.w)
        is Widget.Progress -> ProgressWidgetView(widget.w)
        is Widget.Timeline -> TimelineWidgetView(widget.w)
        is Widget.Gauge -> GaugeWidgetView(widget.w)
        is Widget.Quiz -> QuizWidgetView(widget.w)
        is Widget.Form -> FormWidgetView(widget.w, onSendPrompt)
        is Widget.Carousel -> CarouselWidgetView(widget.w)
        is Widget.Cards -> CardsWidgetView(widget.w, onSendPrompt)
        is Widget.Followups -> FollowupsWidgetView(widget.w, onSendPrompt)
        is Widget.Media -> MediaWidgetView(widget.w, onSendPrompt)
        is Widget.Actions -> ActionsWidgetView(widget.w, onSendPrompt)
        is Widget.Panel -> PanelWidgetView(widget.w)
        is Widget.Voice -> VoiceWidgetView(widget.w, onSendPrompt)
        is Widget.Calc -> CalcWidgetView(widget.w, onSendPrompt)
        is Widget.App -> AppWidgetView(widget.w, onSendPrompt)
    }
}

// --- chart ------------------------------------------------------------------

@Composable
private fun ChartWidgetView(w: ChartWidget) {
    WidgetCardShell(w.title.ifEmpty { "Chart" }, "chart") {
        when (w.type) {
            "bar", "histogram" -> BarChart(w)
            "pie" -> PieChart(w)
            "donut" -> DonutChart(w)
            "hbar" -> HBarChart(w)
            "stacked" -> StackedBarChart(w)
            "stackedArea" -> StackedAreaChart(w)
            "scatter" -> ScatterChart(w, bubble = false)
            "bubble" -> ScatterChart(w, bubble = true)
            "funnel" -> FunnelChart(w)
            "radar" -> RadarChart(w)
            "grouped" -> GroupedBarChart(w)
            "boxplot" -> BoxPlotChart(w)
            "heatmap" -> HeatmapChart(w)
            "correlation" -> HeatmapChart(w, diverging = true)
            "treemap" -> TreemapChart(w)
            "waterfall" -> WaterfallChart(w)
            "candlestick" -> CandlestickChart(w)
            "gantt" -> GanttChart(w)
            "chord" -> ChordChart(w)
            else -> LineChart(w, filled = w.type == "area")
        }
        if (w.series.size > 1 && w.type !in setOf(
                "pie", "donut", "funnel", "radar", "hbar", "chord",
                "heatmap", "correlation", "treemap", "waterfall", "candlestick", "gantt"
            )
        ) {
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

private data class LineGeom(val minX: Float, val minY: Float, val spanX: Float, val spanY: Float)

@Composable
private fun LineChart(w: ChartWidget, filled: Boolean) {
    var animate by remember { mutableStateOf(false) }
    LaunchedEffect(w) { animate = true }
    val progress by animateFloatAsState(if (animate) 1f else 0f, tween(700), label = "chart")
    val colors = w.series.indices.map { seriesColor(it) }
    val gridColor = GsTheme.colors.divider
    // Memoised: the draw-in animates over ~42 frames, and re-flattening every
    // point on each frame is what made large charts stutter.
    val geom = remember(w.series) {
        val flat = w.series.flatMap { it.data }
        val xs = flat.map { it.getOrElse(0) { 0f } }
        val ys = flat.map { it.getOrElse(1) { 0f } }
        val minX = xs.minOrNull() ?: 0f
        val maxX = xs.maxOrNull() ?: 1f
        val minY = minOf(0f, ys.minOrNull() ?: 0f)
        val maxY = ys.maxOrNull() ?: 1f
        LineGeom(
            minX = minX,
            minY = minY,
            spanX = (maxX - minX).takeIf { it > 0f } ?: 1f,
            spanY = (maxY - minY).takeIf { it > 0f } ?: 1f
        )
    }
    val minX = geom.minX
    val minY = geom.minY
    val spanX = geom.spanX
    val spanY = geom.spanY
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
            // Draw only fully-arrived points; the partial segment is appended
            // next. Taking visible+1 here overshot and then jumped back, which
            // showed as the line doubling over itself during the draw-in.
            val drawn = pts.take(visible)
            val path = Path()
            drawn.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
            var endX = drawn.last().x
            if (visible < pts.size) {
                val frac = pts.size * progress - visible
                val a = pts[visible - 1]; val b = pts[visible]
                endX = a.x + (b.x - a.x) * frac
                path.lineTo(endX, a.y + (b.y - a.y) * frac)
            }
            if (filled) {
                val fillPath = Path().apply {
                    addPath(path)
                    lineTo(endX, ch)
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
    val labels = remember(w.series, w.labels) {
        if (w.labels.size >= w.series.first().data.size) w.labels
        else w.series.first().data.map { it.getOrElse(0) { 0f }.toInt().toString() }
    }
    val maxV = remember(w.series) {
        w.series.flatMap { s -> s.data.map { it.getOrElse(1) { 0f } } }.maxOrNull()?.takeIf { it > 0f } ?: 1f
    }
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

/**
 * Pie and donut accept BOTH documented-ish shapes:
 *
 *  1. one series per slice, each with a single point (`name` = slice label) —
 *     the shape the prompt documents;
 *  2. one series carrying one point per label, with `labels` naming them —
 *     what most models actually emit.
 *
 * Shape 1 used to be rejected outright by `parseWidget`'s two-point minimum,
 * and shape 2 rendered as a single slice because the renderer only read
 * `series`. Both are real, so normalise here instead of guessing upstream.
 */
internal fun pieSlices(w: ChartWidget): List<Pair<String, Float>> {
    val single = w.series.singleOrNull()
    if (single != null && single.data.size > 1) {
        val names = if (w.labels.size >= single.data.size) w.labels
        else single.data.indices.map { "Slice ${it + 1}" }
        return names.take(single.data.size).mapIndexed { i, label ->
            label to single.data[i].getOrElse(1) { 0f }
        }.filter { it.second > 0f }
    }
    return w.series.map { s ->
        s.name to (s.data.firstOrNull()?.getOrElse(1) { 0f } ?: 0f)
    }.filter { it.second > 0f }
}

@Composable
private fun PieChart(w: ChartWidget) {
    var animate by remember { mutableStateOf(false) }
    LaunchedEffect(w) { animate = true }
    val progress by animateFloatAsState(if (animate) 1f else 0f, tween(700), label = "chart")
    val colors = w.series.indices.map { seriesColor(it) }
    // pie uses series[*].data[0] as (labelIndex, value); or name + single value
    val slices = remember(w.series, w.labels) { pieSlices(w) }
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
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val c = GsTheme.colors

    val filtered = remember(w.rows, query) {
        if (query.isBlank()) w.rows
        else w.rows.filter { row -> row.any { it.contains(query, ignoreCase = true) } }
    }
    val rows = remember(filtered, sortCol, asc) {
        if (sortCol < 0) filtered
        else filtered.sortedWith { a, b ->
            val va = a.getOrNull(sortCol) ?: ""
            val vb = b.getOrNull(sortCol) ?: ""
            val na = va.toFloatOrNull(); val nb = vb.toFloatOrNull()
            val cmp = if (na != null && nb != null) na.compareTo(nb) else va.compareTo(vb)
            if (asc) cmp else -cmp
        }
    }
    val visible = remember(rows, expanded) { if (expanded) rows else rows.take(8) }
    val csv = remember(w.columns, w.rows) {
        buildString {
            appendLine(w.columns.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" })
            w.rows.forEach { row ->
                appendLine(row.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" })
            }
        }
    }

    WidgetCardShell(
        title = w.title.ifEmpty { "Table" },
        tag = "table",
        action = {
            Text(
                "Copy",
                style = MaterialTheme.typography.labelSmall,
                color = c.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(c.raisedSurface)
                    .clickable { clipboard.setText(AnnotatedString(csv)) }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    ) {
        if (w.rows.size > 5) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Filter…", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
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
            visible.forEach { row ->
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
            if (rows.isEmpty()) {
                Text(
                    "No matching rows",
                    style = MaterialTheme.typography.labelMedium,
                    color = GsTheme.colors.textTertiary,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }
            if (rows.size > 8) {
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.padding(top = 2.dp)) {
                    Text(if (expanded) "Show less" else "Show all (${rows.size})")
                }
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
