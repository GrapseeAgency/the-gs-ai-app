package com.grapsee.gsai.ui.explore

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.AssistantsStore
import com.grapsee.gsai.data.model.AssistantSample
import com.grapsee.gsai.data.model.SampleData
import com.grapsee.gsai.ui.components.GsCard
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.components.GsEmptyState
import com.grapsee.gsai.ui.components.GsInputBar
import com.grapsee.gsai.ui.components.GsScreenScaffold
import com.grapsee.gsai.ui.components.GsSectionHeader
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.GsMotion

/**
 * AERUO KINETIC — EXPLORE, the discovery layer / curated catalogue.
 * Horizontal rows: Top picks, Curated for you, Helpful prompts and — when
 * the user has created assistants — Made by you. One catalogue feeds the
 * assistant rows, shared with the Assistants hub (curated samples +
 * published user creations), so names never drift between surfaces.
 * NOTHING is measured, so nothing is claimed: the star ratings and "8.2k
 * uses" badges and the fabricated "Featured tools" inventory are gone —
 * cards carry name, description and category only, and every row title
 * describes curation, never popularity data that does not exist. Search +
 * category chips filter the rows together; serif prompt cards carry the
 * editorial voice.
 */

private data class ExplorePrompt(val text: String, val category: String)

private val popularPrompts = listOf(
    ExplorePrompt("Turn rough notes into a crisp launch email", "Business"),
    ExplorePrompt("Plan my week around three priorities", "Productivity"),
    ExplorePrompt("Explain this codebase like I'm brand new", "Engineering")
)

@Composable
fun ExploreScreen(onNavigate: (String) -> Unit) {
    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    val term = searchQuery.trim()

    // One catalogue, shared with the Assistants hub: curated samples plus the
    // user's published creations (archive respects the owner's intent).
    val userAssistants by AssistantsStore.assistants.collectAsState()
    val catalogue = remember(userAssistants) {
        SampleData.assistants + userAssistants.filter { it.published && !it.archived }
    }
    val categories = remember(catalogue) {
        listOf("All") + catalogue.map { it.category }.distinct().sorted()
    }
    val category = selectedCategory

    val filtered = catalogue.filter {
        (category == "All" || it.category == category) && matchesTerm(term, it.name, it.category, it.description)
    }
    val topPicks = remember(filtered) { filtered.take(4) }
    // Curated for you: the full filtered catalogue in catalogue order — the
    // order the editors of the app chose, not a popularity metric (no
    // measured usage exists to rank by).
    val curated = remember(filtered) { filtered }
    val mine = remember(userAssistants, term) {
        userAssistants.filter { !it.archived && matchesTerm(term, it.name, it.category) }
    }
    val prompts = popularPrompts.filter {
        (category == "All" || it.category == category) && matchesTerm(term, it.text, it.category)
    }
    val nothingToShow = filtered.isEmpty() && prompts.isEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        GsScreenScaffold(title = "Explore") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(GsMotion.spaceL)
            ) {
                Spacer(Modifier.height(GsMotion.spaceS))
                GsInputBar(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    // Results filter live as you type; Search on the IME commits
                    // by dismissing the keyboard so the reader sees them.
                    onSend = {},
                    imeAction = ImeAction.Search,
                    placeholder = "Search assistants and prompts…"
                )
                CategoryChips(categories, selectedCategory, onSelect = { selectedCategory = it })

                if (nothingToShow) {
                    GsEmptyState(
                        icon = Icons.Outlined.Search,
                        title = if (term.isEmpty()) "Nothing in $category yet" else "No matches for \"$term\"",
                        message = if (term.isEmpty()) {
                            "Try another category to browse the catalogue."
                        } else {
                            "Try different words or another category."
                        }
                    )
                } else {
                    if (topPicks.isNotEmpty()) {
                        TopPicksRow(topPicks, onNavigate)
                    }
                    if (curated.isNotEmpty()) {
                        CuratedRow(curated, onNavigate)
                    }
                    if (prompts.isNotEmpty()) {
                        PromptsRow(prompts, onNavigate)
                    }
                    if (mine.isNotEmpty()) {
                        MadeByYouRow(mine, onNavigate)
                    }
                }
                Spacer(Modifier.height(GsMotion.spaceL))
            }
        }
    }
}

/** Combined query gate — empty term passes everything, else any-field contains. */
private fun matchesTerm(term: String, vararg fields: String): Boolean =
    term.isEmpty() || fields.any { it.contains(term, ignoreCase = true) }

@Composable
private fun CategoryChips(categories: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
    ) {
        categories.forEach { label ->
            GsChip(text = label, selected = label == selected) { onSelect(label) }
        }
    }
}

@Composable
private fun TopPicksRow(assistants: List<AssistantSample>, onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
        GsSectionHeader(title = "Top picks")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS),
            contentPadding = PaddingValues(horizontal = GsMotion.spaceXS)
        ) {
            items(assistants, key = { it.id }) { assistant ->
                GsCard(
                    onClick = { onNavigate(GsRoutes.assistant(assistant.id)) },
                    modifier = Modifier.width(232.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().height(132.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        AssistantBadge()
                        Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceXS)) {
                            Text(
                                text = assistant.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = assistant.category,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CuratedRow(assistants: List<AssistantSample>, onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
        GsSectionHeader(title = "Curated for you")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS),
            contentPadding = PaddingValues(horizontal = GsMotion.spaceXS)
        ) {
            // The numeral is the card's editorial ordinal — the position in
            // the curated catalogue, not a measured rank.
            itemsIndexedRanked(assistants) { rank, assistant ->
                GsCard(
                    onClick = { onNavigate(GsRoutes.assistant(assistant.id)) },
                    modifier = Modifier.width(148.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().height(124.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = rank.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceXS)) {
                            Text(
                                text = assistant.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = assistant.category,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Ranked lazy items — index flows in as the curated position (1-based). */
private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedRanked(
    assistants: List<AssistantSample>,
    card: @Composable (Int, AssistantSample) -> Unit
) {
    assistants.forEachIndexed { index, assistant ->
        item(key = assistant.id) { card(index + 1, assistant) }
    }
}

@Composable
private fun PromptsRow(prompts: List<ExplorePrompt>, onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
        GsSectionHeader(title = "Helpful prompts")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS),
            contentPadding = PaddingValues(horizontal = GsMotion.spaceXS)
        ) {
            items(prompts, key = { it.text }) { prompt ->
                GsCard(
                    onClick = { onNavigate(GsRoutes.chat(null, prompt.text)) },
                    modifier = Modifier.width(248.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().height(128.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = prompt.text,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = prompt.category,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MadeByYouRow(assistants: List<AssistantSample>, onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
        GsSectionHeader(title = "Made by you")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS),
            contentPadding = PaddingValues(horizontal = GsMotion.spaceXS)
        ) {
            items(assistants, key = { it.id }) { assistant ->
                GsCard(
                    onClick = { onNavigate(GsRoutes.assistant(assistant.id)) },
                    modifier = Modifier.width(184.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().height(124.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        AssistantBadge(person = true)
                        Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceXS)) {
                            Text(
                                text = assistant.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val flags = buildList {
                                if (assistant.pinned) add("Pinned")
                                if (assistant.published) add("Published")
                            }
                            Text(
                                text = if (flags.isEmpty()) "You" else "You · ${flags.joinToString(" · ")}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantBadge(person: Boolean = false) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.size(38.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (person) Icons.Outlined.Person else Icons.Outlined.SmartToy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
