package com.grapsee.gsai.ui.voice.position

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** The labels and gates below mirror the MIT-licensed Libraries.dev VoicePositionKit. */
internal enum class PositionSide(val label: String, val modelName: String) {
    LEFT("Left", "left"),
    AHEAD("Ahead", "ahead"),
    RIGHT("Right", "right"),
}

/** Android may not expose a reliable pose sensor, so UNKNOWN is never mapped to a pose model. */
internal enum class PhonePose(val label: String, val modelName: String?) {
    HAND("In hand", "hand"),
    DESK("On desk", "desk"),
    UNKNOWN("Pose unavailable", null),
}

internal enum class PositionInput { UNKNOWN, STEREO, MONO, UNAVAILABLE }
internal enum class VoiceDistance { SMALL, LARGE }

/** Device-route validation only; this local heuristic does not prove spatial accuracy. */
internal class StereoInputGate {
    private val window = java.util.ArrayDeque<Pair<Boolean, Boolean>>()
    private var distinctFrames = 0
    private var duplicatedFrames = 0

    fun step(channelCount: Int, voiced: Boolean, differenceRatio: Double): PositionInput {
        if (channelCount < 2) return PositionInput.MONO
        if (!voiced) return current()

        val distinct = differenceRatio > MIN_DIFFERENCE_RATIO
        val duplicated = differenceRatio <= DUPLICATED_MONO_RATIO
        window.addLast(distinct to duplicated)
        if (distinct) distinctFrames++
        if (duplicated) duplicatedFrames++
        if (window.size > WINDOW_FRAMES) {
            val (wasDistinct, wasDuplicated) = window.removeFirst()
            if (wasDistinct) distinctFrames--
            if (wasDuplicated) duplicatedFrames--
        }
        return current()
    }

    private fun current(): PositionInput = when {
        distinctFrames >= MIN_DISTINCT_FRAMES -> PositionInput.STEREO
        window.size >= WINDOW_FRAMES && duplicatedFrames >= WINDOW_FRAMES -> PositionInput.MONO
        else -> PositionInput.UNKNOWN
    }

    private companion object {
        const val MIN_DIFFERENCE_RATIO = 0.0005
        const val DUPLICATED_MONO_RATIO = 0.000001
        const val MIN_DISTINCT_FRAMES = 3
        const val WINDOW_FRAMES = 48
    }
}
internal enum class CalibrationIssue { SIDES_TOO_ALIKE, AHEAD_OUTSIDE }

internal data class DirectionCues(val level: Double, val phase: Double) {
    val phaseX: Double get() = cos(phase * PI / 180.0)
    val phaseY: Double get() = sin(phase * PI / 180.0)
}

/** One 1024-sample stereo frame with the same features used by VoicePositionKit. */
internal data class StereoFrame(
    val cues: DirectionCues,
    val features: List<Double>,
    val power: Double,
    val bandPower: List<Double>,
    val coherence: List<Double>,
)

/** Only displayed when the source is a live, voiced stereo frame. */
internal data class DirectionEstimate(
    val pan: Float,
    val side: PositionSide,
    val probabilities: List<Double>?,
    val modelBased: Boolean,
)

