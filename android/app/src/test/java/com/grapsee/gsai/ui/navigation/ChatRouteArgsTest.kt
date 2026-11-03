package com.grapsee.gsai.ui.navigation

import android.content.Context
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.createGraph
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * WHY THIS TEST EXISTS
 *
 * The owner typed "hello" on the pre-chat page, tapped send, and nothing
 * happened: the message never arrived in a chat, and the page he was on came
 * straight back. Two defects stacked, and neither one is visible in a
 * screenshot or a compile:
 *
 *   1. `GsRoutes.chat(...)` emitted `send=1` / `attach=1`. Navigation parses
 *      boolean arguments with `String.toBoolean()`, which is true ONLY for
 *      "true" — so `autoSend` arrived as FALSE, the chat surface found an
 *      empty transcript, and the "nothing here, go to the hero" rule bounced
 *      the reader back to where they started.
 *   2. `attach` was not declared in the route pattern at all, so
 *      `chat(new?attach=1)` matched no destination — the paperclip threw
 *      instead of opening the sheet.
 *
 * The assertions below run the REAL route builder through a REAL NavController,
 * so a future encoding change (`=1`, `=yes`, a renamed key) fails here instead
 * of on the owner's phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatRouteArgsTest {

    private lateinit var nav: NavController

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        nav = NavController(context)
        nav.navigatorProvider.addNavigator(ComposeNavigator())
        nav.graph =
            nav.createGraph(startDestination = GsRoutes.NEW_CHAT) {
                composable(GsRoutes.NEW_CHAT) {}
                composable(
                    route = GsRoutes.CHAT,
                    arguments = listOf(
                        navArgument(GsRoutes.ARG_CONVERSATION) {
                            type = NavType.StringType; defaultValue = "new"
                        },
                        navArgument(GsRoutes.ARG_PROMPT) {
                            type = NavType.StringType; defaultValue = ""
                        },
                        navArgument(GsRoutes.ARG_SEND) {
                            type = NavType.BoolType; defaultValue = false
                        },
                        navArgument(GsRoutes.ARG_ATTACH) {
                            type = NavType.BoolType; defaultValue = false
                        },
                    )
                ) {}
            }
    }

    private fun args() = requireNotNull(nav.currentBackStackEntry?.arguments)

    @Test
    fun `a send from the pre-chat page arrives as a real auto-send`() {
        nav.navigate(GsRoutes.chat(null, "hello", autoSend = true))

        assertEquals(GsRoutes.CHAT, nav.currentDestination?.route)
        assertEquals("hello", args().getString(GsRoutes.ARG_PROMPT))
        assertTrue(
            "autoSend must survive the route — `send=1` parsed as false and " +
                "sent the reader straight back to the pre-chat page",
            args().getBoolean(GsRoutes.ARG_SEND)
        )
        assertFalse(args().getBoolean(GsRoutes.ARG_ATTACH))
    }

    @Test
    fun `the paperclip opens the chat with its attach sheet up`() {
        nav.navigate(GsRoutes.chat(null, attach = true))

        assertEquals(
            "an undeclared route argument matches no destination at all",
            GsRoutes.CHAT,
            nav.currentDestination?.route
        )
        assertTrue(args().getBoolean(GsRoutes.ARG_ATTACH))
        assertFalse(args().getBoolean(GsRoutes.ARG_SEND))
    }

    @Test
    fun `a prompt full of reserved characters survives the trip`() {
        val prompt = "what about a & b? 100% / 50% \"quoted\" #hash"
        nav.navigate(GsRoutes.chat(null, prompt, autoSend = true))

        assertEquals(prompt, args().getString(GsRoutes.ARG_PROMPT))
        assertTrue(args().getBoolean(GsRoutes.ARG_SEND))
    }

    @Test
    fun `an empty prompt is not written to the route at all`() {
        val route = GsRoutes.chat(null, "   ", autoSend = false)
        assertFalse(route.contains("prompt="))
        assertEquals("chat/new", route)
    }
}
