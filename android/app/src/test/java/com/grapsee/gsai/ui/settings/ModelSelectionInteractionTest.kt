package com.grapsee.gsai.ui.settings

import android.content.Context
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.TheGsAiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every control on the two rebuilt screens, pressed, and the preference or
 * route it produced.
 *
 * SettingsModelSelectionTest proves the STORE round-trips. This proves the
 * SCREEN is actually wired to it: before it, the model radio held a local
 * `remember`, so the tick jumped back to Pro on every visit while the store
 * test stayed green. A preference test that never presses the control cannot
 * catch that.
 *
 * Runs in the JVM unit suite (android-app.yml), so it gates every push without
 * a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class ModelSelectionInteractionTest {

    @get:Rule val compose = createComposeRule()

    /**
     * Robolectric gives each test a fresh Application, but SettingsStore is a
     * static object hydrated once from prefs. Clearing the file AND
     * re-running init puts every field back to its shipped default, so one
     * test's "High / Creative / remember off" cannot leak into the next.
     */
    @Before
    fun resetStore() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("gs_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
        SettingsStore.init(ctx)
    }

    private fun renderModelScreen(
        onOpenModelDetails: () -> Unit = {},
    ) {
        compose.setContent {
            TheGsAiTheme(darkTheme = false) {
                AiIntelligenceScreen(
                    onBack = {},
                    onLocalAiRequested = {},
                    onTryComponents = {},
                    onOpenModelDetails = onOpenModelDetails,
                )
            }
        }
    }

    @Test
    fun everyEffortTileWritesThePreferenceAndTheWireMode() {
        renderModelScreen()

        val cases = listOf(
            "Minimal" to ("minimal" to "flash"),
            "Light" to ("light" to "flash"),
            "Standard" to ("standard" to "auto"),
            "High" to ("high" to "thinking"),
        )
        for ((label, expected) in cases) {
            compose.onNodeWithText(label).performScrollTo().performClick()
            compose.waitForIdle()
            assertEquals(
                "$label did not land in the store",
                expected.first,
                SettingsStore.reasoningEffort,
            )
            assertEquals(
                "$label wrote the wrong wire mode",
                expected.second,
                SettingsStore.chatMode(null),
            )
        }
    }

    @Test
    fun theModelRadioPersistsMostRecentTap() {
        renderModelScreen()

        compose.onNodeWithText("Nexly Creative").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals("creative", SettingsStore.preferredModel)

        compose.onNodeWithText("Nexly Advanced").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals("advanced", SettingsStore.preferredModel)
    }

    @Test
    fun rememberMyChoicesIsARealSwitchWithARealEffect() {
        renderModelScreen()

        compose.onNodeWithText("Remember my choices").performScrollTo()
        // The mock draws it ON; the shipped default is ON and the switch is a
        // toggleable, not a painted pill.
        compose.onNodeWithText("Remember my choices").assertIsOn()
        compose.onNodeWithText("Remember my choices").performClick()
        compose.waitForIdle()
        assertFalse(SettingsStore.rememberChoices)
        compose.onNodeWithText("Remember my choices").assertIsOff()

        // With the switch OFF, a new effort must not change the new-chat
        // default — that suppression is the only thing the switch controls.
        compose.onNodeWithText("High").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals("high", SettingsStore.reasoningEffort)
        assertEquals("flash", SettingsStore.chatMode(null))

        compose.onNodeWithText("Remember my choices").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(SettingsStore.rememberChoices)
    }

    @Test
    fun autoSelectModelStaysOffAndDisabled() {
        renderModelScreen()

        // Kept-off on purpose: the backend chooses the model, so a tappable
        // switch here would promise a choice the app cannot make.
        compose.onNodeWithText("Auto-select model").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Auto-select model").assertIsOff()
    }

    @Test
    fun resetRestoresTheRecommendedDefaults() {
        SettingsStore.updatePreferredModel("creative")
        SettingsStore.updateRememberChoices(false)
        SettingsStore.updateReasoningEffort("high")
        renderModelScreen()

        compose.onNodeWithText("Reset to default").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals("pro", SettingsStore.preferredModel)
        assertTrue(SettingsStore.rememberChoices)
        assertEquals("standard", SettingsStore.reasoningEffort)
        assertEquals("auto", SettingsStore.chatMode(null))
    }

    @Test
    fun viewModelDetailsFiresItsRoute() {
        var opened = 0
        renderModelScreen(onOpenModelDetails = { opened += 1 })

        compose.onNodeWithText("View model details").performScrollTo().performClick()
        assertEquals(1, opened)
    }

    @Test
    fun theChatHistoryRowOpensTheSearchPage() {
        val routes = mutableListOf<String>()
        compose.setContent {
            TheGsAiTheme(darkTheme = false) {
                PrivacySettingsScreen(onBack = {}, onNavigate = { routes += it })
            }
        }

        compose.onNodeWithText("Chat History").performScrollTo().performClick()
        assertEquals(listOf(GsRoutes.CHAT_SEARCH), routes)
    }
}