/** A device-local calibration of the upstream kit's level/phase cues, not a distance estimate. */
internal data class PositionCalibration(
    val points: Map<PositionSide, DirectionCues> = emptyMap(),
    val spreads: Map<PositionSide, DirectionCues> = emptyMap(),
) {
    val isReady: Boolean get() = points[PositionSide.LEFT] != null && points[PositionSide.RIGHT] != null
    val hasAny: Boolean get() = points.isNotEmpty()

    fun point(side: PositionSide): DirectionCues? = points[side]

    fun issue(): CalibrationIssue? {
        val left = points[PositionSide.LEFT] ?: return null
        val right = points[PositionSide.RIGHT] ?: return null
        val delta = z(right) - z(left)
        val length = dot(delta, delta)
        if (length < 9.0) return CalibrationIssue.SIDES_TOO_ALIKE
        points[PositionSide.AHEAD]?.let { ahead ->
            val projection = dot(z(ahead) - z(left), delta) / length
            if (projection < 0.05 || projection > 0.95) return CalibrationIssue.AHEAD_OUTSIDE
        }
        return null
    }

    /** Same nearest-path projection used by VoicePositionKit's calibration fallback. */
    fun pan(cues: DirectionCues): Double? {
        val left = points[PositionSide.LEFT] ?: return null
        val right = points[PositionSide.RIGHT] ?: return null
        val path = buildList {
            add(-1.0 to z(left))
            points[PositionSide.AHEAD]?.let { add(0.0 to z(it)) }
            add(1.0 to z(right))
        }
        val current = z(cues)
        val fits = path.zipWithNext().map { (start, end) ->
            val delta = end.second - start.second
            val length = dot(delta, delta)
            val offset = current - start.second
            val t = if (length > 1e-9) (dot(offset, delta) / length).coerceIn(0.0, 1.0) else 0.5
            val nearest = start.second + delta * t
            val distance = sqrt(dot(nearest - current, nearest - current))
            distance to (start.first + t * (end.first - start.first))
        }
        val nearestDistance = fits.minOfOrNull { it.first } ?: return null
        val weights = fits.map { (distance, _) ->
            exp(-((distance - nearestDistance).pow(2)) / (2.0 * 0.3 * 0.3))
        }
        val weightSum = weights.sum().takeIf { it > 0.0 } ?: return null
        val blended = fits.indices.sumOf { weights[it] * fits[it].second } / weightSum
        val familiarity = ((6.5 - nearestDistance) / 1.5).coerceIn(0.0, 1.0)
        return blended * familiarity
    }

    fun record(side: PositionSide, frames: List<StereoFrame>): PositionCalibration? {
        val measured = measure(frames) ?: return null
        return copy(
            points = points + (side to measured.first),
            spreads = spreads + (side to measured.second),
        )
    }

    /** VoicePositionKit's robust calibration window: median level, circular phase mean, and measured spread. */
    private fun measure(frames: List<StereoFrame>): Pair<DirectionCues, DirectionCues>? {
        if (frames.size < MIN_CALIBRATION_FRAMES) return null
        val powers = frames.map(StereoFrame::power).sorted()
        val peak = powers.last()
        val quiet = powers[powers.size / 10]
        if (peak - quiet <= 20.0) return null
        val strong = frames.filter { it.power >= max(peak - 15.0, quiet + 20.0) }
        if (strong.size < MIN_STRONG_FRAMES) return null

        val levels = strong.map { it.cues.level }.sorted()
        val phaseMeanX = strong.sumOf { it.cues.phaseX } / strong.size
        val phaseMeanY = strong.sumOf { it.cues.phaseY } / strong.size
        val resultant = sqrt(phaseMeanX * phaseMeanX + phaseMeanY * phaseMeanY).coerceIn(1e-6, 1.0)
        val cues = DirectionCues(
            level = levels[levels.size / 2],
            phase = atan2(phaseMeanY, phaseMeanX) * 180.0 / PI,
        )
        val lower = levels[levels.size / 4]
        val upper = levels[levels.size * 3 / 4]
        val spread = DirectionCues(
            level = (upper - lower) / 1.35,
            phase = sqrt(-2.0 * ln(resultant)) * 180.0 / PI,
        )
        return cues to spread
    }

    private fun z(cues: DirectionCues): DoubleArray {
        val spread = combinedSpread()
        val phaseScale = spread.phase * PI / 180.0
        return doubleArrayOf(cues.level / spread.level, cues.phaseX / phaseScale, cues.phaseY / phaseScale)
    }

    private fun combinedSpread(): DirectionCues {
        val all = spreads.values
        if (all.isEmpty()) return DirectionCues(1.5, 20.0)
        return DirectionCues(
            level = max(0.5, all.map(DirectionCues::level).average()),
            phase = max(6.0, all.map(DirectionCues::phase).average()),
        )
    }

    private fun dot(a: DoubleArray, b: DoubleArray): Double = a.indices.sumOf { a[it] * b[it] }
    private operator fun DoubleArray.minus(other: DoubleArray) = DoubleArray(size) { this[it] - other[it] }
    private operator fun DoubleArray.plus(other: DoubleArray) = DoubleArray(size) { this[it] + other[it] }
    private operator fun DoubleArray.times(scale: Double) = DoubleArray(size) { this[it] * scale }

    private companion object {
        const val MIN_CALIBRATION_FRAMES = 20
        const val MIN_STRONG_FRAMES = 15
    }
}

