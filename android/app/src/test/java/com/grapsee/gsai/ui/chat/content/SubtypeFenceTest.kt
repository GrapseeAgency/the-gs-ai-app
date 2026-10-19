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

    // THE LIVE PDF PAYLOAD (probe 2026-10-20): the server answers a ```pdf
    // request with {"title":…,"pages":[{"page":1,"text":…}]} — no type, no
    // item URLs. The parser used to drop it ("No document").
    @Test
    fun `a live pdf page body parses as a document`() {
        val w = widgetOf(
            "```pdf\n{\"title\":\"Lease agreement\",\"pages\":[" +
                "{\"page\":1,\"text\":\"LEASE AGREEMENT between the parties.\"}," +
                "{\"page\":2,\"text\":\"RENT is due on the first day.\"}]}\n```"
        )
        assertEquals("media", w.kind)
        assertTrue(w.json.contains("\"type\":\"pdf\""))
        val parsed = parseWidget(w.kind, w.json)
        assertNotNull("the live pdf payload must parse", parsed)
        val media = (parsed as Widget.Media).w
        assertEquals("doc", media.type)
        assertEquals(2, media.pages.size)
        assertEquals(1, media.pages[0].page)
        assertTrue(media.pages[1].text.contains("RENT"))
    }

    @Test
    fun `a bare pages payload is promoted to a document`() {
        val json = "{\"title\":\"Memo\",\"pages\":[{\"page\":1,\"text\":\"Page one body.\"}]}"
        assertEquals("media", inferWidgetKind(json))
        assertNotNull(parseWidget("media", json))
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

/**
 * Numeric-column detection for tables.
 *
 * The contract tells the model that the renderer right-aligns numbers. It did
 * not, so every price column was ragged. Detection is deliberately tolerant of
 * currency, signs, thousands separators and short units, and deliberately
 * strict about anything that starts with a letter ("Q1" is a label).
 */
class NumericColumnTest {

    @Test
    fun `plain and formatted numbers are numeric`() {
        listOf("120", "-3.5", "$1,200", "12%", "1.2 km", "+8", "0.04").forEach {
            assertTrue("$it should be numeric", NUMERIC_CELL.matches(it))
        }
    }

    @Test
    fun `labels and words are not numeric`() {
        listOf("Q1", "Revenue", "N/A", "", "12 units sold", "Apple").forEach {
            assertTrue("$it should not be numeric", !NUMERIC_CELL.matches(it))
        }
    }
}
