package com.grapsee.gsai.ui.search

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.AssistantsStore
import com.grapsee.gsai.data.ProjectStore
import com.grapsee.gsai.data.local.ftsMatchQuery
import com.grapsee.gsai.data.model.SampleData
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.components.GsEmptyState
import com.grapsee.gsai.ui.components.GsInputBar
import com.grapsee.gsai.ui.components.GsListItem
import com.grapsee.gsai.ui.components.GsScreenScaffold
import com.grapsee.gsai.ui.components.GsSectionHeader
import com.grapsee.gsai.ui.components.gsConversationTitle
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.GsMotion
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.coroutines.delay

/**
 * AERUO KINETIC — SEARCH, the one-index entry point.
 * One query over the real on-device corpus: Room-backed conversations and
 * message bodies (FTS full-text index with a LIKE fallback for CJK/symbol
 * input), Library saved items, the assistant catalogue (samples + the user's
 * own) and projects. Results arrive grouped with honest per-group counts and
 * every row routes to its real destination. Store hiccups resolve to "no
 * matches", never an error. Kind chips gate the groups; recent searches are
 * the queries the reader actually acted on.
 */

private enum class HitKind(val label: String, val icon: ImageVector) {
    CONVERSATIONS("Conversations", Icons.Outlined.ChatBubbleOutline),
    MESSAGES("Messages", Icons.Outlined.ChatBubbleOutline),
    LIBRARY("Library", Icons.Outlined.BookmarkBorder),
    ASSISTANTS("Assistants", Icons.Outlined.SmartToy),
    PROJECTS("Projects", Icons.Outlined.Folder)
}

private data class SearchHit(
    val kind: HitKind,
    val title: String,
    val subtitle: String,
    val route: String
)

/** Search mirrors the Projects surface: user-created projects only, real meta. */
private fun projectHitsFor(term: String): List<SearchHit> {
    if (term.length < 2) return emptyList()
    return ProjectStore.projects
        .filter { it.name.contains(term, ignoreCase = true) || it.blurb.contains(term, ignoreCase = true) }
        .take(8)
        .map { project ->
            val meta = if (project.blurb.isNotBlank()) project.blurb
            else "${project.chatIds.size} chats"
            SearchHit(HitKind.PROJECTS, project.name, meta, GsRoutes.project(project.id))
        }
}

/** Recent searches the reader acted on — local-first, hiccup-safe, newest first. */
private object RecentSearches {
    private const val PREFS = "gs_search"
    private const val KEY = "gs.search.recent"

    fun load(context: Context): List<String> {
        val stored = runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        }.getOrNull()
        return stored?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
    }

    fun record(context: Context, term: String) {
        runCatching {
            val next = (listOf(term) + load(context)).distinct().take(5)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, next.joinToString("\n")).apply()
        }
    }
}

