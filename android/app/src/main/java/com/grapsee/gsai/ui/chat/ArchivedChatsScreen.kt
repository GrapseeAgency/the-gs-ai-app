package com.grapsee.gsai.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.local.ConversationEntity
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.components.ConversationActionsSheet
import com.grapsee.gsai.ui.components.GsEmptyState
import com.grapsee.gsai.ui.components.GsListItem
import com.grapsee.gsai.ui.components.GsScreenScaffold
import com.grapsee.gsai.ui.components.GsSectionHeader
import com.grapsee.gsai.ui.theme.GsMotion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Archive shelf — live from the Room cache (every archived chat, not a demo
 * subset) with restore + delete affordances per row.
 */
@Composable
fun ArchivedChatsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val archivedFlow = remember {
        runCatching { ServiceLocator.chat.archivedConversations() }.getOrElse { flowOf(emptyList()) }
    }
    val archived by archivedFlow.collectAsState(initial = emptyList())
    var actionTarget by remember { mutableStateOf<ConversationEntity?>(null) }

    LaunchedEffect(Unit) {
        runCatching { ServiceLocator.chat.refreshConversations() }
    }

    GsScreenScaffold(title = "Archived", onBack = onBack) {
        if (archived.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
            ) {
                GsSectionHeader("Recently archived")
                GsEmptyState(
                    icon = Icons.Outlined.Archive,
                    title = "Nothing archived yet",
                    message = "Chats you archive from the drawer or list will rest here until you bring them back."
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                GsSectionHeader("Recently archived")
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(archived, key = { it.id }) { entity ->
                        GsListItem(
                            title = entity.title,
                            subtitle = "Archived · ${ChatTime.relative(entity.updatedAt)}",
                            leading = {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Outlined.Archive,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                runCatching { ServiceLocator.chat.setArchived(entity.id, false) }
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Unarchive,
                                            contentDescription = "Unarchive",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { actionTarget = entity },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = "Delete permanently",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            onClick = {}
                        )
                    }
                }
            }
        }
    }

    val target = actionTarget
    if (target != null) {
        ConversationActionsSheet(
            title = target.title,
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
