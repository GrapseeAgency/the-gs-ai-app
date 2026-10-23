package com.grapsee.gsai.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grapsee.gsai.data.AccountStore
import com.grapsee.gsai.data.OnboardingProfileStore
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.data.local.ConversationPreviewRow
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.RefArt
import com.grapsee.gsai.ui.components.ReferenceBackdrop
import com.grapsee.gsai.ui.components.RefArtwork
import com.grapsee.gsai.ui.components.gsConversationTitle
import com.grapsee.gsai.ui.components.gsGlass
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsRadius
import com.grapsee.gsai.ui.theme.GsTheme
import com.grapsee.gsai.ui.theme.LocalGsIsDark
import com.grapsee.gsai.ui.theme.untracked
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal data class PromptSuggestion(
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

    HomeContent(
        firstName = firstName,
        greeting = greeting,
        suggestions = suggestions,
        recent = recent,
        onNewChat = onNewChat,
        onStartPrompt = onStartPrompt,
        onOpenConversation = onOpenConversation,
        onOpenDrawer = onOpenDrawer,
        onOpenChats = onOpenChats,
        onOpenAgent = onOpenAgent,
        onOpenVoice = onOpenVoice,
        onOpenSettings = onOpenSettings,
    )
}

/**
 * Home as state in, UI out. Nothing here touches the service locator or the
 * clock, which is what lets a screenshot test render the real page at any
 * width without an emulator.
 */
