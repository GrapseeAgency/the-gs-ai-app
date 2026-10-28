package com.grapsee.gsai.ui.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.ui.profile.ProfileScreen
import com.grapsee.gsai.ui.theme.TheGsAiTheme
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The three Settings screens on the JVM — the evidence compared against
 * the mocks under docs/reference/settings. One class, one Gradle invocation: each
 * separate `--tests` invocation pays ~60-90s of fixed Robolectric boot.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun onlyOnRequest() {
        assumeTrue(System.getProperty("gs.screenshots") == "true")
        // The interaction suite shares the JVM; a switch left ON/OFF by it must
        // not colour a capture. Reset to the shipped defaults explicitly.
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("gs_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
        SettingsStore.init(ctx)
    }

    private fun shoot(path: String, content: @Composable () -> Unit) {
        compose.setContent { TheGsAiTheme(darkTheme = false) { content() } }
        compose.onRoot().captureRoboImage(path)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
    fun hubAtSheetSize() =
        shoot("build/screenshots/nexly-settings-393x852.png") {
            SettingsHubScreen(onBack = {}, onNavigate = {})
        }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
    fun hubAtCompact() =
        shoot("build/screenshots/nexly-settings-360x780.png") {
            SettingsHubScreen(onBack = {}, onNavigate = {})
        }

    @Test
    @Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
    fun profilePreferencesAtSheetSize() =
        shoot("build/screenshots/nexly-profile-393x852.png") {
            ProfileScreen(onNavigate = {}, onBack = {})
        }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
    fun profilePreferencesAtCompact() =
        shoot("build/screenshots/nexly-profile-360x780.png") {
            ProfileScreen(onNavigate = {}, onBack = {})
        }

    @Test
    @Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
    fun memoryAtSheetSize() =
        shoot("build/screenshots/nexly-memory-393x852.png") {
            MemorySettingsScreen(onBack = {}, onNavigate = {})
        }

    @Test
    @Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
    fun privacyDataControls() =
        shoot("build/screenshots/nexly-privacy-393x852.png") {
            PrivacySettingsScreen(onBack = {}, onNavigate = {})
        }

    @Test
    @Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
    fun modelSelection() =
        shoot("build/screenshots/nexly-model-393x852.png") {
            AiIntelligenceScreen(
                onBack = {},
                onLocalAiRequested = {},
                onTryComponents = {},
                onOpenModelDetails = {},
            )
        }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
    fun memoryAtCompact() =
        shoot("build/screenshots/nexly-memory-360x780.png") {
            MemorySettingsScreen(onBack = {}, onNavigate = {})
        }
}
