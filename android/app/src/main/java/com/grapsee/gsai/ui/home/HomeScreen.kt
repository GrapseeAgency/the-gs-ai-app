package com.grapsee.gsai.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Subject
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.components.GsCard
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.components.GsInputBar
import com.grapsee.gsai.ui.components.GsListItem
import com.grapsee.gsai.ui.components.GsQuickActionTile
import com.grapsee.gsai.ui.components.GsSectionHeader
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.kineticPress
import com.grapsee.gsai.ui.theme.rememberAuroraBrush
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * AERUO KINETIC — HOME, the AI command centre.
 * Editorial greeting → universal input → quick actions → discovery ladder.
 * Aurora gradient appears exactly once: today's AI activity meter.
 */

private data class HomeAction(val label: String, val icon: ImageVector, val route: String)

private val homeActions = listOf(
    HomeAction("New chat", Icons.Outlined.Add, GsRoutes.chat(null)),
    HomeAction("Voice", Icons.Outlined.Mic, GsRoutes.VOICE),
    HomeAction("Analyse image", Icons.Outlined.Image, "vision"),
    HomeAction("Analyse document", Icons.Outlined.Description, GsRoutes.chat(null)),
    HomeAction("Write", Icons.Outlined.Edit, "create/writing"),
    HomeAction("Research", Icons.Outlined.TravelExplore, "research"),
    HomeAction("Code", Icons.Outlined.Code, "create/code"),
    HomeAction("Translate", Icons.Outlined.Translate, GsRoutes.chat(null)),
    HomeAction("Summarise", Icons.Outlined.Subject, GsRoutes.chat(null)),
    HomeAction("Brainstorm", Icons.Outlined.Psychology, GsRoutes.chat(null)),
    HomeAction("Generate image", Icons.Outlined.Palette, GsRoutes.chat(null))
)

private val suggestedPrompts = listOf(
    "Draft a launch plan",
    "Explain quantum computing",
    "Debug my Kotlin code",
    "Plan a trip to Kyoto"
)

private data class ResumeItem(val title: String, val subtitle: String, val route: String)

private val resumeItems = listOf(
    ResumeItem("Brand voice guidelines", "Chat · 2h ago", GsRoutes.chat("demo-1")),
    ResumeItem("Market research summary", "Research · yesterday", GsRoutes.chat("demo-2"))
)

private data class ConversationItem(val title: String, val subtitle: String, val route: String)

private val recentConversations = listOf(
    ConversationItem("Packaging copy round 2", "Yesterday · 24 messages", GsRoutes.chat("demo-3")),
    ConversationItem("Kotlin coroutine debug", "2 days ago · 12 messages", GsRoutes.chat("demo-4")),
    ConversationItem("Kyoto itinerary", "Last week · 31 messages", GsRoutes.chat("demo-5"))
)

private data class PinnedAssistant(val name: String, val category: String)

private val pinnedAssistants = listOf(
    PinnedAssistant("WriteWell", "Writing"),
    PinnedAssistant("CodeCompanion", "Coding")
)

private data class ProjectRowItem(val title: String, val subtitle: String, val route: String)

private val recentProjects = listOf(
    ProjectRowItem("Brand Refresh 2025", "8 chats · 14 files · 3 members", GsRoutes.project("project-brand")),
    ProjectRowItem("Q3 Launch Plan", "12 chats · 9 files · 2 members", GsRoutes.project("project-launch"))
)

private data class ModelCard(val name: String, val tagline: String, val icon: ImageVector)

private val recommendedModels = listOf(
    ModelCard("GS Swift", "Fast", Icons.Outlined.Speed),
    ModelCard("GS Balanced", "Everyday reasoning", Icons.Outlined.Balance),
    ModelCard("GS Deep", "Long-horizon reasoning", Icons.Outlined.Psychology)
)

private data class DiscoverCard(val label: String, val blurb: String, val icon: ImageVector, val route: String)

private val discoverCards = listOf(
    DiscoverCard("Vision", "Analyse any image", Icons.Outlined.Visibility, GsRoutes.chat(null)),
    DiscoverCard("Voice mode", "Talk it through", Icons.Outlined.Mic, GsRoutes.VOICE),
    DiscoverCard("Web research", "Cited answers", Icons.Outlined.Public, GsRoutes.chat(null))
)

private fun greetingForHour(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}

@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = GsMotion.spaceM),
        verticalArrangement = Arrangement.spacedBy(GsMotion.spaceL)
    ) {
        Spacer(Modifier.height(GsMotion.spaceS))
        GreetingHeader(onNavigate)
        UniversalInput(onNavigate)
        QuickActionsSection(onNavigate)
        SuggestedPromptsSection(onNavigate)
        ContinueSection(onNavigate)
        RecentConversationsSection(onNavigate)
        PinnedAssistantsSection(onNavigate)
        RecentProjectsSection(onNavigate)
        RecommendedModelsSection(onNavigate)
        ActivityTodaySection()
        DiscoverSection(onNavigate)
        Spacer(Modifier.height(GsMotion.spaceL))
    }
}

