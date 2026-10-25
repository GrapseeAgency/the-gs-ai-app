package com.grapsee.gsai.ui.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.ReferenceBackdrop
import com.grapsee.gsai.ui.components.gsGlass
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsFonts
import com.grapsee.gsai.ui.theme.GsTheme
import com.grapsee.gsai.ui.theme.LocalGsIsDark
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Explore + Discover — TWO pages, matching the two authored mocks.
 *
 * EXPLORE (the reference with Popular Tools / Creative Collections / Featured):
 * the featured hero, the Popular Tools grid, Creative Collections and the
 * Featured journey carousel. No chips, no composer; the reference's floating
 * dock is ExploreDock — an overlay, not a navigation-graph rewrite.
 *
 * DISCOVER (the reference with the EXPLORE hero / category chips / composer):
 * the "Discover what you can do with Nexly" hero, category chips, Featured
 * Tools, Popular prompt rows and the Ask-me-anything composer.
 *
 * Every card opens a REAL conversation through [onOpenChat]; Explore's CTA
 * leads to Discover; nothing on either page is decorative.
 *
 * The artwork is authored vector art (assets/artExplore svg sources rendered to PNG), and
 * every panel carries the reference's rich glass: gradient fill, luminous
 * rim, warm shadow.
 */

// ---------------------------------------------------------------------------
// palette (pixel-sampled from the authored mocks)
// ---------------------------------------------------------------------------

object ExploreTones {
    val PinkBg = Color(0xFFFBD7E7)
    val LavenderBg = Color(0xFFDED8FD)
    val GreenBg = Color(0xFFCAEBD4)
    val ApricotBg = Color(0xFFFEDFC5)
    val BlueBg = Color(0xFFD6E6FC)

    val PinkFg = Color(0xFFC43366)
    val LavenderFg = Color(0xFF5A50D6)
    val GreenFg = Color(0xFF177A50)
    val ApricotFg = Color(0xFFB85C12)
    val BlueFg = Color(0xFF1663C4)
    // The Discover mock's Writing Assistant / Code Helper icons are a warm
    // vermilion, distinct from the brown-orange apricot fg they'd inherit.
    val VermilionFg = Color(0xFFE8604C)

    val Peach = Color(0xFFFEB47D)
    val PeachDeep = Color(0xFFFEC493)
    val PeachPale = Color(0xFFFEDFC5)
    val CtaTop = Color(0xFFFFE9DC)
    val CtaBottom = Color(0xFFFED3C0)

    val DiscoverBg = Color(0xFFFBE6F5)
    val DiscoverInk = Color(0xFFC0447E)

    val InkStrong = Color(0xFF181C30)
    val InkHeader = Color(0xFF2B2C37)
    val InkBody = Color(0xFF4A4B55)
    val InkMuted = Color(0xFF8D8D95)
}

// ---------------------------------------------------------------------------
// catalog
// ---------------------------------------------------------------------------

data class ExploreTone(val bg: Color, val fg: Color)

enum class ExploreCategory(val label: String) {
    All("All"),
    Productivity("Productivity"),
    Creativity("Creativity"),
    Learning("Learning"),
    Lifestyle("Lifestyle"),
}

data class ExploreTool(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: String,
    val tone: ExploreTone,
    val category: ExploreCategory,
    /** Seeds the conversation this card opens — never a dead end. */
    val prompt: String,
    /** The reference paints some tiles as saturated discs with white glyphs. */
    val filledTile: Boolean = false,
)

object ExploreCatalog {
    val tools = listOf(
        ExploreTool(
            id = "image-studio",
            title = "Image Studio",
            subtitle = "Create stunning images with AI",
            icon = "photo",
            tone = ExploreTone(ExploreTones.PinkBg, ExploreTones.PinkFg),
            category = ExploreCategory.Creativity,
            prompt = "Act as my image studio director. Ask me the subject, mood and style one question at a time, then write three detailed image prompts I can paste into any image generator.",
        ),
        ExploreTool(
            id = "video-generator",
            title = "Video Generator",
            subtitle = "Turn ideas into videos",
            icon = "player-play",
            tone = ExploreTone(ExploreTones.LavenderBg, Color(0xFF8E90E8)),
            category = ExploreCategory.Creativity,
            prompt = "Help me plan a short video. Ask what it is for and who watches it, then give me a shot-by-shot outline with narration lines and on-screen text for each scene.",
            filledTile = true,
        ),
        ExploreTool(
            id = "voice-assistant",
            title = "Voice Assistant",
            subtitle = "Natural, human-like voices",
            icon = "waveform",
            tone = ExploreTone(ExploreTones.GreenBg, ExploreTones.GreenFg),
            category = ExploreCategory.Productivity,
            prompt = "Be my speaking coach. We'll practise a short introduction out loud: I'll type what I want to say, you tighten it, then give me feedback on clarity and tone.",
        ),
        ExploreTool(
            id = "code-assistant",
            title = "Code Assistant",
            subtitle = "Write, debug, and build faster",
            icon = "code",
            tone = ExploreTone(ExploreTones.ApricotBg, ExploreTones.ApricotFg),
            category = ExploreCategory.Productivity,
            prompt = "Be my pair programmer. Ask what I'm building and which language, then help me write and debug it step by step, explaining every fix.",
        ),
        ExploreTool(
            id = "writing-assistant",
            title = "Writing Assistant",
            subtitle = "Craft, improve and perfect your text",
            icon = "file-text",
            tone = ExploreTone(ExploreTones.BlueBg, ExploreTones.BlueFg),
            category = ExploreCategory.Productivity,
            prompt = "Be my editor. I'll paste text; you keep my voice, fix grammar and flow, and show me a short list of what you changed and why.",
        ),
        ExploreTool(
            id = "study-plan",
            title = "Create a study plan",
            subtitle = "Personalized learning plan for your goals.",
            icon = "school",
            tone = ExploreTone(ExploreTones.LavenderBg, ExploreTones.LavenderFg),
            category = ExploreCategory.Learning,
            prompt = "Build me a personalized study plan. Ask my subject, deadline and weekly hours first, then give a day-by-day plan with one review session per week.",
        ),
        ExploreTool(
            id = "logo-maker",
            title = "Generate a logo",
            subtitle = "Turn your idea into a unique logo.",
            icon = "brush",
            tone = ExploreTone(ExploreTones.ApricotBg, ExploreTones.ApricotFg),
            category = ExploreCategory.Creativity,
            prompt = "Help me design a logo. Ask about the name, feeling and where it will live, then propose three concepts described precisely enough to brief a designer or generator.",
        ),
        ExploreTool(
            id = "trip-planner",
            title = "Plan a trip",
            subtitle = "Get a custom travel itinerary.",
            icon = "plane",
            tone = ExploreTone(ExploreTones.GreenBg, ExploreTones.GreenFg),
            category = ExploreCategory.Lifestyle,
            prompt = "Plan a trip with me. Ask the destination, dates and pace, then build a day-by-day itinerary with one highlight, one meal and one rest stop per day.",
        ),
        ExploreTool(
            id = "doc-summary",
            title = "Summarize a document",
            subtitle = "Save time with AI summaries.",
            icon = "file-description",
            tone = ExploreTone(ExploreTones.BlueBg, ExploreTones.BlueFg),
            category = ExploreCategory.Learning,
            prompt = "Summarize documents for me. I'll paste or attach one; give a five-bullet summary, the key numbers, and three questions I should ask about it.",
        ),
    )

