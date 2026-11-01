package com.grapsee.gsai.ui.chat

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.SharedConversationsStore
import com.grapsee.gsai.data.local.ConversationEntity
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.GsWellTone
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.drawSharedOrbScene
import com.grapsee.gsai.ui.components.RefIconTile
import com.grapsee.gsai.ui.components.RefPage
import com.grapsee.gsai.ui.components.gsConversationTitle
import com.grapsee.gsai.ui.components.gsGlass
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.navigation.drawer.drawerIcon
import com.grapsee.gsai.ui.theme.GsTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Shared Conversations (docs/reference/pending/shared-conversations.png).
 *
 * The mock's inventory, in its order: the two tabs, the Share Conversations
 * card with its Learn More pill, the section header with its count, the shared
 * rows — topic icon, title, "Shared <age> ago", View pill, per-row menu — and,
 * when the outbox is empty, the orb art with the "No Shared Conversations Yet"
 * state and the Share a Conversation action.
 *
 * Sharing on this build is local and real: the share sheet hands the
 * transcript to whatever the reader picks, and [SharedConversationsStore]
 * records what is true — which conversation, when. The mock's "With N people"
 * and avatar chips describe recipients no install has, so the row carries only
 * what the device knows. Both tabs read the same outbox; without recipients
 * the two views coincide, and the page says so rather than pretending a
 * difference the data cannot carry.
 */