@Composable
private fun GreetingHeader(onNavigate: (String) -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceXS)
        ) {
            Text(
                text = greetingForHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HeaderIcon(Icons.Outlined.Notifications, "Notifications") { onNavigate(GsRoutes.NOTIFICATIONS) }
        HeaderIcon(Icons.Outlined.Settings, "Settings") { onNavigate(GsRoutes.SETTINGS) }
        HeaderIcon(Icons.Outlined.Search, "Search") { onNavigate(GsRoutes.SEARCH) }
    }
}

@Composable
private fun HeaderIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun UniversalInput(onNavigate: (String) -> Unit) {
    // InputBar is intentionally disabled; the wrapping Surface owns the click
    // so the whole pill feels kinetic and opens a fresh chat.
    Surface(
        onClick = { onNavigate(GsRoutes.chat(null)) },
        modifier = Modifier
            .fillMaxWidth()
            .kineticPress(),
        shape = RoundedCornerShape(GsMotion.radiusInput),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        GsInputBar(
            value = "",
            onValueChange = {},
            onSend = {},
            enabled = false,
            placeholder = "Ask anything…"
        )
    }
}

@Composable
private fun QuickActionsSection(onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceM)) {
        GsSectionHeader(title = "Quick actions")
        ActionRow(homeActions.take(6), onNavigate)
        ActionRow(homeActions.drop(6), onNavigate)
    }
}

@Composable
private fun ActionRow(actions: List<HomeAction>, onNavigate: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
    ) {
        actions.forEach { action ->
            GsQuickActionTile(
                label = action.label,
                icon = action.icon,
                onClick = { onNavigate(action.route) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SuggestedPromptsSection(onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
        GsSectionHeader(title = "Suggested prompts")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
        ) {
            suggestedPrompts.forEach { prompt ->
                GsChip(text = prompt, selected = false) { onNavigate(GsRoutes.chat(null)) }
            }
        }
    }
}

@Composable
private fun ContinueSection(onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
        GsSectionHeader(title = "Continue where you left off")
        resumeItems.forEach { item ->
            GsListItem(
                title = item.title,
                subtitle = item.subtitle,
                leading = { IconBadge(Icons.Outlined.History) },
                onClick = { onNavigate(item.route) }
            )
        }
    }
}

@Composable
private fun RecentConversationsSection(onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
        GsSectionHeader(title = "Recent conversations")
        recentConversations.forEach { item ->
            GsListItem(
                title = item.title,
                subtitle = item.subtitle,
                leading = { IconBadge(Icons.Outlined.ChatBubbleOutline) },
                onClick = { onNavigate(item.route) }
            )
        }
    }
}

@Composable
private fun PinnedAssistantsSection(onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
        GsSectionHeader(title = "Pinned assistants")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
        ) {
            pinnedAssistants.forEach { assistant ->
                GsCard(
                    modifier = Modifier.width(160.dp),
                    onClick = { onNavigate(GsRoutes.ASSISTANTS) }
                ) {
                    Text(
                        text = assistant.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = assistant.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentProjectsSection(onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
        GsSectionHeader(title = "Recent projects")
        recentProjects.forEach { item ->
            GsListItem(
                title = item.title,
                subtitle = item.subtitle,
                leading = { IconBadge(Icons.Outlined.Folder) },
                onClick = { onNavigate(item.route) }
            )
        }
    }
}

@Composable
private fun RecommendedModelsSection(onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
        GsSectionHeader(title = "Recommended models")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
        ) {
            recommendedModels.forEach { model ->
                GsCard(
                    modifier = Modifier.width(150.dp),
                    onClick = { onNavigate(GsRoutes.MODELS) }
                ) {
                    IconBadge(icon = model.icon, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(GsMotion.spaceS))
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = model.tagline,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityTodaySection() {
    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
        GsSectionHeader(title = "Today's AI activity")
        GsCard(onClick = null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
            ) {
                Text(
                    text = "12 chats",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "·",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "3 docs",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "·",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "45m voice",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(GsMotion.spaceM))
            // The one sanctioned aurora moment on Home — AI was alive today.
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(10.dp)
                    .background(
                        brush = rememberAuroraBrush(),
                        shape = RoundedCornerShape(5.dp)
                    )
            )
        }
    }
}

@Composable
private fun DiscoverSection(onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
        GsSectionHeader(title = "Discover more")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
        ) {
            discoverCards.forEach { card ->
                GsCard(
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(card.route) }
                ) {
                    IconBadge(icon = card.icon)
                    Spacer(Modifier.height(GsMotion.spaceS))
                    Text(
                        text = card.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = card.blurb,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Surface(
        shape = CircleShape,
        color = container,
        modifier = modifier.size(38.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
