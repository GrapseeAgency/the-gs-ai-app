package com.grapsee.gsai.ui.chat.content

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.ui.theme.GsTheme
import com.grapsee.gsai.ui.theme.skeletonSurface
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

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
    "doc", "document", "pdf", "embed"
)

/** Chart sub-types the `chart` kind renders (see WidgetsExtra.kt / WidgetsCharts2.kt). */
internal val CHART_TYPES = setOf(
    "line", "area", "stackedArea", "bar", "hbar", "stacked", "pie", "donut",
    "scatter", "bubble", "histogram", "funnel", "radar",
    "grouped", "boxplot", "heatmap", "correlation", "treemap", "waterfall",
    "candlestick", "gantt", "chord", "quadrant", "sankey", "network",
    "wordcloud", "sunburst", "bullet",
    // v3 — parity with the web renderer (WidgetsCharts3.kt)
    "violin", "contour", "parallel", "dendrogram", "force", "depth"
)

/**
 * Point decoding that accepts every shape a real model emits.
 *
 * The documented shape is `[[x, y], …]`, but the live server sends all three
 * of these, and each one used to fail kotlinx decoding outright — the whole
 * payload became `null` and the transcript showed raw JSON:
 *
 *   [12000, 5400, 2100]        flat values, x implied by index
 *   [["Q1", 120], ["Q2", 180]] string label as x
 *   [[0, 120], [1, 180]]       the documented pair
 *   [{"x": 0, "y": 120}]       object form
 *
 * Normalising at decode time is the only place this can be fixed once: every
 * chart renderer downstream keeps reading `[x, y]` and none of them changed.
 */
internal object PointListSerializer : KSerializer<List<List<Float>>> {
    private val delegate = ListSerializer(ListSerializer(Float.serializer()))
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): List<List<Float>> {
        val input = decoder as? JsonDecoder ?: return delegate.deserialize(decoder)
        val array = input.decodeJsonElement() as? JsonArray ?: return emptyList()
        return array.mapIndexedNotNull { index, item -> point(item, index) }
    }

    override fun serialize(encoder: Encoder, value: List<List<Float>>) {
        delegate.serialize(encoder, value)
    }

    private fun point(item: JsonElement, index: Int): List<Float>? = when (item) {
        is JsonPrimitive -> item.floatOrNull?.let { listOf(index.toFloat(), it) }
        is JsonArray -> {
            val numbers = item.mapNotNull { (it as? JsonPrimitive)?.floatOrNull }
            when {
                // Preserve up to 5 coordinates so 2D [x, y], 3D bubble [x, y, z],
                // and 4D candlestick [o, h, l, c] all survive intact.
                numbers.size >= 2 -> numbers.take(5)
                numbers.size == 1 -> listOf(index.toFloat(), numbers[0])
                else -> null
            }
        }
        is JsonObject -> {
            val o = (item["open"] as? JsonPrimitive)?.floatOrNull ?: (item["o"] as? JsonPrimitive)?.floatOrNull
            val h = (item["high"] as? JsonPrimitive)?.floatOrNull ?: (item["h"] as? JsonPrimitive)?.floatOrNull
            val l = (item["low"] as? JsonPrimitive)?.floatOrNull ?: (item["l"] as? JsonPrimitive)?.floatOrNull
            val c = (item["close"] as? JsonPrimitive)?.floatOrNull ?: (item["c"] as? JsonPrimitive)?.floatOrNull
            if (o != null && h != null && l != null && c != null) {
                listOf(o, h, l, c)
            } else {
                val y = (item["y"] as? JsonPrimitive)?.floatOrNull
                    ?: (item["value"] as? JsonPrimitive)?.floatOrNull
                    ?: (item["val"] as? JsonPrimitive)?.floatOrNull
                    ?: (item["v"] as? JsonPrimitive)?.floatOrNull
                    ?: (item["score"] as? JsonPrimitive)?.floatOrNull
                    ?: (item["count"] as? JsonPrimitive)?.floatOrNull
                    ?: (item["amount"] as? JsonPrimitive)?.floatOrNull
                if (y == null) null
                else {
                    val x = (item["x"] as? JsonPrimitive)?.floatOrNull ?: index.toFloat()
                    val z = (item["z"] as? JsonPrimitive)?.floatOrNull
                        ?: (item["size"] as? JsonPrimitive)?.floatOrNull
                        ?: (item["r"] as? JsonPrimitive)?.floatOrNull
                        ?: (item["radius"] as? JsonPrimitive)?.floatOrNull
                    if (z != null) listOf(x, y, z) else listOf(x, y)
                }
            }
        }
        else -> null
    }
}

@Serializable
data class ChartSeries(
    val name: String = "",
    @Serializable(with = PointListSerializer::class)
    val data: List<List<Float>> = emptyList(),
    /** Parallel coordinates: one value per axis, aligned to [ChartWidget.axes]. */
    val values: List<Float> = emptyList()
)

/** A graph node: the wire sends "Budget" (bare string) or {"id","label",...}. */
@Serializable(with = GraphNodeSerializer::class)
data class GraphNode(
    val id: String = "",
    val label: String = "",
    val group: String = "",
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val r: Float = 0f
)

/**
 * Node decoding that accepts both wire shapes (see PointListSerializer for
 * the same idea on points): a bare string becomes an unpositioned node whose
 * id and label are the string.
 */
internal object GraphNodeSerializer : KSerializer<GraphNode> {
    private val stringSer = String.serializer()
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("GraphNode") {
        element("id", stringSer.descriptor)
        element("label", stringSer.descriptor)
        element("group", stringSer.descriptor)
        element("x", Float.serializer().descriptor)
        element("y", Float.serializer().descriptor)
        element("z", Float.serializer().descriptor)
        element("r", Float.serializer().descriptor)
    }

    override fun deserialize(decoder: Decoder): GraphNode {
        val input = decoder as? JsonDecoder ?: return GraphNode()
        return when (val el = input.decodeJsonElement()) {
            is JsonPrimitive -> {
                val text = el.contentOrNull ?: ""
                GraphNode(id = text, label = text)
            }
            is JsonObject -> {
                fun str(vararg keys: String): String {
                    for (k in keys) {
                        val v = (el[k] as? JsonPrimitive)?.contentOrNull
                        if (v != null) return v
                    }
                    return ""
                }
                fun num(k: String): Float = (el[k] as? JsonPrimitive)?.floatOrNull ?: 0f
                GraphNode(
                    id = str("id", "label"),
                    label = str("label", "id"),
                    group = str("group"),
                    x = num("x"),
                    y = num("y"),
                    z = num("z"),
                    r = num("r")
                )
            }
            else -> GraphNode()
        }
    }

    override fun serialize(encoder: Encoder, value: GraphNode) {
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.id)
            encodeStringElement(descriptor, 1, value.label)
            encodeStringElement(descriptor, 2, value.group)
            encodeFloatElement(descriptor, 3, value.x)
            encodeFloatElement(descriptor, 4, value.y)
            encodeFloatElement(descriptor, 5, value.z)
            encodeFloatElement(descriptor, 6, value.r)
        }
    }
}

/** A graph edge: {"source","target","value"} or the compact [a, b] / [a, b, w]. */
@Serializable(with = GraphLinkSerializer::class)
data class GraphLink(val source: String = "", val target: String = "", val value: Float = 1f)

internal object GraphLinkSerializer : KSerializer<GraphLink> {
    private val stringSer = String.serializer()
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("GraphLink") {
        element("source", stringSer.descriptor)
        element("target", stringSer.descriptor)
        element("value", Float.serializer().descriptor)
    }

    override fun deserialize(decoder: Decoder): GraphLink {
        val input = decoder as? JsonDecoder ?: return GraphLink()
        return when (val el = input.decodeJsonElement()) {
            is JsonObject -> GraphLink(
                source = (el["source"] as? JsonPrimitive)?.contentOrNull ?: "",
                target = (el["target"] as? JsonPrimitive)?.contentOrNull ?: "",
                value = (el["value"] as? JsonPrimitive)?.floatOrNull
                    ?: (el["weight"] as? JsonPrimitive)?.floatOrNull
                    ?: 1f
            )
            is JsonArray -> {
                val parts = el.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                val nums = el.mapNotNull { (it as? JsonPrimitive)?.floatOrNull }
                when {
                    parts.size >= 2 -> GraphLink(parts[0], parts[1], nums.getOrNull(2) ?: 1f)
                    else -> GraphLink()
                }
            }
            else -> GraphLink()
        }
    }

    override fun serialize(encoder: Encoder, value: GraphLink) {
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.source)
            encodeStringElement(descriptor, 1, value.target)
            encodeFloatElement(descriptor, 2, value.value)
        }
    }
}

/** One weight/measure row — wordcloud rows, bullet rows. */
@Serializable
data class ChartItem(
    val text: String = "",
    val label: String = "",
    val weight: Float = 0f,
    val value: Float = 0f,
    val target: Float = Float.NaN,
    val max: Float = 0f,
    val ranges: List<List<Float>> = emptyList()
)

/** A tree node — sunburst rings, dendrogram merges (distance = merge height). */
@Serializable
data class TreeNodeData(
    val label: String = "",
    val value: Float = 0f,
    val distance: Float = 0f,
    val children: List<TreeNodeData> = emptyList()
)

@Serializable
data class ChartWidget(
    val type: String = "line",
    val title: String = "",
    val unit: String = "",
    /** Bar/pie category names; empty falls back to the x-index labels. */
    val labels: List<String> = emptyList(),
    val series: List<ChartSeries> = emptyList(),
    // v3 structural payloads — defaulted so v1/v2 payloads decode unchanged.
    val nodes: List<GraphNode> = emptyList(),
    val links: List<GraphLink> = emptyList(),
    val edges: List<GraphLink> = emptyList(),
    val items: List<ChartItem> = emptyList(),
    val root: TreeNodeData? = null,
    val axes: List<String> = emptyList(),
    /** Contour field samples [x, y, z]. */
    val points: List<List<Float>> = emptyList(),
    val levels: Int = 0,
    /** Depth (order book): [price, size] rows per side. */
    val bids: List<List<Float>> = emptyList(),
    val asks: List<List<Float>> = emptyList()
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

// --- v3 kinds (parity with the web genui library) ----------------------------

internal val SIM_TYPES = setOf("particles", "cloth", "softbody", "fluid", "smoke", "rigid", "terrain", "game")
internal val STAGE3D_TYPES = setOf("scatter3d", "surface3d", "molecule", "cad", "anatomy", "scene", "vr", "ar")
internal val CANVAS_TYPES = setOf("whiteboard", "mindmap", "diagram", "polygons")
internal val EDITOR_TYPES = setOf("sheet", "nodes", "pivot", "site", "css", "video", "audio")
internal val COLLAB_TYPES = setOf("counter", "vote", "todo", "pointer")

@Serializable
data class SimWidget(
    val sim: String = "particles",
    /** Fence-injected hint (```cloth has no payload field) — see parseWidget. */
    val type: String? = null,
    val title: String = "",
    val gravity: Float = 9.8f,
    val speed: Float = 1f,
    val damping: Float = 0.99f,
    val count: Int = 60,
    val size: Int = 64,
    val seed: Int = 42,
    val interactive: Boolean = true
)

@Serializable
data class Stage3DWidget(
    val view: String = "scene",
    val type: String? = null,
    val title: String = "",
    val nodes: List<GraphNode> = emptyList(),
    /** Pairs of node indices — molecules/cad/scene bonds. */
    val bonds: List<List<Int>> = emptyList(),
    /** surface3d: a CalcExpr math expression over x and y. */
    val z: String = "",
    val span: Float = 2f,
    val spin: Boolean = true
)

@Serializable
data class CanvasNode(val label: String = "", val x: Float = 0f, val y: Float = 0f, val group: String = "")

@Serializable
data class CanvasWidgetData(
    val surface: String = "whiteboard",
    val type: String? = null,
    val title: String = "",
    val nodes: List<CanvasNode> = emptyList(),
    val edges: List<List<Int>> = emptyList()
)

@Serializable
data class ClipData(val start: Float = 0f, val dur: Float = 4f, val label: String = "")

@Serializable
data class TrackData(val name: String = "", val clips: List<ClipData> = emptyList())

@Serializable
data class SiteBlock(val type: String = "text", val text: String = "")

@Serializable
data class EditorWidgetData(
    val editor: String = "sheet",
    val type: String? = null,
    val title: String = "",
    val columns: List<String> = emptyList(),
    val cells: Map<String, String> = emptyMap(),
    val rows: List<List<String>> = emptyList(),
    val tracks: List<TrackData> = emptyList(),
    val duration: Int = 60,
    val layout: Map<String, JsonElement> = emptyMap(),
    val blocks: List<SiteBlock> = emptyList(),
    // Node-graph editor payload (v3). Defaulted so sheet payloads decode unchanged.
    val nodes: List<EditorNodeData> = emptyList(),
    val edges: List<List<Int>> = emptyList()
)

@Serializable
data class EditorNodeData(
    val x: Float = 0f,
    val y: Float = 0f,
    val label: String = "",
    val group: String = ""
)

@Serializable
data class TerminalCommand(val name: String = "", val description: String = "")

@Serializable
data class TerminalWidget(
    val title: String = "Terminal",
    val welcome: String = "",
    val commands: List<TerminalCommand> = emptyList()
)

@Serializable
data class CollabWidgetData(
    val surface: String = "counter",
    val type: String? = null,
    val room: String = "lobby",
    val title: String = "",
    val label: String = "",
    val items: List<String> = emptyList(),
    val start: Int = 0
)

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
    data class Tabs(val w: TabsWidget) : Widget { override val title get() = w.title }
    data class Accordion(val w: AccordionWidget) : Widget { override val title get() = w.title }
    data class Steps(val w: StepsWidget) : Widget { override val title get() = w.title }
    data class Menu(val w: MenuWidget) : Widget { override val title get() = w.title }
    data class Chips(val w: ChipsWidget) : Widget { override val title get() = w.title }
    data class Rating(val w: RatingWidget) : Widget { override val title get() = w.title }
    data class Poll(val w: PollWidget) : Widget { override val title get() = w.title }
    data class Diff(val w: DiffWidget) : Widget { override val title get() = w.title }
    data class Tree(val w: TreeWidget) : Widget { override val title get() = w.title }
    data class Flashcards(val w: FlashcardsWidget) : Widget { override val title get() = w.title }
    data class Checklist(val w: ChecklistWidget) : Widget { override val title get() = w.title }
    data class Sim(val w: SimWidget) : Widget { override val title get() = w.title }
    data class Stage3D(val w: Stage3DWidget) : Widget { override val title get() = w.title }
    data class Canvas(val w: CanvasWidgetData) : Widget { override val title get() = w.title }
    data class Editor(val w: EditorWidgetData) : Widget { override val title get() = w.title }
    data class Terminal(val w: TerminalWidget) : Widget { override val title get() = w.title }
    data class Collab(val w: CollabWidgetData) : Widget { override val title get() = w.title }
}

