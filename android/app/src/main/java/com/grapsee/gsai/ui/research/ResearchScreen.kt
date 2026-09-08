package com.grapsee.gsai.ui.research

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.components.GsCard
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.components.GsEmptyState
import com.grapsee.gsai.ui.components.GsInputBar
import com.grapsee.gsai.ui.components.GsListItem
import com.grapsee.gsai.ui.components.GsScreenScaffold
import com.grapsee.gsai.ui.components.GsSectionHeader
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.rememberAuroraBrush
import kotlinx.coroutines.launch

/**
 * AERUO KINETIC — RESEARCH, multi-source reasoning with citations.
 * Deliberately distinct from chat: a research-mode header chip, an aurora
 * "reading sources" life-sign, a serif Synthesis answer with inline [n]
 * markers, source cards with bookmark toggles and export/report affordances.
 * Sample data only — the research agent lands with the data layer.
 */

private data class ResearchSource(
    val domain: String,
    val title: String,
    val year: String,
    val relevance: Int
)

private val researchSources = listOf(
    ResearchSource("arxiv.org", "LFP cell cost curves and the road below 80 dollars per kWh", "2025", 96),
    ResearchSource("nature.com", "Sodium-ion scale-up: an evidence review", "2024", 88),
    ResearchSource("theverge.com", "Why every automaker is suddenly hedging on batteries", "2025", 81),
    ResearchSource("economist.com", "The lithium glut and the 2025 correction", "2025", 74),
    ResearchSource("iea.org", "Global EV Outlook: batteries and supply chains", "2024", 69)
)

private val suggestedQuestions = listOf(
    "EV battery supply chain 2025",
    "State of small models",
    "Creator economy economics",
    "GLP-1 market outlook"
)

private data class RecentResearch(val query: String, val meta: String)

private val recentResearch = listOf(
    RecentResearch("EV battery supply chain 2025", "12 sources · 2h ago"),
    RecentResearch("State of small models", "9 sources · yesterday"),
    RecentResearch("GLP-1 market outlook", "14 sources · 3d ago")
)

private val synthesisParagraphs = listOf(
    "Global EV battery demand grew an estimated 33% in 2024, led by LFP adoption in China and a widening gap between announced gigafactory capacity and realistic 2025 commissioning [1]. Pack-level cell prices fell below 100 dollars per kWh for mainstream chemistries for the first time, resetting the economics of legacy NMC lines [2].",
    "Supply concentration remains the structural risk: a handful of provinces refine most of the world's lithium, and new trade measures are pushing Western automakers toward multi-source contracts through 2026 [3]. Most analysts now expect pricing to stabilise by mid-2025 as the inventory overhang clears [4].",
    "The practical takeaway for strategy teams is a two-track plan — secure LFP volume now while funding sodium-ion pilots that hedge raw-material exposure beyond 2027 [5]."
)

@Composable
fun ResearchScreen(onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var searched by remember { mutableStateOf(false) }
    var bookmarks by remember { mutableStateOf(setOf<Int>()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showSnack: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        GsScreenScaffold(
            title = "Research",
            onBack = onBack,
            actions = { GsChip(text = "Research mode", selected = true, onClick = {}) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(GsMotion.spaceL)
            ) {
                Spacer(Modifier.height(GsMotion.spaceS))
                GsInputBar(
                    value = query,
                    onValueChange = { query = it },
                    onSend = { searched = true },
                    placeholder = "Ask a research question…"
                )

                if (!searched) {
                    GsCard(onClick = null) {
                        GsEmptyState(
                            icon = Icons.Outlined.TravelExplore,
                            title = "Multi-source research",
                            message = "Ask a question and GS reads across papers, press and reports — then synthesises a cited answer."
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                        GsSectionHeader(title = "Try one of these")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
                        ) {
                            suggestedQuestions.forEach { question ->
                                GsChip(text = question, selected = false) {
                                    query = question
                                    searched = true
                                }
                            }
                        }
                    }
                } else {
                    ReasoningStatusRow()
                    SynthesisCard()
                    SourcesSection(
                        bookmarks = bookmarks,
                        onToggleBookmark = { index ->
                            bookmarks = if (index in bookmarks) bookmarks - index else bookmarks + index
                        }
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                        GsSectionHeader(title = "Export report")
                        Row(horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                            listOf("PDF", "Markdown", "Notion").forEach { format ->
                                GsChip(text = format, selected = false) {
                                    showSnack("Report export queued")
                                }
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                        GsSectionHeader(title = "Recent research")
                        recentResearch.forEach { item ->
                            GsListItem(
                                title = item.query,
                                subtitle = item.meta,
                                leading = { RecentBadge() }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(GsMotion.spaceL))
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(GsMotion.spaceM)
        )
    }
}

/** Pulsing aurora life-sign — the model is actively reading sources. */
@Composable
private fun AuroraDot() {
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(rememberAuroraBrush(), CircleShape)
    )
}

@Composable
private fun ReasoningStatusRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
    ) {
        AuroraDot()
        Text(
            text = "Reading 5 sources · Cross-checking claims…",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SynthesisCard() {
    GsCard {
        Text(
            text = "Synthesis",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(GsMotion.spaceS))
        Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
            synthesisParagraphs.forEach { paragraph ->
                CitedParagraph(text = paragraph)
            }
        }
    }
}

/** Body text with inline [n] citation markers tinted primary. */
@Composable
private fun CitedParagraph(text: String) {
    val markerColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, markerColor) {
        buildAnnotatedString {
            var index = 0
            while (index < text.length) {
                val open = text.indexOf('[', index)
                if (open < 0) {
                    append(text.substring(index))
                    break
                }
                append(text.substring(index, open))
                var close = text.indexOf(']', open)
                if (close < 0) close = text.length - 1
                withStyle(SpanStyle(color = markerColor, fontWeight = FontWeight.SemiBold)) {
                    append(text.substring(open, close + 1))
                }
                index = close + 1
            }
        }
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun SourcesSection(
    bookmarks: Set<Int>,
    onToggleBookmark: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
        GsSectionHeader(title = "Sources", actionLabel = "5", onAction = {})
        researchSources.forEachIndexed { index, source ->
            SourceRow(
                source = source,
                bookmarked = index in bookmarks,
                onToggleBookmark = { onToggleBookmark(index) }
            )
        }
    }
}

@Composable
private fun SourceRow(
    source: ResearchSource,
    bookmarked: Boolean,
    onToggleBookmark: () -> Unit
) {
    GsCard(onClick = null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = source.domain.first().uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(GsMotion.spaceXS)
            ) {
                Text(
                    text = source.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
                ) {
                    Text(
                        text = "${source.domain} · ${source.year}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    GsChip(text = "${source.relevance}% match", selected = false, onClick = {})
                }
            }
            IconButton(onClick = onToggleBookmark) {
                Icon(
                    imageVector = if (bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = if (bookmarked) "Bookmarked" else "Bookmark",
                    tint = if (bookmarked) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun RecentBadge() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.size(34.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Outlined.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
