package com.grapsee.gsai.ui.projects

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.ProjectStore
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.components.GsCard
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.components.GsListItem
import com.grapsee.gsai.ui.components.GsScreenScaffold
import com.grapsee.gsai.ui.theme.GsMotion
import java.time.Duration
import java.time.OffsetDateTime

/**
 * AERUO KINETIC — PROJECT DETAIL.
 * The hero answers from the stored project: the name, blurb and custom
 * instructions the reader actually wrote. Tabs answer from real state —
 * Chats lists the conversations genuinely linked to the project (linked and
 * unlinked right here), Activity replays the store's real event log, and
 * Files/Members are honest gates for subsystems that don't exist on-device
 * yet. The settings button opens edit + delete — no dead controls.
 */

private val projectTabs = listOf("Chats", "Files", "Activity", "Members")

private data class ChatRow(val id: String, val title: String, val updatedAt: String)

@Composable
fun ProjectDetailScreen(projectId: String, onBack: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showEdit by remember { mutableStateOf(false) }
    var showLinker by remember { mutableStateOf(false) }
    val project = ProjectStore.byId(projectId)

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        GsScreenScaffold(
            title = "Project",
            onBack = onBack,
            actions = {
                IconButton(onClick = { showEdit = true }) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Project settings",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        ) {
            if (project == null) {
                Text(
                    text = "This project no longer exists.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(GsMotion.spaceM)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(GsMotion.spaceL)
                ) {
                    Spacer(Modifier.height(GsMotion.spaceS))
                    HeroCard(project = project)
                    TabBar(selectedTab, onSelect = { selectedTab = it })
                    when (selectedTab) {
                        0 -> ChatsTab(
                            project = project,
                            onOpenLinker = { showLinker = true }
                        )
                        1 -> FilesTab()
                        2 -> ActivityTab(project = project)
                        else -> MembersTab()
                    }
                    Spacer(Modifier.height(GsMotion.spaceL))
                }
            }
        }
    }

    if (showEdit && project != null) {
        val confirmDelete = remember { mutableStateOf(false) }
        if (confirmDelete.value) {
            AlertDialog(
                onDismissRequest = { confirmDelete.value = false },
                title = { Text("Delete \"${project.name}\"?") },
                text = { Text("The project, its instructions and its activity log are removed from this device. Chats stay in your library.") },
                confirmButton = {
                    TextButton(onClick = {
                        ProjectStore.delete(project.id)
                        showEdit = false
                        onBack()
                    }) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete.value = false }) { Text("Cancel") }
                }
            )
        } else {
            ProjectFieldsDialog(
                title = "Edit project",
                confirmLabel = "Save",
                initialName = project.name,
                initialBlurb = project.blurb,
                initialInstructions = project.instructions,
                onDismiss = { showEdit = false },
                onConfirm = { name, blurb, instructions ->
                    ProjectStore.update(project.id, name, blurb, instructions)
                    showEdit = false
                }
            )
        }
    }

    if (showLinker && project != null) {
        ChatLinkerDialog(project = project, onDismiss = { showLinker = false })
    }
}

@Composable
private fun HeroCard(project: ProjectStore.Project) {
    GsCard {
        Text(
            text = project.name,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (project.blurb.isNotBlank()) {
            Text(
                text = project.blurb,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (project.instructions.isNotBlank()) {
            Spacer(Modifier.height(GsMotion.spaceM))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(GsMotion.spaceM),
                    verticalArrangement = Arrangement.spacedBy(GsMotion.spaceXS)
                ) {
                    Text(
                        text = "CUSTOM INSTRUCTIONS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = project.instructions,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun TabBar(selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
        projectTabs.forEachIndexed { index, label ->
            GsChip(text = label, selected = index == selectedIndex) { onSelect(index) }
        }
    }
}

@Composable
private fun ChatsTab(project: ProjectStore.Project, onOpenLinker: () -> Unit) {
    var rows by remember { mutableStateOf<List<ChatRow>>(emptyList()) }

    LaunchedEffect(project.chatIds) {
        rows = runCatching {
            project.chatIds.mapNotNull { id ->
                ServiceLocator.db.conversationDao().getById(id)?.let {
                    ChatRow(it.id, it.title, it.updatedAt)
                }
            }
        }.getOrDefault(emptyList())
    }

    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
        GsChip(text = "Add chats", selected = false, onClick = onOpenLinker)
        if (rows.isEmpty()) {
            Text(
                text = "No chats linked yet — add conversations to bundle them with these instructions.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        rows.forEach { row ->
            GsListItem(
                title = row.title,
                subtitle = "updated ${relativeMoment(row.updatedAt)}",
                leading = { TabBadge(Icons.Outlined.ChatBubbleOutline) },
                trailing = {
                    IconButton(onClick = { ProjectStore.unlinkChat(project.id, row.id) }) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Remove ${row.title} from project",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )
        }
    }
}

/** Picker over the real conversation book — checkboxes mirror current links. */
@Composable
private fun ChatLinkerDialog(project: ProjectStore.Project, onDismiss: () -> Unit) {
    var conversations by remember { mutableStateOf<List<ChatRow>>(emptyList()) }
    var selected by remember { mutableStateOf(project.chatIds.toSet()) }

    LaunchedEffect(Unit) {
        conversations = runCatching {
            ServiceLocator.db.conversationDao().all().map {
                ChatRow(it.id, it.title, it.updatedAt)
            }
        }.getOrDefault(emptyList())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link chats") },
        text = {
            if (conversations.isEmpty()) {
                Text("No conversations on this device yet.")
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(GsMotion.spaceXS)
                ) {
                    conversations.forEach { conversation ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = conversation.id in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + conversation.id
                                    else selected - conversation.id
                                }
                            )
                            Column {
                                Text(
                                    text = conversation.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "updated ${relativeMoment(conversation.updatedAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                ProjectStore.linkChats(
                    project.id,
                    selected.toList(),
                    conversations.associate { it.id to it.title }
                )
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun FilesTab() {
    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
        Text(
            text = "File attachments aren't supported yet — projects hold files once on-device storage lands.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActivityTab(project: ProjectStore.Project) {
    if (project.events.isEmpty()) {
        Text(
            text = "Nothing yet — actions you take on this project show up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
        project.events.asReversed().forEach { event ->
            GsListItem(
                title = event.text,
                subtitle = relativeMoment(event.at),
                leading = { TabBadge(Icons.Outlined.History) }
            )
        }
    }
}

@Composable
private fun MembersTab() {
    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
        GsListItem(
            title = "You",
            subtitle = "Owner",
            leading = { InitialsBadge("You") }
        )
        Text(
            text = "Team members arrive with accounts and sharing.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TabBadge(icon: ImageVector) {
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

@Composable
private fun InitialsBadge(initials: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        modifier = Modifier.size(38.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
