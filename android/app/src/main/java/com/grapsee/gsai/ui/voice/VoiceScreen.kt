package com.grapsee.gsai.ui.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import com.grapsee.gsai.data.tts.TtsFocus
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.components.RefIconButton
import com.grapsee.gsai.ui.orbs.OrbSize
import com.grapsee.gsai.ui.orbs.ThinkingOrb
import com.grapsee.gsai.ui.orbs.OrbPhase
import com.grapsee.gsai.ui.orbs.orbStateForVoice
import com.grapsee.gsai.ui.theme.GsHaptics
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.GsTheme
import com.grapsee.gsai.ui.theme.gsHaptic
import com.grapsee.gsai.ui.icons.GsIcons

/**
 * Honest engine states. Every status line the user can ever see maps to what
 * the recognizer is actually doing — voice mode never performs "Listening…"
 * with the microphone off.
 */
internal enum class VoicePhase {
    Idle,        // mic not started yet — one tap begins
    Listening,   // recognizer live, partial words stream in
    Processing,  // end of speech captured, waiting for the final result
    Result,      // transcript final — actions unlocked
    Paused,      // user muted an active session
    Denied,      // RECORD_AUDIO refused — path to Settings, no fake listening
    Unavailable, // device has no speech service at all
    NoSpeech,    // timeout / nothing intelligible said
    Error        // recognizer failed (offline engine, busy system, …)
}

/**
 * Full-screen voice mode, in the reference language: the global bloom stays
 * visible (no opaque scrim), the transcript is one cream-glass card, and the
 * only accent fills are the live microphone and the send action. On-device
 * SpeechRecognizer drives the live transcript, TextToSpeech reads a finished
 * transcript back, and the result hands off to a brand-new chat via the
 * prefill route. The waveform only breathes while the microphone is open.
 */
