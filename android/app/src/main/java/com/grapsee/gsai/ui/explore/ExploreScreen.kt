package com.grapsee.gsai.ui.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
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
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.gsGlass
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
                .gsGlass(role = GsGlassRole.FloatingControl, shape = CircleShape)
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
            .gsGlass(role = GsGlassRole.FloatingControl, shape = RoundedCornerShape(24.dp))
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
            .gsGlass(role = GsGlassRole.CardLight, shape = RoundedCornerShape(26.dp)),
    ) {
        // The reference's hero: pearl on the left where the copy sits, the
        // luminous bubble artwork carrying the right side. The art is the
        // authored backdrop's own bubble (upper-right region of that render),
        // full-strength, with its left edge fading into the pearl card.
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.66f),
        ) {
            Image(
                painter = painterResource(com.grapsee.gsai.R.drawable.gs_home_backdrop),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // The bubble sits at ~(0.74, 0.24) of the authored render;
                // band starts at 15% so the bubble fills the upper frame.
                alignment = BiasAlignment(0.74f, 0.15f),
                // The authored render is gentle; the reference's hero art is
                // vivid — lift saturation so the peach/lavender reads.
                colorFilter = ColorFilter.colorMatrix(
                    ColorMatrix().apply { setToSaturation(1.4f) }
                ),
                modifier = Modifier.matchParentSize(),
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to Color(0xFFFDF1EC).copy(alpha = 0.85f),
                            0.5f to Color(0xFFFDF1EC).copy(alpha = 0.15f),
                            1f to Color.Transparent,
                        ),
                    ),
            )
        }
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
                fontSize = 22.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = GsFonts.Product,
                color = inkStrong,
                modifier = Modifier.widthIn(max = 230.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Powerful tools, creative features, and more ways to bring your ideas to life — all in one place.",
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                fontFamily = GsFonts.Product,
                color = inkBody,
                modifier = Modifier.widthIn(max = 240.dp),
            )
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .background(
                        Brush.horizontalGradient(listOf(ExploreTones.CtaTop, ExploreTones.CtaBottom)),
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
                            Modifier.background(
                                Brush.horizontalGradient(listOf(ExploreTones.Peach, ExploreTones.PeachDeep)),
                                RoundedCornerShape(17.dp),
                            )
                        } else {
                            Modifier.gsGlass(role = GsGlassRole.FloatingControl, shape = RoundedCornerShape(17.dp))
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
            fontSize = 16.5.sp,
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
            .gsGlass(role = GsGlassRole.CardLight, shape = RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClickLabel = tool.title) { onOpen() }
            .padding(14.dp),
    ) {
        Box(
            Modifier
                .size(46.dp)
                .background(tool.tone.bg, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                exploreIcon(tool.icon),
                contentDescription = null,
                tint = tool.tone.fg,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            tool.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = GsFonts.Product,
            color = inkStrong,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            tool.subtitle,
            fontSize = 11.5.sp,
            lineHeight = 15.sp,
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
            .gsGlass(role = GsGlassRole.CardLight, shape = RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClickLabel = tool.title) { onOpen() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(tool.tone.bg, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                exploreIcon(tool.icon),
                contentDescription = null,
                tint = tool.tone.fg,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                tool.title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = GsFonts.Product,
                color = inkStrong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                tool.subtitle,
                fontSize = 11.5.sp,
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
            .gsGlass(role = GsGlassRole.CardLight, shape = RoundedCornerShape(20.dp))
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
            val art = when (collection.subtitle) {
                "Work smarter, not harder" -> com.grapsee.gsai.R.drawable.gs_home_video_art
                "Build new skills" -> com.grapsee.gsai.R.drawable.gs_home_backdrop
                else -> com.grapsee.gsai.R.drawable.gs_home_lotus_new
            }
            Image(
                painter = painterResource(art),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = alignment,
                modifier = Modifier.matchParentSize(),
                alpha = 0.9f,
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
            .gsGlass(role = GsGlassRole.CardLight, shape = RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClickLabel = ExploreCatalog.featured.title) { onOpen() },
    ) {
        // The reference's featured card: the scene fills the card's right
        // side under a pearl veil, words on the left.
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.46f),
        ) {
            Image(
                painter = painterResource(com.grapsee.gsai.R.drawable.gs_home_lotus_new),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
                alpha = 0.9f,
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to Color(0xFFFBEDE7).copy(alpha = 0.94f),
                            0.5f to Color(0xFFFBEDE7).copy(alpha = 0.30f),
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
                    .background(ExploreCatalog.featured.tone.bg, CircleShape),
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
            .gsGlass(role = GsGlassRole.FloatingControl, shape = RoundedCornerShape(26.dp))
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
                .background(
                    Brush.linearGradient(listOf(ExploreTones.PeachPale, ExploreTones.Peach)),
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
                GsIcons.Sparkles,
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
            .gsGlass(role = GsGlassRole.CardLight, shape = RoundedCornerShape(20.dp))
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
                .background(
                    Brush.horizontalGradient(listOf(ExploreTones.CtaTop, ExploreTones.CtaBottom)),
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
