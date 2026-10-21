package com.grapsee.gsai.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grapsee.gsai.data.AccountStore
import com.grapsee.gsai.data.OnboardingProfileStore
import com.grapsee.gsai.data.local.ConversationPreviewRow
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.RefArt
import com.grapsee.gsai.ui.components.RefArtwork
import com.grapsee.gsai.ui.components.gsConversationTitle
import com.grapsee.gsai.ui.components.gsGlass
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsTheme
import java.time.LocalTime
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

/** The "Create" pill's real dispatch: an image/video-making request. */
private const val CREATE_PROMPT =
    "Help me create something visual. Ask what I want to make (image or short video), the mood, and the subject, then build it with me."

/**
 * Home — the reference front door.
 *
 * Brand lockup, the person's real name, four quick actions, the bento of real
 * capabilities, the honest "Today's Used" heading (the app has no invented
 * usage numbers), the suggestions the setup wizard actually selected, and the
 * floating dock. Every tap dispatches a real destination; nothing here is a
 * decorative card.
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
    onOpenSettings: () -> Unit = onOpenDrawer,
) {
    val context = LocalContext.current
    val name = AccountStore.displayName(context)
    val firstName = name.trim().substringBefore(' ').takeIf { it.isNotBlank() }
    val suggestions = remember {
        val chosen = OnboardingProfileStore.capabilities
        promptSuggestions.filter { it.capability in chosen }.ifEmpty { defaultSuggestions }
    }
    val recentFlow = remember { recentConversations() }
    val recent by recentFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Still awake"
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            HomeHeader(onAssistant = onOpenVoice, onMenu = onOpenDrawer)
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(6.dp))
                // Reference order: the thread you were just in sits ABOVE the
                // greeting, as a readable glass preview — the fastest way back
                // into real work. Hidden when nothing has been said yet.
                if (recent.isNotEmpty()) {
                    val resume = recent.first()
                    RecentPreviewCard(resume) {
                        onOpenConversation(resume.conversation.id)
                    }
                    Spacer(Modifier.height(16.dp))
                }
                GreetingBlock(greeting = greeting, firstName = firstName)
                Spacer(Modifier.height(26.dp))
                QuickPills(
                    onNewChat = onNewChat,
                    onExplore = onOpenAgent,
                    onCreate = { onStartPrompt(CREATE_PROMPT) },
                    onMore = onOpenDrawer,
                )
                Spacer(Modifier.height(24.dp))
                Bento(
                    onVideo = { onStartPrompt(CREATE_PROMPT) },
                    onMindGuide = {
                        onStartPrompt(
                            "Be my clarity companion. Ask me what's on my mind, then help me find the one next step."
                        )
                    },
                    onReflection = {
                        onStartPrompt(
                            "Give me a short daily reflection prompt and sit with my answer without judgement."
                        )
                    },
                )
                Spacer(Modifier.height(28.dp))
                TodayUsedHeader(onSeeAll = onOpenChats)
                Spacer(Modifier.height(10.dp))
                SuggestionsCard(
                    title = "Suggestions",
                    subtitle = "Try suggested questions first.",
                    onClick = { suggestions.firstOrNull()?.let { onStartPrompt(it.prompt) } },
                )
                Spacer(Modifier.height(124.dp))
            }
        }
        // Content scrolls under the floating dock. A canvas-coloured fade
        // below it keeps the last heading ("Today's Used") from being read
        // through the glass — the dock floats over atmosphere, not over text.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(150.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, GsTheme.colors.appBackground)
                    )
                )
        )
        HomeDock(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            onHome = {},
            onChats = onOpenChats,
            onGenerate = onNewChat,
            onSettings = onOpenSettings,
        )
    }
}

// ---------------------------------------------------------------------------
// Brand header
// ---------------------------------------------------------------------------

@Composable
private fun HomeHeader(onAssistant: () -> Unit, onMenu: () -> Unit) {
    val colors = GsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // The reference's avatar is the authored 3D bloom itself — the
            // one place the app already owns a real render, so no vector
            // stand-in belongs on top of it.
            Image(
                painter = painterResource(com.grapsee.gsai.R.drawable.gs_companion_orb),
                contentDescription = "Nexly",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(42.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = "Nexly",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary
            )
            Text(
                text = "AI Companion",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary
            )
        }
        Spacer(Modifier.weight(1f))
        GlassCircleButton(GsIcons.Sparkles, "Assistant", onAssistant)
        Spacer(Modifier.width(8.dp))
        GlassCircleButton(GsIcons.Menu, "Open menu", onMenu)
    }
}

// ---------------------------------------------------------------------------
// Greeting
// ---------------------------------------------------------------------------

@Composable
private fun GreetingBlock(greeting: String, firstName: String?) {
    Column {
        Text(
            text = greeting + ",",
            // The sheet sets the greeting at ~34pt semibold with almost no
            // extra leading — two lines that read as one block, not a poster.
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.SemiBold,
                lineHeight = 36.sp
            ),
            color = GsTheme.colors.textPrimary
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = firstName ?: "there",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 36.sp
                ),
                color = GsTheme.colors.textPrimary
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                GsIcons.Sparkles,
                contentDescription = null,
                tint = GsTheme.colors.accentStrong,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Your thoughts, questions, ideas —\nI'm here for it all.",
            style = MaterialTheme.typography.bodyLarge,
            color = GsTheme.colors.textSecondary
        )
    }
}

// ---------------------------------------------------------------------------
// Quick action pills
// ---------------------------------------------------------------------------

@Composable
private fun QuickPills(
    onNewChat: () -> Unit,
    onExplore: () -> Unit,
    onCreate: () -> Unit,
    onMore: () -> Unit,
) {
    // Four pills, one row, no horizontal scroller. At 12sp type and 9dp of
    // inset the set measures ~340dp on a 393dp screen — inside the reference's
    // gutter — so "More" is never parked off-screen.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassPill(GsIcons.Plus, "New Chat", onNewChat)
        GlassPill(GsIcons.Compass, "Explore", onExplore)
        GlassPill(GsIcons.Sparkles, "Create", onCreate)
        GlassPill(GsIcons.Ellipsis, "More", onMore)
    }
}

// ---------------------------------------------------------------------------
// Bento — Video / Mind Guide / Today's reflection
// ---------------------------------------------------------------------------

@Composable
private fun Bento(
    onVideo: () -> Unit,
    onMindGuide: () -> Unit,
    onReflection: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Measured from the sheet: the video card is 196pt of a 356pt row
        // (1.22 : 1), and the stacked pair splits 75 / 117 (0.64 : 1).
        VideoCard(onVideo, Modifier.weight(1.22f).fillMaxSize())
        Column(
            modifier = Modifier.weight(1f).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MindGuideCard(onMindGuide, Modifier.fillMaxWidth().weight(0.64f))
            ReflectionCard(onReflection, Modifier.fillMaxWidth().weight(1f))
        }
    }
}

@Composable
private fun VideoCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFFE7DBFF),
                        Color(0xFFFFD8E7),
                        Color(0xFFFFE7D3),
                    )
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = "Create a video",
                onClick = onClick
            )
    ) {
        // The reference's art is the pearl itself: overlapping glass spheres
        // bleeding off the lower-right corner, not a film-strip glyph.
        RefArtwork(
            art = RefArt.Pearl,
            size = 200.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 34.dp, y = 30.dp),
            contentDescription = "Pearlescent glass spheres"
        )
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            ReferenceChip(icon = GsIcons.Play, label = "Video")
            Spacer(Modifier.weight(1f))
            Text(
                text = "Create Stunning\nVideos with AI",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = GsTheme.colors.textPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Turn your ideas into beautiful videos in minutes.",
                style = MaterialTheme.typography.labelMedium,
                color = GsTheme.colors.textSecondary
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.55f))
                    .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    GsIcons.Play,
                    contentDescription = "Play preview",
                    tint = GsTheme.colors.textPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun MindGuideCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassPanel(modifier = modifier, onClick = onClick, onClickLabel = "Open Mind Guide") {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // A pearl, not a lens: violet at the crown, blue at the rim and a
            // blush underside, exactly as the reference's orb reads.
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFFFFF),
                                Color(0xFFD9C8FF),
                                Color(0xFF8E9BFF),
                                Color(0xFF63B4FF),
                                Color(0xFFFFA6C8)
                            )
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.TopStart
            ) {
                Box(
                    Modifier
                        .padding(9.dp)
                        .size(9.dp)
                        .background(Color.White.copy(alpha = 0.65f), CircleShape)
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Mind Guide",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = GsTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Your personal clarity companion.",
                    style = MaterialTheme.typography.labelSmall,
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
}

@Composable
private fun ReflectionCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassPanel(modifier = modifier, onClick = onClick, onClickLabel = "Open today's reflection") {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Today's reflection",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = GsTheme.colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        GsIcons.ChevronRight,
                        contentDescription = null,
                        tint = GsTheme.colors.textTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Take a moment to check in with yourself.",
                    style = MaterialTheme.typography.labelSmall,
                    color = GsTheme.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    // The lotus sits under the copy's last line, as in the
                    // sheet; keep an inset so the two never collide.
                    modifier = Modifier.padding(end = 26.dp)
                )
            }
            Box(Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 8.dp)) {
                RefArtwork(
                    art = RefArt.Reflection,
                    size = 84.dp,
                    contentDescription = "A lotus resting on still water"
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Today's Used + suggestions
// ---------------------------------------------------------------------------

@Composable
private fun TodayUsedHeader(onSeeAll: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Today's Used",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = GsTheme.colors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClickLabel = "See all conversations",
                    onClick = onSeeAll
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "See All",
                style = MaterialTheme.typography.labelMedium,
                color = GsTheme.colors.textSecondary
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                GsIcons.ArrowRight,
                contentDescription = null,
                tint = GsTheme.colors.textSecondary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun SuggestionsCard(title: String, subtitle: String, onClick: () -> Unit) {
    GlassPanel(onClick = onClick, onClickLabel = title) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color.White.copy(alpha = 0.55f))
                    .border(1.dp, GsTheme.colors.glassBorder, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    GsIcons.Lightbulb,
                    contentDescription = null,
                    tint = GsTheme.colors.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = GsTheme.colors.textPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = GsTheme.colors.textSecondary
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
}

/**
 * Preview text for the resume card: prose, not markdown. The last stored
 * message can be a rich reply — headings, tables, fenced code — and dumping
 * that into a card reads as garbage rather than as a memory.
 */
