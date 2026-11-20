package com.grapsee.gsai.ui.voice.position

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class VoicePositionMathTest {

    @Test
    fun `calibration follows upstream level phase path and requires both outer sides`() {
        val leftOnly = PositionCalibration().record(PositionSide.LEFT, calibrationFrames(-3.0, -30.0))!!
        assertNull(leftOnly.pan(DirectionCues(-3.0, -30.0)))

        val withAhead = leftOnly.record(PositionSide.AHEAD, calibrationFrames(0.0, 0.0))!!
        val complete = withAhead.record(PositionSide.RIGHT, calibrationFrames(3.0, 30.0))!!

        assertTrue(complete.isReady)
        assertEquals(-1.0, complete.pan(DirectionCues(-3.0, -30.0))!!, 0.01)
        assertEquals(1.0, complete.pan(DirectionCues(3.0, 30.0))!!, 0.01)
        assertEquals(CalibrationIssue.SIDES_TOO_ALIKE, calibration(0.1, 0.0, 0.1, 0.0).issue())
    }

    @Test
    fun `calibration refuses quiet-only windows and checks ahead lies between the sides`() {
        assertNull(PositionCalibration().record(PositionSide.LEFT, List(30) { frame(-60.0, 0.0, 0.0) }))
        val outside = calibration(-3.0, -30.0, 5.0, 50.0, ahead = 20.0 to 180.0)
        assertEquals(CalibrationIssue.AHEAD_OUTSIDE, outside.issue())
    }

    @Test
    fun `direction gate ignores silence and decision commits after sustained evidence`() {
        val gate = DirectionGate()
        repeat(5) { gate.step(frame(-60.0, 0.0, 0.0), 1.0 / 48.0) }
        repeat(80) { gate.step(frame(-30.0, 2.0, 35.0), 1.0 / 48.0) }
        assertTrue(gate.voiced)
        assertTrue(gate.cues != null)

        val decision = DirectionDecision()
        repeat(48) { decision.step(1.0, 1.0 / 48.0) }
        assertEquals(1, decision.side)
        repeat(120) { decision.step(-1.0, 1.0 / 48.0) }
        assertEquals(-1, decision.side)
        repeat(180) { decision.step(null, 1.0 / 48.0) }
        assertTrue(kotlin.math.abs(decision.pan) < 0.1)
    }

    @Test
    fun `distance decision is independent of loudness and returns small after quiet`() {
        val decision = DistanceDecision()
        repeat(180) { decision.step(0.99, 1.0 / 48.0) }
        assertEquals(VoiceDistance.LARGE, decision.distance)
        repeat(180) { decision.step(null, 1.0 / 48.0) }
        assertEquals(VoiceDistance.SMALL, decision.distance)
        assertEquals(0.0, decision.belief, 0.0001)
    }

    @Test
    fun `distance gate waits for sustained voice and releases after quiet`() {
        val gate = DistanceGate()
        repeat(5) { gate.step(frame(-60.0, 0.0, 0.0), 1.0 / 48.0) }
        assertNull(gate.features)

        repeat(30) { gate.step(frame(-30.0, 0.0, 0.0), 1.0 / 48.0) }
        assertNotNull(gate.features)

        repeat(60) { gate.step(frame(-60.0, 0.0, 0.0), 1.0 / 48.0) }
        assertNull(gate.features)
    }

    @Test
    fun `stereo analyzer distinguishes duplicate channels from separated channel content`() {
        val analyzer = StereoFrameAnalyzer(sampleRate = 48_000, channelCount = 2)
        val duplicate = ShortArray(1024 * 2)
        val separated = ShortArray(1024 * 2)
        for (frameIndex in 0 until 1024) {
            val sample = (12_000.0 * sin(2.0 * PI * 440.0 * frameIndex / 48_000.0)).toInt().toShort()
            duplicate[frameIndex * 2] = sample
            duplicate[frameIndex * 2 + 1] = sample
            separated[frameIndex * 2] = sample
            separated[frameIndex * 2 + 1] = 0
        }

        val mono = analyzer.analyse(duplicate)!!
        val stereo = analyzer.analyse(separated)!!
        assertEquals(0.0, mono.channelDifferenceRatio, 0.0)
        assertTrue(stereo.channelDifferenceRatio > 0.0005)
        assertNotNull(stereo.frame)

        val monoGate = StereoInputGate()
        repeat(48) { monoGate.step(channelCount = 2, voiced = true, differenceRatio = mono.channelDifferenceRatio) }
        assertEquals(PositionInput.MONO, monoGate.step(channelCount = 2, voiced = false, differenceRatio = 0.0))
        val stereoGate = StereoInputGate()
        repeat(3) { stereoGate.step(channelCount = 2, voiced = true, differenceRatio = stereo.channelDifferenceRatio) }
        assertEquals(PositionInput.STEREO, stereoGate.step(channelCount = 2, voiced = false, differenceRatio = 0.0))
        val uncertainGate = StereoInputGate()
        repeat(48) { uncertainGate.step(channelCount = 2, voiced = true, differenceRatio = 0.0001) }
        assertEquals(PositionInput.UNKNOWN, uncertainGate.step(channelCount = 2, voiced = false, differenceRatio = 0.0001))
        assertEquals(PositionInput.MONO, StereoInputGate().step(channelCount = 1, voiced = false, differenceRatio = 0.0))
    }

    @Test
    fun `pose without a distance classifier remains unavailable`() {
        val model = VoicePositionModelFile.decode(modelBytes(distancePose = "desk"))
        assertTrue(model.sideModel(PhonePose.HAND) != null)
        assertTrue(model.distanceModel(PhonePose.HAND) == null)
        assertTrue(model.distanceModel(PhonePose.DESK) != null)
        assertTrue(model.distanceModel(PhonePose.UNKNOWN) == null)
    }

    @Test
    fun `MFXL weights drive probabilities instead of fixed side values`() {
        val model = VoicePositionModelFile.decode(modelBytes(distancePose = "desk"))
        val sideModel = model.sideModel(PhonePose.HAND)!!
        val left = sideModel.probabilities(List(25) { if (it == 0) -1.0 else 0.0 })!!
        val right = sideModel.probabilities(List(25) { if (it == 0) 1.0 else 0.0 })!!
        assertTrue(left[0] > left[2])
        assertTrue(right[2] > right[0])
        assertEquals(1.0, left.sum(), 1e-9)
        assertEquals(1.0, right.sum(), 1e-9)

        val distance = model.distanceModel(PhonePose.DESK)!!
        assertTrue(distance.large(listOf(-1.0)) < 0.5)
        assertTrue(distance.large(listOf(1.0)) > 0.5)
        assertFalse(distance.large(emptyList()) != null)
    }

    private fun calibration(
        leftLevel: Double,
        leftPhase: Double,
        rightLevel: Double,
        rightPhase: Double,
        ahead: Pair<Double, Double> = 0.0 to 0.0,
    ): PositionCalibration {
        var value = PositionCalibration()
        value = value.record(PositionSide.LEFT, calibrationFrames(leftLevel, leftPhase))!!
        value = value.record(PositionSide.AHEAD, calibrationFrames(ahead.first, ahead.second))!!
        value = value.record(PositionSide.RIGHT, calibrationFrames(rightLevel, rightPhase))!!
        return value
    }

    private fun calibrationFrames(level: Double, phase: Double): List<StereoFrame> =
        List(5) { frame(-60.0, level, phase) } + List(25) { frame(-30.0, level, phase) }

    private fun frame(power: Double, level: Double, phase: Double) = StereoFrame(
        cues = DirectionCues(level, phase),
        features = List(25) { 0.0 },
        power = power,
        bandPower = List(13) { -20.0 },
        coherence = List(13) { 0.8 },
    )

    private fun modelBytes(distancePose: String): ByteArray {
        val featureCount = 25
        val sideFloats = DoubleArray(featureCount * 5 + 3)
        for (i in 0 until featureCount) sideFloats[featureCount + i] = 1.0
        sideFloats[featureCount * 2] = -3.0
        sideFloats[featureCount * 2 + 2] = 3.0
        val distanceFloats = doubleArrayOf(0.0, 1.0, 4.0, 0.0)
        val sideBytes = sideFloats.toLittleEndianBytes()
        val distanceBytes = distanceFloats.toLittleEndianBytes()
        val json = """
            {
              "kind":"position","version":"test",
              "levelBands":[150,250,400,600,800,1000,1300,1700,2200,2800,3500,4500,6000,9000],
              "phaseBands":[150,300,500,800,1200,1800,2700],"features":25,
              "poses":[{"name":"hand","offset":0,"length":${sideBytes.size}},
                       {"name":"desk","offset":0,"length":${sideBytes.size}}],
              "distance":[{"name":"$distancePose","offset":${sideBytes.size},"length":${distanceBytes.size},"features":1}]
            }
        """.trimIndent().toByteArray(Charsets.UTF_8)
        val payload = sideBytes + distanceBytes
        val header = ByteArray(8 + json.size + payload.size)
        "MFXL".toByteArray(Charsets.US_ASCII).copyInto(header, 0)
        writeLittleEndianInt(json.size, header, 4)
        json.copyInto(header, 8)
        payload.copyInto(header, 8 + json.size)
        return header
    }

    private fun DoubleArray.toLittleEndianBytes(): ByteArray {
        val result = ByteArray(size * 4)
        forEachIndexed { index, value -> writeLittleEndianInt(java.lang.Float.floatToIntBits(value.toFloat()), result, index * 4) }
        return result
    }

    private fun writeLittleEndianInt(value: Int, target: ByteArray, offset: Int) {
        target[offset] = value.toByte()
        target[offset + 1] = (value ushr 8).toByte()
        target[offset + 2] = (value ushr 16).toByte()
        target[offset + 3] = (value ushr 24).toByte()
    }
}