@Composable
fun VoiceScreen(
    onBack: () -> Unit,
    // Result hand-off: opens a new chat seeded with the spoken text.
    onSendToChat: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    // Platform touch confirmation for committed actions (send/copy).
    val view = LocalView.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current

    var phase by remember { mutableStateOf(VoicePhase.Idle) }
    // Live text while listening; final text once the result lands.
    var transcript by remember { mutableStateOf("") }
    var finalTranscript by remember { mutableStateOf("") }
    var speakerOn by remember { mutableStateOf(false) }
    // Permission state survives recomposition so re-entry skips the prompt.
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var sessionTick by remember { mutableStateOf(0) } // restart handle for a fresh listen

    // Real microphone level, fed by RecognitionListener.onRmsChanged while the
    // mic is open. Held as a Float state so the waveform reads it in the draw
    // phase — the bars track actual loudness without ever recomposing.
    val rmsLevel = remember { mutableStateOf(0f) }
    // Capability probe: some OEM recognizers never invoke onRmsChanged. The
    // first callback flips this true; while the mic is open and it is still
    // false, the waveform falls back to its oscillator so the screen is never
    // dead during real speech.
    val rmsSeen = remember { mutableStateOf(false) }

    // Read-aloud engine for the speaker toggle — same discipline as chat: the
    // engine is wrapped in the shared audio-focus helper (focus before speak,
    // abandon on stop/shutdown, stop on focus loss), silent when the device
    // has no engine, shut down when the screen leaves.
    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember {
        TtsFocus(
            context,
            onReady = { ttsReady = it },
            onStoppedByFocusLoss = { speakerOn = false }
        )
    }
    DisposableEffect(Unit) {
        onDispose { tts.shutdown() }
    }

    // The recognizer lives exactly as long as the screen does. Recreated on
    // demand for each fresh listen; destroyed with the screen — nothing keeps
    // listening (or holds the mic) after voice mode closes.
    val recognizer = remember { mutableStateOf<SpeechRecognizer?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            recognizer.value?.runCatching { destroy() }
            recognizer.value = null
        }
    }

    fun beginListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            phase = VoicePhase.Unavailable
            return
        }
        recognizer.value?.runCatching { destroy() }
        val newRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        newRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { phase = VoicePhase.Listening }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                rmsSeen.value = true
                // The system reports RMS in dB, empirically about [-2, 10] with
                // silence near the floor. Normalize and clamp to 0..1, then smooth
                // asymmetrically: rise quickly so onset reads, fall slowly so the
                // bars glide down instead of stuttering with each sample.
                val raw = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                val prev = rmsLevel.value
                rmsLevel.value =
                    if (raw > prev) prev + (raw - prev) * 0.5f
                    else prev + (raw - prev) * 0.12f
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { if (phase == VoicePhase.Listening) phase = VoicePhase.Processing }
            override fun onError(error: Int) {
                phase = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        VoicePhase.NoSpeech
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoicePhase.Denied
                    else -> VoicePhase.Error
                }
            }
            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (text.isBlank()) {
                    phase = VoicePhase.NoSpeech
                } else {
                    finalTranscript = text
                    transcript = text
                    phase = VoicePhase.Result
                    if (speakerOn && ttsReady) {
                        runCatching { tts.speak(text, "voice-result") }
                    }
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                // Partials arrive at phrase rate from the system service — direct
                // application is cheap and keeps the transcript genuinely live.
                partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { transcript = it }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        recognizer.value = newRecognizer
        transcript = ""
        finalTranscript = ""
        rmsLevel.value = 0f
        rmsSeen.value = false
        phase = VoicePhase.Processing
        newRecognizer.startListening(intent)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micGranted = granted
        if (granted) beginListening() else phase = VoicePhase.Denied
    }

    fun requestOrBegin() {
        if (micGranted) beginListening()
        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Returning with permission already granted starts straight away; first
    // visit waits for one explicit tap so the permission ask never surprises.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (micGranted && SpeechRecognizer.isRecognitionAvailable(context)) sessionTick = 1
    }
    androidx.compose.runtime.LaunchedEffect(sessionTick) {
        if (sessionTick > 0) beginListening()
    }

    fun toggleMute() {
        when (phase) {
            VoicePhase.Listening, VoicePhase.Processing -> {
                recognizer.value?.runCatching { cancel() }
                phase = VoicePhase.Paused
            }
            VoicePhase.Paused, VoicePhase.Idle, VoicePhase.NoSpeech, VoicePhase.Error ->
                requestOrBegin()
            else -> Unit
        }
    }

    fun toggleSpeaker() {
        speakerOn = !speakerOn
        if (speakerOn && phase == VoicePhase.Result && ttsReady) {
            runCatching { tts.speak(finalTranscript, "voice-result") }
        } else if (!speakerOn) {
            tts.stop()
        }
    }

    val statusLine = when (phase) {
        VoicePhase.Idle -> "Tap the mic and speak"
        VoicePhase.Listening -> "Listening…"
        VoicePhase.Processing -> if (transcript.isBlank()) "Starting the mic…" else "Processing…"
        VoicePhase.Result -> "Got it"
        VoicePhase.Paused -> "Paused"
        VoicePhase.Denied -> "Microphone permission is off, allow it to speak"
        VoicePhase.Unavailable -> "Speech recognition isn't available on this device"
        VoicePhase.NoSpeech -> "Didn't catch that, tap the mic and try again"
        VoicePhase.Error -> "That didn't come through, tap the mic and try again"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // No opaque appBackground: the global ReferenceBackdrop is the
            // surface here too, so voice mode belongs to the same product.
            // Voice mode is a full-bleed custom canvas (no GsScreenScaffold),
            // so it carries its own system-bar insets: safeDrawing covers the
            // status bar, gesture bar and display cutout around the 24dp rhythm.
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(GsMotion.spaceL)
        ) {
            // Close — top start, the same bare chevron/cross the reference uses.
            Row(modifier = Modifier.fillMaxWidth()) {
                RefIconButton(
                    icon = GsIcons.X,
                    label = "Close voice mode",
                    onClick = onBack,
                    tint = GsTheme.colors.textPrimary
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Phase 4: the activity orb mirrors the recognizer's REAL phase —
            // "Listening…" while the mic is live, "Working…" while it
            // processes. Idle, error and permission states show no orb: an
            // absent state is never faked.
            orbStateForVoice(phase)?.let { orbState ->
                // The live RMS feeds the orb's reactive core (mandate: RECORDING
                // glows with the voice); processing falls back to the default
                // luminous wash. This is the Android mirror of the iOS
                // RMS-in-installTap path.
                val live = phase == VoicePhase.Listening
                ThinkingOrb(
                    state = orbState,
                    size = OrbSize.STANDARD,
                    contentDescription = orbState.label,
                    luminance = if (live) OrbPhase.RECORDING.luminance else null,
                    amplitude = if (live) rmsLevel.value else -1f,
                    modifier = Modifier.padding(bottom = GsMotion.spaceM)
                )
            }

            Text(
                text = statusLine,
                style = MaterialTheme.typography.displaySmall,
                color = if (phase == VoicePhase.Denied || phase == VoicePhase.Unavailable ||
                    phase == VoicePhase.NoSpeech || phase == VoicePhase.Error)
                    GsTheme.colors.textSecondary else GsTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // Permission recovery: after "Don't ask again" the runtime launcher
            // is a silent no-op forever. When the system can still ask (rationale
            // available) we re-launch it; once it can't, the honest path is the
            // app's own Settings page where the toggle actually lives.
            if (phase == VoicePhase.Denied) {
                Spacer(modifier = Modifier.height(GsMotion.spaceS))
                val activity = context as? android.app.Activity
                val canAskAgain = activity != null && runCatching {
                    ActivityCompat.shouldShowRequestPermissionRationale(
                        activity,
                        Manifest.permission.RECORD_AUDIO
                    )
                }.getOrDefault(false)
                val openAppSettings: () -> Unit = {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (canAskAgain) {
                        GsChip(
                            text = "Allow microphone",
                            selected = false,
                            onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                        )
                    } else {
                        GsChip(text = "Open Settings", selected = false, onClick = openAppSettings)
                    }
                }
            }

            Spacer(modifier = Modifier.height(GsMotion.spaceL))

            // Waveform — 24 aurora bars, breathing only while the mic is live.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(24) { index ->
                    WaveformBar(
                        index = index,
                        active = phase == VoicePhase.Listening,
                        level = rmsLevel,
                        driven = rmsSeen.value,
                    )
                }
            }

            Spacer(modifier = Modifier.height(GsMotion.spaceL))

            // Transcript — the real words (partial while speaking, final after).
            Surface(
                color = GsTheme.colors.raisedSurface,
                shape = RoundedCornerShape(GsMotion.radiusCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(GsMotion.spaceM),
                    verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
                ) {
                    Text(
                        text = if (transcript.isBlank()) "Your words will appear here."
                        else transcript,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (transcript.isBlank()) GsTheme.colors.textSecondary else GsTheme.colors.textPrimary
                    )
                    if (phase == VoicePhase.Result) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(
                                GsMotion.spaceS, Alignment.CenterHorizontally
                            )
                        ) {
                            GsChip(
                                text = "Send to chat",
                                selected = true,
                                onClick = {
                                    // The recognizer is done with the mic before
                                    // the hand-off — close the session cleanly.
                                    GsHaptics.longPress(haptics)
                                    recognizer.value?.runCatching { destroy() }
                                    recognizer.value = null
                                    onSendToChat(finalTranscript)
                                }
                            )
                            GsChip(
                                text = "Copy",
                                selected = false,
                                onClick = {
                                    clipboard.setText(AnnotatedString(finalTranscript))
                                    GsHaptics.press(view)
                                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                }
                            )
                            GsChip(
                                text = "Try again",
                                selected = false,
                                onClick = {
                                    GsHaptics.longPress(haptics)
                                    sessionTick++
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VoiceCircle(
                    icon = if (phase == VoicePhase.Listening || phase == VoicePhase.Processing)
                        GsIcons.Mic else GsIcons.MicOff,
                    container = GsTheme.colors.elevatedSurface,
                    tint = if (phase == VoicePhase.Listening) GsTheme.colors.textPrimary else GsTheme.colors.textSecondary,
                    contentDescription = if (phase == VoicePhase.Listening) "Pause" else "Start listening"
                ) {
                    toggleMute()
                }
                VoiceCircle(
                    icon = GsIcons.PhoneOff,
                    container = GsTheme.colors.error.copy(alpha = 0.18f),
                    tint = GsTheme.colors.error,
                    contentDescription = "End voice session"
                ) {
                    onBack()
                }
                VoiceCircle(
                    icon = if (speakerOn) GsIcons.Volume2 else GsIcons.VolumeX,
                    container = GsTheme.colors.elevatedSurface,
                    tint = if (speakerOn) GsTheme.colors.textPrimary else GsTheme.colors.textSecondary,
                    contentDescription = if (speakerOn) "Read-back on" else "Read-back off"
                ) {
                    toggleSpeaker()
                }
            }

            Spacer(modifier = Modifier.height(GsMotion.spaceM))

            Text(
                text = "Speech stays on this device · ${java.util.Locale.getDefault().displayLanguage}",
                style = MaterialTheme.typography.labelMedium,
                color = GsTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(GsMotion.spaceS))
        }
    }
}

/** One waveform bar. Height spans 8–56dp.
 *
 *  When the recognizer reports a live level ([driven], proven by the onRmsChanged
 *  probe) the bar's amplitude follows the microphone: loud speech pushes it up,
 *  silence lets it settle near the baseline. A per-index oscillator is layered on
 *  top so the surface shimmers rather than moving as one slab. When no level ever
 *  arrives the oscillator carries the bar alone, so older recognizers still get a
 *  lively — if not audio-true — waveform.
 *
 *  Draw-phase throughout: [level] and the oscillator are read inside drawBehind,
 *  so a level change invalidates the draw but never recomposes or re-lays-out the
 *  bar. Bars rest at a calm 10dp whenever the microphone is not genuinely open. */
@Composable
private fun WaveformBar(
    index: Int,
    active: Boolean,
    level: State<Float>,
    driven: Boolean,
) {
    val aurora = GsTheme.colors.aurora
    val brush = remember(aurora) { Brush.verticalGradient(colors = aurora) }
    if (!active) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(56.dp)
                .drawBehind {
                    val barHeight = 10.dp.toPx()
                    drawRoundRect(
                        brush = brush,
                        topLeft = Offset(0f, size.height - barHeight),
                        size = Size(size.width, barHeight),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                }
        )
        return
    }
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(index * 45)
        ),
        label = "wavePhase"
    )
    Box(
        modifier = Modifier
            .width(4.dp)
            .height(56.dp)
            .drawBehind {
                // Oscillator gives each bar its own rhythm; the live level scales
                // the whole excursion so the group breathes with the speaker.
                val shimmer = 0.55f + 0.45f * phase
                val amp = if (driven) level.value * shimmer else shimmer
                val barHeight = 8.dp.toPx() + 48.dp.toPx() * amp.coerceIn(0f, 1f)
                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(0f, size.height - barHeight),
                    size = Size(size.width, barHeight),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }
    )
}

@Composable
private fun VoiceCircle(
    icon: ImageVector,
    container: Color,
    tint: Color,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        modifier = Modifier.size(64.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