/**
 * Decoder tuned for what models actually write, not for what the spec allows.
 *
 * Strict kotlinx JSON rejects a trailing comma, a `// note` or an unquoted key,
 * and every one of those turned a perfectly good widget into a code block. The
 * backend repairs payloads before persisting, but the client also renders
 * transcripts written by older builds and payloads that arrive mid-stream, so
 * it has to be forgiving on its own.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
internal val widgetJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    allowTrailingComma = true
    allowComments = true
}

/**
 * Parse + validate a widget payload. Returns null when anything is off — the
 * caller renders the raw JSON as a code block, so the model can never break
 * the transcript with a bad widget.
 */
internal fun chartGrowthPercentAndroid(first: Double, last: Double): Double? {
    if (!first.isFinite() || !last.isFinite() || first == 0.0) return null
    return (((last - first) / kotlin.math.abs(first)) * 100.0).takeIf { it.isFinite() }
}

internal fun chartStepPercentAndroid(values: List<Double>, index: Int): Double? {
    if (index <= 0) return null
    return chartGrowthPercentAndroid(values.getOrNull(index - 1) ?: return null, values.getOrNull(index) ?: return null)
}

fun parseWidget(kind: String, rawJson: String): Widget? {
    if (rawJson.isBlank() || rawJson.length > MAX_WIDGET_BYTES) return null
    val resolvedKind = widgetKindOf(kind) ?: kind.lowercase().trim()
    val json = normalizeWidgetJson(resolvedKind, rawJson)
    return try {
        when (resolvedKind) {
            "chart" -> {
                val w = widgetJson.decodeFromString<ChartWidget>(json)
                // Pie/donut are documented as ONE SERIES PER SLICE with a
                // single point — requiring two points silently rejected every
                // correctly-shaped pie and rendered it as a code block. A
                // gantt is ONE RANGE PER SERIES (Task 1 = [0,2]), the same
                // deal (probe 2026-10-20).
                val minPoints = if (w.type == "pie" || w.type == "donut" || w.type == "gantt") 1 else 2
                val series = w.series.take(MAX_CHART_SERIES).map {
                    it.copy(data = it.data.take(MAX_POINTS_PER_SERIES))
                }.filter { it.data.size >= minPoints }
                // v3 structural charts carry their payload OUTSIDE `series` —
                // nodes/links, items, a root tree, field points, bids/asks,
                // axes+aligned values. The two-points-per-series rule would
                // reject every correct payload of those shapes, so each type
                // gets its own body test (null = not this shape).
                val structural: ChartWidget? = when (w.type) {
                    "sankey", "network", "force" ->
                        if (w.nodes.isNotEmpty() && (w.links.isNotEmpty() || w.edges.isNotEmpty()))
                            w.copy(nodes = w.nodes.take(60), links = w.links.take(200), edges = w.edges.take(200))
                        else null
                    "wordcloud" -> if (w.items.isNotEmpty()) w.copy(items = w.items.take(60)) else null
                    "sunburst" -> w.root?.let { w.copy(root = clampTreeNode(it, 4)) }
                    "dendrogram" -> w.root?.let { w.copy(root = clampTreeNode(it, 8)) }
                    "bullet" -> if (w.items.isNotEmpty()) w.copy(items = w.items.take(12)) else null
                    "violin" ->
                        if (w.series.isNotEmpty())
                            w.copy(series = w.series.take(8).map { s -> s.copy(data = s.data.take(400)) })
                        else null
                    "contour" ->
                        if (w.points.size >= 9)
                            w.copy(points = w.points.take(2500), levels = if (w.levels in 3..12) w.levels else 6)
                        else null
                    "parallel" ->
                        if (w.axes.size >= 3 && w.series.any { it.values.isNotEmpty() })
                            w.copy(axes = w.axes.take(10), series = w.series.take(12))
                        else null
                    "depth" ->
                        if (w.bids.isNotEmpty() || w.asks.isNotEmpty())
                            w.copy(bids = w.bids.take(40), asks = w.asks.take(40))
                        else null
                    "quadrant" -> if (w.series.any { it.data.isNotEmpty() }) w else null
                    else -> null
                }
                when {
                    structural != null && w.type in CHART_TYPES -> Widget.Chart(structural)
                    series.isNotEmpty() && w.type in CHART_TYPES -> Widget.Chart(w.copy(series = series))
                    // A ```gantt fence whose payload is a schedule of dated
                    // entries, not a series: a chart with no series is nothing,
                    // but a timeline is exactly what the model meant (the probe
                    // caught the live payload). Fall through before giving up.
                    series.isEmpty() && hasEntries(json) ->
                        widgetJson.decodeFromString<TimelineWidget>(json).let { t ->
                            val entries = t.entries.take(MAX_TIMELINE_ENTRIES)
                            if (entries.isEmpty()) null else Widget.Timeline(t.copy(entries = entries))
                        }
                    else -> null
                }
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
            "media" -> {
                // A payload with pages and no type at all is a document — the
                // fence (or bare promotion) said so, the payload just omitted
                // it. The data class default fills "image", so absence has to
                // be read off the raw JSON object, never off the decoded model.
                val statedType = (widgetJson.parseToJsonElement(json) as? JsonObject)
                    ?.get("type")
                    ?.let { it as? JsonPrimitive }
                    ?.contentOrNull
                    ?.lowercase()
                widgetJson.decodeFromString<MediaWidget>(json).let { w ->
                    // A ```search fence names the list `results` — same items.
                    val allItems = w.items.ifEmpty { w.results }
                    val raw = statedType
                        ?: if (w.pages.bodies.any { it.text.isNotBlank() } || w.pages.count > 0) "pdf" else "image"
                    val type = if (raw == "document" || raw == "pdf") "doc" else raw
                    if (type !in MEDIA_TYPES) null
                    else if (type == "code") {
                        if (w.code.isBlank()) null else Widget.Media(w.copy(type = type))
                    } else if (type == "doc") {
                        val items = allItems.take(3).filter { it.url.startsWith("https://") }
                        // The live model answers a ```pdf request with pages
                        // (bodies or a count) and no URLs at all — that IS the
                        // document.
                        if (items.isEmpty() && w.pages.bodies.none { it.text.isNotBlank() } && w.pages.count <= 0) null
                        else Widget.Media(w.copy(type = type, items = items))
                    } else {
                        val items = allItems.take(20).filter { it.url.isNotBlank() || it.title.isNotBlank() }
                        if (items.isEmpty()) null else Widget.Media(w.copy(type = type, items = items))
                    }
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
                val rawType = w.type.lowercase().ifBlank { "finance" }
                val normType = if (rawType in CALC_TYPES) rawType else "finance"
                if (fields.isEmpty() || outputs.isEmpty()) null
                else Widget.Calc(
                    w.copy(
                        type = normType,
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
            // v3 — sims, 3D stages, canvases, editors, terminal, collab.
            "sim" -> widgetJson.decodeFromString<SimWidget>(json).let { w ->
                val sim = if (w.sim in SIM_TYPES) w.sim
                else w.type?.takeIf { it in SIM_TYPES } ?: "particles"
                Widget.Sim(
                    w.copy(
                        sim = sim,
                        gravity = w.gravity.coerceIn(-30f, 30f),
                        speed = w.speed.coerceIn(0.1f, 3f),
                        damping = w.damping.coerceIn(0f, 1f),
                        count = w.count.coerceIn(1, 120),
                        size = w.size.coerceIn(20, 120)
                    )
                )
            }
            "stage3d" -> widgetJson.decodeFromString<Stage3DWidget>(json).let { w ->
                val view = if (w.view in STAGE3D_TYPES) w.view
                else w.type?.takeIf { it in STAGE3D_TYPES } ?: "scene"
                val nodes = w.nodes.take(60)
                // z must be a real math expression — the same CalcExpr the calc
                // widgets use is the guard; junk never reaches the renderer.
                val zExpr = w.z.takeIf { it.isNotBlank() && it.length <= 120 }
                val zOk = zExpr == null || CalcExpr.eval(zExpr, mapOf("x" to 0.5, "y" to 0.5)) != null
                val bonds = w.bonds
                    .filter { it.size >= 2 }
                    .map { p -> listOf(p[0], p[1]) }
                    .filter { (a, b) -> a >= 0 && b >= 0 && a < nodes.size && b < nodes.size }
                    .take(200)
                if (!zOk) null
                else Widget.Stage3D(w.copy(view = view, nodes = nodes, bonds = bonds, z = zExpr ?: ""))
            }
            "canvas" -> widgetJson.decodeFromString<CanvasWidgetData>(json).let { w ->
                val surface = if (w.surface in CANVAS_TYPES) w.surface
                else w.type?.takeIf { it in CANVAS_TYPES } ?: "whiteboard"
                val nodes = w.nodes.take(40).map { n -> n.copy(x = n.x.coerceIn(0f, 1f), y = n.y.coerceIn(0f, 1f)) }
                val edges = w.edges
                    .filter { it.size >= 2 && it[0] >= 0 && it[1] >= 0 && it[0] < nodes.size && it[1] < nodes.size }
                    .take(120)
                Widget.Canvas(w.copy(surface = surface, nodes = nodes, edges = edges))
            }
            "editor" -> widgetJson.decodeFromString<EditorWidgetData>(json).let { w ->
                val editor = if (w.editor in EDITOR_TYPES) w.editor
                else w.type?.takeIf { it in EDITOR_TYPES } ?: "sheet"
                Widget.Editor(
                    w.copy(
                        editor = editor,
                        columns = w.columns.take(12),
                        rows = w.rows.take(60).map { r -> r.take(12) },
                        cells = w.cells.entries.take(200).associate { it.key to it.value },
                        tracks = w.tracks.take(6),
                        duration = w.duration.coerceIn(1, 600),
                        blocks = w.blocks.take(20)
                    )
                )
            }
            "terminal" -> widgetJson.decodeFromString<TerminalWidget>(json).let { w ->
                val commands = w.commands.take(12).filter { it.name.isNotBlank() }
                Widget.Terminal(w.copy(commands = commands))
            }
            "collab" -> widgetJson.decodeFromString<CollabWidgetData>(json).let { w ->
                val surface = if (w.surface in COLLAB_TYPES) w.surface
                else w.type?.takeIf { it in COLLAB_TYPES } ?: "counter"
                val room = w.room.lowercase().replace(Regex("[^a-z0-9-]+"), "-")
                    .trim('-').take(48).ifEmpty { "lobby" }
                Widget.Collab(w.copy(surface = surface, room = room, items = w.items.filter { it.isNotBlank() }.take(12)))
            }
            // v2 — layout, navigation, and interactive controls.
            "tabs" -> widgetJson.decodeFromString<TabsWidget>(json).let { w ->
                val tabs = w.tabs.filter { it.label.isNotBlank() && (it.body.isNotBlank() || it.items.isNotEmpty()) }.take(8)
                if (tabs.isEmpty()) null else Widget.Tabs(w.copy(tabs = tabs))
            }
            "accordion" -> widgetJson.decodeFromString<AccordionWidget>(json).let { w ->
                val sections = w.sections.filter { it.label.isNotBlank() && it.body.isNotBlank() }.take(10)
                if (sections.isEmpty()) null else Widget.Accordion(w.copy(sections = sections))
            }
            "steps" -> widgetJson.decodeFromString<StepsWidget>(json).let { w ->
                val steps = w.steps.filter { it.title.isNotBlank() }.take(12)
                if (steps.isEmpty()) null else Widget.Steps(w.copy(steps = steps))
            }
            "menu" -> widgetJson.decodeFromString<MenuWidget>(json).let { w ->
                val options = w.options.filter { it.label.isNotBlank() }.take(12)
                if (options.isEmpty()) null else Widget.Menu(w.copy(options = options))
            }
            "chips" -> widgetJson.decodeFromString<ChipsWidget>(json).let { w ->
                val options = w.options.filter { it.label.isNotBlank() }.take(12)
                if (options.isEmpty()) null else Widget.Chips(w.copy(options = options))
            }
            "rating" -> widgetJson.decodeFromString<RatingWidget>(json).let { w ->
                val style = if (w.style in setOf("star", "heart", "thumb", "nps")) w.style else "star"
                if (w.title.isBlank() && w.prompt.isBlank()) null else Widget.Rating(w.copy(style = style))
            }
            "poll" -> widgetJson.decodeFromString<PollWidget>(json).let { w ->
                val options = w.options.filter { it.label.isNotBlank() }.take(12)
                if (options.size < 2 || options.any { it.votes < 0 }) null else Widget.Poll(w.copy(options = options))
            }
            "diff" -> widgetJson.decodeFromString<DiffWidget>(json).let { w ->
                if (w.unified.isBlank() && w.before.isBlank() && w.after.isBlank()) null
                else Widget.Diff(w)
            }
            "tree" -> widgetJson.decodeFromString<TreeWidget>(json).let { w ->
                val roots = w.roots.filter { it.label.isNotBlank() }.take(8)
                if (roots.isEmpty()) null else Widget.Tree(w.copy(roots = roots))
            }
            "flashcards" -> widgetJson.decodeFromString<FlashcardsWidget>(json).let { w ->
                val cards = w.cards.filter { it.front.isNotBlank() && it.back.isNotBlank() }.take(20)
                if (cards.isEmpty()) null else Widget.Flashcards(w.copy(cards = cards))
            }
            "checklist" -> widgetJson.decodeFromString<ChecklistWidget>(json).let { w ->
                val items = w.items.filter { it.label.isNotBlank() }.take(30)
                if (items.isEmpty()) null else Widget.Checklist(w.copy(items = items))
            }
            else -> null
        }
    } catch (_: Throwable) {
        null // malformed JSON / wrong types -> degrade to a code block
    }
}

/**
 * The widget kind a BARE payload describes, or null when it is not a widget.
 *
 * The live server sometimes streams the payload with no fence at all — just
 * `{"type":"bar","series":[…]}`, or the same thing inside a ```json block.
 * Without this, the transcript showed that JSON as body text: the exact "why is
 * it printing code at me" failure. Kind is inferred from the payload's own
 * discriminator and then CONFIRMED by the real validator, so prose that merely
 * contains braces can never be promoted.
 */
/** Depth + width clamp for tree payloads (sunburst/dendrogram). */
private fun clampTreeNode(node: TreeNodeData, maxDepth: Int, depth: Int = 1): TreeNodeData =
    if (depth >= maxDepth) node.copy(children = emptyList())
    else node.copy(children = node.children.take(8).map { clampTreeNode(it, maxDepth, depth + 1) })

/** A payload that carries dated `entries` but no series: a schedule, not a chart. */
private fun hasEntries(json: String): Boolean =
    try {
        (widgetJson.parseToJsonElement(json) as? JsonObject)?.containsKey("entries") == true
    } catch (_: Throwable) {
        false
    }

/**
 * A plan is TIMED rows, not any array called `rows`. Requiring both a time and
 * an activity keeps a table, a progress list or a bespoke `{rows:[...]}` widget
 * from being stolen by the timetable renderer. Mirrors `isNexlyPlanPayload`.
 */
private fun hasTimedRows(obj: JsonObject): Boolean {
    val root = listOf("plan", "schedule", "timetable")
        .firstNotNullOfOrNull { obj[it] as? JsonObject } ?: obj
    val rows = (root["rows"] as? JsonArray) ?: (root["items"] as? JsonArray) ?: return false
    return rows.any { element ->
        val row = element as? JsonObject ?: return@any false
        val time = (row["time"] ?: row["start"] ?: row["from"] ?: row["date"] ?: row["slot"]) as? JsonPrimitive
        val text = (row["text"] ?: row["label"] ?: row["title"]
            ?: row["activity"] ?: row["detail"] ?: row["task"]) as? JsonPrimitive
        val hasTime = time?.contentOrNull?.isNotBlank() == true
        val hasText = text?.contentOrNull?.isNotBlank() == true
        hasTime && hasText
    }
}

fun inferWidgetKind(json: String): String? {
    val normalized = normalizeWidgetJson("json", json)
    val obj = try {
        widgetJson.parseToJsonElement(normalized) as? JsonObject
    } catch (_: Throwable) {
        null
    } ?: return null
    val keys = obj.keys
    val candidates = buildList {
        // Explicit kind / sub-type discriminators
        for (k in listOf("kind", "widget", "type", "sim", "view", "surface", "editor")) {
            val raw = (obj[k] as? JsonPrimitive)?.contentOrNull?.trim() ?: continue
            val mapped = widgetKindOf(raw)
            if (mapped != null && mapped !in this) add(mapped)
        }
        // Structural signatures across all supported kinds
        if (("rows" in keys || "items" in keys) && hasTimedRows(obj)) add("plan")
        if ("series" in keys || "nodes" in keys && "links" in keys || "words" in keys || "root" in keys || "axes" in keys) add("chart")
        if ("columns" in keys && "rows" in keys) {
            add("table"); add("editor"); add("app")
        }
        if ("tabs" in keys) add("tabs")
        if ("sections" in keys) { add("accordion"); add("tabs") }
        if ("roots" in keys || "tree" in keys) add("tree")
        if ("unified" in keys || ("before" in keys && "after" in keys) || ("old" in keys && "new" in keys)) add("diff")
        if ("cards" in keys) {
            add("flashcards"); add("stat"); add("cards")
        }
        if ("left" in keys && "right" in keys) add("compare")
        if ("entries" in keys) add("timeline")
        if ("questions" in keys) add("quiz")
        if ("fields" in keys) add("form")
        if ("options" in keys || "choices" in keys) {
            add("poll"); add("menu"); add("chips")
        }
        if ("style" in keys && ("max" in keys || "prompt" in keys)) add("rating")
        if ("prompts" in keys) add("followups")
        if ("buttons" in keys) add("actions")
        if ("phrases" in keys) add("voice")
        if ("sliders" in keys || "outputs" in keys) add("calc")
        if ("sim" in keys) add("sim")
        if ("view" in keys || "bonds" in keys || "z" in keys) add("stage3d")
        if ("surface" in keys) {
            add("canvas"); add("collab")
        }
        if ("editor" in keys || "tracks" in keys || "cells" in keys || "blocks" in keys) add("editor")
        if ("commands" in keys || "cwd" in keys) add("terminal")
        if ("room" in keys) add("collab")
        if ("steps" in keys) {
            add("steps"); add("app")
        }
        if ("items" in keys || "tasks" in keys || "todos" in keys) {
            add("checklist"); add("progress"); add("gauge"); add("carousel"); add("cards"); add("steps"); add("accordion"); add("menu"); add("chips"); add("app"); add("media"); add("collab")
        }
        if ("pages" in keys) add("media")
        if ("groups" in keys || "slots" in keys) add("app")
        if ("body" in keys && ("title" in keys || "tone" in keys || "cta" in keys)) add("panel")
    }
    return candidates.firstOrNull {
        if (it == "plan") parseNexlyPlanData(normalized) != null else parseWidget(it, normalized) != null
    }
}

// ---------------------------------------------------------------------------
// Rendering
// ---------------------------------------------------------------------------
private val widgetShape = RoundedCornerShape(14.dp)

/**
 * Twelve curated data hues, fixed rather than theme roles.
 *
 * The old palette cycled five semantic tokens (accent/success/warning/link/
 * toolExecution) — so a two-series chart, a five-slice pie and a progress list
 * all came out looking like the same chart, and every card on the screen shared
 * one background. A fixed data palette keeps a series identifiable across
 * light/dark and across widgets, which is what every production GENUI surface
 * (OpenAI's Open-JSON-UI, Google A2UI, Vercel AI SDK) does: colour is DATA,
 * carried by the payload, not by the container.
 */
internal object WidgetPalette {
    private val hues = listOf(
        0xFF4C6FFF, // 0 azure
        0xFF0CA678, // 1 jade
        0xFFF08C00, // 2 amber
        0xFFE8590C, // 3 tangerine
        0xFF9C36B5, // 4 orchid
        0xFF1098AD, // 5 teal
        0xFFD6336C, // 6 rose
        0xFF6741D9, // 7 violet
        0xFF66A80F, // 8 olive
        0xFFE03131, // 9 crimson
        0xFF0B7285, // 10 deep teal
        0xFF5F3DC4  // 11 indigo
    )

    fun at(i: Int): Color = Color(hues[((i % hues.size) + hues.size) % hues.size])

    /** True when the canvas is dark — hues get lifted so they stay legible. */
    @Composable
    fun onDarkCanvas(): Boolean = GsTheme.colors.appBackground.luminance() < 0.4f

    /** Lift a hue on dark canvases: same identity, readable weight. */
    fun legible(color: Color, dark: Boolean): Color =
        if (!dark) color else Color(
            red = color.red + (1f - color.red) * 0.30f,
            green = color.green + (1f - color.green) * 0.30f,
            blue = color.blue + (1f - color.blue) * 0.30f,
            alpha = color.alpha
        )
}

/**
 * Series colour, index-stable and genuinely varied (12 hues, not 5).
 *
 * When the model named an accent for this widget, the FIRST series takes it —
 * otherwise the card would be tangerine and its bars azure, which reads as a
 * bug rather than a design.
 */
@Composable
internal fun seriesColor(i: Int): Color {
    val dark = WidgetPalette.onDarkCanvas()
    val accent = LocalWidgetStyle.current?.accent
    if (accent != null) {
        return WidgetPalette.legible(if (i == 0) accent else WidgetPalette.at(i + 1), dark)
    }
    return WidgetPalette.legible(WidgetPalette.at(i), dark)
}

/**
 * DrawScope-safe twin of [seriesColor]: Canvas draw lambdas are NOT
 * @Composable, so a palette read has to happen without composition. Same
 * twelve hues, same index stability — only the accent override is skipped.
 */
internal fun seriesInk(i: Int, dark: Boolean = false): Color =
    WidgetPalette.legible(WidgetPalette.at(i), dark)

/**
 * Surface treatment. Six genuinely different faces, so a chart, a KPI, a form
 * and a table never read as the same object. Editorial and Inset change the
 * STRUCTURE — a left rule, an inner panel — not just the tint of one card.
 */
internal enum class CardTone {
    /** Solid elevated surface — dense content (tables, cards, media). */
    Plain,

    /** Hue wash from the top + hairline — data visualisations. */
    Tinted,

    /** Stronger hue wash — hero numbers (stat, gauge). */
    Hero,

    /** No fill, hairline border — lists and quiet controls. */
    Outline,

    /** No card at all: transparent with a 2dp accent rule on the left. */
    Editorial,

    /** A recessed panel inside the card — forms, quizzes, calculators, apps. */
    Inset
}

/**
 * Stable hue per widget family. Same kind → same identity, different kinds →
 * different identity, with no per-call-site bookkeeping: the shell derives it
 * from the tag, so all ~40 existing widget views gained a face at once.
 */
internal fun widgetHue(tag: String): Color {
    val key = tag.lowercase()
    val slot = FAMILY_SLOTS[key]
    return if (slot != null) WidgetPalette.at(slot) else WidgetPalette.at(key.hashCode())
}

private val FAMILY_SLOTS = mapOf(
    "chart" to 0, "stat" to 1, "gauge" to 2, "compare" to 3,
    "progress" to 4, "timeline" to 5, "table" to 6, "quiz" to 7,
    "form" to 8, "carousel" to 9, "cards" to 10, "followups" to 11,
    "media" to 3, "actions" to 2, "panel" to 1, "voice" to 8,
    "calc" to 9, "app" to 4,
    "tabs" to 0, "accordion" to 5, "steps" to 4, "menu" to 8,
    "chips" to 11, "rating" to 2, "poll" to 7, "diff" to 9,
    "tree" to 10, "flashcards" to 6, "checklist" to 1
)

/**
 * The FAMILY a tag belongs to.
 *
 * `media`, `calc` and `app` views pass their SUB-TYPE as the tag ("pdf",
 * "loan", "kanban") so each one gets its own hue — but the tone table only
 * knew the kind names, so every calculator, every media card and every app
 * surface silently fell through to Plain and lost its tint. Resolving the
 * family first keeps the per-type identity AND the intended surface.
 */
internal fun widgetFamily(tag: String): String {
    val key = tag.lowercase()
    return when {
        key in APP_TYPES -> "app"
        key in CALC_TYPES -> "calc"
        key in MEDIA_TYPES -> "media"
        else -> key
    }
}

internal fun widgetTone(tag: String): CardTone = when (widgetFamily(tag)) {
    "chart", "carousel", "tabs", "flashcards" -> CardTone.Tinted
    "stat", "gauge", "rating" -> CardTone.Hero
    "form", "quiz", "calc", "app", "poll", "diff" -> CardTone.Inset
    "timeline", "panel", "steps", "accordion" -> CardTone.Editorial
    "table", "cards", "media", "tree", "checklist" -> CardTone.Plain
    else -> CardTone.Outline
}

/** Editorial is square, inset is rounder, everything else keeps the shared 14. */
private val editorShape = RoundedCornerShape(0.dp)
private val insetShape = RoundedCornerShape(16.dp)
private val insetInnerShape = RoundedCornerShape(12.dp)
private fun toneShape(tone: CardTone): RoundedCornerShape = when (tone) {
    CardTone.Editorial -> editorShape
    CardTone.Inset -> insetShape
    else -> widgetShape
}

/**
 * Art direction chosen by the MODEL, for one widget.
 *
 * The renderer derives a sensible identity from the widget's family, but the
 * model is the one that knows what the block is about — a churn chart should
 * be crimson, a savings chart jade. So the payload may carry `accent` and
 * `variant`, and they win over the derived defaults. Provided through a
 * CompositionLocal so all ~40 widget views honour it without a single call-site
 * change (and without threading a parameter through 18 data classes).
 */
internal data class WidgetStyle(val accent: Color? = null, val tone: CardTone? = null)

internal val LocalWidgetStyle = staticCompositionLocalOf<WidgetStyle?> { null }

/** `#RRGGBB` or `#AARRGGBB`, or null when it is not a colour. */
internal fun parseHexColor(raw: String): Color? {
    val s = raw.trim().removePrefix("#")
    if (s.length != 6 && s.length != 8) return null
    val value = s.toLongOrNull(16) ?: return null
    return if (s.length == 6) Color(0xFF000000L or value) else Color(value)
}

/** Reads the model's style keys off a raw payload; null when it specified none. */
internal fun widgetStyleOf(json: String): WidgetStyle? {
    val obj = try {
        widgetJson.parseToJsonElement(json) as? JsonObject
    } catch (_: Throwable) {
        null
    } ?: return null
    val accent = (obj["accent"] as? JsonPrimitive)?.contentOrNull?.let(::parseHexColor)
    val tone = when ((obj["variant"] as? JsonPrimitive)?.contentOrNull?.lowercase()) {
        "plain" -> CardTone.Plain
        "tinted" -> CardTone.Tinted
        "hero" -> CardTone.Hero
        "outline" -> CardTone.Outline
        "editorial" -> CardTone.Editorial
        "inset" -> CardTone.Inset
        else -> null
    }
    return if (accent == null && tone == null) null else WidgetStyle(accent, tone)
}

private fun widgetTagGlassIcon(tag: String): Pair<String, String> {
    return when (tag.lowercase()) {
        "chart" -> "chart-bar" to "lavender"
        "table" -> "list-check" to "cyan"
        "calc", "calculator" -> "calculator" to "amber"
        "stat", "kpi", "metric" -> "trending-up" to "green"
        "compare", "versus" -> "arrows-sort" to "lavender"
        "timeline", "schedule", "calendar" -> "calendar" to "blue"
        "quiz", "poll", "choices" -> "help-circle" to "lavender"
        "form", "input" -> "edit" to "cyan"
        "checklist", "productivity", "kanban", "todo" -> "checkbox" to "green"
        "code", "terminal", "diff" -> "code" to "slate"
        "tree", "hierarchy", "flow", "diagram" -> "target" to "teal"
        "citation", "sources", "links" -> "link" to "blue"
        "alert", "callout", "warning" -> "alert-triangle" to "amber"
        else -> "sparkles" to "lavender"
    }
}

/**
 * The one card surface. [hue] and [tone] default from [tag], so a view only
 * passes them when it wants a specific identity (charts key off their type,
 * compare keys off its two sides). A model-supplied [LocalWidgetStyle] always
 * wins.
 */
@Composable
internal fun WidgetCardShell(
    title: String,
    tag: String,
    action: (@Composable () -> Unit)? = null,
    hue: Color = widgetHue(tag),
    tone: CardTone = widgetTone(tag),
    content: @Composable () -> Unit
) {
    val style = LocalWidgetStyle.current
    val chosenHue = style?.accent ?: hue
    val chosenTone = style?.tone ?: tone
    val c = GsTheme.colors
    val dark = WidgetPalette.onDarkCanvas()
    val accent = WidgetPalette.legible(chosenHue, dark)
    val base = c.elevatedSurface
    val clear = Color.Transparent
    val shape = toneShape(chosenTone)
    val fill: Brush = when (chosenTone) {
        CardTone.Plain, CardTone.Inset -> Brush.verticalGradient(listOf(base, base))
        CardTone.Outline, CardTone.Editorial -> Brush.verticalGradient(listOf(clear, clear))
        CardTone.Tinted -> Brush.verticalGradient(
            listOf(accent.copy(alpha = if (dark) 0.16f else 0.10f), base, base)
        )
        CardTone.Hero -> Brush.verticalGradient(
            listOf(accent.copy(alpha = if (dark) 0.26f else 0.18f), base, base)
        )
    }
    val edge = when (chosenTone) {
        CardTone.Outline -> c.divider
        CardTone.Plain, CardTone.Inset -> c.border.copy(alpha = 0.55f)
        CardTone.Editorial -> clear
        else -> accent.copy(alpha = if (dark) 0.32f else 0.22f)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(fill)
            // Editorial is not a card: a 2dp hue rule on the left edge IS the
            // surface, so the block reads as a figure in the answer.
            .then(
                if (chosenTone == CardTone.Editorial) {
                    Modifier.drawBehind {
                        drawRect(color = accent, size = Size(2.dp.toPx(), size.height))
                    }
                } else {
                    Modifier.border(1.dp, edge, shape)
                }
            )
    ) {
        // Two-pixel hue hairline: the cheapest way to give every family a face
        // without touching a single chart renderer.
        if (chosenTone == CardTone.Tinted || chosenTone == CardTone.Hero) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.10f))))
            )
        }
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            val (leadingIcon, cleanedTitle) = remember(title, tag) {
                val pair = NexlyIcons.extractLeadingIcon(title)
                val clean = NexlyIcons.cleanPlainText(pair.second)
                val defaultPair = widgetTagGlassIcon(tag)
                (pair.first ?: defaultPair.first) to clean
            }
            val defaultTone = remember(tag) { widgetTagGlassIcon(tag).second }
            Row(verticalAlignment = Alignment.CenterVertically) {
                NexlyGlassGlyphTile(
                    name = leadingIcon,
                    toneName = defaultTone,
                    size = 24.dp,
                    glyphSize = 14.dp,
                    modifier = Modifier.padding(end = 8.dp)
                )
                if (cleanedTitle.isNotEmpty()) {
                    Text(
                        cleanedTitle,
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
                    color = accent,
                    modifier = if (chosenTone == CardTone.Editorial) {
                        // Editorial has no pill: the tag is a quiet caption on
                        // the rule, not another chip on another card.
                        Modifier.padding(start = 8.dp)
                    } else {
                        Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(accent.copy(alpha = if (dark) 0.20f else 0.12f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    }
                )
            }
            // Inset is a card inside a card: the content sits in its own
            // recessed panel, so a form never reads as a chart with fields.
            if (chosenTone == CardTone.Inset) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(insetInnerShape)
                        .background(c.raisedSurface)
                        .border(1.dp, c.border.copy(alpha = 0.45f), insetInnerShape)
                        .padding(10.dp)
                ) { content() }
            } else {
                content()
            }
        }
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
    val widget = remember(block.kind, block.json) {
        parseWidget(block.kind, block.json)
            ?: inferWidgetKind(block.json)?.let { inferred -> parseWidget(inferred, block.json) }
    }
    if (widget == null) {
        WidgetFallback(block)
        return
    }
    val style = remember(block.json) { widgetStyleOf(block.json) }
    CompositionLocalProvider(LocalWidgetStyle provides style) {
        WidgetView(widget, onSendPrompt)
    }
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
        is Widget.Compare -> CompareWidgetView(widget.w, onSendPrompt)
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
        is Widget.Tabs -> TabsWidgetView(widget.w)
        is Widget.Accordion -> AccordionWidgetView(widget.w)
        is Widget.Steps -> StepsWidgetView(widget.w)
        is Widget.Menu -> MenuWidgetView(widget.w, onSendPrompt)
        is Widget.Chips -> ChipsWidgetView(widget.w, onSendPrompt)
        is Widget.Rating -> RatingWidgetView(widget.w, onSendPrompt)
        is Widget.Poll -> PollWidgetView(widget.w)
        is Widget.Diff -> DiffWidgetView(widget.w)
        is Widget.Tree -> TreeWidgetView(widget.w)
        is Widget.Flashcards -> FlashcardsWidgetView(widget.w)
        is Widget.Checklist -> ChecklistWidgetView(widget.w)
        is Widget.Sim -> SimWidgetView(widget.w)
        is Widget.Stage3D -> Stage3DWidgetView(widget.w)
        is Widget.Canvas -> CanvasWidgetView(widget.w)
        is Widget.Editor -> EditorWidgetView(widget.w)
        is Widget.Terminal -> TerminalWidgetView(widget.w)
        is Widget.Collab -> CollabWidgetView(widget.w)
    }
}

