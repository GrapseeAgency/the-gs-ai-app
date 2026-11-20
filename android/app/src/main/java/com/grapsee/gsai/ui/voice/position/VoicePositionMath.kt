package com.grapsee.gsai.ui.voice.position

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Relative direction as heard by the person holding the phone. */
internal enum class PositionSide(val label: String) {
    LEFT("Left"),
    AHEAD("Ahead"),
    RIGHT("Right"),
}

/** The phone's current physical pose; each pose has its own calibration. */
internal enum class PhonePose(val label: String) {
    HAND("In hand"),
    DESK("On desk"),
    UNKNOWN("Pose unavailable"),
}

/** Two measured stereo cues. Neither contains an absolute distance estimate. */
internal data class StereoCue(
    val levelDifferenceDb: Double,
    val lagSamples: Double,
)

internal data class CalibratedCue(
    val levelDifferenceDb: Double,
    val lagSamples: Double,
    val levelSpreadDb: Double,
    val lagSpreadSamples: Double,
)

internal enum class CalibrationIssue {
    SIDES_TOO_ALIKE,
    AHEAD_OUTSIDE,
}

internal data class DirectionEstimate(
    /** −1 = calibrated left, 0 = calibrated ahead, +1 = calibrated right. */
    val pan: Float,
    val side: PositionSide,
    val confidence: Float,
)

/**
 * Device-local, pose-specific calibration. A voice is only assigned a side
 * when both outer sides were measured and the current stereo cue fits that
 * calibrated path. The optional ahead point improves the centre mapping.
 */
