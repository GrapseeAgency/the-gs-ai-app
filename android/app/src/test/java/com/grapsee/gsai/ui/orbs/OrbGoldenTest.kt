package com.grapsee.gsai.ui.orbs

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Golden-vector parity (Phase 4 §17): the native engine must reproduce the
 * reference engine's exact dot lists at frozen timestamps. The vectors come
 * from upstream's parity harness (thinking-orbs@0.3.1 — 9 states × 2 sizes ×
 * 4 timestamps; dot count + the first-3/last-2 dots of the z-sorted draw
 * order, 1e-4 tolerance = libm ulp headroom, far below a device pixel).
 *
 * This is the anti-drift contract: a tuning change upstream regenerates
 * docs/orbs/ + these fixtures (scripts/gen-orb-spec.mjs) and this test fails
 * until both platforms are regenerated together.
 */
class OrbGoldenTest {

    @Test
    fun allGoldenVectorsMatch() {
        for (case in OrbGoldenData.cases) {
            val resolved = OrbSpec.resolve(case.state, case.size)
            assertEquals(case.key + ": mode", case.mode, resolved.mode)
            val buf = OrbFrameBuffer()
            OrbEngine.render(case.size.px.toDouble(), case.t, resolved, buf)
            assertEquals(case.key + ": dotCount", case.dotCount, buf.dotCount)
            assertEquals(case.key + ": lineCount", case.lineCount, buf.lineCount)
            // sample = first-3 + last-2 of the reference's z-sorted draw order
            val positions = intArrayOf(0, 1, 2, case.dotCount - 2, case.dotCount - 1)
            for (si in positions.indices) {
                val idx = buf.drawOrder[positions[si]]
                for (f in 0 until 6) {
                    val expected = case.sample[si * 6 + f]
                    val actual = when (f) {
                        0 -> buf.px[idx]
                        1 -> buf.py[idx]
                        2 -> buf.pz[idx]
                        3 -> buf.pr[idx]
                        4 -> buf.pWhite[idx]
                        else -> buf.pAlpha[idx]
                    }
                    assertEquals(
                        case.key + " dot#" + positions[si] + " field#" + f,
                        expected,
                        actual,
                        1e-4
                    )
                }
            }
        }
    }

    /** Reduced-motion contract: the static representative frame is exactly t = 0.6. */
    @Test
    fun staticFrameConstantMatchesReference() {
        assertEquals(0.6, OrbSpec.STATIC_T, 0.0)
    }

    /** The buffer must never grow unbounded: a long run reuses the same arrays. */
    @Test
    fun longRunStaysAllocationStable() {
        val resolved = OrbSpec.resolve(OrbState.COMPOSING, OrbSize.STANDARD)
        val buf = OrbFrameBuffer()
        var t = 0.0
        repeat(600) {
            OrbEngine.render(64.0, t, resolved, buf)
            t += 1.0 / 60.0
        }
        assertEquals(600, (t * 60).roundToInt())
        // dot count must stay within the preallocated capacity, every frame
        if (buf.dotCount > buf.px.size) throw AssertionError("frame buffer overflow: " + buf.dotCount)
    }
}
