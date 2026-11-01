package com.grapsee.gsai.ui.chat

import androidx.compose.foundation.Image
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.grapsee.gsai.ui.components.gsGlass
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.R
import com.grapsee.gsai.data.local.ConversationEntity
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.components.ConversationActionsSheet
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.GsWellTone
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefEmptyState
import com.grapsee.gsai.ui.components.RefIconTile
import com.grapsee.gsai.ui.components.RefPage
import com.grapsee.gsai.ui.components.RefArt
import com.grapsee.gsai.ui.components.gsConversationTitle
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.navigation.drawer.drawerIcon
import com.grapsee.gsai.ui.theme.GsTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Archived Conversations (docs/reference/pending/archived-conversations.png).
 *
 * The mock's inventory, in its order: the totals card with the lotus art, the
 * archived rows — topic icon, title, "Archived • <date>", a Restore pill and a
 * per-row menu — the About card, and the Clear Archived action. Every row is a
 * real archived Room row; the count, the dates and the restores are measured,
 * never drawn.
 */
@Composable
fun ArchivedChatsScreen(
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit = {}
) {
    val colors = GsTheme.colors
    val scope = rememberCoroutineScope()
    val archivedFlow = remember {
        runCatching { ServiceLocator.chat.archivedConversations() }.getOrElse { flowOf(emptyList()) }
    }
    val archived by archivedFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var actionTarget by remember { mutableStateOf<ConversationEntity?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }
    var clearOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { ServiceLocator.chat.refreshConversations() }
    }

    fun restoreAll() {
        scope.launch {
            archived.forEach { entity ->
                runCatching { ServiceLocator.chat.setArchived(entity.id, false) }
            }
            runCatching { ServiceLocator.chat.refreshConversations() }
        }
    }

    fun clearArchived() {
        scope.launch {
            archived.forEach { entity ->
                runCatching { ServiceLocator.chat.delete(entity.id) }
            }
            runCatching { ServiceLocator.chat.refreshConversations() }
        }
    }

    RefPage(
        title = "Archived Conversations",
        subtitle = "View and restore your archived chats.",
        onBack = onBack,
        actions = {
            // The mock's kebab: the two whole-page actions, both real.
            Box(
                Modifier
                    .size(36.dp)
                    .gsKebabCircle()
                    .clickable(role = Role.Button, onClickLabel = "More options") { menuOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(GsIcons.EllipsisVertical, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
            }
        },
    ) {
        // Totals card — real count, the reference's own lotus bleeding off the
        // trailing edge.
        RefCard {
            Box(Modifier.fillMaxWidth().heightIn(min = 79.dp)) {
                Image(
                    painter = painterResource(R.drawable.ref_archived_lotus),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .height(87.dp),
                )
                Row(
                    Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 120.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RefIconTile(GsIcons.Archive, tint = GsWellTone.Lilac.ink, size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "${archived.size} Archived Conversation${if (archived.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.textPrimary,
                        )
                        Text(
                            "Keep your space tidy. Restore anytime.",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        }

        if (archived.isEmpty()) {
            RefEmptyState(
                art = RefArt.Archive,
                title = "Nothing archived yet",
                message = "Chats you archive from the drawer or list rest here until you bring them back."
            )
        } else {
            Spacer(Modifier.height(10.dp))
            RefCard {
                archived.forEachIndexed { index, entity ->
                    if (index > 0) com.grapsee.gsai.ui.components.RefDivider()
                    ArchivedRow(
                        entity = entity,
                        onOpen = { onOpenConversation(entity.id) },
                        onRestore = {
                            scope.launch {
                                runCatching { ServiceLocator.chat.setArchived(entity.id, false) }
                                runCatching { ServiceLocator.chat.refreshConversations() }
                            }
                        },
                        onMore = { actionTarget = entity }
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        // About card — the mock's explainer; the chevron opens the long form.
        RefCard {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = "About archived chats") { aboutOpen = true }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RefIconTile(GsIcons.Info, tint = colors.textSecondary, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "About Archived Chats",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                    )
                    Text(
                        "Archived conversations are hidden from your main chat list, but you can restore them anytime.",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                    )
                }
                Icon(GsIcons.ChevronRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(Modifier.height(12.dp))
        // Clear Archived — destructive, gated behind a confirm dialog.
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFFE4576B).copy(alpha = 0.10f))
                .border(1.dp, Color(0xFFE4576B).copy(alpha = 0.22f), RoundedCornerShape(20.dp))
                .clickable(role = Role.Button, onClickLabel = "Clear archived") { clearOpen = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(GsIcons.Trash2, contentDescription = null, tint = Color(0xFFE4576B), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Clear Archived",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFFE4576B),
                    )
                }
                Text(
                    "Permanently remove all archived conversations.",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
    }

    if (menuOpen) {
        AlertDialog(
            onDismissRequest = { menuOpen = false },
            title = { Text("Archived chats") },
            text = { Text("Bring every archived conversation back, or clear the shelf for good. Both apply to all ${archived.size} archived chats.") },
            confirmButton = {
                TextButton(onClick = { menuOpen = false; restoreAll() }) { Text("Restore all") }
            },
            dismissButton = {
                TextButton(onClick = { menuOpen = false; clearOpen = true }) { Text("Clear archived…") }
            },
        )
    }

    if (aboutOpen) {
        AlertDialog(
            onDismissRequest = { aboutOpen = false },
            title = { Text("About Archived Chats") },
            text = {
                Text(
                    "Archived conversations are hidden from your main chat list, but you can restore them anytime. " +
                        "Restoring puts the chat back exactly where it was — history, attachments and all. " +
                        "Clearing archived chats removes the transcripts and their messages from this device for good."
                )
            },
            confirmButton = { TextButton(onClick = { aboutOpen = false }) { Text("Got it") } },
        )
    }

    if (clearOpen) {
        AlertDialog(
            onDismissRequest = { clearOpen = false },
            title = { Text("Clear archived?") },
            text = { Text("This permanently removes all ${archived.size} archived conversations and their messages from this device. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { clearOpen = false; clearArchived() }) { Text("Clear archived") }
            },
            dismissButton = { TextButton(onClick = { clearOpen = false }) { Text("Cancel") } },
        )
    }

    val target = actionTarget
    if (target != null) {
        ConversationActionsSheet(
            title = gsConversationTitle(target.title),
            pinned = target.pinned,
            archiveLabel = "Unarchive",
            onDismiss = { actionTarget = null },
            onTogglePin = {
                actionTarget = null
                scope.launch { runCatching { ServiceLocator.chat.setPinned(target.id, !target.pinned) } }
            },
            onArchive = {
                actionTarget = null
                scope.launch { runCatching { ServiceLocator.chat.setArchived(target.id, false) } }
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

/**
 * One archived row, drawn to the mock: the topic tile the classifier picks,
 * the honest "Archived • <date>" line, a Restore pill and a per-row menu.
 */
@Composable
private fun ArchivedRow(
    entity: ConversationEntity,
    onOpen: () -> Unit,
    onRestore: () -> Unit,
    onMore: () -> Unit,
) {
    val colors = GsTheme.colors
    val title = gsConversationTitle(entity.title)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 74.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = title,
                onClick = onOpen
            )
            .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RefIconTile(drawerIcon(entity.title, entity.pinned), tint = archivedTone(entity.title), size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "Archived  •  ${ChatTime.longDate(entity.updatedAt)}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary
            )
        }
        // The mock's Restore pill: rimmed, with the restore glyph and the verb.
        Row(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.72f))
                .border(1.dp, colors.glassBorder, RoundedCornerShape(999.dp))
                .clickable(role = Role.Button, onClickLabel = "Restore", onClick = onRestore)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(GsIcons.ArchiveRestore, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Restore", style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
        }
        Spacer(Modifier.width(6.dp))
        Icon(
            GsIcons.EllipsisVertical,
            contentDescription = "More actions",
            tint = colors.textTertiary,
            modifier = Modifier
                .size(30.dp)
                .clickable(role = Role.Button, onClickLabel = "More actions", onClick = onMore)
                .padding(6.dp),
        )
    }
}

/** The mock's row tones: creative lilac, code peach, study mint. */
private fun archivedTone(title: String): Color = when (drawerTopicTone(title)) {
    "blue" -> GsWellTone.Peach.ink
    "mint" -> GsWellTone.Mint.ink
    else -> GsWellTone.Lilac.ink
}

private fun drawerTopicTone(title: String): String = when (com.grapsee.gsai.ui.navigation.drawer.drawerTopic(title)) {
    com.grapsee.gsai.ui.navigation.drawer.DrawerTopic.Code -> "blue"
    com.grapsee.gsai.ui.navigation.drawer.DrawerTopic.Planner,
    com.grapsee.gsai.ui.navigation.drawer.DrawerTopic.Data,
    com.grapsee.gsai.ui.navigation.drawer.DrawerTopic.Money,
    com.grapsee.gsai.ui.navigation.drawer.DrawerTopic.Review -> "mint"
    else -> "lilac"
}

private fun Modifier.gsKebabCircle(): Modifier =
    this.gsGlass(GsGlassRole.FloatingControl, CircleShape)

/** Shared relative-time formatter for chat rows (kept tiny, no external deps). */
object ChatTime {
    fun relative(iso: String): String = runCatching {
        val timestamp = java.time.OffsetDateTime.parse(iso)
        val elapsed = java.time.Duration.between(timestamp, java.time.OffsetDateTime.now())
        when {
            elapsed.toMinutes() < 1 -> "just now"
            elapsed.toHours() < 1 -> "${elapsed.toMinutes()}m ago"
            elapsed.toHours() < 24 -> "${elapsed.toHours()}h ago"
            elapsed.toDays() < 7 -> "${elapsed.toDays()}d ago"
            else -> timestamp.toLocalDate().toString()
        }
    }.getOrElse { "earlier" }

/** "2h ago" from an epoch millis — the shared rows' age. */
    fun ago(millis: Long): String {
        val elapsed = java.time.Duration.between(
            java.time.Instant.ofEpochMilli(millis),
            java.time.Instant.now(),
        )
        return when {
            elapsed.toMinutes() < 1 -> "just now"
            elapsed.toHours() < 1 -> "${elapsed.toMinutes()}m ago"
            elapsed.toHours() < 24 -> "${elapsed.toHours()}h ago"
            elapsed.toDays() < 7 -> "${elapsed.toDays()}d ago"
            else -> java.time.Instant.ofEpochMilli(millis).toString().take(10)
        }
    }

    /** "Apr 14, 2025" — the mock's date shape, from the row's real timestamp. */
    fun longDate(iso: String): String = runCatching {
        val timestamp = java.time.OffsetDateTime.parse(iso)
        timestamp.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy"))
    }.getOrElse { "earlier" }
}
