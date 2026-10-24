package com.grapsee.gsai.ui.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.navigation.drawer.DESTINATIONS
import com.grapsee.gsai.ui.navigation.drawer.DrawerFixture
import com.grapsee.gsai.ui.navigation.drawer.DrawerIdentity
import com.grapsee.gsai.ui.navigation.drawer.DrawerRecent
import com.grapsee.gsai.ui.navigation.drawer.iconForChat
import com.grapsee.gsai.ui.theme.TheGsAiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every control in the drawer, pressed, and the route it produced.
 *
 * The pixel gate proves the panel LOOKS right; this proves each row does
 * something — and specifically that it does the thing its label says. Before this
 * file, "the destinations are wired" was a claim about source code rather than a
 * fact about behaviour.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class DrawerInteractionTest {

    @get:Rule val compose = createComposeRule()

    private val routes = mutableListOf<String>()
    private var closed = 0

    private fun fixture() = DrawerFixture(
        identity = DrawerIdentity("Arafat", "Personal workspace", "A"),
        recents = listOf(
            DrawerRecent("c1", "Plan my tomorrow", "Just now", iconForChat(true)),
            DrawerRecent("c2", "Weekend in Kyoto", "Mon", iconForChat(false)),
        ),
    )

    private fun render() {
        compose.setContent {
            TheGsAiTheme {
                GsDrawerContent(
                    selectedRoute = GsRoutes.HOME,
                    onNavigate = { routes += it },
                    onClose = { closed += 1 },
                    fixture = fixture(),
                )
            }
        }
    }

    @Test
    fun theSearchPillOpensSearch() {
        render()
        compose.onNodeWithTag("drawer-search").performClick()
        assertEquals(listOf(GsRoutes.CHAT_SEARCH), routes)
        assertEquals("the drawer closes behind the search it opened", 1, closed)
    }

    @Test
    fun theHeroRowStartsANewChat() {
        render()
        compose.onNodeWithTag("drawer-hero").performClick()
        assertEquals(listOf(GsRoutes.chat(null)), routes)
    }

    @Test
    fun everyDestinationRoutesToItsOwnScreen() {
        render()
        for (destination in DESTINATIONS) {
            routes.clear()
            compose.onNodeWithTag("drawer-dest-${destination.label}").performClick()
            assertEquals(destination.label, listOf(destination.route), routes.toList())
        }
    }

    @Test
    fun theGearOpensSettings() {
        render()
        compose.onNodeWithTag("drawer-settings").performClick()
        assertEquals(listOf(GsRoutes.SETTINGS), routes)
    }

    @Test
    fun aRecentRowOpensThatConversation() {
        render()
        compose.onNodeWithTag("drawer-recent-c2").performClick()
        assertEquals(listOf(GsRoutes.chat("c2")), routes)
    }

    @Test
    fun theCloseControlClosesWithoutNavigating() {
        render()
        compose.onNodeWithTag("drawer-close").performClick()
        assertEquals(emptyList<String>(), routes)
        assertEquals(1, closed)
    }
}
