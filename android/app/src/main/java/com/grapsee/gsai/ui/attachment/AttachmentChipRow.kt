package com.grapsee.gsai.ui.attachment

import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.attachment.AttachmentDraft
import com.grapsee.gsai.data.attachment.AttachmentFailure
import com.grapsee.gsai.data.attachment.AttachmentKind
import com.grapsee.gsai.data.attachment.AttachmentPhase
import com.grapsee.gsai.data.attachment.AttachmentRules
import java.io.File
import com.grapsee.gsai.ui.icons.GsIcons

/**
 * PHASE 5 composer/transcript chips (docs/ATTACHMENTS.md §6/§7).
 *
 * One horizontal row of attachment chips living ABOVE the text field inside
 * the composer (and, read-only, on user bubbles in the transcript). Every
 * visible state is REAL: preparing/uploading show an indeterminate spinner
 * (no fabricated percent exists), failed shows the honest reason + Retry +
 * Remove, ready shows the staged thumbnail (images) or the monochrome kind
 * icon (pdf/document). Theme colors only — thumbnails are user content and
 * MAY be color; all chrome stays monochrome.
 */
@Composable
fun AttachmentChipRow(
    drafts: List<AttachmentDraft>,
    modifier: Modifier = Modifier,
    editable: Boolean = true,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    onRemove: (String) -> Unit = {},
    onRetry: (String) -> Unit = {}
) {
    if (drafts.isEmpty()) return
    // Bounded media cards; horizontal scroll keeps multiple previews out of the text field.
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = if (editable) 312.dp else 336.dp),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(drafts, key = { it.id }) { draft ->
            AttachmentChip(
                draft = draft,
                editable = editable,
                onRemove = { onRemove(draft.id) },
                onRetry = { onRetry(draft.id) }
            )
        }
    }
}

