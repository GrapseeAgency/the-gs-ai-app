package com.grapsee.gsai.ui.support

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.data.PlanStore
import com.grapsee.gsai.ui.chat.content.NexlyGlyph
import com.grapsee.gsai.ui.components.GsChoice
import com.grapsee.gsai.ui.components.GsChoiceDialog
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.RefArt
import com.grapsee.gsai.ui.components.RefBleedArt
import com.grapsee.gsai.ui.components.GsWellTone
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefGlyphWell
import com.grapsee.gsai.ui.components.RefGroupHeader
import com.grapsee.gsai.ui.components.RefHeaderAction
import com.grapsee.gsai.ui.components.RefHeroCard
import com.grapsee.gsai.ui.components.RefInfoCard
import com.grapsee.gsai.ui.components.RefSheetPillButton
import com.grapsee.gsai.ui.components.RefRowDivider
import com.grapsee.gsai.ui.components.RefSettingsPage
import com.grapsee.gsai.ui.components.RefSheetRow
import com.grapsee.gsai.ui.components.RefSheetTile
import com.grapsee.gsai.ui.components.RefWellTile
import com.grapsee.gsai.ui.components.TileCorner
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.GsFonts
import com.grapsee.gsai.ui.theme.GsTheme

/**
 * One help topic. This is app CONTENT, not invented telemetry: each answer
 * describes something this build actually does, and a topic whose feature does
 * not exist is not listed.
 */
