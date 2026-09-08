package com.grapsee.gsai.data.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech

/**
 * Read-aloud with audio-focus discipline. Every TextToSpeech instance in the
 * app (chat read-aloud, voice-mode read-back) goes through this wrapper so a
 * spoken answer never blasts over the user's music:
 *
 *  - an [AudioFocusRequest] is built once with AUDIOFOCUS_GAIN and
 *    USAGE_MEDIA + CONTENT_TYPE_SPEECH (minSdk 26 → no deprecated shim needed);
 *  - focus is requested before every [speak] — a denial returns ERROR instead
 *    of playing over the current audio owner;
 *  - focus is abandoned on [stop]/[shutdown]/[abandonFocus] and whenever an
 *    utterance completes;
 *  - AUDIOFOCUS_LOSS (and LOSS_TRANSIENT — TTS cannot pause mid-utterance, so
 *    the honest move is to stop) tears playback down through the engine and
 *    notifies the screen via [onStoppedByFocusLoss] so its speaking state
 *    stays truthful.
 *
 * TTS lifecycle ownership stays with the caller (create in remember, dispose
 * via [shutdown] in a DisposableEffect).
 */
class TtsFocus(
    context: Context,
    private val onReady: (Boolean) -> Unit = {},
    private val onStoppedByFocusLoss: () -> Unit = {}
) {
    val engine: TextToSpeech =
        TextToSpeech(context) { status -> onReady(status == TextToSpeech.SUCCESS) }

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val focusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                val wasSpeaking = runCatching { engine.isSpeaking }.getOrDefault(false)
                runCatching { engine.stop() }
                abandonFocus()
                if (wasSpeaking) onStoppedByFocusLoss()
            }
            else -> Unit
        }
    }

    private val focusRequest: AudioFocusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener(focusListener)
            .build()

    private fun requestFocus(): Boolean =
        runCatching { audioManager.requestAudioFocus(focusRequest) }
            .getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_FAILED) ==
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    /** Release the ear — called on stop/shutdown/utterance completion. */
    fun abandonFocus() {
        runCatching { audioManager.abandonAudioFocusRequest(focusRequest) }
    }

    /** Focus-gated QUEUE_FLUSH speak; returns TextToSpeech.SUCCESS/ERROR. */
    fun speak(text: String, utteranceId: String): Int {
        if (!requestFocus()) return TextToSpeech.ERROR
        return engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        runCatching { engine.stop() }
        abandonFocus()
    }

    fun shutdown() {
        runCatching { engine.stop() }
        abandonFocus()
        runCatching { engine.shutdown() }
    }
}
