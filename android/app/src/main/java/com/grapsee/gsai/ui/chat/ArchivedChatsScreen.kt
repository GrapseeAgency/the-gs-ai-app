package com.grapsee.gsai.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.local.ConversationEntity
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.components.ConversationActionsSheet
import com.grapsee.gsai.ui.components.RefArt
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefDivider
import com.grapsee.gsai.ui.components.RefEmptyState
import com.grapsee.gsai.ui.components.RefIconTile
import com.grapsee.gsai.ui.components.RefPage
import com.grapsee.gsai.ui.components.gsConversationTitle
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Archive shelf — real archived Room rows, each with restore and delete.
 * Tapping the row opens the conversation; the old version's row looked
 * tappable and did nothing.
 */
@Composable
fun ArchivedChatsScreen(
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val archivedFlow = remember {
        runCatching { ServiceLocator.chat.archivedConversations() }.getOrElse { flowOf(emptyList()) }
    }
    val archived by archivedFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var actionTarget by remember { mutableStateOf<ConversationEntity?>(null) }

    LaunchedEffect(Unit) {
        runCatching { ServiceLocator.chat.refreshConversations() }
    }

    RefPage(
        title = "Archive",
        subtitle = "Out of the way, never deleted.",
        onBack = onBack
    ) {
        if (archived.isEmpty()) {
            RefEmptyState(
                art = RefArt.Archive,
                title = "Nothing archived yet",
                message = "Chats you archive from the drawer or list rest here until you bring them back."
            )
        } else {
            Text(
                text = "${archived.size} archived conversation${if (archived.size == 1) "" else "s"}",
                style = MaterialTheme.typography.labelMedium,
                color = GsTheme.colors.textTertiary,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp)
            )
            RefCard {
                archived.forEachIndexed { index, entity ->
                    if (index > 0) RefDivider()
                    ArchivedRow(
                        entity = entity,
                        onOpen = { onOpenConversation(entity.id) },
                        onRestore = {
                            scope.launch {
                                runCatching { ServiceLocator.chat.setArchived(entity.id, false) }
                            }
                        },
                        onMore = { actionTarget = entity }
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Restored chats return to Chats immediately. Delete removes the transcript and its messages from this device.",
                style = MaterialTheme.typography.labelSmall,
                color = GsTheme.colors.textTertiary,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
    }

    val target = actionTarget
    if (target != null) {
        ConversationActionsSheet(
            title = gsConversationTitle(target.title),
            pinned = target.pinned,
            archiveLabel = "Unarchive",
            onDismiss = { actionTarget = null },
            onTogglePin = {
                actionTarget = null
                scope.launch { runCatching { ServiceLocator.chat.setPinned(target.id, !target.pinned) } }
            },
            onArchive = {
                actionTarget = null
                scope.launch { runCatching { ServiceLocator.chat.setArchived(target.id, false) } }
            },
            onDelete = {
                actionTarget = null
                scope.launch { runCatching { ServiceLocator.chat.delete(target.id) } }
            },
            onRename = { name ->
                actionTarget = null
                scope.launch { runCatching { ServiceLocator.chat.rename(target.id, name) } }
            }
        )
    }
}

@Composable
private fun ArchivedRow(
    entity: ConversationEntity,
    onOpen: () -> Unit,
    onRestore: () -> Unit,
    onMore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = gsConversationTitle(entity.title),
                onClick = onOpen
            )
            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RefIconTile(GsIcons.Archive, tint = GsTheme.colors.textSecondary)
        Column(Modifier.weight(1f)) {
            Text(
                gsConversationTitle(entity.title),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = GsTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "Archived · ${ChatTime.relative(entity.updatedAt)}",
                style = MaterialTheme.typography.labelMedium,
                color = GsTheme.colors.textSecondary
            )
        }
        IconButton(onClick = onRestore, modifier = Modifier.size(36.dp)) {
            Icon(
                GsIcons.ArchiveRestore,
                contentDescription = "Unarchive",
                tint = GsTheme.colors.accentStrong,
                modifier = Modifier.size(18.dp)
            )
        }
        IconButton(onClick = onMore, modifier = Modifier.size(36.dp)) {
            Icon(
                GsIcons.EllipsisVertical,
                contentDescription = "More actions",
                tint = GsTheme.colors.textTertiary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** Shared relative-time formatter for chat rows (kept tiny, no external deps). */
object ChatTime {
    fun relative(iso: String): String = runCatching {
        val timestamp = java.time.OffsetDateTime.parse(iso)
        val elapsed = java.time.Duration.between(timestamp, java.time.OffsetDateTime.now())
        when {
            elapsed.toMinutes() < 1 -> "just now"
            elapsed.toHours() < 1 -> "${elapsed.toMinutes()}m ago"
            elapsed.toHours() < 24 -> "${elapsed.toHours()}h ago"
            elapsed.toDays() < 7 -> "${elapsed.toDays()}d ago"
            else -> timestamp.toLocalDate().toString()
        }
    }.getOrElse { "earlier" }
}
