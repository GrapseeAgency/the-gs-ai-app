package com.grapsee.gsai.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.local.ConversationEntity
import com.grapsee.gsai.data.local.ConversationPreviewRow
import com.grapsee.gsai.data.liveupdate.LiveUpdateState
import com.grapsee.gsai.data.liveupdate.LiveUpdater
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.components.ConversationActionsSheet
import com.grapsee.gsai.ui.components.RefArt
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefDivider
import com.grapsee.gsai.ui.components.RefEmptyState
import com.grapsee.gsai.ui.components.RefIconButton
import com.grapsee.gsai.ui.components.RefIconTile
import com.grapsee.gsai.ui.components.RefPage
import com.grapsee.gsai.ui.components.RefSegmented
import com.grapsee.gsai.ui.components.gsConversationTitle
import com.grapsee.gsai.ui.components.rememberDeviceOffline
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.GsTheme
import java.time.Duration
import java.time.OffsetDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Chats — the conversation shelf.
 *
 * One page title, one segmented filter, one glass group of real Room rows.
 * Nothing here is invented: the list is the on-device store, the preview is
 * the last real message, and the empty state says so. The old screen wrapped
 * each row in its own card and painted a separate gradient behind the page;
 * both fought the bloom. Now the page is transparent and the rows share one
 * cream surface, like the reference.
 */
