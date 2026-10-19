package com.grapsee.gsai.ui.chat.content

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.theme.GsTheme
import com.grapsee.gsai.ui.theme.skeletonSurface
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
    "doc", "document", "pdf"
)

/** Chart sub-types the `chart` kind renders (see WidgetsExtra.kt / WidgetsCharts2.kt). */
internal val CHART_TYPES = setOf(
    "line", "area", "stackedArea", "bar", "hbar", "stacked", "pie", "donut",
    "scatter", "bubble", "histogram", "funnel", "radar",
    "grouped", "boxplot", "heatmap", "correlation", "treemap", "waterfall",
    "candlestick", "gantt", "chord"
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
                numbers.size >= 2 -> listOf(numbers[0], numbers[1])
                numbers.size == 1 -> listOf(index.toFloat(), numbers[0])
                else -> null
            }
        }
        is JsonObject -> {
            val y = (item["y"] as? JsonPrimitive)?.floatOrNull
                ?: (item["value"] as? JsonPrimitive)?.floatOrNull
            if (y == null) null
            else listOf((item["x"] as? JsonPrimitive)?.floatOrNull ?: index.toFloat(), y)
        }
        else -> null
    }
}

@Serializable
data class ChartSeries(
    val name: String = "",
    @Serializable(with = PointListSerializer::class)
    val data: List<List<Float>> = emptyList()
)

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
fun inferWidgetKind(json: String): String? {
    val obj = try {
        widgetJson.parseToJsonElement(json) as? JsonObject
    } catch (_: Throwable) {
        null
    } ?: return null
    val keys = obj.keys
    val candidates = buildList {
        if ("series" in keys) add("chart")
        if ("columns" in keys && "rows" in keys) add("table")
        if ("cards" in keys) add("stat")
        if ("entries" in keys) add("timeline")
        if ("questions" in keys) add("quiz")
        if ("fields" in keys) add("form")
        if ("prompts" in keys) add("followups")
        if ("buttons" in keys) add("actions")
        if ("items" in keys) {
            add("progress"); add("gauge"); add("carousel"); add("app"); add("media")
        }
        if ("steps" in keys || "groups" in keys) add("app")
    }
    return candidates.firstOrNull { parseWidget(it, json) != null }
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

/** Series colour, index-stable and genuinely varied (12 hues, not 5). */
@Composable
internal fun seriesColor(i: Int): Color =
    WidgetPalette.legible(WidgetPalette.at(i), WidgetPalette.onDarkCanvas())

/**
 * Surface treatment. Four tones instead of one flat card, so a chart, a KPI
 * row, a progress list and a table never read as the same object.
 */
internal enum class CardTone {
    /** Solid elevated surface — dense content (tables, cards, media). */
    Plain,

    /** Hue wash from the top + hairline — data visualisations. */
    Tinted,

    /** Stronger hue wash — hero numbers (stat, gauge). */
    Hero,

    /** No fill, hairline border — lists and quiet controls. */
    Outline
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
    "calc" to 9, "app" to 4
)

internal fun widgetTone(tag: String): CardTone = when (tag.lowercase()) {
    "chart", "app", "calc", "form", "quiz", "carousel", "cards", "media" -> CardTone.Tinted
    "stat", "gauge" -> CardTone.Hero
    "progress", "timeline", "followups", "actions", "voice", "panel", "compare" -> CardTone.Outline
    else -> CardTone.Plain
}

/**
 * The one card surface. [hue] and [tone] default from [tag], so a view only
 * passes them when it wants a specific identity (charts key off their type,
 * compare keys off its two sides).
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
    val c = GsTheme.colors
    val dark = WidgetPalette.onDarkCanvas()
    val accent = WidgetPalette.legible(hue, dark)
    val base = c.elevatedSurface
    val clear = Color.Transparent
    val fill: Brush = when (tone) {
        CardTone.Plain -> Brush.verticalGradient(listOf(base, base))
        CardTone.Outline -> Brush.verticalGradient(listOf(clear, clear))
        CardTone.Tinted -> Brush.verticalGradient(
            listOf(accent.copy(alpha = if (dark) 0.16f else 0.10f), base, base)
        )
        CardTone.Hero -> Brush.verticalGradient(
            listOf(accent.copy(alpha = if (dark) 0.26f else 0.18f), base, base)
        )
    }
    val edge = when (tone) {
        CardTone.Outline -> c.divider
        CardTone.Plain -> c.border.copy(alpha = 0.55f)
        else -> accent.copy(alpha = if (dark) 0.32f else 0.22f)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(widgetShape)
            .background(fill)
            .border(1.dp, edge, widgetShape)
    ) {
        // Two-pixel hue hairline: the cheapest way to give every family a face
        // without touching a single chart renderer.
        if (tone == CardTone.Tinted || tone == CardTone.Hero) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.10f))))
            )
        }
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
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
                    color = accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(accent.copy(alpha = if (dark) 0.20f else 0.12f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            content()
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
    "treemap" to 10, "candlestick" to 11
)

@Composable
private fun ChartWidgetView(w: ChartWidget) {
    WidgetCardShell(
        w.title.ifEmpty { "Chart" },
        "chart",
        hue = WidgetPalette.at(CHART_HUE_SLOTS[w.type] ?: 0),
        tone = CardTone.Tinted
    ) {
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
    // pie uses series[*].data[0] as (labelIndex, value); or name + single value
    val slices = remember(w.series, w.labels) { pieSlices(w) }
    // Colours key off SLICES, not series: the common shape is ONE series with
    // one point per slice, so keying off `series.indices` gave every slice the
    // same colour — a five-slice pie that looked like a single-colour disc.
    val colors = slices.indices.map { seriesColor(it) }
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
    val c = GsTheme.colors
    val dark = WidgetPalette.onDarkCanvas()
    WidgetCardShell(w.title.ifEmpty { "Stats" }, "stat") {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            w.cards.forEachIndexed { i, card ->
                // One hue per card: a KPI row should look like a row of
                // different numbers, not four identical grey boxes.
                val accent = WidgetPalette.legible(WidgetPalette.at(i), dark)
                Column(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(accent.copy(alpha = if (dark) 0.22f else 0.14f), c.surface)
                            )
                        )
                        .border(1.dp, accent.copy(alpha = if (dark) 0.34f else 0.22f), RoundedCornerShape(12.dp))
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
    val c = GsTheme.colors
    val dark = WidgetPalette.onDarkCanvas()
    // Two opposing hues — the whole point of a comparison is that the sides are
    // distinguishable at a glance, not that they share a card colour.
    val hues = listOf(
        WidgetPalette.legible(WidgetPalette.at(1), dark),
        WidgetPalette.legible(WidgetPalette.at(3), dark)
    )
    WidgetCardShell(w.title.ifEmpty { "Compare" }, "compare") {
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(w.left, w.right).forEachIndexed { i, side ->
                val accent = hues[i]
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = if (dark) 0.14f else 0.08f))
                        .border(1.dp, accent.copy(alpha = if (dark) 0.28f else 0.18f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(side.title, style = MaterialTheme.typography.labelLarge, color = accent, fontWeight = FontWeight.SemiBold)
                    side.items.forEach { item ->
                        Row(Modifier.padding(top = 6.dp)) {
                            Text("•", color = accent, modifier = Modifier.padding(end = 6.dp))
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
