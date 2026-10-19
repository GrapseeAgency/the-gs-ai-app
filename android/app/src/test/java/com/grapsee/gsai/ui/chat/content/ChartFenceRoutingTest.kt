package com.grapsee.gsai.ui.chat.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Chart sub-type fences and the widget card identity.
 *
 * Both of these were visible on a real device and both were MY bugs:
 *
 *  1. The model answered with ```funnel / ```gantt (its own fence language, a
 *     real shape it emits constantly). The router only knew ```chart, so the
 *     payload fell through to a CODE BLOCK — raw JSON on screen — and the
 *     recovery pass then drew a sample chart ("Item 1…4") on top of a payload
 *     that was perfectly good all along.
 *  2. Every widget shared one card background and the series palette cycled
 *     five theme tokens, so a five-slice pie keyed off `series.indices` (one
 *     series, five points) painted all five slices the SAME colour.
 *
 * These tests pin the routing, the type injection and the palette identity so
 * neither can silently come back.
 */
class ChartFenceRoutingTest {

    private val funnelPayload = """
        {"title":"Checkout funnel","labels":["Visit","Cart","Pay","Done"],
         "series":[{"name":"Users","data":[[0,1200],[1,540],[2,210],[3,160]]}]}
    """.trimIndent()

    @Test
    fun `funnel fence becomes a chart widget, not a code block`() {
        val blocks = parseBlocks("```funnel\n$funnelPayload\n```")
        assertEquals(1, blocks.size)
        val w = blocks.single() as Block.WidgetBlock
        assertEquals("chart", w.kind)
        val chart = parseWidget(w.kind, w.json) as Widget.Chart
        assertEquals("funnel", chart.w.type)
        assertEquals(4, chart.w.series.first().data.size)
    }

    @Test
    fun `gantt fence keeps its payload and its type`() {
        val json = """{"title":"Roadmap","labels":["Q1","Q2"],"series":[{"name":"A","data":[[0,1],[1,2]]}]}"""
        val w = parseBlocks("```gantt\n$json\n```").single() as Block.WidgetBlock
        assertEquals("chart", w.kind)
        assertEquals("gantt", (parseWidget(w.kind, w.json) as Widget.Chart).w.type)
    }

    @Test
    fun `stackedarea alias canonicalises to stackedArea`() {
        val json = """{"title":"T","series":[{"name":"A","data":[[0,1],[1,2]]}]}"""
        val w = parseBlocks("```stackedarea\n$json\n```").single() as Block.WidgetBlock
        assertEquals("stackedArea", (parseWidget(w.kind, w.json) as Widget.Chart).w.type)
    }

    @Test
    fun `an explicit type in the payload wins over the fence hint`() {
        val json = """{"type":"bar","title":"T","series":[{"name":"A","data":[[0,1],[1,2]]}]}"""
        val w = parseBlocks("```line\n$json\n```").single() as Block.WidgetBlock
        assertEquals("bar", (parseWidget(w.kind, w.json) as Widget.Chart).w.type)
    }

    @Test
    fun `every chart alias routes to the chart kind`() {
        CHART_LANGUAGE_ALIASES.forEach { alias ->
            assertEquals("alias $alias", "chart", widgetKindOf(alias))
        }
        // and the plain widget kinds still map to themselves
        WIDGET_LANGUAGES.forEach { kind -> assertEquals(kind, widgetKindOf(kind)) }
        assertNull(widgetKindOf("kotlin"))
        assertNull(widgetKindOf(null))
    }

    @Test
    fun `a non-json body under a chart alias stays code`() {
        val block = parseBlocks("```funnel\nnot json at all\n```").single()
        assertTrue("expected a code block, got $block", block is Block.CodeBlock)
    }

    @Test
    fun `an open alias fence is a widget skeleton, not raw json`() {
        val block = parseBlocks("```heatmap\n{\"title\":\"Grid\",").single()
        val w = block as Block.WidgetBlock
        assertTrue(w.open)
        assertEquals("chart", w.kind)
    }

    @Test
    fun `recovery never fires on a transcript that already has an alias fence`() {
        assertTrue(WidgetRecovery.hasWidgetFence("```funnel\n$funnelPayload\n```"))
        assertTrue(WidgetRecovery.hasWidgetFence("```gantt\n{}\n```"))
        assertTrue(WidgetRecovery.hasWidgetFence("```chart\n{}\n```"))
        // and still does not fire on prose
        assertTrue(!WidgetRecovery.hasWidgetFence("Just a paragraph about funnels."))
    }

    @Test
    fun `a real alias payload is not replaced by a sample chart`() {
        val reply = "Here is the funnel.\n\n```funnel\n$funnelPayload\n```\n"
        val recovered = WidgetRecovery.recover(reply, "show me a funnel chart", "")
        assertNull("recovery must not stack a sample on a real payload", recovered)
    }
}

/** Palette identity: colour is data, and data must be distinguishable. */
class WidgetPaletteTest {

    @Test
    fun `twelve hues are distinct and the cycle is stable`() {
        val hues = (0 until 12).map { WidgetPalette.at(it) }
        assertEquals(12, hues.distinct().size)
        assertEquals(WidgetPalette.at(0), WidgetPalette.at(12))
        assertEquals(WidgetPalette.at(3), WidgetPalette.at(-9))
    }

    @Test
    fun `core families do not share a hue`() {
        val families = listOf(
            "chart", "stat", "table", "compare", "progress", "timeline",
            "gauge", "quiz", "form", "carousel", "cards", "followups"
        )
        val hues = families.map { widgetHue(it) }
        assertEquals("every family needs its own face", families.size, hues.distinct().size)
    }

    @Test
    fun `families get different surface tones`() {
        assertEquals(CardTone.Tinted, widgetTone("chart"))
        assertEquals(CardTone.Hero, widgetTone("stat"))
        assertEquals(CardTone.Hero, widgetTone("gauge"))
        assertEquals(CardTone.Outline, widgetTone("progress"))
        assertEquals(CardTone.Outline, widgetTone("timeline"))
        assertEquals(CardTone.Plain, widgetTone("table"))
        assertNotEquals(widgetTone("chart"), widgetTone("stat"))
    }

    @Test
    fun `an unknown tag still gets a stable hue`() {
        assertEquals(widgetHue("brandnew"), widgetHue("brandnew"))
        assertEquals(widgetHue("brandnew"), widgetHue("BrandNew"))
    }
}
