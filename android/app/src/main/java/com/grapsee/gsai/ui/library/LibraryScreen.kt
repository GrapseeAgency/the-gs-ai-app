package com.grapsee.gsai.ui.library

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.TipsAndUpdates
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.components.GsCard
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.components.GsEmptyState
import com.grapsee.gsai.ui.components.GsListItem
import com.grapsee.gsai.ui.components.GsScreenScaffold
import com.grapsee.gsai.ui.components.GsSectionHeader
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.GsMotion

/**
 * AERUO KINETIC — LIBRARY, the personal knowledge space.
 * Filter chips switch the saved-items index; collections sit above as
 * horizontal editorial cards. Files filter has no samples yet → empty state.
 */

private val libraryFilters = listOf(
    "All", "Messages", "Documents", "Images", "Files", "Prompts"
)

private data class LibraryItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val kind: String
)

private val libraryItems = listOf(
    LibraryItem("Q3 report.pdf", "Document · 1h ago", Icons.Outlined.Description, "Documents"),
    LibraryItem("Brand guidelines.docx", "Document · 3d ago", Icons.Outlined.Description, "Documents"),
    LibraryItem("hero-banner-v2.png", "Image · yesterday", Icons.Outlined.Image, "Images"),
    LibraryItem("Cold email sequence prompt", "Prompt · last week", Icons.Outlined.TipsAndUpdates, "Prompts")
)

private data class Collection(val name: String, val count: String)

private val collections = listOf(
    Collection("Brand kit", "12 items"),
    Collection("Research papers", "8 items"),
    Collection("Design refs", "21 items")
)

@Composable
fun LibraryScreen(onNavigate: (String) -> Unit) {
    var selectedFilter by remember { mutableIntStateOf(0) }
    val filter = libraryFilters[selectedFilter]
    val visibleItems = if (selectedFilter == 0) libraryItems
    else libraryItems.filter { it.kind == filter }

    // Real saves from the chat surface — persist in Room, render above seeds.
    val savedItems by remember { ServiceLocator.chat.savedItems() }
        .collectAsState(initial = emptyList())
    val realVisible = savedItems.filter { selectedFilter == 0 || filter == "Messages" }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        GsScreenScaffold(
            title = "Library",
            actions = {
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Add to library",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(GsMotion.spaceL)
            ) {
                Spacer(Modifier.height(GsMotion.spaceS))
                FilterChips(selectedFilter, onSelect = { selectedFilter = it })
                CollectionsSection()
                if (realVisible.isEmpty() && visibleItems.isEmpty()) {
                    GsEmptyState(
                        icon = Icons.Outlined.Folder,
                        title = "No ${filter.lowercase()} yet",
                        message = "Saved ${filter.lowercase()} will collect here as you work."
                    )
                } else {
                    realVisible.forEach { item ->
                        GsListItem(
                            title = item.title,
                            subtitle = "Saved message",
                            leading = { ItemBadge(Icons.Outlined.BookmarkBorder) },
                            trailing = {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = "More",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = { onNavigate(GsRoutes.chat(null)) }
                        )
                    }
                    visibleItems.forEach { item ->
                        GsListItem(
                            title = item.title,
                            subtitle = item.subtitle,
                            leading = { ItemBadge(item.icon) },
                            trailing = {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = "More",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = { onNavigate(GsRoutes.chat(null)) }
                        )
                    }
                }
                Spacer(Modifier.height(GsMotion.spaceL))
            }
        }
    }
}

@Composable
private fun FilterChips(selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
    ) {
        libraryFilters.forEachIndexed { index, label ->
            GsChip(text = label, selected = index == selectedIndex) { onSelect(index) }
        }
    }
}

@Composable
private fun CollectionsSection() {
    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
        GsSectionHeader(title = "Collections")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
        ) {
            collections.forEach { collection ->
                GsCard(modifier = Modifier.width(150.dp)) {
                    Text(
                        text = collection.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = collection.count,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemBadge(icon: ImageVector) {
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