    data class Collection(
        val id: String,
        val title: String,
        val subtitle: String,
        val category: ExploreCategory,
    )

    val collections = listOf(
        Collection("design", "Design & Visuals", "For creators & dreamers", ExploreCategory.Creativity),
        Collection("work", "Work & Productivity", "Work smarter, not harder", ExploreCategory.Productivity),
        Collection("learning", "Learning & Growth", "Build new skills", ExploreCategory.Learning),
    )

    /**
     * Discover's Featured Tools — the mock's OWN four cards. They share the
     * Explore page's concepts but not its copy, icons or tones (the Discover
     * mock paints Image Studio with sparkles, titles Voice simply "Voice"
     * on lavender, and Code Helper "Code Helper" in vermilion), so they are
     * their own entries rather than a re-skin that would drift the Explore
     * mock's Popular Tools. Mirror of iOS ExploreCatalog.discoverFeatured.
     */
    val discoverFeatured = listOf(
        ExploreTool(
            id = "discover-image-studio",
            title = "Image Studio",
            subtitle = "Turn ideas into stunning visuals with AI.",
            icon = "sparkles",
            tone = ExploreTone(ExploreTones.ApricotBg, ExploreTones.ApricotFg),
            category = ExploreCategory.Creativity,
            prompt = "Act as my image studio director. Ask me the subject, mood and style one question at a time, then write three detailed image prompts I can paste into any image generator.",
        ),
        ExploreTool(
            id = "discover-voice",
            title = "Voice",
            subtitle = "Create natural voice responses and audio.",
            icon = "waveform",
            tone = ExploreTone(ExploreTones.LavenderBg, ExploreTones.LavenderFg),
            category = ExploreCategory.Productivity,
            prompt = "Be my speaking coach. We'll practise a short introduction out loud: I'll type what I want to say, you tighten it, then give me feedback on clarity and tone.",
        ),
        ExploreTool(
            id = "discover-writing",
            title = "Writing Assistant",
            subtitle = "Craft, improve and perfect your text.",
            icon = "file-text",
            tone = ExploreTone(ExploreTones.PinkBg, ExploreTones.VermilionFg),
            category = ExploreCategory.Productivity,
            prompt = "Be my editor. I'll paste text; you keep my voice, fix grammar and flow, and show me a short list of what you changed and why.",
        ),
        ExploreTool(
            id = "discover-code",
            title = "Code Helper",
            subtitle = "Get coding help, explanations and more.",
            icon = "code",
            tone = ExploreTone(ExploreTones.ApricotBg, ExploreTones.VermilionFg),
            category = ExploreCategory.Productivity,
            prompt = "Be my pair programmer. Ask what I'm building and which language, then help me write and debug it step by step, explaining every fix.",
        ),
    )

    /**
     * One page of the Featured carousel — a journey card plus the authored
     * art panel it bleeds through. Every page seeds a real conversation;
     * the carousel is content, not chrome.
     */
    data class FeaturedJourney(
        val id: String,
        val title: String,
        val subtitle: String,
        val icon: String,
        val tone: ExploreTone,
        val artRes: Int,
        val prompt: String,
    )

    val featuredJourneys = listOf(
        FeaturedJourney(
            id = "kyoto-weekend",
            title = "Weekend in Kyoto",
            subtitle = "A creative journey through Japan's timeless beauty",
            icon = "sparkles",
            tone = ExploreTone(ExploreTones.ApricotBg, ExploreTones.ApricotFg),
            artRes = com.grapsee.gsai.R.drawable.gs_explore_kyoto,
            prompt = "Plan a creative weekend in Kyoto. Ask my pace and interests, then give me one day per page: morning temple light, one craft workshop, one quiet meal, and one place locals actually go.",
        ),
        FeaturedJourney(
            id = "studio-bloom",
            title = "Studio Bloom",
            subtitle = "A ten-minute creative warm-up, guided",
            icon = "brush",
            tone = ExploreTone(ExploreTones.PinkBg, ExploreTones.PinkFg),
            artRes = com.grapsee.gsai.R.drawable.gs_explore_col_design,
            prompt = "Take me through a quick creative warm-up. Ask my mood, then give me three image prompts, a matching palette, and one small exercise I can finish in ten minutes.",
        ),
        FeaturedJourney(
            id = "lotus-lesson",
            title = "The Lotus Lesson",
            subtitle = "One new skill, twenty quiet minutes",
            icon = "school",
            tone = ExploreTone(ExploreTones.GreenBg, ExploreTones.GreenFg),
            artRes = com.grapsee.gsai.R.drawable.gs_explore_col_learning,
            prompt = "Teach me one new thing today. Ask what I keep postponing, then turn it into a twenty-minute first lesson with one small win at the end.",
        ),
    )

