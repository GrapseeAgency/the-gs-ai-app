package com.grapsee.gsai.ui.navigation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.local.ConversationEntity
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.components.ConversationActionsSheet
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.theme.Aeruo
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.kineticPress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * AERUO KINETIC drawer — the primary navigation, exactly like the benchmark
 * AI apps (ChatGPT / Claude / Kimi): obsidian panel, account header, one-tap
 * new chat, LIVE recents from the Room cache (pin / archive / delete on
 * long-press), and the section map. The home canvas stays clean.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GsDrawerContent(
    onNavigate: (String) -> Unit,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val recents by remember {
        runCatching { ServiceLocator.chat.activeConversations() }.getOrElse { flowOf(emptyList()) }
    }.collectAsState(initial = emptyList())

    var actionTarget by remember { mutableStateOf<ConversationEntity?>(null) }

    val mutate: (suspend (ConversationEntity) -> Unit) -> Unit = { action ->
        val target = actionTarget
        actionTarget = null
        if (target != null) {
            scope.launch { runCatching { action(target) } }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(304.dp)
            .clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
            .background(Aeruo.Obsidian)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = GsMotion.spaceM)
    ) {
        Spacer(Modifier.height(GsMotion.spaceL))

        // Account header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Aeruo.AccentSoftDark),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "GA",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Aeruo.Accent
                )
            }
            Spacer(Modifier.width(GsMotion.spaceS))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Grapsee Admin",
                    style = MaterialTheme.typography.titleMedium,
                    color = Aeruo.TextDark
                )
                Text(
                    "graphesee@gmail.com",
                    style = MaterialTheme.typography.labelMedium,
                    color = Aeruo.TextMutedDark
                )
            }
            GsChip(text = "Pro", selected = true, onClick = { onNavigate(GsRoutes.BILLING) })
        }

        Spacer(Modifier.height(GsMotion.spaceL))

        // New chat — the one loud action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(GsMotion.radiusCard))
                .background(Aeruo.RaisedDark)
                .kineticPress()
                .clickable {
                    onClose()
                    onNavigate(GsRoutes.chat(null))
                }
                .padding(horizontal = GsMotion.spaceM, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Edit,
                contentDescription = null,
                tint = Aeruo.Accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(GsMotion.spaceS))
            Text(
                "New chat",
                style = MaterialTheme.typography.titleMedium,
                color = Aeruo.TextDark
            )
        }

        Spacer(Modifier.height(GsMotion.spaceL))
        DrawerLabel("Recent")
        Spacer(Modifier.height(GsMotion.spaceS))

        val liveRecents = recents.take(RECENT_LIMIT)
        if (liveRecents.isEmpty()) {
            // Cold cache — tasteful samples keep the drawer alive (same policy as Chats).
            sampleRecents.forEach { sample ->
                DrawerRow(sample.title) {
                    onClose()
                    onNavigate(GsRoutes.chat(sample.id))
                }
            }
        } else {
            liveRecents.forEach { conversation ->
                RecentRow(
                    conversation = conversation,
                    onOpen = {
                        onClose()
                        onNavigate(GsRoutes.chat(conversation.id))
                    },
                    onActions = { actionTarget = conversation }
                )
            }
            Spacer(Modifier.height(GsMotion.spaceXS))
            DrawerRow("All chats", icon = Icons.Outlined.Inbox) {
                onClose(); onNavigate(GsRoutes.CHATS)
            }
            DrawerRow("Archived", icon = Icons.Outlined.Archive) {
                onClose(); onNavigate(GsRoutes.CHAT_ARCHIVE)
            }
        }

        Spacer(Modifier.height(GsMotion.spaceL))
        HorizontalDivider(color = Aeruo.OutlineDark, thickness = 1.dp)
        Spacer(Modifier.height(GsMotion.spaceL))

        DrawerLabel("Explore")
        Spacer(Modifier.height(GsMotion.spaceS))
        DrawerRow("Chats", icon = Icons.Outlined.Edit) {
            onClose(); onNavigate(GsRoutes.CHATS)
        }
        DrawerRow("Explore", icon = Icons.Outlined.Explore) {
            onClose(); onNavigate(GsRoutes.EXPLORE)
        }
        DrawerRow("Create", icon = Icons.Outlined.AutoAwesome) {
            onClose(); onNavigate(GsRoutes.CREATE)
        }
        DrawerRow("Library", icon = Icons.Outlined.Bookmarks) {
            onClose(); onNavigate(GsRoutes.LIBRARY)
        }
        DrawerRow("Projects", icon = Icons.Outlined.Folder) {
            onClose(); onNavigate(GsRoutes.PROJECTS)
        }
        DrawerRow("Assistants", icon = Icons.Outlined.SmartToy) {
            onClose(); onNavigate(GsRoutes.ASSISTANTS)
        }
        DrawerRow("Models", icon = Icons.Outlined.Speed) {
            onClose(); onNavigate(GsRoutes.MODELS)
        }
        DrawerRow("Search", icon = Icons.Outlined.Search) {
            onClose(); onNavigate(GsRoutes.SEARCH)
        }

        Spacer(Modifier.height(GsMotion.spaceL))
        HorizontalDivider(color = Aeruo.OutlineDark, thickness = 1.dp)
        Spacer(Modifier.height(GsMotion.spaceL))

        DrawerLabel("Account")
        Spacer(Modifier.height(GsMotion.spaceS))
        DrawerRow("Upgrade plan", icon = Icons.Outlined.AutoAwesome) {
            onClose(); onNavigate(GsRoutes.BILLING)
        }
        DrawerRow("Notifications", icon = Icons.Outlined.Notifications) {
            onClose(); onNavigate(GsRoutes.NOTIFICATIONS)
        }
        DrawerRow("Profile", icon = Icons.Outlined.Person) {
            onClose(); onNavigate(GsRoutes.PROFILE)
        }
        DrawerRow("Settings", icon = Icons.Outlined.Settings) {
            onClose(); onNavigate(GsRoutes.SETTINGS)
        }

        Spacer(Modifier.height(GsMotion.spaceXL))
    }

    if (actionTarget != null) {
        ConversationActionsSheet(
            title = actionTarget?.title.orEmpty(),
            pinned = actionTarget?.pinned == true,
            onDismiss = { actionTarget = null },
            onTogglePin = { mutate { c -> ServiceLocator.chat.setPinned(c.id, !c.pinned) } },
            onArchive = { mutate { c -> ServiceLocator.chat.setArchived(c.id, true) } },
            onDelete = { mutate { c -> ServiceLocator.chat.delete(c.id) } },
            onRename = { name ->
                val target = actionTarget
                actionTarget = null
                if (target != null) {
                    scope.launch { runCatching { ServiceLocator.chat.rename(target.id, name) } }
                }
            }
        )
    }
}

