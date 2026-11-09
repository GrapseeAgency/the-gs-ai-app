package com.grapsee.gsai.ui.chat.content

import java.time.LocalDate
import java.time.YearMonth

/** Source order is evidence: never sort categories into an invented trend. */
internal fun chronologicalChartLabels(labels: List<String>): Boolean {
    if (labels.size < 2) return false
    val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    var family: String? = null
    var previous: Long? = null
    for (source in labels) {
        val label = source.trim().lowercase()
        val quarter = Regex("""^q([1-4])(?:\s+((?:19|20|21)\d{2}))?$""").matchEntire(label)
        val month = Regex("""^([a-z]+)(?:\s+((?:19|20|21)\d{2}))?$""").matchEntire(label)
        val coordinate: Pair<String, Long> = when {
            Regex("""^(?:19|20|21)\d{2}$""").matches(label) -> "year" to label.toLong()
            Regex("""^\d{4}-\d{2}(?:-\d{2})?$""").matches(label) -> {
                val day = runCatching { if (label.length == 7) YearMonth.parse(label).atDay(1) else LocalDate.parse(label) }.getOrNull() ?: return false
                "date" to day.toEpochDay()
            }
            quarter != null -> {
                val year = quarter.groupValues[2]
                (if (year.isEmpty()) "quarter" else "dated-quarter") to ((year.toLongOrNull() ?: 0) * 4 + quarter.groupValues[1].toLong())
            }
            month != null && Regex("""^(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)$""").matches(month.groupValues[1]) -> {
                val year = month.groupValues[2]
                (if (year.isEmpty()) "month" else "dated-month") to ((year.toLongOrNull() ?: 0) * 12 + months.indexOf(month.groupValues[1].take(3)))
            }
            else -> return false
        }
        if ((family != null && family != coordinate.first) || (previous != null && coordinate.second <= previous)) return false
        family = coordinate.first
        previous = coordinate.second
    }
    return true
}

internal fun categoricalChartType(labels: List<String>): String = when {
    chronologicalChartLabels(labels) -> "line"
    labels.size > 7 || labels.any { it.length > 18 } -> "hbar"
    else -> "bar"
}

internal fun pieAlternativeAllowed(w: ChartWidget): Boolean {
    val slices = w.type in setOf("pie", "donut")
    if (!slices && w.series.size != 1) return false
    val values = w.series.flatMap { it.data.map { point -> point.getOrNull(1) ?: Float.NaN } }
    if (values.isEmpty() || values.any { !it.isFinite() || it < 0 }) return false
    val total = values.sumOf { it.toDouble() }
    return total > 0 && (slices || w.unit != "%" || kotlin.math.abs(total - 100) < 1e-6)
}
