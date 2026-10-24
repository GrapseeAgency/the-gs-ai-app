package com.grapsee.gsai.ui.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import kotlin.math.min
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.ui.components.ReferenceBackdrop
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsFonts
import com.grapsee.gsai.ui.theme.GsTheme
import com.grapsee.gsai.ui.theme.LocalGsIsDark
import kotlinx.coroutines.launch

/**
 * Explore AI — the discover page.
 *
 * Matches the Explore reference pair at full brightness: pearl backdrop,
 * luminous glass panels, the featured hero with its Discover chip and Explore
 * All door, category chips, a live search field, the Featured Tools grid,
 * Popular prompt rows, Creative Collections and the featured journey card.
 * No bottom navigation bar — the reference's nav is intentionally absent;
 * back is the system gesture, and everything on the page leads into a real
 * conversation via [onOpenChat] (prompt prefill / auto-send), the same real
 * dispatch path Home's quick pills use.
 */
@Composable
fun ExploreScreen(
    onOpenChat: (prompt: String, autoSend: Boolean) -> Unit,
    onBack: () -> Unit = {},
    /** Render-harness entry points: the real states, driven from outside. */
    initialQuery: String = "",
    initialSearchOpen: Boolean = false,
) {
    val colors = GsTheme.colors
    val isDark = LocalGsIsDark.current
    val inkStrong = if (isDark) colors.textPrimary else Color(0xFF181C30)
    val inkHeader = if (isDark) colors.textPrimary else Color(0xFF2B2C37)
    val inkBody = if (isDark) colors.textSecondary else Color(0xFF4A4B55)
    val inkMuted = if (isDark) colors.textTertiary else Color(0xFF8D8D95)

    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var searchOpen by rememberSaveable { mutableStateOf(initialSearchOpen) }
    var category by rememberSaveable { mutableStateOf(ExploreCategory.All) }
    var showAllTools by rememberSaveable { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        // Same pearl world as Home: the authored full-bleed render in light,
        // the procedural gradient for Solid style and dark.
        if (isDark || SettingsStore.backgroundStyle == "Solid") {
            ReferenceBackdrop(Modifier.matchParentSize(), showBlossom = false, bokeh = false)
        } else {
            Image(
                painter = painterResource(com.grapsee.gsai.R.drawable.gs_home_backdrop),
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
            ExploreHeader(
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
                SearchField(
                    query = query,
                    onQuery = { query = it },
                    inkBody = inkBody,
                    inkMuted = inkMuted,
                )
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(scroll)
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.height(14.dp))

                if (query.isNotBlank()) {
                    SectionHeader("Results", inkHeader, inkMuted, count = ExploreCatalog.search(query).size)
                    Spacer(Modifier.height(10.dp))
                    val results = ExploreCatalog.search(query)
                    if (results.isEmpty()) {
                        EmptySearch(query, inkBody, inkMuted, onAsk = { onOpenChat(query, true) })
                    } else {
                        results.forEach { tool ->
                            PromptRow(tool, inkStrong, inkMuted) { onOpenChat(tool.prompt, false) }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                } else {
                    HeroCard(
                        inkStrong = inkStrong,
                        inkBody = inkBody,
                        onExploreAll = {
                            category = ExploreCategory.All
                            showAllTools = true
                            scope.launch { scroll.animateScrollTo(scroll.maxValue) }
                        },
                    )
                    Spacer(Modifier.height(18.dp))
                    CategoryChips(category, onSelect = { category = it })
                    Spacer(Modifier.height(20.dp))

                    val cat = category
                    val tools = ExploreCatalog.byCategory(cat)
                    SectionHeader(
                        "Featured Tools",
                        inkHeader,
                        inkMuted,
                        onSeeAll = { showAllTools = !showAllTools },
                        seeAllLabel = if (showAllTools) "Show less" else "See All",
                    )
                    Spacer(Modifier.height(12.dp))
                    val visible = if (showAllTools) tools else tools.take(4)
                    visible.chunked(2).forEach { rowTools ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            rowTools.forEach { tool ->
                                Box(Modifier.weight(1f)) {
                                    ToolCard(tool, inkStrong, inkMuted) {
                                        onOpenChat(tool.prompt, false)
                                    }
                                }
                            }
                            if (rowTools.size == 1) Spacer(Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    val promptRows = ExploreCatalog.tools.filter { it.id in setOf("study-plan", "logo-maker", "trip-planner", "doc-summary") }
                        .filter { cat == ExploreCategory.All || it.category == cat }
                    if (promptRows.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        SectionHeader("Popular", inkHeader, inkMuted)
                        Spacer(Modifier.height(12.dp))
                        promptRows.forEach { tool ->
                            PromptRow(tool, inkStrong, inkMuted) { onOpenChat(tool.prompt, false) }
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    SectionHeader("Creative Collections", inkHeader, inkMuted)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ExploreCatalog.collections.forEach { collection ->
                            CollectionCard(collection, inkStrong, inkMuted) {
                                category = collection.category
                                showAllTools = false
                                scope.launch { scroll.animateScrollTo(0) }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    SectionHeader("Featured", inkHeader, inkMuted)
                    Spacer(Modifier.height(12.dp))
                    FeaturedCard(inkStrong, inkBody, inkMuted) {
                        onOpenChat(ExploreCatalog.featured.prompt, false)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            ExploreComposer(
                inkBody = inkBody,
                inkMuted = inkMuted,
                onSend = { text -> onOpenChat(text, true) },
            )
        }
    }
}

/** Header: orb + product name + the search circle (reference-exact). */
@Composable
private fun ExploreHeader(
    inkStrong: Color,
    inkMuted: Color,
    searchOpen: Boolean,
    onToggleSearch: () -> Unit,
) {
    val colors = GsTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(com.grapsee.gsai.R.drawable.gs_companion_orb),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(38.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                "Nexly",
                fontSize = 16.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = GsFonts.Product,
                color = inkStrong,
            )
            Text(
                "AI Companion",
                fontSize = 11.sp,
                fontFamily = GsFonts.Product,
                color = inkMuted,
            )
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(40.dp)
                .exploreGlass(shape = CircleShape, elevation = 8.dp)
                .clickable(role = Role.Button, onClickLabel = if (searchOpen) "Close search" else "Open search") {
                    onToggleSearch()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                GsIcons.Search,
                contentDescription = null,
                tint = inkStrong,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** The expanding search field. Filters the real catalog; nothing faked. */
@Composable
private fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    inkBody: Color,
    inkMuted: Color,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .exploreGlass(shape = RoundedCornerShape(24.dp), elevation = 10.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(GsIcons.Search, contentDescription = null, tint = inkMuted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    "Search tools and abilities",
                    fontSize = 13.5.sp,
                    fontFamily = GsFonts.Product,
                    color = inkMuted,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 13.5.sp,
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
                    .size(16.dp)
                    .clickable(role = Role.Button, onClickLabel = "Clear search") { onQuery("") },
            )
        }
    }
}

/** Hero: Discover chip + title + body + the Explore All door + ethereal art. */
@Composable
private fun HeroCard(
    inkStrong: Color,
    inkBody: Color,
    onExploreAll: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .exploreGlass(shape = RoundedCornerShape(26.dp), elevation = 14.dp),
    ) {
        // The reference's hero: pearl on the left where the copy sits, a
        // The reference's OWN iridescent bubbles carry the card's right side:
        // the artwork is cropped from the authored Explore mock and shipped
        // as gs_explore_hero, its left edge fading into the pearl so the
        // copy sits on quiet ground. matchParentSize, not fillMaxHeight —
        // the scroll's constraints are unbounded and a fill would collapse.
        Image(
            painter = painterResource(com.grapsee.gsai.R.drawable.gs_explore_hero),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.CenterEnd,
            modifier = Modifier.matchParentSize(),
        )
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
        Column(Modifier.padding(18.dp)) {
            Row(
                Modifier
                    .background(ExploreTones.DiscoverBg, RoundedCornerShape(14.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    GsIcons.Sparkles,
                    contentDescription = null,
                    tint = ExploreTones.DiscoverInk,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    "Discover",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = GsFonts.Product,
                    color = ExploreTones.DiscoverInk,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Explore AI\nPossibilities",
                fontSize = 23.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = GsFonts.Product,
                color = inkStrong,
                modifier = Modifier.widthIn(max = 205.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Powerful tools, creative features, and more ways to bring your ideas to life — all in one place.",
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                fontFamily = GsFonts.Product,
                color = inkBody,
                modifier = Modifier.widthIn(max = 215.dp),
            )
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .shadow(
                        elevation = 10.dp,
                        shape = RoundedCornerShape(18.dp),
                        ambientColor = Color(0x2EEDBEA4),
                        spotColor = Color(0x59EB9E72),
                    )
                    .background(
                        Brush.horizontalGradient(listOf(ExploreTones.CtaTop, ExploreTones.CtaBottom)),
                        RoundedCornerShape(18.dp),
                    )
                    .border(
                        1.dp,
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.90f),
                            1f to Color.White.copy(alpha = 0.35f),
                        ),
                        RoundedCornerShape(18.dp),
                    )
                    .clickable(role = Role.Button, onClickLabel = "Explore all tools") { onExploreAll() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Explore All",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GsFonts.Product,
                    color = inkStrong,
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    GsIcons.ArrowRight,
                    contentDescription = null,
                    tint = inkStrong,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/** Category chips — live filter, peach when selected. */
@Composable
private fun CategoryChips(
    selected: ExploreCategory,
    onSelect: (ExploreCategory) -> Unit,
) {
    val isDark = LocalGsIsDark.current
    val colors = GsTheme.colors
    val inkMuted = if (isDark) colors.textTertiary else Color(0xFF7A7B85)
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ExploreCategory.entries.forEach { cat ->
            val active = cat == selected
            Box(
                Modifier
                    .then(
                        if (active) {
                            Modifier
                                .shadow(
                                    elevation = 8.dp,
                                    shape = RoundedCornerShape(17.dp),
                                    ambientColor = Color(0x2EEDBEA4),
                                    spotColor = Color(0x59EB9E72),
                                )
                                .background(
                                    Brush.horizontalGradient(listOf(ExploreTones.Peach, ExploreTones.PeachDeep)),
                                    RoundedCornerShape(17.dp),
                                )
                                .border(
                                    1.dp,
                                    Brush.verticalGradient(
                                        0f to Color.White.copy(alpha = 0.85f),
                                        1f to Color.White.copy(alpha = 0.30f),
                                    ),
                                    RoundedCornerShape(17.dp),
                                )
                        } else {
                            Modifier.exploreGlass(shape = RoundedCornerShape(17.dp), elevation = 6.dp)
                        },
                    )
                    .clickable(role = Role.Button, onClickLabel = cat.label) { onSelect(cat) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    cat.label,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    fontFamily = GsFonts.Product,
                    color = if (active) Color.White else inkMuted,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    inkHeader: Color,
    inkMuted: Color,
    count: Int? = null,
    onSeeAll: (() -> Unit)? = null,
    seeAllLabel: String = "See All",
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = GsFonts.Product,
            color = inkHeader,
        )
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                count.toString(),
                fontSize = 12.5.sp,
                fontFamily = GsFonts.Product,
                color = inkMuted,
            )
        }
        Spacer(Modifier.weight(1f))
        if (onSeeAll != null) {
            Row(
                Modifier.clickable(role = Role.Button, onClickLabel = seeAllLabel) { onSeeAll() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    seeAllLabel,
                    fontSize = 12.5.sp,
                    fontFamily = GsFonts.Product,
                    color = inkMuted,
                )
                Spacer(Modifier.width(4.dp))
                Icon(GsIcons.ArrowRight, contentDescription = null, tint = inkMuted, modifier = Modifier.size(12.dp))
            }
        }
    }
}

/** Featured Tools grid card. Tapping opens the tool's real conversation. */
@Composable
private fun ToolCard(
    tool: ExploreTool,
    inkStrong: Color,
    inkMuted: Color,
    onOpen: () -> Unit,
) {
    Column(
        Modifier
            .exploreGlass(shape = RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClickLabel = tool.title) { onOpen() }
            .padding(14.dp),
    ) {
        ExploreIconCircle(tool = tool, circle = 46.dp, glyph = 20.dp)
        Spacer(Modifier.height(10.dp))
        Text(
            tool.title,
            fontSize = 15.5.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = GsFonts.Product,
            color = inkStrong,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            tool.subtitle,
            fontSize = 12.5.sp,
            lineHeight = 16.sp,
            fontFamily = GsFonts.Product,
            color = inkMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Icon(
            GsIcons.ArrowRight,
            contentDescription = null,
            tint = inkMuted,
            modifier = Modifier
                .size(14.dp)
                .align(Alignment.End),
        )
    }
}

/** Popular / results row. */
@Composable
private fun PromptRow(
    tool: ExploreTool,
    inkStrong: Color,
    inkMuted: Color,
    onOpen: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .exploreGlass(shape = RoundedCornerShape(18.dp), elevation = 9.dp)
            .clickable(role = Role.Button, onClickLabel = tool.title) { onOpen() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExploreIconCircle(tool = tool, circle = 40.dp, glyph = 18.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                tool.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = GsFonts.Product,
                color = inkStrong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                tool.subtitle,
                fontSize = 12.5.sp,
                fontFamily = GsFonts.Product,
                color = inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(GsIcons.ChevronRight, contentDescription = null, tint = inkMuted, modifier = Modifier.size(16.dp))
    }
}

/** Collection card: artwork wash + title, taps filter the page. */
@Composable
private fun CollectionCard(
    collection: ExploreCatalog.Collection,
    inkStrong: Color,
    inkMuted: Color,
    onOpen: () -> Unit,
) {
    Column(
        Modifier
            .width(168.dp)
            .exploreGlass(shape = RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClickLabel = collection.title) { onOpen() },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(86.dp),
        ) {
            // Each collection carries its own scene, like the reference set:
            // the lotus for Design, the pearl-drape scene for Work, the
            // bubble close-up for Learning.
            val alignment = when (collection.subtitle) {
                "Work smarter, not harder" -> BiasAlignment(0.5f, 0.62f)
                "Build new skills" -> BiasAlignment(0.74f, 0.24f)
                else -> Alignment.Center
            }
            // Each scene is cropped from the authored Explore mock: the
            // lotus for Design, the misty pagoda landscape for Work, the
            // orange lotus close-up for Learning.
            val art = when (collection.subtitle) {
                "Work smarter, not harder" -> com.grapsee.gsai.R.drawable.gs_explore_col_work
                "Build new skills" -> com.grapsee.gsai.R.drawable.gs_explore_col_learning
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
                            0f to Color.White.copy(alpha = 0.10f),
                            1f to Color.White.copy(alpha = 0.38f),
                        ),
                    ),
            )
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    collection.title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GsFonts.Product,
                    color = inkStrong,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(4.dp))
                Icon(GsIcons.ArrowRight, contentDescription = null, tint = inkMuted, modifier = Modifier.size(13.dp))
            }
            Spacer(Modifier.height(2.dp))
            Text(
                collection.subtitle,
                fontSize = 11.sp,
                fontFamily = GsFonts.Product,
                color = inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The featured journey card (reference: Weekend in Kyoto). */
@Composable
private fun FeaturedCard(
    inkStrong: Color,
    inkBody: Color,
    inkMuted: Color,
    onOpen: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .exploreGlass(shape = RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClickLabel = ExploreCatalog.featured.title) { onOpen() },
    ) {
        // The reference's featured card: the scene fills the card's right
        // side under a pearl veil, words on the left. No pagoda asset ships,
        // so the scene is drawn — soft sky, distant hills, the pagoda's
        // three tiers and a blossom branch — in the reference's palette.
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.50f),
        ) {
            Image(
                painter = painterResource(com.grapsee.gsai.R.drawable.gs_explore_kyoto),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to Color(0xFFFBEDE7).copy(alpha = 0.80f),
                            0.45f to Color(0xFFFBEDE7).copy(alpha = 0.12f),
                            1f to Color.Transparent,
                        ),
                    ),
            )
        }
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .shadow(
                        elevation = 7.dp,
                        shape = CircleShape,
                        ambientColor = Color(0x2EEDBEA4),
                        spotColor = Color(0x4DE29C7C),
                    )
                    .background(
                        Brush.verticalGradient(
                            0f to lerp(ExploreCatalog.featured.tone.bg, Color.White, 0.45f),
                            1f to ExploreCatalog.featured.tone.bg,
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
                    GsIcons.Sparkles,
                    contentDescription = null,
                    tint = ExploreCatalog.featured.tone.fg,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    ExploreCatalog.featured.title,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GsFonts.Product,
                    color = inkStrong,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    ExploreCatalog.featured.subtitle,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    fontFamily = GsFonts.Product,
                    color = inkMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(30.dp)
                    .background(Color.White.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    GsIcons.ChevronRight,
                    contentDescription = null,
                    tint = inkBody,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

/**
 * The composer (reference #2): "Ask me anything…" that sends a REAL message
 * into a fresh conversation — auto-send, the same path as the chat screen.
 */
@Composable
private fun ExploreComposer(
    inkBody: Color,
    inkMuted: Color,
    onSend: (String) -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    val canSend = draft.isNotBlank()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .padding(bottom = 10.dp, top = 4.dp)
            .exploreGlass(shape = RoundedCornerShape(26.dp), elevation = 14.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            GsIcons.Sparkles,
            contentDescription = null,
            tint = inkMuted,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (draft.isEmpty()) {
                Text(
                    "Ask me anything…",
                    fontSize = 13.5.sp,
                    fontFamily = GsFonts.Product,
                    color = inkMuted,
                )
            }
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 13.5.sp,
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
                .size(42.dp)
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
                        Modifier.clickable(
                            role = Role.Button,
                            onClickLabel = "Send",
                        ) {
                            onSend(draft.trim())
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
                tint = if (canSend) Color.White else inkMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Honest empty state: the query becomes the conversation. */
@Composable
private fun EmptySearch(
    query: String,
    inkBody: Color,
    inkMuted: Color,
    onAsk: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .exploreGlass(shape = RoundedCornerShape(20.dp))
            .padding(18.dp),
    ) {
        Text(
            "No tool matches \"$query\" yet",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = GsFonts.Product,
            color = inkBody,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "I can still help — ask me directly and we'll figure it out together.",
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontFamily = GsFonts.Product,
            color = inkMuted,
        )
        Spacer(Modifier.height(12.dp))
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
                .clickable(role = Role.Button, onClickLabel = "Ask about ${query.trim()}") { onAsk() }
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Ask about \"${query.trim()}\"",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = GsFonts.Product,
                color = inkBody,
                maxLines = 1,
            )
            Spacer(Modifier.width(6.dp))
            Icon(GsIcons.ArrowRight, contentDescription = null, tint = inkBody, modifier = Modifier.size(13.dp))
        }
    }
}

/**
 * The reference's glass is RICH: every panel is a warm gradient fill with the
 * bloom showing through, a luminous rim that brightens toward the top-left,
 * and a soft warm shadow. Flat fills read as plastic next to it — the mock's
 * buttons, icon circles and cards all carry this treatment, and so does
 * everything on this page.
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
                0f to Color(0xFFFFFAF6).copy(alpha = if (isDark) 0.90f else 0.97f),
                1f to Color(0xFFFCEDE5).copy(alpha = if (isDark) 0.80f else 0.88f),
                start = Offset.Zero,
                end = Offset.Infinite,
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

/** The icon circle: tinted gradient, white rim, its own soft glow. */
@Composable
private fun ExploreIconCircle(
    tool: ExploreTool,
    circle: Dp,
    glyph: Dp,
) {
    val bgLight = lerp(tool.tone.bg, Color.White, 0.45f)
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
                Brush.verticalGradient(0f to bgLight, 1f to tool.tone.bg),
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
            exploreIcon(tool.icon),
            contentDescription = null,
            tint = tool.tone.fg,
            modifier = Modifier.size(glyph),
        )
    }
}

/** Catalog icon ids (kebab, shared with the iOS glyph table) → vectors. */
private fun exploreIcon(id: String): ImageVector = when (id) {
    "photo" -> GsIcons.Image
    "player-play" -> GsIcons.PlayCircle
    "microphone" -> GsIcons.Mic
    "code" -> GsIcons.Code
    "file-text" -> GsIcons.FileText
    "school" -> GsIcons.School
    "brush" -> GsIcons.Brush
    "plane" -> GsIcons.Plane
    "clipboard-text" -> GsIcons.ClipboardList
    "salad" -> GsIcons.Salad
    "sparkles" -> GsIcons.Sparkles
    else -> GsIcons.Sparkles
}