/** The upstream voiced-frame gate. Timing constants are the VoicePositionKit values, not fitted outputs. */
internal class DirectionGate {
    var features: List<Double>? = null
        private set
    var cues: DirectionCues? = null
        private set
    var voiced: Boolean = false
        private set

    private var level: Double? = null
    private var phaseX = 1.0
    private var phaseY = 0.0
    private var floor: Double? = null
    private var recent: Double? = null
    private var quiet = 0.0

    fun step(frame: StereoFrame, dt: Double) {
        val power = frame.power
        val previousFloor = floor ?: power
        floor = if (power < previousFloor) power else previousFloor + (power - previousFloor) * (1.0 - exp(-dt / 4.0))
        val snr = power - (floor ?: power)
        val previousRecent = recent ?: power
        val rise = max(0.0, power - previousRecent)
        recent = previousRecent + (power - previousRecent) * (1.0 - exp(-dt / 0.15))
        voiced = snr > VOICED_ABOVE
        if (!voiced) {
            quiet += dt
            if (quiet > HOLD_FOR) {
                level = null
                features = null
                cues = null
            }
            return
        }
        quiet = 0.0
        val oldLevel = level
        if (oldLevel == null) {
            level = frame.cues.level
            phaseX = frame.cues.phaseX
            phaseY = frame.cues.phaseY
            features = frame.features
        } else {
            val weight = min(1.0, (snr - VOICED_ABOVE) / (FULL_ABOVE - VOICED_ABOVE)) * min(3.0, 1.0 + rise / 6.0)
            val amount = 1.0 - exp(-dt * weight / EASE)
            level = oldLevel + (frame.cues.level - oldLevel) * amount
            phaseX += (frame.cues.phaseX - phaseX) * amount
            phaseY += (frame.cues.phaseY - phaseY) * amount
            val oldFeatures = features
            features = if (oldFeatures != null && oldFeatures.size == frame.features.size) {
                oldFeatures.indices.map { i -> oldFeatures[i] + (frame.features[i] - oldFeatures[i]) * amount }
            } else frame.features
        }
        cues = level?.let { DirectionCues(it, atan2(phaseY, phaseX) * 180.0 / PI) }
    }

    fun reset() {
        features = null
        cues = null
        voiced = false
        level = null
        phaseX = 1.0
        phaseY = 0.0
        floor = null
        recent = null
        quiet = 0.0
    }

    private companion object {
        const val VOICED_ABOVE = 15.0
        const val FULL_ABOVE = 30.0
        const val EASE = 0.25
        const val HOLD_FOR = 1.0
    }
}

/** Commit hysteresis from VoicePositionKit: a sustained reading before a side change, with centre transitions. */
internal class DirectionDecision {
    var pan: Double = 0.0
        private set
    var side: Int = 0
        private set
    private var belief = 0.0
    private var held = 0.0

    fun step(reading: Double?, dt: Double) {
        belief = if (reading != null) {
            belief + (reading - belief) * (1.0 - exp(-dt / BUILD))
        } else {
            belief * exp(-dt / FADE)
        }
        if (side == 0) {
            val targetSide = when {
                belief > TAKE -> 1
                belief < -TAKE -> -1
                else -> 0
            }
            held = if (targetSide == 0) 0.0 else held + dt
            if (targetSide != 0 && held >= HOLD) {
                side = targetSide
                held = 0.0
            }
        } else if (belief * side < KEEP) {
            side = 0
        }
        val target = if (side == 0) 0.0 else side * min(1.0, abs(belief) / 0.8)
        pan += (target - pan) * (1.0 - exp(-dt / EASE))
    }

    fun reset() {
        pan = 0.0
        side = 0
        belief = 0.0
        held = 0.0
    }

    private companion object {
        const val BUILD = 0.5
        const val TAKE = 0.5
        const val KEEP = 0.25
        const val HOLD = 0.15
        const val FADE = 1.5
        const val EASE = 0.6
    }
}

