package com.grapsee.gsai.ui.attachment

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.os.Build
import android.media.MediaMetadataRetriever
import android.content.Context
import com.grapsee.gsai.BuildConfig
import com.grapsee.gsai.data.attachment.AttachmentDraft
import com.grapsee.gsai.data.attachment.AttachmentRules
import com.grapsee.gsai.di.ServiceLocator
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
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
    private const val TARGET_PX = 256

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
                val ext = file.extension.lowercase()
                val preview = when {
                    ext == "pdf" -> ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                        PdfRenderer(fd).use { pdf ->
                            if (pdf.pageCount == 0) null else pdf.openPage(0).use { page ->
                                val height = (TARGET_PX.toFloat() * page.height / page.width).toInt().coerceIn(1, 512)
                                Bitmap.createBitmap(TARGET_PX, height, Bitmap.Config.ARGB_8888).also {
                                    it.eraseColor(Color.WHITE)
                                    page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                }
                            }
                        }
                    }
                    ext in setOf("mp4", "m4v", "mov", "webm") && Build.VERSION.SDK_INT >= 27 -> {
                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(file.absolutePath)
                            retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, TARGET_PX, TARGET_PX)
                        } finally { retriever.release() }
                    }
                    ext in setOf("txt", "text", "md", "markdown", "csv") -> {
                        val chars = CharArray(1200)
                        val count = file.bufferedReader().use { it.read(chars) }.coerceAtLeast(0)
                        val text = String(chars, 0, count)
                        Bitmap.createBitmap(256, 320, Bitmap.Config.ARGB_8888).also { bitmap ->
                            val canvas = Canvas(bitmap)
                            canvas.drawColor(Color.WHITE)
                            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 13f }
                            text.lines().flatMap { it.chunked(30) }.take(17).forEachIndexed { i, line ->
                                canvas.drawText(line, 14f, 25f + i * 17f, paint)
                            }
                        }
                    }
                    else -> null
                }
                if (preview != null) {
                    cache.put(file.absolutePath, preview)
                    return@runCatching preview
                }
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

/** Fetch missing history previews through the normal session-bearing client.
 * IDs, not untrusted remote URLs, select an app-owned endpoint. Cache lives
 * under attachments/, so the existing privacy wipe removes it too.
 */
internal suspend fun attachmentPreviewFile(context: Context, draft: AttachmentDraft): File = withContext(Dispatchers.IO) {
    draft.localPath?.let(::File)?.takeIf { it.isFile && it.length() > 0 }?.let { return@withContext it }
    val id = draft.remoteId ?: error("Attachment is not uploaded")
    require(id.matches(Regex("[A-Za-z0-9_-]+")))
    val directory = File(context.filesDir, "attachments/preview-$id").apply { mkdirs() }
    val file = File(directory, AttachmentRules.sanitizeDisplayName(draft.displayName))
    if (file.isFile && file.length() in 1..AttachmentRules.MAX_BYTES) return@withContext file
    ServiceLocator.http.prepareGet("${BuildConfig.BASE_URL.trimEnd('/')}/api/v1/files/$id").execute { response ->
        check(response.status.value == 200) { "Preview unavailable (${response.status.value})" }
        val bytes = response.bodyAsChannel().readRemaining(AttachmentRules.MAX_BYTES + 1).readBytes()
        check(bytes.isNotEmpty() && bytes.size <= AttachmentRules.MAX_BYTES) { "Preview exceeds file limit" }
        val temp = File.createTempFile("preview-", ".part", directory)
        try {
            temp.writeBytes(bytes)
            check(temp.renameTo(file) || file.isFile) { "Could not cache preview" }
        } finally { temp.delete() }
    }
    file
}
