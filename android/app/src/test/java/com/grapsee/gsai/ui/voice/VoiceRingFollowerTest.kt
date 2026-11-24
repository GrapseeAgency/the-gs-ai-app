package com.grapsee.gsai.ui.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceRingFollowerTest {
    private fun advance(
        f: VoiceRingFollower, start: Int, frames: Int, level: Double,
        direction: Double = 0.0, processing: Boolean = false
    ): RingInput {
        var result = f.step(start/60.0, level, processing, direction)
        for (i in start+1..start+frames) {
            result = f.step(i/60.0, level, processing, direction)
        }
        return result
    }

    @Test fun actualVoiceOpensTheRingAndSpeedsItsClock() {
        val idle = advance(VoiceRingFollower(),0,180,0.0)
        val voice = advance(VoiceRingFollower(),0,180,0.85)
        assertTrue(voice.soft > idle.soft + 0.5)
        assertTrue(voice.drive > idle.drive * 2)
        assertEquals(0.0, voice.pan, 1e-9)
    }

    @Test fun silenceDoesNotInventASideAndARealSideEasesHome() {
        val f = VoiceRingFollower()
        val idle = advance(f,0,120,0.0,1.0)
        assertEquals(0.0,idle.pan,1e-9)
        val right = advance(f,121,240,0.85,1.0)
        assertTrue(right.pan > 0.9)
        val settled = advance(f,362,600,0.0)
        assertTrue(kotlin.math.abs(settled.pan)<0.01)
    }

    @Test fun processingEasesInAndIgnoresDirection() {
        val f = VoiceRingFollower()
        val p = advance(f,0,180,0.0,1.0,true)
        assertTrue(p.processing>0.98)
        assertTrue(p.spin>1.0)
        assertEquals(0.0,p.pan,1e-9)
    }
}
