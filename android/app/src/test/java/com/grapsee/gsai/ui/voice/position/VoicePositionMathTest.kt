package com.grapsee.gsai.ui.voice.position

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePositionMathTest {

    @Test
    fun `calibrated stereo cues map left ahead and right without inventing a distance`() {
        val calibration = calibration(
            left = StereoCue(-3.0, -4.0),
            ahead = StereoCue(0.0, 0.0),
            right = StereoCue(3.0, 4.0),
        )

        val left = estimateVoicePosition(true, true, calibration, StereoCue(-3.0, -4.0))
        val ahead = estimateVoicePosition(true, true, calibration, StereoCue(0.0, 0.0))
        val right = estimateVoicePosition(true, true, calibration, StereoCue(3.0, 4.0))

        assertEquals(PositionSide.LEFT, left?.side)
        assertEquals(PositionSide.AHEAD, ahead?.side)
        assertEquals(PositionSide.RIGHT, right?.side)
        assertTrue((left?.pan ?: 0f) < -0.9f)
        assertTrue((ahead?.pan ?: 1f) in -0.1f..0.1f)
        assertTrue((right?.pan ?: 0f) > 0.9f)
    }

    @Test
    fun `direction stays unavailable until both outer sides are calibrated`() {
        val calibration = PositionCalibration().record(PositionSide.LEFT, repeated(StereoCue(-2.0, -2.0)))!!

        assertNull(calibration.estimate(StereoCue(-2.0, -2.0)))
        assertNull(estimateVoicePosition(true, true, calibration, StereoCue(-2.0, -2.0)))
    }

    @Test
    fun `mono and quiet input never produce a direction reading`() {
        val calibration = calibration(
            left = StereoCue(-3.0, -4.0),
            ahead = StereoCue(0.0, 0.0),
            right = StereoCue(3.0, 4.0),
        )
        val cue = StereoCue(-3.0, -4.0)

        assertNull(estimateVoicePosition(stereoAvailable = false, voiced = true, calibration = calibration, cue = cue))
        assertNull(estimateVoicePosition(stereoAvailable = true, voiced = false, calibration = calibration, cue = cue))
        assertNull(estimateVoicePosition(stereoAvailable = true, voiced = true, calibration = calibration, cue = null))
    }

    @Test
    fun `calibration rejects indistinguishable left and right points`() {
        val calibration = calibration(
            left = StereoCue(0.1, 0.1),
            ahead = StereoCue(0.0, 0.0),
            right = StereoCue(0.1, 0.1),
        )

        assertEquals(CalibrationIssue.SIDES_TOO_ALIKE, calibration.issue())
        assertNull(calibration.estimate(StereoCue(0.1, 0.1)))
    }

    @Test
    fun `a cue far from the calibration path is not assigned a guessed side`() {
        val calibration = calibration(
            left = StereoCue(-3.0, -4.0),
            ahead = StereoCue(0.0, 0.0),
            right = StereoCue(3.0, 4.0),
        )

        assertNull(calibration.estimate(StereoCue(30.0, 40.0)))
    }

    private fun calibration(
        left: StereoCue,
        ahead: StereoCue,
        right: StereoCue,
    ): PositionCalibration = PositionCalibration()
        .record(PositionSide.LEFT, repeated(left))!!
        .record(PositionSide.AHEAD, repeated(ahead))!!
        .record(PositionSide.RIGHT, repeated(right))!!

    private fun repeated(cue: StereoCue): List<StereoCue> = List(40) { cue }
}
