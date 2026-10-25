package com.grapsee.gsai.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
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
 * docs/reference/settings/*.png. One class, one Gradle invocation: each
 * separate `--tests` invocation pays ~60-90s of fixed Robolectric boot.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun onlyOnRequest() {
        assumeTrue(System.getProperty("gs.screenshots") == "true")
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
    @Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
    fun memoryAtCompact() =
        shoot("build/screenshots/nexly-memory-360x780.png") {
            MemorySettingsScreen(onBack = {}, onNavigate = {})
        }
}
