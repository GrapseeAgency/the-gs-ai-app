package com.grapsee.gsai.ui.chat.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidTextMotionTest {
    @Test fun initialAndRewrittenTextNeverReplay() {
        val motion = FluidTextMotion("already visible")
        assertTrue(motion.update("already visible", 0, true).isEmpty())
        assertTrue(motion.update("rewritten", 1, true).isEmpty())
        assertTrue(motion.update("rewritten!", 2, false).isEmpty())
        assertTrue(motion.update("rewritten!", 3, true).isEmpty())
    }

    @Test fun appendKeepsPrefixAndFinishesWithoutBuffering() {
        val motion = FluidTextMotion("old ")
        val bands = motion.update("old new words", 1_000_000L, true)
        assertEquals(4, bands.first().start)
        assertEquals(13, bands.last().end)
        assertTrue(bands.first().opacity(1_000_000L) >= 0.57f)
        assertEquals(1f, bands.first().opacity(200_000_000L), 0.001f)
        assertTrue(motion.update("old new words", 200_000_000L, true).isEmpty())
    }

    @Test fun burstAndRepeatedUpdatesHaveBoundedWork() {
        val motion = FluidTextMotion("")
        val text = "a".repeat(1000)
        val bands = motion.update(text, 1, true)
        assertEquals(952, bands.first().start)
        assertEquals(1000, bands.last().end)
        assertTrue(bands.size <= 4)
        var current = text
        repeat(30) {
            current += "x"
            assertTrue(motion.update(current, 2L + it * 1_000_000L, true).size <= 8)
        }
    }

    @Test fun neverMasksHalfOfSurrogateOrExistingCombiningCluster() {
        val motion = FluidTextMotion("e")
        val result = motion.update("e\u0301 hello", 0, true)
        assertTrue(result.all { it.start >= 2 })
        val emoji = FluidTextMotion("")
        val text = "a".repeat(11) + "😀" + "b".repeat(15)
        val bands = emoji.update(text, 0, true)
        assertTrue(bands.none { it.start == 12 || it.end == 12 })
    }
}
