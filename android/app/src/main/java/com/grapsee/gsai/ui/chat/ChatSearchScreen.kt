package com.grapsee.gsai.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.components.GsEmptyState
import com.grapsee.gsai.ui.components.GsInputBar
import com.grapsee.gsai.ui.components.GsListItem
import com.grapsee.gsai.ui.components.GsScreenScaffold
import com.grapsee.gsai.ui.components.GsSectionHeader
import com.grapsee.gsai.ui.theme.GsMotion
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.coroutines.delay

private data class ChatHit(
    val conversationId: String,
    val title: String,
    val snippet: String,
    val moment: String
)

private val searchFilters = listOf("This week", "Model", "Has files")

/**
 * Chat-scoped search over the on-device store — real hits from Room (titles +
 * message bodies) with honest edge states: a start-typing hint, a no-matches
 * empty state, and tap-through into the conversation. Store hiccups resolve
 * to "no matches", never an error.
 */
@Composable
fun ChatSearchScreen(
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf<String?>(null) }
    var hits by remember { mutableStateOf<List<ChatHit>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }

    val term = query.trim()
    LaunchedEffect(term, activeFilter) {
        if (term.length < 2) {
            hits = emptyList()
            searched = false
            return@LaunchedEffect
        }
        delay(220) // settle keystrokes before touching the store
        hits = searchChats(term, activeFilter)
        searched = true
    }

    GsScreenScaffold(title = "Search chats", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceM)
        ) {
            GsInputBar(
                value = query,
                onValueChange = { query = it },
                onSend = {},
                placeholder = "Search messages and chats…"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                searchFilters.forEach { filter ->
                    GsChip(
                        text = filter,
                        selected = activeFilter == filter,
                        onClick = { activeFilter = if (activeFilter == filter) null else filter }
                    )
                }
            }

            when {
                term.length < 2 -> GsEmptyState(
                    icon = Icons.Outlined.ManageSearch,
                    title = "Search your chats",
                    message = "Start typing to find any conversation or message on this device."
                )
                searched && hits.isEmpty() -> GsEmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = "No matches",
                    message = "Nothing matched \"$term\". Try a shorter word or different phrasing."
                )
                else -> {
                    GsSectionHeader("Results")
                    hits.forEach { hit ->
                        GsListItem(
                            title = hit.title,
                            subtitle = "${hit.snippet} · ${hit.moment}",
                            leading = {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Outlined.ChatBubbleOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            onClick = { onOpenConversation(hit.conversationId) }
                        )
                    }
                    Text(
                        text = "Search runs across every conversation on this device.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = GsMotion.spaceM)
                    )
                }
            }
        }
    }
}

/** Room-backed search; any store hiccup quietly reads as "no matches". */
private suspend fun searchChats(term: String, filter: String?): List<ChatHit> {
    return runCatching {
        val db = ServiceLocator.db
        val withinWeek = filter == "This week"
        val titleHits = db.conversationDao().searchByTitle(term)
            .filter { !withinWeek || recentInstant(it.updatedAt) }
            .map { convo ->
                ChatHit(convo.id, convo.title, "Chat title match", relativeMoment(convo.updatedAt))
            }
        val messageHits = db.messageDao().searchContent(term)
            .filter { !withinWeek || recentInstant(it.createdAt) }
            .mapNotNull { message ->
                val convo = db.conversationDao().getById(message.conversationId) ?: return@mapNotNull null
                ChatHit(
                    message.conversationId,
                    convo.title,
                    snippet(message.content, term),
                    relativeMoment(message.createdAt)
                )
            }
        (titleHits + messageHits).distinctBy { it.conversationId }.take(24)
    }.getOrElse { emptyList() }
}

private fun recentInstant(iso: String): Boolean = runCatching {
    val then = OffsetDateTime.parse(iso).toInstant()
    Duration.between(then, Instant.now()).toDays() < 7
}.getOrDefault(true)

private fun relativeMoment(iso: String): String = runCatching {
    val then = OffsetDateTime.parse(iso).toInstant()
    val minutes = Duration.between(then, Instant.now()).toMinutes()
    when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 60 * 24 -> "${minutes / 60}h ago"
        minutes < 60 * 24 * 7 -> "${minutes / 60 / 24}d ago"
        else -> "earlier"
    }
}.getOrDefault("earlier")

/** Window the match into a single readable line. */
private fun snippet(content: String, term: String): String {
    val clean = content.replace('\n', ' ').trim()
    val index = clean.lowercase().indexOf(term.lowercase())
    if (index < 0) return clean.take(80)
    val start = maxOf(0, index - 24)
    val end = minOf(clean.length, index + term.length + 48)
    val prefix = if (start > 0) "…" else ""
    val suffix = if (end < clean.length) "…" else ""
    return prefix + clean.substring(start, end).trim() + suffix
}