// --- chart ------------------------------------------------------------------

/**
 * Each chart SUB-TYPE gets its own hue, so a transcript with a bar chart, a
 * funnel and a heatmap reads as three different objects instead of three
 * copies of one grey card. Data series still use the shared 12-hue palette —
 * the identity is on the container, the meaning stays in the data.
 */
private val CHART_HUE_SLOTS = mapOf(
    "bar" to 0, "hbar" to 0, "grouped" to 0, "stacked" to 0,
    "line" to 1, "histogram" to 2, "boxplot" to 2,
    "funnel" to 3, "pie" to 4, "donut" to 4,
    "area" to 5, "stackedArea" to 5, "gantt" to 5,
    "scatter" to 6, "bubble" to 6,
    "radar" to 7, "chord" to 7,
    "waterfall" to 8, "heatmap" to 9, "correlation" to 9,
    "treemap" to 10, "candlestick" to 11,
    "violin" to 4, "contour" to 5, "parallel" to 8, "dendrogram" to 10,
    "force" to 7, "depth" to 3, "sankey" to 10, "network" to 1,
    "wordcloud" to 8, "sunburst" to 4, "bullet" to 2, "quadrant" to 6
)

/** Charts that read better as a figure than as a filled card. */
private val EDITORIAL_CHARTS = setOf("line", "area", "stackedArea", "scatter", "bubble", "candlestick")

