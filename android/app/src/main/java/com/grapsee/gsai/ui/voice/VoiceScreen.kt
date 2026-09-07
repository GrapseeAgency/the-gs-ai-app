package com.grapsee.gsai.ui.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.theme.Aeruo
import com.grapsee.gsai.ui.theme.GsMotion

/** Call-end red — the single non-aurora accent allowed in voice mode. */
private val EndCallRed = Color(0xFFE5484D)

/**
 * Honest engine states. Every status line the user can ever see maps to what
 * the recognizer is actually doing — voice mode never performs "Listening…"
 * with the microphone off.
 */
private enum class VoicePhase {
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
 * Full-screen dark voice mode, now real: on-device SpeechRecognizer drives the
 * live transcript, TextToSpeech reads a finished transcript back, and the
 * result hands off to a brand-new chat via the prefill route. The obsidian
 * palette and the aurora waveform are unchanged — the waveform just only
 * breathes while the microphone is genuinely open.
 */
@Composable
fun VoiceScreen(
    onBack: () -> Unit,
    // Result hand-off: opens a new chat seeded with the spoken text.
    onSendToChat: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

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

    // Read-aloud engine for the speaker toggle — same discipline as chat: silent
    // when the device has no engine, shut down when the screen leaves.
    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember {
        TextToSpeech(context) { status -> ttsReady = status == TextToSpeech.SUCCESS }
    }
    DisposableEffect(Unit) {
        onDispose { runCatching { tts.stop(); tts.shutdown() } }
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
            override fun onRmsChanged(rmsdB: Float) {}
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
                        runCatching { tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voice-result") }
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
            runCatching { tts.speak(finalTranscript, TextToSpeech.QUEUE_FLUSH, null, "voice-result") }
        } else if (!speakerOn) {
            runCatching { tts.stop() }
        }
    }

    val statusLine = when (phase) {
        VoicePhase.Idle -> "Tap the mic and speak"
        VoicePhase.Listening -> "Listening…"
        VoicePhase.Processing -> if (transcript.isBlank()) "Starting the mic…" else "Processing…"
        VoicePhase.Result -> "Got it"
        VoicePhase.Paused -> "Paused"
        VoicePhase.Denied -> "Microphone permission is off — allow it to speak"
        VoicePhase.Unavailable -> "Speech recognition isn't available on this device"
        VoicePhase.NoSpeech -> "Didn't catch that — tap the mic and try again"
        VoicePhase.Error -> "That didn't come through — tap the mic and try again"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Aeruo.Obsidian)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(GsMotion.spaceL)
        ) {
            // Close — top start
            Row(modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close voice mode",
                        tint = Aeruo.TextDark
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = statusLine,
                style = MaterialTheme.typography.displaySmall,
                color = if (phase == VoicePhase.Denied || phase == VoicePhase.Unavailable ||
                    phase == VoicePhase.NoSpeech || phase == VoicePhase.Error)
                    Aeruo.TextMutedDark else Aeruo.TextDark,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(GsMotion.spaceL))

            // Waveform — 24 aurora bars, breathing only while the mic is live.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(24) { index ->
                    WaveformBar(index = index, active = phase == VoicePhase.Listening)
                }
            }

            Spacer(modifier = Modifier.height(GsMotion.spaceL))

            // Transcript — the real words (partial while speaking, final after).
            Surface(
                color = Aeruo.RaisedDark,
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
                        color = if (transcript.isBlank()) Aeruo.TextMutedDark else Aeruo.TextDark
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
                                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                }
                            )
                            GsChip(
                                text = "Try again",
                                selected = false,
                                onClick = { sessionTick++ }
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
                        Icons.Outlined.Mic else Icons.Outlined.MicOff,
                    container = if (phase == VoicePhase.Listening) Aeruo.ContainerHighDark
                    else Aeruo.ContainerDark,
                    tint = if (phase == VoicePhase.Listening) Aeruo.TextDark else Aeruo.TextMutedDark,
                    contentDescription = if (phase == VoicePhase.Listening) "Pause" else "Start listening"
                ) {
                    toggleMute()
                }
                VoiceCircle(
                    icon = Icons.Outlined.CallEnd,
                    container = EndCallRed.copy(alpha = 0.18f),
                    tint = EndCallRed,
                    contentDescription = "End voice session"
                ) {
                    onBack()
                }
                VoiceCircle(
                    icon = if (speakerOn) Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff,
                    container = Aeruo.ContainerDark,
                    tint = if (speakerOn) Aeruo.TextDark else Aeruo.TextMutedDark,
                    contentDescription = if (speakerOn) "Read-back on" else "Read-back off"
                ) {
                    toggleSpeaker()
                }
            }

            Spacer(modifier = Modifier.height(GsMotion.spaceM))

            Text(
                text = "Speech stays on this device · ${java.util.Locale.getDefault().displayLanguage}",
                style = MaterialTheme.typography.labelMedium,
                color = Aeruo.TextMutedDark,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(GsMotion.spaceS))
        }
    }
}

/** One animated waveform bar — height oscillates 8–56dp with a per-index phase delay.
 *  Draw-phase: the phase state is read inside drawBehind, so the bar never recomposes
 *  and never re-lays-out; only its 4×56dp node re-renders each frame. Bars rest at a
 *  calm 10dp whenever the microphone is not genuinely open. */
@Composable
private fun WaveformBar(index: Int, active: Boolean) {
    if (!active) {
        val brush = remember { Brush.verticalGradient(colors = Aeruo.Aurora) }
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
    val brush = remember {
        Brush.verticalGradient(colors = Aeruo.Aurora)
    }
    Box(
        modifier = Modifier
            .width(4.dp)
            .height(56.dp)
            .drawBehind {
                val barHeight = 8.dp.toPx() + 48.dp.toPx() * phase
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
