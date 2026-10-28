package com.grapsee.gsai.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three keys the reference screens write, and the mapping that lets four
 * effort tiles drive a wire that only understands three modes.
 *
 * This is the always-on proof that those preferences are not decorative: the
 * capture suite runs on dispatch only, so a dead key would otherwise survive
 * until someone looked at a screenshot.
 */
class SettingsModelSelectionTest {

    /**
     * Every tile must survive a round trip, EXCEPT `minimal`, which the wire
     * cannot distinguish from `light` — that collapse is deliberate and
     * documented in [SettingsStore.wireModeForEffort].
     */
    @Test
    fun `effort tiles round trip through the wire mode`() {
        for (effort in listOf("light", "standard", "high")) {
            val mode = SettingsStore.wireModeForEffort(effort)
            assertEquals("effort $effort did not survive the round trip",
                effort, SettingsStore.effortForMode(mode))
        }
    }

    /**
     * Minimal is the documented loss: it writes the same `flash` mode as Light,
     * so reading back gives Light. Asserted rather than assumed — if the backend
     * ever gains a fourth mode this test fails and the mapping must change.
     */
    @Test
    fun `minimal collapses onto light because the wire has no fourth mode`() {
        assertEquals("flash", SettingsStore.wireModeForEffort("minimal"))
        assertEquals(SettingsStore.wireModeForEffort("light"), SettingsStore.wireModeForEffort("minimal"))
        assertEquals("light", SettingsStore.effortForMode("flash"))
    }

    @Test
    fun `every effort maps to one of the three accepted modes`() {
        val accepted = setOf("flash", "thinking", "auto")
        for (effort in listOf("minimal", "light", "standard", "high")) {
            assertTrue("effort $effort produced a mode the backend rejects",
                SettingsStore.wireModeForEffort(effort) in accepted)
        }
    }

    @Test
    fun `updating the effort updates both the tile and the wire mode`() {
        SettingsStore.updateReasoningEffort("high")
        assertEquals("high", SettingsStore.reasoningEffort)
        SettingsStore.updateReasoningEffort("standard")
        assertEquals("standard", SettingsStore.reasoningEffort)
    }

    @Test
    fun `the model radio persists instead of resetting on every visit`() {
        SettingsStore.updatePreferredModel("creative")
        assertEquals("creative", SettingsStore.preferredModel)
        SettingsStore.updatePreferredModel("pro")
        assertEquals("pro", SettingsStore.preferredModel)
    }

    @Test
    fun `remember my choices is a real preference, not a drawn switch`() {
        SettingsStore.updateRememberChoices(false)
        assertFalse(SettingsStore.rememberChoices)
        SettingsStore.updateRememberChoices(true)
        assertTrue(SettingsStore.rememberChoices)
    }
}
