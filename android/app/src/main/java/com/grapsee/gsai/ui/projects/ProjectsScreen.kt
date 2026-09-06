package com.grapsee.gsai.ui.projects

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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.components.GsCard
import com.grapsee.gsai.ui.components.GsScreenScaffold
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.GsMotion

/**
 * AERUO KINETIC — PROJECTS dashboard.
 * One "New project" lead card (static) + three project cards that push the
 * detail screen with their sample ids.
 */

private data class ProjectSummary(
    val id: String,
    val name: String,
    val blurb: String,
    val meta: String,
    val updated: String
)

private val projectSummaries = listOf(
    ProjectSummary(
        id = "project-brand",
        name = "Brand Refresh 2025",
        blurb = "Reposition the flagship line — voice, palette and packaging across every touchpoint.",
        meta = "8 chats · 14 files · 3 members",
        updated = "2h ago"
    ),
    ProjectSummary(
        id = "project-launch",
        name = "Q3 Launch Plan",
        blurb = "Go-to-market plan for the Q3 release — channels, messaging and milestones.",
        meta = "12 chats · 9 files · 2 members",
        updated = "yesterday"
    ),
    ProjectSummary(
        id = "project-research",
        name = "Research: AI market",
        blurb = "Market sizing and competitor scan for the AI platform space.",
        meta = "5 chats · 21 files · 4 members",
        updated = "3d ago"
    )
)

@Composable
fun ProjectsScreen(onNavigate: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        GsScreenScaffold(
            title = "Projects",
            actions = {
                IconButton(onClick = {}) {
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
                NewProjectCard()
                projectSummaries.forEach { project ->
                    ProjectCard(project = project) {
                        onNavigate(GsRoutes.project(project.id))
                    }
                }
                Spacer(Modifier.height(GsMotion.spaceL))
            }
        }
    }
}

@Composable
private fun NewProjectCard() {
    GsCard(onClick = {}) {
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
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProjectCard(project: ProjectSummary, onClick: () -> Unit) {
    GsCard(onClick = onClick) {
        Text(
            text = project.name,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = project.blurb,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(GsMotion.spaceS))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = project.meta,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Updated ${project.updated}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
