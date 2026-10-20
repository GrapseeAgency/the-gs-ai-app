package com.grapsee.gsai.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grapsee.gsai.data.AccountStore
import com.grapsee.gsai.data.OnboardingProfileStore
import com.grapsee.gsai.data.local.ConversationPreviewRow
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.components.RefArt
import com.grapsee.gsai.ui.components.RefArtwork
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefDivider
import com.grapsee.gsai.ui.components.RefEmptyState
import com.grapsee.gsai.ui.components.RefIconButton
import com.grapsee.gsai.ui.components.RefIconTile
import com.grapsee.gsai.ui.components.RefPage
import com.grapsee.gsai.ui.components.RefPrimaryButton
import com.grapsee.gsai.ui.components.gsConversationTitle
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

private data class PromptSuggestion(
    val capability: String,
    val title: String,
    val prompt: String,
    val icon: ImageVector,
)

/**
 * Every suggestion maps to a capability the setup wizard actually offered, so
 * a chosen tool changes what Home puts in front of the user. The prompts are
 * real dispatches into a new chat — not decorative cards.
 */
private val promptSuggestions = listOf(
    PromptSuggestion(
        capability = "Draft and write",
        title = "Draft something in my voice",
        prompt = "Draft a short, warm note I can send to a colleague. Ask me for the details you need first.",
        icon = GsIcons.NotebookPen
    ),
    PromptSuggestion(
        capability = "Research the web",
        title = "Research a question properly",
        prompt = "I want to understand a topic properly. Ask me what it is, then give me a sourced briefing with the strongest counterpoints.",
        icon = GsIcons.Globe
    ),
    PromptSuggestion(
        capability = "Analyse images",
        title = "Read an image or screenshot",
        prompt = "I'd like to analyse an image or screenshot. Tell me what you can read from it and ask me to attach the file.",
        icon = GsIcons.ImagePlus
    ),
    PromptSuggestion(
        capability = "Write and fix code",
        title = "Fix or review some code",
        prompt = "Help me fix or review some code. Ask for the snippet, the language, and the error you're seeing.",
        icon = GsIcons.Code
    ),
    PromptSuggestion(
        capability = "Summarise documents",
        title = "Summarise a long document",
        prompt = "Summarise a long document for me. Ask me to attach it, then give me the argument, the evidence, and what to do next.",
        icon = GsIcons.FileText
    ),
    PromptSuggestion(
        capability = "Think it through",
        title = "Think a decision through",
        prompt = "Help me think a decision through. Ask me the one question that matters most, then lay out the options and the trade-offs.",
        icon = GsIcons.Lightbulb
    ),
)

private val defaultSuggestions = listOf(
    promptSuggestions[0], promptSuggestions[1], promptSuggestions[5], promptSuggestions[3]
)

/**
 * Home — a real front door, not a dashboard of invented numbers.
 *
 * The previous screen carried hardcoded usage percentages, a "GS-5 nano"
 * model that does not exist, and a dock whose Settings icon opened a new chat.
 * All of that is gone. What remains is the person's real name, the tools they
 * picked at setup, the conversations actually on this device, and three real
 * destinations. The hero is the app's one bloom, drawn once.
 */
