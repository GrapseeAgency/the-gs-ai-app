package com.grapsee.gsai.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetalSendBendTest {
    @Test fun neutralAndDeadzoneLeaveTheOriginalCircleUnchanged() {
        val bend = MetalCircleBend()
        assertNull(bend.step(0.0, 38f, 0.0, 0.0))
        assertNull(bend.step(1.0 / 60, 38f, 0.02, 0.0))
    }

    @Test fun tiltDeformsTheRingTowardTheOriginalContactAndSettles() {
        val bend = MetalCircleBend()
        var field: MetalCircleField? = null
        repeat(120) { field = bend.step(it / 60.0, 38f, 0.45, 0.0) }
        val f = requireNotNull(field)
        assertEquals(38.0, f.cx, 0.001)
        assertEquals(19.0, f.cy, 0.001)
        assertTrue(f.deform(38.0, 19.0).first > 43.0)
        assertEquals(19.0, f.deform(38.0, 19.0).second, 0.001)
        repeat(240) { field = bend.step((it + 120) / 60.0, 38f, 0.0, 0.0) }
        assertNull(field)
    }

    @Test fun leftAndRightTiltsAreSymmetric() {
        val left = MetalCircleBend()
        val right = MetalCircleBend()
        var l: MetalCircleField? = null
        var r: MetalCircleField? = null
        repeat(90) {
            l = left.step(it / 60.0, 38f, -0.45, 0.0)
            r = right.step(it / 60.0, 38f, 0.45, 0.0)
        }
        assertNotNull(l)
        assertNotNull(r)
        assertEquals(38.0, requireNotNull(l).deform(0.0, 19.0).first +
            requireNotNull(r).deform(38.0, 19.0).first, 0.0001)
    }
}
