package com.grapsee.gsai.ui.orbs

import com.grapsee.gsai.data.chat.ChatStreamController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * PHASE 6 — the honest chat-stream → orb mapping, including the real
 * image-analysis wait. The orb may only show states the pipeline really has:
 * image requests waiting for the first token are a REAL phase now (the vision
 * model genuinely receives and analyses the image), so WORKING is mapped —
 * never SEARCHING/SOLVING/CONNECTING, which still have no real app state.
 */
class OrbStateMappingTest {

    private val streaming = ChatStreamController.Phase.Streaming

    // ---- image requests (PHASE 6) ----

    @Test
    fun `image request before first token maps to WORKING`() {
        assertEquals(OrbState.WORKING, orbStateForChatStream(streaming, "", requestHasImages = true))
        assertEquals(OrbState.WORKING, orbStateForChatStream(streaming, null, requestHasImages = true))
    }

    @Test
    fun `image request transitions to COMPOSING once tokens flow`() {
        assertEquals(
            OrbState.COMPOSING,
            orbStateForChatStream(streaming, "Reading the image…", requestHasImages = true)
        )
    }

    // ---- text-only requests (Phase 4 behaviour preserved) ----

    @Test
    fun `text request before first token maps to BREATHING`() {
        assertEquals(OrbState.BREATHING, orbStateForChatStream(streaming, "", requestHasImages = false))
        assertEquals(OrbState.BREATHING, orbStateForChatStream(streaming, null, requestHasImages = false))
    }

    @Test
    fun `text request with tokens maps to COMPOSING`() {
        assertEquals(OrbState.COMPOSING, orbStateForChatStream(streaming, "Hello", requestHasImages = false))
    }

    // ---- terminal phases never map (settled turn = no orb) ----

    @Test
    fun `non-streaming phases map to null regardless of images`() {
        for (phase in listOf(
            ChatStreamController.Phase.Finalizing,
            ChatStreamController.Phase.Done,
            ChatStreamController.Phase.Cancelled
        )) {
            assertNull(orbStateForChatStream(phase, "", requestHasImages = true))
            assertNull(orbStateForChatStream(phase, "partial text", requestHasImages = true))
            assertNull(orbStateForChatStream(phase, null, requestHasImages = false))
        }
    }

    @Test
    fun `null phase maps to null`() {
        assertNull(orbStateForChatStream(null, null, requestHasImages = true))
        assertNull(orbStateForChatStream(null, "text", requestHasImages = false))
    }

    // ---- whitespace-only buffer is still "no tokens yet" ----

    @Test
    fun `blank buffer still counts as pre-token`() {
        assertEquals(OrbState.WORKING, orbStateForChatStream(streaming, "  ", requestHasImages = true))
        assertEquals(OrbState.BREATHING, orbStateForChatStream(streaming, "  ", requestHasImages = false))
    }

    // ---- the fabricated states stay unmapped ----

    @Test
    fun `mapping never returns the unmapped catalogue states`() {
        val fabricated = setOf(
            OrbState.SEARCHING,
            OrbState.SOLVING,
            OrbState.CONNECTING,
            OrbState.WEAVING,
            OrbState.SHAPING
        )
        val results = listOf(
            orbStateForChatStream(streaming, "", requestHasImages = true),
            orbStateForChatStream(streaming, "tokens", requestHasImages = true),
            orbStateForChatStream(streaming, "", requestHasImages = false),
            orbStateForChatStream(streaming, "tokens", requestHasImages = false)
        )
        for (state in results.filterNotNull()) {
            check(state !in fabricated) { "fabricated orb state mapped: $state" }
        }
    }
}
