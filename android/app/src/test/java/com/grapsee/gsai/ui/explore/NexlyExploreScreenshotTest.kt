package com.grapsee.gsai.ui.explore

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
 * The Explore AI page, rendered on the JVM: the REAL screen — hero, chips,
 * search, Featured Tools grid, Popular rows, Collections, Featured journey,
 * composer — over the same pearl backdrop Home mounts. No mockup.
 *
 * The references are docs/reference/explore/explore-*.png; the captures sit
 * beside the chat renders in the nexly-chat-render-android artifact.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NexlyExploreScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun onlyOnRequest() {
        assumeTrue(System.getProperty("gs.screenshots") == "true")
    }

    @Composable
    private fun explorePage() {
        ExploreScreen(
            onOpenChat = { _, _ -> },
            onBack = {},
        )
    }

    private fun shoot(path: String) {
        compose.setContent { TheGsAiTheme(darkTheme = false) { explorePage() } }
        compose.onRoot().captureRoboImage(path)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w393dp-h1400dp-xxhdpi")
    fun exploreFullPageAtReferenceHeight() =
        shoot("build/screenshots/nexly-explore-full-393x1400.png")

    @Test
    @Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
    fun exploreAtSheetSize() =
        shoot("build/screenshots/nexly-explore-393x852.png")

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun exploreAtPixel6() =
        shoot("build/screenshots/nexly-explore-411x914.png")

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
    fun exploreAtCompact() =
        shoot("build/screenshots/nexly-explore-360x780.png")

    @Test
    @Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
    fun exploreSearchOpen() {
        compose.setContent {
            TheGsAiTheme(darkTheme = false) {
                // The live results state: field open, real query filtering the
                // real catalog into rows.
                ExploreScreen(
                    onOpenChat = { _, _ -> },
                    onBack = {},
                    initialQuery = "study",
                    initialSearchOpen = true,
                )
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/nexly-explore-search-393x852.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
    fun exploreSearchEmpty() {
        compose.setContent {
            TheGsAiTheme(darkTheme = false) {
                // The honest empty state: the query becomes the conversation.
                ExploreScreen(
                    onOpenChat = { _, _ -> },
                    onBack = {},
                    initialQuery = "kintsugi",
                    initialSearchOpen = true,
                )
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/nexly-explore-search-empty-393x852.png")
    }
}
