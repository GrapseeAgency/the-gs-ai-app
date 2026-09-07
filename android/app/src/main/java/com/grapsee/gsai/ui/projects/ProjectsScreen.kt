package com.grapsee.gsai.ui.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.ProjectStore
import com.grapsee.gsai.ui.components.GsCard
import com.grapsee.gsai.ui.components.GsScreenScaffold
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.GsMotion
import java.time.Duration
import java.time.OffsetDateTime

/**
 * AERUO KINETIC — PROJECTS dashboard.
 * Every card answers from [ProjectStore]: user-created projects with real
 * chat counts and a timestamp that moves when the reader touches the
 * project. The lead card and the toolbar plus both open the create dialog —
 * no dead buttons, no seeded rows; a fresh install shows an honest empty
 * state until the reader builds their first project.
 */

internal fun relativeMoment(iso: String): String = runCatching {
    val seconds = Duration.between(OffsetDateTime.parse(iso), OffsetDateTime.now()).seconds
    when {
        seconds < 60 -> "just now"
        seconds < 3600 -> "${seconds / 60}m ago"
        seconds < 86400 -> "${seconds / 3600}h ago"
        seconds < 7 * 86400 -> "${seconds / 86400}d ago"
        else -> "earlier"
    }
}.getOrDefault("earlier")

@Composable
fun ProjectsScreen(onNavigate: (String) -> Unit) {
    var showCreate by remember { mutableStateOf(false) }
    val projects = ProjectStore.projects

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        GsScreenScaffold(
            title = "Projects",
            actions = {
                IconButton(onClick = { showCreate = true }) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "New project",
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
                NewProjectCard(onClick = { showCreate = true })
                if (projects.isEmpty()) {
                    Text(
                        text = "No projects yet — create one to bundle chats, instructions and context.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                projects.forEach { project ->
                    GsCard(onClick = { onNavigate(GsRoutes.project(project.id)) }) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceXS)
                        ) {
                            Text(
                                text = project.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (project.blurb.isNotBlank()) {
                                Text(
                                    text = project.blurb,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "${project.chatIds.size} chats · updated ${relativeMoment(project.updatedAt)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Spacer(Modifier.height(GsMotion.spaceL))
            }
        }
    }

    if (showCreate) {
        ProjectFieldsDialog(
            title = "New project",
            confirmLabel = "Create",
            initialName = "",
            initialBlurb = "",
            initialInstructions = "",
            onDismiss = { showCreate = false },
            onConfirm = { name, blurb, instructions ->
                ProjectStore.create(name, blurb, instructions)
                showCreate = false
            }
        )
    }
}

@Composable
private fun NewProjectCard(onClick: () -> Unit) {
    GsCard(onClick = onClick) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Text(
                text = "New project",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Bundle chats, files and instructions",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Shared create/edit fields — one dialog shape for both entry points. */
@Composable
internal fun ProjectFieldsDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    initialBlurb: String,
    initialInstructions: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, blurb: String, instructions: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var blurb by remember { mutableStateOf(initialBlurb) }
    var instructions by remember { mutableStateOf(initialInstructions) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = blurb,
                    onValueChange = { blurb = it },
                    label = { Text("What is it for? (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text("Custom instructions (optional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, blurb, instructions) },
                enabled = name.isNotBlank()
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