    fun byCategory(category: ExploreCategory): List<ExploreTool> =
        if (category == ExploreCategory.All) tools else tools.filter { it.category == category }

    fun search(query: String): List<ExploreTool> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return tools
        return tools.filter {
            it.title.lowercase().contains(q) ||
                it.subtitle.lowercase().contains(q) ||
                it.category.label.lowercase().contains(q)
        }
    }
}

// ---------------------------------------------------------------------------
// shared: page scaffold, header, glass, icon circle, section header
// ---------------------------------------------------------------------------

@Composable
private fun ExplorePage(
    inkStrong: Color,
    inkHeader: Color,
    inkBody: Color,
    inkMuted: Color,
    bottomOverlay: @Composable () -> Unit = {},
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val isDark = LocalGsIsDark.current
    Box(Modifier.fillMaxSize()) {
        if (isDark || SettingsStore.backgroundStyle == "Solid") {
            ReferenceBackdrop(Modifier.matchParentSize(), showBlossom = false, bokeh = false)
        } else {
            Image(
                painter = painterResource(com.grapsee.gsai.R.drawable.gs_explore_backdrop),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            content()
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            bottomOverlay()
        }
    }
}

@Composable
private fun ExplorePageHeader(
    inkStrong: Color,
    inkMuted: Color,
    searchOpen: Boolean,
    onToggleSearch: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(com.grapsee.gsai.R.drawable.gs_companion_orb),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                "Nexly",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = GsFonts.Product,
                color = inkStrong,
            )
            Text(
                "AI Companion",
                fontSize = 10.sp,
                fontFamily = GsFonts.Product,
                color = inkMuted,
            )
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(36.dp)
                .exploreGlass(shape = CircleShape, elevation = 8.dp)
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (searchOpen) "Close search" else "Open search",
                ) { onToggleSearch() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                GsIcons.Search,
                contentDescription = null,
                tint = inkStrong,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * The Explore page's floating dock — the reference mock's bottom bar: a
 * glass capsule carrying Home, the lit Explore pill, and Settings.
 *
 * Overlay only. It does NOT rewrite the navigation contract: the drawer
 * stays the app's section switcher, Home pops back, Settings is a plain
 * push. The capsule wears the measured FloatingControl glass, which
 * ships blur-free on purpose — the same law the Home dock obeys.
 */
@Composable
private fun ExploreDock(
    inkStrong: Color,
    onOpenHome: () -> Unit,
    onScrollToTop: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    // the pill's ink is fixed deep brown in both mocks and on Home's
    // Generate pill — the peach capsule never inverts with the scheme
    val pillInk = Color(0xFF4A2414)
    Row(
        Modifier
            .fillMaxWidth()
            .gsGlass(GsGlassRole.FloatingControl, CircleShape)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .gsGlass(GsGlassRole.FloatingControl, CircleShape)
                .clickable(role = Role.Button, onClickLabel = "Home") { onOpenHome() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(GsIcons.House, contentDescription = null, tint = inkStrong, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.weight(1f))
        // the mock's lit action: a peach capsule, compass seated in a white well
        Row(
            Modifier
                .widthIn(max = 157.dp)
                .height(46.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    ambientColor = Color(0x2EEDBEA4),
                    spotColor = Color(0x4DE29C7C),
                )
                .background(
                    Brush.horizontalGradient(listOf(ExploreTones.CtaTop, ExploreTones.Peach)),
                    CircleShape,
                )
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.85f),
                        1f to Color.White.copy(alpha = 0.35f),
                    ),
                    CircleShape,
                )
                .clickable(
                    role = Role.Button,
                    onClickLabel = "Explore, current tab. Scrolls to top",
                ) { onScrollToTop() }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .background(Color.White.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(GsIcons.Compass, contentDescription = null, tint = pillInk, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "Explore",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = GsFonts.Product,
                color = pillInk,
                maxLines = 1,
            )
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(46.dp)
                .gsGlass(GsGlassRole.FloatingControl, CircleShape)
                .clickable(role = Role.Button, onClickLabel = "Settings") { onOpenSettings() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(GsIcons.Settings, contentDescription = null, tint = inkStrong, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun ExploreSearchField(
    query: String,
    onQuery: (String) -> Unit,
    inkBody: Color,
    inkMuted: Color,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .exploreGlass(shape = RoundedCornerShape(22.dp), elevation = 8.dp)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(GsIcons.Search, contentDescription = null, tint = inkMuted, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    "Search tools and abilities",
                    fontSize = 12.sp,
                    fontFamily = GsFonts.Product,
                    color = inkMuted,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 12.sp,
                    fontFamily = GsFonts.Product,
                    color = inkBody,
                ),
                cursorBrush = SolidColor(ExploreTones.Peach),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("explore-search-input"),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                GsIcons.X,
                contentDescription = "Clear search",
                tint = inkMuted,
                modifier = Modifier
                    .size(14.dp)
                    .clickable(role = Role.Button, onClickLabel = "Clear search") { onQuery("") },
            )
        }
    }
}

/**
 * The reference's rich glass: warm gradient fill with the bloom showing
 * through, a luminous rim toward the top-left, a soft warm shadow.
 */
@Composable
private fun Modifier.exploreGlass(
    shape: Shape,
    elevation: Dp = 12.dp,
): Modifier = composed {
    val isDark = LocalGsIsDark.current
    this
        .shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = Color(0x2EEDBEA4),
            spotColor = Color(0x55E29C7C),
        )
        .clip(shape)
        .background(
            Brush.linearGradient(
                0f to Color(0xFFFFFAF6).copy(alpha = if (isDark) 0.85f else 0.90f),
                1f to Color(0xFFFCEDE5).copy(alpha = if (isDark) 0.74f else 0.79f),
                start = androidx.compose.ui.geometry.Offset.Zero,
                end = androidx.compose.ui.geometry.Offset.Infinite,
            ),
            shape,
        )
        .border(
            1.dp,
            Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.95f),
                1f to Color.White.copy(alpha = 0.30f),
            ),
            shape,
        )
}

