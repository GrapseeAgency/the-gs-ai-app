package com.grapsee.gsai.ui.components

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.ui.theme.TheGsAiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * WHY THIS TEST EXISTS
 *
 * The composer used to carry a mic AND a send button side by side. Two discs
 * for one slot, one of them always dead, and the reader had to aim at the right
 * one. The owner's instruction was specific: "send button extra kore proyojon
 * nei — oi voice icon ta change hobe jokhon keu typing korbe, voice icon theke
 * send icon a transformation hobe".
 *
 * That is a behavioural contract, so it is tested as one: with an empty field
 * the control IS the voice action, with text it IS the send action, and the
 * verb it reports is the verb it performs. The morph animation itself is
 * visual; what must never break is which action a tap triggers.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class ComposerActionTest {

    @get:Rule val compose = createComposeRule()

    @Before
    fun reset() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("gs_settings", Context.MODE_PRIVATE).edit().clear().commit()
        SettingsStore.init(ctx)
    }

    @Test
    fun `an empty field offers voice, a typed field offers send`() {
        var hasContent by mutableStateOf(false)
        var sends = 0
        var voices = 0

        compose.setContent {
            TheGsAiTheme(darkTheme = false) {
                GsComposerAction(
                    hasContent = hasContent,
                    onSend = { sends++ },
                    onVoice = { voices++ },
                )
            }
        }

        compose.onNodeWithContentDescription("Voice").assertIsDisplayed().performClick()
        assertEquals(1, voices)
        assertEquals(0, sends)

        hasContent = true
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Send").assertIsDisplayed().performClick()
        assertEquals(1, sends)
        assertEquals("the mic must not fire once the field holds text", 1, voices)
    }

    @Test
    fun `without a voice route the control is a send button from the start`() {
        var sends = 0
        compose.setContent {
            TheGsAiTheme(darkTheme = false) {
                GsComposerAction(hasContent = true, onSend = { sends++ })
            }
        }

        compose.onNodeWithContentDescription("Send").assertIsDisplayed().performClick()
        assertTrue("a surface with no voice destination must not advertise one", sends == 1)
        assertTrue(
            "no voice node may exist when onVoice is null",
            compose.onAllNodesWithContentDescription("Voice").fetchSemanticsNodes().isEmpty()
        )
    }
}
