package com.grapsee.gsai.ui.create

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
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.TipsAndUpdates
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.components.GsCard
import com.grapsee.gsai.ui.components.GsEmptyState
import com.grapsee.gsai.ui.components.GsScreenScaffold
import com.grapsee.gsai.ui.components.GsSectionHeader
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.GsMotion

/**
 * AERUO KINETIC — CREATE, the generation lobby.
 * "Make anything" editorial hero + ten tool cards; each tool opens its own
 * workspace later — for now everything funnels into a chat, except the
 * assistant builder which has its own route.
 */

private data class CreateTool(
    val title: String,
    val blurb: String,
    val icon: ImageVector,
    val route: String
)

// Workspace tools route to their dedicated studios (Task 8-c — wired in GsNavHost);
// everything else still funnels into a fresh chat.
private val createTools = listOf(
    CreateTool("AI image", "Generate art from a prompt", Icons.Outlined.Palette, "create/image"),
    CreateTool("Image edit", "Retouch and restyle", Icons.Outlined.AutoFixHigh, "create/image"),
    CreateTool("Document", "Reports, briefs and memos", Icons.Outlined.Description, GsRoutes.chat(null)),
    CreateTool("Presentation", "Decks from a single prompt", Icons.Outlined.Slideshow, GsRoutes.chat(null)),
    CreateTool("Spreadsheet", "Tables with live formulas", Icons.Outlined.TableChart, GsRoutes.chat(null)),
    CreateTool("Writing", "Drafts in your voice", Icons.Outlined.EditNote, "create/writing"),
    CreateTool("Code", "Snippets and scaffolds", Icons.Outlined.Code, "create/code"),
    CreateTool("Diagram", "Architecture and flows", Icons.Outlined.AccountTree, GsRoutes.chat(null)),
    CreateTool("Prompt builder", "Compose reusable prompts", Icons.Outlined.TipsAndUpdates, "create/prompt"),
    CreateTool("Assistant builder", "Design your own AI", Icons.Outlined.SmartToy, GsRoutes.ASSISTANT_CREATE)
)

@Composable
fun CreateScreen(onNavigate: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        GsScreenScaffold(title = "Create") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(GsMotion.spaceL)
            ) {
                Spacer(Modifier.height(GsMotion.spaceS))
                Text(
                    text = "Make anything",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                createTools.chunked(2).forEach { rowTools ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
                    ) {
                        rowTools.forEach { tool ->
                            CreateToolCard(
                                tool = tool,
                                modifier = Modifier.weight(1f),
                                onClick = { onNavigate(tool.route) }
                            )
                        }
                        if (rowTools.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                RecentCreationsSection()
                Spacer(Modifier.height(GsMotion.spaceL))
            }
        }
    }
}

@Composable
private fun CreateToolCard(
    tool: CreateTool,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    GsCard(modifier = modifier, onClick = onClick) {
        ToolBadge(tool.icon)
        Spacer(Modifier.height(GsMotion.spaceS))
        Text(
            text = tool.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = tool.blurb,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RecentCreationsSection() {
    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
        GsSectionHeader(title = "Recent creations")
        GsEmptyState(
            icon = Icons.Outlined.HourglassEmpty,
            title = "Nothing yet",
            message = "Your generated images, docs and decks will live here."
        )
    }
}

@Composable
private fun ToolBadge(icon: ImageVector) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.size(40.dp)
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