@Composable
fun SearchScreen(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var kindFilter by remember { mutableStateOf<HitKind?>(null) }
    var storeHits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var recents by remember { mutableStateOf(RecentSearches.load(context)) }

    val savedItems by ServiceLocator.chat.savedItems().collectAsState(initial = emptyList())
    val userAssistants by AssistantsStore.assistants.collectAsState()

    val term = query.trim()

    // Room-backed half (conversations + messages) rides the debounce; the
    // in-memory half (library/assistants/projects) filters live, so the
    // screen answers instantly and the store results land a beat later.
    LaunchedEffect(term) {
        if (term.length < 2) {
            storeHits = emptyList()
            return@LaunchedEffect
        }
        storeHits = emptyList() // stale hits from the previous term never linger
        delay(220) // settle keystrokes before touching the store
        storeHits = searchConversationsAndMessages(term)
    }

    val libraryHits = remember(savedItems, term) {
        if (term.length < 2) emptyList()
        else savedItems.asSequence()
            .filter { it.title.contains(term, ignoreCase = true) || it.content.contains(term, ignoreCase = true) }
            .take(10)
            .map { item ->
                val contentMatched = !item.title.contains(term, ignoreCase = true)
                SearchHit(
                    HitKind.LIBRARY,
                    item.title,
                    (if (contentMatched) snippet(item.content, term) else "Saved ${item.kind}") +
                        " · " + relativeMoment(item.createdAt),
                    GsRoutes.LIBRARY
                )
            }
            .toList()
    }
    val assistantHits = remember(userAssistants, term) {
        if (term.length < 2) emptyList()
        else (SampleData.assistants + userAssistants)
            .filter {
                it.name.contains(term, ignoreCase = true) ||
                    it.description.contains(term, ignoreCase = true) ||
                    it.category.contains(term, ignoreCase = true)
            }
            .take(8)
            .map { assistant ->
                SearchHit(
                    HitKind.ASSISTANTS,
                    assistant.name,
                    "${assistant.category} · ★ ${String.format(java.util.Locale.US, "%.1f", assistant.rating)}",
                    GsRoutes.assistant(assistant.id)
                )
            }
    }
    val projectHits = remember(term, ProjectStore.projects) { projectHitsFor(term) }

    val allHits = storeHits + libraryHits + assistantHits + projectHits
    val visibleKinds = HitKind.entries.filter { kindFilter == null || it == kindFilter }

    // Insets come from GsScreenScaffold; imePadding keeps the keyboard from
    // covering the results while typing (same contract as the chat surface).
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        GsScreenScaffold(title = "Search") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(GsMotion.spaceL)
            ) {
                Spacer(Modifier.height(GsMotion.spaceS))
                GsInputBar(
                    value = query,
                    onValueChange = { query = it },
                    onSend = {},
                    placeholder = "Search everything…"
                )

                if (term.isEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                        GsSectionHeader(title = "Recent searches")
                        if (recents.isEmpty()) {
                            Text(
                                text = "Searches you act on land here.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
                            ) {
                                recents.forEach { recent ->
                                    GsChip(text = recent, selected = false) { query = recent }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
                ) {
                    GsChip(text = "All", selected = kindFilter == null) { kindFilter = null }
                    HitKind.entries.forEach { kind ->
                        GsChip(text = kind.label, selected = kindFilter == kind) {
                            kindFilter = if (kindFilter == kind) null else kind
                        }
                    }
                }

                when {
                    term.isEmpty() -> GsEmptyState(
                        icon = Icons.Outlined.Search,
                        title = "Search everything",
                        message = "Conversations, messages, library items, assistants and projects — one index over this device."
                    )
                    term.length < 2 -> GsEmptyState(
                        icon = Icons.Outlined.ManageSearch,
                        title = "Keep typing",
                        message = "At least two characters to search everything on this device."
                    )
                    allHits.isEmpty() -> GsEmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = "No matches",
                        message = "Nothing matched \"$term\". Try a shorter word or different phrasing."
                    )
                    else -> visibleKinds.forEach { kind ->
                        val hits = allHits.filter { it.kind == kind }
                        if (hits.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                                GsSectionHeader(
                                    title = kind.label,
                                    actionLabel = "${hits.size} found",
                                    onAction = {}
                                )
                                hits.forEach { hit ->
                                    GsListItem(
                                        title = if (hit.kind == HitKind.CONVERSATIONS) gsConversationTitle(hit.title) else hit.title,
                                        subtitle = hit.subtitle,
                                        leading = { ResultBadge(kind.icon) },
                                        onClick = {
                                            RecentSearches.record(context, term)
                                            recents = RecentSearches.load(context)
                                            onNavigate(hit.route)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(GsMotion.spaceL))
            }
        }
    }
}

/** Room-backed search — FTS full-text for plain ASCII words, LIKE for
 *  everything else (CJK, symbols). Any store hiccup reads as "no matches". */
private suspend fun searchConversationsAndMessages(term: String): List<SearchHit> {
    return runCatching {
        val db = ServiceLocator.db
        val match = ftsMatchQuery(term)
        val conversationHits = (
            if (match != null) db.conversationDao().searchByTitleFts(match)
            else db.conversationDao().searchByTitle(term)
            )
            .take(10)
            .map { convo ->
                SearchHit(
                    HitKind.CONVERSATIONS,
                    convo.title,
                    "Chat title match · " + relativeMoment(convo.updatedAt),
                    GsRoutes.chat(convo.id)
                )
            }
        val messageHits = (
            if (match != null) db.messageDao().searchContentFts(match)
            else db.messageDao().searchContent(term)
            )
            .take(12)
            .mapNotNull { message ->
                val convo = db.conversationDao().getById(message.conversationId) ?: return@mapNotNull null
                SearchHit(
                    HitKind.MESSAGES,
                    convo.title,
                    snippet(message.content, term) + " · " + relativeMoment(message.createdAt),
                    GsRoutes.chat(convo.id)
                )
            }
        conversationHits + messageHits
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

@Composable
private fun ResultBadge(icon: ImageVector) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.size(38.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
