package com.grapsee.gsai.ui.navigation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.local.ConversationEntity
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.components.ConversationActionsSheet
import com.grapsee.gsai.ui.components.GsButton
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.components.gsConversationTitle
import com.grapsee.gsai.ui.theme.GsHaptics
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.GsRadius
import com.grapsee.gsai.ui.theme.GsTheme
import com.grapsee.gsai.ui.theme.kineticPress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * AERUO KINETIC drawer — the primary navigation of the product shell
 * (STEP 2). One coherent hierarchy, top to bottom:
 *
 *  1. ACCOUNT — avatar, identity, subscription indicator; the row itself is
 *     the account action (Profile) and the plan chip routes to Billing.
 *  2. NEW CHAT — the single dominant action, styled as the Primary button.
 *  3. RECENT — live conversations (tap to open, long-press for actions),
 *     capped so history never drowns navigation; All chats / Archived below.
 *  4. PRIMARY — the core product areas: Home, Chats, Explore, Create, Library.
 *  5. TOOLS — the workspace surface: Projects, Assistants, Models, Search.
 *  6. ACCOUNT — Profile, Notifications, Billing, Settings; deliberately
 *     separated from product navigation.
 *
 * Every section row exposes a selected state driven by the current route —
 * the drawer always answers "where am I?". Content follows the active theme
 * through GsTheme tokens; nothing here is forced dark.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GsDrawerContent(
    selectedRoute: String?,
    onNavigate: (String) -> Unit,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
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
            .clip(RoundedCornerShape(topEnd = GsRadius.sheet, bottomEnd = GsRadius.sheet))
            .background(GsTheme.colors.navSurface)
            .semantics { paneTitle = "Navigation drawer" }
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = GsMotion.spaceM)
    ) {
        Spacer(Modifier.height(GsMotion.spaceL))

        // 1 · Account header — identity + subscription indicator + account action
        val headerInteraction = remember { MutableInteractionSource() }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GsRadius.mdShape())
                .kineticPress(headerInteraction)
                .clickable(
                    interactionSource = headerInteraction,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClickLabel = "Open profile"
                ) { onNavigate(GsRoutes.PROFILE) }
                .padding(horizontal = GsMotion.spaceS, vertical = GsMotion.spaceS),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(GsTheme.colors.accentSoft),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "GA",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = GsTheme.colors.accent
                )
            }
            Spacer(Modifier.width(GsMotion.spaceS))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Grapsee Admin",
                    style = MaterialTheme.typography.titleMedium,
                    color = GsTheme.colors.textPrimary
                )
                Text(
                    "graphesee@gmail.com",
                    style = MaterialTheme.typography.labelMedium,
                    color = GsTheme.colors.textSecondary
                )
            }
            GsChip(text = "Pro", selected = true, onClick = { onNavigate(GsRoutes.BILLING) })
        }

        Spacer(Modifier.height(GsMotion.spaceL))

        // 2 · New chat — the one loud action, dressed as the Primary button
        GsButton(
            label = "New chat",
            leadingIcon = Icons.Outlined.Edit,
            onClick = {
                onClose()
                onNavigate(GsRoutes.chat(null))
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(GsMotion.spaceL))

        // 3 · Recent — accessible history, never overwhelming
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
                    onActions = {
                        // The reveal haptic the system lists play on long-press.
                        GsHaptics.longPress(haptics)
                        actionTarget = conversation
                    }
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
        DrawerDivider()
        Spacer(Modifier.height(GsMotion.spaceL))

        // 4 · Primary navigation — the core product areas (no label; the
        // divider already separates them from history)
        DrawerRow("Home", icon = Icons.Outlined.Home, selected = selectedRoute == GsRoutes.HOME) {
            onClose(); onNavigate(GsRoutes.HOME)
        }
        DrawerRow("Chats", icon = Icons.Outlined.Chat, selected = selectedRoute == GsRoutes.CHATS) {
            onClose(); onNavigate(GsRoutes.CHATS)
        }
        DrawerRow("Explore", icon = Icons.Outlined.Explore, selected = selectedRoute == GsRoutes.EXPLORE) {
            onClose(); onNavigate(GsRoutes.EXPLORE)
        }
        DrawerRow("Create", icon = Icons.Outlined.AutoAwesome, selected = selectedRoute == GsRoutes.CREATE) {
            onClose(); onNavigate(GsRoutes.CREATE)
        }
        DrawerRow("Library", icon = Icons.Outlined.Bookmarks, selected = selectedRoute == GsRoutes.LIBRARY) {
            onClose(); onNavigate(GsRoutes.LIBRARY)
        }

        Spacer(Modifier.height(GsMotion.spaceL))
        DrawerDivider()
        Spacer(Modifier.height(GsMotion.spaceL))

        // 5 · Tools — the workspace surface
        DrawerLabel("Tools")
        Spacer(Modifier.height(GsMotion.spaceS))
        DrawerRow("Projects", icon = Icons.Outlined.Folder, selected = selectedRoute == GsRoutes.PROJECTS) {
            onClose(); onNavigate(GsRoutes.PROJECTS)
        }
        DrawerRow("Assistants", icon = Icons.Outlined.SmartToy, selected = selectedRoute == GsRoutes.ASSISTANTS) {
            onClose(); onNavigate(GsRoutes.ASSISTANTS)
        }
        DrawerRow("Models", icon = Icons.Outlined.Speed, selected = selectedRoute == GsRoutes.MODELS) {
            onClose(); onNavigate(GsRoutes.MODELS)
        }
        DrawerRow("Search", icon = Icons.Outlined.Search, selected = selectedRoute == GsRoutes.SEARCH) {
            onClose(); onNavigate(GsRoutes.SEARCH)
        }

        Spacer(Modifier.height(GsMotion.spaceL))
        DrawerDivider()
        Spacer(Modifier.height(GsMotion.spaceL))

        // 6 · Account — system areas, clearly separated from product navigation
        DrawerLabel("Account")
        Spacer(Modifier.height(GsMotion.spaceS))
        DrawerRow("Profile", icon = Icons.Outlined.Person, selected = selectedRoute == GsRoutes.PROFILE) {
            onClose(); onNavigate(GsRoutes.PROFILE)
        }
        DrawerRow("Notifications", icon = Icons.Outlined.Notifications, selected = selectedRoute == GsRoutes.NOTIFICATIONS) {
            onClose(); onNavigate(GsRoutes.NOTIFICATIONS)
        }
        DrawerRow("Billing", icon = Icons.Outlined.CreditCard, selected = selectedRoute == GsRoutes.BILLING) {
            onClose(); onNavigate(GsRoutes.BILLING)
        }
        DrawerRow("Settings", icon = Icons.Outlined.Settings, selected = selectedRoute == GsRoutes.SETTINGS) {
            onClose(); onNavigate(GsRoutes.SETTINGS)
        }

        Spacer(Modifier.height(GsMotion.spaceXL))
    }

    if (actionTarget != null) {
        ConversationActionsSheet(
            title = gsConversationTitle(actionTarget?.title),
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
    val folderInteraction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GsRadius.mdShape())
            .kineticPress(folderInteraction)
            .combinedClickable(
                interactionSource = folderInteraction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onOpen,
                onLongClick = onActions
            )
            .padding(horizontal = GsMotion.spaceM, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
    ) {
        Text(
            gsConversationTitle(conversation.title),
            style = MaterialTheme.typography.bodyMedium,
            color = GsTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (conversation.pinned) {
            Icon(
                Icons.Filled.Star,
                contentDescription = "Pinned",
                tint = GsTheme.colors.accent,
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
        color = GsTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

/** The drawer's one hairline — structural separation between route groups. */
@Composable
private fun DrawerDivider() {
    HorizontalDivider(color = GsTheme.colors.divider, thickness = 1.dp)
}

/**
 * One navigation row. [selected] marks the drawer's answer to "where am I":
 * a soft accent container with accent content — deliberately quieter than a
 * filled pill, loud enough to read at a glance.
 */
@Composable
private fun DrawerRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val colors = GsTheme.colors
    val navRowInteraction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GsRadius.mdShape())
            .background(if (selected) colors.accentSoft else colors.appBackground.copy(alpha = 0f))
            .kineticPress(navRowInteraction)
            .clickable(
                interactionSource = navRowInteraction,
                indication = LocalIndication.current,
                role = Role.Button
            ) { onClick() }
            .semantics { this.selected = selected }
            .padding(horizontal = GsMotion.spaceM, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) colors.accent else colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Spacer(Modifier.width(2.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) colors.accent else colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
