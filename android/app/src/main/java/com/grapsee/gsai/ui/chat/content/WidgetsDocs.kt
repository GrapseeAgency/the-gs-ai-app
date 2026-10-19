package com.grapsee.gsai.ui.chat.content

// ---------------------------------------------------------------------------
// Document previews (0.80.0) — the `media` type=doc path.
//
// A file card that only opens the browser is not a preview. This renders the
// document in-chat:
//   * PDF   — downloaded (capped) and rasterized page by page with PdfRenderer.
//   * image — decoded through the shared bitmap cache.
//   * text  — fetched and shown in a bounded scroll area.
// Anything else falls back to an honest open-externally card with the reason.
// ---------------------------------------------------------------------------

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.theme.GsTheme
import com.grapsee.gsai.ui.theme.skeletonSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val MAX_DOC_BYTES = 15L * 1024 * 1024
private const val MAX_TEXT_CHARS = 60_000

private enum class DocKind { PDF, IMAGE, TEXT, OTHER }

private fun docKind(url: String, mime: String): DocKind {
    val path = url.substringBefore('?').substringBefore('#').lowercase()
    val m = mime.lowercase()
    return when {
        m.contains("pdf") || path.endsWith(".pdf") -> DocKind.PDF
        m.startsWith("image/") || listOf(".png", ".jpg", ".jpeg", ".webp", ".gif").any(path::endsWith) -> DocKind.IMAGE
        m.startsWith("text/") || listOf(".txt", ".md", ".csv", ".json", ".log").any(path::endsWith) -> DocKind.TEXT
        else -> DocKind.OTHER
    }
}

@Composable
fun DocumentPreviewView(url: String, title: String, meta: String = "", mime: String = "") {
    if (!url.startsWith("https://")) {
        DocOpenCard(url, title.ifBlank { "Document" }, "Preview needs an https link", meta)
        return
    }
    when (docKind(url, mime)) {
        DocKind.PDF -> PdfDocumentPreview(url, title.ifBlank { "Document" })
        DocKind.IMAGE -> ImageDocumentPreview(url, title)
        DocKind.TEXT -> TextDocumentPreview(url, title.ifBlank { "Document" })
        DocKind.OTHER -> DocOpenCard(url, title.ifBlank { "Document" }, "No inline preview for this format", meta)
    }
}

// --- PDF --------------------------------------------------------------------

private sealed interface PdfState {
    data object Loading : PdfState
    data class Ready(val file: File, val pages: Int) : PdfState
    data class Failed(val reason: String) : PdfState
}

@Composable
private fun PdfDocumentPreview(url: String, title: String) {
    val context = LocalContext.current
    val state by produceState<PdfState>(PdfState.Loading, url) {
        value = withContext(Dispatchers.IO) { loadPdf(context, url) }
    }
    when (val s = state) {
        PdfState.Loading -> DocSkeleton()
        is PdfState.Failed -> DocOpenCard(url, title, s.reason, "")
        is PdfState.Ready -> PdfPages(s.file, s.pages, title)
    }
}

private fun loadPdf(context: Context, url: String): PdfState {
    return try {
        val dir = File(context.cacheDir, "gsdocs").apply { mkdirs() }
        val file = File(dir, url.hashCode().toString() + ".pdf")
        if (file.length() > 1024) {
            val pages = countPdfPages(file)
            if (pages > 0) return PdfState.Ready(file, pages)
            file.delete()
        }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 25_000
            instanceFollowRedirects = true
        }
        connection.inputStream.use { input ->
            file.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > MAX_DOC_BYTES) {
                        file.delete()
                        return PdfState.Failed("Document is larger than 15 MB")
                    }
                    out.write(buffer, 0, read)
                }
            }
        }
        val pages = countPdfPages(file)
        if (pages <= 0) {
            file.delete()
            PdfState.Failed("This PDF could not be opened")
        } else {
            PdfState.Ready(file, pages)
        }
    } catch (t: Throwable) {
        PdfState.Failed(t.message ?: "Download failed")
    }
}

private fun countPdfPages(file: File): Int = try {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { it.pageCount }
    }
} catch (_: Throwable) {
    0
}

private fun renderPdfPage(file: File, index: Int): ImageBitmap? = try {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { renderer ->
            val i = index.coerceIn(0, renderer.pageCount - 1)
            renderer.openPage(i).use { page ->
                val width = 900
                val height = (width.toFloat() * page.height / page.width).toInt().coerceIn(200, 4000)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap.asImageBitmap()
            }
        }
    }
} catch (_: Throwable) {
    null
}

