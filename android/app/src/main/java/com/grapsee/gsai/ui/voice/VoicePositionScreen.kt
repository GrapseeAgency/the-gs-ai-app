package com.grapsee.gsai.ui.voice

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grapsee.gsai.data.tts.VoicePlaybackState
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.components.RefIconButton
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.GsTheme
import com.grapsee.gsai.ui.voice.position.CalibrationIssue
import com.grapsee.gsai.ui.voice.position.CalibrationResult
import com.grapsee.gsai.ui.voice.position.CalibrationResultCode
import com.grapsee.gsai.ui.voice.position.DirectionEstimate
import com.grapsee.gsai.ui.voice.position.PositionInput
import com.grapsee.gsai.ui.voice.position.PositionSide
import com.grapsee.gsai.ui.voice.position.VoiceDistance
import com.grapsee.gsai.ui.voice.position.VoicePositionMonitor
import com.grapsee.gsai.ui.voice.position.VoicePositionUiState
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Experimental native Android Voice Position port. Dictation and TTS stay in
 * the existing Talk screen; this surface only analyzes microphone input locally.
 */
@Composable
fun VoicePositionScreen(
    onBack: () -> Unit,
    onOpenSpeech: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val monitor = remember(context) { VoicePositionMonitor(context) }
    val state by monitor.state.collectAsStateWithLifecycle()
    val appSpeaking by VoicePlaybackState.isSpeaking.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(monitor, appSpeaking) {
        monitor.setReadsDistance(!appSpeaking)
    }
    var microphoneGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }
    var calibrationNotice by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        microphoneGranted = granted
        permissionDenied = !granted
        if (granted) monitor.start()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, monitor, microphoneGranted) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    val grantedNow = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                    if (microphoneGranted != grantedNow) microphoneGranted = grantedNow
                    if (grantedNow) monitor.start() else monitor.stop()
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> monitor.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(monitor) {
        onDispose { monitor.close() }
    }

    val colors = GsTheme.colors
    val canAskAgain = remember(permissionDenied) {
        val activity = context as? Activity
        activity != null && runCatching {
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
        }.getOrDefault(false)
    }
    val requestMic: () -> Unit = {
        if (permissionDenied && !canAskAgain) {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                )
            }
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(GsMotion.spaceL),
            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceM)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                RefIconButton(
                    icon = GsIcons.X,
                    label = "Close Voice Position",
                    onClick = onBack,
                    tint = colors.textPrimary
                )
                Text(
                    text = "Voice Position",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary
                )
                TextButton(onClick = onOpenSpeech) {
                    Text("Talk", color = colors.textPrimary)
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
            ) {
                Text(
                    text = "Where is the voice?",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Calibrate left, ahead and right for this phone and how it is held. Microphone audio is analyzed on this device only; it is not transcribed or uploaded.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )
            }

            PositionHeroCard(
                state = state,
                onToggle = {
                    when {
                        state.running -> monitor.stop()
                        !microphoneGranted -> requestMic()
                        else -> monitor.start()
                    }
                }
            )
            PositionReadingsCard(state = state)
            CalibrationCard(
                state = state,
                notice = calibrationNotice,
                onCalibrate = { side ->
                    calibrationNotice = null
                    scope.launch {
                        calibrationNotice = calibrationResultCopy(monitor.calibrate(side))
                    }
                },
                onReset = { confirmReset = true }
            )

            if (!microphoneGranted) {
                MessageCard(
                    text = if (permissionDenied) {
                        "Microphone access is off. Allow it to sense Voice Position."
                    } else {
                        "Microphone access is needed for local stereo analysis."
                    },
                    actionLabel = if (permissionDenied && !canAskAgain) "Open Settings" else "Allow microphone",
                    onAction = requestMic
                )
            }

            state.error?.let { error ->
                MessageCard(text = error)
            }

            Text(
                text = "This is an experimental native Android port of the open-source model reader, not validated on Android hardware. Add your licensed VoicePosition.mfxl to app/src/main/assets for model readings; without it, calibrate side locally and distance stays unavailable. iPhone 15 Pro beta results do not apply here. Mono or indistinguishable channels—including many Bluetooth routes—show no direction or distance. Audio stays on-device.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset Voice Position calibration?") },
            text = { Text("This removes the saved left, ahead and right references for both phone poses.") },
            confirmButton = {
                TextButton(onClick = {
                    monitor.resetCalibration()
                    calibrationNotice = "Calibration cleared for this device."
                    confirmReset = false
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PositionHeroCard(state: VoicePositionUiState, onToggle: () -> Unit) {
    val colors = GsTheme.colors
    val direction = state.direction
    val headline = when {
        state.input == PositionInput.UNAVAILABLE -> "Microphone unavailable"
        direction != null -> direction.side.label
        !state.running -> "Ready to listen"
        state.input == PositionInput.MONO -> "Direction unavailable"
        state.input == PositionInput.UNKNOWN -> "Checking stereo input"
        !state.hasSideModel && !state.calibration.isReady -> "Position model unavailable"
        else -> "Listening for a voice"
    }
    val status = when {
        state.input == PositionInput.UNAVAILABLE -> state.error ?: "This device could not open a microphone input."
        !state.running -> "Start local microphone sensing to read the stereo image."
        state.input == PositionInput.MONO -> "The audio route is mono or both channels are indistinguishable. No direction or distance is reported."
        state.input == PositionInput.UNKNOWN -> "Waiting to verify distinct stereo input from the phone's microphones."
        direction != null -> {
            val source = if (direction.modelBased) "model" else "calibration"
            "Live $source reading · pan ${String.format(Locale.US, "%+.2f", direction.pan)}"
        }
        state.calibration.issue() == CalibrationIssue.SIDES_TOO_ALIKE -> "Left and right calibrations sound alike. Move farther apart and recalibrate."
        state.calibration.issue() == CalibrationIssue.AHEAD_OUTSIDE -> "Ahead must fall between the left and right calibration points."
        state.modelError != null -> "Model unavailable: ${state.modelError}"
        !state.hasSideModel && !state.calibration.isReady -> if (state.modelLoaded) {
            "No side classifier for ${state.pose.label.lowercase()}. Wait for pose sensing or calibrate this pose."
        } else {
            "No model file is bundled. Add your licensed VoicePosition.mfxl or calibrate both outer sides."
        }
        else -> "Waiting for a voiced frame; no direction is shown during silence."
    }

    Surface(
        color = colors.raisedSurface,
        shape = RoundedCornerShape(GsMotion.radiusCard),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(GsMotion.spaceL),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
        ) {
            GsVoiceRing(
                phase = if (state.running) RingPhase.Listening else RingPhase.Idle,
                level = { if (state.running) state.level else 0f },
                direction = if (state.running && state.input == PositionInput.STEREO) direction?.pan else null,
                diameter = 224,
                label = if (state.running) "Voice position ring" else "Voice Position is paused"
            )
            Text(
                text = headline,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )

            PanRail(direction = direction)

            GsChip(
                text = if (state.running) "Stop sensing" else "Start sensing",
                selected = state.running,
                onClick = onToggle
            )
        }
    }
}

@Composable
private fun PositionReadingsCard(state: VoicePositionUiState) {
    val colors = GsTheme.colors
    Surface(
        color = colors.raisedSurface,
        shape = RoundedCornerShape(GsMotion.radiusCard),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(GsMotion.spaceM),
            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceM)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceM)) {
                ReadingCell(
                    title = "Pose",
                    value = state.pose.label,
                    modifier = Modifier.weight(1f)
                )
                ReadingCell(
                    title = "Input",
                    value = when (state.input) {
                        PositionInput.STEREO -> "Stereo"
                        PositionInput.MONO -> "Mono"
                        PositionInput.UNAVAILABLE -> "Unavailable"
                        PositionInput.UNKNOWN -> "Not verified"
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            state.inputName?.let { name ->
                Text("Audio device · $name", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            }

            Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceXS)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Loudness", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                    Text(
                        if (state.running) "${(state.level * 100).toInt()}%" else "—",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary
                    )
                }
                AudioLevelRail(level = if (state.running) state.level else 0f)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceM)) {
                ReadingCell(
                    title = "Distance",
                    value = when {
                        state.input != PositionInput.STEREO || !state.hasDistanceModel -> "Unavailable"
                        !state.readsDistance -> "Paused"
                        !state.distanceVoiceActive -> "Waiting for voice"
                        state.distance == VoiceDistance.SMALL -> "Near"
                        state.distance == VoiceDistance.LARGE -> "Far"
                        else -> "Waiting for voice"
                    },
                    modifier = Modifier.weight(1f)
                )
                ReadingCell(
                    title = "Model",
                    value = when {
                        state.modelError != null -> "Invalid model file"
                        state.modelLoaded -> "VoicePosition ${state.modelVersion.orEmpty()}"
                        else -> "Not installed · calibrate"
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            val distanceDetail = when {
                state.input != PositionInput.STEREO -> "Stereo input is required; loudness is never used as a distance estimate."
                !state.modelLoaded -> "VoicePosition.mfxl is not installed. Calibration enables side only; distance stays unavailable."
                !state.hasDistanceModel -> "The loaded model has no distance classifier for the current pose."
                !state.readsDistance -> "Distance updates are paused while GS reads aloud."
                !state.distanceVoiceActive -> "No voiced distance reading; silence is unavailable."
                state.distanceBelief != null -> "Distance-model belief ${(state.distanceBelief * 100).toInt()}% · categorical, not metres."
                else -> "Waiting for the model's distance reading."
            }
            Text(
                text = distanceDetail,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary
            )
            state.probabilities?.let { probabilities ->
                if (state.direction?.modelBased == true && probabilities.size == 3) {
                    Text(
                        text = "Model probabilities · L ${(probabilities[0] * 100).toInt()}%  A ${(probabilities[1] * 100).toInt()}%  R ${(probabilities[2] * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun CalibrationCard(
    state: VoicePositionUiState,
    notice: String?,
    onCalibrate: (PositionSide) -> Unit,
    onReset: () -> Unit,
) {
    val colors = GsTheme.colors
    Surface(
        color = colors.raisedSurface,
        shape = RoundedCornerShape(GsMotion.radiusCard),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(GsMotion.spaceM),
            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Calibrate this phone", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                TextButton(onClick = onReset, enabled = state.hasAnyCalibration && state.calibrating == null) { Text("Reset") }
            }
            Text(
                "Keep the phone still. Stand at each marked side and speak naturally for a few seconds.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceXS)) {
                PositionSide.entries.forEach { side ->
                    val calibrated = state.calibration.point(side) != null
                    val enabled = state.running && state.input == PositionInput.STEREO &&
                        state.calibrating == null
                    GsChip(
                        text = if (calibrated) "✓ ${side.label}" else side.label,
                        selected = calibrated || state.calibrating == side,
                        onClick = if (enabled) ({ onCalibrate(side) }) else null,
                    )
                }
            }
            state.calibrating?.let { side ->
                Text(
                    text = "Speak from ${side.label.lowercase()}… ${(state.calibrationProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textPrimary
                )
                CalibrationProgress(state.calibrationProgress)
            }
            (notice ?: state.calibrationMessage)?.let { message ->
                Text(message, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
            Text(
                "Saved per detected pose (hand / desk). If motion sensing is unavailable, the pose is shown as unavailable and stored separately. Recalibrate for each phone; Android mic routing varies.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
private fun ReadingCell(title: String, value: String, modifier: Modifier = Modifier) {
    val colors = GsTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
        Text(value, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
    }
}

@Composable
private fun PanRail(direction: DirectionEstimate?) {
    val colors = GsTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Canvas(Modifier.fillMaxWidth().height(28.dp)) {
            val y = size.height / 2f
            drawLine(
                color = colors.textSecondary.copy(alpha = 0.42f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            for (fraction in listOf(0f, 0.5f, 1f)) {
                drawCircle(
                    color = colors.textSecondary.copy(alpha = 0.7f),
                    radius = 2.dp.toPx(),
                    center = Offset(size.width * fraction, y)
                )
            }
            direction?.let {
                drawCircle(
                    color = colors.accentStrong,
                    radius = 6.dp.toPx(),
                    center = Offset(size.width * ((it.pan + 1f) / 2f).coerceIn(0f, 1f), y)
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("LEFT", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            Text("AHEAD", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            Text("RIGHT", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
        }
    }
}

@Composable
private fun AudioLevelRail(level: Float) {
    val colors = GsTheme.colors
    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
        val radius = size.height / 2f
        drawRoundRect(
            color = colors.textSecondary.copy(alpha = 0.16f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
        )
        if (level > 0f) {
            drawRoundRect(
                color = colors.accentStrong,
                size = androidx.compose.ui.geometry.Size(size.width * level.coerceIn(0f, 1f), size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
            )
        }
    }
}

@Composable
private fun CalibrationProgress(progress: Float) {
    val colors = GsTheme.colors
    Canvas(Modifier.fillMaxWidth().height(5.dp)) {
        val radius = size.height / 2f
        drawRoundRect(
            color = colors.textSecondary.copy(alpha = 0.16f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
        )
        drawRoundRect(
            color = colors.accentStrong,
            size = androidx.compose.ui.geometry.Size(size.width * progress.coerceIn(0f, 1f), size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
        )
    }
}

@Composable
private fun MessageCard(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    val colors = GsTheme.colors
    Surface(
        color = colors.raisedSurface,
        shape = RoundedCornerShape(GsMotion.radiusCard),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(GsMotion.spaceM),
            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, textAlign = TextAlign.Center)
            if (actionLabel != null && onAction != null) {
                GsChip(text = actionLabel, selected = false, onClick = onAction)
            }
        }
    }
}

private fun calibrationResultCopy(result: CalibrationResult): String = when (result.code) {
    CalibrationResultCode.SAVED -> when (result.issue) {
        CalibrationIssue.SIDES_TOO_ALIKE -> "Saved, but left and right sound alike. Move farther apart and recalibrate."
        CalibrationIssue.AHEAD_OUTSIDE -> "Saved, but ahead must be between the left and right positions. Recalibrate ahead."
        null -> "Calibration saved for this phone pose."
    }
    CalibrationResultCode.TIMED_OUT -> "Timed out before enough speech was captured. Try again and speak for a few seconds."
    CalibrationResultCode.NO_STEREO_INPUT -> "Stereo microphone input is unavailable on this device or route."
    CalibrationResultCode.PHONE_MOVED -> "The phone changed pose during calibration. Keep it still and try again."
    CalibrationResultCode.TOO_LITTLE_AUDIO -> "Not enough clear stereo speech was captured. Try again in a quieter room."
    CalibrationResultCode.ALREADY_RUNNING -> "A calibration is already in progress."
    CalibrationResultCode.NOT_LISTENING -> "Start Voice Position before calibrating."
}
