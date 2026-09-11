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
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
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
 * AERUO KINETIC drawer — a CHAT HISTORY PANEL, not a route directory.
 * The sidebar is primarily about starting and navigating conversations:
 *
 *  1. ACCOUNT — one compact identity row; opening it reveals the account
 *     controls (Profile · Settings · Notifications · Billing) in a sheet
 *     instead of spending four sidebar rows on them.
 *  2. NEW CHAT + SEARCH — the two conversation actions, always first.
 *  3. RECENT — the visual heart: live conversations, tap to open, long-press
 *     for pin / rename / archive / delete. Genuine empty state — nothing is
 *     invented. "All chats" is the quiet overflow into the full history hub.
 *  4. MORE — one row. Explore, Create, Projects, Library, Assistants, Voice,
 *     Models and Compare models live behind it, in a sheet, one step away —
 *     present, but never competing with conversation at row parity.
 *
 * Everything else the old drawer exposed is gone from the first level. The
 * screens still exist underneath; the sidebar simply stops cataloguing them.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GsDrawerContent(
    selectedRoute: String?,
    onNavigate: (String) -> Unit,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    // Account identity: the name/email the user actually typed at auth, via
    // AccountStore. Nothing is fabricated — a blank stored value degrades to a
    // neutral label, never a made-up name or address.
    val context = LocalContext.current
    val storedName = AccountStore.displayName(context)
    val storedEmail = AccountStore.email(context)
    val recents by remember {
        runCatching { ServiceLocator.chat.activeConversations() }.getOrElse { flowOf(emptyList()) }
    }.collectAsState(initial = emptyList())

    var actionTarget by remember { mutableStateOf<ConversationEntity?>(null) }
    var accountSheetOpen by remember { mutableStateOf(false) }
    var moreSheetOpen by remember { mutableStateOf(false) }

    // Sheet rows dismiss their own sheet first, then close the drawer and
    // navigate — no stuck bottom sheet over the destination.
    val navigateFromSheet: (String) -> Unit = { route ->
        accountSheetOpen = false
        moreSheetOpen = false
        onClose()
        onNavigate(route)
    }

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

        // 1 · Account — compact entry; the row reveals the account sheet.
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
                    onClickLabel = "Account options"
                ) { accountSheetOpen = true }
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
                    color = GsTheme.colors.textPrimary
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
        }

        Spacer(Modifier.height(GsMotion.spaceL))

        // 2 · New chat + Search — the two conversation actions, first.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
        ) {
            GsButton(
                label = "New chat",
                leadingIcon = Icons.Outlined.Edit,
                onClick = {
                    onClose()
                    onNavigate(GsRoutes.chat(null))
                },
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    onClose()
                    onNavigate(GsRoutes.CHAT_SEARCH)
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(GsRadius.mdShape())
                    .background(GsTheme.colors.raisedSurface)
            ) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = "Search chats",
                    tint = GsTheme.colors.textPrimary
                )
            }
        }

        Spacer(Modifier.height(GsMotion.spaceL))

        // 3 · Recent — the heart of the sidebar. Live history only; when the
        // store is empty the panel says so honestly and nothing is invented.
        if (recents.isEmpty()) {
            DrawerLabel("Recent")
            Spacer(Modifier.height(GsMotion.spaceS))
            Text(
                "No conversations yet",
                style = MaterialTheme.typography.bodyMedium,
                color = GsTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = GsMotion.spaceM, vertical = GsMotion.spaceS)
            )
        } else {
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
        }
        Spacer(Modifier.height(GsMotion.spaceXS))
        DrawerRow("All chats", icon = Icons.Outlined.Inbox, selected = selectedRoute == GsRoutes.CHATS) {
            onClose(); onNavigate(GsRoutes.CHATS)
        }

        Spacer(Modifier.height(GsMotion.spaceL))
        DrawerDivider()
        Spacer(Modifier.height(GsMotion.spaceL))

        // 4 · More — every other product surface, one quiet row away.
        DrawerRow("More", icon = Icons.Outlined.Apps) { moreSheetOpen = true }

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

    if (accountSheetOpen) {
        DrawerNavSheet(onDismiss = { accountSheetOpen = false }) {
            SheetRow("Profile", Icons.Outlined.Person) { navigateFromSheet(GsRoutes.PROFILE) }
            SheetRow("Settings", Icons.Outlined.Settings) { navigateFromSheet(GsRoutes.SETTINGS) }
            SheetRow("Notifications", Icons.Outlined.Notifications) { navigateFromSheet(GsRoutes.NOTIFICATIONS) }
            SheetRow("Billing", Icons.Outlined.CreditCard) { navigateFromSheet(GsRoutes.BILLING) }
        }
    }

    if (moreSheetOpen) {
        DrawerNavSheet(onDismiss = { moreSheetOpen = false }) {
            SheetRow("Explore", Icons.Outlined.Explore, selectedRoute == GsRoutes.EXPLORE) { navigateFromSheet(GsRoutes.EXPLORE) }
            SheetRow("Create", Icons.Outlined.AutoAwesome, selectedRoute == GsRoutes.CREATE) { navigateFromSheet(GsRoutes.CREATE) }
            SheetRow("Projects", Icons.Outlined.Folder, selectedRoute == GsRoutes.PROJECTS) { navigateFromSheet(GsRoutes.PROJECTS) }
            SheetRow("Library", Icons.Outlined.Bookmarks, selectedRoute == GsRoutes.LIBRARY) { navigateFromSheet(GsRoutes.LIBRARY) }
            SheetRow("Assistants", Icons.Outlined.SmartToy, selectedRoute == GsRoutes.ASSISTANTS) { navigateFromSheet(GsRoutes.ASSISTANTS) }
            SheetRow("Voice", Icons.Outlined.GraphicEq, selectedRoute == GsRoutes.VOICE) { navigateFromSheet(GsRoutes.VOICE) }
            SheetRow("Models", Icons.Outlined.Speed, selectedRoute == GsRoutes.MODELS) { navigateFromSheet(GsRoutes.MODELS) }
            SheetRow("Compare models", icon = null, selectedRoute == GsRoutes.MODEL_COMPARE) { navigateFromSheet(GsRoutes.MODEL_COMPARE) }
        }
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
                tint = GsTheme.colors.textSecondary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/** Pin / archive / delete sheet lives in ui/components.ConversationActionsSheet (shared with Chats). */

/**
 * The compact account/More reveal: a native bottom sheet so the sidebar itself
 * never becomes a catalogue. Rows navigate and dismiss everything.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DrawerNavSheet(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = GsMotion.spaceM)
                .padding(bottom = GsMotion.spaceL),
            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceXS)
        ) {
            content()
        }
    }
}

@Composable
private fun SheetRow(
    title: String,
    icon: ImageVector?,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val colors = GsTheme.colors
    val rowInteraction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GsRadius.mdShape())
            .background(if (selected) colors.accentSoft else colors.appBackground.copy(alpha = 0f))
            .kineticPress(rowInteraction)
            .clickable(
                interactionSource = rowInteraction,
                indication = LocalIndication.current,
                role = Role.Button
            ) { onClick() }
            .padding(horizontal = GsMotion.spaceM, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DrawerLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = GsTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

/** The drawer's one hairline — structural separation between groups. */
@Composable
private fun DrawerDivider() {
    HorizontalDivider(color = GsTheme.colors.divider, thickness = 1.dp)
}

/**
 * One navigation row. [selected] marks the drawer's answer to "where am I":
 * a soft neutral container — quiet, readable, colourless.
 */
@Composable
private fun DrawerRow(
    title: String,
    icon: ImageVector? = null,
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
                tint = if (selected) colors.textPrimary else colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Spacer(Modifier.width(2.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) colors.textPrimary else colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private const val RECENT_LIMIT = 12

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
