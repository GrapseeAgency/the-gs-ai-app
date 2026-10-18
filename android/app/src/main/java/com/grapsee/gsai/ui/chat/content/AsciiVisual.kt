package com.grapsee.gsai.ui.chat.content

// ---------------------------------------------------------------------------
// Plain text → native widget, chosen for you.
//
// The model keeps answering "make me a chart" with ASCII art in a plain code
// fence (bar runs, pipe tables, percentage lists, dated logs) — which the
// renderer showed as monospace text, i.e. exactly the "bunch of texts" the
// user complained about. This module recognises those fences at render time
// and converts them into the SAME widget payloads the GENUI path renders
// natively, so a plain fence becomes a real, interactive visual with zero
// model cooperation.
//
// Detection is deliberately conservative: when nothing parses with
// confidence the fence stays a code block — never a chart with invented
// numbers. When several kinds could match, the richest reading wins.
// ---------------------------------------------------------------------------

private val BAR_RUN = Regex("[█▓▒■●▮◆▪#*]{3,}")
private val NUM = Regex("-?\\d+(?:[.,]\\d+)?")
private val PERCENT_LINE = Regex("^(.*?)[\\s:–-]+(\\d+(?:[.,]\\d+)?)\\s*%\\s*$")
private val DATE_LINE = Regex(
    "^(\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}|\\d{4}[-/.]\\d{1,2}|\\d{4}|(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\.?\\s+\\d{1,2}(?:,\\s*\\d{4})?)\\s*[-–—|:]\\s*(.+)$",
    RegexOption.IGNORE_CASE
)
private val LABEL_VALUE = Regex("^(.{2,40}?)\\s*[:\\-–—]\\s*(-?[\\d.,]+)\\s*([A-Za-z%°$/]+)?\\s*$")

/** Recognise a value that may carry a unit ("63mg", "82.1", "1,204"). */
internal fun parseNumber(raw: String): Float? {
    val m = NUM.find(raw) ?: return null
    return m.value.replace(",", "").toFloatOrNull()
}

/** Plain text of an inline span tree (tables never nest emphasis in practice). */
internal fun flattenSpans(spans: List<InlineSpan>): String {
    val sb = StringBuilder()
    fun walk(list: List<InlineSpan>) {
        for (s in list) when (s) {
            is InlineSpan.TextSpan -> sb.append(s.text)
            is InlineSpan.CodeSpan -> sb.append(s.code)
            is InlineSpan.BoldSpan -> walk(s.children)
            is InlineSpan.ItalicSpan -> walk(s.children)
            is InlineSpan.StrikeSpan -> walk(s.children)
            else -> Unit
        }
    }
    walk(spans)
    return sb.toString()
}

/**
 * The single native widget this plain fence should become, or null when the
 * body is ordinary code. The renderer draws the widget directly — no tabs, no
 * placeholder, the user gets the picture or the code and nothing else.
 */
fun visualFromAscii(code: String): Widget? {
    if (code.isBlank() || code.length > 12_000) return null
    val lines = code.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
    if (lines.size < 2) return null

    barChartFrom(lines)?.let { return it }
    timelineFrom(lines)?.let { return it }
    progressFrom(lines)?.let { return it }
    statCardsFrom(lines)?.let { return it }
    gridFrom(lines)?.let { return it } // pie / bar / table, richest first
    return null
}

/** Bar-art mode: ≥3 lines carrying a run of block characters. */
private fun barChartFrom(lines: List<String>): Widget? {
    val candidates = lines.mapNotNull { line ->
        val run = BAR_RUN.find(line) ?: return@mapNotNull null
        val before = line.take(run.range.first).trim().trim('|', '-', ' ', '·', ':', '>').trim()
        val after = line.substring(run.range.last + 1)
        val value = parseNumber(after) ?: run.value.length.toFloat()
        if (before.isEmpty()) null else before to value
    }
    if (candidates.size < 3) return null
    return Widget.Chart(
        ChartWidget(
            type = "bar",
            title = captionOf(lines),
            series = listOf(ChartSeries(name = "", data = candidates.mapIndexed { i, (_, v) -> listOf(i.toFloat(), v) })),
            labels = candidates.map { it.first }
        )
    )
}

/** Dated log lines ("2026-10-19 — Shipped the fix") become a timeline. */
private fun timelineFrom(lines: List<String>): Widget? {
    val entries = lines.mapNotNull { line ->
        val m = DATE_LINE.matchEntire(line.trim()) ?: return@mapNotNull null
        TimelineEntry(date = m.groupValues[1], title = m.groupValues[2].trim())
    }
    if (entries.size < 2) return null
    return Widget.Timeline(TimelineWidget(title = captionOf(lines), entries = entries))
}

/** Percentage lines ("Testing  ████  63%") become a progress widget. */
private fun progressFrom(lines: List<String>): Widget? {
    val items = lines.mapNotNull { raw ->
        val line = raw.trim()
        // Grid rows ("Organic search | 42%") are a table's business, not a
        // progress bar's — only true single-column "label NN%" lines ride here.
        if (line.contains('|') || splitRow(line).size > 1) return@mapNotNull null
        val m = PERCENT_LINE.matchEntire(line) ?: return@mapNotNull null
        val label = m.groupValues[1].trim().trim('|', '-', ' ', '·', '>').trim()
        val pct = parseNumber(m.groupValues[2]) ?: return@mapNotNull null
        if (label.isEmpty()) null else ProgressItem(label = label.take(48), percent = pct)
    }
    if (items.size < 2) return null
    return Widget.Progress(ProgressWidget(title = captionOf(lines), items = items))
}