@Composable
private fun PdfPages(file: File, pages: Int, title: String) {
    val c = GsTheme.colors
    var page by remember(file) { mutableIntStateOf(0) }
    val bitmap by produceState<ImageBitmap?>(null, file, page) {
        value = withContext(Dispatchers.IO) { renderPdfPage(file, page) }
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                "PDF · ${page + 1}/$pages",
                style = MaterialTheme.typography.labelSmall,
                color = c.textTertiary
            )
        }
        val bmp = bitmap
        if (bmp == null) {
            Box(
                Modifier.fillMaxWidth().height(220.dp)
                    .skeletonSurface(RoundedCornerShape(10.dp), c.raisedSurface)
            )
        } else {
            Image(
                bitmap = bmp,
                contentDescription = "$title page ${page + 1}",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(androidx.compose.ui.graphics.Color.White)
            )
        }
        if (pages > 1) {
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PagerButton("Prev", enabled = page > 0) { page-- }
                Text(
                    "${page + 1} / $pages",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textTertiary,
                    modifier = Modifier.weight(1f)
                )
                PagerButton("Next", enabled = page < pages - 1) { page++ }
            }
        }
    }
}

@Composable
private fun PagerButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = GsTheme.colors
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) c.raisedSurface else c.raisedSurface.copy(alpha = 0.4f))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) c.textPrimary else c.textTertiary
        )
    }
}

// --- image ------------------------------------------------------------------

@Composable
private fun ImageDocumentPreview(url: String, title: String) {
    val c = GsTheme.colors
    val state by produceState<ImageState>(ImageState.Loading, url) {
        value = fetchBitmap(url)?.let { ImageState.Ready(it) } ?: ImageState.Failed
    }
    Column(Modifier.fillMaxWidth()) {
        if (title.isNotBlank()) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        when (val s = state) {
            is ImageState.Ready -> Image(
                bitmap = s.bitmap,
                contentDescription = title,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            )
            ImageState.Failed -> DocOpenCard(url, title.ifBlank { "Image" }, "Preview unavailable", "")
            ImageState.Loading -> Box(
                Modifier.fillMaxWidth().height(180.dp)
                    .skeletonSurface(RoundedCornerShape(10.dp), c.raisedSurface)
            )
        }
    }
}

// --- text -------------------------------------------------------------------

@Composable
private fun TextDocumentPreview(url: String, title: String) {
    val c = GsTheme.colors
    val state by produceState<String?>(null, url) {
        value = withContext(Dispatchers.IO) { fetchText(url) }
    }
    val body = state
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = c.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        if (body == null) {
            Box(
                Modifier.fillMaxWidth().height(140.dp)
                    .skeletonSurface(RoundedCornerShape(10.dp), c.raisedSurface)
            )
        } else {
            Box(
                Modifier.fillMaxWidth().height(240.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.raisedSurface)
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp)
            ) {
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textPrimary,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

private fun fetchText(url: String): String? = try {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 15_000
        instanceFollowRedirects = true
    }
    connection.inputStream.use { input ->
        val buffer = ByteArray(32 * 1024)
        val builder = StringBuilder()
        while (builder.length < MAX_TEXT_CHARS) {
            val read = input.read(buffer)
            if (read <= 0) break
            builder.append(String(buffer, 0, read, Charsets.UTF_8))
        }
        builder.take(MAX_TEXT_CHARS).toString().ifBlank { "(empty document)" }
    }
} catch (_: Throwable) {
    null
}

// --- fallback ---------------------------------------------------------------

@Composable
private fun DocOpenCard(url: String, title: String, reason: String, meta: String) {
    val c = GsTheme.colors
    val uri = LocalUriHandler.current
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(c.raisedSurface)
            .clickable { if (url.isNotBlank()) runCatching { uri.openUri(url) } }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(c.accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                title.substringAfterLast('.', "DOC").uppercase().take(4),
                style = MaterialTheme.typography.labelSmall,
                color = c.accent
            )
        }
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOf(reason, meta).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = c.textTertiary
            )
        }
        Text("Open", style = MaterialTheme.typography.labelMedium, color = c.accent)
    }
}

@Composable
private fun DocSkeleton() {
    val c = GsTheme.colors
    Box(
        Modifier.fillMaxWidth().height(200.dp)
            .skeletonSurface(RoundedCornerShape(10.dp), c.raisedSurface)
    )
}
