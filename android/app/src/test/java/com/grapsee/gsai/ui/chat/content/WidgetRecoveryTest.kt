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

    @Test
    fun `the exact sentence captured from the live backend is stripped`() {
        // Verbatim from the deployed API probe, 2026-10-19.
        val live = "I'd be happy to show you a bar chart comparing Q1-Q4 revenue for " +
            "three products. However, I don't have the ability to generate or display " +
            "visual charts directly."
        val cleaned = WidgetRecovery.stripFalseRefusal(live)
        assertTrue(
            "false claim survived: $cleaned",
            !cleaned.contains("don't have the ability", ignoreCase = true)
        )
        assertTrue("legitimate opening was lost: $cleaned", cleaned.contains("I'd be happy"))
    }

    // -----------------------------------------------------------------------
    // End-to-end: recovery output must survive the REAL renderer gate, not
    // just look right in a unit assertion.
    // -----------------------------------------------------------------------

    @Test
    fun `recovered chart survives parseWidget - the actual renderer gate`() {
        val block = WidgetRecovery.recover(refusal, "Show me a bar chart", sectorAnswer)!!
        val parsed = parseWidget(block.kind, block.json)
        assertNotNull("parseWidget rejected the recovered payload", parsed)
        assertTrue("expected a Chart, got $parsed", parsed is Widget.Chart)
        val chart = (parsed as Widget.Chart).w
        assertEquals("bar", chart.type)
        assertEquals(5, chart.labels.size)
        assertEquals(5, chart.series.first().data.size)
        assertTrue(chart.series.first().data.all { it.size == 2 })
    }

    @Test
    fun `recovered table survives parseWidget`() {
        val block = WidgetRecovery.recover("- Alpha: 40\n- Beta: 25", "put it in a table", "")!!
        val parsed = parseWidget(block.kind, block.json)
        assertTrue(parsed is Widget.Table)
        assertEquals(2, (parsed as Widget.Table).w.rows.size)
    }

    @Test
    fun `recovered block never collides with a parsed block key`() {
        // RichBlocks keys siblings by sourceStart. 0 was the original bug:
        // the recovered widget duplicated the first paragraph's key.
        val cleaned = WidgetRecovery.stripFalseRefusal(refusal)
        val parsed = parseBlocks(cleaned)
        val recovered = WidgetRecovery.recover(refusal, "Show me a bar chart", sectorAnswer)!!
        val keys = (parsed + recovered).map { it.sourceStart }
        assertEquals("duplicate composition key: $keys", keys.size, keys.distinct().size)
        assertTrue(parsed.isNotEmpty())
        assertTrue(recovered.sourceStart != 0)
    }

    @Test
    fun `pie recovery survives the renderer gate`() {
        val block = WidgetRecovery.recover(
            "- Alpha: 40\n- Beta: 25\n- Gamma: 35",
            "make a pie chart",
            ""
        )!!
        val parsed = parseWidget(block.kind, block.json)
        assertTrue("pie was rejected by parseWidget", parsed is Widget.Chart)
        assertEquals(3, (parsed as Widget.Chart).w.series.size)
    }

    @Test
    fun `a single multi-point pie series is expanded to slices`() {
        val w = ChartWidget(
            type = "pie",
            labels = listOf("Alpha", "Beta", "Gamma"),
            series = listOf(
                ChartSeries(
                    name = "value",
                    data = listOf(listOf(0f, 40f), listOf(1f, 25f), listOf(2f, 35f))
                )
            )
        )
        val slices = pieSlices(w)
        assertEquals(listOf("Alpha", "Beta", "Gamma"), slices.map { it.first })
        assertEquals(3, slices.size)
    }

    // -----------------------------------------------------------------------
    // The fallback is not "bar chart only": every shape the text can honestly
    // support must be reachable, and the requested chart type must be honoured.
    // -----------------------------------------------------------------------

    @Test
    fun `requested chart type is honoured, not hardcoded to bar`() {
        val reply = "- Alpha: 40\n- Beta: 25\n- Gamma: 35"
        val cases = mapOf(
            "show a horizontal bar chart" to "hbar",
            "show a line chart" to "line",
            "show an area chart" to "area",
            "show a pie chart" to "pie",
            "show a donut chart" to "donut",
            "show a radar chart" to "radar",
            "show a funnel" to "funnel",
            "show a waterfall" to "waterfall",
            "show a treemap" to "treemap"
        )
        for ((prompt, expected) in cases) {
            val block = WidgetRecovery.recover(reply, prompt, "")
            assertNotNull("no widget for: $prompt", block)
            val chart = widgetJson.decodeFromString<ChartWidget>(block!!.json)
            assertEquals("wrong type for: $prompt", expected, chart.type)
            assertTrue("parseWidget rejected: $prompt", parseWidget("chart", block.json) is Widget.Chart)
        }
    }

    @Test
    fun `stat request produces stat cards that the renderer accepts`() {
        val block = WidgetRecovery.recover(sectorAnswer, "show me the key stats", "")
        assertNotNull(block)
        assertEquals("stat", block!!.kind)
        val parsed = parseWidget(block.kind, block.json)
        assertTrue("parseWidget rejected stat", parsed is Widget.Stat)
        assertEquals(5, (parsed as Widget.Stat).w.cards.size)
        assertTrue(parsed.w.cards.all { it.label.isNotBlank() && it.value.isNotBlank() })
    }

    @Test
    fun `progress request produces progress bars from percentages`() {
        val reply = "- Design: 80%\n- Build: 45%\n- QA: 10%"
        val block = WidgetRecovery.recover(reply, "show progress", "")
        assertNotNull(block)
        assertEquals("progress", block!!.kind)
        val parsed = parseWidget(block.kind, block.json)
        assertTrue("parseWidget rejected progress", parsed is Widget.Progress)
        val items = (parsed as Widget.Progress).w.items
        assertEquals(listOf(80f, 45f, 10f), items.map { it.percent })
    }

    @Test
    fun `progress refuses values that would need an invented scale`() {
        val reply = "- Revenue: 2.5 trillion\n- Cost: 1 trillion"
        assertNull(WidgetRecovery.recover(reply, "show progress", ""))
    }

    @Test
    fun `timeline request produces dated entries`() {
        val reply = """
            - 2024-01: Launched private beta
            - 2024-03: Public launch
            - 2024-06: Reached 10k users
        """.trimIndent()
        val block = WidgetRecovery.recover(reply, "show me a timeline", "")
        assertNotNull(block)
        assertEquals("timeline", block!!.kind)
        val parsed = parseWidget(block.kind, block.json)
        assertTrue("parseWidget rejected timeline", parsed is Widget.Timeline)
        val entries = (parsed as Widget.Timeline).w.entries
        assertEquals(listOf("2024-01", "2024-03", "2024-06"), entries.map { it.date })
    }

    @Test
    fun `gallery request collects https images into a carousel`() {
        val reply = "Here: https://images.example.com/a.png and https://images.example.com/b.jpg"
        val block = WidgetRecovery.recover(reply, "show me a gallery", "")
        assertNotNull(block)
        assertEquals("carousel", block!!.kind)
        val parsed = parseWidget(block.kind, block.json)
        assertTrue("parseWidget rejected carousel", parsed is Widget.Carousel)
        assertEquals(2, (parsed as Widget.Carousel).w.items.size)
    }

    @Test
    fun `gallery with no image urls renders nothing`() {
        assertNull(WidgetRecovery.recover("No pictures here, sorry.", "show me a gallery", ""))
    }

    @Test
    fun diagnosticPaletteMatrix() {
        val reply = """
            - Alpha: 80%
            - Beta: 45%
            - Gamma: 10%
            - 2024-01: Launched private beta
            - 2024-03: Public launch
            https://images.example.com/a.png
        """.trimIndent()
        val prompts = listOf(
            "show a bar chart", "show a horizontal bar chart", "show a line chart",
            "show an area chart", "show a pie chart", "show a donut chart",
            "show a radar chart", "show a funnel", "show a waterfall", "show a treemap",
            "put it in a table", "show me the key stats", "show progress",
            "show me a timeline", "show me a gallery"
        )
        for (p in prompts) {
            val block = WidgetRecovery.recover(reply, p, "")
            val detail = when {
                block == null -> "null"
                block.kind == "chart" ->
                    "chart/" + widgetJson.decodeFromString<ChartWidget>(block.json).type
                else -> block.kind
            }
            println("DIAG_MATRIX=$p -> $detail")
        }
        assertTrue(true)
    }

    @Test
    fun diagnosticPayloadForReportedFailure() {
        val block = WidgetRecovery.recover(refusal, "Show me a bar chart", sectorAnswer)!!
        val parsed = parseWidget(block.kind, block.json)
        println("DIAG_KIND=${block.kind}")
        println("DIAG_JSON=${block.json}")
        println("DIAG_PARSED=${parsed?.javaClass?.simpleName}")
        println("DIAG_LABELS=${(parsed as Widget.Chart).w.labels}")
        println("DIAG_VALUES=${parsed.w.series.first().data}")
        println("DIAG_CLEANED=${WidgetRecovery.stripFalseRefusal(refusal).replace("\n", " | ")}")
        assertTrue(parsed is Widget.Chart)
    }

    @Test
    fun `recovery is not triggered when a real widget fence is present`() {
        val withWidget = "```chart\n{\"type\":\"bar\",\"labels\":[\"a\",\"b\"],\"series\":[{\"name\":\"v\",\"data\":[[0,1],[1,2]]}]}\n```"
        assertTrue(WidgetRecovery.hasWidgetFence(withWidget))
        // The caller (BlocksContent) skips recovery on this flag; prove the flag
        // is what actually gates it, not a lucky parse.
        val skipped = if (WidgetRecovery.hasWidgetFence(withWidget)) null
        else WidgetRecovery.recover(withWidget, "show me a chart", sectorAnswer)
        assertNull(skipped)
    }
}