@Composable
internal fun HomeContent(
    firstName: String?,
    greeting: String,
    suggestions: List<PromptSuggestion>,
    recent: List<ConversationPreviewRow>,
    onNewChat: () -> Unit,
    onStartPrompt: (String) -> Unit,
    onOpenConversation: (String) -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenChats: () -> Unit,
    onOpenAgent: () -> Unit,
    onOpenVoice: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        // Hoisted so the dock's Home button can scroll this page back to its top.
        // It used to be `onHome = {}` — a labelled, focusable, accessibility-
        // announced control whose action was an empty lambda, which is worse than
        // no control at all: VoiceOver tells a person the button exists and then
        // does nothing. Tapping the tab you are already on means "go to the top".
        val scrollState = rememberScrollState()
        val scrollScope = rememberCoroutineScope()
        // The reference's backdrop is a photograph of light through pearl.
        // The authored render ships as a full-bleed image; the procedural
        // gradient is kept only for Solid background style and dark mode.
        if (LocalGsIsDark.current || SettingsStore.backgroundStyle == "Solid") {
            ReferenceBackdrop(
                Modifier.matchParentSize(),
                showBlossom = false,
                bokeh = false
            )
        } else {
            Image(
                painter = painterResource(com.grapsee.gsai.R.drawable.gs_home_backdrop),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            HomeHeader(onAssistant = onOpenVoice, onMenu = onOpenDrawer)
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(52.dp))
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
                Spacer(Modifier.height(46.dp))
                QuickPills(
                    onNewChat = onNewChat,
                    onExplore = onOpenAgent,
                    onCreate = { onStartPrompt(CREATE_PROMPT) },
                    onMore = onOpenDrawer,
                )
                Spacer(Modifier.height(40.dp))
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
                Spacer(Modifier.height(10.dp))
                TodayUsedHeader(onSeeAll = onOpenChats)
                Spacer(Modifier.height(18.dp))
                SuggestionsCard(
                    title = "Suggestions",
                    subtitle = "Try suggested questions first.",
                    onClick = { suggestions.firstOrNull()?.let { onStartPrompt(it.prompt) } },
                )
                Spacer(Modifier.height(140.dp))
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
            onHome = { scrollScope.launch { scrollState.animateScrollTo(0) } },
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
                .size(38.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // The reference avatar is the authored 3D pearl — the one place
            // the app already owns a real render of the companion.
            Image(
                painter = painterResource(com.grapsee.gsai.R.drawable.gs_home_avatar),
                contentDescription = "Nexly",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(42.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = "Nexly",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = colors.textPrimary
            )
            Text(
                text = "AI Companion",
                style = MaterialTheme.typography.labelSmall.untracked().copy(fontSize = 13.sp),
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
                // One four-point star, not the three-star "sparkles" cluster.
                GsIcons.Sparkle,
                contentDescription = null,
                tint = GsTheme.colors.accentStrong,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Your thoughts, questions, ideas —\nI'm here for it all.",
            // 12.5/18, measured off the sheet: its two subtitle lines are 18 pt
            // apart with an 11.5 pt glyph band, and ours were 22 pt apart at 15 sp.
            // The size was never checked against the picture — both platforms agreed
            // on 15 because the contract was derived FROM them, which is the
            // difference between parity and fidelity.
            style = MaterialTheme.typography.bodyMedium.untracked().copy(
                fontSize = 12.5.sp,
                lineHeight = 18.sp
            ),
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
    // Four pills, one row, no horizontal scroller. Measured against the
    // bundled Poppins metrics: the wide tier is 336dp and the compact tier
    // 313dp, so the set clears the 353dp row on the sheet's 393dp phone AND
    // the 320dp row on a 360dp one. "More" is never parked off-screen — the
    // exact defect the review flagged when the wide tier measured 360dp.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 340.dp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassPill(GsIcons.Plus, "New Chat", onNewChat, compact)
            GlassPill(GsIcons.Compass, "Explore", onExplore, compact)
            GlassPill(GsIcons.Sparkles, "Create", onCreate, compact)
            GlassPill(GsIcons.Ellipsis, "More", onMore, compact)
        }
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
    // Measured off the sheet's card rims: left card x 207..538 and right card
    // 555..831 of a 680px screen, i.e. 54.5 / 45.5 with a 10dp gutter, and the
    // row spans y 704..1075 = 214dp. The previous 55.6 / 12 / 224 came from a
    // comment that divided 338 by 680 and called the result 55.6% — it is
    // 49.7%, so the shipped number had no measurement behind it.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(214.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        VideoCard(onVideo, Modifier.weight(54.5f).fillMaxSize())
        Column(
            modifier = Modifier.weight(45.5f).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MindGuideCard(onMindGuide, Modifier.fillMaxWidth().weight(0.375f))
            ReflectionCard(onReflection, Modifier.fillMaxWidth().weight(0.625f))
        }
    }
}

@Composable
private fun VideoCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = GsRadius.homeCardShape()
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFFFDFAF8),
                        Color(0xFFF3EBF5),
                        Color(0xFFF8EBE3),
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.45f), shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = "Create a video",
                onClick = onClick
            )
    ) {
        // The authored pearl render from the reference, filling the card's
        // bottom-right and trimmed by its clip — the mock composites it so the
        // colour reaches the copy, not just the corner.
        //
        // Crop, not Fit, and the number that says so is the card's own chroma
        // distribution measured on four real renders (two scales x two asset
        // orientations, tool-results/measure_video_art_variants.py). Letterboxed
        // into the 200 pt box the card reads chroma mean 17.2, p90 43.4, 228.7 L;
        // filled it reads 22.1, 50.2, 226.0 — against the reference card's 20.5,
        // 52.0, 225.2. Fit leaves the page 3.5 L too light and a quarter short of
        // the reference's saturation, because a 864x560 asset letterboxed into
        // 200x200 covers 130 of the card's 214 points and the rest is base
        // gradient. The orientation of the asset turned out to be worth 0.02 of
        // the error score, which is why nothing transforms it in the manifest.
        //
        // What is still not the reference, and is an artwork rather than a
        // layout: WHERE the colour sits. Averaging chroma across the card's rows,
        // the reference's peak is at y 167 pt of 214 with 30.7% of the mass in the
        // upper half; ours peaks at y 63 with 47.2% up top. The authored asset has
        // its saturated mass at the top right and the pale swirl at the bottom
        // left, and the mock reads the other way round — but flipping the asset
        // moves the peak to y 57, i.e. the wrong way, because the asset's lower
        // band is not the mirror of its upper one. Matching that needs the art the
        // mock was drawn from, not another transform of this one.
        Image(
            painter = painterResource(com.grapsee.gsai.R.drawable.gs_home_video_art),
            contentDescription = "Pearlescent bloom",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 16.dp, y = 12.dp)
                .size(200.dp)
        )
        // Left-to-right scrim keeps the copy on paper while the art owns the
        // right third, exactly as the mock composites it.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color(0xE6FBF6F3),
                        0.55f to Color(0x80FBF6F3),
                        1f to Color(0x00FBF6F3)
                    )
                )
        )
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            // The sheet stacks copy from the TOP: chip, then headline, then
            // body, and only the play control is pinned to the bottom. A
            // flexible spacer above the headline pushed the copy into the
            // middle of the card instead.
            ReferenceChip(icon = GsIcons.Play, label = "Video")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Create Stunning\nVideos with AI",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = GsTheme.colors.textPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Turn your ideas into beautiful videos in minutes.",
                style = MaterialTheme.typography.labelMedium.untracked().copy(
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                ),
                color = GsTheme.colors.textSecondary
            )
            Spacer(Modifier.weight(1f))
            // 37dp disc with a FILLED triangle. Measured on the sheet: the disc is
            // 37.0 pt across and the glyph is 9.2 x 11.5 pt at 65% ink, i.e. a solid
            // triangle. This was a 44dp disc around Tabler's outline glyph (30% ink)
            // while iOS already drew SF's solid `play` — so the two platforms
            // disagreed on the card's most salient control, and both were 7 pt larger
            // than the sheet.
            Box(
                modifier = Modifier
                    .size(37.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.55f))
                    .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // GsTheme.colors is @Composable, so it cannot be read inside the
                // draw lambda; hoisting it is what the compiler's
                // "@Composable invocations can only happen from ..." error asks for.
                val ink = GsTheme.colors.textPrimary
                Canvas(Modifier.size(width = 9.dp, height = 11.dp).offset(x = 1.dp)) {
                    val tri = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, size.height / 2f)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(tri, ink)
                }
            }
        }
    }
}

