package com.grapsee.gsai.ui.chat.content

// ---------------------------------------------------------------------------
// Media widgets (0.79.0) — one `media` kind, discriminated by `type`, covering
// the list's media/content section: image, gallery, video, audio, code, file,
// link, citation, search results, entity card, product card, listing.
//
// Same contract as every other widget: strict JSON in, validated model out,
// malformed payload degrades to a code block upstream (parseWidget returns null).
// ---------------------------------------------------------------------------

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.theme.GsTheme
import kotlinx.serialization.Serializable

// --- models -----------------------------------------------------------------

@Serializable
data class MediaItem(
    val url: String = "",
    val thumb: String = "",
    val title: String = "",
    val subtitle: String = "",
    val caption: String = "",
    val meta: String = "",
    /** Tap sends this back into the chat (product/listing/entity "Ask" affordance). */
    val prompt: String = "",
    val domain: String = "",
    val snippet: String = "",
    val price: String = "",
    val badge: String = "",
    val rating: String = ""
)

@Serializable
data class MediaWidget(
    /** image | gallery | video | audio | code | file | link | citation | search | entity | product | listing */
    val type: String = "image",
    val title: String = "",
    val items: List<MediaItem> = emptyList(),
    /** code type: the source body. */
    val code: String = "",
    /** code type: fence language, shown as a tag. */
    val language: String = "",
    /** code type: optional caption under the block. */
    val text: String = ""
)

// --- helpers ----------------------------------------------------------------

