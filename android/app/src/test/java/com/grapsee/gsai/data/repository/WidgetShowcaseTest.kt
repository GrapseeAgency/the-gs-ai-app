package com.grapsee.gsai.data.repository

import com.grapsee.gsai.ui.chat.content.Block
import com.grapsee.gsai.ui.chat.content.parseBlocks
import com.grapsee.gsai.ui.chat.content.parseWidget
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The catalogue is only useful if every page it ships actually renders.
 *
 * The offline responder is the one place where no model validates the fences
 * before they reach the transcript, so the payloads are proven here against
 * the SAME parser the chat screen uses. A typo in one JSON object fails this
 * test instead of showing a code block on a phone with no connection.
 */
class WidgetShowcaseTest {

    private val pages = listOf(
        "Show a gallery of rendered components",
        "Show the chart catalogue",
        "Show the structural chart catalogue",
        "Show the form and quiz catalogue",
        "Show the app catalogue",
        "Show the media catalogue",
        "Show the 3D and canvas catalogue"
    )

    @Test
    fun `every catalogue page parses into rendered widgets`() {
        for (prompt in pages) {
            val reply = WidgetShowcase.replyFor(prompt)
            assertNotNull("no page for: $prompt", reply)

            val widgets = parseBlocks(reply!!).filterIsInstance<Block.WidgetBlock>()
            assertTrue("$prompt produced no widgets", widgets.size >= 3)

            for (block in widgets) {
                assertNull(
                    "$prompt -> ${block.kind} is not valid JSON a renderer accepts: ${block.json.take(120)}",
                    block.json.takeIf { parseWidget(block.kind, it) == null }
                )
            }
        }
    }

    @Test
    fun `followups point at pages that exist`() {
        for (prompt in pages) {
            val reply = WidgetShowcase.replyFor(prompt)!!
            val followups = parseBlocks(reply)
                .filterIsInstance<Block.WidgetBlock>()
                .filter { it.kind == "followups" }
            assertTrue("$prompt has no followups block", followups.isNotEmpty())

            val prompts = followups.flatMap { block ->
                Json.parseToJsonElement(block.json).jsonObject["prompts"]!!.jsonArray
                    .map { it.jsonPrimitive.content }
            }
            assertTrue("$prompt has no followup prompts", prompts.isNotEmpty())
            for (next in prompts) {
                assertNotNull("followup '$next' does not open a page", WidgetShowcase.replyFor(next))
            }
        }
    }

    @Test
    fun `ordinary prompts are not hijacked by the catalogue`() {
        assertNull(WidgetShowcase.replyFor("Write a short email to my landlord"))
        assertNull(WidgetShowcase.replyFor("What is the capital of Portugal?"))
    }

    @Test
    fun `chart page carries every series type the renderer names`() {
        val reply = WidgetShowcase.replyFor("Show the chart catalogue")!!
        val types = Regex("\\{\"type\":\"([a-zA-Z0-9]+)\"").findAll(reply)
            .map { it.groupValues[1] }
            .toSet()
        val expected = setOf(
            "bar", "hbar", "grouped", "stacked", "line", "area", "stackedArea",
            "pie", "donut", "scatter", "bubble", "radar", "funnel", "histogram",
            "boxplot", "waterfall", "gantt"
        )
        assertEquals("chart catalogue drifted", expected, types)
    }
}
