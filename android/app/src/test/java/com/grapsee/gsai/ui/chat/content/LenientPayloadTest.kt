package com.grapsee.gsai.ui.chat.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decoder has to accept what models actually write.
 *
 * A strict kotlinx parser rejects a trailing comma, a `//` note and an unquoted
 * key. Each of those used to turn a good payload into a code block, which the
 * user reads as "the model is broken". The backend repairs payloads before it
 * persists them, but the client also renders transcripts written by older
 * builds and payloads that arrive while streaming, so it must be forgiving by
 * itself.
 */
class LenientPayloadTest {

    @Test
    fun `a trailing comma still parses`() {
        val w = parseWidget("chart", """{"type":"bar","series":[{"name":"v","data":[1,2]},]}""")
        assertNotNull(w)
        assertEquals(2, (w as Widget.Chart).w.series.first().data.size)
    }

    @Test
    fun `a comment still parses`() {
        val w = parseWidget(
            "table",
            """
            {
              "columns": ["Brand", "Price"], // the only columns
              "rows": [["A", "120"]] /* one row */
            }
            """.trimIndent()
        )
        assertNotNull(w)
        assertEquals(2, (w as Widget.Table).w.columns.size)
    }

    @Test
    fun `an unquoted key still parses`() {
        val w = parseWidget("stat", """{title:"KPI",cards:[{label:"Revenue",value:"120"}]}""")
        assertNotNull(w)
        assertEquals("Revenue", (w as Widget.Stat).w.cards.first().label)
    }

    /**
     * The bug this file exists for: a URL contains `//`, and a regex-based
     * comment stripper deleted the rest of the line — so EVERY payload with a
     * link failed as invalid JSON, on the server and in every repair path.
     */
    @Test
    fun `a url keeps its double slash`() {
        val w = parseWidget(
            "media",
            """{"type":"image","items":[{"url":"https://cdn.test/a.jpg","thumb":"http://x.test/b.png"}]}"""
        )
        assertNotNull(w)
        val item = (w as Widget.Media).w.items.first()
        assertEquals("https://cdn.test/a.jpg", item.url)
        assertEquals("http://x.test/b.png", item.thumb)
    }
}

/**
 * Model-authored HTML/SVG must be able to use the app's theme.
 *
 * Before this, a ```html block received two colours and a transparent
 * background, so every bespoke view invented its own greys and looked pasted
 * on. The document now gets the full token set as CSS custom properties.
 */
class DocTokensTest {

    @Test
    fun `the document carries every theme variable`() {
        val doc = buildDocument("html", "<p>hi</p>", DocTokens.of("#14171C", "#4C6FFF"))
        assertNotNull(doc)
        val css = doc!!
        listOf(
            "--gs-text:", "--gs-text-2:", "--gs-text-3:",
            "--gs-surface:", "--gs-raised:", "--gs-border:",
            "--gs-accent:", "--gs-hue-1:", "--gs-hue-12:",
            "--gs-radius:", "--gs-space:", "--gs-font:"
        ).forEach { assertTrue("missing $it", css.contains(it)) }
    }

    @Test
    fun `dark mode sets color-scheme dark`() {
        val dark = DocTokens.of("#F2F5F9", "#4C6FFF").copy(dark = true)
        val doc = buildDocument("html", "<p>hi</p>", dark)!!
        assertTrue(doc.contains("color-scheme:dark"))
    }

    @Test
    fun `light mode sets color-scheme light`() {
        val doc = buildDocument("html", "<p>hi</p>", DocTokens.of("#14171C", "#4C6FFF"))!!
        assertTrue(doc.contains("color-scheme:light"))
    }

    @Test
    fun `an svg fence renders as an svg document`() {
        val block = parseBlocks("```svg\n<svg viewBox=\"0 0 10 10\"><circle cx=\"5\" cy=\"5\" r=\"4\"/></svg>\n```").single()
        assertTrue("expected an html block, got $block", block is Block.HtmlBlock)
        assertEquals("svg", (block as Block.HtmlBlock).mode)
        val doc = buildDocument("svg", block.code, DocTokens.of("#14171C", "#4C6FFF"))
        assertNotNull(doc)
        assertTrue(doc!!.contains("<svg viewBox"))
        assertTrue(doc.contains("--gs-accent"))
    }

    @Test
    fun `an svg payload is never treated as a widget`() {
        val block = parseBlocks("```svg\n<svg></svg>\n```").single()
        assertTrue(block is Block.HtmlBlock)
    }
}
