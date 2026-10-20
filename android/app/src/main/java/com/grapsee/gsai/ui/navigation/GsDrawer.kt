package com.grapsee.gsai.ui.navigation

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.data.AccountStore
import com.grapsee.gsai.data.local.ConversationEntity
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.components.ConversationActionsSheet
import com.grapsee.gsai.ui.components.RefAvatar
import com.grapsee.gsai.ui.components.RefDivider
import com.grapsee.gsai.ui.components.RefIconTile
import com.grapsee.gsai.ui.components.RefPrimaryButton
import com.grapsee.gsai.ui.components.gsConversationTitle
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsHaptics
import com.grapsee.gsai.ui.theme.GsTheme
import com.grapsee.gsai.ui.theme.kineticPress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * The history panel: identity, one search affordance, one primary action, the
 * real recents, and a footer that opens the account sheet.
 *
 * Restyled in the reference pass. The old drawer painted its own peach
 * gradient over the app background and used Material surface roles for the
 * rows; this one is a cream-glass panel over the shared bloom, with the same
 * 40dp pale tiles and 22dp radii as every other page. No new destinations:
 * each row was already wired.
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
    val context = LocalContext.current
    val storedName = AccountStore.displayName(context)
    val storedEmail = AccountStore.email(context)
    val recents by remember {
        runCatching { ServiceLocator.chat.activeConversations() }.getOrElse { flowOf(emptyList()) }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    var actionTarget by remember { mutableStateOf<ConversationEntity?>(null) }
    var accountSheetOpen by remember { mutableStateOf(false) }

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
            .width(308.dp)
            .clip(RoundedCornerShape(topEnd = 30.dp, bottomEnd = 30.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        GsTheme.colors.accentSoft.copy(alpha = 0.55f),
                        GsTheme.colors.navSurface,
                        GsTheme.colors.navSurface
                    )
                )
            )
            .semantics { paneTitle = "Navigation drawer" }
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp)
    ) {
        Spacer(Modifier.height(10.dp))

        // Identity first: the panel belongs to a person, not a route map.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClickLabel = "Account options"
                ) { accountSheetOpen = true }
                .semantics { contentDescription = "Account options" }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RefAvatar(initials = initialsFor(storedName), size = 46.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    storedName.ifBlank { "GS account" },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = GsTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    storedEmail.ifBlank { "Signed in on this device" },
                    style = MaterialTheme.typography.labelSmall,
                    color = GsTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                GsIcons.ChevronDown,
                contentDescription = null,
                tint = GsTheme.colors.textTertiary,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        SearchPill(
            onClick = {
                onClose()
                onNavigate(GsRoutes.CHAT_SEARCH)
            }
        )

        Spacer(Modifier.height(10.dp))
        RefPrimaryButton(
            label = "New chat",
            onClick = {
                onClose()
                onNavigate(GsRoutes.chat(null))
            },
            icon = GsIcons.SquarePen
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            DrawerLabel("Recent")
            if (recents.isEmpty()) {
                Text(
                    text = "No conversations yet. The first one starts above.",
                    style = MaterialTheme.typography.labelMedium,
                    color = GsTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            } else {
                recents.take(RECENT_LIMIT).forEach { conversation ->
                    RecentRow(
                        conversation = conversation,
                        selected = selectedRoute == GsRoutes.chat(conversation.id),
                        onOpen = {
                            onClose()
                            onNavigate(GsRoutes.chat(conversation.id))
                        },
                        onActions = {
                            GsHaptics.longPress(haptics)
                            actionTarget = conversation
                        }
                    )
                }
            }

            DrawerLabel("Go to")
            DrawerRow(
                title = "All chats",
                icon = GsIcons.Inbox,
                selected = selectedRoute == GsRoutes.CHATS
            ) {
                onClose(); onNavigate(GsRoutes.CHATS)
            }
            DrawerRow(
                title = "Voice",
                icon = GsIcons.AudioLines,
                selected = selectedRoute == GsRoutes.VOICE
            ) {
                onClose(); onNavigate(GsRoutes.VOICE)
            }
            DrawerRow(
                title = "Travel agent",
                icon = GsIcons.Compass,
                selected = selectedRoute == GsRoutes.TRAVEL_AGENT
            ) {
                onClose(); onNavigate(GsRoutes.TRAVEL_AGENT)
            }
            DrawerRow(
                title = "Settings",
                icon = GsIcons.Settings,
                selected = selectedRoute == GsRoutes.SETTINGS
            ) {
                onClose(); onNavigate(GsRoutes.SETTINGS)
            }
            Spacer(Modifier.height(12.dp))
        }

        RefDivider(startIndent = 4.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClickLabel = "Account options"
                ) { accountSheetOpen = true }
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                GsIcons.UserRound,
                contentDescription = null,
                tint = GsTheme.colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Account and preferences",
                style = MaterialTheme.typography.labelMedium,
                color = GsTheme.colors.textSecondary,
                modifier = Modifier.weight(1f)
            )
            Icon(
                GsIcons.ChevronRight,
                contentDescription = null,
                tint = GsTheme.colors.textTertiary,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
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

@Composable
private fun SearchPill(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(GsTheme.colors.glassSurface.copy(alpha = 0.72f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = "Search chats",
                onClick = onClick
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            GsIcons.Search,
            contentDescription = null,
            tint = GsTheme.colors.textTertiary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            "Search your chats",
            style = MaterialTheme.typography.labelMedium,
            color = GsTheme.colors.textTertiary
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentRow(
    conversation: ConversationEntity,
    selected: Boolean,
    onOpen: () -> Unit,
    onActions: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) GsTheme.colors.accentSoft.copy(alpha = 0.85f)
                else Color.Transparent
            )
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onOpen,
                onLongClick = onActions
            )
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (selected) GsTheme.colors.accent else Color.Transparent)
        )
        Text(
            text = gsConversationTitle(conversation.title),
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.5.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = if (selected) GsTheme.colors.accentStrong else GsTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (conversation.pinned) {
            Icon(
                GsIcons.Star,
                contentDescription = "Pinned",
                tint = GsTheme.colors.textTertiary,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

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
                .padding(horizontal = 14.dp)
                .padding(bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
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
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) GsTheme.colors.accentSoft else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = title,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) RefIconTile(icon)
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = GsTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(
            GsIcons.ChevronRight,
            contentDescription = null,
            tint = GsTheme.colors.textTertiary,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun DrawerLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.2.sp
        ),
        color = GsTheme.colors.textTertiary,
        modifier = Modifier.padding(start = 10.dp, top = 18.dp, bottom = 6.dp)
    )
}

@Composable
private fun DrawerRow(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) GsTheme.colors.accentSoft.copy(alpha = 0.85f) else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = title,
                onClick = onClick
            )
            .semantics { this.selected = selected }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RefIconTile(icon, tint = if (selected) GsTheme.colors.accentStrong else GsTheme.colors.textSecondary)
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            ),
            color = if (selected) GsTheme.colors.accentStrong else GsTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private const val RECENT_LIMIT = 12

private fun initialsFor(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return "GS"
    return words.take(2).map { it.first().uppercaseChar() }.joinToString("")
}
