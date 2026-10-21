package com.grapsee.gsai.ui.chat

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.data.local.MessageEntity
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.components.RefArt
import com.grapsee.gsai.ui.components.RefArtwork
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefDivider
import com.grapsee.gsai.ui.components.RefPage
import com.grapsee.gsai.ui.components.RefRow
import com.grapsee.gsai.ui.components.RefSection
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * CHAT DETAILS (reference sheet: "Chat Info") — the identity card, the honest
 * stats for THIS thread (messages, span, response style), and the rows that
 * describe where the chat lives. Every number is measured from the stored
 * messages; nothing is decorative.
 */
@Composable
fun ChatInfoScreen(
    conversationId: String,
    onBack: () -> Unit,
) {
    val colors = GsTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<MessageEntity>>(emptyList()) }
    var title by remember { mutableStateOf("This chat") }

    LaunchedEffect(conversationId) {
        messages = runCatching { ServiceLocator.chat.history(conversationId) }.getOrDefault(emptyList())
        title = runCatching {
            ServiceLocator.chat.conversations().first()
                .firstOrNull { it.id == conversationId }?.title
        }.getOrNull().orEmpty().ifBlank { "This chat" }
    }

    val mode = SettingsStore.chatMode(conversationId)
    val local = SettingsStore.preferLocal
    val spanLabel = remember(messages) { spanOf(messages) }
    val responseStyle = when (mode) {
        "flash" -> "Fast"
        "thinking" -> "Reasoning"
        else -> "Auto"
    }

    RefPage(
        title = "Chat Info",
        subtitle = "Everything this conversation knows about itself.",
        onBack = onBack
    ) {
        // Identity — the same bloom the chat uses, never a second logo.
        RefCard(padding = PaddingValues(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RefArtwork(RefArt.Bloom, size = 84.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Nexly",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary
                    )
                    Text(
                        "AI Companion",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary
                    )
                    Text(
                        title,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textTertiary,
                        maxLines = 2
                    )
                }
            }
        }

        // Stats — measured, not invented.
        RefCard(padding = PaddingValues(vertical = 18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ChatStat("${messages.size}", "Messages")
                ChatStat(spanLabel, "Span")
                ChatStat(responseStyle, "Response style")
            }
        }

        RefSection(label = "This chat") {
            RefRow(
                title = "Chat name",
                subtitle = title,
                icon = GsIcons.SquarePen
            )
            RefDivider()
            RefRow(
                title = "Model",
                subtitle = if (local) "On-device engine" else "Nexly Cloud",
                icon = GsIcons.Cpu
            )
            RefDivider()
            RefRow(
                title = "Chat mode",
                subtitle = when (mode) {
                    "flash" -> "Fast — immediate, concise answers"
                    "thinking" -> "Reasoning — spends longer on hard problems"
                    else -> "Auto — chooses per question"
                },
                icon = GsIcons.Brain
            )
            RefDivider()
            RefRow(
                title = "Export chat",
                subtitle = "Send the transcript as text",
                icon = GsIcons.FileText,
                onClickLabel = "Export chat",
                onClick = {
                    scope.launch {
                        val transcript = messages.joinToString("\n\n") { m ->
                            val who = if (m.role == "user") "You" else "Nexly"
                            "$who: ${m.content}"
                        }
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Nexly — $title")
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "# $title\n\n" + transcript.ifBlank { "No messages yet." }
                            )
                        }
                        runCatching {
                            context.startActivity(Intent.createChooser(send, "Export chat"))
                        }
                    }
                }
            )
        }

        Text(
            "Stats come from the messages stored on this device.",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
            modifier = Modifier.padding(top = 8.dp, start = 6.dp)
        )
    }
}

@Composable
private fun ChatStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = GsTheme.colors.textPrimary
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = GsTheme.colors.textSecondary
        )
    }
}

/** First-to-last message span, in the shortest honest unit. */
private fun spanOf(messages: List<MessageEntity>): String {
    if (messages.size < 2) return "—"
    val stamps = messages.mapNotNull { message ->
        runCatching { java.time.Instant.parse(message.createdAt).toEpochMilli() }.getOrNull()
    }
    if (stamps.size < 2) return "—"
    val span = (stamps.maxOrNull() ?: 0L) - (stamps.minOrNull() ?: 0L)
    if (span <= 0L) return "—"
    val minutes = span / 60_000L
    return when {
        minutes < 1L -> "<1m"
        minutes < 60L -> "${minutes}m"
        minutes < 1_440L -> "${minutes / 60L}h ${minutes % 60L}m"
        else -> "${minutes / 1_440L}d"
    }
}