/** One or two "label — value unit" lines become stat cards. */
private fun statCardsFrom(lines: List<String>): Widget? {
    if (lines.size > 2) return null
    val cards = lines.mapNotNull { line ->
        val m = LABEL_VALUE.matchEntire(line.trim()) ?: return@mapNotNull null
        val v = parseNumber(m.groupValues[2]) ?: return@mapNotNull null
        StatCardData(
            label = m.groupValues[1].trim().take(40),
            value = if (v % 1f == 0f) v.toLong().toString() else v.toString(),
            unit = m.groupValues[3].trim().take(8)
        )
    }
    if (cards.isEmpty()) return null
    return Widget.Stat(StatWidget(title = captionOf(lines), cards = cards))
}

/** Pipe- or 2+-space-separated grids with a readable numeric column. */
private fun gridFrom(lines: List<String>): Widget? {
    val rows = lines.filterNot { isSeparatorLike(it) }.map { splitRow(it) }
    if (rows.size < 2) return null
    val width = rows.maxOf { it.size }
    if (width < 2 || rows.any { it.size != width }) return null
    val title = captionOf(lines)
    val numericCol = (1 until width).firstOrNull { c ->
        rows.drop(1).count { parseNumber(it.getOrElse(c) { "" }) != null } >= maxOf(2, (rows.size - 1) * 2 / 3)
    } ?: return null
    val header = rows.first()
    val body = rows.drop(1)
    val values = body.mapNotNull { r -> parseNumber(r.getOrElse(numericCol) { "" }) }
    if (values.size < 2) return null
    val labels = body.map { it.first().take(20) }
    val seriesName = header.getOrElse(numericCol) { "Value" }

    // Shares of a whole (explicit % everywhere, or a sum that lands on 100)
    // read as a pie; everything else is a bar chart.
    val allPercent = body.all { it.getOrElse(numericCol) { "" }.trim().endsWith("%") }
    val sum = values.sum()
    val looksLikeShares = allPercent || (sum in 97f..103f && values.all { it > 0f } && values.size in 2..10)
    if (looksLikeShares && values.all { it > 0f }) {
        return Widget.Chart(
            ChartWidget(
                type = "pie",
                title = title,
                series = values.mapIndexed { i, v -> ChartSeries(name = labels[i], data = listOf(listOf(i.toFloat(), v))) }
            )
        )
    }
    val chart = Widget.Chart(
        ChartWidget(
            type = "bar",
            title = title,
            series = listOf(ChartSeries(name = seriesName, data = values.mapIndexed { i, v -> listOf(i.toFloat(), v) })),
            labels = labels
        )
    )
    // A wide, mostly-textual grid (3+ columns of prose) reads better as a
    // table than as a chart; anything two-column is a chart.
    if (width >= 3 && numericCol == width - 1) return Widget.Table(
        TableWidget(title = title, columns = header, rows = body)
    )
    return chart
}

/** A caption line (no bars, not a separator, not a grid row) titles the widget. */
private fun captionOf(lines: List<String>): String {
    val first = lines.firstOrNull() ?: return ""
    if (BAR_RUN.containsMatchIn(first)) return ""
    if (isSeparatorLike(first)) return ""
    if (splitRow(first).size >= 2 && parseNumber(splitRow(first).last()) != null) return ""
    return if (first.length <= 60) first else ""
}

private fun isSeparatorLike(line: String): Boolean =
    line.all { it == '-' || it == '=' || it == ' ' || it == '+' || it == '|' || it == '·' }

/** Split a fence line into cells — pipes win, else runs of 2+ spaces. */
private fun splitRow(line: String): List<String> {
    val pipeTrim = line.trim().trim('|')
    if (pipeTrim.contains('|')) {
        val cells = pipeTrim.split('|').map { it.trim() }
        if (cells.size >= 2) return cells
    }
    return line.trim().split(Regex("\\s{2,}")).map { it.trim() }.filter { it.isNotEmpty() }
}

/** Markdown tables → the same bar chart, driven by the rendered table. */
fun chartFromTableBlock(block: Block.TableBlock): ChartWidget? {
    val header = block.headers.map { flattenSpans(it) }
    val rows = block.rows.map { row -> row.map { flattenSpans(it) } }
    if (header.size < 2 || rows.size < 2) return null
    val numericCol = (1 until header.size).firstOrNull { c ->
        rows.count { parseNumber(it.getOrElse(c) { "" }) != null } >= maxOf(2, rows.size * 2 / 3)
    } ?: return null
    val points = rows.mapIndexedNotNull { i, row ->
        val v = parseNumber(row.getOrElse(numericCol) { "" }) ?: return@mapIndexedNotNull null
        listOf(i.toFloat(), v)
    }
    if (points.size < 2) return null
    return ChartWidget(
        type = "bar",
        series = listOf(ChartSeries(name = header.getOrElse(numericCol) { "Value" }, data = points)),
        labels = rows.map { it.first().take(20) }
    )
}
