package com.grapsee.gsai.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSuggestionsTest {

    @Test
    fun `no setup keeps the four-way breadth`() {
        val suggestions = chatSuggestionsFor(emptySet(), emptySet())
        assertEquals(4, suggestions.size)
        assertEquals(chatSuggestionDefaults, suggestions)
    }

    @Test
    fun `a chosen capability leads the empty state`() {
        val suggestions = chatSuggestionsFor(setOf("Write and fix code"), emptySet())
        assertEquals("Fix my code", suggestions.first().label)
        assertEquals(4, suggestions.size)
    }

    @Test
    fun `an interest can lead when no capability matches`() {
        val suggestions = chatSuggestionsFor(emptySet(), setOf("Design"))
        assertEquals("Sketch a layout", suggestions.first().label)
        assertEquals(4, suggestions.size)
    }

    @Test
    fun `capability outranks interest and the list stays distinct`() {
        val suggestions = chatSuggestionsFor(
            capabilities = setOf("Draft and write"),
            interests = setOf("Design", "Marketing")
        )
        assertEquals("Draft something", suggestions.first().label)
        assertEquals(4, suggestions.size)
        assertEquals(4, suggestions.map { it.prompt }.toSet().size)
    }

    @Test
    fun `every card carries a real prompt`() {
        for (suggestion in chatSuggestionPool) {
            assertTrue("${suggestion.label} has no prompt", suggestion.prompt.length > 40)
            assertTrue("${suggestion.label} is not reachable", suggestion.capabilities.isNotEmpty() || suggestion.interests.isNotEmpty())
        }
    }
}
