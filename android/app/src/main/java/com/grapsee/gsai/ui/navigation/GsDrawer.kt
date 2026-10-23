package com.grapsee.gsai.ui.navigation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grapsee.gsai.data.AccountStore
import com.grapsee.gsai.data.local.ConversationEntity
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.components.ConversationActionsSheet
import com.grapsee.gsai.ui.components.GlassFrostedPanel
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.RefArt
import com.grapsee.gsai.ui.components.RefArtwork
import com.grapsee.gsai.ui.components.RefDivider
import com.grapsee.gsai.ui.components.RefIconTile
import com.grapsee.gsai.ui.components.gsConversationTitle
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.navigation.drawer.DESTINATIONS
import com.grapsee.gsai.ui.navigation.drawer.DrawerDestinationRow
import com.grapsee.gsai.ui.navigation.drawer.DrawerFixture
import com.grapsee.gsai.ui.navigation.drawer.DrawerFooter
import com.grapsee.gsai.ui.navigation.drawer.DrawerHeroRow
import com.grapsee.gsai.ui.navigation.drawer.DrawerIdentity
import com.grapsee.gsai.ui.navigation.drawer.DrawerMetrics
import com.grapsee.gsai.ui.navigation.drawer.DrawerRecent
import com.grapsee.gsai.ui.navigation.drawer.DrawerRecentRow
import com.grapsee.gsai.ui.navigation.drawer.DrawerSearchPill
import com.grapsee.gsai.ui.navigation.drawer.DrawerSectionLabel
import com.grapsee.gsai.ui.navigation.drawer.RECENT_LIMIT
import com.grapsee.gsai.ui.navigation.drawer.iconForTitle
import com.grapsee.gsai.ui.navigation.drawer.relativeTimeIso
import com.grapsee.gsai.ui.theme.GsHaptics
import com.grapsee.gsai.ui.theme.GsTheme
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * The navigation drawer, rebuilt to the sheet.
 *
 * WHAT CHANGED AND WHY
 * --------------------
 * The old panel was an opaque cream gradient with seven flat rows and no search
 * affordance (`SearchPill` was declared and never called). The sheet has a
 * different shape: a search pill, one warm "New chat" row, two labelled sections
 * whose rows carry discs and chevrons, chat rows with relative times, and a
 * footer with a gear. Those are not restyles of the old rows — they are a
 * different set of parts — so the body was replaced and the parts moved to
 * `ui/navigation/drawer/`, where each is measured in exactly one place
 * (`DrawerMetrics`) and gated against the sheet by `scripts/check_drawer_render.py`.
 *
 * The shell stayed: edge-swipe, the scrim, dismissal, the account sheet and the
 * long-press action sheet all already worked, and the plan's whole risk argument
 * was to keep them.
 *
 * THE PANEL IS NOT A BLUR OF HOME. Measured on the sheet, the panel's internal
 * high-frequency energy is 1.79 L with no card-shaped steps — a frosted bloom,
 * not a live blur of the page underneath. `GlassFrostedPanel` is exactly that
 * composite (frosted backdrop + translucency + rim), so the panel is drawn with
 * it rather than `Modifier.blur`, which would ghost the bento cards through a
 * surface the reference shows as opaque.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GsDrawerContent(
    selectedRoute: String?,
    onNavigate: (String) -> Unit,
    onClose: () -> Unit,
    /**
     * A capture needs four rows and a name without a database. Production passes
     * nothing; the harnesses pass a fixture, so no test ever writes to
     * ConversationStore — Home's render test asserts the store is empty, and a
     * seeding drawer test would fail it from a distance.
     */
    fixture: DrawerFixture? = null,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val storedName = AccountStore.displayName(context)
    val recentsFromStore by remember {
        runCatching { ServiceLocator.chat.activeConversations() }.getOrElse { flowOf(emptyList()) }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    var actionTarget by remember { mutableStateOf<ConversationEntity?>(null) }
    var accountSheetOpen by remember { mutableStateOf(false) }

    val identity = fixture?.identity ?: DrawerIdentity(
        name = storedName.ifBlank { "Your account" },
        workspace = "Personal workspace",
        initials = initialsFor(storedName),
    )
    val recents: List<DrawerRecent> = fixture?.recents ?: recentsFromStore
        .take(RECENT_LIMIT)
        .map { conversation ->
            val title = gsConversationTitle(conversation.title)
            DrawerRecent(
                id = conversation.id,
                title = title,
                timestamp = relativeTimeIso(conversation.updatedAt),
                icon = iconForTitle(title),
            )
        }

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

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(DrawerMetrics.panelRatio)
            .semantics { paneTitle = "Navigation drawer" },
    ) {
        GlassFrostedPanel(
            shape = RoundedCornerShape(bottomEnd = DrawerMetrics.cornerRadius),
            role = GsGlassRole.Modal,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize()) {
                DrawerHeader(onClose = onClose)

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    item {
                        Spacer(Modifier.height(18.dp))
                        DrawerSearchPill(onOpenSearch = {
                            onClose()
                            onNavigate(GsRoutes.CHAT_SEARCH)
                        })
                    }
                    item {
                        Spacer(Modifier.height(DrawerMetrics.pillToHero))
                        DrawerHeroRow(
                            label = "New chat",
                            onClick = {
                                onClose()
                                onNavigate(GsRoutes.chat(null))
                            },
                        )
                    }
                    item { DrawerSectionLabel("YOUR SPACE") }
                    items(DESTINATIONS) { destination ->
                        DrawerDestinationRow(
                            destination = destination,
                            selected = selectedRoute == destination.route,
                            onClick = {
                                onClose()
                                onNavigate(destination.route)
                            },
                        )
                    }
                    item {
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.padding(horizontal = DrawerMetrics.contentInset + 4.dp)) {
                            RefDivider()
                        }
                        DrawerSectionLabel("RECENT CHATS")
                    }
                    if (recents.isEmpty()) {
                        item {
                            Text(
                                text = "No conversations yet.",
                                style = MaterialTheme.typography.labelMedium,
                                color = DrawerMetrics.timestampInk,
                                modifier = Modifier.padding(
                                    start = DrawerMetrics.contentInset,
                                    top = 6.dp,
                                    bottom = 10.dp,
                                ),
                            )
                        }
                    } else {
                        items(recents) { recent ->
                            DrawerRecentRow(
                                recent = recent,
                                selected = selectedRoute == GsRoutes.chat(recent.id),
                                onClick = {
                                    onClose()
                                    onNavigate(GsRoutes.chat(recent.id))
                                },
                                onLongPress = {
                                    val target = recentsFromStore.firstOrNull { it.id == recent.id }
                                    if (target != null) {
                                        GsHaptics.longPress(haptics)
                                        actionTarget = target
                                    }
                                },
                            )
                        }
                    }
                    item { Spacer(Modifier.height(10.dp)) }
                }

                Box(Modifier.padding(horizontal = DrawerMetrics.contentInset + 4.dp)) {
                    RefDivider()
                }
                DrawerFooter(
                    identity = identity,
                    onAccount = { accountSheetOpen = true },
                    onSettings = { navigateFromSheet(GsRoutes.SETTINGS) },
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = 4.dp),
                )
            }
        }
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
            },
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