private val SWITCHABLE_CHART_TYPES = setOf("line", "bar", "area", "pie", "donut")
internal fun sliceChartByRange(w: ChartWidget, rangeIndex: Int): Pair<ChartWidget, String> {
    val maxPts = w.series.maxOfOrNull { it.data.size } ?: 0
    if (maxPts <= 2) return w to "All ($maxPts pts)"
    val rangeSpecs = listOf(
        Triple("All ($maxPts pts)", 0, maxPts),
        Triple("Last ${maxOf(2, (maxPts * 3 + 3) / 4)} pts", maxPts - maxOf(2, (maxPts * 3 + 3) / 4), maxPts),
        Triple("Last ${maxOf(2, (maxPts + 1) / 2)} pts", maxPts - maxOf(2, (maxPts + 1) / 2), maxPts),
        Triple("First ${maxOf(2, (maxPts + 1) / 2)} pts", 0, maxOf(2, (maxPts + 1) / 2))
    )
    val (label, start, end) = rangeSpecs[rangeIndex % rangeSpecs.size]
    if (start == 0 && end == maxPts) return w to label
    val slicedLabels = w.labels.drop(start).take(end - start)
    val slicedSeries = w.series.map { s ->
        // A short series contributes only observations inside this window.
        // It must never fall back to its entire unsliced history.
        val sub = s.data.drop(start).take(end - start).mapIndexed { idx, pt ->
            if (pt.size >= 2) listOf(idx.toFloat(), pt[1]) + pt.drop(2) else pt
        }
        s.copy(data = sub)
    }.filter { it.data.isNotEmpty() }
    return w.copy(labels = slicedLabels, series = slicedSeries) to label
}

