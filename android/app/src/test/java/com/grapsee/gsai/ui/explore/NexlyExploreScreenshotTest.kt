package com.grapsee.gsai.ui.explore

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
 * BOTH Explore pages, rendered on the JVM: the Explore page (hero + Popular
 * Tools + Collections + Featured) and the Discover page (EXPLORE hero + chips
 * + Featured Tools + Popular + composer) — the real composables over the
 * authored backdrop. No mockup.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NexlyExploreScreenshotTest {
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
    @Config(sdk = [34], qualifiers = "w393dp-h1400dp-xxhdpi")
    fun exploreFullPageAtReferenceHeight() =
        shoot("build/screenshots/nexly-explore-full-393x1400.png") {
            ExploreScreen(onOpenChat = { _, _ -> }, onOpenDiscover = {}, onOpenTravelAgent = {}, onBack = {}, onOpenSettings = {})
        }

    @Test
    @Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
    fun exploreAtSheetSize() =
        shoot("build/screenshots/nexly-explore-393x852.png") {
            ExploreScreen(onOpenChat = { _, _ -> }, onOpenDiscover = {}, onOpenTravelAgent = {}, onBack = {}, onOpenSettings = {})
        }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun exploreAtPixel6() =
        shoot("build/screenshots/nexly-explore-411x914.png") {
            ExploreScreen(onOpenChat = { _, _ -> }, onOpenDiscover = {}, onOpenTravelAgent = {}, onBack = {}, onOpenSettings = {})
        }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
    fun exploreAtCompact() =
        shoot("build/screenshots/nexly-explore-360x780.png") {
            ExploreScreen(onOpenChat = { _, _ -> }, onOpenDiscover = {}, onOpenTravelAgent = {}, onBack = {}, onOpenSettings = {})
        }

    @Test
    @Config(sdk = [34], qualifiers = "w393dp-h1400dp-xxhdpi")
    fun discoverFullPage() =
        shoot("build/screenshots/nexly-discover-full-393x1400.png") {
            DiscoverScreen(onOpenChat = { _, _ -> }, onOpenTravelAgent = {}, onBack = {})
        }

    @Test
    @Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
    fun discoverAtSheetSize() =
        shoot("build/screenshots/nexly-discover-393x852.png") {
            DiscoverScreen(onOpenChat = { _, _ -> }, onOpenTravelAgent = {}, onBack = {})
        }

    @Test
    @Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
    fun discoverSearchState() {
        shoot("build/screenshots/nexly-explore-search-393x852.png") {
            DiscoverScreen(onOpenChat = { _, _ -> }, onOpenTravelAgent = {}, onBack = {}, initialQuery = "study", initialSearchOpen = true)
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
    fun discoverSearchEmpty() {
        shoot("build/screenshots/nexly-explore-search-empty-393x852.png") {
            DiscoverScreen(onOpenChat = { _, _ -> }, onOpenTravelAgent = {}, onBack = {}, initialQuery = "kintsugi", initialSearchOpen = true)
        }
    }
}