@Composable
fun HomeDashboardScreen(
    onNewChat: () -> Unit,
    onStartPrompt: (String) -> Unit,
    onOpenConversation: (String) -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenChats: () -> Unit,
    onOpenAgent: () -> Unit,
    onOpenVoice: () -> Unit,
) {
    val context = LocalContext.current
    val name = AccountStore.displayName(context)
    val firstName = name.trim().substringBefore(' ').takeIf { it.isNotBlank() }
    val suggestions = remember {
        val chosen = OnboardingProfileStore.capabilities
        promptSuggestions.filter { it.capability in chosen }.ifEmpty { defaultSuggestions }
    }
    val interests = OnboardingProfileStore.interests
    val recentFlow = remember { recentConversations() }
    val recent by recentFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Still awake"
    }
    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))

    RefPage(
        title = if (firstName != null) "$greeting, $firstName" else greeting,
        subtitle = today,
        largeTitle = true,
        leading = {
            RefIconButton(
                icon = GsIcons.PanelLeft,
                label = "Open menu",
                onClick = onOpenDrawer
            )
        },
        actions = {
            RefIconButton(
                icon = GsIcons.History,
                label = "All chats",
                onClick = onOpenChats
            )
        }
    ) {
        HeroCard(onStart = onNewChat)

        Spacer(Modifier.height(20.dp))
        Text(
            text = if (interests.isEmpty()) "A few ways to begin" else "Because you chose ${interests.take(3).joinToString(", ").lowercase()}",
            style = MaterialTheme.typography.titleSmall,
            color = GsTheme.colors.textPrimary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        RefCard {
            suggestions.take(4).forEachIndexed { index, suggestion ->
                if (index > 0) RefDivider()
                SuggestionRow(
                    title = suggestion.title,
                    icon = suggestion.icon,
                    onClick = { onStartPrompt(suggestion.prompt) }
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 2.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Pick up where you left off",
                style = MaterialTheme.typography.titleSmall,
                color = GsTheme.colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "See all",
                style = MaterialTheme.typography.labelMedium,
                color = GsTheme.colors.accentStrong,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClickLabel = "See all conversations",
                        onClick = onOpenChats
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        if (recent.isEmpty()) {
            RefCard {
                RefEmptyState(
                    art = RefArt.Conversation,
                    title = "No conversation yet",
                    message = "Your first chat will wait here, with the whole transcript intact."
                )
            }
        } else {
            RefCard {
                recent.take(3).forEachIndexed { index, row ->
                    if (index > 0) RefDivider()
                    RecentRow(row) { onOpenConversation(row.conversation.id) }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = "More from GS",
            style = MaterialTheme.typography.titleSmall,
            color = GsTheme.colors.textPrimary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        RefCard {
            ExploreRow(
                title = "Travel agent",
                subtitle = "Plan a journey with a companion who asks the right questions",
                icon = GsIcons.Compass,
                onClick = onOpenAgent
            )
            RefDivider()
            ExploreRow(
                title = "Voice",
                subtitle = "Speak a thought and send the transcript to a new chat",
                icon = GsIcons.AudioLines,
                onClick = onOpenVoice
            )
            RefDivider()
            ExploreRow(
                title = "All chats",
                subtitle = "Search, pin and tidy your history",
                icon = GsIcons.Inbox,
                onClick = onOpenChats
            )
        }

        Spacer(Modifier.height(18.dp))
        RefPrimaryButton(
            label = "Start a new conversation",
            onClick = onNewChat,
            icon = GsIcons.SquarePen
        )
        Text(
            text = "GS can be wrong. Check anything that matters.",
            style = MaterialTheme.typography.labelSmall,
            color = GsTheme.colors.textTertiary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun HeroCard(onStart: () -> Unit) {
    val colors = GsTheme.colors
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        colors.glassSurface,
                        colors.accentSoft.copy(alpha = 0.7f)
                    )
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = "Start a conversation",
                onClick = onStart
            )
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        // A second, very quiet wash: the bloom's echo inside the card.
        Box(
            modifier = Modifier
                .size(210.dp)
                .background(
                    Brush.radialGradient(
                        listOf(colors.aurora.first().copy(alpha = 0.35f), Color.Transparent)
                    ),
                    RoundedCornerShape(999.dp)
                )
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RefArtwork(art = RefArt.Bloom, size = 132.dp)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "What should we make of today?",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "A thought, a file, or a blank page. All three are a fine place to start.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 26.dp)
            )
        }
    }
}

@Composable
private fun SuggestionRow(title: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = title,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RefIconTile(icon)
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = GsTheme.colors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            GsIcons.ArrowUp,
            contentDescription = null,
            tint = GsTheme.colors.textTertiary,
            modifier = Modifier
                .size(16.dp)
                .background(Color.Transparent)
        )
    }
}

@Composable
private fun RecentRow(row: ConversationPreviewRow, onClick: () -> Unit) {
    val preview = row.lastMessage?.trim()?.replace('\n', ' ')?.take(90)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 66.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(GsTheme.colors.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                GsIcons.MessageCircle,
                contentDescription = null,
                tint = GsTheme.colors.accentStrong,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                gsConversationTitle(row.conversation.title),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = GsTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!preview.isNullOrBlank()) {
                Text(
                    preview,
                    style = MaterialTheme.typography.labelMedium,
                    color = GsTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (row.conversation.pinned) {
            Icon(
                GsIcons.Star,
                contentDescription = "Pinned",
                tint = GsTheme.colors.accentStrong,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun ExploreRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 66.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = title,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RefIconTile(icon)
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = GsTheme.colors.textPrimary
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = GsTheme.colors.textSecondary,
                maxLines = 2
            )
        }
        Icon(
            GsIcons.ChevronRight,
            contentDescription = null,
            tint = GsTheme.colors.textTertiary,
            modifier = Modifier.size(18.dp)
        )
    }
}

private fun recentConversations(): Flow<List<ConversationPreviewRow>> =
    runCatching { ServiceLocator.db.conversationDao().observeActiveWithPreview() }
        .getOrElse { flowOf(emptyList()) }