@Composable
private fun MindGuideCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    // CardLight, not Card: this card sits on the bloom, where the sheet's glass
    // is thin and its hairline is lit at +17.2 L. The Suggestions card below the
    // fold sits on flat cream, where the same sheet shows a thick fill and an
    // almost invisible edge. One material, two grounds, two roles.
    GlassPanel(
        modifier = modifier,
        role = GsGlassRole.CardLight,
        onClick = onClick,
        onClickLabel = "Open Mind Guide",
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxWidth < 148.dp
            Row(
                // The sheet's orb is 70px of a 680px screen = 40dp. Shrinking
                // it to 30dp stopped the truncation but traded one defect for
                // another — a pale, undersized pearl. The width comes back from
                // the insets instead: 5dp start, a 6dp gap, and a 14dp gutter
                // for the overlaid chevron leaves ~88dp of text column, and the
                // body drops to 9.5sp to match what the sheet actually sets.
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = if (compact) 4.dp else 5.dp,
                        end = if (compact) 12.dp else 14.dp,
                        top = 8.dp,
                        bottom = 8.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // The authored pearl orb from the reference — the same render
                // the mock uses, not a gradient approximation.
                Image(
                    painter = painterResource(com.grapsee.gsai.R.drawable.gs_home_mind_orb),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(if (compact) 32.dp else 38.dp)
                )
                Spacer(Modifier.width(if (compact) 5.dp else 6.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Mind Guide",
                        style = MaterialTheme.typography.titleSmall.untracked().copy(
                            fontSize = if (compact) 12.sp else 13.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = GsTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        // Reference breaks after "personal". Three lines are
                        // allowed so a 360dp card wraps "companion." onto a
                        // third line instead of clipping it to "personal…".
                        text = "Your personal\nclarity companion.",
                        style = MaterialTheme.typography.labelSmall.untracked().copy(
                            fontSize = if (compact) 9.sp else 9.5.sp,
                            lineHeight = if (compact) 11.sp else 12.sp
                        ),
                        color = GsTheme.colors.textSecondary,
                        maxLines = 3
                    )
                }
            }
            Icon(
                GsIcons.ChevronRight,
                contentDescription = null,
                tint = GsTheme.colors.textTertiary,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp)
                    .size(13.dp)
            )
        }
    }
}