@Composable
private fun RemoteThumb(url: String, modifier: Modifier, contentDesc: String? = null) {
    val c = GsTheme.colors
    val state by produceState<ImageState>(
        initialValue = imageCache.get(url)?.let { ImageState.Ready(it) } ?: ImageState.Loading,
        key1 = url
    ) {
        value = if (!url.startsWith("https://")) {
            ImageState.Failed
        } else {
            fetchBitmap(url)?.let { ImageState.Ready(it) } ?: ImageState.Failed
        }
    }
    Box(
        modifier.clip(RoundedCornerShape(10.dp)).background(c.raisedSurface),
        contentAlignment = Alignment.Center
    ) {
        when (val s = state) {
            is ImageState.Ready -> Image(
                bitmap = s.bitmap,
                contentDescription = contentDesc,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            is ImageState.Loading -> Text("…", color = c.textTertiary, style = MaterialTheme.typography.labelMedium)
            is ImageState.Failed -> Text("No image", color = c.textTertiary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun Monogram(text: String, size: Dp = 40.dp) {
    val c = GsTheme.colors
    val letter = text.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        Modifier.size(size).clip(CircleShape).background(c.accent.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Text(letter, color = c.accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

private fun openUrl(handler: UriHandler, url: String) {
    if (url.isNotBlank()) runCatching { handler.openUri(url) }
}

@Composable
private fun CopyButton(text: String, label: String = "Copy") {
    val clipboard = LocalClipboardManager.current
    TextButton(onClick = { clipboard.setText(AnnotatedString(text)) }) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

// --- entry ------------------------------------------------------------------

@Composable
fun MediaWidgetView(w: MediaWidget, onSendPrompt: ((String) -> Unit)? = null) {
    val c = GsTheme.colors
    WidgetCardShell(w.title, w.type) {
        when (w.type) {
            "image" -> SingleImage(w.items.firstOrNull())
            "gallery" -> Gallery(w.items)
            "video" -> VideoList(w.items)
            "audio" -> AudioList(w.items)
            "code" -> CodeCard(w)
            "file" -> FileList(w.items)
            "link" -> LinkList(w.items)
            "citation" -> CitationList(w.items)
            "search" -> SearchList(w.items, onSendPrompt)
            "entity" -> EntityList(w.items, onSendPrompt)
            "product" -> ProductList(w.items, onSendPrompt)
            "listing" -> ListingList(w.items, onSendPrompt)
            else -> Text(
                "Unsupported media type",
                style = MaterialTheme.typography.bodySmall,
                color = c.textTertiary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

// --- image ------------------------------------------------------------------

@Composable
private fun SingleImage(item: MediaItem?) {
    val c = GsTheme.colors
    if (item == null || item.url.isBlank()) {
        Text("No image", style = MaterialTheme.typography.bodySmall, color = c.textTertiary, modifier = Modifier.padding(top = 8.dp))
        return
    }
    Column(Modifier.padding(top = 8.dp)) {
        RemoteThumb(item.url, Modifier.fillMaxWidth().height(210.dp), item.caption.ifEmpty { item.title })
        val caption = item.caption.ifEmpty { item.title }
        if (caption.isNotBlank()) {
            Text(
                caption,
                style = MaterialTheme.typography.labelSmall,
                color = c.textSecondary,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

// --- gallery ----------------------------------------------------------------

@Composable
private fun Gallery(items: List<MediaItem>) {
    val c = GsTheme.colors
    val shown = items.filter { it.url.startsWith("https://") || it.thumb.startsWith("https://") }
    if (shown.isEmpty()) {
        Text("No images", style = MaterialTheme.typography.bodySmall, color = c.textTertiary, modifier = Modifier.padding(top = 8.dp))
        return
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        shown.take(20).forEach { item ->
            Column(Modifier.width(150.dp)) {
                RemoteThumb(
                    item.thumb.ifBlank { item.url },
                    Modifier.width(150.dp).height(105.dp),
                    item.caption.ifEmpty { item.title }
                )
                val caption = item.caption.ifEmpty { item.title }
                if (caption.isNotBlank()) {
                    Text(
                        caption,
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

// --- video / audio ----------------------------------------------------------

@Composable
private fun VideoList(items: List<MediaItem>) {
    val c = GsTheme.colors
    val uri = LocalUriHandler.current
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.take(10).forEach { item ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.raisedSurface)
                    .clickable { openUrl(uri, item.url) }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(contentAlignment = Alignment.Center) {
                    RemoteThumb(
                        item.thumb.ifBlank { item.url },
                        Modifier.width(120.dp).height(68.dp),
                        item.title
                    )
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("▶", color = c.onAccent, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(
                        item.title.ifBlank { "Video" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.meta.isNotBlank()) {
                        Text(item.meta, style = MaterialTheme.typography.labelSmall, color = c.textTertiary, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioList(items: List<MediaItem>) {
    val c = GsTheme.colors
    val uri = LocalUriHandler.current
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.take(10).forEach { item ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.raisedSurface)
                    .clickable { openUrl(uri, item.url) }.padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) { Text("▶", color = c.accent, style = MaterialTheme.typography.labelMedium) }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(
                        item.title.ifBlank { "Audio" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.meta.isNotBlank()) {
                        Text(item.meta, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                    }
                }
            }
        }
    }
}

// --- code -------------------------------------------------------------------

@Composable
private fun CodeCard(w: MediaWidget) {
    val c = GsTheme.colors
    if (w.code.isBlank()) {
        Text("No code", style = MaterialTheme.typography.bodySmall, color = c.textTertiary, modifier = Modifier.padding(top = 8.dp))
        return
    }
    Column(Modifier.padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                w.language.ifBlank { "code" },
                style = MaterialTheme.typography.labelSmall,
                color = c.textTertiary,
                fontFamily = FontFamily.Monospace
            )
            CopyButton(w.code)
        }
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(c.raisedSurface)
                .padding(10.dp)
        ) {
            Text(
                w.code.take(4000),
                style = MaterialTheme.typography.bodySmall,
                color = c.textPrimary,
                fontFamily = FontFamily.Monospace
            )
        }
        if (w.text.isNotBlank()) {
            Text(w.text, style = MaterialTheme.typography.labelSmall, color = c.textSecondary, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

// --- file -------------------------------------------------------------------

@Composable
private fun FileList(items: List<MediaItem>) {
    val c = GsTheme.colors
    val uri = LocalUriHandler.current
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.take(10).forEach { item ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.raisedSurface)
                    .clickable { openUrl(uri, item.url) }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val ext = item.title.substringAfterLast('.', "").uppercase().take(4).ifBlank { "FILE" }
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(c.accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) { Text(ext, style = MaterialTheme.typography.labelSmall, color = c.accent, fontWeight = FontWeight.Bold) }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(
                        item.title.ifBlank { "File" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val meta = listOf(item.meta, item.subtitle).filter { it.isNotBlank() }.joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Text(meta, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                    }
                }
                TextButton(onClick = { openUrl(uri, item.url) }) { Text("Open", style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

// --- link -------------------------------------------------------------------

@Composable
private fun LinkList(items: List<MediaItem>) {
    val c = GsTheme.colors
    val uri = LocalUriHandler.current
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.take(10).forEach { item ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.raisedSurface)
                    .clickable { openUrl(uri, item.url) }.padding(10.dp)
            ) {
                Text(
                    item.title.ifBlank { item.url },
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.link,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val domain = item.domain.ifBlank { item.url.substringAfter("://", "").substringBefore('/') }
                if (domain.isNotBlank()) {
                    Text(domain, style = MaterialTheme.typography.labelSmall, color = c.textTertiary, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

// --- citations --------------------------------------------------------------

@Composable
private fun CitationList(items: List<MediaItem>) {
    val c = GsTheme.colors
    val uri = LocalUriHandler.current
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.take(20).forEachIndexed { i, item ->
            Row(Modifier.fillMaxWidth().clickable { openUrl(uri, item.url) }) {
                Text(
                    "${i + 1}.",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.accent,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(22.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        item.title.ifBlank { item.url },
                        style = MaterialTheme.typography.labelMedium,
                        color = c.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val domain = item.domain.ifBlank { item.url.substringAfter("://", "").substringBefore('/') }
                    if (domain.isNotBlank()) {
                        Text(domain, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                    }
                }
            }
        }
    }
}

// --- search results ---------------------------------------------------------

@Composable
private fun SearchList(items: List<MediaItem>, onSendPrompt: ((String) -> Unit)?) {
    val c = GsTheme.colors
    val uri = LocalUriHandler.current
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.take(10).forEach { item ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.raisedSurface)
                    .clickable {
                        if (item.prompt.isNotBlank()) onSendPrompt?.invoke(item.prompt) else openUrl(uri, item.url)
                    }.padding(10.dp)
            ) {
                Text(
                    item.title.ifBlank { item.url },
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.link,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val domain = item.domain.ifBlank { item.url.substringAfter("://", "").substringBefore('/') }
                if (domain.isNotBlank()) {
                    Text(domain, style = MaterialTheme.typography.labelSmall, color = c.textTertiary, modifier = Modifier.padding(top = 1.dp))
                }
                if (item.snippet.isNotBlank()) {
                    Text(
                        item.snippet,
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

// --- entity card ------------------------------------------------------------

@Composable
private fun EntityList(items: List<MediaItem>, onSendPrompt: ((String) -> Unit)?) {
    val c = GsTheme.colors
    val uri = LocalUriHandler.current
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.take(10).forEach { item ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.raisedSurface)
                    .clickable { if (item.prompt.isNotBlank()) onSendPrompt?.invoke(item.prompt) else openUrl(uri, item.url) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Monogram(item.title.ifBlank { item.subtitle })
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(
                        item.title.ifBlank { "Entity" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textPrimary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val sub = listOf(item.subtitle, item.meta).filter { it.isNotBlank() }.joinToString(" · ")
                    if (sub.isNotBlank()) {
                        Text(sub, style = MaterialTheme.typography.labelSmall, color = c.textTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (item.prompt.isNotBlank()) {
                    TextButton(onClick = { onSendPrompt?.invoke(item.prompt) }) {
                        Text("Ask", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

// --- product card -----------------------------------------------------------

@Composable
private fun ProductList(items: List<MediaItem>, onSendPrompt: ((String) -> Unit)?) {
    val c = GsTheme.colors
    val uri = LocalUriHandler.current
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.take(12).forEach { item ->
            Column(
                Modifier.width(160.dp).clip(RoundedCornerShape(10.dp)).background(c.raisedSurface)
                    .clickable { if (item.prompt.isNotBlank()) onSendPrompt?.invoke(item.prompt) else openUrl(uri, item.url) }
                    .padding(8.dp)
            ) {
                RemoteThumb(item.thumb.ifBlank { item.url }, Modifier.width(144.dp).height(100.dp), item.title)
                if (item.badge.isNotBlank()) {
                    Text(item.badge, style = MaterialTheme.typography.labelSmall, color = c.accent, modifier = Modifier.padding(top = 4.dp))
                }
                Text(
                    item.title.ifBlank { "Product" },
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (item.price.isNotBlank()) {
                        Text(item.price, style = MaterialTheme.typography.labelMedium, color = c.textPrimary, fontWeight = FontWeight.SemiBold)
                    }
                    if (item.rating.isNotBlank()) {
                        Text(item.rating, style = MaterialTheme.typography.labelSmall, color = c.warning)
                    }
                }
            }
        }
    }
}

// --- business listing -------------------------------------------------------

@Composable
private fun ListingList(items: List<MediaItem>, onSendPrompt: ((String) -> Unit)?) {
    val c = GsTheme.colors
    val uri = LocalUriHandler.current
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.take(10).forEach { item ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.raisedSurface)
                    .clickable { if (item.prompt.isNotBlank()) onSendPrompt?.invoke(item.prompt) else openUrl(uri, item.url) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RemoteThumb(item.thumb.ifBlank { item.url }, Modifier.size(56.dp), item.title)
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(
                        item.title.ifBlank { "Listing" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textPrimary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.meta.isNotBlank()) {
                        Text(item.meta, style = MaterialTheme.typography.labelSmall, color = c.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (item.rating.isNotBlank()) {
                            Text(item.rating, style = MaterialTheme.typography.labelSmall, color = c.warning)
                        }
                        if (item.price.isNotBlank()) {
                            Text(item.price, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                        }
                    }
                }
            }
        }
    }
}
