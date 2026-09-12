package com.grapsee.gsai.ui.attachment

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Refresh
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
    // §6: row height caps at ≈2 chip heights; horizontal scroll absorbs the rest.
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 120.dp),
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
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.padding(end = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 6.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChipThumbnail(draft)
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    text = draft.displayName.ifEmpty { "attachment" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp)
                )
                Text(
                    text = when (draft.phase) {
                        AttachmentPhase.Failed -> failureLabel(draft.failure, draft.serverCode)
                        AttachmentPhase.Ready -> AttachmentRules.humanSize(draft.byteSize)
                        // Size is unknown until the staged copy exists — no invented bytes.
                        else -> "…"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (draft.phase == AttachmentPhase.Failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (editable) {
                if (draft.phase == AttachmentPhase.Failed) {
                    ChipButton(
                        icon = Icons.Outlined.Refresh,
                        label = "Retry ${draft.displayName}",
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = onRetry
                    )
                }
                ChipButton(
                    icon = Icons.Outlined.Close,
                    label = "Remove ${draft.displayName}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onRemove
                )
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
private fun ChipThumbnail(draft: AttachmentDraft) {
    val boxShape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(boxShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        if (draft.phase == AttachmentPhase.Failed) {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
        } else {
            val stagedFile = draft.localPath?.let(::File)
            val bitmap = rememberStagedThumbnail(
                if (draft.kind == AttachmentKind.Image) stagedFile else null
            )
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
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
    AttachmentKind.Image -> Icons.Outlined.Image
    AttachmentKind.Pdf -> Icons.Outlined.PictureAsPdf
    AttachmentKind.Document -> Icons.Outlined.InsertDriveFile
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
