package com.grapsee.gsai.ui.orbs

import com.grapsee.gsai.data.chat.ChatStreamController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * PHASE 6/7 — the honest chat-stream → orb mapping. The orb may only show
 * states the pipeline really has:
 *  - image requests wait on real vision analysis before the first token;
 *  - document requests (PDF/TXT/MD/CSV) wait on real server-side extraction
 *    before the first token;
 *  both are genuine work, so WORKING is mapped — never SEARCHING/SOLVING/
 * CONNECTING/WEAVING/SHAPING, which still have no real app state.
 */
class OrbStateMappingTest {

    private val streaming = ChatStreamController.Phase.Streaming

    // ---- attachment requests: images (PHASE 6) and documents (PHASE 7) ----

    @Test
    fun `image request before first token maps to WORKING`() {
        assertEquals(OrbState.WORKING, orbStateForChatStream(streaming, "", requestHasAttachments = true))
        assertEquals(OrbState.WORKING, orbStateForChatStream(streaming, null, requestHasAttachments = true))
    }

    @Test
    fun `image request transitions to COMPOSING once tokens flow`() {
        assertEquals(
            OrbState.COMPOSING,
            orbStateForChatStream(streaming, "Reading the image…", requestHasAttachments = true)
        )
    }

    @Test
    fun `document request before first token maps to WORKING`() {
        // PHASE 7 — server-side extraction of a PDF/TXT/MD/CSV attachment is
        // real pre-first-token work; the orb must show it, not "Thinking…".
        assertEquals(OrbState.WORKING, orbStateForChatStream(streaming, "", requestHasAttachments = true))
    }

    // ---- text-only requests (Phase 4 behaviour preserved) ----

    @Test
    fun `text request before first token maps to BREATHING`() {
        assertEquals(OrbState.BREATHING, orbStateForChatStream(streaming, "", requestHasAttachments = false))
        assertEquals(OrbState.BREATHING, orbStateForChatStream(streaming, null, requestHasAttachments = false))
    }

    @Test
    fun `text request with tokens maps to COMPOSING`() {
        assertEquals(OrbState.COMPOSING, orbStateForChatStream(streaming, "Hello", requestHasAttachments = false))
    }

    // ---- terminal phases never map (settled turn = no orb) ----

    @Test
    fun `non-streaming phases map to null regardless of attachments`() {
        for (phase in listOf(
            ChatStreamController.Phase.Finalizing,
            ChatStreamController.Phase.Done,
            ChatStreamController.Phase.Cancelled
        )) {
            assertNull(orbStateForChatStream(phase, "", requestHasAttachments = true))
            assertNull(orbStateForChatStream(phase, "partial text", requestHasAttachments = true))
            assertNull(orbStateForChatStream(phase, null, requestHasAttachments = false))
        }
    }

    @Test
    fun `null phase maps to null`() {
        assertNull(orbStateForChatStream(null, null, requestHasAttachments = true))
        assertNull(orbStateForChatStream(null, "text", requestHasAttachments = false))
    }

    // ---- whitespace-only buffer is still "no tokens yet" ----

    @Test
    fun `blank buffer still counts as pre-token`() {
        assertEquals(OrbState.WORKING, orbStateForChatStream(streaming, "  ", requestHasAttachments = true))
        assertEquals(OrbState.BREATHING, orbStateForChatStream(streaming, "  ", requestHasAttachments = false))
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
            orbStateForChatStream(streaming, "", requestHasAttachments = true),
            orbStateForChatStream(streaming, "tokens", requestHasAttachments = true),
            orbStateForChatStream(streaming, "", requestHasAttachments = false),
            orbStateForChatStream(streaming, "tokens", requestHasAttachments = false)
        )
        for (state in results.filterNotNull()) {
            check(state !in fabricated) { "fabricated orb state mapped: $state" }
        }
    }
}
