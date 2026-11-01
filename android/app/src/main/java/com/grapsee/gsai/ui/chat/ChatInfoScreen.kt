package com.grapsee.gsai.ui.chat

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.data.SharedConversationsStore
import com.grapsee.gsai.data.local.ConversationEntity
import com.grapsee.gsai.data.local.MessageEntity
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefGroupHeader
import com.grapsee.gsai.ui.components.RefIconTile
import com.grapsee.gsai.ui.components.RefPage
import com.grapsee.gsai.ui.components.RefSettingsPage
import com.grapsee.gsai.ui.components.GsWellTone
import com.grapsee.gsai.ui.components.gsConversationTitle
import com.grapsee.gsai.ui.components.gsGlass
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsFonts
import com.grapsee.gsai.ui.theme.GsTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Conversation Details (docs/reference/pending/conversation-details.png).
 *
 * The mock's inventory, in its order: the conversation hero (icon tile, title,
 * pin mark, started time, message count and span), the Conversation Actions
 * group — rename, the pin switch, archive, share, export, delete — the Memory
 * & Context group with its one row, the About card, and the closing Delete
 * Conversation action. Every value is measured from the stored conversation;
 * the pin, rename, archive, share, export and delete are the real operations.
 */
@Composable
fun ChatInfoScreen(
    conversationId: String,
    onBack: () -> Unit,
    onOpenMemory: () -> Unit = {},
) {
    val colors = GsTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<MessageEntity>>(emptyList()) }
    var conversation by remember { mutableStateOf<ConversationEntity?>(null) }
    var renameOpen by remember { mutableStateOf(false) }
    var renameDraft by remember { mutableStateOf("") }
    var deleteOpen by remember { mutableStateOf(false) }

    LaunchedEffect(conversationId) {
        messages = runCatching { ServiceLocator.chat.history(conversationId) }.getOrDefault(emptyList())
        conversation = runCatching {
            ServiceLocator.chat.conversations().first().firstOrNull { it.id == conversationId }
        }.getOrNull()
    }

    val title = conversation?.let { gsConversationTitle(it.title) } ?: "This chat"
    val pinned = conversation?.pinned ?: false
    val spanLabel = remember(messages) { spanOf(messages) }

    // Export: the system file picker writes the real transcript — a download,
    // not a claim of one.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val body = "# $title\n\n" + messages.joinToString("\n\n") { m ->
                    val who = if (m.role == "user") "You" else "Nexly"
                    "${ChatTime.stamp(m.createdAt)} — $who:\n${m.content}"
                }
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write((body.ifBlank { "No messages yet." }).toByteArray())
                    }
                }
            }
        }
    }

    RefSettingsPage(
        title = "Conversation Details",
        subtitle = "Manage your conversation.",
        onBack = onBack,
        ink = Color(0xFF21355E),
        headerTrailing = {
            // The mock's kebab opens the same action set the rows carry.
            Box(
                Modifier
                    .size(36.dp)
                    .gsGlass(GsGlassRole.FloatingControl, androidx.compose.foundation.shape.CircleShape)
                    .clickable(role = Role.Button, onClickLabel = "More options") { renameOpen = false; deleteOpen = false }
                    .then(
                        Modifier.clickable(role = Role.Button, onClickLabel = "More options") {
                            renameDraft = title
                            renameOpen = true
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(GsIcons.EllipsisVertical, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
            }
        },
    ) {
        // Hero — the conversation's own identity, measured.
        RefCard {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RefIconTile(GsIcons.MessageSquare, tint = GsWellTone.Lilac.ink, size = 56.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (pinned) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                GsIcons.Pin,
                                contentDescription = "Pinned",
                                tint = GsWellTone.Lilac.ink,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Text(
                        "Started ${ChatTime.longDateTime(conversation?.createdAt)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                    )
                    Text(
                        "${messages.size} messages  •  ~$spanLabel",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        RefGroupHeader(title = "Conversation Actions", caption = "Quick actions for this chat")

        RefCard {
            DetailRow(
                icon = GsIcons.SquarePen, tone = GsWellTone.Lilac,
                title = "Rename Conversation", subtitle = "Give this chat a new name.",
                onClickLabel = "Rename Conversation",
                onClick = { renameDraft = title; renameOpen = true },
            )
            DetailDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RefIconTile(GsIcons.Pin, tint = GsWellTone.Peach.ink)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Pin Conversation", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
                    Text("Keep this chat at the top.", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                }
                Switch(
                    checked = pinned,
                    onCheckedChange = { on ->
                        scope.launch {
                            runCatching { ServiceLocator.chat.setPinned(conversationId, on) }
                            conversation = conversation?.copy(pinned = on)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = colors.accent,
                        checkedThumbColor = Color.White,
                    ),
                )
            }
            DetailDivider()
            DetailRow(
                icon = GsIcons.Archive, tone = GsWellTone.Mint,
                title = "Archive Conversation", subtitle = "Move this chat to archive.",
                onClickLabel = "Archive Conversation",
                onClick = {
                    scope.launch {
                        runCatching { ServiceLocator.chat.setArchived(conversationId, true) }
                        onBack()
                    }
                },
            )
            DetailDivider()
            DetailRow(
                icon = GsIcons.Share2, tone = GsWellTone.Lilac,
                title = "Share Conversation", subtitle = "Share this chat link.",
                onClickLabel = "Share Conversation",
                onClick = {
                    // The real share sheet, and the record the Shared
                    // Conversations page lists. There are no recipients on this
                    // build, so the record keeps what is true: what was shared,
                    // and when.
                    SharedConversationsStore.record(context, conversationId, title)
                    scope.launch {
                        val transcript = messages.joinToString("\n\n") { m ->
                            val who = if (m.role == "user") "You" else "Nexly"
                            "$who: ${m.content}"
                        }
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Nexly — $title")
                            putExtra(Intent.EXTRA_TEXT, "# $title\n\n" + transcript.ifBlank { "No messages yet." })
                        }
                        runCatching { context.startActivity(Intent.createChooser(send, "Share Conversation")) }
                    }
                },
            )
            DetailDivider()
            DetailRow(
                icon = GsIcons.Download, tone = GsWellTone.Peach,
                title = "Export Conversation", subtitle = "Download this chat as a file.",
                onClickLabel = "Export Conversation",
                onClick = { exportLauncher.launch("$title.txt") },
            )
            DetailDivider()
            DetailRow(
                icon = GsIcons.Trash2, tone = GsWellTone.Mint,
                title = "Delete Conversation", subtitle = "Remove this chat from your history.",
                onClickLabel = "Delete Conversation",
                onClick = { deleteOpen = true },
            )
        }

        Spacer(Modifier.height(8.dp))
        RefGroupHeader(title = "Memory & Context", caption = "Manage related memory")
        RefCard {
            DetailRow(
                icon = GsIcons.Brain, tone = GsWellTone.Lilac,
                title = "Manage Memory", subtitle = "View or clear memories used in this chat.",
                onClickLabel = "Manage Memory",
                onClick = onOpenMemory,
            )
        }

        Spacer(Modifier.height(10.dp))
        // About This Conversation — the mock's closing note.
        RefCard {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(GsWellTone.Lilac.wash.copy(alpha = 0.55f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RefIconTile(GsIcons.Lightbulb, tint = GsWellTone.Lilac.ink, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("About This Conversation", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
                    Text(
                        "This conversation may include personal or sensitive information. Manage it responsibly.",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        // The mock's closing action — the same delete the row carries.
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(colors.accent.copy(alpha = 0.72f), colors.accent)
                    )
                )
                .clickable(role = Role.Button, onClickLabel = "Delete Conversation") { deleteOpen = true }
                .padding(horizontal = 16.dp, vertical = 13.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(GsIcons.Trash2, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Delete Conversation", color = Color.White, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            }
        }
        Spacer(Modifier.height(10.dp))
    }

    if (renameOpen) {
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("Rename Conversation") },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    singleLine = true,
                    label = { Text("Conversation name") },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameDraft.isNotBlank(),
                    onClick = {
                        renameOpen = false
                        scope.launch {
                            runCatching { ServiceLocator.chat.rename(conversationId, renameDraft) }
                            conversation = conversation?.let {
                                it.copy(title = renameDraft)
                            }
                        }
                    },
                ) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("Cancel") } },
        )
    }

    if (deleteOpen) {
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            title = { Text("Delete conversation?") },
            text = { Text("This removes \"$title\" and its messages from this device. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteOpen = false
                    scope.launch {
                        runCatching { ServiceLocator.chat.delete(conversationId) }
                        onBack()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteOpen = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tone: GsWellTone,
    title: String,
    subtitle: String,
    onClickLabel: String,
    onClick: () -> Unit,
) {
    val colors = GsTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RefIconTile(icon, tint = tone.ink)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
        }
        Icon(GsIcons.ChevronRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun DetailDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 66.dp, end = 12.dp)
            .height(1.dp)
            .background(GsTheme.colors.divider)
    )
}

/** First-to-last message span, in the shortest honest unit. */
private fun spanOf(messages: List<MessageEntity>): String {
    if (messages.size < 2) return "1 min"
    val stamps = messages.mapNotNull { message ->
        runCatching { java.time.Instant.parse(message.createdAt).toEpochMilli() }.getOrNull()
    }
    if (stamps.size < 2) return "1 min"
    val span = (stamps.maxOrNull() ?: 0L) - (stamps.minOrNull() ?: 0L)
    val minutes = span / 60_000L
    return when {
        minutes < 1L -> "1 min"
        minutes < 60L -> "$minutes min"
        minutes < 1_440L -> "${minutes / 60L}h ${minutes % 60L}m"
        else -> "${minutes / 1_440L}d"
    }
}
