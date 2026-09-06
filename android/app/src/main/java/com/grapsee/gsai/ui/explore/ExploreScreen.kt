package com.grapsee.gsai.ui.explore

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.components.GsCard
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.components.GsEmptyState
import com.grapsee.gsai.ui.components.GsInputBar
import com.grapsee.gsai.ui.components.GsListItem
import com.grapsee.gsai.ui.components.GsScreenScaffold
import com.grapsee.gsai.ui.components.GsSectionHeader
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.GsMotion

/**
 * AERUO KINETIC — EXPLORE, the discovery layer / AI app store.
 * Category chips filter the static catalogue; serif prompt cards carry the
 * editorial voice. Search bar is decorative until the index lands.
 */

private val exploreCategories = listOf(
    "All", "Coding", "Education", "Business", "Writing", "Productivity",
    "Research", "Design", "Mathematics", "Language", "Science", "Entertainment"
)

private data class ExploreAssistant(
    val id: String,
    val name: String,
    val author: String,
    val uses: String,
    val rating: String,
    val category: String
)

private val trendingAssistants = listOf(
    ExploreAssistant("asst-1", "WriteWell", "Maya Kessler", "12.4k uses", "★ 4.8", "Writing"),
    ExploreAssistant("asst-2", "CodeCompanion", "Tom Reyes", "8.1k uses", "★ 4.7", "Coding"),
    ExploreAssistant("asst-3", "MarketMind", "Ana Duarte", "6.3k uses", "★ 4.9", "Business"),
    ExploreAssistant("asst-4", "StudyBuddy", "Alex Lin", "5.2k uses", "★ 4.6", "Education")
)

private data class ExplorePrompt(val text: String, val category: String, val uses: String)

private val popularPrompts = listOf(
    ExplorePrompt("Turn rough notes into a crisp launch email", "Business", "3.2k uses"),
    ExplorePrompt("Plan my week around three priorities", "Productivity", "2.7k uses"),
    ExplorePrompt("Explain this codebase like I'm brand new", "Coding", "2.1k uses")
)

private data class ExploreTool(val name: String, val blurb: String, val icon: ImageVector, val category: String)

private val featuredTools = listOf(
    ExploreTool("Deep Research", "Multi-source answers with citations", Icons.Outlined.TravelExplore, "Research"),
    ExploreTool("Slide Studio", "Decks from a single prompt", Icons.Outlined.Slideshow, "Business")
)

@Composable
fun ExploreScreen(onNavigate: (String) -> Unit) {
    var selectedCategory by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    val category = exploreCategories[selectedCategory]

    val assistants = if (selectedCategory == 0) trendingAssistants
    else trendingAssistants.filter { it.category == category }
    val prompts = if (selectedCategory == 0) popularPrompts
    else popularPrompts.filter { it.category == category }
    val tools = if (selectedCategory == 0) featuredTools
    else featuredTools.filter { it.category == category }
    val nothingToShow = assistants.isEmpty() && prompts.isEmpty() && tools.isEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
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
                    onSend = {},
                    placeholder = "Search assistants, prompts, tools…"
                )
                CategoryChips(selectedCategory, onSelect = { selectedCategory = it })

                if (nothingToShow) {
                    GsEmptyState(
                        icon = Icons.Outlined.Search,
                        title = "Nothing in $category yet",
                        message = "Try another category — new assistants, prompts and tools land every week."
                    )
                } else {
                    if (assistants.isNotEmpty()) {
                        AssistantsSection(assistants, onNavigate)
                    }
                    if (prompts.isNotEmpty()) {
                        PromptsSection(prompts, onNavigate)
                    }
                    if (tools.isNotEmpty()) {
                        ToolsSection(tools, onNavigate)
                    }
                }
                Spacer(Modifier.height(GsMotion.spaceL))
            }
        }
    }
}

@Composable
private fun CategoryChips(selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
    ) {
        exploreCategories.forEachIndexed { index, label ->
            GsChip(text = label, selected = index == selectedIndex) { onSelect(index) }
        }
    }
}

@Composable
private fun AssistantsSection(
    assistants: List<ExploreAssistant>,
    onNavigate: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
        GsSectionHeader(title = "Trending assistants")
        assistants.forEach { assistant ->
            GsListItem(
                title = assistant.name,
                subtitle = "by ${assistant.author} · ${assistant.uses} · ${assistant.rating}",
                leading = { AssistantBadge() },
                onClick = { onNavigate(GsRoutes.assistant(assistant.id)) }
            )
        }
    }
}

@Composable
private fun PromptsSection(prompts: List<ExplorePrompt>, onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
        GsSectionHeader(title = "Popular prompts")
        prompts.forEach { prompt ->
            GsCard(onClick = { onNavigate(GsRoutes.chat(null)) }) {
                Text(
                    text = prompt.text,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(GsMotion.spaceXS))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = prompt.category,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "  ·  ${prompt.uses}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolsSection(tools: List<ExploreTool>, onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
        GsSectionHeader(title = "Featured AI tools")
        tools.forEach { tool ->
            GsCard(onClick = { onNavigate(GsRoutes.chat(null)) }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceM)
                ) {
                    ToolBadge(tool.icon)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(GsMotion.spaceXS)
                    ) {
                        Text(
                            text = tool.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = tool.blurb,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    GsChip(text = "Try it", selected = false) { onNavigate(GsRoutes.chat(null)) }
                }
            }
        }
    }
}

@Composable
private fun AssistantBadge() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.size(38.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Outlined.SmartToy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ToolBadge(icon: ImageVector) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        modifier = Modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