/** One live recent: tap to open, long-press for the benchmark action set. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentRow(
    conversation: ConversationEntity,
    onOpen: () -> Unit,
    onActions: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .kineticPress()
            .combinedClickable(onClick = onOpen, onLongClick = onActions)
            .padding(horizontal = GsMotion.spaceM, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
    ) {
        Text(
            conversation.title,
            style = MaterialTheme.typography.bodyMedium,
            color = Aeruo.TextDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (conversation.pinned) {
            Icon(
                Icons.Filled.Star,
                contentDescription = "Pinned",
                tint = Aeruo.Accent,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/** Pin / archive / delete sheet now lives in ui/components.ConversationActionsSheet (shared with Chats). */

@Composable
private fun DrawerLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = Aeruo.TextMutedDark,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun DrawerRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .kineticPress()
            .clickable(onClick = onClick)
            .padding(horizontal = GsMotion.spaceM, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = Aeruo.TextMutedDark,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Spacer(Modifier.width(2.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = Aeruo.TextDark,
            maxLines = 1
        )
    }
}

private const val RECENT_LIMIT = 5

private data class SampleRecent(val id: String, val title: String)

private val sampleRecents = listOf(
    SampleRecent("demo-1", "Q3 pricing strategy"),
    SampleRecent("demo-2", "Kyoto trip plan"),
    SampleRecent("demo-3", "Kotlin coroutines notes"),
    SampleRecent("demo-4", "Brand voice guidelines"),
    SampleRecent("demo-5", "Research: AI market")
)
