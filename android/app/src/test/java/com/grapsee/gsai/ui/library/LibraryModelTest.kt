package com.grapsee.gsai.ui.library

import com.grapsee.gsai.data.local.SavedItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * The library's rules, without a device.
 *
 * Every one of these is a way the page could quietly lose something: a chip
 * that filters to nothing, a day heading that swallows an unreadable stamp, a
 * snippet that cuts mid-word. None of them would fail a screenshot.
 */
class LibraryModelTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val today: LocalDate = LocalDate.of(2026, 11, 5)

    private fun item(
        id: String,
        kind: String = "message",
        title: String = "Title",
        content: String = "Content",
        createdAt: String = "2026-11-05T09:00:00.000Z",
    ) = SavedItemEntity(id = id, kind = kind, title = title, content = content, createdAt = createdAt)

    @Test
    fun facetsAreBuiltFromWhatIsActuallySaved() {
        val items = listOf(
            item("1", kind = "message"),
            item("2", kind = "message"),
            item("3", kind = "image"),
        )
        val facets = LibraryModel.facetsOf(items)
        // No "Drafts" chip: it would filter to an empty page.
        assertEquals(listOf("message", "image"), facets.map { it.kind })
        assertEquals(listOf(2, 1), facets.map { it.count })
        assertEquals(listOf("Messages", "Images"), facets.map { it.label })
    }

    @Test
    fun anUnknownKindStillGetsItsOwnChipAndLabel() {
        val facets = LibraryModel.facetsOf(listOf(item("1", kind = "recipe")))
        assertEquals("recipe", facets.single().kind)
        assertEquals("Recipes", facets.single().label)
        assertEquals("Recipe", LibraryModel.kindLabel("recipe"))
    }

    @Test
    fun anEmptyLibraryHasNoFacetsAtAll() {
        assertTrue(LibraryModel.facetsOf(emptyList()).isEmpty())
    }

    @Test
    fun theAllFacetMeansEverythingAndAKindMeansThatKind() {
        val items = listOf(item("1", kind = "message"), item("2", kind = "image"))
        assertEquals(2, LibraryModel.applyFilter(items, null).size)
        assertEquals(2, LibraryModel.applyFilter(items, LibraryModel.ALL).size)
        assertEquals(listOf("2"), LibraryModel.applyFilter(items, "image").map { it.id })
        assertTrue(LibraryModel.applyFilter(items, "draft").isEmpty())
    }

    @Test
    fun daysAreLabelledAndNewestFirst() {
        val items = listOf(
            item("old", createdAt = "2026-10-28T09:00:00.000Z"),
            item("today", createdAt = "2026-11-05T08:00:00.000Z"),
            item("yesterday", createdAt = "2026-11-04T22:00:00.000Z"),
        )
        val days = LibraryModel.groupByDay(items, today = today, zone = zone)
        assertEquals(listOf("Today", "Yesterday", "28 Oct 2026"), days.map { it.label })
        assertEquals(listOf("today"), days[0].items.map { it.id })
        assertEquals(listOf("yesterday"), days[1].items.map { it.id })
    }

    @Test
    fun anUnreadableTimestampIsKeptRatherThanDropped() {
        val days = LibraryModel.groupByDay(
            listOf(item("broken", createdAt = "not-a-date")),
            today = today,
            zone = zone,
        )
        assertEquals("Earlier", days.single().label)
        assertEquals(listOf("broken"), days.single().items.map { it.id })
        assertNull(LibraryModel.dayOf("not-a-date", zone))
    }

    @Test
    fun snippetsCollapseWhitespaceAndCutOnAWordBoundary() {
        val long = "The quick brown fox jumps over the lazy dog, then it keeps going and going."
        val snippet = LibraryModel.snippet(long, max = 30)
        assertTrue("snippet kept a newline: $snippet", !snippet.contains("\n"))
        assertTrue("snippet did not end in an ellipsis: $snippet", snippet.endsWith("…"))
        assertTrue("snippet overran the budget: $snippet", snippet.length <= 31)
        assertTrue("snippet cut mid-word: $snippet", !snippet.removeSuffix("…").endsWith(" "))
        assertEquals(
            "short answers are shown whole",
            "Two words",
            LibraryModel.snippet("  Two\n\nwords  "),
        )
    }

    @Test
    fun theSummaryCountsEveryFacet() {
        val items = listOf(
            item("1", kind = "message"),
            item("2", kind = "message"),
            item("3", kind = "draft"),
        )
        assertEquals("2 messages · 1 draft", LibraryModel.summaryLine(items))
        assertEquals("Nothing saved yet", LibraryModel.summaryLine(emptyList()))
        assertEquals(
            "1 image",
            LibraryModel.summaryLine(listOf(item("1", kind = "image"))),
        )
    }
}
