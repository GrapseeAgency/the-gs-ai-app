package com.grapsee.gsai.ui.orbs

import com.grapsee.gsai.data.chat.ChatStreamController
import com.grapsee.gsai.ui.voice.VoicePhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The luminous 12-state product contract (OrbPhase.kt), tested as PRESENTATION
 * only — never geometry. The golden-vector test owns the nine dot fields; this
 * test owns the twelve phases that light them and the honest resolvers that pick
 * a phase from real application signals. A phase may only borrow an existing
 * geometry; it may not invent a new one.
 */
class OrbPhaseTest {

    @Test
    fun `the product vocabulary is exactly twelve phases`() {
        assertEquals(12, OrbPhase.entries.size)
    }

    @Test
    fun `every phase projects onto one of the nine generated geometries`() {
        val nine = OrbState.entries.toSet()
        for (phase in OrbPhase.entries) {
            assertTrue("$phase geometry not in the nine", nine.contains(phase.geometry))
        }
    }

    @Test
    fun `every phase carries an in-range luminous profile`() {
        for (phase in OrbPhase.entries) {
            val p = phase.luminance
            assertTrue("${phase} glowAlpha out of range", p.glowAlpha in 0f..1f)
            assertTrue("${phase} coreAlpha out of range", p.coreAlpha in 0f..1f)
            assertTrue("${phase} glowAlpha must be positive", p.glowAlpha > 0f)
        }
    }

    @Test
    fun `alarm and mute never pulse`() {
        // Motion must not playful-ize a failure or an inert connection.
        assertEquals(false, OrbPhase.ERROR.luminance.pulse)
        assertEquals(false, OrbPhase.PAUSED.luminance.pulse)
        assertEquals(false, OrbPhase.OFFLINE.luminance.pulse)
    }

    @Test
    fun `the nine geometries each default to a luminous phase`() {
        for (state in OrbState.entries) {
            // luminousProfileFor must never blow up for any real geometry.
            val p = luminousProfileFor(state)
            assertTrue("${state} default glow out of range", p.glowAlpha in 0f..1f)
        }
    }

    @Test
    fun `a settled turn and a null phase show no orb`() {
        assertNull(orbPhaseForChatStream(ChatStreamController.Phase.Done, "text"))
        assertNull(orbPhaseForChatStream(ChatStreamController.Phase.Finalizing, ""))
        assertNull(orbPhaseForChatStream(null, null))
    }

    @Test
    fun `chat phases stay honest and agree with the geometry mapping where it matters`() {
        val streaming = ChatStreamController.Phase.Streaming
        // tokens flowing -> STREAMING, whose geometry is the same COMPOSING the
        // tested OrbState mapping returns for a flowing buffer.
        assertEquals(OrbPhase.STREAMING, orbPhaseForChatStream(streaming, "Hello"))
        assertEquals(OrbState.COMPOSING, OrbPhase.STREAMING.geometry)
        // quiet pre-token text turn -> THINKING (BREATHING), matching OrbState.
        assertEquals(OrbPhase.THINKING, orbPhaseForChatStream(streaming, "", requestHasAttachments = false))
        assertEquals(OrbState.BREATHING, OrbPhase.THINKING.geometry)
        // terminal product signals win over the live phase read.
        assertEquals(OrbPhase.SUCCESS, orbPhaseForChatStream(streaming, "done", completed = true))
        assertEquals(OrbPhase.ERROR, orbPhaseForChatStream(streaming, "", errored = true))
    }

    @Test
    fun `voice distinguishes quiet listening from a live capture level`() {
        assertEquals(OrbPhase.LISTENING, orbPhaseForVoice(VoicePhase.Listening, amplitude = 0f))
        assertEquals(OrbPhase.RECORDING, orbPhaseForVoice(VoicePhase.Listening, amplitude = 0.5f))
        assertEquals(OrbPhase.REASONING, orbPhaseForVoice(VoicePhase.Processing))
        assertNull(orbPhaseForVoice(VoicePhase.Idle))
        assertNull(orbPhaseForVoice(VoicePhase.Error))
    }
}
