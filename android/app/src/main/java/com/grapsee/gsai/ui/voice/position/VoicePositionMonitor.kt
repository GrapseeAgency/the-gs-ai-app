package com.grapsee.gsai.ui.voice.position

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal enum class PositionInput {
    UNKNOWN,
    STEREO,
    MONO,
    UNAVAILABLE,
}

internal data class VoicePositionUiState(
    val supported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
    val running: Boolean = false,
    val input: PositionInput = PositionInput.UNKNOWN,
    val inputName: String? = null,
    val sampleRate: Int = 48_000,
    val level: Float = 0f,
    val pose: PhonePose = PhonePose.UNKNOWN,
    val direction: DirectionEstimate? = null,
    val calibration: PositionCalibration = PositionCalibration(),
    val calibrating: PositionSide? = null,
    val calibrationProgress: Float = 0f,
    val calibrationMessage: String? = null,
    val error: String? = null,
)

internal enum class CalibrationResultCode {
    SAVED,
    TIMED_OUT,
    NO_STEREO_INPUT,
    PHONE_MOVED,
    TOO_LITTLE_AUDIO,
    ALREADY_RUNNING,
    NOT_LISTENING,
}

internal data class CalibrationResult(
    val code: CalibrationResultCode,
    val issue: CalibrationIssue? = null,
)

private data class AudioFrameMetrics(
    val rmsDb: Double,
    val normalizedLevel: Float,
    val cue: StereoCue?,
    val channelDifferenceRatio: Double,
)

/**
 * Android's best-effort, calibration-based counterpart to the iOS beta.
 * Android has no upstream VoicePositionKit port; this class only reports a
 * direction when stereo input was observed and this exact phone pose has a
 * usable left/right calibration. It deliberately never estimates distance.
 */
