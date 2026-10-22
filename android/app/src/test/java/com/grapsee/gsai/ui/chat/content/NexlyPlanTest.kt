package com.grapsee.gsai.ui.chat.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Nexly timetable contract on the Android side. The model emits one fenced
 * `plan` payload; the parser must turn it into a first-class plan card, never
 * raw JSON or a markdown table, and the chat shell must be able to tell that a
 * settled reply owns a timetable (so the Thinking card and the footer timestamp
 * do not disappear/duplicate).
 */
class NexlyPlanTest {

    private val wire = """
        Got it! Here is your day.

        ```plan
        {
          "title": "Here's your personalized plan for tomorrow:",
          "rows": [
            { "time": "8:00 – 8:30 AM", "icon": "sunrise", "text": "Morning routine + light exercise" },
            { "time": "9:00 – 11:00 AM", "icon": "book-2", "text": "Classes" },
            { "time": "1:00 – 2:00 PM", "icon": "tools-kitchen-2", "text": "Lunch + short break" }
          ],
          "closing": "You got this, Arafat!",
          "closingIcon": "flame",
          "timestamp": "9:45 PM"
        }
        ```
    """.trimIndent()

    @Test
    fun `a closed plan fence becomes a first-class plan block`() {
        val blocks = parseBlocks(wire)
        assertEquals(2, blocks.size)
        assertTrue(blocks.first() is Block.Paragraph)
        val plan = blocks.filterIsInstance<Block.PlanBlock>().single()
        assertEquals(3, plan.plan.rows.size)
        assertEquals("8:00 – 8:30 AM", plan.plan.rows.first().time)
        assertEquals("book-2", plan.plan.rows[1].icon)
        assertEquals("flame", plan.plan.closingIcon)
        assertEquals("9:45 PM", plan.plan.timestamp)
        assertFalse(plan.open)
    }

    @Test
    fun `a json-wrapped plan is promoted, a label-only rows payload is not`() {
        val jsonPlan = """{"rows":[{"time":"9:00 AM","icon":"book-2","text":"Classes"}]}"""
        val block = parseBlocks("```json\n$jsonPlan\n```").single()
        assertTrue("expected a plan, got $block", block is Block.PlanBlock)

        val labelRows = """{"rows":[{"label":"Revenue","value":10}]}"""
        assertFalse(parseBlocks("```json\n$labelRows\n```").single() is Block.PlanBlock)
    }

    @Test
    fun `inference only claims timed rows`() {
        assertEquals("plan", inferWidgetKind("""{"rows":[{"time":"9:00","text":"Class"}]}"""))
        assertNull(inferWidgetKind("""{"rows":[{"label":"Revenue","value":10}]}"""))
    }

    @Test
    fun `wrappers and common row synonyms parse`() {
        val parsed = parseNexlyPlanData(
            """{
              "schedule": {
                "heading": "Sunday",
                "items": [
                  {"start":"8:00 AM","activity":"Run","icon":"run"},
                  {"from":"9:00 AM","task":"Study","icon":"study"}
                ],
                "footer":"Go get it",
                "timestamp":"8:00 PM"
              }
            }"""
        )
        assertNotNull(parsed)
        val plan = parsed!!
        assertEquals("Sunday", plan.title)
        assertEquals(2, plan.rows.size)
        assertEquals("Run", plan.rows.first().text)
        assertEquals("book-2", plan.rows[1].icon)
        assertEquals("Go get it", plan.closing)
    }

    @Test
    fun `an incomplete plan payload has no card`() {
        assertNull(parseNexlyPlanData("""{"rows":[{"time":"9:00"}]}"""))
        assertNull(parseNexlyPlanData("""{"rows":[{"text":"Class"}]}"""))
    }

    @Test
    fun `emoji inside a plan becomes icon tokens, never a pictograph`() {
        assertEquals("You got this :flame:", cleanPlanText("You got this 💪", 40))
        assertFalse(Regex("\\p{So}").containsMatchIn(cleanPlanText("Classes 📚", 40)))
    }

    @Test
    fun `natural timetable fence names become plans`() {
        val json = """{"rows":[{"time":"8:00","icon":"sunrise","text":"Run"}]}"""
        for (language in listOf("plan", "schedule", "timetable", "routine", "day")) {
            val block = parseBlocks("```$language\n$json\n```").single()
            assertTrue("$language fence must be a plan, got $block", block is Block.PlanBlock)
        }
    }

    @Test
    fun `the reference palette survives an omitted tone`() {
        assertEquals("amber", NexlyIcons.toneFor("sunrise"))
        assertEquals("lavender", NexlyIcons.toneFor("book-2"))
        assertEquals("blue", NexlyIcons.toneFor("device-laptop"))
        assertEquals("green", NexlyIcons.toneFor("tools-kitchen-2"))
        assertEquals("pink", NexlyIcons.toneFor("run"))
        assertEquals("lilac", NexlyIcons.toneFor("moon"))
    }

    @Test
    fun `the shell detects both fenced and promoted plans`() {
        assertTrue(containsNexlyPlan(wire))
        assertTrue(containsNexlyPlan("""{"rows":[{"time":"9:00","text":"Class"}]}"""))
        assertFalse(containsNexlyPlan("A normal answer with no timetable."))
    }
}