private fun cleanPreview(raw: String, limit: Int = 150): String {
    val text = raw
        .replace(Regex("```[\\s\\S]*?```"), " ")
        .replace(Regex("(?m)^\\s*\\|?[\\s:|-]{3,}\\|?\\s*$"), " ")
        .replace(Regex("-{3,}"), " ")
        .replace(Regex("\\|"), " ")
        .replace(Regex("[#*_>`]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    return if (text.length <= limit) text else text.take(limit).trimEnd() + "…"
}

/**
 * The reference's top "resume" card: the last thing you were working on, in
 * full sentences rather than a truncated list row. It is a real destination —
 * the whole card reopens the thread.
 */
@Composable
private fun RecentPreviewCard(row: ConversationPreviewRow, onClick: () -> Unit) {
    val colors = GsTheme.colors
    val preview = cleanPreview(row.lastMessage ?: row.conversation.title)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .gsGlass(GsGlassRole.Card, RoundedCornerShape(22.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = "Continue this chat",
                onClick = onClick
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = preview.ifBlank { "Pick up where you left off." },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.conversation.title,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                GsIcons.ArrowRight,
                contentDescription = null,
                tint = colors.accentStrong,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Floating dock
// ---------------------------------------------------------------------------

@Composable
private fun HomeDock(
    modifier: Modifier = Modifier,
    onHome: () -> Unit,
    onChats: () -> Unit,
    onGenerate: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .gsGlass(GsGlassRole.FloatingControl, RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DockCircle(FilledHouse, "Home", selected = true, onClick = onHome)
        DockCircle(GsIcons.BookOpen, "Chats", onClick = onChats)
        // The reference seats the peach pill inside a white well, so the dock
        // reads as three glass buttons and one lit action — not four siblings.
        Box(
            modifier = Modifier
                .weight(1f)
                .height(54.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.55f))
                .padding(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFFFFD3B4), Color(0xFFFFBE9C))
                        )
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClickLabel = "Generate",
                        onClick = onGenerate
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    GsIcons.Sparkles,
                    contentDescription = null,
                    tint = Color(0xFF4A2415),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Generate",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFF4A2415)
                )
            }
        }
        DockCircle(GsIcons.Settings, "Settings", onClick = onSettings)
    }
}

