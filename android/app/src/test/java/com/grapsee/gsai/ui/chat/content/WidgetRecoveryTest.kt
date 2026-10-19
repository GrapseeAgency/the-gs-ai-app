package com.grapsee.gsai.ui.chat.content

import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Widget recovery must be provable, not hoped for. These tests replay the
 * exact failure the operator hit on a real device: the model answered
 * "Show me a bar chart" with "I'm unable to create or display visualizations",
 * while the numbers were sitting in the previous answer.
 */
class WidgetRecoveryTest {

    private val refusal = """
        I apologize, but I'm unable to create or display visualizations like bar charts.
        As a text-based AI, I can only provide information in written form.

        However, I can describe what such a bar chart would look like: the tallest bar
        would be software, followed by semiconductors.
    """.trimIndent()

    private val sectorAnswer = """
        Here is the breakdown by sector:

        5. Telecommunications
        - Market Cap: ~$2-3 trillion
        - Key Players: AT&T, Verizon, China Mobile, Vodafone
        - Focus: Network infrastructure, mobile services

        4. Semiconductors
        - Market Cap: ~$2-3 trillion
        - Key Players: TSMC, NVIDIA, Samsung
        - Focus: Chip design and fabrication

        3. Consumer Electronics
        - Market Cap: ~$1-2 trillion
        - Key Players: Apple, Samsung, Sony
        - Focus: Devices and wearables

        2. Enterprise IT Services
        - Market Cap: ~$1 trillion
        - Key Players: Accenture, IBM, Infosys
        - Focus: Consulting and outsourcing

        1. Software & Internet Services
        - Market Cap: ~$2-3 trillion
        - Key Players: Microsoft, Google, Amazon
        - Focus: Cloud and platforms
    """.trimIndent()

    @Test
    fun `screenshot case - refusal plus numbers in the prior turn still renders a chart`() {
        val block = WidgetRecovery.recover(refusal, "Show me a bar chart", sectorAnswer)
        assertNotNull("a chart must be recovered", block)
        assertEquals("chart", block!!.kind)

        val chart = widgetJson.decodeFromString<ChartWidget>(block.json)
        assertEquals("bar", chart.type)
        assertTrue("needs at least 4 sector bars", chart.labels.size >= 4)
        assertEquals(chart.labels.size, chart.series.first().data.size)
        // The sector names are the categories, not the repeated "Market Cap".
        assertTrue(chart.labels.any { it.contains("Telecommunications") })
        assertTrue(chart.labels.any { it.contains("Semiconductors") })
        assertTrue(chart.title.contains("from the answer above"))
    }

    @Test
    fun `labelled lines in the reply itself are charted`() {
        val reply = """
            Revenue by quarter:
            - Q1: ${'$'}120k
            - Q2: ${'$'}150k
            - Q3: ${'$'}180k
            - Q4: ${'$'}210k
        """.trimIndent()
        val block = WidgetRecovery.recover(reply, "plot this as a line chart", "")
        assertNotNull(block)
        val chart = widgetJson.decodeFromString<ChartWidget>(block!!.json)
        assertEquals("line", chart.type)
        assertEquals(listOf("Q1", "Q2", "Q3", "Q4"), chart.labels)
        assertEquals(4, chart.series.first().data.size)
    }

    @Test
    fun `markdown table is converted when the user asked for a chart`() {
        val reply = """
            | Region | Revenue |
            | --- | --- |
            | North | 120 |
            | South | 90 |
            | East | 150 |
        """.trimIndent()
        val block = WidgetRecovery.recover(reply, "show me a bar chart", "")
        assertNotNull(block)
        val chart = widgetJson.decodeFromString<ChartWidget>(block!!.json)
        assertEquals(listOf("North", "South", "East"), chart.labels)
    }

    @Test
    fun `pie asks produce one series per slice`() {
        val reply = "- Alpha: 40\n- Beta: 25\n- Gamma: 35"
        val block = WidgetRecovery.recover(reply, "make a pie chart", "")
        assertNotNull(block)
        val chart = widgetJson.decodeFromString<ChartWidget>(block!!.json)
        assertEquals("pie", chart.type)
        assertEquals(3, chart.series.size)
        assertEquals(listOf("Alpha", "Beta", "Gamma"), chart.series.map { it.name })
    }

    @Test
    fun `a table request produces a table widget`() {
        val reply = "- Alpha: 40\n- Beta: 25"
        val block = WidgetRecovery.recover(reply, "put that in a table", "")
        assertNotNull(block)
        assertEquals("table", block!!.kind)
        val table = widgetJson.decodeFromString<TableWidget>(block.json)
        assertEquals(listOf("Item", "Value"), table.columns)
        assertEquals(2, table.rows.size)
    }

    @Test
    fun `never invents numbers - one value is not enough`() {
        assertNull(WidgetRecovery.recover("Only one figure: 42.", "show me a chart", ""))
    }

    @Test
    fun `never invents numbers - empty context is not enough`() {
        assertNull(WidgetRecovery.recover(refusal, "show me a chart", ""))
    }

    @Test
    fun `no visual request means no recovery`() {
        assertNull(WidgetRecovery.recover(sectorAnswer, "which sector is biggest?", ""))
    }

    @Test
    fun `diagram requests are never hijacked into data charts`() {
        assertNull(WidgetRecovery.askOf("draw a flowchart of checkout"))
        assertNull(WidgetRecovery.askOf("make a mindmap for android"))
        assertNull(WidgetRecovery.recover(sectorAnswer, "draw a flowchart", ""))
    }

    @Test
    fun `existing widget fences disable recovery`() {
        val withWidget = "Here you go\n```chart\n{\"type\":\"bar\"}\n```"
        assertTrue(WidgetRecovery.hasWidgetFence(withWidget))
        assertTrue(!WidgetRecovery.hasWidgetFence("just prose with a ```code fence"))
    }

    @Test
    fun `false capability claims are stripped only on recovery`() {
        val cleaned = WidgetRecovery.stripFalseRefusal(refusal)
        assertTrue(!cleaned.contains("text-based AI", ignoreCase = true))
        assertTrue(!cleaned.contains("unable to create or display", ignoreCase = true))
        // Legitimate content survives.
        assertTrue(cleaned.contains("tallest bar", ignoreCase = true))
    }

    @Test
    fun `a genuine data gap is not treated as a capability claim`() {
        val honest = "I don't have the revenue figures for last quarter, so I can't chart them."
        assertEquals(honest, WidgetRecovery.stripFalseRefusal(honest))
    }
}
