package com.grapsee.gsai.ui.chat

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertTextContains
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class ComposerToolbarTest {
    @get:Rule val compose = createComposeRule()

    @Before fun reset() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("gs_settings", Context.MODE_PRIVATE).edit().clear().commit()
        SettingsStore.init(ctx)
    }

    @Test fun fieldIsAboveToolbarAndMenusPerformRealActions() {
        val draft = mutableStateOf("")
        val mode = mutableStateOf("auto")
        val sent = mutableListOf<String>()
        // The send disc runs a continuous metal-shader clock; an auto-advancing
        // test clock never reports idle, so frames are advanced explicitly.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TheGsAiTheme(darkTheme = false) {
                ComposerRow(
                    draftText = { draft.value }, onDraftChange = { draft.value = it },
                    onSend = { sent.add(it) }, onAttach = {},
                    isStreaming = false, onStop = {},
                    queuedText = { "" }, onQueue = {}, onUnqueue = {},
                    selectedMode = mode.value, onModeSelected = { mode.value = it },
                    onVoice = {}
                )
            }
        }
        compose.mainClock.advanceTimeByFrame()
        val input = compose.onNode(hasSetTextAction())
        val agent = compose.onNodeWithContentDescription("Agent tasks, Agent")
        val auto = compose.onNodeWithContentDescription("Response mode, Auto")
        val bounds = input.fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.bottom <= agent.fetchSemanticsNode().boundsInRoot.top)
        assertTrue(bounds.bottom <= auto.fetchSemanticsNode().boundsInRoot.top)
        input.performTextInput("Kotlin")
        agent.performClick()
        compose.mainClock.advanceTimeBy(320)
        compose.onNodeWithText("Code").performClick()
        compose.mainClock.advanceTimeByFrame()
        input.assertTextContains("Help me code Kotlin")
        auto.performClick()
        compose.mainClock.advanceTimeBy(320)
        compose.onNodeWithText("Thinking").performClick()
        compose.mainClock.advanceTimeByFrame()
        assertEquals("thinking", mode.value)
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("Send").performClick()
        assertEquals(listOf("Help me code Kotlin"), sent)
    }
}
