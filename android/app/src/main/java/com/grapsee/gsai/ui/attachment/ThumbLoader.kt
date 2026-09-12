package com.grapsee.gsai.ui.attachment

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PHASE 5 thumbnail pipeline (docs/ATTACHMENTS.md §8): scaled decodes of the
 * STAGED local file only (~128 px target), background-dispatched, one small
 * in-memory LRU for the whole app. There is deliberately NO network decode —
 * history entries without a staged file render the monochrome kind icon.
 *
 * Guarantees: never a full-size decode, never a main-thread decode, never a
 * full-size allocation; the compose helper cancels per chip (a removed chip's
 * in-flight decode publishes nothing).
 */
object ThumbLoader {

    /** Decode target edge in pixels (§8: ≈128 px). */
    private const val TARGET_PX = 128

    /** 24 MB byte-sized LRU — allocationByteCount keeps big bitmaps honest. */
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    fun cached(file: File?): Bitmap? {
        file ?: return null
        return cache.get(file.absolutePath)
    }

    /**
     * Scaled decode of the staged file with the classic inSampleSize walk.
     * Returns null (honestly) for unreadable/non-image files.
     */
    suspend fun decode(file: File?): Bitmap? {
        file ?: return null
        if (!file.exists() || file.length() == 0L) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= TARGET_PX &&
                    bounds.outHeight / (sample * 2) >= TARGET_PX
                ) {
                    sample *= 2
                }
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return@runCatching null
                cache.put(file.absolutePath, bitmap)
                bitmap
            }.getOrNull()
        }
    }
}

/**
 * Compose binding: decodes the staged file's thumbnail once per file, chip-
 * scoped. Cancellation is automatic — the LaunchedEffect is keyed on the file,
 * so a removed/replaced chip cancels its decode and never publishes. The LRU
 * hit path skips the worker entirely.
 */
@Composable
fun rememberStagedThumbnail(file: File?): ImageBitmap? {
    var bitmap by remember(file) { mutableStateOf(ThumbLoader.cached(file)) }
    LaunchedEffect(file) {
        if (file != null && bitmap == null) {
            bitmap = ThumbLoader.decode(file)
        }
    }
    return bitmap?.asImageBitmap()
}
