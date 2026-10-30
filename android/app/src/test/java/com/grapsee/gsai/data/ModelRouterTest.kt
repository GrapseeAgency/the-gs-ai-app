package com.grapsee.gsai.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The rule behind the Model Selection page's two controls. It is a pure
 * function on purpose: the reference draws Auto Select ON and Medium lit at the
 * same time, and this is what makes that pair mean something instead of two
 * stored values nothing reads.
 */
class ModelRouterTest {

    @Test
    fun anImageAlwaysWantsTheVisionModel() {
        assertEquals(
            GsModel.VISION,
            ModelRouter.pick("make this brighter", hasImage = true, preferred = GsModel.PRO),
        )
        // Even when the reader's preference is another model: only Vision can
        // read the attachment, so the preference does not outrank it.
        assertEquals(
            GsModel.VISION,
            ModelRouter.pick("compile this", hasImage = true, preferred = GsModel.CODE),
        )
    }

    @Test
    fun aCodeSignalBeatsLengthAndPreference() {
        assertEquals(
            GsModel.CODE,
            ModelRouter.pick("```fun main() {}```", hasImage = false, preferred = GsModel.LITE),
        )
        assertEquals(
            GsModel.CODE,
            ModelRouter.pick("why does this stack trace appear", hasImage = false, preferred = GsModel.PRO),
        )
    }

    @Test
    fun aLongMessageIsTreatedAsComplexAndShortOnesKeepThePreference() {
        assertEquals(
            GsModel.PRO,
            ModelRouter.pick("word ".repeat(60), hasImage = false, preferred = GsModel.LITE),
        )
        assertEquals(
            GsModel.LITE,
            ModelRouter.pick("what's the weather", hasImage = false, preferred = GsModel.LITE),
        )
    }

    @Test
    fun effortMapsToTheThreeWireModesTheServerAccepts() {
        assertEquals("flash", ModelRouter.modeFor("minimal"))
        assertEquals("flash", ModelRouter.modeFor("low"))
        assertEquals("auto", ModelRouter.modeFor("standard"))
        assertEquals("thinking", ModelRouter.modeFor("high"))
    }

    @Test
    fun autoSelectTakesTheModelsEffortAndOffTakesTheReaders() {
        // ON + a code message -> Code's default (high) -> thinking, whatever the
        // chip says.
        assertEquals(
            "thinking",
            ModelRouter.modeForMessage(
                text = "please debug this python",
                hasImage = false,
                preferred = GsModel.PRO,
                effort = "minimal",
                autoSelect = true,
            ),
        )
        // OFF -> the reader's effort is the authority.
        assertEquals(
            "flash",
            ModelRouter.modeForMessage(
                text = "please debug this python",
                hasImage = false,
                preferred = GsModel.PRO,
                effort = "minimal",
                autoSelect = false,
            ),
        )
    }

    @Test
    fun anUnknownStoredIdFallsBackToProInsteadOfNoSelection() {
        assertEquals(GsModel.PRO, GsModel.byId(null))
        assertEquals(GsModel.PRO, GsModel.byId("gpt-9"))
        assertEquals(GsModel.LITE, GsModel.byId("lite"))
    }

    @Test
    fun theTabsCapabilityBucketsMatchTheModelsTheyShouldShow() {
        // All is the unfiltered list; Text must not offer Vision-only models.
        assertEquals(
            listOf(GsModel.PRO, GsModel.LITE),
            GsModel.entries.filter { "text" in it.capabilities },
        )
        assertEquals(
            listOf(GsModel.PRO, GsModel.VISION),
            GsModel.entries.filter { "vision" in it.capabilities },
        )
        assertEquals(
            listOf(GsModel.PRO, GsModel.CODE),
            GsModel.entries.filter { "code" in it.capabilities },
        )
    }
}
