package com.grapsee.gsai.ui.chat.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain text → native widget conversions.
 *
 * Every sample is REAL model output captured from the production backend
 * (curl against the deployed origin, 2026-10-19): the "make me a chart"
 * reply is a pipe table inside a plain fence. Before this converter both
 * rendered as monospace text — the "just a bunch of texts" complaint.
 */
class AsciiVisualTest {

    @Test
    fun `pipe table fence becomes a bar chart`() {
        val code = """
            Country        | Cups/day | Life Expectancy
            -----------------------------------------------
            Finland        | 10.0     | 82.1
            Norway         | 9.9      | 82.3
            Iceland        | 9.0      | 83.1
            Denmark        | 8.7      | 81.3
            Netherlands    | 8.4      | 82.3
            Sweden         | 8.2      | 82.8
            Switzerland    | 8.1      | 83.6
        """.trimIndent()

        val visual = visualFromAscii(code)
        assertTrue("fence must convert to a widget", visual is Widget.Chart)
        val w = (visual as Widget.Chart).w
        assertEquals("bar", w.type)
        assertEquals(7, w.series.single().data.size)
        assertEquals(listOf("Finland", "Norway", "Iceland", "Denmark", "Netherlands", "Sweden", "Switzerland"), w.labels)
        // first numeric column is Cups/day: Finland = 10.0
        assertEquals(10f, w.series.single().data.first()[1], 0.001f)
    }

    @Test
    fun `percentage grid becomes a pie chart`() {
        val code = """
            Traffic source | Share
            Organic search | 42%
            Direct         | 28%
            Referral       | 18%
            Social         | 12%
        """.trimIndent()

        val visual = visualFromAscii(code)
        assertTrue("share grid must become a pie", visual is Widget.Chart)
        val w = (visual as Widget.Chart).w
        assertEquals("pie", w.type)
        assertEquals(4, w.series.size)
        assertEquals("Organic search", w.series.first().name)
        assertEquals(42f, w.series.first().data.first()[1], 0.001f)
    }

    @Test
    fun `bar art fence becomes chart with values`() {
        val code = """
            CAFFEINE CONTENT
            ESPRESSO SHOT    ████              63mg
            AMERICANO       ██████ ██          94mg
            BREWED COFFEE   ██████████         95mg
            LATTE            ████████          63mg
        """.trimIndent()

        val visual = visualFromAscii(code)
        assertTrue("bar art must convert to a chart", visual is Widget.Chart)
        val w = (visual as Widget.Chart).w
        assertEquals(listOf("ESPRESSO SHOT", "AMERICANO", "BREWED COFFEE", "LATTE"), w.labels)
        assertEquals(listOf(63f, 94f, 95f, 63f), w.series.single().data.map { it[1] })
        assertEquals("CAFFEINE CONTENT", w.title)
    }

    @Test
    fun `dated log lines become a timeline`() {
        val code = """
            Release notes
            2026-10-18 - Shipped the honesty sweep
            2026-10-19 - Shipped the final sweep
            2026-10-20 - Shipped the rendering fix
        """.trimIndent()

        val visual = visualFromAscii(code)
        assertTrue("dated lines must become a timeline", visual is Widget.Timeline)
        val w = (visual as Widget.Timeline).w
        assertEquals(3, w.entries.size)
        assertEquals("Release notes", w.title)
        assertEquals("2026-10-18", w.entries[0].date)
        assertEquals("Shipped the honesty sweep", w.entries[0].title)
    }

    @Test
    fun `percentage lines become a progress widget`() {
        val code = """
            Testing 63%
            Review 40%
            docs 90%
        """.trimIndent()

        val visual = visualFromAscii(code)
        assertTrue("percent lines must become progress", visual is Widget.Progress)
        val w = (visual as Widget.Progress).w
        assertEquals(listOf(63f, 40f, 90f), w.items.map { it.percent })
        assertEquals(listOf("Testing", "Review", "docs"), w.items.map { it.label })
    }

    @Test
    fun `two label value lines become stat cards`() {
        val code = """
            Monthly users: 1204
            Chats per day: 38
        """.trimIndent()

        val visual = visualFromAscii(code)
        assertTrue("label/value lines must become stats", visual is Widget.Stat)
        val w = (visual as Widget.Stat).w
        assertEquals(2, w.cards.size)
        assertEquals("Monthly users", w.cards[0].label)
        assertEquals("1204", w.cards[0].value)
        assertEquals("38", w.cards[1].value)
    }

    @Test
    fun `wide prose grid stays a table`() {
        val code = """
            Task        | Owner  | Hours
            Audit       | Ana    | 12
            Fix         | Bohdan | 20
            Verify      | Chen   | 8
        """.trimIndent()

        val visual = visualFromAscii(code)
        assertTrue("three-column grid must become a table", visual is Widget.Table)
        val w = (visual as Widget.Table).w
        assertEquals(listOf("Task", "Owner", "Hours"), w.columns)
        assertEquals(3, w.rows.size)
    }

    @Test
    fun `ordinary source code stays a code block`() {
        val code = """
            fun main() {
                val list = listOf(1, 2, 3)
                println(list.sum())
            }
        """.trimIndent()
        assertNull(visualFromAscii(code))
    }

    @Test
    fun `markdown table with numeric column converts to chart`() {
        val block = Block.TableBlock(
            headers = listOf(
                listOf(InlineSpan.TextSpan("Region")),
                listOf(InlineSpan.TextSpan("Users"))
            ),
            aligns = listOf(TableAlign.LEFT, TableAlign.RIGHT),
            rows = listOf(
                listOf(listOf(InlineSpan.TextSpan("EU")), listOf(InlineSpan.TextSpan("1,204"))),
                listOf(listOf(InlineSpan.TextSpan("US")), listOf(InlineSpan.TextSpan("980")))
            ),
            sourceStart = 0
        )
        val chart = chartFromTableBlock(block)
        assertEquals(listOf("EU", "US"), chart!!.labels)
        assertEquals(listOf(1204f, 980f), chart.series.single().data.map { it[1] })
    }

    @Test
    fun `prose fence with no numbers is left alone`() {
        val code = """
            I would love to help with that.
            Unfortunately I cannot render charts in this interface.
            I can only generate text responses here.
        """.trimIndent()
        assertNull(visualFromAscii(code))
    }

    @Test
    fun `fraction lines become a gauge`() {
        val code = """
            Battery
            Battery: 78/100
            Signal: 64/100
        """.trimIndent()
        val visual = visualFromAscii(code)
        assertTrue("fraction lines must become a gauge", visual is Widget.Gauge)
        val w = (visual as Widget.Gauge).w
        assertEquals(2, w.items.size)
        assertEquals(78f, w.items[0].value)
        assertEquals(100f, w.items[0].max)
        assertEquals(64f, w.items[1].value)
    }

    @Test
    fun `coordinate pairs become a scatter chart`() {
        val code = """
            Samples
            3, 5
            1, 2
            4, 8
            6, 1
            2, 3
        """.trimIndent()
        val visual = visualFromAscii(code)
        assertTrue("coordinate pairs must become a scatter chart", visual is Widget.Chart)
        val w = (visual as Widget.Chart).w
        assertEquals("scatter", w.type)
        assertEquals(5, w.series.single().data.size)
    }
}