/** The icon circle: tinted gradient, white rim, its own glow. */
@Composable
private fun ExploreIconCircle(
    tool: ExploreTool,
    circle: Dp,
    glyph: Dp,
) {
    val tileBg = if (tool.filledTile) tool.tone.fg else tool.tone.bg
    val glyphTint = if (tool.filledTile) Color.White else tool.tone.fg
    val bgLight = lerp(tileBg, Color.White, 0.45f)
    Box(
        Modifier
            .size(circle)
            .shadow(
                elevation = 7.dp,
                shape = CircleShape,
                ambientColor = Color(0x2EEDBEA4),
                spotColor = Color(0x4DE29C7C),
            )
            .background(
                Brush.verticalGradient(0f to bgLight, 1f to tileBg),
                CircleShape,
            )
            .border(
                1.2.dp,
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.95f),
                    1f to Color.White.copy(alpha = 0.40f),
                ),
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (tool.icon == "player-play") {
            // the mock's play is a filled rounded triangle, not an outline glyph
            Canvas(Modifier.size(glyph)) {
                val w = size.width
                val h = size.height
                val p = Path().apply {
                    moveTo(w * 0.28f, h * 0.16f)
                    cubicTo(w * 0.42f, h * 0.08f, w * 0.55f, h * 0.14f, w * 0.82f, h * 0.42f)
                    cubicTo(w * 0.92f, h * 0.52f, w * 0.92f, h * 0.48f, w * 0.82f, h * 0.58f)
                    cubicTo(w * 0.55f, h * 0.86f, w * 0.42f, h * 0.92f, w * 0.28f, h * 0.84f)
                    cubicTo(w * 0.16f, h * 0.76f, w * 0.16f, h * 0.24f, w * 0.28f, h * 0.16f)
                    close()
                }
                drawPath(p, Color.White)
            }
        } else {
            Icon(
                exploreIcon(tool.icon),
                contentDescription = null,
                tint = glyphTint,
                modifier = Modifier.size(glyph),
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    inkHeader: Color,
    inkMuted: Color,
    onSeeAll: (() -> Unit)? = null,
    // The Explore mock capitalises "See All"; the Discover mock writes
    // "See all". Both are the contract of their own page.
    label: String = "See All",
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = GsFonts.Product,
            color = inkHeader,
        )
        Spacer(Modifier.weight(1f))
        if (onSeeAll != null) {
            Row(
                Modifier.clickable(role = Role.Button, onClickLabel = "See All") { onSeeAll() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    fontSize = 11.sp,
                    fontFamily = GsFonts.Product,
                    color = inkMuted,
                )
                Spacer(Modifier.width(4.dp))
                Icon(GsIcons.ArrowRight, contentDescription = null, tint = inkMuted, modifier = Modifier.size(11.dp))
            }
        }
    }
}

/** Catalog icon ids (kebab, shared with the iOS glyph table) → vectors. */
private fun exploreIcon(id: String): ImageVector = when (id) {
    "photo" -> GsIcons.Image
    "player-play" -> GsIcons.PlayCircle
    "waveform" -> GsIcons.AudioWaveform
    "code" -> GsIcons.Code
    "file-text" -> GsIcons.FileText
    "file-description" -> GsIcons.FileText
    "school" -> GsIcons.GraduationCap  // the mock paints a mortarboard, not a schoolhouse
    "brush" -> GsIcons.Brush
    "plane" -> GsIcons.Plane
    "clipboard-text" -> GsIcons.ClipboardList
    "salad" -> GsIcons.Salad
    "sparkles" -> GsIcons.Sparkles
    else -> GsIcons.Sparkles
}

// ---------------------------------------------------------------------------
// EXPLORE — the reference with Popular Tools / Collections / Featured
// ---------------------------------------------------------------------------

