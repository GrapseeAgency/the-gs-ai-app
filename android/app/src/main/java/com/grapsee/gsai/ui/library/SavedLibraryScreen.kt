package com.grapsee.gsai.ui.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.data.local.SavedItemEntity
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.GsWellTone
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefGroupHeader
import com.grapsee.gsai.ui.components.RefInfoCard
import com.grapsee.gsai.ui.components.RefInlinePill
import com.grapsee.gsai.ui.components.RefNavRow
import com.grapsee.gsai.ui.components.RefRowDivider
import com.grapsee.gsai.ui.components.RefSettingsPage
import com.grapsee.gsai.ui.components.RefWellTile
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsFonts
import com.grapsee.gsai.ui.theme.GsTheme
import java.time.LocalDate

/**
 * SAVED LIBRARY — the page behind every save.
 *
 * Until this screen existed, `ChatRepository.saveToLibrary` wrote rows that
 * nothing in the app could ever read back: a chat turn, a studio image or a
 * draft went into `saved_items` and the only place any of it surfaced was the
 * four-row "Recent Memories" preview in Memory Management — which showed the
 * newest four and offered no way to reach the rest. Saving was a one-way door.
 *
 * So this page is the destination those rows always implied:
 *
 *  - **Facets are built from the data, not from a fixed list.** A "Drafts"
 *    chip over an empty page is the dead end this repo keeps deleting, so a
 *    kind with no items has no chip, and an empty library has no chips at all.
 *  - **Day headings, not one flat list.** A library that grows for months is
 *    unreadable as a single column; "Today / Yesterday / 3 Nov 2026" is the
 *    smallest structure that keeps the newest save at the top where it belongs.
 *  - **Every row opens its own page.** The list truncates at 72 characters;
 *    the item page is where the whole answer lives, with copy and delete.
 *  - **The empty state is a page, not a shrug.** It says what saving is, how
 *    to do it, and offers the one action that produces a first save.
 *
 * Rows are read straight from `savedItemDao().observeAll()` so a delete on the
 * item page lands here without a refresh.
 */
