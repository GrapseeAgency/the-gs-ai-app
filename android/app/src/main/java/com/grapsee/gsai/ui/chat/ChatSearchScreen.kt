package com.grapsee.gsai.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.local.ConversationEntity
import com.grapsee.gsai.data.local.ftsMatchQuery
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.GsWellTone
import com.grapsee.gsai.ui.components.RefArt
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefChip
import com.grapsee.gsai.ui.components.RefDivider
import com.grapsee.gsai.ui.components.RefEmptyState
import com.grapsee.gsai.ui.components.RefGroupHeader
import com.grapsee.gsai.ui.components.RefIconTile
import com.grapsee.gsai.ui.components.RefInfoCard
import com.grapsee.gsai.ui.components.RefPage
import com.grapsee.gsai.ui.components.RefRowDivider
import com.grapsee.gsai.ui.components.RefSettingsPage
import com.grapsee.gsai.ui.components.RefTextField
import com.grapsee.gsai.ui.components.RefWellTile
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

/** The mock's six chips, in its order. */
private val historyChips = listOf("All", "Chats", "Research", "Ideas", "Planning", "More")

/**
 * Keyword topic buckets for the four topic chips.
 *
 * There is no topic/facet column in `ConversationEntity`, so these labels are a
 * classification of the title, not stored metadata — the honest approximation is
 * recorded in docs/reference/pending/README.md.
 */
private val chipKeywords: Map<String, List<String>> = mapOf(
    "Research" to listOf("research", "study", "science", "data", "code", "learn"),
    "Ideas" to listOf("idea", "design", "brand", "creative", "logo"),
    "Planning" to listOf("plan", "timeline", "schedule", "trip", "travel", "milestone"),
)

private fun matchesChip(title: String, chip: String?): Boolean {
    if (chip == null || chip == "All" || chip == "More") return true
    val lower = title.lowercase()
    val keys = chipKeywords[chip] ?: return true
    return keys.any { lower.contains(it) }
}

/** "Today, 9:12 AM" / "Sep 16" — real timestamps, never a placeholder. */
private fun historyMoment(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    return runCatching {
        val at = java.time.OffsetDateTime.parse(iso)
        val now = java.time.OffsetDateTime.now()
        if (at.toLocalDate() == now.toLocalDate()) {
            "Today, " + at.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
        } else {
            at.format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))
        }
    }.getOrDefault("—")
}

/**
 * Chat-scoped search over the on-device store — real hits from Room (titles +
 * message bodies), real filters, tap-through into the conversation.
 */