@Composable
fun HelpSupportScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val colors = GsTheme.colors
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<HelpCategory?>(null) }
    var showFilter by remember { mutableStateOf(false) }
    var showAllTopics by remember { mutableStateOf(false) }
    var categoryTopics by remember { mutableStateOf<HelpCategory?>(null) }

    val topics = HelpCatalog.topics

    val visible = topics.filter { topic ->
        (filter == null || topic.category == filter) &&
            (query.isBlank() ||
                topic.question.contains(query, ignoreCase = true) ||
                topic.answer.contains(query, ignoreCase = true))
    }

    RefSettingsPage(
        title = "Help & Support",
        subtitle = "We're here to help. Find answers, get assistance, or reach out to our team.",
        onBack = onBack,
    ) {
        // ---- Search -------------------------------------------------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.glassSurface.copy(alpha = 0.70f))
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    GsIcons.Search,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(8.dp))
                // A real field, not a painted one: typing here filters the topic
                // list below, so the search the sheet draws actually searches.
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("help-search"),
                    textStyle = TextStyle(
                        fontSize = 11.sp,
                        fontFamily = GsFonts.Product,
                        color = colors.textPrimary,
                    ),
                    decorationBox = { inner ->
                        if (query.isBlank()) {
                            Text(
                                "Search for help, topics, or keywords…",
                                fontSize = 11.sp,
                                fontFamily = GsFonts.Product,
                                color = colors.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    },
                )
            }
            Spacer(Modifier.width(8.dp))
            // The mock's sliders button: opens the category filter that the
            // list below actually applies.
            RefHeaderAction(
                icon = GsIcons.SlidersHorizontal,
                label = "Filter topics",
                onClick = { showFilter = true },
            )
        }

        Spacer(Modifier.height(12.dp))
        // ---- Quick help ---------------------------------------------------
        RefHeroCard {
            RefGlyphWell(glyph = "robot", tone = GsWellTone.Lilac, size = 40.dp)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Need quick help?",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GsFonts.Product,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    "Try our AI assistant for instant answers to common questions.",
                    fontSize = 10.5.sp,
                    lineHeight = 11.sp,
                    fontFamily = GsFonts.Product,
                    color = colors.textTertiary,
                )
            }
            Spacer(Modifier.width(8.dp))
            RefSheetPillButton(
                label = "Ask Nexly",
                onClick = { onNavigate(GsRoutes.chat(null)) },
            )
        }

        // ---- Browse by Category -------------------------------------------
        RefGroupHeader(
            title = "Browse by Category",
            caption = "View all",
            onCaptionClick = { showFilter = true },
        )
        CategoryGrid(
            categories = listOf(
                Triple(HelpCategory.General, GsIcons.CircleHelp, GsWellTone.Lilac),
                Triple(HelpCategory.Troubleshooting, GsIcons.Wrench, GsWellTone.Blue),
                Triple(HelpCategory.Account, GsIcons.Receipt, GsWellTone.Peach),
            ),
            captionFor = ::categoryCaption,
            onOpen = { categoryTopics = it },
        )
        Spacer(Modifier.height(8.dp))
        CategoryGrid(
            categories = listOf(
                Triple(HelpCategory.Privacy, GsIcons.ShieldCheck, GsWellTone.Mint),
                Triple(HelpCategory.Features, GsIcons.Settings, GsWellTone.Lilac),
                Triple(HelpCategory.Support, GsIcons.MessageCircle, GsWellTone.Rose),
            ),
            captionFor = ::categoryCaption,
            onOpen = { categoryTopics = it },
        )

        // ---- Popular topics ----------------------------------------------
        RefGroupHeader(
            title = "Popular Help Topics",
            caption = "View all",
            leadingIcon = GsIcons.Flame,
            onCaptionClick = { showAllTopics = true },
        )
        RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(vertical = 2.dp)) {
            val shown = visible.take(4)
            if (shown.isEmpty()) {
                Box(Modifier.padding(14.dp)) {
                    Text(
                        if (query.isBlank()) {
                            "No topics in this category yet — ask Nexly or contact support below."
                        } else {
                            "No topic matches \"$query\". Try a shorter word, or contact support below."
                        },
                        fontSize = 10.5.sp,
                        lineHeight = 12.sp,
                        fontFamily = GsFonts.Product,
                        color = colors.textTertiary,
                    )
                }
            } else {
                shown.forEachIndexed { index, topic ->
                    if (index > 0) RefRowDivider(startIndent = 57.dp)
                    RefSheetRow(
                        title = topic.question,
                        subtitle = topic.summary,
                        onClick = { onNavigate(GsRoutes.helpArticle(topic.id)) },
                        leading = { RefWellTile(topic.icon, topic.tone, 34.dp) },
                    )
                }
            }
        }

        // ---- Still need help ---------------------------------------------
        Spacer(Modifier.height(18.dp))
        RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RefGlyphWell(glyph = "headset", tone = GsWellTone.Lilac, size = 34.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Still need help?",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = GsFonts.Product,
                        color = colors.textPrimary,
                    )
                    Text(
                        "Our support team is available 24/7 to assist you.",
                        fontSize = 10.5.sp,
                        fontFamily = GsFonts.Product,
                        color = colors.textTertiary,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RefSheetTile(
                    title = "Contact Support",
                    caption = "Get in touch with our team",
                    icon = GsIcons.Mail,
                    tone = GsWellTone.Peach,
                    corner = TileCorner.BottomChevron,
                    centered = true,
                    modifier = Modifier.weight(1f),
                    onClick = { mailSupport(context, "Nexly support", "Hi team,") },
                )
                RefSheetTile(
                    title = "Report a Problem",
                    caption = "Help us improve Nexly",
                    icon = GsIcons.Bug,
                    tone = GsWellTone.Rose,
                    corner = TileCorner.BottomChevron,
                    centered = true,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        mailSupport(
                            context,
                            "Problem report",
                            "What happened:\n\nWhat I expected:\n\nSteps to reproduce:\n",
                        )
                    },
                )
                RefSheetTile(
                    title = "Live Chat",
                    caption = "Chat with Nexly now",
                    icon = GsIcons.MessageCircle,
                    tone = GsWellTone.Blue,
                    corner = TileCorner.BottomChevron,
                    centered = true,
                    modifier = Modifier.weight(1f),
                    // Nexly is the only chat that answers in this build, so the
                    // tile routes there rather than opening a window nobody is
                    // behind.
                    onClick = { onNavigate(GsRoutes.chat(null)) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(vertical = 10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NexlyGlyph(name = "heart", size = 12.dp, color = GsWellTone.Rose.ink)
                Spacer(Modifier.width(6.dp))
                Text(
                    "Your satisfaction matters to us",
                    fontSize = 10.5.sp,
                    fontFamily = GsFonts.Product,
                    color = colors.textTertiary,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
    }

    // ---- Dialogs ----------------------------------------------------------

    if (showFilter) {
        GsChoiceDialog(
            title = "Filter by category",
            subtitle = "Six groups, counted from the articles in this build",
            options = HelpCategory.values().map { category ->
                GsChoice(
                    id = category.name,
                    label = category.label,
                    detail = categoryCaption(category),
                    selected = filter == category,
                )
            },
            onDismiss = { showFilter = false },
            onSelect = { choice ->
                val picked = HelpCategory.values().firstOrNull { it.name == choice.id }
                showFilter = false
                filter = picked
                categoryTopics = picked
            },
        )
    }

    if (showAllTopics) {
        GsChoiceDialog(
            title = "Popular Help Topics",
            subtitle = "${topics.size} articles in this build",
            options = topics.map { topic ->
                GsChoice(
                    id = topic.id,
                    label = topic.question,
                    detail = topic.category.label,
                    tone = topic.tone,
                    selected = false,
                )
            },
            onDismiss = { showAllTopics = false },
            onSelect = { choice ->
                showAllTopics = false
                onNavigate(GsRoutes.helpArticle(choice.id))
            },
        )
    }

    categoryTopics?.let { category ->
        val inCategory = topics.filter { it.category == category }
        GsChoiceDialog(
            title = category.label,
            subtitle = if (inCategory.isEmpty()) {
                "No articles in this category yet"
            } else {
                "${inCategory.size} article${if (inCategory.size == 1) "" else "s"}"
            },
            options = inCategory.map { topic ->
                GsChoice(
                    id = topic.id,
                    label = topic.question,
                    detail = topic.summary,
                    tone = topic.tone,
                    selected = false,
                )
            },
            onDismiss = { categoryTopics = null },
            onSelect = { choice ->
                categoryTopics = null
                onNavigate(GsRoutes.helpArticle(choice.id))
            },
        )
    }
}

private fun categoryCaption(category: HelpCategory): String = when (category) {
    HelpCategory.General -> "Find answers to common questions."
    HelpCategory.Troubleshooting -> "Fix issues and get things working."
    HelpCategory.Account -> "Manage your account, plans and payments."
    HelpCategory.Privacy -> "Learn about your data and security."
    HelpCategory.Features -> "Get to know Nexly's features and tools."
    HelpCategory.Support -> "Contact our team for personalized help."
}

@Composable
private fun CategoryGrid(
    categories: List<Triple<HelpCategory, ImageVector, GsWellTone>>,
    captionFor: (HelpCategory) -> String,
    onOpen: (HelpCategory) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.forEach { (category, icon, tone) ->
            RefSheetTile(
                title = category.label,
                caption = captionFor(category),
                icon = icon,
                tone = tone,
                modifier = Modifier.weight(1f),
                onClick = { onOpen(category) },
            )
        }
    }
}
