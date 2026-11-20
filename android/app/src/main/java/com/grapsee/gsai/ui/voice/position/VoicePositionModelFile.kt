package com.grapsee.gsai.ui.voice.position

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import kotlin.math.exp

/**
 * Android reader for the public MFXL VoicePosition model container. The
 * separately licensed VoicePosition.mfxl weights are never bundled here; the
 * app owner adds their licensed copy to app/src/main/assets when available.
 */
internal class VoicePositionModelFile private constructor(
    val version: String,
    private val sideModels: Map<PhonePose, DirectionModel>,
    private val distanceModels: Map<PhonePose, DistanceModel>,
) {
    fun sideModel(pose: PhonePose): DirectionModel? = sideModels[pose]
    fun distanceModel(pose: PhonePose): DistanceModel? = distanceModels[pose]

    internal data class LoadResult(
        val model: VoicePositionModelFile?,
        val error: String? = null,
        val missing: Boolean = false,
    )

    internal class ModelFormatException(message: String) : IllegalArgumentException(message)

    companion object {
        const val ASSET_NAME = "VoicePosition.mfxl"
        private const val MODEL_KIND = "position"
        private const val SIDE_COUNT = 3
        private const val HEADER_PREFIX_BYTES = 8
        private const val MAX_HEADER_BYTES = 1_048_576

        fun fromAssets(context: Context): LoadResult = try {
            val bytes = context.assets.open(ASSET_NAME).use { it.readBytes() }
            LoadResult(decode(bytes))
        } catch (_: java.io.FileNotFoundException) {
            LoadResult(model = null, missing = true)
        } catch (failure: Exception) {
            LoadResult(model = null, error = failure.message ?: "The Voice Position model could not be read.")
        }

        /** Decode without retaining or modifying the licensed model bytes. */
        fun decode(bytes: ByteArray): VoicePositionModelFile {
            if (bytes.size <= HEADER_PREFIX_BYTES ||
                bytes[0] != 'M'.code.toByte() || bytes[1] != 'F'.code.toByte() ||
                bytes[2] != 'X'.code.toByte() || bytes[3] != 'L'.code.toByte()
            ) {
                throw ModelFormatException("This file is not an MFXL model.")
            }
            val headerLength = ByteBuffer.wrap(bytes, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (headerLength <= 0 || headerLength > MAX_HEADER_BYTES ||
                HEADER_PREFIX_BYTES.toLong() + headerLength > bytes.size.toLong()
            ) {
                throw ModelFormatException("The Voice Position model header is truncated or invalid.")
            }
            val payloadStart = HEADER_PREFIX_BYTES + headerLength
            val header = try {
                JSONObject(String(bytes, HEADER_PREFIX_BYTES, headerLength, StandardCharsets.UTF_8))
            } catch (_: Exception) {
                throw ModelFormatException("The Voice Position model header is not valid JSON.")
            }
            if (header.optString("kind") != MODEL_KIND) {
                throw ModelFormatException("The MFXL file is not a Voice Position model.")
            }
            if (header.optInt("features", -1) != MODEL_FEATURE_COUNT ||
                header.doubleList("levelBands") != LEVEL_BANDS_HZ.toList() ||
                header.doubleList("phaseBands") != PHASE_BANDS_HZ.toList()
            ) {
                throw ModelFormatException("The Voice Position model uses different feature bands.")
            }
            val modelVersion = header.optString("version").takeIf(String::isNotBlank)
                ?: throw ModelFormatException("The Voice Position model has no version.")
            val poses = header.optJSONArray("poses")
                ?: throw ModelFormatException("The Voice Position model has no pose models.")
            val distances = header.optJSONArray("distance") ?: JSONArray()

            fun values(entry: JSONObject, expectedCount: Int): DoubleArray {
                val offset = entry.optLong("offset", -1L)
                val byteLength = entry.optLong("length", -1L)
                val payloadSize = bytes.size - payloadStart
                if (offset < 0 || byteLength != expectedCount.toLong() * Float.SIZE_BYTES ||
                    offset > payloadSize.toLong() - byteLength
                ) {
                    throw ModelFormatException("A Voice Position model weight block is truncated or has the wrong size.")
                }
                val start = payloadStart + offset.toInt()
                val block = ByteBuffer.wrap(bytes, start, byteLength.toInt())
                    .slice()
                    .order(ByteOrder.LITTLE_ENDIAN)
                return DoubleArray(expectedCount) {
                    val value = block.float.toDouble()
                    if (!value.isFinite()) throw ModelFormatException("The Voice Position model contains a non-finite weight.")
                    value
                }
            }

            val sideModels = linkedMapOf<PhonePose, DirectionModel>()
            for (index in 0 until poses.length()) {
                val entry = poses.optJSONObject(index) ?: continue
                val pose = poseFromName(entry.optString("name")) ?: continue
                val n = MODEL_FEATURE_COUNT
                val raw = values(entry, n * 5 + SIDE_COUNT)
                val means = raw.copyOfRange(0, n)
                val std = raw.copyOfRange(n, 2 * n)
                if (std.any { it <= 0.0 }) throw ModelFormatException("The Voice Position model has an invalid feature scale.")
                val weights = Array(n) { row -> DoubleArray(SIDE_COUNT) { column -> raw[2 * n + row * SIDE_COUNT + column] } }
                val bias = raw.copyOfRange(5 * n, 5 * n + SIDE_COUNT)
                sideModels[pose] = DirectionModel(means, std, weights, bias)
            }
            if (sideModels.isEmpty()) throw ModelFormatException("The Voice Position model has no hand or desk side classifier.")

            val distanceModels = linkedMapOf<PhonePose, DistanceModel>()
            for (index in 0 until distances.length()) {
                val entry = distances.optJSONObject(index) ?: continue
                val pose = poseFromName(entry.optString("name")) ?: continue
                val count = entry.optInt("features", -1)
                if (count <= 0 || count > DISTANCE_FEATURE_COUNT) {
                    throw ModelFormatException("The Voice Position distance model has an invalid feature count.")
                }
                val raw = values(entry, count * 3 + 1)
                val means = raw.copyOfRange(0, count)
                val std = raw.copyOfRange(count, 2 * count)
                if (std.any { it <= 0.0 }) throw ModelFormatException("The Voice Position distance model has an invalid feature scale.")
                val weights = raw.copyOfRange(2 * count, 3 * count)
                distanceModels[pose] = DistanceModel(means, std, weights, raw[3 * count])
            }
            return VoicePositionModelFile(modelVersion, sideModels, distanceModels)
        }

        private fun poseFromName(name: String): PhonePose? = when (name.lowercase()) {
            "hand" -> PhonePose.HAND
            "desk" -> PhonePose.DESK
            else -> null
        }

        private fun JSONObject.doubleList(key: String): List<Double> {
            val array = optJSONArray(key) ?: return emptyList()
            return List(array.length()) { index -> array.optDouble(index, Double.NaN) }
        }
    }
}

internal class DirectionModel(
    private val mean: DoubleArray,
    private val std: DoubleArray,
    private val weights: Array<DoubleArray>,
    private val bias: DoubleArray,
) {
    fun probabilities(features: List<Double>): List<Double>? {
        if (features.size != mean.size) return null
        val logits = bias.copyOf()
        for (index in features.indices) {
            val normalized = (features[index] - mean[index]) / std[index]
            for (classIndex in logits.indices) logits[classIndex] += normalized * weights[index][classIndex]
        }
        val top = logits.maxOrNull() ?: return null
        val exponentials = DoubleArray(logits.size) { exp(logits[it] - top) }
        val total = exponentials.sum()
        if (!total.isFinite() || total <= 0.0) return null
        return exponentials.map { it / total }
    }

    fun pan(features: List<Double>): Double? {
        val probabilities = probabilities(features) ?: return null
        return probabilities[2] - probabilities[0]
    }
}

internal class DistanceModel(
    private val mean: DoubleArray,
    private val std: DoubleArray,
    private val weights: DoubleArray,
    private val bias: Double,
) {
    fun large(features: List<Double>): Double? {
        if (features.size < weights.size) return null
        var logit = bias
        for (index in weights.indices) logit += (features[index] - mean[index]) / std[index] * weights[index]
        if (!logit.isFinite()) return null
        return if (logit >= 0.0) 1.0 / (1.0 + exp(-logit)) else {
            val e = exp(logit)
            e / (1.0 + e)
        }
    }
}

private const val MODEL_FEATURE_COUNT = 25
private const val DISTANCE_FEATURE_COUNT = 30