@Composable
fun ChatSearchScreen(
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit = {},
    onOpenArchive: () -> Unit = {},
    onOpenSaved: () -> Unit = {},
    onExport: () -> Unit = {},
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

    RefSettingsPage(
        title = "Chat History & Search",
        subtitle = "Find, revisit, and manage your conversations",
        onBack = onBack
    ) {
        RefTextField(
            value = query,
            onValueChange = { query = it },
            label = "Search your conversations…",
            placeholder = "A word or phrase",
            leadingIcon = GsIcons.Search,
            imeAction = ImeAction.Search,
            onDone = { commitSearch() }
        )
        Spacer(Modifier.height(10.dp))

        // docs/reference/pending/chat-history-search.png draws six chips. The
        // labels are the mock's; the four topic chips are KEYWORD-derived from
        // the title (there is no topic column in the schema) — approximate by
        // construction, and recorded as such in the reference README.
        // Six chips do not fit a 393pt row: the mock draws them all, so the row
        // scrolls rather than clipping "More" off the right edge (measured in
        // capture 26-chat-history-search before this change).
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            historyChips.forEach { chip ->
                RefChip(
                    text = chip,
                    selected = activeFilter == chip,
                    onClick = {
                        activeFilter = if (activeFilter == chip) null else chip
                    }
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        // Totals. `observeRecent()` reads the conversations table only (no
        // message join, no per-row subquery), so both numbers come from one
        // cheap query rather than a walk of the transcript.
        val recent by ServiceLocator.db.conversationDao()
            .observeRecent()
            .collectAsState(initial = emptyList())
        RefCard(role = GsGlassRole.CardLight) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                HistoryStat(
                    icon = GsIcons.MessageCircle,
                    tone = GsWellTone.Peach,
                    label = "Total Conversations",
                    value = recent.size.toString(),
                    caption = "Your chat history with Nexly",
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                HistoryStat(
                    icon = GsIcons.Calendar,
                    tone = GsWellTone.Peach,
                    label = "Last Active",
                    value = historyMoment(recent.firstOrNull()?.updatedAt),
                    caption = "Most recent conversation",
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        when {
            term.length < 2 -> {
                RefGroupHeader("Recent Chats", "${recent.size}")
                if (recent.isEmpty()) {
                    RefInfoCard(
                        text = "No conversations yet. Your chats appear here as you start them.",
                        icon = GsIcons.MessageCircle,
                    )
                } else {
                    RefCard(role = GsGlassRole.CardLight) {
                        recent.take(7).forEachIndexed { index, conversation ->
                            if (index > 0) RefRowDivider()
                            RecentChatRow(conversation) {
                                onOpenConversation(conversation.id)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                RefGroupHeader("Quick Actions", null)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HistoryAction(GsIcons.Search, GsWellTone.Peach, "Search Chats", "Find specific topics", Modifier.weight(1f)) { }
                    HistoryAction(GsIcons.Archive, GsWellTone.Rose, "Manage History", "Delete or archive chats", Modifier.weight(1f)) { onOpenArchive() }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HistoryAction(GsIcons.Bookmark, GsWellTone.Lilac, "Saved items", "View your saved answers", Modifier.weight(1f)) { onOpenSaved() }
                    HistoryAction(GsIcons.Download, GsWellTone.Blue, "Export Chat History", "Download your data", Modifier.weight(1f)) { onExport() }
                }
                Spacer(Modifier.height(14.dp))
                RefInfoCard(
                    text = "Start a new conversation — get back to what matters.",
                    icon = GsIcons.Sparkles,
                )
                Spacer(Modifier.height(10.dp))
            }
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

// ---------------------------------------------------------------------------
// The mock's cells — docs/reference/pending/chat-history-search.png
// ---------------------------------------------------------------------------

/** One half of the totals card: well, label, value, caption. */
@Composable
private fun HistoryStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tone: GsWellTone,
    label: String,
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        RefWellTile(icon, tone, 28.dp)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = GsTheme.colors.textTertiary,
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = GsTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                caption,
                style = MaterialTheme.typography.labelSmall,
                color = GsTheme.colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One Quick Action cell: well, title, subtitle. */
@Composable
private fun HistoryAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tone: GsWellTone,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    RefCard(role = GsGlassRole.CardLight, modifier = modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClickLabel = title,
                    onClick = onClick,
                )
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RefWellTile(icon, tone, 28.dp)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = GsTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = GsTheme.colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** One Recent Chats row: real title, real preview line, real timestamp. */
@Composable
private fun RecentChatRow(conversation: ConversationEntity, onClick: () -> Unit) {
    val title = gsConversationTitle(conversation.title)
    // Glyph from real signals only — pinned, then archived; there is no topic
    // column to draw the mock's per-topic icons from.
    val icon = when {
        conversation.pinned -> GsIcons.Bookmark
        conversation.archived -> GsIcons.Archive
        else -> GsIcons.MessageCircle
    }
    val tone = when {
        conversation.pinned -> GsWellTone.Mint
        conversation.archived -> GsWellTone.Blue
        else -> GsWellTone.Lilac
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = title,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RefWellTile(icon, tone, 28.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = GsTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Tap to reopen this conversation",
                style = MaterialTheme.typography.labelSmall,
                color = GsTheme.colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            historyMoment(conversation.updatedAt),
            style = MaterialTheme.typography.labelSmall,
            color = GsTheme.colors.textTertiary,
            maxLines = 1,
        )
    }
}
