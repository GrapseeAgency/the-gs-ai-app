package com.grapsee.gsai.ui.attachment

import android.media.MediaPlayer
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import java.io.File

/** Native playback of actual staged bytes, no external player/install needed.
 * Timings come from MediaPlayer, never generated waveform or fake progress.
 */
@Composable
internal fun MediaPreview(file: File, video: Boolean, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 4.dp) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(file.name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                if (video) NativeVideoPreview(file) else NativeAudioPreview(file)
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close preview") }
            }
        }
    }
}

@Composable
private fun NativeVideoPreview(file: File) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var failed by remember(file) { mutableStateOf(false) }
    val view = remember(file) { VideoView(context) }
    DisposableEffect(view, lifecycle) {
        view.setMediaController(MediaController(context).apply { setAnchorView(view) })
        view.setOnErrorListener { _, _, _ -> failed = true; true }
        view.setOnPreparedListener {
            // Preparation may finish after Home/background. Never start hidden playback.
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) view.start()
        }
        view.setVideoPath(file.absolutePath)
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) view.pause() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); view.stopPlayback() }
    }
    if (failed) {
        Text("This device could not decode the video.", color = MaterialTheme.colorScheme.error)
    } else {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxWidth().height(220.dp))
        Text("Tap the video for playback controls", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun NativeAudioPreview(file: File) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val player = remember(file) { MediaPlayer() }
    var ready by remember(file) { mutableStateOf(false) }
    var playing by remember(file) { mutableStateOf(false) }
    var failed by remember(file) { mutableStateOf(false) }
    var duration by remember(file) { mutableIntStateOf(0) }
    var position by remember(file) { mutableIntStateOf(0) }
    DisposableEffect(player, lifecycle) {
        player.setOnPreparedListener { ready = true; duration = it.duration.coerceAtLeast(0) }
        player.setOnCompletionListener { playing = false; position = duration }
        player.setOnErrorListener { _, _, _ -> failed = true; playing = false; true }
        try { player.setDataSource(file.absolutePath); player.prepareAsync() }
        catch (_: Exception) { failed = true }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && ready && playing) {
                player.pause(); playing = false
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); player.release() }
    }
    LaunchedEffect(player, playing) {
        while (playing) {
            position = runCatching { player.currentPosition }.getOrDefault(position)
            delay(200)
        }
    }
    if (failed) {
        Text("This device could not decode the audio.", color = MaterialTheme.colorScheme.error)
    } else {
        if (!ready) LinearProgressIndicator(Modifier.fillMaxWidth())
        Slider(value = position.toFloat().coerceIn(0f, duration.coerceAtLeast(1).toFloat()),
            onValueChange = { position = it.toInt(); player.seekTo(position) },
            valueRange = 0f..duration.coerceAtLeast(1).toFloat(), enabled = ready)
        Text(if (ready) "${mediaTime(position)} / ${mediaTime(duration)}" else "Opening audio…",
            style = MaterialTheme.typography.bodySmall)
        Button(enabled = ready, onClick = {
            if (playing) player.pause() else { if (position >= duration) player.seekTo(0); player.start() }
            playing = !playing
        }, modifier = Modifier.fillMaxWidth()) { Text(if (playing) "Pause audio" else "Play audio") }
    }
}

private fun mediaTime(milliseconds: Int): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