@Composable
private fun ChartWidgetView(w: ChartWidget) {
    val isSwitchable = w.type in SWITCHABLE_CHART_TYPES && w.series.isNotEmpty()
    var activeType by remember(w) { mutableStateOf(w.type) }
    var rangeIndex by remember(w) { mutableStateOf(0) }
    val (slicedWidget, rangeLabel) = remember(w, rangeIndex, isSwitchable) {
        if (isSwitchable) sliceChartByRange(w, rangeIndex) else w to "All"
    }

    WidgetCardShell(
        NexlyIcons.cleanPlainText(w.title).ifEmpty { "Chart" },
        "chart",
        hue = WidgetPalette.at(CHART_HUE_SLOTS[activeType] ?: 0),
        tone = if (activeType in EDITORIAL_CHARTS) CardTone.Editorial else CardTone.Tinted
    ) {
        if (isSwitchable) {
            // 07. Interactive Charts: Type Switcher Pills + Range Selector (Screenshot 6)
            val tabs = listOf(
                Triple("line", "chart-line", "Line"),
                Triple("bar", "chart-bar", "Bar"),
                Triple("area", "chart-area", "Area"),
                Triple(if (w.type == "donut") "donut" else "pie", "chart-pie", "Pie")
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFEDE6FF))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEach { (id, iconName, label) ->
                        val selected = activeType == id || (id == "pie" && activeType == "donut")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (selected) Color(0xFF6C4CF6) else Color.Transparent)
                                .clickable { activeType = id }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            NexlyGlyph(
                                name = iconName,
                                size = 12.dp,
                                color = if (selected) Color.White else Color(0xFF3E3666)
                            )
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color.White else Color(0xFF3E3666)
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.8f))
                        .border(1.dp, Color(0xFFDFD7FA), RoundedCornerShape(8.dp))
                        .clickable { rangeIndex = (rangeIndex + 1) % 4 }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    NexlyGlyph(
                        name = "calendar",
                        size = 12.dp,
                        color = Color(0xFF433B6B)
                    )
                    Text(
                        text = "$rangeLabel ▾",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF433B6B)
                    )
                }
            }
        }

        val effectiveWidget = remember(slicedWidget, activeType) {
            if (activeType == slicedWidget.type) slicedWidget else slicedWidget.copy(type = activeType)
        }
        when (effectiveWidget.type) {
            "bar", "histogram" -> BarChart(effectiveWidget)
            "pie" -> PieChart(effectiveWidget)
            "donut" -> DonutChart(effectiveWidget)
            "hbar" -> HBarChart(effectiveWidget)
            "stacked" -> StackedBarChart(effectiveWidget)
            "stackedArea" -> StackedAreaChart(effectiveWidget)
            "scatter" -> ScatterChart(effectiveWidget, bubble = false)
            "bubble" -> ScatterChart(effectiveWidget, bubble = true)
            "funnel" -> FunnelChart(effectiveWidget)
            "radar" -> RadarChart(effectiveWidget)
            "grouped" -> GroupedBarChart(effectiveWidget)
            "boxplot" -> BoxPlotChart(effectiveWidget)
            "heatmap" -> HeatmapChart(effectiveWidget)
            "correlation" -> HeatmapChart(effectiveWidget, diverging = true)
            "treemap" -> TreemapChart(effectiveWidget)
            "waterfall" -> WaterfallChart(effectiveWidget)
            "candlestick" -> CandlestickChart(effectiveWidget)
            "gantt" -> GanttChart(effectiveWidget)
            "chord" -> ChordChart(effectiveWidget)
            // v3 parity (WidgetsCharts3.kt)
            "sankey" -> SankeyChart(effectiveWidget)
            "network" -> NetworkChart(effectiveWidget)
            "force" -> ForceChart(effectiveWidget)
            "wordcloud" -> WordcloudChart(effectiveWidget)
            "sunburst" -> SunburstChart(effectiveWidget)
            "bullet" -> BulletChart(effectiveWidget)
            "quadrant" -> QuadrantChart(effectiveWidget)
            "violin" -> ViolinChart(effectiveWidget)
            "contour" -> ContourChart(effectiveWidget)
            "parallel" -> ParallelChart(effectiveWidget)
            "dendrogram" -> DendrogramChart(effectiveWidget)
            "depth" -> DepthChart(effectiveWidget)
            else -> LineChart(effectiveWidget, filled = effectiveWidget.type == "area")
        }
        if (slicedWidget.series.size > 1 && activeType !in setOf(
                "pie", "donut", "funnel", "radar", "hbar", "chord",
                "heatmap", "correlation", "treemap", "waterfall", "candlestick", "gantt",
                "sankey", "network", "force", "wordcloud", "sunburst", "bullet",
                "dendrogram", "depth", "contour"
            )
        ) {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                slicedWidget.series.forEachIndexed { i, s ->
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

        // 3-Card KPI Summary Strip below switchable charts (Screenshot 6)
        if (isSwitchable) {
            val kpiStats = remember(slicedWidget.series, slicedWidget.labels, slicedWidget.unit, activeType) {
                val pts = slicedWidget.series.firstOrNull()?.data.orEmpty()
                val ys = pts.map { it.getOrElse(1) { Float.NaN } }
                if (ys.size >= 2 && ys.all { it.isFinite() }) {
                    val total = ys.sum()
                    val maxIdx = ys.indices.maxByOrNull { ys[it] } ?: 0
                    val peakLabel = slicedWidget.labels.getOrNull(maxIdx)?.takeIf { it.isNotBlank() } ?: "Peak"
                    val peakVal = ys.getOrElse(maxIdx) { 0f }
                    val pct = if (activeType in setOf("pie", "donut")) null else chartGrowthPercentAndroid(ys.first().toDouble(), ys.last().toDouble())
                    Triple(
                        axisLabel(total, slicedWidget.unit),
                        peakLabel to axisLabel(peakVal, slicedWidget.unit),
                        pct
                    )
                } else null
            }
            if (kpiStats != null) {
                Text("Summary: ${slicedWidget.series.firstOrNull()?.name?.ifEmpty { "Series 1" } ?: "Series 1"}", style = MaterialTheme.typography.labelSmall, color = GsTheme.colors.textSecondary)
                val (totalStr, peakPair, pct) = kpiStats
                val pos = pct != null && pct >= 0.0
                val pctStr = pct?.let { String.format(java.util.Locale.US, "%s%.1f%%", if (pos) "+" else "", it) } ?: if (activeType in setOf("pie", "donut")) "N/A (slice values)" else "N/A (zero baseline)"
                val deltaText = if (pct == null) "No defined baseline" else "${if (pos) "↑" else "↓"} $pctStr"
                val pillBg = if (pct == null) Color(0xFFF0EDF5) else if (pos) Color(0xFFE6F9F0) else Color(0xFFFDECEC)
                val pillFg = if (pct == null) Color(0xFF655E70) else if (pos) Color(0xFF129A5A) else Color(0xFFE04F5F)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.82f))
                            .border(1.dp, Color(0xFFE3DCFA), RoundedCornerShape(12.dp))
                            .padding(horizontal = 9.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        NexlyGlassGlyphTile(
                            name = "chart-bar",
                            toneName = "lavender",
                            size = 24.dp,
                            glyphSize = 14.dp
                        )
                        Text(totalStr, fontSize = 14.sp, color = GsTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
                        Text("Total", fontSize = 10.sp, color = GsTheme.colors.textTertiary, fontWeight = FontWeight.Medium)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(pillBg)
                                .padding(horizontal = 6.dp, vertical = 1.5.dp)
                        ) {
                            Text(
                                text = deltaText,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = pillFg
                            )
                        }
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.82f))
                            .border(1.dp, Color(0xFFE3DCFA), RoundedCornerShape(12.dp))
                            .padding(horizontal = 9.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        NexlyGlassGlyphTile(
                            name = "trophy",
                            toneName = "amber",
                            size = 24.dp,
                            glyphSize = 14.dp
                        )
                        Text(peakPair.second, fontSize = 14.sp, color = GsTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
                        Text("Peak (${peakPair.first})", fontSize = 10.sp, color = GsTheme.colors.textTertiary, fontWeight = FontWeight.Medium, maxLines = 1)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFFEFEAFF))
                                .padding(horizontal = 6.dp, vertical = 1.5.dp)
                        ) {
                            NexlyGlyph(
                                name = "star",
                                size = 10.dp,
                                color = Color(0xFF5B3DF5)
                            )
                            Text("Highest", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5B3DF5))
                        }
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.82f))
                            .border(1.dp, Color(0xFFE3DCFA), RoundedCornerShape(12.dp))
                            .padding(horizontal = 9.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        NexlyGlassGlyphTile(
                            name = "trending-up",
                            toneName = "green",
                            size = 24.dp,
                            glyphSize = 14.dp
                        )
                        Text(
                            text = pctStr,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = GsTheme.colors.textPrimary
                        )
                        Text("Growth Rate", fontSize = 10.sp, color = GsTheme.colors.textTertiary, fontWeight = FontWeight.Medium, maxLines = 1)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(pillBg)
                                .padding(horizontal = 6.dp, vertical = 1.5.dp)
                        ) {
                            Text(
                                text = deltaText,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = pillFg
                            )
                        }
                    }
                }
                // Key Insights Box (Screenshot 6)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF2ECFF))
                        .border(1.dp, Color(0xFFD9CCFF), RoundedCornerShape(12.dp))
                        .padding(horizontal = 11.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    NexlyGlassGlyphTile(
                        name = "bulb",
                        toneName = "amber",
                        size = 24.dp,
                        glyphSize = 14.dp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Key Insights:",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF211845)
                        )
                        Text(
                            text = "Peak in ${peakPair.first} (${peakPair.second}) · $pctStr overall change across ${rangeLabel.lowercase()}",
                            fontSize = 11.sp,
                            color = Color(0xFF39325C)
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
        // Snap the ceiling to a round step, otherwise the four gridlines are
        // labelled 0 / 61 / 122 / 183 and the chart reads as a debug overlay.
        val rawMaxY = ys.maxOrNull() ?: 1f
        val step = niceStep(((rawMaxY - minY) / 3f).takeIf { it > 0f } ?: 1f)
        val maxY = minY + step * 3f
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
    val measurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontSize = 10.5.sp, color = GsTheme.colors.textTertiary)
    val probe = rememberChartProbe()
    val ink = GsTheme.colors.textPrimary
    val xLabels = remember(w.series, w.labels) {
        val count = w.series.maxOfOrNull { it.data.size } ?: 0
        if (w.labels.size >= count && count > 0) w.labels.take(count)
        else List(count) { w.series.firstOrNull()?.data?.getOrNull(it)?.getOrNull(0)?.toInt()?.toString() ?: "" }
    }
    // Auto-select the peak point on initial mount so the floating tooltip + guide line
    // from Screenshot 6 ("Apr · $78.4K · ↑ +12.5%") are visible immediately.
    LaunchedEffect(w.series, xLabels) {
        if (probe.datum == null) {
            val firstSeries = w.series.firstOrNull()
            val pts = firstSeries?.data.orEmpty()
            if (pts.size >= 2) {
                val maxIdx = pts.indices.maxByOrNull { pts[it].getOrElse(1) { 0f } } ?: 0
                val pt = pts[maxIdx]
                probe.select(
                    ChartDatum(
                        label = xLabels.getOrElse(maxIdx) { pt.getOrElse(0) { 0f }.toInt().toString() },
                        value = pt.getOrElse(1) { 0f },
                        series = firstSeries?.name,
                        index = maxIdx
                    )
                )
            }
        }
    }
    val activeDatum = probe.datum
    if (activeDatum != null) {
        val selectedSeries = w.series.firstOrNull { it.name == activeDatum.series } ?: w.series.firstOrNull()
        val pts = selectedSeries?.data.orEmpty()
        val stepPct = chartStepPercentAndroid(pts.map { it.getOrElse(1) { Float.NaN }.toDouble() }, activeDatum.index)
        val stepPos = stepPct != null && stepPct >= 0.0

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.94f))
                    .border(1.dp, Color(0xFFDCD3FA), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = activeDatum.label,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF564E7A)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = axisLabel(activeDatum.value, w.unit),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F1842)
                    )
                    Text(
                        text = stepPct?.let { String.format(java.util.Locale.US, "%s %s%.1f%%", if (stepPos) "↑" else "↓", if (stepPos) "+" else "", it) } ?: "N/A (no prior baseline)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (stepPos) Color(0xFF129A5A) else Color(0xFFE04F5F)
                    )
                }
            }
        }
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .pointerInput(w.series, geom) {
                detectTapGestures { at ->
                    val padL = 34.dp.toPx()
                    val padT = 8.dp.toPx()
                    val cw = (size.width - padL).coerceAtLeast(1f)
                    val ch = (size.height - padT * 2f).coerceAtLeast(1f)
                    val tapX = at.x
                    val tapY = at.y - padT
                    var best: ChartDatum? = null
                    var bestDist = Float.MAX_VALUE
                    w.series.forEach { s ->
                        s.data.forEachIndexed { i, pt ->
                            val px = padL + cw * ((pt.getOrElse(0) { 0f } - minX) / spanX)
                            val py = padT + ch * (1f - ((pt.getOrElse(1) { 0f } - minY) / spanY))
                            val dx = px - tapX
                            val dy = py - tapY
                            val d = dx * dx + dy * dy
                            if (d < bestDist) {
                                bestDist = d
                                best = ChartDatum(
                                    label = xLabels.getOrElse(i) { pt.getOrElse(0) { 0f }.toInt().toString() },
                                    value = pt.getOrElse(1) { 0f },
                                    series = s.name,
                                    index = i
                                )
                            }
                        }
                    }
                    // 44dp is the touch-target radius, not a precision limit:
                    // a tap anywhere near the series should read, not miss.
                    if (best != null && bestDist <= (44.dp.toPx() * 44.dp.toPx())) {
                        probe.select(best!!)
                    } else {
                        probe.clear()
                    }
                }
            }
            .padding(top = 8.dp)
    ) {
        val padL = 34.dp.toPx(); val padT = 8.dp.toPx()
        val cw = (size.width - padL).coerceAtLeast(1f)
        val ch = (size.height - padT).coerceAtLeast(1f)
        val base = padT + ch
        val selected = probe.datum
        repeat(4) { g ->
            val y = padT + ch * g / 3f
            drawLine(gridColor, Offset(padL, y), Offset(size.width, y), strokeWidth = 1f)
            val laid = measurer.measure(axisLabel(minY + spanY * (1f - g / 3f), w.unit), axisStyle)
            drawText(
                laid,
                topLeft = Offset(
                    (padL - laid.size.width - 6.dp.toPx()).coerceAtLeast(0f),
                    y - laid.size.height / 2f
                )
            )
        }
        w.series.forEachIndexed { si, s ->
            val col = colors[si]
            val pts = s.data.map {
                Offset(
                    padL + cw * ((it.getOrElse(0) { 0f } - minX) / spanX),
                    padT + ch * (1f - ((it.getOrElse(1) { 0f } - minY) / spanY))
                )
            }
            if (pts.size < 2) return@forEachIndexed
            val visible = (pts.size * progress).toInt().coerceAtLeast(1)
            val drawn = pts.take(visible)
            val path = Path()
            drawn.forEachIndexed { i, p ->
                if (i == 0) {
                    path.moveTo(p.x, p.y)
                } else {
                    val prev = drawn[i - 1]
                    val midX = (prev.x + p.x) * 0.5f
                    path.cubicTo(midX, prev.y, midX, p.y, p.x, p.y)
                }
            }
            var endX = drawn.last().x
            if (visible < pts.size) {
                val frac = pts.size * progress - visible
                val a = pts[visible - 1]; val b = pts[visible]
                endX = a.x + (b.x - a.x) * frac
                path.lineTo(endX, a.y + (b.y - a.y) * frac)
            }
            val fillPath = Path().apply {
                addPath(path)
                lineTo(endX, base)
                lineTo(pts.first().x, base)
                close()
            }
            drawPath(
                fillPath,
                Brush.verticalGradient(
                    colors = listOf(
                        col.copy(alpha = if (filled) 0.30f else 0.16f),
                        col.copy(alpha = 0.01f)
                    ),
                    startY = padT,
                    endY = base
                )
            )
            drawPath(path, col, style = Stroke(width = 3.dp.toPx()))
            if (progress >= 1f) pts.forEach { drawCircle(col, radius = 3.dp.toPx(), center = it) }
            // The selected datum gets a ring and a guide line, so "which point"
            // is never a guess on a dense series.
            if (selected != null && progress >= 1f &&
                (selected.series.isNullOrBlank() || selected.series == s.name)
            ) {
                val idx = if (selected.index >= 0) selected.index else s.data.indexOfFirst {
                    it.getOrElse(1) { 0f } == selected.value
                }
                if (idx >= 0 && idx < pts.size) {
                    val p = pts[idx]
                    drawLine(
                        col.copy(alpha = 0.35f),
                        Offset(p.x, padT),
                        Offset(p.x, base),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawCircle(ink, radius = 6.5.dp.toPx(), center = p, style = Stroke(2.dp.toPx()))
                    drawCircle(col, radius = 4.dp.toPx(), center = p)
                }
            }
        }
    }
    ChartInspectBar(probe, w.unit, "Tap a point to read it", colors.firstOrNull() ?: GsTheme.colors.accent)
    // X labels in equal cells, so they line up with the points above them.
    if (xLabels.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, start = 34.dp)) {
            xLabels.take(8).forEach { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = GsTheme.colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * The axis ceiling: a "nice" number, not the raw maximum.
 *
 * Gridlines at 0/33/66/100% of 187 read as a debug overlay; gridlines at
 * 0/50/100/150 read as a chart. The step is rounded to 1/2/5×10^n so the labels
 * are always short, and the top line is step×3 so all four lines are round.
 */
private fun niceStep(raw: Float): Float {
    if (raw <= 0f) return 1f
    val exp = kotlin.math.floor(kotlin.math.log10(raw.toDouble())).toInt()
    val base = Math.pow(10.0, exp.toDouble()).toFloat()
    val n = raw / base
    val mult = when {
        n <= 1f -> 1f
        n <= 2f -> 2f
        n <= 5f -> 5f
        else -> 10f
    }
    return mult * base
}

/** 1234 -> "1.2K", 0.5 -> "0.5", 100 -> "100" (+ the widget's unit). */
internal fun axisLabel(value: Float, unit: String): String {
    val n = when {
        value >= 1_000_000f -> String.format(java.util.Locale.US, "%.1fM", value / 1_000_000f)
        value >= 1_000f -> String.format(java.util.Locale.US, "%.1fK", value / 1_000f)
        value == value.toInt().toFloat() -> value.toInt().toString()
        else -> String.format(java.util.Locale.US, "%.1f", value)
    }
    return n + unit
}

@Composable
private fun BarChart(w: ChartWidget) {
    var animate by remember { mutableStateOf(false) }
    LaunchedEffect(w) { animate = true }
    val progress by animateFloatAsState(if (animate) 1f else 0f, tween(700), label = "chart")
    val c = GsTheme.colors
    val colors = w.series.indices.map { seriesColor(it) }
    val gridColor = c.divider
    val probe = rememberChartProbe()
    val labels = remember(w.series, w.labels) {
        val first = w.series.firstOrNull()?.data.orEmpty()
        if (w.labels.size >= first.size) w.labels
        else first.map { it.getOrElse(0) { 0f }.toInt().toString() }
    }
    val maxV = remember(w.series) {
        w.series.flatMap { s -> s.data.map { it.getOrElse(1) { 0f } } }.maxOrNull()?.takeIf { it > 0f } ?: 1f
    }
    val axisMax = remember(maxV) { niceStep(maxV / 3f) * 3f }
    val measurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontSize = 10.5.sp, color = c.textTertiary)
    val valueStyle = TextStyle(fontSize = 11.sp, color = c.textSecondary, fontWeight = FontWeight.SemiBold)
    // Value labels only when they cannot collide: one series, at most eight bars.
    val showValues = w.series.size == 1 && labels.size <= 8

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(190.dp)
            // Tap first, pad second: the gesture node must own the full 190dp
            // box or the 8dp top inset would shift every hit test.
            .pointerInput(w.series, labels) {
                detectTapGestures { at ->
                    val gutter = 34.dp.toPx()
                    if (at.x < gutter) return@detectTapGestures
                    val groups = labels.size.coerceAtLeast(1)
                    val groupW = ((size.width - gutter).coerceAtLeast(1f)) / groups
                    val gi = ((at.x - gutter) / groupW).toInt().coerceIn(0, groups - 1)
                    val seriesCount = w.series.size.coerceAtLeast(1)
                    val barW = (groupW * 0.62f) / seriesCount
                    val local = at.x - (gutter + gi * groupW + groupW * 0.19f)
                    val si = (local / barW).toInt().coerceIn(0, seriesCount - 1)
                    val point = w.series.getOrNull(si)?.data?.getOrNull(gi)
                    if (point != null) {
                        probe.select(
                            ChartDatum(
                                label = labels.getOrElse(gi) { "" },
                                value = point.getOrElse(1) { 0f },
                                series = w.series[si].name
                            )
                        )
                    }
                }
            }
            .padding(top = 8.dp)
    ) {
        val gutter = 34.dp.toPx()
        val top = 16.dp.toPx()
        val plotW = (size.width - gutter).coerceAtLeast(1f)
        val plotH = (size.height - top).coerceAtLeast(1f)
        val groups = labels.size.coerceAtLeast(1)
        val seriesCount = w.series.size.coerceAtLeast(1)
        val selected = probe.datum

        repeat(4) { g ->
            val y = top + plotH * g / 3f
            drawLine(gridColor, Offset(gutter, y), Offset(size.width, y), strokeWidth = 1f)
            val laid = measurer.measure(axisLabel(axisMax * (1f - g / 3f), w.unit), axisStyle)
            drawText(
                laid,
                topLeft = Offset(
                    (gutter - laid.size.width - 6.dp.toPx()).coerceAtLeast(0f),
                    y - laid.size.height / 2f
                )
            )
        }

        val groupW = plotW / groups
        val barW = (groupW * 0.62f) / seriesCount
        val radius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        w.series.forEachIndexed { si, s ->
            s.data.forEachIndexed { gi, point ->
                val v = point.getOrElse(1) { 0f }.coerceAtLeast(0f)
                val h = plotH * (v / axisMax) * progress
                if (h <= 0.5f) return@forEachIndexed
                val x = gutter + gi * groupW + groupW * 0.19f + si * barW
                val width = barW * 0.92f
                val isSelected = selected != null &&
                    selected.label == labels.getOrElse(gi) { "" } &&
                    (selected.series == null || selected.series == w.series[si].name)
                val dim = selected != null && !isSelected
                drawRoundRect(
                    colors[si % colors.size].copy(alpha = if (dim) 0.32f else 1f),
                    topLeft = Offset(x, top + plotH - h),
                    size = Size(width, h),
                    cornerRadius = radius
                )
                if (isSelected) {
                    drawRoundRect(
                        color = c.textPrimary.copy(alpha = 0.55f),
                        topLeft = Offset(x, top + plotH - h),
                        size = Size(width, h),
                        cornerRadius = radius,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
                if (showValues) {
                    val laid = measurer.measure(axisLabel(v, w.unit), valueStyle)
                    drawText(
                        laid,
                        topLeft = Offset(
                            x + (width - laid.size.width) / 2f,
                            (top + plotH - h - laid.size.height - 2.dp.toPx()).coerceAtLeast(0f)
                        )
                    )
                }
            }
        }
    }
    ChartInspectBar(probe, w.unit, "Tap a bar to inspect", colors.firstOrNull() ?: c.accent)
    // Labels sit in equal cells under the plot, so they line up with the bars
    // instead of being spread edge to edge.
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, start = 34.dp)) {
        labels.take(8).forEach { label ->
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = c.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
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
    val categorySeries = w.series.all { it.data.size <= 1 }
    return w.series.flatMapIndexed { seriesIndex, series ->
        series.data.mapIndexed { pointIndex, point ->
            val label = if (categorySeries) {
                series.name.ifEmpty { w.labels.getOrNull(seriesIndex) ?: "Slice ${seriesIndex + 1}" }
            } else {
                val pointLabel = w.labels.getOrNull(pointIndex) ?: "Slice ${pointIndex + 1}"
                if (w.series.size > 1) "${series.name}: $pointLabel" else pointLabel
            }
            label to point.getOrElse(1) { point.lastOrNull() ?: Float.NaN }
        }
    }
}

internal fun pieSourceProblemAndroid(w: ChartWidget): String? {
    val slices = pieSlices(w)
    if (slices.isEmpty()) return "No observations in this range."
    val total = slices.sumOf { it.second.toDouble() }
    return if (slices.any { !it.second.isFinite() || it.second < 0f } ||
        !total.isFinite() || total <= 0.0 || total > Float.MAX_VALUE.toDouble()) {
        "Pie charts require finite nonnegative observations and a positive total. Use Line or Bar for signed data."
    } else null
}

@Composable
private fun PieChart(w: ChartWidget) {
    pieSourceProblemAndroid(w)?.let { problem ->
        Text(problem, style = MaterialTheme.typography.bodySmall, color = GsTheme.colors.textSecondary)
        return
    }

    var animate by remember { mutableStateOf(false) }
    LaunchedEffect(w) { animate = true }
    val progress by animateFloatAsState(if (animate) 1f else 0f, tween(700), label = "chart")
    // pie uses series[*].data[0] as (labelIndex, value); or name + single value
    val slices = remember(w.series, w.labels) { pieSlices(w) }
    // Colours key off SLICES, not series: the common shape is ONE series with
    // one point per slice, so keying off `series.indices` gave every slice the
    // same colour — a five-slice pie that looked like a single-colour disc.
    val colors = slices.indices.map { seriesColor(it) }
    val total = slices.sumOf { it.second.toDouble() }.toFloat().takeIf { it > 0f } ?: 1f
    val probe = rememberChartProbe()
    val selected = probe.datum
    // Hoisted: a Canvas draw lambda is not @Composable, so theme reads must
    // happen in the composition above it.
    val ink = GsTheme.colors.textPrimary

    // Cumulative sweeps, used by BOTH the painter and the hit test so a tap
    // always lands on the slice the reader sees, even at a boundary.
    val sweeps = remember(slices) {
        var running = 0f
        slices.map { (_, v) -> running.let { start -> running += 360f * (v / total); start } }
    }

    Column {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(
                Modifier
                    .size(140.dp)
                    .pointerInput(slices) {
                        detectTapGestures { at ->
                            val r = 70.dp.toPx()
                            val dx = at.x - r
                            val dy = at.y - r
                            if (dx * dx + dy * dy > r * r) {
                                probe.clear()
                                return@detectTapGestures
                            }
                            val deg = ((Math.toDegrees(kotlin.math.atan2(dy, dx).toDouble()) + 90.0) % 360.0 + 360.0) % 360.0
                            var hit = slices.lastIndex
                            for (i in slices.indices) {
                                val start = sweeps[i]
                                val sweep = 360f * (slices[i].second / total)
                                if (deg >= start && deg < start + sweep) {
                                    hit = i
                                    break
                                }
                            }
                            val slice = slices.getOrNull(hit) ?: return@detectTapGestures
                            probe.select(
                                ChartDatum(
                                    label = slice.first.ifEmpty { "Slice ${hit + 1}" },
                                    value = slice.second,
                                    share = slice.second / total,
                                    index = hit
                                )
                            )
                        }
                    }
            ) {
                var start = -90f
                slices.forEachIndexed { i, (_, v) ->
                    val arc = 360f * (v / total) * progress
                    drawArc(colors[i % colors.size], startAngle = start, sweepAngle = arc, useCenter = true)
                    start += arc
                }
                if (selected != null && progress >= 1f) {
                    val i = selected.index
                    if (i in slices.indices) {
                        drawArc(
                            color = ink.copy(alpha = 0.65f),
                            startAngle = -90f + sweeps[i],
                            sweepAngle = 360f * (slices[i].second / total),
                            useCenter = true,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }
            Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                slices.forEachIndexed { i, (name, v) ->
                    val isSelected = selected?.index == i
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button
                            ) {
                                probe.select(
                                    ChartDatum(
                                        label = name.ifEmpty { "Slice ${i + 1}" },
                                        value = v,
                                        share = v / total,
                                        index = i
                                    )
                                )
                            }
                            .padding(horizontal = 4.dp, vertical = 3.dp)
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(colors[i % colors.size]))
                        Text(
                            "${name.ifEmpty { "Slice ${i + 1}" }} — ${((v / total) * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = if (isSelected) GsTheme.colors.textPrimary else GsTheme.colors.textSecondary,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
        ChartInspectBar(probe, w.unit, "Tap a slice to read it", colors.firstOrNull() ?: GsTheme.colors.accent)
    }
}

/** A cell that is a quantity: optional sign/currency, digits, optional unit. */
internal val NUMERIC_CELL = Regex("""^[^\p{L}]{0,3}[-+]?\d[\d.,]*\s*(?:%|[a-zA-Z]{1,3})?$""")

// --- stat cards -------------------------------------------------------------

@Composable
private fun StatWidgetView(w: StatWidget) {
    val c = GsTheme.colors
    val dark = WidgetPalette.onDarkCanvas()
    var selectedIdx by remember { mutableIntStateOf(0) }
    val safeSelected = selectedIdx.coerceIn(0, (w.cards.size - 1).coerceAtLeast(0))
    WidgetCardShell(NexlyIcons.cleanPlainText(w.title).ifEmpty { "Stats" }, "stat") {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            w.cards.forEachIndexed { i, card ->
                val accent = WidgetPalette.legible(WidgetPalette.at(i), dark)
                val isSelected = i == safeSelected
                Column(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(accent.copy(alpha = if (isSelected) (if (dark) 0.32f else 0.22f) else (if (dark) 0.20f else 0.12f)), c.surface)
                            )
                        )
                        .border(if (isSelected) 1.5.dp else 1.dp, accent.copy(alpha = if (isSelected) 0.65f else (if (dark) 0.34f else 0.22f)), RoundedCornerShape(12.dp))
                        .clickable { selectedIdx = i }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .width(130.dp)
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(card.value, style = MaterialTheme.typography.titleLarge, color = accent, fontWeight = FontWeight.Bold)
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
        val activeCard = w.cards.getOrNull(safeSelected)
        if (activeCard != null && w.cards.size > 1) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.raisedSurface)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${activeCard.label}: ${activeCard.value}${if (activeCard.unit.isNotEmpty()) " ${activeCard.unit}" else ""}",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                if (activeCard.delta.isNotEmpty()) {
                    Text(
                        activeCard.delta,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (activeCard.direction == "up") c.success else if (activeCard.direction == "down") c.error else c.textSecondary
                    )
                }
            }
        }
    }
}

// --- table ------------------------------------------------------------------

private fun tableEntityIconBadge(raw: String): Pair<String, String>? {
    val clean = NexlyIcons.cleanPlainText(raw)
    if (clean.isEmpty() || clean.length > 32 || clean.all { !it.isLetter() }) return null
    val lower = clean.lowercase()
    return when {
        lower.startsWith("python") -> "brand-python" to "blue"
        lower == "java" -> "coffee" to "amber"
        lower.startsWith("c++") || lower == "cpp" -> "brand-cpp" to "blue"
        lower == "c" || lower == "c#" -> "code" to "lavender"
        lower.startsWith("javascript") || lower == "js" -> "brand-javascript" to "amber"
        lower.startsWith("typescript") || lower == "ts" -> "brand-typescript" to "blue"
        lower.startsWith("rust") -> "brand-rust" to "peach"
        lower == "go" || lower == "golang" -> "brand-golang" to "mint"
        lower.startsWith("swift") -> "brand-apple" to "lavender"
        lower.startsWith("kotlin") || lower.startsWith("android") -> "brand-android" to "green"
        else -> NexlyIcons.semanticIconFor(raw, "sparkles")
    }
}

internal fun widgetTablePageSizeAndroid(selection: Int, rowCount: Int): Int =
    if (selection == -1) maxOf(1, rowCount) else maxOf(1, selection)

@Composable
private fun TableWidgetView(w: TableWidget) {
    var sortCol by remember { mutableIntStateOf(-1) }
    var asc by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var pageSize by remember(w.rows.size) { mutableIntStateOf(if (w.rows.size in 4..5) 3 else 5) }
    var page by remember { mutableIntStateOf(0) }
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
            val na = parseSortableNumberAndroid(va)
            val nb = parseSortableNumberAndroid(vb)
            val cmp = if (na != null && nb != null) na.compareTo(nb) else va.compareTo(vb)
            if (asc) cmp else -cmp
        }
    }
    // -1 means the entire filtered dataset, not a disguised 25-row cap.
    val effectivePageSize = widgetTablePageSizeAndroid(pageSize, rows.size)
    val totalPages = maxOf(1, (rows.size + effectivePageSize - 1) / effectivePageSize)
    val safePage = page.coerceIn(0, totalPages - 1)
    val visible = remember(rows, safePage, effectivePageSize) {
        rows.drop(safePage * effectivePageSize).take(effectivePageSize)
    }
    val startRow = if (rows.isEmpty()) 0 else safePage * effectivePageSize + 1
    val endRow = (safePage * effectivePageSize + visible.size).coerceAtMost(rows.size)
    val primaryNameCol = remember(w.columns) {
        val idx = w.columns.indexOfFirst {
            it.contains("language", true) || it.contains("product", true) ||
                it.contains("name", true) || it.contains("framework", true) ||
                it.contains("model", true) || it.contains("company", true)
        }
        if (idx >= 0) idx
        else if (w.columns.firstOrNull()?.trim() == "#" || w.columns.firstOrNull()?.contains("rank", true) == true) 1
        else 0
    }
    val csv = remember(w.columns, w.rows) {
        buildString {
            appendLine(w.columns.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" })
            w.rows.forEach { row ->
                appendLine(row.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" })
            }
        }
    }

    WidgetCardShell(
        title = NexlyIcons.cleanPlainText(w.title).ifEmpty { "Table" },
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
                onValueChange = {
                    query = it
                    page = 0
                },
                placeholder = { Text("Filter…", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
        val numericCols = remember(w.columns, w.rows) {
            w.columns.indices.map { ci ->
                val cells = w.rows.mapNotNull { it.getOrNull(ci) }.filter { it.isNotBlank() }
                cells.isNotEmpty() && cells.count { NUMERIC_CELL.matches(it.trim()) } * 2 > cells.size
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.72f))
                .border(1.dp, Color(0xFFE2DBFA), RoundedCornerShape(12.dp))
        ) {
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier
                        .background(Color(0xFFEDE6FF))
                        .padding(horizontal = 10.dp)
                ) {
                    w.columns.forEachIndexed { ci, col ->
                        val colWidth = if (col.trim() == "#" || col.equals("rank", true)) 52.dp else 132.dp
                        Text(
                            if (w.sortable) "$col ${if (sortCol == ci) (if (asc) "▲" else "▼") else ""}" else col,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF221A47),
                            fontWeight = FontWeight.Bold,
                            textAlign = if (numericCols[ci] && ci > 0) TextAlign.End else TextAlign.Start,
                            modifier = Modifier
                                .width(colWidth)
                                .then(if (w.sortable) Modifier.clickable {
                                    if (sortCol == ci) asc = !asc else { sortCol = ci; asc = true }
                                } else Modifier)
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(GsTheme.colors.divider))
                visible.forEach { row ->
                    Row(modifier = Modifier.padding(horizontal = 10.dp)) {
                        w.columns.indices.forEach { ci ->
                            val colName = w.columns[ci]
                            val colWidth = if (colName.trim() == "#" || colName.equals("rank", true)) 52.dp else 132.dp
                            val rawVal = (row.getOrNull(ci) ?: "").trim()
                            val isTrend = Regex("^[↑↓↗↘+-]?\\s*[+-]?\\d+(?:\\.\\d+)?%$").matches(rawVal) &&
                                (rawVal.startsWith("+") || rawVal.startsWith("-") || rawVal.startsWith("↑") || rawVal.startsWith("↓") || colName.contains("trend", true) || colName.contains("growth", true) || colName.contains("change", true))
                            val isNeg = isTrend && (rawVal.contains("-") || rawVal.startsWith("↓") || rawVal.startsWith("↘"))

                            if (ci == primaryNameCol) {
                                val badge = tableEntityIconBadge(rawVal)
                                val displayLabel = NexlyIcons.cleanPlainText(rawVal)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .width(colWidth)
                                        .padding(vertical = 7.dp, horizontal = 4.dp)
                                ) {
                                    if (badge != null) {
                                        NexlyGlassGlyphTile(
                                            name = badge.first,
                                            toneName = badge.second,
                                            size = 21.dp,
                                            glyphSize = 12.dp
                                        )
                                        Spacer(Modifier.width(7.dp))
                                    }
                                    Text(
                                        text = displayLabel,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = GsTheme.colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            } else {
                                val displayStr = if (isTrend) {
                                    val cleanNum = rawVal.replace(Regex("^[↑↓↗↘\\s]+"), "")
                                    "${if (isNeg) "↓" else "↑"} ${if (cleanNum.startsWith("+") || cleanNum.startsWith("-")) cleanNum else "${if (isNeg) "-" else "+"}$cleanNum"}"
                                } else rawVal
                                Text(
                                    displayStr,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = if (isTrend || ci == 0) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = when {
                                        isTrend && isNeg -> Color(0xFFE04F5F)
                                        isTrend -> Color(0xFF129A5A)
                                        else -> GsTheme.colors.textPrimary
                                    },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = if (numericCols[ci] && ci > 0) TextAlign.End else TextAlign.Start,
                                    modifier = Modifier
                                        .width(colWidth)
                                        .padding(vertical = 7.dp, horizontal = 4.dp)
                                )
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(GsTheme.colors.divider.copy(alpha = 0.35f)))
                }
            }
            if (rows.isEmpty()) {
                Text(
                    "No matching rows",
                    style = MaterialTheme.typography.labelMedium,
                    color = GsTheme.colors.textTertiary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
            // Pagination & Display Controls Bar (Screenshot 5)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF6F2FF))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "‹",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (safePage > 0) GsTheme.colors.textSecondary else GsTheme.colors.textTertiary.copy(alpha = 0.45f),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(enabled = safePage > 0) { page = (safePage - 1).coerceAtLeast(0) }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    (0 until totalPages).forEach { p ->
                        val active = p == safePage
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(if (active) Color(0xFF6C4CF6) else Color.Transparent)
                                .clickable { page = p },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (p + 1).toString(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (active) Color.White else GsTheme.colors.textSecondary
                            )
                        }
                    }
                    Text(
                        text = "›",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (safePage < totalPages - 1) GsTheme.colors.textSecondary else GsTheme.colors.textTertiary.copy(alpha = 0.45f),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(enabled = safePage < totalPages - 1) { page = (safePage + 1).coerceAtMost(totalPages - 1) }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Text(
                        text = "$startRow–$endRow of ${rows.size}",
                        fontSize = 10.5.sp,
                        color = Color(0xFF6A6190),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color.White.copy(alpha = 0.85f))
                        .border(1.dp, Color(0xFFDFD7FA), RoundedCornerShape(7.dp))
                        .clickable {
                            pageSize = when (pageSize) {
                                3 -> 5
                                5 -> 10
                                10 -> -1
                                else -> 3
                            }
                            page = 0
                        }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Show: ${if (pageSize == -1) "All" else pageSize.toString()} ▾",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF3E3666)
                    )
                }
            }
        }
        if (rows.size >= 2) {
            val topName = rows.firstOrNull()?.getOrNull(primaryNameCol).orEmpty()
            val topMetric = rows.firstOrNull()?.drop(primaryNameCol + 1)?.firstOrNull().orEmpty()
            val secondName = rows.getOrNull(1)?.getOrNull(primaryNameCol).orEmpty()
            val secondMetric = rows.getOrNull(1)?.drop(primaryNameCol + 1)?.firstOrNull().orEmpty()
            if (topName.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF2ECFF))
                        .border(1.dp, Color(0xFFD9CCFF), RoundedCornerShape(12.dp))
                        .padding(horizontal = 11.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    NexlyGlassGlyphTile(
                        name = "bulb",
                        toneName = "amber",
                        size = 24.dp,
                        glyphSize = 14.dp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Key Insights",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF211845)
                        )
                        Text(
                            text = "• $topName leads${if (topMetric.isNotBlank()) " ($topMetric)" else ""}${if (secondName.isNotBlank()) " followed by $secondName${if (secondMetric.isNotBlank()) " ($secondMetric)" else ""}" else ""} across ${rows.size} entries",
                            fontSize = 11.sp,
                            color = Color(0xFF39325C)
                        )
                    }
                }
            }
        }
    }
}

