package com.grapsee.gsai.ui.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.TipsAndUpdates
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.selection.SelectionContainer
import com.grapsee.gsai.data.local.SavedItemEntity
import com.grapsee.gsai.di.ServiceLocator
import kotlinx.coroutines.launch
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

/** A saved item's kind string maps onto the Library chip that owns it. */
private fun filterLabelFor(kind: String): String = when (kind) {
    "image" -> "Images"
    "document" -> "Documents"
    "file" -> "Files"
    "prompt" -> "Prompts"
    else -> "Messages"
}

@Composable
fun LibraryScreen(onNavigate: (String) -> Unit) {
    var selectedFilter by remember { mutableIntStateOf(0) }
    val filter = libraryFilters[selectedFilter]
    val visibleItems = if (selectedFilter == 0) libraryItems
    else libraryItems.filter { it.kind == filter }

    // Real saves from the chat surface and the studios — persist in Room, render
    // under the chip that owns their kind (messages, images, documents…).
    val savedItems by remember { ServiceLocator.chat.savedItems() }
        .collectAsState(initial = emptyList())
    val realVisible = savedItems.filter { selectedFilter == 0 || filterLabelFor(it.kind) == filter }

    // Item management: tap a real save to read it in full, copy or remove it.
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    var viewingItem by remember { mutableStateOf<SavedItemEntity?>(null) }
    val showSnack: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

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
                        val (badge, label) = when (item.kind) {
                            "image" -> Icons.Outlined.Image to "Saved image"
                            "document" -> Icons.Outlined.Description to "Saved document"
                            else -> Icons.Outlined.BookmarkBorder to "Saved message"
                        }
                        GsListItem(
                            title = item.title,
                            subtitle = label,
                            leading = { ItemBadge(badge) },
                            trailing = {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = "More",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = { viewingItem = item }
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

        SnackbarHost(hostState = snackbarHostState)

        viewingItem?.let { item ->
            SavedItemSheet(
                item = item,
                onDismiss = { viewingItem = null },
                onCopy = { text ->
                    clipboard.setText(AnnotatedString(text))
                    showSnack("Copied")
                },
                onContinueInChat = {
                    val text = item.content
                    viewingItem = null
                    onNavigate(GsRoutes.chat(null, text))
                },
                onDelete = {
                    val target = item
                    viewingItem = null
                    scope.launch {
                        runCatching { ServiceLocator.chat.deleteSavedItem(target.id) }
                            .onSuccess { showSnack("Removed from Library") }
                            .onFailure { showSnack("Couldn't remove right now") }
                    }
                }
            )
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

/**
 * Library item detail: the saved turn in full, selectable for partial copies,
 * with Copy and Delete as the row's real actions. Delete is instant and
 * local — Room is the source of truth, so the list updates itself.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SavedItemSheet(
    item: SavedItemEntity,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
    onContinueInChat: () -> Unit,
    onDelete: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = GsMotion.spaceM)
                .padding(bottom = GsMotion.spaceL),
            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.BookmarkBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
            Text(
                text = when (item.kind) {
                    "image" -> "Saved image"
                    "document" -> "Saved document"
                    else -> "Saved message"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline
            )
            SelectionContainer {
                Text(
                    text = item.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                TextButton(onClick = onContinueInChat) {
                    Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Continue in chat")
                }
                TextButton(onClick = { onCopy(item.content) }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy")
                }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
