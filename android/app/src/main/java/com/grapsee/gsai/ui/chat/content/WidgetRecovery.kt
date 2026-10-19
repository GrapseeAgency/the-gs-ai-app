package com.grapsee.gsai.ui.chat.content

import kotlinx.serialization.encodeToString

/**
 * Client-side widget recovery.
 *
 * WHY THIS EXISTS
 * ---------------
 * Widget rendering must not depend on the model remembering a prompt. A flash
 * or text-only model routinely answers "show me a bar chart" with prose:
 * *"I'm unable to create or display visualizations like bar charts."* The
 * client can render the whole palette perfectly well — what it lacked was data.
 *
 * This layer reads what the answer ALREADY stated (or the turn before it) and
 * builds the widget locally, so the user gets the visual even when the model
 * refuses. It is the difference between "our app can render" and "our app
 * renders", regardless of which model is behind the API.
 *
 * SCOPE — this is a fallback, not the product. When the model emits a widget
 * fence, nothing here runs. The full 150-item palette is still driven by the
 * model; recovery only covers shapes that can be derived from prose WITHOUT
 * inventing structure: charts, tables, stat cards, progress, timelines and
 * image galleries. Forms, quizzes, maps, kanban boards and custom HTML need a
 * schema the text does not contain, so they are never fabricated here.
 *
 * HONESTY RULES (non-negotiable)
 * ------------------------------
 *  - never invent a number for a data visual: every value is parsed from text
 *    already shown. The one exception is the explicit SAMPLE chart drawn when
 *    the user asked for a chart and the conversation holds no data at all —
 *    its title says SAMPLE and its series is called "Sample";
 *  - never hide that this was reconstructed — titles say "from the answer
 *    above";
 *  - bail out silently when there is not enough data, rather than drawing an
 *    empty widget;
 *  - the only prose ever removed is a *false* capability claim ("I can't
 *    display charts"), and only when a widget was successfully recovered.
 */
internal sealed interface VisualAsk {
    data class Chart(val type: String) : VisualAsk
    object Table : VisualAsk
    object Stat : VisualAsk
    object Progress : VisualAsk
    object Timeline : VisualAsk
    object Gallery : VisualAsk
}

internal object WidgetRecovery {

    private const val MAX_POINTS = 8
    private const val MAX_GALLERY = 12

    /**
     * Never a real document offset: recovered blocks are appended after the
     * parsed ones, and RichBlocks keys siblings by sourceStart, so 0 would
     * collide with the first paragraph and duplicate a composition key.
     */
    private const val RECOVERED_OFFSET = Int.MAX_VALUE

    /** One extracted value. [display] keeps the human-facing unit ("2.5 trillion"). */
    private data class Point(
        val label: String,
        val value: Float,
        val display: String,
        val percent: Boolean = false
    )

    private data class Raw(
        val section: String,
        val label: String,
        val value: Float,
        val display: String,
        val percent: Boolean
    )

    // Order matters: the most specific phrase wins ("horizontal bar" before
    // "bar chart" before the bare "chart" fallback).
    private val chartTypes = listOf(
        "horizontal bar" to "hbar",
        "stacked bar" to "stacked",
        "stacked area" to "stackedArea",
        "bar chart" to "bar",
        "bar graph" to "bar",
        "column chart" to "bar",
        "line chart" to "line",
        "line graph" to "line",
        "area chart" to "area",
        "pie chart" to "pie",
        "doughnut" to "donut",
        "donut" to "donut",
        "radar" to "radar",
        "funnel" to "funnel",
        "waterfall" to "waterfall",
        "treemap" to "treemap",
        "histogram" to "bar",
        "chart" to "bar",
        "graph" to "bar",
        "plot" to "bar",
        "visuali" to "bar"
    )

    private val headingLine = Regex("""^#{1,6}\s+(.+)$""")
    private val numberedHeading = Regex("""^\d{1,2}[.)]\s+(.+)$""")
    private val bulletPrefix = Regex("""^\s*(?:[-*+•·]|\d{1,2}[.)])\s+""")
    private val markdownNoise = Regex("""[*_`]{1,3}""")
    private val separator = Regex("""\s*[:=]\s*|\s+[—–]\s+""")

