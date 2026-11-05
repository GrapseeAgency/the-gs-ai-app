package com.grapsee.gsai.ui.explore

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.grapsee.gsai.ui.theme.TheGsAiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The tool page's contract, proved rather than photographed.
 *
 * The screenshot of a tool page stays green if every row is dead, so these
 * tests assert the three things the page exists to do: an example really hands
 * its own sentence to the composer, a paired tool really navigates to that
 * tool, and the trip planner's primary action really opens Travel Agent instead
 * of a generic chat.
 *
 * The alias test guards the seam between the two mocks: Discover's "Code
 * Helper" and Explore's "Code Assistant" are one capability, and a card that
 * opened a page keyed on the Discover id alone would show the missing state.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
class ToolDetailInteractionTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun everyToolInEveryCatalogueHasADetailPage() {
        val ids = (ExploreCatalog.tools + ExploreCatalog.discoverFeatured).map { it.id }
        val missing = ids.filter { ToolCatalog.detail(it) == null || ToolCatalog.tool(it) == null }
        assertEquals("tools without a page: $missing", emptyList<String>(), missing)
    }

    @Test
    fun theDiscoverCardsResolveToTheirCanonicalTool() {
        assertEquals("image-studio", ToolCatalog.canonicalId("discover-image-studio"))
        assertEquals("code-assistant", ToolCatalog.canonicalId("discover-code"))
        // A canonical id must not be rewritten by the alias table.
        assertEquals("image-studio", ToolCatalog.canonicalId("image-studio"))
        assertNotNull(ToolCatalog.detail("discover-code"))
    }

    @Test
    fun tappingAnExampleHandsItsOwnSentenceToTheComposer() {
        var started: String? = null
        compose.setContent {
            TheGsAiTheme(darkTheme = false) {
                ToolDetailScreen(
                    toolId = "image-studio",
                    onBack = {},
                    onStart = { started = it },
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("tool-example-0").performClick()
        compose.waitForIdle()

        val expected = ToolCatalog.detail("image-studio")!!.examples.first()
        assertEquals(expected, started)
        // The example is not the tool's generic prompt — that would make the
        // three rows decorative.
        assertTrue(started != ToolCatalog.tool("image-studio")!!.prompt)
    }

    @Test
    fun aPairedToolOpensThatToolsPage() {
        var opened: String? = null
        compose.setContent {
            TheGsAiTheme(darkTheme = false) {
                ToolDetailScreen(
                    toolId = "image-studio",
                    onBack = {},
                    onStart = {},
                    onOpenTool = { opened = it },
                )
            }
        }
        compose.waitForIdle()

        val pair = ToolCatalog.pairs("image-studio").first()
        compose.onAllNodesWithText(pair.title).onFirst().performClick()
        compose.waitForIdle()

        assertEquals(pair.id, opened)
    }

    @Test
    fun theTripPlannersPrimaryActionIsTravelAgent() {
        var openedAgent = false
        var started: String? = null
        compose.setContent {
            TheGsAiTheme(darkTheme = false) {
                ToolDetailScreen(
                    toolId = "trip-planner",
                    onBack = {},
                    onStart = { started = it },
                    onOpenTravelAgent = { openedAgent = true },
                )
            }
        }
        compose.waitForIdle()

        compose.onAllNodesWithText("Open Travel Agent").onFirst().performClick()
        compose.waitForIdle()

        assertTrue("the trip planner's CTA did not open Travel Agent", openedAgent)
        assertEquals("the CTA must not fall back to a seeded chat", null, started)
    }

    @Test
    fun aToolThatIsNotInThisBuildSaysSo() {
        compose.setContent {
            TheGsAiTheme(darkTheme = false) {
                ToolDetailScreen(toolId = "no-such-tool", onBack = {}, onStart = {})
            }
        }
        compose.waitForIdle()

        compose.onAllNodesWithText("Tools change between versions.").onFirst().assertExists()
    }
}