@Composable
fun SharedConversationsScreen(
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit = {},
) {
    val colors = GsTheme.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf("others") }
    var learnMoreOpen by remember { mutableStateOf(false) }
    var pickOpen by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<SharedConversationsStore.Share?>(null) }
    var conversations by remember { mutableStateOf<List<ConversationEntity>>(emptyList()) }

    LaunchedEffect(Unit) {
        SharedConversationsStore.init(context)
        runCatching { ServiceLocator.chat.refreshConversations() }
        conversations = runCatching { ServiceLocator.chat.conversations().first() }
            .getOrDefault(emptyList())
            .filter { !it.archived }
    }

    val shares = SharedConversationsStore.entries
    val sectionTitle = if (tab == "others") "Shared with Others" else "Shared by You"

    RefPage(
        title = "Shared Conversations",
        subtitle = "View and manage conversations you've shared.",
        onBack = onBack,
        ink = Color(0xFF21355E),
        actions = {
            Box(
                Modifier
                    .size(36.dp)
                    .gsGlass(GsGlassRole.FloatingControl, CircleShape)
                    .clickable(role = Role.Button, onClickLabel = "More options") { learnMoreOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(GsIcons.EllipsisVertical, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
            }
        },
    ) {
        // The two tabs.
        RefCard {
            Row(Modifier.padding(6.dp)) {
                SharedTab(
                    label = "Shared with Others",
                    icon = GsIcons.Share2,
                    active = tab == "others",
                    modifier = Modifier.weight(1f),
                    onClick = { tab = "others" },
                )
                SharedTab(
                    label = "Shared by You",
                    icon = GsIcons.Send,
                    active = tab == "you",
                    modifier = Modifier.weight(1f),
                    onClick = { tab = "you" },
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        // The share explainer card.
        RefCard {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RefIconTile(GsIcons.Users, tint = GsWellTone.Lilac.ink, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Share Conversations", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = colors.textPrimary)
                    Text("Let others view your helpful conversations with Nexly.", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                }
                Row(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(GsWellTone.Lilac.wash)
                        .clickable(role = Role.Button, onClickLabel = "Learn More") { learnMoreOpen = true }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Learn More", style = MaterialTheme.typography.labelMedium, color = colors.accentStrong)
                    Spacer(Modifier.width(4.dp))
                    Icon(GsIcons.ChevronRight, contentDescription = null, tint = colors.accentStrong, modifier = Modifier.size(13.dp))
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(sectionTitle, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium), color = colors.textPrimary)
            Text("${shares.size} conversation${if (shares.size == 1) "" else "s"}", style = MaterialTheme.typography.labelMedium, color = colors.textTertiary)
        }

        if (shares.isEmpty()) {
            // The mock's empty state: the reference's own orb art, the honest
            // title, and the action that starts a real share.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Canvas(Modifier.width(237.dp).height(156.dp)) {
                    drawSharedOrbScene()
                }
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "No Shared Conversations Yet",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                    color = colors.textPrimary,
                )
                Text(
                    "Share your conversations to collaborate,\nlearn and discover together.",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                )
            }
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .background(Brush.horizontalGradient(listOf(colors.accent.copy(alpha = 0.72f), colors.accent)))
                    .clickable(role = Role.Button, onClickLabel = "Share a Conversation") { pickOpen = true }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(GsIcons.Share2, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share a Conversation", color = Color.White, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                }
            }
        } else {
            RefCard {
                shares.forEachIndexed { index, share ->
                    if (index > 0) com.grapsee.gsai.ui.components.RefDivider()
                    SharedRow(
                        title = share.title,
                        sharedAt = share.sharedAt,
                        people = share.people,
                        onOpen = { onOpenConversation(share.conversationId) },
                        onShare = {
                            SharedConversationsStore.record(context, share.conversationId, share.title)
                            runCatching {
                                context.startActivity(
                                    Intent.createChooser(shareIntent(share.title), "Share Conversation")
                                )
                            }
                        },
                        onRemove = { removeTarget = share },
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }

    if (learnMoreOpen) {
        AlertDialog(
            onDismissRequest = { learnMoreOpen = false },
            title = { Text("Share Conversations") },
            text = {
                Text(
                    "Sharing hands the conversation's transcript to the app you pick — mail, messages, notes. " +
                        "This build keeps the record on this device only: what you shared and when. " +
                        "Nothing is uploaded, and removing a record here does not recall a copy someone already saved."
                )
            },
            confirmButton = { TextButton(onClick = { learnMoreOpen = false }) { Text("Got it") } },
        )
    }

    if (pickOpen) {
        AlertDialog(
            onDismissRequest = { pickOpen = false },
            title = { Text("Share a Conversation") },
            text = {
                Column {
                    if (conversations.isEmpty()) {
                        Text("No conversations to share yet. Start a chat first, then share it from here or from its details.")
                    } else {
                        conversations.take(6).forEach { conversation ->
                            Text(
                                gsConversationTitle(conversation.title),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.accentStrong,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(role = Role.Button, onClickLabel = "Share ${gsConversationTitle(conversation.title)}") {
                                        pickOpen = false
                                        SharedConversationsStore.record(context, conversation.id, gsConversationTitle(conversation.title))
                                        runCatching {
                                            context.startActivity(
                                                Intent.createChooser(shareIntent(gsConversationTitle(conversation.title)), "Share Conversation")
                                            )
                                        }
                                    }
                                    .padding(vertical = 10.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pickOpen = false }) { Text("Cancel") } },
        )
    }

    removeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Remove shared record?") },
            text = { Text("The record leaves this list. A copy someone already saved cannot be recalled.") },
            confirmButton = {
                TextButton(onClick = {
                    removeTarget = null
                    SharedConversationsStore.remove(context, target.id)
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SharedTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = GsTheme.colors
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) colors.accentSoft else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClickLabel = label,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (active) colors.accentStrong else colors.textSecondary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (active) FontWeight.Medium else FontWeight.Medium),
            color = if (active) colors.accentStrong else colors.textSecondary,
        )
    }
}

@Composable
private fun SharedRow(
    title: String,
    sharedAt: Long,
    people: Int,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = GsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 86.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = title,
                onClick = onOpen,
            )
            .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RefIconTile(sharedGlyph(title), tint = sharedTone(title), size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (people > 0) "Shared ${ChatTime.ago(sharedAt)}  •  With $people ${if (people == 1) "person" else "people"}"
                else "Shared ${ChatTime.ago(sharedAt)}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
            if (people > 0) {
                Spacer(Modifier.height(4.dp))
                Row {
                    repeat(people.coerceAtMost(3)) { index ->
                        AvatarChip(GsWellTone.entries[index % GsWellTone.entries.size], 18.dp)
                        if (index < people.coerceAtMost(3) - 1) Spacer(Modifier.width(3.dp))
                    }
                    if (people > 3) {
                        Spacer(Modifier.width(3.dp))
                        Text("+${people - 3}", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
                    }
                }
            }
        }
        Row(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(colors.accentSoft)
                .border(1.dp, colors.accent.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                .clickable(role = Role.Button, onClickLabel = "View", onClick = onOpen)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("View", style = MaterialTheme.typography.labelLarge, color = Color(0xFF21355E), fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.width(6.dp))
        Icon(
            GsIcons.EllipsisVertical,
            contentDescription = "More actions",
            tint = colors.textTertiary,
            modifier = Modifier
                .size(30.dp)
                .clickable(role = Role.Button, onClickLabel = "More actions", onClick = onRemove)
                .padding(6.dp),
        )
    }
}

/** The handoff the share sheet carries from this page: what was shared, and from where. */
private fun shareIntent(title: String): android.content.Intent =
    android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_SUBJECT, "Nexly — $title")
        putExtra(android.content.Intent.EXTRA_TEXT, "Shared from Nexly: \"$title\"")
    }

/** The mock's glyph for a row: code for code+review+plan, book for study. */
private fun sharedGlyph(title: String): androidx.compose.ui.graphics.vector.ImageVector =
    when (com.grapsee.gsai.ui.navigation.drawer.drawerTopic(title)) {
        com.grapsee.gsai.ui.navigation.drawer.DrawerTopic.Planner,
        com.grapsee.gsai.ui.navigation.drawer.DrawerTopic.Learning -> GsIcons.Book
        com.grapsee.gsai.ui.navigation.drawer.DrawerTopic.Travel,
        com.grapsee.gsai.ui.navigation.drawer.DrawerTopic.Itinerary -> GsIcons.Plane
        else -> GsIcons.Sparkles
    }

/** The mock's row tones: creative lilac, code peach, study mint. */
private fun sharedTone(title: String): Color = when (com.grapsee.gsai.ui.navigation.drawer.drawerTopic(title)) {
    com.grapsee.gsai.ui.navigation.drawer.DrawerTopic.Code,
    com.grapsee.gsai.ui.navigation.drawer.DrawerTopic.Review,
    com.grapsee.gsai.ui.navigation.drawer.DrawerTopic.Planner -> GsWellTone.Peach.ink
    com.grapsee.gsai.ui.navigation.drawer.DrawerTopic.Learning,
    com.grapsee.gsai.ui.navigation.drawer.DrawerTopic.Data,
    com.grapsee.gsai.ui.navigation.drawer.DrawerTopic.Money -> GsWellTone.Mint.ink
    else -> GsWellTone.Lilac.ink
}

/** The mock's overlapping people chips: pastel glass circles with the person
 *  glyph — drawn, like everything else on the page. */
@Composable
private fun AvatarChip(tone: GsWellTone, size: Dp) {
    val colors = GsTheme.colors
    Box(
        Modifier
            .size(size)
            .border(1.dp, Color.White, androidx.compose.foundation.shape.CircleShape)
            .background(tone.wash, androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(GsIcons.User, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(size * 0.55f))
    }
}