    /**
     * `2`, `2.5`, `2,300`, `~$2-3 trillion`, `45%`, `120k`.
     * Group 1 = low, 2 = optional high (a range), 3 = unit.
     */
    private val numberAtStart = Regex(
        """^~?\s*(?:about\s+|approx\.?\s+|around\s+|over\s+|under\s+|more than\s+|less than\s+)?""" +
            """(?:[A-Z]{3}\s*)?[$€£¥₹]?\s*(-?\d{1,3}(?:,\d{3})+(?:\.\d+)?|-?\d+(?:\.\d+)?)""" +
            """(?:\s*[-–]\s*(\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d+(?:\.\d+)?))?""" +
            """\s*(%|percent|trillion|billion|million|thousand|tn|bn|mn|k|m|b|t)?""",
        RegexOption.IGNORE_CASE
    )

    /** Dates a timeline can anchor on: 2024, 2024-03, 15 Jan 2024, Jan 2024, Q1 2024. */
    private val dateish = Regex(
        """^(?:(?:19|20)\d{2}(?:[-/]\d{1,2}){0,2}""" +
            """|(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\.?\s+\d{1,2},?\s+(?:19|20)\d{2}""" +
            """|(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\.?\s+(?:19|20)\d{2}""" +
            """|\d{1,2}\s+(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\.?\s+(?:19|20)\d{2}""" +
            """|q[1-4]\s+(?:19|20)\d{2})$""",
        RegexOption.IGNORE_CASE
    )

    private val imageUrl = Regex(
        """https://[^\s)\]"'<>]+?\.(?:png|jpe?g|webp|gif)(?:\?[^\s)\]"'<>]*)?""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Built from the ONE canonical language set (ContentBlocks.WIDGET_FENCE_LANGUAGES)
     * so recovery can never fire on a transcript that already contains a widget —
     * including chart sub-type fences like ```funnel, which used to slip past
     * this guard and get a fake sample chart stacked on top of a real payload.
     */
    private val widgetFence = Regex(
        """(?m)^\s*```(?:""" +
            WIDGET_FENCE_LANGUAGES.sortedByDescending { it.length }.joinToString("|") +
            """)\b"""
    )

    /** "create", "create or display", "generate, render and show" — all one verb phrase. */
    private val renderVerbs =
        "(?:create|generate|display|show|produce|render|draw)" +
            "(?:\\s*(?:,|or|and)\\s*(?:create|generate|display|show|produce|render|draw))*"

    private val falseCapabilityClaims = listOf(
        Regex(
            """(?i)\bI(?:'m| am) (?:just )?an? (?:large )?(?:text-based|language) (?:AI|model)[^.!?]*[.!?]"""
        ),
        Regex("""(?i)\bAs an? (?:text-based|language) (?:AI|model)[^.!?]*[.!?]"""),
        Regex(
            """(?i)\bI (?:can't|cannot|can not|don't|do not) (?:have the ability to )?""" +
                renderVerbs + """ (?:any )?(?:visual |interactive )?""" +
                """(?:charts?|graphs?|visuali[sz]ations?|tables?)[^.!?]*[.!?]"""
        ),
        Regex(
            """(?i)\bI(?:'m| am) unable to """ + renderVerbs + """ (?:any )?""" +
                """(?:visual |interactive )?(?:charts?|graphs?|visuali[sz]ations?)[^.!?]*[.!?]"""
        )
    )

    /** True when the text already carries a real widget fence — recovery must not double up. */
    fun hasWidgetFence(text: String): Boolean = widgetFence.containsMatchIn(text)

    /** What visual did the user ask for, if any. */
    fun askOf(prompt: String): VisualAsk? {
        val p = prompt.lowercase()
        // A diagram request is not a data-chart request: never hijack it.
        if (p.contains("flowchart") || p.contains("flow chart") || p.contains("diagram") ||
            p.contains("mindmap") || p.contains("org chart")
        ) {
            return null
        }
        chartTypes.firstOrNull { p.contains(it.first) }?.let { return VisualAsk.Chart(it.second) }
        if (p.contains("table") || p.contains("spreadsheet")) return VisualAsk.Table
        if (p.contains("timeline") || p.contains("roadmap") || p.contains("milestone") ||
            p.contains("history of") || p.contains("chronolog")
        ) {
            return VisualAsk.Timeline
        }
        if (p.contains("progress") || p.contains("completion") || p.contains("percent complete")) {
            return VisualAsk.Progress
        }
        if (p.contains("gallery") || p.contains("photos") || p.contains("images") ||
            p.contains("pictures")
        ) {
            return VisualAsk.Gallery
        }
        if (p.contains("stat") || p.contains("kpi") || p.contains("metric") ||
            p.contains("key numbers") || p.contains("at a glance")
        ) {
            return VisualAsk.Stat
        }
        return null
    }

    /**
     * Builds a widget from text the answer already contains, or returns null.
     * [reply] is tried first; [prior] (the previous assistant turn) is the
     * fallback for the very common "show me a chart" follow-up with no data.
     */
    fun recover(reply: String, prompt: String, prior: String): Block.WidgetBlock? {
        // Defence in depth: never stack a synthesised widget on a transcript
        // that already carries one. The caller checks this too, but relying on
        // the caller is how a good ```funnel payload ended up with a fake
        // "Item 1…4" sample drawn underneath it.
        if (hasWidgetFence(reply)) return null
        val ask = askOf(prompt) ?: return null
        return when (ask) {
            VisualAsk.Gallery -> galleryBlock(reply, prior)
            VisualAsk.Timeline -> timelineBlock(reply, prior)
            else -> {
                val fromReply = extract(reply)
                val chosen = if (fromReply.points.size >= 2) fromReply else extract(prior)
                if (chosen.points.size < 2) {
                    // No data anywhere. Asking for a chart and getting "I can't
                    // draw" is the exact failure this layer exists for, so show
                    // a SAMPLE — unmistakably labelled, never passed off as the
                    // user's data. Other kinds stay silent: a sample form or map
                    // would be noise, not a demonstration.
                    return if (ask is VisualAsk.Chart) sampleChart(ask.type) else null
                }
                when (ask) {
                    is VisualAsk.Chart -> chartBlock(chosen.points, ask.type, chosen.seriesName)
                    VisualAsk.Table -> tableBlock(chosen.points)
                    VisualAsk.Stat -> statBlock(chosen.points, chosen.seriesName)
                    VisualAsk.Progress -> progressBlock(chosen.points, chosen.seriesName)
                    else -> null
                }
            }
        }
    }