// --- compare ----------------------------------------------------------------

@Composable
private fun CompareWidgetView(w: CompareWidget, onSendPrompt: ((String) -> Unit)? = null) {
    val c = GsTheme.colors
    val dark = WidgetPalette.onDarkCanvas()
    var pickedSide by remember { mutableIntStateOf(-1) }
    val hues = listOf(
        WidgetPalette.legible(WidgetPalette.at(1), dark),
        WidgetPalette.legible(WidgetPalette.at(3), dark)
    )
    WidgetCardShell(NexlyIcons.cleanPlainText(w.title).ifEmpty { "Compare" }, "compare") {
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(w.left, w.right).forEachIndexed { i, side ->
                    val accent = hues[i]
                    val isPicked = pickedSide == i
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(accent.copy(alpha = if (isPicked) (if (dark) 0.24f else 0.15f) else (if (dark) 0.14f else 0.08f)))
                            .border(if (isPicked) 1.5.dp else 1.dp, accent.copy(alpha = if (isPicked) 0.7f else (if (dark) 0.28f else 0.18f)), RoundedCornerShape(12.dp))
                            .clickable { pickedSide = if (isPicked) -1 else i }
                            .padding(12.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(side.title, style = MaterialTheme.typography.labelLarge, color = accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            if (isPicked) {
                                NexlyGlyph(name = "circle-check", size = 14.dp, color = accent)
                            }
                        }
                        side.items.forEach { item ->
                            Row(Modifier.padding(top = 6.dp)) {
                                Text("•", color = accent, modifier = Modifier.padding(end = 6.dp))
                                Text(item, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                            }
                        }
                    }
                }
            }
            if (pickedSide in 0..1) {
                val chosen = if (pickedSide == 0) w.left else w.right
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.raisedSurface)
                        .clickable { onSendPrompt?.invoke("Tell me more about ${chosen.title} and how to get started.") }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Selected: ${chosen.title}", style = MaterialTheme.typography.labelMedium, color = c.textPrimary, fontWeight = FontWeight.SemiBold)
                    Text("Explore →", style = MaterialTheme.typography.labelSmall, color = hues[pickedSide], fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// --- progress ---------------------------------------------------------------

@Composable
private fun ProgressWidgetView(w: ProgressWidget) {
    val c = GsTheme.colors
    var offsets by remember(w.items) { mutableStateOf(List(w.items.size) { 0f }) }
    WidgetCardShell(NexlyIcons.cleanPlainText(w.title).ifEmpty { "Progress" }, "progress") {
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            w.items.forEachIndexed { i, item ->
                val adjusted = (item.percent + (offsets.getOrElse(i) { 0f })).coerceIn(0f, 100f)
                var animate by remember(item.label, adjusted) { mutableStateOf(false) }
                LaunchedEffect(item.label, adjusted) { animate = true }
                val p by animateFloatAsState(if (animate) adjusted / 100f else 0f, tween(450), label = "prog$i")
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            val next = if (adjusted >= 100f) 0f else (adjusted + 10f).coerceAtMost(100f)
                            offsets = offsets.toMutableList().also { it[i] = next - item.percent }
                        }
                        .padding(vertical = 2.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(item.label, style = MaterialTheme.typography.labelMedium, color = c.textPrimary)
                        Text("${adjusted.toInt()}%", style = MaterialTheme.typography.labelMedium, color = c.textSecondary, fontFamily = FontFamily.Monospace)
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 5.dp)
                            .height(7.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(c.raisedSurface)
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
    val c = GsTheme.colors
    var selectedIdx by remember { mutableIntStateOf(0) }
    val safeSelected = selectedIdx.coerceIn(0, (w.entries.size - 1).coerceAtLeast(0))
    WidgetCardShell(NexlyIcons.cleanPlainText(w.title).ifEmpty { "Timeline" }, "timeline") {
        Column(Modifier.padding(top = 8.dp)) {
            w.entries.forEachIndexed { i, e ->
                val isSelected = i == safeSelected
                val dotColor = seriesColor(i)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) dotColor.copy(alpha = 0.08f) else Color.Transparent)
                        .clickable { selectedIdx = i }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(if (isSelected) 11.dp else 9.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        if (i < w.entries.lastIndex) {
                            Box(Modifier.width(2.dp).height(34.dp).background(c.divider))
                        }
                    }
                    Column(Modifier.padding(start = 10.dp, bottom = 10.dp)) {
                        Text(e.date, style = MaterialTheme.typography.labelSmall, color = if (isSelected) dotColor else c.textTertiary, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        Text(e.title, style = MaterialTheme.typography.bodySmall, color = c.textPrimary, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
                        if (e.detail.isNotEmpty()) {
                            Text(e.detail, style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                        }
                    }
                }
            }
        }
    }
}
