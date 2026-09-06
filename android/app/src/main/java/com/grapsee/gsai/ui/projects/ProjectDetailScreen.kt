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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.components.GsCard
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.components.GsListItem
import com.grapsee.gsai.ui.components.GsScreenScaffold
import com.grapsee.gsai.ui.theme.GsMotion

/**
 * AERUO KINETIC — PROJECT DETAIL.
 * Hero card with custom-instructions preview, then a segmented chip tab bar:
 * Chats / Files / Activity / Members. Pure pass-through composables — no nav.
 */

private data class ProjectSample(val name: String, val blurb: String)

private fun projectSample(id: String): ProjectSample = when (id) {
    "project-brand" -> ProjectSample(
        "Brand Refresh 2025",
        "Reposition the flagship line — voice, palette and packaging across every touchpoint."
    )
    "project-launch" -> ProjectSample(
        "Q3 Launch Plan",
        "Go-to-market plan for the Q3 release — channels, messaging and milestones."
    )
    "project-research" -> ProjectSample(
        "Research: AI market",
        "Market sizing and competitor scan for the AI platform space."
    )
    else -> ProjectSample(
        "New Project",
        "Give this project a purpose — it bundles chats, files, instructions and members."
    )
}

private val projectTabs = listOf("Chats", "Files", "Activity", "Members")

@Composable
fun ProjectDetailScreen(projectId: String, onBack: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val sample = projectSample(projectId)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        GsScreenScaffold(
            title = "Project",
            onBack = onBack,
            actions = {
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Project settings",
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
                HeroCard(sample)
                TabBar(selectedTab, onSelect = { selectedTab = it })
                when (selectedTab) {
                    0 -> ChatsTab()
                    1 -> FilesTab()
                    2 -> ActivityTab()
                    else -> MembersTab()
                }
                Spacer(Modifier.height(GsMotion.spaceL))
            }
        }
    }
}

@Composable
private fun HeroCard(sample: ProjectSample) {
    GsCard {
        Text(
            text = sample.name,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = sample.blurb,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
                    text = "Always use British English. Cite sources for market data. Keep decks under 12 slides.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
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
private fun ChatsTab() {
    val chats = listOf(
        Triple("Voice & tone workshop", "2h ago · 18 messages", Icons.Outlined.ChatBubbleOutline),
        Triple("Packaging copy round 2", "Yesterday · 24 messages", Icons.Outlined.ChatBubbleOutline),
        Triple("Competitor palette scan", "3d ago · 9 messages", Icons.Outlined.ChatBubbleOutline)
    )
    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
        chats.forEach { (title, subtitle, icon) ->
            GsListItem(
                title = title,
                subtitle = subtitle,
                leading = { TabBadge(icon) }
            )
        }
    }
}

@Composable
private fun FilesTab() {
    val files = listOf(
        "brief.pdf" to Icons.Outlined.PictureAsPdf,
        "logo-explorations.png" to Icons.Outlined.Image,
        "budget-v3.xlsx" to Icons.Outlined.TableChart,
        "messaging.docx" to Icons.Outlined.Description
    )
    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
        files.forEachIndexed { index, (name, icon) ->
            GsListItem(
                title = name,
                subtitle = "Added ${listOf("1h ago", "yesterday", "4d ago", "last week")[index]}",
                leading = { TabBadge(icon) }
            )
        }
    }
}

@Composable
private fun ActivityTab() {
    val activity = listOf(
        Triple("Maya added brief.pdf", "1h ago", Icons.Outlined.Description),
        Triple("Tom started ‘Packaging copy round 2’", "3h ago", Icons.Outlined.ChatBubbleOutline),
        Triple("Maya shared a summary with the team", "yesterday", Icons.Outlined.Group),
        Triple("Alex joined the project", "2d ago", Icons.Outlined.Person)
    )
    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
        activity.forEach { (title, subtitle, icon) ->
            GsListItem(
                title = title,
                subtitle = subtitle,
                leading = { TabBadge(icon) }
            )
        }
    }
}

@Composable
private fun MembersTab() {
    val members = listOf(
        Triple("Maya Kessler", "MK", "Owner"),
        Triple("Tom Reyes", "TR", "Editor"),
        Triple("Alex Lin", "AL", "Viewer")
    )
    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
        members.forEach { (name, initials, role) ->
            GsListItem(
                title = name,
                subtitle = role,
                leading = { InitialsBadge(initials) },
                trailing = {
                    GsChip(text = role, selected = false, onClick = {})
                }
            )
        }
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
            Text(
                text = initials,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
