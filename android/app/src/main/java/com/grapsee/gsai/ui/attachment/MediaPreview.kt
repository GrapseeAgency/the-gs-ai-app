package com.grapsee.gsai.ui.attachment

import androidx.compose.ui.graphics.asImageBitmap
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
import com.grapsee.gsai.ui.icons.GsIcons

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
internal fun InlineAudioPreview(file: File) {
    NativeAudioPreview(file, compact = true)
}

@Composable
private fun NativeAudioPreview(file: File, compact: Boolean = false) {
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
    } else if (compact) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(
                enabled = ready,
                onClick = {
                    if (playing) player.pause()
                    else { if (position >= duration) player.seekTo(0); player.start() }
                    playing = !playing
                },
                modifier = Modifier.size(40.dp)
            ) {
                androidx.compose.material3.Icon(
                    imageVector = if (playing) GsIcons.Pause else GsIcons.Play,
                    contentDescription = if (playing) "Pause audio" else "Play audio"
                )
            }
            Slider(
                modifier = Modifier.weight(1f),
                value = position.toFloat().coerceIn(0f, duration.coerceAtLeast(1).toFloat()),
                onValueChange = { position = it.toInt(); player.seekTo(position) },
                valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                enabled = ready
            )
            Text(
                text = if (ready) "${mediaTime(position)} / ${mediaTime(duration)}" else "…",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
        if (!ready) LinearProgressIndicator(Modifier.fillMaxWidth())
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

/** A bounded, off-main-thread photo viewer; never launches an external app. */
@Composable
internal fun ImagePreview(file: File, title: String, onDismiss: () -> Unit) {
    var bitmap by remember(file) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var loaded by remember(file) { mutableStateOf(false) }
    LaunchedEffect(file) {
        bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(file.absolutePath, bounds)
                var sample = 1
                while (bounds.outWidth / sample > 1536 || bounds.outHeight / sample > 1536) sample *= 2
                android.graphics.BitmapFactory.decodeFile(file.absolutePath,
                    android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull()
        }
        loaded = true
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 4.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                val image = bitmap
                when {
                    image != null -> androidx.compose.foundation.Image(
                        bitmap = image.asImageBitmap(), contentDescription = title,
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 440.dp))
                    !loaded -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    else -> Text("This device could not decode the image.", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close preview") }
            }
        }
    }
}
