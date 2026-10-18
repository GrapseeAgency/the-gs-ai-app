package com.grapsee.gsai.ui.chat.content

import kotlin.math.max

// ---------------------------------------------------------------------------
// ASCII → native visuals.
//
// The model keeps answering "make me a chart" with ASCII art in a plain code
// fence (bar runs, pipe tables) — which the renderer showed as monospace text,
// i.e. exactly the "bunch of texts" the user complained about. This module
// recognises those fences at render time and converts them into the SAME
// widget payloads the GENUI path renders natively, so a plain fence becomes a
// real chart with zero model cooperation. Markdown tables get the same
// treatment through [chartFromTableBlock].
//
// Deliberately conservative: anything that does not parse with confidence
// stays a code block — never a chart with invented numbers.
// ---------------------------------------------------------------------------

/** Everything visual one plain fence may be shown as. */
class AsciiVisual(
    val chart: ChartWidget?,
    val table: TableWidget?
)

private val BAR_RUN = Regex("[█▓▒■●▮◆▪#*]{3,}")
private val NUM = Regex("-?\\d+(?:[.,]\\d+)?")

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
 * Everything a plain fence can be shown as. Returns null when the body is
 * ordinary code — the caller then renders the code card unchanged.
 */
fun visualFromAscii(code: String): AsciiVisual? {
    if (code.isBlank() || code.length > 12_000) return null
    val lines = code.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
    if (lines.size < 2) return null
    val title = guessTitle(lines)
    barChartFrom(lines, title)?.let { return AsciiVisual(it, tableFrom(lines, title)) }
    val t = tableFrom(lines, title) ?: return null
    val chart = chartFromGrid(t)
    return AsciiVisual(chart, t)
}

/**
 * Bar-art mode: ≥3 lines each carrying a run of block characters, optionally
 * followed by the value. Without an explicit number the bar length becomes
 * the value — the only honest reading of a bare ASCII bar.
 */
private fun barChartFrom(lines: List<String>, title: String): ChartWidget? {
    val candidates = lines.mapNotNull { line ->
        val run = BAR_RUN.find(line) ?: return@mapNotNull null
        val before = line.take(run.range.first).trim().trim('|', '-', ' ', '·', ':', '>').trim()
        val after = line.substring(run.range.last + 1)
        val value = parseNumber(after) ?: run.value.length.toFloat()
        if (before.isEmpty()) null else before to value
    }
    if (candidates.size < 3) return null
    return ChartWidget(
        type = "bar",
        title = title,
        series = listOf(ChartSeries(name = "", data = candidates.mapIndexed { i, (_, v) -> listOf(i.toFloat(), v) })),
        labels = candidates.map { it.first }
    )
}

/** A caption line (no bars, not a separator, not a row) titles the grid. */
private fun guessTitle(lines: List<String>): String {
    val first = lines.firstOrNull() ?: return ""
    if (BAR_RUN.containsMatchIn(first)) return ""
    if (isSeparatorLike(first)) return ""
    if (splitRow(first).size >= 2 && parseNumber(splitRow(first).last()) != null) return ""
    return if (first.length <= 60) first else ""
}

private fun isSeparatorLike(line: String): Boolean =
    line.all { it == '-' || it == '=' || it == ' ' || it == '+' || it == '|' || it == '·' }

/** Pipe- or 2+-space-separated grid with a readable numeric column. */
private fun tableFrom(lines: List<String>, title: String): TableWidget? {
    val rows = lines.filterNot { isSeparatorLike(it) }.map { splitRow(it) }
    if (rows.size < 2) return null
    val width = rows.maxOf { it.size }
    if (width < 2 || rows.any { it.size != width }) return null
    val numericCol = (1 until width).firstOrNull { c ->
        rows.drop(1).count { parseNumber(it.getOrElse(c) { "" }) != null } >= max(2, (rows.size - 1) * 2 / 3)
    } ?: return null
    return TableWidget(title = title, columns = rows.first(), rows = rows.drop(1))
}

/** Grid → bar chart: column 0 categories, first readable numeric column values. */
private fun chartFromGrid(t: TableWidget): ChartWidget? {
    val numericCol = (1 until t.columns.size).firstOrNull { c ->
        t.rows.count { parseNumber(it.getOrElse(c) { "" }) != null } >= max(2, t.rows.size * 2 / 3)
    } ?: return null
    val points = t.rows.mapIndexedNotNull { i, row ->
        val v = parseNumber(row.getOrElse(numericCol) { "" }) ?: return@mapIndexedNotNull null
        listOf(i.toFloat(), v)
    }
    if (points.size < 2) return null
    return ChartWidget(
        type = "bar",
        title = t.title,
        series = listOf(ChartSeries(name = t.columns.getOrElse(numericCol) { "Value" }, data = points)),
        labels = t.rows.map { it.first().take(20) }
    )
}

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
        rows.count { parseNumber(it.getOrElse(c) { "" }) != null } >= max(2, rows.size * 2 / 3)
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