@Composable
fun ExploreScreen(
    onOpenChat: (prompt: String, autoSend: Boolean) -> Unit,
    onOpenDiscover: () -> Unit,
    onBack: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    /** Render-harness entry points: the real states, driven from outside. */
    initialSearchOpen: Boolean = false,
) {
    val colors = GsTheme.colors
    val isDark = LocalGsIsDark.current
    val inkStrong = if (isDark) colors.textPrimary else ExploreTones.InkStrong
    val inkHeader = if (isDark) colors.textPrimary else ExploreTones.InkHeader
    val inkBody = if (isDark) colors.textSecondary else ExploreTones.InkBody
    val inkMuted = if (isDark) colors.textTertiary else ExploreTones.InkMuted

    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(initialSearchOpen) }
    val scroll = rememberScrollState()
    val scrollScope = rememberCoroutineScope()

    ExplorePage(
        inkStrong = inkStrong,
        inkHeader = inkHeader,
        inkBody = inkBody,
        inkMuted = inkMuted,
        bottomOverlay = {
            ExploreDock(
                inkStrong = inkStrong,
                onOpenHome = onBack,
                onScrollToTop = { scrollScope.launch { scroll.animateScrollTo(0) } },
                onOpenSettings = onOpenSettings,
            )
        },
    ) {
        ExplorePageHeader(
            inkStrong = inkStrong,
            inkMuted = inkMuted,
            searchOpen = searchOpen,
            onToggleSearch = {
                searchOpen = !searchOpen
                if (!searchOpen) query = ""
            },
        )

        AnimatedVisibility(
            visible = searchOpen,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            ExploreSearchField(query, { query = it }, inkBody, inkMuted)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(10.dp))

            if (query.isNotBlank()) {
                SectionHeader("Results", inkHeader, inkMuted)
                Spacer(Modifier.height(8.dp))
                val results = ExploreCatalog.search(query)
                if (results.isEmpty()) {
                    ExploreEmptySearch(query, inkBody, inkMuted, onAsk = { onOpenChat(query, true) })
                } else {
                    results.forEach { tool ->
                        ExplorePromptRow(tool, inkStrong, inkMuted) { onOpenChat(tool.prompt, false) }
                        Spacer(Modifier.height(7.dp))
                    }
                }
            } else {
                // HERO — the Discover chip, the bubbles on the right, the door
                // into the full Discover page.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .exploreGlass(shape = RoundedCornerShape(24.dp), elevation = 14.dp),
                ) {
                    Box(Modifier.matchParentSize()) {
                        Box(
                            Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(216.dp),
                        ) {
                            Image(
                                painter = painterResource(com.grapsee.gsai.R.drawable.gs_explore_hero),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                alignment = Alignment.TopEnd,
                                modifier = Modifier.matchParentSize(),
                            )
                        }
                        Box(
                            Modifier
                                .matchParentSize()
                                .background(
                                    Brush.horizontalGradient(
                                        0f to Color(0xFFFDF1EC).copy(alpha = 0.94f),
                                        0.45f to Color(0xFFFDF1EC).copy(alpha = 0.22f),
                                        1f to Color.Transparent,
                                    ),
                                ),
                        )
                    }
                    Column(Modifier.padding(13.dp)) {
                        Row(
                            Modifier
                                .background(ExploreTones.DiscoverBg, RoundedCornerShape(13.dp))
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                GsIcons.Sparkles,
                                contentDescription = null,
                                tint = ExploreTones.DiscoverInk,
                                modifier = Modifier.size(10.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Discover",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = GsFonts.Product,
                                color = ExploreTones.DiscoverInk,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Explore AI\nPossibilities",
                            fontSize = 17.5.sp,
                            lineHeight = 21.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = GsFonts.Product,
                            color = inkStrong,
                            modifier = Modifier.widthIn(max = 200.dp),
                        )
                        Spacer(Modifier.height(7.dp))
                        Text(
                            "Powerful tools, creative features, and more ways to bring your ideas to life — all in one place.",
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp,
                            fontFamily = GsFonts.Product,
                            color = inkBody,
                            modifier = Modifier.widthIn(max = 200.dp),
                        )
                        Spacer(Modifier.height(11.dp))
                        Row(
                            Modifier
                                .shadow(
                                    elevation = 9.dp,
                                    shape = RoundedCornerShape(16.dp),
                                    ambientColor = Color(0x2EEDBEA4),
                                    spotColor = Color(0x59EB9E72),
                                )
                                .background(
                                    Brush.horizontalGradient(listOf(ExploreTones.CtaTop, ExploreTones.CtaBottom)),
                                    RoundedCornerShape(16.dp),
                                )
                                .border(
                                    1.dp,
                                    Brush.verticalGradient(
                                        0f to Color.White.copy(alpha = 0.90f),
                                        1f to Color.White.copy(alpha = 0.35f),
                                    ),
                                    RoundedCornerShape(16.dp),
                                )
                                .clickable(role = Role.Button, onClickLabel = "Explore all tools") { onOpenDiscover() }
                                .padding(horizontal = 13.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Explore All",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = GsFonts.Product,
                                color = inkStrong,
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                GsIcons.ArrowRight,
                                contentDescription = null,
                                tint = inkStrong,
                                modifier = Modifier.size(11.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                // POPULAR TOOLS — the reference's horizontal cards
                SectionHeader("Popular Tools", inkHeader, inkMuted, onSeeAll = onOpenDiscover)
                Spacer(Modifier.height(10.dp))
                val grid = ExploreCatalog.tools.take(4)
                grid.chunked(2).forEach { rowTools ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowTools.forEach { tool ->
                            Box(Modifier.weight(1f)) {
                                ExploreToolCardHorizontal(tool, inkStrong, inkMuted) {
                                    onOpenChat(tool.prompt, false)
                                }
                            }
                        }
                        if (rowTools.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                }

                Spacer(Modifier.height(8.dp))

                // CREATIVE COLLECTIONS
                SectionHeader("Creative Collections", inkHeader, inkMuted, onSeeAll = onOpenDiscover)
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ExploreCatalog.collections.forEach { collection ->
                        ExploreCollectionCard(collection, inkStrong, inkMuted) {
                            // a collection opens the Discover page
                            onOpenDiscover()
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // FEATURED — the journey carousel (the mock's Kyoto card first)
                SectionHeader("Featured", inkHeader, inkMuted)
                Spacer(Modifier.height(10.dp))
                ExploreFeaturedCarousel(inkStrong, inkBody, inkMuted) { prompt ->
                    onOpenChat(prompt, false)
                }
            }
            // clearance for the floating dock (height + margins)
            Spacer(Modifier.height(96.dp))
        }
    }
}

/** The reference's Popular Tools card: icon left, copy right, arrow low-right. */
@Composable
private fun ExploreToolCardHorizontal(
    tool: ExploreTool,
    inkStrong: Color,
    inkMuted: Color,
    onOpen: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .exploreGlass(shape = RoundedCornerShape(18.dp), elevation = 10.dp)
            .clickable(role = Role.Button, onClickLabel = tool.title) { onOpen() }
            .padding(11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ExploreIconCircle(tool = tool, circle = 36.dp, glyph = 15.dp)
            Spacer(Modifier.width(9.dp))
            Text(
                tool.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = GsFonts.Product,
                color = inkStrong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            tool.subtitle,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            fontFamily = GsFonts.Product,
            color = inkMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Icon(
            GsIcons.ArrowRight,
            contentDescription = null,
            tint = inkMuted,
            modifier = Modifier
                .size(11.dp)
                .align(Alignment.End),
        )
    }
}

@Composable
private fun ExploreCollectionCard(
    collection: ExploreCatalog.Collection,
    inkStrong: Color,
    inkMuted: Color,
    onOpen: () -> Unit,
) {
    Column(
        Modifier
            .width(150.dp)
            .exploreGlass(shape = RoundedCornerShape(18.dp), elevation = 10.dp)
            .clickable(role = Role.Button, onClickLabel = collection.title) { onOpen() },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(72.dp),
        ) {
            val art = when (collection.id) {
                "work" -> com.grapsee.gsai.R.drawable.gs_explore_col_work
                "learning" -> com.grapsee.gsai.R.drawable.gs_explore_col_learning
                else -> com.grapsee.gsai.R.drawable.gs_explore_col_design
            }
            Image(
                painter = painterResource(art),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.06f),
                            1f to Color.White.copy(alpha = 0.34f),
                        ),
                    ),
            )
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    collection.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GsFonts.Product,
                    color = inkStrong,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(4.dp))
                Icon(GsIcons.ArrowRight, contentDescription = null, tint = inkMuted, modifier = Modifier.size(12.dp))
            }
            Spacer(Modifier.height(2.dp))
            Text(
                collection.subtitle,
                fontSize = 10.sp,
                fontFamily = GsFonts.Product,
                color = inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ExploreFeaturedCarousel(
    inkStrong: Color,
    inkBody: Color,
    inkMuted: Color,
    onOpenChat: (String) -> Unit,
) {
    val pager = rememberPagerState(pageCount = { ExploreCatalog.featuredJourneys.size })
    val pagerScope = rememberCoroutineScope()
    Box(Modifier.fillMaxWidth()) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()) { page ->
            val journey = ExploreCatalog.featuredJourneys[page]
            ExploreFeaturedCard(journey, inkStrong, inkMuted) {
                onOpenChat(journey.prompt)
            }
        }
        // the mock's next control: a glass bubble just inside the card's
        // right edge. The reference paints no page dots, so there are none.
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
                .size(30.dp)
                .shadow(
                    elevation = 6.dp,
                    shape = CircleShape,
                    ambientColor = Color(0x2EE29C7C),
                    spotColor = Color(0x4DE29C7C),
                )
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.75f),
                        1f to ExploreTones.PeachPale.copy(alpha = 0.55f),
                    ),
                    CircleShape,
                )
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.85f),
                        1f to Color.White.copy(alpha = 0.35f),
                    ),
                    CircleShape,
                )
                .clickable(role = Role.Button, onClickLabel = "Next featured journey") {
                    pagerScope.launch {
                        pager.animateScrollToPage(
                            (pager.currentPage + 1) % ExploreCatalog.featuredJourneys.size,
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                GsIcons.ChevronRight,
                contentDescription = null,
                tint = inkBody,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

@Composable
private fun ExploreFeaturedCard(
    journey: ExploreCatalog.FeaturedJourney,
    inkStrong: Color,
    inkMuted: Color,
    onOpen: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .exploreGlass(shape = RoundedCornerShape(18.dp), elevation = 10.dp)
            .clickable(role = Role.Button, onClickLabel = journey.title) { onOpen() },
    ) {
        // the authored scene fills the card's right side under a veil
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(150.dp),
        ) {
            Image(
                painter = painterResource(journey.artRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to Color(0xFFFBEDE7).copy(alpha = 0.88f),
                            0.55f to Color(0xFFFBEDE7).copy(alpha = 0.18f),
                            1f to Color.Transparent,
                        ),
                    ),
            )
        }
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .shadow(
                        elevation = 7.dp,
                        shape = CircleShape,
                        ambientColor = Color(0x2EEDBEA4),
                        spotColor = Color(0x4DE29C7C),
                    )
                    .background(
                        Brush.verticalGradient(
                            0f to lerp(journey.tone.bg, Color.White, 0.45f),
                            1f to journey.tone.bg,
                        ),
                        CircleShape,
                    )
                    .border(
                        1.2.dp,
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.95f),
                            1f to Color.White.copy(alpha = 0.40f),
                        ),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    exploreIcon(journey.icon),
                    contentDescription = null,
                    tint = journey.tone.fg,
                    modifier = Modifier.size(15.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    journey.title,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GsFonts.Product,
                    color = inkStrong,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    journey.subtitle,
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp,
                    fontFamily = GsFonts.Product,
                    color = inkMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // clearance for the carousel's edge bubble
            Spacer(Modifier.width(38.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// DISCOVER — the reference with the EXPLORE hero / chips / composer
// ---------------------------------------------------------------------------

@Composable
fun DiscoverScreen(
    onOpenChat: (prompt: String, autoSend: Boolean) -> Unit,
    onBack: () -> Unit = {},
    initialQuery: String = "",
    initialSearchOpen: Boolean = false,
) {
    val colors = GsTheme.colors
    val isDark = LocalGsIsDark.current
    val inkStrong = if (isDark) colors.textPrimary else ExploreTones.InkStrong
    val inkHeader = if (isDark) colors.textPrimary else ExploreTones.InkHeader
    val inkBody = if (isDark) colors.textSecondary else ExploreTones.InkBody
    val inkMuted = if (isDark) colors.textTertiary else ExploreTones.InkMuted

    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var searchOpen by rememberSaveable { mutableStateOf(initialSearchOpen) }
    var category by rememberSaveable { mutableStateOf(ExploreCategory.All) }
    val scroll = rememberScrollState()
    var draft by rememberSaveable { mutableStateOf("") }
    val canSend = draft.isNotBlank()

    ExplorePage(inkStrong, inkHeader, inkBody, inkMuted) {
        ExplorePageHeader(
            inkStrong = inkStrong,
            inkMuted = inkMuted,
            searchOpen = searchOpen,
            onToggleSearch = {
                searchOpen = !searchOpen
                if (!searchOpen) query = ""
            },
        )

        AnimatedVisibility(
            visible = searchOpen,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            ExploreSearchField(query, { query = it }, inkBody, inkMuted)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(10.dp))

            if (query.isNotBlank()) {
                SectionHeader("Results", inkHeader, inkMuted)
                Spacer(Modifier.height(8.dp))
                val results = ExploreCatalog.search(query)
                if (results.isEmpty()) {
                    ExploreEmptySearch(query, inkBody, inkMuted, onAsk = { onOpenChat(query, true) })
                } else {
                    results.forEach { tool ->
                        ExplorePromptRow(tool, inkStrong, inkMuted) { onOpenChat(tool.prompt, false) }
                        Spacer(Modifier.height(7.dp))
                    }
                }
            } else {
                // HERO — the reference's eyebrow + two-line title + Start Exploring
                Box(
                    Modifier
                        .fillMaxWidth()
                        .exploreGlass(shape = RoundedCornerShape(24.dp), elevation = 14.dp),
                ) {
                    // Same de-seamed artwork: full-card bleed, no frame edge.
                    Image(
                        painter = painterResource(com.grapsee.gsai.R.drawable.gs_explore_hero),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopEnd,
                        modifier = Modifier.matchParentSize(),
                    )
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(
                                Brush.horizontalGradient(
                                    0f to Color(0xFFFDF1EC).copy(alpha = 0.55f),
                                    0.5f to Color.Transparent,
                                    1f to Color.Transparent,
                                ),
                            ),
                    )
                    Column(Modifier.padding(13.dp)) {
                        Text(
                            "EXPLORE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = GsFonts.Product,
                            color = inkMuted,
                            letterSpacing = 2.sp,
                        )
                        Spacer(Modifier.height(7.dp))
                        Text(
                            "Discover what\nyou can do with Nexly",
                            fontSize = 17.sp,
                            lineHeight = 21.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = GsFonts.Product,
                            color = inkStrong,
                        )
                        Spacer(Modifier.height(7.dp))
                        Text(
                            "AI tools, creative features and smart experiences — all in one place.",
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp,
                            fontFamily = GsFonts.Product,
                            color = inkBody,
                            modifier = Modifier.widthIn(max = 220.dp),
                        )
                        Spacer(Modifier.height(11.dp))
                        Row(
                            Modifier
                                .shadow(
                                    elevation = 9.dp,
                                    shape = RoundedCornerShape(16.dp),
                                    ambientColor = Color(0x2EEDBEA4),
                                    spotColor = Color(0x59EB9E72),
                                )
                                .background(
                                    Brush.horizontalGradient(listOf(ExploreTones.CtaTop, ExploreTones.CtaBottom)),
                                    RoundedCornerShape(16.dp),
                                )
                                .border(
                                    1.dp,
                                    Brush.verticalGradient(
                                        0f to Color.White.copy(alpha = 0.90f),
                                        1f to Color.White.copy(alpha = 0.35f),
                                    ),
                                    RoundedCornerShape(16.dp),
                                )
                                .clickable(role = Role.Button, onClickLabel = "Start exploring") {
                                    category = ExploreCategory.All
                                }
                                .padding(horizontal = 13.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Start Exploring",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = GsFonts.Product,
                                color = inkStrong,
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                GsIcons.ArrowRight,
                                contentDescription = null,
                                tint = inkStrong,
                                modifier = Modifier.size(11.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))

                // CATEGORY CHIPS
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    ExploreCategory.entries.forEach { cat ->
                        val active = cat == category
                        Box(
                            Modifier
                                .then(
                                    if (active) {
                                        Modifier
                                            .shadow(
                                                elevation = 8.dp,
                                                shape = RoundedCornerShape(15.dp),
                                                ambientColor = Color(0x2EEDBEA4),
                                                spotColor = Color(0x59EB9E72),
                                            )
                                            .background(
                                                Brush.verticalGradient(
                                                    0f to Color(0xFFFFCDA2),
                                                    1f to ExploreTones.Peach,
                                                ),
                                                RoundedCornerShape(15.dp),
                                            )
                                            .border(
                                                1.2.dp,
                                                Brush.verticalGradient(
                                                    0f to Color.White.copy(alpha = 0.95f),
                                                    1f to Color.White.copy(alpha = 0.45f),
                                                ),
                                                RoundedCornerShape(15.dp),
                                            )
                                    } else {
                                        Modifier.exploreGlass(shape = RoundedCornerShape(15.dp), elevation = 5.dp)
                                    },
                                )
                                .clickable(role = Role.Button, onClickLabel = cat.label) { category = cat }
                                .padding(horizontal = 13.dp, vertical = 6.dp),
                        ) {
                            Text(
                                cat.label,
                                fontSize = 11.5.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                fontFamily = GsFonts.Product,
                                color = if (active) Color.White else inkMuted,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                // FEATURED TOOLS — the reference's vertical cards. "See all"
                // opens the search field: the honest whole-catalogue gesture
                // this page already owns.
                SectionHeader("Featured Tools", inkHeader, inkMuted, onSeeAll = { searchOpen = true }, label = "See all")
                Spacer(Modifier.height(10.dp))
                // The reference's Featured Tools are the mock's own four
                // cards; the chips filter within them.
                val visible = ExploreCatalog.discoverFeatured
                    .filter { category == ExploreCategory.All || it.category == category }
                visible.chunked(2).forEach { rowTools ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowTools.forEach { tool ->
                            Box(Modifier.weight(1f)) {
                                DiscoverToolCardVertical(tool, inkStrong, inkMuted) {
                                    onOpenChat(tool.prompt, false)
                                }
                            }
                        }
                        if (rowTools.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                }

                // POPULAR — prompt rows
                Spacer(Modifier.height(6.dp))
                SectionHeader("Popular", inkHeader, inkMuted, onSeeAll = { searchOpen = true }, label = "See all")
                Spacer(Modifier.height(10.dp))
                val promptRows = ExploreCatalog.tools.filter {
                    it.id in setOf("study-plan", "logo-maker", "trip-planner", "doc-summary")
                }.filter { category == ExploreCategory.All || it.category == category }
                promptRows.forEach { tool ->
                    ExplorePromptRow(tool, inkStrong, inkMuted) { onOpenChat(tool.prompt, false) }
                    Spacer(Modifier.height(7.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // COMPOSER — the reference's Ask me anything with the paper plane
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .navigationBarsPadding()
                .padding(bottom = 8.dp, top = 4.dp)
                .exploreGlass(shape = RoundedCornerShape(24.dp), elevation = 14.dp)
                .padding(horizontal = 11.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // the mock seats the sparkles in a small white well, tinted the
            // warm apricot the empty composer wears
            Box(
                Modifier
                    .size(30.dp)
                    .background(Color.White.copy(alpha = 0.60f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    GsIcons.Sparkles,
                    contentDescription = null,
                    tint = ExploreTones.ApricotFg,
                    modifier = Modifier.size(15.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (draft.isEmpty()) {
                    Text(
                        "Ask me anything…",
                        fontSize = 12.sp,
                        fontFamily = GsFonts.Product,
                        color = inkMuted,
                    )
                }
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 12.sp,
                        fontFamily = GsFonts.Product,
                        color = inkBody,
                    ),
                    cursorBrush = SolidColor(ExploreTones.Peach),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("explore-composer-input"),
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(38.dp)
                    .shadow(
                        elevation = 10.dp,
                        shape = CircleShape,
                        ambientColor = Color(0x2EEDBEA4),
                        spotColor = Color(0x66EB9E72),
                    )
                    .background(
                        Brush.linearGradient(
                            0f to Color(0xFFFFDDB8),
                            1f to ExploreTones.Peach,
                        ),
                        CircleShape,
                    )
                    .border(
                        1.4.dp,
                        Brush.linearGradient(
                            0f to Color.White.copy(alpha = 0.90f),
                            1f to Color.White.copy(alpha = 0.40f),
                        ),
                        CircleShape,
                    )
                    .then(
                        if (canSend) {
                            Modifier.clickable(role = Role.Button, onClickLabel = "Send") {
                                onOpenChat(draft.trim(), true)
                                draft = ""
                            }
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    GsIcons.Navigation,
                    contentDescription = if (canSend) "Send message" else null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** The reference's Featured Tools card: icon top, copy below, arrow low-right. */
@Composable
private fun DiscoverToolCardVertical(
    tool: ExploreTool,
    inkStrong: Color,
    inkMuted: Color,
    onOpen: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .exploreGlass(shape = RoundedCornerShape(18.dp), elevation = 10.dp)
            .clickable(role = Role.Button, onClickLabel = tool.title) { onOpen() }
            .padding(11.dp),
    ) {
        ExploreIconCircle(tool = tool, circle = 36.dp, glyph = 15.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            tool.title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = GsFonts.Product,
            color = inkStrong,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        // the mock seats the arrow in a small glass well beside the subtitle,
        // not on its own row below it
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                tool.subtitle,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                fontFamily = GsFonts.Product,
                color = inkMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(5.dp))
            Box(
                Modifier
                    .size(22.dp)
                    .background(Color.White.copy(alpha = 0.60f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    GsIcons.ArrowRight,
                    contentDescription = null,
                    tint = inkMuted,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
    }
}

@Composable
private fun ExplorePromptRow(
    tool: ExploreTool,
    inkStrong: Color,
    inkMuted: Color,
    onOpen: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .exploreGlass(shape = RoundedCornerShape(16.dp), elevation = 9.dp)
            .clickable(role = Role.Button, onClickLabel = tool.title) { onOpen() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExploreIconCircle(tool = tool, circle = 34.dp, glyph = 15.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                tool.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = GsFonts.Product,
                color = inkStrong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                tool.subtitle,
                fontSize = 10.5.sp,
                fontFamily = GsFonts.Product,
                color = inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(GsIcons.ChevronRight, contentDescription = null, tint = inkMuted, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun ExploreEmptySearch(
    query: String,
    inkBody: Color,
    inkMuted: Color,
    onAsk: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .exploreGlass(shape = RoundedCornerShape(18.dp), elevation = 10.dp)
            .padding(14.dp),
    ) {
        Text(
            "No tool matches \"$query\" yet",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = GsFonts.Product,
            color = inkBody,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            "I can still help — ask me directly and we'll figure it out together.",
            fontSize = 10.5.sp,
            lineHeight = 14.sp,
            fontFamily = GsFonts.Product,
            color = inkMuted,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .shadow(
                    elevation = 9.dp,
                    shape = RoundedCornerShape(14.dp),
                    ambientColor = Color(0x2EEDBEA4),
                    spotColor = Color(0x59EB9E72),
                )
                .background(
                    Brush.horizontalGradient(listOf(ExploreTones.CtaTop, ExploreTones.CtaBottom)),
                    RoundedCornerShape(14.dp),
                )
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.90f),
                        1f to Color.White.copy(alpha = 0.35f),
                    ),
                    RoundedCornerShape(14.dp),
                )
                .clickable(role = Role.Button, onClickLabel = "Ask about ${query.trim()}") { onAsk() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Ask about \"${query.trim()}\"",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = GsFonts.Product,
                color = inkBody,
                maxLines = 1,
            )
            Spacer(Modifier.width(6.dp))
            Icon(GsIcons.ArrowRight, contentDescription = null, tint = inkBody, modifier = Modifier.size(12.dp))
        }
    }
}
