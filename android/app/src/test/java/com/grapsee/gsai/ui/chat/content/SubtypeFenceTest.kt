package com.grapsee.gsai.ui.chat.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sub-types used as the fence language.
 *
 * Models write ```kanban, ```loan, ```gallery and ```pdf the same way they
 * write ```funnel: the fence names the shape, the payload carries the content.
 * Before this mapping every one of those became a code block, so a request for
 * a booking flow or a PDF preview ended as raw JSON on screen.
 */
class SubtypeFenceTest {

    private fun widgetOf(markdown: String): Block.WidgetBlock {
        val block = parseBlocks(markdown).single()
        assertTrue("expected a widget, got $block", block is Block.WidgetBlock)
        return block as Block.WidgetBlock
    }

    @Test
    fun `kanban maps to the app kind`() {
        val w = widgetOf(
            "```kanban\n{\"title\":\"Sprint\",\"columns\":[{\"title\":\"Todo\",\"items\":[{\"title\":\"a\"}]}]}\n```"
        )
        assertEquals("app", w.kind)
        assertTrue(w.json.contains("\"type\":\"kanban\""))
        assertNotNull(parseWidget(w.kind, w.json))
    }

    @Test
    fun `loan maps to the calc kind`() {
        val w = widgetOf(
            "```loan\n{\"fields\":[{\"key\":\"p\",\"label\":\"Principal\",\"value\":250000}],\"outputs\":[{\"label\":\"Payment\",\"expr\":\"p*0.01\"}]}\n```"
        )
        assertEquals("calc", w.kind)
        assertTrue(w.json.contains("\"type\":\"loan\""))
        assertNotNull(parseWidget(w.kind, w.json))
    }

    @Test
    fun `gallery maps to the media kind`() {
        val w = widgetOf("```gallery\n{\"items\":[{\"url\":\"https://x.test/a.jpg\"}]}\n```")
        assertEquals("media", w.kind)
        assertTrue(w.json.contains("\"type\":\"gallery\""))
        assertNotNull(parseWidget(w.kind, w.json))
    }

    @Test
    fun `pdf maps to the media kind`() {
        val w = widgetOf("```pdf\n{\"items\":[{\"url\":\"https://x.test/a.pdf\"}]}\n```")
        assertEquals("media", w.kind)
        assertTrue(w.json.contains("\"type\":\"pdf\""))
    }

    @Test
    fun `an explicit type wins over the fence`() {
        val w = widgetOf("```kanban\n{\"type\":\"calendar\",\"items\":[]}\n```")
        assertEquals("app", w.kind)
        assertTrue(w.json.contains("\"type\":\"calendar\""))
        assertTrue(!w.json.contains("kanban"))
    }

    @Test
    fun `a code fence is never a widget`() {
        val block = parseBlocks("```code\n{ \"const\": 1 }\n```").single()
        assertTrue("source code must stay code, got $block", block is Block.CodeBlock)
    }

    @Test
    fun `a search fence is a result list, not an app filter`() {
        assertEquals("media", widgetKindOf("search"))
    }

    @Test
    fun `an unknown language is still not a widget`() {
        assertNull(widgetKindOf("python"))
        assertNull(widgetKindOf(null))
    }

    @Test
    fun `a chart alias still wins over every subtype`() {
        assertEquals("chart", widgetKindOf("waterfall"))
        assertEquals("chart", widgetKindOf("gantt"))
    }
}

/**
 * Tone resolution for sub-typed cards.
 *
 * media/calc/app views pass their sub-type as the tag, so the family table has
 * to resolve it — otherwise every calculator and every board silently lost its
 * tint and looked like a plain table.
 */
class WidgetFamilyToneTest {

    @Test
    fun `app subtypes resolve to the app family`() {
        assertEquals("app", widgetFamily("kanban"))
        assertEquals(CardTone.Tinted, widgetTone("kanban"))
        assertEquals(CardTone.Tinted, widgetTone("booking"))
    }

    @Test
    fun `calc subtypes resolve to the calc family`() {
        assertEquals("calc", widgetFamily("mortgage"))
        assertEquals(CardTone.Tinted, widgetTone("mortgage"))
    }

    @Test
    fun `media subtypes resolve to the media family`() {
        assertEquals("media", widgetFamily("pdf"))
        assertEquals(CardTone.Tinted, widgetTone("pdf"))
    }

    @Test
    fun `plain kinds keep their own tone`() {
        assertEquals(CardTone.Hero, widgetTone("stat"))
        assertEquals(CardTone.Outline, widgetTone("timeline"))
        assertEquals(CardTone.Plain, widgetTone("table"))
        assertEquals(CardTone.Tinted, widgetTone("chart"))
    }
}
