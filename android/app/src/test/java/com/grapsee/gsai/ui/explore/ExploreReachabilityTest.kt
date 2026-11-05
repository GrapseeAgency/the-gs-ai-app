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
 * Catalogue taps have exactly one rule since 0.86.24: a tool card opens that
 * tool's PAGE, and the page — not the card — decides what happens next.
 *
 * Before this, the trip planner jumped straight to Travel Agent and every
 * other tool pushed its prompt into the composer. Both were real actions, and
 * both skipped the question the reader was asking when they tapped a card
 * called "Image Studio": what does this do? The rule that replaced them is
 * simpler to state and harder to get wrong — a card opens its own page.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class ExploreReachabilityTest {

    @get:Rule val compose = createComposeRule()

    private fun renderDiscover(
        onTool: (String) -> Unit,
        onAgent: () -> Unit = {},
        onChat: () -> Unit = {},
    ) {
        compose.setContent {
            TheGsAiTheme(darkTheme = false) {
                DiscoverScreen(
                    onOpenChat = { _, _ -> onChat() },
                    onOpenTravelAgent = onAgent,
                    onOpenTool = onTool,
                    onBack = {},
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theTripPlannerOpensItsOwnPage() {
        var opened: String? = null
        var agent = 0
        var chats = 0
        renderDiscover(onTool = { opened = it }, onAgent = { agent++ }, onChat = { chats++ })

        compose.onNodeWithText("Plan a trip").performScrollTo().performClick()

        assertEquals("the trip planner card must open the tool page", "trip-planner", opened)
        assertEquals("the card must not jump to Travel Agent itself", 0, agent)
        assertEquals("the card must not start a chat", 0, chats)
    }

    @Test
    fun everyOtherToolOpensItsOwnPage() {
        var opened: String? = null
        var agent = 0
        var chats = 0
        renderDiscover(onTool = { opened = it }, onAgent = { agent++ }, onChat = { chats++ })

        compose.onNodeWithText("Generate a logo").performScrollTo().performClick()

        assertEquals("logo-maker", opened)
        assertEquals("a non-travel tool must not open Travel Agent", 0, agent)
        assertEquals("a non-travel tool must not seed a chat from the card", 0, chats)
    }
}