@Composable
private fun AttachmentChip(
    draft: AttachmentDraft,
    editable: Boolean,
    onRemove: () -> Unit,
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    var localFile by remember(draft.id, draft.localPath) {
        mutableStateOf(draft.localPath?.let(::File)?.takeIf { it.isFile })
    }
    var previewFailed by remember(draft.id) { mutableStateOf(false) }
    var attempt by remember(draft.id) { mutableStateOf(0) }
    var showingMedia by remember(draft.id) { mutableStateOf(false) }
    if (showingMedia && localFile != null) {
        if (draft.kind == AttachmentKind.Image) ImagePreview(localFile!!, draft.displayName) { showingMedia = false }
        else MediaPreview(localFile!!, draft.kind == AttachmentKind.Video) { showingMedia = false }
    }
    LaunchedEffect(draft.id, draft.remoteId, draft.localPath, attempt) {
        if (localFile == null && draft.remoteId != null) {
            previewFailed = false
            try { localFile = attachmentPreviewFile(context, draft) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { previewFailed = true }
        }
    }
    fun openPreview() {
        val file = localFile
        if (file == null) { attempt++; return }
        if (draft.kind == AttachmentKind.Audio || draft.kind == AttachmentKind.Video || draft.kind == AttachmentKind.Image) {
            showingMedia = true
            return
        }
        try {
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, draft.mimeType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (_: Exception) {
            Toast.makeText(context, "No viewer is available for this file type", Toast.LENGTH_SHORT).show()
        }
    }
    val visual = draft.kind != AttachmentKind.Audio
    val edge = if (editable) 160 else 224
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = .72f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)),
        modifier = Modifier.padding(end = 10.dp).width(if (visual) (edge + 16).dp else 256.dp).animateContentSize()
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (visual) {
                Box(Modifier.fillMaxWidth()) {
                    Box(Modifier.clickable(onClickLabel = "Preview ${draft.displayName}", onClick = ::openPreview)) {
                        ChipThumbnail(draft, localFile, edge)
                    }
                    if (draft.kind == AttachmentKind.Video) {
                        Icon(GsIcons.CirclePlay, contentDescription = "Video", tint = Color.White,
                            modifier = Modifier.align(Alignment.Center).size(36.dp))
                    }
                    if (editable) {
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface.copy(alpha = .85f),
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                            ChipButton(GsIcons.X, "Remove ${draft.displayName}", MaterialTheme.colorScheme.onSurface, onRemove)
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!visual) ChipThumbnail(draft, localFile, 56)
                Column(Modifier.weight(1f)) {
                    Text(draft.displayName.ifEmpty { "Attachment" }, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(when (draft.phase) {
                        AttachmentPhase.Failed -> failureLabel(draft.failure, draft.serverCode)
                        AttachmentPhase.Selected, AttachmentPhase.Preparing -> "Preparing…"
                        AttachmentPhase.Uploading -> "Uploading…"
                        AttachmentPhase.Ready -> if (previewFailed) "Preview unavailable · Retry" else AttachmentRules.humanSize(draft.byteSize)
                    }, style = MaterialTheme.typography.labelSmall, maxLines = 2,
                        color = if (draft.phase == AttachmentPhase.Failed || previewFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if ((localFile != null || previewFailed) && (editable || draft.kind != AttachmentKind.Audio)) {
                    ChipButton(if (draft.kind == AttachmentKind.Audio || draft.kind == AttachmentKind.Video) GsIcons.CirclePlay else GsIcons.ExternalLink,
                        if (previewFailed) "Retry preview ${draft.displayName}" else "Open ${draft.displayName}",
                        MaterialTheme.colorScheme.primary, ::openPreview)
                }
            }
            if (!editable && draft.kind == AttachmentKind.Audio && localFile != null) {
                InlineAudioPreview(localFile!!)
            }
            if (editable && (!visual || draft.phase == AttachmentPhase.Failed)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (draft.phase == AttachmentPhase.Failed) ChipButton(GsIcons.RefreshCw, "Retry ${draft.displayName}", MaterialTheme.colorScheme.primary, onRetry)
                    if (!visual) ChipButton(GsIcons.X, "Remove ${draft.displayName}", MaterialTheme.colorScheme.onSurfaceVariant, onRemove)
                }
            }
        }
    }
}

/**
 * 48dp thumbnail square: real decoded thumb for image drafts with a staged
 * file (user content — may be color), monochrome outline icon + extension
 * label for pdf/document, error icon when failed, indeterminate spinner
 * overlay while preparing/uploading (never a fabricated percent).
 */
@Composable
private fun ChipThumbnail(draft: AttachmentDraft, localFile: File?, edge: Int) {
    val boxShape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .size(edge.dp)
            .clip(boxShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        if (draft.phase == AttachmentPhase.Failed) {
            Icon(
                GsIcons.CircleAlert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
        } else {
            val bitmap = rememberStagedThumbnail(if (draft.kind != AttachmentKind.Audio) localFile else null)
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = if (draft.kind == AttachmentKind.Pdf || draft.kind == AttachmentKind.Document) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = kindIcon(draft.kind),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    val extension = draft.displayName.substringAfterLast('.', "")
                    if (extension.isNotEmpty()) {
                        Text(
                            text = extension.take(4).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        if (draft.phase == AttachmentPhase.Preparing || draft.phase == AttachmentPhase.Uploading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** 44dp-minimum chip action target (§6 remove/retry sizing). */
@Composable
private fun ChipButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Icon(
            icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

private fun kindIcon(kind: AttachmentKind): ImageVector = when (kind) {
    AttachmentKind.Image -> GsIcons.Image
    AttachmentKind.Pdf -> GsIcons.FileText
    AttachmentKind.Document -> GsIcons.File
    AttachmentKind.Audio -> GsIcons.FileMusic
    AttachmentKind.Video -> GsIcons.FileVideo
}

/** Honest, human failure copy — taxonomy mapped 1:1, codes shown verbatim. */
internal fun failureLabel(failure: AttachmentFailure?, code: Int?): String = when (failure) {
    AttachmentFailure.TooLarge -> "Too large"
    AttachmentFailure.Unsupported -> "Unsupported type"
    AttachmentFailure.ReadFailed -> "Couldn't read"
    AttachmentFailure.Network -> "Network error"
    AttachmentFailure.RateLimited -> "Slow down a little"
    AttachmentFailure.Server -> code?.let { "Server error ($it)" } ?: "Server error"
    AttachmentFailure.ConversationMissing -> "Conversation missing"
    null -> "Failed"
}