internal class VoicePositionMonitor(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val captureLock = Any()
    private val calibrationMutex = Mutex()
    private val activeCalibration = AtomicReference<CalibrationCapture?>(null)
    private val calibrationsLock = Any()
    private val calibrations = loadCalibrations()
    private val stateMutable = MutableStateFlow(
        VoicePositionUiState(pose = PhonePose.UNKNOWN, calibration = calibrationFor(PhonePose.UNKNOWN))
    )
    val state = stateMutable.asStateFlow()

    private val captureJob = AtomicReference<Job?>(null)
    private val recorder = AtomicReference<AudioRecord?>(null)

    @Volatile
    private var currentPose = PhonePose.UNKNOWN
    @Volatile
    private var channelCount = 0
    @Volatile
    private var flatSinceMillis: Long? = null
    @Volatile
    private var movingSinceMillis: Long? = null
    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 0f

    private val poseListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val alpha = 0.82f
            gravityX = alpha * gravityX + (1f - alpha) * event.values[0]
            gravityY = alpha * gravityY + (1f - alpha) * event.values[1]
            gravityZ = alpha * gravityZ + (1f - alpha) * event.values[2]
            val now = SystemClock.elapsedRealtime()
            val screenFlat = abs(gravityZ) > 8.9f &&
                sqrt(gravityX * gravityX + gravityY * gravityY) < 3.6f
            val next = when {
                screenFlat -> {
                    flatSinceMillis = flatSinceMillis ?: now
                    movingSinceMillis = null
                    if (currentPose != PhonePose.DESK && now - (flatSinceMillis ?: now) >= DESK_SETTLE_MS) {
                        PhonePose.DESK
                    } else currentPose
                }
                else -> {
                    movingSinceMillis = movingSinceMillis ?: now
                    flatSinceMillis = null
                    if (currentPose != PhonePose.HAND && now - (movingSinceMillis ?: now) >= HAND_SETTLE_MS) {
                        PhonePose.HAND
                    } else currentPose
                }
            }
            if (next != currentPose) {
                currentPose = next
                val calibration = calibrationFor(next)
                stateMutable.update {
                    it.copy(pose = next, calibration = calibration, direction = null)
                }
                activeCalibration.get()?.let { capture ->
                    if (capture.pose != next) {
                        finishCapture(capture, CalibrationResult(CalibrationResultCode.PHONE_MOVED))
                    }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            stateMutable.update {
                it.copy(
                    supported = false,
                    input = PositionInput.UNAVAILABLE,
                    error = "Voice Position requires Android 10 or newer.",
                )
            }
            return
        }
        synchronized(captureLock) {
            if (captureJob.get()?.isActive == true) return
            stateMutable.update { it.copy(error = null, calibrationMessage = null, direction = null) }
            startPoseTracking()
            val job = scope.launch(start = CoroutineStart.LAZY) { captureLoop() }
            captureJob.set(job)
            job.start()
        }
    }

    fun stop() {
        stopPoseTracking()
        activeCalibration.getAndSet(null)?.completion?.complete(
            CalibrationResult(CalibrationResultCode.NOT_LISTENING)
        )
        recorder.get()?.let { active -> runCatching { active.stop() } }
        captureJob.getAndSet(null)?.cancel()
        stateMutable.update {
            it.copy(
                running = false,
                level = 0f,
                direction = null,
                calibrating = null,
                calibrationProgress = 0f,
                input = PositionInput.UNKNOWN,
            )
        }
    }

    fun resetCalibration() {
        synchronized(calibrationsLock) {
            calibrations.clear()
            preferences.edit().remove(CALIBRATION_KEY).apply()
        }
        stateMutable.update {
            it.copy(calibration = PositionCalibration(), direction = null, calibrationMessage = "Calibration cleared for this device.")
        }
    }

    suspend fun calibrate(side: PositionSide): CalibrationResult = calibrationMutex.withLock {
        if (!state.value.running) return@withLock CalibrationResult(CalibrationResultCode.NOT_LISTENING)
        if (channelCount < 2) return@withLock CalibrationResult(CalibrationResultCode.NO_STEREO_INPUT)

        val capture = CalibrationCapture(
            side = side,
            pose = currentPose,
            targetFrames = max(30, ceil(CALIBRATION_SECONDS * state.value.sampleRate / FRAME_SIZE).toInt()),
        )
        if (!activeCalibration.compareAndSet(null, capture)) {
            return@withLock CalibrationResult(CalibrationResultCode.ALREADY_RUNNING)
        }
        stateMutable.update {
            it.copy(
                calibrating = side,
                calibrationProgress = 0f,
                calibrationMessage = "Speak naturally from ${side.label.lowercase()} for about three seconds.",
            )
        }

        try {
            withTimeout(CALIBRATION_TIMEOUT_MS) { capture.completion.await() }
        } catch (_: TimeoutCancellationException) {
            CalibrationResult(CalibrationResultCode.TIMED_OUT)
        } finally {
            activeCalibration.compareAndSet(capture, null)
            stateMutable.update {
                if (it.calibrating == side) it.copy(calibrating = null, calibrationProgress = 0f) else it
            }
        }
    }

    override fun close() {
        stop()
        scope.cancel()
    }

    private suspend fun captureLoop() {
        var activeRecorder: AudioRecord? = null
        try {
            val plan = createCapturePlan()
            if (plan == null) {
                stateMutable.update {
                    it.copy(
                        running = false,
                        input = PositionInput.UNAVAILABLE,
                        error = "This device could not open a microphone input.",
                    )
                }
                return
            }
            activeRecorder = plan.recorder
            if (!currentCoroutineContext().isActive) throw CancellationException("Voice Position stopped.")
            channelCount = plan.channelCount
            recorder.set(activeRecorder)
            stateMutable.update {
                it.copy(
                    running = true,
                    input = if (plan.channelCount >= 2) PositionInput.UNKNOWN else PositionInput.MONO,
                    inputName = activeRecorder.routedDevice?.productName?.toString(),
                    sampleRate = plan.sampleRate,
                    error = null,
                )
            }

            val buffer = ShortArray(FRAME_SIZE * plan.channelCount)
            val analyzer = StereoFrameAnalyzer(plan.sampleRate, plan.channelCount)
            val recentStereoEvidence = ArrayDeque<Boolean>()
            val recentIdenticalEvidence = ArrayDeque<Boolean>()
            var recentDistinct = 0
            var recentIdentical = 0
            var voiceFrames = 0
            var noiseFloorDb = -60.0
            var voicedRun = 0
            var lastUiUpdate = 0L

            while (currentCoroutineContext().isActive) {
                val read = activeRecorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (read <= 0) {
                    if (read == AudioRecord.ERROR_DEAD_OBJECT) {
                        throw IllegalStateException("The microphone input was disconnected.")
                    }
                    delay(12)
                    continue
                }
                val metrics = analyzer.analyse(buffer, read) ?: continue
                val rmsDb = metrics.rmsDb
                // The noise floor falls quickly and rises slowly; a sustained
                // energy gate avoids turning a tap or a short click into a side.
                val floorRate = if (rmsDb < noiseFloorDb + 12.0) 0.08 else 0.004
                noiseFloorDb += (rmsDb - noiseFloorDb) * floorRate
                val energyGate = rmsDb > MIN_VOICE_DB && rmsDb - noiseFloorDb > MIN_VOICE_SNR_DB
                voicedRun = if (energyGate) voicedRun + 1 else 0
                val voiced = voicedRun >= MIN_VOICE_FRAMES

                if (plan.channelCount >= 2 && energyGate) {
                    voiceFrames++
                    val distinct = metrics.channelDifferenceRatio > MIN_STEREO_DIFFERENCE_RATIO
                    val identical = metrics.channelDifferenceRatio <= EXACT_MONO_DIFFERENCE_RATIO
                    recentStereoEvidence.addLast(distinct)
                    recentIdenticalEvidence.addLast(identical)
                    if (distinct) recentDistinct++
                    if (identical) recentIdentical++
                    if (recentStereoEvidence.size > STEREO_WINDOW_FRAMES) {
                        if (recentStereoEvidence.removeFirst()) recentDistinct--
                        if (recentIdenticalEvidence.removeFirst()) recentIdentical--
                    }
                }
                val input = when {
                    plan.channelCount < 2 -> PositionInput.MONO
                    recentDistinct >= MIN_STEREO_DISTINCT_FRAMES -> PositionInput.STEREO
                    voiceFrames >= STEREO_WINDOW_FRAMES && recentIdentical >= STEREO_WINDOW_FRAMES -> PositionInput.MONO
                    else -> PositionInput.UNKNOWN
                }
                val pose = currentPose
                val calibration = calibrationFor(pose)
                val cue = metrics.cue
                val estimate = estimateVoicePosition(
                    stereoAvailable = input == PositionInput.STEREO,
                    voiced = voiced,
                    calibration = calibration,
                    cue = cue,
                )
                updateCalibration(cue, voiced, pose)

                val now = SystemClock.elapsedRealtime()
                if (now - lastUiUpdate >= UI_UPDATE_MS || activeCalibration.get() != null) {
                    lastUiUpdate = now
                    val pending = activeCalibration.get()
                    stateMutable.update {
                        it.copy(
                            running = true,
                            input = input,
                            inputName = activeRecorder.routedDevice?.productName?.toString(),
                            level = metrics.normalizedLevel,
                            pose = pose,
                            direction = estimate,
                            calibration = calibrationFor(pose),
                            calibrating = pending?.side,
                            calibrationProgress = pending?.progress ?: 0f,
                            error = null,
                        )
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            activeCalibration.getAndSet(null)?.completion?.complete(
                CalibrationResult(CalibrationResultCode.NO_STEREO_INPUT)
            )
            stateMutable.update {
                it.copy(
                    running = false,
                    input = PositionInput.UNAVAILABLE,
                    level = 0f,
                    direction = null,
                    error = failure.message ?: "Microphone capture failed.",
                )
            }
        } finally {
            if (activeRecorder != null) {
                runCatching {
                    if (activeRecorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) activeRecorder.stop()
                }
                runCatching { activeRecorder.release() }
            }
            recorder.compareAndSet(activeRecorder, null)
            channelCount = 0
            stateMutable.update {
                it.copy(running = false, level = 0f, direction = null, calibrating = null, calibrationProgress = 0f)
            }
            captureJob.compareAndSet(currentCoroutineContext()[Job], null)
            stopPoseTracking()
        }
    }

    private fun updateCalibration(cue: StereoCue?, voiced: Boolean, pose: PhonePose) {
        val capture = activeCalibration.get() ?: return
        if (capture.pose != pose) {
            finishCapture(capture, CalibrationResult(CalibrationResultCode.PHONE_MOVED))
            return
        }
        if (!voiced || cue == null || capture.completed) return
        capture.samples.add(cue)
        val count = capture.frames.incrementAndGet()
        if (count % 5 == 0 || count >= capture.targetFrames) {
            stateMutable.update {
                if (it.calibrating == capture.side) {
                    it.copy(calibrationProgress = capture.progress)
                } else it
            }
        }
        if (count >= capture.targetFrames && activeCalibration.compareAndSet(capture, null)) {
            val next = calibrationFor(capture.pose).record(capture.side, capture.samples.toList())
            if (next == null) {
                finishCapture(capture, CalibrationResult(CalibrationResultCode.TOO_LITTLE_AUDIO))
                return
            }
            synchronized(calibrationsLock) {
                calibrations[capture.pose] = next
                persistCalibrationsLocked()
            }
            stateMutable.update {
                if (it.pose == capture.pose) {
                    it.copy(
                        calibration = next,
                        calibrationProgress = 1f,
                        calibrationMessage = calibrationMessage(capture.side, next.issue()),
                    )
                } else it
            }
            finishCapture(capture, CalibrationResult(CalibrationResultCode.SAVED, next.issue()))
        }
    }

    private fun finishCapture(capture: CalibrationCapture, result: CalibrationResult) {
        capture.completed = true
        activeCalibration.compareAndSet(capture, null)
        stateMutable.update {
            if (it.calibrating == capture.side) {
                it.copy(
                    calibrating = null,
                    calibrationProgress = if (result.code == CalibrationResultCode.SAVED) 1f else 0f,
                    calibrationMessage = when (result.code) {
                        CalibrationResultCode.PHONE_MOVED -> "The phone changed pose. Keep it still and calibrate again."
                        CalibrationResultCode.TOO_LITTLE_AUDIO -> "Not enough clear stereo speech was captured. Try again in a quieter room."
                        else -> it.calibrationMessage
                    },
                )
            } else it
        }
        capture.completion.complete(result)
    }

    private fun calibrationMessage(side: PositionSide, issue: CalibrationIssue?): String = when (issue) {
        CalibrationIssue.SIDES_TOO_ALIKE -> "${side.label} saved, but left and right still sound alike. Move farther apart and recalibrate."
        CalibrationIssue.AHEAD_OUTSIDE -> "${side.label} saved. Recalibrate ahead between the left and right positions."
        null -> "${side.label} saved for the ${currentPose.label.lowercase()} pose."
    }

    private fun createCapturePlan(): CapturePlan? {
        val sourceCandidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true") {
                add(MediaRecorder.AudioSource.UNPROCESSED)
            }
            add(MediaRecorder.AudioSource.MIC)
        }.distinct()

        for (channels in listOf(2, 1)) {
            val mask = if (channels == 2) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
            for (sampleRate in SAMPLE_RATES) {
                val minimum = AudioRecord.getMinBufferSize(sampleRate, mask, AudioFormat.ENCODING_PCM_16BIT)
                if (minimum <= 0) continue
                for (source in sourceCandidates) {
                    val built = runCatching {
                        AudioRecord.Builder()
                            .setAudioSource(source)
                            .setAudioFormat(
                                AudioFormat.Builder()
                                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                    .setSampleRate(sampleRate)
                                    .setChannelMask(mask)
                                    .build()
                            )
                            .setBufferSizeInBytes(max(minimum * 2, FRAME_SIZE * channels * 2))
                            .build()
                    }.getOrNull() ?: continue
                    if (built.state == AudioRecord.STATE_INITIALIZED) {
                        val started = runCatching {
                            built.startRecording()
                            built.recordingState == AudioRecord.RECORDSTATE_RECORDING
                        }.getOrDefault(false)
                        if (started) {
                            return CapturePlan(built, sampleRate, built.channelCount.coerceAtLeast(1))
                        }
                    }
                    runCatching { built.stop() }
                    runCatching { built.release() }
                }
            }
        }
        return null
    }

    private fun startPoseTracking() {
        val manager = sensorManager ?: return
        val sensor = accelerometer ?: return
        manager.registerListener(poseListener, sensor, SensorManager.SENSOR_DELAY_NORMAL, Handler(Looper.getMainLooper()))
    }

    private fun stopPoseTracking() {
        sensorManager?.unregisterListener(poseListener)
        flatSinceMillis = null
        movingSinceMillis = null
    }

    private fun calibrationFor(pose: PhonePose): PositionCalibration = synchronized(calibrationsLock) {
        calibrations[pose] ?: PositionCalibration()
    }

    private fun loadCalibrations(): MutableMap<PhonePose, PositionCalibration> {
        val result = mutableMapOf<PhonePose, PositionCalibration>()
        val root = runCatching { JSONObject(preferences.getString(CALIBRATION_KEY, "{}")) }.getOrDefault(JSONObject())
        for (pose in PhonePose.entries) {
            val group = root.optJSONObject(pose.name) ?: continue
            result[pose] = PositionCalibration(
                left = readCalibratedCue(group.optJSONObject(PositionSide.LEFT.name)),
                ahead = readCalibratedCue(group.optJSONObject(PositionSide.AHEAD.name)),
                right = readCalibratedCue(group.optJSONObject(PositionSide.RIGHT.name)),
            )
        }
        return result
    }

    private fun persistCalibrationsLocked() {
        val root = JSONObject()
        for ((pose, calibration) in calibrations) {
            val group = JSONObject()
            calibration.left?.let { group.put(PositionSide.LEFT.name, it.toJson()) }
            calibration.ahead?.let { group.put(PositionSide.AHEAD.name, it.toJson()) }
            calibration.right?.let { group.put(PositionSide.RIGHT.name, it.toJson()) }
            root.put(pose.name, group)
        }
        preferences.edit().putString(CALIBRATION_KEY, root.toString()).apply()
    }

    private fun readCalibratedCue(json: JSONObject?): CalibratedCue? {
        if (json == null) return null
        return runCatching {
            CalibratedCue(
                levelDifferenceDb = json.getDouble("level"),
                lagSamples = json.getDouble("lag"),
                levelSpreadDb = json.getDouble("levelSpread"),
                lagSpreadSamples = json.getDouble("lagSpread"),
            )
        }.getOrNull()
    }

    private fun CalibratedCue.toJson() = JSONObject().apply {
        put("level", levelDifferenceDb)
        put("lag", lagSamples)
        put("levelSpread", levelSpreadDb)
        put("lagSpread", lagSpreadSamples)
    }

    private data class CapturePlan(val recorder: AudioRecord, val sampleRate: Int, val channelCount: Int)

    private class CalibrationCapture(
        val side: PositionSide,
        val pose: PhonePose,
        val targetFrames: Int,
    ) {
        val samples = ConcurrentLinkedQueue<StereoCue>()
        val frames = AtomicInteger(0)
        val completion = CompletableDeferred<CalibrationResult>()
        @Volatile var completed = false
        val progress: Float get() = (frames.get().toFloat() / targetFrames).coerceIn(0f, 1f)
    }

    private class StereoFrameAnalyzer(private val sampleRate: Int, private val channels: Int) {
        fun analyse(samples: ShortArray, shortCount: Int): AudioFrameMetrics? {
            val frameCount = shortCount / channels
            if (frameCount < MIN_FRAME_COUNT) return null

            var meanLeft = 0.0
            var meanRight = 0.0
            for (i in 0 until frameCount) {
                val base = i * channels
                meanLeft += samples[base] / PCM_SCALE
                meanRight += if (channels >= 2) samples[base + 1] / PCM_SCALE else samples[base] / PCM_SCALE
            }
            meanLeft /= frameCount
            meanRight /= frameCount

            var energyLeft = 0.0
            var energyRight = 0.0
            var energyDifference = 0.0
            for (i in 0 until frameCount) {
                val base = i * channels
                val left = samples[base] / PCM_SCALE - meanLeft
                val right = if (channels >= 2) samples[base + 1] / PCM_SCALE - meanRight else left
                energyLeft += left * left
                energyRight += right * right
                val difference = left - right
                energyDifference += difference * difference
            }
            energyLeft /= frameCount
            energyRight /= frameCount
            val rms = sqrt((energyLeft + energyRight) / 2.0)
            val rmsDb = 20.0 * log10(max(rms, RMS_FLOOR))
            val differenceRatio = sqrt(energyDifference / frameCount) / max(rms, RMS_FLOOR)

            if (channels < 2 || energyLeft <= 0.0 || energyRight <= 0.0) {
                return AudioFrameMetrics(rmsDb, normalizeLevel(rmsDb), null, differenceRatio)
            }
            val levelDb = 20.0 * log10(sqrt(energyRight / energyLeft))
            val lag = estimateLag(samples, frameCount, meanLeft, meanRight, energyLeft, energyRight)
            return AudioFrameMetrics(
                rmsDb = rmsDb,
                normalizedLevel = normalizeLevel(rmsDb),
                cue = StereoCue(levelDb, lag),
                channelDifferenceRatio = differenceRatio,
            )
        }

        private fun estimateLag(
            samples: ShortArray,
            frameCount: Int,
            meanLeft: Double,
            meanRight: Double,
            energyLeft: Double,
            energyRight: Double,
        ): Double {
            val maxLag = ceil(sampleRate * MAX_MICROPHONE_LAG_SECONDS).toInt().coerceIn(1, MAX_LAG_SAMPLES)
            var bestLag = 0
            var bestCorrelation = Double.NEGATIVE_INFINITY
            for (lag in -maxLag..maxLag) {
                val start = max(0, -lag)
                val end = min(frameCount, frameCount - lag)
                var cross = 0.0
                var leftPower = 0.0
                var rightPower = 0.0
                for (i in start until end) {
                    val j = i + lag
                    val left = samples[i * 2] / PCM_SCALE - meanLeft
                    val right = samples[j * 2 + 1] / PCM_SCALE - meanRight
                    cross += left * right
                    leftPower += left * left
                    rightPower += right * right
                }
                val denominator = sqrt(leftPower * rightPower)
                val correlation = if (denominator > 0.0) cross / denominator else 0.0
                if (correlation > bestCorrelation) {
                    bestCorrelation = correlation
                    bestLag = lag
                }
            }
            // Channel amplitudes remain the primary cue. The measured lag is
            // retained as a calibrated secondary cue, not treated as metres.
            return bestLag.toDouble()
        }

        private fun normalizeLevel(rmsDb: Double): Float = ((rmsDb + 60.0) / 55.0).coerceIn(0.0, 1.0).toFloat()

        private companion object {
            const val MIN_FRAME_COUNT = 128
            const val MAX_MICROPHONE_LAG_SECONDS = 0.00025
            const val MAX_LAG_SAMPLES = 12
            const val PCM_SCALE = 32768.0
            const val RMS_FLOOR = 1e-6
        }
    }

    private companion object {
        const val PREFERENCES = "voice_position"
        const val CALIBRATION_KEY = "pose_calibrations_v1"
        const val FRAME_SIZE = 1024
        const val CALIBRATION_SECONDS = 3.0
        const val CALIBRATION_TIMEOUT_MS = 12_000L
        const val MIN_VOICE_DB = -55.0
        const val MIN_VOICE_SNR_DB = 10.0
        const val MIN_VOICE_FRAMES = 8
        const val MIN_STEREO_DIFFERENCE_RATIO = 0.0005
        const val EXACT_MONO_DIFFERENCE_RATIO = 0.000001
        const val MIN_STEREO_DISTINCT_FRAMES = 3
        const val STEREO_WINDOW_FRAMES = 48
        const val UI_UPDATE_MS = 100L
        const val DESK_SETTLE_MS = 1_500L
        const val HAND_SETTLE_MS = 500L
        val SAMPLE_RATES = intArrayOf(48_000, 44_100, 32_000, 16_000)
    }
}
