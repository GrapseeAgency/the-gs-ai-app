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
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.components.RefIconButton
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.orbs.OrbPhase
import com.grapsee.gsai.ui.orbs.OrbSize
import com.grapsee.gsai.ui.orbs.OrbState
import com.grapsee.gsai.ui.orbs.ThinkingOrb
import com.grapsee.gsai.ui.orbs.luminance
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.GsTheme
import com.grapsee.gsai.ui.voice.position.CalibrationIssue
import com.grapsee.gsai.ui.voice.position.CalibrationResult
import com.grapsee.gsai.ui.voice.position.CalibrationResultCode
import com.grapsee.gsai.ui.voice.position.DirectionEstimate
import com.grapsee.gsai.ui.voice.position.PositionInput
import com.grapsee.gsai.ui.voice.position.PositionSide
import com.grapsee.gsai.ui.voice.position.VoicePositionMonitor
import com.grapsee.gsai.ui.voice.position.VoicePositionUiState
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Calibration-based Android voice-position experience. Dictation and TTS stay
 * in the existing Talk screen; this screen only reads stereo input locally.
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
    val scope = rememberCoroutineScope()
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
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, monitor, microphoneGranted, state.supported) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (microphoneGranted && state.supported) monitor.start()
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
                    text = "Calibrate left, ahead and right for this phone and how it is held.",
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
                        !state.supported -> Unit
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

            if (!microphoneGranted && state.supported) {
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
                text = "Android uses device-local stereo calibration; there is no validated Android model. Distance is unavailable. Mono or Bluetooth audio may not provide direction. No audio is uploaded.",
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
        !state.supported -> "Android 10 or newer"
        direction != null -> direction.side.label
        !state.running -> "Ready to listen"
        state.input == PositionInput.MONO -> "Direction unavailable"
        !state.calibration.isReady -> "Calibrate left and right"
        else -> "Listening for a voice"
    }
    val status = when {
        !state.supported -> "This app keeps its existing Android minimum; Voice Position requires Android 10+ APIs."
        !state.running -> "Start local microphone sensing to read the stereo image."
        state.input == PositionInput.MONO -> "The audio route is mono or both channels are identical. No direction is reported."
        state.input == PositionInput.UNKNOWN -> "Stereo has not been verified yet. Use the phone's built-in microphone and calibrate from each side."
        direction != null -> "Live calibrated reading · pan ${String.format(Locale.US, "%+.2f", direction.pan)} · ${ (direction.confidence * 100).toInt()}% fit"
        state.calibration.issue() == CalibrationIssue.SIDES_TOO_ALIKE -> "Left and right calibrations sound alike. Move farther apart and recalibrate."
        state.calibration.issue() == CalibrationIssue.AHEAD_OUTSIDE -> "Ahead must fall between the left and right calibration points."
        !state.calibration.isReady -> "No model is bundled. Calibrate both outer sides to enable direction."
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
            ThinkingOrb(
                state = OrbState.LISTENING,
                size = OrbSize.STANDARD,
                contentDescription = if (state.running) "Listening for stereo voice position" else "Voice Position is paused",
                luminance = if (state.running) OrbPhase.RECORDING.luminance else OrbPhase.LISTENING.luminance,
                amplitude = if (state.running) state.level else -1f,
                renderScale = 1.65f,
                modifier = Modifier.padding(vertical = GsMotion.spaceS)
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
                text = when {
                    state.running -> "Stop sensing"
                    !state.supported -> "Android 10+ required"
                    else -> "Start sensing"
                },
                selected = state.running,
                onClick = if (state.supported) onToggle else null
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
                    value = "Unavailable",
                    modifier = Modifier.weight(1f)
                )
                ReadingCell(
                    title = "Model",
                    value = "Calibration only",
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = "No Android distance model is bundled or validated; volume is not used as a distance estimate.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary
            )
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
                TextButton(onClick = onReset, enabled = state.calibration.isReady) { Text("Reset") }
            }
            Text(
                "Keep the phone still. Stand at each marked side and speak naturally for a few seconds.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceXS)) {
                PositionSide.entries.forEach { side ->
                    val calibrated = state.calibration.point(side) != null
                    val enabled = state.supported && state.running &&
                        state.input != PositionInput.MONO && state.input != PositionInput.UNAVAILABLE &&
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
