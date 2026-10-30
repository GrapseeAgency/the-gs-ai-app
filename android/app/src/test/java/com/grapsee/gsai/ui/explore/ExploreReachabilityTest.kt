package com.grapsee.gsai.ui.explore

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.grapsee.gsai.ui.theme.TheGsAiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Catalogue taps have exactly one rule: the trip planner opens its own screen
 * (Travel Agent), every other tool seeds the composer. Travel Agent was built
 * and mounted in the nav host but NOTHING navigated to it — Discover's Popular
 * list is where the planner is visible, so that is the path this pins.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class ExploreReachabilityTest {

    @get:Rule val compose = createComposeRule()

    private fun renderDiscover(onAgent: () -> Unit, onChat: () -> Unit) {
        compose.setContent {
            TheGsAiTheme(darkTheme = false) {
                DiscoverScreen(
                    onOpenChat = { _, _ -> onChat() },
                    onOpenTravelAgent = onAgent,
                    onBack = {},
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theTripPlannerOpensTheTravelAgent() {
        var agent = 0
        var chats = 0
        renderDiscover(onAgent = { agent++ }, onChat = { chats++ })

        compose.onNodeWithText("Plan a trip").performScrollTo().performClick()

        assertEquals("the trip planner must open Travel Agent", 1, agent)
        assertEquals("no chat may open for the trip planner", 0, chats)
    }

    @Test
    fun everyOtherToolStillSeedsTheComposer() {
        var agent = 0
        var chats = 0
        renderDiscover(onAgent = { agent++ }, onChat = { chats++ })

        compose.onNodeWithText("Generate a logo").performScrollTo().performClick()

        assertEquals("a non-travel tool must not open Travel Agent", 0, agent)
        assertEquals("a non-travel tool seeds the composer", 1, chats)
    }
}
