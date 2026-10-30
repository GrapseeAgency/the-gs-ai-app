package com.grapsee.gsai.ui.home

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.settings.ModelSelectionScreen
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
 * The two new pages' contracts, proved on the real Compose tree:
 *  - the model picker's tabs filter the list, a card reports its id, the
 *    Auto Select switch reports its value and Continue fires;
 *  - the pre-chat composer reports what was typed with autoSend, a tool chip
 *    seeds its own prompt without sending, and the model entry opens the
 *    picker.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class NewChatAndModelPickerTest {

    @get:Rule val compose = createComposeRule()

    @Before
    fun reset() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("gs_settings", Context.MODE_PRIVATE).edit().clear().commit()
        SettingsStore.init(ctx)
        SettingsStore.updatePreferredModel("pro")
        SettingsStore.updateAutoSelectModel(true)
        ServiceLocator.init(ctx)
    }

    // ---- Model picker -----------------------------------------------------

    @Test
    fun theVisionTabShowsOnlyModelsThatDeclareVision() {
        compose.setContent {
            TheGsAiTheme(darkTheme = false) {
                ModelSelectionScreen(onBack = {}, onContinue = {}, preferred = "pro")
            }
        }

        compose.onNodeWithTag("model-tab-all").assertIsDisplayed()
        compose.onNodeWithText("Nexly Lite").assertIsDisplayed()

        compose.onNodeWithTag("model-tab-vision").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Nexly Vision").assertIsDisplayed()
        assertTrue(
            "the Vision tab must drop the text-only model",
            compose.onAllNodesWithText("Nexly Lite").fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun pickingACardReportsItsId() {
        val picked = mutableListOf<String>()
        compose.setContent {
            TheGsAiTheme(darkTheme = false) {
                ModelSelectionScreen(
                    onBack = {}, onContinue = {}, preferred = "pro",
                    onPreferred = { picked += it },
                )
            }
        }

        compose.onNodeWithTag("model-card-lite").performClick()
        compose.onNodeWithTag("model-card-code").performClick()

        assertEquals(listOf("lite", "code"), picked)
    }

    @Test
    fun autoSelectAndContinueAreWired() {
        var auto: Boolean? = null
        var continued = 0
        compose.setContent {
            TheGsAiTheme(darkTheme = false) {
                ModelSelectionScreen(
                    onBack = {}, onContinue = { continued += 1 },
                    preferred = "pro", autoSelect = true,
                    onAutoSelect = { auto = it },
                )
            }
        }

        compose.onNodeWithTag("auto-select-row").performClick()
        assertEquals(false, auto)

        compose.onNodeWithTag("model-continue").performClick()
        assertEquals(1, continued)
    }

    @Test
    fun theEffortControlReportsTheTierItWrites() {
        val efforts = mutableListOf<String>()
        compose.setContent {
            TheGsAiTheme(darkTheme = false) {
                ModelSelectionScreen(
                    onBack = {}, onContinue = {}, preferred = "pro",
                    effort = "standard", onEffort = { efforts += it },
                )
            }
        }

        compose.onNodeWithTag("effort-high").performClick()
        assertEquals(listOf("high"), efforts)
    }

    // ---- Pre-chat page ----------------------------------------------------

    @Test
    fun theComposerSendsWhatWasTypedAndWritesTheMode() {
        val sent = mutableListOf<Pair<String, Boolean>>()
        compose.setContent {
            TheGsAiTheme(darkTheme = false) {
                NewChatScreen(
                    onStartChat = { prompt, autoSend -> sent += prompt to autoSend },
                    onOpenConversation = {}, onOpenAllChats = {},
                    onOpenModels = {}, onOpenMenu = {}, onOpenExplore = {},
                )
            }
        }

        compose.onNodeWithTag("new-chat-input").performTextInput("hello there")
        compose.onNodeWithTag("new-chat-send").performClick()

        assertEquals(listOf("hello there" to true), sent)
    }

    @Test
    fun aToolChipSeedsItsPromptWithoutSending() {
        val sent = mutableListOf<Pair<String, Boolean>>()
        compose.setContent {
            TheGsAiTheme(darkTheme = false) {
                NewChatScreen(
                    onStartChat = { prompt, autoSend -> sent += prompt to autoSend },
                    onOpenConversation = {}, onOpenAllChats = {},
                    onOpenModels = {}, onOpenMenu = {}, onOpenExplore = {},
                )
            }
        }

        compose.onNodeWithText("Web Search").performClick()
        assertEquals(listOf("Search the web for " to false), sent)
    }

    @Test
    fun theModelEntryAndTheMenuAreLive() {
        var models = 0
        var menu = 0
        compose.setContent {
            TheGsAiTheme(darkTheme = false) {
                NewChatScreen(
                    onStartChat = { _, _ -> },
                    onOpenConversation = {}, onOpenAllChats = {},
                    onOpenModels = { models += 1 }, onOpenMenu = { menu += 1 },
                    onOpenExplore = {},
                )
            }
        }

        compose.onNodeWithTag("explore-models").performClick()
        compose.onNodeWithTag("new-chat-menu").performClick()

        assertEquals(1, models)
        assertEquals(1, menu)
    }
}