@Composable
private fun ReflectionCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassPanel(
        modifier = modifier,
        role = GsGlassRole.CardLight,
        onClick = onClick,
        onClickLabel = "Open today's reflection",
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxWidth < 148.dp
            // Lotus painted first; copy keeps its own z-plane.
            Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 10.dp).zIndex(1f)) {
                // Title and chevron share a row again: overlaying the glyph
                // made it graze the final "n" at 360dp. Measured against the
                // bundled Poppins, a 10dp inset leaves 112.5dp at 393dp — the
                // sheet's 12.5sp title is 112.2dp, so it fits at the sheet's
                // size — and 98dp at 360dp, which needs the 10sp step.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Today's reflection",
                        style = MaterialTheme.typography.titleSmall.untracked().copy(
                            fontSize = if (compact) 10.sp else 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = GsTheme.colors.textPrimary,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        GsIcons.ChevronRight,
                        contentDescription = null,
                        tint = GsTheme.colors.textTertiary,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Take a moment to check in with yourself.",
                    style = MaterialTheme.typography.labelSmall.untracked().copy(
                        fontSize = if (compact) 9.5.sp else 10.5.sp,
                        lineHeight = if (compact) 11.5.sp else 13.sp
                    ),
                    color = GsTheme.colors.textSecondary,
                    maxLines = 3
                )
            }
            Box(Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 8.dp)) {
                // The authored lotus from the reference — feathered so it
                // melts into the card surface rather than sitting on top.
                Image(
                    painter = painterResource(com.grapsee.gsai.R.drawable.gs_home_lotus_new),
                    contentDescription = "A lotus in bloom",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(
                        width = if (compact) 120.dp else 150.dp,
                        height = if (compact) 80.dp else 100.dp
                    )
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
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            ),
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
                style = MaterialTheme.typography.labelLarge.untracked(),
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
    // CardDim: same 30% glass as the bento above it, dim edge, because this card
    // sits on flat cream where the sheet's own hairline measures +2.6 L instead
    // of +19.9 L.
    GlassPanel(role = GsGlassRole.CardDim, onClick = onClick, onClickLabel = title) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.55f))
                    .border(1.dp, GsTheme.colors.glassBorder, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    GsIcons.Lightbulb,
                    contentDescription = null,
                    tint = GsTheme.colors.textPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.untracked().copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = GsTheme.colors.textPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium.untracked().copy(fontSize = 12.sp),
                    color = GsTheme.colors.textSecondary
                )
            }
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
            .clip(GsRadius.homeCardShape())
            .gsGlass(GsGlassRole.Card, GsRadius.homeCardShape())
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
            style = MaterialTheme.typography.bodyMedium.untracked(),
            color = colors.textPrimary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.conversation.title,
                style = MaterialTheme.typography.labelSmall.untracked(),
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
            .gsGlass(GsGlassRole.FloatingControl, GsRadius.pill)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        DockCircle(FilledHouse, "Home", selected = true, onClick = onHome)
        DockCircle(FilledBook, "Chats", onClick = onChats)
        // The reference seats the peach pill inside a white well, so the dock
        // reads as three glass buttons and one lit action — not four siblings.
        Box(
            modifier = Modifier
                .weight(1f)
                .height(60.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.55f))
                .padding(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        // Sampled from the sheet's Generate pill (#F8D6C0 at
                        // its centre, warmer at the right edge).
                        Brush.horizontalGradient(
                            listOf(Color(0xFFFBDAC6), Color(0xFFF6C6AC))
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
                    tint = Color(0xFF3A2116),
                    modifier = Modifier.size(19.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Generate",
                    style = MaterialTheme.typography.labelLarge.untracked().copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color(0xFF3A2116),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        DockCircle(FilledGear, "Settings", onClick = onSettings)
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


/** Filled open book, for the same reason as [FilledHouse]. */
private val FilledBook: ImageVector by lazy {
    ImageVector.Builder(
        name = "FilledBook",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(11.2f, 6.4f)
            curveTo(8.6f, 4.9f, 5.4f, 4.9f, 2.8f, 6.6f)
            curveTo(2.5f, 6.8f, 2.4f, 7.1f, 2.4f, 7.4f)
            lineTo(2.4f, 18.2f)
            curveTo(2.4f, 18.9f, 3.1f, 19.3f, 3.7f, 19f)
            curveTo(6f, 17.7f, 8.8f, 17.8f, 11f, 19.2f)
            curveTo(11.4f, 19.4f, 11.8f, 19.4f, 12.2f, 19.2f)
            curveTo(14.4f, 17.8f, 17.2f, 17.7f, 19.5f, 19f)
            curveTo(20.1f, 19.3f, 20.8f, 18.9f, 20.8f, 18.2f)
            lineTo(20.8f, 7.4f)
            curveTo(20.8f, 7.1f, 20.7f, 6.8f, 20.4f, 6.6f)
            curveTo(17.8f, 4.9f, 14.6f, 4.9f, 12f, 6.4f)
            lineTo(12f, 17.4f)
            curveTo(11.8f, 17.4f, 11.4f, 17.3f, 11.2f, 17.2f)
            close()
        }
    }.build()
}


/**
 * Filled gear. The sheet's settings glyph is a solid cog with a hole; the
 * generated Lucide set only has the outline, so the silhouette lives here.
 */
private val FilledGear: ImageVector by lazy {
    ImageVector.Builder(
        name = "FilledGear",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
            moveTo(18.79f, 9.6f)
            lineTo(19.02f, 10.38f)
            lineTo(21.28f, 10.53f)
            lineTo(21.28f, 13.47f)
            lineTo(19.02f, 13.62f)
            lineTo(18.79f, 14.4f)
            lineTo(18.5f, 15.1f)
            lineTo(18.11f, 15.82f)
            lineTo(19.6f, 17.53f)
            lineTo(17.53f, 19.6f)
            lineTo(15.82f, 18.11f)
            lineTo(15.1f, 18.5f)
            lineTo(14.4f, 18.79f)
            lineTo(13.62f, 19.02f)
            lineTo(13.47f, 21.28f)
            lineTo(10.53f, 21.28f)
            lineTo(10.38f, 19.02f)
            lineTo(9.6f, 18.79f)
            lineTo(8.9f, 18.5f)
            lineTo(8.18f, 18.11f)
            lineTo(6.47f, 19.6f)
            lineTo(4.4f, 17.53f)
            lineTo(5.89f, 15.82f)
            lineTo(5.5f, 15.1f)
            lineTo(5.21f, 14.4f)
            lineTo(4.98f, 13.62f)
            lineTo(2.72f, 13.47f)
            lineTo(2.72f, 10.53f)
            lineTo(4.98f, 10.38f)
            lineTo(5.21f, 9.6f)
            lineTo(5.5f, 8.9f)
            lineTo(5.89f, 8.18f)
            lineTo(4.4f, 6.47f)
            lineTo(6.47f, 4.4f)
            lineTo(8.18f, 5.89f)
            lineTo(8.9f, 5.5f)
            lineTo(9.6f, 5.21f)
            lineTo(10.38f, 4.98f)
            lineTo(10.53f, 2.72f)
            lineTo(13.47f, 2.72f)
            lineTo(13.62f, 4.98f)
            lineTo(14.4f, 5.21f)
            lineTo(15.1f, 5.5f)
            lineTo(15.82f, 5.89f)
            lineTo(17.53f, 4.4f)
            lineTo(19.6f, 6.47f)
            lineTo(18.11f, 8.18f)
            lineTo(18.5f, 8.9f)
            close()
            moveTo(15.1f, 12f)
            lineTo(14.68f, 10.45f)
            lineTo(13.55f, 9.32f)
            lineTo(12f, 8.9f)
            lineTo(10.45f, 9.32f)
            lineTo(9.32f, 10.45f)
            lineTo(8.9f, 12f)
            lineTo(9.32f, 13.55f)
            lineTo(10.45f, 14.68f)
            lineTo(12f, 15.1f)
            lineTo(13.55f, 14.68f)
            lineTo(14.68f, 13.55f)
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
    role: GsGlassRole = GsGlassRole.Card,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val shape = GsRadius.homeCardShape()
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
    Box(modifier = base.gsGlass(role, shape), content = content)
}

@Composable
private fun GlassCircleButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    // Reference measures 80px of a 680px screen = 46pt at 393.
    Box(
        modifier = Modifier
            .size(46.dp)
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
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun GlassPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    compact: Boolean = false,
) {
    Row(
        modifier = Modifier
            .heightIn(min = 46.dp)
            .gsGlass(GsGlassRole.FloatingControl, GsRadius.pill)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = GsTheme.colors.textPrimary,
            modifier = Modifier.size(13.dp)
        )
        Spacer(Modifier.width(if (compact) 3.dp else 4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.untracked().copy(
                fontSize = if (compact) 12.sp else 13.sp,
                fontWeight = FontWeight.Medium
            ),
            color = GsTheme.colors.textPrimary,
            maxLines = 1,
            softWrap = false
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
            modifier = Modifier.size(8.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            // 10sp/10dp: the sheet's chip is 7.2 pt tall measured as dark pixels
            // over the card (icon and text are the same height there). At 11sp the
            // label rendered an 8.33 pt cap and a 13dp icon made the whole chip 11.0
            // pt — 3.8 pt more than the sheet's, the "Video 12 vs 13" a review
            // listed that no gate could see until the lint learned to resolve copy
            // passed as a parameter. The icon is 8dp because on the sheet the icon
            // and the label are the same height (7.2 pt); at 10dp it set the chip's
            // height on its own.
            style = MaterialTheme.typography.labelSmall.untracked().copy(fontSize = 10.sp),
            color = GsTheme.colors.textPrimary
        )
    }
}

private fun recentConversations(): Flow<List<ConversationPreviewRow>> =
    runCatching { ServiceLocator.db.conversationDao().observeActiveWithPreview() }
        .getOrElse { flowOf(emptyList()) }
