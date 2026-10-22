package com.grapsee.gsai.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
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
 * The real Home tree rendered on the JVM. [HomeContent] is state in, UI out,
 * so the page can be screenshotted at the sheet's exact size (393x852) and at
 * the narrow sizes the layout has to survive — no emulator required.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun onlyOnRequest() {
        assumeTrue(System.getProperty("gs.screenshots") == "true")
    }

    @Composable
    private fun content() = HomeContent(
        firstName = "Arafat",
        greeting = "Good morning",
        suggestions = emptyList(),
        recent = emptyList(),
        onNewChat = {},
        onStartPrompt = {},
        onOpenConversation = {},
        onOpenDrawer = {},
        onOpenChats = {},
        onOpenAgent = {},
        onOpenVoice = {},
        onOpenSettings = {},
    )

    private fun shoot(path: String) {
        compose.setContent { TheGsAiTheme(darkTheme = false) { content() } }
        compose.onRoot().captureRoboImage(path)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
    fun homeAtSheetSize() = shoot("build/screenshots/home-393x852.png")

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun homeAtPixel6() = shoot("build/screenshots/home-411x914.png")

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
    fun homeAtNarrowWidth() = shoot("build/screenshots/home-360x780.png")
}
