package com.grapsee.gsai.ui.chat

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.data.DataExport
import com.grapsee.gsai.data.local.ConversationEntity
import com.grapsee.gsai.data.local.ConversationPreviewRow
import com.grapsee.gsai.data.local.ConversationTotals
import com.grapsee.gsai.data.local.ftsMatchQuery
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.components.ConversationActionsSheet
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
import com.grapsee.gsai.ui.components.RefRowDivider
import com.grapsee.gsai.ui.components.RefSettingsPage
import com.grapsee.gsai.ui.components.RefTextField
import com.grapsee.gsai.ui.components.RefToggleRow
import com.grapsee.gsai.ui.components.RefWellTile
import com.grapsee.gsai.ui.components.gsConversationTitle
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.navigation.drawer.DrawerTopic
import com.grapsee.gsai.ui.navigation.drawer.drawerTopic
import com.grapsee.gsai.ui.theme.GsTheme
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ChatHit(
    val conversationId: String,
    val title: String,
    val snippet: String,
    val moment: String,
    val archived: Boolean,
    val hasFiles: Boolean,
    val topic: DrawerTopic,
)

/** The mock's six chips. `More` is a sheet of real facets, not a topic. */
private val historyChips = listOf("All", "Chats", "Research", "Ideas", "Planning", "More")

/**
 * The four topic labels mapped onto the SHIPPED classifier (`drawerTopic`),
 * never onto an invented column. The mapping is a bucket, so it is
 * approximate by construction — recorded in docs/reference/pending/README.md.
 *
 * `Chats` is the classifier's own fallback bucket (general chats), which makes
 * it different from `All`; if both meant everything, one of them would be a
 * dead chip.
 */
private val chipTopics: Map<String, Set<DrawerTopic>> = mapOf(
    "Chats" to setOf(DrawerTopic.Chat),
    "Research" to setOf(
        DrawerTopic.Science, DrawerTopic.Learning, DrawerTopic.Data, DrawerTopic.Code,
    ),
    "Ideas" to setOf(DrawerTopic.Ideas, DrawerTopic.Design, DrawerTopic.Writing),
    "Planning" to setOf(DrawerTopic.Plan, DrawerTopic.Work, DrawerTopic.Money, DrawerTopic.Travel),
)

/** Title -> topic, process-wide. A title's topic never changes, so the
 *  classifier runs once per distinct headline, off the main thread. */
private val topicCache = ConcurrentHashMap<String, DrawerTopic>()

private fun topicOf(title: String): DrawerTopic =
    topicCache.getOrPut(title) { drawerTopic(title) }

private fun chipAccepts(chip: String, topic: DrawerTopic): Boolean {
    if (chip == "All" || chip == "More") return true
    val accepted = chipTopics[chip] ?: return true
    return topic in accepted
}

/** "Today, 9:12 AM" / "Sep 16" — real timestamps, never a placeholder. */
private fun historyMoment(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    return runCatching {
        val at = OffsetDateTime.parse(iso)
        val now = OffsetDateTime.now()
        if (at.toLocalDate() == now.toLocalDate()) {
            "Today, " + at.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
        } else {
            at.format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))
        }
    }.getOrDefault("—")
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

private fun hasFiles(attachmentsJson: String): Boolean {
    val t = attachmentsJson.trim()
    return t.isNotEmpty() && t != "[]" && t != "none"
}

/**
 * Chat History & Search — the supplied reference rebuilt as one page:
 * search + real filters, the totals card, ≤7 recent rows with their real last
 * message and row menu, the 2×2 Quick Actions and the new-chat CTA.
 */
