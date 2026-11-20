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
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

internal data class VoicePositionUiState(
    val running: Boolean = false,
    val input: PositionInput = PositionInput.UNKNOWN,
    val inputName: String? = null,
    val sampleRate: Int = 48_000,
    val level: Float = 0f,
    val pose: PhonePose = PhonePose.UNKNOWN,
    val direction: DirectionEstimate? = null,
    val probabilities: List<Double>? = null,
    val calibration: PositionCalibration = PositionCalibration(),
    val hasAnyCalibration: Boolean = false,
    val calibrating: PositionSide? = null,
    val calibrationProgress: Float = 0f,
    val calibrationMessage: String? = null,
    val modelLoaded: Boolean = false,
    val modelVersion: String? = null,
    val modelError: String? = null,
    val hasSideModel: Boolean = false,
    val hasDistanceModel: Boolean = false,
    val distanceVoiceActive: Boolean = false,
    val distance: VoiceDistance? = null,
    val distanceBelief: Float? = null,
    val soundsFar: Boolean = false,
    val readsDistance: Boolean = true,
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

/** Android capture + upstream VoicePosition model inference; audio never leaves this process. */
internal class VoicePositionMonitor(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val modelLoad = VoicePositionModelFile.fromAssets(appContext)
    private val modelFile = modelLoad.model
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val captureLock = Any()
    private val calibrationMutex = Mutex()
    private val activeCalibration = AtomicReference<CalibrationCapture?>(null)
    private val calibrationsLock = Any()
    private val calibrations = loadCalibrations()
    private val stateMutable = MutableStateFlow(initialState())
    val state = stateMutable.asStateFlow()

    private val captureJob = AtomicReference<Job?>(null)
    private val recorder = AtomicReference<AudioRecord?>(null)

    @Volatile private var readsDistance = true
    @Volatile private var currentPose = PhonePose.UNKNOWN
    @Volatile private var channelCount = 0
    @Volatile private var flatSinceMillis: Long? = null
    @Volatile private var heldSinceMillis: Long? = null
    private var gravityInitialized = false
    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 0f

    private val poseListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.values.size < 3) return
            val alpha = GRAVITY_FILTER_ALPHA
            if (!gravityInitialized) {
                gravityX = event.values[0]
                gravityY = event.values[1]
                gravityZ = event.values[2]
                gravityInitialized = true
            } else {
                gravityX = alpha * gravityX + (1f - alpha) * event.values[0]
                gravityY = alpha * gravityY + (1f - alpha) * event.values[1]
                gravityZ = alpha * gravityZ + (1f - alpha) * event.values[2]
            }
            val linearX = event.values[0] - gravityX
            val linearY = event.values[1] - gravityY
            val linearZ = event.values[2] - gravityZ
            val now = SystemClock.elapsedRealtime()
            val flatAndStill = abs(gravityZ) / STANDARD_GRAVITY > 0.9f &&
                sqrt(linearX * linearX + linearY * linearY + linearZ * linearZ) / STANDARD_GRAVITY < 0.03f
            val next = if (flatAndStill) {
                flatSinceMillis = flatSinceMillis ?: now
                heldSinceMillis = null
                if (now - (flatSinceMillis ?: now) >= DESK_SETTLE_MS) PhonePose.DESK else currentPose
            } else {
                heldSinceMillis = heldSinceMillis ?: now
                flatSinceMillis = null
                if (now - (heldSinceMillis ?: now) >= HAND_SETTLE_MS) PhonePose.HAND else currentPose
            }
            if (next != currentPose) {
                currentPose = next
                val calibration = calibrationFor(next)
                stateMutable.update {
                    it.copy(
                        pose = next,
                        calibration = calibration,
                        hasSideModel = modelFile?.sideModel(next) != null,
                        hasDistanceModel = modelFile?.distanceModel(next) != null,
                        distanceVoiceActive = false,
                        direction = null,
                        probabilities = null,
                        distance = null,
                        distanceBelief = null,
                        soundsFar = false,
                    )
                }
                activeCalibration.get()?.let { capture ->
                    if (capture.pose != next) finishCapture(capture, CalibrationResult(CalibrationResultCode.PHONE_MOVED))
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private fun initialState() = VoicePositionUiState(
        pose = PhonePose.UNKNOWN,
        calibration = calibrationFor(PhonePose.UNKNOWN),
        hasAnyCalibration = hasAnyCalibration(),
        modelLoaded = modelFile != null,
        modelVersion = modelFile?.version,
        modelError = modelLoad.error,
        hasSideModel = modelFile?.sideModel(PhonePose.UNKNOWN) != null,
        hasDistanceModel = modelFile?.distanceModel(PhonePose.UNKNOWN) != null,
    )

    /** Called by the shared TTS playback coordinator: freeze distance while GS audio is audible. */
    fun setReadsDistance(enabled: Boolean) {
        readsDistance = enabled
        stateMutable.update { it.copy(readsDistance = enabled) }
    }

    fun start() {
        synchronized(captureLock) {
            if (captureJob.get()?.isActive == true) return
            stateMutable.update { it.copy(error = null, calibrationMessage = null, readsDistance = readsDistance) }
            startPoseTracking()
            val job = scope.launch(start = CoroutineStart.LAZY) { captureLoop() }
            captureJob.set(job)
            job.start()
        }
    }

    fun stop() {
        stopPoseTracking()
        activeCalibration.getAndSet(null)?.completion?.complete(CalibrationResult(CalibrationResultCode.NOT_LISTENING))
        recorder.get()?.let { active -> runCatching { active.stop() } }
        captureJob.getAndSet(null)?.cancel()
        stateMutable.update {
            it.copy(
                running = false,
                level = 0f,
                direction = null,
                probabilities = null,
                distanceVoiceActive = false,
                distance = null,
                distanceBelief = null,
                soundsFar = false,
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
            it.copy(calibration = PositionCalibration(), hasAnyCalibration = false, direction = null, probabilities = null, calibrationMessage = "Calibration cleared for this device.")
        }
    }

    suspend fun calibrate(side: PositionSide): CalibrationResult = calibrationMutex.withLock {
        if (!state.value.running) return@withLock CalibrationResult(CalibrationResultCode.NOT_LISTENING)
        if (state.value.input != PositionInput.STEREO || channelCount < 2) {
            return@withLock CalibrationResult(CalibrationResultCode.NO_STEREO_INPUT)
        }
        val capture = CalibrationCapture(side, currentPose)
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
                    it.copy(running = false, input = PositionInput.UNAVAILABLE, error = "This device could not open a microphone input.")
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
                    modelLoaded = modelFile != null,
                    modelVersion = modelFile?.version,
                    modelError = modelLoad.error,
                    hasSideModel = modelFile?.sideModel(currentPose) != null,
                    hasDistanceModel = modelFile?.distanceModel(currentPose) != null,
                    distanceVoiceActive = false,
                    pose = currentPose,
                    calibration = calibrationFor(currentPose),
                    error = null,
                )
            }

            val buffer = ShortArray(FRAME_SIZE * plan.channelCount)
            val analyzer = StereoFrameAnalyzer(plan.sampleRate, plan.channelCount)
            val stereoInputGate = StereoInputGate()
            var lastUiUpdate = 0L
            var lastPose = currentPose
            var lastFrameTime = SystemClock.elapsedRealtimeNanos()
            val directionGate = DirectionGate()
            val directionDecision = DirectionDecision()
            val distanceGate = DistanceGate()
            val distanceDecision = DistanceDecision()

            while (currentCoroutineContext().isActive) {
                var filled = 0
                while (filled < buffer.size && currentCoroutineContext().isActive) {
                    val read = activeRecorder.read(buffer, filled, buffer.size - filled, AudioRecord.READ_BLOCKING)
                    if (read == AudioRecord.ERROR_DEAD_OBJECT) throw IllegalStateException("The microphone input was disconnected.")
                    if (read < 0) throw IllegalStateException("Microphone read failed with AudioRecord error $read.")
                    if (read == 0) {
                        delay(12)
                        continue
                    }
                    filled += read
                }
                if (!currentCoroutineContext().isActive) break
                val analysis = analyzer.analyse(buffer) ?: continue
                val frame = analysis.frame
                val nowNanos = SystemClock.elapsedRealtimeNanos()
                val dt = ((nowNanos - lastFrameTime) / 1_000_000_000.0).coerceIn(0.001, 0.1)
                lastFrameTime = nowNanos
                val pose = currentPose
                if (pose != lastPose) {
                    directionGate.reset()
                    directionDecision.reset()
                    distanceGate.reset()
                    distanceDecision.reset()
                    lastPose = pose
                }

                if (frame != null) directionGate.step(frame, dt)
                val voiced = frame != null && directionGate.voiced
                val input = stereoInputGate.step(
                    channelCount = plan.channelCount,
                    voiced = voiced,
                    differenceRatio = analysis.channelDifferenceRatio,
                )

                val active = activeCalibration.get()
                if (active != null && input == PositionInput.MONO) {
                    finishCapture(active, CalibrationResult(CalibrationResultCode.NO_STEREO_INPUT))
                } else if (frame != null && input == PositionInput.STEREO && active != null) {
                    updateCalibration(active, frame, dt)
                }
                val calibration = calibrationFor(pose)
                val sideModel = modelFile?.sideModel(pose)
                val distanceModel = modelFile?.distanceModel(pose)
                val features = directionGate.features
                val probabilities = if (input == PositionInput.STEREO && sideModel != null && features != null) {
                    sideModel.probabilities(features)
                } else null
                val calibratedReading = if (input == PositionInput.STEREO && calibration.isReady) {
                    directionGate.cues?.let(calibration::pan)
                } else null
                val modelReading = if (input == PositionInput.STEREO && !calibration.isReady) {
                    probabilities?.let { it[2] - it[0] }
                } else null
                val reading = calibratedReading ?: modelReading
                directionDecision.step(reading, dt)
                val readable = calibratedReading != null || modelReading != null
                val direction = if (readable && directionGate.cues != null) {
                    val side = when {
                        directionDecision.side < 0 -> PositionSide.LEFT
                        directionDecision.side > 0 -> PositionSide.RIGHT
                        else -> PositionSide.AHEAD
                    }
                    DirectionEstimate(
                        pan = directionDecision.pan.toFloat(),
                        side = side,
                        probabilities = if (calibratedReading != null) null else probabilities,
                        modelBased = calibratedReading == null,
                    )
                } else null

                if (frame != null && input == PositionInput.STEREO && distanceModel != null && readsDistance) {
                    distanceGate.step(frame, dt)
                    val distanceReading = distanceGate.features?.let(distanceModel::large)
                    distanceDecision.step(distanceReading, dt)
                } else if (input != PositionInput.STEREO || distanceModel == null) {
                    distanceGate.reset()
                    distanceDecision.reset()
                }
                val distanceVoiceActive = input == PositionInput.STEREO &&
                    distanceModel != null && distanceGate.features != null
                val distance = if (distanceVoiceActive) distanceDecision.distance else null
                val distanceBelief = if (distanceVoiceActive) distanceDecision.belief.toFloat() else null
                val soundsFar = distanceVoiceActive && (
                    distanceDecision.distance == VoiceDistance.LARGE || distanceDecision.belief > DISTANCE_TO_SMALL
                )

                updateCalibrationProgress()
                val now = SystemClock.elapsedRealtime()
                if (now - lastUiUpdate >= UI_UPDATE_MS) {
                    lastUiUpdate = now
                    stateMutable.update {
                        it.copy(
                            running = true,
                            input = input,
                            inputName = activeRecorder.routedDevice?.productName?.toString(),
                            sampleRate = plan.sampleRate,
                            level = analysis.level,
                            pose = pose,
                            direction = direction,
                            probabilities = direction?.probabilities,
                            calibration = calibrationFor(pose),
                            hasSideModel = sideModel != null,
                            hasDistanceModel = distanceModel != null,
                            distanceVoiceActive = distanceVoiceActive,
                            distance = distance,
                            distanceBelief = distanceBelief,
                            soundsFar = soundsFar,
                            readsDistance = readsDistance,
                            calibrating = activeCalibration.get()?.side,
                            error = null,
                        )
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            activeCalibration.getAndSet(null)?.completion?.complete(CalibrationResult(CalibrationResultCode.NO_STEREO_INPUT))
            stateMutable.update {
                it.copy(
                    running = false,
                    input = PositionInput.UNAVAILABLE,
                    level = 0f,
                    direction = null,
                    probabilities = null,
                    distanceVoiceActive = false,
                    distance = null,
                    distanceBelief = null,
                    soundsFar = false,
                    error = failure.message ?: "Microphone capture failed.",
                )
            }
        } finally {
            if (activeRecorder != null) {
                runCatching { if (activeRecorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) activeRecorder.stop() }
                runCatching { activeRecorder.release() }
            }
            recorder.compareAndSet(activeRecorder, null)
            channelCount = 0
            stateMutable.update {
                it.copy(running = false, level = 0f, direction = null, probabilities = null, distanceVoiceActive = false, distance = null, distanceBelief = null, calibrating = null, calibrationProgress = 0f)
            }
            captureJob.compareAndSet(currentCoroutineContext()[Job], null)
            stopPoseTracking()
        }
    }

    private fun updateCalibration(capture: CalibrationCapture, frame: StereoFrame, dt: Double) {
        if (capture.pose != currentPose) {
            finishCapture(capture, CalibrationResult(CalibrationResultCode.PHONE_MOVED))
            return
        }
        if (capture.completed) return
        capture.frames.add(frame)
        capture.quietest = minOf(capture.quietest, frame.power)
        if (frame.power > capture.quietest + CALIBRATION_VOICE_OVER_QUIET_DB) {
            val voicedNanos = capture.voicedDurationNanos.addAndGet((dt * NANOS_PER_SECOND).toLong())
            if (voicedNanos >= CALIBRATION_VOICE_DURATION_NANOS) {
                if (activeCalibration.compareAndSet(capture, null)) {
                    val next = calibrationFor(capture.pose).record(capture.side, capture.frames.toList())
                    if (next == null) {
                        finishCapture(capture, CalibrationResult(CalibrationResultCode.TOO_LITTLE_AUDIO))
                    } else {
                        synchronized(calibrationsLock) {
                            calibrations[capture.pose] = next
                            persistCalibrationsLocked()
                        }
                        stateMutable.update {
                            if (it.pose == capture.pose) {
                                it.copy(
                                    calibration = next,
                                    hasAnyCalibration = true,
                                    calibrationProgress = 1f,
                                    calibrationMessage = calibrationMessage(capture.side, next.issue()),
                                )
                            } else it
                        }
                        finishCapture(capture, CalibrationResult(CalibrationResultCode.SAVED, next.issue()))
                    }
                }
            }
        }
    }

    private fun updateCalibrationProgress() {
        val capture = activeCalibration.get() ?: return
        val step = (capture.progress * 20).toInt().coerceIn(0, 20)
        if (step != capture.lastProgressStep) {
            capture.lastProgressStep = step
            stateMutable.update {
                if (it.calibrating == capture.side) it.copy(calibrationProgress = capture.progress) else it
            }
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
        // Keep the 9 kHz model band below Nyquist; the model's accuracy is not claimed for Android hardware.
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
                        val actualChannels = built.channelCount.coerceAtLeast(1)
                        if (started && actualChannels in 1..2) return CapturePlan(built, sampleRate, actualChannels)
                    }
                    runCatching { built.stop() }
                    runCatching { built.release() }
                }
            }
        }
        return null
    }

    private fun startPoseTracking() {
        currentPose = PhonePose.UNKNOWN
        gravityInitialized = false
        flatSinceMillis = null
        heldSinceMillis = null
        val sensor = accelerometer ?: return
        sensorManager?.registerListener(poseListener, sensor, SensorManager.SENSOR_DELAY_GAME, Handler(Looper.getMainLooper()))
    }

    private fun stopPoseTracking() {
        sensorManager?.unregisterListener(poseListener)
        flatSinceMillis = null
        heldSinceMillis = null
    }

    private fun calibrationFor(pose: PhonePose): PositionCalibration = synchronized(calibrationsLock) {
        calibrations[pose] ?: PositionCalibration()
    }

    private fun hasAnyCalibration(): Boolean = synchronized(calibrationsLock) {
        calibrations.values.any { it.hasAny }
    }

    private fun loadCalibrations(): MutableMap<PhonePose, PositionCalibration> {
        val result = mutableMapOf<PhonePose, PositionCalibration>()
        val root = runCatching { JSONObject(preferences.getString(CALIBRATION_KEY, "{}")) }.getOrDefault(JSONObject())
        for (pose in PhonePose.entries) {
            val group = root.optJSONObject(pose.name) ?: continue
            val points = linkedMapOf<PositionSide, DirectionCues>()
            val spreads = linkedMapOf<PositionSide, DirectionCues>()
            for (side in PositionSide.entries) {
                readCue(group.optJSONObject(side.modelName))?.let { points[side] = it }
                readCue(group.optJSONObject("${side.modelName}Spread"))?.let { spreads[side] = it }
            }
            if (points.isNotEmpty()) result[pose] = PositionCalibration(points, spreads)
        }
        return result
    }

    private fun persistCalibrationsLocked() {
        val root = JSONObject()
        for ((pose, calibration) in calibrations) {
            val group = JSONObject()
            for (side in PositionSide.entries) {
                calibration.points[side]?.let { group.put(side.modelName, it.toJson()) }
                calibration.spreads[side]?.let { group.put("${side.modelName}Spread", it.toJson()) }
            }
            root.put(pose.name, group)
        }
        preferences.edit().putString(CALIBRATION_KEY, root.toString()).apply()
    }

    private fun readCue(json: JSONObject?): DirectionCues? {
        if (json == null) return null
        return runCatching {
            DirectionCues(json.getDouble("level"), json.getDouble("phase"))
        }.getOrNull()
    }

    private fun DirectionCues.toJson() = JSONObject().apply {
        put("level", level)
        put("phase", phase)
    }

    private data class CapturePlan(val recorder: AudioRecord, val sampleRate: Int, val channelCount: Int)

    private class CalibrationCapture(val side: PositionSide, val pose: PhonePose) {
        val frames = ConcurrentLinkedQueue<StereoFrame>()
        val voicedDurationNanos = AtomicLong(0L)
        val completion = CompletableDeferred<CalibrationResult>()
        @Volatile var quietest = Double.POSITIVE_INFINITY
        @Volatile var completed = false
        @Volatile var lastProgressStep = -1
        val progress: Float get() = (voicedDurationNanos.get().toDouble() / CALIBRATION_VOICE_DURATION_NANOS).toFloat().coerceIn(0f, 1f)
    }

    private companion object {
        const val PREFERENCES = "voice_position"
        const val CALIBRATION_KEY = "pose_calibrations_v2"
        const val FRAME_SIZE = 1024
        const val CALIBRATION_TIMEOUT_MS = 12_000L
        const val CALIBRATION_VOICE_DURATION_NANOS = 3_000_000_000L
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val CALIBRATION_VOICE_OVER_QUIET_DB = 20.0
        const val UI_UPDATE_MS = 100L
        const val DESK_SETTLE_MS = 1_500L
        const val HAND_SETTLE_MS = 500L
        const val GRAVITY_FILTER_ALPHA = 0.8f // Per-event low-pass heuristic; not upstream motion data.
        const val STANDARD_GRAVITY = 9.80665f
        const val DISTANCE_TO_SMALL = 0.35
        val SAMPLE_RATES = intArrayOf(48_000, 44_100, 32_000)
    }
}
