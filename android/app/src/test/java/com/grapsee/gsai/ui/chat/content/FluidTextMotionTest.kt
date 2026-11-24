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
        assertEquals(0.18f, bands.first().opacity(1_000_000L), 0.001f)
        assertTrue(bands.first().opacity(80_000_000L) > bands.first().opacity(1_000_000L))
        assertEquals(1f, bands.first().opacity(350_000_000L), 0.001f)
        assertTrue(motion.update("old new words", 350_000_000L, true).isEmpty())
    }

    @Test fun burstAndRepeatedUpdatesNeverDropUnsettledInk() {
        val motion = FluidTextMotion("")
        val text = "a".repeat(1000)
        val bands = motion.update(text, 1, true)
        assertEquals(0, bands.first().start)
        assertEquals(1000, bands.last().end)
        assertTrue(bands.size <= 4)
        var current = text
        repeat(30) {
            current += "x"
            val active = motion.update(current, 2L + it * 1_000_000L, true)
            assertEquals(bands.first(), active.first())
            assertEquals(current.length, active.last().end)
            assertTrue(active.zipWithNext().all { (a, b) -> a.end == b.start })
        }
        assertTrue(motion.update(current, 600_000_000L, true).isEmpty())
    }

    @Test fun opticalFrontMovesAcrossRealInkAndSettlesTogether() {
        val band = FluidTextBand(0, 12, 0, 200_000_000)
        for (tick in 0..200) {
            val now = tick * 1_000_000L
            val first = band.opacity(now, 0f)
            val last = band.opacity(now, 1f)
            assertTrue(first >= last)
            assertTrue(first in 0.179f..1.001f)
            assertTrue(last in 0.179f..1.001f)
            if (tick > 0) assertTrue(first + 0.0001f >= band.opacity(now - 1_000_000L, 0f))
        }
        assertTrue(band.opacity(90_000_000, 0f) - band.opacity(90_000_000, 1f) > 0.2f)
        assertEquals(1f, band.opacity(200_000_000, 1f), 0.001f)
    }

    @Test fun cadenceIsContinuousAndDoesNotRestartOldBands() {
        val motion = FluidTextMotion("")
        val first = motion.update("first word ", 0, true).first()
        val later = motion.update("first word next word ", 17_000_000, true)
        assertEquals(first, later.first())
        assertTrue(later.last().start >= first.end)
        val newest = motion.update("first word next word final", 117_000_000, true).last()
        assertTrue(newest.durationNanos in 185_000_000L..265_000_000L)
        assertTrue(motion.update("first word next word final", 600_000_000, true).isEmpty())
    }

    @Test fun neverMasksHalfOfSurrogateOrExistingCombiningCluster() {
        val motion = FluidTextMotion("e")
        val result = motion.update("e\u0301 hello", 0, true)
        assertTrue(result.all { it.start >= 2 })
        val emoji = FluidTextMotion("")
        val text = "a".repeat(11) + "😀" + "b".repeat(15)
        val bands = emoji.update(text, 0, true)
        assertTrue(bands.none { it.start == 12 || it.end == 12 })
        val growingCluster = FluidTextMotion("")
        growingCluster.update("e", 0, true)
        assertTrue(growingCluster.update("e\u0301", 1_000_000, true).isEmpty())
    }
}
