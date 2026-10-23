package com.grapsee.gsai.ui.navigation

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.grapsee.gsai.ui.home.HomeContent
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.navigation.drawer.DrawerFixture
import com.grapsee.gsai.ui.navigation.drawer.DrawerIdentity
import com.grapsee.gsai.ui.navigation.drawer.DrawerMetrics
import com.grapsee.gsai.ui.navigation.drawer.DrawerRecent
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
 * The drawer, composed the way the app composes it: a real
 * [ModalNavigationDrawer] with the production [DrawerMetrics.scrim] over the
 * real Home page, so the strip the gate measures is the dim Home actually shows
 * — not a harness-only rectangle that would let the app drift while the gate
 * stays green.
 *
 * Real screenshots of the drawer are gated by `scripts/check_drawer_render.py`,
 * which re-derives its numbers from `assets/reference/drawer-reference.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DrawerScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun onlyOnRequest() {
        assumeTrue(System.getProperty("gs.screenshots") == "true")
    }

    /**
     * The sheet's own rows, so the capture can be compared with it line for
     * line: four titles, and the relative times it shows. A fixture is a
     * PARAMETER — nothing here touches ConversationStore, because Home's render
     * test asserts the store is empty and a seeding drawer test would fail it
     * from a distance.
     */
    private fun fixture(count: Int = 4) = DrawerFixture(
        identity = DrawerIdentity("Arafat", "Personal workspace", "A"),
        recents = listOf(
            DrawerRecent("1", "Plan my tomorrow", "Just now", GsIcons.Sparkles),
            DrawerRecent("2", "Ideas for a brand launch", "Yesterday", GsIcons.TrendingUp),
            DrawerRecent("3", "Explain quantum physics", "Yesterday", GsIcons.Atom),
            DrawerRecent("4", "Weekend in Kyoto", "Mon", GsIcons.Brush),
        ).let { rows ->
            if (count <= rows.size) rows else rows + List(count - rows.size) { i ->
                DrawerRecent("x$i", "Conversation ${i + 5}", "Mon", GsIcons.Sparkles)
            }
        },
    )

    @Composable
    private fun home() = HomeContent(
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

    @OptIn(ExperimentalMaterial3Api::class)
    private fun shoot(path: String, dark: Boolean = false, recentCount: Int = 4) {
        compose.setContent {
            TheGsAiTheme(darkTheme = dark) {
                val state = rememberDrawerState(DrawerValue.Open)
                ModalNavigationDrawer(
                    drawerState = state,
                    scrimColor = DrawerMetrics.scrim,
                    drawerContent = {
                        GsDrawerContent(
                            selectedRoute = GsRoutes.HOME,
                            onNavigate = {},
                            onClose = {},
                            fixture = fixture(recentCount),
                        )
                    },
                ) {
                    home()
                }
            }
        }
        // The panel must not need an animation frame to be fully open: a
        // half-open one is what the width assertion catches, not tolerates.
        compose.mainClock.advanceTimeBy(1_000)
        compose.onRoot().captureRoboImage(path)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
    fun drawerAtSheetSize() = shoot("build/screenshots/drawer-393x852.png")

    @Test
    @Config(sdk = [34], qualifiers = "w393dp-h852dp-night-xxhdpi")
    fun drawerInDark() = shoot("build/screenshots/drawer-393x852-dark.png", dark = true)

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
    fun drawerAtNarrowWidth() = shoot("build/screenshots/drawer-360x780.png")

    /**
     * 500 conversations must draw the same four rows. This is the frame the
     * "lazy list" claim is made against: the panel shows a bounded number of
     * rows no matter how many exist.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
    fun drawerWithManyConversations() =
        shoot("build/screenshots/drawer-500chats-393x852.png", recentCount = 500)
}
