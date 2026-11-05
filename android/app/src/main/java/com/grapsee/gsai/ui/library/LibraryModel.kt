package com.grapsee.gsai.ui.library

import com.grapsee.gsai.data.local.SavedItemEntity
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The saved library's pure logic — facets, labels, day headings and the
 * snippet a row shows.
 *
 * It lives outside the composables for one reason: the library's only real
 * job is to not lose things, and "not losing things" is a question about the
 * data, not about the pixels. The JVM suite can answer it without a device.
 *
 * Kinds come from `ChatRepository.saveToLibrary`: chat turns land as
 * "message", the studios save images and drafts under their own kind. The
 * list is open — an unknown kind is labelled from its own name rather than
 * dropped, because a saved item the app refuses to show is a lost item.
 */
internal object LibraryModel {

    /** The facet that means "no filter". */
    const val ALL = "all"

    /** The order facets appear in: the kinds a chat actually produces first. */
    private val CANONICAL = listOf("message", "image", "draft")

    /** Singular, for one item's badge. */
    fun kindLabel(kind: String): String = when (kind.lowercase(Locale.US)) {
        "message" -> "Message"
        "image" -> "Image"
        "draft" -> "Draft"
        "" -> "Saved item"
        else -> kind.replaceFirstChar { it.uppercase(Locale.US) }
    }

    /** Plural, for a filter chip that counts several. */
    fun facetLabel(kind: String): String = when (kind.lowercase(Locale.US)) {
        "message" -> "Messages"
        "image" -> "Images"
        "draft" -> "Drafts"
        "" -> "Items"
        else -> kind.replaceFirstChar { it.uppercase(Locale.US) } + "s"
    }

    /**
     * The chips this library should show, with their counts.
     *
     * Built from the items that exist, not from a fixed list: a "Drafts" chip
     * that filters to an empty page is the dead end this app keeps deleting.
     * Empty library therefore has no chips at all — just the empty state.
     */
    fun facetsOf(items: List<SavedItemEntity>): List<LibraryFacet> {
        val counts = items.groupingBy { it.kind.lowercase(Locale.US) }.eachCount()
        val ordered = CANONICAL.filter { counts.containsKey(it) } +
            counts.keys.filterNot { it in CANONICAL }.sorted()
        return ordered.map { LibraryFacet(kind = it, label = facetLabel(it), count = counts.getValue(it)) }
    }

    /** [kind] null or [ALL] means everything; anything else is an exact match. */
    fun applyFilter(items: List<SavedItemEntity>, kind: String?): List<SavedItemEntity> =
        if (kind.isNullOrBlank() || kind == ALL) items
        else items.filter { it.kind.equals(kind, ignoreCase = true) }

    /** The day a save belongs to, or null when the timestamp cannot be read. */
    fun dayOf(iso: String, zone: ZoneId = ZoneId.systemDefault()): LocalDate? = runCatching {
        val instant = runCatching { Instant.parse(iso) }
            .getOrElse { OffsetDateTime.parse(iso).toInstant() }
        instant.atZone(zone).toLocalDate()
    }.getOrNull()

    /** "Today" / "Yesterday" / "5 Nov 2026" — the heading above a day's saves. */
    fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK).format(date)
    }

    /** "5 Nov" — the short stamp on a row, for saves that are not today. */
    fun shortDate(iso: String, zone: ZoneId = ZoneId.systemDefault()): String {
        val date = dayOf(iso, zone) ?: return ""
        return DateTimeFormatter.ofPattern("d MMM", Locale.UK).format(date)
    }

    /**
     * One line of a saved answer, for the row under its title. Newlines and
     * runs of spaces collapse so a multi-paragraph answer does not push the
     * row taller than the reference's 52dp.
     */
    fun snippet(content: String, max: Int = 72): String {
        val flat = content.replace(Regex("\\s+"), " ").trim()
        if (flat.length <= max) return flat
        val cut = flat.take(max)
        // Break on a word boundary when there is one near the end.
        val space = cut.lastIndexOf(' ')
        val body = if (space > max / 2) cut.take(space) else cut
        return body.trimEnd().trimEnd(',', '.', ';', ':') + "…"
    }

    /**
     * Items grouped under day headings, newest first.
     *
     * Unreadable timestamps are NOT dropped: they fall under "Earlier", so a
     * row written by an older build still shows up rather than vanishing.
     */
    fun groupByDay(
        items: List<SavedItemEntity>,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<LibraryDay> {
        val sorted = items.sortedByDescending { it.createdAt }
        val buckets = LinkedHashMap<String, MutableList<SavedItemEntity>>()
        for (item in sorted) {
            val date = dayOf(item.createdAt, zone)
            val key = if (date == null) "Earlier" else dayLabel(date, today)
            buckets.getOrPut(key) { mutableListOf() } += item
        }
        return buckets.map { (label, rows) -> LibraryDay(label = label, items = rows) }
    }

    /** "9 messages · 2 images · 1 draft" — the line under the hero's count.
     *  The count itself is the card's title, so it is not repeated here.
     *  Singular at one, because "1 drafts" is the kind of copy a count is
     *  supposed to get right. */
    fun summaryLine(items: List<SavedItemEntity>): String {
        if (items.isEmpty()) return "Nothing saved yet"
        return facetsOf(items).joinToString(" · ") { facet ->
            val name = if (facet.count == 1) kindLabel(facet.kind) else facet.label
            "${facet.count} ${name.lowercase(Locale.UK)}"
        }
    }
}

internal data class LibraryFacet(val kind: String, val label: String, val count: Int)

internal data class LibraryDay(val label: String, val items: List<SavedItemEntity>)