@Composable
fun SavedLibraryScreen(
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    onStartChat: () -> Unit = {},
) {
    val colors = GsTheme.colors
    val dao = ServiceLocator.db.savedItemDao()
    var items by remember { mutableStateOf<List<SavedItemEntity>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var facet by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        dao.observeAll().collect {
            items = it
            loaded = true
        }
    }

    val facets = remember(items) { LibraryModel.facetsOf(items) }
    // A chip that no longer has items (its last row was deleted on the item
    // page) must not leave the list filtered to nothing.
    val active = facet?.takeIf { key -> facets.any { it.kind == key } }
    val visible = remember(items, active) { LibraryModel.applyFilter(items, active) }
    val days = remember(visible) { LibraryModel.groupByDay(visible) }
    val today = remember { LocalDate.now() }

    RefSettingsPage(
        title = "Saved Library",
        subtitle = "Everything you kept from a chat, on this device",
        onBack = onBack,
    ) {
        // A summary card over an empty library reads "0 things kept" above a
        // card that already says "Nothing saved yet" — two ways of saying one
        // nothing. The empty page gets the empty card and nothing else.
        if (loaded && items.isNotEmpty()) {
            heroCard(count = items.size, summary = LibraryModel.summaryLine(items))
        }

        if (!loaded) {
            // One frame between composition and the first Room emission. A
            // skeleton row beats a flash of "nothing saved yet" over a
            // library that is about to fill.
            Spacer(Modifier.height(10.dp))
            RefCard(role = GsGlassRole.CardLight) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .background(colors.divider.copy(alpha = 0.5f), CircleShape)
                    )
                    Spacer(Modifier.width(11.dp))
                    Text(
                        "Reading this device's library…",
                        fontSize = 11.5.sp,
                        fontFamily = GsFonts.Product,
                        color = colors.textTertiary,
                    )
                }
            }
        } else if (items.isEmpty()) {
            emptyCard(onStartChat = onStartChat)
        } else {
            if (facets.size > 1) {
                Spacer(Modifier.height(12.dp))
                FacetRow(
                    facets = facets,
                    total = items.size,
                    selected = active,
                    onSelect = { facet = it },
                )
            }

            days.forEach { day ->
                RefGroupHeader(day.label, "${day.items.size} saved")
                RefCard(role = GsGlassRole.CardLight) {
                    day.items.forEachIndexed { index, item ->
                        RefNavRow(
                            title = item.title.ifBlank { LibraryModel.kindLabel(item.kind) },
                            subtitle = LibraryModel.snippet(item.content),
                            onClick = { onOpenItem(item.id) },
                            leading = {
                                RefWellTile(libraryGlyph(item.kind), libraryTone(item.kind), 28.dp)
                            },
                            trailing = {
                                Text(
                                    // A relative heading already says when;
                                    // repeating "4 Nov" under "Yesterday" is
                                    // the date saying itself twice.
                                    if (day.label == "Today" || day.label == "Yesterday") ""
                                    else LibraryModel.shortDate(item.createdAt),
                                    fontSize = 11.sp,
                                    fontFamily = GsFonts.Product,
                                    color = colors.textTertiary,
                                )
                            },
                        )
                        if (index < day.items.lastIndex) RefRowDivider()
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        RefInfoCard(
            text = "Saved items stay on this device. Export or remove them from " +
                "Settings → Export / Delete Data.",
            icon = GsIcons.Bookmark,
        )
        Spacer(Modifier.height(10.dp))
    }
}

/** The library's own artwork card: the lotus the Memory page leads with, and
 *  the one honest number on the page — how much is actually here. */
@Composable
private fun heroCard(count: Int, summary: String) {
    val colors = GsTheme.colors
    RefCard(role = GsGlassRole.CardLight) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(com.grapsee.gsai.R.drawable.gs_home_lotus_new),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
                Box(
                    Modifier
                        .size(36.dp)
                        .background(Color.White.copy(alpha = 0.34f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        GsIcons.Bookmark,
                        contentDescription = null,
                        tint = Color(0xFF5A50D6),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (count == 1) "1 thing kept" else "$count things kept",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GsFonts.Product,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    summary,
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp,
                    fontFamily = GsFonts.Product,
                    color = colors.textTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The facet strip: "All 12 · Messages 9 · Images 2 · Drafts 1".
 *
 * A capsule row rather than the app's group cards — it is a control, not a
 * destination, and it sits between two cards where a third glass panel would
 * read as content.
 */
@Composable
private fun FacetRow(
    facets: List<LibraryFacet>,
    total: Int,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FacetChip(
                label = "All",
                count = total,
                selected = selected == null,
                onClick = { onSelect(null) },
            )
            facets.forEach { facet ->
                FacetChip(
                    label = facet.label,
                    count = facet.count,
                    selected = selected == facet.kind,
                    onClick = { onSelect(facet.kind) },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun FacetChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = GsTheme.colors
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) colors.accentSoft else colors.glassSurface)
            .clickable(role = Role.Tab, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            fontFamily = GsFonts.Product,
            color = if (selected) colors.accent else colors.textSecondary,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            count.toString(),
            fontSize = 10.sp,
            fontFamily = GsFonts.Product,
            color = if (selected) colors.accent else colors.textTertiary,
        )
    }
}

/** Nothing saved yet — the page explains the verb instead of apologising. */@Composable
private fun emptyCard(onStartChat: () -> Unit) {
    val colors = GsTheme.colors
    RefCard(role = GsGlassRole.CardLight) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RefWellTile(GsIcons.Bookmark, GsWellTone.Peach, 46.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                "Nothing saved yet",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = GsFonts.Product,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Long-press any answer in a chat and choose Save. Images from the " +
                    "studios and drafts you keep land here too — with the whole " +
                    "text, not a preview.",
                fontSize = 11.sp,
                lineHeight = 15.sp,
                fontFamily = GsFonts.Product,
                color = colors.textTertiary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            RefInlinePill(label = "Start a chat", icon = GsIcons.ArrowRight) { onStartChat() }
        }
    }
}

// ---------------------------------------------------------------------------
// Kind → look. One mapping for the list, the item page and Memory's preview,
// so a saved image cannot be a rose palette tile on one screen and a lilac
// message tile on the next.
// ---------------------------------------------------------------------------

internal fun libraryGlyph(kind: String): androidx.compose.ui.graphics.vector.ImageVector =
    when (kind.lowercase(java.util.Locale.US)) {
        "image" -> GsIcons.Palette
        "draft" -> GsIcons.FileText
        else -> GsIcons.MessageCircle
    }

internal fun libraryTone(kind: String): GsWellTone = when (kind.lowercase(java.util.Locale.US)) {
    "image" -> GsWellTone.Rose
    "draft" -> GsWellTone.Blue
    else -> GsWellTone.Lilac
}