/**
 * The dock's Home glyph is FILLED in the reference — a solid little house, not
 * an outline. The generated Lucide set is stroke-only by contract (every glyph
 * must inherit its tint through SrcIn), so this one silhouette is drawn as an
 * explicit filled path rather than bending the generator's rule.
 */
private val FilledHouse: ImageVector by lazy {
    ImageVector.Builder(
        name = "FilledHouse",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 3.1f)
            lineTo(2.7f, 10.8f)
            curveTo(2.3f, 11.2f, 2.5f, 11.9f, 3.1f, 11.9f)
            lineTo(4.6f, 11.9f)
            lineTo(4.6f, 19.3f)
            curveTo(4.6f, 20f, 5.1f, 20.5f, 5.8f, 20.5f)
            lineTo(9.5f, 20.5f)
            lineTo(9.5f, 15.5f)
            curveTo(9.5f, 15f, 9.9f, 14.6f, 10.4f, 14.6f)
            lineTo(13.6f, 14.6f)
            curveTo(14.1f, 14.6f, 14.5f, 15f, 14.5f, 15.5f)
            lineTo(14.5f, 20.5f)
            lineTo(18.2f, 20.5f)
            curveTo(18.9f, 20.5f, 19.4f, 20f, 19.4f, 19.3f)
            lineTo(19.4f, 11.9f)
            lineTo(20.9f, 11.9f)
            curveTo(21.5f, 11.9f, 21.7f, 11.2f, 21.3f, 10.8f)
            close()
        }
    }.build()
}

