package com.grapsee.gsai.ui.assistants

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.AssistantsStore
import com.grapsee.gsai.data.model.AssistantSample
import com.grapsee.gsai.data.model.SampleData
import com.grapsee.gsai.ui.components.GsCard
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.components.GsEmptyState
import com.grapsee.gsai.ui.components.GsScreenScaffold
import com.grapsee.gsai.ui.components.SheetAction
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.GsMotion

private val segmentTabs = listOf("Marketplace", "My assistants", "Favourites", "Published", "Archived")

@Composable
fun AssistantsScreen(onNavigate: (String) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    // Real store wiring: user assistants, favourites, pin/archive — all local-first.
    val userAssistants by AssistantsStore.assistants.collectAsState()
    val favourites by AssistantsStore.favourites.collectAsState()
    var actionTarget by remember { mutableStateOf<AssistantSample?>(null) }
    var deleteTarget by remember { mutableStateOf<AssistantSample?>(null) }

    GsScreenScaffold(
        title = "Assistants",
        actions = {
            IconButton(onClick = { onNavigate(GsRoutes.ASSISTANT_CREATE) }) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "Create assistant",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceM)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
            ) {
                segmentTabs.forEachIndexed { index, label ->
                    GsChip(text = label, selected = tab == index, onClick = { tab = index })
                }
            }

            val visible = when (tab) {
                1 -> userAssistants.filter { !it.archived }.sortedByDescending { it.pinned }
                2 -> (SampleData.assistants + userAssistants.filter { !it.archived })
                    .filter { it.id in favourites }
                3 -> SampleData.assistants.filter { it.published } +
                    userAssistants.filter { it.published && !it.archived }
                4 -> userAssistants.filter { it.archived }
                else -> SampleData.assistants +
                    userAssistants.filter { it.published && !it.archived }
            }

            if (tab == 0) {
                val featured = SampleData.assistants.first()
                FeaturedCard(
                    assistant = featured,
                    onClick = { onNavigate(GsRoutes.assistant(featured.id)) }
                )
            }

            if (visible.isEmpty()) {
                val (emptyTitle, emptyMessage) = when (tab) {
                    1 -> "No assistants yet" to "Create your first assistant and it will live here."
                    2 -> "Nothing starred yet" to "Tap the heart on any assistant to keep it close."
                    4 -> "Nothing archived" to "Archived assistants rest here — unarchive any time."
                    else -> "Nothing here yet" to "Assistants you create or save will appear in this space."
                }
                GsEmptyState(
                    icon = Icons.Outlined.SmartToy,
                    title = emptyTitle,
                    message = emptyMessage
                )
            } else {
                visible.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowItems.forEach { assistant ->
                            AssistantCard(
                                assistant = assistant,
                                modifier = Modifier.weight(1f),
                                favourited = assistant.id in favourites,
                                onClick = { onNavigate(GsRoutes.assistant(assistant.id)) },
                                onLongClick = { actionTarget = assistant }
                            )
                        }
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(GsMotion.spaceL))
        }
    }

    actionTarget?.let { target ->
        val isUserAssistant = userAssistants.any { it.id == target.id }
        AssistantActionsSheet(
            assistant = target,
            isUserAssistant = isUserAssistant,
            favourited = target.id in favourites,
            onDismiss = { actionTarget = null },
            onToggleFavourite = {
                AssistantsStore.toggleFavourite(target.id)
                actionTarget = null
            },
            onTogglePin = {
                AssistantsStore.togglePin(target.id)
                actionTarget = null
            },
            onToggleArchive = {
                AssistantsStore.setArchived(target.id, !target.archived)
                actionTarget = null
            },
            onEdit = {
                actionTarget = null
                onNavigate(GsRoutes.assistantEdit(target.id))
            },
            onDeleteRequest = {
                deleteTarget = target
                actionTarget = null
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete assistant") },
            text = {
                Text("“${target.name}” will be removed from My assistants. Chats you started with it stay in your history.")
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    AssistantsStore.delete(target.id)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Keep") }
            }
        )
    }
}

/** Featured marketplace card — flat surface with a 4dp aurora-teal accent bar (no gradient). */
@Composable
private fun FeaturedCard(assistant: AssistantSample, onClick: () -> Unit) {
    GsCard(onClick = onClick) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(GsMotion.spaceM))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(GsMotion.spaceXS)
            ) {
                Text(
                    text = "FEATURED",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = assistant.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = assistant.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "★ ${assistant.rating} · ${assistant.uses} uses · ${assistant.category}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Card with real state: tap opens the profile, long-press opens the action
 * sheet (favourite for everything; pin/archive/edit/delete for user-owned).
 * Pin and favourite badges render inline so state is visible at a glance.
 */
@Composable
private fun AssistantCard(
    assistant: AssistantSample,
    modifier: Modifier = Modifier,
    favourited: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(modifier = modifier) {
        GsCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick,
            // Long-press opens the assistant's action sheet through GsCard's
            // combinedClickable — one gesture pipeline, and TalkBack gets the
            // hold as a labelled action instead of a silent pointer rule.
            onLongClick = onLongClick
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.SmartToy,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (assistant.pinned) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    if (assistant.pinned && favourited) {
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    if (favourited) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = "Favourited",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Text(
                    text = assistant.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = assistant.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "★ ${assistant.rating} · ${assistant.uses} uses",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Long-press actions — the ConversationActionsSheet idiom, assistant-flavoured. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AssistantActionsSheet(
    assistant: AssistantSample,
    isUserAssistant: Boolean,
    favourited: Boolean,
    onDismiss: () -> Unit,
    onToggleFavourite: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit,
    onEdit: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = GsMotion.spaceM)
                .padding(bottom = GsMotion.spaceL),
            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceXS)
        ) {
            Text(
                assistant.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = GsMotion.spaceS)
            )
            SheetAction(
                icon = if (favourited) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                label = if (favourited) "Remove from favourites" else "Favourite",
                onClick = onToggleFavourite
            )
            if (isUserAssistant) {
                SheetAction(
                    icon = Icons.Outlined.PushPin,
                    label = if (assistant.pinned) "Unpin" else "Pin to top",
                    onClick = onTogglePin
                )
                SheetAction(
                    icon = Icons.Outlined.Edit,
                    label = "Edit",
                    onClick = onEdit
                )
                SheetAction(
                    icon = Icons.Outlined.Archive,
                    label = if (assistant.archived) "Unarchive" else "Archive",
                    onClick = onToggleArchive
                )
                SheetAction(
                    icon = Icons.Outlined.Delete,
                    label = "Delete",
                    tone = MaterialTheme.colorScheme.error,
                    onClick = onDeleteRequest
                )
            }
        }
    }
}
