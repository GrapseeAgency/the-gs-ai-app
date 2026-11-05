package com.grapsee.gsai.ui.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.ui.chat.content.NexlyGlyph
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefFootnote
import com.grapsee.gsai.ui.components.RefGroupHeader
import com.grapsee.gsai.ui.components.RefNavRow
import com.grapsee.gsai.ui.components.RefPrimaryButton
import com.grapsee.gsai.ui.components.RefRowDivider
import com.grapsee.gsai.ui.components.RefSettingsPage
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsFonts
import com.grapsee.gsai.ui.theme.GsTheme
import com.grapsee.gsai.ui.theme.LocalGsIsDark

/**
 * ONE TOOL, EXPLAINED BEFORE IT IS STARTED.
 *
 * The four entry points into a tool — Explore's Popular Tools, Discover's
 * Featured Tools, a collection's tool list, and the search results — all used
 * to do the same thing: push a paragraph into the composer and open a blank
 * conversation. The card promised a capability and the app answered with a
 * wall of prompt text the reader had no chance to read in context.
 *
 * This page is the missing middle. It says what the tool is for, the three
 * moves it makes, and offers three openings. Every opening starts the same
 * real conversation the card used to start — so the page adds a step without
 * adding a dead end. "Pairs well with" leads to another tool's page, which is
 * the sub-page of this sub-page.
 *
 * The trip planner keeps its own screen (Travel Agent); its primary action
 * here opens that screen rather than a generic chat, because a purpose-built
 * surface should not be demoted to a seeded prompt.
 */
@Composable
fun ToolDetailScreen(
    toolId: String,
    onBack: () -> Unit,
    onStart: (prompt: String) -> Unit,
    onOpenTool: (String) -> Unit = {},
    onOpenTravelAgent: () -> Unit = {},
) {
    val colors = GsTheme.colors
    val dark = LocalGsIsDark.current
    val tool = ToolCatalog.tool(toolId)
    val detail = ToolCatalog.detail(toolId)

    if (tool == null || detail == null) {
        RefSettingsPage(
            title = "Tool",
            subtitle = "That tool is not in this build",
            onBack = onBack,
        ) {
            RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(18.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Tools change between versions.",
                        fontSize = 12.sp,
                        fontFamily = GsFonts.Product,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    RefPrimaryButton(
                        label = "Explore everything",
                        onClick = onBack,
                        icon = GsIcons.Compass,
                    )
                }
            }
        }
        return
    }

    val pairs = ToolCatalog.pairs(toolId)
    val isTripPlanner = tool.id == "trip-planner"
    // On the dark canvas the tool's ink is a deep colour on near-black, so the
    // glyph and the step numbers disappear. The tone's own wash is the light
    // member of the pair, which is what the dark surface needs.
    val toneInk = if (dark) tool.tone.bg else tool.tone.fg

    RefSettingsPage(
        title = tool.title,
        subtitle = tool.category.label,
        onBack = onBack,
    ) {
        // ---- The tool's own card ------------------------------------------
        RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(0.dp)) {
            Box(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    tool.tone.bg.copy(alpha = if (dark) 0.18f else 0.30f),
                                    Color.Transparent,
                                )
                            )
                        )
                )
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(17.dp))
                                .background(
                                    when {
                                        dark -> Color.White.copy(alpha = 0.10f)
                                        tool.filledTile -> tool.tone.bg
                                        else -> Color.White.copy(alpha = 0.76f)
                                    }
                                )
                                .border(
                                    1.dp,
                                    Color.White.copy(alpha = if (dark) 0.16f else 0.88f),
                                    RoundedCornerShape(17.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            NexlyGlyph(name = tool.icon, size = 24.dp, color = toneInk)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                tool.category.label.uppercase(),
                                fontSize = 9.5.sp,
                                letterSpacing = 0.8.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = GsFonts.Product,
                                color = toneInk,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                tool.title,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = GsFonts.Product,
                                color = colors.textPrimary,
                            )
                            Text(
                                tool.subtitle,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                fontFamily = GsFonts.Product,
                                color = colors.textSecondary,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        detail.blurb,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        fontFamily = GsFonts.Product,
                        color = colors.textSecondary,
                    )
                }
            }
        }

        // ---- What it does, in three moves ---------------------------------
        RefGroupHeader("How it works", "Three moves, in order")
        RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(14.dp)) {
            detail.steps.forEachIndexed { index, step ->
                if (index > 0) Spacer(Modifier.height(11.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(tool.tone.bg.copy(alpha = if (dark) 0.16f else 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${index + 1}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = GsFonts.Product,
                            color = toneInk,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        step,
                        modifier = Modifier.weight(1f),
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        fontFamily = GsFonts.Product,
                        color = colors.textSecondary,
                    )
                }
            }
        }

        // ---- Openings -----------------------------------------------------
        RefGroupHeader("Try it with", "Tap one to start — nothing sends until you do")
        RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(0.dp)) {
            detail.examples.forEachIndexed { index, example ->
                if (index > 0) RefRowDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClickLabel = example) { onStart(example) }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("tool-example-$index"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        example,
                        modifier = Modifier.weight(1f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontFamily = GsFonts.Product,
                        color = colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        GsIcons.ArrowRight,
                        contentDescription = null,
                        tint = colors.textTertiary,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
            RefRowDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = "Use my own words") {
                        onStart(tool.prompt)
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag("tool-open-composer"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Use my own words",
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = GsFonts.Product,
                    color = colors.accentStrong,
                )
                Icon(
                    GsIcons.ArrowRight,
                    contentDescription = null,
                    tint = colors.accentStrong,
                    modifier = Modifier.size(13.dp),
                )
            }
        }

        // ---- Pairs --------------------------------------------------------
        if (pairs.isNotEmpty()) {
            RefGroupHeader("Pairs well with", "The next tool in the same job")
            RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(0.dp)) {
                pairs.forEachIndexed { index, pair ->
                    if (index > 0) RefRowDivider()
                    RefNavRow(
                        title = pair.title,
                        subtitle = pair.subtitle,
                        onClick = { onOpenTool(pair.id) },
                        leading = { PairWell(pair) },
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        RefFootnote(
            if (isTripPlanner) {
                "The trip planner has its own screen: dates, pace and a day-by-day board you can edit."
            } else {
                "A tool is a way of starting the same assistant. Nothing is installed and nothing is sent until you press send."
            }
        )
        Spacer(Modifier.height(10.dp))
        RefPrimaryButton(
            label = if (isTripPlanner) "Open Travel Agent" else "Start a conversation",
            onClick = { if (isTripPlanner) onOpenTravelAgent() else onStart(tool.prompt) },
            icon = if (isTripPlanner) GsIcons.Plane else GsIcons.Sparkles,
        )
        Spacer(Modifier.height(16.dp))
    }
}

/** A paired tool's own glyph in its own tone — a row of identical sparkles
 *  would say "related" and nothing else. */
@Composable
private fun PairWell(tool: ExploreTool) {
    val dark = LocalGsIsDark.current
    Box(
        Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(tool.tone.bg.copy(alpha = if (dark) 0.20f else 0.90f)),
        contentAlignment = Alignment.Center,
    ) {
        NexlyGlyph(
            name = tool.icon,
            size = 15.dp,
            color = if (dark) tool.tone.bg else tool.tone.fg,
        )
    }
}
