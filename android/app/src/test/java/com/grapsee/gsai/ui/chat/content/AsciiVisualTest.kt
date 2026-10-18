package com.grapsee.gsai.ui.chat.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ASCII → native visual conversions.
 *
 * Both samples are REAL model output captured from the production backend
 * (curl against the deployed origin, 2026-10-19): the "make me a chart"
 * reply is a plain code fence holding a pipe table, and the earlier reply
 * shape is bar art. Before this converter both rendered as monospace text —
 * the "just a bunch of texts" complaint.
 */
class AsciiVisualTest {

    @Test
    fun `pipe table fence becomes chart and table`() {
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
        assertNotNull(visual)
        val chart = visual!!.chart
        assertNotNull("fence must convert to a chart", chart)
        assertEquals("bar", chart!!.type)
        assertEquals(7, chart.series.single().data.size)
        assertEquals(listOf("Finland", "Norway", "Iceland", "Denmark", "Netherlands", "Sweden", "Switzerland"), chart.labels)
        // first numeric column is Cups/day: Finland = 10.0
        assertEquals(10f, chart.series.single().data.first()[1], 0.001f)
        val table = visual.table
        assertNotNull("fence must also convert to a table", table)
        assertEquals(3, table!!.columns.size)
        assertEquals(7, table.rows.size)
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
        assertNotNull(visual)
        val chart = visual!!.chart
        assertNotNull("bar art must convert to a chart", chart)
        assertEquals(listOf("ESPRESSO SHOT", "AMERICANO", "BREWED COFFEE", "LATTE"), chart!!.labels)
        assertEquals(listOf(63f, 94f, 95f, 63f), chart.series.single().data.map { it[1] })
        assertEquals("CAFFEINE CONTENT", chart.title)
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
        assertNotNull(chart)
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
}
