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

    @Test fun allRealPacketPiecesDevelopImmediatelyWithContinuousPhase() {
        val motion = FluidTextMotion("")
        val bands = motion.update("abc ".repeat(200), 1_000_000L, true)
        assertTrue(bands.size > 1)
        assertTrue(bands.all { it.bornNanos == 1_000_000L })
        assertTrue(bands.all { it.opacity(17_000_000L, 1f) > 0.18f })
        for ((a, b) in bands.zipWithNext()) {
            assertEquals(a.phaseTo, b.phaseFrom, 0.00001f)
            assertEquals(a.opacity(100_000_000L, 1f), b.opacity(100_000_000L, 0f), 0.00001f)
        }
        for (pos in 0..100) {
            var previous = 0f
            for (tick in 0..1000) {
                val p = fluidLocalProgress(tick / 1000f, pos / 100f, 0.48f)
                assertTrue(p + 0.000001f >= previous)
                if (tick in 1..999) assertTrue(p > 0f && p < 1f)
                previous = p
            }
        }
    }

    @Test fun reusableGradientPlacementMatchesNativeInkCurve() {
        for ((from, to) in listOf(0f to 1f, 1f to 0f, 0.42f to 0.43f, 0.2f to 0.2f)) {
            for (tick in 0..100) {
                val progress = tick / 100f
                val projection = fluidInkProjection(progress, from, to, 0.48f)
                for (step in 0..100) {
                    val x = step / 100f
                    val actual = projection.solid ?: fluidInkOpacity((x - projection.origin) / projection.scale)
                    val expected = fluidInkOpacity(fluidLocalProgress(progress, from + (to - from) * x, 0.48f))
                    assertEquals(expected, actual, 0.0002f)
                }
            }
        }
    }

    @Test fun continuousInkProjectionMatchesCurveInBothDirections() {
        for (direction in listOf(0f to 1f, 1f to 0f, 0.3f to 0.42f)) {
            for (tick in 0..100) {
                val progress = tick / 100f
                val stops = fluidInkStops(progress, direction.first, direction.second, 0.48f)
                assertEquals(0f, stops.first().position, 0.0001f)
                assertEquals(1f, stops.last().position, 0.0001f)
                assertTrue(stops.zipWithNext().all { (a, b) -> a.position < b.position })
                for (x in 0..100) {
                    val f = x / 100f
                    val pair = stops.zipWithNext().first { (_, b) -> b.position >= f }
                    val (a, b) = pair
                    val alpha = a.opacity + (b.opacity - a.opacity) * (f - a.position) / (b.position - a.position)
                    val phase = direction.first + (direction.second - direction.first) * f
                    assertEquals(fluidInkOpacity(fluidLocalProgress(progress, phase, 0.48f)), alpha, 0.008f)
                }
            }
        }
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

    @Test fun markdownClosingDoesNotRestartPreviouslyVisibleInk() {
        val motion = FluidTextMotion("")
        val first = motion.update("**hello", 0, true).single()
        val updated = motion.update("hello world", 50_000_000, true, sourceAppend = true)
        val preserved = updated.first()
        assertEquals(0, preserved.start)
        assertEquals(5, preserved.end)
        assertEquals(first.bornNanos, preserved.bornNanos)
        assertEquals(first.opacity(50_000_000, 2f / 7f), preserved.opacity(50_000_000, 0f), 0.001f)
        assertEquals(5, updated.last().start)
        assertEquals(11, updated.last().end)
        assertEquals(50_000_000L, updated.last().bornNanos)
        assertTrue(motion.update("replacement", 55_000_000, true).isEmpty())
    }

    @Test fun ambiguousRenderedRewritesNeverReplayOldText() {
        val motion = FluidTextMotion("**word")
        assertTrue(motion.update("word word", 0, true, sourceAppend = true).isEmpty())
        assertTrue(motion.update("word word!", 1, true).single().start == 9)
    }

    @Test fun consecutivePacketsJoinWithoutAccumulatingAVisualBacklog() {
        val motion = FluidTextMotion("")
        var text = ""
        var previousBirth = Long.MIN_VALUE
        repeat(60) { tick ->
            val now = tick * 5_000_000L
            text += "abcdefghij"
            val newest = motion.update(text, now, true).last()
            assertEquals(now, newest.bornNanos)
            assertTrue(newest.bornNanos >= previousBirth)
            assertEquals(text.length, newest.end)
            assertTrue(newest.opacity(now, 1f) >= 0.179f)
            previousBirth = newest.bornNanos
        }
        assertTrue(motion.update(text, 800_000_000L, true).isEmpty())
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