/**
 * The header: the product's orb, the wordmark and its subtitle, and the close
 * control. Its top inset is the safe area plus 4 dp (the sheet's orb starts
 * 62.7 pt from the frame top, i.e. 3.7 pt below a 59 pt safe area).
 */
@Composable
private fun DrawerHeader(onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(
                start = DrawerMetrics.contentInset + 4.dp,
                end = DrawerMetrics.contentInset,
                top = 4.dp,
            )
            .heightIn(min = DrawerMetrics.headerOrb + 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(DrawerMetrics.headerOrb)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFFFE9D5), Color(0xFFFFCDB0), Color(0xFFFFB9A0))
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            RefArtwork(art = RefArt.Bloom, size = 44.dp, contentDescription = "")
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = "Nexly",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = DrawerMetrics.wordmark,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = GsTheme.colors.textPrimary,
                maxLines = 1,
            )
            Text(
                text = "AI Companion",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = DrawerMetrics.subtitle
                ),
                color = DrawerMetrics.labelInk,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .size(DrawerMetrics.closeHit + 10.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClickLabel = "Close menu",
                    onClick = onClose,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                GsIcons.X,
                contentDescription = "Close menu",
                tint = GsTheme.colors.textPrimary,
                modifier = Modifier.size(DrawerMetrics.closeGlyph + 6.dp),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DrawerNavSheet(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
                .padding(bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
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
    onClick: () -> Unit,
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
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) RefIconTile(icon)
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = GsTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            GsIcons.ChevronRight,
            contentDescription = null,
            tint = GsTheme.colors.textTertiary,
            modifier = Modifier.size(16.dp),
        )
    }
}

private fun initialsFor(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return "GS"
    return words.take(2).map { it.first().uppercaseChar() }.joinToString("")
}
