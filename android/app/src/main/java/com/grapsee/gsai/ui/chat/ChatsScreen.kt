package com.grapsee.gsai.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.local.ConversationEntity
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.components.ConversationActionsSheet
import com.grapsee.gsai.ui.components.GsCard
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.components.GsEmptyState
import com.grapsee.gsai.ui.components.GsListItem
import com.grapsee.gsai.ui.components.GsOfflineBanner
import com.grapsee.gsai.ui.components.GsScreenScaffold
import com.grapsee.gsai.ui.components.GsSkeleton
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.GsMotion
import java.time.Duration
import java.time.OffsetDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Chats hub — Room conversation list as the source of truth with the
 * backend sync engine behind it. Falls back to tasteful sample rows while
 * the backend is unreachable so the surface is never dead. Rows carry the
 * benchmark action set (pin / archive / delete) via the overflow menu.
 */
@Composable
fun ChatsScreen(onNavigate: (String) -> Unit) {
    val conversationList = remember { conversationsFlow() }
    val conversations by conversationList.collectAsState(initial = emptyList())
    val offline by remember { connectionStateFlow() }.collectAsState()
    val scope = rememberCoroutineScope()

    var firstLoadDone by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(FILTER_ALL) }
    var actionTarget by remember { mutableStateOf<ConversationEntity?>(null) }

    LaunchedEffect(Unit) {
        runCatching { ServiceLocator.chat.refreshConversations() }
        firstLoadDone = true
    }

    val display = conversations.ifEmpty { if (firstLoadDone) sampleConversations() else emptyList() }
    val rows = when (filter) {
        FILTER_PINNED -> display.filter { it.pinned }
        FILTER_UNREAD -> emptyList()
        else -> display
    }
    val loading = !firstLoadDone && conversations.isEmpty()

    GsScreenScaffold(
        title = "Chats",
        actions = {
            IconButton(onClick = { onNavigate(GsRoutes.CHAT_SEARCH) }) {
                Icon(Icons.Outlined.Search, contentDescription = "Search chats",
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            IconButton(onClick = { onNavigate(GsRoutes.chat(null)) }) {
                Icon(Icons.Outlined.Add, contentDescription = "New chat",
                    tint = MaterialTheme.colorScheme.onBackground)
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceM)
        ) {
            GsOfflineBanner(visible = offline)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickLink("Folders", Icons.Outlined.Folder) { onNavigate(GsRoutes.CHAT_FOLDERS) }
                QuickLink("Archive", Icons.Outlined.Archive) { onNavigate(GsRoutes.CHAT_ARCHIVE) }
                QuickLink("Shared", Icons.Outlined.People) { onNavigate(GsRoutes.CHAT_SHARED) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                listOf(FILTER_ALL, FILTER_PINNED, FILTER_UNREAD).forEach { label ->
                    GsChip(text = label, selected = filter == label) { filter = label }
                }
            }

            when {
                loading -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GsSkeleton(68)
                    GsSkeleton(68)
                    GsSkeleton(68)
                }
                rows.isEmpty() -> GsEmptyState(
                    icon = Icons.Outlined.Inbox,
                    title = if (filter == FILTER_UNREAD) "All caught up" else "No conversations yet",
                    message = if (filter == FILTER_UNREAD)
                        "Nothing unread — enjoy the quiet."
                    else
                        "Start a chat with the + button and it will show up here."
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(rows, key = { it.id }) { entity ->
                        GsListItem(
                            title = entity.title,
                            subtitle = previewLine(entity),
                            leading = {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Outlined.ChatBubbleOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (entity.pinned) {
                                        Icon(
                                            Icons.Filled.Star,
                                            contentDescription = "Pinned",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { actionTarget = entity },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.MoreVert,
                                            contentDescription = "Conversation actions",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            onClick = { onNavigate(GsRoutes.chat(entity.id)) }
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
            onDismiss = { actionTarget = null },
            onTogglePin = {
                actionTarget = null
                scope.launch { runCatching { ServiceLocator.chat.setPinned(target.id, !target.pinned) } }
            },
            onArchive = {
                actionTarget = null
                scope.launch { runCatching { ServiceLocator.chat.setArchived(target.id, true) } }
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
private fun RowScope.QuickLink(label: String, icon: ImageVector, onClick: () -> Unit) {
    GsCard(modifier = Modifier.weight(1f), onClick = onClick) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = label,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(GsMotion.spaceXS))
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

// --- null-safe ServiceLocator access (previews never crash) -----------------

private fun conversationsFlow(): Flow<List<ConversationEntity>> =
    runCatching { ServiceLocator.chat.activeConversations() }.getOrElse { flowOf(emptyList()) }

private fun connectionStateFlow(): StateFlow<Boolean> =
    runCatching { ServiceLocator.chat.connectionState }.getOrElse { MutableStateFlow(false) }

// --- sample data (shown seamlessly when Room is empty and backend is away) --

private const val FILTER_ALL = "All"
private const val FILTER_PINNED = "Pinned"
private const val FILTER_UNREAD = "Unread"

private data class SampleChat(
    val id: String,
    val title: String,
    val pinned: Boolean,
    val preview: String
)

private val sampleChats = listOf(
    SampleChat("sample-1", "Launch plan review", true, "Summarised the Q3 positioning deck · 09:41"),
    SampleChat("sample-2", "Kotlin coroutines deep-dive", false, "Explained structured concurrency · Yesterday"),
    SampleChat("sample-3", "Kyoto trip planning", true, "Built a 7-day design-focused itinerary · Mon"),
    SampleChat("sample-4", "Log-parsing regex", false, "Drafted a pattern for multi-line stack traces · Sun")
)

private fun sampleConversations(): List<ConversationEntity> = sampleChats.map {
    ConversationEntity(
        id = it.id,
        title = it.title,
        modelId = null,
        pinned = it.pinned,
        archived = false,
        updatedAt = "sample",
        createdAt = "sample"
    )
}

private fun previewLine(entity: ConversationEntity): String =
    sampleChats.firstOrNull { it.id == entity.id }?.preview
        ?: "Last message · ${relativeTime(entity.updatedAt)}"

private fun relativeTime(iso: String): String = runCatching {
    val timestamp = OffsetDateTime.parse(iso)
    val elapsed = Duration.between(timestamp, OffsetDateTime.now())
    when {
        elapsed.toMinutes() < 1 -> "just now"
        elapsed.toHours() < 1 -> "${elapsed.toMinutes()}m ago"
        elapsed.toHours() < 24 -> "${elapsed.toHours()}h ago"
        elapsed.toDays() < 7 -> "${elapsed.toDays()}d ago"
        else -> timestamp.toLocalDate().toString()
    }
}.getOrElse { "" }