    /**
     * Removes sentences that claim the app cannot render — a claim that is
     * simply false here. Only called after a widget was recovered, so a
     * legitimate "I don't have that data" is never touched.
     */
    fun stripFalseRefusal(text: String): String {
        var out = text.replace(
            Regex("""(?i)^\s*I apologi[sz]e,?\s*(?:but\s*)?"""),
            ""
        )
        for (pattern in falseCapabilityClaims) out = pattern.replace(out, "")
        // Tidy the seams the removals left behind (" ,", "..", ragged spaces).
        out = out.replace(Regex("""[ \t]{2,}"""), " ")
        out = out.replace(Regex("""\s+([.,;:])"""), "$1")
        out = out.replace(Regex("""([.,])\s*[.,]"""), "$1")
        out = out.replace(Regex("""\n{3,}"""), "\n\n")
        out = out.replace(Regex("""(?i)^\s*(?:however|but)[, ]+"""), "")
        return out.trim()
    }

    // -----------------------------------------------------------------------
    // Extraction
    // -----------------------------------------------------------------------

    private data class Extracted(val points: List<Point>, val seriesName: String)

    private fun extract(text: String): Extracted {
        val rows = collect(text)
        if (rows.isEmpty()) return Extracted(emptyList(), "Value")

        // A repeated metric label ("Market Cap") under distinct section
        // headings ("Telecommunications", "Semiconductors") means the SECTIONS
        // are the categories and the metric is the series. Otherwise the labels
        // themselves are the categories.
        val counts = rows.groupingBy { it.label.lowercase() }.eachCount()
        val dominant = counts.entries.maxByOrNull { it.value }
        val useSection = dominant != null && dominant.value >= 2 &&
            dominant.value * 2 >= rows.size &&
            rows.count { it.label.equals(dominant.key, ignoreCase = true) && it.section.isNotBlank() } >= 2

        val points = if (useSection) {
            rows.filter {
                it.label.equals(dominant!!.key, ignoreCase = true) && it.section.isNotBlank()
            }.map { Point(it.section, it.value, it.display, it.percent) }
        } else {
            rows.map { Point(it.label, it.value, it.display, it.percent) }
        }

        val seen = HashSet<String>()
        val unique = points.filter { seen.add(it.label.lowercase()) }.take(MAX_POINTS)
        val name = if (useSection) dominant!!.key.replaceFirstChar { it.uppercase() } else "Value"
        return Extracted(unique, name)
    }