@Composable
private fun DockCircle(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (selected) 0.62f else 0.44f))
            .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = GsTheme.colors.textPrimary.copy(alpha = if (selected) 1f else 0.78f),
            modifier = Modifier.size(21.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// Shared surfaces
// ---------------------------------------------------------------------------

@Composable
private fun GlassPanel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    val base = if (onClick != null) {
        modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Button,
            onClickLabel = onClickLabel,
            onClick = onClick
        )
    } else {
        modifier
    }
    Box(modifier = base.gsGlass(GsGlassRole.Card, shape), content = content)
}

@Composable
private fun GlassCircleButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .gsGlass(GsGlassRole.FloatingControl, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = GsTheme.colors.textPrimary,
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun GlassPill(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .gsGlass(GsGlassRole.FloatingControl, RoundedCornerShape(999.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick
            )
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = GsTheme.colors.textPrimary,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = GsTheme.colors.textPrimary
        )
    }
}

@Composable
private fun ReferenceChip(icon: ImageVector, label: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.45f))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = GsTheme.colors.textPrimary,
            modifier = Modifier.size(13.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = GsTheme.colors.textPrimary
        )
    }
}

private fun recentConversations(): Flow<List<ConversationPreviewRow>> =
    runCatching { ServiceLocator.db.conversationDao().observeActiveWithPreview() }
        .getOrElse { flowOf(emptyList()) }