/** Upstream distance features: the desk model currently uses channel coherence at 800–1700 Hz, not loudness. */
internal class DistanceGate {
    var features: List<Double>? = null
        private set
    private var floor: Double? = null
    private var swingMean: DoubleArray? = null
    private val swingVariance = DoubleArray(3)
    private var quiet = 0.0
    private var voiced = 0.0

    fun step(frame: StereoFrame, dt: Double) {
        if (frame.bandPower.size != LEVEL_BANDS.size - 1 || frame.coherence.size != LEVEL_BANDS.size - 1) return
        val power = frame.power
        val oldFloor = floor ?: power
        floor = if (power < oldFloor) power else oldFloor + (power - oldFloor) * (1.0 - exp(-dt / 4.0))
        val snr = power - (floor ?: power)
        val broad = doubleArrayOf(
            frame.bandPower.subList(0, 4).average(),
            frame.bandPower.subList(4, 9).average(),
            frame.bandPower.subList(9, 13).average(),
        )
        val amount = 1.0 - exp(-dt / SWING_OVER)
        val means = swingMean ?: broad.copyOf()
        for (i in broad.indices) {
            means[i] += (broad[i] - means[i]) * amount
            val delta = broad[i] - means[i]
            swingVariance[i] += (delta * delta - swingVariance[i]) * amount
        }
        swingMean = means

        if (snr <= VOICED_ABOVE) {
            quiet += dt
            voiced = 0.0
            if (quiet > HOLD_FOR) features = null
            return
        }
        voiced += dt
        if (features == null && voiced < START_AFTER) return
        quiet = 0.0
        val all = frame.bandPower.subList(0, 13).average()
        val current = buildList {
            addAll(frame.coherence)
            frame.bandPower.take(13).forEach { add(it - all) }
            swingVariance.forEach { add(sqrt(it)) }
            add(snr)
        }
        val old = features
        if (old == null) {
            features = current
            return
        }
        val weight = min(1.0, (snr - VOICED_ABOVE) / (FULL_ABOVE - VOICED_ABOVE))
        val smoothing = 1.0 - exp(-dt * weight / EASE)
        features = old.indices.map { i -> old[i] + (current[i] - old[i]) * smoothing }
    }

    fun reset() {
        features = null
        floor = null
        swingMean = null
        swingVariance.fill(0.0)
        quiet = 0.0
        voiced = 0.0
    }

    private companion object {
        const val VOICED_ABOVE = 15.0
        const val FULL_ABOVE = 30.0
        const val EASE = 0.6
        const val SWING_OVER = 1.0
        const val HOLD_FOR = 1.0
        const val START_AFTER = 0.15
    }
}

/** Distance transitions use the upstream model's belief and hysteresis. */
internal class DistanceDecision {
    var distance: VoiceDistance = VoiceDistance.SMALL
        private set
    var belief: Double = 0.0
        private set
    private var held = 0.0
    private var quiet = 0.0

    fun step(reading: Double?, dt: Double) {
        if (reading != null) {
            quiet = 0.0
            belief += (reading - belief) * (1.0 - exp(-dt / BUILD))
        } else {
            quiet += dt
            if (quiet >= FORGET_AFTER) {
                belief = 0.0
                distance = VoiceDistance.SMALL
            }
        }
        val crossing = if (distance == VoiceDistance.SMALL) belief > TO_LARGE else belief < TO_SMALL
        held = if (crossing) held + dt else 0.0
        if (crossing && held >= HOLD) {
            distance = if (distance == VoiceDistance.SMALL) VoiceDistance.LARGE else VoiceDistance.SMALL
            held = 0.0
        }
    }

    fun reset() {
        distance = VoiceDistance.SMALL
        belief = 0.0
        held = 0.0
        quiet = 0.0
    }

    private companion object {
        const val BUILD = 0.8
        const val TO_LARGE = 0.65
        const val TO_SMALL = 0.35
        const val HOLD = 0.25
        const val FORGET_AFTER = 2.0
    }
}

internal val LEVEL_BANDS_HZ = doubleArrayOf(150.0, 250.0, 400.0, 600.0, 800.0, 1000.0, 1300.0, 1700.0, 2200.0, 2800.0, 3500.0, 4500.0, 6000.0, 9000.0)
internal val PHASE_BANDS_HZ = doubleArrayOf(150.0, 300.0, 500.0, 800.0, 1200.0, 1800.0, 2700.0)
