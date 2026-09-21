package com.grapsee.gsai.ui.chat

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ManageSearch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.data.chat.ClarifyOption
import com.grapsee.gsai.data.chat.ClarifyPrompt
import com.grapsee.gsai.data.chat.EngineOutcome
import com.grapsee.gsai.data.chat.EngineRoundRow
import com.grapsee.gsai.data.chat.ResearchPhaseNote
import com.grapsee.gsai.data.chat.SearchTraceItem
import com.grapsee.gsai.data.chat.SourceCard
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.theme.GsMotion
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * PHASE 8.1/8.2 — the search-turn UI seam: search trace card, source cards and
 * clarify quick-choices (docs/search-event-protocol.md, v1 + v2 FROZEN).
 *
 * Everything these composables draw comes from REAL received events or the
 * persisted message — the caller passes controller state / entity columns
 * verbatim. There are no timers, no fake progress and no invented steps here:
 * an empty list simply renders nothing. The design system stays strictly
 * monochrome — theme roles only (the app's palette). Animation is subtle and
 * gated on the app's reduce-motion setting (SettingsStore), the same
 * discipline as the streaming caret.
 *
 * PHASE 8.2 additions: per-round engine status rows and an expandable
 * "Research details" audit section inside the trace card, both rendered ONLY
 * from received events; and the source-tile favicon attempt — the protocol's
 * icons.duckduckgo.com URL is derived CLIENT-side from the wire domain and
 * loaded asynchronously (Coil, never the main thread), with the domain
 * monogram tile kept intact as placeholder AND error fallback.
 */

// ---------------------------------------------------------------------------
// Search trace card
// ---------------------------------------------------------------------------

/**
 * Compact collapsible card above the streaming bubble: one line per real
 * search-chain event while streaming; collapsed to the one-line summary once
 * the turn settles (protocol: "After done, the trace collapses to a summary
 * line; source cards stay"). When the full trace is still in memory the
 * collapsed card can be re-expanded; after a reload only the summary line
 * exists and the card is honest about that (no chevron, no dead toggle).
 *
 * [items] are the trace lines in arrival order (already de-duplicated by the
 * stream accumulator); [summary] is the collapsed one-liner derived from the
 * terminal `search completed` counts (or the failure line); [expandable]
 * marks whether detail exists to expand into.
 *
 * PHASE 8.2 (protocol v2 §20 audit trail): inside the expanded card the
 * per-round engine status rows render under the trace lines, and an
 * expandable "Research details" section (collapsed by default after done,
 * expanded while streaming — it follows [defaultExpanded]) lists queries run,
 * per-engine outcomes, sources found/read/failed, syndicated groups and
 * citations used. All of it is memory-only detail: it exists exactly as long
 * as the received events do.
 */
@Composable
fun SearchTraceCard(
    items: List<SearchTraceItem>,
    summary: String?,
    expandable: Boolean,
    defaultExpanded: Boolean,
    engineRows: List<EngineRoundRow> = emptyList(),
    researchNotes: List<ResearchPhaseNote> = emptyList(),
    usedCitations: List<Int> = emptyList()
) {
    if (items.isEmpty() && summary.isNullOrBlank() &&
        engineRows.isEmpty() && researchNotes.isEmpty()
    ) {
        return
    }
    var expanded by remember(defaultExpanded) { mutableStateOf(defaultExpanded) }
    // The details section follows the same default as the card itself: open
    // while streaming, collapsed after done, re-expandable while in memory.
    var detailsExpanded by remember(defaultExpanded) { mutableStateOf(defaultExpanded) }
    // Reduce-motion users get the state change without the motion.
    val reduceMotion = SettingsStore.reduceMotion
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = expandable) { expanded = !expanded }
            ) {
                Icon(
                    Icons.Outlined.ManageSearch,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (expanded) "Search" else (summary ?: "Search"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (expanded) FontWeight.Normal else FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (expandable) {
                    Icon(
                        Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) "Collapse search trace" else "Expand search trace",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer { rotationZ = if (expanded) 180f else 0f }
                    )
                }
            }
            val hasResearchDetail = engineRows.isNotEmpty() || researchNotes.isNotEmpty()
            if (items.isNotEmpty() || hasResearchDetail) {
                AnimatedVisibility(
                    visible = expanded,
                    enter = if (reduceMotion) EnterTransition.None
                    else fadeIn(tween(GsMotion.REDUCED_TWEEN_MS)) +
                        expandVertically(tween(GsMotion.REDUCED_TWEEN_MS)),
                    exit = if (reduceMotion) ExitTransition.None
                    else fadeOut(tween(GsMotion.REDUCED_TWEEN_MS)) +
                        shrinkVertically(tween(GsMotion.REDUCED_TWEEN_MS))
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        items.forEach { item -> TraceLine(item) }
                        // PHASE 8.2: per-round engine status rows — one small
                        // monochrome row per engine, straight from the event.
                        engineRows.forEach { row ->
                            row.engines.forEach { outcome ->
                                EngineLine(row.round, outcome, showRound = engineRows.size > 1)
                            }
                        }
                        if (hasResearchDetail) {
                            ResearchDetailsSection(
                                items = items,
                                engineRows = engineRows,
                                researchNotes = researchNotes,
                                usedCitations = usedCitations,
                                expanded = detailsExpanded,
                                onToggle = { detailsExpanded = !detailsExpanded },
                                reduceMotion = reduceMotion
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One trace line: honest glyph (✓ done / ↗ in flight / ✕ failed / – skipped)
 * + the event's real text. New lines fade in subtly — skipped entirely under
 * reduce-motion (the line simply appears, like the reduced-motion caret).
 */
@Composable
private fun TraceLine(item: SearchTraceItem) {
    val reduceMotion = SettingsStore.reduceMotion
    val alpha = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduceMotion) alpha.animateTo(1f, tween(durationMillis = GsMotion.REDUCED_TWEEN_MS))
    }
    val glyph = when (item.kind) {
        SearchTraceItem.Kind.SearchStarted,
        SearchTraceItem.Kind.Query,
        SearchTraceItem.Kind.Results,
        SearchTraceItem.Kind.Read,
        SearchTraceItem.Kind.RoundVerified,
        SearchTraceItem.Kind.Composing,
        SearchTraceItem.Kind.Completed -> "✓"
        SearchTraceItem.Kind.Reading -> "↗"
        SearchTraceItem.Kind.SourceFailed,
        SearchTraceItem.Kind.SearchFailed -> "✕"
        SearchTraceItem.Kind.SourceSkipped -> "–"
    }
    val failed = item.kind == SearchTraceItem.Kind.SourceFailed ||
        item.kind == SearchTraceItem.Kind.SearchFailed
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.graphicsLayer { this.alpha = alpha.value }
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.labelMedium,
            color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = item.text,
            style = MaterialTheme.typography.bodySmall,
            color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

// ---------------------------------------------------------------------------
// PHASE 8.2 — engine rows + "Research details" audit section
// ---------------------------------------------------------------------------

/**
 * One engine outcome row — check glyph + engine display name + result count
 * when ok, cross glyph + short error when failed, a quiet dash when the engine
 * ran but found nothing. Monochrome theme roles only; an unknown engine id is
 * shown verbatim (honest — never invented). [showRound] prefixes the round
 * number only when the turn actually ran multiple rounds.
 */
@Composable
private fun EngineLine(round: Int, outcome: EngineOutcome, showRound: Boolean) {
    val name = engineDisplayName(outcome.id)
    val ok = outcome.status == "ok"
    val failed = outcome.status == "failed"
    val glyph = when {
        ok -> "✓"
        failed -> "✕"
        else -> "–"
    }
    val color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    val detail = when (outcome.status) {
        "ok" -> outcome.count?.let { " · $it results" } ?: ""
        "failed" -> outcome.error?.let { " — ${it.take(SHORT_ERROR_CHARS)}" } ?: ""
        "empty" -> " — no results"
        else -> outcome.error?.let { " — ${it.take(SHORT_ERROR_CHARS)}" } ?: ""
    }
    val prefix = if (showRound) "Round $round · " else ""
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = prefix + name + detail,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * The expandable "Research details" audit trail (protocol v2 §20) — queries
 * run, per-engine outcomes, sources found/read/failed, syndicated groups,
 * citations used, and the completion timings when the wire carried them.
 * EVERY line is composed from received events only; an aggregate the events
 * never delivered simply does not render.
 */
@Composable
private fun ResearchDetailsSection(
    items: List<SearchTraceItem>,
    engineRows: List<EngineRoundRow>,
    researchNotes: List<ResearchPhaseNote>,
    usedCitations: List<Int>,
    expanded: Boolean,
    onToggle: () -> Unit,
    reduceMotion: Boolean
) {
    val completed = researchNotes.filterIsInstance<ResearchPhaseNote.Completed>().firstOrNull()
    val rounds = researchNotes.filterIsInstance<ResearchPhaseNote.RoundSummary>()
    Column(modifier = Modifier.padding(top = 6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
        ) {
            Text(
                text = "Research details",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "Collapse research details" else "Expand research details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationZ = if (expanded) 180f else 0f }
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = if (reduceMotion) EnterTransition.None
            else fadeIn(tween(GsMotion.REDUCED_TWEEN_MS)) +
                expandVertically(tween(GsMotion.REDUCED_TWEEN_MS)),
            exit = if (reduceMotion) ExitTransition.None
            else fadeOut(tween(GsMotion.REDUCED_TWEEN_MS)) +
                shrinkVertically(tween(GsMotion.REDUCED_TWEEN_MS))
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                // Queries run — the research completed count when it arrived,
                // otherwise the real query trace lines (both event-born).
                val queriesRun = completed?.queries
                    ?: items.count { it.kind == SearchTraceItem.Kind.Query }
                if (queriesRun > 0) DetailLine("Queries run: $queriesRun")
                // Per-engine outcomes — the same honest rows, listed for the audit.
                engineRows.forEach { row ->
                    row.engines.forEach { outcome ->
                        EngineLine(row.round, outcome, showRound = engineRows.size > 1)
                    }
                }
                // Sources found / read / failed — the completed totals when they
                // arrived, else the per-round sums (failed only exists per round).
                val found = completed?.sources ?: rounds.sumOf { it.found }
                val read = completed?.retrieved ?: rounds.sumOf { it.read }
                val failed = rounds.sumOf { it.failed }
                val sourceParts = mutableListOf<String>()
                if (found > 0) sourceParts += "$found found"
                if (read > 0) sourceParts += "$read read"
                if (failed > 0) sourceParts += "$failed failed"
                if (sourceParts.isNotEmpty()) {
                    DetailLine("Sources — " + sourceParts.joinToString(" · "))
                }
                // Syndicated groups (v2 wire field, per round).
                val syndicated = rounds.sumOf { it.syndicatedGroups ?: 0 }
                if (syndicated > 0) {
                    DetailLine("Syndicated groups: $syndicated")
                }
                // Citations the answer actually bound (`research completed`).
                if (usedCitations.isNotEmpty()) {
                    DetailLine("Citations used: " + usedCitations.joinToString(", "))
                }
                // Completion timings (optional wire block) — raw ms, honest.
                completed?.let { done ->
                    val timingParts = mutableListOf<String>()
                    done.timings?.firstSearchMs?.let { timingParts += "first search ${msLabel(it)}" }
                    done.timings?.firstSourceMs?.let { timingParts += "first source ${msLabel(it)}" }
                    done.timings?.firstTokenMs?.let { timingParts += "first token ${msLabel(it)}" }
                    done.totalMs?.let { timingParts += "total ${msLabel(it)}" }
                    if (timingParts.isNotEmpty()) {
                        DetailLine("Timing — " + timingParts.joinToString(" · "))
                    }
                }
            }
        }
    }
}

/** One aggregate detail line — plain text, monochrome, no glyph of its own. */
@Composable
private fun DetailLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

/** Engine display names for the known ids; unknown ids pass through verbatim. */
private fun engineDisplayName(id: String): String = when (id) {
    "bing-news-rss" -> "Bing News"
    "google-news-rss" -> "Google News"
    "bing-web" -> "Bing Web"
    "duckduckgo-lite" -> "DuckDuckGo"
    "wikipedia" -> "Wikipedia"
    "searxng" -> "SearXNG"
    "z-ai" -> "Z AI"
    "openlibrary" -> "Open Library"
    "gutenberg" -> "Gutenberg"
    "arxiv" -> "arXiv"
    "crossref" -> "Crossref"
    else -> id
}

/** The failed row's error stays short — the full reason lives in the wire, not the row. */
private const val SHORT_ERROR_CHARS = 80

private fun msLabel(value: Long): String = "$value ms"

// ---------------------------------------------------------------------------
// Source cards
// ---------------------------------------------------------------------------

/**
 * The turn's REAL sources as a horizontal row of compact cards — built live
 * while `source` events land, then replaced 1:1 by the persisted done sources.
 * Tile: PHASE 8.2 attempts the REAL favicon first — the protocol's
 * icons.duckduckgo.com URL, derived CLIENT-side from the wire domain and
 * loaded asynchronously by Coil (never the main thread); the monogram tile is
 * BOTH the loading placeholder and the error fallback, so a missing or blocked
 * favicon degrades to exactly the pre-8.2 letter tile. Two-line title, domain,
 * optional date and the citation ordinal badge. Tap opens the real URL through
 * [onOpenUrl] (the screen's ACTION_VIEW path); a card whose URL is not a plain
 * http(s) link is inert — never a broken intent.
 */
@Composable
fun SourceCardsRow(sources: List<SourceCard>, onOpenUrl: (String) -> Unit) {
    if (sources.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        sources.forEach { card -> SourceCardView(card, onOpenUrl) }
    }
}

@Composable
private fun SourceCardView(card: SourceCard, onOpenUrl: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .width(184.dp)
            .clickable(
                enabled = card.url.startsWith("https://") || card.url.startsWith("http://")
            ) { onOpenUrl(card.url) }
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // PHASE 8.2: attempt the real favicon — derived client-side from
                // the wire domain (protocol §honest-state: never trust model text
                // for the URL), fetched ASYNC by Coil. The monogram tile below is
                // the placeholder AND error fallback; the image itself is
                // decorative (contentDescription = null — the domain label under
                // this row carries the meaning for accessibility).
                val faviconDomain = faviconDomainOrNull(card.domain)
                if (faviconDomain != null) {
                    SubcomposeAsyncImage(
                        model = "https://icons.duckduckgo.com/ip3/$faviconDomain.ico",
                        contentDescription = null,
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape),
                        loading = { MonogramTile(card.domain) },
                        error = { MonogramTile(card.domain) }
                    )
                } else {
                    MonogramTile(card.domain)
                }
                Spacer(Modifier.weight(1f))
                // Citation ordinal badge — the same N the answer's [N] chips use.
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = card.ordinal.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = card.title.ifBlank { card.domain },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = card.domain,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Honest retrieval state — only what the reader needs: a failure,
            // a skip, or the honest "headline only" (never fetched). A read or
            // cited source carries just its date, when the wire gave one.
            when (card.status) {
                "failed" -> SourceCardStatusLine("✕ Failed to read", MaterialTheme.colorScheme.error)
                "skipped" -> SourceCardStatusLine("– Skipped", MaterialTheme.colorScheme.onSurfaceVariant)
                "snippet_only" -> SourceCardStatusLine("Headline only", MaterialTheme.colorScheme.onSurfaceVariant)
                else -> card.publishedDate?.let { SourceCardStatusLine(dateLabel(it), MaterialTheme.colorScheme.outline) }
            }
        }
    }
}

@Composable
private fun SourceCardStatusLine(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * The pre-8.2 monogram tile, kept intact — a letter honestly derived from the
 * wire domain, not an image. It is the favicon's loading placeholder AND its
 * error fallback, so the tile is always present.
 */
@Composable
private fun MonogramTile(domain: String) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = domain.trim().removePrefix("www.")
                .firstOrNull()?.uppercase() ?: "#",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Only a plausible hostname earns a favicon attempt (the URL is derived
 * client-side from the wire domain — garbage in, no request out).
 */
private fun faviconDomainOrNull(domain: String): String? {
    val trimmed = domain.trim().removePrefix("www.")
    if (trimmed.isEmpty() || trimmed.length > 255) return null
    if (trimmed.any { it.isWhitespace() || it == '/' || it == '\\' }) return null
    if (!trimmed.contains('.')) return null
    return trimmed
}

/** "2026-02-19" → "19 Feb 2026"; unparseable input stays verbatim (honest). */
private fun dateLabel(raw: String): String = runCatching {
    LocalDate.parse(raw.take(10)).format(DateTimeFormatter.ofPattern("d MMM yyyy"))
}.getOrDefault(raw)

// ---------------------------------------------------------------------------
// Clarify quick-choices
// ---------------------------------------------------------------------------

/**
 * The clarify turn IS the clarification question: the streamed answer above
 * and these quick-choice chips below. Tapping a chip sends its label as a
 * normal user message through the existing send path — no hidden protocol,
 * no synthetic "answer". Rendered only once the turn has settled, so every
 * chip is immediately tappable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClarifyChips(prompt: ClarifyPrompt, onPick: (ClarifyOption) -> Unit) {
    if (prompt.options.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (prompt.question.isNotBlank()) {
            Text(
                text = prompt.question,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            prompt.options.forEach { option ->
                GsChip(
                    text = option.label,
                    selected = false,
                    onClick = { onPick(option) }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared helpers
// ---------------------------------------------------------------------------

/**
 * Opens a source URL the only honest way: a real ACTION_VIEW intent. Devices
 * without a handler stay quiet (the caller snacks); non-http(s) schemes are
 * refused BEFORE the intent is built — the URL came over the wire, and the
 * client trusts it exactly as far as a browser does.
 */
fun openSourceUrl(context: Context, url: String): Boolean {
    if (!url.startsWith("https://") && !url.startsWith("http://")) return false
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
