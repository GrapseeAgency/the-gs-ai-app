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
 * client can render charts perfectly well — what it lacked was data.
 *
 * This layer reads the numbers the answer ALREADY stated (or the turn before
 * it) and builds the widget locally, so the user gets the visual even when the
 * model refuses to emit one. It is the difference between "our app can render"
 * and "our app renders", regardless of which model is behind the API.
 *
 * HONESTY RULES (non-negotiable)
 * ------------------------------
 *  - never invent a number: every value is parsed from text already shown;
 *  - never hide that this was reconstructed — the title says "from the answer
 *    above";
 *  - bail out silently when fewer than two labelled values exist, rather than
 *    drawing a chart out of nothing;
 *  - the only prose ever removed is a *false* capability claim ("I can't
 *    display charts"), and only when a widget was successfully recovered.
 */
internal sealed interface VisualAsk {
    data class Chart(val type: String) : VisualAsk
    object Table : VisualAsk
}

internal object WidgetRecovery {

    private const val MAX_POINTS = 8

    /** One extracted value. [display] keeps the human-facing unit ("2.5 trillion"). */
    private data class Point(val label: String, val value: Float, val display: String)

    private data class Raw(
        val section: String,
        val label: String,
        val value: Float,
        val display: String
    )

    // Order matters: the most specific phrase wins ("horizontal bar" before
    // "bar chart" before the bare "chart" fallback).
    private val chartTypes = listOf(
        "horizontal bar" to "hbar",
        "stacked bar" to "stacked",
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

    private val widgetFence = Regex(
        """(?m)^\s*```(?:chart|stat|table|compare|progress|timeline|gauge|quiz|form|""" +
            """carousel|cards|followups|media|actions|panel|voice|calc|app)\b"""
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
                renderVerbs + """ (?:any )?(?:visual|interactive )?""" +
                """(?:charts?|graphs?|visuali[sz]ations?|tables?)[^.!?]*[.!?]"""
        ),
        Regex(
            """(?i)\bI(?:'m| am) unable to """ + renderVerbs + """ (?:any )?""" +
                """(?:visual|interactive )?(?:charts?|graphs?|visuali[sz]ations?)[^.!?]*[.!?]"""
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
        return null
    }

    /**
     * Builds a widget from text the answer already contains, or returns null.
     * [reply] is tried first; [prior] (the previous assistant turn) is the
     * fallback for the very common "show me a chart" follow-up with no data.
     */
    fun recover(reply: String, prompt: String, prior: String): Block.WidgetBlock? {
        val ask = askOf(prompt) ?: return null
        val fromReply = extract(reply)
        val chosen = if (fromReply.points.size >= 2) fromReply else extract(prior)
        if (chosen.points.size < 2) return null
        return when (ask) {
            is VisualAsk.Chart -> chartBlock(chosen.points, ask.type, chosen.seriesName)
            VisualAsk.Table -> tableBlock(chosen.points)
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
        // are the categories and the metric is the series — the screenshot
        // case. Otherwise the labels themselves are the categories.
        val counts = rows.groupingBy { it.label.lowercase() }.eachCount()
        val dominant = counts.entries.maxByOrNull { it.value }
        val useSection = dominant != null && dominant.value >= 2 &&
            dominant.value * 2 >= rows.size &&
            rows.count { it.label.equals(dominant.key, ignoreCase = true) && it.section.isNotBlank() } >= 2

        val points = if (useSection) {
            rows.filter {
                it.label.equals(dominant!!.key, ignoreCase = true) && it.section.isNotBlank()
            }.map { Point(it.section, it.value, it.display) }
        } else {
            rows.map { Point(it.label, it.value, it.display) }
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
                return Raw(section, label, v.first, v.second)
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
        return Raw(section, label, v.first, v.second)
    }

    /** Returns (normalised value, human display) or null. */
    private fun parseNumber(text: String): Pair<Float, String>? {
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
        return value to display(raw, unit)
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
    // Block construction
    // -----------------------------------------------------------------------

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
        return Block.WidgetBlock(
            kind = "chart",
            json = widgetJson.encodeToString(widget),
            open = true,
            sourceStart = 0
        )
    }

    private fun tableBlock(points: List<Point>): Block.WidgetBlock {
        val widget = TableWidget(
            title = "From the numbers in this answer",
            columns = listOf("Item", "Value"),
            rows = points.map { listOf(it.label, it.display) }
        )
        return Block.WidgetBlock(
            kind = "table",
            json = widgetJson.encodeToString(widget),
            open = true,
            sourceStart = 0
        )
    }
}