@Composable
fun ChatsScreen(onNavigate: (String) -> Unit) {
    val conversationList = remember { conversationsPreviewFlow() }
    val conversations by conversationList.collectAsStateWithLifecycle(initialValue = emptyList())
    val liveUpdate by LiveUpdater.state.collectAsStateWithLifecycle()
    val offline by rememberDeviceOffline()
    val scope = rememberCoroutineScope()

    var firstLoadDone by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(FILTER_ALL) }
    var actionTarget by remember { mutableStateOf<ConversationEntity?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    // Reference multi-select: long-press a row, act on the set.
    var selection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmingBulkDelete by remember { mutableStateOf(false) }
    val selectionMode = selection.isNotEmpty()

    suspend fun doRefresh() {
        if (refreshing) return
        refreshing = true
        runCatching { ServiceLocator.chat.refreshConversations() }
        refreshing = false
        firstLoadDone = true
    }

    LaunchedEffect(Unit) { doRefresh() }

    // System back leaves selection before it leaves the screen.
    BackHandler(enabled = selectionMode) {
        selection = emptySet()
        confirmingBulkDelete = false
    }

    val rows = when (filter) {
        FILTER_PINNED -> conversations.filter { it.conversation.pinned }
        else -> conversations
    }
    val loading = !firstLoadDone && conversations.isEmpty()

    RefPage(
        title = "Chats",
        subtitle = "Every conversation stays on this device.",
        onBack = null,
        actions = {
            RefIconButton(
                icon = GsIcons.Search,
                label = "Search chats",
                onClick = { onNavigate(GsRoutes.CHAT_SEARCH) }
            )
            RefIconButton(
                icon = GsIcons.SquarePen,
                label = "New chat",
                onClick = { onNavigate(GsRoutes.chat(null)) }
            )
        }
    ) {
        if (selectionMode) {
            SelectionBar(
                count = selection.size,
                allSelected = rows.isNotEmpty() && selection.size == rows.size,
                onToggleAll = {
                    selection = if (selection.size == rows.size) {
                        emptySet()
                    } else {
                        rows.map { it.conversation.id }.toSet()
                    }
                },
                onClose = { selection = emptySet() },
                onPin = {
                    val ids = selection
                    selection = emptySet()
                    scope.launch { ids.forEach { runCatching { ServiceLocator.chat.setPinned(it, true) } } }
                },
                onArchive = {
                    val ids = selection
                    selection = emptySet()
                    scope.launch { ids.forEach { runCatching { ServiceLocator.chat.setArchived(it, true) } } }
                },
                onDelete = { confirmingBulkDelete = true }
            )
            Spacer(Modifier.height(12.dp))
        }

        if (offline) {
            InfoCard(
                icon = GsIcons.WifiOff,
                title = "Working offline",
                body = "New replies will use the built-in responder until the connection is back."
            )
        }

        when (val update = liveUpdate) {
            is LiveUpdateState.Available -> InfoCard(
                icon = GsIcons.Download,
                title = "GS ${update.versionName} is ready",
                body = "Tap to install the update.",
                onClick = { LiveUpdater.beginInstallFlow() }
            )
            is LiveUpdateState.Downloading -> InfoCard(
                icon = GsIcons.Download,
                title = "Updating… ${update.percent}%",
                body = "Keep GS open for a moment."
            )
            is LiveUpdateState.Ready -> InfoCard(
                icon = GsIcons.Download,
                title = "Starting installer…",
                body = "The system will ask you to confirm."
            )
            is LiveUpdateState.Failed -> InfoCard(
                icon = GsIcons.CircleAlert,
                title = "Update didn't finish",
                body = "Tap to try again.",
                onClick = { LiveUpdater.beginInstallFlow() }
            )
            is LiveUpdateState.Idle -> Unit
        }

        Spacer(Modifier.height(16.dp))
        RefSegmented(
            options = listOf(FILTER_ALL, FILTER_PINNED),
            selected = filter,
            onSelect = { filter = it },
            modifier = Modifier.padding(horizontal = 2.dp)
        )

        Spacer(Modifier.height(14.dp))
        RefCard {
            ShelfRow(
                title = "Archive",
                subtitle = "Chats you've put away",
                icon = GsIcons.Archive,
                onClick = { onNavigate(GsRoutes.CHAT_ARCHIVE) }
            )
            RefDivider()
            ShelfRow(
                title = "Search chats",
                subtitle = "Find a word across every message",
                icon = GsIcons.TextSearch,
                onClick = { onNavigate(GsRoutes.CHAT_SEARCH) }
            )
        }

        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (filter == FILTER_PINNED) "Pinned" else "Recent",
                style = MaterialTheme.typography.titleSmall,
                color = GsTheme.colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = when {
                    loading -> "Checking…"
                    refreshing -> "Refreshing…"
                    rows.isEmpty() -> "Nothing yet"
                    else -> "${rows.size} conversation${if (rows.size == 1) "" else "s"}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = GsTheme.colors.textTertiary
            )
        }
        Spacer(Modifier.height(8.dp))

        when {
            loading -> RefCard {
                repeat(3) { index ->
                    if (index > 0) RefDivider()
                    SkeletonRow()
                }
            }
            rows.isEmpty() -> RefEmptyState(
                art = if (filter == FILTER_PINNED) RefArt.Archive else RefArt.Conversation,
                title = if (filter == FILTER_PINNED) "Nothing pinned yet" else "No conversations yet",
                message = if (filter == FILTER_PINNED) {
                    "Pin a chat from its row menu and it will wait here."
                } else {
                    "Start with a thought. Your first conversation will live here."
                },
                actionLabel = if (filter == FILTER_PINNED) null else "Start a chat",
                onAction = if (filter == FILTER_PINNED) null else ({ onNavigate(GsRoutes.chat(null)) })
            )
            else -> RefCard {
                rows.forEachIndexed { index, row ->
                    if (index > 0) RefDivider()
                    ChatRow(
                        row = row,
                        selectionMode = selectionMode,
                        selected = row.conversation.id in selection,
                        onClick = {
                            if (selectionMode) {
                                selection = selection.toggle(row.conversation.id)
                            } else {
                                onNavigate(GsRoutes.chat(row.conversation.id))
                            }
                        },
                        onLongClick = { selection = selection + row.conversation.id },
                        onActions = { actionTarget = row.conversation }
                    )
                }
            }
        }

        Text(
            text = "History is stored locally. Signing out keeps it on this device.",
            style = MaterialTheme.typography.labelSmall,
            color = GsTheme.colors.textTertiary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 18.dp)
        )
    }

    val target = actionTarget
    if (target != null) {
        ConversationActionsSheet(
            title = gsConversationTitle(target.title),
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

    if (confirmingBulkDelete) {
        AlertDialog(
            onDismissRequest = { confirmingBulkDelete = false },
            title = { Text("Delete ${selection.size} chat${if (selection.size == 1) "" else "s"}?") },
            text = { Text("This cannot be undone. Every message in them is removed from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    val ids = selection
                    confirmingBulkDelete = false
                    selection = emptySet()
                    scope.launch { ids.forEach { runCatching { ServiceLocator.chat.delete(it) } } }
                }) {
                    Text("Delete", color = GsTheme.colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingBulkDelete = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun InfoCard(
    icon: ImageVector,
    title: String,
    body: String,
    onClick: (() -> Unit)? = null,
) {
    RefCard(
        modifier = Modifier
            .padding(bottom = 10.dp)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onClick
                ) else Modifier
            )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RefIconTile(icon)
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = GsTheme.colors.textPrimary
                )
                Text(
                    body,
                    style = MaterialTheme.typography.labelMedium,
                    color = GsTheme.colors.textSecondary
                )
            }
            if (onClick != null) {
                Icon(
                    GsIcons.ChevronRight,
                    contentDescription = null,
                    tint = GsTheme.colors.textTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ShelfRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = title,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RefIconTile(icon)
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = GsTheme.colors.textPrimary
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = GsTheme.colors.textSecondary
            )
        }
        Icon(
            GsIcons.ChevronRight,
            contentDescription = null,
            tint = GsTheme.colors.textTertiary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRow(
    row: ConversationPreviewRow,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onActions: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .background(
                if (selected) GsTheme.colors.accentSoft else androidx.compose.ui.graphics.Color.Transparent
            )
            .combinedClickable(
                role = Role.Button,
                onClickLabel = gsConversationTitle(row.conversation.title),
                onLongClickLabel = "Select ${gsConversationTitle(row.conversation.title)}",
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(GsTheme.colors.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                GsIcons.MessageCircle,
                contentDescription = null,
                tint = GsTheme.colors.accentStrong,
                modifier = Modifier.size(19.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    gsConversationTitle(row.conversation.title),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = GsTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (row.conversation.pinned) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        GsIcons.Star,
                        contentDescription = "Pinned",
                        tint = GsTheme.colors.accentStrong,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
            Text(
                previewLine(row),
                style = MaterialTheme.typography.labelMedium,
                color = GsTheme.colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (selectionMode) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) GsTheme.colors.accent
                        else GsTheme.colors.glassSurface.copy(alpha = 0.8f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        GsIcons.Check,
                        contentDescription = "Selected",
                        tint = GsTheme.colors.onAccent,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        } else {
            IconButton(onClick = onActions, modifier = Modifier.size(38.dp)) {
                Icon(
                    GsIcons.EllipsisVertical,
                    contentDescription = "Conversation actions",
                    tint = GsTheme.colors.textTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** Toggle one id in a selection set. */
private fun Set<String>.toggle(id: String): Set<String> =
    if (id in this) this - id else this + id

/**
 * The reference's "N selected" bar: count, select-all, and the three bulk
 * actions the store can actually perform — pin, archive, delete.
 */
@Composable
private fun SelectionBar(
    count: Int,
    allSelected: Boolean,
    onToggleAll: () -> Unit,
    onClose: () -> Unit,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    RefCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(38.dp)) {
                Icon(
                    GsIcons.X,
                    contentDescription = "Exit selection",
                    tint = GsTheme.colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = "$count selected",
                style = MaterialTheme.typography.titleSmall,
                color = GsTheme.colors.textPrimary
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (allSelected) "Clear" else "Select all",
                style = MaterialTheme.typography.labelMedium,
                color = GsTheme.colors.accentStrong,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onToggleAll)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
            SelectionAction(GsIcons.Star, "Pin selected", onPin)
            SelectionAction(GsIcons.Archive, "Archive selected", onArchive)
            SelectionAction(GsIcons.Trash2, "Delete selected", onDelete, destructive = true)
        }
    }
}

@Composable
private fun SelectionAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(38.dp)) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (destructive) GsTheme.colors.error else GsTheme.colors.textSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SkeletonRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(GsTheme.colors.border.copy(alpha = 0.45f))
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(
                Modifier
                    .fillMaxWidth(0.55f)
                    .height(11.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(GsTheme.colors.border.copy(alpha = 0.55f))
            )
            Box(
                Modifier
                    .fillMaxWidth(0.85f)
                    .height(9.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(GsTheme.colors.border.copy(alpha = 0.35f))
            )
        }
    }
}

private fun conversationsPreviewFlow(): Flow<List<ConversationPreviewRow>> =
    runCatching { ServiceLocator.db.conversationDao().observeActiveWithPreview() }
        .getOrElse { flowOf(emptyList()) }

private const val FILTER_ALL = "All"
private const val FILTER_PINNED = "Pinned"

/** The real last message + its moment, or just the moment when there is none. */
private fun previewLine(row: ConversationPreviewRow): String {
    val message = row.lastMessage?.trim()?.replace('\n', ' ')?.trim()
    return when {
        !message.isNullOrBlank() -> {
            val clipped = if (message.length > 90) message.take(90) + "…" else message
            "$clipped · ${relativeTime(row.conversation.updatedAt)}"
        }
        else -> relativeTime(row.conversation.updatedAt)
    }
}

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
