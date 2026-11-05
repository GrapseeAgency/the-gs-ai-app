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
import com.grapsee.gsai.ui.components.gsGlass
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefFootnote
import com.grapsee.gsai.ui.components.RefGroupHeader
import com.grapsee.gsai.ui.components.RefPrimaryButton
import com.grapsee.gsai.ui.components.RefSettingsPage
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsFonts
import com.grapsee.gsai.ui.theme.GsTheme
import com.grapsee.gsai.ui.theme.LocalGsIsDark

/**
 * ONE COLLECTION, WITH ITS OWN TOOLS.
 *
 * The Creative Collections carousel on Explore had three cards and one
 * destination: every one of them called `onOpenDiscover()`, so tapping "Work &
 * Productivity" opened the entire mixed catalogue. The card made a promise
 * ("here are the tools for this") and the app answered a different question
 * ("here is everything"). That is the kind of small lie this pass exists to
 * remove.
 *
 * This page is what the card promised: the collection's name, what it is for,
 * how many tools it holds, and then exactly those tools — each of which opens a
 * real conversation, like every other card in Explore.
 *
 * A collection with no tools in this build says so instead of showing an empty
 * list under a heading.
 */
@Composable
fun CollectionScreen(
    collectionId: String,
    onBack: () -> Unit,
    onOpenChat: (prompt: String, autoSend: Boolean) -> Unit,
    onExploreAll: () -> Unit = {},
    /** A tool card opens the tool's own page, which then starts the chat. */
    onOpenTool: (String) -> Unit = {},
) {
    val colors = GsTheme.colors
    val dark = LocalGsIsDark.current
    val collection = ExploreCatalog.collections.firstOrNull { it.id == collectionId }

    if (collection == null) {
        RefSettingsPage(
            title = "Collection",
            subtitle = "That collection is not in this build",
            onBack = onBack,
        ) {
            RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(18.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Collections change between versions.",
                        fontSize = 12.sp,
                        fontFamily = GsFonts.Product,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    RefPrimaryButton(label = "Explore everything", onClick = onExploreAll, icon = GsIcons.Compass)
                }
            }
        }
        return
    }

    val tools = ExploreCatalog.tools.filter { it.category == collection.category }
    val lead = tools.firstOrNull()

    RefSettingsPage(
        title = collection.title,
        subtitle = collection.subtitle,
        onBack = onBack,
    ) {
        // ---- The collection's own card -----------------------------------
        RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(0.dp)) {
            Box(Modifier.fillMaxWidth()) {
                // A wash in the collection's own tone, so the page reads as a
                // place rather than as another list.
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    // A quarter-strength wash, not a colour block: the page is cream, and a
                                    // saturated green slab beside it reads as a different app.
                                    (lead?.tone?.bg ?: Color.White).copy(alpha = if (dark) 0.16f else 0.26f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = if (dark) 0.12f else 0.72f))
                                .border(
                                    1.dp,
                                    Color.White.copy(alpha = if (dark) 0.16f else 0.85f),
                                    RoundedCornerShape(14.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            NexlyGlyph(
                                name = "sparkles",
                                size = 20.dp,
                                color = lead?.tone?.fg ?: colors.accentStrong
                            )
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                collection.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = GsFonts.Product,
                                color = colors.textPrimary,
                            )
                            Text(
                                "${collection.category.label} • ${tools.size} tool${if (tools.size == 1) "" else "s"}",
                                fontSize = 11.sp,
                                fontFamily = GsFonts.Product,
                                color = colors.textTertiary,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = collectionBlurb(collection.id, collection.subtitle),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        fontFamily = GsFonts.Product,
                        color = colors.textSecondary,
                    )
                }
            }
        }

        // ---- Its tools ----------------------------------------------------
        if (tools.isEmpty()) {
            RefGroupHeader("Tools", "None in this build yet")
            RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(16.dp)) {
                Text(
                    "This collection is a promise the build cannot keep yet, so it holds no tools. Explore's other collections are complete.",
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    fontFamily = GsFonts.Product,
                    color = colors.textSecondary,
                )
            }
        } else {
            RefGroupHeader("Tools", "Each one opens a real conversation")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tools.forEach { tool ->
                    CollectionToolCard(tool) { onOpenTool(tool.id) }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        RefFootnote("Each tool opens its own page first, with three openings to start from. Nothing is sent until you press send.")
        Spacer(Modifier.height(10.dp))
        RefPrimaryButton(
            label = "Explore everything",
            onClick = onExploreAll,
            icon = GsIcons.Compass,
        )
        Spacer(Modifier.height(16.dp))
    }
}

/** One tool in a collection: glyph, name, what it does, and an arrow. */
@Composable
private fun CollectionToolCard(tool: ExploreTool, onOpen: () -> Unit) {
    val colors = GsTheme.colors
    val dark = LocalGsIsDark.current
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .gsGlass(GsGlassRole.CardLight, shape)
            .clickable(role = Role.Button, onClickLabel = tool.title, onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 11.dp)
            .testTag("collection-tool-${tool.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (tool.filledTile) tool.tone.bg else Color.White.copy(alpha = if (dark) 0.12f else 0.66f))
                .border(
                    1.dp,
                    Color.White.copy(alpha = if (dark) 0.14f else 0.80f),
                    RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            NexlyGlyph(
                name = tool.icon,
                size = 17.dp,
                color = if (tool.filledTile) tool.tone.fg else (tool.tone.fg)
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                tool.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = GsFonts.Product,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                tool.subtitle,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                fontFamily = GsFonts.Product,
                color = colors.textTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            GsIcons.ArrowRight,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(13.dp)
        )
    }
}

/**
 * The collection's own sentence. The card on Explore carries a four-word
 * strapline; a page needs to say what the place is FOR.
 */
private fun collectionBlurb(id: String, fallback: String): String = when (id) {
    "design" -> "Work that is judged by the eye: copy, layout, naming, moodboards and the words around them. Every tool here starts a conversation with the visual problem already framed."
    "work" -> "The jobs that eat an afternoon: summarising a thread, turning notes into a plan, drafting the message you keep postponing, and checking your own writing."
    "learning" -> "Explaining, quizzing and practising — the three things a person actually does when they learn something, rather than the three things a course sells."
    else -> fallback
}
