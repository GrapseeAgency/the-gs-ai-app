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
import androidx.compose.foundation.rememberScrollState
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
 * The five chips the build can honour. The mock also draws "More"; there is no
 * sixth topic behind it, so it is not drawn — a chip that filters nothing is
 * the dead-control class this repo keeps deleting.
 */
private val historyChips = listOf("All", "Chats", "Research", "Ideas", "Planning")

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
    if (chip == null || chip == "All") return true
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
    var topic by remember { mutableStateOf<String?>(null) }
    var hits by remember { mutableStateOf<List<ChatHit>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val term = query.trim()
    LaunchedEffect(term) {
        if (term.length < 2) {
            hits = emptyList()
            searched = false
            return@LaunchedEffect
        }
        delay(220)
        hits = searchChats(term)
        searched = true
    }

    fun commitSearch() {
        if (term.length < 2) return
        scope.launch {
            hits = searchChats(term)
            searched = true
        }
    }

    // The chip narrows the visible hits as well as the list below, so tapping
    // one changes what is on screen instead of only repainting the chip.
    val visibleHits = hits.filter { matchesChip(it.title, topic) }

    RefSettingsPage(
        title = "Chat History & Search",
        subtitle = "Find, revisit, and manage your conversations",
        onBack = onBack
    ) {
        RefTextField(
            value = query,
            onValueChange = { query = it },
            // The mock draws ONE placeholder inside the field, with no label
            // above it; the label is what pushed the chips to the fold.
            placeholder = "Search your conversations…",
            leadingIcon = GsIcons.Search,
            imeAction = ImeAction.Search,
            onDone = { commitSearch() }
        )
        Spacer(Modifier.height(10.dp))

        // docs/reference/pending/chat-history-search.png draws six chips. The
        // labels are the mock's minus "More" (no sixth topic exists); the four
        // topic chips are KEYWORD-derived from the title (there is no topic
        // column in the schema) — approximate by construction, and recorded as
        // such in the reference README.
        // Five chips still exceed a 393pt row, so the row scrolls rather than
        // clipping the last chip off the right edge (measured in capture
        // 26-chat-history-search before this change).
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            historyChips.forEach { chip ->
                RefChip(
                    text = chip,
                    selected = topic == chip,
                    onClick = {
                        topic = if (topic == chip) null else chip
                    }
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        // Totals. `observeActiveWithPreview()` resolves each row's newest
        // message in the same query, so the count and the previews come from
        // one read instead of a walk of the transcript.
        val recent by ServiceLocator.db.conversationDao()
            .observeActiveWithPreview()
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
                    value = historyMoment(recent.firstOrNull()?.conversation?.updatedAt),
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
                } else if (recent.none { matchesChip(it.conversation.title, topic) }) {
                    RefInfoCard(
                        text = "No conversations in \"$topic\" yet. Tap All to see every chat.",
                        icon = GsIcons.MessageCircle,
                    )
                } else {
                    RefCard(role = GsGlassRole.CardLight) {
                        recent.filter { matchesChip(it.conversation.title, topic) }
                            .take(7).forEachIndexed { index, row ->
                            if (index > 0) RefRowDivider()
                            RecentChatRow(row.conversation, row.lastMessage) {
                                onOpenConversation(row.conversation.id)
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
                    HistoryAction(GsIcons.Bookmark, GsWellTone.Lilac, "Saved items", "Your saved answers", Modifier.weight(1f)) { onOpenSaved() }
                    HistoryAction(GsIcons.Download, GsWellTone.Blue, "Export Chat History", "Download your data", Modifier.weight(1f)) { onExport() }
                }
                Spacer(Modifier.height(14.dp))
                RefInfoCard(
                    text = "Start a new conversation — get back to what matters.",
                    icon = GsIcons.Sparkles,
                )
                Spacer(Modifier.height(10.dp))
            }
            searched && visibleHits.isEmpty() -> RefEmptyState(
                art = RefArt.Search,
                title = "No matches",
                message = if (topic != null) {
                    "Nothing matched \"$term\" in \"$topic\". Try All or a shorter word."
                } else {
                    "Nothing matched \"$term\". Try a shorter word or different phrasing."
                }
            )
            else -> {
                Text(
                    text = "${visibleHits.size} result${if (visibleHits.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = GsTheme.colors.textTertiary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
                RefCard {
                    visibleHits.forEachIndexed { index, hit ->
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
private suspend fun searchChats(term: String): List<ChatHit> {
    return runCatching {
        val db = ServiceLocator.db
        val match = ftsMatchQuery(term)
        val titleHits = (
            if (match != null) db.conversationDao().searchByTitleFts(match)
            else db.conversationDao().searchByTitle(term)
            )
            .map { convo ->
                ChatHit(convo.id, convo.title, "Chat title match", relativeMoment(convo.updatedAt))
            }
        val messageHits = (
            if (match != null) db.messageDao().searchContentFts(match)
            else db.messageDao().searchContent(term)
            )
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
                // The mock's copy is one line, but the real half-card is ~120dp
                // wide; wrapping keeps every word instead of shipping "Ne…".
                maxLines = 2,
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

/**
 * One line of the last message for a list row: the first PROSE line, markdown
 * flattened. A row that opened with "## The first three planets" followed by a
 * table read like a rendering bug, not a preview — so headings are stripped
 * and table rows are skipped rather than flattened into pipe noise.
 */
internal fun previewLine(raw: String?): String {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return "No messages yet"
    val firstProse = text.lineSequence()
        .map { line ->
            line.trim()
                .replace(Regex("^#{1,6}\\s*"), "")
                .replace(Regex("^[-*+]\\s+"), "")
                .replace(Regex("^>\\s*"), "")
                .replace(Regex("^```[A-Za-z0-9]*$"), "")
                .trim()
        }
        .firstOrNull { line ->
            line.isNotEmpty() && !line.startsWith("|") && !line.matches(Regex("^[-|: ]+$"))
        }
        ?: return "No messages yet"
    return firstProse
        .replace(Regex("[*_`|>]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(96)
}

/** One Recent Chats row: real title, real last-message preview, real timestamp. */
@Composable
private fun RecentChatRow(
    conversation: ConversationEntity,
    lastMessage: String?,
    onClick: () -> Unit,
) {
    val title = gsConversationTitle(conversation.title)
    // Glyph from a real signal only — pinned or not. There is no topic column
    // to draw the mock's per-topic icons from, and the query this list reads
    // excludes archived chats (they live in Manage History).
    val icon = if (conversation.pinned) GsIcons.Bookmark else GsIcons.MessageCircle
    val tone = if (conversation.pinned) GsWellTone.Mint else GsWellTone.Lilac
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
                // The mock draws the last message here; `observeActiveWithPreview`
                // supplies it, so the line is what was actually said — not a
                // repeated instruction invented to fill the row. Markdown is
                // flattened: a heading marker in a preview is not copy.
                previewLine(lastMessage),
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
