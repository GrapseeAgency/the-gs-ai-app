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
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
// Material3 1.3.0: PullToRefreshBox lives in the pulltorefresh sub-package
// (top-level material3.PullToRefreshBox only arrived later).
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
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
import com.grapsee.gsai.ui.components.gsConversationTitle
import com.grapsee.gsai.ui.components.GsSkeleton
import com.grapsee.gsai.ui.components.rememberDeviceOffline
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.gsHaptic
import java.time.Duration
import java.time.OffsetDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Chats hub — Room conversation list as the source of truth with the
 * backend sync engine behind it. Nothing is invented when the list is
 * empty: an honest empty state is shown instead of sample rows. The only
 * quick link is Archive (a real store-backed surface — Folders and Shared
 * were dead fabrication and are gone). Rows carry the benchmark action set
 * (pin / archive / delete) via the overflow menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(onNavigate: (String) -> Unit) {
    val conversationList = remember { conversationsFlow() }
    val conversations by conversationList.collectAsState(initial = emptyList())
    val offline by rememberDeviceOffline()
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    var firstLoadDone by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(FILTER_ALL) }
    var actionTarget by remember { mutableStateOf<ConversationEntity?>(null) }
    // Pull-to-refresh honesty: this flag IS the in-flight refreshConversations()
    // call — the spinner never shows when no refresh is running.
    var refreshing by remember { mutableStateOf(false) }

    suspend fun doRefresh() {
        refreshing = true
        runCatching { ServiceLocator.chat.refreshConversations() }
        refreshing = false
        firstLoadDone = true
    }

    LaunchedEffect(Unit) { doRefresh() }

    // No sample fallback: when Room is empty the honest empty state shows.
    val rows = when (filter) {
        FILTER_PINNED -> conversations.filter { it.pinned }
        else -> conversations
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
        // Material3 1.3 pull-to-refresh around the whole hub content; the list
        // underneath is a LazyColumn, so the nested-scroll drag is handed to
        // the indicator only at the top of the list.
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { doRefresh() } },
            state = rememberPullToRefreshState(),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceM)
        ) {
            GsOfflineBanner(visible = offline)

            // Archive only: the one quick link with a real destination behind
            // it. Folders and Shared led to hardcoded fake screens and are
            // deliberately not offered.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickLink("Archive", Icons.Outlined.Archive) { onNavigate(GsRoutes.CHAT_ARCHIVE) }
            }

            // All/Pinned only — both are computed from real Room state. The
            // old "Unread" chip was hardcoded to filter to an empty list
            // (nothing tracks read state), so it never offered anything true.
            Row(horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                listOf(FILTER_ALL, FILTER_PINNED).forEach { label ->
                    GsChip(text = label, selected = filter == label) {
                        view.gsHaptic(HapticFeedbackConstants.VIRTUAL_KEY)
                        filter = label
                    }
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
                    title = if (filter == FILTER_PINNED) "Nothing pinned yet" else "No conversations yet",
                    message = if (filter == FILTER_PINNED) {
                        "Pin a chat from its overflow menu and it will live here."
                    } else {
                        "Start a chat with the + button and it will show up here."
                    }
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(rows, key = { it.id }) { entity ->
                        GsListItem(
                            title = gsConversationTitle(entity.title),
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

// rememberDeviceOffline lives in ui.components (shared with the chat surface).

private fun conversationsFlow(): Flow<List<ConversationEntity>> =
    runCatching { ServiceLocator.chat.activeConversations() }.getOrElse { flowOf(emptyList()) }

// --- filters -----------------------------------------------------------------

private const val FILTER_ALL = "All"
private const val FILTER_PINNED = "Pinned"

private fun previewLine(entity: ConversationEntity): String =
    "Last message · ${relativeTime(entity.updatedAt)}"

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
