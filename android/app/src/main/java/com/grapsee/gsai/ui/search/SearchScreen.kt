package com.grapsee.gsai.ui.search

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
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.components.GsEmptyState
import com.grapsee.gsai.ui.components.GsInputBar
import com.grapsee.gsai.ui.components.GsListItem
import com.grapsee.gsai.ui.components.GsScreenScaffold
import com.grapsee.gsai.ui.components.GsSectionHeader
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.GsMotion

/**
 * AERUO KINETIC — SEARCH, the one-index entry point.
 * Live query bar + recent searches + toggleable filters; a typed query reveals
 * grouped static results with counts, a blank query shows the editorial
 * empty state. FTS5 wiring lands with the data layer.
 */

private val recentSearches = listOf(
    "pricing strategy",
    "kotlin coroutines",
    "brand guidelines"
)

private val searchFilters = listOf("Date", "Model", "Type", "Project", "Assistant")

@Composable
fun SearchScreen(onNavigate: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var activeFilters by remember { mutableStateOf(setOf<String>()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        GsScreenScaffold(title = "Search") {
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
                    onSend = {},
                    placeholder = "Search everything…"
                )

                Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                    GsSectionHeader(title = "Recent searches")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
                    ) {
                        recentSearches.forEach { recent ->
                            GsChip(text = recent, selected = false) { query = recent }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
                ) {
                    searchFilters.forEach { filter ->
                        GsChip(
                            text = filter,
                            selected = filter in activeFilters
                        ) {
                            activeFilters = if (filter in activeFilters) {
                                activeFilters - filter
                            } else {
                                activeFilters + filter
                            }
                        }
                    }
                }

                if (query.isBlank()) {
                    GsEmptyState(
                        icon = Icons.Outlined.Search,
                        title = "Search everything",
                        message = "Conversations, messages, files, assistants, projects and prompts — one index."
                    )
                } else {
                    ResultSection(title = "Conversations", count = "2") {
                        GsListItem(
                            title = "Brand voice guidelines",
                            subtitle = "Chat · 2h ago",
                            leading = { ResultBadge(Icons.Outlined.ChatBubbleOutline) },
                            onClick = { onNavigate(GsRoutes.chat("demo-1")) }
                        )
                        GsListItem(
                            title = "Pricing strategy brainstorm",
                            subtitle = "Chat · yesterday",
                            leading = { ResultBadge(Icons.Outlined.ChatBubbleOutline) },
                            onClick = { onNavigate(GsRoutes.chat("demo-1")) }
                        )
                    }
                    ResultSection(title = "Messages", count = "2") {
                        GsListItem(
                            title = "Saved: pricing strategy idea",
                            subtitle = "Saved message · 2d ago",
                            leading = { ResultBadge(Icons.Outlined.ChatBubbleOutline) },
                            onClick = { onNavigate(GsRoutes.chat("demo-2")) }
                        )
                        GsListItem(
                            title = "Re: launch checklist",
                            subtitle = "Message · yesterday",
                            leading = { ResultBadge(Icons.Outlined.ChatBubbleOutline) },
                            onClick = { onNavigate(GsRoutes.chat("demo-2")) }
                        )
                    }
                    ResultSection(title = "Files", count = "1") {
                        GsListItem(
                            title = "Q3 report.pdf",
                            subtitle = "Document · 1h ago",
                            leading = { ResultBadge(Icons.Outlined.Description) },
                            onClick = { onNavigate(GsRoutes.chat(null)) }
                        )
                    }
                    ResultSection(title = "Assistants", count = "1") {
                        GsListItem(
                            title = "MarketMind",
                            subtitle = "by Ana Duarte · ★ 4.9",
                            leading = { ResultBadge(Icons.Outlined.SmartToy) },
                            onClick = { onNavigate(GsRoutes.assistant("asst-1")) }
                        )
                    }
                    ResultSection(title = "Projects", count = "1") {
                        GsListItem(
                            title = "Brand Refresh 2025",
                            subtitle = "8 chats · 14 files · 3 members",
                            leading = { ResultBadge(Icons.Outlined.Folder) },
                            onClick = { onNavigate(GsRoutes.project("project-brand")) }
                        )
                    }
                }
                Spacer(Modifier.height(GsMotion.spaceL))
            }
        }
    }
}

@Composable
private fun ResultSection(
    title: String,
    count: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
        GsSectionHeader(title = title, actionLabel = count, onAction = {})
        content()
    }
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
