package com.grapsee.gsai.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Recent Chats subtitle is the last message, not markdown source.
 *
 * The device capture showed "## The first three planets" and then the pipe row
 * of a table in the row; both are fixed here rather than left to the eye.
 */
class ChatPreviewLineTest {

    @Test
    fun `heading markers are not shown`() {
        assertEquals("The first three planets", previewLine("## The first three planets"))
    }

    @Test
    fun `table rows are skipped in favour of the first prose line`() {
        val message = """
            ## The first three planets

            | Planet | Moons | Orbit days |
            | :--- | :---: | ---: |
            | Mercury | 0 | 88 |
        """.trimIndent()
        assertEquals("The first three planets", previewLine(message))
    }

    @Test
    fun `inline emphasis and quotes are flattened`() {
        assertEquals("Hello world", previewLine("> Hello **world**"))
    }

    @Test
    fun `bullet markers are stripped`() {
        assertEquals("Mercury is closest", previewLine("- Mercury is closest"))
    }

    @Test
    fun `empty messages read as empty, never as a blank row`() {
        assertEquals("No messages yet", previewLine(null))
        assertEquals("No messages yet", previewLine("   "))
        assertEquals("No messages yet", previewLine("```kotlin\n```"))
    }
}