internal data class PositionCalibration(
    val left: CalibratedCue? = null,
    val ahead: CalibratedCue? = null,
    val right: CalibratedCue? = null,
) {
    val isReady: Boolean get() = left != null && right != null

    fun point(side: PositionSide): CalibratedCue? = when (side) {
        PositionSide.LEFT -> left
        PositionSide.AHEAD -> ahead
        PositionSide.RIGHT -> right
    }

    fun record(side: PositionSide, samples: List<StereoCue>): PositionCalibration? {
        if (samples.size < MIN_CALIBRATION_FRAMES) return null
        val levels = samples.map(StereoCue::levelDifferenceDb)
        val lags = samples.map(StereoCue::lagSamples)
        val measured = CalibratedCue(
            levelDifferenceDb = median(levels),
            lagSamples = median(lags),
            levelSpreadDb = max(MIN_LEVEL_SPREAD_DB, medianAbsoluteDeviation(levels)),
            lagSpreadSamples = max(MIN_LAG_SPREAD, medianAbsoluteDeviation(lags)),
        )
        return when (side) {
            PositionSide.LEFT -> copy(left = measured)
            PositionSide.AHEAD -> copy(ahead = measured)
            PositionSide.RIGHT -> copy(right = measured)
        }
    }

    /** Reject a calibration that cannot distinguish its outer sides. */
    fun issue(): CalibrationIssue? {
        val l = left ?: return null
        val r = right ?: return null
        val levelScale = max(MIN_LEVEL_SPREAD_DB, (l.levelSpreadDb + r.levelSpreadDb) / 2.0)
        val lagScale = max(MIN_LAG_SPREAD, (l.lagSpreadSamples + r.lagSpreadSamples) / 2.0)
        val dx = (r.levelDifferenceDb - l.levelDifferenceDb) / levelScale
        val dy = (r.lagSamples - l.lagSamples) / lagScale
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared < MIN_OUTER_SEPARATION_SQUARED) return CalibrationIssue.SIDES_TOO_ALIKE

        ahead?.let { a ->
            val ax = (a.levelDifferenceDb - l.levelDifferenceDb) / levelScale
            val ay = (a.lagSamples - l.lagSamples) / lagScale
            val projection = (ax * dx + ay * dy) / lengthSquared
            if (projection < MIN_AHEAD_PROJECTION || projection > MAX_AHEAD_PROJECTION) {
                return CalibrationIssue.AHEAD_OUTSIDE
            }
        }
        return null
    }

    /**
     * Projects the live stereo cue onto left→ahead→right in normalized cue
     * space. Far-off cues fade to no reading instead of being guessed.
     */
    fun estimate(cue: StereoCue): DirectionEstimate? {
        val l = left ?: return null
        val r = right ?: return null
        if (issue() != null) return null

        val points = buildList {
            add(-1.0 to normalized(l))
            ahead?.let { add(0.0 to normalized(it)) }
            add(1.0 to normalized(r))
        }
        val current = normalized(cue)
        val fits = points.zipWithNext().map { (start, end) ->
            val dx = end.second.first - start.second.first
            val dy = end.second.second - start.second.second
            val lengthSquared = dx * dx + dy * dy
            val px = current.first - start.second.first
            val py = current.second - start.second.second
            val t = if (lengthSquared > 1e-9) {
                ((px * dx + py * dy) / lengthSquared).coerceIn(0.0, 1.0)
            } else {
                0.5
            }
            val offsetX = start.second.first + t * dx - current.first
            val offsetY = start.second.second + t * dy - current.second
            val distance = sqrt(offsetX * offsetX + offsetY * offsetY)
            val pan = start.first + t * (end.first - start.first)
            distance to pan
        }
        val nearest = fits.minOfOrNull { it.first } ?: return null
        val familiarity = ((MAX_FIT_DISTANCE - nearest) / FIT_FADE_DISTANCE).coerceIn(0.0, 1.0)
        if (familiarity <= 0.0) return null

        val weights = fits.map { (distance, _) ->
            exp(-((distance - nearest) * (distance - nearest)) / (2.0 * BLEND_SIGMA * BLEND_SIGMA))
        }
        val totalWeight = weights.sum().takeIf { it > 0.0 } ?: return null
        val blendedPan = fits.indices.sumOf { weights[it] * fits[it].second } / totalWeight
        val pan = (blendedPan * familiarity).coerceIn(-1.0, 1.0).toFloat()
        val side = when {
            pan <= -SIDE_THRESHOLD -> PositionSide.LEFT
            pan >= SIDE_THRESHOLD -> PositionSide.RIGHT
            else -> PositionSide.AHEAD
        }
        return DirectionEstimate(pan, side, familiarity.toFloat())
    }

    private fun normalized(cue: CalibratedCue): Pair<Double, Double> {
        val points = listOfNotNull(left, ahead, right)
        val levelScale = max(MIN_LEVEL_SPREAD_DB, points.map(CalibratedCue::levelSpreadDb).average())
        val lagScale = max(MIN_LAG_SPREAD, points.map(CalibratedCue::lagSpreadSamples).average())
        return cue.levelDifferenceDb / levelScale to cue.lagSamples / lagScale
    }

    private fun normalized(cue: StereoCue): Pair<Double, Double> {
        val points = listOfNotNull(left, ahead, right)
        val levelScale = max(MIN_LEVEL_SPREAD_DB, points.map(CalibratedCue::levelSpreadDb).average())
        val lagScale = max(MIN_LAG_SPREAD, points.map(CalibratedCue::lagSpreadSamples).average())
        return cue.levelDifferenceDb / levelScale to cue.lagSamples / lagScale
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        } else {
            sorted[middle]
        }
    }

    private fun medianAbsoluteDeviation(values: List<Double>): Double {
        val center = median(values)
        return median(values.map { kotlin.math.abs(it - center) }) * MAD_TO_SIGMA
    }

    private companion object {
        const val MIN_CALIBRATION_FRAMES = 30
        const val MIN_LEVEL_SPREAD_DB = 0.5
        const val MIN_LAG_SPREAD = 0.5
        const val MIN_OUTER_SEPARATION_SQUARED = 9.0
        const val MIN_AHEAD_PROJECTION = 0.05
        const val MAX_AHEAD_PROJECTION = 0.95
        const val MAX_FIT_DISTANCE = 6.5
        const val FIT_FADE_DISTANCE = 1.5
        const val BLEND_SIGMA = 0.3
        const val SIDE_THRESHOLD = 0.33f
        const val MAD_TO_SIGMA = 1.4826
    }
}

/** No reading is possible from mono/quiet input, even if a calibration exists. */
internal fun estimateVoicePosition(
    stereoAvailable: Boolean,
    voiced: Boolean,
    calibration: PositionCalibration,
    cue: StereoCue?,
): DirectionEstimate? {
    if (!stereoAvailable || !voiced || cue == null) return null
    return calibration.estimate(cue)
}
