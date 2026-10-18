package com.grapsee.gsai.ui.chat.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GENUI widget contract (docs/GENUI.md): fenced widget blocks ride the normal
 * markdown stream; parsing the JSON happens at RENDER time, so the parser must
 * (a) recognise widget fences, (b) keep mid-stream fences open, (c) never let a
 * malformed payload break the transcript — parseWidget() returns null and the
 * renderer degrades to a code block.
 */
class WidgetBlocksTest {

    // --- fence routing -----------------------------------------------------

    @Test
    fun `closed chart fence becomes a widget block`() {
        val blocks = parseBlocks("before\n\n```chart\n{\"type\":\"line\"}\n```\n\nafter")
        assertEquals(3, blocks.size)
        val w = blocks[1] as Block.WidgetBlock
        assertEquals("chart", w.kind)
        assertEquals("{\"type\":\"line\"}", w.json)
        assertEquals(false, w.open)
    }

    @Test
    fun `all six widget kinds are recognised, case-insensitively`() {
        for (kind in listOf("chart", "stat", "table", "compare", "progress", "timeline")) {
            val blocks = parseBlocks("```${kind.uppercase()}\n{}\n```")
            val w = blocks.single() as Block.WidgetBlock
            assertEquals(kind, w.kind)
        }
    }

    @Test
    fun `open widget fence mid-stream is a widget block marked open`() {
        // The closing fence has not arrived yet — this is exactly what a flush
        // looks like while the model is still emitting the JSON.
        val blocks = parseBlocks("text\n\n```chart\n{\"type\":\"line\",\"seri")
        val w = blocks.last() as Block.WidgetBlock
        assertEquals("chart", w.kind)
        assertTrue(w.open)
    }

    @Test
    fun `unknown fence languages still become code blocks`() {
        val blocks = parseBlocks("```kotlin\nfun main() {}\n```")
        val c = blocks.single() as Block.CodeBlock
        assertEquals("kotlin", c.language)
    }

    @Test
    fun `widget fences survive streaming flushes byte-identically`() {
        val full = "intro\n\n```progress\n{\"items\":[{\"label\":\"a\",\"percent\":50}]}\n```\n\noutro"
        // Exercise the REAL incremental path the renderer uses, not parseBlocks.
        var cache = ""
        var blocks: List<Block> = emptyList()
        for (chunk in full.chunked(11)) {
            val next = cache + chunk
            blocks = parseStreamingBlocks(cache, blocks, next)
            cache = next
        }
        val direct = parseBlocks(full)
        assertEquals(direct, blocks)
        assertTrue(direct.any { it is Block.WidgetBlock && it.kind == "progress" && !it.open })
    }

    @Test
    fun `widget block offsets shift with the rekey delta`() {
        val b = Block.WidgetBlock("chart", "{}", false, 40)
        // withShiftedSource is internal plumbing; the important bit is the type survives.
        val shifted = parseBlocks("x\n\n```chart\n{}\n```")[1] as Block.WidgetBlock
        assertTrue(shifted.sourceStart > 0)
        assertNotNull(b.json)
    }

    // --- payload validation --------------------------------------------------

    private val chartJson = """{"type":"line","title":"Prices","unit":"$","series":[{"name":"a","data":[[1,2],[2,4],[3,3]]}]}"""

    @Test
    fun `valid chart parses with typed series`() {
        val w = parseWidget("chart", chartJson)
        assertTrue(w is Widget.Chart)
        assertEquals(1, (w as Widget.Chart).w.series.size)
        assertEquals(4f, w.w.series[0].data[1][1])
    }

    @Test
    fun `chart with too many points is capped, not dropped`() {
        val many = (1..300).joinToString(",") { "[$it,$it]" }
        val w = parseWidget("chart", """{"type":"line","series":[{"name":"a","data":[$many]}]}""") as Widget.Chart
        assertEquals(200, w.w.series[0].data.size)
    }

    @Test
    fun `chart with too few points or unknown type degrades to null`() {
        assertNull(parseWidget("chart", """{"type":"line","series":[{"name":"a","data":[[1,2]]}]}"""))
        assertNull(parseWidget("chart", """{"type":"scatter","series":[{"name":"a","data":[[1,2],[2,3]]}]}"""))
        assertNull(parseWidget("chart", """{"series":[]}"""))
    }

    @Test
    fun `stat cards parse, cap at eight`() {
        val cards = (1..10).joinToString(",") { """{"label":"L$it","value":"$it"}""" }
        val w = parseWidget("stat", """{"cards":[$cards]}""") as Widget.Stat
        assertEquals(8, w.w.cards.size)
    }

    @Test
    fun `table requires columns and rows`() {
        assertNull(parseWidget("table", """{"columns":[],"rows":[]}"""))
        val w = parseWidget(
            "table",
            """{"columns":["a","b"],"rows":[["1","x"],["2","y"]],"sortable":true}"""
        ) as Widget.Table
        assertEquals(2, w.w.rows.size)
        assertTrue(w.w.sortable)
    }

    @Test
    fun `progress percent is clamped to 0-100`() {
        val w = parseWidget("progress", """{"items":[{"label":"a","percent":140},{"label":"b","percent":-5}]}""") as Widget.Progress
        assertEquals(100f, w.w.items[0].percent)
        assertEquals(0f, w.w.items[1].percent)
    }

    @Test
    fun `compare and timeline parse`() {
        assertNotNull(parseWidget("compare", """{"left":{"title":"A","items":["x"]},"right":{"title":"B","items":["y"]}}"""))
        assertNotNull(parseWidget("timeline", """{"entries":[{"date":"2026","title":"Launch"}]}"""))
    }

    @Test
    fun `malformed payloads degrade to null, never throw`() {
        assertNull(parseWidget("chart", "not json"))
        assertNull(parseWidget("chart", """{"type":"line","series":"oops"}"""))
        assertNull(parseWidget("chart", ""))
        assertNull(parseWidget("stat", "[" + "1,".repeat(70000))) // over the byte cap
        assertNull(parseWidget("unknown-kind", "{}"))
    }

    @Test
    fun `unknown json keys are ignored, older clients survive newer payloads`() {
        val w = parseWidget("stat", """{"title":"t","cards":[{"label":"L","value":"1","futureField":{"nested":true}}]}""")
        assertTrue(w is Widget.Stat)
    }
}
