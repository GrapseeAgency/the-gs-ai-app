package com.grapsee.gsai.ui.voice.position

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal data class AudioFrameAnalysis(
    val level: Float,
    val frame: StereoFrame?,
    val channelDifferenceRatio: Double,
)

/**
 * Kotlin/Android counterpart of Libraries.dev VoiceCore.SpectrumAnalyser:
 * 1024-sample Blackman-windowed FFT, the same frequency bands, cross-spectrum
 * cues and model features. This extracts measurements only; the model or
 * calibration decides direction.
 */
internal class StereoFrameAnalyzer(
    private val sampleRate: Int,
    private val channelCount: Int,
) {
    private val window = DoubleArray(FRAME_SIZE) { index ->
        val alpha = 0.16
        val a0 = (1.0 - alpha) / 2.0
        val a1 = 0.5
        val a2 = alpha / 2.0
        val phase = 2.0 * PI * index / FRAME_SIZE
        a0 - a1 * cos(phase) + a2 * cos(2.0 * phase)
    }
    private val twiddleReal = DoubleArray(FRAME_SIZE / 2) { cos(-2.0 * PI * it / FRAME_SIZE) }
    private val twiddleImag = DoubleArray(FRAME_SIZE / 2) { sin(-2.0 * PI * it / FRAME_SIZE) }

    fun analyse(interleaved: ShortArray): AudioFrameAnalysis? {
        if (channelCount !in 1..2 || interleaved.size != FRAME_SIZE * channelCount || sampleRate <= 0) return null
        var energyLeft = 0.0
        var energyRight = 0.0
        var differenceEnergy = 0.0
        for (i in 0 until FRAME_SIZE) {
            val left = interleaved[i * channelCount].toDouble() / PCM_SCALE
            val right = if (channelCount == 2) interleaved[i * channelCount + 1].toDouble() / PCM_SCALE else left
            energyLeft += left * left
            energyRight += right * right
            val delta = left - right
            differenceEnergy += delta * delta
        }
        val rms = sqrt((energyLeft + energyRight) / (2.0 * FRAME_SIZE)).coerceIn(0.0, 1.0)
        val differenceRatio = if (channelCount == 2) {
            sqrt(differenceEnergy / FRAME_SIZE) / max(rms, RMS_FLOOR)
        } else 0.0
        if (channelCount < 2) return AudioFrameAnalysis(rms.toFloat(), null, differenceRatio)

        val left = spectrum(interleaved, 0)
        val right = spectrum(interleaved, 1)
        val levelEdges = LEVEL_BANDS_HZ
        val phaseEdges = PHASE_BANDS_HZ
        val bandCount = levelEdges.size - 1
        val binHz = sampleRate.toDouble() / FRAME_SIZE
        var energyBandLeft = 1e-24
        var energyBandRight = 1e-24
        var power = 1e-24
        var phaseReal = 0.0
        var phaseImag = 0.0
        val bandLeft = DoubleArray(bandCount) { 1e-24 }
        val bandRight = DoubleArray(bandCount) { 1e-24 }
        val bandCrossReal = DoubleArray(bandCount)
        val bandCrossImag = DoubleArray(bandCount)
        val phaseBandReal = DoubleArray(phaseEdges.size - 1)
        val phaseBandImag = DoubleArray(phaseEdges.size - 1)

        for (i in 1 until FRAME_SIZE / 2) {
            val hz = i * binHz
            if (hz >= max(levelEdges.last(), 6000.0)) break
            val lr = left.first[i]
            val li = left.second[i]
            val rr = right.first[i]
            val ri = right.second[i]
            val pl = lr * lr + li * li
            val pr = rr * rr + ri * ri
            // right × conjugate(left), matching the upstream microphone convention.
            val crossReal = rr * lr + ri * li
            val crossImag = ri * lr - rr * li

            if (hz >= 300.0 && hz <= 6000.0) power += pl + pr
            if (hz >= 1000.0 && hz < 4000.0) {
                energyBandLeft += pl
                energyBandRight += pr
            }
            if (hz >= 200.0 && hz < 700.0) {
                phaseReal += crossReal
                phaseImag += crossImag
            }
            val levelBand = bandFor(hz, levelEdges)
            if (levelBand >= 0) {
                bandLeft[levelBand] += pl
                bandRight[levelBand] += pr
                bandCrossReal[levelBand] += crossReal
                bandCrossImag[levelBand] += crossImag
            }
            val phaseBand = bandFor(hz, phaseEdges)
            if (phaseBand >= 0) {
                phaseBandReal[phaseBand] += crossReal
                phaseBandImag[phaseBand] += crossImag
            }
        }

        val features = ArrayList<Double>(MODEL_FEATURE_COUNT)
        for (band in 0 until bandCount) {
            features += 10.0 * log10(bandRight[band] / bandLeft[band])
        }
        for (band in phaseBandReal.indices) {
            val magnitude = hypot(phaseBandReal[band], phaseBandImag[band]) + 1e-24
            features += phaseBandReal[band] / magnitude
            features += phaseBandImag[band] / magnitude
        }

        val frame = StereoFrame(
            cues = DirectionCues(
                level = 10.0 * log10(energyBandRight / energyBandLeft),
                phase = atan2(phaseImag, phaseReal) * 180.0 / PI,
            ),
            features = features,
            power = 10.0 * log10(power),
            bandPower = bandLeft.indices.map { band -> 10.0 * log10(bandLeft[band] + bandRight[band]) },
            coherence = bandLeft.indices.map { band ->
                min(1.0, hypot(bandCrossReal[band], bandCrossImag[band]) / sqrt(bandLeft[band] * bandRight[band]))
            },
        )
        return AudioFrameAnalysis(rms.toFloat(), frame, differenceRatio)
    }

    private fun bandFor(hz: Double, edges: DoubleArray): Int {
        var index = 0
        while (index < edges.lastIndex && hz >= edges[index + 1]) index++
        return if (index < edges.lastIndex && hz >= edges[index] && hz < edges[index + 1]) index else -1
    }

    private fun spectrum(samples: ShortArray, channel: Int): Pair<DoubleArray, DoubleArray> {
        val real = DoubleArray(FRAME_SIZE)
        val imaginary = DoubleArray(FRAME_SIZE)
        for (i in 0 until FRAME_SIZE) {
            real[i] = samples[i * channelCount + channel].toDouble() / PCM_SCALE * window[i]
        }
        fft(real, imaginary)
        val scale = 0.5 / FRAME_SIZE
        for (i in real.indices) {
            real[i] *= scale
            imaginary[i] *= scale
        }
        return real to imaginary
    }

    private fun fft(real: DoubleArray, imaginary: DoubleArray) {
        var j = 0
        for (i in 1 until FRAME_SIZE) {
            var bit = FRAME_SIZE shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val realValue = real[i]
                real[i] = real[j]
                real[j] = realValue
                val imaginaryValue = imaginary[i]
                imaginary[i] = imaginary[j]
                imaginary[j] = imaginaryValue
            }
        }
        var size = 2
        while (size <= FRAME_SIZE) {
            val half = size shr 1
            val stride = FRAME_SIZE / size
            var start = 0
            while (start < FRAME_SIZE) {
                for (offset in 0 until half) {
                    val twiddle = offset * stride
                    val wr = twiddleReal[twiddle]
                    val wi = twiddleImag[twiddle]
                    val even = start + offset
                    val odd = even + half
                    val tr = wr * real[odd] - wi * imaginary[odd]
                    val ti = wr * imaginary[odd] + wi * real[odd]
                    real[odd] = real[even] - tr
                    imaginary[odd] = imaginary[even] - ti
                    real[even] += tr
                    imaginary[even] += ti
                }
                start += size
            }
            size = size shl 1
        }
    }

    private companion object {
        const val FRAME_SIZE = 1024
        const val PCM_SCALE = 32768.0
        const val RMS_FLOOR = 1e-6
        const val MODEL_FEATURE_COUNT = 25
    }
}
