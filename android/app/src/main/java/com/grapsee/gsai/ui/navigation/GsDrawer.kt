package com.grapsee.gsai.ui.navigation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsHaptics
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.GsRadius
import com.grapsee.gsai.ui.theme.GsTheme
import com.grapsee.gsai.ui.theme.kineticPress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * DESIGN PHASE 2 drawer — the Dola spirit, humbler. A CHAT HISTORY PANEL,
 * not a route directory. Structure (one visual unit per group, groups
 * separated by spacing, never borders):
 *
 *  1. TOP BAR — the search pill (calm, leading) and the one primary action
 *     of this screen: New chat (the accent). Nothing else competes.
 *  2. RECENT — the visual heart, weighted to fill: live conversations, tap
 *     to open, long-press for pin / rename / archive / delete. Genuine
 *     empty state. "All chats" is the quiet overflow row.
 *  3. FOOTER — the account row, pinned to the bottom like the reference
 *     app: identity lives where the thumb rests, never above the content.
 *
 * REDUCTION (design phase 2, screen 16): the More sheet is GONE. Explore,
 * Create, Projects, Library and Assistants were static demo surfaces —
 * sample conversations, invented assistants, fake collections — exactly the
 * "system design tool" padding the operator rejected. Voice stays: it is a
 * real, wired capability, promoted to its own drawer row. The underlying
 * composables remain routable for now but no navigation surface advertises
 * them; a later cleanup pass deletes the files.
 *
 * Icons are Lucide via GsIcons — one family, one weight, neutral containers,
 * no decorative glyphs (RULE ZERO).
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

    // Sheet rows dismiss their own sheet first, then close the drawer and
    // navigate — no stuck bottom sheet over the destination.
    val navigateFromSheet: (String) -> Unit = { route ->
        accountSheetOpen = false
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
            .padding(horizontal = GsMotion.spaceM)
    ) {
        Spacer(Modifier.height(GsMotion.spaceM))

        // 1 · Top bar — search pill + the screen's one primary action.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
        ) {
            val searchInteraction = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(GsRadius.pill)
                    .background(GsTheme.colors.raisedSurface)
                    .kineticPress(searchInteraction)
                    .clickable(
                        interactionSource = searchInteraction,
                        indication = LocalIndication.current,
                        role = Role.Button,
                        onClickLabel = "Search chats"
                    ) {
                        onClose()
                        onNavigate(GsRoutes.CHAT_SEARCH)
                    }
                    .padding(horizontal = GsMotion.spaceM),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
            ) {
                Icon(
                    GsIcons.Search,
                    contentDescription = null,
                    tint = GsTheme.colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    "Search",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GsTheme.colors.textSecondary
                )
            }
            GsButton(
                label = "New chat",
                leadingIcon = GsIcons.SquarePen,
                onClick = {
                    onClose()
                    onNavigate(GsRoutes.chat(null))
                }
            )
        }

        Spacer(Modifier.height(GsMotion.spaceM))

        // 2 · Recent — the heart, weighted to fill. Live history only; when
        // the store is empty the panel says so honestly, nothing invented.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            DrawerLabel("Recent")
            Spacer(Modifier.height(GsMotion.spaceS))
            if (recents.isEmpty()) {
                Text(
                    "No conversations yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GsTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = GsMotion.spaceM, vertical = GsMotion.spaceS)
                )
            } else {
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
            DrawerRow("All chats", icon = GsIcons.Inbox, selected = selectedRoute == GsRoutes.CHATS) {
                onClose(); onNavigate(GsRoutes.CHATS)
            }
            Spacer(Modifier.height(GsMotion.spaceXS))
            // Voice — the one non-chat capability with a real, wired screen
            // earns its row. The demo surfaces are reduced out (screen 16).
            DrawerRow("Voice", icon = GsIcons.AudioLines, selected = selectedRoute == GsRoutes.VOICE) {
                onClose(); onNavigate(GsRoutes.VOICE)
            }
        }

        Spacer(Modifier.height(GsMotion.spaceS))

        // 3 · Footer — the account row, pinned where the thumb rests.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GsRadius.mdShape())
                .kineticPress()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClickLabel = "Account options"
                ) { accountSheetOpen = true }
                .semantics { contentDescription = "Account options" }
                .padding(horizontal = GsMotion.spaceS, vertical = GsMotion.spaceS),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    // Real identity only: the stored name, else a neutral
                    // label — never an invented "Admin".
                    storedName.ifBlank { "GS account" },
                    style = MaterialTheme.typography.titleSmall,
                    color = GsTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    // The stored email, else a neutral line — never a
                    // fabricated address.
                    storedEmail.ifBlank { "Signed in" },
                    style = MaterialTheme.typography.labelMedium,
                    color = GsTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                GsIcons.ChevronRight,
                contentDescription = null,
                tint = GsTheme.colors.textSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.height(GsMotion.spaceS))
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
            SheetRow("Profile", GsIcons.UserRound) { navigateFromSheet(GsRoutes.PROFILE) }
            SheetRow("Settings", GsIcons.Settings) { navigateFromSheet(GsRoutes.SETTINGS) }
            SheetRow("Notifications", GsIcons.Bell) { navigateFromSheet(GsRoutes.NOTIFICATIONS) }
            SheetRow("Billing", GsIcons.CreditCard) { navigateFromSheet(GsRoutes.BILLING) }
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
                GsIcons.Star,
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
            .padding(horizontal = GsMotion.spaceS, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
    ) {
        if (icon != null) {
            // One container spec: 32dp rounded square, neutral fill, icon
            // inside — the reference app's row rhythm, monochrome.
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(GsRadius.mdShape())
                    .background(colors.raisedSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
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
            color = colors.textPrimary,
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
