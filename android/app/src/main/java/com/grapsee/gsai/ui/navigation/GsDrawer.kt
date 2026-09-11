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
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GraphicEq
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.local.ConversationEntity
import com.grapsee.gsai.data.AccountStore
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.components.ConversationActionsSheet
import com.grapsee.gsai.ui.components.GsButton
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
 * AERUO KINETIC drawer — the primary navigation of the product shell.
 * PHASE 2 reclassification — a product navigation system, not an app
 * directory. Top to bottom:
 *
 *  1. ACCOUNT HEADER — real stored identity; the row opens Profile.
 *     (The fabricated "Pro" plan chip is gone — no plan system exists.)
 *  2. NEW CHAT — the single dominant action.
 *  3. RECENT — LIVE conversations only; when there are none the group says
 *     nothing (the hardcoded sample chats are deleted — an honest empty
 *     state beats invented history). All chats / Archived always visible.
 *  4. PRIMARY — Home, Chats, Explore, Create, Library.
 *  5. SECONDARY ("More") — Projects, Assistants, Voice, Search.
 *  6. ACCOUNT — Profile, Notifications, Billing, Settings.
 *  7. ADVANCED — collapsed by default: Models, Compare models. Technical
 *     surfaces stay reachable without sitting at parity with conversation.
 *
 * Every section row exposes a selected state driven by the current route —
 * the drawer always answers "where am I?".
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
    // Account identity (STEP 3 minimal wiring): the name/email the user
    // actually typed at auth, via AccountStore. Nothing is fabricated — a
    // blank stored value degrades to a neutral label, never a made-up name
    // or address. Read per composition; the drawer re-runs this body each
    // time it is opened.
    val context = LocalContext.current
    val storedName = AccountStore.displayName(context)
    val storedEmail = AccountStore.email(context)
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
                    initialsFor(storedName),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = GsTheme.colors.accent
                )
            }
            Spacer(Modifier.width(GsMotion.spaceS))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    // Real identity only: the stored name, else a neutral
                    // label — never an invented "Admin".
                    storedName.ifBlank { "GS account" },
                    style = MaterialTheme.typography.titleMedium,
                    color = GsTheme.colors.textPrimary
                )
                Text(
                    // The stored email, else a neutral line — never a
                    // fabricated address.
                    storedEmail.ifBlank { "Signed in" },
                    style = MaterialTheme.typography.labelMedium,
                    color = GsTheme.colors.textSecondary
                )
            }
            // The fabricated "Pro" plan chip is removed — there is no plan
            // system; Billing (honest early-access state) stays in Account.
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

        // 3 · Recent — live history only. When the store is empty the group
        // collapses to its two real destinations; nothing is invented.
        DrawerLabel("Recent")
        Spacer(Modifier.height(GsMotion.spaceS))

        recents.take(RECENT_LIMIT).forEach { conversation ->
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

        // 5 · Secondary — real workspaces, one step below the product core
        DrawerLabel("More")
        Spacer(Modifier.height(GsMotion.spaceS))
        DrawerRow("Projects", icon = Icons.Outlined.Folder, selected = selectedRoute == GsRoutes.PROJECTS) {
            onClose(); onNavigate(GsRoutes.PROJECTS)
        }
        DrawerRow("Assistants", icon = Icons.Outlined.SmartToy, selected = selectedRoute == GsRoutes.ASSISTANTS) {
            onClose(); onNavigate(GsRoutes.ASSISTANTS)
        }
        DrawerRow("Voice", icon = Icons.Outlined.GraphicEq, selected = selectedRoute == GsRoutes.VOICE) {
            onClose(); onNavigate(GsRoutes.VOICE)
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

        Spacer(Modifier.height(GsMotion.spaceL))
        DrawerDivider()
        Spacer(Modifier.height(GsMotion.spaceL))

        // 7 · Advanced — technical surfaces, collapsed by default. A normal
        // user never needs them; they stay reachable without competing with
        // conversation at row parity.
        var advancedOpen by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GsRadius.mdShape())
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = LocalIndication.current,
                    role = Role.Button
                ) { advancedOpen = !advancedOpen }
                .padding(horizontal = GsMotion.spaceM, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
        ) {
            Icon(
                Icons.Outlined.Speed,
                contentDescription = null,
                tint = GsTheme.colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                "Advanced",
                style = MaterialTheme.typography.bodyMedium,
                color = GsTheme.colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = if (advancedOpen) "Collapse advanced" else "Expand advanced",
                tint = GsTheme.colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
        if (advancedOpen) {
            DrawerRow("Models", icon = Icons.Outlined.SmartToy, selected = selectedRoute == GsRoutes.MODELS) {
                onClose(); onNavigate(GsRoutes.MODELS)
            }
            DrawerRow("Compare models", selected = selectedRoute == GsRoutes.MODEL_COMPARE) {
                onClose(); onNavigate(GsRoutes.MODEL_COMPARE)
            }
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

/**
 * Initials from the REAL stored name: the first letter of each of the first
 * two words, uppercased. No stored name → the neutral "GS" mark — never
 * initials of a name that does not exist.
 */
private fun initialsFor(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return "GS"
    return words.take(2).map { it.first().uppercaseChar() }.joinToString("")
}