    private fun collect(text: String): List<Raw> {
        if (text.isBlank()) return emptyList()
        val rows = ArrayList<Raw>()
        var section = ""
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("```")) continue
            val heading = headingOf(line)
            if (heading != null) {
                section = heading
                continue
            }
            parseLine(line, section)?.let { rows += it }
            if (rows.size >= 40) break
        }
        return rows
    }

    private fun headingOf(line: String): String? {
        headingLine.matchEntire(line)?.let { return cleanLabel(it.groupValues[1]) }
        val m = numberedHeading.matchEntire(line) ?: return null
        val title = cleanLabel(m.groupValues[1])
        // "5. Telecommunications" is a heading; "1. Revenue was $10M" is data.
        if (title.length in 2..60 && hasLetter(title) && title.none { it.isDigit() } &&
            separator.find(title) == null
        ) {
            return title
        }
        return null
    }

    private fun parseLine(line: String, section: String): Raw? {
        // Markdown table row: first cell = label, first numeric cell = value.
        if (line.startsWith("|")) {
            val cells = line.trim('|').split('|').map { it.trim() }
            if (cells.size < 2) return null
            if (cells.all { it.isEmpty() || it.all { c -> c == '-' || c == ':' || c == ' ' } }) return null
            val label = cleanLabel(cells[0])
            if (!hasLetter(label) || label.length < 2) return null
            for (cell in cells.drop(1)) {
                val v = parseNumber(cell) ?: continue
                return Raw(section, label, v.first, v.second, v.third)
            }
            return null
        }

        val body = cleanLabel(line.replace(bulletPrefix, ""))
        val m = separator.find(body) ?: return null
        val label = body.substring(0, m.range.first).trim()
        if (label.length !in 2..60 || !hasLetter(label)) return null
        if (label.endsWith("?") || label.endsWith("!") || label.contains("http")) return null
        val rest = body.substring(m.range.last + 1).trim()
        val v = parseNumber(rest) ?: return null
        return Raw(section, label, v.first, v.second, v.third)
    }

    /** Returns (normalised value, human display, isPercent) or null. */
    private fun parseNumber(text: String): Triple<Float, String, Boolean>? {
        val m = numberAtStart.find(text.trim()) ?: return null
        val low = m.groupValues[1].replace(",", "").toFloatOrNull() ?: return null
        val high = m.groupValues[2].replace(",", "").toFloatOrNull()
        val raw = if (high != null) (low + high) / 2f else low
        val unit = m.groupValues[3].lowercase()

        val factor = when (unit) {
            "trillion", "tn", "t" -> 1e12f
            "billion", "bn", "b" -> 1e9f
            "million", "mn", "m" -> 1e6f
            "thousand", "k" -> 1e3f
            else -> 1f
        }
        val value = raw * factor
        if (!value.isFinite()) return null
        return Triple(value, display(raw, unit), unit == "%" || unit == "percent")
    }

    private fun display(raw: Float, unit: String): String {
        val n = if (raw == raw.toLong().toFloat()) raw.toLong().toString()
        else String.format(java.util.Locale.US, "%.1f", raw)
        return when (unit) {
            "" -> n
            "%", "percent" -> "$n%"
            else -> "$n $unit"
        }
    }

    private fun cleanLabel(s: String): String =
        s.replace(markdownNoise, "")
            .trim()
            .trim('"', '\'', '“', '”')
            .trimEnd('.', ':', ';', ',')
            .trim()

    private fun hasLetter(s: String): Boolean = s.any { it.isLetter() }

    // -----------------------------------------------------------------------
    // Timeline + gallery read text, not numbers
    // -----------------------------------------------------------------------

    private fun timelineEntries(text: String): List<TimelineEntry> {
        if (text.isBlank()) return emptyList()
        val out = ArrayList<TimelineEntry>()
        for (rawLine in text.lineSequence()) {
            val line = cleanLabel(rawLine.trim().replace(bulletPrefix, ""))
            if (line.isEmpty() || line.startsWith("```")) continue
            val m = separator.find(line) ?: continue
            val date = line.substring(0, m.range.first).trim()
            val title = line.substring(m.range.last + 1).trim()
            if (!dateish.matches(date) || title.length < 2) continue
            out += TimelineEntry(date = date, title = title.take(90))
            if (out.size >= 10) break
        }
        return out
    }

    private fun galleryBlock(reply: String, prior: String): Block.WidgetBlock? {
        val urls = LinkedHashSet<String>()
        for (text in listOf(reply, prior)) {
            imageUrl.findAll(text).forEach { urls += it.value.trimEnd('.', ',', ')') }
            if (urls.size >= MAX_GALLERY) break
        }
        if (urls.isEmpty()) return null
        val widget = CarouselWidget(
            title = "Images from the answer above",
            items = urls.take(MAX_GALLERY).map { CarouselItem(image = it, caption = "") }
        )
        return widgetBlock("carousel", widgetJson.encodeToString(widget))
    }

    private fun timelineBlock(reply: String, prior: String): Block.WidgetBlock? {
        val entries = timelineEntries(reply).ifEmpty { timelineEntries(prior) }
        if (entries.size < 2) return null
        val widget = TimelineWidget(title = "Timeline — from the answer above", entries = entries)
        return widgetBlock("timeline", widgetJson.encodeToString(widget))
    }

    // -----------------------------------------------------------------------
    // Block construction
    // -----------------------------------------------------------------------

    /**
     * The no-data case. The user asked for a chart and the answer contains
     * nothing to chart. The title says SAMPLE and the series is called
     * "Sample" — this is a renderer demonstration, never a claim about the
     * user's data.
     */
    private fun sampleChart(type: String): Block.WidgetBlock {
        val labels = listOf("Item 1", "Item 2", "Item 3", "Item 4")
        val values = listOf(42f, 68f, 55f, 81f)
        val series = if (type == "pie" || type == "donut") {
            labels.zip(values).map { (label, value) ->
                ChartSeries(name = label, data = listOf(listOf(0f, value)))
            }
        } else {
            listOf(
                ChartSeries(
                    name = "Sample",
                    data = values.mapIndexed { i, v -> listOf(i.toFloat(), v) }
                )
            )
        }
        val widget = ChartWidget(
            type = type,
            title = "Sample chart — no data in this conversation",
            labels = labels,
            series = series
        )
        return widgetBlock("chart", widgetJson.encodeToString(widget))
    }

    private fun chartBlock(points: List<Point>, type: String, seriesName: String): Block.WidgetBlock {
        val title = if (seriesName == "Value") {
            "From the numbers in this answer"
        } else {
            "$seriesName — from the answer above"
        }
        val series = if (type == "pie" || type == "donut") {
            // Pie reads one series per slice, each with a single (index, value).
            points.map { p -> ChartSeries(name = p.label, data = listOf(listOf(0f, p.value))) }
        } else {
            listOf(
                ChartSeries(
                    name = seriesName,
                    data = points.mapIndexed { i, p -> listOf(i.toFloat(), p.value) }
                )
            )
        }
        val widget = ChartWidget(
            type = type,
            title = title,
            labels = points.map { it.label },
            series = series
        )
        return widgetBlock("chart", widgetJson.encodeToString(widget))
    }

    private fun tableBlock(points: List<Point>): Block.WidgetBlock {
        val widget = TableWidget(
            title = "From the numbers in this answer",
            columns = listOf("Item", "Value"),
            rows = points.map { listOf(it.label, it.display) }
        )
        return widgetBlock("table", widgetJson.encodeToString(widget))
    }

    private fun statBlock(points: List<Point>, seriesName: String): Block.WidgetBlock {
        val widget = StatWidget(
            title = if (seriesName == "Value") "At a glance" else "$seriesName — at a glance",
            cards = points.take(6).map { StatCardData(label = it.label, value = it.display) }
        )
        return widgetBlock("stat", widgetJson.encodeToString(widget))
    }

    private fun progressBlock(points: List<Point>, seriesName: String): Block.WidgetBlock? {
        // Percent-suffixed values are unambiguous. A set of plain numbers that
        // all sit in 0..100 is the other honest reading; anything else would
        // require inventing a scale, so bail out.
        val percentPoints = points.filter { it.percent }
        val chosen = if (percentPoints.size >= 2) percentPoints
        else points.filter { it.value in 0f..100f }.takeIf { it.size == points.size } ?: emptyList()
        if (chosen.size < 2) return null
        val widget = ProgressWidget(
            title = if (seriesName == "Value") "Progress — from the answer above"
            else "$seriesName — from the answer above",
            items = chosen.map { ProgressItem(label = it.label, percent = it.value.coerceIn(0f, 100f)) }
        )
        return widgetBlock("progress", widgetJson.encodeToString(widget))
    }

    private fun widgetBlock(kind: String, json: String): Block.WidgetBlock =
        Block.WidgetBlock(kind = kind, json = json, open = true, sourceStart = RECOVERED_OFFSET)
}
