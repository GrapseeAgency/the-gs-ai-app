package com.grapsee.gsai.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.local.ftsMatchQuery
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.components.RefArt
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefChip
import com.grapsee.gsai.ui.components.RefDivider
import com.grapsee.gsai.ui.components.RefEmptyState
import com.grapsee.gsai.ui.components.RefIconTile
import com.grapsee.gsai.ui.components.RefPage
import com.grapsee.gsai.ui.components.RefTextField
import com.grapsee.gsai.ui.components.gsConversationTitle
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsTheme
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class ChatHit(
    val conversationId: String,
    val title: String,
    val snippet: String,
    val moment: String
)

/**
 * The filters that are REAL. "Model" was deleted because nothing read it;
 * "This week" and "Has files" change the query for real.
 */
private val searchFilters = listOf("This week", "Has files")

/**
 * Chat-scoped search over the on-device store — real hits from Room (titles +
 * message bodies), real filters, tap-through into the conversation.
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
    val scope = rememberCoroutineScope()

    val term = query.trim()
    LaunchedEffect(term, activeFilter) {
        if (term.length < 2) {
            hits = emptyList()
            searched = false
            return@LaunchedEffect
        }
        delay(220)
        hits = searchChats(term, activeFilter)
        searched = true
    }

    fun commitSearch() {
        if (term.length < 2) return
        scope.launch {
            hits = searchChats(term, activeFilter)
            searched = true
        }
    }

    RefPage(
        title = "Search",
        subtitle = "Titles and message text, on this device.",
        onBack = onBack
    ) {
        RefTextField(
            value = query,
            onValueChange = { query = it },
            label = "What are you looking for?",
            placeholder = "A word or phrase",
            leadingIcon = GsIcons.TextSearch,
            imeAction = ImeAction.Search,
            onDone = { commitSearch() }
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            searchFilters.forEach { filter ->
                RefChip(
                    text = filter,
                    selected = activeFilter == filter,
                    onClick = { activeFilter = if (activeFilter == filter) null else filter }
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        when {
            term.length < 2 -> RefEmptyState(
                art = RefArt.Search,
                title = "Search your chats",
                message = "Type at least two characters. Every conversation and message on this device is searched."
            )
            searched && hits.isEmpty() -> RefEmptyState(
                art = RefArt.Search,
                title = "No matches",
                message = if (activeFilter != null) {
                    "Nothing matched \"$term\" with \"$activeFilter\" on. Try another filter or a shorter word."
                } else {
                    "Nothing matched \"$term\". Try a shorter word or different phrasing."
                }
            )
            else -> {
                Text(
                    text = "${hits.size} result${if (hits.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = GsTheme.colors.textTertiary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
                RefCard {
                    hits.forEachIndexed { index, hit ->
                        if (index > 0) RefDivider()
                        HitRow(hit) { onOpenConversation(hit.conversationId) }
                    }
                }
                Text(
                    text = "Search runs across every conversation on this device. Nothing is sent to a server.",
                    style = MaterialTheme.typography.labelSmall,
                    color = GsTheme.colors.textTertiary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun HitRow(hit: ChatHit, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = gsConversationTitle(hit.title),
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RefIconTile(GsIcons.MessageCircle, tint = GsTheme.colors.textSecondary)
        Column(Modifier.weight(1f)) {
            Text(
                gsConversationTitle(hit.title),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = GsTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${hit.snippet} · ${hit.moment}",
                style = MaterialTheme.typography.labelMedium,
                color = GsTheme.colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            GsIcons.ChevronRight,
            contentDescription = null,
            tint = GsTheme.colors.textTertiary,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** Room-backed search; any store hiccup quietly reads as "no matches". */
private suspend fun searchChats(term: String, filter: String?): List<ChatHit> {
    return runCatching {
        val db = ServiceLocator.db
        val withinWeek = filter == "This week"
        val filesOnly = filter == "Has files"
        val match = ftsMatchQuery(term)
        val titleHits = if (filesOnly) {
            emptyList()
        } else {
            (
                if (match != null) db.conversationDao().searchByTitleFts(match)
                else db.conversationDao().searchByTitle(term)
                )
                .filter { !withinWeek || recentInstant(it.updatedAt) }
                .map { convo ->
                    ChatHit(convo.id, convo.title, "Chat title match", relativeMoment(convo.updatedAt))
                }
        }
        val messageHits = (
            if (match != null) db.messageDao().searchContentFts(match)
            else db.messageDao().searchContent(term)
            )
            .filter { !filesOnly || hasFiles(it.attachments) }
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

private fun hasFiles(attachmentsJson: String): Boolean {
    val t = attachmentsJson.trim()
    return t.isNotEmpty() && t != "[]" && t != "none"
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