@Composable
fun ChatSearchScreen(
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit = {},
    onOpenArchive: () -> Unit = {},
    onOpenSaved: () -> Unit = {},
    onViewAllChats: () -> Unit = {},
    onNewChat: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = ServiceLocator.db.conversationDao()

    var query by remember { mutableStateOf("") }
    var chip by remember { mutableStateOf("All") }
    var archivedOnly by remember { mutableStateOf(false) }
    var filesOnly by remember { mutableStateOf(false) }
    var facetsOpen by remember { mutableStateOf(false) }
    var hits by remember { mutableStateOf<List<ChatHit>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<ConversationEntity?>(null) }
    val searchFocus = remember { FocusRequester() }

    val export = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            val ok = DataExport.write(context, uri)
            Toast.makeText(
                context,
                if (ok) "Export written" else "Could not write the export",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

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

    // Totals come from one aggregate query, NOT from the preview list's size.
    val totalsFlow = remember { dao.observeTotals() }
    val totals by totalsFlow
        .collectAsState(initial = ConversationTotals(0, null))

    // The list follows the Archived facet; Has files is filtered below.
    val recentFlow = remember(archivedOnly) {
        if (archivedOnly) dao.observeArchivedWithPreview() else dao.observeActiveWithPreview()
    }
    val recent by recentFlow.collectAsState(initial = emptyList())

    // Classify once per title on Dispatchers.Default; recompute when the
    // (id, title) set changes, not on every keystroke or recomposition.
    val titles = recent.map { it.conversation.id to it.conversation.title }
    val topicFlow = remember { MutableStateFlow<Map<String, DrawerTopic>>(emptyMap()) }
    LaunchedEffect(titles) {
        topicFlow.value = withContext(Dispatchers.Default) {
            titles.associate { (id, title) -> id to topicOf(title) }
        }
    }
    val topicById by topicFlow.collectAsState()

    val visibleRecent by remember(recent, topicById, chip, filesOnly) {
        derivedStateOf {
            recent.filter { row ->
                (!filesOnly || row.hasFiles) &&
                    chipAccepts(chip, topicById[row.conversation.id] ?: DrawerTopic.Chat)
            }
        }
    }
    val visibleHits by remember(hits, chip, filesOnly, archivedOnly) {
        derivedStateOf {
            hits.filter { hit ->
                (!filesOnly || hit.hasFiles) &&
                    (!archivedOnly || hit.archived) &&
                    chipAccepts(chip, hit.topic)
            }
        }
    }

    RefSettingsPage(
        title = "Chat History & Search",
        subtitle = "Find, revisit, and manage your conversations",
        onBack = onBack,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RefTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                // The mock draws ONE placeholder inside the field, with no label.
                placeholder = "Search your conversations…",
                leadingIcon = GsIcons.Search,
                imeAction = ImeAction.Search,
                onDone = { commitSearch() },
                focusRequester = searchFocus,
            )
            Spacer(Modifier.width(8.dp))
            FilterButton(active = archivedOnly || filesOnly) { facetsOpen = true }
        }
        Spacer(Modifier.height(10.dp))

        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            historyChips.forEach { label ->
                if (label == "More") {
                    RefChip(
                        text = label,
                        selected = archivedOnly || filesOnly,
                        onClick = { facetsOpen = true },
                        compact = true,
                    )
                } else {
                    RefChip(
                        text = label,
                        selected = chip == label,
                        onClick = { chip = if (chip == label) "All" else label },
                        compact = true,
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        RefCard(role = GsGlassRole.CardLight) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                HistoryStat(
                    icon = GsIcons.MessageCircle,
                    tone = GsWellTone.Peach,
                    label = "Total Conversations",
                    value = totals.activeCount.toString(),
                    caption = "Your chat history with Nexly",
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                HistoryStat(
                    icon = GsIcons.Calendar,
                    tone = GsWellTone.Peach,
                    label = "Last Active",
                    value = historyMoment(totals.lastActiveAt),
                    caption = "Most recent conversation",
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        when {
            term.length < 2 -> {
                RefGroupHeader(
                    title = "Recent Chats",
                    trailing = { ViewAllLink(onClick = onViewAllChats) },
                )
                when {
                    recent.isEmpty() -> RefInfoCard(
                        text = if (archivedOnly) {
                            "No archived chats. Archive a conversation from its ⋮ menu and it waits here."
                        } else {
                            "No conversations yet. Your chats appear here as you start them."
                        },
                        icon = GsIcons.MessageCircle,
                    )
                    visibleRecent.isEmpty() -> RefInfoCard(
                        text = "No chats match this filter. Try All, or clear the filters.",
                        icon = GsIcons.MessageCircle,
                    )
                    else -> RefCard(role = GsGlassRole.CardLight) {
                        visibleRecent.take(7).forEachIndexed { index, row ->
                            if (index > 0) RefRowDivider()
                            RecentChatRow(
                                row = row,
                                onClick = { onOpenConversation(row.conversation.id) },
                                onActions = { actionTarget = row.conversation },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                RefGroupHeader("Quick Actions", null)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HistoryAction(
                        GsIcons.Search, GsWellTone.Peach,
                        "Search Chats", "Find specific topics",
                        Modifier.weight(1f),
                    ) { searchFocus.requestFocus() }
                    HistoryAction(
                        GsIcons.Archive, GsWellTone.Rose,
                        "Manage History", "Delete or archive chats",
                        Modifier.weight(1f),
                    ) { onOpenArchive() }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HistoryAction(
                        GsIcons.Bookmark, GsWellTone.Lilac,
                        "Saved items", "Your saved answers",
                        Modifier.weight(1f),
                    ) { onOpenSaved() }
                    HistoryAction(
                        GsIcons.Download, GsWellTone.Blue,
                        "Export Chat History", "Download your data",
                        Modifier.weight(1f),
                    ) { export.launch("gs-chat-history-export.json") }
                }
                Spacer(Modifier.height(14.dp))
                NewConversationCard(onClick = onNewChat)
                Spacer(Modifier.height(10.dp))
            }
            searched && visibleHits.isEmpty() -> RefEmptyState(
                art = RefArt.Search,
                title = "No matches",
                message = buildString {
                    append("Nothing matched \"")
                    append(term)
                    append('"')
                    if (chip != "All") append(" in \"$chip\"")
                    if (archivedOnly) append(" in archived chats")
                    if (filesOnly) append(" with files")
                    append(". Try a shorter word or clear the filters.")
                },
            )
            else -> {
                Text(
                    text = "${visibleHits.size} result${if (visibleHits.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = GsTheme.colors.textTertiary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
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
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 16.dp),
                )
            }
        }
    }

    if (facetsOpen) {
        FacetSheet(
            archived = archivedOnly,
            files = filesOnly,
            onArchived = { archivedOnly = it },
            onFiles = { filesOnly = it },
            onDismiss = { facetsOpen = false },
        )
    }

    val target = actionTarget
    if (target != null) {
        ConversationActionsSheet(
            title = gsConversationTitle(target.title),
            pinned = target.pinned,
            onDismiss = { actionTarget = null },
            onTogglePin = {
                actionTarget = null
                scope.launch { runCatching { ServiceLocator.chat.setPinned(target.id, !target.pinned) } }
            },
            onArchive = {
                actionTarget = null
                scope.launch { runCatching { ServiceLocator.chat.setArchived(target.id, true) } }
            },
            onDelete = {
                actionTarget = null
                scope.launch { runCatching { ServiceLocator.chat.delete(target.id) } }
            },
            onRename = { name ->
                actionTarget = null
                scope.launch { runCatching { ServiceLocator.chat.rename(target.id, name) } }
            },
        )
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
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RefIconTile(GsIcons.MessageCircle, tint = GsTheme.colors.textSecondary)
        Column(Modifier.weight(1f)) {
            Text(
                gsConversationTitle(hit.title),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = GsTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${hit.snippet} · ${hit.moment}",
                style = MaterialTheme.typography.labelMedium,
                color = GsTheme.colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            GsIcons.ChevronRight,
            contentDescription = null,
            tint = GsTheme.colors.textTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Room-backed search; any store hiccup quietly reads as "no matches". */
private suspend fun searchChats(term: String): List<ChatHit> {
    return runCatching {
        val db = ServiceLocator.db
        val dao = db.conversationDao()
        val match = ftsMatchQuery(term)
        val titleHits = (
            if (match != null) dao.searchByTitleFts(match)
            else dao.searchByTitle(term)
            ).map { convo ->
            ChatHit(
                conversationId = convo.id,
                title = convo.title,
                snippet = "Chat title match",
                moment = relativeMoment(convo.updatedAt),
                archived = convo.archived,
                hasFiles = dao.hasFiles(convo.id),
                topic = withContext(Dispatchers.Default) { topicOf(convo.title) },
            )
        }
        val messageHits = (
            if (match != null) db.messageDao().searchContentFts(match)
            else db.messageDao().searchContent(term)
            ).mapNotNull { message ->
            val convo = dao.getById(message.conversationId) ?: return@mapNotNull null
            ChatHit(
                conversationId = message.conversationId,
                title = convo.title,
                snippet = snippet(message.content, term),
                moment = relativeMoment(message.createdAt),
                archived = convo.archived,
                hasFiles = hasFiles(message.attachments),
                topic = withContext(Dispatchers.Default) { topicOf(convo.title) },
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

/** The round filter control beside the field. Opens the real facets sheet. */
@Composable
private fun FilterButton(active: Boolean, onClick: () -> Unit) {
    val colors = GsTheme.colors
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(
                if (active) colors.accentSoft else colors.glassSurface.copy(alpha = 0.55f),
                CircleShape,
            )
            .border(
                1.dp,
                if (active) colors.accent.copy(alpha = 0.40f) else colors.glassBorder,
                CircleShape,
            )
            .clickable(role = Role.Button, onClickLabel = "Search filters", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            GsIcons.SlidersHorizontal,
            contentDescription = "Search filters",
            tint = if (active) colors.accent else colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** "View all →" in the Recent Chats header: a real route, not a caption. */
@Composable
private fun ViewAllLink(onClick: () -> Unit) {
    val colors = GsTheme.colors
    Row(
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Button,
            onClickLabel = "View all chats",
            onClick = onClick,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "View all",
            fontSize = 10.5.sp,
            fontFamily = com.grapsee.gsai.ui.theme.GsFonts.Product,
            color = colors.accent,
        )
        Icon(
            GsIcons.ChevronRight,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(12.dp),
        )
    }
}

/** The mock's footer: a real tap target that starts a new conversation. */
@Composable
private fun NewConversationCard(onClick: () -> Unit) {
    val colors = GsTheme.colors
    RefCard(role = GsGlassRole.CardLight) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClickLabel = "Start a new conversation",
                    onClick = onClick,
                )
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RefWellTile(GsIcons.Sparkles, GsWellTone.Peach, 28.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Start a new conversation",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = com.grapsee.gsai.ui.theme.GsFonts.Product,
                    color = colors.textPrimary,
                )
                Text(
                    "Get back to what matters",
                    fontSize = 11.sp,
                    fontFamily = com.grapsee.gsai.ui.theme.GsFonts.Product,
                    color = colors.textTertiary,
                )
            }
            Box(
                Modifier
                    .size(30.dp)
                    .background(colors.accent.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    GsIcons.ArrowRight,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

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

/** One Recent Chats row: real title, real preview, real timestamp, real menu. */
@Composable
private fun RecentChatRow(
    row: ConversationPreviewRow,
    onClick: () -> Unit,
    onActions: () -> Unit,
) {
    val conversation = row.conversation
    val title = gsConversationTitle(conversation.title)
    // Glyph from real signals only; the topic would be a guess this row cannot
    // prove, while pinned and has-files are columns.
    val icon = when {
        conversation.pinned -> GsIcons.Star
        row.hasFiles -> GsIcons.Paperclip
        else -> GsIcons.MessageCircle
    }
    val tone = when {
        conversation.pinned -> GsWellTone.Mint
        row.hasFiles -> GsWellTone.Blue
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
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
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
                previewLine(row.lastMessage),
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
        Box(
            Modifier
                .size(34.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClickLabel = "Chat actions",
                    onClick = onActions,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                GsIcons.MoreVertical,
                contentDescription = "Chat actions",
                tint = GsTheme.colors.textTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** More chip / filter button: the remaining real facets, both backed by DAO. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FacetSheet(
    archived: Boolean,
    files: Boolean,
    onArchived: (Boolean) -> Unit,
    onFiles: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                "Filter chats",
                style = MaterialTheme.typography.titleMedium,
                color = GsTheme.colors.textPrimary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            RefToggleRow(
                title = "Archived conversations",
                subtitle = "Only chats you have archived",
                icon = GsIcons.Archive,
                checked = archived,
                onCheckedChange = onArchived,
            )
            RefRowDivider()
            RefToggleRow(
                title = "Has files",
                subtitle = "Only chats with attachments",
                icon = GsIcons.Paperclip,
                checked = files,
                onCheckedChange = onFiles,
            )
            if (archived || files) {
                TextButton(onClick = { onArchived(false); onFiles(false) }) {
                    Text("Clear filters")
                }
            }
        }
    }
}
